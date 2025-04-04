package com.example.soundseeder;

import android.util.Log;

public class Logger {
    private static boolean log = true;
    public static String TAG = "CHORUS_DEBUG";

    public static void setLogging(boolean enabled) {
        log = enabled;
    }

    public static void log(Object o) {
        if (log) {
            Log.d(TAG, o.toString());
        }
    }

    public static void error(Exception e) {
        if (log) {
            Log.e(TAG, e.toString());
            for (StackTraceElement elem : e.getStackTrace()) {
                Log.e(TAG, elem.toString());
            }
        }
    }
}
