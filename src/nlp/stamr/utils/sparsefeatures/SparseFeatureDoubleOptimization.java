package nlp.stamr.utils.sparsefeatures;

import nlp.stamr.utils.SparseVector;

/**
 * Created by keenon on 11/26/14.
 */
public abstract class SparseFeatureDoubleOptimization<T> extends SparseFeature<T> {
    @Override
    protected SparseVector featurize(T t, boolean observing) {
        return new SparseVector(featurizeDouble(t, observing));
    }

    @Override
    public void observe(T t) {
        if (maxSize == 0) maxSize = getSize(t);
    }

    public abstract int getSize(T t);

    public abstract double[] featurizeDouble(T t, boolean observing);
}
