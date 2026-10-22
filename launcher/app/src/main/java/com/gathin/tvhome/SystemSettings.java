package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

/**
 * Reads and writes the TV's own screensaver and sleep timers, so the Settings screen can set them
 * without opening the system pages. Writing needs two grants that only ADB can give, and that
 * build-and-install.sh gives: WRITE_SETTINGS (appops) for the screensaver delay and
 * WRITE_SECURE_SETTINGS (pm grant) for the sleep timer and the screensaver choice. Without them every
 * write returns false and the caller opens the system page instead.
 */
final class SystemSettings {
    private SystemSettings() {}

    /** Settings.Secure keys Android TV's own Settings app uses; hidden from the public SDK. */
    static final String SLEEP_TIMEOUT = "sleep_timeout";
    static final String ATTENTIVE_TIMEOUT = "attentive_timeout";
    static final String SCREENSAVER_COMPONENTS = "screensaver_components";
    static final String SCREENSAVER_ENABLED = "screensaver_enabled";
    static final int NEVER = -1;
    /** The shortest no-input-at-all timeout we set: long enough for a film. */
    static final int ATTENTIVE_MIN = 4 * 60 * 60_000;
    /** Out of the box the TV goes to sleep after an hour without use. */
    static final int DEFAULT_SLEEP = 60 * 60_000;

    /**
     * Sets the sleep timer to an hour once, if it was off (never), so an idle TV turns itself off. Done
     * only once, so a later choice in Settings sticks; tried again next start while the grant is missing.
     */
    static void applyDefaultSleep(Context c, android.content.SharedPreferences prefs) {
        if (prefs.getBoolean("sleep_default_done", false)) return;
        if (sleepAfter(c) != NEVER || setSleepAfter(c, DEFAULT_SLEEP)) {
            prefs.edit().putBoolean("sleep_default_done", true).apply();
        }
    }

    /** Idle time before the screensaver starts, in ms; NEVER for never. */
    static int screensaverDelay(Context c) {
        int v = Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, NEVER);
        return v >= Integer.MAX_VALUE / 2 ? NEVER : v;
    }

    static boolean setScreensaverDelay(Context c, int ms) {
        try {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(c)) return false;
            return Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT,
                    ms == NEVER ? Integer.MAX_VALUE : ms);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Time after which the screen turns off and the device sleeps, in ms; NEVER for never. */
    static int sleepAfter(Context c) {
        int v = Settings.Secure.getInt(c.getContentResolver(), SLEEP_TIMEOUT, NEVER);
        return v <= 0 || v >= Integer.MAX_VALUE / 2 ? NEVER : v;
    }

    static boolean setSleepAfter(Context c, int ms) {
        ContentResolver cr = c.getContentResolver();
        try {
            boolean ok = Settings.Secure.putInt(cr, SLEEP_TIMEOUT, ms == NEVER ? -1 : ms);
            // Android 10+ also turns the TV off after a stretch with no remote input at all, even while a
            // film plays. Keep it in step, but never under 4 hours, so a long film isn't cut off.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Settings.Secure.putInt(cr, ATTENTIVE_TIMEOUT, ms == NEVER ? -1 : Math.max(ms, ATTENTIVE_MIN));
            }
            return ok;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The chosen screensaver's component ("pkg/.Class"), or "" for none. */
    static String screensaver(Context c) {
        String v = Settings.Secure.getString(c.getContentResolver(), SCREENSAVER_COMPONENTS);
        return v == null ? "" : v;
    }

    static boolean setScreensaver(Context c, String component) {
        try {
            ContentResolver cr = c.getContentResolver();
            Settings.Secure.putString(cr, SCREENSAVER_COMPONENTS, component);
            return Settings.Secure.putInt(cr, SCREENSAVER_ENABLED, component.isEmpty() ? 0 : 1);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
