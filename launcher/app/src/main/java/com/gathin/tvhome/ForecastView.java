package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The Forecast Channel, after the console's: a channel preview (CURRENT card with Wii Menu / Start), then
 * pages in the channel's broadcast style (grey button bars top and bottom, a green title tab and city pill,
 * a blue board, the red "As of" strip): Current, Today, Tomorrow and the 5-day forecast, and last a spinning
 * globe with the weather in cities around the world. Up / down turn the pages. Geometry in 1920 x 1080
 * reference px, scaled to the view.
 */
final class ForecastView extends View {

    interface Listener {
        void onExit();
    }

    enum Page { PREVIEW, CURRENT, TODAY, TOMORROW, FIVE_DAY, GLOBE }

    private static final int GREEN_DARK = 0xFF0C8A2A, GREEN = 0xFF13A537, GREEN_LIGHT = 0xFF3CCB5A;
    private static final int BLUE_TOP = 0xFF1A4FC4, BLUE_MID = 0xFF3478E8, NAVY = 0xFF0A1F5C;
    private static final int YELLOW = 0xFFFFD43B, CYAN = 0xFF8DEBFF, WII_BLUE = 0xFF3FC0EC;
    private static final float[] ZOOMS = {190f, 300f, 470f};
    private static final android.graphics.ColorMatrixColorFilter LAND = new android.graphics.ColorMatrixColorFilter(
            new float[] {
                0, 0, 0, 0, 255,
                0, 0, 0, 0, 255,
                0, 0, 0, 0, 255,
                0.5f, 0.9f, -0.6f, 0, -10,
            });

    private final Listener listener;
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final WiiHand hand = new WiiHand(Typeface.create("sans-serif", Typeface.BOLD));
    private final float[] stars = new float[240];

    private Page page = Page.PREVIEW;
    private int previewSel = 1;
    private Weather.Report report;
    private List<Weather.City> world;
    private String status = "";
    private Bitmap map;            // the Blue Marble, for the boards' faint backdrop

    private Globe globe;
    private double lonC, lonShown, tiltC;
    private int zoom = 1;
    private int cityFocus = -1;
    private boolean globeDirty = true;

