package com.example.batteryguard;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private static final String FILE = "batteryguard";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) { return sp(c).getBoolean("enabled", false); }
    static void setEnabled(Context c, boolean v) { sp(c).edit().putBoolean("enabled", v).apply(); }

    /** Battery % at which the warning + countdown appears. */
    static int threshold(Context c) { return sp(c).getInt("threshold", 5); }
    static void setThreshold(Context c, int v) { sp(c).edit().putInt("threshold", v).apply(); }

    /** Seconds of countdown before switching to the "Battery life extender" screen. */
    static int countdown(Context c) { return sp(c).getInt("countdown", 15); }
    static void setCountdown(Context c, int v) { sp(c).edit().putInt("countdown", v).apply(); }
}
