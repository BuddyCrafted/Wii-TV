package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Google TV and Android TV, optional: brings WiiOS up whenever the TV's own home screen appears (Home pressed,
 * Back out of an app, power-on), for TVs whose system launcher keeps Home even when WiiOS is set as the home
 * app, as some TCL builds do. Nothing on the TV is disabled: switch this on or off in the TV's own Settings >
 * Accessibility (WiiOS Home button), with no computer needed.
 *
 * Two jobs, each its own switch in WiiOS Settings > Change interface:
 *  - Home button opens WiiOS: the TV's home appearing always brings WiiOS up.
 *  - Apps return to WiiOS: an app opened from WiiOS that closes itself with its own Exit (YouTube's, say)
 *    lands back on WiiOS instead of the TV's home, while the remote's Home still goes to the TV's home. The
 *    remote's Home key is noticed (never held back) to tell the two apart.
 *
 * It only acts while WiiOS is the interface in use: with the TV's own interface chosen in Choose an
 * interface, it stands aside completely and the TV's home is untouched. It hears window changes from the
 * stock launchers only (res/xml/google_tv_home_service.xml) and the Home key, and nothing else.
 */
public class GoogleTvHomeService extends AccessibilityService {
    /** One start per Home press or home window flicker. */
    private static final long DEBOUNCE_MS = 700;
    /** A home window this soon after the remote's Home key came from that key, not an app's exit. */
    private static final long HOME_KEY_MS = 2500;
    static final String KEY_HOME_OPENS = "gtv_home_opens_wiios", KEY_RETURN = "gtv_return_to_wiios";
    private long lastLaunch;
    private long homeKeyAt;
    /** Set when WiiOS opens an app (WiiMenuActivity); the next home window decides where its exit lands. */
    private static volatile boolean appFromWiiOS;

    static void openedFromWiiOS() {
        appFromWiiOS = true;
    }

    /** Back in WiiOS (Back out of the app, say): no app of its is open any more. */
    static void backInWiiOS() {
        appFromWiiOS = false;
    }

    @Override
    protected boolean onKeyEvent(android.view.KeyEvent event) {
        if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_HOME) homeKeyAt = SystemClock.uptimeMillis();
        return false;   // only noticed, never held back
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        RemoteServer.attach(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence cls = event.getClassName();
        // The launcher's home itself, not a dialog or panel it shows.
        if (cls == null || !(cls.toString().endsWith("HomeActivity") || cls.toString().endsWith("MainActivity"))) return;
        boolean fromWiiOS = appFromWiiOS;
        appFromWiiOS = false;
        if (!Systems.usingWiiOS(this)) return;
        long now = SystemClock.uptimeMillis();
        android.content.SharedPreferences prefs = Profiles.main(this);
        boolean homeKey = now - homeKeyAt < HOME_KEY_MS;
        boolean bring = prefs.getBoolean(KEY_HOME_OPENS, true)
                || (prefs.getBoolean(KEY_RETURN, false) && fromWiiOS && !homeKey);
        if (!bring) return;
        if (now - lastLaunch < DEBOUNCE_MS) return;
        lastLaunch = now;
        try {
            startActivity(new Intent(this, WiiMenuActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (RuntimeException ignored) {
            // can't start right now; the next home window tries again
        }
    }

    private static String id(android.content.Context c) {
        return c.getPackageName() + "/" + GoogleTvHomeService.class.getName();
    }

    /** Whether it's switched on in the TV's accessibility list. */
    static boolean enabled(android.content.Context c) {
        String cur = android.provider.Settings.Secure.getString(c.getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return cur != null && cur.contains(GoogleTvHomeService.class.getName());
    }

    /**
     * Switches it on or off directly when the installer granted WRITE_SECURE_SETTINGS; false without the grant,
     * and the caller opens the TV's Accessibility page instead.
     */
    static boolean setEnabled(android.content.Context c, boolean on) {
        try {
            android.content.ContentResolver cr = c.getContentResolver();
            String cur = android.provider.Settings.Secure.getString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            java.util.List<String> list = new java.util.ArrayList<>();
            if (cur != null && !cur.isEmpty() && !"null".equals(cur)) {
                for (String s : cur.split(":")) if (!s.isEmpty() && !s.contains(GoogleTvHomeService.class.getName())) list.add(s);
            }
            if (on) list.add(id(c));
            android.provider.Settings.Secure.putString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    android.text.TextUtils.join(":", list));
            if (on) android.provider.Settings.Secure.putInt(cr, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        RemoteServer.detach(this);
        super.onDestroy();
    }
}
