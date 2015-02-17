package nlp.stamr.alignments;

import nlp.stamr.AMR;
import nlp.stamr.AMRConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Takes a non-projective AMR and swaps the tree until it become projective
 */
public class Projectifier {

    public static AMR[] projectify(AMR[] bank) {
        AMR[] projectified = new AMR[bank.length];
        for (int i = 0; i < bank.length; i++) projectified[i] = projectify(bank[i]);
        return projectified;
    }

    public static AMR projectify(AMR amr) {
        AMR clone = new AMR();
        clone.subsumeContentsOf(amr);
        clone.sourceText = amr.sourceText;
        clone.annotationWrapper = amr.annotationWrapper;
        demoteModal(clone);
        demoteConjunction(clone);
        pronounify(clone);
        swapToMinimizeNonProjectivity(clone);
        return clone;
    }

    public static AMR[] deprojectify(AMR[] bank) {
        AMR[] deprojectified = new AMR[bank.length];
        for (int i = 0; i < bank.length; i++) deprojectified[i] = deprojectify(bank[i]);
        return deprojectified;
    }

    public static AMR deprojectify(AMR amr) {
        AMR clone = new AMR();
        clone.subsumeContentsOf(amr);
        clone.sourceText = amr.sourceText;
        clone.annotationWrapper = amr.annotationWrapper;
        promoteConjunction(clone);
        promoteModal(clone);
        return clone;
    }

    // Sometimes the aligner will make mistakes aligning ambiguous stuff,
    // like multiple "and"s, and this switch should help with that. Avoidable
    // non-projectivity is a good clue that the alignment was wrong.

    public static boolean swapToMinimizeNonProjectivity(AMR amr) {
        boolean swapped = false;
        for (AMR.Node node1 : amr.nodes) {
            for (AMR.Node node2 : amr.nodes) {
                if (node1 == node2) continue;
                if (!node1.title.equals(node2.title)) continue;
                int numNonProjective = amr.getNonProjectiveArcs().size();

                int buf = node1.alignment;
                node1.alignment = node2.alignment;
                node2.alignment = buf;

                int swappedNumNonProjective = amr.getNonProjectiveArcs().size();

                // If the swap reduced the number of non-projective arcs, then keep the swap
                // permanently. Otherwise, unswitch.

                if (swappedNumNonProjective >= numNonProjective) {
                    node2.alignment = node1.alignment;
                    node1.alignment = buf;
                }
                else {
                    node1.alignmentFixed = true;
                    node2.alignmentFixed = true;
                    swapped = true;
                }
            }
        }
        return swapped;
    }

    public static void arbitraryPseudoProjectify(AMR amr) {
        boolean transformed = false;
        while (amr.getNonProjectiveArcs().size() > 0) {
            int minimumSize = Integer.MAX_VALUE;
            AMR.Arc shortestNonProjectiveArc = null;

            for (AMR.Arc arc : amr.getNonProjectiveArcs()) {
                if (Math.abs(arc.tail.alignment - arc.head.alignment) < minimumSize) {
                    if (amr.incomingArcs.containsKey(arc.head)) {
                        if (amr.incomingArcs.get(arc.head).size() > 0) {
                            minimumSize = Math.abs(arc.tail.alignment - arc.head.alignment);
                            shortestNonProjectiveArc = arc;
                        }
                        else {
                            amr.incomingArcs.remove(arc.head);
                        }
                    }
                }
            }

            // This happens sometimes with situations that are hopeless to untangle. Just bail.
            if (shortestNonProjectiveArc == null) {
                return;
            }

            AMR.Arc parentArc = amr.incomingArcs.get(shortestNonProjectiveArc.head).get(0);
            shortestNonProjectiveArc.head = parentArc.head;
            shortestNonProjectiveArc.title += ";"+parentArc.title;
            amr.outgoingArcs.get(parentArc.tail).remove(shortestNonProjectiveArc);
            if (amr.outgoingArcs.get(parentArc.tail).size() == 0) {
                amr.outgoingArcs.remove(parentArc.tail);
            }
            if (!amr.outgoingArcs.containsKey(parentArc.head)) amr.outgoingArcs.put(parentArc.head, new ArrayList<AMR.Arc>());
            amr.outgoingArcs.get(parentArc.head).add(shortestNonProjectiveArc);
            transformed = true;
        }
        if (transformed) {
            /*
            System.out.println("Pseudo-projectivized:");
            System.out.println(amr.toString(AMR.AlignmentPrinting.ALL));
            */
        }
    }