    ForecastView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        Random r = new Random(7);
        for (int i = 0; i < stars.length; i++) stars[i] = r.nextFloat();
        setFocusable(true);
    }

    void setReport(Weather.Report r) {
        report = r;
        if (r != null && cityFocus < 0) restore();
        invalidate();
    }

    void setWorld(List<Weather.City> w) {
        world = w;
        invalidate();
    }

    void setStatus(String s) {
        status = s == null ? "" : s;
        invalidate();
    }

    void setGlobe(Globe g, Bitmap flatMap) {
        globe = g;
        map = flatMap;
        globeDirty = true;
        invalidate();
    }

    Page page() {
        return page;
    }

    private void restore() {
        lonC = lonShown = report == null ? -40 : report.lon;
        tiltC = report == null ? 25 : clampTilt(report.lat);
        zoom = 1;
        cityFocus = -1;
        globeDirty = true;
    }

    private static double clampTilt(double t) {
        return Math.max(-55, Math.min(65, t));
    }

    // ---- keys -------------------------------------------------------------------------------------

    private static boolean ok(int k) {
        return k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BUTTON_A
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    boolean handleKey(int k) {
        boolean back = k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_BUTTON_B || k == KeyEvent.KEYCODE_ESCAPE;
        switch (page) {
            case PREVIEW:
                if (k == KeyEvent.KEYCODE_DPAD_LEFT) previewSel = 0;
                else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) previewSel = 1;
                else if (ok(k)) {
                    if (previewSel == 0) listener.onExit();
                    else page = Page.CURRENT;
                } else if (back) listener.onExit();
                else return false;
                invalidate();
                return true;
            case GLOBE:
                return globeKey(k, back);
            default:
                int i = page.ordinal();
                if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (i > Page.CURRENT.ordinal()) page = Page.values()[i - 1];
                } else if (k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_DPAD_RIGHT || ok(k)) {
                    page = Page.values()[i + 1];
                } else if (back) {
                    page = Page.PREVIEW;
                } else {
                    return false;
                }
                invalidate();
                return true;
        }
    }

    private boolean globeKey(int k, boolean back) {
        if (back) {
            page = Page.FIVE_DAY;   // End
        } else if (k == KeyEvent.KEYCODE_DPAD_LEFT) {
            lonC -= 12;
        } else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
            lonC += 12;
        } else if (k == KeyEvent.KEYCODE_DPAD_UP) {
            tiltC = clampTilt(tiltC + 10);
        } else if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
            tiltC = clampTilt(tiltC - 10);
        } else if (k == KeyEvent.KEYCODE_MEDIA_REWIND || k == KeyEvent.KEYCODE_BUTTON_L1 || k == KeyEvent.KEYCODE_PAGE_UP) {
            zoom = Math.max(0, zoom - 1);
        } else if (k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || k == KeyEvent.KEYCODE_BUTTON_R1 || k == KeyEvent.KEYCODE_PAGE_DOWN) {
            zoom = Math.min(ZOOMS.length - 1, zoom + 1);
        } else if (ok(k)) {
            nextCity();
        } else if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || k == KeyEvent.KEYCODE_BUTTON_Y) {
            restore();
        } else {
            return false;
        }
        globeDirty = true;
        invalidate();
        return true;
    }

    /** Next: the next city eastward of the one in focus (or of the view), brought to the middle. */
    private void nextCity() {
        if (world == null || world.isEmpty()) return;
        double from = cityFocus >= 0 ? world.get(cityFocus).lon : lonC;
        int best = -1;
        double bestD = 999;
        for (int i = 0; i < world.size(); i++) {
            double d = world.get(i).lon - from;
            while (d <= 0.5) d += 360;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        cityFocus = best;
        Weather.City c = world.get(best);
        // Keep the turn short: aim at the same lap of longitude the view is on.
        double target = c.lon;
        while (target - lonShown > 180) target -= 360;
        while (target - lonShown < -180) target += 360;
        lonC = target;
        tiltC = clampTilt(c.lat);
        zoom = Math.max(zoom, 1);
    }

    // ---- drawing ----------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        float s = getWidth() / 1920f;
        c.save();
        c.scale(s, s);
        switch (page) {
            case PREVIEW: drawPreview(c); break;
            case GLOBE: drawGlobe(c, s); break;
            default: drawBoard(c); break;
        }
        c.restore();
    }

    private String place() {
        return report == null || report.place.isEmpty() ? getContext().getString(R.string.wx_here) : report.place;
    }

    private String asOf() {
        long t = report == null || report.fetchedAt == 0 ? System.currentTimeMillis() : report.fetchedAt;
        Date d = new Date(t);
        String time = android.text.format.DateFormat.getTimeFormat(getContext()).format(d);
        String date = new SimpleDateFormat("MM/dd", Locale.getDefault()).format(d);
        return getContext().getString(R.string.wx_as_of, time, date);
    }

    private String pageTitle(Page p) {
        switch (p) {
            case CURRENT: return getContext().getString(R.string.wx_page_current);
            case TODAY: return getContext().getString(R.string.wx_page_today);
            case TOMORROW: return getContext().getString(R.string.wx_page_tomorrow);
            case FIVE_DAY: return getContext().getString(R.string.wx_page_5day);
            case GLOBE: return getContext().getString(R.string.wx_page_globe);
            default: return "";
        }
    }

    /** Grey glossy bar of three buttons, as at the top and bottom of the channel's pages. */
    private void drawButtonBar(Canvas c, float top, float bottom, String left, String mid, String right, int highlight) {
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(0, top, 0, bottom, 0xFFF7F7F7, 0xFFC9C9C9, Shader.TileMode.CLAMP));
        c.drawRect(0, top, 1920, bottom, paint);
        paint.setShader(null);
        float[] xs = {0, 640, 1280, 1920};
        String[] labels = {left, mid, right};
        for (int i = 0; i < 3; i++) {
            if (i == highlight) {
                paint.setColor(0xFFFFFFFF);
                paint.setShader(new LinearGradient(0, top, 0, bottom, 0xFF5AB4F0, 0xFF1F78C8, Shader.TileMode.CLAMP));
                c.drawRect(xs[i] + 3, top + 3, xs[i + 1] - 3, bottom - 3, paint);
                paint.setShader(null);
            }
            if (i > 0) {
                paint.setColor(0xFF9E9E9E);
                c.drawRect(xs[i] - 2, top, xs[i], bottom, paint);
                paint.setColor(0xFFFFFFFF);
                c.drawRect(xs[i], top, xs[i] + 2, bottom, paint);
            }
            if (labels[i] != null && !labels[i].isEmpty()) {
                text.setTypeface(regular);
                text.setTextSize(46);
                text.setTextAlign(Paint.Align.CENTER);
                text.setColor(i == highlight ? 0xFFFFFFFF : 0xFF2E2E2E);
                c.drawText(labels[i], (xs[i] + xs[i + 1]) / 2f, (top + bottom) / 2f + 16, text);
            }
        }
        paint.setColor(0xFF8E8E8E);
        c.drawRect(0, top == 0 ? bottom - 3 : top, 1920, top == 0 ? bottom : top + 3, paint);
    }

    /** The green title tab and the green pill with the place name. */
    private void drawHeader(Canvas c, float top, float bottom, String title) {
        paint.setColor(NAVY);
        c.drawRect(0, top, 1920, bottom, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(0, top, 0, bottom, GREEN, GREEN_DARK, Shader.TileMode.CLAMP));
        c.drawRect(0, top + 8, 640, bottom - 8, paint);
        rect.set(600, top + 14, 1900, bottom - 14);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, GREEN_LIGHT, GREEN, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(0x66FFFFFF);
        c.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint);
        paint.setStyle(Paint.Style.FILL);

        text.setTypeface(bold);
        text.setColor(0xFFFFFFFF);
        text.setShadowLayer(4, 0, 3, 0x88000000);
        text.setTextAlign(Paint.Align.CENTER);
        String[] lines = title.split("\n");
        float size = lines.length > 1 ? 54 : 72;
        text.setTextSize(size);
        float mid = (top + bottom) / 2f, y0 = mid - (lines.length - 1) * size * 0.52f + size * 0.36f;
        for (int i = 0; i < lines.length; i++) c.drawText(lines[i], 300, y0 + i * size * 1.04f, text);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(64);
        CharSequence p = TextUtils.ellipsize(place(), text, 1100, TextUtils.TruncateAt.END);
        c.drawText(p, 0, p.length(), 1840, mid + 22, text);
        text.clearShadowLayer();
    }

    /** The blue board, with the world map faintly behind it, as on the console. */
    private void drawBody(Canvas c, float top, float bottom) {
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(0, top, 0, bottom, new int[] {BLUE_TOP, BLUE_MID, BLUE_TOP},
                new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, top, 1920, bottom, paint);
        paint.setShader(null);
        if (map != null) {
            int sv = c.save();
            c.clipRect(0, top, 1920, bottom);
            // Continents as faint light shapes on the blue, as on the console's boards: brightness becomes
            // white with alpha, so the dark ocean drops out.
            paint.setColorFilter(LAND);
            paint.setAlpha(52);
            c.drawBitmap(map, null, new RectF(0, top - 80, 1920, top - 80 + 960), paint);
            paint.setAlpha(255);
            paint.setColorFilter(null);
            c.restoreToCount(sv);
        }
    }

    /** The strip under the board: credits on the left, "As of" on red at the right. */
    private void drawInfoStrip(Canvas c, float top, float bottom) {
        paint.setColor(NAVY);
        c.drawRect(0, top, 1920, bottom, paint);
        text.setTypeface(regular);
        text.setTextSize(28);
        text.setColor(0xFFDDE6FF);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText(getContext().getString(R.string.wx_credits), 40, (top + bottom) / 2f + 10, text);
        // Red ribbon fading in from the left, solid under the time.
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(1000, 0, 1360, 0, 0x00D3202A, 0xFFD3202A, Shader.TileMode.CLAMP));
        c.drawRect(1000, top + 6, 1920, bottom - 6, paint);
        paint.setShader(null);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(36);
        text.setColor(0xFFFFFFFF);
        c.drawText(status.isEmpty() ? asOf() : status, 1880, (top + bottom) / 2f + 13, text);
    }

    private void drawBoard(Canvas c) {
        c.drawColor(0xFF000000);
        int i = page.ordinal();
        Page prev = i > Page.CURRENT.ordinal() ? Page.values()[i - 1] : null;
        Page next = Page.values()[i + 1];
        drawButtonBar(c, 0, 110, "", prev == null ? "" : "▲  " + pageTitle(prev), "", -1);
        drawHeader(c, 110, 260, pageTitle(page).replace("5-DAY ", "5-DAY\n"));
        drawBody(c, 260, 900);
        drawInfoStrip(c, 900, 966);
        drawButtonBar(c, 966, 1080, "Ⓑ  " + getContext().getString(R.string.set_back),
                "▼  " + pageTitle(next), "", -1);

        Weather.Report r = report;
        if (r == null) {
            text.setTypeface(bold);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(56);
            text.setColor(0xFFFFFFFF);
            c.drawText(status.isEmpty() ? getContext().getString(R.string.wx_loading) : status, 960, 600, text);
        } else if (page == Page.CURRENT) {
            drawCurrentBody(c, r, 260, 900);
        } else if (page == Page.TODAY || page == Page.TOMORROW) {
            int d = page == Page.TODAY ? 0 : 1;
            if (r.days.size() > d) drawDayBody(c, r, r.days.get(d), d == 0);
        } else if (page == Page.FIVE_DAY) {
            drawFiveDay(c, r);
        }
        hand.draw(c, 1200, 1012, 1.35f);
    }

    private void sideArrows(Canvas c, float y) {
        paint.setColor(CYAN);
        for (int side = 0; side < 2; side++) {
            float x = side == 0 ? 90 : 1830, d = side == 0 ? -1 : 1;
            path.reset();
            path.moveTo(x + d * 30, y);
            path.lineTo(x - d * 20, y - 44);
            path.lineTo(x - d * 20, y + 44);
            path.close();
            c.drawPath(path, paint);
        }
    }

    private void drawCurrentBody(Canvas c, Weather.Report r, float top, float bottom) {
        float mid = (top + bottom) / 2f;
        sideArrows(c, mid);
        text.setTypeface(bold);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0xFFFFFFFF);
        text.setShadowLayer(6, 0, 4, 0x88000000);
        text.setTextSize(250);
        c.drawText(Weather.temp(r.temp), 700, mid + 50, text);
        text.setTextSize(76);
        c.drawText(getContext().getString(Weather.label(r.code)), 700, mid + 170, text);
        text.clearShadowLayer();
        WeatherArt.icon(c, 1300, mid - 10, 210, Weather.sky(r.code), r.day);
    }

    private void drawDayBody(Canvas c, Weather.Report r, Weather.Day d, boolean today) {
        WeatherArt.icon(c, 480, 520, 190, Weather.sky(d.code), true);
        text.setTypeface(bold);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0xFFFFFFFF);
        text.setShadowLayer(5, 0, 3, 0x88000000);
        text.setTextSize(64);
        c.drawText(getContext().getString(Weather.label(d.code)), 480, 800, text);

        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(56);
        c.drawText(getContext().getString(R.string.wx_high), 960, 410, text);
        c.drawText(getContext().getString(R.string.wx_low), 960, 590, text);
        text.setTextSize(150);
        text.setColor(YELLOW);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(Weather.temp(d.max), 1700, 440, text);
        text.setColor(CYAN);
        c.drawText(Weather.temp(d.min), 1700, 620, text);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(48);
        text.setColor(0xFFFFFFFF);
        String extra = getContext().getString(R.string.wx_rain_chance, d.rainChance);
        if (today) {
            extra += "    " + getContext().getString(R.string.wx_humidity, r.humidity) + "    "
                    + getContext().getString(r.fahrenheit ? R.string.wx_wind_mph : R.string.wx_wind_kmh, Math.round(r.wind));
        }
        c.drawText(extra, 960, 790, text);
        text.clearShadowLayer();
    }

    private void drawFiveDay(Canvas c, Weather.Report r) {
        int n = Math.min(5, r.days.size() - 1);
        float x0 = 40, colW = (1920 - 80) / 5f;
        SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        SimpleDateFormat out = new SimpleDateFormat("EEE", getResources().getConfiguration().getLocales().get(0));
        for (int i = 0; i < n; i++) {
            Weather.Day d = r.days.get(i + 1);
            float x = x0 + i * colW;
            // Day name on a darker cell, as on the console's board.
            paint.setColor(0xFFFFFFFF);
            paint.setShader(new LinearGradient(0, 280, 0, 380, 0xFF16348C, 0xFF0D2470, Shader.TileMode.CLAMP));
            c.drawRect(x + 6, 280, x + colW - 6, 380, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setShader(new LinearGradient(0, 380, 0, 880, 0x553E86F0, 0x22184AB8, Shader.TileMode.CLAMP));
            c.drawRect(x + 6, 384, x + colW - 6, 880, paint);
            paint.setShader(null);
            String day;
            try {
                Date date = in.parse(d.date);
                day = date == null ? d.date : out.format(date).replace(".", "").toUpperCase(Locale.getDefault());
            } catch (java.text.ParseException e) {
                day = d.date;
            }
            text.setTypeface(bold);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(0xFFFFFFFF);
            text.setShadowLayer(4, 0, 3, 0x88000000);
            text.setTextSize(66);
            c.drawText(day, x + colW / 2f, 355, text);
            WeatherArt.icon(c, x + colW / 2f, 500, 92, Weather.sky(d.code), true);
            text.setTextSize(118);
            text.setColor(YELLOW);
            c.drawText(String.valueOf(Math.round(d.max)), x + colW / 2f, 710, text);
            text.setColor(CYAN);
            c.drawText(String.valueOf(Math.round(d.min)), x + colW / 2f, 850, text);
            text.clearShadowLayer();
        }
    }

    private void drawPreview(Canvas c) {
        c.drawColor(0xFF000000);
        RectF frame = new RectF(36, 30, 1884, 1050);
        int sv = c.save();
        path.reset();
        path.addRoundRect(frame, 70, 70, Path.Direction.CW);
        c.clipPath(path);
        // Top: the channel's blue name bar.
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(0, 30, 0, 190, 0xFF3F8FE8, 0xFF1A55C0, Shader.TileMode.CLAMP));
        c.drawRect(frame.left, 30, frame.right, 190, paint);
        paint.setShader(null);
        text.setTypeface(bold);
        text.setColor(0xFFFFFFFF);
        text.setTextSize(64);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(getContext().getString(R.string.wx_title), 1830, 135, text);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(40);
        c.drawText("WiiOS", 100, 128, text);
        drawHeader(c, 190, 330, getContext().getString(R.string.wx_page_current));
        drawBody(c, 330, 760);
        if (report != null) drawCurrentBody(c, report, 330, 760);
        drawInfoStrip(c, 760, 820);
        // Bottom: the console's grey panel with Wii Menu / Start.
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(0, 820, 0, 1050, 0xFFF4F4F4, 0xFFD0D0D4, Shader.TileMode.CLAMP));
        c.drawRect(frame.left, 820, frame.right, 1050, paint);
        paint.setShader(null);
        String[] labels = {getContext().getString(R.string.wx_wii_menu), getContext().getString(R.string.wx_start)};
        float[] cx = {660, 1260};
        for (int i = 0; i < 2; i++) {
            RectF b = new RectF(cx[i] - 260, 868, cx[i] + 260, 998);
            drawPill(c, b, i == previewSel);
            text.setTypeface(regular);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(58);
            text.setColor(0xFF4A4A4A);
            c.drawText(labels[i], b.centerX(), b.centerY() + 20, text);
        }
        c.restoreToCount(sv);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(12);
        paint.setColor(0xFF2A2A2A);
        c.drawRoundRect(frame, 70, 70, paint);
        paint.setStyle(Paint.Style.FILL);
        hand.draw(c, cx[previewSel] + 150, 960, 1.6f);
    }

    private void drawPill(Canvas c, RectF r, boolean on) {
        float rad = r.height() / 2f;
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0xFFFFFFFF, 0xFFE0E0E0, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        if (on) {
            for (int k = 3; k >= 1; k--) {
                paint.setColor((Math.min(255, 70 / k) << 24) | (WII_BLUE & 0xFFFFFF));
                paint.setStrokeWidth(4 + k * 6);
                c.drawRoundRect(r, rad, rad, paint);
            }
        }
        paint.setColor(on ? WII_BLUE : 0xFFB8B8B8);
        paint.setStrokeWidth(on ? 7 : 5);
        c.drawRoundRect(r, rad, rad, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    // ---- globe ------------------------------------------------------------------------------------

    private void drawGlobe(Canvas c, float s) {
        c.drawColor(0xFF000000);
        // Stars.
        paint.setColor(0xFFFFFFFF);
        for (int i = 0; i + 2 < stars.length; i += 3) {
            paint.setAlpha(60 + (int) (stars[i + 2] * 160));
            c.drawCircle(stars[i] * 1920, stars[i + 1] * 1080, 1.2f + stars[i + 2] * 1.8f, paint);
        }
        paint.setAlpha(255);

        // Ease the spin toward its target; each frame only shifts the texture.
        double diff = lonC - lonShown;
        boolean spinning = Math.abs(diff) > 0.3;
        lonShown = spinning ? lonShown + diff * 0.25 : lonC;
        // Work in screen px (1920 x 1080); the globe renders at its own smaller size and is scaled up.
        float f = globe != null ? globe.W / 1920f : 0.5f;
        float gr = ZOOMS[zoom] * 2, gx = 960, gy = 584;
        float r = gr * f, cx = gx * f, cy = gy * f;

        // Atmosphere glow.
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new RadialGradient(gx, gy, gr * 1.12f, new int[] {0x00000000, 0x00000000, 0x664FA8FF, 0x00000000},
                new float[] {0f, 0.86f, 0.9f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(gx, gy, gr * 1.12f, paint);
        paint.setShader(null);

        if (globe != null && globe.ready()) {
            Bitmap b = globe.render(cx, cy, r, tiltC, lonShown);
            c.drawBitmap(b, null, new RectF(0, 0, 1920, 1080), paint);
        } else {
            paint.setColor(0xFFFFFFFF);
            paint.setShader(new RadialGradient(gx - gr * 0.35f, gy - gr * 0.35f, gr * 1.3f, 0xFF4F9BFF, 0xFF0B2E7A, Shader.TileMode.CLAMP));
            c.drawCircle(gx, gy, gr, paint);
            paint.setShader(null);
            text.setTypeface(bold);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(44);
            text.setColor(0xFFFFFFFF);
            c.drawText(getContext().getString(R.string.wx_globe_loading), gx, gy, text);
        }

        // Cities on the near side: weather icon and name; the focused one gets a card.
        if (world != null) {
            java.util.List<RectF> taken = new java.util.ArrayList<>();
            // The focused city first, so it always gets its label.
            java.util.List<Integer> order = new java.util.ArrayList<>();
            if (cityFocus >= 0) order.add(cityFocus);
            for (int i = 0; i < world.size(); i++) if (i != cityFocus) order.add(i);
            for (int i : order) {
                Weather.City city = world.get(i);
                float[] p = Globe.project(city.lat, city.lon, gx, gy, gr, tiltC, lonShown);
                if (p[2] < 0.12f) continue;
                float x = p[0], y = p[1];
                if (x < 60 || x > 1860 || y < 190 || y > 880) continue;
                RectF box = new RectF(x - 95, y - 50, x + 95, y + 40);
                boolean clash = false;
                for (RectF t : taken) if (RectF.intersects(t, box)) clash = true;
                if (clash) continue;
                taken.add(box);
                if (city.known) WeatherArt.icon(c, x, y - 14, 24 + 6 * zoom, Weather.sky(city.code), city.day);
                text.setTypeface(bold);
                text.setTextAlign(Paint.Align.CENTER);
                text.setTextSize(26 + 4 * zoom);
                text.setColor(0xFFFFFFFF);
                text.setShadowLayer(5, 0, 2, 0xEE000000);
                c.drawText(city.name, x, y + 30 + 4 * zoom, text);
                text.clearShadowLayer();
            }
            if (cityFocus >= 0 && !spinning) drawCityCard(c, world.get(cityFocus), gx, gy, gr);
        }

        drawButtonBar(c, 0, 110, getContext().getString(R.string.wx_zoom_out) + "  ⊖",
                getContext().getString(R.string.wx_next) + "  ▶", getContext().getString(R.string.wx_zoom_in) + "  ⊕", -1);
        drawButtonBar(c, 966, 1080, getContext().getString(R.string.wx_end), "", getContext().getString(R.string.wx_restore), 1);
        // "CURRENT WEATHER" tag.
        rect.set(1320, 130, 1900, 200);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(rect.left, 0, rect.right, 0, 0x0013A537, GREEN, Shader.TileMode.CLAMP));
        c.drawRect(rect, paint);
        paint.setShader(null);
        text.setTypeface(bold);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(46);
        text.setColor(0xFFFFFFFF);
        c.drawText(getContext().getString(R.string.wx_globe_tag), 1880, 182, text);
        text.setTypeface(regular);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(28);
        c.drawText(getContext().getString(R.string.wx_credits), 40, 940, text);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(getContext().getString(R.string.wx_globe_keys), 1880, 940, text);
        hand.draw(c, 990, 1015, 1.35f);

        if (spinning) postInvalidateOnAnimation();
    }

    private void drawCityCard(Canvas c, Weather.City city, float cx, float cy, float r) {
        float[] p = Globe.project(city.lat, city.lon, cx, cy, r, tiltC, lonShown);
        if (p[2] < 0.1f) return;
        float x = p[0], y = p[1];
        rect.set(x + 40, y - 150, x + 470, y - 20);
        if (rect.right > 1900) rect.offset(-(rect.width() + 80), 0);
        if (rect.top < 130) rect.offset(0, 130 - rect.top);
        paint.setColor(0xF2FFFFFF);
        c.drawRoundRect(rect, 26, 26, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        paint.setColor(WII_BLUE);
        c.drawRoundRect(rect, 26, 26, paint);
        paint.setStyle(Paint.Style.FILL);
        text.setTypeface(bold);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(44);
        text.setColor(0xFF333333);
        c.drawText(city.name, rect.left + 28, rect.top + 56, text);
        text.setTypeface(regular);
        text.setTextSize(38);
        text.setColor(0xFF555555);
        String line = city.known ? Weather.temp(city.temp) + "  " + getContext().getString(Weather.label(city.code)) : "—";
        c.drawText(line, rect.left + 28, rect.top + 108, text);
    }
}
