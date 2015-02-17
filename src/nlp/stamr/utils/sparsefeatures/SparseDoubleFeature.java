package nlp.stamr.utils.sparsefeatures;

import nlp.stamr.utils.SparseVector;

/**
 * Created by keenon on 11/24/14.
 */
public abstract class SparseDoubleFeature<T> extends SparseFeature<T> {

    protected abstract double classify(T t);

    @Override
    protected SparseVector featurize(T t, boolean observing) {
        SparseVector vector = new SparseVector();
        vector.add(0, classify(t));
        return vector;
    }
}
