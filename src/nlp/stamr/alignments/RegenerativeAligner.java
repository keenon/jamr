package nlp.stamr.alignments;

import edu.stanford.nlp.classify.km.kernels.GaussianRBFKernel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.IdentityHashSet;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import nlp.experiments.AMRPipeline;
import nlp.keenonutils.JaroWinklerDistance;
import nlp.stamr.AMR;
import nlp.stamr.AMRConstants;
import nlp.stamr.AMRSlurp;
import nlp.stamr.annotation.AnnotationManager;
import nlp.stamr.datagen.DumpSequence;

import java.io.IOException;
import java.util.*;

import gurobi.*;
import nlp.stamr.utils.MiscUtils;

/**
 * Created by keenon on 2/22/15.
 *
 * Formulates the alignment problem as an ILP involving correct sequence labellings, to minimize DICT tags and maximize
 * the ability of the sequence tagger to regenerate the AMR.
 *
 * To do this we first run through the AMR and segment out different types of nodes, with the names, verbs, time expressions,
 * named entity types, objects, and unattached values.
 *
 * Then we run a hand-calibrated scoring system to find a max-match between the nodes and the tokens of the sentence, given
 * a set of restrictions.
 */
public class RegenerativeAligner {

    public static boolean DEBUG = false;

    public static void main(String[] args) throws IOException {
        cleanSubbank();

        AMR[] bank = AMRSlurp.slurp("data/deft-amr-100.txt", AMRSlurp.Format.LDC);
        dumpAlignmentProcess(new AMR[]{bank[4]});

        /*
        AMR amr = AMRSlurp.parseAMRTree("(c / country :name (n / name :op1 \"Tajikistan\"))");
        amr.sourceText = "the country named Tajikistan".split(" ");
        AnnotationManager manager= new AnnotationManager();
        manager.annotate(amr);

        dumpAlignmentProcess(new AMR[]{
                amr
        });
        */
    }

    private static void dumpAlignmentProcess(AMR[] minibank) {
        align(minibank);
        for (int j = 0; j < Math.min(minibank.length,4); j++) {
            AMR amr = minibank[j];
            for (int i = 0; i < amr.sourceText.length; i++) {
                String type = DumpSequence.getType(amr, i);
                if (DEBUG) System.out.println(amr.sourceText[i] + ": " + type);
            }
            System.out.println(amr.toString(AMR.AlignmentPrinting.ALL));
        }
    }

    private static void cleanSubbank() throws IOException {
        AMR[] bank = AMRSlurp.slurp("data/deft-amr-100.txt", AMRSlurp.Format.LDC);
        for (int i = 10; i < bank.length; i++) {
            for (AMR.Node n : bank[i].nodes) n.alignmentFixed = false;
        }
        AMRSlurp.burp("data/deft-amr-100.txt", AMRSlurp.Format.LDC, bank, AMR.AlignmentPrinting.FIXED_ONLY, false);
    }

    private static void burpSubbank() throws IOException {
        AMR[] bank = AMRSlurp.slurp("data/deft-amr-release-r3-proxy-train.txt", AMRSlurp.Format.LDC);
        AMR[] subbank = new AMR[100];
        Set<Integer> blocked = new HashSet<>();
        Random r = new Random(42);
        int i = 0;
        while (i < subbank.length) {
            int j = r.nextInt(bank.length);
            if (!blocked.contains(j)) {
                subbank[i] = bank[j];
                blocked.add(j);
                i++;
            }
        }
        AMRSlurp.burp("data/deft-amr-100.txt", AMRSlurp.Format.LDC, subbank, AMR.AlignmentPrinting.NONE, false);
    }

    public static void align(AMR[] bank) {
        try {
            ConstraintSet[] constraintSets = new ConstraintSet[bank.length];
            for (int i = 0; i < bank.length; i++) {
                for (AMR.Node n : bank[i].nodes) {
                    n.alignment = 0;
                }
                System.out.println("building constraint set "+i+"/"+bank.length);
                constraintSets[i] = buildConstraintSet(bank[i], false);
            }

            joinConstraintSets(constraintSets);

            for (int i = 0; i < bank.length; i++) {
                System.out.println("solving constraint set "+i+"/"+bank.length);
                decodeConstraintSet(constraintSets[i], false);
            }
        } catch (GRBException e) {
            e.printStackTrace();
        }
    }

