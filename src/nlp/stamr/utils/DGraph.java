package nlp.stamr.utils;

import edu.stanford.nlp.util.FixedPrioritiesPriorityQueue;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PriorityQueue;


/**
 * Gabor's DGraph implementation
 */
class DGraph {
    /**
     * Edge states of the directed graph.
     * <ul>
     * <li>edges[i][j]=0 means no edge from i to j
     * <li>edges[i][j]=n means an edge from i to j is present, and the value
     * (n) saves the node in the clique that the edge actually points
     * to/from.
     * </ul>
     */
    private int[][] edges;
    private double[][] weights;
    private Object[][] labels;
    private int[] parents;
    private PriorityQueue<Integer>[] parentWeights;
    private int size;
    private boolean[] valid;

    private int cycle[];
    private int seen[];
    private int marked[];
    private int uniqueInt = 1;

    @SuppressWarnings("unchecked")
    public DGraph(double[][] w, Object[][] labels) {
        assert w.length > 0;
        assert w[0].length == w.length;
        // --Create Graph
        // (set variables)
        this.edges = new int[2 * w.length][2 * w.length];
        this.weights = new double[2 * w.length][2 * w.length];
        // (no edges to root)
        for (int parent = 0; parent < weights.length; parent++) {
            edges[parent][0] = 0;
            weights[parent][0] = Double.NaN;
        }
        // (fill edges)
        for (int parent = 0; parent < w.length; parent++) {
            for (int child = 1; child < w[0].length; child++) {
                if (parent == child) {
                    edges[parent][child] = 0;
                    weights[parent][child] = Double.NaN;
                } else {
                    edges[parent][child] = child;
                    weights[parent][child] = w[parent][child];
                }
            }
        }
        // (create labels)
        this.labels = labels;
        // (set size)
        this.size = w.length;
        // --Initialize Tree
        // (set variables)
        this.parents = new int[2 * w.length];
        this.parentWeights = (PriorityQueue<Integer>[]) new PriorityQueue[2 * w.length];
        // (root node)
        this.parents[0] = -1;
        this.parentWeights[0] = null;
        // (greedy match)
        for (int child = 1; child < w[0].length; child++) {
            // (vars)
            int argmax = -1;
            double max = Double.NEGATIVE_INFINITY;
            this.parentWeights[child] = new FixedPrioritiesPriorityQueue<Integer>(
                    2 * w.length);
            // (find parent)
            for (int parent = 0; parent < w.length; parent++) {
                if (parent == child) {
                    continue;
                }
                parentWeights[child].add(parent, weights[parent][child]);
                if (weights[parent][child] > max) {
                    max = weights[parent][child];
                    argmax = parent;
                }
            }
            if (argmax < 0) {
                throw new IllegalStateException("Node "+child+" doesn't have a parent: "+argmax);
            }
            assert argmax >= 0;
            // (set parent)
            assert argmax != child;
            parents[child] = argmax;
        }
        // --Initialize
        // (valid)
        this.valid = new boolean[2 * w.length];
        for (int i = 0; i < w.length; i++) {
            valid[i] = true;
        }
        // (cycle state)
        seen = new int[parents.length];
        marked = new int[parents.length];
        cycle = new int[parents.length];
    }

    private int[] cycle(int iter) {
        for (int node = 0; node < size; node++) {
            // (skips)
            if (!valid[node]) {
                continue;
            }
            if (seen[node] == iter) {
                continue;
            }
            // (get first node)
            seen[node] = iter;
            uniqueInt += 1;
            assert marked[node] != uniqueInt;
            marked[node] = uniqueInt;
            cycle[0] = node;
            int cycleSize = 1;
            int target = parents[node];
            assert target != node;
            assert target < 0 || marked[target] != uniqueInt;
            // (traverse cycle)
            while (true) {
                if (target < 0) {
                    break; // not a cycle (hit no parent)
                } else if (seen[node] == iter && marked[node] != uniqueInt) {
                    break; // we've checked this node previously
                } else if (marked[target] == uniqueInt) {
                    assert cycleSize > 1;
                    // (get start of cycle)
                    int start = 0;
                    for (start = 0; start < cycleSize; start++) {
                        if (cycle[start] == target) {
                            break;
                        }
                    }
                    // (copy cycle)
                    int[] rtn = new int[cycleSize - start];
                    assert rtn.length > 1;
                    for (int i = 0; i < rtn.length; i++) {
                        rtn[i] = cycle[cycleSize - 1 - i];
                    }
                    return rtn;
                } else {
                    // (continue searching)
                    seen[target] = iter; // mark seen
                    marked[target] = uniqueInt; // mark
                    cycle[cycleSize] = target; // add to cycle
                    cycleSize += 1;
                    assert parents[target] != target;
                    target = parents[target]; // pop parent
                }
            }
        }
        // (no cycle)
        return null;
    }

