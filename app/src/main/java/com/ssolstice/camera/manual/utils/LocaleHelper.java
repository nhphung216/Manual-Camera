package com.ssolstice.camera.manual.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;

import java.util.Locale;

public class LocaleHelper {

    private static final String SELECTED_LANGUAGE = "Locale.Helper.Selected.Language";

    public static Context setLocale(Context context) {
        String language = getPersistedData(context);
        return updateResources(context, language);
    }

    public static void setLocale(Context context, String language) {
        persist(context, language);
        updateResources(context, language);
    }

    public static void clearOverride(Context context) {
        persist(context, "auto");
    }

    private static void persist(Context context, String language) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(SELECTED_LANGUAGE, language).apply();
    }

    private static String getPersistedData(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(SELECTED_LANGUAGE, "auto");
    }

    public static Context updateResources(Context context, String language) {
        Locale locale;
        if (language.equals("auto")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                locale = Resources.getSystem().getConfiguration().getLocales().get(0);
            } else {
                locale = Resources.getSystem().getConfiguration().locale;
            }
        } else {
            if (language.contains("-r")) {
                String[] parts = language.split("-r");
                locale = new Locale(parts[0], parts[1]);
            } else {
                locale = new Locale(language);
            }
        }

        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        return context.createConfigurationContext(config);
    }
}
