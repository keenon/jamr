package nlp.stamr.annotation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds a serializable annotation of multiple sentences
 */
public class MultiSentenceAnnotationWrapper implements Serializable {
    public List<AnnotationWrapper> sentences;
    public MultiSentenceAnnotationWrapper(List<AnnotationWrapper> sentences) {
        this.sentences = sentences;
    }
    public MultiSentenceAnnotationWrapper(AnnotationWrapper sentence) {
        sentences = new ArrayList<AnnotationWrapper>();
        sentences.add(sentence);
    }
}
