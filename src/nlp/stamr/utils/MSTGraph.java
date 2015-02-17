package nlp.stamr.utils;

import edu.stanford.nlp.util.IdentityHashSet;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Managed a graph of nodes, and stitching them together with Edmonds' algorithm
 */
public class MSTGraph {
    Map<Integer,Set<Triple<Integer,String,Double>>> arcs = new HashMap<Integer, Set<Triple<Integer, String, Double>>>();


    private void addNode(int k) {
        if (!arcs.containsKey(k)) {
            arcs.put(k, new HashSet<Triple<Integer, String, Double>>());
        }
    }

    public void addArc(int head, int tail, String type, double score) {
        addNode(head);
        addNode(tail);
        arcs.get(head).add(new Triple<Integer, String, Double>(tail, type, score));
    }

    public Map<Integer,Set<Pair<String,Integer>>> getMST(boolean debug) {

        Map<Integer, Set<Pair<String,Integer>>> graph = new HashMap<Integer, Set<Pair<String,Integer>>>();

        // Map everything onto a sequential set of ints

        Map<Integer,Integer> nodesToSequenceMap = new HashMap<Integer, Integer>();
        Map<Integer,Integer> sequenceToNodes = new HashMap<Integer, Integer>();

        // Make sure 0 is special, cause DGraph treats it as special
        sequenceToNodes.put(0, 0);
        nodesToSequenceMap.put(0, 0);

        for (int i : arcs.keySet()) {
            if (!nodesToSequenceMap.containsKey(i)) {
                sequenceToNodes.put(nodesToSequenceMap.size(), i);
                nodesToSequenceMap.put(i, nodesToSequenceMap.size());
            }
        }

        int numNodes = nodesToSequenceMap.size();
        if (numNodes <= 1) {
            // We have no nodes here except artificial root...
            return graph;
        }
        if (numNodes == 2) {
            graph.put(0, new HashSet<Pair<String,Integer>>(){{
                add(new Pair<>("ROOT",1));
            }});
            return graph; // No arcs to be had here, I'm afraid
        }

        double[][] weights = new double[numNodes][numNodes];
        String[][] arcLabels = new String[numNodes][numNodes];

        // Initialize all arcs to "impossible"

        for (int i = 0; i < weights.length; i++) {
            for (int j = 0; j < weights.length; j++) {
                weights[i][j] = Double.NEGATIVE_INFINITY;
            }
        }

        // Set the weights for the graph into the arrays.

        for (int a : arcs.keySet()) {
            for (Triple<Integer,String,Double> arc : arcs.get(a)) {
                int b = arc.first;

                int i = nodesToSequenceMap.get(a);
                int j = nodesToSequenceMap.get(b);

                arcLabels[i][j] = arc.second;
                weights[i][j] = arc.third;
            }
        }

        for (int node : nodesToSequenceMap.keySet()) {
            if (node == 0) continue;
            int i = nodesToSequenceMap.get(node);
            double max = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < weights.length; j++) {
                if (weights[j][i] > max) max = weights[j][i];
            }
            if (max == Double.NEGATIVE_INFINITY) {
                System.out.println("Broke on node "+node);
                assert(max > Double.NEGATIVE_INFINITY);
            }
        }

        // Add root arcs

        for (int i = 1; i < numNodes; i++) {
            arcLabels[0][i] = "ROOT";
        }

        double[] rootWeights = new double[numNodes];
        for (int i = 0; i < numNodes; i++) {
            rootWeights[i] = weights[0][i];
        }

        Pair<int[], Object[]> outArcs = new Pair<int[], Object[]>();
        double maxScore = Double.NEGATIVE_INFINITY;

        // Ensure only 1 root per graph

        for (int r = 1; r < numNodes; r++) {

            // We're trying to make a ROOT that the arc existence classifier calls impossible
            if (weights[0][r] == Double.NEGATIVE_INFINITY) {
                continue;
            }

            // QUOTE, VALUE nodes make bad roots
            boolean hasOutgoingArcs = false;
            for (int i = 0; i < numNodes; i++) {
                if (weights[r][i] > Double.NEGATIVE_INFINITY) {
                    hasOutgoingArcs = true;
                    break;
                }
            }
            if (!hasOutgoingArcs) continue;

            for (int i = 1; i < numNodes; i++) {
                weights[0][i] = i == r ? rootWeights[i] : Double.NEGATIVE_INFINITY;
            }

            DGraph dGraph = new DGraph(weights, arcLabels);
            Pair<int[], Object[]> possibleArcs = dGraph.chuLiuEdmonds();
            if (!dGraph.testOptimality()) {
                throw new IllegalStateException("Can't have a non-optimal graph solution!");
            }

            double score = 0.0;
            for (int i = 1; i < numNodes; i++) {
                score += weights[possibleArcs.first[i - 1] + 1][i];
            }
            if (score > maxScore) {
                maxScore = score;
                outArcs = possibleArcs;
            }
        }

        int[] parents = outArcs.first;
        Object[] parentArcs = outArcs.second;

        if (debug) {
            for (int i = 0; i < numNodes; i++) {
                System.out.println(i+": "+parents[i]+" with arc "+parentArcs[i].toString());
            }
        }

        if (parents == null) {
            System.out.println("Break");
        }

        // Decode the graph

        for (int i = 1; i < numNodes; i++) {
            int a = 0;
            if (parents[i-1] != -1) {
                a = sequenceToNodes.get(parents[i-1]+1);
            }
            int b = sequenceToNodes.get(i);
            String s = (String)parentArcs[i-1];
            graph.putIfAbsent(a, new IdentityHashSet<Pair<String, Integer>>());
            graph.get(a).add(new Pair<String, Integer>(s, b));
        }

        return graph;
    }
}
