package nlp.stamr.utils;

/**
 * Created by keenon on 11/24/14.
 */
public class PrecisionCounter {
    int correct = 0;
    int incorrect = 0;

    public void observe(boolean match) {
        if (match) correct++;
        else incorrect++;
    }

    public String report() {
        return "Precision: "+((double)correct / (double)(correct + incorrect)) + "\n";
    }
}
