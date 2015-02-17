package nlp.stamr.alignments.conditional.binary;

import nlp.stamr.AMR;
import nlp.stamr.AMRConstants;
import nlp.stamr.alignments.conditional.RuleConstants;
import nlp.stamr.alignments.conditional.types.BinaryAlignmentFeature;
import nlp.stamr.ontonotes.SRL;

/**
 * Puts in a stiff constraint that you should not put arcs across obvious sentence boundaries.
 */

public class CrossingPunctuationFeature extends BinaryAlignmentFeature{
    @Override
    public void observe(SRL srl) {

    }

    @Override
    public void observe(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc, double probability) {

    }

    @Override
    public double score(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc) {
        for (int i = Math.min(token,parentToken)+1; i < Math.max(token,parentToken); i++) {
            if (AMRConstants.sentenceIndicators.contains(amr.getSourceToken(i))) {
                return RuleConstants.VIOLATION_PUNISHMENT_PROBABILITY;
            }
        }
        return 1.0;
    }

    @Override
    public void addAll(BinaryAlignmentFeature bf) {

    }

    @Override
    public void clear() {

    }

    @Override
    public void cook() {

    }
}
