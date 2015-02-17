package nlp.stamr.alignments.regression.binary;

import nlp.stamr.AMR;
import nlp.stamr.alignments.regression.types.BinaryAlignmentFeaturizer;
import nlp.stamr.ontonotes.SRL;

/**
 * Creates a nice featurized version of dependency path features for use in linear classifiers
 */
public class DependencyPathFeaturizer extends BinaryAlignmentFeaturizer {
    @Override
    public String featurize(SRL srl, SRL.Arc arc) {
        return null;
    }

    @Override
    public String featurize(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc) {
        String path = amr.annotationWrapper.getDependencyPathBetweenNodes(token, parentToken);
        String fixedPath = path.replaceAll(">nn","").replaceAll("<nn","");
        if (fixedPath.length() > 0)
            return fixedPath;
        else
            return path;
    }
}
