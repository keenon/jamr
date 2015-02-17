package nlp.stamr.alignments;

import edu.stanford.nlp.ie.NumberNormalizer;
import nlp.stamr.AMR;
import nlp.stamr.AMRConstants;
import nlp.stamr.alignments.conditional.binary.*;
import nlp.stamr.alignments.conditional.types.BinaryAlignmentFeature;
import nlp.stamr.alignments.conditional.types.UnaryAlignmentFeature;
import nlp.stamr.alignments.conditional.unary.EditDistanceUnaryFeature;
import nlp.stamr.alignments.conditional.unary.LexicalUnaryFeature;
import nlp.stamr.alignments.conditional.unary.PossessiveBiasFeature;
import nlp.stamr.ontonotes.SRL;
import nlp.stamr.ontonotes.SRLSlurp;
import nlp.stamr.utils.TimingEstimator;
import edu.stanford.nlp.util.IdentityHashSet;

import java.util.*;

/**
 * Handles aligning nlp.stamr.AMR to source tokens, using an EM algorithm
 */
public class EMAligner {

    public static void align(AMR[] bank) throws InterruptedException {
        align(bank, SRLSlurp.slurp());
    }

    public static void align(AMR[] bank, SRL[] augmentBank) throws InterruptedException {
        align(bank, augmentBank, 15);
    }

    public static void align(AMR[] bank, int iterations) throws InterruptedException {
        align(bank, SRLSlurp.slurp(), iterations);
    }

    public static void align(AMR[] bank, SRL[] augmentBank, int iterations) throws InterruptedException {
        align(bank, augmentBank, iterations, 2, null, null);
    }

    public static void align(AMR[] bank, int iterations, int threadCount) throws InterruptedException {
        align(bank, SRLSlurp.slurp(), iterations, threadCount, null, null);
    }

    public static void align(AMR[] bank, int iterations, int threadCount, EMHook preIteration, EMHook postIteration) throws InterruptedException {
        align(bank, SRLSlurp.slurp(), iterations, threadCount, preIteration, postIteration);
    }

