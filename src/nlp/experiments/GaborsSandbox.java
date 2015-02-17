package nlp.experiments;


import com.esotericsoftware.kryo.io.Input;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.io.IOUtils;
import nlp.keenonutils.Lazy;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.process.WordShapeClassifier;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.sequences.Clique;
import edu.stanford.nlp.sequences.FeatureFactory;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.time.SUTimeSimpleParser;
import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 *
 * @author Gabor Angeli
 */
public class GaborsSandbox extends FeatureFactory<CoreLabel> {

  // We need these to exist
  public static String trainFile ="/home/gabor/tmp/amr-experiment/data/train-400-seq.txt";
  public static String testFile ="/home/gabor/tmp/amr-experiment/data/test-100-seq.txt";
  public static String wordVecFile ="/home/gabor/tmp/amr-experiment/data/glove.6B.300d.txt.gz";
  public static String word2VecFile ="/home/gabor/tmp/amr-experiment/data/google-300-trimmed.ser.gz";

  // These are caches that are computed automatically
  public static String trainCache ="/home/gabor/tmp/amr-experiment/data/train-400-seq.txt.cache";
  public static String testCache ="/home/gabor/tmp/amr-experiment/data/test-100-seq.txt.cache";
  public static String wordVecCachedFile ="/home/gabor/tmp/amr-experiment/data/glove.cached.ser.gz";

  private static final Lazy<StanfordCoreNLP> corenlp = Lazy.of(() -> new StanfordCoreNLP(new Properties() {{
    setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,depparse,parse,dcoref");
    setProperty("ssplit.isOneSentence", "true");
    setProperty("tokenize.whitespace", "true");
  }}));

  private static Pattern INTEGER = Pattern.compile("[0-9]+");

  /**
   *
   * @param info A PaddedList of the feature-value pairs
   * @param position The current position to extract features at
   * @param clique The particular clique for which to extract features. It
   *     should be a member of the knownCliques list.
   * @return
   */
  @Override
  public Collection<String> getCliqueFeatures(PaddedList<CoreLabel> info, int position, Clique clique) {
    List<String> features = new ArrayList<>();
    if (clique == cliqueC) {
      CoreLabel token = info.get(position);
      addAllInterningAndSuffixing(features, new ArrayList<String>() {{
        add("word: " + token.word());
        add("lemma: " + token.lemma());
        add("pos: " + token.tag());
        add("word+ner: " + token.word() + "+" + token.ner());
        add("word+pos: " + token.word() + "+" + token.tag());
        add("shape: " + WordShapeClassifier.wordShape(token.word(), WordShapeClassifier.WORDSHAPECHRIS4));
//        add("last_word: " + info.get(position-1).word());
//        add("next_word: " + info.get(position+1).word());
//        add("last_last_word: " + info.get(position-2).word());
//        add("next_next_word: " + info.get(position+2).word());
        add("left_bigram: " + (position == 0 ? "^" : info.get(position - 1).word()) + " " + token.word());
        add("right_bigram: " + token.word() + " " + (position == info.size() - 1 ? "$" : info.get(position + 1).word()));
        if (INTEGER.matcher(token.word()).matches()) {
          add("is_int: true");
        }
        add("cliqueC");
      }}, "C");
    } else if (clique == cliqueCpC) {
      CoreLabel token = info.get(position);
      CoreLabel prev = info.get(position - 1);
      addAllInterningAndSuffixing(features, new ArrayList<String>() {{
//        add(prev.word() + "_" + token.word());
        add("word: " + token.word());
        add("cliqueCpC");
      }}, "CpC");
    } else if (clique == cliqueCp2C) {
      CoreLabel token = info.get(position);
      CoreLabel prev = info.get(position - 1);
      CoreLabel prev2 = info.get(position - 2);
      addAllInterningAndSuffixing(features, new ArrayList<String>() {{
//        add(prev2.word() + "_" + prev.word() + "_" + token.word());
        add("word: " + token.word());
        add("cliqueCp2C");
      }}, "C2Cp");
    } else if (clique == cliqueCp3C) {
      CoreLabel token = info.get(position);
      CoreLabel prev = info.get(position - 1);
      CoreLabel prev2 = info.get(position - 2);
      CoreLabel prev3 = info.get(position - 3);
      addAllInterningAndSuffixing(features, new ArrayList<String>() {{
//        add(prev3.word() + "_ " + prev2.word() + "_" + prev.word() + "_" + token.word());
        add("word: " + token.word());
        add("cliqueCp3C");
      }}, "Cp3C");
    }
    return features;
  }

