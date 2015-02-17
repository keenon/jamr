package nlp.stamr.alignments.conditional.binary;

import nlp.stamr.AMR;
import nlp.stamr.alignments.conditional.types.BinaryAlignmentFeature;
import nlp.stamr.ontonotes.SRL;
import nlp.stamr.utils.ConditionalDistribution;

/**
 * A feature to keep track of distribution over parent offsets
 */
public class ParentOffsetBinaryFeature extends BinaryAlignmentFeature {
    ConditionalDistribution<Integer, String> parentOffsetDistribution = new ConditionalDistribution<Integer, String>();

    @Override
    public void observe(SRL srl) {
        for (SRL.Arc arc : srl.arcs) {
            int offset = calculateOffsetBucket(arc.alignment, srl.alignment);
            if (!arc.rel.contains("ARGM")) {
                parentOffsetDistribution.observe(offset, arc.rel, 1.0);
            }
        }
    }

    @Override
    public void observe(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc, double probability) {
        int offset = calculateOffsetBucket(token,parentToken);
        parentOffsetDistribution.observe(offset,parentArc.title,probability);
    }

    @Override
    public double score(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc) {
        int offset = calculateOffsetBucket(token,parentToken);
        return parentOffsetDistribution.probAGivenB(offset,parentArc.title);
    }

    @Override
    public void addAll(BinaryAlignmentFeature bf) {
        parentOffsetDistribution.addAll(((ParentOffsetBinaryFeature)bf).parentOffsetDistribution);
    }

    @Override
    public void clear() {
        parentOffsetDistribution.clear();
    }

    @Override
    public void cook() {

    }

    public int calculateOffsetBucket(int token, int parentToken) {
        // Leaving NULL
        if ((token == 0) && (parentToken != 0)) {
            return 100;
        }
        // Entering NULL
        else if ((token != 0) && (parentToken == 0)) {
            return -100;
        }
        // Behaving normally
        else {
            return token - parentToken;
        }
    }
}