    public static void align(AMR[] bank, SRL[] augmentBank, int iterations, int threadCount, EMHook preIteration, EMHook postIteration) throws InterruptedException {

        UnaryAlignmentFeature[] globalUnaryAlignmentFeatures = new UnaryAlignmentFeature[] {
                new LexicalUnaryFeature()
                ,new EditDistanceUnaryFeature()
                ,new PossessiveBiasFeature()
        };
        BinaryAlignmentFeature[] globalBinaryAlignmentFeatures = new BinaryAlignmentFeature[] {
                new NameOffsetBinaryFeature()
                ,new ParentOffsetBinaryFeature()
                ,new DependencyPathDictionaryFeature()
                ,new DependencyDistanceFeature()
                ,new CrossingPunctuationFeature()
        };

        int fixedNodes = setupStep(bank);

        System.out.println("Read "+fixedNodes+" fixed alignment nodes to use as constraints for EM");

        System.out.println("Creating "+threadCount+" threads");

        Thread[] eStepThreads = new Thread[threadCount];
        ParallelEStep[] parallelESteps = new ParallelEStep[threadCount];
        Thread[] mStepThreads = new Thread[threadCount];
        ParallelMStep[] parallelMSteps = new ParallelMStep[threadCount];
        Thread[] alignmentThreads = new Thread[threadCount];
        ParallelAlignmentStep[] parallelAlignmentSteps = new ParallelAlignmentStep[threadCount];

        int bankSliceCursor = 0;
        int augmentBankSliceCursor = 0;

        for (int i = 0; i < threadCount; i++) {

            // Do slice of bank

            int bankSliceSize = bank.length / threadCount;
            if (i == threadCount-1) {
                bankSliceSize = bank.length - bankSliceCursor;
            }
            AMR[] bankSlice = new AMR[bankSliceSize];
            System.arraycopy(bank,bankSliceCursor,bankSlice,0,bankSliceSize);
            bankSliceCursor += bankSliceSize;

            // Do same slice of augment bank

            int augmentBankSliceSize = augmentBank.length / threadCount;
            if (i == threadCount-1) {
                augmentBankSliceSize = augmentBank.length - augmentBankSliceCursor;
            }
            SRL[] augmentBankSlice = new SRL[augmentBankSliceSize];
            System.arraycopy(augmentBank,augmentBankSliceCursor,augmentBankSlice,0,augmentBankSliceSize);
            augmentBankSliceCursor += augmentBankSliceSize;

            // Create thread slice of the dataset

            parallelESteps[i] = new ParallelEStep(bankSlice,globalUnaryAlignmentFeatures,globalBinaryAlignmentFeatures);
            parallelMSteps[i] = new ParallelMStep(bankSlice,augmentBankSlice,globalUnaryAlignmentFeatures,globalBinaryAlignmentFeatures);
            parallelAlignmentSteps[i] = new ParallelAlignmentStep(bankSlice,globalUnaryAlignmentFeatures,globalBinaryAlignmentFeatures);
        }

        TimingEstimator estimator = new TimingEstimator();
        estimator.start();

        for (int i = 0; i < iterations; i++) {
            System.out.println();
            System.out.println("EM Iteration " + (i+1)+" / "+iterations);
            System.out.println("-------");

            if (preIteration != null) {
                preIteration.hook(i);
            }

            for (UnaryAlignmentFeature unaryAlignmentFeature : globalUnaryAlignmentFeatures) unaryAlignmentFeature.clear();
            for (BinaryAlignmentFeature binaryAlignmentFeature : globalBinaryAlignmentFeatures) binaryAlignmentFeature.clear();

            System.out.println(" [M Step] Collecting soft counts ...");

            for (int t = 0; t < threadCount; t++) {
                mStepThreads[t] = new Thread(parallelMSteps[t]);
                mStepThreads[t].start();
            }
            for (int t = 0; t < threadCount; t++) {
                mStepThreads[t].join();
                for (int u = 0; u < globalUnaryAlignmentFeatures.length; u++) {
                    globalUnaryAlignmentFeatures[u].addAll(parallelMSteps[t].localUnaryAlignmentFeatures[u]);
                }
                for (int b = 0; b < globalBinaryAlignmentFeatures.length; b++) {
                    globalBinaryAlignmentFeatures[b].addAll(parallelMSteps[t].localBinaryAlignmentFeatures[b]);
                }
            }

            // Cook everything once we're done collapsing it all

            for (int u = 0; u < globalUnaryAlignmentFeatures.length; u++) {
                globalUnaryAlignmentFeatures[u].cook();
            }
            for (int b = 0; b < globalBinaryAlignmentFeatures.length; b++) {
                globalBinaryAlignmentFeatures[b].cook();
            }

            if (postIteration != null) {
                System.out.println(" [Checkin Step] Getting intermediate predictions ...");
                for (int t = 0; t < threadCount; t++) {
                    alignmentThreads[t] = new Thread(parallelAlignmentSteps[t]);
                    alignmentThreads[t].start();
                }
                for (int t = 0; t < threadCount; t++) {
                    alignmentThreads[t].join();
                }
                // doFinalAlignments(bank, globalUnaryAlignmentFeatures, globalBinaryAlignmentFeatures);
                postIteration.hook(i);
            }

            System.out.println(" [E Step] Taking soft expectations ...");

            for (int t = 0; t < threadCount; t++) {
                eStepThreads[t] = new Thread(parallelESteps[t]);
                eStepThreads[t].start();
            }
            for (int t = 0; t < threadCount; t++) {
                eStepThreads[t].join();
            }

            System.out.println();
            System.out.println(estimator.reportEstimate(i, iterations));
        }

        System.out.println(" Getting final predictions ... ");

        for (int t = 0; t < threadCount; t++) {
            alignmentThreads[t] = new Thread(parallelAlignmentSteps[t]);
            alignmentThreads[t].start();
        }
        for (int t = 0; t < threadCount; t++) {
            alignmentThreads[t].join();
        }
        // doFinalAlignments(bank, globalUnaryAlignmentFeatures, globalBinaryAlignmentFeatures);

        System.out.println("Done");

    }

    private static class ParallelMStep implements Runnable {
        public UnaryAlignmentFeature[] localUnaryAlignmentFeatures;
        public BinaryAlignmentFeature[] localBinaryAlignmentFeatures;
        public AMR[] bankSlice;
        public SRL[] augmentBankSlice;

