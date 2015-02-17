package nlp.stamr;

import nlp.stamr.annotation.AnnotationWrapper;
import nlp.stamr.annotation.MultiSentenceAnnotationWrapper;
import nlp.stamr.utils.MiscUtils;
import edu.stanford.nlp.util.IdentityHashSet;
import edu.stanford.nlp.util.Pair;

import java.io.Serializable;
import java.util.*;

/**
 * Handles nlp.stamr.AMR in the most sensible way I can think of
 */
public class AMR implements Serializable {

    public AMR() {}

    @Deprecated
    public AnnotationWrapper annotationWrapper = null;

    public AMR(MultiSentenceAnnotationWrapper multisentenceAnnotationWrapper) {
        this.multiSentenceAnnotationWrapper = multisentenceAnnotationWrapper;
    }

    public enum NodeType {
        QUOTE,
        VALUE,
        ENTITY
    }

    public static class Node implements Serializable {
        public String title;
        public String ref;
        public boolean isFirstRef = true;
        public int depth = 0;
        public int alignment = 0;
        public boolean alignmentFixed = false;
        public double[] softAlignments = new double[0];
        public int testAlignment = -1;

        public NodeType type;

        public Node(String ref, String title, NodeType type) {this.ref = ref; this.title = title; this.type = type;}

        public String toString() {
            if (type == NodeType.ENTITY) {
                return "("+ref+" / "+title+")";
            }
            else if (type == NodeType.QUOTE) {
                return "\""+title+"\"";
            }
            else if (type == NodeType.VALUE) {
                return title;
            }
            return title;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Node) {
                Node n = (Node)o;
                return (n.title.equals(title)) && (n.type.equals(type)); // && (n.alignment == alignment);
            }
            else return false;
        }

