package nlp.stamr.alignments.conditional.unary;

import nlp.stamr.AMR;
import nlp.stamr.alignments.conditional.types.UnaryAlignmentFeature;
import nlp.stamr.ontonotes.SRL;
import nlp.stamr.utils.ConditionalDistribution;

/**
 * Handles scoring for basic lexical affinities
 */
public class POSUnaryFeature extends UnaryAlignmentFeature {
    ConditionalDistribution<String,String> posDistribution = new ConditionalDistribution<String, String>();

    @Override
    public void observe(SRL srl) {
        // Do nothing
    }

    @Override
    public void observe(AMR amr, AMR.Node node, int token, double probability) {
        posDistribution.observe(amr.getParentArc(node).title, amr.annotationWrapper.getPOSTagAtIndex(token), probability);
    }

    @Override
    public double score(AMR amr, AMR.Node node, int token) {
        return posDistribution.probAGivenB(amr.getParentArc(node).title,amr.annotationWrapper.getPOSTagAtIndex(token));
    }

    @Override
    public void addAll(UnaryAlignmentFeature uf) {
        posDistribution.addAll(((POSUnaryFeature) uf).posDistribution);
    }

    @Override
    public void clear() {
        posDistribution.clear();
    }

    @Override
    public void cook() {

    }
}
