package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * A profile's character: a round, friendly head-and-shoulders figure of our own (not any console's
 * characters). A look is packed into one int: skin tone, hair colour, hair style and shirt colour, each an
 * index into the tables below, so it is stored as a single preference.
 */
final class Avatar {
    private Avatar() {}

    static final int[] SKIN = {0xFFFBE0C8, 0xFFF2C9A0, 0xFFE0AC7E, 0xFFC68A5E, 0xFF9C6644, 0xFF6B4430};
    static final int[] HAIR = {0xFF2B1D14, 0xFF5A3A22, 0xFF9A6233, 0xFFE3B45E, 0xFFB7412B, 0xFF8F8F8F, 0xFF3F5BD6};
    static final int STYLES = 5;   // short, long, spiky, bun, none
    static final int[] SHIRT = {0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFDD835, 0xFF8E24AA, 0xFFFF7043,
            0xFF26A69A, 0xFF37474F};

    static final int FIELDS = 4;
    private static final int[] SIZES = {SKIN.length, HAIR.length, STYLES, SHIRT.length};

    static int get(int look, int field) {
        int v = look;
        for (int i = 0; i < field; i++) v /= 16;
        return Math.floorMod(v % 16, SIZES[field]);
    }

    static int with(int look, int field, int value) {
        int mul = 1;
        for (int i = 0; i < field; i++) mul *= 16;
        int old = (look / mul) % 16;
        return look + (Math.floorMod(value, SIZES[field]) - old) * mul;
    }

    static int count(int field) {
        return SIZES[field];
    }

    /** A different starting look for each new profile. */
    static int defaultLook(int id) {
        int look = 0;
        look = with(look, 0, id * 2 + 1);
        look = with(look, 1, id * 3);
        look = with(look, 2, id);
        return with(look, 3, id + 1);
    }

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PATH = new Path();
    private static final RectF R = new RectF();

    /**
     * Draws the character with its head centred at (cx, cy), head radius r; the shoulders reach about 2.2 r
     * below. `blink` 0..1 closes the eyes.
     */
    static void draw(Canvas c, float cx, float cy, float r, int look, float blink) {
        Paint p = P;
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        int skin = SKIN[get(look, 0)], hair = HAIR[get(look, 1)], style = get(look, 2), shirt = SHIRT[get(look, 3)];
        // Long hair falls behind the shoulders.
        if (style == 1) {
            p.setColor(hair);
            c.drawRoundRect(cx - 1.08f * r, cy - 0.6f * r, cx + 1.08f * r, cy + 1.25f * r, 0.5f * r, 0.5f * r, p);
        }
        // Shoulders and shirt, with a round neckline.
        p.setColor(shirt);
        R.set(cx - 1.55f * r, cy + 0.82f * r, cx + 1.55f * r, cy + 3.2f * r);
        c.drawRoundRect(R, 0.9f * r, 0.9f * r, p);
        p.setColor(0x26000000);
        c.drawArc(cx - 0.5f * r, cy + 0.5f * r, cx + 0.5f * r, cy + 1.25f * r, 0f, 180f, true, p);
        p.setColor(skin);
        c.drawArc(cx - 0.42f * r, cy + 0.45f * r, cx + 0.42f * r, cy + 1.12f * r, 0f, 180f, true, p);
        // Head and ears.
        c.drawCircle(cx - 0.98f * r, cy + 0.05f * r, 0.2f * r, p);
        c.drawCircle(cx + 0.98f * r, cy + 0.05f * r, 0.2f * r, p);
        c.drawCircle(cx, cy, r, p);
        // Hair on top.
        p.setColor(hair);
        switch (style) {
            case 0: case 1:
                c.drawArc(cx - 1.04f * r, cy - 1.08f * r, cx + 1.04f * r, cy + 0.75f * r, 180f, 180f, true, p);
                PATH.reset();
                PATH.moveTo(cx - 1.04f * r, cy - 0.17f * r);
                PATH.quadTo(cx - 0.2f * r, cy - 0.15f * r, cx + 0.35f * r, cy - 0.62f * r);
                PATH.quadTo(cx + 0.7f * r, cy - 0.2f * r, cx + 1.04f * r, cy - 0.17f * r);
                PATH.lineTo(cx + 1.04f * r, cy - 0.3f * r);
                PATH.lineTo(cx - 1.04f * r, cy - 0.3f * r);
                PATH.close();
                c.drawPath(PATH, p);
                break;
            case 2:
                // A cap of hair with spikes standing up out of it.
                c.drawArc(cx - 1.04f * r, cy - 1.0f * r, cx + 1.04f * r, cy + 0.6f * r, 180f, 180f, true, p);
                PATH.reset();
                PATH.moveTo(cx - 0.95f * r, cy - 0.45f * r);
                for (int i = 0; i < 5; i++) {
                    float x0 = cx - 0.95f * r + i * (1.9f * r / 5f), x1 = x0 + 1.9f * r / 5f;
                    float lift = i == 2 ? 1.45f : i % 2 == 0 ? 1.15f : 1.32f;
                    PATH.lineTo((x0 + x1) / 2f, cy - lift * r);
                    PATH.lineTo(x1, cy - 0.55f * r);
                }
                PATH.lineTo(cx + 0.95f * r, cy - 0.3f * r);
                PATH.lineTo(cx - 0.95f * r, cy - 0.3f * r);
                PATH.close();
                c.drawPath(PATH, p);
                break;
            case 3:
                c.drawCircle(cx, cy - 1.1f * r, 0.42f * r, p);
                c.drawArc(cx - 1.03f * r, cy - 1.05f * r, cx + 1.03f * r, cy + 0.6f * r, 180f, 180f, true, p);
                break;
            default:
                break;
        }
        // Face: eyes, rosy cheeks and a smile.
        p.setColor(0xFF2B2B2B);
        float eh = 0.17f * r * (1f - 0.9f * blink);
        R.set(cx - 0.42f * r, cy + 0.08f * r - eh / 2, cx - 0.26f * r, cy + 0.08f * r + eh / 2);
        c.drawOval(R, p);
        R.set(cx + 0.26f * r, cy + 0.08f * r - eh / 2, cx + 0.42f * r, cy + 0.08f * r + eh / 2);
        c.drawOval(R, p);
        p.setColor(0x40FF5A5A);
        c.drawCircle(cx - 0.58f * r, cy + 0.36f * r, 0.15f * r, p);
        c.drawCircle(cx + 0.58f * r, cy + 0.36f * r, 0.15f * r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(0.07f * r);
        p.setColor(0xFF7A3B2E);
        c.drawArc(cx - 0.26f * r, cy + 0.18f * r, cx + 0.26f * r, cy + 0.55f * r, 20f, 140f, false, p);
        p.setStyle(Paint.Style.FILL);
    }
}
