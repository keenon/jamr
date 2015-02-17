package nlp.stamr.utils.sparsefeatures;

import nlp.stamr.utils.SparseVector;

/**
 * Holds the guts of a multiclass featurizer
 */
public abstract class SparseMulticlassFeature<T> extends SparseFeature<T> {
    @Override
    public void observe(T t) {
        int cl = classify(t);
        if (cl > maxSize) maxSize = cl;
    }

    protected abstract int classify(T t);

    @Override
    protected SparseVector featurize(T t, boolean observing) {
        int cl = classify(t);

        if (observing) cl ++; // leave room at 0 for unknown quantities
        else if (cl > maxSize) cl = 0; // Always return 0 for unknown values

        SparseVector vec = new SparseVector();
        vec.add(cl, 1.0);
        return vec;
    }
}
