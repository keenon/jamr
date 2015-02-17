package nlp.stamr.alignments.regression.unary;

import nlp.stamr.AMR;
import nlp.stamr.AMRConstants;
import nlp.stamr.alignments.regression.types.UnaryAlignmentFeaturizer;
import nlp.stamr.ontonotes.SRL;

/**
 * Bucketed edit distance feature
 */
public class EditDistanceFeaturizer extends UnaryAlignmentFeaturizer {
    @Override
    public String featurize(SRL srl) {
        return null;
    }

    @Override
    public String featurize(AMR amr, AMR.Node node, int token) {
        if (AMRConstants.getCommonAMRisms(amr, node).contains(amr.getSourceToken(token).toLowerCase())) return "EDIT:ISM";
        else return matchLength(node.title, amr.getSourceToken(token));
    }

    public String matchLength(String node, String token) {
        if (!AMRConstants.combinedTaxonomy.contains(node) && node.matches("([^-]+\\-[0-9]+)")) {
            node = node.substring(0,node.lastIndexOf("-"));
        }
        token = AMRConstants.trimSuffix(token.toLowerCase());
        int minLength = Math.min(token.length(),node.length());
        int maxLength = Math.max(token.length(),node.length());
        for (int i = 0; i < minLength; i++) {
            if (token.charAt(i) != node.charAt(i)) {
                return i+":"+maxLength;
            }
        }
        return minLength+":"+maxLength;
    }
}
