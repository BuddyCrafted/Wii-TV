package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** A channel tile that is just a name in white on one colour; no intrinsic size, so it fills the tile. */
final class NameTile extends Drawable {

    private final String name;
    private final int color;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    NameTile(String name, int color) {
        this.name = name;
        this.color = color;
        text.setColor(Color.WHITE);
        text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        c.drawColor(color);
        text.setTextSize(b.height() * 0.30f);
        float max = b.width() * 0.8f;
        float w = text.measureText(name);
        if (w > max) text.setTextSize(text.getTextSize() * max / w);
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(name, b.exactCenterX(), b.exactCenterY() - (fm.ascent + fm.descent) / 2f, text);
    }

    @Override
    public void setAlpha(int alpha) {
        text.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        text.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