  /**
   *
   */
  private static class Sequence implements Serializable {
    private static final long serialVersionUID = 42L;
    List<CoreLabel> tokens;
    String[] words;
    String[] lemmas;
    String[] pos;
    String[] ner;
    String[] dependencyParentArc;
    boolean[] isCoreferent;
    boolean[] isCanonicalMention;

    String[] labels;

    public Sequence(List<String> words, List<String> labels) {
      Annotation ann = new Annotation(StringUtils.join(words, " "));
      corenlp.get().annotate(ann);
      this.tokens = ann.get(CoreAnnotations.TokensAnnotation.class);
      this.words = tokens.stream().map(CoreLabel::word).toArray(String[]::new);
      this.lemmas = tokens.stream().map(CoreLabel::lemma).toArray(String[]::new);
      this.pos = tokens.stream().map(CoreLabel::tag).toArray(String[]::new);
      this.ner = tokens.stream().map(CoreLabel::ner).toArray(String[]::new);

      this.labels = labels.toArray(new String[labels.size()]);
      for (int i = 0; i < tokens.size(); ++i) {
        tokens.get(i).set(CoreAnnotations.AnswerAnnotation.class, this.labels[i]);
      }

      if (this.labels.length != this.words.length) {
        throw new IllegalArgumentException();
      }

      SemanticGraph tree = ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
      dependencyParentArc = new String[this.words.length];
      Arrays.fill(dependencyParentArc, "n/a");
      for (IndexedWord node : tree.vertexSet()) {
        if (tree.incomingEdgeIterator(node).hasNext()) {
          dependencyParentArc[node.index() - 1] = tree.incomingEdgeIterator(node).next().getRelation().toString();
        }
      }
      for (IndexedWord root : tree.getRoots()) {
        dependencyParentArc[root.index() - 1] = "root";
      }

      isCoreferent = new boolean[this.words.length];
      isCanonicalMention = new boolean[this.words.length];
      for (CorefChain chain : ann.get(CorefCoreAnnotations.CorefChainAnnotation.class).values()) {
        for (CorefChain.CorefMention mention : chain.getMentionsInTextualOrder()) {
          for (int i = mention.startIndex; i < mention.endIndex; ++i) {
            isCoreferent[i - 1] = true;
            if (chain.getRepresentativeMention() == mention) {
              isCanonicalMention[i - 1] = true;
            }
          }
        }
      }
    }
  }

