package nlp.keenonutils;

import java.util.Collection;

/**
 * Created by keenon on 11/11/14.
 */
public class Functional {

    public abstract static class BooleanFunc {
        public abstract boolean func(Object o);
    }

    public static <T extends Collection> T filter(T l, BooleanFunc bf) {
        T newColl = null;
        try {
            newColl = (T)l.getClass().newInstance();
            for (Object o : l) {
                if (bf.func(o)) newColl.add(o);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return newColl;
    }

    public abstract static class MapFunc {
        public abstract Object func(Object o);
    }

    public static <T extends Collection> T map(T l, MapFunc mf) {
        T newColl = null;
        try {
            newColl = (T)l.getClass().newInstance();
            for (Object o : l) {
                newColl.add(mf.func(o));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return newColl;
    }
}