    private static class ConstraintSet {
        public AMR amr;
        public GRBVar[][] vars;
        public String[][] alignmentType;
        String[] tokens;
        Annotation annotation;
        List<AMR.Node> allowedNodes;
        List<Integer> allowedTokens;
        GRBEnv env;
        GRBModel model;
        GRBQuadExpr goalExpr;
    }

    private static void decodeConstraintSet(ConstraintSet set, boolean alreadyRelaxed) throws GRBException {

        try {
            // Run the actual optimization!

            set.model.setObjective(set.goalExpr);
            set.model.optimize();

            // Get the values of the model

            for (int i = 0; i < set.allowedNodes.size(); i++) {
                for (int j = 0; j < set.allowedTokens.size(); j++) {
                    if (set.vars[i][j].get(GRB.DoubleAttr.X) == 1) {
                        set.allowedNodes.get(i).alignment = set.allowedTokens.get(j);
                        break;
                    }
                }
            }

            // Go through and back-fill a bit

            for (AMR.Node node : set.allowedNodes) {
                if (node.title.endsWith("-quantity") || node.title.endsWith("-entity")) {
                    if (set.amr.outgoingArcs.containsKey(node)) {
                        int minChild = Integer.MAX_VALUE;
                        for (AMR.Arc arc : set.amr.outgoingArcs.get(node)) {
                            if (arc.tail.alignment < minChild) {
                                minChild = arc.tail.alignment;
                            }
                        }
                        if (minChild < Integer.MAX_VALUE) {
                            for (AMR.Node node2 : set.allowedNodes) {
                                if (node2 != node && node2.alignment == node.alignment) {
                                    node2.alignment = minChild;
                                }
                            }
                            node.alignment = minChild;
                        }
                    }
                }
            }

            // Through some irritating bug in the system, sometimes exact matches will go ignored, so we should fix that
            // retroactively, because I'm too sleepy to go fix it properly

            for (int i = 0; i < set.tokens.length; i++) {
                if (set.amr.nodesWithAlignment(i).size() == 0) {
                    // We have a NONE node
                    List<AMR.Node> identityMatches = new ArrayList<>();
                    for (AMR.Node node : set.amr.nodes) {
                        if (node.title.equalsIgnoreCase(set.tokens[i])) {
                            identityMatches.add(node);
                        }
                    }
                    if (identityMatches.size() == 1) {
                        identityMatches.get(0).alignment = i;
                    }
                }
            }

            // Dispose of model and environment
            set.model.dispose();
            set.env.dispose();
        }
        catch (Exception e) {
            set.model.dispose();
            set.env.dispose();

            if (!alreadyRelaxed) {
                System.err.println("Had infeasible model, falling back to unconstrained model");
                ConstraintSet setUnconstrained = buildConstraintSet(set.amr, true);
                decodeConstraintSet(setUnconstrained, true);
            }
            else {
                System.err.println("Had infeasible model, backing off to EasyFirstEM");
                EasyFirstAligner.align(set.amr);
            }
        }
    }

    private static void joinConstraintSets(ConstraintSet[] sets) throws GRBException {
        Map<String,Set<Pair<ConstraintSet,Integer>>> tokenSets = new HashMap<>();
        for (ConstraintSet set : sets) {
            for (int i = 0; i < set.allowedTokens.size(); i++) {
                String s = set.tokens[set.allowedTokens.get(i)].toLowerCase();
                if (!tokenSets.containsKey(s)) tokenSets.put(s, new HashSet<>());
                tokenSets.get(s).add(new Pair<>(set, i));
            }
        }

        for (Set<Pair<ConstraintSet,Integer>> wordOccurancesInData : tokenSets.values()) {
            Map<String, Set<Triple<ConstraintSet,Integer,Integer>>> alignmentSets = new HashMap<>();

            double totalExampleCount = 0;

            for (Pair<ConstraintSet,Integer> p : wordOccurancesInData) {
                int j = p.second;

                for (int i = 0; i < p.first.allowedNodes.size(); i++) {
                    String type = p.first.alignmentType[i][j];
                    if (type.equals("DICT")) {
                        type = "DICT"+p.first.allowedNodes.get(i).title;
                    }
                    if (!alignmentSets.containsKey(type)) alignmentSets.put(type, new HashSet<>());
                    alignmentSets.get(type).add(new Triple<>(p.first, i, p.second));

                    totalExampleCount++;
                }
            }

            // We want to maximize agreement between the different possible assignments, without too much hassle
            // The stupidly easy way to do this is to add a linear term corresponding to the first pass soft EM
            // alignments for that token, which is just observed counts.

            // The complicated way involves putting an exponential number of quadratic terms into the system, and
            // praying for rain.

            for (Set<Triple<ConstraintSet,Integer,Integer>> alignmentSet : alignmentSets.values()) {

                // This penalizes appropriately for each alignment, assuming it's a probability space

                double score = Math.min(0.5, -Math.log(totalExampleCount / alignmentSet.size()) * 0.01);

                for (Triple<ConstraintSet,Integer,Integer> triple : alignmentSet) {
                    triple.first.goalExpr.addTerm(score, triple.first.vars[triple.second][triple.third]);
                }
            }
        }
    }

