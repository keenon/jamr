package nlp.stamr.utils.sparsefeatures;

import java.util.List;

/**
 * Takes a list of SparseMulticlassFeatures and writes them into a single outgoing vector
 */
public class SparseMulticlassFeatureCombiner<T> extends SparseMulticlassFeature<T> {
    List<SparseMulticlassFeature<T>> features;

    public SparseMulticlassFeatureCombiner(List<SparseMulticlassFeature<T>> features) {
        this.features = features;
    }

    @Override
    protected int classify(T t) {
        int val = 0;
        int multiple = 1;
        for (SparseMulticlassFeature<T> feature : features) {
            val += feature.classify(t)*multiple;
            multiple *= feature.maxSize;
        }
        return val;
    }

    @Override
    public void observe(T t) {
        maxSize = 1;
        for (SparseMulticlassFeature<T> feature : features) {
            feature.observe(t);
            maxSize *= feature.maxSize;
        }
    }
}
