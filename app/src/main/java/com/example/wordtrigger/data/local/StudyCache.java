package com.example.wordtrigger.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.wordtrigger.widget.StreakWidgetProvider;
import com.example.wordtrigger.data.remote.StudyProgress;

public final class StudyCache {
    private static final String PREFS = "study_cache";
    private static final String KEY_STREAK = "streak";
    private static final String KEY_LAST_DATE = "last_streak_date";
    private static final String KEY_BEST = "best_streak";
    private static final String KEY_DONE_DATE = "done_date";
    private static final String KEY_DONE_COUNT = "done_count";

    private StudyCache() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int effective(int streak, String lastDate, String today, String yesterday) {
        if (lastDate == null) return 0;
        return lastDate.equals(today) || lastDate.equals(yesterday) ? streak : 0;
    }

    public static synchronized void saveStreak(Context c, int streak, String lastStreakDate) {
        SharedPreferences p = prefs(c);
        p.edit().putInt(KEY_STREAK, streak).putString(KEY_LAST_DATE, lastStreakDate)
                .putInt(KEY_BEST, Math.max(p.getInt(KEY_BEST, 0), streak)).apply();
        StreakWidgetProvider.updateAll(c);
    }

    public static synchronized void saveBest(Context c, int best) {
        SharedPreferences p = prefs(c);
        p.edit().putInt(KEY_BEST, Math.max(p.getInt(KEY_BEST, 0), best)).apply();
        StreakWidgetProvider.updateAll(c);
    }

    public static synchronized int best(Context c) {
        SharedPreferences p = prefs(c);
        return Math.max(p.getInt(KEY_BEST, 0), p.getInt(KEY_STREAK, 0));
    }

    public static synchronized void saveDone(Context c, int doneToday) {
        prefs(c).edit().putString(KEY_DONE_DATE, StudyProgress.dateString(0))
                .putInt(KEY_DONE_COUNT, doneToday).apply();
        StreakWidgetProvider.updateAll(c);
    }

    public static synchronized int effectiveStreak(Context c) {
        SharedPreferences p = prefs(c);
        return effective(p.getInt(KEY_STREAK, 0), p.getString(KEY_LAST_DATE, null),
                StudyProgress.dateString(0), StudyProgress.dateString(-1));
    }

    public static synchronized int doneToday(Context c) {
        SharedPreferences p = prefs(c);
        return StudyProgress.dateString(0).equals(p.getString(KEY_DONE_DATE, "")) ? p.getInt(KEY_DONE_COUNT, 0) : 0;
    }
}