    private static ConstraintSet buildConstraintSet(AMR amr, boolean relaxContraints) throws GRBException {
        GRBEnv env = new GRBEnv();
        env.set(GRB.IntParam.OutputFlag, 0);

        GRBModel model = new GRBModel(env);
        GRBQuadExpr goalExpr = new GRBQuadExpr();

        String[] tokens = amr.sourceText;
        Annotation annotation = amr.multiSentenceAnnotationWrapper.sentences.get(0).annotation;

        // Get all the tokens that aren't up for discussion, b/c we grabbed them using NER or SUTime
        Pair<List<AMR>,Set<Integer>> pair = AMRPipeline.getDeterministicChunks(tokens, annotation);
        List<AMR> gen = pair.first;
        Set<Integer> blockedTokens = new HashSet<>(); // pair.second;
        Set<AMR.Node> blockedNodes = new IdentityHashSet<>();

        Collections.sort(gen, new Comparator<AMR>() {
            @Override
            public int compare(AMR o1, AMR o2) {
                return -1*Integer.compare(o1.nodes.size(), o2.nodes.size());
            }
        });

        for (AMR chunk : gen) {
            if (DEBUG) System.out.println("Trying to match:");
            if (DEBUG) System.out.println(chunk.toString());
            Set<Pair<AMR.Node,AMR.Node>> match = amr.getMatchingSubtreeOrEmpty(chunk);
            if (match.size() > 0) {
                boolean alreadyTaken = false;
                for (Pair<AMR.Node, AMR.Node> p : match) {
                    if (blockedNodes.contains(p.first)) {
                        alreadyTaken = true;
                        break;
                    }
                }
                if (!alreadyTaken) {
                    for (Pair<AMR.Node, AMR.Node> p : match) {
                        p.first.alignment = p.second.alignment;
                        blockedNodes.add(p.first);
                        blockedTokens.add(p.first.alignment);
                    }
                }
            }
        }
        if (DEBUG) System.out.println("Blocked nodes: "+blockedNodes);

        Set<String> rewardLabels = new HashSet<String>(){{
            add("VERB");
            add("IDENTITY");
            add("LEMMA");
            // add("NAME");
            // add("COREF");
        }};

        Set<String> punishLabels = new HashSet<String>(){{
            add("DICT");
        }};

        List<Integer> allowedTokens = new ArrayList<>();
        for (int i = 0; i < amr.sourceText.length; i++) {
            String pos = annotation.get(CoreAnnotations.TokensAnnotation.class).get(i).get(CoreAnnotations.PartOfSpeechAnnotation.class);
            // Block determiners and prepositions from alignment
            if (pos.equals("DT") || pos.equals("IN") || pos.equals("TO") || pos.equals("POS") || pos.equals(".")) {}
            else {
                if (!blockedTokens.contains(i)) allowedTokens.add(i);
            }
        }
        if (DEBUG) System.out.println("Allowed tokens: "+allowedTokens);

        List<AMR.Node> allowedNodes = new ArrayList<>();
        for (AMR.Node node : amr.nodes) {
            if (!blockedNodes.contains(node)) allowedNodes.add(node);
        }
        if (DEBUG) System.out.println("Allowed nodes: "+allowedNodes);

        String[][] alignmentType = new String[allowedNodes.size()][allowedTokens.size()];
        for (int i = 0; i < allowedNodes.size(); i++) {
            for (int j = 0; j < allowedTokens.size(); j++) {
                alignmentType[i][j] = DumpSequence.getType(allowedNodes.get(i), allowedTokens.get(j), tokens, annotation, amr);
            }
        }
        if (DEBUG) System.out.println("Alignment types: "+Arrays.deepToString(alignmentType));

        // Look through all non-blocked nodes and non-blocked tokens, and find the match that minimizes the number of DICT
        // elements, and minimizes the edit distance between DICT elements and their corresponding tokens.

        GRBVar[][] vars = new GRBVar[allowedNodes.size()][allowedTokens.size()];
        for (int i = 0; i < allowedNodes.size(); i++) {
            for (int j = 0; j < allowedTokens.size(); j++) {
                vars[i][j] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "align:" + i + "-" + j);
            }
        }
        model.update();

