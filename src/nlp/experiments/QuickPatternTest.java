package nlp.experiments;

import java.util.regex.Pattern;

/**
 * Created by keenon on 2/11/15.
 */
public class QuickPatternTest {
    public static void main(String[] args) {
        match("asdflkj");
        match("CEO");
        match("89");
        match("32x");
        match("hello-01");
        match(":");
        match("/");
    }

    static Pattern p = Pattern.compile("[a-zA-Z0-9-]*");
    private static void match(String s) {
        System.out.println(s+" :: "+p.matcher(s).matches());
    }
}
