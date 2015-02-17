package nlp.experiments;

import nlp.cache.BatchCoreNLPCache;
import nlp.cache.CoreNLPCache;
import edu.stanford.nlp.classify.LogisticClassifier;
import edu.stanford.nlp.curator.CuratorAnnotations;
import edu.stanford.nlp.curator.PredicateArgumentAnnotation;
import nlp.experiments.greedy.Generator;
import nlp.experiments.greedy.GreedyState;
import edu.stanford.nlp.ie.NumberNormalizer;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import nlp.stamr.AMR;
import nlp.stamr.AMRConstants;
import nlp.stamr.AMRSlurp;
import nlp.stamr.datagen.DumpSequence;
import nlp.stamr.evaluation.Smatch;
import nlp.stamr.utils.MSTGraph;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import nlp.word2vec.Word2VecLoader;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by keenon on 1/27/15.
 *
 * Holds and trains several pipes, which can be analyzed separately or together to give results about AMR.
 */
public class AMRPipeline {

    public static boolean FULL_DATA = true;
    public static boolean TINY_DATA = false;
    public static int trainDataSize = 400;
    public static boolean USE_MULTIHEAD = false;
    public static int maxAllowedHeads = 2;
    public static boolean GREEDY_ARC_HEURISTIC = true;

    /////////////////////////////////////////////////////
    // FEATURE SPECS
    //
    // Features can return Double, double[], or Strings
    // (or anything that handles .toString() without
    // collisions, String is default case)
    /////////////////////////////////////////////////////

    static Map<String,double[]> embeddings;
    static FrameManager frameManager;

    static {
        try {
            embeddings = Word2VecLoader.loadData("data/google-300-fulldata.ser.gz");
            frameManager = new FrameManager("data/frames");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getSRLSenseAtIndex(Annotation annotation, int index) {
        PredicateArgumentAnnotation srl = annotation.get(CuratorAnnotations.PropBankSRLAnnotation.class);
        if (srl == null) return "-"; // If we have no SRL on this example, then oh well
        for (PredicateArgumentAnnotation.AnnotationSpan span : srl.getPredicates()) {
            if ((span.startToken <= index) && (span.endToken >= index + 1)) {
                String sense = span.getAttribute("predicate") + "-" + span.getAttribute("sense");
                return sense;
            }
        }
        return "NONE";
    }

    private String getPrepSenseAtIndex(Annotation annotation, int index) {
        PredicateArgumentAnnotation srl = annotation.get(CuratorAnnotations.PrepSRLAnnotation.class);
        if (srl == null) return "-"; // If we have no SRL on this example, then oh well
        for (PredicateArgumentAnnotation.AnnotationSpan span : srl.getPredicates()) {
            if ((span.startToken <= index) && (span.endToken >= index + 1)) {
                return span.label;
            }
        }
        return "NONE";
    }

    @SuppressWarnings("unchecked")
    LinearPipe<Pair<LabeledSequence,Integer>, String> nerPlusPlus = new LinearPipe<>(
            new ArrayList<Function<Pair<LabeledSequence,Integer>,Object>>(){{

                // Input pair is (Seq, index into Seq for current token)

                add((pair) -> pair.first.tokens[pair.second]);

                add((pair) -> embeddings.get(pair.first.tokens[pair.second]));

                // Left context
                add((pair) -> {
                    if (pair.second == 0) return "^";
                    else return pair.first.tokens[pair.second-1];
                });
                // Right context
                add((pair) -> {
                    if (pair.second == pair.first.tokens.length-1) return "$";
                    else return pair.first.tokens[pair.second+1];
                });

                // Left bigram
                add((pair) -> {
                    if (pair.second == 0) return "^:"+pair.first.tokens[pair.second];
                    else return pair.first.tokens[pair.second-1]+":"+pair.first.tokens[pair.second];
                });
                // Right bigram
                add((pair) -> {
                    if (pair.second == pair.first.tokens.length-1) return pair.first.tokens[pair.second]+":$";
                    else return pair.first.tokens[pair.second+1]+":"+pair.first.tokens[pair.second];
                });

                // POS
                add((pair) -> pair.first.annotation.get(CoreAnnotations.TokensAnnotation.class)
                        .get(pair.second).get(CoreAnnotations.PartOfSpeechAnnotation.class));
                // Left POS
                add((pair) -> {
                    if (pair.second == 0) return "^";
                    else return pair.first.annotation.get(CoreAnnotations.TokensAnnotation.class)
                            .get(pair.second-1).get(CoreAnnotations.PartOfSpeechAnnotation.class);
                });
                // Right POS
                add((pair) -> {
                    if (pair.second >= pair.first.tokens.length-1) return "$";
                    else return pair.first.annotation.get(CoreAnnotations.TokensAnnotation.class)
                            .get(pair.second+1).get(CoreAnnotations.PartOfSpeechAnnotation.class);
                });
                // Left POS + POS
                add((pair) -> {
                    String myPOS = pair.first.annotation.get(CoreAnnotations.TokensAnnotation.class)
                        .get(pair.second).get(CoreAnnotations.PartOfSpeechAnnotation.class);
                    if (pair.second == 0) return "^:"+myPOS;
                    else return pair.first.annotation.get(CoreAnnotations.TokensAnnotation.class)
                            .get(pair.second-1).get(CoreAnnotations.PartOfSpeechAnnotation.class)+":"+myPOS;
                });
                // Right POS + POS
                add((pair) -> {
                    String myPOS = pair.first.annotation.get(CoreAnnotations.TokensAnnotation.class)
                            .get(pair.second).get(CoreAnnotations.PartOfSpeechAnnotation.class);
                    if (pair.second >= pair.first.tokens.length-1) return myPOS+":$";
                    else return myPOS+":"+pair.first.annotation.get(CoreAnnotations.TokensAnnotation.class)
                            .get(pair.second+1).get(CoreAnnotations.PartOfSpeechAnnotation.class);
                });

                // Token dependency parent
                add((pair) -> {
                    SemanticGraph graph = pair.first.annotation.get(CoreAnnotations.SentencesAnnotation.class).get(0)
                            .get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
                    IndexedWord indexedWord = graph.getNodeByIndexSafe(pair.second + 1);
                    if (indexedWord == null) return "NON-DEP";
                    List<IndexedWord> l = graph.getPathToRoot(indexedWord);
                    if (l.size() > 0) {
                        return l.get(0).word();
                    }
                    else return "ROOT";
                });
                // Token dependency parent POS
                add((pair) -> {
                    SemanticGraph graph = pair.first.annotation.get(CoreAnnotations.SentencesAnnotation.class).get(0)
                            .get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
                    IndexedWord indexedWord = graph.getNodeByIndexSafe(pair.second + 1);
                    if (indexedWord == null) return "NON-DEP";
                    List<IndexedWord> l = graph.getPathToRoot(indexedWord);
                    if (l.size() > 0) {
                        return l.get(0).get(CoreAnnotations.PartOfSpeechAnnotation.class);
                    }
                    else return "ROOT";
                });
                // Dependency parent arc
                add((pair) -> {
                    SemanticGraph graph = pair.first.annotation.get(CoreAnnotations.SentencesAnnotation.class).get(0)
                            .get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
                    IndexedWord indexedWord = graph.getNodeByIndexSafe(pair.second + 1);
                    if (indexedWord == null) return "NON-DEP";
                    IndexedWord parent = graph.getParent(indexedWord);
                    if (parent == null) return "ROOT";
                    return graph.getAllEdges(parent, indexedWord).get(0).getRelation().toString();
                });
                // Dependency parent arc + Parent POS
                add((pair) -> {
                    SemanticGraph graph = pair.first.annotation.get(CoreAnnotations.SentencesAnnotation.class).get(0)
                            .get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
                    IndexedWord indexedWord = graph.getNodeByIndexSafe(pair.second + 1);
                    if (indexedWord == null) return "NON-DEP";
                    IndexedWord parent = graph.getParent(indexedWord);
                    if (parent == null) return "ROOT";
                    return graph.getAllEdges(parent, indexedWord).get(0).getRelation().toString()+
                            parent.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                });

                // Closest frame similarity
                add((pair) -> frameManager.getMaxSimilarity(pair.first.tokens[pair.second].toLowerCase()));
                // Closest frame token
                add((pair) -> frameManager.getClosestFrame(pair.first.tokens[pair.second].toLowerCase()));

                // Token NER
                add((pair) -> pair.first.annotation.get(CoreAnnotations.TokensAnnotation.class).get(pair.second).get(CoreAnnotations.NamedEntityTagAnnotation.class));
                // Left bigram NER
                add((pair) -> {
                    String leftNER = "^";
                    if (pair.second > 0) {
                        leftNER = pair.first.annotation.get(CoreAnnotations.TokensAnnotation.class).get(pair.second-1).get(CoreAnnotations.NamedEntityTagAnnotation.class);
                    }
                    return leftNER+":"+pair.first.annotation.get(CoreAnnotations.TokensAnnotation.class).get(pair.second).get(CoreAnnotations.NamedEntityTagAnnotation.class);
                });
                // Right bigram NER
                add((pair) -> {
                    String rightNER = "$";
                    if (pair.second < pair.first.tokens.length-1) {
                        rightNER = pair.first.annotation.get(CoreAnnotations.TokensAnnotation.class).get(pair.second+1).get(CoreAnnotations.NamedEntityTagAnnotation.class);
                    }
                    return pair.first.annotation.get(CoreAnnotations.TokensAnnotation.class).get(pair.second).get(CoreAnnotations.NamedEntityTagAnnotation.class)+":"+rightNER;
                });

                // Get the SRL lemma here
                add((pair) -> {
                    return getSRLSenseAtIndex(pair.first.annotation, pair.second);
                });

                // Binary indicator of SRL lemma
                add((pair) -> {
                    PredicateArgumentAnnotation srl = pair.first.annotation.get(CuratorAnnotations.PropBankSRLAnnotation.class);
                    if (srl == null) return "-"; // If we have no SRL on this example, then oh well
                    for (PredicateArgumentAnnotation.AnnotationSpan span : srl.getPredicates()) {
                        if ((span.startToken <= pair.second) && (span.endToken >= pair.second + 1)) {
                            return "YES";
                        }
                    }
                    return "NO";
                });

                // Get the Prep sense tag here
                add((pair) -> {
                    return getPrepSenseAtIndex(pair.first.annotation, pair.second);
                });

                /*
                // Get coref chain existence
                add((pair) -> {
                     = pair.first.annotation.get(CoreAnnotations.TokensAnnotation.class).get(pair.second).get(CorefCoreAnnotations.CorefChainAnnotation.class);
                    return "COREF";
                });
                */
            }},
            AMRPipeline::writeNerPlusPlusContext
    );

    @SuppressWarnings("unchecked")
    LinearPipe<Triple<LabeledSequence,Integer,Integer>, String> dictionaryLookup = new LinearPipe<>(
            new ArrayList<Function<Triple<LabeledSequence,Integer,Integer>,Object>>(){{

                // Input triple is (Seq, index into Seq for start of expression, index into Seq for end of expression)

                add((triple) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = triple.second; i <= triple.third; i++) {
                        sb.append(triple.first.annotation.get(CoreAnnotations.TokensAnnotation.class).get(i).get(CoreAnnotations.LemmaAnnotation.class).toLowerCase());
                    }
                    return sb.toString();
                });
            }},
            AMRPipeline::writeDictionaryContext
    );

