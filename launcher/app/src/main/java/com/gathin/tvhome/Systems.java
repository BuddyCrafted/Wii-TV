package com.gathin.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The systems the picker offers: WiiOS, the TV's own home (Google TV, Android TV, Fire TV) and any other
 * home-screen app installed. The choice is made once, the first time, and kept until it's changed in
 * WiiOS Settings or with the Choose a system tile in the TV's app row. Accounts, apps and settings are the
 * TV's and stay as they are; only the home screen changes.
 *
 * WiiOS stays the TV's real home: while another system is chosen, Home presses that reach the menu are
 * handed straight on to it (and on Fire TV the Home redirect stands aside). Opening WiiOS from its tile
 * still shows WiiOS.
 */
final class Systems {
    private Systems() {}

    static final String WIIOS = "wiios";
    /** Show the picker at every power-on too (off by default: it's a one-time choice). */
    static final String KEY_PICKER = "picker_every_start", KEY_SECONDS = "picker_seconds";
    private static final String KEY_ACTIVE = "system_active", KEY_CHOSEN = "system_chosen";

    /** Home activities that aren't systems to switch to (setup fallbacks, the chooser). */
    private static final Set<String> NOT_SYSTEMS = new HashSet<>(Arrays.asList(
            "com.android.settings", "com.android.tv.settings", "android", "com.android.provision",
            "com.google.android.tungsten.setupwraith", "com.google.android.apps.tv.dreamx"));

    static final class Sys {
        final String id;
        final String label;
        final ResolveInfo info;

        Sys(String id, String label, ResolveInfo info) {
            this.id = id;
            this.label = label;
            this.info = info;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return Profiles.main(c);
    }

    /** WiiOS first, then every other home screen on the TV. */
    static List<Sys> list(Context c) {
        List<Sys> out = new ArrayList<>();
        out.add(new Sys(WIIOS, "WiiOS", null));
        PackageManager pm = c.getPackageManager();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : pm.queryIntentActivities(home, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || NOT_SYSTEMS.contains(pkg)) continue;
            String id = new ComponentName(pkg, ri.activityInfo.name).flattenToShortString();
            out.add(new Sys(id, label(pkg, ri.loadLabel(pm)), ri));
        }
        return out;
    }

    /** The TV's own homes by the name people know them by. */
    private static String label(String pkg, CharSequence fallback) {
        switch (pkg) {
            case "com.google.android.apps.tv.launcherx": return "Google TV";
            case "com.google.android.tvlauncher": case "com.google.android.leanbacklauncher": return "Android TV";
            case "com.amazon.tv.launcher": return "Fire TV";
            default: return fallback == null ? pkg : fallback.toString();
        }
    }

    static boolean pickerEveryStart(Context c) {
        return prefs(c).getBoolean(KEY_PICKER, false);
    }

    /** True until a system has been chosen once. */
    static boolean firstChoice(Context c) {
        return !prefs(c).getBoolean(KEY_CHOSEN, false) && list(c).size() > 1;
    }

    static int seconds(Context c) {
        return prefs(c).getInt(KEY_SECONDS, 5);
    }

    static String active(Context c) {
        return prefs(c).getString(KEY_ACTIVE, WIIOS);
    }

    static void setActive(Context c, String id) {
        prefs(c).edit().putString(KEY_ACTIVE, id).putBoolean(KEY_CHOSEN, true).apply();
    }

    static boolean wiiosActive(Context c) {
        return WIIOS.equals(active(c));
    }

    /** At power-on, when asked to: the picker is only worth showing with something besides WiiOS to pick. */
    static boolean offerAtStart(Context c) {
        return pickerEveryStart(c) && list(c).size() > 1;
    }

    /** Name of the system in use, for Settings. */
    static String activeLabel(Context c) {
        String id = active(c);
        for (Sys s : list(c)) if (s.id.equals(id)) return s.label;
        return "WiiOS";
    }

    static Intent launch(Context c, String id) {
        if (WIIOS.equals(id)) return null;
        ComponentName cn = ComponentName.unflattenFromString(id);
        if (cn == null) return null;
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i.resolveActivity(c.getPackageManager()) == null ? null : i;
    }

    /**
     * While another system is chosen, hands the menu's Home on to it. False (and WiiOS active again) if
     * that system is gone or won't open, so Home always lands somewhere.
     */
    static boolean forward(Activity a) {
        if (wiiosActive(a)) return false;
        Intent i = launch(a, active(a));
        if (i != null) {
            try {
                a.startActivity(i);
                a.overridePendingTransition(0, 0);
                return true;
            } catch (RuntimeException ignored) {
                // fall through: back to WiiOS
            }
        }
        setActive(a, WIIOS);
        return false;
    }
}
