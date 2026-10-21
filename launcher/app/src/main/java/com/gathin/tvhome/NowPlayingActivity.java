package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

/**
 * The Now Playing screensaver as a screen of its own: Settings > Screensaver & sleep > Preview, and the
 * "Music screen" a listener can open on purpose. Back leaves; any other key brings it back from dark.
 */
public class NowPlayingActivity extends Activity {
    static final String KEY_DARK = "dream_dark_minutes";

    private NowPlayingView view;

    static long darkAfterMs(Context c) {
        int min = c.getSharedPreferences(AppLanguage.PREFS, MODE_PRIVATE).getInt(KEY_DARK, 0);
        return min * 60_000L;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new NowPlayingView(this, darkAfterMs(this));
        setContentView(view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.start();
    }

    @Override
    protected void onPause() {
        view.stop();
        super.onPause();
    }

    private static boolean isMediaKey(int k) {
        switch (k) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            finish();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE || isMediaKey(keyCode)) {
            return super.onKeyDown(keyCode, event);   // volume and play/pause still reach the system
        }
        view.wake();
        return true;
    }
}
