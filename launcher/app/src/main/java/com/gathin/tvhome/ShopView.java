package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Shop and Games channels, after the console's shop: white pages with pale blue edges and dotted rules, rounded white
 * buttons that glow blue, the title top left, and lists of titles with a picture, name, publisher and price on
 * the right ("Free"). Screens: home (categories), a category's list, a title's page, and the download screen.
 * Geometry in 1920 x 1080 reference px.
 */
final class ShopView extends View {

    interface Listener {
        void onExit();
        /** The title page's main button was pressed. */
        void onAction(ShopCatalog.App app, Action action);
        void onPlaySound(int which);
        /** A direct app's page opened: look up its latest version (answer with setLatestVersion). */
        void onLookup(ShopCatalog.App app);
    }

    enum Screen { HOME, LIST, DETAIL, DOWNLOAD }

    /** What a title page's main button does. */
    enum Action { OPEN, STORE, DOWNLOAD, WEB }

    static final String INSTALLED = "installed";
    /** Games channel: the games already on the device, opened straight from the list. */
    static final String MY_GAMES = "my_games";
    private static final int ROWS = 4;
    private static final int BLUE = 0xFF3FC0EC, EDGE = 0xFFB9E9F8, TEXT = 0xFF555555, CYAN_TEXT = 0xFF34B5E5;

    private final Listener listener;
    private final Context ctx;
    private final ShopCatalog.Store store;
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private final WiiHand hand = new WiiHand(Typeface.create("sans-serif", Typeface.BOLD));
    private final Map<String, Bitmap> thumbs = new HashMap<>();

    private ShopCatalog catalog = new ShopCatalog();
    private Screen screen = Screen.HOME;
    private int homeFocus;
    private String category = "popular";
    private String categoryName = "";
    private List<ShopCatalog.App> list = new ArrayList<>();
    /** List focus: a row index, or BACK_BTN / NEXT_BTN on the bottom bar. */
    private int listFocus;
    private int detailFocus = 1;
    private ShopCatalog.App app;
    private String latestVersion;
    private float progress = -1f;
    private String downloadStatus = "";
    private float handX = -1, handY;

    private static final int BACK_BTN = -1, NEXT_BTN = -2;

    ShopView(Context context, ShopCatalog.Store store, Listener listener) {
        super(context);
        this.ctx = context;
        this.store = store;
        this.listener = listener;
        setFocusable(true);
    }

    void setCatalog(ShopCatalog c) {
        catalog = c;
        thumbs.clear();
        if (screen == Screen.LIST) openList(category, categoryName);
        invalidate();
    }

    /** Installed apps changed (or we came back from the store / installer): redraw states and thumbnails. */
    void refreshInstalled() {
        thumbs.clear();
        if (screen == Screen.LIST) list = listFor(category);
        if (screen == Screen.DOWNLOAD && app != null && ShopCatalog.installed(ctx, app) != null) screen = Screen.DETAIL;
        invalidate();
    }

    void setLatestVersion(ShopCatalog.App a, String version) {
        if (a == app) {
            latestVersion = version;
            invalidate();
        }
    }

    void showDownload(ShopCatalog.App a, float fraction, String status) {
        app = a;
        screen = Screen.DOWNLOAD;
        progress = fraction;
        downloadStatus = status == null ? "" : status;
        invalidate();
    }

    void showDetail() {
        screen = Screen.DETAIL;
        invalidate();
    }

    Screen screen() {
        return screen;
    }

    // ---- data -------------------------------------------------------------------------------------

    private List<HomeItem> homeItems() {
        List<HomeItem> out = new ArrayList<>();
        if (store.games()) out.add(new HomeItem(MY_GAMES, ctx.getString(R.string.games_mine)));
        for (ShopCatalog.Category c : catalog.categories) out.add(new HomeItem(c.id, c.name));
        out.add(new HomeItem(INSTALLED, ctx.getString(R.string.shop_installed)));
        return out;
    }

    private static final class HomeItem {
        final String id, name;

