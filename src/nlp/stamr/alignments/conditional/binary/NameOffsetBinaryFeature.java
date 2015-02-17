package nlp.stamr.alignments.conditional.binary;

import nlp.stamr.AMR;
import nlp.stamr.AMRConstants;
import nlp.stamr.alignments.conditional.RuleConstants;
import nlp.stamr.alignments.conditional.types.BinaryAlignmentFeature;
import nlp.stamr.ontonotes.SRL;

/**
 * Forces the offset of (name :op1 "") to be 0 and (thing :name name) to be 0
 */
public class NameOffsetBinaryFeature extends BinaryAlignmentFeature {
    @Override
    public void observe(SRL srl) {
        // Do nothing
    }

    @Override
    public void observe(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc, double probability) {
        // Do nothing
    }

    @Override
    public double score(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc) {
        /*if (parentArc.title.equals("name") && (node.title.equals("name")) && AMRConstants.nerTaxonomy.contains(parentArc.head.title)) {
            if (token != parentToken) {
                return 0;
            }
        }*/
        double penalty = RuleConstants.VIOLATION_PUNISHMENT_PROBABILITY;
        if (parentArc.title.equals("name") && (node.title.equals("name"))) {
            if (token != parentToken) {
                return penalty;
            }
        }
        if (parentArc.title.equals("op1") && (node.type == AMR.NodeType.QUOTE)) {
            if (token != parentToken) {
                return penalty;
            }
        }
        if (parentArc.title.equals("unit") && (parentArc.head.title.contains("-quantity"))) {
            if (token != parentToken) {
                return penalty;
            }
        }
        if ((parentArc.title.equals("ARG2-of") || parentArc.title.equals("ARG1-of") || parentArc.title.equals("ARG0-of")) && AMRConstants.commonNominals.contains(parentArc.head.title)) {
            if (token != parentToken) {
                return penalty;
            }
        }
        return 1.0;
    }

    @Override
    public void addAll(BinaryAlignmentFeature bf) {
        // Do nothing
    }

    @Override
    public void clear() {
        // Do nothing
    }

    @Override
    public void cook() {

    }
}
