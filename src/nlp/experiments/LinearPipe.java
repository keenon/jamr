package nlp.experiments;

import com.sun.corba.se.impl.orbutil.ObjectWriter;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.optimization.SGDToQNMinimizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.SystemUtils;
import edu.stanford.nlp.util.Triple;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Created by keenon on 1/27/15.
 *
 * Implements a simple linear ML pipe, which takes in some sort of information, and pipes out a new sort.
 *
 * Useful for both standalone testing and full-runs of real stuff.
 */
public class LinearPipe<IN,OUT> {

    Function<IN,Object>[] features;
    Classifier<Integer,String> bucketClassifier;
    List<Classifier<OUT,String>> classifiers;
    public BiConsumer<IN, BufferedWriter> debugErrorContext;
    Pattern whitespacePattern = Pattern.compile("\\s+");

    TwoDimensionalCounter<String,OUT> memorizedClassifier;

    List<OUT> outTypes;
    List<String> svmClassifiers;

    public boolean automaticallyReweightTrainingData = true;
    public double sigma = 1.0;
    public double epsilon = 0.05;

    public enum ClassifierType {
        LINEAR,
        LOGISTIC,
        SVM,
        BAYESIAN
    }

    public ClassifierType type = ClassifierType.LINEAR;

    @SuppressWarnings("unchecked")
    public LinearPipe(List<Function<IN,Object>> features, BiConsumer<IN, BufferedWriter> debugErrorContext) {
        this.features = features.toArray(new Function[features.size()]);
        this.debugErrorContext = debugErrorContext;
    }

    public void debugFeatures(IN in) {
        Counter<String> features = featurize(in);
        System.out.println("Debugging features for "+in.toString());
        for (String s : features.keySet()) {
            System.out.println(s+":"+features.getCount(s));
        }
    }

    /*
    Helpful note: Run this with XX:StringTableSize=1000003, which should vastly improve interned string hashmap
    performance
     */

    private Counter<String> featurize(IN in) {
        Counter<String> featureCounts = new ClassicCounter<>();

        for (int i = 0; i < features.length; i++) {
            Function<IN,Object> feature = features[i];

            Object obj = feature.apply(in);

            if (obj == null) continue;

            if (obj instanceof double[]) {
                double[] arr = (double[])obj;
                for (int j = 0; j < arr.length; j++) {
                    featureCounts.setCount((i + "->" + j).intern(), arr[j]);
                }
            }
            else if (obj instanceof Double) {
                featureCounts.setCount(Integer.toString(i).intern(), (double)obj);
            }
            else if (obj instanceof Set) {
                Set s = (Set)obj;
                for (Object o : s) {
                    featureCounts.setCount(Integer.toString(i) + "->" + o.toString().intern(), 1.0);
                }
            }
            else {
                featureCounts.setCount((Integer.toString(i) + "->" + obj.toString()).intern(), 1.0);
            }
        }

        return featureCounts;
    }

    private Collection<String> discreteFeaturize(IN in) {
        Collection<String> featureValues = new HashSet<>();

        for (int i = 0; i < features.length; i++) {
            Function<IN,Object> feature = features[i];

            Object obj = feature.apply(in);

            if (obj == null) continue;

            if (obj instanceof double[]) {
                throw new IllegalArgumentException("Can't have double arguments to discreteFeaturize!");
            }
            else if (obj instanceof Double) {
                throw new IllegalArgumentException("Can't have double arguments to discreteFeaturize!");
            }
            else {
                featureValues.add(Integer.toString(i) + "->" + obj.toString());
            }
        }

        return featureValues;
    }

    private Datum<OUT, String> toDiscreteDatum(IN in, OUT out) {
        return new BasicDatum<>(discreteFeaturize(in), out);
    }

    private RVFDatum<OUT, String> toDatum(IN in, OUT out) {
        return new RVFDatum<>(featurize(in), out);
    }

