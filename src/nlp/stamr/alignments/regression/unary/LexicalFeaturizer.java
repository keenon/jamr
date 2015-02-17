package nlp.stamr.alignments.regression.unary;

import nlp.stamr.AMR;
import nlp.stamr.alignments.regression.types.UnaryAlignmentFeaturizer;
import nlp.stamr.ontonotes.SRL;

/**
 * Handles AMR-english co-occurrence
 */
public class LexicalFeaturizer extends UnaryAlignmentFeaturizer {
    @Override
    public String featurize(SRL srl) {
        return srl.sense.toLowerCase()+":"+srl.sourceToken.toLowerCase();
    }

    @Override
    public String featurize(AMR amr, AMR.Node node, int token) {
        return node.title.toLowerCase()+":"+amr.getSourceToken(token).toLowerCase();
    }
}