  /**
   *
   * @param seq
   * @param index
   * @param vectors
   * @return
   */
  public static Counter<String> featurize(Sequence seq, int index, Map<String, double[]> vectors) {
    return new ClassicCounter<String>() {{
      int shapeType = WordShapeClassifier.WORDSHAPECHRIS4;
      incrementCount("word: " + seq.words[index], 1.0);
      incrementCount("lemma: " + seq.lemmas[index], 1.0);
      incrementCount("pos: " + seq.pos[index], 1.0);
      incrementCount("ner: " + seq.ner[index], 1.0);
      incrementCount("word+ner: " + seq.words[index] + "_" + seq.ner[index], 1.0);
      incrementCount("left_bigram: " + (index == 0 ? "^" : seq.words[index - 1]) + " " + seq.words[index], 1.0);
      incrementCount("right_bigram: " + seq.words[index] + " " + (index == seq.words.length - 1 ? "$" : seq.words[index + 1]));
//      incrementCount("coref_signature: " + seq.isCoreferent[index] + " " + seq.isCanonicalMention[index], 1.0);
      incrementCount("is_coref: " + seq.pos[index] + ": " + seq.isCoreferent[index]);
//      incrementCount("is_coref_canonical: " + seq.pos[index] + ": " + seq.isCanonicalMention[index]);
      incrementCount("shape: " + WordShapeClassifier.wordShape(seq.words[index], shapeType));
      if (INTEGER.matcher(seq.words[index]).matches()) {
        incrementCount("is_int: true", 1.0);
      }
//      incrementCount("left_shape: " + (index == 0 ? "^" : WordShapeClassifier.wordShape(seq.words[index - 1], shapeType)) + " " + WordShapeClassifier.wordShape(seq.words[index], shapeType), 1.0);
//      incrementCount("right_shape: " + WordShapeClassifier.wordShape(seq.words[index], shapeType) + " " + (index == seq.words.length - 1 ? "$" : WordShapeClassifier.wordShape(seq.words[index + 1], shapeType)));
      incrementCount("left_ner: " + (index == 0 ? "^" : seq.ner[index - 1]));
      incrementCount("right_ner: " + (index == seq.ner.length - 1 ? "$" : seq.ner[index + 1]));
      incrementCount("context: " + (index == 0 ? "^" : seq.words[index - 1]) + " _ " + (index == seq.words.length - 1 ? "$" : seq.words[index + 1]));
      incrementCount("incoming_edge: " + "<-" + seq.dependencyParentArc[index] + "-");
//      incrementCount("incoming_edge+lemma: " + seq.lemmas[index] + "<-" + seq.dependencyParentArc[index] + "-");

//      for (int len = 1; len < Math.min(3, seq.words[index].length()); ++len) {
//        incrementCount("prefix: " + seq.words[index].substring(len));
//        incrementCount("suffix: " + seq.words[index].substring(seq.words[index].length() - len, seq.words[index].length()));
//      }


//      if (vectors.containsKey(seq.words[index])) {
//        double[] v = vectors.get(seq.words[index]);
//        for (int i = 0; i < v.length; ++i) {
//          incrementCount("glove_word[" + i + "]", SloppyMath.sigmoid(v[i]));
//        }
//      }
    }};
  }

  /**
   *
   * @param path
   * @return
   * @throws java.io.IOException
   */
  public static Map<String,double[]> loadWordVectors(String path) throws IOException {
        File f = new File(path);
        assert(f.exists());
        Input input;
        if (path.endsWith(".gz")) {
            input = new Input(new GZIPInputStream(new FileInputStream(f)));
        }
        else {
            input = new Input(new FileInputStream(f));
        }

        Map<String,double[]> embeddings = new HashMap<>();
        int words = input.readInt();
        int dimension = input.readInt();

        for (int i = 0; i < words; i++) {
            String s = input.readString();
            double[] arr = new double[dimension];
            for (int j = 0; j < dimension; j++) {
                arr[j] = input.readFloat();
            }
            embeddings.put(s, arr);
        }

        input.close();

        return embeddings;
    }



