package nlp.stamr.alignments;

import nlp.stamr.AMR;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Models a Bayesian PGM Tree, so we can do efficient factor elimination starting at the leaves to get a true MLE assignment for alignments.
 */
public class PGMTree {

    public enum MessagePassingType {
        MAX_SUM,
        SUM_PRODUCT
    }

    public class SingleFactor {
        double[] originalFactor;
        double[] incomingMessageUpwards;
        double[] incomingMessageDownwards;

        public SingleFactor(double[] originalFactor) {
            this.originalFactor = originalFactor;
            incomingMessageUpwards = new double[originalFactor.length];
            incomingMessageDownwards = new double[originalFactor.length];
        }

        public double[] getOutgoingMessageUpwards() {
            double[] message = new double[originalFactor.length];
            for (int i = 0; i < originalFactor.length; i++) {
                message[i] = originalFactor[i] + incomingMessageUpwards[i];
            }
            return message;
        }

        public double[] getMarginal() {
            double[] marginal = new double[originalFactor.length];
            for (int i = 0; i < originalFactor.length; i++) {
                marginal[i] = originalFactor[i] + incomingMessageUpwards[i] + incomingMessageDownwards[i];
            }
            normalizeLogDistribution(marginal);
            return marginal;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            printSingleFactor(sb,this);
            return sb.toString();
        }
    }

    public class JointFactor {

        // First index is parent value, second is child
        double[][] factor;
        double[] upwardsMessage;
        double[] downwardsMessage;

        public JointFactor(double[][] factor) {
            this.factor = factor;
        }

        void calculateUpwardsMessage(double[] incomingMessages, MessagePassingType type) {
            upwardsMessage = new double[possibleAlignments];

            if (type == MessagePassingType.MAX_SUM) {
                for (int i = 0; i < possibleAlignments; i++) {
                    upwardsMessage[i] = Double.NEGATIVE_INFINITY;
                }
            }

            for (int parent = 0; parent < possibleAlignments; parent++) {
                for (int child = 0; child < possibleAlignments; child++) {
                    if (type == MessagePassingType.SUM_PRODUCT) {
                        upwardsMessage[parent] += Math.exp(factor[parent][child] + incomingMessages[child]);
                    }
                    else if (type == MessagePassingType.MAX_SUM) {
                        double prob = factor[parent][child] + incomingMessages[child];
                        if (prob > upwardsMessage[parent]) {
                            upwardsMessage[parent] = prob;
                        }
                    }
                }
            }

            if (type == MessagePassingType.SUM_PRODUCT) {
                for (int i = 0; i < possibleAlignments; i++) {
                    upwardsMessage[i] = Math.log(upwardsMessage[i]);
                }
            }
        }

        void calculateDownwardsMessage(double[] incomingMessages, MessagePassingType type) {
            downwardsMessage = new double[possibleAlignments];

            if (type == MessagePassingType.MAX_SUM) {
                for (int i = 0; i < possibleAlignments; i++) {
                    downwardsMessage[i] = Double.NEGATIVE_INFINITY;
                }
            }
            else if (type == MessagePassingType.SUM_PRODUCT) {
                for (int parent = 0; parent < possibleAlignments; parent++) {
                    for (int child = 0; child < possibleAlignments; child++) {
                        downwardsMessage[child] += Math.exp(factor[parent][child] + incomingMessages[parent]);
                    }
                }
                for (int i = 0; i < possibleAlignments; i++) {
                    downwardsMessage[i] = Math.log(downwardsMessage[i]);
                }
            }
        }

        double[] childFactorGivenParent(int parent) {
            return Arrays.copyOf(factor[parent],possibleAlignments);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            printJointFactor(sb,this);
            return sb.toString();
        }
    }

    AMR graph;
    int possibleAlignments;
    Map<AMR.Node, SingleFactor> singleFactorMap = new IdentityHashMap<AMR.Node, SingleFactor>();
    Map<AMR.Arc, JointFactor> jointFactorMap = new IdentityHashMap<AMR.Arc, JointFactor>();

    public PGMTree(AMR graph) {
        this.graph = graph;
        possibleAlignments = graph.sourceTokenCount();
    }

    public void setFactor(AMR.Arc arc, double[][] factor) {
        jointFactorMap.put(arc,new JointFactor(factor));
    }

    public void setFactor(AMR.Node node, double[] factor) {
        singleFactorMap.put(node,new SingleFactor(factor));
    }

    public double[] getMarginal(AMR.Node node) {
        return singleFactorMap.get(node).getMarginal();
    }