    private Set<String> getBagOfEdges(AMRNodeSet set, int head, int tail) {
        if (head == 0) return new HashSet<>();
        int headToken = set.nodes[head].alignment;
        int tailToken = set.nodes[tail].alignment;
        SemanticGraph graph = set.annotation.get(CoreAnnotations.SentencesAnnotation.class).get(0)
                .get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
        IndexedWord headIndexedWord = graph.getNodeByIndexSafe(headToken + 1);
        IndexedWord tailIndexedWord = graph.getNodeByIndexSafe(tailToken + 1);
        if (headIndexedWord == null || tailIndexedWord == null) {
            return new HashSet<>();
        }
        List<SemanticGraphEdge> edges = graph.getShortestUndirectedPathEdges(headIndexedWord, tailIndexedWord);
        Set<String> bagOfEdges = new HashSet<>();
        for (SemanticGraphEdge edge : edges) {
            bagOfEdges.add(edge.getRelation().toString());
        }
        return bagOfEdges;
    }

    private String getPath(AMRNodeSet set, int head, int tail) {
        if (head == 0 || tail == 0) { // if tail == 0, then something else went fairly wrong
            return "ROOT:NOPATH";
        }
        if (head == tail) {
            return "IDENTITY";
        }
        int headToken = set.nodes[head].alignment;
        int tailToken = set.nodes[tail].alignment;
        SemanticGraph graph = set.annotation.get(CoreAnnotations.SentencesAnnotation.class).get(0)
                .get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);

        IndexedWord headIndexedWord = graph.getNodeByIndexSafe(headToken+1);
        IndexedWord tailIndexedWord = graph.getNodeByIndexSafe(tailToken+1);

        if (headIndexedWord == null || tailIndexedWord == null) {
            return "NOTOKENS:NOPATH";
        }

        List<SemanticGraphEdge> edges = graph.getShortestUndirectedPathEdges(headIndexedWord, tailIndexedWord);

        StringBuilder sb = new StringBuilder();
        IndexedWord currentWord = headIndexedWord;
        for (SemanticGraphEdge edge : edges) {
            if (edge.getDependent().equals(currentWord)) {
                sb.append(" ^ ");
                currentWord = edge.getGovernor();
            }
            else {
                if (!edge.getGovernor().equals(currentWord)) {
                    throw new IllegalStateException("Edges not in order");
                }
                sb.append(" v ");
                currentWord = edge.getDependent();
            }
            sb.append(edge.getRelation().toString());
            if (currentWord != headIndexedWord) {
                sb.append(currentWord.get(CoreAnnotations.PartOfSpeechAnnotation.class));
            }
        }

