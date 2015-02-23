package nlp.stamr.alignments;

import nlp.stamr.AMR;
import nlp.stamr.AMRSlurp;
import nlp.stamr.ontonotes.SRLSlurp;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles testing alignments
 */
public class AlignmentTester {

    public static void main(String[] args) throws IOException, InterruptedException {
        AMR[] lpBank = AMRSlurp.slurp("src/test/resources/amr-bank-v1.2-human-assisted.txt", AMRSlurp.Format.LDC);
        AMR[] smallbank = new AMR[300];
        for (int i = 0; i < smallbank.length; i++) {
            smallbank[i] = lpBank[i];
        }

        int folds = 2;
        int iterations = 2;
        int threads = 64;

        SRLSlurp.ignored = true;
        testBank(smallbank, folds, threads, iterations, "src/test/resources/experimental-results/smallbank-no-ontonotes");

        /*
        SRLSlurp.ignored = false;
        testBank(smallbank, folds, threads, iterations, "src/test/resources/results/smallbank-with-ontonotes");

        SRLSlurp.ignored = true;
        testBank(lpBank, folds, threads, iterations, "src/test/resources/results/lpbank-no-ontonotes");
        SRLSlurp.ignored = false;
        testBank(lpBank, folds, threads, iterations, "src/test/resources/results/lpbank-with-ontonotes");

        AMR[] bank = AMRSlurp.slurp("src/test/resources/amr-bank-v1.2-human-assisted.txt", AMRSlurp.Format.LDC, "src/test/resources/bigbank.txt", AMRSlurp.Format.LDC);

        SRLSlurp.ignored = true;
        testBank(bank, folds, threads, iterations, "src/test/resources/results/bigbank-no-ontonotes");
        SRLSlurp.ignored = false;
        testBank(bank, folds, threads, iterations, "src/test/resources/results/bigbank-with-ontonotes");
        */
    }

    private static class AlignmentKeeper {
        Map<AMR.Node, Integer> mappings = new IdentityHashMap<AMR.Node, Integer>();

        public void saveAndConceal(AMR amr) {
            amr.matchesCache.clear();
            mappings.clear();
            for (AMR.Node node : amr.nodes) {
                if (node.alignmentFixed) {
                    mappings.put(node,node.alignment);
                    node.alignmentFixed = false;
                    node.alignment = 0;
                }
            }
        }

        public void restore(AMR amr) {
            amr.matchesCache.clear();
            for (AMR.Node node : amr.nodes) {
                if (mappings.containsKey(node)) {
                    node.alignmentFixed = true;
                    node.alignment = mappings.get(node);
                }
            }
        }

        public int countCorrect(AMR amr) {
            int correct = 0;
            for (AMR.Node node : amr.nodes) {
                if (mappings.containsKey(node)) {
                    if (node.alignment == mappings.get(node)) correct++;
                    else {
                        node.testAlignment = node.alignment;
                    }
                }
            }
            return correct;
        }

        public int countMeasured(AMR amr) {
            return mappings.size();
        }
    }

    public static double[] testBankRuleBased(AMR[] train, AMR[] test) {

        AlignmentKeeper[] keepers = new AlignmentKeeper[test.length];
        int correct = 0;
        int total = 0;
        for (int i = 0; i < test.length; i++) {
            keepers[i] = new AlignmentKeeper();
            keepers[i].saveAndConceal(test[i]);
        }
        EasyFirstAligner.align(test);
        for (int i = 0; i < test.length; i++) {
            correct += keepers[i].countCorrect(test[i]);
            total += keepers[i].countMeasured(test[i]);
            keepers[i].restore(test[i]);
        }
        double perc = (double)correct / (double) total;
        System.out.println("Correct percentage: " + perc);

        return new double[]{
                perc
        };
    }

