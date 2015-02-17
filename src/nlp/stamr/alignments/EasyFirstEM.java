package nlp.stamr.alignments;

import nlp.stamr.AMR;
import nlp.stamr.alignments.conditional.binary.DistancePunishment;
import nlp.stamr.alignments.conditional.types.BinaryAlignmentFeature;
import nlp.stamr.alignments.conditional.types.UnaryAlignmentFeature;
import nlp.stamr.alignments.conditional.unary.EditDistanceUnaryFeature;
import nlp.stamr.alignments.conditional.unary.LexicalUnaryFeature;
import edu.stanford.nlp.util.IdentityHashSet;
import edu.stanford.nlp.util.Pair;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Runs EM over the output of an EasyFirstAligner, using lexical and offset probabilities for each node, as
 * collected over the whole corpus.
 */
public class EasyFirstEM {

    static void onePassAlign(List<EasyFirstAligner.PotentialAlignments> potentialAlignments) {

        List<BinaryAlignmentFeature> binaryAlignmentFeatures = Arrays.asList(
                //(BinaryAlignmentFeature)new ParentOffsetBinaryFeature()
                (BinaryAlignmentFeature)new DistancePunishment()
                //,new DependencyPathFeature()
        );

        List<UnaryAlignmentFeature> unaryAlignmentFeatures = Arrays.asList(
                (UnaryAlignmentFeature)new LexicalUnaryFeature()
                ,new EditDistanceUnaryFeature()
                //,new POSUnaryFeature()
        );

        // First go through and observe all the existing alignments

        for (EasyFirstAligner.PotentialAlignments alignments : potentialAlignments) {

            // Observe unary combinations

            for (AMR.Node node : alignments.amr.nodes) {
                Set<Integer> possibleAlignments = alignments.getAlignments(node);
                for (UnaryAlignmentFeature feature : unaryAlignmentFeatures) {
                    for (int i : possibleAlignments) {
                        feature.observe(alignments.amr, node, i, 1.0 / ((double)possibleAlignments.size()));
                    }
                }
            }

            // Observe binary combinations

            for (AMR.Arc arc : alignments.amr.arcs) {
                Set<Integer> headAlignments = alignments.getAlignments(arc.head);
                Set<Integer> tailAlignments = alignments.getAlignments(arc.tail);

                for (BinaryAlignmentFeature feature : binaryAlignmentFeatures) {
                    for (int i : headAlignments) {
                        for (int j : tailAlignments) {
                            feature.observe(alignments.amr, arc.tail, j, i, arc, 1.0 / ((double)headAlignments.size() * tailAlignments.size()));
                        }
                    }
                }
            }
        }

        // Then do another pass, and do a greedy assignment to alignments

        for (EasyFirstAligner.PotentialAlignments alignments : potentialAlignments) {
            Set<Set<AMR.Node>> donePinnedSets = new IdentityHashSet<Set<AMR.Node>>();

            for (AMR.Node n : alignments.amr.topologicalSort()) {
                Set<AMR.Node> pinnedSet = alignments.getPinSet(n);
                if (donePinnedSets.contains(pinnedSet)) continue;
                donePinnedSets.add(pinnedSet);

                Set<Integer> possibleAlignments = alignments.unionAlignmentsForPinset(pinnedSet);
                double maxScore = Double.NEGATIVE_INFINITY;
                int maxScoreIndex = -1;

                for (int i : possibleAlignments) {
                    double score = scoreSetAlignment(alignments, unaryAlignmentFeatures, binaryAlignmentFeatures, pinnedSet, i);
                    if (score > maxScore) {
                        maxScore = score;
                        maxScoreIndex = i;
                    }
                }

                assert(maxScoreIndex != -1);
                for (AMR.Node node : pinnedSet) {
                    node.alignment = maxScoreIndex;
                }
            }

            // Then put in all the suggestions, in case the score-based alignments failed

            for (Pair<AMR.Node,AMR.Node> pair : alignments.suggestedAlignments) {
                if (pair.first.alignment == 0 && pair.second.alignment != 0) {
                    pair.first.alignment = pair.second.alignment;
                }
                else if (pair.second.alignment == 0 && pair.first.alignment != 0) {
                    pair.second.alignment = pair.first.alignment;
                }
            }
        }
    }

    private static double scoreSetAlignment(EasyFirstAligner.PotentialAlignments alignments, List<UnaryAlignmentFeature> unaryAlignmentFeatures, List<BinaryAlignmentFeature> binaryAlignmentFeatures, Set<AMR.Node> set, int i) {
        double score = 1.0;

        for (AMR.Node node : set) {
            for (UnaryAlignmentFeature feature : unaryAlignmentFeatures) {
                score += feature.score(alignments.amr, node, i);
            }

            AMR.Arc parentArc = alignments.amr.getParentArc(node);
            if (parentArc != null) {
                for (BinaryAlignmentFeature feature : binaryAlignmentFeatures) {
                    score += feature.score(alignments.amr, node, i, parentArc.head.alignment, parentArc);
                }
            }
        }

        return score;
    }
}
