package nlp.stamr.alignments;

import nlp.stamr.AMR;
import nlp.stamr.AMRSlurp;

import java.io.IOException;

/**
 * Generates alignments for the LDC dataset, and writes it out.
 */
public class LDCAnnotator {
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

        AMRSlurp.burp("src/test/resources/ldc-official/dev-aligned.txt", AMRSlurp.Format.LDC, dev, AMR.AlignmentPrinting.ALL, false);
        AMRSlurp.burp("src/test/resources/ldc-official/train-aligned.txt", AMRSlurp.Format.LDC, train, AMR.AlignmentPrinting.ALL, false);
    }
}