        HomeItem(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private List<ShopCatalog.App> listFor(String cat) {
        if (MY_GAMES.equals(cat)) return ShopCatalog.installedGames(ctx, catalog);
        if (!INSTALLED.equals(cat)) return catalog.in(cat);
        List<ShopCatalog.App> out = new ArrayList<>();
        for (ShopCatalog.App a : catalog.apps) if (ShopCatalog.installed(ctx, a) != null) out.add(a);
        return out;
    }

    private void openList(String cat, String name) {
        category = cat;
        categoryName = name;
        list = listFor(cat);
        listFocus = list.isEmpty() ? BACK_BTN : 0;
        screen = Screen.LIST;
    }

    private String status(ShopCatalog.App a) {
        if (a.web()) return ctx.getString(R.string.games_web);
        if (MY_GAMES.equals(category)) return ctx.getString(R.string.games_play);
        String v = ShopCatalog.installed(ctx, a);
        if (v == null) return a.price.isEmpty() ? ctx.getString(R.string.shop_free) : a.price;
        if (a == app && latestVersion != null && !latestVersion.isEmpty() && !latestVersion.equals(v)) {
            return ctx.getString(R.string.shop_update);
        }
        return ctx.getString(R.string.shop_installed_tag);
    }

    private Action action(ShopCatalog.App a) {
        if (a.web()) return Action.WEB;
        String v = ShopCatalog.installed(ctx, a);
        if (v != null) {
            boolean newer = a.direct() && latestVersion != null && !latestVersion.isEmpty() && !latestVersion.equals(v);
            return newer ? Action.DOWNLOAD : Action.OPEN;
        }
        return a.direct() ? Action.DOWNLOAD : Action.STORE;
    }

    private String actionLabel(ShopCatalog.App a) {
        switch (action(a)) {
            case OPEN: return ctx.getString(R.string.shop_open);
            case STORE: return ctx.getString(R.string.shop_get_store);
            case WEB: return ctx.getString(R.string.games_play);
            default: return ctx.getString(ShopCatalog.installed(ctx, a) != null ? R.string.shop_update : R.string.shop_download);
        }
    }

    private String sourceLabel(ShopCatalog.App a) {
        switch (a.source) {
            case "fdroid": return "F-Droid";
            case "github": return "GitHub";
            case "url": return ctx.getString(R.string.shop_from_web);
            case "web": return ctx.getString(R.string.games_from_web);
            case "device": return ctx.getString(R.string.games_on_device);
            default: return DeviceProfile.fireTv() ? "Amazon Appstore" : "Google Play";
        }
    }

    // ---- keys -------------------------------------------------------------------------------------

    private static boolean ok(int k) {
        return k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BUTTON_A
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    boolean handleKey(int k) {
        boolean back = k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_BUTTON_B || k == KeyEvent.KEYCODE_ESCAPE;
        int before = focusKey();
        switch (screen) {
            case HOME: {
                int n = homeItems().size(); // grid of 2 columns, then Back (index n)
                if (back) {
                    listener.onExit();
                    return true;
                }
                if (k == KeyEvent.KEYCODE_DPAD_LEFT && homeFocus < n && homeFocus % 2 == 1) homeFocus--;
                else if (k == KeyEvent.KEYCODE_DPAD_RIGHT && homeFocus < n && homeFocus % 2 == 0 && homeFocus + 1 < n) homeFocus++;
                else if (k == KeyEvent.KEYCODE_DPAD_UP) homeFocus = homeFocus == n ? (n - 1) - (n - 1) % 2 : homeFocus >= 2 ? homeFocus - 2 : homeFocus;
                else if (k == KeyEvent.KEYCODE_DPAD_DOWN) homeFocus = homeFocus < n && homeFocus + 2 < n ? homeFocus + 2 : n;
                else if (ok(k)) {
                    listener.onPlaySound(WiiSounds.SELECT);
                    if (homeFocus == n) listener.onExit();
                    else openList(homeItems().get(homeFocus).id, homeItems().get(homeFocus).name);
                    invalidate();
                    return true;
                } else return false;
                break;
            }
            case LIST: {
                int pages = Math.max(1, (list.size() + ROWS - 1) / ROWS);
                if (back) {
                    listener.onPlaySound(WiiSounds.BACK);
                    screen = Screen.HOME;
                    invalidate();
                    return true;
                }
                if (k == KeyEvent.KEYCODE_DPAD_UP) {
                    if (listFocus >= 0) listFocus = Math.max(0, listFocus - 1);
                    else if (!list.isEmpty()) listFocus = Math.min(list.size() - 1, page() * ROWS + ROWS - 1);
                } else if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (listFocus >= 0 && listFocus < list.size() - 1) listFocus++;
                    else if (listFocus >= 0) listFocus = BACK_BTN;
                } else if (k == KeyEvent.KEYCODE_DPAD_LEFT) {
                    listFocus = BACK_BTN;
                } else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    listFocus = page() < pages - 1 ? NEXT_BTN : listFocus;
                } else if (ok(k)) {
                    listener.onPlaySound(WiiSounds.SELECT);
                    if (listFocus == BACK_BTN) screen = Screen.HOME;
                    else if (listFocus == NEXT_BTN) listFocus = Math.min(list.size() - 1, (page() + 1) * ROWS);
                    else if (listFocus >= 0 && MY_GAMES.equals(category)) listener.onAction(list.get(listFocus), Action.OPEN);
                    else if (listFocus >= 0) openDetail(list.get(listFocus));
                    invalidate();
                    return true;
                } else return false;
                break;
            }
            case DETAIL:
                if (back) {
                    listener.onPlaySound(WiiSounds.BACK);
                    screen = Screen.LIST;
                    invalidate();
                    return true;
                }
                if (k == KeyEvent.KEYCODE_DPAD_LEFT) detailFocus = 0;
                else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) detailFocus = 1;
                else if (ok(k)) {
                    listener.onPlaySound(WiiSounds.SELECT);
                    if (detailFocus == 0) screen = Screen.LIST;
                    else listener.onAction(app, action(app));
                    invalidate();
                    return true;
                } else return false;
                break;
            case DOWNLOAD:
                // The download runs to the end; Back returns to the title page once it has failed or finished.
                if (back || ok(k)) {
                    if (progress < 0f || progress >= 1f) {
                        screen = Screen.DETAIL;
                        invalidate();
                    }
                }
                return true;
        }
        if (focusKey() != before) listener.onPlaySound(WiiSounds.MOVE);
        invalidate();
        return true;
    }

