package nlp.keenonutils;

import java.util.function.Supplier;

/**
 * Created by keenon on 1/27/15.
 *
 * blarg
 */
public class Lazy<T> {
    Supplier<T> supplier = null;
    T cache = null;

    public static <T> Lazy<T> of(Supplier<T> s){
        Lazy<T> lazy = new Lazy<T>();
        lazy.supplier = s;
        return lazy;
    }

    public T get() {
        if (cache == null) {
            cache = supplier.get();
        }
        return cache;
    }
}
