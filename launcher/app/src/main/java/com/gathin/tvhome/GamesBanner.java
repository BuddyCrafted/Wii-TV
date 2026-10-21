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

/** The Games channel's tile: white fading to pale blue, a blue game pad, and the channel's name. */
final class GamesBanner extends Drawable {
    private final String title;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    GamesBanner(String title) {
        this.title = title;
    }

    /** A rounded blue game pad with a white cross and four buttons. Top-left at (x, y), `width` wide (0.5 x as tall). */
    static void drawPad(Canvas c, float x, float y, float width, Paint p) {
        float u = width / 200f;
        Path body = new Path();
        // Two grips joined by a bar, like a modern pad seen from above.
        body.addRoundRect(new RectF(x + 30 * u, y + 6 * u, x + 170 * u, y + 66 * u), 30 * u, 30 * u, Path.Direction.CW);
        body.addCircle(x + 46 * u, y + 52 * u, 44 * u, Path.Direction.CW);
        body.addCircle(x + 154 * u, y + 52 * u, 44 * u, Path.Direction.CW);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, y, 0, y + 96 * u, 0xFF5CCDF6, 0xFF1E8FD0, Shader.TileMode.CLAMP));
        c.drawPath(body, p);
        p.setShader(null);
        // Cross.
        p.setColor(0xFFFFFFFF);
        float cx = x + 48 * u, cy = y + 48 * u;
        c.drawRoundRect(new RectF(cx - 22 * u, cy - 7 * u, cx + 22 * u, cy + 7 * u), 3 * u, 3 * u, p);
        c.drawRoundRect(new RectF(cx - 7 * u, cy - 22 * u, cx + 7 * u, cy + 22 * u), 3 * u, 3 * u, p);
        // Buttons.
        float bx = x + 152 * u, by = y + 48 * u, r = 8 * u, d = 16 * u;
        c.drawCircle(bx, by - d, r, p);
        c.drawCircle(bx, by + d, r, p);
        c.drawCircle(bx - d, by, r, p);
        c.drawCircle(bx + d, by, r, p);
        // Start / select.
        c.drawRoundRect(new RectF(x + 86 * u, y + 28 * u, x + 96 * u, y + 34 * u), 3 * u, 3 * u, p);
        c.drawRoundRect(new RectF(x + 104 * u, y + 28 * u, x + 114 * u, y + 34 * u), 3 * u, 3 * u, p);
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
        float pw = h * 1.0f;
        drawPad(c, w / 2f - pw / 2f, h * 0.16f, pw, paint);
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
