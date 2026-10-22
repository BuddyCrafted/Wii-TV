package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;

import java.util.Calendar;

/**
 * The menu's segment clock (h:mm and AM/PM) for the screensavers, with the same digit shapes as WiiMenuView.
 * Drawn with its top-left at (x, y); u is one reference px. 380 u wide, 80 u tall.
 */
final class SegmentClock {
    private static final int[] SEGMENTS = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x27, 0x7F, 0x6F};
    private static final float W = 50f, H = 79f, T = 12f, GAP = 2.5f;
    private static final float[] DIGIT_X = {0f, 61f, 158f, 219f};
    static final float WIDTH = 380f, HEIGHT = 80f;

    private final Path path = new Path();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float cornersFor = -1f;
    private CornerPathEffect corners;

    SegmentClock() {
        text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    }

    void draw(Canvas c, Paint fill, float x, float y, float u, boolean colon) {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR);
        if (hour == 0) hour = 12;
        int minute = now.get(Calendar.MINUTE);
        if (u != cornersFor) {
            corners = new CornerPathEffect(2.5f * u);
            cornersFor = u;
        }
        fill.setPathEffect(corners);
        if (hour >= 10) digit(c, fill, x + DIGIT_X[0] * u, y, u, hour / 10);
        digit(c, fill, x + DIGIT_X[1] * u, y, u, hour % 10);
        digit(c, fill, x + DIGIT_X[2] * u, y, u, minute / 10);
        digit(c, fill, x + DIGIT_X[3] * u, y, u, minute % 10);
        fill.setPathEffect(null);
        if (colon) {
            float d = 5.5f * u, cx = x + 134.5f * u;
            c.drawRoundRect(cx - d, y + 17f * u, cx + d, y + 28f * u, 2f * u, 2f * u, fill);
            c.drawRoundRect(cx - d, y + 50f * u, cx + d, y + 61f * u, 2f * u, 2f * u, fill);
        }
        text.setColor(fill.getColor());
        text.setTextSize(34f / 0.711f * u);
        c.drawText(now.get(Calendar.AM_PM) == Calendar.PM ? "PM" : "AM", x + 317f * u, y + 80f * u, text);
    }

    private void digit(Canvas c, Paint fill, float xa, float ya, float u, int d) {
        int bits = SEGMENTS[d];
        float t = T * u, g = GAP * u, k = t / 2f;
        float xb = xa + W * u, yb = ya + H * u, ym = ya + H * u / 2f;
        if ((bits & 0x01) != 0) poly(c, fill, xa + g, ya, xb - g, ya, xb - t - g, ya + t, xa + t + g, ya + t);
        if ((bits & 0x08) != 0) poly(c, fill, xa + t + g, yb - t, xb - t - g, yb - t, xb - g, yb, xa + g, yb);
        if ((bits & 0x20) != 0) poly(c, fill, xa, ya + g, xa + t, ya + t + g, xa + t, ym - k - g, xa + k, ym - g, xa, ym - k - g);
        if ((bits & 0x02) != 0) poly(c, fill, xb, ya + g, xb, ym - k - g, xb - k, ym - g, xb - t, ym - k - g, xb - t, ya + t + g);
        if ((bits & 0x10) != 0) poly(c, fill, xa, ym + k + g, xa + k, ym + g, xa + t, ym + k + g, xa + t, yb - t - g, xa, yb - g);
        if ((bits & 0x04) != 0) poly(c, fill, xb, ym + k + g, xb, yb - g, xb - t, yb - t - g, xb - t, ym + k + g, xb - k, ym + g);
        if ((bits & 0x40) != 0) {
            poly(c, fill, xa + k + g, ym, xa + t + g, ym - k, xb - t - g, ym - k, xb - k - g, ym, xb - t - g, ym + k, xa + t + g, ym + k);
        }
    }

    private void poly(Canvas c, Paint fill, float... p) {
        path.reset();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
        path.close();
        c.drawPath(path, fill);
    }
}
