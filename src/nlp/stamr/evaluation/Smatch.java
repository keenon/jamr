package nlp.stamr.evaluation;

import nlp.stamr.AMR;
import nlp.stamr.AMRSlurp;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Handles calculating Smatch scores over individual trees and sets of trees together
 */
public class Smatch {
    public static double smatch(AMR gold, AMR test) throws IOException, InterruptedException {
        return smatch(new AMR[]{gold}, new AMR[]{test});
    }

    static final String DIRECTORY = "tmp/smatch";
    static final String GOLD_PATH = DIRECTORY+"/gold.txt";
    static final String TEST_PATH = DIRECTORY+"/test.txt";

    public static double smatch(AMR[] goldSet, AMR[] testSet) throws IOException, InterruptedException {
        prepFiles();
        AMRSlurp.burp(GOLD_PATH, AMRSlurp.Format.LDC, goldSet, AMR.AlignmentPrinting.NONE, true);
        AMRSlurp.burp(TEST_PATH, AMRSlurp.Format.LDC, testSet, AMR.AlignmentPrinting.NONE, true);

        String command = "src/main/python/smatch_py/smatch.py -f "+GOLD_PATH+" "+TEST_PATH;

        System.out.println("Running command: "+command);

        Process p = Runtime.getRuntime().exec(command);
        p.waitFor();

        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));

        String line = br.readLine();
        System.out.println("Smatch result: "+line);
        return Double.parseDouble(line.split(":")[1].trim());
    }

    public static void prepFiles() {
        ensureFolderExists(DIRECTORY);
        createOrClear(GOLD_PATH);
        createOrClear(TEST_PATH);
    }

    public static void ensureFolderExists(String path) {
        File file = new File(path);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    public static void createOrClear(String path) {
        File file = new File(path);
        try {
            if (file.exists())
                file.delete();
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