        @Override
        public int hashCode() {
            return title.hashCode();
        }
    }

    public static class Arc implements Serializable {
        public Node head;
        public Node tail;
        public String title;

        public Arc(Node head, Node tail, String title) {this.head = head; this.tail = tail; this.title = title;}

        @Override
        public String toString() {
            return "("+head.toString()+" :"+title+" "+tail.toString()+")";
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Arc) {
                Arc a = (Arc)o;
                // Null-protected version of equals()
                return ((((a.head == null) && (head == null)) || (a.head != null && a.head.equals(head))) && (((a.tail == null) && (tail == null)) || (a.tail != null && a.tail.equals(tail))) && a.title.equals(title));
                // return a.head.equals(head) && a.tail.equals(tail) && a.title.equals(title);
            }
            else return false;
        }

        @Override
        public int hashCode() {
            return (head == null ? 1 : head.hashCode())*title.hashCode()*tail.hashCode();
        }
    }

    public static class CorefGroup implements Serializable {
        public String ref;
        public Set<Node> nodes = new IdentityHashSet<Node>();

        public CorefGroup(String ref, Node seed) {
            this.ref = ref;
            nodes.add(seed);
        }

        public void addNode(Node node) {
            nodes.add(node);
        }
    }

    public enum Direction {
        UP,
        DOWN
    }

    public String docId = "";
    public String[] sourceText = new String[0];

    public Node head = null;
    public final Set<Node> nodes = new IdentityHashSet<Node>();
    public final Set<Arc> arcs = new IdentityHashSet<Arc>();
    public final Map<Node,List<Arc>> outgoingArcs = new IdentityHashMap<Node, List<Arc>>();
    public final Map<Node,List<Arc>> incomingArcs = new IdentityHashMap<Node, List<Arc>>();
    public final Map<String,CorefGroup> groups = new HashMap<String, CorefGroup>();

    // Just an optimization cache for EMAligner to use
    public final Map<Node,Set<Integer>> matchesCache = new IdentityHashMap<Node, Set<Integer>>();

    public int treeDepth = 0;

    public Node nullNode = new Node("NULL","NULL", NodeType.ENTITY);
    public Arc nullArc = null;

    public MultiSentenceAnnotationWrapper multiSentenceAnnotationWrapper;

    public Set<String> quoteNodeTextSet = new HashSet<String>();

    /////////////////////////////////////////////////
    // PUBLIC INTERFACE

    public Node addNode(String ref, String title) {
        Node node = new Node(ref, title, NodeType.ENTITY);
        nodes.add(node);
        addNodeToGroup(node, ref);
        if (head == null) {
            head = node;
            nullArc = new Arc(nullNode,head,"ROOT");
        }
        return node;
    }

    public Node addNode(String ref, String title, int alignment) {
        Node node = addNode(ref, title);
        node.alignment = alignment;
        return node;
    }

    public Node addNodeAmbiguousValueOrRef(String title) {
        if (groups.containsKey(title)) return addNode(title);
        else return addNode(title, NodeType.VALUE);
    }

    public Node addNode(String title, NodeType type) {
        Node node = new Node("", title, type);
        if (type == NodeType.QUOTE) {
            quoteNodeTextSet.add(title);
        }
        nodes.add(node);
        if (head == null) {
            head = node;
            nullArc = new Arc(nullNode,head,"ROOT");
        }
        return node;
    }

    public Node addNode(String title, NodeType type, int alignment) {
        Node node = addNode(title, type);
        node.alignment = alignment;
        return node;
    }

    public Node addNode(String ref) {
        // Take the same title as the first node in the group
        Node node = new Node(ref, nodesWithRef(ref).iterator().next().title, NodeType.ENTITY);
        node.isFirstRef = false;
        nodes.add(node);
        addNodeToGroup(node, ref);
        return node;
    }

    public Node addNode(String ref, int alignment) {
        Node node = addNode(ref);
        node.alignment = alignment;
        return node;
    }

    public Arc addArc(Node head, Node tail, String title) {
        assert(head != tail);
        Arc arc = new Arc(head,tail,title);
        arcs.add(arc);
        addNodeArcToMap(head, arc, outgoingArcs);
        addNodeArcToMap(tail, arc, incomingArcs);

        // attempts to protect against accidentally introducing loops that crash smatch
        try {
            if ((tail.ref != null) && (head.ref != null) && tail.ref.equals(head.ref)) {
                giveNodeUniqueRef(tail);
            }
        }
        catch (NullPointerException e) {
            e.printStackTrace();
        }

        tail.depth = head.depth+1;
        if (tail.depth > treeDepth) treeDepth = tail.depth;
        return arc;
    }

    public void giveNodeUniqueRef(Node node) {
        Set<String> takenRefs = new HashSet<String>();
        for (Node n : nodes) {
            if (n == node) continue;
            takenRefs.add(n.ref);
        }

        String baseRef = node.title.charAt(0)+"";
        try {
            Integer.parseInt(baseRef);
            baseRef = "x";
        }
        catch (Exception e) {
            // do nothing
        }
        String ref = baseRef;
        int offset = 2;
        while (takenRefs.contains(ref)) {
            ref = baseRef+offset;
            offset++;
        }

        node.ref = ref;
        groups.put(ref, new CorefGroup(ref, node));
    }

    public Node replaceSetWithNode(Set<Node> nodes, String ref, String title, int alignment) {
        Node newNode = addNode(ref, title, alignment);
        newNode.alignmentFixed = true;

        for (Node node : nodes) {
            List<Arc> removeArcs = new ArrayList<Arc>();
            if (incomingArcs.containsKey(node)) {
                for (Arc arc : incomingArcs.get(node)) {
                    // Remove all internal arcs
                    if (nodes.contains(arc.head)) {
                        removeArcs.add(arc);
                    }
                    else {
                        arc.tail = newNode;
                        if (!incomingArcs.containsKey(newNode)) incomingArcs.put(newNode,new ArrayList<Arc>());
                        incomingArcs.get(newNode).add(arc);
                    }
                }
            }
            if (outgoingArcs.containsKey(node)) {
                for (Arc arc : outgoingArcs.get(node)) {
                    // Remove all internal arcs
                    if (nodes.contains(arc.tail)) {
                        removeArcs.add(arc);
                    }
                    else {
                        arc.head = newNode;
                        if (!outgoingArcs.containsKey(newNode)) outgoingArcs.put(newNode,new ArrayList<Arc>());
                        outgoingArcs.get(newNode).add(arc);
                    }
                }
            }
            if (head == node) head = newNode;

            arcs.removeAll(removeArcs);
            outgoingArcs.remove(node);
            incomingArcs.remove(node);
        }

        this.nodes.removeAll(nodes);

        return newNode;
    }

    public Set<Arc> getDuplicateArcs(Arc arc) {
        Set<Arc> set = arcsBetweenGroups(arc.head.ref, arc.tail.ref, arc.title);
        set.remove(arc);
        return set;
    }

    public Set<Arc> arcsBetweenGroups(String headRef, String tailRef, String title) {
        CorefGroup headGroup = groups.get(headRef);
        CorefGroup tailGroup = groups.get(tailRef);
        Set<Arc> set = new IdentityHashSet<Arc>();
        for (Arc arc : arcs) {
            if (arc.title.equals(title)) {
                if (headGroup.nodes.contains(arc.head)) {
                    if (tailGroup.nodes.contains(arc.tail)) {
                        set.add(arc);
                    }
                }
            }
        }
        return set;
    }

    public boolean isChildOfNode(Node child, Node parent) {
        if (incomingArcs.containsKey(child) && incomingArcs.get(child).size() == 0) incomingArcs.remove(child);
        if (!incomingArcs.containsKey(child)) return false;
        else if (incomingArcs.get(child).get(0).head == parent) return true;
        else return isChildOfNode(incomingArcs.get(child).get(0).head, parent);
    }

    public Arc getParentArc(Node node) {
        if (incomingArcs.containsKey(node) && incomingArcs.get(node).size() == 0) {
            incomingArcs.remove(node);
        }
        if (!incomingArcs.containsKey(node)) return nullArc;
        else return incomingArcs.get(node).get(0);
    }

    public List<Node> topologicalSortOnlyConnected() {
        return breadthFirstSearch(head);
    }

    public List<Node> topologicalSort() {
        List<Node> sorted = new ArrayList<Node>();

        if (head == null) return sorted;

        List<Node> firstSorted = breadthFirstSearch(head);
        sorted.addAll(firstSorted);

        Set<Node> remainingNodes = new IdentityHashSet<Node>();
        remainingNodes.addAll(nodes);
        remainingNodes.removeAll(firstSorted);

        while (!remainingNodes.isEmpty()) {
            List<Node> disconnectedSortedSet = breadthFirstSearch(remainingNodes.iterator().next());
            sorted.addAll(disconnectedSortedSet);
            remainingNodes.removeAll(disconnectedSortedSet);
        }

        return sorted;
    }

    public List<Node> breadthFirstSearch() {
        if (head == null) return new ArrayList<>();
        else return breadthFirstSearch(head);
    }

    public List<Node> breadthFirstSearch(Node node) {
        List<Node> sorted = new ArrayList<Node>();
        Set<Node> visited = new IdentityHashSet<Node>();
        Queue<Node> visit = new ArrayDeque<Node>();

        visit.add(node);
        while (!visit.isEmpty()) {
            Node popped = visit.poll();
            if (visited.contains(popped)) continue;
            visited.add(popped);
            sorted.add(popped);
            if (outgoingArcs.containsKey(popped)) {
                for (Arc child : outgoingArcs.get(popped)) {
                    visit.add(child.tail);
                }
            }
        }

        return sorted;
    }

    public Set<String> containedRefs() {
        Set<String> refs = new HashSet<String>();
        for (Node node : nodes) {
            refs.add(node.ref);
        }
        return refs;
    }

    public boolean nodeSetConnected(Set<Node> nodes) {
        if (nodes.size() <= 1) return true;

        outer: for (Node node : nodes) {
            List<Node> children = breadthFirstSearch(node);
            for (Node nodePrime : nodes) if (!children.contains(nodePrime) && nodePrime != node) continue outer;
            return true;
        }
        return false;
    }

    public List<Node> depthFirstSearch() {
        return depthFirstSearchNode(head);
    }

    public List<Node> depthFirstSearchNode(Node node) {
        return depthFirstSearchNode(node, new IdentityHashSet<Node>());
    }

    public List<Node> depthFirstSearchNode(Node node, Set<Node> visited) {
        List<Node> list = new ArrayList<Node>();
        if (node == null) return list;
        if (visited.contains(node)) return list;
        list.add(node);
        visited.add(node);
        if (outgoingArcs.containsKey(node)) {
            for (Arc child : outgoingArcs.get(node)) {
                list.addAll(depthFirstSearchNode(child.tail, visited));
            }
        }
        return list;
    }

    public List<Node> depthFirstSearchAlignmentSorted() {
        return depthFirstSearchNodeAlignnmentSorted(head);
    }

    public List<Node> depthFirstSearchNodeAlignnmentSorted(Node node) {
        List<Node> list = new ArrayList<Node>();
        if (node == null) return list;
        list.add(node);
        if (outgoingArcs.containsKey(node)) {
            Set<Arc> unvisitedArcs = new IdentityHashSet<Arc>();
            unvisitedArcs.addAll(outgoingArcs.get(node));
            while (!unvisitedArcs.isEmpty()) {
                Arc bestArc = null;
                for (Arc arc : unvisitedArcs) {
                    if ((bestArc == null) || (arc.tail.alignment < bestArc.tail.alignment)) {
                        bestArc = arc;
                    }
                }
                unvisitedArcs.remove(bestArc);
                list.addAll(depthFirstSearchNodeAlignnmentSorted(bestArc.tail));
            }
        }
        return list;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof AMR) {
            AMR a = (AMR)o;
            if (!MiscUtils.setsEqualUsingEquals(a.arcs, arcs)) return false;
            if (!MiscUtils.setsEqualUsingEquals(a.nodes,nodes)) return false;
            if (!a.head.equals(head)) return false;
            return true;
        }
        else return false;
    }

    public boolean isTree() {
        return isTreeRecursive(head, new IdentityHashSet<Node>());
    }

    private boolean isTreeRecursive(Node node, Set<Node> visited) {
        if (visited.contains(node)) return false;
        visited.add(node);
        if (outgoingArcs.containsKey(node)) {
            for (Arc arc : outgoingArcs.get(node)) {
                if (!isTreeRecursive(arc.tail, visited)) return false;
            }
        }
        return true;
    }

    public void treeify() {
        List<Arc> nullArcs = new ArrayList<Arc>();
        for (Arc arc : arcs) {
            if (arc.tail == null) nullArcs.add(arc);
        }
        removeArcs(nullArcs);

        treeifyRecursive(head, new IdentityHashSet<Node>());
    }

    private void treeifyRecursive(Node node, Set<Node> visited) {
        visited.add(node);
        if (outgoingArcs.containsKey(node)) {
            for (Arc arc : outgoingArcs.get(node)) {
                if (visited.contains(arc.tail)) {
                    Node clone;
                    if (arc.tail.type == NodeType.ENTITY) {
                        clone = addNode(arc.tail.ref);
                    }
                    else {
                        clone = addNode(arc.tail.title, arc.tail.type, arc.tail.alignment);
                    }
                    clone.alignment = arc.tail.alignment;
                    arc.tail = clone;
                }
                else treeifyRecursive(arc.tail, visited);
            }
        }
    }

    public Node getSetHead(Set<Node> set) {
        List<Node> reachableFromRoot = breadthFirstSearch();
        // Give priority to stuff that's reachable from the root
        outer:
        for (Node node : set) {
            if (reachableFromRoot.contains(node)) {
                for (Node node2 : set) {
                    if (node2 == node) continue;
                    if (isChildOfNode(node, node2)) continue outer;
                }
                return node;
            }
        }
        return getAdjacentSetHead(set);
    }

    public Node getAdjacentSetHead(Set<Node> set) {
        assert(set.size() > 0);
        outer:
        for (Node node : set) {
            if (incomingArcs.containsKey(node)) {
                for (Arc arc : incomingArcs.get(node)) {
                    if (set.contains(arc.head)) {
                        continue outer;
                    }
                }
            }
            return node;
        }
        return set.iterator().next();
    }

    public List<Node> topologicalSortAdjacentSet(Set<Node> set) {
        List<Node> sorted = topologicalSort();
        List<Node> sortedSubset = new ArrayList<Node>();
        for (Node node : sorted) {
            if (set.contains(node)) {
                sortedSubset.add(node);
            }
        }
        return sortedSubset;
    }

    public Set<Arc> getNonProjectiveArcs() {
        Set<Arc> nonProjectiveArcs = new IdentityHashSet<Arc>();
        for (Arc arc1 : arcs) {
            if (arc1.head.alignment == arc1.tail.alignment) continue;
            for (Arc arc2 : arcs) {
                if (arc1 == arc2) continue;
                if (arc2.head.alignment == arc2.tail.alignment) continue;
                if (arc1.head.alignment == arc2.tail.alignment) continue;
                if (arc1.head.alignment == arc2.head.alignment) continue;
                if (arc2.head.alignment == arc1.tail.alignment) continue;
                if (arc2.head.alignment == arc1.head.alignment) continue;
                if (MiscUtils.intBetweenExclusive(arc1.head.alignment, arc2.head.alignment, arc2.tail.alignment) != MiscUtils.intBetweenExclusive(arc1.tail.alignment, arc2.head.alignment, arc2.tail.alignment)) {
                    nonProjectiveArcs.add(arc1);
                    break;
                }
            }
        }
        return nonProjectiveArcs;
    }

    /*
    public Set<Arc> getNonProjectiveArcs() {
        Set<Arc> nonProjectiveArcs = new IdentityHashSet<Arc>();
        Set<Integer> posts = new IdentityHashSet<Integer>();
        for (Node node : topologicalSort()) {
            posts.add(node.alignment);
            if (outgoingArcs.containsKey(node)) {
                for (Arc arc : outgoingArcs.get(node)) {
                    for (int post : posts) {
                        if (MiscUtils.intBetweenExclusive(post, arc.head.alignment, arc.tail.alignment)) {
                            nonProjectiveArcs.add(arc);
                            break;
                        }
                    }
                }
            }
        }
        return nonProjectiveArcs;
    }
    */

    public boolean hasHomongenousNonNullAlignment() {
        int alignment = -1;
        for (Node node : nodes) {
            if (alignment == -1) alignment = node.alignment;
            if ((node.alignment == 0) || (node.alignment != alignment)) return false;
        }
        return true;
    }

    public Set<Node> getLargestConnectedSet(Collection<Node> subset) {
        Set<Set<Node>> sets = getConnectedSets(subset);
        Set<Node> largest = null;
        for (Set<Node> set : sets) {
            if (largest == null || set.size() > largest.size()) largest = set;
        }
        return largest;
    }

    public Set<Set<Node>> getConnectedSets(Collection<Node> subset) {
        Set<Set<Node>> sets = new HashSet<>();

        for (Node n : subset) {
            sets.add(new IdentityHashSet<Node>(){{
                add(n);
            }});
        }

        while (true) {
            Pair<Set<Node>,Set<Node>> adjacentSet = null;

            outer: for (Set<Node> s1 : sets) {
                for (Set<Node> s2 : sets) {
                    if (s1 == s2) continue;
                    for (Node n1 : s1) {
                        for (Node n2 : s2) {
                            if (nodesAdjacent(n1, n2)) {
                                adjacentSet = new Pair<>(s1, s2);
                                break outer;
                            }
                        }
                    }
                }
            }

            if (adjacentSet != null) {
                sets.remove(adjacentSet.second);
                adjacentSet.first.addAll(adjacentSet.second);
            }
            else break;
        }

        return sets;
    }

    public Pair<AMR,Map<Node,Node>> cloneConnectedSubset(Collection<Node> subset) {
        AMR clone = new AMR();
        clone.multiSentenceAnnotationWrapper = multiSentenceAnnotationWrapper;
        clone.sourceText = sourceText;
        if (subset.size() == 0) return new Pair<AMR, Map<Node, Node>>(clone, new HashMap<Node, Node>());

        Map<Node,Node> oldToNew = clone.subsumeContentsOf(this);
        Set<Node> excludedNodes = new IdentityHashSet<Node>();
        excludedNodes.addAll(nodes);
        excludedNodes.removeAll(subset);

        // Remove non-child-clones

        Set<Node> newExcludedNodes = new IdentityHashSet<Node>();
        for (Node excluded : excludedNodes) {
            clone.nodes.remove(oldToNew.get(excluded));
            clone.incomingArcs.remove(oldToNew.get(excluded));
            clone.outgoingArcs.remove(oldToNew.get(excluded));
            newExcludedNodes.add(oldToNew.get(excluded));
        }

        // Remove arcs involving non-children

        Set<Arc> excludedArcs = new IdentityHashSet<Arc>();
        for (Arc arc : clone.arcs) {
            if (newExcludedNodes.contains(arc.head) || newExcludedNodes.contains(arc.tail)) {
                excludedArcs.add(arc);
            }
        }
        clone.arcs.removeAll(excludedArcs);
        for (List<Arc> arcs : clone.incomingArcs.values()) {
            arcs.removeAll(excludedArcs);
        }
        for (List<Arc> arcs : clone.outgoingArcs.values()) {
            arcs.removeAll(excludedArcs);
        }

        // Set new head, assuming that the collection is in fact fully connected

        for (Node node : clone.nodes) {
            if (clone.incomingArcs.containsKey(node)) {
                if (clone.incomingArcs.get(node).size() == 0) {
                    clone.incomingArcs.remove(node);
                }
            }
            if (clone.outgoingArcs.containsKey(node)) {
                if (clone.outgoingArcs.get(node).size() == 0) {
                    clone.outgoingArcs.remove(node);
                }
            }
        }

        for (Node node : clone.nodes) {
            if (!clone.incomingArcs.containsKey(node)) {
                clone.head = node;
                break;
            }
        }

        return new Pair<AMR, Map<Node, Node>>(clone,oldToNew);
    }

    public AMR cloneSubtree(Node node) {
        return cloneConnectedSubset(depthFirstSearchNode(node)).first;
    }

    public boolean adjacentAlignmentClustersUnique() {
        Set<Integer> alignments = new HashSet<Integer>();
        for (Set<Node> cluster : splitAdjacentAlignmentClusters().first) {
            int clusterAlignment = cluster.iterator().next().alignment;
            if (alignments.contains(clusterAlignment)) return false;
            alignments.add(clusterAlignment);
        }
        return true;
    }

    public Pair<Set<Set<Node>>,Map<Pair<Set<Node>,Set<Node>>,Set<Arc>>> splitAdjacentAlignmentClusters() {

        Set<Set<Node>> adjacentAlignmentClusters = new IdentityHashSet<Set<Node>>();
        for (int i = 0; i < sourceTokenCount(); i++) {
            Set<Node> iAligned = new IdentityHashSet<Node>();
            for (Node node : nodes) {
                if (node.alignment == i) iAligned.add(node);
            }
            adjacentAlignmentClusters.addAll(splitAdjacentSets(iAligned));
        }

        Map<Pair<Set<Node>,Set<Node>>,Set<Arc>> barrierArcs = new IdentityHashMap<Pair<Set<Node>, Set<Node>>, Set<Arc>>();

        for (Set<Node> adjacentAlignmentCluster1 : adjacentAlignmentClusters) {
            for (Set<Node> adjacentAlignmentCluster2 : adjacentAlignmentClusters) {
                if (adjacentAlignmentCluster1 == adjacentAlignmentCluster2) continue;
                Pair<Set<Node>,Set<Node>> pair = new Pair<Set<Node>, Set<Node>>(adjacentAlignmentCluster1,adjacentAlignmentCluster2);
                barrierArcs.put(pair, new IdentityHashSet<Arc>());
                for (Arc arc : arcs) {
                    if (adjacentAlignmentCluster1.contains(arc.head) && adjacentAlignmentCluster2.contains(arc.tail)) {
                        barrierArcs.get(pair).add(arc);
                    }
                }
            }
        }

        return new Pair<Set<Set<Node>>, Map<Pair<Set<Node>, Set<Node>>, Set<Arc>>>(adjacentAlignmentClusters, barrierArcs);
    }

    public Set<Node> getAdjacentAlignmentChildren(Node node) {
        return getAdjacentAlignmentChildren(node, new HashSet<Node>());
    }

    private Set<Node> getAdjacentAlignmentChildren(Node node, Set<Node> visited) {
        Set<Node> set = new HashSet<Node>();
        visited.add(node);
        set.add(node);

        if (outgoingArcs.containsKey(node)) {
            for (AMR.Arc arc : outgoingArcs.get(node)) {
                if (!visited.contains(arc.tail) && (arc.tail.alignment == node.alignment)) {
                    set.addAll(getAdjacentAlignmentChildren(arc.tail, visited));
                }
            }
        }

        return set;
    }

    public Set<Set<Node>> splitAdjacentSets(Set<Node> set) {
        Set<Set<Node>> adjacentSets = new IdentityHashSet<Set<Node>>();

        Queue<Node> splitSet = new ArrayDeque<Node>();
        splitSet.addAll(set);

        // May take outrageous time (O(n^3)), but since n is small, and we only do this once, I
        // don't mind so much. Should probably come up with a better way to do this, though.

        Set<Node> currentAdjacentSet = new IdentityHashSet<Node>();

        while (splitSet.size() > 0) {

            Set<Node> addSet = new IdentityHashSet<Node>();

            for (AMR.Node node : splitSet) {
                for (AMR.Node adj : currentAdjacentSet) {
                    if (nodesAdjacent(node,adj)) {
                        addSet.add(node);
                        break;
                    }
                }
            }

            if (addSet.size() > 0) {
                currentAdjacentSet.addAll(addSet);
                splitSet.removeAll(addSet);
            }
            else {
                if (currentAdjacentSet.size() > 0) adjacentSets.add(currentAdjacentSet);
                currentAdjacentSet = new IdentityHashSet<Node>();
                currentAdjacentSet.add(splitSet.poll());
            }
        }

        if (currentAdjacentSet.size() > 0) adjacentSets.add(currentAdjacentSet);

        return adjacentSets;
    }

    public boolean nodesAdjacent(Node a, Node b) {
        if (incomingArcs.containsKey(a)) {
            for (Arc arc : incomingArcs.get(a)) {
                if (arc.head == b) return true;
            }
        }
        if (outgoingArcs.containsKey(a)) {
            for (Arc arc : outgoingArcs.get(a)) {
                if (arc.tail == b) return true;
            }
        }
        return false;
    }

    public void flipParentArcIfExists(Node node, String newArcName) {
        if (node != head && incomingArcs.containsKey(node)) {
            flipArc(getParentArc(node), newArcName);
        }
    }

    public void moveAllArcsToAndRemove(AMR.Node source, AMR.Node dest) {
        Set<Arc> moveOutgoingArcs = new IdentityHashSet<Arc>();
        if (outgoingArcs.containsKey(source)) {
            moveOutgoingArcs.addAll(outgoingArcs.get(source));
        }
        for (AMR.Arc arc : moveOutgoingArcs) {
            if (arc.tail != dest) {
                addArc(dest, arc.tail, arc.title);
            }
        }

        Set<Arc> moveIncomingArcs = new IdentityHashSet<Arc>();
        if (incomingArcs.containsKey(source)) {
            moveIncomingArcs.addAll(incomingArcs.get(source));
        }
        for (AMR.Arc arc : moveIncomingArcs) {
            if (arc.head != dest) {
                addArc(arc.head, dest, arc.title);
            }
        }

        if (head == source) {
            head = dest;
        }

        removeArcs(moveOutgoingArcs);
        removeArcs(moveIncomingArcs);
        removeNode(source);
    }

    public void flipArc(Arc arc, String newArcName) {
        Node arcHead = arc.head;
        Node arcTail = arc.tail;
        removeArc(arc);
        addArc(arcTail, arcHead, newArcName);

        if (arcHead == head) {
            head = arcTail;
        }
        else if (incomingArcs.containsKey(arcHead)) {
            Arc parentArc = getParentArc(arcHead);
            Node parentArcHead = parentArc.head;
            removeArc(parentArc);
            addArc(parentArcHead, arcTail, parentArc.title);
        }
    }

    @Override
    public int hashCode() {
        int hashCode = 1;
        for (Node node : nodes) hashCode *= node.hashCode();
        for (Arc arc : arcs) hashCode *= arc.hashCode();
        return hashCode;
    }

    public String toCoNLLString() {
        StringBuilder sb = new StringBuilder();
        for (AMR.Arc arc : arcs) {
            if (arc.head != null && arc.tail != null && arc != nullArc) {
                sb.append("\t");
                sb.append(arc.head.toString());
                sb.append("\t");
                sb.append(arc.head.alignment);
                sb.append(":"+arc.title);
                sb.append(arc.tail.toString());
                sb.append("\t");
                sb.append(arc.tail.alignment);
                sb.append("\t");
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public String toTriplesOutput() {
        StringBuilder sb = new StringBuilder();
        for (AMR.Arc arc : arcs) {
            if (arc.head != null && arc.tail != null && arc != nullArc) {
                sb.append("(");
                sb.append(arc.head.title);
                sb.append(" ");
                sb.append(":"+arc.title);
                sb.append(" ");
                sb.append(arc.tail.title);
                sb.append(")");
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toString(AlignmentPrinting.NONE);
    }

    public String toString(AlignmentPrinting alignmentPrinting) {
        return toString(alignmentPrinting,null);
    }

    public String toString(AlignmentPrinting alignmentPrinting, Node selected) {
        StringBuilder sb = new StringBuilder();
        if (head == null) {
            return "()";
        }
        else if (!isTree()) {
            return "(LOOPY TREE)";
        }
        else {
            printNode(sb,head,new HashSet<String>(),0,alignmentPrinting,selected,false);
            return sb.toString();
        }
    }

    public String toStringForSmatch() {
        removeSmatchSelfLoops();
        StringBuilder sb = new StringBuilder();
        if ((head == null) || !isTree() || (head == nullNode)) {
            return "(n / no-parse)";
        }
        else {
            printNode(sb,head,new HashSet<String>(),0, AlignmentPrinting.NONE,null,true);
            return sb.toString();
        }
    }

    public int sourceTokenCount() {
        return sourceText.length;
    }

    public String getSourceToken(int i) {
        if (i >= sourceText.length) return "OOB";
        return sourceText[i];
        /*if (i == 0) return "NULL";
        else return sourceText[i-1];*/
    }

    // This is mostly only useful for writing more concise unit tests
    public Node nodeWithName(String name) {
        for (Node n : nodes) if (n.title.equals(name)) return n;
        return null;
    }

    public boolean hasSmatchSelfLoop() {
        for (Arc arc : arcs) {
            if (arc.head.ref.equals(arc.tail.ref)) return true;
        }
        return false;
    }

    public boolean nodeAdjacentToRefGroup(Node node, String ref) {
        return nodeAdjacentToRefGroup(node, ref, null);
    }

    public void trimDisconnectedSubsets() {
        List<Node> connected = depthFirstSearch();
        Set<Node> notConnected = new IdentityHashSet<Node>();
        notConnected.addAll(nodes);
        notConnected.removeAll(connected);

        Set<Arc> arcsToRemove = new IdentityHashSet<Arc>();
        for (Arc arc : arcs) {
            if ((arc.head == null) || (arc.tail == null) || notConnected.contains(arc.head) || notConnected.contains(arc.tail)) {
                arcsToRemove.add(arc);
            }
        }
        removeArcs(arcsToRemove);

        for (Node n : notConnected) removeNode(n);

        for (Arc arc : arcs) {
            assert((arc.head != null) && (arc.tail != null));
        }
    }

    public boolean nodeAdjacentToRefGroup(Node node, String ref, Node ignoredNode) {
        if (incomingArcs.containsKey(node)) {
            for (Arc arc : incomingArcs.get(node)) {
                if ((arc.head != ignoredNode) && arc.head.ref.equals(ref)) return true;
            }
        }
        else if (outgoingArcs.containsKey(node)) {
            for (Arc arc : outgoingArcs.get(node)) {
                if ((arc.tail != ignoredNode) && arc.tail.ref.equals(ref)) return true;
            }
        }
        return false;
    }

    public boolean hasQuoteNodeFor(int i) {
        return quoteNodeTextSet.contains(getSourceToken(i));
    }

    public Map<Node,Node> subsumeContentsOf(AMR amr) {
        Map<Node,Node> oldToNew = new IdentityHashMap<Node, Node>();

        // Duplicate old nodes

        // Add the head first, so we subsume it as our own head if we need one

        if (amr.head != null)
            subsumeNode(amr.head, oldToNew);

        for (Node node : amr.nodes) {
            if (node == amr.head) continue;
            subsumeNode(node, oldToNew);
        }

        // Duplicate old arcs

        for (Arc arc : amr.arcs) {
            if ((arc.head != null) && (arc.tail != null))
                addArc(oldToNew.get(arc.head),oldToNew.get(arc.tail),arc.title);
        }

        return oldToNew;
    }

    public Map<Node,Node> subsumeNodesOf(AMR amr, Collection<Node> subsumeNodes) {
        Map<Node,Node> oldToNew = new IdentityHashMap<Node, Node>();
        IdentityHashSet<Node> subsumeNodesSet = new IdentityHashSet<Node>();
        subsumeNodesSet.addAll(subsumeNodes);

        // Duplicate old nodes

        // Add the head first, so we subsume it as our own head if we need one

        if (subsumeNodes.contains(amr.head))
            if (amr.head != null)
                subsumeNode(amr.head, oldToNew);

        for (Node node : subsumeNodes) {
            if (node == amr.head) continue;
            subsumeNode(node, oldToNew);
        }

        // Duplicate old arcs

        for (Arc arc : amr.arcs) {
            if ((arc.head != null) && (arc.tail != null) && subsumeNodesSet.contains(arc.head) && subsumeNodesSet.contains(arc.tail))
                addArc(oldToNew.get(arc.head),oldToNew.get(arc.tail),arc.title);
        }

        return oldToNew;
    }

    public void removeSmatchSelfLoops() {

        // Removes smatch self-loops as a post-processing step

        for (Arc arc : arcs) {
            if (arc.head != null && arc.tail != null && arc.head.ref.equals(arc.tail.ref)) {
                Node node = arc.tail;
                String baseRef = node.title.toLowerCase().charAt(0)+"";
                int counter = 1;
                while (groups.containsKey(baseRef+counter)) counter++;
                node.ref = baseRef+counter;
                groups.put(baseRef+counter, new CorefGroup(baseRef+counter, node));
            }
        }
    }

    public void removeNode(Node node) {
        if (incomingArcs.containsKey(node)) {
            for (Arc arc : incomingArcs.get(node)) {
                arc.tail = null;
            }
            incomingArcs.remove(node);
        }
        if (outgoingArcs.containsKey(node)) {
            for (Arc arc : outgoingArcs.get(node)) {
                arc.head = null;
            }
            outgoingArcs.remove(node);
        }
        if (groups.containsKey(node.ref))
            groups.get(node.ref).nodes.remove(node);
        nodes.remove(node);
    }

    public void removeArc(Arc arc) {
        if (outgoingArcs.containsKey(arc.head)) {
            outgoingArcs.get(arc.head).remove(arc);
            if (outgoingArcs.get(arc.head).size() == 0) outgoingArcs.remove(arc.head);
        }
        if (incomingArcs.containsKey(arc.tail)) {
            incomingArcs.get(arc.tail).remove(arc);
            if (incomingArcs.get(arc.tail).size() == 0) incomingArcs.remove(arc.tail);
        }
        arcs.remove(arc);
    }

    public void removeArcs(Collection<Arc> arcs) {
        for (Arc arc : arcs) removeArc(arc);
    }

    public void moveIncomingArcs(Node from, Node to) {
        if (incomingArcs.containsKey(from) && (incomingArcs.get(from).size() == 0)) incomingArcs.remove(from);
        if (incomingArcs.containsKey(from)) {
            for (AMR.Arc arc : incomingArcs.get(from)) {
                arc.tail = to;
            }
            if (!incomingArcs.containsKey(to)) incomingArcs.put(to, new ArrayList<Arc>());
            incomingArcs.get(to).addAll(incomingArcs.get(from));
            incomingArcs.remove(from);
        }
        else if (head == from) {
            head = to;
        }
    }

    public void replaceNode(Node replaced, Node replacer) {
        if (incomingArcs.containsKey(replaced)) {
            incomingArcs.put(replacer, incomingArcs.get(replaced));
            incomingArcs.remove(replaced);
            for (Arc arc : incomingArcs.get(replacer)) {
                arc.tail = replacer;
            }
        }

        if (outgoingArcs.containsKey(replaced)) {
            outgoingArcs.put(replacer, outgoingArcs.get(replaced));
            outgoingArcs.remove(replaced);
            for (Arc arc : outgoingArcs.get(replacer)) {
                arc.head = replacer;
            }
        }

        nodes.remove(replaced);
        nodes.add(replacer);

        if (head == replaced) head = replacer;
    }

    public int numRoots() {
        int count = 0;
        for (Node node : nodes) {
            if (incomingArcs.containsKey(node)) {
                if (incomingArcs.get(node).size() == 0) count++;
            }
            else count++;
        }
        return count;
    }

    private void subsumeNode(AMR.Node node, Map<Node,Node> oldToNew) {
        Node newNode;
        if (node.type == NodeType.ENTITY) {
            newNode = addNode(node.ref, node.title, node.alignment);
        }
        else {
            newNode = addNode(node.title, node.type, node.alignment);
        }
        newNode.alignment = node.alignment;
        newNode.alignmentFixed = node.alignmentFixed;
        newNode.softAlignments = node.softAlignments;
        oldToNew.put(node,newNode);
    }

    public Map<Node,Node> replaceChildNodeWithGraph(Node replace, AMR subgraph) {
        Map<Node,Node> oldToNew = subsumeContentsOf(subgraph);

        for (Node newNode : oldToNew.values()) {
            newNode.alignment = replace.alignment;
            // Give node a unique ref, because presumably it's not coref with anything yet
            giveNodeUniqueRef(newNode);
        }

        if (incomingArcs.containsKey(replace)) {
            for (Arc arc : incomingArcs.get(replace)) {
                arc.tail = oldToNew.get(subgraph.head);
                addNodeArcToMap(oldToNew.get(subgraph.head),arc,incomingArcs);
            }
        }
        if (outgoingArcs.containsKey(replace)) {
            for (Arc arc : outgoingArcs.get(replace)) {
                arc.head = oldToNew.get(subgraph.head);
                addNodeArcToMap(oldToNew.get(subgraph.head),arc,outgoingArcs);
            }
        }
        nodes.remove(replace);
        incomingArcs.remove(replace);
        outgoingArcs.remove(replace);

        if (head == replace) {
            head = oldToNew.get(subgraph.head);
        }

        return oldToNew;
    }

    public boolean hasAlignments() {
        for (Node node : nodes) if (node.alignment != 0) return true;
        return false;
    }

    public boolean hasFixedAlignments() {
        for (Node node : nodes) if (!node.alignmentFixed) return false;
        return true;
    }

    public Set<Arc> chunkChildArcs(Set<Node> chunk) {
        Set<Arc> arcs = new HashSet<Arc>();
        for (Node node : chunk) {
            if (outgoingArcs.containsKey(node)) {
                for (Arc arc : outgoingArcs.get(node)) {
                    if (arc.tail.alignment != node.alignment) {
                        arcs.add(arc);
                    }
                }
            }
        }
        return arcs;
    }

    public Set<Node> nodesWithAlignment(int i) {
        Set<Node> set = new HashSet<Node>();
        for (Node node : nodes) {
            if (node.alignment == i) set.add(node);
        }
        return set;
    }

    public String shortestPathFromNodeToNode(Node source, Node goal, boolean includeTraversedNodes) {
        Deque<Pair<Node,String>> searchQueue = new ArrayDeque<Pair<Node, String>>();
        Set<Node> visited = new IdentityHashSet<Node>();

        String startPath = "";
        if (includeTraversedNodes) startPath += "["+source.title+"]";
        searchQueue.push(new Pair<Node, String>(source, startPath));

        while (!searchQueue.isEmpty()) {
            Pair<Node,String> explorePair = searchQueue.poll();
            Node node = explorePair.first;
            String path = explorePair.second;

            if (node == goal) {
                return path;
            }

            visited.add(node);

            if (incomingArcs.containsKey(node)) {
                for (Arc arc : incomingArcs.get(node)) {
                    if (!visited.contains(arc.head) && arc.head != null) {
                        String newPath = path+"<"+arc.title;
                        if (includeTraversedNodes) newPath += "["+arc.head.title+"]";
                        searchQueue.push(new Pair<Node, String>(arc.head, newPath));
                    }
                }
            }
            if (outgoingArcs.containsKey(node)) {
                for (Arc arc : outgoingArcs.get(node)) {
                    if (!visited.contains(arc.tail) && arc.tail != null) {
                        String newPath = path+">"+arc.title;
                        if (includeTraversedNodes) newPath += "["+arc.tail.title+"]";
                        searchQueue.push(new Pair<Node, String>(arc.tail, newPath));
                    }
                }
            }
        }

        return "NOPATH";
    }

    /////////////////////////////////////////////////
    // PRIVATE INTERFACE

    private void addNodeToGroup(Node node, String ref) {
        if (groups.containsKey(ref)) groups.get(ref).addNode(node);
        else groups.put(ref,new CorefGroup(ref,node));
    }

    private void addNodeArcToMap(Node node, Arc arc, Map<Node,List<Arc>> map) {
        if (map.containsKey(node)) {
            map.get(node).add(arc);
        }
        else {
            List<Arc> list = new ArrayList<Arc>();
            list.add(arc);
            map.put(node,list);
        }
    }

    public String formatSourceTokens() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sourceText.length; i++) {
            sb.append(sourceText[i]).append(" ");
        }
        return sb.toString();
    }

    public enum AlignmentPrinting {
        NONE,
        ALL,
        FIXED_ONLY
    }

    private void printOpeningBracket(StringBuilder sb, Node node, Node selected) {
        if (selected == node) sb.append("=*=");
        if (selected != null && (node.alignmentFixed)) {
            if ((node.testAlignment != -1) && (node.alignment != node.testAlignment)) {
                sb.append("=!=");
            }
            else if (incomingArcs.containsKey(node) && getNonProjectiveArcs().contains(incomingArcs.get(node).get(0))) {
                sb.append("=&=");
            }
            else {
                sb.append("=#=");
            }
        }
    }

    private void printClosingBracket(StringBuilder sb, Node node, Node selected) {
        if (selected == node) sb.append("*=*");
        if (selected != null && (node.alignmentFixed)) {
            if ((node.testAlignment != -1) && (node.alignment != node.testAlignment)) {
                sb.append("!=!");
            }
            else if (incomingArcs.containsKey(node) && getNonProjectiveArcs().contains(incomingArcs.get(node).get(0))) {
                sb.append("&=&");
            }
            else {
                sb.append("#=#");
            }
        }
    }

    public Set<Node> nodesWithRef(String ref) {
        Set<Node> nodesWithRef = new HashSet<Node>();
        for (Node node : nodes) {
            if (node.ref.equals(ref)) {
                nodesWithRef.add(node);
            }
        }
        return nodesWithRef;
    }

    private void printNode(StringBuilder sb, Node node, Set<String> visitedRefs, int depth, AlignmentPrinting alignmentPrinting, Node selected, boolean forSmatch) {
        if (forSmatch && node.ref.startsWith("/")) {
            node.ref = "s";
            node.title = "slash-character";
            giveNodeUniqueRef(node);
        }
        if (forSmatch && node.ref.startsWith(":")) {
            node.ref = "c";
            node.title = "colon-character";
            giveNodeUniqueRef(node);
        }
        if (node.type == NodeType.ENTITY) {
            if ((!outgoingArcs.containsKey(node) || forSmatch) && visitedRefs.contains(node.ref)) {
                printOpeningBracket(sb, node, selected);
                sb.append(node.ref);
                printAlignment(sb, node, alignmentPrinting, selected);
                printClosingBracket(sb, node, selected);
            }
            else {
                sb.append("(");
                printOpeningBracket(sb, node, selected);
                if (visitedRefs.contains(node.ref)) {
                    sb.append(node.ref);
                }
                else {
                    sb.append(node.ref).append(" / ").append(node.title);
                    visitedRefs.add(node.ref);
                    if (forSmatch) {
                        for (Node groupMember : nodesWithRef(node.ref)) {
                            if (outgoingArcs.containsKey(groupMember)) {
                                for (Arc arc : outgoingArcs.get(groupMember)) {
                                    sb.append("\n");
                                    printArc(sb, arc, visitedRefs, depth + 1, alignmentPrinting, selected, forSmatch);
                                }
                            }
                        }
                    }
                }
                if (forSmatch && node.title.equals("slash-character")) {
                    node.ref = "/";
                    node.title = "/";
                }
                printAlignment(sb,node,alignmentPrinting, selected);
                printClosingBracket(sb, node, selected);

                if (!forSmatch) {
                    if (outgoingArcs.containsKey(node)) {
                        for (Arc arc : outgoingArcs.get(node)) {
                            sb.append("\n");
                            printArc(sb, arc, visitedRefs, depth + 1, alignmentPrinting, selected, forSmatch);
                        }
                    }
                }
                sb.append(")");
            }
        }
        else if (node.type == NodeType.QUOTE) {
            printOpeningBracket(sb, node, selected);
            sb.append("\"");
            sb.append(node.title);
            sb.append("\"");
            printAlignment(sb,node,alignmentPrinting, selected);
            printClosingBracket(sb, node, selected);
        }
        else if (node.type == NodeType.VALUE) {
            printOpeningBracket(sb, node, selected);
            sb.append(node.title);
            printAlignment(sb,node,alignmentPrinting, selected);
            printClosingBracket(sb, node, selected);
        }
    }

    private void printAlignment(StringBuilder sb, Node node, AlignmentPrinting alignmentPrinting, Node selected) {
        if ((alignmentPrinting != AlignmentPrinting.NONE) && (alignmentPrinting == AlignmentPrinting.ALL || node.alignmentFixed)) {
            if ((selected == null) || (node.testAlignment == -1) || (node.testAlignment == node.alignment)) {
                sb.append(" [").append(node.alignment).append(" = \"").append(getSourceToken(node.alignment)).append("\"]");
            }
            else {
                sb.append(" [GUESS ").append(node.testAlignment).append(" = \"").append(getSourceToken(node.testAlignment)).append("\" ACTUAL ").append(node.alignment).append(" = \"").append(getSourceToken(node.alignment)).append("\"]");
            }
        }
    }

    private void printArc(StringBuilder sb, Arc arc, Set<String> visitedRefs, int depth, AlignmentPrinting alignmentPrinting, Node selected, boolean forSmatch) {

        // Bail if we get a bad arc
        if (arc.head == null || arc.tail == null) return;

        if (forSmatch) {
            if (arc.head.ref != null && arc.head.ref.equals(arc.tail.ref)) {
                System.out.println(toString(AlignmentPrinting.ALL));
                throw new IllegalStateException("Can't have an instant self-loop when dealing with smatch");
            }
        }
        for (int i = 0; i < depth; i++) sb.append("\t");
        String title = arc.title;
        sb.append(":").append(arc.title).append(" ");
        printNode(sb, arc.tail, visitedRefs, depth, alignmentPrinting, selected, forSmatch);
    }

}
