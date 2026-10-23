package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.List;

/**
 * "Choose a system": WiiOS, the TV's own home and any other home screen, as cards in the menu's style. Shown
 * once the first time, then from WiiOS Settings > Change system and its own tile in the TV's app row, so
 * there's always a way back. If set to show at every power-on, it counts down and starts the current system
 * by itself; any key stops the countdown.
 */
public class SystemPickerActivity extends Activity {
    /** At power-on (when asked for): count down and start the current system by itself. */
    static final String EXTRA_COUNTDOWN = "countdown";
    /** The one-time first choice: says where to change it later. */
    static final String EXTRA_FIRST = "first";

    private View view;
    private List<Systems.Sys> systems;
    private WiiSounds sounds;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        systems = Systems.list(this);
        Theme theme = Theme.load(Profiles.currentPrefs(this));
        sounds = new WiiSounds(this, Profiles.main(this).getBoolean("sounds", getResources().getBoolean(R.bool.wii_sounds)));
        int focus = 0;
        String current = Systems.active(this);
        for (int i = 0; i < systems.size(); i++) if (systems.get(i).id.equals(current)) focus = i;
        int seconds = getIntent().getBooleanExtra(EXTRA_COUNTDOWN, false) ? Systems.seconds(this) : 0;
        PackageManager pm = getPackageManager();
        Drawable[] art = new Drawable[systems.size()];
        for (int i = 0; i < systems.size(); i++) {
            Systems.Sys s = systems.get(i);
            if (s.info == null) continue;
            art[i] = s.info.activityInfo.loadBanner(pm);
            if (art[i] == null) art[i] = s.info.activityInfo.applicationInfo.loadBanner(pm);
            if (art[i] == null) art[i] = s.info.loadIcon(pm);
        }
        boolean first = getIntent().getBooleanExtra(EXTRA_FIRST, false);
        view = new View(this, Theme.argb(theme.accent), systems, art, focus, seconds, Systems.active(this),
                getString(first ? R.string.sys_first_title : R.string.sys_title),
                getString(first ? R.string.sys_first_hint : R.string.sys_hint), getString(R.string.sys_current));
        setContentView(view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        view.stop();
    }

