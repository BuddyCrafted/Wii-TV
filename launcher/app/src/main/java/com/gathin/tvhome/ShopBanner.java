package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** The Shop Channel's tile: white fading to pale blue, a blue shopping bag, and the channel's name. */
final class ShopBanner extends Drawable {
    private final String title;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    ShopBanner(String title) {
        this.title = title;
    }

    /** A pair of blue shopping bags, the back one darker, with rounded handles. Top-left at (x, y), `size` tall. */
    static void drawBag(Canvas c, float x, float y, float size, Paint p) {
        float u = size / 100f;
        Path handle = new Path();
        for (int k = 1; k >= 0; k--) {
            float ox = x + k * 16 * u, oy = y - k * 6 * u;
            int body = k == 1 ? 0xFF1E8FD0 : 0xFF2FB2EC;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7 * u);
            p.setColor(body);
            handle.reset();
            handle.addArc(new RectF(ox + 20 * u, oy + 4 * u, ox + 56 * u, oy + 44 * u), 180, 180);
            c.drawPath(handle, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            p.setShader(new LinearGradient(0, oy + 26 * u, 0, oy + 100 * u, k == 1 ? 0xFF2A9BDA : 0xFF4CC6F4, body, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(ox + 6 * u, oy + 26 * u, ox + 72 * u, oy + 100 * u), 6 * u, 6 * u, p);
            p.setShader(null);
        }
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float w = b.width(), h = b.height();
        c.save();
        c.translate(b.left, b.top);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(0, 0, 0, h, 0xFFFFFFFF, 0xFFD8F2FC, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        drawBag(c, w / 2f - h * 0.27f, h * 0.1f, h * 0.55f, paint);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(0xFF2FA9E3);
        paint.setTextSize(h * 0.17f);
        c.drawText(title, w / 2f, h * 0.88f, paint);
        c.restore();
    }

    @Override public int getIntrinsicWidth() { return 640; }
    @Override public int getIntrinsicHeight() { return 344; }
    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
