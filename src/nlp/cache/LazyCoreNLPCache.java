package nlp.cache;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import edu.stanford.nlp.curator.CuratorClient;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;

import java.io.*;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Created by keenon on 12/28/14.
 *
 * Maintains a CoreNLP system that will fault in individual examples from a cache on request (using Kryo), or will write
 * individual examples out on completion, thus hopefully hiding much of the latency and removing much of the memory
 * requirement of the simpler array-based cacheing strategy.
 */
public class LazyCoreNLPCache extends CoreNLPCache {
    boolean reading;
    OutputStream os;
    StanfordCoreNLP coreNLP;
    StanfordCoreNLP coreNLPFallback;
    String[] sentences;
    Thread writerThread;

    TransferMap<Integer, Annotation> annotationTransferMap = new TransferMap<>();

    public LazyCoreNLPCache(String sourcePath, String[] sentences) {
        this.sentences = sentences;
        for (int i = 0; i < sentences.length; i++) {
            assert(sentences[i] != null);
        }

        String cachePath = sourcePath;
        if (cachePath.endsWith(".txt"))
            cachePath = cachePath.substring(0, cachePath.length()-".txt".length());
        cachePath += ".ser.gz";

        File cacheFile = new File(cachePath);
        reading = cacheFile.exists();
        try {
            if (reading) {
                // Start reading into the cache
                new Thread(new Reader(new GZIPInputStream(new FileInputStream(cacheFile)),
                        annotationTransferMap,
                        sentences.length,
                        protobufAnnotationSerializer,
                        kryo)).start();
            }
            else {
                os = new GZIPOutputStream(new FileOutputStream(cacheFile));
                Properties props = new Properties();
                props.put("annotators", "tokenize, ssplit, pos, lemma, ner, regexner1, regexner2, parse, dcoref, srl, nom, prep");
                props.put("curator.host", "localhost"); // point to the curator host
                props.put("curator.port", "9010"); // point to the curator port

                props.put("customAnnotatorClass.regexner1", "edu.stanford.nlp.pipeline.TokensRegexNERAnnotator");
                props.put("regexner1.mapping", "data/kbp_regexner_mapping_nocase.tab");
                props.put("regexner1.validpospattern", "^(NN|JJ).*");
                props.put("regexner1.ignorecase", "true");
                props.put("regexner1.noDefaultOverwriteLabels", "CITY");

                props.put("customAnnotatorClass.regexner2", "edu.stanford.nlp.pipeline.TokensRegexNERAnnotator");
                props.put("regexner2.mapping", "data/kbp_regexner_mapping.tab");
                props.put("regexner2.ignorecase", "false");
                props.put("regexner2.noDefaultOverwriteLabels", "CITY");

                coreNLP = new CuratorClient(props, false);

                Properties propsFallback = new Properties();
                propsFallback.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
                coreNLPFallback = new StanfordCoreNLP(propsFallback);

                TransferMap<Integer, Annotation> writerMap = new TransferMap<>();
                int numThreads = Runtime.getRuntime().availableProcessors();
                for (int i = 0; i < numThreads; i++) {
                    new Thread(new Annotator(i, numThreads, coreNLP, coreNLPFallback, sentences, annotationTransferMap, writerMap)).start();
                }
                writerThread = new Thread(new Writer(os,
                        writerMap,
                        sentences.length,
                        protobufAnnotationSerializer,
                        kryo));
                writerThread.start();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class Reader implements Runnable {
        InputStream is;
        ProtobufAnnotationSerializer protobufAnnotationSerializer;
        Kryo kryo;
        Input input;
        ObjectInputStream ois;
        TransferMap<Integer, Annotation> transferMap;
        int size = 0;

        public Reader(InputStream is,
                      TransferMap<Integer, Annotation> transferMap,
                      int size,
                      ProtobufAnnotationSerializer protobufAnnotationSerializer,
                      Kryo kryo) {
            this.is = is;
            if (useProtobuf) {
                this.protobufAnnotationSerializer = protobufAnnotationSerializer;
            }
            else if (useKryo) {
                this.kryo = kryo;
                this.input = new Input(is);
            }
            else {
                try {
                    this.ois = new ObjectInputStream(is);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            this.transferMap = transferMap;
            this.size = size;
        }

        @Override
        public void run() {
            for (int i = 0; i < size; i++) {
                try {
                    Annotation annotation;
                    if (useProtobuf) {
                        annotation = protobufAnnotationSerializer.read(is).first;
                    }
                    else if (useKryo) {
                        annotation = kryo.readObject(input, Annotation.class);
                    }
                    else {
                        annotation = (Annotation)ois.readObject();
                    }
                    transferMap.put(i, annotation);
                }
                catch (Exception e) {
                    // This means we've reached the end of available stuff to read
                    return;
                }
            }
        }
    }

    private static class Writer implements Runnable {
        ProtobufAnnotationSerializer protobufAnnotationSerializer;
        Kryo kryo;
        OutputStream os;
        Output output;
        ObjectOutputStream oos;
        TransferMap<Integer, Annotation> writerMap;
        int size = 0;

        public Writer(OutputStream os,
                      TransferMap<Integer, Annotation> writerMap,
                      int size,
                      ProtobufAnnotationSerializer protobufAnnotationSerializer,
                      Kryo kryo) {
            if (useProtobuf) {
                this.os = os;
                this.protobufAnnotationSerializer = protobufAnnotationSerializer;
            }
            else if (useKryo) {
                this.output = new Output(os);
                this.kryo = kryo;
            }
            else {
                try {
                    this.oos = new ObjectOutputStream(os);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            this.writerMap = writerMap;
            this.size = size;
        }

        @Override
        public void run() {
            for (int i = 0; i < size; i++) {
                if (useProtobuf) {
                    try {
                        protobufAnnotationSerializer.write(writerMap.getBlockingAndRemove(i), os);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                else if (useKryo) {
                    kryo.writeObject(output, writerMap.getBlockingAndRemove(i));
                }
                else {
                    System.out.println("Writing "+i+"/"+size+" to "+oos.toString());
                    try {
                        oos.writeObject(writerMap.getBlockingAndRemove(i));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    System.out.println("Written "+i+"/"+size+" to "+oos.toString());
                }
            }
            System.out.println("Closing");
            try {
                if (useProtobuf) {
                    os.close();
                } else if (useKryo) {
                    output.close();
                } else {
                    oos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static class Annotator implements Runnable {
        int threadIdx;
        int numThreads;
        StanfordCoreNLP coreNLP;
        StanfordCoreNLP coreNLPFallback;
        String[] sentences;
        TransferMap<Integer, Annotation> transferMap;
        TransferMap<Integer, Annotation> writingMap;

        public Annotator(int threadIdx,
                         int numThreads,
                         StanfordCoreNLP coreNLP,
                         StanfordCoreNLP coreNLPFallback,
                         String[] sentences,
                         TransferMap<Integer, Annotation> transferMap,
                         TransferMap<Integer, Annotation> writingMap) {
            this.threadIdx = threadIdx;
            this.numThreads = numThreads;
            this.coreNLP = coreNLP;
            this.coreNLPFallback = coreNLPFallback;
            this.sentences = sentences;
            this.transferMap = transferMap;
            this.writingMap = writingMap;
        }

        @Override
        public void run() {
            for (int i = threadIdx; i < sentences.length; i += numThreads) {
                Annotation annotation = new Annotation(sentences[i]);
                try {
                    coreNLP.annotate(annotation);
                }
                catch (Exception e) {
                    coreNLPFallback.annotate(annotation);
                }
                System.out.println("Annotated "+i+"/"+sentences.length+", inserting...");
                transferMap.put(i, annotation);
                writingMap.put(i, annotation);
                System.out.println("Inserted "+i+"/"+sentences.length);
            }
        }
    }

    public Annotation getAnnotation(int index) {
        assert(index < sentences.length);
        return annotationTransferMap.getBlockingAndRemove(index);
    }

    @Override
    public void close() {
        if (!reading) {
            try {
                writerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
