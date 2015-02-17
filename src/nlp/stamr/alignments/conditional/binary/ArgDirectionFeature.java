package nlp.stamr.alignments.conditional.binary;

import nlp.stamr.AMR;
import nlp.stamr.alignments.conditional.RuleConstants;
import nlp.stamr.alignments.conditional.types.BinaryAlignmentFeature;
import nlp.stamr.ontonotes.SRL;

/**
 * A dirty hack to try to force corrections for English word order constraints.
 */
public class ArgDirectionFeature extends BinaryAlignmentFeature {
    @Override
    public void observe(SRL srl) {

    }

    @Override
    public void observe(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc, double probability) {

    }

    @Override
    public double score(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc) {
        boolean hasArg0 = false;
        if (amr.outgoingArcs.containsKey(parentArc.head)) {
            for (AMR.Arc arc : amr.outgoingArcs.get(parentArc.head)) {
                if (arc == parentArc) continue;
                if (arc.title.equals("ARG0")) hasArg0 = true;
            }
        }
        double penalty = RuleConstants.VIOLATION_PUNISHMENT_PROBABILITY;
        if ((parentArc.title.equals("ARG1") && !hasArg0) || parentArc.title.equals("ARG0")) {
            if (token > parentToken) return penalty; // subject to the right discouraged
        }
        if ((parentArc.title.equals("ARG1") && hasArg0) || parentArc.title.equals("ARG2")) {
            if (token < parentToken) return penalty; // object to the left discouraged
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
