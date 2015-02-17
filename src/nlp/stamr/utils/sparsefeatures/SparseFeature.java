package nlp.stamr.utils.sparsefeatures;

import nlp.stamr.utils.SparseVector;

import java.util.List;
import java.util.Map;

/**
 * Holds a feature that returns a sparse vector, and has instruments for concatenation based on the size of different
 * sparse features.
 */
public abstract class SparseFeature<T> {
    protected int maxSize = 0;

    public void observe(T t) {
        SparseVector features = featurize(t, true);
        int val = (int) features.getMaxNonZeroEntry();
        if (val > maxSize) maxSize = val;
    }

    protected abstract SparseVector featurize(T t, boolean observing);

    public static <T> SparseVector concatenate(List<SparseFeature<T>> features, T t) {
        int offset = 0;
        SparseVector result = new SparseVector();
        for (SparseFeature<T> feature : features) {
            if (SparseMulticlassFeature.class.isAssignableFrom(feature.getClass())) {
                SparseMulticlassFeature<T> multiclassFeature = (SparseMulticlassFeature<T>)feature;
                result.add(multiclassFeature.classify(t) + offset, 1.0);
            }
            else if (SparseFeatureDoubleOptimization.class.isAssignableFrom(feature.getClass())) {
                SparseFeatureDoubleOptimization<T> doubleOptimization = (SparseFeatureDoubleOptimization<T>)feature;
                double[] values = doubleOptimization.featurizeDouble(t, false);
                for (int i = 0; i < values.length; i++) {
                    if (values[i] != Double.NEGATIVE_INFINITY && values[i] != Double.POSITIVE_INFINITY && values[i] != Double.NaN)
                        result.add(i + offset, values[i]);
                }
            }
            else {
                SparseVector vec = feature.featurize(t, false);
                for (Map.Entry<Long, Double> entry : vec.counter.entrySet()) {
                    if (entry.getValue() != Double.NEGATIVE_INFINITY && entry.getValue() != Double.POSITIVE_INFINITY && entry.getValue() != Double.NaN)
                        result.add(entry.getKey() + offset, entry.getValue());
                }
            }
            offset += feature.maxSize;
        }
        return result;
    }

    public static <T> void observe(List<SparseFeature<T>> features, T t) {
        for (SparseFeature<T> feature : features) {
            feature.observe(t);
        }
    }
}
