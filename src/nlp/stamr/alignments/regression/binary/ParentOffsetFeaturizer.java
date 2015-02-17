package nlp.stamr.alignments.regression.binary;

import nlp.stamr.AMR;
import nlp.stamr.alignments.regression.types.BinaryAlignmentFeaturizer;
import nlp.stamr.ontonotes.SRL;

/**
 * Deals with bucketing the offsets
 */
public class ParentOffsetFeaturizer extends BinaryAlignmentFeaturizer {
    @Override
    public String featurize(SRL srl, SRL.Arc arc) {
        int offset = calculateOffsetBucket(arc.alignment,srl.alignment);
        return ""+offset;
    }

    @Override
    public String featurize(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc) {
        int offset = calculateOffsetBucket(token,parentToken);
        return ""+offset;
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
