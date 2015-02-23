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
        // AMR[] bank = AMRSlurp.slurp("data/deft-amr-100.txt", AMRSlurp.Format.LDC);
        // dumpAlignmentProcess(bank);

        AMR amr = AMRSlurp.parseAMRTree("(c / country :name (n / name :op1 \"Tajikistan\"))");
        amr.sourceText = "the country named Tajikistan".split(" ");
        AnnotationManager manager= new AnnotationManager();
        manager.annotate(amr);

        dumpAlignmentProcess(new AMR[]{
                amr
        });
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
            GRBEnv env = new GRBEnv();
            env.set(GRB.IntParam.OutputFlag, 0);

            ConstraintSet[] constraintSets = new ConstraintSet[bank.length];
            for (int i = 0; i < bank.length; i++) {
                System.out.println("building constraint set "+i+"/"+bank.length);
                constraintSets[i] = buildConstraintSet(bank[i], env);
            }

            joinConstraintSets(constraintSets);

            // Decode all the resulting constraint sets

            for (int i = 0; i < bank.length; i++) {
                decodeConstraintSet(constraintSets[i]);
            }

            env.dispose();
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
        GRBModel model;
        GRBQuadExpr goalExpr;
    }

    private static void decodeConstraintSet(ConstraintSet set) throws GRBException {

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

            // Dispose of model and environment
            set.model.dispose();
        }
        catch (Exception e) {
            System.err.println("Had infeasible model, falling back to EasyFirstAligner");
            EasyFirstAligner.align(set.amr);
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

                double score = Math.min(0.5, -Math.log(totalExampleCount / alignmentSet.size()) * 0.001);

                for (Triple<ConstraintSet,Integer,Integer> triple : alignmentSet) {
                    triple.first.goalExpr.addTerm(score, triple.first.vars[triple.second][triple.third]);
                }
            }
        }
    }

    private static ConstraintSet buildConstraintSet(AMR amr, GRBEnv env) throws GRBException {

        GRBModel model = new GRBModel(env);
        GRBQuadExpr goalExpr = new GRBQuadExpr();

        String[] tokens = amr.sourceText;
        Annotation annotation = amr.multiSentenceAnnotationWrapper.sentences.get(0).annotation;

        // Get all the tokens that aren't up for discussion, b/c we grabbed them using NER or SUTime
        Pair<List<AMR>,Set<Integer>> pair = AMRPipeline.getDeterministicChunks(tokens, annotation);
        List<AMR> gen = pair.first;
        Set<Integer> blockedTokens = pair.second;
        Set<AMR.Node> blockedNodes = new IdentityHashSet<>();

        for (AMR chunk : gen) {
            if (DEBUG) System.out.println("Trying to match:");
            if (DEBUG) System.out.println(chunk.toString());
            Set<Pair<AMR.Node,AMR.Node>> match = amr.getMatchingSubtreeOrEmpty(chunk);
            if (match.size() > 0) {
                for (Pair<AMR.Node, AMR.Node> p : match) {
                    p.first.alignment = p.second.alignment;
                    blockedNodes.add(p.first);
                }
            }
        }
        if (DEBUG) System.out.println("Blocked nodes: "+blockedNodes);

        Set<String> rewardLabels = new HashSet<String>(){{
            add("VERB");
            add("IDENTITY");
            add("LEMMA");
            add("NAME");
            add("COREF");
        }};

        Set<String> punishLabels = new HashSet<String>(){{
            add("DICT");
        }};

        List<Integer> allowedTokens = new ArrayList<>();
        for (int i = 0; i < amr.sourceText.length; i++) {
            if (!blockedTokens.contains(i)) allowedTokens.add(i);
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

        // Make sure we never assign nodes that aren't immediately adjacent to the same token, or have the same name
        // TODO: This may be slightly too restrictive - would be nice to come up with a linear "clustering" constraint

        for (int j = 0; j < allowedTokens.size(); j++) {
            for (int i = 0; i < allowedNodes.size(); i++) {
                for (int i2 = 0; i2 < allowedNodes.size(); i2++) {
                    if (i == i2) continue;
                    if (!amr.nodesAdjacent(allowedNodes.get(i), allowedNodes.get(i2))
                            && !amr.connectedByName(allowedNodes.get(i), allowedNodes.get(i2))
                            && !allowedNodes.get(i).ref.equals(allowedNodes.get(i2).ref)
                            && !allowedNodes.get(i).title.equals(allowedNodes.get(i2).title)) {
                        GRBLinExpr expr = new GRBLinExpr();
                        expr.addTerm(1.0, vars[i][j]);
                        expr.addTerm(1.0, vars[i2][j]);
                        model.addConstr(expr, GRB.LESS_EQUAL, 1.0, "adjacent" + j + "-" + i + "-" + i2);
                    }
                }
            }
        }

        // Add the goal: minimize the number of punished labels, and maximize the number of rewarded labels

        for (int i = 0; i < allowedNodes.size(); i++) {
            for (int j = 0; j < allowedTokens.size(); j++) {
                if (punishLabels.contains(alignmentType[i][j])) {
                    goalExpr.addTerm(1.0, vars[i][j]);
                }
                else if (rewardLabels.contains(alignmentType[i][j])) {
                    goalExpr.addTerm(-1.0, vars[i][j]);
                }

                // Reward inverse edit distance slightly for DICT elements

                if (alignmentType[i][j].equals("DICT")) {
                    if (!allowedNodes.get(i).title.equals("name")) {
                        if (!AMRConstants.nerTaxonomy.contains(allowedNodes.get(i).title)) {
                            double dist = JaroWinklerDistance.distance(allowedNodes.get(i).title, tokens[allowedTokens.get(j)]);
                            dist = Math.pow(dist, 4.0);
                            if (DEBUG) System.out.println("Dist for " + allowedNodes.get(i).title + ", " + tokens[allowedTokens.get(j)] + ": " + dist);
                            goalExpr.addTerm(-0.5 * dist, vars[i][j]);
                        }
                    }
                }
            }
        }

        // Add a very small reward to bias known NER-type labels to align with ARG-adjacent nodes
        // This lets us automatically capture the "sailor" phenomenon.

        Set<Pair<Integer,Integer>> dictVerbExceptions = new HashSet<>();
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
                                    dictVerbExceptions.add(new Pair<>(i, i2));
                                }
                            }
                        }
                        // Trying to match for cases like ``Russian'', where we don't have exact matches
                        if (arc.title.equals("name")) {
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

        // Make sure we never assign two nodes to the same token that would imply different tag types for the sequence model

        for (int j = 0; j < allowedTokens.size(); j++) {
            for (int i = 0; i < allowedNodes.size(); i++) {
                outer: for (int i2 = 0; i2 < allowedNodes.size(); i2++) {
                    if (i == i2) continue;
                    for (Pair<Integer,Integer> exception : dictVerbExceptions) {
                        if ((exception.first == i && exception.second == i2) || (exception.first == i2 && exception.second == i)) continue outer;
                    }

                    if (!alignmentType[i][j].equals(alignmentType[i2][j])) {
                        GRBLinExpr expr = new GRBLinExpr();
                        expr.addTerm(1.0, vars[i][j]);
                        expr.addTerm(1.0, vars[i2][j]);
                        model.addConstr(expr, GRB.LESS_EQUAL, 1.0, "type"+j+"-"+i+"-"+i2);
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
        return set;
    }

}
