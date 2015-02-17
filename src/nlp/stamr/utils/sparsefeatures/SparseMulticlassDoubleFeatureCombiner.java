package nlp.stamr.utils.sparsefeatures;

import nlp.stamr.utils.SparseVector;

/**
 * Takes a list of SparseMulticlassFeatures and writes them into a single outgoing vector
 */
public class SparseMulticlassDoubleFeatureCombiner<T> extends SparseFeature<T> {
    SparseMulticlassFeature<T> multiclassFeature;
    SparseDoubleFeature<T> doubleFeature;

    public SparseMulticlassDoubleFeatureCombiner(SparseMulticlassFeature<T> multiclassFeature, SparseDoubleFeature<T> doubleFeature) {
        this.multiclassFeature = multiclassFeature;
        this.doubleFeature = doubleFeature;
    }

    @Override
    protected SparseVector featurize(T t, boolean observing) {
        SparseVector vec = multiclassFeature.featurize(t, observing);
        vec.multiplyAll(doubleFeature.classify(t));
        return vec;
    }
}
