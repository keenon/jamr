package nlp.stamr.alignments;

import nlp.stamr.AMR;
import nlp.stamr.AMRConstants;
import edu.stanford.nlp.util.IdentityHashSet;
import edu.stanford.nlp.util.Pair;

import java.util.*;

import static nlp.keenonutils.Functional.*;

/**
 * This is the primarily rule-based attempt to express all the intuition from labeling 500 sentences by hand....
 */
public class EasyFirstAligner {

    public static class PotentialAlignments {
        public AMR amr;
        Map<AMR.Node,Set<Integer>> potentialAlignments = new IdentityHashMap<AMR.Node, Set<Integer>>();
        Set<Set<AMR.Node>> pinnedAlignments = new IdentityHashSet<Set<AMR.Node>>();
        Set<Pair<AMR.Node,AMR.Node>> suggestedAlignments = new IdentityHashSet<Pair<AMR.Node, AMR.Node>>();

        public PotentialAlignments(AMR amr) {
            this.amr = amr;
            for (AMR.Node node : amr.nodes) {
                Set<Integer> potentialAlignmentSet = new HashSet<Integer>();
                potentialAlignments.put(node, potentialAlignmentSet);

                Set<AMR.Node> pinSet = new IdentityHashSet<AMR.Node>();
                pinSet.add(node);
                pinnedAlignments.add(pinSet);
            }
        }

        public boolean hasForcedAlignment(AMR.Node node) {
            return unionAlignmentsForPinset(getPinSet(node)).size() == 1;
        }

        public int getForcedAlignment(AMR.Node node) {
            assert hasForcedAlignment(node);
            return unionAlignmentsForPinset(getPinSet(node)).iterator().next();
        }

        public boolean noInformationAbout(AMR.Node node) {
            return unionAlignmentsForPinset(getPinSet(node)).size() == amr.sourceText.length;
        }

        public void setAlignments(AMR.Node node, Set<Integer> alignments) {
            potentialAlignments.put(node, alignments);
        }

        public void intersectAlignments(AMR.Node node, Set<Integer> alignments) {
            if (alignments.size() == 0) return;

            alignments.retainAll(potentialAlignments.get(node));

            // Don't let us intersect away to nothing
            if (alignments.size() > 0) {
                potentialAlignments.put(node, alignments);
            }
        }

        public void unionAlignments(AMR.Node node, Set<Integer> alignments) {
            potentialAlignments.get(node).addAll(alignments);
        }

        public Set<Integer> getAlignments(AMR.Node node) {
            return unionAlignmentsForPinset(getPinSet(node));
        }

        public Set<AMR.Node> getPinSet(AMR.Node node) {
            for (Set<AMR.Node> set : pinnedAlignments) {
                if (set.contains(node)) {
                    return set;
                }
            }
            throw new IllegalStateException("Shouldn't get here: "+node.toString());
        }

        public void pinAlignments(AMR.Node node, AMR.Node node2) {
            if (node == null || node2 == null) return;
            if (node == node2) return;
            Set<AMR.Node> pinSet1 = getPinSet(node);
            Set<AMR.Node> pinSet2 = getPinSet(node2);
            if (pinSet1 == pinSet2) return;
            pinSet1.addAll(pinSet2);
            pinnedAlignments.remove(pinSet2);

            // Verify that the states still work
            for (AMR.Node n : amr.nodes) {
                getPinSet(n);
            }
        }

        public void suggestAlignment(AMR.Node node, AMR.Node node2) {
            suggestedAlignments.add(new Pair<AMR.Node,AMR.Node>(node, node2));
        }

        public Set<Integer> unionAlignmentsForPinset(Set<AMR.Node> pinSet) {
            Set<Integer> alignments = new HashSet<Integer>();
            for (AMR.Node node : pinSet) {
                alignments.addAll(potentialAlignments.get(node));
            }
            if (alignments.size() == 0) {
                for (int i = 0; i < amr.sourceText.length; i++) {
                    alignments.add(i);
                }
            }
            return alignments;
        }

        public void makeAllForcedAlignments() {
            for (Set<AMR.Node> pinSet : pinnedAlignments) {
                Set<Integer> pinnedAlignments = unionAlignmentsForPinset(pinSet);
                if (pinnedAlignments.size() == 1) {
                    int forcedAlignment = pinnedAlignments.iterator().next();
                    for (AMR.Node node : pinSet) {
                        assert(node.alignment == 0);
                        node.alignment = forcedAlignment;
                    }
                }
            }
        }
    }