    public void guaranteeMemorizable(List<Pair<IN,OUT>> data) {
        Set<String> set = new HashSet<>();
        for (Pair<IN,OUT> pair : data) {
            Counter<String> f = featurize(pair.first);
            for (String s : f.keySet()) {
                if (set.contains(s)) throw new IllegalArgumentException("Can't have the same feature appear twice!\n"+
                s);
                set.add(s);
            }
        }
    }

    private class Parmap<E> implements Runnable {
        List<Pair<IN,OUT>> data;
        Object[] outs;
        int threadIdx;
        int numThreads;
        Function<Pair<IN,OUT>, E> fn;
        AtomicInteger atomic;

        public Parmap(List<Pair<IN,OUT>> data, Object[] outs, int threadIdx, int numThreads, Function<Pair<IN,OUT>, E> fn, AtomicInteger atomic) {
            this.data = data;
            this.outs = outs;
            this.threadIdx = threadIdx;
            this.numThreads = numThreads;
            this.fn = fn;
            this.atomic = atomic;
        }

        @Override
        public void run() {
            for (int i = threadIdx; i < outs.length; i += numThreads) {
                outs[i] = fn.apply(data.get(i));
                int done = atomic.incrementAndGet();
                if (done % 10000 == 0) {
                    System.out.println("Featurized "+done+"/"+data.size());
                }
            }
        }
    }

    private <E> List<E> parmap(List<Pair<IN,OUT>> data, Function<Pair<IN,OUT>, E> fn) {
        Object[] outs = new Object[data.size()];
        int cpus = Runtime.getRuntime().availableProcessors();
        AtomicInteger atomic = new AtomicInteger();
        Thread[] threads = new Thread[cpus];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(new Parmap<>(data, outs, i, cpus, fn, atomic));
            threads[i].start();
        }
        for (int i = 0; i < threads.length; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        List<E> es = new ArrayList<>();
        for (int i = 0; i < outs.length; i++) {
            if (outs[i] == null) {
                throw new IllegalStateException("Shouldn't have any null outs: "+i);
            }
            es.add((E)outs[i]);
        }
        return es;
    }

    public void train(List<Pair<IN,OUT>> data) {
        List<Set<OUT>> clusters = new ArrayList<>();

        Set<OUT> bigCluster = new HashSet<>();
        for (Pair<IN,OUT> pair : data) {
            bigCluster.add(pair.second);
        }

        clusters.add(bigCluster);

        train(data, clusters);
    }

    private <L,F> float[] getNormalizingArray(RVFDataset<L,F> dataset) {
        float[] dataWeights = new float[dataset.size()];
        Counter<L> labelCounts = new ClassicCounter<>();
        for (int i = 0; i < dataset.size(); i++) {
            labelCounts.incrementCount(dataset.getDatum(i).label());
        }
        for (int i = 0; i < dataset.size(); i++) {
            dataWeights[i] = (float) (dataset.size() / labelCounts.getCount(dataset.getDatum(i).label()));
        }
        return dataWeights;
    }


