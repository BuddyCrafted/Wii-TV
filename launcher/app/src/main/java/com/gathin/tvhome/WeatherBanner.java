package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;

/** The Forecast Channel's tile: the sky, the icon and the temperature now, drawn at whatever size the tile is. */
final class WeatherBanner extends Drawable {
    private final Weather.Report report;
    private final String title;
    private final Typeface face;
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    WeatherBanner(Weather.Report report, String title, Typeface face) {
        this.report = report;
        this.title = title;
        this.face = face;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float w = b.width(), h = b.height();
        c.save();
        c.translate(b.left, b.top);
        Weather.Sky sky = report == null ? Weather.Sky.PARTLY : Weather.sky(report.code);
        boolean day = report == null || report.day;
        WeatherArt.sky(c, w, h, sky, day);
        WeatherArt.icon(c, w * 0.3f, h * 0.48f, h * 0.36f, sky, day);
        text.setTypeface(face);
        text.setColor(0xFFFFFFFF);
        text.setShadowLayer(h * 0.02f, 0, h * 0.012f, 0x66000000);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(h * 0.36f);
        if (report != null) c.drawText(Weather.temp(report.temp), w * 0.56f, h * 0.6f, text);
        text.setTextSize(h * 0.12f);
        String place = report == null || report.place.isEmpty() ? title : report.place;
        c.drawText(place, w * 0.56f, h * 0.82f, text);
        c.restore();
    }

    @Override public int getIntrinsicWidth() { return 640; }
    @Override public int getIntrinsicHeight() { return 344; }
    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