    public static void align(AMR[] bank) {
        List<PotentialAlignments> potentialAlignments = new ArrayList<PotentialAlignments>();

        for (AMR amr : bank) {
            potentialAlignments.add(align(amr));
        }

        EasyFirstEM.onePassAlign(potentialAlignments);
    }

    public static PotentialAlignments align(AMR amr) {
        PotentialAlignments alignments = new PotentialAlignments(amr);

        alignNER(alignments); // 29.4
        alignTextMatches(alignments); // 70.6
        alignOrgRole(alignments); // 70.8
        alignSuperlatives(alignments); // 29.7
        alignNominalHeads(alignments); // 72.2
        alignCoreferentNodes(alignments); // 73.7
        alignNegativePrefixNodes(alignments); // 73.8
        alignCommonDictionaryTerms(alignments); // 75.8
        alignDates(alignments); // 76.8

        alignments.makeAllForcedAlignments();

        return alignments;
    }

    public static void alignNER(PotentialAlignments alignments) {
        for (AMR.Node node : alignments.amr.nodes) {
            if (node.title.equals("name")) {

                // This is what it takes to express things functionally in Java. Blarg.
                @SuppressWarnings("unchecked")
                List<String> nameSegments = (List)map(filter(alignments.amr.depthFirstSearchNode(node), new BooleanFunc() {
                    @Override
                    public boolean func(Object o) {
                        return ((AMR.Node) o).type == AMR.NodeType.QUOTE;
                    }
                }), new MapFunc() {
                    @Override
                    public Object func(Object o) {
                        return ((AMR.Node)o).title;
                    }
                });
                String[] name = nameSegments.toArray(new String[nameSegments.size()]);

                // Try to find a window of exact matches

                Set<Integer> exactMatchWindows = findExactMatchWindow(name, alignments.amr.sourceText);

                // If we succeeded in doing that, then excellent

                if (exactMatchWindows.size() > 0) {
                    // Align all the children of the name node
                    int quoteIndex = 0;
                    for (AMR.Node n : alignments.amr.depthFirstSearchNode(node)) {
                        if (n.type == AMR.NodeType.QUOTE) {
                            final int quoteIndexThisLoop = quoteIndex;
                            alignments.unionAlignments(n, map(exactMatchWindows, new MapFunc() {
                                @Override
                                public Object func(Object o) {
                                    return ((Integer) o) + quoteIndexThisLoop;
                                }
                            }));
                            if (quoteIndex == 0) {
                                alignments.pinAlignments(node, n);
                            }
                            quoteIndex++;
                        } else {
                            alignments.unionAlignments(n, exactMatchWindows);
                            alignments.pinAlignments(node, n);
                        }
                    }
                    // Align the immediate parent of the name node
                    alignments.unionAlignments(alignments.amr.getParentArc(node).head, exactMatchWindows);
                    alignments.pinAlignments(alignments.amr.getParentArc(node).head, node);
                }

                // If we didn't, we ought to check for known confounders

                else {
                    Set<Integer> confounderAlignments = new HashSet<Integer>();
                    for (int i = 0; i < alignments.amr.sourceText.length; i++) {
                        if (AMRConstants.confounderToName.containsKey(alignments.amr.sourceText[i].toLowerCase())) {
                            String[] confounderName = AMRConstants.confounderToName.get(alignments.amr.sourceText[i].toLowerCase());
                            if (Arrays.deepEquals(confounderName, name)) {
                                confounderAlignments.add(i);
                            }
                        }
                    }
                    if (confounderAlignments.size() > 0) {
                        for (AMR.Node n : alignments.amr.depthFirstSearchNode(node)) {
                            alignments.unionAlignments(n, confounderAlignments);
                            alignments.pinAlignments(node, n);
                        }
                        alignments.unionAlignments(alignments.amr.getParentArc(node).head, confounderAlignments);
                        alignments.pinAlignments(alignments.amr.getParentArc(node).head, node);
                    }
                }
            }
        }
    }

    public static Set<Integer> findExactMatchWindow(String[] name, String[] tokens) {
        Set<Integer> matches = new HashSet<Integer>();
        outer: for (int i = 0; i < tokens.length - name.length; i++) {
            for (int j = 0; j < name.length; j++) {
                if (!stringMatchIgnoreSuffix(name[j], (tokens[i + j]))) {
                    continue outer;
                }
            }
            matches.add(i);
        }
        return matches;
    }

