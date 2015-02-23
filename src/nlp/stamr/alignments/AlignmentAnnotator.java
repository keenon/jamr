package nlp.stamr.alignments;

import com.googlecode.lanterna.TerminalFacade;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenWriter;
import com.googlecode.lanterna.terminal.Terminal;
import nlp.stamr.AMR;
import nlp.stamr.AMRSlurp;
import edu.stanford.nlp.util.IdentityHashSet;
import edu.stanford.nlp.util.Triple;

import java.io.IOException;
import java.util.*;

/**
 * Allows you to read in a bunch of AMRs, align them using a simple GUI, and write back the output.
 */
public class AlignmentAnnotator {

    Screen screen;
    ScreenWriter screenWriter;
    AMR[] train;
    AMR[] bank;

    AMR amr;
    int currentAMRIndex = 0;
    AMR.Node currentAMRNode;
    int currentAMRNodeIndex = 0;

    Map<Character,Integer> alignmentShortcuts = new HashMap<Character, Integer>();
    Map<Integer,Character> shortcutsDisplay = new HashMap<Integer, Character>();

    String sourcePath;
    String outputPath;

    boolean keepRunning = true;

    public static AMR[] filterAMRs(AMR[] bank, String prefix) {
        List<AMR> list = new ArrayList<AMR>();
        for (AMR amr : bank) {
            if (amr.docId.startsWith(prefix)) list.add(amr);
        }
        return list.toArray(new AMR[list.size()]);
    }

    public static AMR[] trimDocs(int num, List<AMR[]> minibanks) {
        int totalNum = 0;
        for (AMR[] minibank : minibanks) totalNum += minibank.length;
        double percentage = (double)num / (double)totalNum;

        List<AMR> collapsed = new ArrayList<AMR>();
        for (AMR[] minibank : minibanks) {
            int total = (int)Math.round(minibank.length * percentage);
            if (minibanks.indexOf(minibank) == minibanks.size()-1) total = num - collapsed.size();
            for (int i = 0; i < total; i++) {
                collapsed.add(minibank[i]);
            }
        }
        return collapsed.toArray(new AMR[collapsed.size()]);
    }

    private static AMR[] subtractSet(AMR[] original, AMR[] sub) {
        Set<AMR> subset = new IdentityHashSet<AMR>(Arrays.asList(sub));

        AMR[] remaining = new AMR[original.length - sub.length];
        int offset = 0;
        for (AMR amr : original) {
            if (!subset.contains(amr)) {
                remaining[offset++] = amr;
            }
        }

        return remaining;
    }

    public static void seperateTrimmedBank() throws IOException {
        AMR[] bank = AMRSlurp.slurp("src/test/resources/ldc-official/training-500-subset.txt", AMRSlurp.Format.LDC);

        AMR[] train = trimDocs(250, Arrays.asList(
                filterAMRs(bank, "bolt"),
                filterAMRs(bank, "DF"),
                filterAMRs(bank, "sdl"),
                filterAMRs(bank, "PROXY"),
                filterAMRs(bank, "nw")
        ));

        AMR[] rest = subtractSet(bank, train);

        AMR[] dev = trimDocs(125, Arrays.asList(
                filterAMRs(rest, "bolt"),
                filterAMRs(rest, "DF"),
                filterAMRs(rest, "sdl"),
                filterAMRs(rest, "PROXY"),
                filterAMRs(rest, "nw")
        ));

        AMR[] test = subtractSet(rest, dev);

        AMRSlurp.burp("src/test/resources/ldc-official/training-250-train.txt", AMRSlurp.Format.LDC, train, AMR.AlignmentPrinting.FIXED_ONLY, false);
        AMRSlurp.burp("src/test/resources/ldc-official/training-125-dev.txt", AMRSlurp.Format.LDC, dev, AMR.AlignmentPrinting.FIXED_ONLY, false);
        AMRSlurp.burp("src/test/resources/ldc-official/training-125-test.txt", AMRSlurp.Format.LDC, test, AMR.AlignmentPrinting.FIXED_ONLY, false);
    }

