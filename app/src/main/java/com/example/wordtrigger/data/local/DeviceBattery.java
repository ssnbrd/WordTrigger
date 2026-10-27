package com.example.wordtrigger.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.wordtrigger.widget.WidgetMath;

public final class DeviceBattery {
    private static final String PREFS = "device_battery";
    private static final long FRESH_MS = 60 * 60 * 1000L;

    private DeviceBattery() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int parsePacket(byte[] data, int len) {
        return WidgetMath.parseBattery(data, len);
    }

    public static void save(Context c, int percent) {
        prefs(c).edit().putInt("percent", percent).putLong("time", System.currentTimeMillis()).apply();
    }

    public static int get(Context c) {
        SharedPreferences p = prefs(c);
        if (System.currentTimeMillis() - p.getLong("time", 0) > FRESH_MS) return -1;
        return p.getInt("percent", -1);
    }
}
