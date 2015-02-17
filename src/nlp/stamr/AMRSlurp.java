package nlp.stamr;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import nlp.stamr.annotation.AnnotationManager;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Parser to handle reading in nlp.stamr.AMR sentences
 */
public class AMRSlurp {

    static AnnotationManager annotationManager = new AnnotationManager();

    public enum Format {
        LITTLE_PRINCE,
        LDC
    }

    public static AMR[] slurp(String path1, Format format1, String path2, Format format2) throws IOException {
        AMR[] a1 = AMRSlurp.slurp(path1, format1);
        AMR[] a2 = AMRSlurp.slurp(path2, format2);
        AMR[] bank = new AMR[a1.length+a2.length];
        System.arraycopy(a1,0,bank,0,a1.length);
        System.arraycopy(a2,0,bank,a1.length,a2.length);
        return bank;
    }

    public static AMR[] slurp(String path, Format format) throws IOException {
        String annotationLocation = path.replace(".txt",".ser.gz");
        return slurp(path, format, annotationLocation);
    }

    public static AMR[] slurp(String path, Format format, String annotationLocation) throws IOException {

        BufferedReader br = new BufferedReader(new FileReader(path));

        StringBuilder sb = new StringBuilder();
        String header = "";

        List<AMR> bank = new ArrayList<AMR>();

        String line;
        while ((line = br.readLine()) != null) {

            // LDC Format looks like this:
            //
            // # ::id blah-im-a-doc-id-001
            // # ::snt He can't seem to help himself .
            // (h / help-01 :ARG0 (h2 / he) :ARG1 h2)
            //
            // # ::id blah
            // # ::snt next sentence

            if (format == Format.LDC) {
                if (line.length() == 0) {
                    bank.add(parse(sb.toString(),header,format));
                    sb.setLength(0);
                    continue;
                }
                else if (line.charAt(0) == '(') {
                    header = sb.toString();
                    sb.setLength(0);
                }
            }

            // Little Prince Format looks like this:
            //
            // He can't seem to help himself . (blah-im-a-doc-id-001)
            // (h / help-01 :ARG0 (h2 / he) :ARG1 h2)
            //
            // Next sentence . (blah)

            else if (format == Format.LITTLE_PRINCE) {
                if (line.length() == 0) {
                    String result = sb.toString();
                    if (result.charAt(0) == '(') {
                        bank.add(parse(result, header, format));
                    }
                    else header = result;
                    sb.setLength(0);
                    continue;
                }
            }

            sb.append(line).append("\n");
        }

        AMR[] arr = bank.toArray(new AMR[bank.size()]);
        /*
        if (annotationLocation != null)
            annotationManager.loadOrCreateAnnotations(arr, annotationLocation);
            */
        return arr;
    }

