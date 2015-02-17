package nlp.stamr.alignments;

import nlp.stamr.AMR;
import nlp.stamr.AMRSlurp;

import java.io.IOException;

/**
 * Generates alignments for the LDC dataset, and writes out a tiny subset for use in hill climbing iterations.
 */
public class TinyAnnotator {
    public static void main(String[] args) throws IOException, InterruptedException {
        AMR[] dev = AMRSlurp.slurp("src/test/resources/ldc-official/dev.txt", AMRSlurp.Format.LDC);
        AMR[] test = AMRSlurp.slurp("src/test/resources/ldc-official/test.txt", AMRSlurp.Format.LDC);
        AMR[] train = AMRSlurp.slurp("src/test/resources/ldc-official/train.txt", AMRSlurp.Format.LDC);
        AMR[] lp = AMRSlurp.slurp("src/test/resources/amr-bank-v1.2-human-assisted.txt", AMRSlurp.Format.LDC);

        AMR[] combined = new AMR[dev.length + test.length + train.length + lp.length];
        System.arraycopy(dev,0,combined,0,dev.length);
        System.arraycopy(test,0,combined,dev.length,test.length);
        System.arraycopy(train,0,combined,dev.length+test.length,train.length);
        System.arraycopy(lp,0,combined,dev.length+test.length+train.length,lp.length);

        EMAligner.align(combined, 4, 64);

        AMR[] tinyDev = new AMR[50];
        int tinyDevIndex = 0;
        for (int i = 0; i < dev.length; i++) {
            if (dev[i].docId.contains("PROXY")) {
                tinyDev[tinyDevIndex] = dev[i];
                tinyDevIndex++;
                if (tinyDevIndex >= tinyDev.length) break;
            }
        }

        AMR[] tinyTrain = new AMR[400];
        int tinyTrainIndex = 0;
        for (int i = 0; i < train.length; i++) {
            if (train[i].docId.contains("PROXY")) {
                tinyTrain[tinyTrainIndex] = train[i];
                tinyTrainIndex++;
                if (tinyTrainIndex >= tinyTrain.length) break;
            }
        }

        AMRSlurp.burp("src/test/resources/ldc-official/tinytrain.txt", AMRSlurp.Format.LDC, tinyTrain, AMR.AlignmentPrinting.ALL, false);
        AMRSlurp.burp("src/test/resources/ldc-official/tinydev.txt", AMRSlurp.Format.LDC, tinyDev, AMR.AlignmentPrinting.ALL, false);
    }
}
