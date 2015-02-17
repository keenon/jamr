package nlp.experiments;

import edu.stanford.nlp.pipeline.Annotation;

/**
 * Created by keenon on 1/27/15.
 *
 * Holds all the interesting information associated with a labeled sequence
 */
public class LabeledSequence {
    public String[] tokens;
    public String[] labels;
    public Annotation annotation;

    public String formatTokens() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i != 0) sb.append(" ");
            sb.append(tokens[i]);
        }
        return sb.toString();
    }
}
