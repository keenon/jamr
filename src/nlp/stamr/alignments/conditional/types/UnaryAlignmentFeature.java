package nlp.stamr.alignments.conditional.types;

import nlp.stamr.AMR;
import nlp.stamr.ontonotes.SRL;

/**
 * Handles aligning AMR tokens to sentence tokens
 */
public abstract class UnaryAlignmentFeature {
    public void observe(AMR amr, AMR.Node node, int token) { observe(amr,node,token,1.0); }
    public abstract void observe(SRL srl);
    public abstract void observe(AMR amr, AMR.Node node, int token, double probability);
    public abstract double score(AMR amr, AMR.Node node, int token);
    public abstract void addAll(UnaryAlignmentFeature uf);
    public abstract void clear();
    public abstract void cook();
}
