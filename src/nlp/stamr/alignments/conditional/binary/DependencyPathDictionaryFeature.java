package nlp.stamr.alignments.conditional.binary;

import nlp.stamr.AMR;
import nlp.stamr.alignments.conditional.types.BinaryAlignmentFeature;
import nlp.stamr.ontonotes.SRL;

/**
 * Holds the dependency path feature
 */
public class DependencyPathDictionaryFeature extends BinaryAlignmentFeature {

    // ConditionalDistribution<String, String> dependencyPathDistribution = new ConditionalDistribution<String, String>();

    @Override
    public void observe(SRL srl) {
        // Do nothing
    }

    @Override
    public void observe(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc, double probability) {
        // dependencyPathDistribution.observe(parentArc.title, amr.getDependencyPathBetweenNodes(token, parentToken), probability);
    }

    @Override
    public double score(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc) {
        String arc = parentArc.title;
        String path = amr.annotationWrapper.getDependencyPathBetweenNodes(token, parentToken).replaceAll("<nn","").replaceAll(">nn","");
        double boost = 10.0;
        /*if (arc.equals("ARG0")) {
            if (path.equals("<nsubj")) return 1.0;
        }
        if (arc.equals("ARG1")) {
            if (path.equals("<dobj")) return 1.0;
        }
        if (arc.equals("mod")) {
            if (path.equals("<amod")) return 1.0;
        }*/
        if (arc.equals("poss")) {
            if (path.equals("<poss")) return 1.0;
            else return 1.0 / boost;
        }
        return 1.0;
        // return dependencyPathDistribution.probAGivenB(parentArc.title, amr.getDependencyPathBetweenNodes(token, parentToken));
    }

    @Override
    public void addAll(BinaryAlignmentFeature bf) {
        //dependencyPathDistribution.addAll(((DependencyPathDictionaryFeature) bf).dependencyPathDistribution);
    }

    @Override
    public void clear() {
        // dependencyPathDistribution.clear();
    }

    @Override
    public void cook() {

    }
}
