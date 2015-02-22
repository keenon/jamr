package nlp.stamr.alignments;

import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.IdentityHashSet;
import edu.stanford.nlp.util.Pair;
import nlp.experiments.AMRPipeline;
import nlp.stamr.AMR;
import nlp.stamr.AMRConstants;
import nlp.stamr.datagen.DumpSequence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public static void align(AMR amr) {
        String[] tokens = amr.sourceText;
        Annotation annotation = amr.multiSentenceAnnotationWrapper.sentences.get(0).annotation;

        // Get all the tokens that aren't up for discussion, b/c we grabbed them using NER or SUTime
        Pair<List<AMR>,Set<Integer>> pair = AMRPipeline.getDeterministicChunks(tokens, annotation);
        List<AMR> gen = pair.first;
        Set<Integer> blockedTokens = pair.second;
        Set<AMR.Node> blockedNodes = new IdentityHashSet<>();

        for (AMR chunk : gen) {
            Set<AMR.Node> match = amr.getMatchingSubtreeOrEmpty(chunk);
            blockedNodes.addAll(match);
        }

        // Look through all non-blocked nodes and non-blocked tokens, and find the match that minimizes the number of DICT
        // elements, and minimizes the edit distance between DICT elements and their corresponding tokens.


    }

}
