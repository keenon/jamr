package nlp.stamr.utils;

import edu.stanford.nlp.util.IdentityHashSet;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Holds directed, labeled graphs
 */
public class DirectedGraph<N,L> {
    Set<N> nodes = new HashSet<N>();
    Set<Triple<N,N,L>> arcs = new HashSet<Triple<N, N, L>>();

    Set<Set<N>> weaklyReachableSets = new IdentityHashSet<Set<N>>();

    public void addNode(N node) {
        if (!nodes.contains(node))
            nodes.add(node);
        weaklyReachableSets.add(new HashSet<N>(Arrays.asList(node)));
    }

    public void addNodes(Collection<N> nodes) {
        for (N node : nodes) addNode(node);
    }

    public Set<N> getNodes() {
        return nodes;
    }

    public void addArc(N head, N tail, L label) {
        arcs.add(new Triple<N, N, L>(head, tail, label));
        weaklyLinkNodes(head, tail);
    }

    private void weaklyLinkNodes(N a, N b) {
        Set<N> aSet = null;
        Set<N> bSet = null;
        for (Set<N> set : weaklyReachableSets) {
            if (set.contains(a)) aSet = set;
            if (set.contains(b)) bSet = set;
        }

        if ((aSet != null) && (bSet != null) && (aSet != bSet)) {
            aSet.addAll(bSet);
            weaklyReachableSets.remove(bSet);
        }
    }

    public Set<Pair<N,N>> potentialArcsForFullyConnected() {
        Set<Pair<N,N>> pair = new HashSet<Pair<N, N>>();
        for (Set<N> weaklyReachableSetA : weaklyReachableSets) {
            for (Set<N> weaklyReachableSetB : weaklyReachableSets) {
                if (weaklyReachableSetA != weaklyReachableSetB) {
                    for (N a : weaklyReachableSetA) {
                        for (N b : weaklyReachableSetB) {
                            pair.add(new Pair<N, N>(a,b));
                        }
                    }
                }
            }
        }
        return pair;
    }

    public boolean wouldIntroduceLoops(N head, N tail) {
        return isParentOfNode(head, tail) || isParentOfNode(tail, head);
    }

    public boolean isParentOfNode(N child, N parent) {
        Set<N> parents = new HashSet<N>();
        for (Triple<N,N,L> arc : getIncomingArcs(child)) {
            parents.add(arc.first);
        }
        if (parents.contains(parent)) return true;
        for (N p : parents) if (isParentOfNode(p, parent)) return true;
        return false;
    }

    public Set<Triple<N,N,L>> getIncomingArcs(N node) {
        Set<Triple<N,N,L>> incomingArcs = new HashSet<Triple<N, N, L>>();
        for (Triple<N,N,L> arc : arcs) {
            if (arc.second == node) {
                incomingArcs.add(arc);
            }
        }
        return incomingArcs;
    }

    public Set<Set<N>> getWeaklyReachableSets() {
        return weaklyReachableSets;
    }

    public N getHeadOfSet(Set<N> set) {
        outer: for (N node : set) {
            for (N parent : set) {
                if (isParentOfNode(node, parent)) continue outer;
            }
            return node;
        }
        return null;
    }
}
