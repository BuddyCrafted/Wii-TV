package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
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
    private static final long COVER_TIMEOUT_MS = 8000;
    /**
     * While the picture is up, ask for the menu again this often, at most RETRIES times. Fire OS does not drop
     * a start held by the app-switch delay, it queues it and runs every queued start when the delay ends: asking
     * every 250 ms piled up 20-30 starts of the menu, which then ran back to back and froze a 1 GB stick for
     * seconds after each Home press. The retries are only there for a start that really was lost (a second Home
     * press clears the queue).
     */
    private static final long RETRY_MS = 1500;
    private static final int RETRIES = 3;
    /** Amazon home windows this soon after opening Settings are its Settings row, not a Home press. */
    private static final long SETTINGS_GRACE_MS = 4000;

    private static volatile FireTvHomeService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable removeCover = this::removeCover;
    private final Runnable retry = this::retryMenu;
    /** The menu is on screen (resumed and drawn); Amazon window events then mean nothing. */
    private static volatile boolean menuVisible;
    /** When the menu last drew itself, and when the Amazon home last came up (uptime ms). */
    private static volatile long menuShownAt;
    private volatile long amazonAt;

    /**
     * Fire OS can switch to its home while the screen is off (sleep, or a long stretch without a key press). The
     * menu can't come up then, and on waking the Amazon home is already there, so no new window event arrives.
     * So on wake (screen on, unlock, screensaver ending), the menu comes back if the Amazon home appeared after
     * the menu was last on screen.
     */
    private final BroadcastReceiver wake = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            main.removeCallbacks(checkWake);
            main.postDelayed(checkWake, 500);
        }
    };
    private final Runnable checkWake = () -> {
        if (menuVisible || amazonScreenAllowed || amazonAt <= menuShownAt) return;
        lastLaunch = 0;
        openMenu();
    };
    private static volatile long settingsOpenedAt;
    private View cover;
    /** The menu picture, decoded ahead of time so it can go up the instant the Amazon home appears. */
    private volatile Bitmap coverBitmap;
    private long lastLaunch;
    private int retriesLeft;
    private boolean longPressed;
    /**
     * Set when WiiOS sends you to Fire TV Settings. Fire OS 7.7 answers android.settings.SETTINGS by
     * showing the Amazon home (HomeActivity_vNext) on its Settings row, so bouncing every Amazon home
     * window back to the menu made Settings flash white and close. Cleared by the next Home press or when the menu is back on screen.
     */
    private static volatile boolean amazonScreenAllowed;

    /** Leave the Amazon screens on top until the next Home press (call before opening Fire TV Settings). */
    static void allowAmazonUntilHome() {
        amazonScreenAllowed = true;
        settingsOpenedAt = SystemClock.uptimeMillis();
    }

    /**
     * Set when WiiOS opens an Appstore page (Shop or Games, "Get it from the store"). On Fire OS those pages and
     * the store search are the Amazon launcher's own windows, so they were covered by the menu at once and the
     * store never seemed to open. During the visit only the Amazon home screen itself (Home pressed) brings the
     * menu back; Back returns to WiiOS's Shop as usual.
     */
    private static volatile boolean storeVisit;
    private static volatile long storeOpenedAt;
    /** Launcher windows this soon after opening a store page belong to it, whatever they are. */
    private static final long STORE_GRACE_MS = 3000;

    /** Leave the Amazon store pages on top until the Amazon home screen shows (call before opening one). */
    static void allowAmazonStore() {
        storeVisit = true;
        storeOpenedAt = SystemClock.uptimeMillis();
    }

    /** The Amazon home screen itself, as opposed to the launcher's store, search or Settings windows. */
    static boolean isAmazonHome(CharSequence className) {
        return className != null && className.toString().contains("HomeActivity");
    }

    /** The menu is showing again (WiiMenuActivity.onResume): cover the Amazon home again from now on. */
    static void coverAmazonHome() {
        amazonScreenAllowed = false;
        storeVisit = false;
    }

    /** The menu has drawn itself (WiiMenuActivity): take the picture of it away. */
    static void menuShown() {
        menuVisible = true;
        menuShownAt = SystemClock.uptimeMillis();
        FireTvHomeService s = instance;
        if (s != null) s.main.post(s.removeCover);
    }

    /** The menu went to the background (WiiMenuActivity.onPause). */
    static void menuHidden() {
        menuVisible = false;
    }

    /** WiiMenuActivity saved a new picture of the menu: decode it now, off the main thread. */
    static void coverSaved() {
        FireTvHomeService s = instance;
        if (s != null) s.loadCover();
    }

    private void loadCover() {
        File f = new File(getCacheDir(), COVER_FILE);
        if (!f.isFile()) return;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        if (b != null) coverBitmap = b;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        RemoteServer.attach(this);
        instance = this;
        new Thread(this::loadCover, "menu-cover-load").start();
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_DREAMING_STOPPED);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wake, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(wake, f);
        }
    }

    @Override
    public void onDestroy() {
        RemoteServer.detach(this);
        try {
            unregisterReceiver(wake);
        } catch (IllegalArgumentException ignored) {
            // never registered (the service was not connected)
        }
        main.removeCallbacks(checkWake);
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

    /**
     * On Fire OS 7.7 the remote's Home button never reaches onKeyEvent: Fire OS handles it first. So the
     * Amazon home showing up is the only sign of a Home press, and pressing Home while it is already in
     * front only changes its content. Both kinds of event bring the menu back, unless you are in Settings.
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int type = event.getEventType();
        if ((type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                || event.getPackageName() == null || !AMAZON_LAUNCHER.contentEquals(event.getPackageName())
                || menuVisible || isAmazonSettings(event.getClassName())) {
            return;
        }
        if (storeVisit) {
            if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    || SystemClock.uptimeMillis() - storeOpenedAt < STORE_GRACE_MS
                    || !isAmazonHome(event.getClassName())) {
                return;
            }
            storeVisit = false;
        }
        if (amazonScreenAllowed) {
            // Moving along the Settings row only changes content. A new Amazon home window after the grace
            // period is Home pressed (or Back out of a Settings page): Settings is over, back to the menu.
            if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    || SystemClock.uptimeMillis() - settingsOpenedAt < SETTINGS_GRACE_MS) {
                return;
            }
            amazonScreenAllowed = false;
        }
        amazonAt = SystemClock.uptimeMillis();
        // Screen off: nothing can come to the front now; the wake check brings the menu back.
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) return;
        openMenu();
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
        // The Fire TV home itself is the chosen system: leave it be.
        if (!Systems.wiiosActive(this)) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastLaunch < DEBOUNCE_MS) return;
        // Already covering this Home press: its start is queued, and more Amazon events must not add starts.
        if (cover != null) return;
        lastLaunch = now;
        showCover();
        retriesLeft = RETRIES;
        startMenu();
        main.removeCallbacks(retry);
        main.postDelayed(retry, RETRY_MS);
    }

    private void startMenu() {
        startActivity(new Intent(this, WiiMenuActivity.class)
                // No transition: every millisecond of animation here is time the Amazon home stays on screen.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    /** Keeps asking for the menu while its picture is up and the real one has not arrived. */
    private void retryMenu() {
        if (menuVisible || cover == null || amazonScreenAllowed || retriesLeft <= 0) return;
        retriesLeft--;
        startMenu();
        main.postDelayed(retry, RETRY_MS);
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
        Bitmap b = coverBitmap;
        if (b == null) {
            loadCover();
            b = coverBitmap;
        }
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
        main.removeCallbacks(retry);
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
