package nlp.experiments.greedy;

import edu.stanford.nlp.pipeline.Annotation;
import nlp.stamr.AMR;

import java.util.*;

/**
 * Created by keenon on 2/4/15.
 *
 * Holds the state necessary for a greedy linking of AMR nodes.
 */
public class GreedyState {
    public int head;
    public AMR.Node[] nodes;
    public String[][] arcs;
    public String[][] forcedArcs;
    public int[] originalParent;

    public Annotation annotation;
    public String[] tokens;

    public Queue<Integer> q = new ArrayDeque<>();

    public boolean finished = false;

    public GreedyState() {}

    public GreedyState(AMR.Node[] nodes, String[] tokens, Annotation annotation) {
        this.head = 0;
        this.nodes = nodes;
        this.arcs = new String[nodes.length][nodes.length];
        this.forcedArcs = new String[nodes.length][nodes.length];
        this.originalParent = new int[nodes.length];
        for (int i = 0; i < originalParent.length; i++) {
            originalParent[i] = -1;
        }
        this.annotation = annotation;
        this.tokens = tokens;
    }

    public GreedyState deepClone() {
        GreedyState clone = new GreedyState(nodes, tokens, annotation);
        clone.head = head;

        clone.arcs = new String[arcs.length][arcs[0].length];
        for (int i = 0; i < arcs.length; i++) {
            System.arraycopy(arcs[i], 0, clone.arcs[i], 0, arcs[i].length);
        }
        clone.forcedArcs = new String[forcedArcs.length][forcedArcs[0].length];
        for (int i = 0; i < forcedArcs.length; i++) {
            System.arraycopy(forcedArcs[i], 0, clone.forcedArcs[i], 0, forcedArcs[i].length);
        }
        clone.originalParent = new int[nodes.length];
        System.arraycopy(originalParent, 0, clone.originalParent, 0, originalParent.length);

        clone.q = new ArrayDeque<>();
        clone.q.addAll(q);

        clone.finished = finished;

        return clone;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GreedyState)) return false;
        GreedyState state = (GreedyState)o;
        if (head != state.head) return false;
        if (nodes.length != state.nodes.length) return false;
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] != state.nodes[i]) return false;
        }
        if (arcs.length != state.arcs.length) return false;
        for (int i = 0; i < arcs.length; i++) {
            if (arcs[i].length != state.arcs[i].length) return false;
            for (int j = 0; j < arcs[i].length; j++) {
                String myArc = arcs[i][j];
                if (myArc == null) myArc = "NONE";
                String theirArc = state.arcs[i][j];
                if (theirArc == null) theirArc = "NONE";
                if (!myArc.equals(theirArc)) return false;
            }
        }
        if (forcedArcs.length != state.forcedArcs.length) return false;
        for (int i = 0; i < forcedArcs.length; i++) {
            if (forcedArcs[i].length != state.forcedArcs[i].length) return false;
            for (int j = 0; j < forcedArcs[i].length; j++) {
                String myArc = forcedArcs[i][j];
                String theirArc = state.forcedArcs[i][j];
                if ((myArc == null) && (theirArc != null)) return false;
                if (myArc != null && !myArc.equals(theirArc)) return false;
            }
        }
        if (originalParent.length != state.originalParent.length) return false;
        for (int i = 0; i < originalParent.length; i++) {
            if (originalParent[i] != state.originalParent[i]) return false;
        }
        if (annotation != state.annotation) return false;
        if (tokens.length != state.tokens.length) return false;
        for (int i = 0; i < tokens.length; i++) {
            if (!tokens[i].equals(state.tokens[i])) return false;
        }
        List<Integer> myQ = new ArrayList<>();
        myQ.addAll(q);
        List<Integer> theirQ = new ArrayList<>();
        theirQ.addAll(state.q);
        if (!myQ.equals(theirQ)) return false;
        if (finished != state.finished) return false;

        // YAY!

        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Head: ").append(head).append("\n");
        sb.append("Q: ").append(Arrays.toString(q.toArray())).append("\n");
        return sb.toString();
    }

    public GreedyState transition(String[] headArcs) {

        // Put in all the arcs

        GreedyState next = deepClone();
        for (int i = 1; i < headArcs.length; i++) {
            next.arcs[head][i] = headArcs[i];
            if (!headArcs[i].equals("NONE")) {
                if (next.originalParent[i] == -1) {
                    next.originalParent[i] = head;
                    if (next.q.contains(i)) {
                        throw new IllegalStateException("Can't visit the same node twice!");
                    }
                    next.q.add(i);
                }
            }
        }

        // Move to next head

        if (next.q.isEmpty()) {
            next.finished = true;
        }
        else {
            next.head = next.q.poll();
            next.finished = false;
        }

        return next;
    }
}