    private double runSVMModel(String path, Counter<String> features) {
        RVFDataset<Boolean, String> dataset = new RVFDataset<>();
        dataset.add(new RVFDatum<>(features));

        // this is the file that the svm light formated dataset
        // will be printed to
        File dataFile = null;
        File modelFile = null;
        File predictFile = null;
        try {
            dataFile = File.createTempFile("svm-", ".data");
            modelFile = new File(path);
            predictFile = File.createTempFile("svm-", ".pred");
            // print the dataset
            PrintWriter pw = new PrintWriter(new FileWriter(dataFile));
            dataset.printSVMLightFormat(pw);
            pw.close();
            dataFile.deleteOnExit();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String cmd = "svm_light/svm_classify -v 0 "+dataFile.getAbsolutePath()+" "+modelFile.getAbsolutePath()+" "+predictFile.getAbsolutePath();

        SystemUtils.run(new ProcessBuilder(whitespacePattern.split(cmd)),
                new PrintWriter(System.err), new PrintWriter(System.err));

        try {
            BufferedReader br = new BufferedReader(new FileReader(predictFile));
            String line = br.readLine();
            return Double.parseDouble(line);
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return 0.0;
    }

    private String createSVMModel(String name, List<RVFDatum<OUT,String>> datumList, OUT tag) {
        RVFDataset<Boolean, String> dataset = new RVFDataset<>();
        for (RVFDatum<OUT, String> datum : datumList) {
            dataset.add(new RVFDatum<>(datum.asFeaturesCounter(), datum.label().equals(tag)));
        }

        // this is the file that the svm light formated dataset
        // will be printed to
        File dataFile = null;
        File modelFile = null;
        try {
            dataFile = File.createTempFile("svm-", ".data");
            modelFile = new File("svm/"+name+".model");
            // print the dataset
            PrintWriter pw = new PrintWriter(new FileWriter(dataFile));
            dataset.printSVMLightFormat(pw);
            pw.close();
            dataFile.deleteOnExit();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String cmd = "svm_light/svm_learn -v 1 -m 400 -c 1.0 -t 2 -z r "+dataFile.getAbsolutePath()+" "+modelFile.getAbsolutePath();
        System.out.println("Training SVM for "+tag);
        System.out.println("<< "+cmd+" >>");

        SystemUtils.run(new ProcessBuilder(whitespacePattern.split(cmd)),
                new PrintWriter(System.err), new PrintWriter(System.err));

        return modelFile.getAbsolutePath();
    }

    public void train(List<Pair<IN,OUT>> data, List<Set<OUT>> clusters) {

        Set<OUT> leftOutCluster = new HashSet<>();
        outer: for (Pair<IN,OUT> pair : data) {
            for (Set<OUT> cluster : clusters) {
                if (cluster.contains(pair.second)) continue outer;
            }
            leftOutCluster.add(pair.second);
        }
        if (leftOutCluster.size() > 0) {
            clusters.add(leftOutCluster);
        }

        classifiers = new ArrayList<>();

        if (type == ClassifierType.LOGISTIC) {
            List<RVFDatum<OUT,String>> datumList = parmap(data, (pair) -> toDatum(pair.first, pair.second));
            LogisticClassifierFactory<OUT, String> factory = new LogisticClassifierFactory<>();
            RVFDataset<OUT, String> dataset = new RVFDataset<>();
            for (RVFDatum<OUT, String> datum : datumList) {
                dataset.add(datum);
            }
            if (automaticallyReweightTrainingData) {
                classifiers.add(factory.trainWeightedData(dataset, getNormalizingArray(dataset)));
            }
            else {
                // Use the HUBER penalty for non-automatically reweighted data
                // Sigma is regularization
                // Epsilon is boundary beyond which the HUBER penalty goes linear
                LogPrior prior = new LogPrior(LogPrior.LogPriorType.HUBER, sigma, epsilon);
                classifiers.add(factory.trainClassifier(dataset, prior, false));
            }
        }
        else if (type == ClassifierType.SVM) {
            List<RVFDatum<OUT,String>> datumList = parmap(data, (pair) -> toDatum(pair.first, pair.second));

            outTypes = new ArrayList<>();
            svmClassifiers = new ArrayList<>();

            for (RVFDatum<OUT,String> datum : datumList) {
                if (!outTypes.contains(datum.label())) {
                    outTypes.add(datum.label());
                }
            }

            for (OUT label : outTypes) {
                svmClassifiers.add(createSVMModel("model"+outTypes.indexOf(label), datumList, label));
            }
        }
        else if (type == ClassifierType.LINEAR) {
            List<RVFDatum<OUT,String>> datumList = parmap(data, (pair) -> toDatum(pair.first, pair.second));

            RVFDataset<OUT, String> dataset = new RVFDataset<>();
            for (RVFDatum<OUT, String> datum : datumList) {
                dataset.add(datum);
            }

            // Create a data-weighting array to down-weight super frequent tags and upweight infrequent ones

            LinearClassifierFactory<OUT, String> factory = new LinearClassifierFactory<>();
            LinearClassifierFactory<Integer, String> bucketClassifierFactory = new LinearClassifierFactory<>();
            factory.setSigma(sigma);  // higher -> less regularization (default=1)
            factory.setVerbose(true);
            factory.setMinimizerCreator(() -> new SGDToQNMinimizer(0.5, 1000, 50, -1));
            bucketClassifierFactory.setSigma(sigma);  // higher -> less regularization (default=1)
            bucketClassifierFactory.setVerbose(true);
            bucketClassifierFactory.setMinimizerCreator(() -> new SGDToQNMinimizer(0.5, 1000, 50, -1));

            if (clusters.size() == 1) {
                // trivial case, just do what we usually do
                if (automaticallyReweightTrainingData) {
                    classifiers.add(factory.trainClassifier(dataset, getNormalizingArray(dataset), new LogPrior(LogPrior.LogPriorType.HUBER, sigma, epsilon)));
                } else {
                    classifiers.add(factory.trainClassifier(dataset));
                }
            }
            else {
                // Create a cluster map dataset
                RVFDataset<Integer, String> bucketDataset = new RVFDataset<>();
                List<RVFDataset<OUT, String>> collectionDatasets = new ArrayList<>();

                for (int i = 0; i < clusters.size(); i++) {
                    collectionDatasets.add(new RVFDataset<>());
                }

                outer: for (RVFDatum<OUT, String> datum : datumList) {
                    for (int i = 0; i < clusters.size(); i++) {
                        if (clusters.get(i).contains(datum.label())) {
                            bucketDataset.add(new RVFDatum<>(datum.asFeaturesCounter(), i));
                            collectionDatasets.get(i).add(datum);
                            continue outer;
                        }
                    }
                }

                if (automaticallyReweightTrainingData) {
                    bucketClassifier = bucketClassifierFactory.trainClassifier(bucketDataset, getNormalizingArray(bucketDataset), new LogPrior(LogPrior.LogPriorType.HUBER, sigma, epsilon));
                }
                else {
                    bucketClassifier = bucketClassifierFactory.trainClassifier(bucketDataset);
                }
                for (int i = 0; i < clusters.size(); i++) {
                    if (automaticallyReweightTrainingData) {
                        classifiers.add(factory.trainClassifier(collectionDatasets.get(i), getNormalizingArray(collectionDatasets.get(i)), new LogPrior(LogPrior.LogPriorType.HUBER, sigma, epsilon)));
                    }
                    else {
                        classifiers.add(factory.trainClassifier(collectionDatasets.get(i)));
                    }
                }
            }

            System.out.println("Trained classifier");
            int correct = 0;
            for (int i = 0; i < dataset.size(); i++) {
                int predictedCluster = 0;
                if (classifiers.size() > 1) {
                    RVFDatum<Integer,String> bucketDatum = new RVFDatum<>(dataset.getRVFDatum(i).asFeaturesCounter());
                    predictedCluster = bucketClassifier.classOf(bucketDatum);
                }
                OUT predicted = classifiers.get(predictedCluster).classOf(dataset.getRVFDatum(i));
                if (predicted.equals(dataset.getRVFDatum(i).label())) correct++;
            }
            System.out.println("Accuracy: "+((double)correct/dataset.size())+" ("+correct+"/"+dataset.size()+")");
        }
        else if (type == ClassifierType.BAYESIAN) {
            memorizedClassifier = new TwoDimensionalCounter<>();
            for (Pair<IN,OUT> pair : data) {
                Collection<String> f = discreteFeaturize(pair.first);
                assert f.size() == 1;
                String s = f.iterator().next();
                memorizedClassifier.incrementCount(s, pair.second, 1.0);
            }

            System.out.println("Trained classifier");

            int correct = 0;
            for (int i = 0; i < data.size(); i++) {
                String s = discreteFeaturize(data.get(i).first).iterator().next();
                OUT predicted = Counters.argmax(memorizedClassifier.getCounter(s));
                if (predicted.equals(data.get(i).second)) correct++;
            }
            System.out.println("Accuracy: "+((double)correct/data.size())+" ("+correct+"/"+data.size()+")");
        }
    }

    public OUT predict(IN in) {
        if (type == ClassifierType.BAYESIAN) {
            String s = discreteFeaturize(in).iterator().next();
            return Counters.argmax(memorizedClassifier.getCounter(s));
        }
        else if (type == ClassifierType.SVM) {
            Counter<OUT> counter = predictSoft(in);
            OUT maxLikelihoodOut = outTypes.get(0);
            double maxLikelihood = Double.NEGATIVE_INFINITY;
            for (OUT o : counter.keySet()) {
                if (counter.getCount(o) > maxLikelihood) {
                    maxLikelihood = counter.getCount(o);
                    maxLikelihoodOut = o;
                }
            }
            return maxLikelihoodOut;
        }
        else {
            Counter<String> features = featurize(in);
            int predictedCluster = 0;
            if (classifiers.size() > 1) {
                RVFDatum<Integer,String> bucketDatum = new RVFDatum<>(features);
                predictedCluster = bucketClassifier.classOf(bucketDatum);
            }
            return classifiers.get(predictedCluster).classOf(new RVFDatum<>(features));
        }
    }

    public Counter<OUT> predictSoft(IN in) {
        if (type == ClassifierType.BAYESIAN) {
            String s = discreteFeaturize(in).iterator().next();
            return memorizedClassifier.getCounter(s);
        }
        else if (type == ClassifierType.SVM) {
            Counter<OUT> counter = new ClassicCounter<>();
            for (int i = 0; i < outTypes.size(); i++) {
                counter.incrementCount(outTypes.get(i), runSVMModel(svmClassifiers.get(i), featurize(in)));
            }
            System.out.println("Finished an SVM prediction");
            return counter;
        }
        else if (type == ClassifierType.LOGISTIC) {
            LogisticClassifier<Boolean,String> logistic = (LogisticClassifier<Boolean,String>)classifiers.get(0);

            double trueCount = logistic.probabilityOf(featurize(in), Boolean.TRUE);
            Counter<Boolean> out = new ClassicCounter<>();
            out.incrementCount(true, Math.log(trueCount));
            out.incrementCount(false, Math.log(1 - trueCount));
            return (Counter<OUT>)out;
        }
        else {
            if (classifiers.size() == 1) {
                return classifiers.get(0).scoresOf(new RVFDatum<>(featurize(in)));
            }
            else {
                Counter<String> features = featurize(in);
                Counter<Integer> bucketProbs = bucketClassifier.scoresOf(new RVFDatum<>(features));
                Counter<OUT> outClasses = new ClassicCounter<>();
                Counters.logNormalizeInPlace(outClasses);

                for (int i : bucketProbs.keySet()) {
                    double logProbI = bucketProbs.getCount(i);
                    Counter<OUT> localClasses = classifiers.get(i).scoresOf(new RVFDatum<OUT, String>(features));
                    Counters.logNormalizeInPlace(localClasses);
                    for (OUT out : localClasses.keySet()) {
                        outClasses.incrementCount(out, Math.exp(localClasses.getCount(out) + logProbI));
                    }
                }

                Counters.logInPlace(outClasses);
                return outClasses;
            }
        }
    }

    public void analyze(List<Pair<IN,OUT>> train, List<Pair<IN,OUT>> test, String directory) throws IOException {
        File dir = new File(directory);
        if (dir.exists()) dir.delete();
        dir.mkdirs();

        analyze(train, directory+"/train");
        analyze(test, directory+"/test");
    }

    private void analyze(List<Pair<IN,OUT>> data, String directory) throws IOException {
        List<Triple<IN,OUT,OUT>> predictions = new ArrayList<>();
        for (Pair<IN,OUT> pair : data) {
            predictions.add(new Triple<>(pair.first, pair.second, predict(pair.first)));
        }

        File dir = new File(directory);
        if (dir.exists()) dir.delete();
        dir.mkdirs();

        writeAccuracy(predictions, directory+"/accuracy.txt");
        writeConfusionMatrix(predictions, directory + "/confusion.csv");
        writeErrors(predictions, directory + "/errors.txt");
        if (data.get(0).second instanceof Boolean) {
            writeTuningCurve(data, directory);
        }
    }

    private void writeTuningCurve(List<Pair<IN,OUT>> data, String path) throws IOException {
        if (!(data.get(0).second instanceof Boolean)) {
            throw new IllegalStateException("Can't call writeTuningCurve on non-boolean data!");
        }

        List<Pair<Double,Boolean>> predictions = new ArrayList<>();
        for (Pair<IN,OUT> pair : data) {
            Counter<Boolean> logProb = (Counter<Boolean>) predictSoft(pair.first);
            Counters.logNormalizeInPlace(logProb);
            double prob = Math.exp(logProb.getCount(true));
            boolean label = (Boolean)pair.second;
            predictions.add(new Pair<>(prob, label));
        }

        int numBuckets = 15;
        List<List<Pair<Double,Boolean>>> buckets = new ArrayList<>();
        for (int i = 0; i < numBuckets; i++) {
            buckets.add(new ArrayList<>());
        }

        for (Pair<Double,Boolean> prediction : predictions) {
            int bucket = (int)Math.floor(prediction.first * numBuckets);
            // This only happens if prediction.first == 1.0
            if (bucket == numBuckets) bucket = numBuckets - 1;
            buckets.get(bucket).add(prediction);
        }

        // Create the actual tuning curve plot

        GNUPlot plot = new GNUPlot();
        double[] xAxis = new double[numBuckets+1];
        double[] yAxis = new double[numBuckets+1];
        for (int i = 0; i < numBuckets; i++) {
            xAxis[i+1] = (1.0 / numBuckets)*(0.5 + i); // put the x-label in the middle of the bucket set
            int totalCount = 0;
            int trueCount = 0;
            for (Pair<Double,Boolean> prediction : buckets.get(i)) {
                totalCount++;
                if (prediction.second) trueCount++;
            }
            yAxis[i+1] = (double)trueCount / totalCount;
        }
        plot.addLine(xAxis, yAxis);
        plot.title = "tuning";
        plot.xLabel = "Predicted Percentage";
        plot.yLabel = "Actual Percentage";
        plot.saveAnalysis(path);
    }

    private void writeAccuracy(List<Triple<IN,OUT,OUT>> predictions, String path) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(path));
        if (predictions.get(0).third instanceof Boolean) {
            int truePositive = 0;
            int falsePositive = 0;
            int falseNegative = 0;
            for (Triple<IN,OUT,OUT> prediction : predictions) {
                boolean target = (Boolean)prediction.second;
                boolean guess = (Boolean)prediction.third;
                if (target && guess) truePositive++;
                else if (!target && guess) falsePositive++;
                else if (target && !guess) falseNegative++;
            }
            bw.write("True positive: "+truePositive+"\n");
            bw.write("False positive: "+falsePositive+"\n");
            bw.write("False negative: "+falseNegative+"\n");
            double precision = (double)truePositive / (truePositive + falsePositive);
            double recall = (double)truePositive / (truePositive + falseNegative);
            bw.write("Precision: "+precision+"\n");
            bw.write("Recall: "+recall+"\n");
            double f1 = 2 * precision * recall / (precision + recall);
            bw.write("F1: "+f1+"\n");
        }
        else {
            int correct = 0;
            for (Triple<IN, OUT, OUT> prediction : predictions) {
                if (prediction.second.equals(prediction.third)) correct++;
            }
            bw.write("Accuracy: " + ((double) correct / predictions.size()));
        }
        bw.close();
    }

    private Pair<List<OUT>, int[][]> getConfusionMatrix(List<Triple<IN,OUT,OUT>> predictions) {
        List<OUT> tagTypes = new ArrayList<>();
        for (Triple<IN,OUT,OUT> prediction : predictions) {
            if (!tagTypes.contains(prediction.second)) tagTypes.add(prediction.second);
            if (!tagTypes.contains(prediction.third)) tagTypes.add(prediction.third);
        }

        int[][] counts = new int[tagTypes.size()][tagTypes.size()];

        for (Triple<IN,OUT,OUT> prediction : predictions) {
            int target = tagTypes.indexOf(prediction.second);
            int guess = tagTypes.indexOf(prediction.third);

            counts[target][guess]++;
        }

        return new Pair<>(tagTypes, counts);
    }

    private void writeConfusionMatrix(List<Triple<IN,OUT,OUT>> predictions, String path) throws IOException {
        Pair<List<OUT>, int[][]> confusion = getConfusionMatrix(predictions);

        BufferedWriter bw = new BufferedWriter(new FileWriter(path));

        bw.write("ROW=TARGET:COL=GUESS");
        for (OUT out : confusion.first) {
            bw.write(","+out);
        }
        bw.write("\n");

        int[][] counts = confusion.second;
        for (int i = 0; i < counts.length; i++) {
            bw.write(""+confusion.first.get(i));
            for (int j = 0; j < counts[i].length; j++) {
                bw.write(","+counts[i][j]);
            }
            if (i != counts.length-1) {
                bw.write("\n");
            }
        }

        bw.close();
    }

    private List<Triple<IN,OUT,OUT>> sortByConfusion(List<Triple<IN,OUT,OUT>> predictions) {
        List<Triple<IN,OUT,OUT>> sortClone = new ArrayList<>();
        sortClone.addAll(predictions);

        final Pair<List<OUT>, int[][]> confusion = getConfusionMatrix(predictions);

        sortClone.sort(new Comparator<Triple<IN, OUT, OUT>>() {
            @Override
            public int compare(Triple<IN, OUT, OUT> o1, Triple<IN, OUT, OUT> o2) {
                int firstTarget = confusion.first.indexOf(o1.second);
                int firstGuess = confusion.first.indexOf(o1.third);
                int secondTarget = confusion.first.indexOf(o2.second);
                int secondGuess = confusion.first.indexOf(o2.third);

                return confusion.second[secondTarget][secondGuess] - confusion.second[firstTarget][firstGuess];
            }
        });

        return sortClone;
    }

    private void writeErrors(List<Triple<IN,OUT,OUT>> predictions, String path) throws IOException {
        List<Triple<IN,OUT,OUT>> sorted = sortByConfusion(predictions);

        BufferedWriter bw = new BufferedWriter(new FileWriter(path));

        for (Triple<IN,OUT,OUT> example : sorted) {
            if (!example.second.equals(example.third)) {
                bw.write("TARGET: " + example.second + "\n");
                bw.write("GUESS: " + example.third + "\n");
                if (debugErrorContext != null) {
                    bw.write("CONTEXT:\n");
                    debugErrorContext.accept(example.first, bw);
                }
                bw.write("\n");
            }
        }

        bw.close();
    }

    public void writeToFile(String path) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path));
        oos.writeDouble(sigma);
        oos.writeDouble(epsilon);
        oos.writeObject(type);
        if (bucketClassifier != null) {
            oos.writeBoolean(true);
            oos.writeObject(bucketClassifier);
        }
        else {
            oos.writeBoolean(false);
        }
        oos.writeObject(classifiers);
    }

    public void readFromFile(String path) throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path));
        sigma = ois.readDouble();
        epsilon = ois.readDouble();
        type = (ClassifierType) ois.readObject();

        boolean hasBucketClassifier = ois.readBoolean();
        if (hasBucketClassifier) {
            bucketClassifier = (Classifier<Integer, String>) ois.readObject();
        }
        classifiers = (List<Classifier<OUT, String>>) ois.readObject();
    }
}
