package nlp.stamr.utils;

import edu.stanford.nlp.util.Pair;

import java.util.*;

/**
 * Created by keenon on 11/25/14.
 */
public class ErrorReporting {

    Map<String,Map<String,Integer>> map = new HashMap<String, Map<String, Integer>>();

    public void observe(String guess, String correct) {
        if (guess.equals(correct)) return;

        if (!map.containsKey(correct)) map.put(correct, new HashMap<String, Integer>());
        map.get(correct).put(guess, map.get(correct).getOrDefault(guess, 0) + 1);
    }

    public String report(int topK) {
        StringBuilder sb = new StringBuilder();
        sb.append("Top ").append(topK).append(" misses:\n");

        List<Pair<String,Integer>> misses = new ArrayList<Pair<String, Integer>>();
        for (String correct : map.keySet()) {
            int total = 0;
            for (String guess : map.get(correct).keySet()) {
                total += map.get(correct).get(guess);
            }
            misses.add(new Pair<String, Integer>(correct, total));
        }

        misses.sort(new Comparator<Pair<String, Integer>>() {
            @Override
            public int compare(Pair<String, Integer> o1, Pair<String, Integer> o2) {
                return o2.second.compareTo(o1.second);
            }
        });

        int missCounter = 0;
        for (Pair<String,Integer> miss : misses) {
            sb.append(miss.first).append(" [").append(miss.second).append("]:\n");

            List<Pair<String,Integer>> guesses = new ArrayList<Pair<String, Integer>>();
            for (String guess : map.get(miss.first).keySet()) {
                guesses.add(new Pair<String, Integer>(guess, map.get(miss.first).get(guess)));
            }
            guesses.sort(new Comparator<Pair<String, Integer>>() {
                @Override
                public int compare(Pair<String, Integer> o1, Pair<String, Integer> o2) {
                    return o2.second.compareTo(o1.second);
                }
            });

            int guessCounter = 0;
            for (Pair<String,Integer> guess : guesses) {
                sb.append("\t").append(guess.first).append(" [").append(guess.second).append("]\n");
                guessCounter++;
                if (guessCounter > topK) break;
            }

            missCounter++;
            if (missCounter > topK) break;
        }

        return sb.toString();
    }
}
