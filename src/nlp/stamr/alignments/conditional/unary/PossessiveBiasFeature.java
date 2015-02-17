package nlp.stamr.alignments.conditional.unary;

import nlp.stamr.AMR;
import nlp.stamr.alignments.conditional.RuleConstants;
import nlp.stamr.alignments.conditional.types.UnaryAlignmentFeature;
import nlp.stamr.ontonotes.SRL;

/**
 * Creates a bias term for possessives aligning to possessives
 */
public class PossessiveBiasFeature extends UnaryAlignmentFeature {
    @Override
    public void observe(SRL srl) {

    }

    @Override
    public void observe(AMR amr, AMR.Node node, int token, double probability) {

    }

    @Override
    public double score(AMR amr, AMR.Node node, int token) {
        if (amr.getParentArc(node).title.equals("poss")) {
            if (amr.annotationWrapper.getPOSTagAtIndex(token).contains("$")) {
                return 1.0;
            } else return RuleConstants.VIOLATION_PUNISHMENT_PROBABILITY;
        }
        else
            return 1.0;
    }

    @Override
    public void addAll(UnaryAlignmentFeature uf) {

    }

    @Override
    public void clear() {

    }

    @Override
    public void cook() {

    }
}
