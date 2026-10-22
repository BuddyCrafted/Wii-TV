package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;

import java.io.File;

/**
 * Makes the channel menu the home screen on Fire TV. Fire OS protects its own launcher: it cannot be
 * disabled, and set-home-activity is accepted but ignored. So this service:
 *  - consumes the remote's Home button and opens the channel menu instead;
 *  - holding Home opens Settings, since the Fire OS quick panel never sees the key any more;
 *  - whenever the Amazon launcher comes to the front anyway (after boot, or when an app exits to it),
 *    brings the channel menu straight back on top;
 *  - covers the screen at once with the last picture of the menu (see {@link #showCover}), because
 *    Fire OS still handles Home itself and then holds every other app's launch for about 5 seconds
 *    (Android's app-switch delay after Home), which left the Amazon home on screen that long.
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

    /** Picture of the menu that WiiMenuActivity saves (cacheDir) and the cover shows. */
    static final String COVER_FILE = "menu-cover.jpg";
    /** The app-switch delay is 5 s; past this the cover goes even if the menu never arrived. */
    private static final long COVER_TIMEOUT_MS = 7000;

    private static volatile FireTvHomeService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable removeCover = this::removeCover;
    private View cover;
    private long lastLaunch;
    private boolean longPressed;
    /**
     * Set when wiiOS sends you to Fire TV Settings. Fire OS 7.7 answers android.settings.SETTINGS by
     * showing the Amazon home (HomeActivity_vNext) on its Settings row, so bouncing every Amazon home
     * window back to the menu made Settings flash white and close. Cleared by the next Home press or when the menu is back on screen.
     */
    private static volatile boolean amazonScreenAllowed;

    /** Leave the Amazon screens on top until the next Home press (call before opening Fire TV Settings). */
    static void allowAmazonUntilHome() {
        amazonScreenAllowed = true;
    }

    /** The menu is showing again (WiiMenuActivity.onResume): cover the Amazon home again from now on. */
    static void coverAmazonHome() {
        amazonScreenAllowed = false;
    }

    /** The menu has drawn itself (WiiMenuActivity): take the picture of it away. */
    static void menuShown() {
        FireTvHomeService s = instance;
        if (s != null) s.main.post(s.removeCover);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onDestroy() {
        removeCover();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // While the picture is up the Amazon home sits underneath it: keys pressed blind would drive it.
        if (cover != null && event.getKeyCode() != KeyEvent.KEYCODE_HOME) return true;
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                longPressed = false;
            } else if (!longPressed && event.isLongPress()) {
                longPressed = true;
                openSettings();
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled() && !longPressed) {
            amazonScreenAllowed = false;
            openMenu();
        }
        // Swallow both down and up so Fire OS never sees Home.
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getPackageName() != null
                && AMAZON_LAUNCHER.contentEquals(event.getPackageName())
                && !amazonScreenAllowed
                && !isAmazonSettings(event.getClassName())) {
            openMenu();
        }
    }

    /**
     * Fire OS answers android.settings.SETTINGS with the Amazon launcher's own SettingsActivity
     * (com.amazon.tv.launcher/.ui.SettingsActivity on Fire OS 7.7). Bouncing that window back to the
     * menu made Settings, Accounts and Sign-in, and hold-Home all look like they did nothing.
     */
    static boolean isAmazonSettings(CharSequence className) {
        return className != null && className.toString().contains("Settings");
    }

    private void openMenu() {
        long now = SystemClock.uptimeMillis();
        if (now - lastLaunch < DEBOUNCE_MS) return;
        lastLaunch = now;
        showCover();
        startActivity(new Intent(this, WiiMenuActivity.class)
                // No transition: every millisecond of animation here is time the Amazon home stays on screen.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    /**
     * Shows the saved picture of the menu over everything, straight away. Accessibility overlays are not
     * subject to the app-switch delay that holds the real menu back after a Home press.
     */
    private void showCover() {
        if (cover != null) {
            main.removeCallbacks(removeCover);
            main.postDelayed(removeCover, COVER_TIMEOUT_MS);
            return;
        }
        File f = new File(getCacheDir(), COVER_FILE);
        if (!f.isFile()) return;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        if (b == null) return;
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.FIT_XY);
        v.setImageBitmap(b);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE);
        try {
            ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).addView(v, lp);
            cover = v;
            main.postDelayed(removeCover, COVER_TIMEOUT_MS);
        } catch (RuntimeException e) {
            // No overlay (service not bound yet, window rejected): the menu still opens, just later.
        }
    }

    private void removeCover() {
        main.removeCallbacks(removeCover);
        View v = cover;
        if (v == null) return;
        cover = null;
        try {
            ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).removeView(v);
        } catch (RuntimeException ignored) {
        }
    }

    private void openSettings() {
        allowAmazonUntilHome();
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
