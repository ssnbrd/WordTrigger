package com.example.wordtrigger;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppSettings {
    private static final String PREFS = "app_settings";
    private static final String KEY_AI_TOPIC = "ai_topic_check";
    private static final String KEY_LANG = "language";

    private AppSettings() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isAiCheckEnabled(Context c) {
        return prefs(c).getBoolean(KEY_AI_TOPIC, true);
    }

    public static void setAiCheckEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_AI_TOPIC, enabled).apply();
    }

    public static String contentCollection(Context c, String base) {
        return "ru".equals(currentLanguage(c)) ? base : base + "_en";
    }

    public static String currentLanguage(Context c) {
        String saved = prefs(c).getString(KEY_LANG, null);
        if (saved != null) return saved;

        androidx.core.os.LocaleListCompat app = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales();
        if (!app.isEmpty() && app.get(0) != null) return "ru".equals(app.get(0).getLanguage()) ? "ru" : "en";

        String lang = c.getResources().getConfiguration().getLocales().get(0).getLanguage();
        return "ru".equals(lang) ? "ru" : "en";
    }

    public static void setLanguage(Context c, String lang) {
        prefs(c).edit().putString(KEY_LANG, lang).apply();
    }
}
