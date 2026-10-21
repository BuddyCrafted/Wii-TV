package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.KeyEvent;

/**
 * The Forecast Channel. Opens on the cached report and refreshes it, and the world cities, when they are
 * older than half an hour. The globe's Earth image is downloaded once, then kept.
 */
public class ForecastActivity extends Activity {
    private ForecastView view;
    private Globe globe;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new ForecastView(this, this::finish);
        setContentView(view);
        view.setReport(Weather.cached(this));
        view.setWorld(Weather.world(this));
        loadGlobe();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Weather.fresh(this)) load();
        if (!Weather.worldFresh(this)) Weather.refreshWorld(this, (w, e) -> {
            if (!isFinishing()) view.setWorld(w);
        });
    }

    private void load() {
        view.setStatus(getString(R.string.wx_loading));
        Weather.refresh(this, (r, error) -> {
            if (isFinishing()) return;
            if (r != null) {
                view.setReport(r);
                view.setStatus("");
            } else {
                view.setStatus(getString(R.string.wx_offline));
            }
        });
    }

    private void loadGlobe() {
        boolean low = DeviceProfile.lowMemory(this);
        Context app = getApplicationContext();
        new Thread(() -> {
            if (!Globe.fetch(app)) return;
            Globe g = low ? new Globe(640, 360) : new Globe(960, 540);
            if (!g.load(app, low)) return;
            // A small copy of the map for the boards' faint backdrop.
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 4;
            Bitmap map = BitmapFactory.decodeFile(Globe.file(app).getPath(), o);
            runOnUiThread(() -> {
                if (!isFinishing()) view.setGlobe(g, map);
            });
            globe = g;
        }, "wii-globe").start();
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
}