    public static void generateTrimmedBank() throws IOException {
        AMR[] bank = AMRSlurp.slurp("src/test/resources/ldc-official/train.txt", AMRSlurp.Format.LDC);

        AMR[] trimmed = trimDocs(500, Arrays.asList(
                filterAMRs(bank, "bolt"),
                filterAMRs(bank, "DF"),
                filterAMRs(bank, "sdl"),
                filterAMRs(bank, "PROXY"),
                filterAMRs(bank, "nw")
        ));

        AMRSlurp.burp("src/test/resources/ldc-official/training-500-subset.txt", AMRSlurp.Format.LDC, trimmed, AMR.AlignmentPrinting.FIXED_ONLY, false);

        AMR[] bolt = filterAMRs(trimmed, "bolt");
        AMR[] dfa = filterAMRs(trimmed, "DF");
        AMR[] mt09sdl = filterAMRs(trimmed, "sdl");
        AMR[] proxy = filterAMRs(trimmed, "PROXY");
        AMR[] xinhua = filterAMRs(trimmed, "nw");

        System.out.println("bolt: "+bolt.length);
        System.out.println("dfa: "+dfa.length);
        System.out.println("mt09sdl: "+mt09sdl.length);
        System.out.println("proxy: "+proxy.length);
        System.out.println("xinhua: "+xinhua.length);
    }

    public static void main(String[] args) throws IOException {
        AMR[] train = AMRSlurp.slurp("data/deft-amr-100.txt", AMRSlurp.Format.LDC);
        AMR[] dev = AMRSlurp.slurp("data/deft-amr-100.txt", AMRSlurp.Format.LDC);
        new AlignmentAnnotator(train, dev, "data/deft-amr-100.txt", false);
    }

    public AlignmentAnnotator(AMR[] train, AMR[] dev, String outputPath, boolean preAligned) {
        this.train = train;
        this.bank = dev;
        this.outputPath = outputPath;
        amr = bank[currentAMRIndex];

        boolean nullToNonNull = false;
        if (nullToNonNull) {
            for (AMR amr : bank) {
                for (AMR.Node node : amr.topologicalSort()) {
                    if (node.alignmentFixed) {
                        if (node.alignment != 0) {
                            node.alignment = node.alignment - 1;
                        } else {
                            if (amr.incomingArcs.containsKey(node)) {
                                AMR.Node parent = amr.incomingArcs.get(node).get(0).head;
                                node.alignment = parent.alignment;
                            } else {
                                node.alignmentFixed = false;
                            }
                        }
                    }
                }
            }
        }

        if (!preAligned) {
            // runEM();
            test();
        }

        screen = TerminalFacade.createScreen();
        screen.startScreen();

        screenWriter = new ScreenWriter(screen);
        screenWriter.setBackgroundColor(Terminal.Color.BLACK);
        screenWriter.setForegroundColor(Terminal.Color.WHITE);

        // Always start at the beginning, that's where tests are at
        setAMR(0);

        redraw();

        while (keepRunning) {
            Key key = screen.readInput();
            if (key != null)
                handleInput(key);

            if(screen.resizePending())
                redraw();
        }

        System.out.println("Saved and Quitting");
    }

