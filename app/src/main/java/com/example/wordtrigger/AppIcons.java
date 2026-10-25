package com.example.wordtrigger;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

public final class AppIcons {
    public static final String DEFAULT_ID = "classic";

    public static final class Choice {
        public final String id;
        public final String alias;
        @StringRes public final int label;
        @DrawableRes public final int background;
        @DrawableRes public final int foreground;

        Choice(String id, String alias, int label, int background, int foreground) {
            this.id = id;
            this.alias = alias;
            this.label = label;
            this.background = background;
            this.foreground = foreground;
        }
    }

    public static final Choice[] ALL = {
            new Choice("classic", ".IconClassic", R.string.icon_classic,
                    R.drawable.ic_launcher_background, R.mipmap.ic_launcher_foreground),
            new Choice("dark", ".IconDark", R.string.icon_dark,
                    R.drawable.ic_icon_bg_dark, R.drawable.ic_icon_fg_wave_blue),
            new Choice("blue", ".IconBlue", R.string.icon_blue,
                    R.drawable.ic_icon_bg_blue, R.drawable.ic_icon_fg_wave_white),
            new Choice("light", ".IconLight", R.string.icon_light,
                    R.drawable.ic_icon_bg_light, R.drawable.ic_icon_fg_wave_blue),
            new Choice("flame", ".IconFlame", R.string.icon_flame,
                    R.drawable.ic_icon_bg_dark, R.drawable.ic_black),
    };

    private AppIcons() {}

    public static Choice byId(String id) {
        for (Choice c : ALL) if (c.id.equals(id)) return c;
        return ALL[0];
    }

    public static Drawable preview(Context c, Choice choice) {
        Drawable bg = ContextCompat.getDrawable(c, choice.background);
        Drawable fg = ContextCompat.getDrawable(c, choice.foreground);
        if (bg == null || fg == null) return null;
        return new LayerDrawable(new Drawable[]{bg.mutate(), fg.mutate()});
    }

    public static void apply(Context c, String id) {
        Choice chosen = byId(id);
        PackageManager pm = c.getPackageManager();

        set(pm, c, chosen, true);
        for (Choice other : ALL) if (other != chosen) set(pm, c, other, false);
        AppSettings.setIconId(c, chosen.id);
    }

    private static void set(PackageManager pm, Context c, Choice choice, boolean enabled) {
        pm.setComponentEnabledSetting(new ComponentName(c.getPackageName(), c.getPackageName() + choice.alias),
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }
}
