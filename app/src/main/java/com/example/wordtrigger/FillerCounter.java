package com.example.wordtrigger;

import android.content.Context;
import android.content.SharedPreferences;

public final class FillerCounter {
    private static final String PREFS = "filler_counter";
    private static final String KEY_DATE = "date";
    private static final String KEY_COUNT = "count";
    private static final String KEY_YESTERDAY = "yesterday";

    private FillerCounter() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized int getToday(Context c) {
        SharedPreferences p = prefs(c);
        return StudyProgress.dateString(0).equals(p.getString(KEY_DATE, "")) ? p.getInt(KEY_COUNT, 0) : 0;
    }

    public static synchronized int getYesterday(Context c) {
        SharedPreferences p = prefs(c);
        String date = p.getString(KEY_DATE, "");
        if (StudyProgress.dateString(0).equals(date)) return p.getInt(KEY_YESTERDAY, -1);
        if (StudyProgress.dateString(-1).equals(date)) return p.getInt(KEY_COUNT, 0);   // сохранённое «сегодня» уже стало вчера
        return -1;
    }

    public static Integer changePercent(int today, int yesterday) {
        return WidgetMath.changePercent(today, yesterday);
    }

    public static synchronized void increment(Context c) {
        set(c, getToday(c) + 1);
    }

    public static synchronized void set(Context c, int count) {
        SharedPreferences p = prefs(c);
        int yesterday = getYesterday(c);
        p.edit().putString(KEY_DATE, StudyProgress.dateString(0)).putInt(KEY_COUNT, count)
                .putInt(KEY_YESTERDAY, yesterday).apply();
        FillerWidgetProvider.updateAll(c);
    }

    public static synchronized void setYesterday(Context c, int yesterday) {
        SharedPreferences p = prefs(c);
        String today = StudyProgress.dateString(0);
        SharedPreferences.Editor e = p.edit().putInt(KEY_YESTERDAY, yesterday);
        if (!today.equals(p.getString(KEY_DATE, ""))) e.putString(KEY_DATE, today).putInt(KEY_COUNT, 0);
        e.apply();
        FillerWidgetProvider.updateAll(c);
    }
}