    private int focusKey() {
        switch (screen) {
            case HOME: return homeFocus;
            case LIST: return listFocus * 31 + page();
            case DETAIL: return detailFocus;
            default: return 0;
        }
    }

    /** Straight to an app's page (from a request to get it), with its category's list behind it for Back. */
    void openApp(ShopCatalog.App a) {
        for (ShopCatalog.Category c : catalog.categories) {
            if (a.categories.contains(c.id)) {
                openList(c.id, c.name);
                break;
            }
        }
        openDetail(a);
        invalidate();
    }

    private void openDetail(ShopCatalog.App a) {
        app = a;
        latestVersion = null;
        detailFocus = 1;
        screen = Screen.DETAIL;
        if (a.direct()) listener.onLookup(a);
    }

    ShopCatalog.App app() {
        return app;
    }

    private int page() {
        int f = listFocus >= 0 ? listFocus : lastRowFocus;
        return Math.max(0, f / ROWS);
    }

    private int lastRowFocus;

    // ---- drawing ----------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        float s = getWidth() / 1920f;
        if (listFocus >= 0) lastRowFocus = listFocus;
        c.save();
        c.scale(s, s);
        drawBackdrop(c);
        float[] h;
        switch (screen) {
            case LIST: h = drawList(c); break;
            case DETAIL: h = drawDetail(c); break;
            case DOWNLOAD: h = drawDownload(c); break;
            default: h = drawHome(c); break;
        }
        if (h != null) {
            if (handX < 0) {
                handX = h[0];
                handY = h[1];
            }
            handX += (h[0] - handX) * 0.35f;
            handY += (h[1] - handY) * 0.35f;
            hand.draw(c, handX, handY, 1.5f);
            if (Math.abs(h[0] - handX) > 0.5f || Math.abs(h[1] - handY) > 0.5f) postInvalidateOnAnimation();
        }
        c.restore();
    }

    /** White page with pale blue edges and dotted rules under the title and above the buttons. */
    private void drawBackdrop(Canvas c) {
        c.drawColor(0xFFFFFFFF);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(0, 0, 260, 0, EDGE, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 260, 1080, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(1920, 0, 1660, 0, EDGE, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(1660, 0, 1920, 1080, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(0xFFB9DDEA);
        paint.setPathEffect(new DashPathEffect(new float[] {6, 8}, 0));
        c.drawLine(0, 175, 1920, 175, paint);
        c.drawLine(0, 840, 1920, 840, paint);
        paint.setPathEffect(null);
        paint.setStyle(Paint.Style.FILL);
    }

    private void title(Canvas c, String t) {
        text.setTypeface(regular);
        text.setTextSize(72);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(TEXT);
        c.drawText(t, 320, 135, text);
    }

    /** A white rounded button with a pale blue rim; focused, it glows blue like the console's. */
    private void button(Canvas c, RectF r, float rad, boolean focused) {
        paint.setColor(0x22000000);
        c.drawRoundRect(r.left + 4, r.top + 6, r.right + 4, r.bottom + 6, rad, rad, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0xFFFFFFFF, 0xFFEEF8FC, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        if (focused) {
            for (int k = 3; k >= 1; k--) {
                paint.setColor((Math.min(255, 80 / k) << 24) | (BLUE & 0xFFFFFF));
                paint.setStrokeWidth(4 + k * 6);
                c.drawRoundRect(r, rad, rad, paint);
            }
        }
        paint.setColor(focused ? BLUE : 0xFFC4E4EF);
        paint.setStrokeWidth(focused ? 6 : 4);
        c.drawRoundRect(r, rad, rad, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void label(Canvas c, String t, float x, float y, float size, int color, Paint.Align align) {
        text.setTypeface(regular);
        text.setTextSize(size);
        text.setColor(color);
        text.setTextAlign(align);
        c.drawText(t, x, y, text);
    }

    private float[] drawHome(Canvas c) {
        title(c, ctx.getString(store.games() ? R.string.games_title : R.string.shop_title));
        if (store.games()) GamesBanner.drawPad(c, 1530, 60, 200, paint);
        else ShopBanner.drawBag(c, 1560, 40, 120, paint);
        label(c, ctx.getString(store.games() ? R.string.games_welcome : R.string.shop_welcome), 320, 235, 40, 0xFF7A7A7A, Paint.Align.LEFT);
        List<HomeItem> items = homeItems();
        float[] h = null;
        // Two columns; rows squeeze together if a catalog has more than three of them.
        float step = Math.min(170f, 570f / ((items.size() + 1) / 2));
        for (int i = 0; i < items.size(); i++) {
            int col = i % 2, row = i / 2;
            rect.set(320 + col * 660, 290 + row * step, 320 + col * 660 + 620, 290 + row * step + step - 35);
            button(c, rect, 30, homeFocus == i);
            label(c, items.get(i).name, rect.centerX(), rect.centerY() + 18, 50, TEXT, Paint.Align.CENTER);
            if (homeFocus == i) h = new float[] {rect.right - 90, rect.bottom - 30};
        }
        rect.set(320, 880, 780, 1000);
        button(c, rect, 30, homeFocus == items.size());
        label(c, ctx.getString(R.string.wx_wii_menu), rect.centerX(), rect.centerY() + 18, 50, TEXT, Paint.Align.CENTER);
        if (homeFocus == items.size()) h = new float[] {rect.right - 90, rect.bottom - 30};
        drawFreeCount(c);
        return h;
    }

    /** Where the console showed your points: here, how many titles there are, all free. */
    private void drawFreeCount(Canvas c) {
        text.setTypeface(bold);
        label(c, String.valueOf(catalog.apps.size()), 1100, 945, 76, CYAN_TEXT, Paint.Align.CENTER);
        label(c, ctx.getString(store.games() ? R.string.games_count : R.string.shop_titles_free), 1100, 995, 34, 0xFF8A8A8A, Paint.Align.CENTER);
    }

    private float[] drawList(Canvas c) {
        title(c, INSTALLED.equals(category) ? ctx.getString(R.string.shop_installed) : categoryName);
        float[] h = null;
        int pages = Math.max(1, (list.size() + ROWS - 1) / ROWS);
        int pg = page();
        // Panel.
        rect.set(300, 205, 1460, 820);
        paint.setColor(0xFFFFFFFF);
        c.drawRoundRect(rect, 16, 16, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(0xFFD3ECF5);
        c.drawRoundRect(rect, 16, 16, paint);
        paint.setStyle(Paint.Style.FILL);
        if (list.isEmpty()) {
            label(c, ctx.getString(R.string.shop_none), 880, 520, 44, 0xFF8A8A8A, Paint.Align.CENTER);
        }
        for (int r = 0; r < ROWS; r++) {
            int i = pg * ROWS + r;
            if (i >= list.size()) break;
            ShopCatalog.App a = list.get(i);
            RectF row = new RectF(320, 220 + r * 150, 1440, 220 + r * 150 + 136);
            button(c, row, 18, listFocus == i);
            RectF th = new RectF(row.left + 14, row.top + 12, row.left + 14 + 200, row.bottom - 12);
            drawThumb(c, a, th);
            text.setTypeface(regular);
            text.setTextSize(44);
            text.setColor(0xFF444444);
            text.setTextAlign(Paint.Align.LEFT);
            c.drawText(a.name, th.right + 30, row.top + 50, text);
            paint.setColor(0xFFDDEFF5);
            c.drawRect(th.right + 30, row.top + 66, row.right - 30, row.top + 69, paint);
            label(c, a.publisher.isEmpty() ? sourceLabel(a) : a.publisher + "  \u00B7  " + sourceLabel(a), th.right + 30, row.top + 112, 32, 0xFF888888, Paint.Align.LEFT);
            label(c, status(a), row.right - 30, row.bottom - 20, 40, TEXT, Paint.Align.RIGHT);
            if (listFocus == i) h = new float[] {row.right - 160, row.bottom - 20};
        }
        // Scroll bar column, as on the console.
        RectF up = new RectF(1480, 205, 1580, 305), down = new RectF(1480, 720, 1580, 820);
        button(c, up, 14, false);
        button(c, down, 14, false);
        triangle(c, up.centerX(), up.centerY(), true);
        triangle(c, down.centerX(), down.centerY(), false);
        paint.setColor(0xFFE6E6E6);
        c.drawRect(1495, 315, 1565, 710, paint);
        float span = 395f / pages;
        paint.setColor(0xFFC9C9C9);
        c.drawRect(1495, 315 + pg * span, 1565, 315 + (pg + 1) * span, paint);
        // Bottom bar.
        rect.set(320, 880, 780, 1000);
        button(c, rect, 30, listFocus == BACK_BTN);
        label(c, ctx.getString(R.string.set_back), rect.centerX(), rect.centerY() + 18, 50, TEXT, Paint.Align.CENTER);
        if (listFocus == BACK_BTN) h = new float[] {rect.right - 90, rect.bottom - 30};
        drawFreeCount(c);
        label(c, (pg + 1) + "/" + pages, 1390, 960, 48, TEXT, Paint.Align.CENTER);
        RectF next = new RectF(1480, 885, 1590, 995);
        button(c, next, 16, listFocus == NEXT_BTN);
        arrow(c, next.centerX(), next.centerY(), pg < pages - 1);
        if (listFocus == NEXT_BTN) h = new float[] {next.right - 30, next.bottom - 20};
        return h;
    }

    private void triangle(Canvas c, float cx, float cy, boolean upward) {
        path.reset();
        float d = upward ? -1 : 1;
        path.moveTo(cx, cy + d * 26);
        path.lineTo(cx - 30, cy - d * 20);
        path.lineTo(cx + 30, cy - d * 20);
        path.close();
        paint.setColor(BLUE);
        c.drawPath(path, paint);
    }

    private void arrow(Canvas c, float cx, float cy, boolean on) {
        path.reset();
        path.moveTo(cx - 30, cy - 12);
        path.lineTo(cx + 2, cy - 12);
        path.lineTo(cx + 2, cy - 28);
        path.lineTo(cx + 34, cy);
        path.lineTo(cx + 2, cy + 28);
        path.lineTo(cx + 2, cy + 12);
        path.lineTo(cx - 30, cy + 12);
        path.close();
        paint.setColor(on ? BLUE : 0xFFCCCCCC);
        c.drawPath(path, paint);
    }

    /** The title's picture: its TV banner (or icon) once installed, else a tile in its colour with its name. */
    private void drawThumb(Canvas c, ShopCatalog.App a, RectF r) {
        Bitmap b = thumbs.get(a.pkg);
        if (b == null && !thumbs.containsKey(a.pkg)) {
            String pkg = ShopCatalog.installedPkg(ctx, a);
            b = pkg == null ? null : installedArt(pkg, Math.round(r.width()), Math.round(r.height()));
            thumbs.put(a.pkg, b);
        }
        int sv = c.save();
        path.reset();
        path.addRoundRect(r, 10, 10, Path.Direction.CW);
        c.clipPath(path);
        if (b != null) {
            c.drawBitmap(b, null, r, paint);
        } else {
            paint.setColor(0xFFFFFFFF);
            paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, a.color, darken(a.color), Shader.TileMode.CLAMP));
            c.drawRect(r, paint);
            paint.setShader(null);
            text.setTypeface(bold);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(0xFFFFFFFF);
            // Shrink a long name to fit (down to 70%), then ellipsize what still doesn't.
            text.setTextSize(r.height() * 0.24f);
            float fit = (r.width() - 24) / text.measureText(a.name);
            if (fit < 1f) text.setTextSize(r.height() * 0.24f * Math.max(0.7f, fit));
            CharSequence t = TextUtils.ellipsize(a.name, text, r.width() - 24, TextUtils.TruncateAt.END);
            c.drawText(t, 0, t.length(), r.centerX(), r.centerY() + r.height() * 0.09f, text);
        }
        c.restoreToCount(sv);
    }

    private Bitmap installedArt(String pkg, int w, int h) {
        try {
            android.content.pm.PackageManager pm = ctx.getPackageManager();
            Drawable d = pm.getApplicationBanner(pkg);
            boolean banner = d != null;
            if (d == null) d = pm.getApplicationIcon(pkg);
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            if (banner) {
                d.setBounds(0, 0, w, h);
            } else {
                cv.drawColor(0xFFF2F8FB);
                int size = Math.round(h * 0.8f);
                d.setBounds((w - size) / 2, (h - size) / 2, (w + size) / 2, (h + size) / 2);
            }
            d.draw(cv);
            return b;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static int darken(int c) {
        int r = (int) (((c >> 16) & 0xFF) * 0.6f), g = (int) (((c >> 8) & 0xFF) * 0.6f), b = (int) ((c & 0xFF) * 0.6f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private float[] drawDetail(Canvas c) {
        ShopCatalog.App a = app;
        if (a == null) return null;
        title(c, a.name);
        RectF th = new RectF(320, 220, 820, 501);
        drawThumb(c, a, th);
        label(c, a.publisher, 880, 270, 44, TEXT, Paint.Align.LEFT);
        label(c, ctx.getString(R.string.shop_from, sourceLabel(a)), 880, 335, 36, 0xFF888888, Paint.Align.LEFT);
        String v = ShopCatalog.installed(ctx, a);
        String ver = v != null ? ctx.getString(R.string.shop_have_version, v.isEmpty() ? "?" : v) : "";
        if (a.direct() && latestVersion != null && !latestVersion.isEmpty()) {
            ver = (ver.isEmpty() ? "" : ver + "   ") + ctx.getString(R.string.shop_latest_version, latestVersion);
        }
        label(c, ver, 880, 395, 34, 0xFF888888, Paint.Align.LEFT);
        if (!a.account.isEmpty()) label(c, ctx.getString(R.string.games_account, a.account), 880, 455, 34, 0xFFD08A1E, Paint.Align.LEFT);
        label(c, a.price.isEmpty() ? ctx.getString(R.string.shop_free) : a.price, 1440, 520, 54, CYAN_TEXT, Paint.Align.RIGHT);
        TextPaint body = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        body.setTypeface(regular);
        body.setTextSize(40);
        body.setColor(0xFF606060);
        StaticLayout layout = StaticLayout.Builder.obtain(a.about, 0, a.about.length(), body, 1120)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.25f).setMaxLines(5).build();
        int sv = c.save();
        c.translate(320, 560);
        layout.draw(c);
        c.restoreToCount(sv);

        float[] h = null;
        rect.set(320, 880, 780, 1000);
        button(c, rect, 30, detailFocus == 0);
        label(c, ctx.getString(R.string.set_back), rect.centerX(), rect.centerY() + 18, 50, TEXT, Paint.Align.CENTER);
        if (detailFocus == 0) h = new float[] {rect.right - 90, rect.bottom - 30};
        rect.set(980, 880, 1580, 1000);
        button(c, rect, 30, detailFocus == 1);
        label(c, actionLabel(a), rect.centerX(), rect.centerY() + 18, 50, detailFocus == 1 ? 0xFF2E86BF : TEXT, Paint.Align.CENTER);
        if (detailFocus == 1) h = new float[] {rect.right - 90, rect.bottom - 30};
        return h;
    }

    private float[] drawDownload(Canvas c) {
        ShopCatalog.App a = app;
        title(c, a == null ? "" : a.name);
        boolean failed = progress < 0f && !downloadStatus.isEmpty();
        String head = failed ? downloadStatus : progress >= 1f ? ctx.getString(R.string.shop_installing)
                : ctx.getString(R.string.shop_downloading);
        label(c, head, 960, 430, 60, TEXT, Paint.Align.CENTER);
        // Progress bar: a white track with the blue fill, percent beside it.
        rect.set(460, 500, 1460, 560);
        paint.setColor(0xFFF0F0F0);
        c.drawRoundRect(rect, 30, 30, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        paint.setColor(0xFFC4E4EF);
        c.drawRoundRect(rect, 30, 30, paint);
        paint.setStyle(Paint.Style.FILL);
        float f = Math.max(0f, Math.min(1f, progress));
        if (f > 0f) {
            RectF fill = new RectF(rect.left + 6, rect.top + 6, rect.left + 6 + (rect.width() - 12) * f, rect.bottom - 6);
            paint.setColor(0xFFFFFFFF);
            paint.setShader(new LinearGradient(0, fill.top, 0, fill.bottom, 0xFF7FD6F7, 0xFF1F9AD6, Shader.TileMode.CLAMP));
            c.drawRoundRect(fill, 24, 24, paint);
            paint.setShader(null);
        }
        label(c, Math.round(f * 100) + "%", 1520, 548, 44, TEXT, Paint.Align.LEFT);
        label(c, failed ? ctx.getString(R.string.shop_press_back) : ctx.getString(R.string.shop_dont_turn_off),
                960, 660, 40, 0xFF8A8A8A, Paint.Align.CENTER);
        return null;
    }
}
