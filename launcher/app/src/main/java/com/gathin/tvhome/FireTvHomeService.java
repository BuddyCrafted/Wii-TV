package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Makes the channel menu the home screen on Fire TV. Fire OS protects its own launcher: it cannot be
 * disabled, and set-home-activity is accepted but ignored. So this service:
 *  - consumes the remote's Home button and opens the channel menu instead;
 *  - holding Home opens Settings, since the Fire OS quick panel never sees the key any more;
 *  - whenever the Amazon launcher comes to the front anyway (after boot, or when an app exits to it),
 *    brings the channel menu straight back on top.
 * Event-driven only: res/xml/fire_tv_home_service.xml limits window events to the Amazon launcher.
 * Same approach as ivantrj/firetv-glass-launcher (MIT).
 *
 * Enable over adb (launcher/build-and-install.sh does this on Fire TV):
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.FireTvHomeService
 *   settings put secure accessibility_enabled 1
 */
public class FireTvHomeService extends AccessibilityService {

    static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    /** One launch per Home press or launcher flash. */
    private static final long DEBOUNCE_MS = 600;

    private long lastLaunch;
    private boolean longPressed;

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                longPressed = false;
            } else if (!longPressed && event.isLongPress()) {
                longPressed = true;
                openSettings();
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled() && !longPressed) {
            openMenu();
        }
        // Swallow both down and up so Fire OS never sees Home.
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getPackageName() != null
                && AMAZON_LAUNCHER.contentEquals(event.getPackageName())) {
            openMenu();
        }
    }

    private void openMenu() {
        long now = SystemClock.uptimeMillis();
        if (now - lastLaunch < DEBOUNCE_MS) return;
        lastLaunch = now;
        startActivity(new Intent(this, WiiMenuActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            openMenu();
        }
    }

    @Override
    public void onInterrupt() {
    }
}