  /**
   *
   * @param path
   * @param cache
   * @param wordVectors
   * @param test
   * @return
   * @throws java.io.IOException
   * @throws ClassNotFoundException
   */
  @SuppressWarnings("unchecked")
  public static Pair<List<Sequence>, RVFDataset<String,String>> readDataset(String path, String cache, Map<String, double[]> wordVectors, boolean test) throws IOException, ClassNotFoundException {
    // Read the data
    List<Sequence> examples;
    if (new File(cache).exists()) {
      examples = IOUtils.readObjectFromFile(cache);
    } else {
      // (cache)
      Stream<String[]> lines = Arrays.asList(IOUtils.slurpFile(path).split("\n")).stream().map(x -> x.trim().split("\t"));
      examples = new ArrayList<>();
      List<String> wordBuffer = new ArrayList<>();
      List<String> labelBuffer = new ArrayList<>();
      lines.forEach(fields -> {
        if (fields.length < 2) {
          if (wordBuffer.size() > 0) {
            examples.add(new Sequence(wordBuffer, labelBuffer));
          }
          wordBuffer.clear();
          labelBuffer.clear();
        } else {
          wordBuffer.add(fields[0]);
          labelBuffer.add(fields[1]);
        }
      });
      IOUtils.writeObjectToFile(examples, cache);
    }

    // Featurize the data
    RVFDataset<String, String> dataset = new RVFDataset<>();
    for (Sequence seq : examples) {
      for (int i = 0; i < seq.words.length; ++i) {
        Counter<String> features = featurize(seq, i, wordVectors);
        dataset.add(new RVFDatum<>(features, seq.labels[i]));
        if (!test && !"IDENTITY".equals(seq.labels[i]) && !"NONE".equals(seq.labels[i])) {
          dataset.add(new RVFDatum<>(features, seq.labels[i]));
        }
        if (!test && "SINGULAR".equals(seq.labels[i])) {
          dataset.add(new RVFDatum<>(features, seq.labels[i]));
          dataset.add(new RVFDatum<>(features, seq.labels[i]));
          dataset.add(new RVFDatum<>(features, seq.labels[i]));
          dataset.add(new RVFDatum<>(features, seq.labels[i]));
        }
        if (!test && "MANY".equals(seq.labels[i])) {
          dataset.add(new RVFDatum<>(features, seq.labels[i]));
          dataset.add(new RVFDatum<>(features, seq.labels[i]));
          dataset.add(new RVFDatum<>(features, seq.labels[i]));
          dataset.add(new RVFDatum<>(features, seq.labels[i]));
        }
      }
    }

    return Pair.makePair(examples, dataset);
  }


  /**
   *
   * @param sentences
   * @param label
   */
  private static void printAccuracy(Collection<List<CoreLabel>> sentences, String label) {
    startTrack(label);
    int correct = 0;
    int total = 0;
    for (List<CoreLabel> sentence : sentences) {
      for (CoreLabel token : sentence) {
        total += 1;
        if (token.get(CoreAnnotations.AnswerAnnotation.class).equals(token.get(CoreAnnotations.GoldAnswerAnnotation.class))) {
          correct += 1;
        }
      }
    }
    log("Accuracy: " + new DecimalFormat("0.0000").format((double) correct / ((double) total)));
    endTrack(label);
  }

  /**
   *
   * @param sentences
   */
  private static void clearAnswerAnnotations(Collection<List<CoreLabel>> sentences) {
    for (List<CoreLabel> sentence : sentences) {
      for (CoreLabel token : sentence) {
        token.set(CoreAnnotations.GoldAnswerAnnotation.class, token.get(CoreAnnotations.AnswerAnnotation.class));
        token.remove(CoreAnnotations.AnswerAnnotation.class);
      }
    }
  }

  /**
   *
   * @return
   * @throws java.io.IOException
   */
  public static Set<String> vocab() throws IOException {
    Set<String> vocab = IOUtils.readColumnSet(trainFile, 0);
    vocab.addAll(IOUtils.readColumnSet(testFile, 0));
    return vocab;
  }

