package nlp.stamr.ontonotes;

import java.util.ArrayList;
import java.util.List;

/**
 * SRL instance, loaded from OntoNotes examples, useful for training
 */
public class SRL {
    public String sourceToken;
    public String sense;
    public int alignment;
    public List<Arc> arcs = new ArrayList<Arc>();

    public static class Arc {
        public String rel;
        public int alignment;

        public Arc(String rel, int alignment) {
            this.rel = rel;
            this.alignment = alignment;
        }

        public void toString(StringBuilder sb) {
            sb.append("\t");
            sb.append(rel);
            sb.append(":");
            sb.append(alignment);
        }
    }

    public SRL(String sourceToken, String sense, int alignment) {
        this.sourceToken = sourceToken;
        this.sense = sense.replace(".","-");
        this.alignment = alignment;
    }

    public void addArc(String rel, int alignment) {
        arcs.add(new Arc(rel,alignment));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(sourceToken);
        sb.append(":");
        sb.append(sense);
        sb.append(":");
        sb.append(alignment);
        for (Arc arc : arcs) {
            sb.append("\n");
            arc.toString(sb);
        }
        return sb.toString();
    }
}