    public static void arbitraryPseudoDeprojectify(AMR amr) {
        for (AMR.Arc arc : amr.arcs) {
            while (arc.title.contains(";")) {
                String title = arc.title;
                int splitIndex = title.lastIndexOf(";");
                arc.title = title.substring(0,splitIndex);
                String arcName = title.substring(splitIndex+1);
                AMR.Arc matchingOutgoing = null;

                for (AMR.Arc outgoing : amr.outgoingArcs.get(arc.head)) {
                    if (outgoing.title.equals(arcName) && (outgoing != arc) && (outgoing.tail != arc.tail)) {
                        matchingOutgoing = outgoing;
                        break;
                    }
                }

                if (matchingOutgoing != null) {
                    arc.head = matchingOutgoing.tail;
                    amr.outgoingArcs.get(matchingOutgoing.head).remove(arc);
                    if (amr.outgoingArcs.get(matchingOutgoing.head).size() == 0) amr.outgoingArcs.remove(matchingOutgoing.head);
                    if (!amr.outgoingArcs.containsKey(matchingOutgoing.tail)) amr.outgoingArcs.put(matchingOutgoing.tail,new ArrayList<AMR.Arc>());
                    amr.outgoingArcs.get(matchingOutgoing.tail).add(arc);
                }
            }
        }
    }

    public static void pronounify(AMR amr) {
        for (AMR.Node node : amr.nodes) {
            if (AMRConstants.pronouns.contains(amr.getSourceToken(node.alignment).toLowerCase())) {
                // Dump all pronouns into actual "pronoun nodes", to be sorted out later
                node.title = amr.getSourceToken(node.alignment).toLowerCase();
            }
        }
    }

    public static void promoteConjunction(AMR amr) {
        boolean foundConjunctionToPromote = false;
        for (AMR.Node cc : amr.nodes) {
            if (AMRConstants.conjunctions.contains(cc.title)) {
                if (amr.incomingArcs.containsKey(cc) && (amr.incomingArcs.get(cc).size() > 0)) {
                    AMR.Arc ccArc = amr.incomingArcs.get(cc).get(0);
                    if (!ccArc.title.equals("cc")) continue;

                    AMR.Node op1 = ccArc.head;

                    amr.moveIncomingArcs(op1, cc);

                    amr.removeArc(ccArc);

                    if (amr.outgoingArcs.containsKey(op1)) {

                        List<AMR.Arc> ccArcs = new ArrayList<AMR.Arc>();
                        ccArcs.addAll(amr.outgoingArcs.get(op1));

                        ccArcs.sort(new Comparator<AMR.Arc>() {
                            @Override
                            public int compare(AMR.Arc o1, AMR.Arc o2) {
                                if (o1.tail.alignment < o2.tail.alignment) {
                                    return -1;
                                }
                                else if (o1.tail.alignment > o2.tail.alignment) {
                                    return 1;
                                }
                                else return 0;
                            }
                        });

                        int opCounter = 1;

                        for (AMR.Arc arc : ccArcs) {
                            if (arc.title.equals("conj")) {
                                amr.removeArc(arc);
                                opCounter++;
                                amr.addArc(cc, arc.tail, "op"+opCounter);
                            }
                        }

                        if (opCounter > 1) {
                            amr.addArc(cc, op1, "op1");
                        }
                        else {
                            amr.addArc(cc, op1, "op2"); // only use "op2" in cases where nothing else is present
                        }
                    }
                    else {
                        amr.addArc(cc, op1, "op2");
                    }

                    foundConjunctionToPromote = true;
                }
            }
        }
    }