    public void setAMR(int newIndex) {
        while (newIndex < 0) newIndex += bank.length;
        newIndex = newIndex % bank.length;
        amr = bank[newIndex];
        currentAMRIndex = newIndex;

        // Set the nlp.stamr.AMR node we're labeling

        setAMRNode(0);

        // Assign new shortcut keys to the tokens in this nlp.stamr.AMR

        alignmentShortcuts.clear();
        shortcutsDisplay.clear();
        for (int i = 0; i < amr.sourceTokenCount(); i++) {
            String token = amr.getSourceToken(i);
            char shortcut;
            if (token.length() > 0 && !alignmentShortcuts.containsKey(token.toLowerCase().charAt(0))) {
                shortcut = token.toLowerCase().charAt(0);
            }
            else {
                boolean found = false;
                // try everything from a-z
                for (shortcut = 'a'; shortcut < 'z'; shortcut++) {
                    if (!alignmentShortcuts.containsKey(shortcut)) {
                        found = true;
                        break;
                    }
                }
                // try 0-9
                if (!found) {
                    for (shortcut = '0'; shortcut < '9'; shortcut++) {
                        if (!alignmentShortcuts.containsKey(shortcut)) {
                            found = true;
                            break;
                        }
                    }
                }
                // try capital letters
                if (!found) {
                    for (shortcut = 'A'; shortcut < 'Z'; shortcut++) {
                        if (!alignmentShortcuts.containsKey(shortcut)) {
                            found = true;
                            break;
                        }
                    }
                }
                // give up - sentence longer than 26*2 + 10, TODO: not totally satisfactory, these sentences do exist
            }
            alignmentShortcuts.put(shortcut,i);
            shortcutsDisplay.put(i, shortcut);
        }
    }

    public void setAMRNode(int i) {
        List<AMR.Node> sort = amr.depthFirstSearch();
        while (i < 0) i += sort.size();
        i = i % sort.size();
        currentAMRNode = sort.get(i);
        currentAMRNodeIndex = i;
    }

    public void runEM() {
        System.out.println("Assisting alignment by running LP aligner");
        RegenerativeAligner.align(bank);
        /*
        try {
            EMAligner.align(bank, 2, 64);
        } catch (InterruptedException e) {
            System.err.println("Weird error during EM with synchronization. Quitting");
            e.printStackTrace();
            System.exit(1);
        }
        */
    }

