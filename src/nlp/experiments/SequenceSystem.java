package nlp.experiments;

import edu.stanford.nlp.curator.CuratorAnnotations;
import edu.stanford.nlp.curator.CuratorClient;
import edu.stanford.nlp.curator.PredicateArgumentAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import nlp.stamr.AMR;
import nlp.word2vec.Word2VecLoader;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

/**
 * Created by keenon on 2/16/15.
 */
public class SequenceSystem {

    AMRPipeline pipeline = new AMRPipeline();

    public SequenceSystem() {
        try {
            trainSystems();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void trainSystems() throws IOException {
        List<LabeledSequence> nerPlusPlusData = AMRPipeline.loadSequenceData("realdata/release-train-seq.txt");
        List<LabeledSequence> dictionaryData = AMRPipeline.loadManygenData("realdata/train-manygen.txt");

        pipeline.nerPlusPlus.sigma = 0.5;
        pipeline.nerPlusPlus.train(AMRPipeline.getNERPlusPlusForClassifier(nerPlusPlusData));

        pipeline.dictionaryLookup.type = LinearPipe.ClassifierType.BAYESIAN;
        pipeline.dictionaryLookup.train(AMRPipeline.getDictionaryForClassifier(dictionaryData));
    }

    StanfordCoreNLP cachedCore = null;

    public StanfordCoreNLP getCoreNLP() {
        if (cachedCore == null) {
            Properties props = new Properties();
            props.put("annotators", "tokenize, ssplit, pos, lemma, ner, regexner1, regexner2, parse, dcoref, srl, nom, prep");
            props.put("curator.host", "localhost"); // point to the curator host
            props.put("curator.port", "9010"); // point to the curator port

            props.put("customAnnotatorClass.regexner1", "edu.stanford.nlp.pipeline.TokensRegexNERAnnotator");
            props.put("regexner1.mapping", "data/kbp_regexner_mapping_nocase.tab");
            props.put("regexner1.validpospattern", "^(NN|JJ).*");
            props.put("regexner1.ignorecase", "true");
            props.put("regexner1.noDefaultOverwriteLabels", "CITY");

            props.put("customAnnotatorClass.regexner2", "edu.stanford.nlp.pipeline.TokensRegexNERAnnotator");
            props.put("regexner2.mapping", "data/kbp_regexner_mapping.tab");
            props.put("regexner2.ignorecase", "false");
            props.put("regexner2.noDefaultOverwriteLabels", "CITY");

            cachedCore = new CuratorClient(props, false);
        }
        return cachedCore;
    }

    public Set<Triple<Integer,Integer,String>> getSpans(String sentence) {
        Annotation annotation = new Annotation(sentence);
        getCoreNLP().annotate(annotation);

        List<CoreLabel> tokensList = annotation.get(CoreAnnotations.TokensAnnotation.class);
        String[] tokens = new String[tokensList.size()];
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = tokensList.get(i).word();
        }
        List<AMR> gen = pipeline.predictClusters(tokens, annotation);

        Set<Triple<Integer,Integer,String>> spans = new HashSet<>();
        for (AMR cluster : gen) {
            int minAlignment = Integer.MAX_VALUE;
            int maxAlignment = Integer.MIN_VALUE;
            for (AMR.Node node : cluster.nodes) {
                if (node.alignment < minAlignment) minAlignment = node.alignment;
                if (node.alignment > maxAlignment) maxAlignment = node.alignment;
            }
            String amrText = cluster.toStringForSmatch();
            spans.add(new Triple<>(minAlignment, maxAlignment, amrText));
        }

        return spans;
    }
}
