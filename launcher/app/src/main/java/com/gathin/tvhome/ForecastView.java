package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The Forecast Channel's screen, after the console's weather channel: the sky behind everything, today's
 * conditions large on the left (place, temperature, icon, high and low, humidity, wind), and the next five
 * days as cards along the bottom. Geometry in 1920 x 1080 reference px, scaled to the view.
 */
final class ForecastView extends View {
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Typeface light, medium;
    private Weather.Report report;
    private String status = "";

    ForecastView(Context context) {
        super(context);
        light = font(context, R.font.outfit_light);
        medium = font(context, R.font.outfit_medium);
    }

    private static Typeface font(Context c, int id) {
        try {
            return c.getResources().getFont(id);
        } catch (RuntimeException e) {
            return Typeface.SANS_SERIF;
        }
    }

    void setReport(Weather.Report r) {
        report = r;
        invalidate();
    }

    void setStatus(String s) {
        status = s == null ? "" : s;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float s = w / 1920f;
        Weather.Report r = report;
        Weather.Sky sky = r == null ? Weather.Sky.PARTLY : Weather.sky(r.code);
        WeatherArt.sky(c, w, h, sky, r == null || r.day);

        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(medium);
        text.setColor(0xFFFFFFFF);
        text.setShadowLayer(6f * s, 0, 3f * s, 0x55000000);
        text.setTextSize(52f * s);
        c.drawText(getContext().getString(R.string.wx_title), 110f * s, 120f * s, text);

        if (r == null) {
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(48f * s);
            c.drawText(status.isEmpty() ? getContext().getString(R.string.wx_loading) : status, w / 2f, h / 2f, text);
            text.clearShadowLayer();
            return;
        }

        // Today, large.
        text.setTextSize(64f * s);
        c.drawText(r.place, 110f * s, 250f * s, text);
        WeatherArt.icon(c, 420f * s, 470f * s, 210f * s, sky, r.day);
        text.setTypeface(light);
        text.setTextSize(250f * s);
        c.drawText(Weather.temp(r.temp), 760f * s, 590f * s, text);
        text.setTypeface(medium);
        text.setTextSize(56f * s);
        c.drawText(getContext().getString(Weather.label(r.code)), 780f * s, 670f * s, text);
        if (!r.days.isEmpty()) {
            Weather.Day today = r.days.get(0);
            text.setTypeface(light);
            text.setTextSize(44f * s);
            c.drawText(getContext().getString(R.string.wx_hilo, Weather.temp(today.max), Weather.temp(today.min)),
                    1300f * s, 400f * s, text);
            c.drawText(getContext().getString(R.string.wx_rain_chance, today.rainChance), 1300f * s, 470f * s, text);
        }
        text.setTypeface(light);
        text.setTextSize(44f * s);
        c.drawText(getContext().getString(R.string.wx_humidity, r.humidity), 1300f * s, 540f * s, text);
        c.drawText(getContext().getString(r.fahrenheit ? R.string.wx_wind_mph : R.string.wx_wind_kmh, Math.round(r.wind)),
                1300f * s, 610f * s, text);
        text.clearShadowLayer();

        // The next days, as white cards.
        int n = Math.min(5, r.days.size() - 1);
        float cardW = 300f * s, gap = 34f * s, top = 760f * s, cardH = 250f * s;
        float x0 = (w - (n * cardW + (n - 1) * gap)) / 2f;
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        SimpleDateFormat out = new SimpleDateFormat("EEE", locale);
        for (int i = 0; i < n; i++) {
            Weather.Day d = r.days.get(i + 1);
            float x = x0 + i * (cardW + gap);
            rect.set(x, top, x + cardW, top + cardH);
            paint.setShader(new LinearGradient(0, top, 0, top + cardH, 0xF2FFFFFF, 0xE6E4E9EE, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, 32f * s, 32f * s, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f * s);
            paint.setColor(0xFFB5B5B5);
            c.drawRoundRect(rect, 32f * s, 32f * s, paint);
            paint.setStyle(Paint.Style.FILL);
            String day;
            try {
                Date date = in.parse(d.date);
                day = date == null ? d.date : out.format(date);
            } catch (java.text.ParseException e) {
                day = d.date;
            }
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(medium);
            text.setTextSize(40f * s);
            text.setColor(0xFF6A6A6A);
            c.drawText(day, rect.centerX(), top + 56f * s, text);
            WeatherArt.icon(c, rect.centerX(), top + 120f * s, 60f * s, Weather.sky(d.code), true);
            text.setTextSize(38f * s);
            text.setColor(0xFFE0533D);
            c.drawText(Weather.temp(d.max), rect.centerX() - 50f * s, top + 210f * s, text);
            text.setColor(0xFF2E86BF);
            c.drawText(Weather.temp(d.min), rect.centerX() + 50f * s, top + 210f * s, text);
        }

        text.setTextAlign(Paint.Align.RIGHT);
        text.setTypeface(light);
        text.setTextSize(32f * s);
        text.setColor(0xCCFFFFFF);
        String updated = r.fetchedAt > 0
                ? getContext().getString(R.string.wx_updated, android.text.format.DateFormat.getTimeFormat(getContext()).format(new Date(r.fetchedAt)))
                : "";
        c.drawText(status.isEmpty() ? updated : status, 1810f * s, 120f * s, text);
    }
}
