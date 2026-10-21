package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/** Weather drawings for the Forecast Channel, in code like the rest of the menu: no image files. */
final class WeatherArt {
    private WeatherArt() {}

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** The backdrop: a sky gradient for the conditions and time of day. */
    static void sky(Canvas c, float w, float h, Weather.Sky sky, boolean day) {
        int top, bottom;
        if (!day) {
            top = 0xFF0B1A3A;
            bottom = 0xFF2B3F6E;
        } else {
            switch (sky) {
                case CLEAR: top = 0xFF1E88E5; bottom = 0xFF9ED8FF; break;
                case PARTLY: top = 0xFF3D8BD8; bottom = 0xFFB8DDF6; break;
                case STORM: top = 0xFF37414F; bottom = 0xFF7A8594; break;
                case SNOW: top = 0xFF8FA9C4; bottom = 0xFFE7EEF6; break;
                default: top = 0xFF6B86A3; bottom = 0xFFC3D0DD; break;
            }
        }
        P.setColor(0xFFFFFFFF);
        P.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, P);
        P.setShader(null);
    }

    /** The icon for a kind of sky, centred at (cx, cy), r ≈ half its width. */
    static void icon(Canvas c, float cx, float cy, float r, Weather.Sky sky, boolean day) {
        switch (sky) {
            case CLEAR:
                if (day) sun(c, cx, cy, r * 0.62f);
                else moon(c, cx, cy, r * 0.6f);
                break;
            case PARTLY:
                if (day) sun(c, cx + r * 0.28f, cy - r * 0.25f, r * 0.45f);
                else moon(c, cx + r * 0.3f, cy - r * 0.28f, r * 0.42f);
                cloud(c, cx - r * 0.1f, cy + r * 0.18f, r * 0.8f, 0xFFFFFFFF);
                break;
            case CLOUDY:
                cloud(c, cx + r * 0.2f, cy - r * 0.15f, r * 0.62f, 0xFFD5DBE2);
                cloud(c, cx - r * 0.08f, cy + r * 0.12f, r * 0.85f, 0xFFFFFFFF);
                break;
            case FOG:
                cloud(c, cx, cy - r * 0.15f, r * 0.8f, 0xFFE9ECEF);
                P.setColor(0xCCFFFFFF);
                P.setStrokeCap(Paint.Cap.ROUND);
                P.setStrokeWidth(r * 0.09f);
                for (int i = 0; i < 3; i++) {
                    float y = cy + r * (0.42f + i * 0.2f);
                    c.drawLine(cx - r * 0.75f + i * r * 0.1f, y, cx + r * 0.7f - i * r * 0.1f, y, P);
                }
                break;
            case DRIZZLE:
            case RAIN:
                cloud(c, cx, cy - r * 0.2f, r * 0.85f, sky == Weather.Sky.RAIN ? 0xFFE3E7EC : 0xFFF2F4F6);
                drops(c, cx, cy + r * 0.45f, r, sky == Weather.Sky.RAIN ? 4 : 3, 0xFF3FA9F5);
                break;
            case SNOW:
                cloud(c, cx, cy - r * 0.2f, r * 0.85f, 0xFFFFFFFF);
                flakes(c, cx, cy + r * 0.52f, r);
                break;
            default:
                cloud(c, cx, cy - r * 0.2f, r * 0.88f, 0xFFB9C1CB);
                bolt(c, cx, cy + r * 0.35f, r * 0.5f);
                break;
        }
    }

    /** The console's sun: a soft glow, a starburst of thin rays, and a bright disc. */
    private static void sun(Canvas c, float cx, float cy, float r) {
        P.setColor(0xFFFFFFFF);
        P.setShader(new RadialGradient(cx, cy, r * 1.9f, new int[] {0xCCFFE680, 0x55FFB300, 0x00FFB300},
                new float[] {0.3f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.9f, P);
        P.setShader(null);
        P.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            float len = i % 2 == 0 ? 1.75f : 1.38f;
            P.setColor(i % 2 == 0 ? 0xFFFFC21A : 0xFFFFD95A);
            P.setStrokeWidth(r * (i % 2 == 0 ? 0.075f : 0.05f));
            c.drawLine(cx + (float) Math.cos(a) * r * 0.9f, cy + (float) Math.sin(a) * r * 0.9f,
                    cx + (float) Math.cos(a) * r * len, cy + (float) Math.sin(a) * r * len, P);
        }
        P.setColor(0xFFFFFFFF);
        P.setShader(new RadialGradient(cx - r * 0.25f, cy - r * 0.25f, r * 1.2f,
                new int[] {0xFFFFFDE8, 0xFFFFD84A, 0xFFFF9F00}, new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, P);
        P.setShader(null);
    }

    private static void moon(Canvas c, float cx, float cy, float r) {
        Path moon = new Path();
        moon.addCircle(cx, cy, r, Path.Direction.CW);
        Path bite = new Path();
        bite.addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, Path.Direction.CW);
        moon.op(bite, Path.Op.DIFFERENCE);
        P.setColor(0xFFFFF1B8);
        c.drawPath(moon, P);
    }

    private static void cloud(Canvas c, float cx, float cy, float r, int color) {
        Path p = new Path();
        p.addCircle(cx - r * 0.42f, cy + r * 0.1f, r * 0.36f, Path.Direction.CW);
        p.addCircle(cx + r * 0.05f, cy - r * 0.12f, r * 0.5f, Path.Direction.CW);
        p.addCircle(cx + r * 0.5f, cy + r * 0.12f, r * 0.34f, Path.Direction.CW);
        p.addRoundRect(new RectF(cx - r * 0.78f, cy + r * 0.05f, cx + r * 0.84f, cy + r * 0.46f), r * 0.2f, r * 0.2f,
                Path.Direction.CW);
        P.setColor(0x33000000);
        c.save();
        c.translate(r * 0.04f, r * 0.07f);
        c.drawPath(p, P);
        c.restore();
        P.setColor(0xFFFFFFFF);   // a shader still takes the paint's alpha; the shadow above left it at 20%
        P.setShader(new LinearGradient(0, cy - r * 0.6f, 0, cy + r * 0.5f, color, darker(color), Shader.TileMode.CLAMP));
        c.drawPath(p, P);
        P.setShader(null);
    }

    private static void drops(Canvas c, float cx, float cy, float r, int n, int color) {
        P.setColor(color);
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setStrokeWidth(r * 0.09f);
        for (int i = 0; i < n; i++) {
            float x = cx - r * 0.45f + i * (r * 0.9f / Math.max(1, n - 1));
            c.drawLine(x, cy, x - r * 0.1f, cy + r * 0.28f, P);
        }
    }

    private static void flakes(Canvas c, float cx, float cy, float r) {
        P.setColor(0xFFFFFFFF);
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setStrokeWidth(r * 0.05f);
        for (int i = 0; i < 3; i++) {
            float x = cx - r * 0.42f + i * r * 0.42f, y = cy + (i % 2) * r * 0.16f, a = r * 0.12f;
            for (int k = 0; k < 3; k++) {
                double t = Math.PI * k / 3;
                c.drawLine(x - (float) Math.cos(t) * a, y - (float) Math.sin(t) * a,
                        x + (float) Math.cos(t) * a, y + (float) Math.sin(t) * a, P);
            }
        }
    }

    private static void bolt(Canvas c, float cx, float cy, float r) {
        Path p = new Path();
        p.moveTo(cx + r * 0.15f, cy - r * 0.6f);
        p.lineTo(cx - r * 0.35f, cy + r * 0.1f);
        p.lineTo(cx, cy + r * 0.1f);
        p.lineTo(cx - r * 0.2f, cy + r * 0.75f);
        p.lineTo(cx + r * 0.4f, cy - r * 0.1f);
        p.lineTo(cx + r * 0.05f, cy - r * 0.1f);
        p.close();
        P.setColor(0xFFFFD43B);
        c.drawPath(p, P);
    }

    private static int darker(int c) {
        int r = (int) (((c >> 16) & 0xFF) * 0.88f), g = (int) (((c >> 8) & 0xFF) * 0.88f), b = (int) ((c & 0xFF) * 0.9f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
