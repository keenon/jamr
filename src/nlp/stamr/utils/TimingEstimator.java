package nlp.stamr.utils;

import edu.stanford.nlp.util.Timing;

/**
 * Prints out projected completion times for a repetitive loop
 */
public class TimingEstimator {

    Timing timing = new Timing();
    long lastReport = 0;

    public void start() {
        timing.start();
    }

    public long secondsSinceLastReport() {
        long time = timing.report();
        return time - lastReport;
    }

    public String reportEstimate(int i, int iterations) {
        long time = timing.report();
        long loop = time - lastReport;
        lastReport = time;
        long projectedTime = (time / (i+1))*(iterations-(i+1));

        StringBuilder sb = new StringBuilder();
        formatMSToSeconds(sb, loop);
        sb.append(" Loop, ");
        formatMSToSeconds(sb,time);
        sb.append(" Elapsed, ");
        formatMSToSeconds(sb,projectedTime);
        sb.append(" Projected");
        sb.append("\n");
        printProgressBar(sb, "00:00:00 Loop, 00:00:00 Elapsed, 00:00:00 Projected".length(), ((double)(time) / (double)(time+projectedTime)));

        return sb.toString();
    }

    public void formatMSToSeconds(StringBuilder sb, long time) {
        long seconds = time / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours < 10) {
            sb.append("0");
        }
        sb.append(hours).append(":");
        minutes = minutes % 60;
        if (minutes < 10) {
            sb.append("0");
        }
        sb.append(minutes).append(":");
        seconds = seconds % 60;
        if (seconds < 10) {
            sb.append("0");
        }
        sb.append(seconds);
    }

    public void printProgressBar(StringBuilder sb, int length, double percentage) {
        int completed = (int)Math.round(percentage * (double)length);
        int leftOver = length - completed;
        for (int i = 0; i < completed; i++) {
            sb.append("=");
        }
        for (int i = 0; i < leftOver; i++) {
            sb.append(".");
        }
    }
}
