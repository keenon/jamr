package nlp.stamr.alignments.conditional.types;

import nlp.stamr.AMR;
import nlp.stamr.ontonotes.SRL;

/**
 * Handles features that take into account the parent alignment
 */
public abstract class BinaryAlignmentFeature {
    public void observe(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc) { observe(amr,node,token,parentToken,parentArc,1.0); }
    public abstract void observe(SRL srl);
    public abstract void observe(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc, double probability);
    public abstract double score(AMR amr, AMR.Node node, int token, int parentToken, AMR.Arc parentArc);
    public abstract void addAll(BinaryAlignmentFeature bf);
    public abstract void clear();
    public abstract void cook();
}