  /**
   *
   * @param args
   * @throws java.io.IOException
   * @throws ClassNotFoundException
   * @throws edu.stanford.nlp.time.SUTimeSimpleParser.SUTimeParsingError
   */
  public static void main(String[] args) throws IOException, ClassNotFoundException, SUTimeSimpleParser.SUTimeParsingError {
    //
    // DATA MUNGING
    //

    startTrack("main");
    forceTrack("Reading vectors");
    Set<String> vocab = vocab();
    Map<String, double[]> wordVectors = new HashMap<>();
    if (new File(wordVecCachedFile).exists()) {
      wordVectors = IOUtils.readObjectFromFile(wordVecCachedFile);
    } else {
      BufferedReader vecReader = IOUtils.getBufferedReaderFromClasspathOrFileSystem(wordVecFile);
      String line;
      while ((line = vecReader.readLine()) != null) {
        String[] fields = line.split(" ");
        if (vocab.contains(fields[0])) {
          double[] values = new double[300];
          for (int i = 1; i < 301; ++i) {
            values[i - 1] = Double.parseDouble(fields[i]);
          }
          wordVectors.put(fields[0], values);
        }
      }
      IOUtils.writeObjectToFile(wordVectors, wordVecCachedFile);
    }
    wordVectors = loadWordVectors(word2VecFile);
    endTrack("Reading vectors");

    forceTrack("Reading data");
    Pair<List<Sequence>, RVFDataset<String,String>> train = readDataset(trainFile, trainCache, wordVectors, false);
    Pair<List<Sequence>, RVFDataset<String,String>> test = readDataset(testFile, testCache, wordVectors, true);
    endTrack("Reading data");

    //
    // CRF TRAINING
    //


    /*

    Properties props = new Properties();
    props.setProperty("featureFactory", "edu.stanford.nlp.kbp.slotfilling.scripts.GaborsSandbox");
    props.setProperty("multiThreadGrad", "4");
    props.setProperty("useOWLQN", "true");
//    props.setProperty("priorLambda", "1.00");
    CRFClassifier<CoreLabel> crf = new CRFClassifier<>(props);

    Collection<List<CoreLabel>> trainSentences = train.first.stream().map(x -> x.tokens).collect(Collectors.toList());
    Collection<List<CoreLabel>> testSentences = test.first.stream().map(x -> x.tokens).collect(Collectors.toList());

    crf.train(trainSentences);
    clearAnswerAnnotations(trainSentences);
    clearAnswerAnnotations(testSentences);

    for (List<CoreLabel> sentence : trainSentences) {
      crf.classify(sentence);
    }
    printAccuracy(trainSentences, "Training Accuracy");
    for (List<CoreLabel> sentence : testSentences) {
      crf.classify(sentence);
    }
    printAccuracy(testSentences, "Test Accuracy");
    endTrack("main");
    System.exit(0);
    */


    //
    // LOGISTIC REGRESSION TRAINING
    //

    LinearClassifierFactory<String,String> factory = new LinearClassifierFactory<>();
//    for (double sigma : new double[]{0.001, 0.01, 0.1, 0.1, 0.5, 0.25, 1.0, 1.25, 1.5, 2.0, 10.0 }) {
//      factory.setSigma(sigma);  // higher -> less regularization (default=1)
//      LinearClassifier<String,String> classifier = factory.trainClassifier(train);
//      log("sigma=" + sigma + "  Accuracy: " + classifier.evaluateAccuracy(test));
//    }

    factory.setSigma(2.0);  // higher -> less regularization (default=1)
    LinearClassifier<String,String> classifier = factory.trainClassifier(train.second);

      /*
    startTrack("Train");
    log("Accuracy: " + classifier.evaluateAccuracy(train.second));
//    for (String label : classifier.labels()) {
//      Pair<Double, Double> pr = classifier.evaluatePrecisionAndRecall(train, label);
//      log("" + label + "  P:" + new DecimalFormat("0.000").format(pr.first) + "  R:" + new DecimalFormat("0.000").format(pr.second));
//    }
    endTrack("Train");

    startTrack("Test");
    log("Accuracy: " + classifier.evaluateAccuracy(test.second));
    for (String label : classifier.labels()) {
      Pair<Double, Double> pr = classifier.evaluatePrecisionAndRecall(test.second, label);
      log("" + label + "  P:" + new DecimalFormat("0.000").format(pr.first) + "  R:" + new DecimalFormat("0.000").format(pr.second));
    }
    endTrack("Test");

    endTrack("main");
    */
  }

}
