package com.example.wordtrigger;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.ArrayRes;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class ExercisePool {
    public static final String TWISTERS = "twisters";
    public static final String SYNONYMS = "synonyms";
    public static final String TOPICS = "topics";

    private static final String PREFS = "exercise_pool";
    private static final int BATCH = 6;
    private static final int LOW_WATER = 2;
    private static final int RECENT = 30;
    private static final long COOLDOWN_MS = 60_000;
    private static final Random RANDOM = new Random();
    private static final Map<String, Long> lastRequest = new HashMap<>();
    private static final Map<String, Boolean> inFlight = new HashMap<>();

    private ExercisePool() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(Context c, String type, String what) {
        return what + "_" + type + "_" + AppSettings.currentLanguage(c);
    }

    private static List<String> read(Context c, String k) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(k, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (Exception ignored) { }
        return out;
    }

    private static void write(Context c, String k, List<String> items) {
        prefs(c).edit().putString(k, new JSONArray(items).toString()).apply();
    }

    private static String title(String item) {
        int bar = item.indexOf('|');
        return (bar > 0 ? item.substring(0, bar) : item).trim().toLowerCase();
    }

    public static synchronized String next(Context c, String type, @ArrayRes int fallbackArray, String exclude) {
        String poolKey = key(c, type, "pool");
        String recentKey = key(c, type, "recent");
        List<String> pool = read(c, poolKey);
        List<String> recent = read(c, recentKey);
        String ex = exclude == null ? "" : exclude.trim().toLowerCase();

        String item = null;
        while (!pool.isEmpty() && item == null) {
            String cand = pool.remove(0);
            if (!title(cand).equals(ex) && !recent.contains(title(cand))) item = cand;
        }
        write(c, poolKey, pool);

        if (item == null) {
            List<String> all = ExerciseContent.list(c, fallbackArray);
            List<String> fresh = new ArrayList<>();
            for (String s : all) if (!recent.contains(title(s)) && !title(s).equals(ex)) fresh.add(s);
            if (fresh.isEmpty()) {
                recent.clear();
                for (String s : all) if (!title(s).equals(ex)) fresh.add(s);
                if (fresh.isEmpty()) fresh = all;
            }
            item = fresh.get(RANDOM.nextInt(fresh.size()));
        }

        recent.add(title(item));
        while (recent.size() > RECENT) recent.remove(0);
        write(c, recentKey, recent);

        if (pool.size() <= LOW_WATER) refill(c, type);
        return item;
    }

    public static void prefetch(Context c, String type) {
        if (read(c, key(c, type, "pool")).size() <= LOW_WATER) refill(c, type);
    }

    private static void refill(Context c, String type) {
        final Context app = c.getApplicationContext();
        if (!LlmClient.isConfigured()) return;
        final String flag = key(app, type, "flight");
        synchronized (ExercisePool.class) {
            long now = System.currentTimeMillis();
            Long last = lastRequest.get(flag);
            if (Boolean.TRUE.equals(inFlight.get(flag)) || (last != null && now - last < COOLDOWN_MS)) return;
            inFlight.put(flag, true);
            lastRequest.put(flag, now);
        }

        List<String> recent = read(app, key(app, type, "recent"));
        String avoid = recent.isEmpty() ? "-" : android.text.TextUtils.join(", ", recent.subList(Math.max(0, recent.size() - 15), recent.size()));
        String[] themes = app.getResources().getStringArray(R.array.gen_themes);
        String theme = themes[RANDOM.nextInt(themes.length)];

        int systemRes, userRes;
        switch (type) {
            case SYNONYMS: systemRes = R.string.ai_gen_system; userRes = R.string.ai_gen_synonyms; break;
            case TOPICS: systemRes = R.string.ai_gen_system; userRes = R.string.ai_gen_topics; break;
            default: systemRes = R.string.ai_gen_system; userRes = R.string.ai_gen_twisters;
        }
        LlmClient.chatStructured(app, app.getString(systemRes), app.getString(userRes, BATCH, avoid, theme), 900, null,
                new LlmClient.Callback() {
                    @Override public void onResult(String text) {
                        List<String> good = new ArrayList<>();
                        for (String item : LlmClient.extractStringArray(text)) {
                            String clean = item.trim();
                            if (isValid(type, clean)) good.add(clean);
                        }
                        synchronized (ExercisePool.class) {
                            String poolKey = key(app, type, "pool");
                            List<String> pool = read(app, poolKey);
                            pool.addAll(good);
                            write(app, poolKey, pool);
                            inFlight.put(flag, false);
                        }
                    }
                    @Override public void onError(LlmClient.Kind kind, String detail) {
                        synchronized (ExercisePool.class) { inFlight.put(flag, false); }
                    }
                });
    }

    static boolean isValid(String type, String item) {
        if (item.isEmpty() || item.length() > 400) return false;
        switch (type) {
            case TWISTERS: {
                int words = SpeechScorer.tokenize(item).size();
                return words >= 3 && words <= 16 && item.length() <= 140 && !item.contains("|");
            }
            case SYNONYMS: {
                int bar = item.indexOf('|');
                return bar > 0 && SpeechScorer.splitSynonyms(item.substring(bar + 1)).size() >= 4;
            }
            case TOPICS: {
                int bar = item.indexOf('|');
                return bar > 0 && SpeechScorer.splitSynonyms(item.substring(bar + 1)).size() >= 5;
            }
            default: return false;
        }
    }
}
