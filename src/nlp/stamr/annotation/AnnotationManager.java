package nlp.stamr.annotation;

import edu.stanford.nlp.curator.CuratorClient;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import nlp.stamr.AMR;
import nlp.stamr.AMRConstants;
import nlp.stamr.utils.TimingEstimator;
import edu.stanford.nlp.util.CoreMap;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Deals with creating, saving, and loading annotations for AMR banks.
 */
public class AnnotationManager {

    StanfordCoreNLP splitter = null;
    StanfordCoreNLP pipeline = null;
    StanfordCoreNLP fallbackPipeline = null;

    private synchronized StanfordCoreNLP getPipeline() {
        if (pipeline == null) {
            // creates a StanfordCoreNLP object, with POS tagging, lemmatization, NER, parsing, coreference resolution, SRL, and Nominalization

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

            pipeline = new CuratorClient(props, false);
        }
        return pipeline;
    }

    private synchronized StanfordCoreNLP getFallbackPipeline() {
        if (fallbackPipeline == null) {
            // creates a StanfordCoreNLP object, with POS tagging, lemmatization, NER, parsing, coreference resolution

            Properties props = new Properties();
            props.put("annotators", "tokenize, ssplit, pos, lemma, ner, regexner1, regexner2, parse, dcoref");

            props.put("customAnnotatorClass.regexner1", "edu.stanford.nlp.pipeline.TokensRegexNERAnnotator");
            props.put("regexner1.mapping", "data/kbp_regexner_mapping_nocase.tab");
            props.put("regexner1.validpospattern", "^(NN|JJ).*");
            props.put("regexner1.ignorecase", "true");
            props.put("regexner1.noDefaultOverwriteLabels", "CITY");

            props.put("customAnnotatorClass.regexner2", "edu.stanford.nlp.pipeline.TokensRegexNERAnnotator");
            props.put("regexner2.mapping", "data/kbp_regexner_mapping.tab");
            props.put("regexner2.ignorecase", "false");
            props.put("regexner2.noDefaultOverwriteLabels", "CITY");

            fallbackPipeline = new StanfordCoreNLP(props, false);
        }
        return fallbackPipeline;
    }

    private synchronized StanfordCoreNLP getSplitterPipeline() {
        if (splitter == null) {
            // creates a StanfordCoreNLP object, with POS tagging, lemmatization, NER, parsing, coreference resolution

            Properties props = new Properties();
            props.put("annotators", "tokenize, ssplit, pos, lemma");

            splitter = new StanfordCoreNLP(props, false);
        }
        return splitter;
    }

    public void loadOrCreateAnnotations(AMR[] bank, String path) {
        File f = new File(path);
        if (f.exists()) {
            System.out.println("Loading existing annotations...");
            loadAnnotations(bank, path);
            // If the load succeeded, then quit
            if (bank[0].multiSentenceAnnotationWrapper != null) {
                return;
            }
        }
        System.out.println("No existing annotations. Creating and saving...");
        annotate(bank);
        saveAnnotations(bank, path);
    }

