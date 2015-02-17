package nlp.stamr.annotation;

import edu.stanford.nlp.curator.CuratorAnnotations;
import edu.stanford.nlp.curator.PredicateArgumentAnnotation;
import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

import java.io.Serializable;
import java.util.*;

/**
 * Gives a clean interface for getting at the kinds of strings that we often want from an Annotation object.
 */
public class AnnotationWrapper implements Serializable {

    public Annotation annotation;
    public AnnotationWrapper(Annotation annotation) {
        this.annotation = annotation;
    }

    public int getNumSentences() {
        try {
            List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
            if (sentences != null) {
                return sentences.size();
            }
        }
        catch (Exception e) {
            return 0;
        }
        return 1;
    }

    public CoreMap getSenteceAnnotation(int index) {
        return annotation.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    }

    public String getSequentialTokens(Collection<Integer> tokens) {
        List<Integer> inOrder = new ArrayList<Integer>();
        inOrder.addAll(tokens);
        inOrder.sort(new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return o1.compareTo(o2);
            }
        });
        String s = "";
        for (int i : inOrder) {
            if (s.length() > 0) s += " ";
            s = s + getTokenAtIndex(i);
        }
        return s;
    }

    public List<String> splitSentenceBoundaries() {
        List<String> sentences = new ArrayList<String>();
        for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
            sentences.add(sentence.toString());
        }
        return sentences;
    }

    public Pair<Integer,Integer> getSentenceIndexForToken(int index) {
        int i = 0;
        int cursor = getSentenceLength(0);
        while ((i < getNumSentences()) && (index < cursor)) {
            i++;
            cursor = getSentenceLength(i);
        }
        return new Pair<Integer, Integer>(i, index - (cursor-getSentenceLength(i)));
    }

    public Tree getPTBAnnotation(int sentence) {
        return getSenteceAnnotation(sentence).get(TreeCoreAnnotations.TreeAnnotation.class);
    }

    public SemanticGraph getCollapsedDependenciesAnnotation(int sentence) {
        return getSenteceAnnotation(sentence).get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
    }

    public SemanticGraph getBasicDependenciesAnnotation(int sentence) {
        return getSenteceAnnotation(sentence).get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    }

    public String getDependencyParent(int i) {
        SemanticGraph graph = getBasicDependenciesAnnotation(0);
        IndexedWord token = getIndexedWordAtIndex(i);
        List<SemanticGraphEdge> edges = graph.getIncomingEdgesSorted(token);
        if (edges.size() == 0) return "ROOT";
        else return edges.iterator().next().getRelation().getShortName();
    }

    public int getSentenceLength(int i) {
        return getSenteceAnnotation(i).get(CoreAnnotations.TokensAnnotation.class).size();
    }

    public int getLength() {
        return annotation.get(CoreAnnotations.TokensAnnotation.class).size();
    }

    public String getTokenAtIndex(int i) {
        return annotation.get(CoreAnnotations.TokensAnnotation.class).get(i).originalText();
    }

    private PredicateArgumentAnnotation.AnnotationSpan getSRLSpanIfExists(int i) {
        PredicateArgumentAnnotation srl = annotation.get(CuratorAnnotations.PropBankSRLAnnotation.class);
        if (srl == null) return null; // If we have no SRL on this example, then oh well
        for (PredicateArgumentAnnotation.AnnotationSpan span : srl.getPredicates()) {
            if ((span.startToken <= i) && (span.endToken >= i + 1)) {
                return span;
            }
        }
        return null;
    }

    private PredicateArgumentAnnotation.AnnotationSpan getNomSpanIfExists(int i) {
        PredicateArgumentAnnotation srl = annotation.get(CuratorAnnotations.NomBankSRLAnnotation.class);
        if (srl == null) return null; // If we have no SRL on this example, then oh well
        for (PredicateArgumentAnnotation.AnnotationSpan span : srl.getPredicates()) {
            if ((span.startToken <= i) && (span.endToken >= i + 1)) {
                return span;
            }
        }
        return null;
    }

    private PredicateArgumentAnnotation.AnnotationSpan getPrepSpanIfExists(int i) {
        PredicateArgumentAnnotation srl = annotation.get(CuratorAnnotations.PrepSRLAnnotation.class);
        if (srl == null) return null; // If we have no SRL on this example, then oh well
        for (PredicateArgumentAnnotation.AnnotationSpan span : srl.getPredicates()) {
            if ((span.startToken <= i) && (span.endToken >= i + 1)) {
                return span;
            }
        }
        return null;
    }

    public int getDependencyRootOfSet(Collection<Integer> set) {
        int minDistance = 10000;
        int minDep = 0;
        for (int i : set) {
            int dist = getDependencyDistanceToRoot(i);
            if (dist < minDistance) {
                minDistance = dist;
                minDep = i;
            }
        }
        return minDep;
    }

    public String getNERSenseAtIndex(int i) {
        IndexedWord word = getIndexedWordAtIndex(i);
        if (word != null) {
            return word.get(CoreAnnotations.NamedEntityTagAnnotation.class);
        }
        return ".";
    }

    public String getSRLSenseAtIndex(int i) {
        PredicateArgumentAnnotation.AnnotationSpan span = getSRLSpanIfExists(i);
        if (span != null)
            return span.getAttribute("predicate") + "-" + span.getAttribute("sense");

        return "";
    }

    public String getNomSenseAtIndex(int i) {
        PredicateArgumentAnnotation.AnnotationSpan span = getNomSpanIfExists(i);
        if (span != null)
            return span.getAttribute("predicate") + "-" + span.getAttribute("sense");

        return "";
    }

    public String getPrepSenseAtIndex(int i) {
        PredicateArgumentAnnotation.AnnotationSpan span = getPrepSpanIfExists(i);
        if (span != null)
            return span.getAttribute("predicate") + "-" + span.getAttribute("sense");

        return "";
    }

    public Set<Integer> getCorefNodes(int i) {
        Set<Integer> coreferentNodes = new HashSet<Integer>();
        Map<Integer,CorefChain> chains = annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
        if (chains.containsKey(i+1)) {
            CorefChain chain = chains.get(i+1);
            for (CorefChain.CorefMention mention : chain.getMentionsInTextualOrder()) {
                if ((mention.startIndex == mention.endIndex-1) && (mention.startIndex != (i-1))) {
                    coreferentNodes.add(mention.startIndex-1);
                }
            }
        }
        return coreferentNodes;
    }

    public String getSRLArcFromTo(int head, int tail) {
        PredicateArgumentAnnotation srl = annotation.get(CuratorAnnotations.PropBankSRLAnnotation.class);
        PredicateArgumentAnnotation.AnnotationSpan span = getSRLSpanIfExists(head);
        if (span != null) {
            for (PredicateArgumentAnnotation.AnnotationSpan argSpan : srl.getArguments(span)) {
                if ((argSpan.startToken <= tail) && (argSpan.endToken >= tail + 1)) {
                    return argSpan.label;
                }
            }
        }
        return "";
    }

    public String[] getTokens() {
        List<String> tokens = new ArrayList<String>();
        for (CoreLabel label : annotation.get(CoreAnnotations.TokensAnnotation.class)) {
            tokens.add(label.originalText());
        }
        return tokens.toArray(new String[tokens.size()]);
    }

    Map<Integer,IndexedWord> indexedWordCache = new HashMap<Integer, IndexedWord>();

    public IndexedWord getIndexedWordAtIndex(int i) {
        if (!indexedWordCache.containsKey(i)) {
            Pair<Integer,Integer> sentenceIndex = getSentenceIndexForToken(i);
            indexedWordCache.put(i,getBasicDependenciesAnnotation(sentenceIndex.first).getNodeByIndexSafe(sentenceIndex.second + 1));
        }
        return indexedWordCache.get(i);
    }

    public String getPOSTagAtIndex(int i) {
        if (annotation == null) return "?";
        IndexedWord word = getIndexedWordAtIndex(i);
        if (word != null) {
            return word.get(CoreAnnotations.PartOfSpeechAnnotation.class);
        }
        return ".";
    }

    public String getTimeAnnotationAtIndex(int i) {
        if (annotation == null) return "?";
        IndexedWord word = getIndexedWordAtIndex(i);
        if (word != null) {
            return word.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
        }
        return ".";
    }

    public String getLemmaAtIndex(int i) {
        IndexedWord word = getIndexedWordAtIndex(i);
        if (word != null) {
            return word.get(CoreAnnotations.LemmaAnnotation.class);
        }
        return "?";
    }

    // Cacheing for the dependency paths

    Map<Pair<Integer,Integer>,String> dependencyPathCache = new HashMap<Pair<Integer, Integer>, String>();
    Map<Pair<Integer,Integer>,Integer> dependencyPathLengthCache = new HashMap<Pair<Integer, Integer>, Integer>();

    Map<Pair<Integer,Integer>,List<SemanticGraphEdge>> dependencyPathEdgeCache = new HashMap<Pair<Integer, Integer>, List<SemanticGraphEdge>>();
    Map<Integer,List<IndexedWord>> dependencyPathRootCache = new HashMap<Integer, List<IndexedWord>>();

    Map<Integer,Integer> dependencyChildrenCache = new HashMap<Integer, Integer>();

    public int getDependencyDistanceBetweenNodes(int start, int end) {
        Pair<Integer,Integer> pair = new Pair<Integer, Integer>(start,end);

        if (!dependencyPathLengthCache.containsKey(pair)) {
            if (!dependencyPathEdgeCache.containsKey(pair)) {
                IndexedWord startWord = getIndexedWordAtIndex(start);
                IndexedWord endWord = getIndexedWordAtIndex(end);

                Pair<Integer,Integer> startSentenceOffset = getSentenceIndexForToken(start);
                Pair<Integer,Integer> endSentenceOffset = getSentenceIndexForToken(end);

                if (!startSentenceOffset.first.equals(endSentenceOffset.first)) return -1;
                if ((startWord == null) || (endWord == null)) return -1;
                dependencyPathEdgeCache.put(new Pair<Integer, Integer>(start, end), getBasicDependenciesAnnotation(startSentenceOffset.first).getShortestUndirectedPathEdges(startWord, endWord));
            }
            List<SemanticGraphEdge> edges = dependencyPathEdgeCache.get(new Pair<Integer, Integer>(start, end));
            int count = 0;
            if (edges == null) count = -1;
            else {
                for (SemanticGraphEdge edge : edges) {
                    if (!edge.getRelation().getShortName().equals("nn")) {
                        count++;
                    }
                }
            }
            dependencyPathLengthCache.put(pair,count);
        }

        return dependencyPathLengthCache.get(pair);
    }

    public int numDependencyChildren(int start) {
        if (!dependencyChildrenCache.containsKey(start)) {
            Pair<Integer, Integer> startSentenceOffset = getSentenceIndexForToken(start);
            dependencyChildrenCache.put(start, countDescendants(getIndexedWordAtIndex(start), getBasicDependenciesAnnotation(startSentenceOffset.first), new HashSet<IndexedWord>()));
        }
        return dependencyChildrenCache.get(start);
    }

    private int countDescendants(IndexedWord word, SemanticGraph graph, Set<IndexedWord> visited) {
        visited.add(word);
        int sum = 0;
        if (word == null) return sum;

        for (IndexedWord descendant : graph.getChildren(word)) {
            if (!visited.contains(descendant))
                sum += countDescendants(descendant, graph, visited) + 1;
        }
        return sum;
    }

    public int getTokenWithDependencyPathFromMe(int me, String path) {
        List<Integer> set = getTokensWithDependencyPathFromMe(me, path);
        if (set.size() > 0) return set.get(0);
        return -1;
    }

    public List<Integer> getTokensWithDependencyPathFromMe(int me, String path) {
        List<Integer> tokens = new ArrayList<Integer>();
        for (int i = 0; i < getLength(); i++) {
            if (i == me) continue;
            if (getDependencyPathBetweenNodes(me, i).equals(path)) tokens.add(i);
        }
        return tokens;
    }

    public int getDependencyDistanceToRoot(int start) {
        if (!dependencyPathRootCache.containsKey(start)) {
            assert(start < getLength());
            IndexedWord startWord = getIndexedWordAtIndex(start);
            if (startWord == null) return -1;
            Pair<Integer,Integer> startSentenceOffset = getSentenceIndexForToken(start);
            dependencyPathRootCache.put(start, getBasicDependenciesAnnotation(startSentenceOffset.first).getPathToRoot(startWord));
        }
        List<IndexedWord> startToRoot = dependencyPathRootCache.get(start);
        if (startToRoot == null) return -1;
        return startToRoot.size();
    }

    public String getDependencyPathBetweenNodes(int start, int end) {
        Pair<Integer,Integer> pair = new Pair<Integer, Integer>(start,end);
        if (!dependencyPathCache.containsKey(pair)) {
            IndexedWord startWord = getIndexedWordAtIndex(start);
            IndexedWord endWord = getIndexedWordAtIndex(end);

            if ((startWord == null) || (endWord == null)) return "?";

            Pair<Integer,Integer> startSentenceOffset = getSentenceIndexForToken(start);
            Pair<Integer,Integer> endSentenceOffset = getSentenceIndexForToken(end);

            if (!startSentenceOffset.first.equals(endSentenceOffset.first)) return "?";

            if (!dependencyPathEdgeCache.containsKey(new Pair<Integer, Integer>(start,end))) {

                dependencyPathEdgeCache.put(new Pair<Integer,Integer>(start,end),getBasicDependenciesAnnotation(startSentenceOffset.first).getShortestUndirectedPathEdges(startWord, endWord));
            }
            List<SemanticGraphEdge> edges = dependencyPathEdgeCache.get(new Pair<Integer, Integer>(start, end));

            if (!dependencyPathRootCache.containsKey(start)) {
                dependencyPathRootCache.put(start, getBasicDependenciesAnnotation(startSentenceOffset.first).getPathToRoot(startWord));
            }
            List<IndexedWord> startToRoot = dependencyPathRootCache.get(start);

            if ((edges == null) || (startToRoot == null)) return "?";

            startToRoot.add(startWord);

            StringBuilder sb = new StringBuilder();
            for (SemanticGraphEdge edge : edges) {
                if (startToRoot.contains(edge.getDependent())) {
                    sb.append("<");
                } else {
                    sb.append(">");
                }
                sb.append(edge.getRelation().getShortName());
            }
            dependencyPathCache.put(pair,sb.toString());
        }
        return dependencyPathCache.get(pair);
    }
}
