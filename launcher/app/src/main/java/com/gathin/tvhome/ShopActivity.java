package com.gathin.tvhome;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import java.io.File;

/**
 * The Shop Channel: browse the catalog, open an app's page, and get it. Google Play apps open their store
 * page (Play installs them); open-source apps download here with a progress screen and go to the package
 * installer, which shows Android's own Install confirmation.
 */
public class ShopActivity extends Activity implements ShopView.Listener {
    private ShopView view;
    private WiiSounds sounds;
    private WiiMusic music;
    private boolean busy;

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            view.refreshInstalled();
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new ShopView(this, this);
        setContentView(view);
        android.content.SharedPreferences prefs = Weather.prefs(this);
        sounds = new WiiSounds(this, prefs.getBoolean("sounds", getResources().getBoolean(R.bool.wii_sounds)));
        music = new WiiMusic(this, prefs.getBoolean("music", getResources().getBoolean(R.bool.wii_music)),
                MenuMusic.SHOP, "shop_music_v1.pcm");
        view.setCatalog(ShopCatalog.local(this));
        ShopCatalog.refresh(this, (c, e) -> {
            if (c != null && !isFinishing()) view.setCatalog(c);
        });
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(packages, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(packages, f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        music.resume();
        view.refreshInstalled();
    }

    @Override
    protected void onPause() {
        music.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        sounds.release();
        music.release();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (view.handleKey(keyCode)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        view.handleKey(KeyEvent.KEYCODE_BACK);
    }

    @Override
    public void onExit() {
        finish();
    }

    @Override
    public void onPlaySound(int which) {
        sounds.play(which);
    }

    @Override
    public void onLookup(ShopCatalog.App app) {
        new Thread(() -> {
            try {
                String[] r = ShopCatalog.resolve(app);
                runOnUiThread(() -> view.setLatestVersion(app, r[0]));
            } catch (Exception ignored) {
                // offline: the page simply shows no latest version
            }
        }, "wii-shop-lookup").start();
    }

    @Override
    public void onAction(ShopCatalog.App app, ShopView.Action action) {
        switch (action) {
            case OPEN: {
                Intent open = ShopCatalog.launchIntent(this, app.pkg);
                if (open != null) startActivity(open);
                break;
            }
            case STORE: {
                Intent store = ShopCatalog.storeIntent(this, app.pkg);
                if (store != null) startActivity(store);
                else Toast.makeText(this, R.string.shop_no_store, Toast.LENGTH_LONG).show();
                break;
            }
            default:
                download(app);
        }
    }

    private void download(ShopCatalog.App app) {
        if (busy) return;
        busy = true;
        view.showDownload(app, 0f, null);
        Context ctx = getApplicationContext();
        new Thread(() -> {
            String error = null;
            try {
                String[] r = ShopCatalog.resolve(app);
                File apk = new File(ctx.getCacheDir(), "shop.apk");
                Updater.download(r[1], apk, f -> runOnUiThread(() -> view.showDownload(app, f < 0 ? 0.5f : f, null)));
                runOnUiThread(() -> view.showDownload(app, 1f, null));
                Updater.commit(ctx, apk, app.pkg);
            } catch (Exception e) {
                error = getString(R.string.shop_failed, String.valueOf(e.getMessage()));
            }
            final String fe = error;
            runOnUiThread(() -> {
                busy = false;
                if (fe != null) view.showDownload(app, -1f, fe);
            });
        }, "wii-shop-download").start();
    }
}