    private void contract(int[] cycle) {
        // --Create Cycle Node
        // (create node)
        int cycleNode = this.size;
        valid[cycleNode] = true;
        this.parentWeights[cycleNode] = new FixedPrioritiesPriorityQueue<Integer>(
                parents.length);
        this.size += 1;
        // (invalidate cycle)
        for (int cycleI = 0; cycleI < cycle.length; cycleI++) {
            valid[cycle[cycleI]] = false;
            assert parents[cycle[cycleI]] >= 0;
        }
        // --Incoming Edges
        // (cycle normalization score)
        double cycleScore = 0.0;
        for (int cycleI = 0; cycleI < cycle.length; cycleI++) {
            int cyclePred = cycle[(cycleI - 1 + cycle.length)
                    % cycle.length];
            cycleScore += weights[cyclePred][cycle[cycleI]];
        }
        assert !Double.isNaN(cycleScore);
        assert cycleScore > Double.NEGATIVE_INFINITY;
        assert cycleScore < Double.POSITIVE_INFINITY;
        // (parent of cycle node vars)
        int bestParent = -1;
        double bestParentScore = Double.NEGATIVE_INFINITY;
        // (edges)
        for (int parent = 0; parent < size - 1; parent++) {
            if (!valid[parent]) {
                continue;
            }
            // (vars)
            int argmax = -1;
            double max = Double.NEGATIVE_INFINITY;
            boolean parentInCycle = false;
            // (get best edge into the cycle)
            for (int cycleChildI = 0; cycleChildI < cycle.length; cycleChildI++) {
                // (overhead)
                int cycleChild = cycle[cycleChildI];
                if (cycleChild == parent) { // can't connect from in cycle
                    parentInCycle = true;
                    break;
                }
                if (edges[parent][cycleChild] == 0) {
                    continue;
                } // no edge
                // (calculate incoming edge score)
                int cyclePred = cycle[(cycleChildI - 1 + cycle.length)
                        % cycle.length];
                double predScore = weights[cyclePred][cycleChild];
                double parentScore = weights[parent][cycleChild];
                double score = parentScore - predScore; // argmax over this
                // (max)
                if (score > max) {
                    argmax = cycleChild;
                    max = score;
                }
            }
            if (parentInCycle) {
                continue;
            } // extend continue in inner loop
            assert !Double.isNaN(max);
            // (propgate -inf)
            if (argmax < 0) {
                assert cycle.length > 0;
                assert max == Double.NEGATIVE_INFINITY;
                argmax = 0;
            } else {
                assert argmax >= 0;
                assert max > Double.NEGATIVE_INFINITY;
                assert max < Double.POSITIVE_INFINITY;
            }
            // (create edge)
            assert parent != cycleNode;
            edges[parent][cycleNode] = argmax;
            weights[parent][cycleNode] = max + cycleScore; // note
            // cycleScore
            // (best edge check)
            if (weights[parent][cycleNode] > bestParentScore) {
                bestParent = parent;
                bestParentScore = weights[parent][cycleNode];
            }
            parentWeights[cycleNode]
                    .add(parent, weights[parent][cycleNode]);
        }
        // (set parent of cycle node)
        assert bestParentScore > Double.NEGATIVE_INFINITY;
        assert bestParentScore < Double.POSITIVE_INFINITY;
        assert bestParent >= 0;
        assert bestParentScore == parentWeights[cycleNode].getPriority();
        assert cycleNode != bestParent;
        parents[cycleNode] = bestParent;
        // --Outgoing Edges
        for (int child = 1; child < size - 1; child++) { // will never go to
            // parent
            if (!valid[child]) {
                continue;
            }
            // (vars)
            int argmax = 0; // was -1
            double max = Double.NEGATIVE_INFINITY;
            // (get best edge into the cycle)
            for (int cycleParentI = 0; cycleParentI < cycle.length; cycleParentI++) {
                int cycleParent = cycle[cycleParentI];
                if (edges[cycleParent][child] == 0) {
                    continue;
                } // no edge
                // (max)
                if (weights[cycleParent][child] > max) {
                    argmax = cycleParent;
                    max = weights[cycleParent][child];
                }
            }
            assert !Double.isNaN(max);
            assert argmax >= 0;
            // assert max > Double.NEGATIVE_INFINITY;
            assert max < Double.POSITIVE_INFINITY;
            // (create edge)
            assert cycleNode != child;
            edges[cycleNode][child] = argmax;
            weights[cycleNode][child] = max;
            // (check if new parent)
            if (cycleNode != child) {
                parentWeights[child].add(cycleNode,
                        weights[cycleNode][child]);
            }
            while (!valid[parentWeights[child].getFirst()]) {
                parentWeights[child].removeFirst();
            }
            assert child != parentWeights[child].getFirst();
            parents[child] = parentWeights[child].getFirst();
        }
    }