    public void passMessages(MessagePassingType type) {
        List<AMR.Node> topologicalSort = graph.topologicalSortOnlyConnected();

        // Do VE in reverse topological order to get messages heading for the root

        for (int i = topologicalSort.size()-1; i >= 0; i--) {
            AMR.Node node = topologicalSort.get(i);
            SingleFactor sf = singleFactorMap.get(node);

            // Nodes that have children must use all incoming messages
            if (graph.outgoingArcs.containsKey(node)) {
                for (AMR.Arc outgoingArc : graph.outgoingArcs.get(node)) {
                    JointFactor jf = jointFactorMap.get(outgoingArc);
                    jf.calculateUpwardsMessage(singleFactorMap.get(outgoingArc.tail).getOutgoingMessageUpwards(),type);
                    for (int j = 0; j < possibleAlignments; j++) {
                        sf.incomingMessageUpwards[j] += jf.upwardsMessage[j];
                    }
                }
            }
        }

        // Complete message passing back down the tree

        for (AMR.Node node : topologicalSort) {
            SingleFactor sf = singleFactorMap.get(node);
            double[] parentMessage = new double[possibleAlignments];
            if (node != graph.head) {
                assert(graph.getParentArc(node) != null);
                parentMessage = jointFactorMap.get(graph.getParentArc(node)).downwardsMessage;
            }
            if (graph.outgoingArcs.containsKey(node)) {
                List<AMR.Arc> outgoingArcs = graph.outgoingArcs.get(node);
                for (int i = 0; i < outgoingArcs.size(); i++) {
                    AMR.Arc outgoingArc = outgoingArcs.get(i);
                    JointFactor jf = jointFactorMap.get(outgoingArc);
                    SingleFactor tailSf = singleFactorMap.get(outgoingArc.tail);

                    // Sum over all messages, except the one we're passing out
                    double[] messageSum = new double[possibleAlignments];
                    for (int j = 0; j < outgoingArcs.size(); j++) {
                        if (j != i) {
                            for (int k = 0; k < possibleAlignments; k++) {
                                messageSum[k] += jointFactorMap.get(outgoingArcs.get(j)).upwardsMessage[k];
                            }
                        }
                    }
                    for (int k = 0; k < possibleAlignments; k++) {
                        messageSum[k] += sf.originalFactor[k];
                        try {
                            messageSum[k] += parentMessage[k];
                        }
                        catch (NullPointerException e) {
                            e.printStackTrace();
                        }
                    }

                    jf.calculateDownwardsMessage(messageSum,type);
                    tailSf.incomingMessageDownwards = jf.downwardsMessage;
                }
            }
        }
    }

    public void getMarginals() {
        passMessages(MessagePassingType.SUM_PRODUCT);

        for (AMR.Node node : graph.nodes) {
            node.softAlignments = singleFactorMap.get(node).getMarginal();
        }
    }

    public double getMLE() {
        passMessages(MessagePassingType.MAX_SUM);

        double[] headMarginal = singleFactorMap.get(graph.head).getMarginal();

        graph.head.alignment = getMaxEntry(headMarginal);

        double jointProbability = headMarginal[graph.head.alignment];

        List<AMR.Node> topologicalSort = graph.topologicalSort();
        for (AMR.Node node : topologicalSort) {
            if (node != graph.head) {
                AMR.Arc parentArc = graph.getParentArc(node);
                double[] alignmentDistribution = jointFactorMap.get(parentArc).childFactorGivenParent(parentArc.head.alignment);

                SingleFactor sf = singleFactorMap.get(node);
                for (int i = 0; i < possibleAlignments; i++) {
                    alignmentDistribution[i] += sf.originalFactor[i];
                }
                normalizeLogDistribution(alignmentDistribution);
                node.alignment = getMaxEntry(alignmentDistribution);
                jointProbability += alignmentDistribution[node.alignment];
            }
        }

        return jointProbability;
    }

