package nlp.stamr.alignments.regression.types;

import nlp.stamr.AMR;
import nlp.stamr.ontonotes.SRL;

/**
 * Holds a real valued feature for unary alignment
 */
public abstract class UnaryAlignmentFeaturizer {
    public abstract String featurize(SRL srl);
    public abstract String featurize(AMR amr, AMR.Node node, int token);

    public boolean equals(Object o) {
        return getClass().equals(o.getClass());
    }
}