    public static void promoteModal(AMR amr) {
        boolean foundModalToPromote = false;
        for (AMR.Node modal : amr.nodes) {
            if (AMRConstants.modals.contains(modal.title)) {
                if (amr.incomingArcs.containsKey(modal)) {
                    AMR.Arc arc = amr.incomingArcs.get(modal).get(0);
                    if (arc.title.equals("modal")) {
                        AMR.Node modalized = arc.head;

                        amr.moveIncomingArcs(modalized, modal);
                        amr.removeArc(arc);

                        amr.addArc(modal, modalized, "domain");
                        foundModalToPromote = true;
                    }
                }
            }
        }
        /*if (foundModalToPromote) {
            System.out.println("Promoted modal:");
            System.out.println(amr.toString(AMR.AlignmentPrinting.ALL));
        }*/
    }

    public static void demoteConjunction(AMR amr) {
        boolean foundCCToDemote = false;
        for (AMR.Node cc : amr.nodes) {
            if (AMRConstants.conjunctions.contains(cc.title)) {
                if (amr.outgoingArcs.containsKey(cc)) {
                    List<AMR.Arc> ccArcs = new ArrayList<AMR.Arc>();
                    ccArcs.addAll(amr.outgoingArcs.get(cc));

                    ccArcs.sort(new Comparator<AMR.Arc>() {
                        @Override
                        public int compare(AMR.Arc o1, AMR.Arc o2) {
                            if (o1.tail.alignment < o2.tail.alignment) {
                                return -1;
                            }
                            else if (o1.tail.alignment > o2.tail.alignment) {
                                return 1;
                            }
                            else return 0;
                        }
                    });

                    AMR.Node firstOp = ccArcs.get(0).tail;

                    // Replace the cc with the first arc's tail

                    amr.moveIncomingArcs(cc, firstOp);

                    // Make an arc from the first op to the "and" node :cc

                    amr.addArc(firstOp, cc, "cc");

                    // Create all the remaining arcs between the children as :conj strings, all coming
                    // from the new head op

                    for (int i = 1; i < ccArcs.size(); i++) {
                        amr.addArc(firstOp, ccArcs.get(i).tail, "conj");
                    }

                    // Clear out all the cc arcs from data structures

                    amr.removeArcs(ccArcs);

                    foundCCToDemote = true;
                }
            }
        }
        if (foundCCToDemote) {
            // System.out.println("Demoted CC:");
            // System.out.println(amr.toString(AMR.AlignmentPrinting.ALL));
        }
    }

    public static void demoteModal(AMR amr) {

        boolean foundModalToDemote = false;

        for (AMR.Node modal : amr.nodes) {
            if (AMRConstants.modals.contains(modal.title)) {
                if (amr.outgoingArcs.containsKey(modal)) {
                    AMR.Arc domainArc = null;
                    for (AMR.Arc arc : amr.outgoingArcs.get(modal)) {
                        if (arc.title.equals("domain")) {
                            domainArc = arc;
                        }
                    }
                    if (domainArc != null) {
                        AMR.Node modalized = domainArc.tail;

                        amr.moveIncomingArcs(modal, modalized);
                        amr.removeArc(domainArc);

                        amr.addArc(modalized, modal, "modal");

                        foundModalToDemote = true;
                    }
                }
            }
        }
    }

    public static void printNonProjective(AMR[] bank) {
        for (AMR amr : bank) {
            if (isNonProjective(amr)) {
                System.out.println("NonProjective:");
                for (AMR.Arc arc : amr.getNonProjectiveArcs()) {
                    System.out.println("\t"+arc.toString());
                }
                System.out.println(amr.toString(AMR.AlignmentPrinting.ALL));
            }
        }
    }

    public static void countNonProjective(AMR[] bank) {
        int nonProjective = 0;
        for (AMR amr : bank) {
            if (isNonProjective(amr)) {
                nonProjective++;
            }
        }
        System.out.println("Number of non-projective sentences: "+nonProjective);
    }

    public static boolean isNonProjective(AMR amr) {
        return amr.getNonProjectiveArcs().size() > 0;
    }
}
