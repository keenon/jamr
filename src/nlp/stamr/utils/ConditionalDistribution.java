package nlp.stamr.utils;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds a conditional distribution P(A | B), with no ability to remove observed values short of clearing the whole thing
 */
public class ConditionalDistribution<A,B> {

    public Counter<B> bCounter = new ClassicCounter<B>();
    public Map<B,Counter<A>> aCounterGivenB = new HashMap<B, Counter<A>>();

    public double probAGivenB(A a, B b) {
        if (aCounterGivenB.containsKey(b)) {
            if (aCounterGivenB.get(b).getCount(a) != 0) {
                return aCounterGivenB.get(b).getCount(a) / bCounter.getCount(b);
            }
            else {
                return 0.001; // Good Turing estimate of unseen probabilities: n1 / n
            }
        }
        else {
            // If we condition on something we've never seen before, we don't have any information.
            // Just return probability of randomly selecting this A from all observed values
            return 0.001;
        }
    }

    public void addAll(ConditionalDistribution<A,B> otherDistribution) {
        bCounter.addAll(otherDistribution.bCounter);
        for (B b : otherDistribution.aCounterGivenB.keySet()) {
            if (aCounterGivenB.containsKey(b)) {
                aCounterGivenB.get(b).addAll(otherDistribution.aCounterGivenB.get(b));
            }
            else {
                aCounterGivenB.put(b,otherDistribution.aCounterGivenB.get(b));
            }
        }
    }

    public void observe(A a, B b, double probability) {
        bCounter.incrementCount(b, probability);
        if (!aCounterGivenB.containsKey(b)) aCounterGivenB.put(b, new ClassicCounter<A>());
        aCounterGivenB.get(b).incrementCount(a, probability);
    }

    public void clear() {
        bCounter.clear();
        for (Counter<A> aCounterB : aCounterGivenB.values()) {
            aCounterB.clear();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("---");
        for (B b : bCounter.keySet()) {
            sb.append("\n").append(b).append(": ");
            for (A a : aCounterGivenB.get(b).keySet()) {
                sb.append("\n\t(").append(a).append("=").append(aCounterGivenB.get(b).getCount(a)).append(")");
            }
        }
        sb.append("\n---");
        return sb.toString();
    }

}
