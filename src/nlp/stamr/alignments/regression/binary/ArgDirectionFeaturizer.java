package nlp.stamr.alignments.regression.binary;

import nlp.stamr.AMR;
import nlp.stamr.alignments.regression.types.BinaryAlignmentFeaturizer;
import nlp.stamr.ontonotes.SRL;

/**
 * Lets the linear regression system work out how much weight to assign to being on a particular side
 */
public class ArgDirectionFeaturizer extends BinaryAlignmentFeaturizer {
    @Override
    public String featurize(SRL srl, SRL.Arc arc) {
        return null;
    }

    @Override
    public String featurize(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc) {
        boolean hasArg0 = false;
        if (amr.outgoingArcs.containsKey(parentArc.head)) {
            for (AMR.Arc arc : amr.outgoingArcs.get(parentArc.head)) {
                if (arc == parentArc) continue;
                if (arc.title.equals("ARG0")) hasArg0 = true;
            }
        }
        if ((parentArc.title.equals("ARG1") && !hasArg0) || parentArc.title.equals("ARG0")) {
            if (token > parentToken) return "ARGDIR:WRONG"; // subject to the right discouraged
            else return "ARGDIR:CORRECT";
        }
        if ((parentArc.title.equals("ARG1") && hasArg0) || parentArc.title.equals("ARG2")) {
            if (token < parentToken) return "ARGDIR:WRONG"; // object to the left discouraged
            else return "ARGDIR:CORRECT";
        }
        if (parentArc.title.equals("op1") && parentArc.head.title.equals("and")) {
            if (token > parentToken) return "CCDIR:WRONG"; // op1 to the right mildly discouraged
            else return "CCDIR:CORRECT";
        }
        if (parentArc.title.equals("op2") && parentArc.head.title.equals("and")) {
            if (token < parentToken) return "CCDIR:WRONG"; // op2 to the left mildly discouraged
            else return "CCDIR:CORRECT";
        }

        return null;
    }
}