    private static boolean stringMatchIgnoreSuffix(String a, String b) {
        // If neither possesses a suffix that we care about, then just compare for equality
        if (a.equalsIgnoreCase(AMRConstants.trimSuffix(a.toLowerCase())) && b.equalsIgnoreCase(AMRConstants.trimSuffix(b.toLowerCase()))) {
            return a.equalsIgnoreCase(b);
        }
        // else match on the first 4 characters, or max number of characters in the string
        return firstKMatch(a, b, 4);
    }

    private static boolean firstKMatch(String a, String b, int k) {
        k = Math.min(Math.min(a.length(),b.length()), k);
        return a.substring(0,k).equalsIgnoreCase(b.substring(0, k));
    }

    public static void alignSuperlatives(PotentialAlignments alignments) {
        for (AMR.Arc arc : alignments.amr.arcs) {
            if (arc.title.equals("degree")) {
                String concat = arc.head.title;
                if (arc.tail.title.equals("most")) {
                    concat = concat + "est";
                }
                else if (arc.tail.title.equals("more")) {
                    concat = concat + "er";
                }
                Set<Integer> possiblities = new HashSet<Integer>();
                for (int i = 0; i < alignments.amr.sourceText.length; i++) {
                    if (alignments.amr.sourceText[i].equalsIgnoreCase(concat)) {
                        possiblities.add(i);
                    }
                }
                alignments.unionAlignments(arc.head, possiblities);
                alignments.unionAlignments(arc.tail, possiblities);
            }
        }
    }

    public static void alignTextMatches(PotentialAlignments alignments) {
        for (AMR.Node node : alignments.amr.nodes) {

            if (node.type == AMR.NodeType.QUOTE) continue;

            List<String> ignoreExactMatches = Arrays.asList(
                    "in",
                    "of",
                    "at"
            );

            Set<Integer> exactMatches = new HashSet<Integer>();
            for (int i = 0; i < alignments.amr.sourceText.length; i++) {
                // Don't match to prepositions by exact match, as a precaution
                if (ignoreExactMatches.contains(alignments.amr.sourceText[i].toLowerCase())) continue;
                // But if we're pretty sure it isn't a preposition, go ahead and match
                if (node.title.equalsIgnoreCase(alignments.amr.sourceText[i])) exactMatches.add(i);
            }
            // If we didn't find anything by exact string match, try a looser string match
            if (exactMatches.size() == 0) {
                for (int i = 0; i < alignments.amr.sourceText.length; i++) {
                    // Don't match to prepositions by exact match, as a precaution
                    if (ignoreExactMatches.contains(alignments.amr.sourceText[i].toLowerCase())) continue;
                    // But if we're pretty sure it isn't a preposition, go ahead and match
                    if (stringMatchIgnoreSuffix(node.title, alignments.amr.sourceText[i])) exactMatches.add(i);
                }
            }

            if (alignments.noInformationAbout(node))
                alignments.unionAlignments(node, exactMatches);
        }
    }

    public static void alignOrgRole(PotentialAlignments alignments) {
        for (AMR.Node node : alignments.amr.nodes) {
            if (node.title.equalsIgnoreCase("have-org-role-91")) {
                if (alignments.amr.outgoingArcs.containsKey(node)) {
                    for (AMR.Arc arc : alignments.amr.outgoingArcs.get(node)) {
                        if (arc.title.equalsIgnoreCase("ARG2")) {
                            alignments.pinAlignments(node, arc.tail);
                        }
                    }
                }
                if (alignments.amr.getParentArc(node).head.title.equals("person")) {
                    alignments.pinAlignments(node, alignments.amr.getParentArc(node).head);
                }
            }
        }
    }

    public static void alignNominalHeads(PotentialAlignments alignments) {
        for (AMR.Arc arc : alignments.amr.arcs) {
            if (Arrays.asList(
                    "ARG0-of",
                    "ARG1-of",
                    "ARG2-of",
                    "ARG3-of"
                ).contains(arc.title) && AMRConstants.nerTaxonomy.contains(arc.head.title)) {
                alignments.pinAlignments(arc.head, arc.tail);
            }
        }
    }

