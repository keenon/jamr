package nlp.stamr.alignments.conditional.binary;

import nlp.stamr.AMR;
import nlp.stamr.alignments.conditional.types.BinaryAlignmentFeature;
import nlp.stamr.ontonotes.SRL;

/**
 * An attempt to punish distant alignments as a function of their height on the tree
 *
 * The intuition goes that the closer you are to the leaves of the tree, the more you should want to
 * align to nodes that are close to you.
 */
public class DistancePunishment extends BinaryAlignmentFeature {
    @Override
    public void observe(SRL srl) {

    }

    @Override
    public void observe(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc, double probability) {

    }

    @Override
    public double score(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc) {
        int distance = Math.abs(parentToken-token);
        double depthPercentage = ((double)node.depth+1) / ((double)amr.treeDepth+1);
        if ((depthPercentage == 0) || (distance == 0)) return 1.0;
        return 1 / Math.pow(distance, depthPercentage);
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
