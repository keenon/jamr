package nlp.stamr.ontonotes;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads a set of SRL examples from OntoNotes
 */
public class SRLSlurp {

    public static boolean ignored = true;

    public static final String DEFAULT_PATH = "src/test/resources/ontonotes-release-4.0/data/english/annotations";

    public static SRL[] slurp() {
        if (ignored)
            return new SRL[0];
        else
            return slurp(DEFAULT_PATH);
    }

    public static SRL[] slurp(String path) {
        List<SRL> srls = slurpDirectory(new File(path));
        System.out.println("Loading SRL augmentation... "+srls.size());
        return srls.toArray(new SRL[srls.size()]);
    }

    private static List<SRL> slurpDirectory(File dir) {
        List<SRL> list = new ArrayList<SRL>();
        Set<String> paths = new HashSet<String>();

        assert(dir != null);
        assert(dir.isDirectory());
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                list.addAll(slurpDirectory(f));
            }
            else {
                String absolutePath = f.getAbsolutePath();
                String clippedPath = absolutePath.substring(0, absolutePath.lastIndexOf("."));
                paths.add(clippedPath);
            }
        }

        for (String path : paths) {
            try {
                list.addAll(slurpPath(path));
            } catch (IOException e) {
                // Do nothing
            }
        }

        return list;
    }

    private static List<SRL> slurpPath(String path) throws IOException, RuntimeIOException {
        List<SRL> list = new ArrayList<SRL>();

        // If either file doesn't exist, skip it
        File treeFile = new File(path+".parse");
        if (!treeFile.exists()) return list;
        File propFile = new File(path+".prop");
        if (!propFile.exists()) return list;

        Treebank treebank = new MemoryTreebank();
        treebank.loadPath(path+".parse");
        Tree[] trees = treebank.toArray(new Tree[treebank.size()]);

        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(path+".prop")));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(" ");

            // Get the source tree

            Tree tree = trees[Integer.parseInt(parts[1])];

            // Get the SRL head token

            int alignment = Integer.parseInt(parts[2]);
            String sense = parts[5];
            String sourceToken = tree.getLeaves().get(alignment).value();
            SRL srl = new SRL(sourceToken, sense, alignment);

            // Do the SRL arguments

            for (int i = 8; i < parts.length; i++) {
                String rel = parts[i].substring(parts[i].indexOf("-")+1,parts[i].length());
                int relAlignment = Integer.parseInt(parts[i].split("-")[0].split(":")[0]);
                srl.addArc(rel, relAlignment);
            }
            list.add(srl);
        }

        return list;
    }

}
