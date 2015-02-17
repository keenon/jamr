package nlp.experiments;

import edu.stanford.nlp.pipeline.Annotation;
import nlp.stamr.AMR;

/**
 * Created by keenon on 1/27/15.
 *
 * Holds a bunch of AMR nodes, their source sentence and mappings, and any arcs that are forced by look-ups.
 */
public class AMRNodeSet {
    public AMR.Node[] nodes;
    public String[][] forcedArcs;
    public String[][] correctArcs;
    public String[] tokens;
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
