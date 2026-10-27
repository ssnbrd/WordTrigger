package com.example.wordtrigger.widget;

import java.nio.charset.StandardCharsets;

public final class WidgetMath {
    private WidgetMath() {}

    public static int filledDots(int streak) {
        if (streak <= 0) return 0;
        return streak % 7 == 0 ? 7 : streak % 7;
    }

    public static Integer changePercent(int today, int yesterday) {
        if (yesterday <= 0) return null;
        return Math.round((today - yesterday) * 100f / yesterday);
    }

    public static int parseBattery(byte[] data, int len) {
        if (len < 3 || len > 8 || data[0] != 'B' || data[1] != ':') return -1;
        try {
            int v = Integer.parseInt(new String(data, 2, len - 2, StandardCharsets.US_ASCII).trim());
            return v < 0 || v > 100 ? -1 : v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
