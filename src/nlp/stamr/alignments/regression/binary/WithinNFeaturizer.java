package nlp.stamr.alignments.regression.binary;

import nlp.stamr.AMR;
import nlp.stamr.alignments.regression.types.BinaryAlignmentFeaturizer;
import nlp.stamr.ontonotes.SRL;

/**
 * Tries to provide a rough handle for saying what is "Close" vs "Far Away"
 */
public class WithinNFeaturizer extends BinaryAlignmentFeaturizer {

    final int n = 5;

    @Override
    public String featurize(SRL srl, SRL.Arc arc) {
        return null;
    }

    @Override
    public String featurize(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc) {
        if (!parentArc.title.contains("ARG")) {
            if (Math.abs(token - parentToken) > n) return "N:FAR";
            else return "N:CLOSE";
        }
        else return null;
    }
}