        // Make sure all node alignments sum to 1, i.e. all values get an assignment

        for (int i = 0; i < allowedNodes.size(); i++) {
            GRBLinExpr expr = new GRBLinExpr();
            for (int j = 0; j < vars[i].length; j++) {
                expr.addTerm(1.0, vars[i][j]);
            }
            model.addConstr(expr, GRB.EQUAL, 1.0, "c" + i);
        }

        // Add the goal: minimize the number of punished labels, and maximize the number of rewarded labels

        for (int i = 0; i < allowedNodes.size(); i++) {
            for (int j = 0; j < allowedTokens.size(); j++) {
                if (punishLabels.contains(alignmentType[i][j])) {
                    goalExpr.addTerm(3.0, vars[i][j]);
                }
                else if (rewardLabels.contains(alignmentType[i][j])) {
                    goalExpr.addTerm(-1.0, vars[i][j]);
                }
            }
        }

        // Add a very small reward to bias known NER-type labels to align with ARG-adjacent nodes
        // This lets us automatically capture the "sailor" phenomenon.

        Set<Pair<Integer,Integer>> nerPairEqualityException = new HashSet<>();
        for (int i = 0; i < allowedNodes.size(); i++) {
            if (AMRConstants.nerTaxonomy.contains(allowedNodes.get(i).title)) {
                for (int i2 = 0; i2 < allowedNodes.size(); i2++) {
                    if (i2 == i) continue;
                    if (amr.nodesAdjacent(allowedNodes.get(i), allowedNodes.get(i2))) {
                        AMR.Arc arc = amr.getAdjacentNodesArc(allowedNodes.get(i), allowedNodes.get(i2));
                        if (arc.title.contains("ARG")) {
                            for (int j = 0; j < allowedTokens.size(); j++) {
                                if (alignmentType[i][j].equals("DICT") && alignmentType[i2][j].equals("VERB")) {
                                    if (DEBUG) System.out.println("Encouraging "+allowedNodes.get(i)+" and "+allowedNodes.get(i2)+" to align to "+tokens[allowedTokens.get(j)]+" together");
                                    goalExpr.addTerm(-0.25, vars[i][j], vars[i2][j]);
                                    nerPairEqualityException.add(new Pair<>(i, i2));
                                }
                            }
                        }
                        // Trying to match for cases like ``Russian'', where we don't have exact matches
                        if (arc.title.equals("name") || allowedNodes.get(i).title.contains("-entity") || allowedNodes.get(i).title.contains("-quantity")) {
                            boolean allDict = true;
                            for (int j = 0; j < allowedTokens.size(); j++) {
                                if (!alignmentType[i][j].equals("DICT") || !alignmentType[i2][j].equals("DICT")) {
                                    allDict = false;
                                    break;
                                }
                            }
                            if (allDict) {
                                for (int j = 0; j < allowedTokens.size(); j++) {
                                    goalExpr.addTerm(-0.15, vars[i][j], vars[i2][j]);
                                }
                            }
                        }
                    }
                }
            }
            // More gentle encouragement for the ``Russian'' case
            if (allowedNodes.get(i).title.equals("name")) {
                for (int i2 = 0; i2 < allowedNodes.size(); i2++) {
                    if (i2 == i) continue;
                    if (amr.nodesAdjacent(allowedNodes.get(i), allowedNodes.get(i2))) {
                        AMR.Arc arc = amr.getAdjacentNodesArc(allowedNodes.get(i), allowedNodes.get(i2));
                        if (arc.title.equals("op1")) {
                            boolean allDict = true;
                            for (int j = 0; j < allowedTokens.size(); j++) {
                                if (!alignmentType[i][j].equals("DICT") || !alignmentType[i2][j].equals("DICT")) {
                                    allDict = false;
                                    break;
                                }
                            }
                            if (allDict) {
                                for (int j = 0; j < allowedTokens.size(); j++) {
                                    goalExpr.addTerm(-0.15, vars[i][j], vars[i2][j]);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hard constraint that nodes that are not the first reference must be either COREF or identical to parent

        Set<Integer> nerIndividualEqualityException = new HashSet<>();
        List<AMR.Node> dfs = amr.depthFirstSearch();

        for (int i = 0; i < allowedNodes.size(); i++) {
            if (allowedNodes.get(i).type != AMR.NodeType.ENTITY) continue;
            if (allowedNodes.get(i).title.equals("name")) continue;

            int myDfsIndex = MiscUtils.indexOfIdentity(dfs, allowedNodes.get(i));
            int firstMatch = -1;

            for (int j = 0; j < myDfsIndex; j++) {
                if (dfs.get(j).ref.equals(allowedNodes.get(i).ref)) {
                    firstMatch = j;
                    break;
                }
            }

            if (firstMatch == -1) continue;

            AMR.Node parentNode = dfs.get(firstMatch);
            if (blockedNodes.contains(parentNode)) {
                if (DEBUG) {
                    System.out.println("Found fixed parent " + amr.getParentArc(parentNode) + " for node " + amr.getParentArc(allowedNodes.get(i)));
                }

                // Either i is coref or i == parentNode.alignment

                GRBLinExpr dif = new GRBLinExpr();

                List<Integer> allowedTerms = new ArrayList<>();

                for (int k = 0; k < allowedTokens.size(); k++) {
                    if (allowedTokens.get(k) == parentNode.alignment) {
                        dif.addTerm(1.0, vars[i][k]);
                        allowedTerms.add(k);
                    }
                    else if (alignmentType[i][k].equals("COREF")) {
                        dif.addTerm(1.0, vars[i][k]);
                        allowedTerms.add(k);
                    }
                }

                if (DEBUG) System.out.println("Allowed terms: "+allowedTerms);

                if (!relaxContraints) {
                    model.addConstr(1.0, GRB.EQUAL, dif, "fixed-or-coref-" + i);
                }

                nerIndividualEqualityException.add(i);
            }
            else {
                int i2 = MiscUtils.indexOfIdentity(allowedNodes, parentNode);
                if (DEBUG) {
                    System.out.println("Found parent " + amr.getParentArc(allowedNodes.get(i2)) + " for node " + amr.getParentArc(allowedNodes.get(i)));
                }

                // i is the coref
                // i2 is the parent ref

                for (int j = 0; j < allowedTokens.size(); j++) {
                    GRBLinExpr dif = new GRBLinExpr();
                    dif.addTerm(1.0, vars[i][j]);
                    dif.addTerm(-1.0, vars[i2][j]);

                    for (int k = 0; k < allowedTokens.size(); k++) {
                        if (k == j) continue;
                        if (alignmentType[i][k].equals("COREF")) {
                            dif.addTerm(1.0, vars[i][k]);
                        }
                    }

                    if (!relaxContraints) {
                        model.addConstr(0.0, GRB.GREATER_EQUAL, dif, "same-" + i + "-" + i2 + "-" + j);
                    }

                    // goalExpr.multAdd(-2.0, dif);
                    // goalExpr.addTerm(-2.0, vars[i][j], vars[lowestIndexCoref][j]);

                    nerPairEqualityException.add(new Pair<>(i, i2));
                }
            }
        }

        // Make sure we punish nodes that aren't immediately adjacent to the same token
        // TODO: This may be slightly too restrictive - would be nice to come up with a linear "clustering" constraint

        GRBVar[][][] caps = new GRBVar[allowedNodes.size()][allowedNodes.size()][allowedTokens.size()];

        for (int i = 0; i < allowedNodes.size(); i++) {
            if (nerIndividualEqualityException.contains(i)) continue;

            outer:
            for (int i2 = 0; i2 < allowedNodes.size(); i2++) {
                if (i == i2) continue;
                if (nerIndividualEqualityException.contains(i2)) continue;

                for (Pair<Integer, Integer> exception : nerPairEqualityException) {
                    if ((exception.first == i && exception.second == i2) || (exception.first == i2 && exception.second == i))
                        continue outer;
                }
                for (int j = 0; j < allowedTokens.size(); j++) {
                    if (!amr.nodesAdjacent(allowedNodes.get(i), allowedNodes.get(i2))
                            && !amr.connectedByName(allowedNodes.get(i), allowedNodes.get(i2))
                            && !allowedNodes.get(i).title.equals(allowedNodes.get(i2))) {
                        caps[i][i2][j] = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, "float" + i + "-" + j);
                    }
                }
            }
        }

        model.update();

        for (int i = 0; i < allowedNodes.size(); i++) {
            if (nerIndividualEqualityException.contains(i)) continue;

            outer:
            for (int i2 = 0; i2 < allowedNodes.size(); i2++) {
                if (i == i2) continue;
                if (nerIndividualEqualityException.contains(i2)) continue;
                for (Pair<Integer, Integer> exception : nerPairEqualityException) {
                    if ((exception.first == i && exception.second == i2) || (exception.first == i2 && exception.second == i))
                        continue outer;
                }
                for (int j = 0; j < allowedTokens.size(); j++) {
                    if (!amr.nodesAdjacent(allowedNodes.get(i), allowedNodes.get(i2))
                            && !amr.connectedByName(allowedNodes.get(i), allowedNodes.get(i2))
                            && !allowedNodes.get(i).title.equals(allowedNodes.get(i2))) {
                        GRBLinExpr sumValue = new GRBLinExpr();
                        sumValue.addTerm(1.0, vars[i][j]);
                        sumValue.addTerm(1.0, vars[i2][j]);
                        sumValue.addTerm(-1.0, caps[i][i2][j]);
                        sumValue.addConstant(-1.0);
                        model.addConstr(sumValue, GRB.LESS_EQUAL, 0.0, "floor" + j + "-" + i + "-" + i2);

                        // We will take this hit twice (symmetrically) for every time we align to a non-adjacent node
                        goalExpr.addTerm(0.8 / 2, caps[i][i2][j]);
                    }
                }
            }
        }

        if (!relaxContraints) {
            // Make sure we never assign two nodes to the same token that would imply different tag types for the sequence model
            // Unless:
            //    - we know that this is a DICT to VERB exception
            //    - this is a coref situation and we need to be able to map to the same thing

            for (int j = 0; j < allowedTokens.size(); j++) {
                for (int i = 0; i < allowedNodes.size(); i++) {
                    if (nerIndividualEqualityException.contains(i)) continue;
                    outer:
                    for (int i2 = 0; i2 < allowedNodes.size(); i2++) {
                        if (i == i2) continue;
                        if (nerIndividualEqualityException.contains(i2)) continue;
                        for (Pair<Integer, Integer> exception : nerPairEqualityException) {
                            if ((exception.first == i && exception.second == i2) || (exception.first == i2 && exception.second == i))
                                continue outer;
                        }

                        if (!alignmentType[i][j].equals(alignmentType[i2][j])) {
                            GRBLinExpr expr = new GRBLinExpr();
                            expr.addTerm(1.0, vars[i][j]);
                            expr.addTerm(1.0, vars[i2][j]);
                            model.addConstr(expr, GRB.LESS_EQUAL, 1.0, "type" + j + "-" + i + "-" + i2);
                        }
                    }
                }
            }
        }

        for (int i = 0; i < allowedNodes.size(); i++) {
            for (int j = 0; j < allowedTokens.size(); j++) {

                // Reward inverse edit distance slightly for DICT elements

                if (alignmentType[i][j].equals("DICT")) {
                    if (!allowedNodes.get(i).title.equals("name")) {
                        if (!AMRConstants.nerTaxonomy.contains(allowedNodes.get(i).title)) {
                            String nodeTitle = allowedNodes.get(i).title;
                            if (nodeTitle.contains("-") && nodeTitle.split("-").length == 2) {
                                String[] parts = nodeTitle.split("-");
                                try {
                                    Integer.parseInt(parts[1]);
                                    nodeTitle = parts[0];
                                }
                                catch (Exception e) {}
                            }
                            double dist = JaroWinklerDistance.distance(nodeTitle, tokens[allowedTokens.get(j)]);
                            // Separate out the different nodes
                            dist = Math.pow(dist, 4.0);
                            if (DEBUG) System.out.println("Dist for " + allowedNodes.get(i).title + ", " + tokens[allowedTokens.get(j)] + ": " + dist);
                            goalExpr.addTerm(-1.5 * dist, vars[i][j]);
                        }
                    }
                }
            }
        }

        ConstraintSet set = new ConstraintSet();
        set.amr = amr;
        set.tokens = tokens;
        set.annotation = annotation;
        set.alignmentType = alignmentType;
        set.allowedNodes = allowedNodes;
        set.allowedTokens = allowedTokens;
        set.vars = vars;
        set.model = model;
        set.goalExpr = goalExpr;
        set.env = env;
        return set;
    }

}
