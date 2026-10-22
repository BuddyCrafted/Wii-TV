package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;

/** The Mii channel, opened from its tile in the channel menu; Back from the plaza returns there. */
public class MiiChannelActivity extends Activity {

    /** Open straight into making a new Mii, and return its id (EXTRA_MII_ID) when it's saved. */
    static final String EXTRA_MAKE = "make_mii", EXTRA_MII_ID = "mii_id";

    private MiiChannelView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new MiiChannelView(this, () -> {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
        setContentView(view);
        view.requestFocus();
        if (getIntent().getBooleanExtra(EXTRA_MAKE, false)) {
            // A profile is asking for its Mii: straight to making one, and back with it once saved.
            view.post(() -> view.makeOne(m -> {
                if (m != null) setResult(RESULT_OK, new android.content.Intent().putExtra(EXTRA_MII_ID, m.id));
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        view.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        view.release();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (view.keyDown(keyCode, event.getRepeatCount(), event.getUnicodeChar())) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (view.keyUp(keyCode)) return true;
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // Back arrives through onKeyDown; this only catches a system back the view never saw.
        view.keyDown(KeyEvent.KEYCODE_BACK, 0, 0);
    }
}
