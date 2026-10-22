package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.KeyEvent;

import io.nayuki.qrcodegen.QrCode;

/**
 * Settings > Phone remote > Connect a phone: a QR code to scan with the phone's camera, which opens the
 * remote page already paired (the PIN rides in the address after #), plus the address and code to type in
 * by hand. Back or OK closes it.
 */
public class PhoneRemoteActivity extends Activity {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RemoteServer.start(this);
        Theme theme = Theme.load(Profiles.currentPrefs(this));
        String url = RemoteServer.url();
        String pin = RemoteServer.pin(this);
        View v = new View(this, Theme.argb(theme.accent), url, pin,
                getString(R.string.remote_title), getString(url == null ? R.string.remote_no_network : R.string.remote_scan),
                url == null ? "" : getString(R.string.remote_or_type, url, pin), getString(R.string.remote_same_wifi));
        setContentView(v);
        v.start();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    static final class View extends SaverView {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int accent;
        private final String title, line1, line2, line3;
        /** The QR code's modules (true = dark), or null without a network. */
        private final boolean[][] qr;
        private LinearGradient bg;

        View(Context c, int accent, String url, String pin, String title, String line1, String line2, String line3) {
            super(c);
            this.accent = accent;
            this.title = title;
            this.line1 = line1;
            this.line2 = line2;
            this.line3 = line3;
            text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            text.setTextAlign(Paint.Align.CENTER);
            qr = url == null ? null : modules(url + "/#" + pin);
        }

        static boolean[][] modules(String data) {
            QrCode code = QrCode.encodeText(data, QrCode.Ecc.MEDIUM);
            boolean[][] m = new boolean[code.size][code.size];
            for (int y = 0; y < code.size; y++) {
                for (int x = 0; x < code.size; x++) m[y][x] = code.getModule(x, y);
            }
            return m;
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
            c.drawText(title, 960f * s, 170f * s, text);
            p.setColor(0xFFC4C4C4);
            c.drawRect(240f * s, 210f * s, 1680f * s, 213f * s, p);
            if (qr != null) {
                float size = 470f, x = 960f - size / 2f, y = 260f;
                // A soft glow round the card, breathing slowly.
                p.setColor(accent);
                p.setAlpha((int) (40 + 25 * Math.sin(t * 2.0)));
                c.drawRoundRect((x - 24f) * s, (y - 24f) * s, (x + size + 24f) * s, (y + size + 24f) * s, 40f * s, 40f * s, p);
                p.setAlpha(255);
                p.setColor(0xFFFFFFFF);
                c.drawRoundRect((x - 12f) * s, (y - 12f) * s, (x + size + 12f) * s, (y + size + 12f) * s, 30f * s, 30f * s, p);
                // Whole-pixel squares with a four-module quiet zone, so phones read it easily.
                int n = qr.length;
                int cell = Math.max(1, (int) (size * s / (n + 8)));
                int ox = Math.round((960f * s) - cell * n / 2f), oy = Math.round((y + size / 2f) * s - cell * n / 2f);
                p.setColor(0xFF222222);
                for (int my = 0; my < n; my++) {
                    for (int mx = 0; mx < n; mx++) {
                        if (qr[my][mx]) c.drawRect(ox + mx * cell, oy + my * cell, ox + (mx + 1) * cell, oy + (my + 1) * cell, p);
                    }
                }
            }
            text.setColor(0xFF4A4A4A);
            text.setTextSize(44f * s);
            c.drawText(line1, 960f * s, 830f * s, text);
            text.setColor(0xFF6A6A6A);
            text.setTextSize(36f * s);
            c.drawText(line2, 960f * s, 900f * s, text);
            text.setColor(0xFF9A9A9A);
            text.setTextSize(32f * s);
            c.drawText(line3, 960f * s, 1010f * s, text);
        }
    }
}
