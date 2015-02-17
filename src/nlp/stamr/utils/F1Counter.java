package nlp.stamr.utils;

/**
 * Observed binary classification problems and spits out F1 scores
 */
public class F1Counter {
    public int truePositive = 0;
    public int falsePositive = 0;
    public int falseNegative = 0;
    public int trueNegative = 0;

    public void observe(boolean predictedValue, boolean correctValue) {
        if ((predictedValue == correctValue) && predictedValue) {
            truePositive ++;
        }
        else if ((predictedValue == correctValue) && !predictedValue) {
            trueNegative ++;
        }
        else if ((predictedValue != correctValue) && !predictedValue) {
            falseNegative ++;
        }
        else {
            falsePositive ++;
        }
    }

    public String report() {
        double precision = (double)truePositive / ((double)truePositive + falsePositive);
        double recall = (double)truePositive / ((double)truePositive + falseNegative);
        double f1 = 2 * (precision * recall) / (precision + recall);
        return "Precision: " + precision + "\n" + "Recall: " + recall + "\n" + "F1: " + f1 + "\n";
    }
}
