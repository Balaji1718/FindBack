package com.balaji.findback.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class ThemeManager {

    private static final String PREF_NAME = "theme_pref";
    private static final String KEY_THEME = "app_theme";

    public static final int LIGHT = 0;
    public static final int DARK = 1;

    /**
     * Applies theme per-activity using setLocalNightMode for instant, flicker-free switching.
     */
    public static void applyThemeToActivity(androidx.appcompat.app.AppCompatActivity activity) {
        int theme = getSavedTheme(activity);
        int mode = (theme == DARK) ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        
        if (activity.getDelegate().getLocalNightMode() != mode) {
            activity.getDelegate().setLocalNightMode(mode);
        }
    }

    public static void applyTheme(Context context) {
        int theme = getSavedTheme(context);
        AppCompatDelegate.setDefaultNightMode(
            theme == DARK ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }

    public static void setTheme(Context context, int theme) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME, theme).apply();
        // Do NOT call setDefaultNightMode here to avoid global activity recreation lag
    }
    
    public static int getSavedTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_THEME, LIGHT);
    }
}
