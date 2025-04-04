package com.example.soundseeder;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;

public class Utils {
    public static final String SHARED_PREFS = "com.avrapps.chorus_preferences";

    public static String getUserName(Context context) {
        return context.getSharedPreferences(SHARED_PREFS, 0).getString(Constants.PREFS_USERNAME, "");
    }

    public static void setUserName(Context context, String userName) {
        context.getSharedPreferences(SHARED_PREFS, 0).edit().putString(Constants.PREFS_USERNAME, userName).commit();
    }

    public static void setHotspotName(Context context, String hotspotName) {
        context.getSharedPreferences(SHARED_PREFS, 0).edit().putString(Constants.PREF_HOTSPOT_NAME, hotspotName).commit();
    }

    public static String getHotspotName(Context context) {
        return context.getSharedPreferences(SHARED_PREFS, 0).getString(Constants.PREF_HOTSPOT_NAME, getUserName(context) + "'s Chorus Network");
    }

    public static boolean shouldSwitchMobileData(Context context) {
        return context.getSharedPreferences(SHARED_PREFS, 0).getBoolean(Constants.PREF_MOBILE_DATA_SWITCH, true);
    }

    public static int getDarkColor(int inputColor) {
        float[] hsv = new float[3];
        Color.colorToHSV(inputColor, hsv);
        hsv[2] = hsv[2] * 0.8f;
        return Color.HSVToColor(hsv);
    }

    public static int getAccentColor(int inputColor) {
        float[] hsv = new float[3];
        Color.colorToHSV(inputColor, hsv);
        hsv[0] = hsv[0] * 1.053f;
        hsv[1] = hsv[1] * 0.976f;
        hsv[2] = hsv[2] * 1.052f;
        return Color.HSVToColor(hsv);
    }

    public static boolean hasLollipop() {
        return Build.VERSION.SDK_INT >= 21;
    }

    public static boolean hasJellyBean() {
        return Build.VERSION.SDK_INT >= 16;
    }
}