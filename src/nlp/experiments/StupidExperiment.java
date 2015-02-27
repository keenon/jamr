package nlp.experiments;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import nlp.stamr.AMR;
import nlp.stamr.AMRSlurp;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by keenon on 2/25/15.
 */
public class StupidExperiment {
    public static void main(String[] args) throws IOException {
        AMR[] train = AMRSlurp.slurp("data/deft-amr-release-r3-proxy-train.txt", AMRSlurp.Format.LDC);
        AMR[] test = AMRSlurp.slurp("data/deft-amr-release-r3-proxy-test.txt", AMRSlurp.Format.LDC);

        Set<String> trainingWords = new HashSet<>();
        for (AMR amr : train) {
            Annotation annotation = amr.multiSentenceAnnotationWrapper.sentences.get(0).annotation;
            for (int i = 0; i < annotation.get(CoreAnnotations.TokensAnnotation.class).size(); i++) {
                trainingWords.add(annotation.get(CoreAnnotations.TokensAnnotation.class).get(i).word());
            }
        }
        Set<String> testingWords = new HashSet<>();
        for (AMR amr : test) {
            Annotation annotation = amr.multiSentenceAnnotationWrapper.sentences.get(0).annotation;
            for (int i = 0; i < annotation.get(CoreAnnotations.TokensAnnotation.class).size(); i++) {
                testingWords.add(annotation.get(CoreAnnotations.TokensAnnotation.class).get(i).word());
            }
        }

        double inTrain = 0;
        double notInTrain = 0;
        for (String s : testingWords) {
            if (trainingWords.contains(s)) inTrain++;
            else notInTrain++;
        }

        System.out.println("Training words: " + trainingWords.size());
        System.out.println("Testing words: "+testingWords.size());

        System.out.println("In Train: "+inTrain+" "+(inTrain / testingWords.size()));
        System.out.println("Not In Train: "+notInTrain+" "+(notInTrain / testingWords.size()));

        Set<String> notInTrainSet = new HashSet<>();

        int i = 0;
        for (String s : testingWords) {
            if (!trainingWords.contains(s)) {
                notInTrainSet.add(s);
                i++;
                // if (i < 20) System.out.println(s);
            }
        }

        double withHyphen = 0;
        double uppercase = 0;
        double number = 0;

        Set<String> unmatched = new HashSet<>();

        Pattern p = Pattern.compile("[0-9]+");

        for (String s : notInTrainSet) {
            Matcher m = p.matcher(s);
            if (s.contains("-")) {
                withHyphen ++;
            }
            else if (Character.isUpperCase(s.charAt(0))) {
                uppercase ++;
            }
            else if (m.matches()) {
                number ++;
            }
            else {
                unmatched.add(s);
            }
        }

        System.out.println("Not in train with hyphen: "+withHyphen+" "+(withHyphen / notInTrainSet.size()));
        System.out.println("Not in train uppercase: "+uppercase+" "+(uppercase / notInTrainSet.size()));
        System.out.println("Not in train number: "+number+" "+(number / notInTrainSet.size()));

        double remaining = unmatched.size();

        System.out.println("Remaining: "+remaining+" "+(remaining / notInTrainSet.size()));

        int j = 0;
        for (String s : unmatched) {
            if (j < 20) {
                j++;
                System.out.println(s);
            }
        }
    }
}
