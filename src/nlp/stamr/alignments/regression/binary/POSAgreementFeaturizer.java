package nlp.stamr.alignments.regression.binary;

import nlp.stamr.AMR;
import nlp.stamr.alignments.regression.types.BinaryAlignmentFeaturizer;
import nlp.stamr.ontonotes.SRL;

/**
 * Creates a featurized POS agreement tag
 */
public class POSAgreementFeaturizer extends BinaryAlignmentFeaturizer {

    @Override
    public String featurize(SRL srl, SRL.Arc arc) {
        return null;
    }

    @Override
    public String featurize(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc) {
        return amr.annotationWrapper.getPOSTagAtIndex(token) + ":" + amr.annotationWrapper.getPOSTagAtIndex(parentToken);
    }
}
