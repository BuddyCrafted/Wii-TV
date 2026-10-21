package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;

/** The Forecast Channel. Opens on the cached report and refreshes it when it is older than half an hour. */
public class ForecastActivity extends Activity {
    private ForecastView view;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new ForecastView(this);
        setContentView(view);
        view.setReport(Weather.cached(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Weather.fresh(this)) load();
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

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            finish();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            load();   // OK refreshes
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