    public static int getMaxEntry(double[] distribution) {
        int max = 0;
        double maxValue = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < distribution.length; i++) {
            if (distribution[i] > maxValue) {
                maxValue = distribution[i];
                max = i;
            }
        }
        return max;
    }

    public static void normalizeLogDistribution(double[] distribution) {
        double sum = 0.0;
        for (int i = 0; i < distribution.length; i++) {
            sum += Math.exp(distribution[i]);
        }
        double logSum = Math.log(sum);
        for (int i = 0; i < distribution.length; i++) {
            distribution[i] -= logSum;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PGM:");
        for (AMR.Node node : graph.nodes) {
            sb.append("\n").append(node.title).append(":\n");
            printSingleFactor(sb,singleFactorMap.get(node));
        }
        for (AMR.Arc arc : graph.arcs) {
            sb.append("\n").append(arc.title).append(":\n");
            printJointFactor(sb,jointFactorMap.get(arc));
        }
        return sb.toString();
    }

    public void printSingleFactor(StringBuilder sb, SingleFactor sf) {
        sb.append("\tFactor");
        sb.append("\n\t\t[");
        for (int i = 0; i < sf.originalFactor.length; i++) {
            sb.append("\n\t\t\t").append(i).append(": ").append(Math.exp(sf.originalFactor[i]));
        }
        sb.append("\n\t\t]");

        sb.append("\n\tIncoming Message Upwards");
        sb.append("\n\t\t[");
        for (int i = 0; i < sf.incomingMessageUpwards.length; i++) {
            sb.append("\n\t\t\t").append(i).append(": ").append(Math.exp(sf.incomingMessageUpwards[i]));
        }
        sb.append("\n\t\t]");

        sb.append("\n\tIncoming Message Downwards");
        sb.append("\n\t\t[");
        for (int i = 0; i < sf.incomingMessageDownwards.length; i++) {
            sb.append("\n\t\t\t").append(i).append(": ").append(Math.exp(sf.incomingMessageDownwards[i]));
        }
        sb.append("\n\t\t]");
    }

    public void printJointFactor(StringBuilder sb, JointFactor jf) {
        sb.append("\n\t[");
        for (int i = 0; i < jf.factor.length; i++) {
            for (int j = 0; j < jf.factor[0].length; j++) {
                sb.append("\n\t\t").append(i).append(",").append(j).append(": ").append(Math.exp(jf.factor[i][j]));
            }
        }

        sb.append("\n\tMessage Upwards");
        sb.append("\n\t\t[");
        for (int i = 0; i < jf.upwardsMessage.length; i++) {
            sb.append("\n\t\t\t").append(i).append(": ").append(Math.exp(jf.upwardsMessage[i]));
        }
        sb.append("\n\t\t]");

        sb.append("\n\tMessage Downwards");
        sb.append("\n\t\t[");
        for (int i = 0; i < jf.downwardsMessage.length; i++) {
            sb.append("\n\t\t\t").append(i).append(": ").append(Math.exp(jf.downwardsMessage[i]));
        }
        sb.append("\n\t\t]");

        sb.append("\n\t]");
    }

    // Debug code
    // Calculates the whole joint table in memory at once. Exponentially inefficient.
    // Useful for verifying toy examples are getting the right values.

    public String debugBruteForce() {
        StringBuilder sb = new StringBuilder();
        double[] bruteForce = fillInBruteForce();
        List<AMR.Node> topologicalSort = graph.topologicalSort();
        sb.append("Ordering: ");
        for (AMR.Node node : topologicalSort) sb.append(node.title).append(" ");
        sb.append("\n");
        for (int i = 0; i < bruteForce.length; i++) {
            for (int n = 0; n < topologicalSort.size(); n++) {
                sb.append(getAssignmentAtIndex(i, n)).append(" ");
            }
            sb.append(": ").append(Math.exp(bruteForce[i])).append("\n");
        }
        return sb.toString();
    }

    public double[] getMarginalBruteForce(AMR.Node node) {
        List<AMR.Node> topologicalSort = graph.topologicalSort();
        int n = topologicalSort.indexOf(node);
        double[] marginal = new double[possibleAlignments];
        double[] bruteForce = fillInBruteForce();
        for (int i = 0; i < bruteForce.length; i++) {
            marginal[getAssignmentAtIndex(i,n)] += Math.exp(bruteForce[i]);
        }
        for (int i = 0; i < marginal.length; i++) {
            marginal[i] = Math.log(marginal[i]);
        }
        normalizeLogDistribution(marginal);
        return marginal;
    }

    public double[] fillInBruteForce() {
        double[] totalDistribution = new double[(int)(Math.round(Math.pow(possibleAlignments,graph.nodes.size())))];
        List<AMR.Node> topologicalSort = graph.topologicalSort();
        for (int i = 0; i < totalDistribution.length; i++) {
            for (int n = 0; n < topologicalSort.size(); n++) {
                AMR.Node node = topologicalSort.get(n);
                node.alignment = getAssignmentAtIndex(i,n);
                totalDistribution[i] += singleFactorMap.get(node).originalFactor[node.alignment];
            }
            for (AMR.Arc arc : graph.arcs) {
                totalDistribution[i] += jointFactorMap.get(arc).factor[arc.head.alignment][arc.tail.alignment];
            }
        }
        return totalDistribution;
    }

    private int getAssignmentAtIndex(int i, int n) {
        int pow = (int)(Math.round(Math.pow(possibleAlignments,n)));
        int assignment = (i / pow) % possibleAlignments;
        return assignment;
    }

}
