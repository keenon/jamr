package nlp.stamr.alignments.conditional.binary;

import nlp.stamr.AMR;
import nlp.stamr.alignments.conditional.types.BinaryAlignmentFeature;
import nlp.stamr.ontonotes.SRL;
import nlp.stamr.utils.ConditionalDistribution;

/**
 * Holds the dependency path feature
 */
public class DependencyPathFeature extends BinaryAlignmentFeature {

    ConditionalDistribution<String, String> dependencyPathDistribution = new ConditionalDistribution<String, String>();

    @Override
    public void observe(SRL srl) {
        // Do nothing
    }

    @Override
    public void observe(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc, double probability) {
        dependencyPathDistribution.observe(parentArc.title, amr.annotationWrapper.getDependencyPathBetweenNodes(token, parentToken), probability);
    }

    @Override
    public double score(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc) {
        return dependencyPathDistribution.probAGivenB(parentArc.title, amr.annotationWrapper.getDependencyPathBetweenNodes(token, parentToken));
    }

    @Override
    public void addAll(BinaryAlignmentFeature bf) {
        dependencyPathDistribution.addAll(((DependencyPathFeature) bf).dependencyPathDistribution);
    }

    @Override
    public void clear() {
        dependencyPathDistribution.clear();
    }

    @Override
    public void cook() {

    }
}
