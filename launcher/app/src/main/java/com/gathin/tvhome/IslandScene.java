package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Random;

/**
 * The Island village theme: a cosy village look of our own (no game's characters, logos or art). Sky with
 * drifting clouds, rolling grass hills with flowers and apple trees, channels on cream signboards with a
 * wooden edge, the clock on a wooden sign, round cream buttons, and two butterflies over the grass.
 * The scenery is baked once; clouds and butterflies are computed from the clock each frame, with nothing
 * allocated per frame. Geometry in 1920 x 1080 reference px; s converts to screen px.
 */
final class IslandScene {
    static final int CREAM = 0xFFFFF9EA, CREAM_EDGE = 0xFFD9BF8A, CREAM_RIM = 0xFFE6D3A8, WOOD = 0xFFC68642,
            WOOD_DARK = 0xFF8B5A2B, SIGN_TEXT = 0xFFFFF8EC;
    /** The clock's wooden sign, centred under the channels. */
    static final float SIGN_L = 650f, SIGN_T = 828f, SIGN_R = 1270f, SIGN_B = 982f;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final Matrix matrix = new Matrix();

    IslandScene() {
        text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
    }

    // ---- baked scenery --------------------------------------------------------------------------

    static Bitmap backdrop(int w, int h, float s) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, 800f * s, 0xFF67BFEF, 0xFFC9EBFB, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        hill(c, p, s, 792f, 22f, 260f, 0.3f, 0xFF9CDB7B);
        hill(c, p, s, 846f, 18f, 190f, 1.7f, 0xFF76C95B);
        // A lighter rim along the near hill, where the sun catches it.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5f * s);
        p.setColor(0x66C8F2A8);
        Path rim = new Path();
        for (float x = 0; x <= 1920; x += 12) {
            float y = (846f + 18f * (float) Math.sin(x / 190f + 1.7f)) * s;
            if (x == 0) rim.moveTo(0, y);
            else rim.lineTo(x * s, y);
        }
        c.drawPath(rim, p);
        p.setStyle(Paint.Style.FILL);
        flowers(c, p, s);
        // In the open grass, clear of the channels above, the buttons and the clock's sign.
        tree(c, p, s, 545f, 955f, 0.6f);
        tree(c, p, s, 1395f, 960f, 0.56f);
        tree(c, p, s, 1530f, 945f, 0.66f);
        sign(c, p, s);
        return b;
    }

    private static void hill(Canvas c, Paint p, float s, float y0, float amp, float period, float phase, int colour) {
        Path hill = new Path();
        hill.moveTo(0, 1080f * s);
        for (float x = 0; x <= 1920; x += 12) hill.lineTo(x * s, (y0 + amp * (float) Math.sin(x / period + phase)) * s);
        hill.lineTo(1920f * s, 1080f * s);
        hill.close();
        p.setColor(colour);
        c.drawPath(hill, p);
    }

    private static void flowers(Canvas c, Paint p, float s) {
        Random rnd = new Random(5);
        int[] petals = {0xFFFF9EC4, 0xFFFFFFFF, 0xFFFFE066, 0xFFFFB3D9, 0xFFB9E3FF};
        for (int i = 0; i < 34; i++) {
            float x = rnd.nextFloat() * 1920f, y = 880f + rnd.nextFloat() * 180f;
            if (x > SIGN_L - 20 && x < SIGN_R + 20 && y < SIGN_B + 30) continue;
            float rr = 5f + rnd.nextFloat() * 3f;
            p.setColor(petals[i % petals.length]);
            for (int k = 0; k < 5; k++) {
                double a = k * Math.PI * 2 / 5;
                c.drawCircle((x + (float) Math.cos(a) * rr) * s, (y + (float) Math.sin(a) * rr) * s, rr * 0.75f * s, p);
            }
            p.setColor(i % 2 == 0 ? 0xFFFFC233 : 0xFFF08A24);
            c.drawCircle(x * s, y * s, rr * 0.6f * s, p);
        }
    }

    private static void tree(Canvas c, Paint p, float s, float x, float base, float k) {
        p.setColor(0xFF8B5A2B);
        c.drawRoundRect((x - 14f * k) * s, (base - 95f * k) * s, (x + 14f * k) * s, base * s, 6f * s, 6f * s, p);
        float cy = base - 140f * k;
        p.setColor(0xFFFFFFFF);
        p.setShader(new RadialGradient((x - 25f * k) * s, (cy - 30f * k) * s, 95f * k * s, 0xFF6CCB5F, 0xFF3E9E45,
                Shader.TileMode.CLAMP));
        c.drawCircle(x * s, cy * s, 72f * k * s, p);
        c.drawCircle((x - 52f * k) * s, (cy + 18f * k) * s, 48f * k * s, p);
        c.drawCircle((x + 54f * k) * s, (cy + 16f * k) * s, 50f * k * s, p);
        p.setShader(null);
        float[][] apples = {{-30, -20}, {22, -38}, {40, 10}, {-50, 28}, {5, 22}};
        for (float[] a : apples) {
            p.setColor(0xFFE53935);
            c.drawCircle((x + a[0] * k) * s, (cy + a[1] * k) * s, 9f * k * s, p);
            p.setColor(0xB3FFFFFF);
            c.drawCircle((x + a[0] * k - 3f * k) * s, (cy + a[1] * k - 3f * k) * s, 2.6f * k * s, p);
        }
    }

    /** The clock's wooden sign on two posts. */
    private static void sign(Canvas c, Paint p, float s) {
        p.setColor(WOOD_DARK);
        c.drawRect((SIGN_L + 70f) * s, (SIGN_B - 10f) * s, (SIGN_L + 96f) * s, 1040f * s, p);
        c.drawRect((SIGN_R - 96f) * s, (SIGN_B - 10f) * s, (SIGN_R - 70f) * s, 1040f * s, p);
        p.setColor(0x33000000);
        c.drawRoundRect((SIGN_L + 4f) * s, (SIGN_T + 10f) * s, (SIGN_R + 4f) * s, (SIGN_B + 10f) * s, 26f * s, 26f * s, p);
        p.setColor(0xFFFFFFFF);   // a shader takes the paint's alpha: opaque, not the shadow's
        p.setShader(new LinearGradient(0, SIGN_T * s, 0, SIGN_B * s, 0xFFD69455, 0xFFB8763A, Shader.TileMode.CLAMP));
        c.drawRoundRect(SIGN_L * s, SIGN_T * s, SIGN_R * s, SIGN_B * s, 26f * s, 26f * s, p);
        p.setShader(null);
        // Wood grain.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(3f * s);
        p.setColor(0x339C5E28);
        for (int i = 0; i < 4; i++) {
            float y = SIGN_T + 30f + i * 32f;
            c.drawLine((SIGN_L + 40f + (i % 2) * 60f) * s, y * s, (SIGN_R - 60f - (i % 3) * 40f) * s, y * s, p);
        }
        p.setStrokeWidth(6f * s);
        p.setColor(WOOD_DARK);
        c.drawRoundRect(SIGN_L * s, SIGN_T * s, SIGN_R * s, SIGN_B * s, 26f * s, 26f * s, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** A round cream button body, size x size, for the WiiOS and envelope buttons. */
    static Bitmap button(int size, float s) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = size / 2f, rr = WiiBase.BTN_RING * s;
        p.setColor(0x33000000);
        c.drawCircle(cx + 3f * s, cx + 6f * s, rr, p);
        p.setColor(0xFFFFFFFF);   // opaque before the shader (it takes the paint's alpha)
        p.setShader(new LinearGradient(0, cx - rr, 0, cx + rr, 0xFFFFFCF2, 0xFFF0E0BC, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cx, rr, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5f * s);
        p.setColor(CREAM_EDGE);
        c.drawCircle(cx, cx, rr - 2.5f * s, p);
        return b;
    }

    // ---- per frame ------------------------------------------------------------------------------

    /** Clouds drifting slowly to the right across the sky. */
    void clouds(Canvas c, float s, float t) {
        p.setColor(0xF2FFFFFF);
        for (int i = 0; i < 6; i++) {
            float span = 1920f + 500f;
            float x = ((i * 431f + t * (7f + i * 2.2f)) % span) - 250f;
            float y = 45f + (i * 157 % 5) * 46f;
            float k = 0.8f + (i % 3) * 0.25f;
            c.drawCircle(x * s, y * s, 34f * k * s, p);
            c.drawCircle((x + 40f * k) * s, (y - 14f * k) * s, 42f * k * s, p);
            c.drawCircle((x + 84f * k) * s, y * s, 32f * k * s, p);
            c.drawRoundRect((x - 30f * k) * s, y * s, (x + 112f * k) * s, (y + 30f * k) * s, 15f * k * s, 15f * k * s, p);
        }
    }

    /** Two butterflies fluttering over the grass. */
    void butterflies(Canvas c, float s, float t) {
        butterfly(c, s, t, 470f, 832f, 0xFFFF8FC7, 0f);
        butterfly(c, s, t, 1420f, 846f, 0xFFFFD54F, 2.4f);
    }

    private void butterfly(Canvas c, float s, float t, float cx, float cy, int colour, float off) {
        float x = cx + 90f * (float) Math.sin(t * 0.33f + off);
        float y = cy + 26f * (float) Math.sin(t * 0.9f + off * 1.7f);
        float flap = 0.25f + 0.75f * Math.abs((float) Math.sin(t * 9f + off));
        int save = c.save();
        c.translate(x * s, y * s);
        c.scale(s, s);
        p.setColor(colour);
        for (int side = -1; side <= 1; side += 2) {
            c.drawOval(side < 0 ? -15f * flap : 0, -12f, side < 0 ? 0 : 15f * flap, 3f, p);
            c.drawOval(side < 0 ? -11f * flap : 0, -1f, side < 0 ? 0 : 11f * flap, 11f, p);
        }
        p.setColor(0xFF5D4037);
        c.drawRoundRect(-1.6f, -11f, 1.6f, 10f, 1.6f, 1.6f, p);
        c.restoreToCount(save);
    }

    /**
     * One channel as a cream signboard with a wooden edge; `art` (the channel's banner, custom picture, or icon
     * and name, at the inner size) fills its face. Null art is an empty sign. `lift` is the focus glow (0..1).
     */
    void sign(Canvas c, float s, float w, float h, BitmapShader art, float lift, int accent) {
        float rad = 30f * s, edge = 10f * s;
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        if (art == null) {
            p.setColor(0xB3FFF9EA);
            c.drawRoundRect(0, 0, w, h - edge, rad, rad, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f * s);
            p.setColor(0x99E6D3A8);
            c.drawRoundRect(0, 0, w, h - edge, rad, rad, p);
            p.setStyle(Paint.Style.FILL);
            return;
        }
        if (lift > 0.01f) {
            p.setStyle(Paint.Style.STROKE);
            for (int k = 3; k >= 1; k--) {
                p.setColor(accent);
                p.setAlpha(Math.round(lift * 70 / k));
                p.setStrokeWidth((8f + k * 6f) * s);
                c.drawRoundRect(-4f * s, -4f * s, w + 4f * s, h + 2f * s, rad + 4f * s, rad + 4f * s, p);
            }
            p.setStyle(Paint.Style.FILL);
        }
        p.setColor(CREAM_EDGE);
        c.drawRoundRect(0, edge, w, h, rad, rad, p);
        p.setColor(CREAM);
        c.drawRoundRect(0, 0, w, h - edge, rad, rad, p);
        float inset = innerInset(s);
        r.set(inset, inset, w - inset, h - edge - inset);
        matrix.setTranslate(inset, inset);
        art.setLocalMatrix(matrix);
        p.setShader(art);
        c.drawRoundRect(r, rad - inset * 0.6f, rad - inset * 0.6f, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f * s);
        p.setColor(CREAM_RIM);
        c.drawRoundRect(0, 0, w, h - edge, rad, rad, p);
        if (lift > 0.01f) {
            p.setStrokeWidth(6f * s);
            p.setColor(accent);
            p.setAlpha(Math.round(255 * Math.min(1f, lift)));
            c.drawRoundRect(-4f * s, -4f * s, w + 4f * s, h + 2f * s, rad + 4f * s, rad + 4f * s, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    static float innerInset(float s) {
        return 12f * s;
    }

    /** Size of a sign's face, for the channel art. */
    static int innerW(float w, float s) {
        return Math.max(1, Math.round(w - 2 * innerInset(s)));
    }

    static int innerH(float h, float s) {
        return Math.max(1, Math.round(h - 10f * s - 2 * innerInset(s)));
    }

    /** The time and date on the wooden sign. */
    void clock(Canvas c, float s, String time, String ampm, String date) {
        float cx = (SIGN_L + SIGN_R) / 2f;
        text.setTextSize(78f * s);
        float tw = text.measureText(time);
        text.setTextSize(36f * s);
        float aw = ampm.isEmpty() ? 0f : text.measureText(ampm) + 10f * s;
        float left = cx * s - (tw + aw) / 2f;
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(0x55000000);
        text.setTextSize(78f * s);
        c.drawText(time, left + 2f * s, 912f * s + 3f * s, text);
        text.setColor(SIGN_TEXT);
        c.drawText(time, left, 912f * s, text);
        if (!ampm.isEmpty()) {
            text.setTextSize(36f * s);
            c.drawText(ampm, left + tw + 10f * s, 912f * s, text);
        }
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(36f * s);
        text.setColor(0x55000000);
        c.drawText(date, cx * s + 2f * s, 958f * s + 2f * s, text);
        text.setColor(SIGN_TEXT);
        c.drawText(date, cx * s, 958f * s, text);
    }
}
