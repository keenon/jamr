package nlp.experiments;

import edu.stanford.nlp.pipeline.Annotation;
import nlp.stamr.AMR;

/**
 * Created by keenon on 1/27/15.
 *
 * Holds a bunch of AMR nodes, their source sentence and mappings, and any arcs that are forced by look-ups.
 */
public class AMRNodeStateBased {
    public AMR.Node[] nodes;
    public String[][] forcedArcs;
    public String[][] partialArcs;
    public String[] tokens;
    public Annotation annotation;

    public int currentParent;

    public String[][] correctArcs;

    public String formatTokens() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i != 0) sb.append(" ");
            sb.append(tokens[i]);
        }
        return sb.toString();
    }

    public AMRNodeStateBased(int size, Annotation annotation) {
        nodes = new AMR.Node[size+1];
        forcedArcs = new String[size+1][size+1];
        partialArcs = new String[size+1][size+1];
        correctArcs = new String[size+1][size+1];
        tokens = new String[size+1];
        this.annotation = annotation;
    }

    public AMRNodeStateBased(AMRNodeStateBased old) {
        nodes = old.nodes;

        forcedArcs = old.forcedArcs;
        partialArcs = new String[nodes.length][nodes.length];
        correctArcs = old.correctArcs;

        for (int i = 0; i < nodes.length; i++) {
            System.arraycopy(old.partialArcs[i], 0, partialArcs[i], 0, old.partialArcs[i].length);
        }

        tokens = old.tokens;
        annotation = old.annotation;
    }
}