        public ParallelMStep(AMR[] bankSlice, SRL[] augmentBankSlice, UnaryAlignmentFeature[] globalUnaryAlignmentFeatures, BinaryAlignmentFeature[] globalBinaryAlignmentFeatures) {
            this.bankSlice = bankSlice;
            this.augmentBankSlice = augmentBankSlice;

            // Clone the unary features

            localUnaryAlignmentFeatures = new UnaryAlignmentFeature[globalUnaryAlignmentFeatures.length];
            for (int i = 0; i < globalUnaryAlignmentFeatures.length; i++) {
                try {
                    localUnaryAlignmentFeatures[i] = globalUnaryAlignmentFeatures[i].getClass().getConstructor().newInstance();
                } catch (Exception e) {
                    System.out.println("Fatal: All unary features must be clonable with a zero arguments constructor. Quitting");
                    e.printStackTrace();
                    System.exit(1);
                }
            }


            // Clone the unary features

            localBinaryAlignmentFeatures = new BinaryAlignmentFeature[globalBinaryAlignmentFeatures.length];
            for (int i = 0; i < globalBinaryAlignmentFeatures.length; i++) {
                try {
                    localBinaryAlignmentFeatures[i] = globalBinaryAlignmentFeatures[i].getClass().getConstructor().newInstance();
                } catch (Exception e) {
                    System.out.println("Fatal: All binary features must be clonable with a zero arguments constructor. Quitting");
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }

        @Override
        public void run() {
            mStep(bankSlice,augmentBankSlice,localUnaryAlignmentFeatures,localBinaryAlignmentFeatures);
        }
    }

    private static class ParallelEStep implements Runnable {
        public UnaryAlignmentFeature[] globalUnaryAlignmentFeatures;
        public BinaryAlignmentFeature[] globalBinaryAlignmentFeatures;
        public AMR[] bankSlice;

        public ParallelEStep(AMR[] bankSlice, UnaryAlignmentFeature[] globalUnaryAlignmentFeatures, BinaryAlignmentFeature[] globalBinaryAlignmentFeatures) {
            this.bankSlice = bankSlice;
            this.globalUnaryAlignmentFeatures = globalUnaryAlignmentFeatures;
            this.globalBinaryAlignmentFeatures = globalBinaryAlignmentFeatures;
        }

        @Override
        public void run() {
            eStep(bankSlice,globalUnaryAlignmentFeatures,globalBinaryAlignmentFeatures);
        }
    }


    private static class ParallelAlignmentStep implements Runnable {
        public UnaryAlignmentFeature[] globalUnaryAlignmentFeatures;
        public BinaryAlignmentFeature[] globalBinaryAlignmentFeatures;
        public AMR[] bankSlice;

        public ParallelAlignmentStep(AMR[] bankSlice, UnaryAlignmentFeature[] globalUnaryAlignmentFeatures, BinaryAlignmentFeature[] globalBinaryAlignmentFeatures) {
            this.bankSlice = bankSlice;
            this.globalUnaryAlignmentFeatures = globalUnaryAlignmentFeatures;
            this.globalBinaryAlignmentFeatures = globalBinaryAlignmentFeatures;
        }

        @Override
        public void run() {
            doFinalAlignments(bankSlice,globalUnaryAlignmentFeatures,globalBinaryAlignmentFeatures);
        }
    }

    // Seed with a random starting alignment
    public static int setupStep(AMR[] bank) {
        int fixedNodes = 0;
        Random r = new Random();
        for (AMR amr : bank) {
            for (AMR.Node node : amr.nodes) {
                node.softAlignments = new double[amr.sourceTokenCount()];
                for (int i = 0; i < node.softAlignments.length; i++) {
                    node.softAlignments[i] = Math.log(1.0 / amr.sourceTokenCount());
                }
                if (!node.alignmentFixed) {
                    node.alignment = r.nextInt(amr.sourceTokenCount());
                }
                else fixedNodes++;
            }
        }
        return fixedNodes;
    }

    public static void doFinalAlignments(AMR[] bank, UnaryAlignmentFeature[] unaryAlignmentFeatures, BinaryAlignmentFeature[] binaryAlignmentFeatures) {
        for (AMR amr : bank) {
            doExactAlignment(amr,unaryAlignmentFeatures,binaryAlignmentFeatures);
        }
    }

    // During M step:
    // Need lexical generation probabilities, and location generation probabilities given parent

    public static void mStep(AMR[] bank, SRL[] augmentBank, UnaryAlignmentFeature[] unaryAlignmentFeatures, BinaryAlignmentFeature[] binaryAlignmentFeatures) {
        for (AMR amr : bank) {
            for (AMR.Node node : amr.nodes) {

                // Some nodes have observed alignments. They are not treated softly

                if (node.alignmentFixed) {
                    for (UnaryAlignmentFeature unaryAlignmentFeature : unaryAlignmentFeatures) {
                        unaryAlignmentFeature.observe(amr,node,node.alignment,1.0);
                    }
                    if (node != amr.head) {
                        for (int j = 0; j < amr.sourceTokenCount(); j++) {
                            AMR.Arc parentArc = amr.getParentArc(node);
                            for (BinaryAlignmentFeature binaryAlignmentFeature : binaryAlignmentFeatures) {
                                binaryAlignmentFeature.observe(amr,node,node.alignment,j,parentArc,Math.exp(parentArc.head.softAlignments[j]));
                            }
                        }
                    } else {
                        for (BinaryAlignmentFeature binaryAlignmentFeature : binaryAlignmentFeatures) {
                            binaryAlignmentFeature.observe(amr,node,node.alignment,0,amr.nullArc,1.0);
                        }
                    }
                }

                // Everything else must take soft expectations over all training data

                else {
                    for (int i = 0; i < amr.sourceTokenCount(); i++) {
                        for (UnaryAlignmentFeature unaryAlignmentFeature : unaryAlignmentFeatures) {
                            unaryAlignmentFeature.observe(amr,node,i,Math.exp(node.softAlignments[i]));
                        }

                        if (node != amr.head) {
                            AMR.Arc parentArc = amr.getParentArc(node);
                            for (int j = 0; j < amr.sourceTokenCount(); j++) {
                                for (BinaryAlignmentFeature binaryAlignmentFeature : binaryAlignmentFeatures) {
                                    binaryAlignmentFeature.observe(amr,node,i,j,parentArc,Math.exp(node.softAlignments[i] + parentArc.head.softAlignments[j]));
                                }
                            }
                        } else {
                            for (BinaryAlignmentFeature binaryAlignmentFeature : binaryAlignmentFeatures) {
                                binaryAlignmentFeature.observe(amr,node,i,0,amr.nullArc,Math.exp(node.softAlignments[i]));
                            }
                        }
                    }
                }
            }
        }
        for (SRL srl : augmentBank) {
            for (UnaryAlignmentFeature unaryAlignmentFeature : unaryAlignmentFeatures) {
                unaryAlignmentFeature.observe(srl);
            }
            for (BinaryAlignmentFeature binaryAlignmentFeature : binaryAlignmentFeatures) {
                binaryAlignmentFeature.observe(srl);
            }
        }
    }

    // During E step:
    // Can use unambiguous-exact-match to guide things. Otherwise assign based on maximum likelihood.
    // Try every combination of children attachments to coref parents, take highest probability joint alignment

    public static void eStep(AMR[] bank, UnaryAlignmentFeature[] unaryAlignmentFeatures, BinaryAlignmentFeature[] binaryAlignmentFeatures) {
        for (AMR amr : bank) {
            doExactInference(amr, unaryAlignmentFeatures, binaryAlignmentFeatures);
        }
    }

    public static void doExactAlignment(AMR amr, UnaryAlignmentFeature[] unaryAlignmentFeatures, BinaryAlignmentFeature[] binaryAlignmentFeatures) {
        PGMTree tree = prepareExactInferenceGraph(amr, unaryAlignmentFeatures, binaryAlignmentFeatures);
        tree.getMLE();

        // If we can do any swaps to minimize non-projectivity, then we do them

        Map<AMR.Node,Boolean> fixedAlignments = new IdentityHashMap<AMR.Node, Boolean>();
        for (AMR.Node node : amr.nodes) fixedAlignments.put(node, node.alignmentFixed);

        if (Projectifier.swapToMinimizeNonProjectivity(amr)) {
            tree = prepareExactInferenceGraph(amr, unaryAlignmentFeatures, binaryAlignmentFeatures);
            tree.getMLE();
        }

        for (AMR.Node node : amr.nodes) node.alignmentFixed = fixedAlignments.get(node);
    }

    public static void doExactInference(AMR amr, UnaryAlignmentFeature[] unaryAlignmentFeatures, BinaryAlignmentFeature[] binaryAlignmentFeatures) {
        PGMTree tree = prepareExactInferenceGraph(amr,unaryAlignmentFeatures,binaryAlignmentFeatures);
        tree.getMarginals();
    }

    public static PGMTree prepareExactInferenceGraph(AMR amr, UnaryAlignmentFeature[] unaryAlignmentFeatures, BinaryAlignmentFeature[] binaryAlignmentFeatures) {
        PGMTree tree = new PGMTree(amr);

        // Prepares factors for a MLE assignment

        for (AMR.Node node : amr.nodes) {
            AMR.Arc parentArc = amr.getParentArc(node);
            double[] singleFactor = new double[amr.sourceTokenCount()];
            double[][] jointFactor = new double[amr.sourceTokenCount()][amr.sourceTokenCount()];

            double singleFactorSum = 0.0;
            double[] jointFactorSum = new double[amr.sourceTokenCount()];

            // Only put matches as factors, limits possibilities to screw up somewhat

            Set<Integer> matches = getMatches(node, amr);
            for (int match : matches) {

                double lexicalProb = 1.0;
                for (UnaryAlignmentFeature unaryAlignmentFeature : unaryAlignmentFeatures) {
                    lexicalProb *= unaryAlignmentFeature.score(amr, node, match);
                }

                if (matches.size() == 1) lexicalProb = 1.0;
                singleFactorSum += lexicalProb;
                singleFactor[match] = lexicalProb;

                Set<Integer> parentMatches = getMatches(parentArc.head, amr);
                for (int parentMatch : parentMatches) {

                    double parentProb = 1.0;
                    for (BinaryAlignmentFeature binaryAlignmentFeature : binaryAlignmentFeatures) {
                        parentProb *= binaryAlignmentFeature.score(amr, node, match, parentMatch, parentArc);
                    }

                    jointFactorSum[parentMatch] += parentProb;
                    jointFactor[parentMatch][match] = parentProb;
                }
            }

            // Normalize values, and log everything

            for (int i = 0; i < amr.sourceTokenCount(); i++) {
                if (singleFactorSum != 0)
                    singleFactor[i] = Math.log(singleFactor[i] / singleFactorSum);
                else
                    singleFactor[i] = Math.log(1.0 / amr.sourceTokenCount());
                for (int j = 0; j < amr.sourceTokenCount(); j++) {
                    if (jointFactorSum[i] != 0)
                        jointFactor[i][j] = Math.log(jointFactor[i][j] / jointFactorSum[i]);
                    else
                        jointFactor[i][j] = Math.log(1.0 / amr.sourceTokenCount());
                }
            }

            tree.setFactor(parentArc,jointFactor);
            tree.setFactor(node,singleFactor);
        }

        return tree;
    }

    public static boolean isNullNode(AMR.Node node, AMR amr) {

        // Look for hallucinated NER nodes

        if (AMRConstants.nerTaxonomy.contains(node.title)) {
            if (amr.outgoingArcs.containsKey(node)) {
                boolean arcsContainArgOfOrName = false;
                for (AMR.Arc arc : amr.outgoingArcs.get(node)) {
                    if ((arc.title.startsWith("ARG") && arc.title.endsWith("-of")) || arc.title.equalsIgnoreCase("name")) {
                        arcsContainArgOfOrName = true;
                        break;
                    }
                }
                if (arcsContainArgOfOrName) {
                    return true;
                }
            }
        }

        // Look for name nodes

        if (node.title.equals("name")) {
            if (amr.outgoingArcs.containsKey(node)) {
                boolean allArcsOps = true;
                for (AMR.Arc arc : amr.outgoingArcs.get(node)) {
                    if (!arc.title.startsWith("op")) {
                        allArcsOps = false;
                        break;
                    }
                }
                if (allArcsOps) return true;
            }
        }

        // Look for quantities

        if (AMRConstants.quantityTaxonomy.contains(node.title.toLowerCase())) {
            return true;
        }

        // Otherwise ignore it

        return false;
    }

    public static Set<Integer> getMatches(AMR.Node node, AMR amr) {
        if (amr.matchesCache.containsKey(node)) return amr.matchesCache.get(node);

        Set<Integer> matches = new IdentityHashSet<Integer>();

        // If the alignment is fixed, force a fixed alignment

        if (node.alignmentFixed) {
            matches.add(node.alignment);
            amr.matchesCache.put(node,matches);
            return matches;
        }

        // Quote nodes get forced to align to the quotes,
        // and must align to contiguous regions

        if (node.type == AMR.NodeType.QUOTE) {

            AMR.Arc parentArc = amr.getParentArc(node);

            // Treat names specially, force them to align contiguously

            if (parentArc.head.title.equals("name")) {

                int opNum = Integer.parseInt(parentArc.title.replace("op",""));
                AMR.Node nameNode = parentArc.head;

                for (int i = 0; i < amr.sourceTokenCount(); i++) {

                    // If we're a known confounder, then look for exact matches according to the dictionary

                    if (AMRConstants.confounderToName.containsKey(amr.getSourceToken(i))) {
                        if (Arrays.asList(AMRConstants.confounderToName.get(amr.getSourceToken(i))).contains(node.title)) {
                            matches.add(i);
                        }
                    }

                    // Align names contiguously, if we're aligning to novel tokens

                    if (amr.getSourceToken(i).equals(node.title)) {
                        boolean wholeSetMatches = true;
                        for (AMR.Arc arc : amr.outgoingArcs.get(nameNode)) {
                            if (arc.title.contains("op")) {
                                int otherOpNum = Integer.parseInt(arc.title.replace("op", ""));
                                if (otherOpNum - opNum + i >= 0 && otherOpNum - opNum + i < amr.sourceText.length) {
                                    if (!amr.getSourceToken(otherOpNum - opNum + i).equalsIgnoreCase(arc.tail.title)) {
                                        wholeSetMatches = false;
                                        break;
                                    }
                                }
                            }
                        }
                        if (wholeSetMatches)
                            matches.add(i);
                    }
                }

                amr.matchesCache.put(node,matches);
                return matches;
            }

            for (int i = 0; i < amr.sourceTokenCount(); i++) {
                if (amr.getSourceToken(i).equalsIgnoreCase(node.title)) matches.add(i);
            }
            if (matches.size() == 0) {
                for (int i = 0; i < amr.sourceTokenCount(); i++) {
                    // First 3 characters match we consider it a match
                    if (amr.getSourceToken(i).length() > 3 && node.title.length() > 3) {
                        if (amr.getSourceToken(i).substring(0, 3).equalsIgnoreCase(node.title.substring(0, 3))) matches.add(i);
                    }
                }
            }
            amr.matchesCache.put(node,matches);
            return matches;
        }

        // Make sure that we only list name tokens for things that are strict parents of names

        if (node.title.equals("name") || (amr.outgoingArcs.containsKey(node) && amr.outgoingArcs.get(node).size() == 1 && amr.outgoingArcs.get(node).get(0).title.equals("name"))) {
            for (AMR.Node child : amr.depthFirstSearchNode(node)) {
                if (child.type == AMR.NodeType.QUOTE) {
                    // Return only the first token as a potential match
                    if (amr.getParentArc(child).title.equals("op1")) {
                        matches = getMatches(child,amr);
                    }
                }
            }
            amr.matchesCache.put(node,matches);
            return matches;
        }

        // Pin nominals to their important ARGx-of child

        if (AMRConstants.nerTaxonomy.contains(node.title)) {
            if (amr.outgoingArcs.containsKey(node) && amr.outgoingArcs.get(node).size() >= 1) {
                for (int i = 0; i < amr.outgoingArcs.get(node).size(); i++) {
                    if (amr.outgoingArcs.get(node).get(i).title.endsWith("-of") && amr.outgoingArcs.get(node).get(i).title.contains("ARG")) {
                        AMR.Node child = amr.outgoingArcs.get(node).get(i).tail;
                        matches = getMatches(child, amr);
                        if (matches.size() > 0) {
                            amr.matchesCache.put(node, matches);
                            return matches;
                        }
                    }
                }
            }
        }

        // Pin quantities to unit children

        if (node.title.contains("-quantity")) {
            if (amr.outgoingArcs.containsKey(node) && amr.outgoingArcs.get(node).size() > 0) {
                for (AMR.Arc arc : amr.outgoingArcs.get(node)) {
                    if (arc.title.equals("unit")) {
                        AMR.Node child = arc.tail;
                        matches = getMatches(child, amr);
                        if (matches.size() > 0) {
                            amr.matchesCache.put(node,matches);
                            return matches;
                        }
                    }
                }
            }
        }

        // Look for raw exact matches. If there's only 1 of us, and only 1 of them, then just match up

        int otherNodesWithSameName = 0;
        for (AMR.Node otherNode : amr.nodes) {
            if (otherNode != node && otherNode.title.equals(node.title)) otherNodesWithSameName++;
        }
        if (otherNodesWithSameName == 0) {
            for (int i = 0; i < amr.sourceTokenCount(); i++) {
                if (amr.getSourceToken(i).equals(node.title) && !node.title.equals("-")) {
                    matches.add(i);
                }
                else if (node.type == AMR.NodeType.VALUE) {
                    if (!amr.getSourceToken(i).equalsIgnoreCase("a")) {
                        try {
                            if (NumberNormalizer.wordToNumber(amr.getSourceToken(i)).equals(Integer.parseInt(node.title))) {
                                matches.add(i);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            if (matches.size() == 1) {
                amr.matchesCache.put(node,matches);
                return matches;
            }
        }

        Set<String> textMatches = AMRConstants.getCommonAMRisms(amr, node);

        if (textMatches.size() > 0) {
            for (int i = 0; i < amr.sourceTokenCount(); i++) {
                String token = amr.getSourceToken(i).toLowerCase();

                if (token.startsWith("anti")) {
                    if (textMatches.contains("oppose-01")) {
                        matches.add(i);
                    }
                    if (textMatches.contains(AMRConstants.trimSuffix(token.replaceAll("anti-", "").replaceAll("anti", "")))) {
                        matches.add(i);
                    }
                }

                if (token.length() == 6 && node.title.equals("date-entity")) {
                    try {
                        int ignored = Integer.parseInt(token);
                        matches.add(i);
                    }
                    catch (Exception ignored) {}
                }

                if (textMatches.contains(token)) {
                    matches.add(i);
                }
                else if (textMatches.contains(amr.annotationWrapper.getLemmaAtIndex(i))) {
                    matches.add(i);
                }
                else if (textMatches.contains(AMRConstants.trimSuffix(token))) {
                    matches.add(i);
                }
                else if (node.type == AMR.NodeType.VALUE) {
                    if (!token.equalsIgnoreCase("a")) {
                        try {
                            if (NumberNormalizer.wordToNumber(token).equals(Integer.parseInt(node.title))) {
                                matches.add(i);
                            }
                        } catch (Exception ignored) {}
                    }
                }
                else {
                    // Add long prefix matches as exact matches
                    int prefixMatchLength = 3;
                    if (token.length() > prefixMatchLength) {
                        for (String textMatch : textMatches) {
                            if (textMatch.length() > prefixMatchLength) {
                                if (textMatch.substring(0, prefixMatchLength).equals(token.substring(0, prefixMatchLength))) matches.add(i);
                            }
                        }
                    }
                }
            }
        }

        if (matches.size() == 0) {

            // Otherwise add all nodes in as potential matches, except nodes that are already perfect matches for quote nodes

            for (int i = 0; i < amr.sourceTokenCount(); i++) {
                if (!amr.hasQuoteNodeFor(i) || AMRConstants.nerTaxonomy.contains(node.title) || node.title.equals("name")) {
                    matches.add(i);
                }
            }
        }

        amr.matchesCache.put(node,matches);
        return matches;
    }

}
