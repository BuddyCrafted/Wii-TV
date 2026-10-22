package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.WindowManager;

import java.io.File;

/**
 * System Update, full screen in the menu's style instead of a toast over the menu: downloads the release with
 * a progress bar, then hands it to the package installer. When the install goes through, Android replaces the
 * app and UpdateReceiver opens the new menu. If the install is cancelled or fails, OK or Back returns to the
 * menu. EXTRA_URL is the APK, EXTRA_BUILD the release's build number.
 */
public class UpdateActivity extends Activity {
    static final String EXTRA_URL = "url", EXTRA_BUILD = "build";

    private UpdateView view;
    private boolean installing, finished;
    private long committedAt;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Theme theme = Theme.load(Profiles.currentPrefs(this));
        view = new UpdateView(this, Theme.argb(theme.accent));
        setContentView(view);
        int build = getIntent().getIntExtra(EXTRA_BUILD, 0);
        String url = getIntent().getStringExtra(EXTRA_URL);
        String footer = getString(R.string.upd_from_to, BuildConfig.WIIOS_BUILD, build);
        show(R.string.upd_downloading, 0f, footer);
        if (url == null) {
            fail(getString(R.string.upd_failed, ""));
            return;
        }
        Context app = getApplicationContext();
        new Thread(() -> {
            try {
                File apk = new File(app.getCacheDir(), "update.apk");
                Updater.download(url, apk, f -> runOnUiThread(() -> show(R.string.upd_downloading, f < 0 ? 0.5f : f, footer)));
                runOnUiThread(() -> {
                    installing = true;
                    committedAt = SystemClock.uptimeMillis();
                    show(R.string.upd_installing, -1f, footer);
                });
                Updater.commit(app, apk, app.getPackageName());
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> fail(getString(R.string.upd_failed, msg)));
            }
        }, "wii-update").start();
    }

    private void show(int status, float progress, String footer) {
        view.set(getString(R.string.upd_title), getString(status), getString(R.string.shop_dont_turn_off), footer,
                progress, true);
    }

    private void fail(String message) {
        finished = true;
        view.set(getString(R.string.upd_title), message, getString(R.string.shop_press_back), "", -1f, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.start();
        // Back from Android's Install confirmation without the app being replaced: it was cancelled.
        if (installing && !finished && SystemClock.uptimeMillis() - committedAt > 3000) {
            fail(getString(R.string.upd_failed, ""));
        }
    }

    @Override
    protected void onPause() {
        view.stop();
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.onKeyDown(keyCode, event);
        }
        // While downloading or installing, keys do nothing: the update finishes by itself.
        if (finished) finish();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (finished) finish();
    }
}