        return sb.toString();
    }

    private String getEnumType(AMR.Node node) {
        if (node.type == AMR.NodeType.ENTITY) return "ENTITY";
        else if (node.type == AMR.NodeType.VALUE) return "VALUE";
        else if (node.type == AMR.NodeType.QUOTE) return "QUOTE";
        else return "UNKNOWN";
    }

    /**
    Here's the list of CMU Features as seen in the ACL '14 paper:

     - Self edge: 1 if edge is between two nodes in the same fragment
     - Tail fragment root: 1 if the edge's tail is the root of its graph fragment
     - Head fragment root: 1 if the edge's head is the root of its graph fragment
     - Path: Dependency edge labels and parts of speech on the shortest syntactic path between any two words of the two spans
     - Distance: Number of tokens (plus one) between the concept's spans
     - Distance indicators: A feature for each distance value
     - Log distance: Log of the distance feature + 1

     Combos:

     - Path & Head concept
     - Path & Tail concept
     - Path & Head word
     - Path & Tail word
     - Distance & Path

     */

    @SuppressWarnings("unchecked")
    ArrayList<Function<Triple<AMRNodeSet,Integer,Integer>,Object>> cmuFeatures =
            new ArrayList<Function<Triple<AMRNodeSet,Integer,Integer>,Object>>(){{
                // Input triple is (Set, array offset for first node, array offset for second node)

                // Self edge
                add((triple) -> {
                    if (triple.first.forcedArcs[triple.second][triple.third] != null) {
                        return 1.0;
                    }
                    else return 0.0;
                });
                // Head fragment root
                add((triple) -> {
                    for (int i = 1; i < triple.first.nodes.length; i++) {
                        // Any non-ROOT forced parents mean that this is not the head of the fragment
                        if (triple.first.forcedArcs[i][triple.second] != null) {
                            return 0.0;
                        }
                    }
                    return 1.0;
                });
                // Tail fragment root
                add((triple) -> {
                    for (int i = 1; i < triple.first.nodes.length; i++) {
                        // Any non-ROOT forced parents mean that this is not the head of the fragment
                        if (triple.first.forcedArcs[i][triple.third] != null) {
                            return 0.0;
                        }
                    }
                    return 1.0;
                });
                // Path
                add((triple) -> getPath(triple.first, triple.second, triple.third));
                // Distance
                add((triple) -> {
                    if (triple.second != 0) {
                        int headAlignment = triple.first.nodes[triple.second].alignment;
                        int tailAlignment = triple.first.nodes[triple.third].alignment;
                        return Math.abs(headAlignment - tailAlignment) + 1.0;
                    }
                    return 0.0;
                });
                // Distance Indicator
                add((triple) -> {
                    if (triple.second != 0) {
                        int headAlignment = triple.first.nodes[triple.second].alignment;
                        int tailAlignment = triple.first.nodes[triple.third].alignment;
                        return "D:"+Math.abs(headAlignment - tailAlignment) + 1.0;
                    }
                    return "D:ROOT";
                });
                // Log distance
                add((triple) -> {
                    if (triple.second != 0) {
                        int headAlignment = triple.first.nodes[triple.second].alignment;
                        int tailAlignment = triple.first.nodes[triple.third].alignment;
                        return Math.log(Math.abs(headAlignment - tailAlignment) + 1.0);
                    }
                    return 0.0;
                });
                // Path + Head concept
                add((triple) -> {
                    String headConcept;
                    if (triple.second == 0) {
                        headConcept = "ROOT";
                    }
                    else {
                        headConcept = triple.first.nodes[triple.second].toString();
                    }
                    return headConcept + ":" + getPath(triple.first, triple.second, triple.third);
                });
                // Path + Tail concept
                add((triple) -> {
                    String tailConcept = triple.first.nodes[triple.third].toString();
                    return tailConcept + ":" + getPath(triple.first, triple.second, triple.third);
                });
                // Path + Head word
                add((triple) -> {
                    String headWord;
                    if (triple.second == 0) {
                        headWord = "ROOT";
                    }
                    else {
                        headWord = triple.first.tokens[triple.first.nodes[triple.second].alignment];
                    }
                    return headWord + ":" + getPath(triple.first, triple.second, triple.third);
                });
                // Path + Tail word
                add((triple) -> {
                    String tailWord = triple.first.tokens[triple.first.nodes[triple.third].alignment];
                    return tailWord + ":" + getPath(triple.first, triple.second, triple.third);
                });
                // Path + Distance
                add((triple) -> {
                    String distanceIndicator;
                    if (triple.second != 0) {
                        int headAlignment = triple.first.nodes[triple.second].alignment;
                        int tailAlignment = triple.first.nodes[triple.third].alignment;
                        distanceIndicator = Integer.toString(Math.abs(headAlignment - tailAlignment));
                    }
                    else {
                        distanceIndicator = "ROOT";
                    }
                    return distanceIndicator + ":" + getPath(triple.first, triple.second, triple.third);
                });

                // Head seq type
                add((triple) -> {
                    if (triple.second == 0) return "ROOT";
                    AMR.Node node = triple.first.nodes[triple.second];
                    return DumpSequence.getType(node, node.alignment, triple.first.tokens, triple.first.annotation, null);
                });
                // Tail seq type
                add((triple) -> {
                    AMR.Node node = triple.first.nodes[triple.third];
                    return DumpSequence.getType(node, node.alignment, triple.first.tokens, triple.first.annotation, null);
                });
                // Head+Tail seq type
                add((triple) -> {
                    AMR.Node head = triple.first.nodes[triple.second];
                    AMR.Node tail = triple.first.nodes[triple.third];
                    String headType = triple.second == 0 ? "ROOT" : DumpSequence.getType(head, head.alignment, triple.first.tokens, triple.first.annotation, null);
                    String tailType = DumpSequence.getType(tail, tail.alignment, triple.first.tokens, triple.first.annotation, null);
                    return headType+tailType;
                });

                // Head node type
                add((triple) -> {
                    if (triple.second == 0) return "ROOT";
                    AMR.Node node = triple.first.nodes[triple.second];
                    return getEnumType(node);
                });
                // Tail node type
                add((triple) -> {
                    AMR.Node node = triple.first.nodes[triple.third];
                    return getEnumType(node);
                });
                // Head+Tail node type
                add((triple) -> {
                    AMR.Node head = triple.first.nodes[triple.second];
                    AMR.Node tail = triple.first.nodes[triple.third];
                    String headType = triple.second == 0 ? "ROOT" : getEnumType(head);
                    String tailType = getEnumType(tail);
                    return headType+tailType;
                });

                // Bag of edges
                add((triple) -> getBagOfEdges(triple.first, triple.second, triple.third));

                // Bag of edges joined with lemma
                add((triple) -> {
                    if (triple.second == 0) return new HashSet<String>();
                    int headToken = triple.first.nodes[triple.second].alignment;
                    String headLemma = triple.first.annotation.get(CoreAnnotations.TokensAnnotation.class).get(headToken).lemma();

                    Set<String> set = getBagOfEdges(triple.first, triple.second, triple.third);
                    Set<String> appendedLemma = new HashSet<>();
                    for (String s : set) {
                        appendedLemma.add(s + headLemma);
                    }
                    return appendedLemma;
                });

                // SRL head
                add((triple) -> {
                    if (triple.second == 0) return new HashSet<String>();
                    int headToken = triple.first.nodes[triple.second].alignment;
                    String headLemma = triple.first.annotation.get(CoreAnnotations.TokensAnnotation.class).get(headToken).lemma();

                    Set<String> set = getBagOfEdges(triple.first, triple.second, triple.third);
                    Set<String> appendedLemma = new HashSet<>();
                    for (String s : set) {
                        appendedLemma.add(s + headLemma);
                    }
                    return appendedLemma;
                });

                // Get SRL path, if it exists
                add((triple) -> {
                    PredicateArgumentAnnotation srl = triple.first.annotation.get(CuratorAnnotations.PropBankSRLAnnotation.class);
                    if (srl == null) return "-";
                    if (triple.second == 0) return "ROOT";

                    int headAlignment = triple.first.nodes[triple.second].alignment;
                    int tailAlignment = triple.first.nodes[triple.third].alignment;

                    for (PredicateArgumentAnnotation.AnnotationSpan span : srl.getPredicates()) {
                        if ((span.startToken <= headAlignment) && (span.endToken >= headAlignment + 1)) {
                            for (PredicateArgumentAnnotation.AnnotationSpan argSpan : srl.getArguments(span)) {
                                if ((argSpan.startToken <= tailAlignment) && (argSpan.endToken >= tailAlignment + 1)) {
                                    // System.out.println(triple.first.nodes[triple.second]+" :"+argSpan.label+" "+triple.first.nodes[triple.third]);
                                    return argSpan.label;
                                }
                            }
                        }
                    }
                    return "NONE";
                });

                /*
                // Get the embeddings for the head node title
                add((triple) -> {
                    if (triple.second == 0) return new double[300];
                    String headTitle = triple.first.nodes[triple.second].title;
                    if (headTitle.contains("-") && !headTitle.equals("-")) headTitle = headTitle.split("-")[0];
                    return embeddings.get(headTitle.toLowerCase());
                });
                // Get the embeddings for the tail node title
                add((triple) -> {
                    String tailTitle = triple.first.nodes[triple.third].title;
                    if (tailTitle.contains("-") && !tailTitle.equals("-")) tailTitle = tailTitle.split("-")[0];
                    return embeddings.get(tailTitle.toLowerCase());
                });
                */

                /**
                 * Path, where all the PREP tokens get tagged with their SRL interpretation instead of POS
                 */
            /*
                add((triple) -> {
                    if (triple.second == 0) { // if tail == 0, then something else went fairly wrong
                        return "ROOT:NOPATH";
                    }
                    if (triple.second == triple.third) {
                        return "IDENTITY";
                    }
                    int headToken = triple.first.nodes[triple.second].alignment;
                    int tailToken = triple.first.nodes[triple.third].alignment;
                    SemanticGraph graph = triple.first.annotation.get(CoreAnnotations.SentencesAnnotation.class).get(0)
                            .get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);

                    IndexedWord headIndexedWord = graph.getNodeByIndexSafe(headToken+1);
                    IndexedWord tailIndexedWord = graph.getNodeByIndexSafe(tailToken+1);

                    if (headIndexedWord == null || tailIndexedWord == null) {
                        return "NOTOKENS:NOPATH";
                    }

                    List<SemanticGraphEdge> edges = graph.getShortestUndirectedPathEdges(headIndexedWord, tailIndexedWord);

                    StringBuilder sb = new StringBuilder();
                    IndexedWord currentWord = headIndexedWord;
                    for (SemanticGraphEdge edge : edges) {
                        if (edge.getDependent().equals(currentWord)) {
                            sb.append(" ^ ");
                            currentWord = edge.getGovernor();
                        }
                        else {
                            if (!edge.getGovernor().equals(currentWord)) {
                                throw new IllegalStateException("Edges not in order");
                            }
                            sb.append(" v ");
                            currentWord = edge.getDependent();
                        }
                        sb.append(edge.getRelation().toString());
                        if (currentWord != headIndexedWord) {
                            sb.append(getPrepSenseAtIndex(triple.first.annotation, currentWord.index()-1));
                            // sb.append(currentWord.get(CoreAnnotations.PartOfSpeechAnnotation.class));
                        }
                    }
                    return sb.toString();
                });
                */
    }};

    LinearPipe<Triple<AMRNodeSet,Integer,Integer>, Boolean> arcExistence = new LinearPipe<>(
            cmuFeatures,
            AMRPipeline::writeArcContext
    );

    @SuppressWarnings("unchecked")
    LinearPipe<Triple<AMRNodeSet,Integer,Integer>, String> arcType = new LinearPipe<>(
            cmuFeatures,
            AMRPipeline::writeArcContext
    );

    /////////////////////////////////////////////////////
    // PIPELINE
    /////////////////////////////////////////////////////

    public void trainStages() throws IOException {
        System.out.println("Loading training data");
        List<LabeledSequence> nerPlusPlusData = loadSequenceData(FULL_DATA ? "realdata/release-train-seq.txt" : (TINY_DATA ? "data/train-3-seq.txt" : "data/train-" + trainDataSize + "-seq.txt"));
        // List<LabeledSequence> dictionaryData = loadManygenData(FULL_DATA ? "realdata/train-manygen.txt" : (TINY_DATA ? "data/train-3-manygen.txt" : "data/train-" + trainDataSize + "-manygen.txt"));
        List<LabeledSequence> dictionaryData = loadManygenData(FULL_DATA ? "realdata/train-manygen.txt" : (TINY_DATA ? "data/train-3-manygen.txt" : "realdata/train-manygen.txt"));
        List<AMRNodeSet> mstData = loadCoNLLData(FULL_DATA ? "realdata/release-train-conll.txt" : ( TINY_DATA ? "data/train-3-conll.txt" : "data/train-"+trainDataSize+"-conll.txt"));

        System.out.println("Training");
        nerPlusPlus.sigma = 0.5;
        nerPlusPlus.train(getNERPlusPlusForClassifier(nerPlusPlusData));

        dictionaryLookup.type = LinearPipe.ClassifierType.BAYESIAN;
        dictionaryLookup.train(getDictionaryForClassifier(dictionaryData));

        arcExistence.type = LinearPipe.ClassifierType.LOGISTIC;
        arcExistence.automaticallyReweightTrainingData = false; // uses HUBER penalty instead on Logistic regression
        arcExistence.sigma = 0.13;
        arcExistence.train(getArcExistenceForClassifier(mstData));

        arcType.sigma = 3.0;
        arcType.train(getArcTypeForClassifier(mstData));

        /*
        LinearClassifier<String,String> classifier = (LinearClassifier<String, String>) arcType.classifiers.get(0);
        PrintWriter pw = IOUtils.getPrintWriter("data/arcTypeWeights-full.txt");
        classifier.dump(pw);
        pw.close();
        */

        LogisticClassifier<Boolean,String> logistic = (LogisticClassifier<Boolean,String>) arcExistence.classifiers.get(0);
        Counter<String> weights = logistic.weightsAsCounter();
        BufferedWriter bw = new BufferedWriter(new FileWriter("data/arcExistence.csv"));
        List<String> keys = new ArrayList<>();
        keys.addAll(weights.keySet());
        Collections.sort(keys, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return Double.compare(weights.getCount(o2), weights.getCount(o1));
            }
        });

        for (String s : keys) {
            bw.write(s);
            bw.write(",");
            bw.write(Double.toString(weights.getCount(s)));
            bw.write("\n");
        }
        bw.close();
    }

    /**
     * This is bunch of code lifted from Ditch for generating special case AMR stuff that we have reliable parsers for.
     */

    private AMR constructNERCluster(Annotation annotation, List<Integer> nerList, String tag, boolean debug) {
        tag = tag.toLowerCase();
        if (tag.equals("date")) { // || tag.equals("time") || tag.equals("duration")) {
            return constructDateCluster(annotation, nerList, tag, debug);
        }
        else if (tag.equals("ordinal")) {
            return constructOrdinalCluster(annotation, nerList, debug);
        }
        else if (tag.equals("number")) {
            return constructNumberCluster(annotation, nerList, debug);
        }
        else if (tag.equals("person")) {
            return constructEntityCluster(annotation, nerList, tag, debug);
        }
        return null;
    }

    private AMR constructDateCluster(Annotation annotation, List<Integer> dateList, String tag, boolean debug) {
        AMR dateChunk = new AMR();
        if (tag.equals("date")) {
            tag = "date-entity";
        }
        AMR.Node root = dateChunk.addNode("" + tag.charAt(0), tag, dateList.get(0));

        String time = annotation.get(CoreAnnotations.TokensAnnotation.class).get(dateList.get(0)).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
        if (time == null) return dateChunk;
        if (debug) System.out.println("DATE: "+dateList.get(0)+" -> "+time);

        int year = -1;
        boolean isWeek = false;
        int week = -1;
        int month = -1;
        int day = -1;

        String[] parts = time.split("-");
        try {
            year = Integer.parseInt(parts[0]);
        }
        catch (Exception ignored) {}
        if (parts.length > 1) {
            if (parts[1].startsWith("W")) {
                isWeek = true;
                try {
                    week = Integer.parseInt(parts[1].substring(1));
                } catch (Exception ignored) {
                }
            } else {
                try {
                    month = Integer.parseInt(parts[1]);
                } catch (Exception ignored) {
                }
            }
        }
        if (parts.length > 2) {
            try {
                day = Integer.parseInt(parts[2]);
            } catch (Exception ignored) {
            }
        }

        if (year != -1) {
            AMR.Node yearN = dateChunk.addNode(""+year, AMR.NodeType.VALUE);
            dateChunk.addArc(root, yearN, "year");
        }
        if (month != -1) {
            AMR.Node monthN = dateChunk.addNode(""+month, AMR.NodeType.VALUE);
            dateChunk.addArc(root, monthN, "month");
        }
        if (week != -1) {
            AMR.Node weekN = dateChunk.addNode(""+week, AMR.NodeType.VALUE);
            dateChunk.addArc(root, weekN, "week");
        }
        if (day != -1) {
            if (isWeek) {
                String weekday = AMRConstants.weekdays.get(day).toLowerCase();
                AMR.Node dayN = dateChunk.addNode(""+weekday.charAt(0), weekday);
                dateChunk.addArc(root, dayN, "weekday");
            }
            else {
                AMR.Node dayN = dateChunk.addNode("" + day, AMR.NodeType.VALUE);
                dateChunk.addArc(root, dayN, "day");
            }
        }

        // Just append everything if we have nothing else
        if (year == -1 && month == -1 && week == -1 && day == -1) {
            for (int i : dateList) {
                String token = annotation.get(CoreAnnotations.TokensAnnotation.class).get(i).lemma();
                AMR.Node word = dateChunk.addNode(""+token.charAt(0), token);
                dateChunk.addArc(root, word, "time");
            }
        }

        return dateChunk;
    }

    private static String getSequentialTokens(Annotation annotation, List<Integer> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i != 0) sb.append(" ");
            sb.append(annotation.get(CoreAnnotations.TokensAnnotation.class).get(list.get(i)));
        }
        return sb.toString();
    }

    private AMR constructOrdinalCluster(Annotation annotation, List<Integer> ordinalList, boolean debug) {
        AMR chunk = new AMR();
        AMR.Node head = chunk.addNode("o", "ordinal-entity");
        Number n = null;
        try {
            n = NumberNormalizer.wordToNumber(getSequentialTokens(annotation, ordinalList));
        }
        catch (Exception ignored) {}
        if (n == null) n = 1;
        AMR.Node tail = chunk.addNode(n.toString(), AMR.NodeType.VALUE, Collections.min(ordinalList));
        chunk.addArc(head, tail, "value");
        return chunk;
    }

    private AMR constructNumberCluster(Annotation annotation, List<Integer> numberList, boolean debug) {
        AMR chunk = new AMR();
        Number n = null;
        try {
            NumberNormalizer.wordToNumber(getSequentialTokens(annotation, numberList));
        }
        catch (Exception ignored) {}
        if (n == null) n = 1;
        chunk.addNode(n.toString(), AMR.NodeType.VALUE);
        return chunk;
    }

    private AMR constructEntityCluster(Annotation annotation, List<Integer> nerList, String tag, boolean debug) {
        AMR nerChunk = new AMR();
        AMR.Node root = nerChunk.addNode("" + tag.charAt(0), tag, nerList.get(0));
        AMR.Node name = nerChunk.addNode("n", "name", nerList.get(0));
        nerChunk.addArc(root, name, "name");

        String quoteConjunction = "";
        for (int i : nerList) {
            if (quoteConjunction.length() > 0) quoteConjunction += " ";
            quoteConjunction += annotation.get(CoreAnnotations.TokensAnnotation.class).get(i).word().toLowerCase();
        }

        if (debug) System.out.println("Checking quote conjunction: \""+quoteConjunction+"\"");

        if (AMRConstants.commonNamedEntityConfusions.containsKey(quoteConjunction)) {
            Pair<String,String> pair = AMRConstants.commonNamedEntityConfusions.get(quoteConjunction);
            // Set the type
            root.title = pair.first;
            // Set the parts as children
            String[] parts = pair.second.split(" ");
            for (int i = 0; i < parts.length; i++) {
                AMR.Node opTag = nerChunk.addNode(parts[i], AMR.NodeType.QUOTE, root.alignment);
                nerChunk.addArc(name, opTag, "op" + (i + 1));
            }
        }
        else {
            for (int i = 0; i < nerList.size(); i++) {
                AMR.Node opTag = nerChunk.addNode(annotation.get(CoreAnnotations.TokensAnnotation.class).get(nerList.get(i)).word(), AMR.NodeType.QUOTE, nerList.get(i));
                nerChunk.addArc(name, opTag, "op" + (i + 1));
            }
        }
        if (debug) {
            System.out.println("Built NER cluster: ");
            System.out.println(nerChunk.toString());
        }

        int min = 10000;
        for (int i : nerList) {
            min = Math.min(min, i);
        }

        for (AMR.Node node : nerChunk.nodes) {
            if (node.alignment == 0) {
                node.alignment = min;
            }
        }

        return nerChunk;
    }

    public List<AMR> predictClusters(String[] tokens, Annotation annotation) {
        List<AMR> gen = new ArrayList<>();
        Set<Integer> blocked = new HashSet<>();

        LabeledSequence labeledSequence = new LabeledSequence();
        labeledSequence.tokens = tokens;
        labeledSequence.annotation = annotation;

        String[] labels = new String[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            labels[i] = nerPlusPlus.predict(new Pair<>(labeledSequence, i));
        }

        // Force anything inside an -LRB- -RRB- to be NONE, which is how AMR generally handles it

        boolean inBrace = false;
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].equals("-LRB-")) {
                inBrace = true;
                labels[i] = "NONE";
                blocked.add(i);
            }
            if (tokens[i].equals("-RRB-")) {
                inBrace = false;
                labels[i] = "NONE";
                blocked.add(i);
            }
            if (inBrace) {
                labels[i] = "NONE";
                blocked.add(i);
            }
        }

        // First we get all adjacent DICT entries together...

        List<List<Integer>> adjacentDicts = new ArrayList<>();

        List<Integer> currentDict = null;
        for (int i = 0; i < tokens.length; i++) {
            if (!blocked.contains(i) && labels[i].equals("DICT")) {
                if (currentDict == null) {
                    currentDict = new ArrayList<>();
                }
                currentDict.add(i);
            }
            else if (currentDict != null) {
                adjacentDicts.add(currentDict);
                currentDict = null;
            }
        }
        if (currentDict != null) {
            adjacentDicts.add(currentDict);
        }

        // Try generating anything that we can add with high precision

        List<Integer> nerSpan = new ArrayList<>();
        String lastNerTag = "O";

        for (int i = 0; i < tokens.length; i++) {
            String nerTag = annotation.get(CoreAnnotations.TokensAnnotation.class).get(i).get(CoreAnnotations.NamedEntityTagAnnotation.class);
            if (nerTag == null) nerTag = "O";
            if (blocked.contains(i)) nerTag = "O";
            if (!nerTag.equals(lastNerTag)) {
                if (!lastNerTag.equals("O")) {
                    AMR attempt = constructNERCluster(annotation, nerSpan, lastNerTag, false);
                    if (attempt != null) {
                        System.out.println("Generated a cluster from a formula!");
                        System.out.println("\""+getSequentialTokens(annotation, nerSpan)+"\"");
                        System.out.println(attempt.toString(AMR.AlignmentPrinting.ALL));
                        gen.add(attempt);
                        blocked.addAll(nerSpan);
                    }
                    nerSpan = new ArrayList<>();
                }
            }
            if (!nerTag.equals("O")) {
                nerSpan.add(i);
            }
            lastNerTag = nerTag;
        }

        // Add all the dict elements

        outer: for (List<Integer> dict : adjacentDicts) {
            int first = dict.get(0);
            int last = dict.get(dict.size() - 1);
            for (int i = first; i <= last; i++) {
                if (blocked.contains(i)) continue outer;
            }

            List<Pair<String,Integer>> bestChunks = getBestAMRChunks(labeledSequence, first, last);
            if (bestChunks.size() > 0) {
                for (Pair<String, Integer> amrPair : bestChunks) {
                    String amrString = amrPair.first;
                    AMR amr;
                    if (amrString.startsWith("(")) {
                        assert (amrString.endsWith(")"));
                        amr = AMRSlurp.parseAMRTree(amrString);
                    } else if (amrString.startsWith("\"")) {
                        if (amrString.split(" ").length > 1) amrString = amrString.split(" ")[0];
                        amr = createAMRSingleton(amrString.substring(1, amrString.length() - 1), AMR.NodeType.QUOTE);
                    } else {
                        amr = createAMRSingleton(amrString, AMR.NodeType.VALUE);
                    }

                    int minAlignment = Integer.MAX_VALUE;
                    int maxAlignment = Integer.MIN_VALUE;
                    for (AMR.Node node : amr.nodes) {
                        node.alignment = Math.min(amrPair.second + node.alignment, tokens.length - 1);
                        if (minAlignment > node.alignment) minAlignment = node.alignment;
                        if (maxAlignment < node.alignment) maxAlignment = node.alignment;
                    }
                    gen.add(amr);
                    for (int i = minAlignment; i <= maxAlignment; i++) {
                        blocked.add(i);
                    }
                }
            }
        }

        // Do all non-dict elements

        for (int i = 0; i < tokens.length; i++) {
            AMR amr = null;
            if (blocked.contains(i)) continue;
            if (labels[i].equals("VERB")) {
                String srlSense = getSRLSenseAtIndex(labeledSequence.annotation, i);
                if (srlSense.equals("-") || srlSense.equals("NONE")) {
                    String stem = labeledSequence.annotation.get(CoreAnnotations.TokensAnnotation.class).
                            get(i).get(CoreAnnotations.LemmaAnnotation.class).toLowerCase();
                    amr = createAMRSingleton(frameManager.getClosestFrame(stem));
                }
                else {
                    amr = createAMRSingleton(srlSense);
                }
            }
            else if (labels[i].equals("IDENTITY")) {
                amr = createAMRSingleton(tokens[i].toLowerCase());
            }
            else if (labels[i].equals("LEMMA")) {
                amr = createAMRSingleton(labeledSequence.annotation.get(CoreAnnotations.TokensAnnotation.class).
                        get(i).get(CoreAnnotations.LemmaAnnotation.class).toLowerCase());
            }
            if (amr != null) {
                for (AMR.Node node : amr.nodes) {
                    node.alignment = i;
                }
                gen.add(amr);
            }
            //
            // else do nothing, no other tags generate any nodes, purely for MST
            //
        }

        return gen;
    }

    private Pair<AMR.Node[],String[][]> predictNodes(String[] tokens, Annotation annotation) {
        List<AMR> gen = predictClusters(tokens, annotation);

        // Go through and pick out all the nodes

        List<AMR.Node> genNodes = new ArrayList<>();
        for (AMR amr : gen) {
            genNodes.addAll(amr.nodes);
        }

        AMR.Node[] nodes = new AMR.Node[genNodes.size()+1];
        for (int i = 0; i < genNodes.size(); i++) {
            nodes[i+1] = genNodes.get(i);
        }

        int length = nodes.length-1;

        String[][] forcedArcs = new String[length+1][length+1];

        // Get back all the forced arcs

        for (AMR amr : gen) {
            for (AMR.Arc arc : amr.arcs) {
                for (int i = 1; i <= length; i++) {
                    for (int j = 1; j <= length; j++) {
                        if (arc.head == nodes[i] && arc.tail == nodes[j]) {
                            forcedArcs[i][j] = arc.title;
                        }
                    }
                }
            }
        }

        return new Pair<>(nodes, forcedArcs);
    }

    private List<Pair<String,Integer>> getBestAMRChunks(LabeledSequence labeledSequence, int first, int last) {
        List<Pair<String,Integer>> bestChunks = new ArrayList<>();

        Counter<String> amrStrings = dictionaryLookup.predictSoft(new Triple<>(labeledSequence, first, last));

        String amrString = null;
        double bestCount = 0.0;
        for (String s : amrStrings.keySet()) {
            double count = amrStrings.getCount(s);
            if (count > bestCount) {
                amrString = s;
                bestCount = count;
            }
        }

        if (bestCount > 0) {
            bestChunks.add(new Pair<String, Integer>(amrString, first));
            return bestChunks;
        }
        else {
            // Recursion base case, we didn't find anything
            if (first == last) {
                return new ArrayList<>();
            }

            for (int pivot = first; pivot < last; pivot++) {
                List<Pair<String,Integer>> part1 = getBestAMRChunks(labeledSequence, first, pivot);
                List<Pair<String,Integer>> part2 = getBestAMRChunks(labeledSequence, pivot+1, last);

                if (part1.size() + part2.size() > bestChunks.size()) {
                    bestChunks = new ArrayList<>();
                    bestChunks.addAll(part1);
                    bestChunks.addAll(part2);
                }
            }

            return bestChunks;
        }
    }

    public AMR runMSTPipeline(String[] tokens, Annotation annotation, AMRNodeSet nodeSet) {

        // Run MST on the arcs we've got

        MSTGraph mstGraph = new MSTGraph();

        int length = nodeSet.nodes.length-1;

        // Parent Node
        for (int i = 0; i <= length; i++) {
            if (nodeSet.nodes[i] == null && i != 0) continue;
            // Can't have outgoing arcs from a non-entity type node
            if (i == 0 || nodeSet.nodes[i].type == AMR.NodeType.ENTITY) {
                // Child Node
                for (int j = 1; j <= length; j++) {
                    if (nodeSet.nodes[j] == null) continue;
                    if (i == j) continue;

                    /*
                    if (nodeSet.forcedArcs[i][j] != null) {
                        mstGraph.addArc(i, j, nodeSet.forcedArcs[i][j], 1000.0);
                    }
                    */

                    // Check if there's a forcedArc that corresponds to this child
                    int forcedParent = -1;
                    for (int k = 0; k <= length; k++) {
                        if (nodeSet.forcedArcs[k][j] != null) {
                            forcedParent = k;
                            break;
                        }
                    }

                    // If there is a forcedParent, then set arcs accordingly
                    if (forcedParent != -1) {
                        if (forcedParent == i) {
                            mstGraph.addArc(i, j, nodeSet.forcedArcs[i][j], 1000.0);
                        }
                        else {
                            // mstGraph.addArc(i, j, nodeSet.forcedArcs[i][j], -1000.0);
                        }
                    }
                    else {
                        double logProb;

                        if (nodeSet.nodes[i] != null
                                && nodeSet.nodes[j] != null
                                && nodeSet.nodes[i].title.equals("name")
                                && nodeSet.nodes[j].type != AMR.NodeType.QUOTE) {
                            // Insanely unlikely that a name will ever link to a non-QUOTE node
                            logProb = -10000;
                        }
                        else {
                            Counter<Boolean> counter = arcExistence.predictSoft(new Triple<>(nodeSet, i, j));
                            Counters.logNormalizeInPlace(counter);
                            logProb = counter.getCount(true);
                        }

                        /*
                        Counter<String> arcTypeLogProb = arcType.predictSoft(new Triple<>(nodeSet, i, j));
                        Counters.logNormalizeInPlace(arcTypeLogProb);

                        for (String s : arcTypeLogProb.keySet()) {
                            mstGraph.addArc(i, j, s, logProb + arcTypeLogProb.getCount(s));
                        }
                        */

                        mstGraph.addArc(i, j, "NO-LABEL", logProb);
                    }
                }
            }
        }

        // Stitch based on the MST we got

        Map<Integer,Set<Pair<String,Integer>>> arcMap = mstGraph.getMST(false);

        final boolean VALIDITY_CHECKS = true;

        if (VALIDITY_CHECKS) {
            Set<Integer> seenNodes = new HashSet<>();
            Queue<Integer> visitQueue = new ArrayDeque<>();
            visitQueue.add(0);
            while (!visitQueue.isEmpty()) {
                int i = visitQueue.poll();
                seenNodes.add(i);
                if (arcMap.containsKey(i)) {
                    for (Pair<String, Integer> arc : arcMap.get(i)) {
                        if (!arc.first.equals("NONE") && !seenNodes.contains(arc.second)) {
                            visitQueue.add(arc.second);
                        }
                    }
                }
            }
            for (int i = 0; i < nodeSet.nodes.length; i++) {
                if (nodeSet.nodes[i] != null && !seenNodes.contains(i)) {
                    System.out.println("Sentence: "+annotation);
                    System.out.println("Missing node "+i+": "+nodeSet.nodes[i]);
                    System.out.println(Arrays.toString(nodeSet.nodes));
                    throw new IllegalStateException("Must contain all relevant nodes!");
                }
            }
        }

        GreedyState state = new GreedyState(nodeSet.nodes, tokens, annotation);
        for (int i : arcMap.keySet()) {
            if (GREEDY_ARC_HEURISTIC) {
                while (true) {
                    Set<String> takenArcs = new HashSet<>();
                    for (int j = 0; j < nodeSet.nodes.length; j++) {
                        if (state.arcs[i][j] != null) {
                            takenArcs.add(state.arcs[i][j]);
                        }
                    }

                    double bestNewArcScore = Double.NEGATIVE_INFINITY;
                    Pair<String, Integer> bestNewArc = null;

                    for (Pair<String, Integer> arc : arcMap.get(i)) {
                        if (arc.first.equals("NO-LABEL")) {
                            if ((state.arcs[i][arc.second] == null || state.arcs[i][arc.second].equals("NO-LABEL"))) {
                                Counter<String> probs = arcType.predictSoft(new Triple<>(nodeSet, i, arc.second));
                                for (String type : probs.keySet()) {
                                    if (!takenArcs.contains(type)) {
                                        if (probs.getCount(type) > bestNewArcScore) {
                                            bestNewArc = new Pair<>(type, arc.second);
                                            bestNewArcScore = probs.getCount(type);
                                        }
                                    }
                                }
                            }
                        } else {
                            state.arcs[i][arc.second] = arc.first;
                        }
                    }

                    if (bestNewArc == null) break;
                    if (state.arcs[i][bestNewArc.second] != null && !state.arcs[i][bestNewArc.second].equals("NO-LABEL")) {
                        throw new IllegalStateException("Trying to label the same arc twice, old " + state.arcs[i][bestNewArc.second] + ", new " + bestNewArc.first);
                    }
                    state.arcs[i][bestNewArc.second] = bestNewArc.first;
                }
            }
            else {
                for (Pair<String, Integer> arc : arcMap.get(i)) {
                    if (arc.first.equals("NO-LABEL")) {
                        state.arcs[i][arc.second] = arcType.predict(new Triple<>(nodeSet, i, arc.second));
                    }
                    else {
                        state.arcs[i][arc.second] = arc.first;
                    }
                }
            }
        }

        if (VALIDITY_CHECKS) {
            // Verify that the arcs are all present that need to be
            for (int i = 0; i < nodeSet.nodes.length; i++) {
                if (nodeSet.nodes[i] != null) {
                    boolean hasIncoming = false;
                    for (int j = 0; j < nodeSet.nodes.length; j++) {
                        if (state.arcs[j][i] != null && !state.arcs[j][i].equals("NONE")) {
                            hasIncoming = true;
                            break;
                        }
                    }
                    if (!hasIncoming) {
                        List<Triple<Integer, String, Integer>> parentArcs = new ArrayList<>();
                        for (int j : arcMap.keySet()) {
                            for (Pair<String, Integer> arc : arcMap.get(j)) {
                                if (arc.second == i) parentArcs.add(new Triple<>(j, arc.first, arc.second));
                            }
                        }
                        // throw new IllegalStateException("Must have incoming arcs for all nodes. Missing: "+nodeSet.nodes[i]);
                    }
                }
            }
        }

        AMR generated = Generator.generateAMR(state);

        if (VALIDITY_CHECKS) {
            // Verify the generated AMR has all relevant nodes
            List<AMR.Node> generatedConnectedNodes = generated.breadthFirstSearch();
            for (int i = 0; i < nodeSet.nodes.length; i++) {
                if (nodeSet.nodes[i] != null) {
                    boolean containsAnEqual = false;
                    for (AMR.Node node : generatedConnectedNodes) {
                        if (nodeSet.nodes[i].title.equals(node.title)) containsAnEqual = true;
                    }
                    if (!containsAnEqual) {
                        throw new IllegalStateException("Must contain all nodes we arc'd for. Missing: " + nodeSet.nodes[i]);
                    }
                }
            }
        }

        return generated;
    }

    public AMR runPipeline(String[] tokens, Annotation annotation) {
        // Special case code to handle things like: 2008-01-03
        if (tokens.length == 1) {
            String token = tokens[0];
            Pattern p = Pattern.compile("[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]");
            if (p.matcher(token).matches()) {
                String[] parts = token.split("-");
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);
                AMR result = new AMR();
                result.sourceText = tokens;

                /*
                (d / date-entity [0 = "2008-02-15"]
                    :year 2008 [0 = "2008-02-15"]
                    :month 2 [0 = "2008-02-15"]
                    :day 15 [0 = "2008-02-15"])
                 */

                AMR.Node root = result.addNode("d", "date-entity", 0);
                AMR.Node yearN = result.addNode(""+year, AMR.NodeType.VALUE);
                AMR.Node monthN = result.addNode(""+month, AMR.NodeType.VALUE);
                AMR.Node dayN = result.addNode(""+day, AMR.NodeType.VALUE);
                result.addArc(root, yearN, "year");
                result.addArc(root, monthN, "month");
                result.addArc(root, dayN, "day");
                return result;
            }
        }

        AMRNodeSet nodeSet = new AMRNodeSet();
        nodeSet.annotation = annotation;
        nodeSet.tokens = tokens;
        Pair<AMR.Node[], String[][]> nodesAndArcs = predictNodes(tokens, annotation);
        nodeSet.nodes = nodesAndArcs.first;
        nodeSet.correctArcs = new String[nodeSet.nodes.length][nodeSet.nodes.length];
        nodeSet.forcedArcs = nodesAndArcs.second;

        return runMSTPipeline(tokens, annotation, nodeSet);
    }

    private AMR createAMRSingleton(String title) {
        return createAMRSingleton(title, AMR.NodeType.ENTITY);
    }

    private AMR createAMRSingleton(String title, AMR.NodeType type) {
        if (title.split(" ").length > 1) {
            title = title.split(" ")[0];
        }
        if (title.contains("\\") || title.contains(":")) {
            title = "and";
        }
        if (title.contains("/")) {
            title = title.replaceAll("/","SLASH");
        }

        // Quick dose of fix so that smatch will run properly

        if (type != AMR.NodeType.QUOTE) {
            Pattern p = Pattern.compile("[a-zA-Z0-9-]*");
            Matcher matcher = p.matcher(title);
            if (!matcher.matches()) {
                title = "x"; // Illegal character replacement
            }
        }

        AMR amr = new AMR();
        if (type == AMR.NodeType.ENTITY) {
            amr.addNode("" + title.toLowerCase().charAt(0), title);
        }
        else {
            amr.addNode(title, type);
        }
        return amr;
    }

    public void analyzeStages() throws IOException {
        System.out.println("Loading training data");
        List<LabeledSequence> nerPlusPlusDataTrain = loadSequenceData(FULL_DATA ? "data/train-500-seq.txt" : ( TINY_DATA ? "data/train-3-seq.txt" : "data/train-"+trainDataSize+"-seq.txt"));
        // List<LabeledSequence> dictionaryDataTrain = loadManygenData(FULL_DATA ? "realdata/train-manygen.txt" : ( TINY_DATA ? "data/train-3-manygen.txt" : "data/train-"+trainDataSize+"-manygen.txt"));
        List<LabeledSequence> dictionaryDataTrain = loadManygenData(FULL_DATA ? "realdata/train-manygen.txt" : ( TINY_DATA ? "data/train-3-manygen.txt" : "realdata/train-manygen.txt"));
        List<AMRNodeSet> mstDataTrain = loadCoNLLData(FULL_DATA ? "data/train-500-conll.txt" : ( TINY_DATA ? "data/train-3-conll.txt" : "data/train-"+trainDataSize+"-conll.txt"));

        System.out.println("Loading dev data");
        List<LabeledSequence> nerPlusPlusDataTest = loadSequenceData(FULL_DATA ? "data/test-100-seq.txt" : (TINY_DATA ? "data/train-3-seq.txt" : "data/dev-100-seq.txt"));
        List<LabeledSequence> dictionaryDataTest = loadManygenData(FULL_DATA ? "data/test-100-manygen.txt" : (TINY_DATA ? "data/train-3-manygen.txt" : "data/dev-100-manygen.txt"));
        List<AMRNodeSet> mstDataTest = loadCoNLLData(FULL_DATA ? "data/test-100-conll.txt" : (TINY_DATA ? "data/train-3-conll.txt" : "data/dev-100-conll.txt"));

        System.out.println("Running NER++ analysis");
        nerPlusPlus.analyze(getNERPlusPlusForClassifier(nerPlusPlusDataTrain),
                getNERPlusPlusForClassifier(nerPlusPlusDataTest),
                FULL_DATA ? "realdata/ner-plus-plus-analysis" : (TINY_DATA ? "data/tiny" : "data/train-"+trainDataSize)+"/ner-plus-plus-analysis");

        System.out.println("Running Dictionary analysis");
        dictionaryLookup.analyze(getDictionaryForClassifier(dictionaryDataTrain),
                getDictionaryForClassifier(dictionaryDataTest),
                FULL_DATA ? "realdata/dictionary-lookup-analysis" : (TINY_DATA ? "data/tiny" : "data/train-"+trainDataSize)+"/dictionary-lookup-analysis");

        System.out.println("Running Arc Existence analysis");
        arcExistence.analyze(getArcExistenceForClassifier(mstDataTrain),
                getArcExistenceForClassifier(mstDataTest),
                FULL_DATA ? "realdata/arc-existence-analysis" : (TINY_DATA ? "data/tiny" : "data/train-"+trainDataSize)+"/arc-existence-analysis");

        System.out.println("Running Arc Type analysis");
        arcType.analyze(getArcTypeForClassifier(mstDataTrain),
                getArcTypeForClassifier(mstDataTest),
                FULL_DATA ? "realdata/arc-type-analysis" : (TINY_DATA ? "data/tiny" : "data/train-"+trainDataSize)+"/arc-type-analysis");
    }

    public void testCompletePipeline() throws IOException, InterruptedException {
        System.out.println("Testing complete pipeline");
        if (TINY_DATA) {
            System.out.println("Testing tiny set");
            analyzeAMRSubset("data/train-3-subset.txt", "data/train-3-conll.txt", "data/tiny/amr-train-analysis");
            System.out.println("Testing in domain dev set");
            analyzeAMRSubset("data/test-100-subset.txt", "data/test-100-conll.txt", "data/tiny/amr-in-domain-dev-analysis");
            System.out.println("Testing real dev set");
            analyzeAMRSubset("data/dev-100-subset.txt", "data/dev-100-conll.txt", "data/tiny/amr-dev-analysis");
        }
        else {
            System.out.println("Testing training set");
            analyzeAMRSubset("data/train-"+trainDataSize+"-subset.txt", "data/train-"+trainDataSize+"-conll.txt", "data/train-"+trainDataSize+"/amr-train-analysis");
            System.out.println("Testing in domain dev set");
            analyzeAMRSubset("data/test-100-subset.txt", "data/test-100-conll.txt", "data/train-"+trainDataSize+"/amr-in-domain-dev-analysis");
            System.out.println("Testing real dev set");
            analyzeAMRSubset("data/dev-100-subset.txt", "data/dev-100-conll.txt", "data/train-"+trainDataSize+"/amr-dev-analysis");
            if (FULL_DATA) {
                System.out.println("Testing on REAL DEV set");
                analyzeAMRSubset("realdata/test-subset.txt", "realdata/test-conll.txt", "data/train-" + trainDataSize + "/amr-real-dev-analysis");
                System.out.println("Testing on REAL TEST set");
                analyzeAMRSubset("realdata/amr-release-1.0-test-proxy.txt", "realdata/release-test-conll.txt", "data/train-" + trainDataSize + "/amr-real-test-analysis");
            }
        }
    }

    private void analyzeAMRSubset(String path, String coNLLPath, String output) throws IOException, InterruptedException {
        AMR[] bank = AMRSlurp.slurp(path, AMRSlurp.Format.LDC);
        List<AMRNodeSet> mstDataTest = loadCoNLLData(coNLLPath);

        String[] sentences = new String[bank.length];
        for (int i = 0; i < bank.length; i++) {
            sentences[i] = bank[i].formatSourceTokens();
        }
        CoreNLPCache cache = new BatchCoreNLPCache(path, sentences);

        AMR[] recovered = new AMR[bank.length];
        AMR[] recoveredPerfectDict = new AMR[bank.length];
        for (int i = 0; i < bank.length; i++) {
            System.out.println("Parsing "+i+"/"+bank.length);
            Annotation annotation = cache.getAnnotation(i);
            recovered[i] = runPipeline(bank[i].sourceText, annotation);
            recoveredPerfectDict[i] = runMSTPipeline(bank[i].sourceText, annotation, mstDataTest.get(i));
        }
        cache.close();

        System.out.println("Finished analyzing");

        File out = new File(output);
        if (out.exists()) out.delete();
        out.mkdirs();

        AMRSlurp.burp(output+"/gold.txt", AMRSlurp.Format.LDC, bank, AMR.AlignmentPrinting.ALL, false);
        AMRSlurp.burp(output+"/recovered.txt", AMRSlurp.Format.LDC, recovered, AMR.AlignmentPrinting.ALL, false);
        AMRSlurp.burp(output+"/recovered-perfect-dict.txt", AMRSlurp.Format.LDC, recoveredPerfectDict, AMR.AlignmentPrinting.ALL, false);

        DumpSequence.dumpCONLL(recovered, output + "/recovered-conll.txt");
        DumpSequence.dumpCONLL(recoveredPerfectDict, output + "/recovered-perfect-dict-conll.txt");

        double smatch = Smatch.smatch(bank, recovered);
        System.out.println("SMATCH for overall "+path+" = "+smatch);
        BufferedWriter bw = new BufferedWriter(new FileWriter(output+"/smatch.txt"));
        bw.write("Smatch: "+smatch);
        bw.close();

        double smatchPerfectDict = Smatch.smatch(bank, recoveredPerfectDict);
        System.out.println("SMATCH for perfect dict "+path+" = "+smatchPerfectDict);
        bw = new BufferedWriter(new FileWriter(output+"/smatch-perfect-dict.txt"));
        bw.write("Smatch: "+smatchPerfectDict);
        bw.close();
    }

    private static void justTrainNERPlusPlus() throws IOException {
        AMRPipeline pipeline = new AMRPipeline();

        // List<LabeledSequence> nerPlusPlusDataTrain = loadSequenceData("data/train-500-seq.txt");
        List<LabeledSequence> nerPlusPlusDataTrain = loadSequenceData("realdata/release-train-seq.txt");
        List<LabeledSequence> nerPlusPlusDataTest = loadSequenceData("data/dev-100-seq.txt");

        List<Pair<Pair<LabeledSequence,Integer>,String>> dataTrain = getNERPlusPlusForClassifier(nerPlusPlusDataTrain);
        List<Pair<Pair<LabeledSequence,Integer>,String>> dataTest = getNERPlusPlusForClassifier(nerPlusPlusDataTest);

        double[] sigmas = new double[]{
                // 0.001, 0.01, 0.1, 0.3, 0.5, 0.7, 0.9, 1.0, 3.0, 10.0, 100.0
                0.5, 0.7
                // 0.1, 0.13, 0.18, 0.2, 0.3
        };

        List<Set<String>> typeClasses = new ArrayList<Set<String>>() {{

            add(new HashSet<String>(){{
                add("VERB");
                add("IDENTITY");
                add("LEMMA");
            }});

            add(new HashSet<String>(){{
                add("NONE");
                add("DICT");
            }});

        }};

        typeClasses.clear();

        for (double i : sigmas) {
            pipeline.nerPlusPlus.sigma = i;
            pipeline.nerPlusPlus.train(dataTrain, typeClasses);
            pipeline.nerPlusPlus.analyze(dataTrain, dataTest, "data/ner-plus-plus-sigma-"+(int)(i*1000));
        }
    }

    private static void justTrainArcType() throws IOException {
        AMRPipeline pipeline = new AMRPipeline();

        boolean BIG = false;

        List<AMRNodeSet> mstDataTrain = loadCoNLLData(BIG ? "realdata/release-train-conll.txt" : "data/train-400-conll.txt");
        // List<AMRNodeSet> mstDataTrain = loadCoNLLData("data/train-3-conll.txt");
        List<AMRNodeSet> mstDataTest = loadCoNLLData("data/dev-100-conll.txt");

        List<Pair<Triple<AMRNodeSet, Integer, Integer>, String>> arcTypeData = getArcTypeForClassifier(mstDataTrain);
        List<Pair<Triple<AMRNodeSet, Integer, Integer>, Boolean>> arcExistenceData = getArcExistenceForClassifier(mstDataTrain);

        /*
        int totalLen = 0;
        int totalArcs = 0;
        for (Pair<Triple<AMRNodeSet, Integer, Integer>, String> pair : data) {
            AMRNodeSet set = pair.first.first;
            SemanticGraph graph = set.annotation.get(CoreAnnotations.SentencesAnnotation.class).get(0)
                    .get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);

            int head = pair.first.second;
            int tail = pair.first.third;

            IndexedWord headIndexedWord = graph.getNodeByIndexSafe(head + 1);
            IndexedWord tailIndexedWord = graph.getNodeByIndexSafe(tail + 1);
            if (headIndexedWord == null || tailIndexedWord == null) {
            }
            else {
                List<SemanticGraphEdge> edges = graph.getShortestUndirectedPathEdges(headIndexedWord, tailIndexedWord);
                totalArcs++;
                totalLen += edges.size();
            }
        }

        System.out.println("Average len: "+((double)totalLen / totalArcs));
        */

        System.out.println("Training");

        List<Set<String>> arcClasses = new ArrayList<Set<String>>() {{

            add(new HashSet<String>(){{
                add("ARG0");
                add("ARG1");
                add("ARG2");
                add("ARG3");
                add("ARG4");
            }});

            add(new HashSet<String>(){{
                add("ARG0-of");
                add("ARG1-of");
                add("ARG2-of");
                add("ARG3-of");
            }});

            add(new HashSet<String>(){{
                add("mod");
                add("op");
                add("poss");
            }});

        }};

        arcClasses.clear();

        double[] existenceSigmas = new double[]{
                // 0.001, 0.01, 0.1, 0.3, 0.5, 0.7, 0.9, 1.0, 3.0, 10.0, 100.0
                // 0.07, 0.1, 0.13, 0.18, 0.2, 0.3
                // 0.1, 0.13, 0.18, 0.2, 0.3
                0.13
        };
        double[] typeSigmas = new double[]{
                3.0
        };

        for (int i = 0; i < existenceSigmas.length; i++) {
            pipeline.arcType.sigma = typeSigmas[i];
            // pipeline.arcType.type = LinearPipe.ClassifierType.SVM;
            pipeline.arcExistence.sigma = existenceSigmas[i];
            pipeline.arcExistence.type = LinearPipe.ClassifierType.LOGISTIC;
            pipeline.arcExistence.automaticallyReweightTrainingData = false;

            pipeline.arcType.train(arcTypeData, arcClasses);
            pipeline.arcExistence.train(arcExistenceData);

            System.out.println("Analyzing");
            System.out.println("type sigma: " + typeSigmas[i]);
            System.out.println("existence sigma: " + existenceSigmas[i]);
            try {
                pipeline.arcType.analyze(arcTypeData, getArcTypeForClassifier(mstDataTest), BIG ? "data/arc-type-big-sigma-" + (int)(pipeline.arcType.sigma * 1000) : "data/arc-type-clusters-sigma-" + (int)(pipeline.arcType.sigma * 1000));
            }
            catch (Exception e) {
                e.printStackTrace();
                System.err.println("meh");
            }
            try {
                pipeline.arcExistence.analyze(arcExistenceData, getArcExistenceForClassifier(mstDataTest), BIG ? "data/arc-existence-big-sigma-"+(int)(pipeline.arcExistence.sigma*1000) : "data/arc-existence-clusters-sigma-" + (int)(pipeline.arcExistence.sigma * 1000));
            }
            catch (Exception e) {
                e.printStackTrace();
                System.err.println("meh");
            }
            System.out.println("Done");

            /*
            LinearClassifier<String,String> lin = (LinearClassifier<String, String>) pipeline.arcType.classifiers.get(0);
            lin.dump(IOUtils.getPrintWriter("data/arc-type-clusters-sigma-" + (int)(pipeline.arcType.sigma * 1000)));
            */
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        justTrainArcType();
        // justTrainNERPlusPlus();

        /*
        AMRPipeline pipeline = new AMRPipeline();
        pipeline.trainStages();
        pipeline.analyzeStages();
        pipeline.testCompletePipeline();
        */
    }

    /////////////////////////////////////////////////////
    // LOADERS
    /////////////////////////////////////////////////////

    public static List<LabeledSequence> loadSequenceData(String path) throws IOException {
        List<LabeledSequence> seqList = new ArrayList<>();

        BufferedReader br = new BufferedReader(new FileReader(path));

        List<String> tokens = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        // Read the TSV file

        String line;
        while ((line = br.readLine()) != null) {
            if (line.length() == 0) {
                LabeledSequence seq = new LabeledSequence();
                seq.tokens = tokens.toArray(new String[tokens.size()]);
                seq.labels = labels.toArray(new String[labels.size()]);
                tokens.clear();
                labels.clear();
                seqList.add(seq);
            }
            else {
                String[] parts = line.split("\t");
                assert(parts.length == 2);
                tokens.add(parts[0]);
                labels.add(parts[1]);
            }
        }
        if (tokens.size() > 0) {
            LabeledSequence seq = new LabeledSequence();
            seq.tokens = tokens.toArray(new String[tokens.size()]);
            seq.labels = labels.toArray(new String[labels.size()]);
            tokens.clear();
            labels.clear();
            seqList.add(seq);
        }

        // Do or load the annotations

        String[] sentences = new String[seqList.size()];
        for (int i = 0; i < seqList.size(); i++) {
            sentences[i] = seqList.get(i).formatTokens();
        }
        CoreNLPCache coreNLPCache = new BatchCoreNLPCache(path, sentences);
        for (int i = 0; i < seqList.size(); i++) {
            seqList.get(i).annotation = coreNLPCache.getAnnotation(i);
        }

        br.close();

        return seqList;
    }

    public static List<LabeledSequence> loadManygenData(String path) throws IOException {
        List<LabeledSequence> seqList = new ArrayList<>();

        BufferedReader br = new BufferedReader(new FileReader(path));

        // Read the TSV file

        LabeledSequence currentSeq = null;
        int stage = 0;

        String line;
        while ((line = br.readLine()) != null) {

            stage ++;

            if (line.length() == 0) {
                if (currentSeq != null) {
                    seqList.add(currentSeq);
                }
                currentSeq = null;
                stage = 0;
            }

            else if (stage == 1) {
                assert(currentSeq == null);

                currentSeq = new LabeledSequence();
                currentSeq.labels = new String[0];
                currentSeq.tokens = line.split(" ");
                currentSeq.labels = new String[currentSeq.tokens.length];
            }
            else {
                String[] parts = line.split("\t");
                assert(parts.length == 3);
                assert(currentSeq != null);

                // Format layout:
                // AMR, START_INDEX, END_INDEX

                String amr = parts[0];

                int startIndex = Integer.parseInt(parts[1]);
                int endIndex = Integer.parseInt(parts[2]);
                for (int i = startIndex; i <= endIndex; i++) {
                    currentSeq.labels[i] = amr;
                }
            }
        }
        if (currentSeq != null) {
            seqList.add(currentSeq);
        }

        // Do or load the annotations

        String[] sentences = new String[seqList.size()];
        for (int i = 0; i < seqList.size(); i++) {
            sentences[i] = seqList.get(i).formatTokens();
        }

        String[] dedupedSentencesLong = new String[sentences.length];
        Map<String,Integer> stringToOffsetMap = new HashMap<>();
        for (String s : sentences) {
            if (!stringToOffsetMap.containsKey(s)) {
                int i = stringToOffsetMap.size();
                stringToOffsetMap.put(s, i);
                dedupedSentencesLong[i] = s;
            }
        }
        String[] dedupedSentences = new String[stringToOffsetMap.size()];
        System.arraycopy(dedupedSentencesLong, 0, dedupedSentences, 0, stringToOffsetMap.size());

        CoreNLPCache coreNLPCache = new BatchCoreNLPCache(path, dedupedSentences);
        for (int i = 0; i < seqList.size(); i++) {
            seqList.get(i).annotation = coreNLPCache.getAnnotation(stringToOffsetMap.get(sentences[i]));
        }

        br.close();

        return seqList;
    }

    public static List<AMRNodeSet> loadCoNLLData(String path) throws IOException {
        List<AMRNodeSet> setList = new ArrayList<>();

        BufferedReader br = new BufferedReader(new FileReader(path));

        // Read the TSV file

        int stage = 0;

        AMRNodeSet currentNodeSet = null;

        String line;
        while ((line = br.readLine()) != null) {

            stage++;

            if (line.length() == 0) {
                // a newline to flush out old stuff
                stage = 0;
                if (currentNodeSet != null) {
                    for (int i = 0; i < currentNodeSet.nodes.length; i++) {
                        for (int j = 0; j < currentNodeSet.nodes.length; j++) {
                            if (i == j) continue;
                            if (currentNodeSet.nodes[i] == null || currentNodeSet.nodes[j] == null) continue;
                            if (currentNodeSet.nodes[i].alignment == currentNodeSet.nodes[j].alignment) {
                                currentNodeSet.forcedArcs[i][j] = currentNodeSet.correctArcs[i][j];
                                currentNodeSet.correctArcs[i][j] = null;
                            }
                        }
                    }
                    setList.add(currentNodeSet);
                }
                currentNodeSet = null;
            }

            else if (stage == 1) {
                // read a sentence
                assert(currentNodeSet == null);
                currentNodeSet = new AMRNodeSet();

                String[] parts = line.split("\t");
                int length = 0;
                try {
                    length = Integer.parseInt(parts[0]);
                }
                catch (Exception e) {
                    System.out.println(line);
                    System.out.println(Arrays.toString(parts));
                    System.out.println("Break");
                }

                currentNodeSet.tokens = parts[1].split(" ");

                currentNodeSet.correctArcs = new String[length+1][length+1];
                currentNodeSet.forcedArcs = new String[length+1][length+1];
                currentNodeSet.nodes = new AMR.Node[length+1];
            }

            else {
                String[] parts = line.split("\t");
                assert(parts.length == 5);

                // Format layout:
                // NODE_INDEX, NODE, PARENT_INDEX, ARC_NAME, ALIGNMENT

                int index = Integer.parseInt(parts[0]);
                String node = parts[1];
                int parentIndex = Integer.parseInt(parts[2]);
                String arcName = parts[3];
                int alignment = Integer.parseInt(parts[4]);

                // Parse out the node

                AMR.Node parsedNode;
                if (node.startsWith("\"")) {
                    String dequoted = node.substring(1, node.length()-1);
                    parsedNode = new AMR.Node(""+dequoted.toLowerCase().charAt(0), dequoted, AMR.NodeType.QUOTE);
                }
                else if (node.startsWith("(")) {
                    String[] debracedParts = node.substring(1, node.length()-1).split("/");
                    parsedNode = new AMR.Node(debracedParts[0], debracedParts[1], AMR.NodeType.ENTITY);
                }
                else {
                    parsedNode = new AMR.Node("x", node, AMR.NodeType.VALUE);
                }
                parsedNode.alignment = alignment;

                assert(currentNodeSet != null);

                currentNodeSet.nodes[index] = parsedNode;
                currentNodeSet.correctArcs[parentIndex][index] = arcName;
            }
        }
        if (currentNodeSet != null) {
            setList.add(currentNodeSet);
        }

        String[] sentences = new String[setList.size()];
        for (int i = 0; i < setList.size(); i++) {
            sentences[i] = setList.get(i).formatTokens();
        }
        CoreNLPCache coreNLPCache = new BatchCoreNLPCache(path, sentences);
        for (int i = 0; i < setList.size(); i++) {
            setList.get(i).annotation = coreNLPCache.getAnnotation(i);
        }

        return setList;
    }

    /////////////////////////////////////////////////////
    // DATA TRANSFORMS
    /////////////////////////////////////////////////////

    public static List<Pair<Pair<LabeledSequence,Integer>,String>> getNERPlusPlusForClassifier(
            List<LabeledSequence> nerPlusPlusData) {
        List<Pair<Pair<LabeledSequence,Integer>,String>> nerPlusPlusTrainingData = new ArrayList<>();
        for (LabeledSequence seq : nerPlusPlusData) {
            for (int i = 0; i < seq.tokens.length; i++) {
                Pair<Pair<LabeledSequence,Integer>,String> pair = new Pair<>();
                pair.first = new Pair<>(seq, i);
                pair.second = seq.labels[i];
                nerPlusPlusTrainingData.add(pair);
            }
        }
        return nerPlusPlusTrainingData;
    }

    public static List<Pair<Triple<LabeledSequence,Integer,Integer>,String>> getDictionaryForClassifier(
        List<LabeledSequence> dictionaryData) {
        List<Pair<Triple<LabeledSequence,Integer,Integer>,String>> dictionaryTrainingData = new ArrayList<>();
        for (LabeledSequence seq : dictionaryData) {
            int min = seq.tokens.length;
            int max = 0;
            for (int i = 0; i < seq.tokens.length; i++) {
                if (seq.labels[i] != null) {
                    if (i < min) min = i;
                    if (i > max) max = i;
                }
            }
            assert(min <= max);

            Pair<Triple<LabeledSequence,Integer,Integer>,String> pair = new Pair<>();
            pair.first = new Triple<>(seq, min, max);
            pair.second = seq.labels[min];
            dictionaryTrainingData.add(pair);
        }
        return dictionaryTrainingData;
    }

    public static List<Pair<Triple<AMRNodeSet,Integer,Integer>,Boolean>> getArcExistenceForClassifier(
            List<AMRNodeSet> mstData) {

        List<Pair<Triple<AMRNodeSet,Integer,Integer>,Boolean>> arcExistenceTrue = new ArrayList<>();
        List<Pair<Triple<AMRNodeSet,Integer,Integer>,Boolean>> arcExistenceFalse = new ArrayList<>();

        for (AMRNodeSet set : mstData) {
            for (int i = 0; i < set.correctArcs.length; i++) {
                if (i != 0 && set.nodes[i] == null) continue;
                for (int j = 1; j < set.correctArcs[i].length; j++) {
                    if (set.nodes[j] == null) continue;
                    boolean arcExists = set.correctArcs[i][j] != null;
                    if (arcExists) {
                        arcExistenceTrue.add(new Pair<>(new Triple<>(set, i, j), true));
                    }
                    else {
                        arcExistenceFalse.add(new Pair<>(new Triple<>(set, i, j), false));
                    }
                }
            }
        }

        int numTrueClones = Math.max(1, arcExistenceFalse.size() / arcExistenceTrue.size());

        List<Pair<Triple<AMRNodeSet,Integer,Integer>,Boolean>> arcExistenceData = new ArrayList<>();
        arcExistenceData.addAll(arcExistenceFalse);
        for (int i = 0; i < numTrueClones; i++) {
            arcExistenceData.addAll(arcExistenceTrue);
        }

        return arcExistenceData;
    }

    public static List<Pair<Triple<AMRNodeSet,Integer,Integer>,String>> getArcTypeForClassifier(
            List<AMRNodeSet> mstData) {
        List<Pair<Triple<AMRNodeSet,Integer,Integer>,String>> arcTypeData = new ArrayList<>();
        for (AMRNodeSet set : mstData) {
            for (int i = 0; i < set.correctArcs.length; i++) {
                if (i != 0 && set.nodes[i] == null) continue;
                for (int j = 1; j < set.correctArcs[i].length; j++) {
                    if (set.nodes[j] == null) continue;
                    boolean arcExists = set.correctArcs[i][j] != null;
                    if (arcExists) {
                        arcTypeData.add(new Pair<>(new Triple<>(set, i, j), set.correctArcs[i][j]));
                    }
                }
            }
        }
        return arcTypeData;
    }

    /////////////////////////////////////////////////////
    // ERROR ANALYSIS
    /////////////////////////////////////////////////////

    public static void writeNerPlusPlusContext(Pair<LabeledSequence,Integer> pair, BufferedWriter bw) {
        LabeledSequence seq = pair.first;
        for (int i = 0; i < seq.tokens.length; i++) {
            try {
                if (i != 0) bw.write(" ");
                if (i == pair.second) bw.write("[");
                bw.write(seq.tokens[i]);
                if (i == pair.second) bw.write("]");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void writeDictionaryContext(Triple<LabeledSequence,Integer,Integer> triple, BufferedWriter bw) {
        LabeledSequence seq = triple.first;
        for (int i = 0; i < seq.tokens.length; i++) {
            try {
                if (i != 0) bw.write(" ");
                if (i == triple.second) bw.write("[");
                bw.write(seq.tokens[i]);
                if (i == triple.third) bw.write("]");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void writeArcContext(Triple<AMRNodeSet, Integer, Integer> triple, BufferedWriter bw) {
        AMRNodeSet set = triple.first;

        AMR.Node source = set.nodes[triple.second];
        AMR.Node sink = set.nodes[triple.third];

        if (source == null || sink == null) return;

        try {
            if (triple.second == 0) {
                bw.write("SOURCE:ROOT");
            }
            else {
                bw.write("SOURCE: " + source.toString() + "=\"" + set.tokens[source.alignment] + "\"\n");
            }
            bw.write("SINK: "+sink.toString()+"=\""+set.tokens[sink.alignment]+"\"\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < set.tokens.length; i++) {
            try {
                if (i != 0) bw.write(" ");
                if (i == set.nodes[triple.second].alignment) bw.write("<<");
                if (i == set.nodes[triple.third].alignment) bw.write(">>");
                bw.write(set.tokens[i]);
                if (i == set.nodes[triple.second].alignment) bw.write(">>");
                if (i == set.nodes[triple.third].alignment) bw.write("<<");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            bw.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }


        for (int i = 1; i < set.nodes.length; i++) {
            if (i == triple.second || i == triple.third) continue;
            try {
                AMR.Node node = set.nodes[i];
                bw.write(node.toString()+"=\""+set.tokens[node.alignment]+"\"\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
