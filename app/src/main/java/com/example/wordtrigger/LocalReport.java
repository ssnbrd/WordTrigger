package com.example.wordtrigger;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LocalReport {

    public static final class Day {
        public final String date;
        public final int count;
        public final List<String> contexts;

        public Day(String date, int count, List<String> contexts) {
            this.date = date;
            this.count = count;
            this.contexts = contexts == null ? new ArrayList<>() : contexts;
        }
    }

    private LocalReport() {}

    public static String build(Context c, List<Day> input) {
        List<Day> days = new ArrayList<>(input);
        Collections.sort(days, (a, b) -> a.date.compareTo(b.date));
        if (days.isEmpty()) return c.getString(R.string.ai_report_empty);

        int total = 0;
        Day best = days.get(0);
        Day worst = days.get(0);
        for (Day d : days) {
            total += d.count;
            if (d.count < best.count) best = d;
            if (d.count > worst.count) worst = d;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(c.getString(R.string.local_total, total, (double) total / days.size())).append("\n");
        if (days.size() > 1) {
            sb.append(c.getString(R.string.local_best, best.date, best.count)).append("\n");
            sb.append(c.getString(R.string.local_worst, worst.date, worst.count)).append("\n");

            int half = days.size() / 2;
            int first = 0, second = 0;
            for (int i = 0; i < days.size(); i++) {
                if (i < half) first += days.get(i).count; else second += days.get(i).count;
            }

            double avgFirst = (double) first / half;
            double avgSecond = (double) second / (days.size() - half);
            if (avgSecond < avgFirst * 0.9) {
                sb.append(c.getString(R.string.local_trend_down, second, first));
            } else if (avgSecond > avgFirst * 1.1) {
                sb.append(c.getString(R.string.local_trend_up, second, first));
            } else {
                sb.append(c.getString(R.string.local_trend_flat));
            }
            sb.append("\n");
        }

        Map<String, int[]> perContext = new HashMap<>();
        for (Day d : days) {
            for (String ctx : d.contexts) {
                int[] v = perContext.get(ctx);
                if (v == null) {
                    v = new int[2];
                    perContext.put(ctx, v);
                }
                v[0] += d.count;
                v[1]++;
            }
        }
        String topContext = null;
        double topAvg = -1;
        for (Map.Entry<String, int[]> e : perContext.entrySet()) {
            double avg = (double) e.getValue()[0] / e.getValue()[1];
            if (avg > topAvg) {
                topAvg = avg;
                topContext = e.getKey();
            }
        }
        if (topContext != null) sb.append(c.getString(R.string.local_context, topContext, topAvg)).append("\n");

        sb.append("\n").append(c.getString(R.string.local_advice));
        return sb.toString();
    }
}
