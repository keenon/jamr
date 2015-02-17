package nlp.stamr.alignments.regression.binary;

import nlp.stamr.AMR;
import nlp.stamr.alignments.regression.types.BinaryAlignmentFeaturizer;
import nlp.stamr.ontonotes.SRL;

/**
 * Created by keenon on 8/12/14.
 */
public class DependencyPathDictionaryFeaturizer extends BinaryAlignmentFeaturizer {
    @Override
    public String featurize(SRL srl, SRL.Arc arc) {
        return null;
    }

    @Override
    public String featurize(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc) {
        String arc = parentArc.title;
        String path = amr.annotationWrapper.getDependencyPathBetweenNodes(token, parentToken).replaceAll("<nn","").replaceAll(">nn","");
        if (arc.equals("ARG0")) {
            if (path.equals("<nsubj")) return "DEPDICT:ARG0";
        }
        if (arc.equals("ARG1")) {
            if (path.equals("<dobj")) return "DEPDICT:ARG1";
        }
        if (arc.equals("mod")) {
            if (path.equals("<amod")) return "DEPDICT:mod";
        }
        if (arc.equals("poss")) {
            if (path.equals("<poss")) return "DEPDICT:poss";
        }
        return null;
    }
}