    private void decycle(int[] cycle) {
        // --Update Graph
        // (remove cycle node)
        int cycleNode = this.size - 1;
        this.size -= 1;
        valid[cycleNode] = false;
        // (add nodes in cycle)
        for (int node : cycle) {
            valid[node] = true;
        }
        // --Break Edge
        // (compute nodes)
        int parent = parents[cycleNode]; // parent outside of cycle
        int cycleChild = edges[parent][cycleNode]; // child in cycle
        int cyclePred = parents[cycleChild]; // parent in cycle
        assert parent >= 0;
        assert cycleChild > 0;
        assert cycleChild < this.size;
        assert cyclePred > 0;
        // (incoming edge)
        assert cycleChild != parent;
        parents[cycleChild] = parent;
        // (break cycle)
        edges[cyclePred][cycleChild] = 0;
        // --Outgoing Edges
        for (int child = 1; child < size; child++) {
            if (!valid[child]) {
                continue;
            }
            if (parents[child] == cycleNode) {
                // (update parents)
                int cycleParent = edges[cycleNode][child];
                assert child != cycleParent;
                parents[child] = cycleParent;
            }
        }
    }

    private void chuLiuEdmonds(int iter) {
        int[] cycle = cycle(iter);
        assert check(cycle);
        if (cycle != null) {
            contract(cycle);
            chuLiuEdmonds(iter + 1);
            decycle(cycle);
        }
        assert check(null);
    }

    public Pair<int[],Object[]> chuLiuEdmonds() {
        // (run algorithm)
        chuLiuEdmonds(1); // note: iter must be >= 1
        double score = 0;
        // (copy parents)
        int[] parents = new int[this.size - 1];
        for (int i = 0; i < parents.length; i++) {
            parents[i] = this.parents[i + 1] - 1;
            score += this.weights[this.parents[i+1]][i+1];
        }
        // (copy labels)
        Object[] labels = new Object[this.size - 1];
        for (int i = 0; i < parents.length; i++) {
            labels[i] = this.labels[this.parents[i + 1]][i + 1];
        }
        // (return)
        return new Pair<int[], Object[]>(parents, labels);
    }

    private void printCycle(int[] cycle) {
        System.out.print("CYCLE: ");
        for (int cycleI = 0; cycleI < cycle.length; cycleI++) {
            System.out.print("" + cycle[cycleI] + "("
                    + parents[cycle[cycleI]] + ")->");
        }
        System.out.println(cycle[0]);
    }

    private boolean check(int[] cycle) {
        // --Check Parents
        for (int child = 1; child < baseSize; child++) {
            if (parents[child] < 0) {
                return false;
            }
        }
        // --Check Edges
        for (int parent = 0; parent < baseSize; parent++) {
            if (edges[parent][0] != 0) {
                return false;
            }
            for (int child = 1; child < baseSize; child++) {
                if (edges[parent][child] < 0) {
                    return false;
                }
                if (edges[parent][child] >= baseSize) {
                    return false;
                }
            }
        }
        // --Check Cycle
        if (cycle != null) {
            for (int cycleI = 0; cycleI < cycle.length; cycleI++) {
                if (cycle[cycleI] == 0) {
                    return false;
                }
                int last = cycle[(cycleI - 1 + cycle.length) % cycle.length];
                if (parents[cycle[cycleI]] != last) {
                    return false;
                }
            }
        }
        // --Check Passed
        return true;
    }

    /** randomly attach each node to different parents */
    public boolean testOptimality() {
        int cycleNum = -1;
        for (int child = 1; child < size; child++) {
            int trueParent = parents[child];
            for (int parent = 0; parent < size; parent++) {
                if (parent == trueParent) {
                    continue;
                }
                if (weights[parent][child] - weights[trueParent][child] > 0.000001) {
                    parents[child] = parent;
                    if (cycle(cycleNum--) == null) {
                        parents[child] = trueParent;
                        System.out.println("Could replace " + trueParent
                                + "->" + child + " ("
                                + weights[trueParent][child] + ")"
                                + " with " + parent + "->" + child + " ("
                                + weights[parent][child] + ")");
                        return false;
                    }
                }
            }
            parents[child] = trueParent;
        }
        return true;
    }

    private int baseSize = -1;

    private void setBaseSize(int baseSize) {
        this.baseSize = baseSize;
    }
}