    public static void alignCoreferentNodes(PotentialAlignments alignments) {

        // Get the alignments for all the nodes with names

        Map<String,AMR.Node> firstNodeWithRef = new HashMap<String, AMR.Node>();
        for (AMR.Node node : alignments.amr.breadthFirstSearch()) {
            if (node.type == AMR.NodeType.ENTITY) {
                if (!firstNodeWithRef.containsKey(node.ref)) firstNodeWithRef.put(node.ref, node);
                else if (alignments.amr.outgoingArcs.containsKey(node)) {
                    firstNodeWithRef.put(node.ref, node);
                }
            }
        }
        for (AMR.Node node : alignments.amr.breadthFirstSearch()) {
            if (node.type == AMR.NodeType.ENTITY) {
                if (firstNodeWithRef.containsKey(node.ref)) {
                    if (firstNodeWithRef.get(node.ref) == node) continue;
                    Set<Integer> acceptableAlignments = new HashSet<Integer>();
                    acceptableAlignments.addAll(alignments.unionAlignmentsForPinset(alignments.getPinSet(firstNodeWithRef.get(node.ref))));
                    for (int i = 0; i < alignments.amr.sourceText.length; i++) {
                        if (AMRConstants.pronouns.contains(alignments.amr.sourceText[i].toLowerCase())) {
                            acceptableAlignments.add(i);
                        }
                    }
                    alignments.setAlignments(node, acceptableAlignments);
                    alignments.suggestAlignment(node, firstNodeWithRef.get(node.ref));
                }
            }
        }
    }

    public static void alignNegativePrefixNodes(PotentialAlignments alignments) {
        for (AMR.Arc arc : alignments.amr.arcs) {
            if (arc.title.equalsIgnoreCase("polarity")) {
                if (arc.head.alignment == 0) {
                    for (String prefix : Arrays.asList(
                            "anti-",
                            "anti",
                            "un"
                    )) {
                        String prefixed = prefix + arc.head.title;
                        Set<Integer> possibilities = new HashSet<Integer>();
                        for (int i = 0; i < alignments.amr.sourceText.length; i++) {
                            if (stringMatchIgnoreSuffix(alignments.amr.sourceText[i], prefixed)) {
                                possibilities.add(i);
                            }
                        }
                        alignments.unionAlignments(arc.head, possibilities);
                        alignments.unionAlignments(arc.tail, possibilities);
                    }
                }
            }
        }
    }

    public static void alignCommonDictionaryTerms(PotentialAlignments alignments) {
        for (AMR.Node node : alignments.amr.nodes) {
            if (AMRConstants.commonAMRisms.containsKey(node.title)) {
                Set<String> commonAMRisms = AMRConstants.commonAMRisms.get(node.title);
                if (commonAMRisms.size() > 0) {
                    Set<Integer> possiblities = new HashSet<Integer>();
                    for (int i = 0; i < alignments.amr.sourceText.length; i++) {
                        for (String commonAMRism : commonAMRisms) {
                            if (alignments.amr.sourceText[i].equalsIgnoreCase(commonAMRism)) possiblities.add(i);
                        }
                    }
                    if (possiblities.size() > 0)
                        alignments.setAlignments(node, possiblities);
                }
            }
        }
    }

    public static void alignDates(PotentialAlignments alignments) {
        for (AMR.Node node : alignments.amr.nodes) {
            if (node.title.equals("date-entity")) {
                Set<Integer> blobAlignments = new HashSet<Integer>();
                for (int i = 0; i < alignments.amr.sourceText.length; i++) {
                    boolean isInt = false;
                    try {
                        Integer.parseInt(alignments.amr.sourceText[i]);
                        isInt = true;
                    }
                    catch (Exception ignored) {}

                    if (alignments.amr.sourceText[i].length() == 6 && isInt) {
                        blobAlignments.add(i);
                    }

                    alignDate(alignments, node, i);
                }

                for (AMR.Node n : alignments.amr.depthFirstSearchNode(node)) {
                    alignments.unionAlignments(n, blobAlignments);
                }
            }
        }
    }

    private static void alignDate(PotentialAlignments alignments, AMR.Node node, int index) {
        if (alignments.amr.outgoingArcs.containsKey(node)) {
            for (AMR.Arc arc : alignments.amr.outgoingArcs.get(node)) {
                // Align the months
                if (arc.title.equals("month")) {
                    Set<Integer> possibleAlignments = new HashSet<Integer>();
                    for (int i = 0; i < alignments.amr.sourceText.length; i++) {
                        if (AMRConstants.monthToOffset.containsKey(alignments.amr.sourceText[i].toLowerCase())) {
                            if (arc.tail.title.equals(""+ AMRConstants.monthToOffset.get(alignments.amr.sourceText[i].toLowerCase()))) {
                                possibleAlignments.add(i);
                            }
                        }
                    }
                    alignments.unionAlignments(arc.tail, possibleAlignments);
                    alignments.pinAlignments(arc.head, arc.tail);
                }
            }
        }
    }
}