    public void test() {
        System.out.println("Running tests");
        try {
            AlignmentTester.testBankRegenerative(train, bank);

            // AlignmentTester.testBankRuleBased(train, bank);

            // AlignmentTester.testBank(bank, 2, 64, 3, null);

            // This is just to keep the compiler from bitching about the unthrown exception
            int i = 0;
            if (i == 1) {
                throw new InterruptedException("e");
            }
        } catch (InterruptedException e) {
            System.err.println("Weird error during alignment testing with synchronization. Quitting.");
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void save() {
        try {
            if (outputPath != null)
                System.out.println("Saving...");
                AMRSlurp.burp(outputPath, AMRSlurp.Format.LDC, bank, AMR.AlignmentPrinting.FIXED_ONLY, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void quit() {
        screen.stopScreen();
        keepRunning = false;
    }

    public void handleInput(Key key) {
        if (key.getCharacter() == '\n') {
            setAMR(currentAMRIndex + 1);
        }
        else if (key.getCharacter() == 's' && key.isCtrlPressed()) {
            save();
        }
        else if (key.getCharacter() == 'q' && key.isCtrlPressed()) {
            save();
            quit();
        }
        else if (key.getCharacter() == 'e' && key.isCtrlPressed()) {
            save();
            runEM();
        }
        else if (key.getCharacter() == 't' && key.isCtrlPressed()) {
            save();
            test();
        }
        else if (key.getCharacter() == 'd' && key.isCtrlPressed()) {
            System.out.println("Debugging node details:");
            System.out.println("Node title: " + currentAMRNode.title);
            System.out.println("Node lemma: " + amr.annotationWrapper.getLemmaAtIndex(currentAMRNode.alignment));
            System.out.println("Node POS: " + amr.annotationWrapper.getPOSTagAtIndex(currentAMRNode.alignment));
            if (currentAMRNode.type == AMR.NodeType.VALUE) {
                System.out.println("Node type: VALUE");
            }
            else if (currentAMRNode.type == AMR.NodeType.ENTITY) {
                System.out.println("Node type: ENTITY");
            }
            else if (currentAMRNode.type == AMR.NodeType.QUOTE) {
                System.out.println("Node type: QUOTE");
            }
        }
        else if (key.toString().equals("Tab")) {
            currentAMRNode.alignmentFixed = true;
            setAMRNode(currentAMRNodeIndex + 1);
        }
        else if (key.toString().equals("ReverseTab")) {
            setAMRNode(currentAMRNodeIndex - 1);
        }
        else if (key.toString().equals("ArrowUp")) {
            setAMRNode(currentAMRNodeIndex - 1);
        }
        else if (key.toString().equals("ArrowDown")) {
            setAMRNode(currentAMRNodeIndex + 1);
        }
        else if (key.toString().equals("ArrowLeft")) {
            setAMR(currentAMRIndex - 1);
        }
        else if (key.toString().equals("ArrowRight")) {
            save();
            setAMR(currentAMRIndex + 1);
        }
        else {
            if (alignmentShortcuts.containsKey(key.getCharacter())) {
                currentAMRNode.alignment = alignmentShortcuts.get(key.getCharacter());
                currentAMRNode.alignmentFixed = true;
            }
        }
        redraw();
    }

    public void redraw() {
        screen.clear();
        screenWriter.drawString(4, 2, "StAMR Alignment Annotation Tool - Document "+(currentAMRIndex+1)+"/"+bank.length);
        screenWriter.drawString(4, 3, "Bank of "+bank.length+" AMRs loaded from \""+sourcePath+"\"");
        if (outputPath != null) screenWriter.drawString(4, 4, "ctrl+s to save aligned AMRs to \""+outputPath+"\"");
        screenWriter.drawString(4, 5, "ctrl+q = quit, ctrl+e = re-run EM, ctrl+t = run tests, ctrl+d = debug selected");

        int width = screen.getTerminalSize().getColumns();
        StringBuilder dividerBuilder = new StringBuilder();
        for (int i = 0; i < width; i++) {
            dividerBuilder.append("-");
        }
        String divider = dividerBuilder.toString();
        screenWriter.drawString(0,6,divider);
        int printTokensLowerBound = printTokens(3,7);
        screenWriter.drawString(0,printTokensLowerBound+2,divider);

        int alignment = currentAMRNode.alignment;
        AMR.Arc parentArc = amr.getParentArc(currentAMRNode);
        AMR.Node parent = parentArc.head;
        int parentAlignment = parent.alignment;

        screenWriter.drawString(4, printTokensLowerBound + 4, "Dependency path between \"" + currentAMRNode.title + "\" and \"" + parent.title + "\": " + amr.multiSentenceAnnotationWrapper.sentences.get(0).getDependencyPathBetweenNodes(alignment, parentAlignment));
        screenWriter.drawString(0,printTokensLowerBound+6,divider);
        printAMR(4, printTokensLowerBound + 8);
        screen.refresh();
    }

    public void setHighlighted() {
        screenWriter.setForegroundColor(Terminal.Color.BLACK);
        screenWriter.setBackgroundColor(Terminal.Color.WHITE);
    }
    public void setHighlightedPinned() {
        screenWriter.setForegroundColor(Terminal.Color.BLACK);
        screenWriter.setBackgroundColor(Terminal.Color.GREEN);
    }
    public void setHighlightedWrong() {
        screenWriter.setForegroundColor(Terminal.Color.BLACK);
        screenWriter.setBackgroundColor(Terminal.Color.RED);
    }
    public void setHighlightedNonProjective() {
        screenWriter.setForegroundColor(Terminal.Color.BLACK);
        screenWriter.setBackgroundColor(Terminal.Color.BLUE);
    }
    public void setPinned() {
        screenWriter.setForegroundColor(Terminal.Color.GREEN);
        screenWriter.setBackgroundColor(Terminal.Color.BLACK);
    }
    public void setWrong() {
        screenWriter.setForegroundColor(Terminal.Color.RED);
        screenWriter.setBackgroundColor(Terminal.Color.BLACK);
    }
    public void setNonProjective() {
        screenWriter.setForegroundColor(Terminal.Color.BLUE);
        screenWriter.setBackgroundColor(Terminal.Color.BLACK);
    }
    public void resetHighlighted() {
        screenWriter.setForegroundColor(Terminal.Color.WHITE);
        screenWriter.setBackgroundColor(Terminal.Color.BLACK);
    }

    public int printTokens(int x, int y) {
        int startx = x;
        int width = screen.getTerminalSize().getColumns();
        for (int i = 0; i < amr.sourceTokenCount(); i++) {
            String token = amr.getSourceToken(i);
            if (x + token.length() > width) {
                x = startx;
                y += 3;
            }
            if (currentAMRNode.alignment == i) {
                setHighlighted();
            }
            if ((currentAMRNode.testAlignment != -1) && (currentAMRNode.testAlignment != currentAMRNode.alignment) && (currentAMRNode.testAlignment == i)) {
                setHighlightedWrong();
            }
            screenWriter.drawString(x + (token.length() / 2), y, shortcutsDisplay.get(i).toString());
            screenWriter.drawString(x, y + 1, token + " ");
            x += token.length()+1;
            resetHighlighted();
        }
        return y;
    }

    public Triple<String,String,String> separateSections(String line, String startBracket, String endBracket) {
        String start;
        String highlight;
        String end;

        if (line.split(startBracket).length != 2) {
            start = "";
            line = line.replace(startBracket.replaceAll("\\\\",""),"");
        }
        else {
            start = line.split(startBracket)[0];
            line = line.split(startBracket)[1];
        }

        if (line.split(endBracket).length != 2) {
            end = "";
            highlight = line.replace(endBracket.replaceAll("\\\\",""),"");
        }
        else {
            highlight = line.split(endBracket)[0];
            end = line.split(endBracket)[1];
        }

        return new Triple<String, String, String>(start,highlight,end);
    }

    public void printAMR(int x, int y) {
        String amrString = amr.toString(AMR.AlignmentPrinting.ALL, currentAMRNode);
        String[] lines = amrString.split("\n");
        for (String line : lines) {
            line = line.replaceAll("\t", "   ");

            if (line.contains("=*==#=") && line.contains("*=*#=#")) {
                setHighlightedPinned();
                printLineWithHighlights(x, y, line, "=\\*==#=","\\*=\\*#=#");
            }
            else if (line.contains("=*==!=") && line.contains("*=*!=!")) {
                setHighlightedWrong();
                printLineWithHighlights(x, y, line, "=\\*==!=","\\*=\\*!=!");
            }
            else if (line.contains("=*==&=") && line.contains("*=*&=&")) {
                setHighlightedNonProjective();
                printLineWithHighlights(x, y, line, "=\\*==&=","\\*=\\*&=&");
            }
            else if (line.contains("=*=") && line.contains("*=*")) {
                setHighlighted();
                printLineWithHighlights(x, y, line, "=\\*=", "\\*=\\*");
            }
            else if (line.contains("=!=") && line.contains("!=!")) {
                setWrong();
                printLineWithHighlights(x, y, line, "=!=", "!=!");
            }
            else if (line.contains("=&=") && line.contains("&=&")) {
                setNonProjective();
                printLineWithHighlights(x, y, line, "=&=", "&=&");
            }
            else if (line.contains("=#=") && line.contains("#=#")) {
                setPinned();
                printLineWithHighlights(x, y, line, "=#=", "#=#");
            }
            else {
                screenWriter.drawString(x, y, line);
            }
            y++;
        }
    }

    public void printLineWithHighlights(int x, int y, String line, String startBracket, String endBracket) {
        Triple<String,String,String> sections = separateSections(line,startBracket,endBracket);

        String start = sections.first;
        String highlight = sections.second;
        String end = sections.third;

        screenWriter.drawString(x + start.length(), y, highlight);
        resetHighlighted();
        screenWriter.drawString(x, y, start);
        screenWriter.drawString(x + start.length() + highlight.length(), y, end);
    }

}
