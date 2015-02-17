package nlp.stamr.alignments.conditional.binary;

import nlp.stamr.AMR;
import nlp.stamr.alignments.conditional.types.BinaryAlignmentFeature;
import nlp.stamr.ontonotes.SRL;
import nlp.stamr.utils.ConditionalDistribution;

/**
 * Captures conditional dependency distance
 */
public class DependencyDistanceFeature extends BinaryAlignmentFeature {

    ConditionalDistribution<Integer, String> dependencyPathDistribution = new ConditionalDistribution<Integer, String>();

    @Override
    public void observe(SRL srl) {
        // Do nothing
    }

    @Override
    public void observe(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc, double probability) {
        dependencyPathDistribution.observe(amr.annotationWrapper.getDependencyDistanceBetweenNodes(token, parentToken), parentArc.title, probability);
    }

    @Override
    public double score(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc) {
        return dependencyPathDistribution.probAGivenB(amr.annotationWrapper.getDependencyDistanceBetweenNodes(token, parentToken), parentArc.title);
    }

    @Override
    public void addAll(BinaryAlignmentFeature bf) {
        dependencyPathDistribution.addAll(((DependencyDistanceFeature) bf).dependencyPathDistribution);
    }

    @Override
    public void clear() {
        dependencyPathDistribution.clear();
    }

    @Override
    public void cook() {

    }
}
