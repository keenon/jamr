package nlp.stamr.utils;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

import java.io.Serializable;
import java.util.Map;

/**
 * Represents a super-sparse vector, addressed by integer locations within itself. Really just
 * a thin wrapper over a counter.
 */
public class SparseVector implements Serializable {
    int size;
    public Counter<Long> counter;
    Counter<Long> attemptedUpdates;
    int sparseThreshold = 8;

    public SparseVector() {
        this(0);
    }

    public SparseVector(int size) {
        this.size = size;
        counter = new ClassicCounter<Long>();
        attemptedUpdates = new ClassicCounter<Long>();
    }

    public SparseVector(double[] denseVector) {
        this.size = denseVector.length;
        counter = new ClassicCounter<Long>();
        attemptedUpdates = new ClassicCounter<Long>();
        for (int i = 0; i < denseVector.length; i++) {
            counter.incrementCount((long)i, denseVector[i]);
        }
    }

    public double get(long i) {
        return counter.getCount(i);
    }

    public void add(long i, double j) {
        counter.incrementCount(i, j);
    }

    public int getSize() {
        return size;
    }

    public SparseVector addAll(SparseVector vector, double multiple) {
        for (long i : vector.counter.keySet()) {
            add(i, vector.get(i)*multiple);
        }
        return this;
    }

    public SparseVector addWithAdagradVector(SparseVector gradient, SparseVector adagrad, double alpha) {
        for (long i : gradient.counter.keySet()) {
            adagrad.add(i, gradient.get(i) * gradient.get(i));
            double adagradVal = Math.sqrt(adagrad.get(i));
            if (adagradVal == 0) adagradVal = 1;
            add(i, gradient.get(i) * (alpha / adagradVal));
        }
        return this;
    }

    public void addAllSparse(SparseVector vector, double multiple) {
        for (long i : vector.counter.keySet()) {
            if (attemptedUpdates.incrementCount(i, 1.0) > sparseThreshold)
                add(i, vector.get(i)*multiple);
        }
    }

    public long getMaxNonZeroEntry() {
        long max = 0;
        for (long i : counter.keySet()) {
            if (i > max) max = i;
        }
        return max;
    }

    public double sum() {
        return counter.totalCount();
    }

    public double dotProduct(SparseVector vector) {
        double sum = 0.0;

        // Iterate only over the smaller of the vectors being dotted

        if (vector.counter.size() < counter.size()) {
            for (Map.Entry<Long, Double> entry : vector.counter.entrySet()) {
                sum += entry.getValue() * counter.getCount(entry.getKey());
            }
        }
        else {
            for (Map.Entry<Long, Double> entry : counter.entrySet()) {
                sum += entry.getValue() * vector.counter.getCount(entry.getKey());
            }
        }

        return sum;
    }

    public SparseVector addAll(SparseVector vector) {
        counter.addAll(vector.counter);
        return this;
    }

    public void swapFrom(SparseVector vector) {
        assert(vector.counter != null);
        counter = vector.counter;
        vector.counter = new ClassicCounter<Long>();
    }

    public void copyFrom(SparseVector vector) {
        clear();
        counter.addAll(vector.counter);
    }

    public void clear() {
        for (Long i : counter.keySet()) {
            counter.setCount(i, 0);
        }
    }

    public void multiplyAll(double multiple) {
        for (long i : counter.keySet()) {
            counter.setCount(i, counter.getCount(i) * multiple);
        }
    }

    @Override
    public String toString() {
        return counter.toString();
    }
}
