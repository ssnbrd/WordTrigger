package com.example.wordtrigger.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.wordtrigger.R;
import com.example.wordtrigger.data.remote.StudyProgress;
import com.example.wordtrigger.util.WheelMath;

import java.util.ArrayList;
import java.util.List;

public final class WordOfDay {
    public static final int SECTORS = 8;

    private static final String PREFS = "word_of_day";
    private static final String KEY_DATE = "date";
    private static final String KEY_WORD = "word";
    private static final String KEY_LANG = "lang";

    public static final class Entry {
        public final String word, definition, example;

        Entry(String word, String definition, String example) {
            this.word = word;
            this.definition = definition;
            this.example = example;
        }
    }

    private WordOfDay() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String today() {
        return StudyProgress.dateString(0);
    }

    public static List<Entry> bank(Context c) {
        String[] raw = c.getResources().getStringArray(R.array.word_of_day_bank);
        List<Entry> list = new ArrayList<>();
        for (String line : raw) {
            String[] p = line.split("\\|", 3);
            if (p.length == 3) list.add(new Entry(p[0].trim(), p[1].trim(), p[2].trim()));
        }
        return list;
    }

    public static int[] todayCandidates(Context c) {
        return WheelMath.candidates(bank(c).size(), SECTORS, today(), AppSettings.currentLanguage(c));
    }

    public static Entry today(Context c) {
        SharedPreferences p = prefs(c);
        if (!today().equals(p.getString(KEY_DATE, ""))) return null;
        List<Entry> bank = bank(c);
        int idx = p.getInt(KEY_WORD, -1);
        if (idx < 0 || idx >= bank.size()) return null;

        return bank.get(idx);
    }

    public static boolean hasToday(Context c) {
        return today(c) != null;
    }

    public static void saveToday(Context c, int bankIndex) {
        prefs(c).edit().putString(KEY_DATE, today()).putInt(KEY_WORD, bankIndex)
                .putString(KEY_LANG, AppSettings.currentLanguage(c)).apply();
    }
}
