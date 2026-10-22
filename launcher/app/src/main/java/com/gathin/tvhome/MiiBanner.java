package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import java.util.List;

/**
 * The Mii channel's tile in the channel menu. With a pack it is the original's: a crowd of faces bobbing, a white
 * veil and the Mii logo fading in over them, and back. Without one, the plaza's first residents in a row and the name.
 */
final class MiiBanner extends Drawable implements WiiMenuView.LiveArt {
    private static final int MAX_SHOWN = 5;
    /** Seconds in one loop of the original's tile. */
    private static final float LOOP = 10f;

    private final List<Mii> miis;
    private final String label;
    private final Typeface face;
    private final MiiArt art = new MiiArt();
    private final MiiArt.Pose pose = new MiiArt.Pose();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();

    MiiBanner(List<Mii> miis, String label, Typeface face) {
        this.miis = miis;
        this.label = label;
        this.face = face;
    }

    private static MiiPack crowdPack() {
        MiiPack p = MiiPack.peek();
        return p != null && !p.crowdNames.isEmpty() && p.bitmap("ui/logo") != null ? p : null;
    }

    @Override
    public boolean animating() {
        return crowdPack() != null;
    }

    @Override
    public void draw(Canvas c) {
        MiiPack p = crowdPack();
        if (p != null) {
            drawCrowd(c, p);
            return;
        }
        Rect b = getBounds();
        float w = b.width(), h = b.height();
        int save = c.save();
        c.translate(b.left, b.top);
        MiiArt.opaque(paint).setShader(new LinearGradient(0, 0, 0, h, 0xFFFFFFFF, 0xFFE6E6E6, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        int n = Math.min(MAX_SHOWN, miis.size());
        float ground = h * 0.70f, u = h * 0.15f;
        float pitch = Math.min(w * 0.17f, w * 0.9f / Math.max(1, n));
        float x0 = w / 2f - pitch * (n - 1) / 2f;
        for (int i = 0; i < n; i++) {
            Mii m = miis.get(i);
            float x = x0 + i * pitch;
            paint.setColor(0x26000000);
            c.drawOval(x - u * 0.62f, ground - u * 0.16f, x + u * 0.62f, ground + u * 0.16f, paint);
            int sv = c.save();
            c.translate(x, ground);
            c.scale(u, u);
            MiiArt.Pose p0 = pose.reset();
            MiiArt.place(m, p0);
            int hs = c.save();
            c.translate(p0.headX, p0.headY);
            art.head(c, m, MiiArt.FRONT, true, false);
            c.restoreToCount(hs);
            art.body(c, m, p0);
            c.translate(p0.headX, p0.headY);
            art.head(c, m, MiiArt.FRONT, false, false);
            c.restoreToCount(sv);
        }

        paint.setTypeface(face);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(h * 0.15f);
        paint.setColor(0xFF8C8C8C);
        c.drawText(label, w / 2f, h * 0.91f, paint);
        c.restoreToCount(save);
    }

    private void drawCrowd(Canvas c, MiiPack p) {
        Rect b = getBounds();
        float w = b.width(), h = b.height();
        float t = (SystemClock.uptimeMillis() % (long) (LOOP * 1000)) / 1000f;
        int save = c.save();
        c.translate(b.left, b.top);
        paint.setShader(null);
        paint.setColor(0xFFFFFFFF);
        c.drawRect(0, 0, w, h, paint);
        bmp.setAlpha(255);
        for (int i = 0; i < p.crowdNames.size(); i++) {
            Bitmap f = p.bitmap("ui/" + p.crowdNames.get(i));
            if (f == null) continue;
            float[] q = p.crowdPlaces.get(i);
            float sc = q[2] * w / Math.max(1, f.getWidth() - 2);
            float bob = (float) Math.sin((t * 1.25f + i * 0.37f) * 2 * Math.PI) * 0.018f * h;
            float cx = q[0] * w, cy = q[1] * h + bob;
            dst.set(cx - f.getWidth() * sc / 2f, cy - f.getHeight() * sc / 2f, cx + f.getWidth() * sc / 2f,
                    cy + f.getHeight() * sc / 2f);
            c.drawBitmap(f, null, dst, bmp);
        }
        float veil = ease(t, 5.5f, 6.1f) - ease(t, 9.1f, 9.7f);
        if (veil > 0.002f) {
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha(Math.round(225 * veil));
            c.drawRect(0, 0, w, h, paint);
        }
        float logo = ease(t, 6.0f, 6.6f) - ease(t, 8.6f, 9.1f);
        Bitmap l = p.bitmap("ui/logo");
        if (logo > 0.002f && l != null) {
            float lw = w * 0.56f, lh = lw * l.getHeight() / l.getWidth();
            dst.set((w - lw) / 2f, (h - lh) / 2f, (w + lw) / 2f, (h + lh) / 2f);
            bmp.setAlpha(Math.round(255 * logo));
            c.drawBitmap(l, null, dst, bmp);
        }
        c.restoreToCount(save);
    }

    private static float ease(float t, float from, float to) {
        float x = Math.max(0f, Math.min(1f, (t - from) / (to - from)));
        return x * x * (3f - 2f * x);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        bmp.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
