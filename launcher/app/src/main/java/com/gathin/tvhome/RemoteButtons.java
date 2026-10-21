package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The remote's app buttons as the viewer set them in Settings > Remote Buttons, on top of app_keys from
 * config.xml (or a theme). Saved as "keycode:package" pairs; an empty package means "leave this button to
 * the system". InputKeyService reads the result and follows changes as they are made.
 */
final class RemoteButtons {
    private RemoteButtons() {}

    static final String KEY_MAP = "remote_buttons";
    static final String KEY_ON = "remote_buttons_on";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(AppLanguage.PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean(KEY_ON, true);
    }

    /** The viewer's own choices: keycode → package ("" = none). */
    static Map<Integer, String> overrides(Context c) {
        return parse(prefs(c).getString(KEY_MAP, ""));
    }

    static Map<Integer, String> parse(String saved) {
        Map<Integer, String> out = new LinkedHashMap<>();
        if (saved == null || saved.isEmpty()) return out;
        for (String pair : saved.split(",")) {
            int colon = pair.indexOf(':');
            if (colon <= 0) continue;
            try {
                out.put(Integer.parseInt(pair.substring(0, colon).trim()), pair.substring(colon + 1).trim());
            } catch (NumberFormatException ignored) {
                // skip a damaged entry
            }
        }
        return out;
    }

    static String join(Map<Integer, String> map) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<Integer, String> e : map.entrySet()) {
            if (b.length() > 0) b.append(',');
            b.append(e.getKey()).append(':').append(e.getValue());
        }
        return b.toString();
    }

    static void set(Context c, int keyCode, String pkg) {
        Map<Integer, String> map = overrides(c);
        map.put(keyCode, pkg == null ? "" : pkg);
        prefs(c).edit().putString(KEY_MAP, join(map)).apply();
    }

    /** What each button does now: config's app_keys, then the viewer's choices; empty when switched off. */
    static Map<Integer, String> effective(Map<Integer, String> fromConfig, Map<Integer, String> overrides, boolean on) {
        Map<Integer, String> out = new LinkedHashMap<>();
        if (!on) return out;
        out.putAll(fromConfig);
        for (Map.Entry<Integer, String> e : overrides.entrySet()) {
            if (e.getValue().isEmpty()) out.remove(e.getKey());
            else out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /** A readable name for a button: the known Google TV app buttons by name, others by their key name. */
    static String name(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_3) return "YouTube button";
        String n = KeyEvent.keyCodeToString(keyCode);
        if (n.startsWith("KEYCODE_")) n = n.substring(8);
        n = n.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return n.isEmpty() ? "Button " + keyCode : Character.toUpperCase(n.charAt(0)) + n.substring(1) + " button";
    }

    /**
     * Switches InputKeyService on in the system's accessibility list, which needs WRITE_SECURE_SETTINGS (granted
     * by build-and-install.sh). True when it is on afterwards.
     */
    static boolean ensureService(Context c) {
        String svc = c.getPackageName() + "/" + InputKeyService.class.getName();
        try {
            android.content.ContentResolver cr = c.getContentResolver();
            String cur = android.provider.Settings.Secure.getString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur != null && cur.contains("InputKeyService")) return true;
            String next = cur == null || cur.isEmpty() || "null".equals(cur) ? svc : cur + ":" + svc;
            android.provider.Settings.Secure.putString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next);
            android.provider.Settings.Secure.putInt(cr, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    /** Keys that must stay with the menu and the system, never captured as a shortcut. */
    static boolean reserved(int k) {
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_HOME: case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN: case KeyEvent.KEYCODE_VOLUME_MUTE: case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_MENU: case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_BUTTON_B:
                return true;
            default:
                return false;
        }
    }
}
