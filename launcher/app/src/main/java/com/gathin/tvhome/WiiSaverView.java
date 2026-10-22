package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The Wii screensaver: the menu's striped backdrop, softly dimmed, with empty channel frames rising through it
 * like bubbles, glowing blue as the focused channel does, and the segment clock and date drifting slowly
 * across the middle.
 */
final class WiiSaverView extends SaverView {
    private static final int BUBBLES = 9;
    private static final int BLUE = 0xFF3FC0EC;
    private static final float TILE_W = 381f, TILE_H = 204.5f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final SegmentClock clock = new SegmentClock();
    private final float[] size = new float[BUBBLES], x0 = new float[BUBBLES], speed = new float[BUBBLES],
            offset = new float[BUBBLES], phase = new float[BUBBLES];
    private Bitmap stripes;
    private Path tile;
    private LinearGradient tileFill;
    private final android.graphics.RectF screen = new android.graphics.RectF();
    private String date = "";
    private long dateAt;

    WiiSaverView(Context context) {
        super(context);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < BUBBLES; i++) {
            size[i] = 0.32f + 0.34f * r.nextFloat();
            x0[i] = (i + 0.5f) * 1920f / BUBBLES + (r.nextFloat() - 0.5f) * 120f;
            speed[i] = 22f + 26f * r.nextFloat();
            offset[i] = r.nextFloat() * 1500f;
            phase[i] = r.nextFloat() * 6.283f;
        }
    }

    @Override
    void layout(int w, int h) {
        // The stripes at half size: they are soft, and this keeps a 1 GB stick's memory free.
        int bw = Math.max(1, w / 2), bh = Math.max(1, h / 2);
        int[] px = new int[bw * bh];
        float bs = bw / 1920f;
        for (int y = 0; y < bh; y++) {
            float ry = (y + 0.5f) / bs;
            float ph = ((ry - WiiPalette.STRIPE_PHASE) / WiiPalette.STRIPE_PERIOD) % 1f;
            float dist = Math.min(ph, 1f - ph) * WiiPalette.STRIPE_PERIOD;
            float k = (float) Math.exp(-dist * dist / (2f * WiiPalette.STRIPE_SIGMA * WiiPalette.STRIPE_SIGMA));
            for (int x = 0; x < bw; x++) {
                float fd = Math.abs((x + 0.5f) / bs - 959.5f) / 32f;
                int i = Math.min(WiiPalette.BG_BASE.length - 2, (int) fd);
                float a = Math.min(1f, fd - i);
                float v = WiiPalette.BG_BASE[i] * (1 - a) + WiiPalette.BG_BASE[i + 1] * a
                        + (WiiPalette.BG_AMP[i] * (1 - a) + WiiPalette.BG_AMP[i + 1] * a) * k;
                int g = Math.max(0, Math.min(255, Math.round(v * 0.82f)));
                px[y * bw + x] = 0xFF000000 | (g << 16) | (g << 8) | g;
            }
        }
        stripes = Bitmap.createBitmap(px, bw, bh, Bitmap.Config.ARGB_8888);
        tile = tilePath(TILE_W, TILE_H, 25f, 3f);
        tileFill = new LinearGradient(0, 0, 0, TILE_H, 0xFFFFFFFF, 0xFFDDEFF7, Shader.TileMode.CLAMP);
        screen.set(0, 0, w, h);
    }

    /** The menu's channel frame, in reference px. */
    private static Path tilePath(float tw, float th, float r, float b) {
        float k = r * 0.4477f;
        Path p = new Path();
        p.moveTo(r, 0f);
        p.quadTo(tw / 2f, -2f * b, tw - r, 0f);
        p.cubicTo(tw - k, 0f, tw, k, tw, r);
        p.quadTo(tw + 2f * b, th / 2f, tw, th - r);
        p.cubicTo(tw, th - k, tw - k, th, tw - r, th);
        p.quadTo(tw / 2f, th + 2f * b, r, th);
        p.cubicTo(k, th, 0f, th - k, 0f, th - r);
        p.quadTo(-2f * b, th / 2f, 0f, r);
        p.cubicTo(0f, k, k, 0f, r, 0f);
        p.close();
        return p;
    }

    @Override
    void frame(Canvas c, float t) {
        if (stripes != null) c.drawBitmap(stripes, null, screen, bmp);
        for (int i = 0; i < BUBBLES; i++) drawBubble(c, i, t);
        // The clock and date drift on a slow Lissajous path.
        float cx = 960f + 300f * (float) Math.sin(t * 2 * Math.PI / 240f);
        float cy = 470f + 150f * (float) Math.sin(t * 2 * Math.PI / 173f + 1f);
        float u = 1.6f;
        fill.setStyle(Paint.Style.FILL);
        fill.setShader(null);
        fill.setColor(0xFF8A8A8A);
        clock.draw(c, fill, (cx - SegmentClock.WIDTH * u / 2f) * s, (cy - 90f) * s, u * s, ((int) t) % 2 == 0);
        if (System.currentTimeMillis() - dateAt > 30_000L) {
            Locale loc = getResources().getConfiguration().getLocales().get(0);
            Date now = new Date();
            String day = new SimpleDateFormat("EEEE", loc).format(now);
            date = day.substring(0, 1).toUpperCase(loc) + day.substring(1) + "  "
                    + new SimpleDateFormat("en".equals(loc.getLanguage()) ? "M/d" : "d/M", loc).format(now);
            dateAt = System.currentTimeMillis();
        }
        text.setColor(0xFF7C7C7C);
        text.setTextSize(64f * s);
        c.drawText(date, cx * s, (cy + 110f) * s, text);
    }

    private void drawBubble(Canvas c, int i, float t) {
        float span = 1080f + 400f;
        float y = 1180f - ((t * speed[i] + offset[i]) % span);
        float x = x0[i] + 50f * (float) Math.sin(t * 0.35f + phase[i]);
        float fadeIn = Math.min(1f, (1180f - y) / 260f), fadeOut = Math.min(1f, (y + 220f) / 380f);
        float a = Math.max(0f, Math.min(fadeIn, fadeOut));
        if (a <= 0f) return;
        float sc = size[i];
        int save = c.save();
        c.translate((x - TILE_W * sc / 2f) * s, y * s);
        c.scale(sc * s, sc * s);
        c.rotate(4f * (float) Math.sin(t * 0.4f + phase[i]), TILE_W / 2f, TILE_H / 2f);
        fill.setColor(0xFFFFFFFF);
        fill.setShader(tileFill);
        fill.setAlpha(Math.round(150 * a));
        c.drawPath(tile, fill);
        fill.setShader(null);
        for (int k = 3; k >= 1; k--) {
            stroke.setColor(BLUE);
            stroke.setAlpha(Math.round(a * 60 / k));
            stroke.setStrokeWidth(4.5f + k * 6f);
            c.drawPath(tile, stroke);
        }
        stroke.setColor(BLUE);
        stroke.setAlpha(Math.round(230 * a));
        stroke.setStrokeWidth(6f);
        c.drawPath(tile, stroke);
        c.restoreToCount(save);
    }
}
