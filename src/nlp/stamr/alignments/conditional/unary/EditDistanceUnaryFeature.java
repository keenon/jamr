package nlp.stamr.alignments.conditional.unary;

import nlp.stamr.AMR;
import nlp.stamr.AMRConstants;
import nlp.stamr.alignments.conditional.types.UnaryAlignmentFeature;
import nlp.stamr.ontonotes.SRL;

/**
 * Tries to model the fact that AMR derives directly from English text, so token names are often similar or identical
 */
public class EditDistanceUnaryFeature extends UnaryAlignmentFeature {

    static final double smoothing = 0.95;

    @Override
    public double score(AMR amr, AMR.Node node, int token) {

        if (node.title.equals("thing")) return 1.0;

        double max = lexicalProbability(node.title.toLowerCase(), amr.getSourceToken(token));

        for (String amrism : AMRConstants.getCommonAMRisms(amr, node)) {
            if (amrism.equals(amr.getSourceToken(token).toLowerCase())) max = 1.0;
        }

        return (1-smoothing) + (smoothing*max);
    }

    public double lexicalProbability(String node, String token) {
        if (!AMRConstants.combinedTaxonomy.contains(node) && node.matches("([^-]+\\-[0-9]+)")) {
            node = node.substring(0,node.lastIndexOf("-"));
        }
        token = AMRConstants.trimSuffix(token);
        int minLength = Math.min(token.length(),node.length());
        int maxLength = Math.max(token.length(),node.length());
        int matchLength = 0;
        for (int i = 0; i < minLength; i++) {
            if (token.charAt(i) == node.charAt(i)) {
                matchLength = i + 1;
            }
            else break;
        }
        return ((double)(matchLength) / (double)(maxLength));
    }

    /*
    This feature doesn't change with observed data, so we just ignore all these functions
     */

    @Override
    public void observe(SRL srl) {
        // Do nothing
    }

    @Override
    public void observe(AMR amr, AMR.Node node, int token, double probability) {
        // Do nothing
    }

    @Override
    public void addAll(UnaryAlignmentFeature uf) {
        // Do nothing
    }

    @Override
    public void clear() {
        // Do nothing
    }

    @Override
    public void cook() {

    }
}
