package nlp.stamr.utils.sparsefeatures;

import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

/**
 * Holds a simple multiclass string featurizer that works based on an index
 */
public class SparseMulticlassStringFeature extends SparseMulticlassFeature<String> {
    Index<String> index = new HashIndex<String>();
    @Override
    protected int classify(String s) {
        return index.addToIndex(s);
    }
}
