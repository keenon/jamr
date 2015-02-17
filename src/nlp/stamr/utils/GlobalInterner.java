package nlp.stamr.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * An attempt at a threadsafe global interner, to reduce interning as a bottleneck later
 */
public class GlobalInterner {

    static AtomicInteger counter = new AtomicInteger();
    static ConcurrentHashMap<Class,ConcurrentHashMap<String,Integer>> interners = new ConcurrentHashMap<Class, ConcurrentHashMap<String, Integer>>();

    public static int intern(String s, Class type) {

        // Should be a relatively lock-light way to put new stuff into the interner

        interners.putIfAbsent(type, interners.computeIfAbsent(type, new Function<Class, ConcurrentHashMap<String, Integer>>() {
            @Override
            public ConcurrentHashMap<String, Integer> apply(Class aClass) {
                return new ConcurrentHashMap<String,Integer>();
            }
        }));

        interners.get(type).putIfAbsent(s, interners.get(type).computeIfAbsent(s, new Function<String, Integer>() {
            @Override
            public Integer apply(String s) {
                return counter.addAndGet(1);
            }
        }));

        // Base case

        return interners.get(type).get(s);
    }

    public static int numVariables(Class type) {
        if (interners.containsKey(type)) {
            return interners.get(type).size();
        }
        return 0;
    }

}
