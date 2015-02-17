package nlp.stamr.utils;

/**
 * Flattens multiple ints (up to 16 bits of info) into a single unique value, using nasty bitwise-trickery
 */
public class MultiFlattener {

    public static long flatten(int... args) {
        assert(args.length <= 4);
        long batch = 0;
        for (int i = 0; i < args.length; i++) {
            assert(args[i] <= Short.MAX_VALUE);
            short s = (short)args[i];
            batch |= (((long)s & 0xFFFF) << (16*i)); // Trim to the smallest 16 bits of the input value with a bitmask
        }
        return batch;
    }

}
