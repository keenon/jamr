package nlp.experiments.greedy;

import nlp.stamr.AMR;

import java.util.*;

/**
 * Created by keenon on 2/7/15.
 */
public class Generator {

    private static AMR.Node lazyGenNode(AMR amr, Map<Integer,AMR.Node> newNodes, GreedyState state, int i) {
        if (!newNodes.containsKey(i)) {
            AMR.Node n = state.nodes[i];
            if (n.type == AMR.NodeType.ENTITY) {
                newNodes.put(i, amr.addNode(n.ref, n.title, n.alignment));
            }
            else {
                newNodes.put(i, amr.addNode(n.title, n.type, n.alignment));
            }
        }
        return newNodes.get(i);
    }

    public static AMR generateAMR(GreedyState state) {
        Map<Integer,AMR.Node> newNodes = new HashMap<>();
        AMR amr = new AMR();
        amr.sourceText = state.tokens;

        Queue<Integer> q = new ArrayDeque<>();
        Set<Integer> visited = new HashSet<>();

        q.add(0);
        while (!q.isEmpty()) {
            int head = q.poll();
            visited.add(head);
            for (int i = 0; i < state.nodes.length; i++) {
                if (head == i) continue;
                if (head == 0) {
                    if (state.arcs[head][i] != null && !state.arcs[head][i].equals("NONE")) {
                        // Generate node, becomes root
                        lazyGenNode(amr, newNodes, state, i);
                        if (!visited.contains(i) && !q.contains(i)) {
                            q.add(i);
                        }
                        break;
                    }
                }
                else {
                    if (state.arcs[head][i] != null && !state.arcs[head][i].equals("NONE")) {
                        AMR.Node newHead = lazyGenNode(amr, newNodes, state, head);
                        AMR.Node newTail = lazyGenNode(amr, newNodes, state, i);
                        for (AMR.Arc arc : amr.arcs) {
                            if (arc.head == newHead && arc.tail == newTail) {
                                throw new IllegalStateException("Can't add an arc between same two nodes twice");
                            }
                        }
                        amr.addArc(newHead, newTail, state.arcs[head][i]);
                        if (!visited.contains(i) && !q.contains(i)) {
                            q.add(i);
                        }
                    }
                }
            }
        }

        if (amr.nodes.size() > visited.size()-1) {
            throw new IllegalStateException("Can't have more nodes than visited during construction!");
        }

        for (AMR.Node node : amr.nodes) {
            amr.giveNodeUniqueRef(node);
            if (amr.outgoingArcs.containsKey(node)) {
                List<AMR.Arc> arcs = amr.outgoingArcs.get(node);
                Collections.sort(arcs, new Comparator<AMR.Arc>() {
                    @Override
                    public int compare(AMR.Arc o1, AMR.Arc o2) {
                        return o1.tail.alignment - o2.tail.alignment;
                    }
                });

                int opCount = 1;
                for (AMR.Arc arc : arcs) {
                    if (arc.title.equals("op")) {
                        arc.title = "op"+opCount;
                        opCount++;
                    }
                }
            }
        }

        amr.treeify();

        return amr;
    }
}