    public static double[] testBankRegenerative(AMR[] train, AMR[] test) {
        AlignmentKeeper[] keepers = new AlignmentKeeper[test.length];
        int correct = 0;
        int total = 0;
        for (int i = 0; i < test.length; i++) {
            keepers[i] = new AlignmentKeeper();
            keepers[i].saveAndConceal(test[i]);
        }
        RegenerativeAligner.align(train);
        for (int i = 0; i < test.length; i++) {
            correct += keepers[i].countCorrect(test[i]);
            total += keepers[i].countMeasured(test[i]);
            keepers[i].restore(test[i]);
        }
        double perc = (double)correct / (double) total;
        System.out.println("Correct percentage: " + perc);

        return new double[]{
                perc
        };
    }

    public static double[] testBank(AMR[] bank, int folds) throws InterruptedException {
        return testBank(bank, folds, 64);
    }

    public static double[] testBank(AMR[] bank, int folds, int threads) throws InterruptedException {
        return testBank(bank, folds, threads, 2, null);
    }

    public static double[] testBank(AMR[] bank, int folds, final int threads, final int iterations, String outputPath) throws InterruptedException {

        // First collect a list of testable AMRs

        List<AMR> testable = new ArrayList<AMR>();
        for (AMR amr : bank) {
            boolean unfixedNodeExists = false;
            for (AMR.Node node : amr.nodes) {
                if (!node.alignmentFixed) {
                    unfixedNodeExists = true;
                    break;
                }
            }
            if (!unfixedNodeExists) {
                testable.add(amr);
            }
        }

        int testSize = testable.size() / folds;
        int foldCursor = 0;

        double[] iterationScore = new double[iterations];
        int[] iterationTotal = new int[iterations];
        int[] iterationCorrect = new int[iterations];

        if (outputPath != null) {
            File outputFolder = new File(outputPath);
            if (!outputFolder.exists())
                outputFolder.mkdirs();
            else {
                for (File child : outputFolder.listFiles()) {
                    child.delete();
                }
            }
        }

        for (int f = 0; f < folds; f++) {
            int foldUpperLimit = foldCursor+testSize;
            if (foldUpperLimit > testable.size()) foldUpperLimit = testable.size();
            int foldSize = foldUpperLimit - foldCursor;

            final AMR[] testSet = new AMR[foldSize];
            System.out.println("Fold: "+f+", Using ["+foldCursor+","+foldUpperLimit+"] fully labeled documents, of "+testable.size()+" labeled total, ["+bank.length+" unlabeled total] as a test set");

            for (int i = foldCursor; i < foldUpperLimit; i++) {
                testSet[i-foldCursor] = testable.get(i);
            }
            foldCursor = foldUpperLimit;

            final double[] perFoldIterationScore = new double[iterations];
            final int[] perFoldTotal = new int[iterations];
            final int[] perFoldCorrect = new int[iterations];

            final ParallelStorer[] storers = new ParallelStorer[threads];
            final ParallelCounter[] counters = new ParallelCounter[threads];

            // Run EM

            EMAligner.align(bank, iterations, threads,

                    // Pre-iteration-hook, store all the test values away

                    new EMHook() {
                        @Override
                        public void hook(int i) {
                            // Only store on the first iteration
                            if (i != 0) return;

                            // Store all the AMRs for this fold

                            Thread[] storerThreads = new Thread[threads];
                            int threadsCursor = 0;
                            int threadsSize = testSet.length / threads;
                            for (int t = 0; t < threads; t++) {

                                // Slice up the test set for this thread

                                int threadSize = threadsSize;
                                if (t == threads - 1) threadSize = testSet.length - threadsCursor;
                                AMR[] threadSet = new AMR[threadSize];
                                System.arraycopy(testSet, threadsCursor, threadSet, 0, threadSize);

                                // Run the storers in parallel

                                storers[t] = new ParallelStorer(threadSet);
                                storerThreads[t] = new Thread(storers[t]);
                                storerThreads[t].start();
                            }
                            for (int t = 0; t < threads; t++) {
                                try {
                                    storerThreads[t].join();
                                } catch (InterruptedException ignored) {
                                    // do nothing
                                }
                            }
                        }
                    },

                    // Post-iteration-hook, get intermediate scores

                    new EMHook() {
                        @Override
                        public void hook(int i) {

                            // Unpack all the scores

                            Thread[] counterThread = new Thread[threads];
                            for (int t = 0; t < threads; t++) {
                                counters[t] = new ParallelCounter(storers[t].amrs, storers[t].keepers, i == iterations - 1); // only restore on the last iteration
                                counterThread[t] = new Thread(counters[t]);
                                counterThread[t].start();
                            }
                            int localTotal = 0;
                            int localCorrect = 0;
                            for (int t = 0; t < threads; t++) {
                                try {
                                    counterThread[t].join();
                                } catch (InterruptedException ignored) {
                                    // do nothing
                                }
                                localCorrect += counters[t].correct;
                                localTotal += counters[t].total;
                            }
                            double localAccuracy = ((double) localCorrect) / ((double) localTotal);
                            perFoldIterationScore[i] = localAccuracy;
                            perFoldCorrect[i] = localCorrect;
                            perFoldTotal[i] = localTotal;

                            System.out.println("Iteration " + i + " got " + localCorrect + "/" + localTotal + " correct, " + localAccuracy + " accuracy");
                        }
                    }
            );

            if (outputPath != null) {
                File foldReport = new File(outputPath + "/fold" + f + "-report.csv");
                try {
                    if (!foldReport.exists()) foldReport.createNewFile();
                    BufferedWriter bw = new BufferedWriter(new FileWriter(foldReport));
                    bw.write("Iteration,Score,Total,Correct\n");
                    for (int i = 0; i < iterations; i++) {
                        bw.write(i + "," + perFoldIterationScore[i] + "," + perFoldTotal[i] + "," + perFoldCorrect[i] + "\n");
                    }
                    bw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            System.out.println("Per fold iteration scores:");
            System.out.println("Iteration,Score");
            for (int i = 0; i < iterations; i++) {
                System.out.println(i+","+perFoldIterationScore[i]);
                iterationScore[i] += perFoldIterationScore[i] / folds;
                iterationTotal[i] += perFoldTotal[i];
                iterationCorrect[i] += perFoldCorrect[i];
            }
        }

        System.out.println("Test got "+iterationCorrect[iterations-1]+"/"+iterationTotal[iterations-1]+" correct, "+iterationScore[iterations-1]+" accuracy");

        System.out.println("Total iteration scores:");
        System.out.println("Iteration,Score");
        for (int i = 0; i < iterations; i++) {
            System.out.println(i+","+iterationScore[i]);
        }

        if (outputPath != null) {
            File finalReport = new File(outputPath + "/final-report.csv");
            try {
                if (!finalReport.exists()) finalReport.createNewFile();
                BufferedWriter bw = new BufferedWriter(new FileWriter(finalReport));
                bw.write("Iteration,Score,Total,Correct\n");
                for (int i = 0; i < iterations; i++) {
                    bw.write(i + "," + iterationScore[i] + "," + iterationTotal[i] + "," + iterationCorrect[i] + "\n");
                }
                bw.close();
                AMRSlurp.burpSerialized(outputPath + "/aligned-bank.ser.gz", bank);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return iterationScore;
    }

    private static class ParallelStorer implements Runnable {
        public AMR[] amrs;
        public AlignmentKeeper[] keepers;

        public ParallelStorer(AMR[] amrs) {
            this.amrs = amrs;
            this.keepers = new AlignmentKeeper[amrs.length];
        }

        @Override
        public void run() {
            for (int i = 0; i < keepers.length; i++) {
                keepers[i] = new AlignmentKeeper();
                keepers[i].saveAndConceal(amrs[i]);
            }
        }
    }

    private static class ParallelCounter implements Runnable {
        public AMR[] amrs;
        public AlignmentKeeper[] keepers;
        public int correct;
        public int total;
        public boolean restore;

        public ParallelCounter(AMR[] amrs, AlignmentKeeper[] keepers, boolean restore) {
            this.amrs = amrs;
            this.keepers = keepers;
            this.restore = restore;
        }

        @Override
        public void run() {
            correct = 0;
            total = 0;
            for (int i = 0; i < keepers.length; i++) {
                correct += keepers[i].countCorrect(amrs[i]);
                total += keepers[i].countMeasured(amrs[i]);
                if (restore) {
                    keepers[i].restore(amrs[i]);
                }
            }
        }
    }
}