    @Override
    protected void onDestroy() {
        sounds.release();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        view.countdownStopped = true;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int next = Math.max(0, Math.min(systems.size() - 1, view.focus + (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
            if (next != view.focus) {
                view.focus = next;
                sounds.play(WiiSounds.MOVE);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            sounds.play(WiiSounds.SELECT);
            choose(systems.get(view.focus).id);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            // Back keeps whatever is running now (the first time: WiiOS, which is what's on screen).
            if (!Profiles.main(this).getBoolean("system_chosen", false)) Systems.setActive(this, Systems.WIIOS);
            sounds.play(WiiSounds.BACK);
            finish();
            return true;
        }
        return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN || super.onKeyDown(keyCode, event);
    }

    private void choose(String id) {
        Systems.setActive(this, id);
        Intent launch = Systems.launch(this, id);
        if (launch != null) {
            try {
                startActivity(launch);
            } catch (RuntimeException e) {
                Systems.setActive(this, Systems.WIIOS);
                launch = null;
            }
        }
        if (launch == null) {
            // WiiOS: back to the menu (also when opened from the TV's app row, with no menu behind).
            startActivity(new Intent(this, WiiMenuActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    static final class View extends SaverView {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int accent;
        private final List<Systems.Sys> systems;
        private final Drawable[] art;
        private final String defaultId, title, hint, defaultLabel;
        private final float[] lift;
        private final long started = SystemClock.uptimeMillis();
        private final int seconds;
        private LinearGradient bg;
        private final SystemPickerActivity owner;
        int focus;
        boolean countdownStopped;
        private boolean fired;

        View(SystemPickerActivity c, int accent, List<Systems.Sys> systems, Drawable[] art, int focus, int seconds,
             String defaultId, String title, String hint, String defaultLabel) {
            super(c);
            owner = c;
            this.accent = accent;
            this.systems = systems;
            this.art = art;
            this.focus = focus;
            this.seconds = seconds;
            this.defaultId = defaultId;
            this.title = title;
            this.hint = hint;
            this.defaultLabel = defaultLabel;
            lift = new float[systems.size()];
            countdownStopped = seconds <= 0;
            text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            text.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        void layout(int w, int h) {
            bg = new LinearGradient(0, 0, 0, h, 0xFFFFFFFF, 0xFFE6ECF0, Shader.TileMode.CLAMP);
        }

        @Override
        void frame(Canvas c, float t) {
            p.setColor(0xFFFFFFFF);
            p.setShader(bg);
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
            p.setColor(0x0F000000);
            for (float y = 0.7f; y < 1080f; y += 9.472f) c.drawRect(0, y * s, w, (y + 2.2f) * s, p);
            text.setColor(0xFF7C7C7C);
            text.setTextSize(62f * s);
            c.drawText(title, 960f * s, 200f * s, text);
            p.setColor(0xFFC4C4C4);
            c.drawRect(240f * s, 240f * s, 1680f * s, 243f * s, p);

            int n = systems.size();
            float cardW = Math.min(400f, (1760f - (n - 1) * 40f) / n), cardH = cardW * 0.6f, gap = 40f;
            float x0 = 960f - (n * cardW + (n - 1) * gap) / 2f;
            for (int i = 0; i < n; i++) {
                lift[i] += ((i == focus ? 1f : 0f) - lift[i]) * 0.25f;
                float l = x0 + i * (cardW + gap), top = 470f - 20f * lift[i];
                float r = l + cardW, b = top + cardH, rad = 28f;
                if (lift[i] > 0.02f) {
                    for (int k = 3; k >= 1; k--) {
                        p.setColor(accent);
                        p.setAlpha((int) (lift[i] * 60 / k));
                        float g = 6f + k * 8f;
                        c.drawRoundRect((l - g) * s, (top - g) * s, (r + g) * s, (b + g) * s, (rad + g) * s, (rad + g) * s, p);
                    }
                }
                p.setColor(0x22000000);
                c.drawRoundRect(l * s, (top + 8f) * s, r * s, (b + 8f) * s, rad * s, rad * s, p);
                p.setColor(0xFFFFFFFF);
                c.drawRoundRect(l * s, top * s, r * s, b * s, rad * s, rad * s, p);
                int save = c.save();
                android.graphics.Path clip = clipPath;
                clip.reset();
                clip.addRoundRect(l * s, top * s, r * s, b * s, rad * s, rad * s, android.graphics.Path.Direction.CW);
                c.clipPath(clip);
                if (systems.get(i).info == null) {
                    // WiiOS: the menu's stripes and wordmark.
                    p.setColor(0xFFF4F6F8);
                    c.drawRect(l * s, top * s, r * s, b * s, p);
                    p.setColor(0x14000000);
                    for (float y = top + 4f; y < b; y += 9.472f) c.drawRect(l * s, y * s, r * s, (y + 2.2f) * s, p);
                    text.setColor(0xFF8C8C8C);
                    text.setTextSize(cardH * 0.3f * s);
                    c.drawText("WiiOS", (l + cardW / 2f) * s, (top + cardH * 0.6f) * s, text);
                } else if (art[i] != null) {
                    Drawable d = art[i];
                    int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
                    boolean banner = iw > 0 && ih > 0 && iw > ih * 1.3f;
                    if (banner) {
                        float sc = Math.min(cardW / iw, cardH / ih);
                        float dw = iw * sc, dh = ih * sc;
                        d.setBounds(Math.round((l + (cardW - dw) / 2f) * s), Math.round((top + (cardH - dh) / 2f) * s),
                                Math.round((l + (cardW + dw) / 2f) * s), Math.round((top + (cardH + dh) / 2f) * s));
                    } else {
                        float size = cardH * 0.55f;
                        d.setBounds(Math.round((l + cardW / 2f - size / 2f) * s), Math.round((top + cardH * 0.12f) * s),
                                Math.round((l + cardW / 2f + size / 2f) * s), Math.round((top + cardH * 0.12f + size) * s));
                    }
                    d.draw(c);
                }
                c.restoreToCount(save);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth((i == focus ? 6f : 3f) * s);
                p.setColor(i == focus ? accent : 0xFFC9D6DE);
                c.drawRoundRect(l * s, top * s, r * s, b * s, rad * s, rad * s, p);
                p.setStyle(Paint.Style.FILL);
                text.setColor(i == focus ? 0xFF4A4A4A : 0xFF8A8A8A);
                text.setTextSize(40f * s);
                c.drawText(systems.get(i).label, (l + cardW / 2f) * s, (b + 70f) * s, text);
                if (systems.get(i).id.equals(defaultId)) {
                    text.setColor(0xFF9A9A9A);
                    text.setTextSize(28f * s);
                    c.drawText(defaultLabel, (l + cardW / 2f) * s, (b + 112f) * s, text);
                }
            }

            text.setColor(0xFF9A9A9A);
            text.setTextSize(34f * s);
            String line = hint;
            if (!countdownStopped) {
                int left = (int) Math.ceil(seconds - (SystemClock.uptimeMillis() - started) / 1000f);
                if (left <= 0 && !fired) {
                    fired = true;
                    post(() -> owner.choose(systems.get(focus).id));
                }
                line = owner.getString(R.string.sys_starting, systems.get(focus).label, Math.max(0, left));
            }
            c.drawText(line, 960f * s, 1000f * s, text);
        }

        private final android.graphics.Path clipPath = new android.graphics.Path();
    }
}
