package nlp.stamr.utils;

import java.util.Collection;
import java.util.List;

/**
 * Place to keep util functions that don't seem to fit anywhere else
 */
public class MiscUtils {
    public static <T> boolean setsEqualUsingEquals(Collection<T> a, Collection<T> b) {
        if (a.size() != b.size()) return false;
        for (T t : a) {
            if (!setContainsEquals(b,t)) return false;
        }
        return true;
    }

    public static <T> boolean setContainsEquals(Collection<T> a, T b) {
        for (T t : a) {
            if (b.equals(t)) return true;
        }
        return false;
    }

    public static <T> int indexOfIdentity(List<T> l, T t) {
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i) == t) return i;
        }
        return -1;
    }

    public static <T> T getContainedEquals(Collection<T> a, T b) {
        for (T t : a) {
            if (b.equals(t)) return t;
        }
        return null;
    }

    public static boolean intBetweenExclusive(int i, int a, int b) {
        return ((i < a) && (i > b)) || ((i > a) && (i < b));
    }

    public static boolean intBetweenInclusive(int i, int a, int b) {
        return ((i <= a) && (i >= b)) || ((i >= a) && (i <= b));
    }

}