    public static AMR[] slurpSerialized(String path) throws IOException {
        AMR[] bank = new AMR[0];
        try{
            InputStream file = new FileInputStream(path);
            InputStream buffer = new GZIPInputStream(new BufferedInputStream(file));
            ObjectInput input = new ObjectInputStream (buffer);
            try{
                bank = (AMR[])input.readObject();
            }
            finally{
                input.close();
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
        return bank;
    }

    public static void burpSerialized(String path, AMR[] bank) throws IOException {
        try {
            OutputStream file = new FileOutputStream(path);
            OutputStream buffer = new GZIPOutputStream(new BufferedOutputStream(file));
            ObjectOutput output = new ObjectOutputStream(buffer);
            try {
                output.writeObject(bank);
            }
            finally {
                output.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void burpCoNLL(String path, AMR[] bank) throws IOException {
        File f = new File(path);
        if (!f.exists()) f.delete();
        f.createNewFile();

        BufferedWriter bw = new BufferedWriter(new FileWriter(path));

        for (AMR amr : bank) {
            bw.append(amr.toCoNLLString());
            bw.append("\n");
        }

        bw.close();
    }

    public static void burpTriples(String path, AMR[] bank) throws IOException {
        File f = new File(path);
        if (!f.exists()) f.delete();
        f.createNewFile();

        BufferedWriter bw = new BufferedWriter(new FileWriter(path));

        for (AMR amr : bank) {
            bw.append(amr.toTriplesOutput());
            bw.append("\n");
        }

        bw.close();
    }

    public static void burp(String path, Format format, AMR[] bank, AMR.AlignmentPrinting alignmentPrinting, boolean forSmatch) throws IOException {

        File f = new File(path);
        if (!f.exists()) f.delete();
        f.createNewFile();

        BufferedWriter bw = new BufferedWriter(new FileWriter(path));

        for (int i = 0; i < bank.length; i++) {
            AMR doc = bank[i];

            // LDC Format looks like this:
            //
            // # ::id blah-im-a-doc-id-001
            // # ::snt He can't seem to help himself .
            // (h / help-01 :ARG0 (h2 / he) :ARG1 h2)
            //
            // # ::id blah
            // # ::snt next sentence

            if (format == Format.LDC) {
                bw.write("# ::id ");
                bw.write(doc.docId);
                bw.write("\n# ::snt ");
                bw.write(doc.formatSourceTokens());
                bw.write("\n");
                if (forSmatch)
                    bw.write(doc.toStringForSmatch());
                else
                    bw.write(doc.toString(alignmentPrinting));
                bw.write("\n\n");
            }

            // Little Prince Format looks like this:
            //
            // 1. He can't seem to help himself . (blah-im-a-doc-id-001)
            // (h / help-01 :ARG0 (h2 / he) :ARG1 h2)
            //
            // Next sentence . (blah)

            else if (format == Format.LITTLE_PRINCE) {
                bw.write(""+i);
                bw.write(". ");
                bw.write(doc.formatSourceTokens());
                bw.write(" (");
                bw.write(doc.docId);
                bw.write(")\n\n");
                if (forSmatch)
                    bw.write(doc.toStringForSmatch());
                else
                    bw.write(doc.toString(alignmentPrinting));
                bw.write("\n\n");
            }
        }

        /*
        String annotationLocation = path.replace(".txt",".ser.gz");
        if (annotationLocation != null)
            annotationManager.saveAnnotations(bank, annotationLocation);
        */

        bw.close();
    }

    public static String[] tokenize(String source) {
        PTBTokenizer ptbt = new PTBTokenizer<CoreLabel>(new StringReader(source), new CoreLabelTokenFactory(), "");
        List<String> tokens = new ArrayList<String>();
        while(ptbt.hasNext()) {
            tokens.add(ptbt.next().toString());
        }
        return tokens.toArray(new String[tokens.size()]);
    }

    public static AMR parse(String amr, String header, Format format) throws IOException {

        String[] sourceText = new String[0];
        String docId = "";

        // LDC Format looks like this (sentences not tokenized):
        //
        // # ::id blah-im-a-doc-id-001
        // # ::snt He can't seem to help himself.
        // (h / help-01 :ARG0 (h2 / he) :ARG1 h2)
        //
        // # ::id blah
        // # ::snt next sentence

        if (format == Format.LDC) {
            for (String part : header.split("\n")) {
                if (part.startsWith("# ::id")) {
                    docId = part.substring("# ::id ".length());
                }
                else if (part.startsWith("# ::snt")) {
                    sourceText = tokenize(part.substring("# ::snt ".length()));
                }
            }
        }

        // Little Prince Format looks like this (sentences tokenized):
        //
        // 11. He ca n't seem to help himself . (blah-im-a-doc-id-001)
        // (h / help-01 :ARG0 (h2 / he) :ARG1 h2)
        //
        // Next sentence . (blah)

        else if (format == Format.LITTLE_PRINCE) {
            String[] parts = header.split(" ");
            docId = parts[parts.length-1];
            docId = docId.substring(1,docId.length()-2);
            String[] sentence = Arrays.copyOfRange(parts,1,parts.length-1);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sentence.length; i++) {
                sb.append(sentence[i]);
                if (i != sentence.length-1) sb.append(" ");
            }
            sourceText = tokenize(sb.toString());
        }

        // Error check on the parsing of the header

        if (docId.equals("") || sourceText.length == 0) {
            System.err.println("Error parsing:\n"+header);
            if (format == Format.LDC) {
                System.err.println("Expected format: LDC");
            }
            else if (format == Format.LITTLE_PRINCE) {
                System.err.println("Expected format: LITTLE_PRINCE");
            }
            throw new IllegalStateException("Header doesn't match supplied format, docId or sourceText failed to extract correctly");
        }

        // A bunch of boilerplate to read in nlp.stamr.AMR using ANTLR

        try {
            AMR doc = parseAMRTree(amr);
            doc.docId = docId;
            doc.sourceText = sourceText;
            return doc;
        }
        catch (ParseCancellationException e) {
            System.err.println("Error parsing:\n"+amr);
            throw e;
        }
    }

    public static AMR parseAMRTree(String amr) {
        ANTLRInputStream input = new ANTLRInputStream(amr);
        AMRLexer lexer = new AMRLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        AMRParser parser = new AMRParser(tokens);
        parser.setErrorHandler(new BailErrorStrategy());
        ParseTree tree = parser.node();
        AMRVisitor visitor = new AMRVisitor();
        visitor.visit(tree);
        return visitor.doc;
    }

    private static class AMRVisitor extends AMRBaseVisitor<AMR.Node> {
        public AMR doc = new AMR();

        @Override
        public AMR.Node visitNode(AMRParser.NodeContext ctx) {
            AMR.Node head;
            if (ctx.parenthesized_title() != null) {
                head = visit(ctx.parenthesized_title());
            }
            else {
                head = visit(ctx.unparenthesized_title());
            }

            if (ctx.alignment() != null) {
                head.alignment = Integer.parseInt(ctx.alignment().NUMBER().getText());
                head.alignmentFixed = true;
            }

            for (AMRParser.ArgContext argCtx : ctx.arg()) {
                AMR.Node tail = visit(argCtx.node());
                doc.addArc(head,tail,argCtx.LABEL().getText());
            }
            return head;
        }

        @Override
        public AMR.Node visitIntroduction(AMRParser.IntroductionContext ctx) {
            return doc.addNode(ctx.LABEL(0).getText(),ctx.entity.getText());
        }

        @Override
        public AMR.Node visitReference(AMRParser.ReferenceContext ctx) {
            return doc.addNode(ctx.LABEL().getText());
        }

        @Override
        public AMR.Node visitValue(AMRParser.ValueContext ctx) {
            return doc.addNode(ctx.getText(), AMR.NodeType.VALUE);
        }

        @Override
        public AMR.Node visitTerminal(AMRParser.TerminalContext ctx) {
            return doc.addNodeAmbiguousValueOrRef(ctx.getText());
        }

        @Override
        public AMR.Node visitQuote(AMRParser.QuoteContext ctx) {
            String text = ctx.getText();
            return doc.addNode(text.substring(1,text.length()-1), AMR.NodeType.QUOTE);
        }
    }
}
