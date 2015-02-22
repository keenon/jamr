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

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

/**
 * Created by keenon on 2/16/15.
 */
public class SequenceSystem {

    AMRPipeline pipeline = new AMRPipeline();
    static boolean DEBUG = false;

    public static void main(String[] args) {
        SequenceSystem system = new SequenceSystem();
        System.out.println("China will sign a treaty with America on Friday June 3");
        System.out.println(system.getSpans("China will sign a treaty with America on Friday June 3").toString());
        System.out.println("2009-01-02");
        System.out.println(system.getSpans("2009-01-02").toString());
        System.out.println("I want to start dating other people and my son's father says if I do he will have nothing to do with his son.");
        System.out.println(system.getSpans("I want to start dating other people and my son's father says if I do he will have nothing to do with his son.").toString());
        System.out.println("A newspaper report on January 1 , 2008 that Iran hanged two convicted drug traffickers in the southeastern city of Zahedan .");
        System.out.println(system.getSpans("A newspaper report on January 1 , 2008 that Iran hanged two convicted drug traffickers in the southeastern city of Zahedan .").toString());
    }

    public SequenceSystem() {
        try {
            trainSystems();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void getNERSystemForData(String dataPath) throws IOException, ClassNotFoundException {
        String classifierPath = dataPath.replaceAll(".txt", "-classifier.ser.gz");
        File f = new File(classifierPath);
        if (!f.exists()) {
            if (DEBUG) System.out.println("Loading sequence data");
            List<LabeledSequence> nerPlusPlusData = AMRPipeline.loadSequenceData(dataPath);
            if (DEBUG) System.out.println("Generating NER++ training data");
            List<Pair<Pair<LabeledSequence,Integer>,String>> nerPlusPlusForClassifier = AMRPipeline.getNERPlusPlusForClassifier(nerPlusPlusData);
            // pipeline.nerPlusPlus.sigma = 0.5;
            pipeline.nerPlusPlus.sigma = 1.0;
            if (DEBUG) System.out.println("Training NER++ classifier");
            pipeline.nerPlusPlus.train(nerPlusPlusForClassifier);
            if (DEBUG) System.out.println("Saving NER++ classifier");
            pipeline.nerPlusPlus.writeToFile(classifierPath);
        }
        else {
            if (DEBUG) System.out.println("Reading NER++ classifier");
            pipeline.nerPlusPlus.readFromFile(classifierPath);
        }
    }

    public void getManygenSystemForData(String dataPath) throws IOException, ClassNotFoundException {
        String classifierPath = dataPath.replaceAll(".txt", "-classifier.ser.gz");
        File f = new File(classifierPath);
        if (!f.exists()) {
            if (DEBUG) System.out.println("Loading manygen data");
            List<LabeledSequence> dictionaryData = AMRPipeline.loadManygenData(dataPath);

            if (DEBUG) System.out.println("Generating manygen training data");
            List<Pair<Triple<LabeledSequence,Integer,Integer>,String>> dictionaryForClassifier = AMRPipeline.getDictionaryForClassifier(dictionaryData);
            pipeline.dictionaryLookup.type = LinearPipe.ClassifierType.BAYESIAN;
            if (DEBUG) System.out.println("Training manygen classifier");
            pipeline.dictionaryLookup.train(dictionaryForClassifier);
            if (DEBUG) System.out.println("Saving manygen classifier");

            pipeline.dictionaryLookup.writeToFile(classifierPath);
        }
        else {
            if (DEBUG) System.out.println("Reading manygen classifier");
            pipeline.dictionaryLookup.readFromFile(classifierPath);
        }
    }

    public void trainSystems() throws IOException, ClassNotFoundException {
        boolean trainOnSmallerSet = false;

        getNERSystemForData(trainOnSmallerSet ? "data/train-400-seq.txt" : "data/deft-train-seq.txt");
        getManygenSystemForData("data/deft-train-manygen.txt");
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

    StanfordCoreNLP cachedFallbackCore = null;

    public StanfordCoreNLP getCachedFallbackCoreNLP() {
        if (cachedFallbackCore == null) {
            Properties props = new Properties();
            props.put("annotators", "tokenize, ssplit, pos, lemma, ner, regexner1, regexner2, parse, dcoref");
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

            cachedFallbackCore = new StanfordCoreNLP(props, false);
        }
        return cachedFallbackCore;
    }

    public Set<Triple<Integer,Integer,String>> getSpans(String sentence) {
        Annotation annotation = new Annotation(sentence);
        try {
            getCoreNLP().annotate(annotation);
        }
        catch (Exception e) {
            getCachedFallbackCoreNLP().annotate(annotation);
        }

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
            amrText = amrText.replaceAll("op[1-9]","op");
            spans.add(new Triple<>(minAlignment, maxAlignment, amrText));
        }

        return spans;
    }
}
