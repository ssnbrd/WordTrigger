package com.example.wordtrigger.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.wordtrigger.util.AppIcons;

public final class AppSettings {
    private static final String PREFS = "app_settings";
    private static final String KEY_AI_TOPIC = "ai_topic_check";
    private static final String KEY_LANG = "language";

    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_ICON = "app_icon";

    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_SYSTEM = "system";

    private AppSettings() {}

    /** Сохранённая тема. По умолчанию тёмная: приложение так выглядело всегда. */
    public static String themeMode(Context c) {
        String v = prefs(c).getString(KEY_THEME, THEME_DARK);
        return THEME_LIGHT.equals(v) || THEME_SYSTEM.equals(v) ? v : THEME_DARK;
    }

    /** Сохраняет и сразу применяет тему: открытые экраны пересоздаются сами. */
    public static void setThemeMode(Context c, String mode) {
        prefs(c).edit().putString(KEY_THEME, mode).apply();
        applyTheme(c);
    }

    public static void applyTheme(Context c) {
        String mode = themeMode(c);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                THEME_LIGHT.equals(mode) ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                        : THEME_SYSTEM.equals(mode) ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
    }

    public static boolean isNight(Context c) {
        return (c.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public static String iconId(Context c) {
        return prefs(c).getString(KEY_ICON, AppIcons.DEFAULT_ID);
    }

    public static void setIconId(Context c, String id) {
        prefs(c).edit().putString(KEY_ICON, id).apply();
    }

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