    public void annotate(AMR[] bank) {
        final TimingEstimator timingEstimator = new TimingEstimator();
        timingEstimator.start();
        // Curator doesn't like it when we multi-thread requests to it... ah well.
        int numThreads = 5;
        final Thread[] threads = new Thread[numThreads];
        AtomicInteger counter = new AtomicInteger();

        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(new AnnotatorThread(bank,t,threads.length,counter,timingEstimator));
            threads[t].start();
        }
        for (int t = 0; t < threads.length; t++) {
            try {
                threads[t].join();
            } catch (InterruptedException ignored) {
                // Do nothing
            }
        }
    }

    public class AnnotatorThread implements Runnable {

        AMR[] bank;
        int threadIndex;
        int threadStep;
        AtomicInteger counter;
        TimingEstimator timer;

        final int REPORT_SIZE = 20;

        public AnnotatorThread(AMR[] bank, int threadIndex, int threadStep, AtomicInteger counter, TimingEstimator timer) {
            this.bank = bank;
            this.threadIndex = threadIndex;
            this.threadStep = threadStep;
            this.counter = counter;
            this.timer = timer;
        }

        @Override
        public void run() {
            for (int i = threadIndex; i < bank.length; i += threadStep) {
                annotate(bank[i]);
                int count = counter.addAndGet(1);
                if (count % REPORT_SIZE == 0) {
                    System.out.println("\n\nAnnotating sentence: " + count + " of " + bank.length+"...\n");
                    System.out.println(timer.reportEstimate(count, bank.length));
                }
            }
        }
    }

    public MultiSentenceAnnotationWrapper annotateMultiSentence(String str) {

        // First check if we need to do multiple sentences here. If we do, then do that

        Annotation an = new Annotation(str);
        getSplitterPipeline().annotate(an);

        List<AnnotationWrapper> sentences = new ArrayList<AnnotationWrapper>();
        for (CoreMap sentence : an.get(CoreAnnotations.SentencesAnnotation.class)) {
            sentences.add(annotate(sentence.toString()));
        }
        assert(sentences.size() > 0);
        return new MultiSentenceAnnotationWrapper(sentences);
    }

    public AnnotationWrapper annotate(String str) {

        // Strip the plurals off of stuff, using the simple rule that
        // we just remove all "s" endings off words that are above a
        // certain length, and have NNS as their POS

        /*
        Annotation an = new Annotation(str);
        getSplitterPipeline().annotate(an);
        String newStr = "";
        List<CoreLabel> tokens = an.get(CoreAnnotations.TokensAnnotation.class);
        for (CoreLabel token : tokens) {
            if (newStr.length() > 0) newStr += " ";
            if (token.tag().equals("NNS")) {
                newStr += token.lemma();
            }
            else {
                newStr += token.originalText();
            }
        }
        str = newStr;
        */

        // run all Annotators on this text
        byte[] data = new byte[0];
        try {
            data = str.getBytes("ASCII");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String ascii = new String(data);
        AnnotationWrapper annotationWrapper = new AnnotationWrapper(new Annotation(ascii));
        try {
            // Only use SRL annotations on stuff with length less than 60 words.
            // Otherwise annotation takes literally all day.

            getPipeline().annotate(annotationWrapper.annotation);
        } catch (Exception e) {
            // Sometimes curator refuses to work. Fine. We just won't
            // have SRL data everywhere. Will probably hurt results somewhat, but alright.
            getFallbackPipeline().annotate(annotationWrapper.annotation);
        }

        // Add special case government stuff to the annotations

        for (TokenSequencePattern pattern : AMRConstants.govPatterns) {
            TokenSequenceMatcher matcher = pattern.getMatcher(annotationWrapper.annotation.get(CoreAnnotations.TokensAnnotation.class));
            while (matcher.find()) {
                for (CoreMap elem : matcher.groupNodes()) {
                    elem.set(CoreAnnotations.NamedEntityTagAnnotation.class, "GOVERNMENT-ORGANIZATION");
                }
            }
        }

        System.out.println("Annotating single-sentence: "+annotationWrapper.annotation.toString());
        return annotationWrapper;
    }

    public void annotate(AMR amr) {
        // run all Annotators on this text
        amr.multiSentenceAnnotationWrapper = annotateMultiSentence(amr.formatSourceTokens());
    }

    public void saveAnnotations(AMR[] bank, String path) {
        MultiSentenceAnnotationWrapper[] annotations = new MultiSentenceAnnotationWrapper[bank.length];
        for (int i = 0; i < bank.length; i++) {
            annotations[i] = bank[i].multiSentenceAnnotationWrapper;
        }

        /*
        For some reason we fail to save dev annotations without going into an infinite loop
        */

        try {
            File f = new File(path);
            if (f.exists()) f.delete();
            f.createNewFile();
            OutputStream file = new FileOutputStream(path);
            OutputStream buffer = new GZIPOutputStream(new BufferedOutputStream(file));
            ObjectOutput output = new ObjectOutputStream(buffer);
            try {
                output.writeObject(annotations);
            }
            catch (Exception e) {
                System.out.println("Something went wrong with saving annotations");
                e.printStackTrace();
            }
            finally {
                output.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadAnnotations(AMR[] bank, String path) {
        MultiSentenceAnnotationWrapper[] annotations = new MultiSentenceAnnotationWrapper[bank.length];
        try{
            InputStream file = new FileInputStream(path);
            InputStream buffer = new GZIPInputStream(new BufferedInputStream(file));
            ObjectInput input = new ObjectInputStream (buffer);
            try{
                annotations = (MultiSentenceAnnotationWrapper[])input.readObject();
            }
            finally{
                input.close();
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }

        for (int i = 0; i < bank.length; i++) {
            bank[i].multiSentenceAnnotationWrapper = annotations[i];
        }
    }
}
