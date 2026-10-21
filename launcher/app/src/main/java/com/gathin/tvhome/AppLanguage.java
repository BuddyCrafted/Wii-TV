package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The channel menu's own language, independent of the TV's: English unless another one is picked in
 * Settings > Language. Applied by wrapping the activity's base context, which works on every Android
 * version the launcher runs on (Fire OS 7 is Android 9, before per-app languages existed).
 */
final class AppLanguage {
    private AppLanguage() {}

    static final String PREFS = "wii_menu";
    static final String KEY = "language";
    static final String DEFAULT = "en";

    /** Language tags with a translation in res/values-*, English first. Mirrors res/xml/locales_config.xml. */
    static final String[] TAGS = {
        "en", "es", "es-AR", "fr", "de", "it", "pt", "ca", "pl", "cs", "fi", "ru", "tr",
        "ar", "iw", "th", "vi", "in", "bn", "zh-CN", "zh-TW", "sr-Latn",
    };

    static String current(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, DEFAULT);
    }

    static void set(Context context, String tag) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply();
    }

    static Locale locale(String tag) {
        // Android's resource folders still use the legacy codes for Hebrew and Indonesian.
        return Locale.forLanguageTag(tag);
    }

    /** The language's name in that language, e.g. "Español", "Deutsch". */
    static String displayName(String tag) {
        Locale l = locale(tag);
        String name = l.getDisplayName(l);
        return name.isEmpty() ? tag : name.substring(0, 1).toUpperCase(l) + name.substring(1);
    }

    static Context wrap(Context base) {
        Locale l = locale(current(base));
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(l);
        config.setLayoutDirection(l);
        return base.createConfigurationContext(config);
    }

    static List<String> tags() {
        List<String> out = new ArrayList<>();
        for (String t : TAGS) out.add(t);
        return out;
    }
}
