package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * A home screen after the old console channel menu: twelve rounded channel frames to a page with the next
 * page's first column at the edge, a striped backdrop, a curved blue line over a shaded panel holding a
 * segment clock, the date and two round buttons in tracks, and a pointing hand that follows the focus.
 *
 * Geometry is in pixels of a 1920 x 1080 capture of the original ("ref px"), measured off it, and scaled by
 * the view width. The static backdrop and the button bodies are computed per pixel in WiiBase off the UI
 * thread; everything that moves is drawn here. Frames are only drawn while something moves.
 */
public class WiiMenuView extends View {

    interface Listener {
        void onChannel(Channel channel);
        void onSettingsButton();
        void onSdButton();
        void onInputsButton();
    }

    static final class Channel {
        final String key;
        final String label;
        final Drawable banner;
        final Drawable icon;
        final Intent intent;
        Bitmap art;
        BitmapShader shader;

        Channel(String key, String label, Drawable banner, Drawable icon, Intent intent) {
            this.key = key;
            this.label = label;
            this.banner = banner;
            this.icon = icon;
            this.intent = intent;
        }
    }

    static final class MenuItem {
        final String label;
        final Runnable action;

        MenuItem(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }

    private static final int SLOTS_PER_PAGE = 12;
    private static final int MIN_PAGES = 4;
    static final int BTN_SETTINGS = -1;
    static final int BTN_SD = -2;
    static final int BTN_INPUTS = -3;
    private static final long INTRO_AUTO_MS = 12_000L;

    // Channel frames: the box through the corner arcs' ends; the sides bow out by TILE_BULGE in the middle.
    private static final float TILE_X0 = 163f;
    private static final float TILE_Y0 = 93.5f;
    private static final float TILE_W = 381f;
    private static final float TILE_H = 204.5f;
    private static final float COL_PITCH = 404f;
    private static final float ROW_PITCH = 228f;
    private static final float PAGE_W = COL_PITCH * 4;
    private static final float TILE_R = 25f;
    private static final float TILE_BULGE = 3f;
    private static final float TILE_BORDER = 4.5f;
    private static final int TILE_BORDER_COLOR = 0xFFB5B5B5;

    private static final float ARROW_L = 1797.5f;
    private static final float ARROW_TIP = 1850f;
    private static final float ARROW_T = 365f;
    private static final float ARROW_B = 462.5f;
    private static final float ARROW_Y = 414f;

    private static final float BTN_RIGHT_CX = 2f * WiiBase.CX - WiiBase.BTN_CX;
    private static final float SD_L = 356f;
    private static final float SD_T = 897f;
    private static final float SD_R = 424f;
    private static final float SD_B = 994f;

    private static final float DIGIT_W = 50f;
    private static final float DIGIT_H = 79f;
    private static final float DIGIT_T = 12f;
    private static final float DIGIT_GAP = 2.5f;
    private static final float CLOCK_TOP = 789f;
    private static final float[] DIGIT_X = {825f, 886f, 983f, 1044f};
    private static final float COLON_X = 959.5f;
    private static final float COLON_DOT = 11f;
    private static final float COLON_Y1 = 811.5f;
    private static final float COLON_Y2 = 844.5f;
    private static final float AMPM_X = 1142f;
    private static final float AMPM_P_W = 24f;
    private static final float AMPM_M_X = 1172f;
    private static final float AMPM_M_W = 28f;
    private static final float AMPM_BASE = 869f;
    private static final float AMPM_CAP = 34f;
    private static final float DATE_TRACKING = 2f;
    private static final float DATE_EXTRA_SPACE = 3f;
    private static final float DATE_BASE = 981f;
    private static final float DATE_CAP = 55f;
    private static final int CLOCK_COLOR = 0xFF9A9A9A;
    private static final int DATE_COLOR = 0xFF7C7C7C;
    /** Fingertip of the hand in the reference capture. */
    private static final float HAND_REF_X = 1072f;
    private static final float HAND_REF_Y = 894f;

    private static final int BLUE = 0xFF3FC0EC;
    /** Segment bits a-g per digit; the original's 7 keeps the upper-left bar. */
    private static final int[] SEGMENTS = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x27, 0x7F, 0x6F};
    /** The previous version's hand was drawn in units of 1/1024 of the width. */
    private static final float HAND_UNIT = 1920f / 1024f;

    private final Listener listener;
    private final List<Channel> channels = new ArrayList<>();
    private int pages = MIN_PAGES;
    private int focus;
    private int page;
    private float pagePos;
    private float[] lift = new float[MIN_PAGES * SLOTS_PER_PAGE + 3];
    private float handX, handY;
    private boolean handPlaced;
    private long lastFrameNanos;
    private boolean started;
    private boolean colonOn = true;
    private boolean refPose;
    private String settingsLabel = "wiiOS";
    private WiiSounds sounds;

    private float introAlpha;
    private boolean introLeaving;
    private long introStartMs;
    private String introTitle = "";
    private String introBodyText = "";
    private String introContinue = "";
    private StaticLayout introBody;

    private List<MenuItem> menuItems = new ArrayList<>();
    private String menuTitle = "";
    private int menuFocus;
    private float menuAlpha;
    private boolean menuOpen;
    private float[] menuLift = new float[0];

    private float s = 1f;
    private int w = 1, h = 1;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF tmp = new RectF();
    private final Matrix matrix = new Matrix();
    private final Path path = new Path();
    private final float[] point = new float[2];
    private final Typeface face;
    private final Typeface faceBold;
    private final Typeface sansBold = Typeface.create("sans-serif", Typeface.BOLD);

    private volatile int buildGen;
    private Bitmap base, btnLeft, btnRight, emptyArt;
    private BitmapShader emptyShader;
    private Path tilePath, arrowPath, handPath, handLines, sdPath;
    private CornerPathEffect arrowCorners, digitCorners;

    private final Runnable blink = new Runnable() {
        @Override
        public void run() {
            colonOn = !colonOn;
            invalidate();
            if (started && !refPose) postDelayed(this, 1000);
        }
    };
    private final Runnable autoContinue = this::dismissIntro;

    WiiMenuView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        Typeface f;
        try {
            f = context.getResources().getFont(R.font.outfit_medium);
        } catch (RuntimeException e) {
            f = Typeface.SANS_SERIF;
        }
        face = f;
        faceBold = weight(context, f, 680);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    /** The variable font at a real weight; Typeface.create(f, BOLD) would only smear the 500 outlines. */
    private static Typeface weight(Context context, Typeface fallback, int wght) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Font font = new Font.Builder(context.getResources(), R.font.outfit)
                        .setFontVariationSettings("'wght' " + wght)
                        .setWeight(Math.min(1000, Math.max(1, wght)))
                        .build();
                return new Typeface.CustomFallbackBuilder(new FontFamily.Builder(font).build()).build();
            } catch (Exception e) {
                // fall through
            }
        }
        return Typeface.create(fallback, Typeface.BOLD);
    }

    // ---- state -----------------------------------------------------------------------------------

    void setChannels(List<Channel> list) {
        channels.clear();
        channels.addAll(list);
        pages = Math.max(MIN_PAGES, (channels.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
        int n = pages * SLOTS_PER_PAGE + 3;
        if (lift.length != n) lift = new float[n];
        if (focus >= pages * SLOTS_PER_PAGE) focus = 0;
        if (page >= pages) {
            page = 0;
            pagePos = 0f;
        }
        invalidate();
    }

    void setIntroText(String title, String body, String cont) {
        introTitle = title;
        introBodyText = body;
        introContinue = cont;
        introBody = null;
    }

    void setSettingsLabel(String label) {
        settingsLabel = label;
        rebuild();
    }

    /** Freezes the screen in the state of the reference capture (2:07 PM, Fri 3/22, hand on the date). */
    void setRefPose(boolean on) {
        refPose = on;
        if (on) {
            colonOn = false;
            focus = Integer.MIN_VALUE;
            introAlpha = 0f;
            introLeaving = false;
            handPlaced = false;
        }
        invalidate();
    }

    void start() {
        if (started) return;
        started = true;
        lastFrameNanos = 0;
        removeCallbacks(blink);
        if (!refPose) postDelayed(blink, 1000);
        if (introAlpha > 0f && !introLeaving) postDelayed(autoContinue, INTRO_AUTO_MS);
        invalidate();
    }

    void stop() {
        started = false;
        removeCallbacks(blink);
        removeCallbacks(autoContinue);
    }

    void playIntro() {
        if (refPose) return;
        menuOpen = false;
        menuAlpha = 0f;
        introAlpha = 1f;
        introLeaving = false;
        introStartMs = SystemClock.uptimeMillis();
        removeCallbacks(autoContinue);
        postDelayed(autoContinue, INTRO_AUTO_MS);
        lastFrameNanos = 0;
        invalidate();
    }

    void dismissIntro() {
        if (introAlpha <= 0f || introLeaving) return;
        introLeaving = true;
        removeCallbacks(autoContinue);
        invalidate();
    }

    void showMenu(String title, List<MenuItem> items) {
        if (items.isEmpty()) return;
        menuTitle = title;
        menuItems = new ArrayList<>(items);
        menuFocus = 0;
        menuLift = new float[menuItems.size()];
        menuOpen = true;
        invalidate();
    }

    void closeMenu() {
        menuOpen = false;
        invalidate();
    }

    /** Back: closes the menu or the welcome screen. False when there was nothing to close. */
    boolean back() {
        if (introAlpha > 0f) {
            dismissIntro();
            return true;
        }
        if (menuOpen) {
            sound(WiiSounds.BACK);
            closeMenu();
            return true;
        }
        return false;
    }

    void setSounds(WiiSounds sounds) {
        this.sounds = sounds;
    }

    private void sound(int which) {
        if (sounds != null) sounds.play(which);
    }

    // ---- keys ------------------------------------------------------------------------------------

    private static boolean isOk(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A;
    }

    private static boolean isDpad(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    boolean handleKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE || keyCode == KeyEvent.KEYCODE_BACK) {
            return false;
        }
        if (refPose) {
            if (!isOk(keyCode) && !isDpad(keyCode)) return false;
            refPose = false;
            focus = 0;
            removeCallbacks(blink);
            postDelayed(blink, 1000);
            invalidate();
            return true;
        }
        if (introAlpha > 0f) {
            // Only OK and the arrows continue: the power or wake-up key that turned the TV on also arrives
            // here, and must not skip the screen it just brought up.
            if (!isOk(keyCode) && !isDpad(keyCode)) return false;
            if (!introLeaving) {
                sound(WiiSounds.START);
                dismissIntro();
            }
            return true;
        }
        if (menuOpen) return menuKey(keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                move(-1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                move(1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                move(0, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                move(0, 1);
                return true;
            default:
                if (isOk(keyCode)) {
                    activate();
                    return true;
                }
                return false;
        }
    }

    private void move(int dx, int dy) {
        int before = focus, pageBefore = page;
        if (focus >= 0) {
            int pg = focus / SLOTS_PER_PAGE, i = focus % SLOTS_PER_PAGE, row = i / 4, col = i % 4;
            if (dx < 0) {
                if (col > 0) {
                    focus--;
                } else if (pg > 0) {
                    focus = (pg - 1) * SLOTS_PER_PAGE + row * 4 + 3;
                    page = pg - 1;
                }
            } else if (dx > 0) {
                if (col < 3) {
                    focus++;
                } else if (pg < pages - 1) {
                    focus = (pg + 1) * SLOTS_PER_PAGE + row * 4;
                    page = pg + 1;
                }
            } else if (dy < 0) {
                if (row > 0) focus -= 4;
            } else if (row < 2) {
                focus += 4;
            } else {
                focus = col == 0 ? BTN_SETTINGS : col == 1 ? BTN_SD : BTN_INPUTS;
            }
        } else if (dy < 0) {
            int col = focus == BTN_SETTINGS ? 0 : focus == BTN_SD ? 1 : 3;
            focus = page * SLOTS_PER_PAGE + 8 + col;
        } else if (dx < 0) {
            if (focus == BTN_SD) focus = BTN_SETTINGS;
            else if (focus == BTN_INPUTS) focus = BTN_SD;
        } else if (dx > 0) {
            if (focus == BTN_SETTINGS) focus = BTN_SD;
            else if (focus == BTN_SD) focus = BTN_INPUTS;
        }
        if (focus != before) {
            sound(page != pageBefore ? WiiSounds.PAGE : WiiSounds.MOVE);
            invalidate();
        }
    }

    private void activate() {
        sound(focus == BTN_INPUTS ? WiiSounds.MENU : WiiSounds.SELECT);
        if (focus == BTN_SETTINGS) listener.onSettingsButton();
        else if (focus == BTN_SD) listener.onSdButton();
        else if (focus == BTN_INPUTS) listener.onInputsButton();
        else if (focus >= 0 && focus < channels.size()) listener.onChannel(channels.get(focus));
    }

    private boolean menuKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (menuFocus > 0) {
                menuFocus--;
                sound(WiiSounds.MOVE);
                invalidate();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (menuFocus < menuItems.size() - 1) {
                menuFocus++;
                sound(WiiSounds.MOVE);
                invalidate();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
        if (isOk(keyCode)) {
            MenuItem item = menuItems.get(menuFocus);
            sound(WiiSounds.SELECT);
            closeMenu();
            item.action.run();
            return true;
        }
        return false;
    }

    // ---- geometry --------------------------------------------------------------------------------

    private int liftIndex(int f) {
        return f >= 0 ? f : pages * SLOTS_PER_PAGE + (-f - 1);
    }

    private void slotRect(int slot, float pos, RectF out) {
        int pg = slot / SLOTS_PER_PAGE, i = slot % SLOTS_PER_PAGE, row = i / 4, col = i % 4;
        float left = (TILE_X0 + (pg * 4 + col) * COL_PITCH - pos * PAGE_W) * s;
        float top = (TILE_Y0 + row * ROW_PITCH) * s;
        out.set(left, top, left + TILE_W * s, top + TILE_H * s);
    }

    /** Where the hand's fingertip goes for this focus. */
    private void handTarget(int f, float[] out) {
        if (refPose || f == Integer.MIN_VALUE) {
            out[0] = HAND_REF_X * s;
            out[1] = HAND_REF_Y * s;
        } else if (f >= 0) {
            slotRect(f, page, tmp);
            out[0] = tmp.left + tmp.width() * 0.70f;
            out[1] = tmp.top + tmp.height() * 0.80f;
        } else if (f == BTN_SD) {
            out[0] = ((SD_L + SD_R) / 2f + 10f) * s;
            out[1] = ((SD_T + SD_B) / 2f + 14f) * s;
        } else {
            float cx = f == BTN_SETTINGS ? WiiBase.BTN_CX : BTN_RIGHT_CX;
            out[0] = (cx + 26f) * s;
            out[1] = (WiiBase.BTN_CY + 22f) * s;
        }
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        w = Math.max(1, nw);
        h = Math.max(1, nh);
        s = w / 1920f;
        tilePath = buildTilePath(TILE_W * s, TILE_H * s, TILE_R * s, TILE_BULGE * s);
        arrowPath = buildArrow();
        arrowCorners = new CornerPathEffect(7f * s);
        digitCorners = new CornerPathEffect(2.5f * s);
        buildHand();
        sdPath = buildSd();
        introBody = null;
        for (Channel c : channels) {
            c.art = null;
            c.shader = null;
        }
        handPlaced = false;
        rebuild();
    }

    /** Recomputes the per-pixel layers off the UI thread; until they arrive the screen shows plain grey. */
    private void rebuild() {
        if (w <= 1 || h <= 1) return;
        final int gen = ++buildGen;
        final int bw = w, bh = h;
        final float bs = s;
        final String label = settingsLabel;
        new Thread(() -> {
            int[] px = WiiBase.backdrop(bw, bh);
            Bitmap bg = Bitmap.createBitmap(px, bw, bh, Bitmap.Config.ARGB_8888);
            int size = (int) Math.ceil(2f * (WiiBase.BTN_RING + WiiBase.BTN_RING_HALF + 2f) * bs);
            int[] bp = WiiBase.button(size, bs);
            Bitmap left = Bitmap.createBitmap(bp, size, size, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true);
            Bitmap right = left.copy(Bitmap.Config.ARGB_8888, true);
            drawWordmark(new Canvas(left), size, bs, label);
            drawEnvelope(new Canvas(right), size, bs);
            int tw = Math.round(TILE_W * bs) + 8, th = Math.round(TILE_H * bs) + 8;
            Bitmap empty = Bitmap.createBitmap(WiiBase.emptySlot(tw, th, bs), tw, th, Bitmap.Config.ARGB_8888);
            post(() -> {
                if (gen != buildGen) return;
                base = bg;
                btnLeft = left;
                btnRight = right;
                emptyArt = empty;
                emptyShader = new BitmapShader(empty, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                invalidate();
            });
        }, "wii-base").start();
    }

    private void drawWordmark(Canvas c, int size, float bs, String label) {
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(sansBold);
        tp.setTextAlign(Paint.Align.CENTER);
        float maxW = 128f * bs;
        tp.setTextSize(52f * bs);
        float tw = tp.measureText(label);
        if (tw > maxW) tp.setTextSize(52f * bs * maxW / tw);
        float y = size / 2f + 17f * bs;
        tp.setColor(0x99FFFFFF);
        c.drawText(label, size / 2f, y + 2f * bs, tp);
        tp.setColor(0xFFA4A4A4);
        c.drawText(label, size / 2f, y, tp);
    }

    private void drawEnvelope(Canvas c, int size, float bs) {
        float cx = size / 2f, cy = size / 2f;
        float ew = 97f * bs, eh = 65f * bs;
        RectF r = new RectF(cx - ew / 2f, cy - eh / 2f, cx + ew / 2f, cy + eh / 2f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF939393);
        c.drawRoundRect(r, 5f * bs, 5f * bs, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4.5f * bs);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(0xFFE2E2E2);
        Path v = new Path();
        v.moveTo(r.left + 2f * bs, r.top + 14f * bs);
        v.lineTo(cx, r.top + 38f * bs);
        v.lineTo(r.right - 2f * bs, r.top + 14f * bs);
        c.drawPath(v, p);
    }

    /** A channel frame: rounded corners joined by sides that bow out by b in the middle. */
    private static Path buildTilePath(float tw, float th, float r, float b) {
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

    /** Right-pointing arrow around its tip at (0, 0); the back edge curves in toward the tip. */
    private Path buildArrow() {
        float l = (ARROW_L - ARROW_TIP) * s, t = (ARROW_T - ARROW_Y) * s, b = (ARROW_B - ARROW_Y) * s;
        Path p = new Path();
        p.moveTo(0f, 0f);
        p.lineTo(l, t);
        p.quadTo(l + 12f * s, 0f, l, b);
        p.close();
        return p;
    }

    /**
     * The pointing hand, fingertip at (0, 0): index finger up, three curled fingers, palm and thumb, in hand
     * units of HAND_UNIT ref px.
     */
    private void buildHand() {
        float u = HAND_UNIT * s;
        Path p = new Path();
        p.addRoundRect(new RectF(0f, 0f, 11f * u, 34f * u), 5.5f * u, 5.5f * u, Path.Direction.CW);
        union(p, u, 10f, 16f, 19f, 34f, 4.5f);
        union(p, u, 18f, 19f, 26f, 36f, 4f);
        union(p, u, 25f, 22f, 32f, 38f, 3.5f);
        union(p, u, -3f, 26f, 32f, 50f, 9f);
        union(p, u, -11f, 25f, 4f, 37f, 6f);
        Path lines = new Path();
        lines.moveTo(10.6f * u, 23f * u);
        lines.lineTo(10.6f * u, 31f * u);
        lines.moveTo(18.4f * u, 25f * u);
        lines.lineTo(18.4f * u, 33f * u);
        lines.moveTo(25.6f * u, 28f * u);
        lines.lineTo(25.6f * u, 35f * u);
        lines.moveTo(3f * u, 32f * u);
        lines.lineTo(9f * u, 34f * u);
        Matrix m = new Matrix();
        m.setTranslate(-5.5f * u, 0f);
        p.transform(m);
        lines.transform(m);
        handPath = p;
        handLines = lines;
    }

    private static void union(Path p, float u, float l, float t, float r, float b, float rad) {
        Path q = new Path();
        q.addRoundRect(new RectF(l * u, t * u, r * u, b * u), rad * u, rad * u, Path.Direction.CW);
        p.op(q, Path.Op.UNION);
    }

    private Path buildSd() {
        float l = SD_L * s, t = SD_T * s, r = SD_R * s, b = SD_B * s, cut = 9f * s;
        Path p = new Path();
        p.moveTo(l, t);
        p.lineTo(r - cut, t);
        p.lineTo(r, t + cut);
        p.lineTo(r, b);
        p.lineTo(l, b);
        p.close();
        return p;
    }

    // ---- animation -------------------------------------------------------------------------------

    private static float approach(float v, float target, float k, float eps) {
        float n = v + (target - v) * k;
        return Math.abs(target - n) < eps ? target : n;
    }

    /** Advances every animated value by dt seconds; true while anything is still moving. */
    private boolean step(float dt) {
        float kPage = 1f - (float) Math.exp(-dt / 0.11f);
        float kLift = 1f - (float) Math.exp(-dt / 0.07f);
        float kHand = 1f - (float) Math.exp(-dt / 0.06f);
        float kMenu = 1f - (float) Math.exp(-dt / 0.08f);

        pagePos = approach(pagePos, page, kPage, 0.0008f);
        boolean moving = pagePos != page;

        int fi = focus == Integer.MIN_VALUE ? -1000 : liftIndex(focus);
        for (int i = 0; i < lift.length; i++) {
            float t = i == fi ? 1f : 0f;
            lift[i] = approach(lift[i], t, kLift, 0.002f);
            if (lift[i] != t) moving = true;
        }

        handTarget(focus, point);
        if (!handPlaced) {
            handX = point[0];
            handY = point[1];
            handPlaced = true;
        }
        handX = approach(handX, point[0], kHand, 0.3f);
        handY = approach(handY, point[1], kHand, 0.3f);
        if (handX != point[0] || handY != point[1]) moving = true;

        float mt = menuOpen ? 1f : 0f;
        menuAlpha = approach(menuAlpha, mt, kMenu, 0.004f);
        if (menuAlpha != mt) moving = true;
        for (int i = 0; i < menuLift.length; i++) {
            float t = menuOpen && i == menuFocus ? 1f : 0f;
            menuLift[i] = approach(menuLift[i], t, kLift, 0.002f);
            if (menuLift[i] != t) moving = true;
        }

        if (introLeaving) {
            introAlpha = approach(introAlpha, 0f, 1f - (float) Math.exp(-dt / 0.2f), 0.004f);
            if (introAlpha == 0f) introLeaving = false;
            else moving = true;
        }
        return moving;
    }

    // ---- drawing ---------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 1f / 60f : Math.min(0.05f, (now - lastFrameNanos) / 1e9f);
        lastFrameNanos = now;
        boolean moving = step(dt);

        if (base != null) c.drawBitmap(base, 0f, 0f, null);
        else c.drawColor(0xFFE9E9E9);
        drawTiles(c);
        drawArrows(c);
        drawButton(c, btnLeft, WiiBase.BTN_CX, BTN_SETTINGS);
        drawButton(c, btnRight, BTN_RIGHT_CX, BTN_INPUTS);
        drawSd(c);
        drawClock(c);
        drawDate(c);
        drawHand(c);
        if (menuAlpha > 0f) drawMenu(c);
        if (introAlpha > 0f) drawIntro(c);

        if (moving || introAlpha > 0f) postInvalidateOnAnimation();
        else lastFrameNanos = 0;
    }

    private void drawTiles(Canvas c) {
        int total = pages * SLOTS_PER_PAGE;
        for (int sl = 0; sl < total; sl++) {
            if (sl == focus) continue;
            slotRect(sl, pagePos, rect);
            if (rect.right < -10f * s || rect.left > w + 10f * s) continue;
            drawTile(c, sl, rect, lift[sl]);
        }
        if (focus >= 0) {
            slotRect(focus, pagePos, rect);
            if (rect.right >= 0 && rect.left <= w) drawTile(c, focus, rect, lift[focus]);
        }
    }

    private void drawTile(Canvas c, int slot, RectF r, float lf) {
        int save = c.save();
        c.translate(r.left, r.top);
        Channel ch = slot < channels.size() ? channels.get(slot) : null;
        if (ch != null) {
            ensureArt(ch, Math.round(r.width()), Math.round(r.height()));
            artPaint.setShader(ch.shader);
            c.drawPath(tilePath, artPaint);
            artPaint.setShader(null);
        } else if (emptyShader != null) {
            matrix.setTranslate(-4f, -4f);
            emptyShader.setLocalMatrix(matrix);
            artPaint.setShader(emptyShader);
            c.drawPath(tilePath, artPaint);
            artPaint.setShader(null);
        } else {
            fill.setColor(0xFFD6D6D6);
            c.drawPath(tilePath, fill);
        }
        if (lf > 0.01f) {
            for (int k = 3; k >= 1; k--) {
                stroke.setColor(withAlpha(BLUE, (int) (lf * 64 / k)));
                stroke.setStrokeWidth((TILE_BORDER + k * 5f) * s);
                c.drawPath(tilePath, stroke);
            }
        }
        stroke.setColor(blend(TILE_BORDER_COLOR, BLUE, lf));
        stroke.setStrokeWidth((TILE_BORDER + 1.5f * lf) * s);
        c.drawPath(tilePath, stroke);
        c.restoreToCount(save);
    }

    private void ensureArt(Channel ch, int tw, int th) {
        tw = Math.max(1, tw);
        th = Math.max(1, th);
        if (ch.art != null && ch.art.getWidth() == tw && ch.art.getHeight() == th) return;
        Bitmap bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.drawColor(0xFFFFFFFF);
        if (ch.banner != null) {
            drawCropped(cv, ch.banner, tw, th);
        } else {
            Paint bg = new Paint();
            bg.setShader(new LinearGradient(0, 0, 0, th, 0xFFFFFFFF, 0xFFE9E9E9, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, tw, th, bg);
            if (ch.icon != null) {
                int size = Math.round(th * 0.46f);
                int left = (tw - size) / 2, top = Math.round(th * 0.12f);
                ch.icon.setBounds(left, top, left + size, top + size);
                ch.icon.draw(cv);
            }
            TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            tp.setTypeface(sansBold);
            tp.setTextSize(th * 0.15f);
            tp.setColor(0xFF7A7A7A);
            tp.setTextAlign(Paint.Align.CENTER);
            CharSequence label = TextUtils.ellipsize(ch.label, tp, tw * 0.86f, TextUtils.TruncateAt.END);
            cv.drawText(label, 0, label.length(), tw / 2f, th * 0.86f, tp);
        }
        ch.art = bmp;
        ch.shader = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    }

    private static void drawCropped(Canvas cv, Drawable d, int tw, int th) {
        int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
        if (iw <= 0 || ih <= 0) {
            d.setBounds(0, 0, tw, th);
        } else {
            float sc = Math.max(tw / (float) iw, th / (float) ih);
            int dw = Math.round(iw * sc), dh = Math.round(ih * sc);
            int dx = (tw - dw) / 2, dy = (th - dh) / 2;
            d.setBounds(dx, dy, dx + dw, dy + dh);
        }
        d.draw(cv);
    }

    private void drawArrows(Canvas c) {
        if (page < pages - 1) drawArrow(c, ARROW_TIP * s, ARROW_Y * s, false);
        if (page > 0) drawArrow(c, (2f * WiiBase.CX - ARROW_TIP) * s, ARROW_Y * s, true);
    }

    private void drawArrow(Canvas c, float x, float y, boolean flip) {
        int save = c.save();
        c.translate(x, y);
        if (flip) c.scale(-1f, 1f);
        float l = (ARROW_L - ARROW_TIP) * s;
        fill.setPathEffect(arrowCorners);
        stroke.setPathEffect(arrowCorners);
        fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(0f, (ARROW_T - ARROW_Y) * s, 0f, (ARROW_B - ARROW_Y) * s,
                new int[] {0xFF6FCDF3, 0xFFA9E6FB, 0xFFDDF8FF, 0xFF9FE0F8, 0xFF62C5F0},
                new float[] {0f, 0.35f, 0.5f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(arrowPath, fill);
        fill.setShader(new LinearGradient(l, 0f, 0f, 0f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawPath(arrowPath, fill);
        fill.setShader(null);
        stroke.setColor(0xFF2E66C2);
        stroke.setStrokeWidth(3.2f * s);
        c.drawPath(arrowPath, stroke);
        fill.setPathEffect(null);
        stroke.setPathEffect(null);
        c.restoreToCount(save);
    }

    private void drawButton(Canvas c, Bitmap bmp, float cxRef, int id) {
        if (bmp == null) return;
        float lf = lift[liftIndex(id)];
        float cx = cxRef * s, cy = WiiBase.BTN_CY * s;
        int save = c.save();
        float sc = 1f + 0.06f * lf;
        c.scale(sc, sc, cx, cy);
        c.drawBitmap(bmp, cx - bmp.getWidth() / 2f, cy - bmp.getHeight() / 2f, bmpPaint);
        if (lf > 0.01f) {
            for (int k = 3; k >= 1; k--) {
                stroke.setColor(withAlpha(BLUE, (int) (lf * 70 / k)));
                stroke.setStrokeWidth((3f + k * 5f) * s);
                c.drawCircle(cx, cy, WiiBase.BTN_RING * s, stroke);
            }
        }
        c.restoreToCount(save);
    }

    private void drawSd(Canvas c) {
        float lf = lift[liftIndex(BTN_SD)];
        int save = c.save();
        float sc = 1f + 0.08f * lf;
        c.scale(sc, sc, (SD_L + SD_R) / 2f * s, (SD_T + SD_B) / 2f * s);
        CornerPathEffect round = new CornerPathEffect(4f * s);
        fill.setPathEffect(round);
        c.translate(4f * s, 4f * s);
        fill.setColor(0x2E000000);
        c.drawPath(sdPath, fill);
        c.translate(-4f * s, -4f * s);
        fill.setColor(0xFF5CC8EE);
        c.drawPath(sdPath, fill);
        fill.setPathEffect(null);
        fill.setColor(0xFFA9DDF3);
        c.drawRect((SD_L + 4f) * s, (SD_T + 4f) * s, (SD_R - 10f) * s, 915f * s, fill);
        fill.setColor(0xFFFFFFFF);
        c.drawRect(366f * s, 915f * s, 422f * s, 975f * s, fill);
        fill.setColor(0xFF4FC1EA);
        c.drawRect(366f * s, 975f * s, 422f * s, 987f * s, fill);
        if (lf > 0.01f) {
            stroke.setPathEffect(round);
            for (int k = 3; k >= 1; k--) {
                stroke.setColor(withAlpha(BLUE, (int) (lf * 80 / k)));
                stroke.setStrokeWidth((2f + k * 4f) * s);
                c.drawPath(sdPath, stroke);
            }
            stroke.setPathEffect(null);
        }
        text.setTypeface(Typeface.create(sansBold, Typeface.BOLD_ITALIC));
        text.setTextSize(24f * s);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0xFF59BDE8);
        c.drawText("SD", 393f * s, 953f * s, text);
        c.restoreToCount(save);
    }

    private void drawClock(Canvas c) {
        int hour, minute;
        boolean pm;
        if (refPose) {
            hour = 2;
            minute = 7;
            pm = true;
        } else {
            Calendar now = Calendar.getInstance();
            hour = now.get(Calendar.HOUR);
            if (hour == 0) hour = 12;
            minute = now.get(Calendar.MINUTE);
            pm = now.get(Calendar.AM_PM) == Calendar.PM;
        }
        fill.setColor(CLOCK_COLOR);
        if (hour >= 10) drawDigit(c, DIGIT_X[0], hour / 10);
        drawDigit(c, DIGIT_X[1], hour % 10);
        drawDigit(c, DIGIT_X[2], minute / 10);
        drawDigit(c, DIGIT_X[3], minute % 10);
        if (colonOn) {
            float d = COLON_DOT / 2f;
            c.drawRoundRect((COLON_X - d) * s, (COLON_Y1 - d) * s, (COLON_X + d) * s, (COLON_Y1 + d) * s, 2f * s, 2f * s, fill);
            c.drawRoundRect((COLON_X - d) * s, (COLON_Y2 - d) * s, (COLON_X + d) * s, (COLON_Y2 + d) * s, 2f * s, 2f * s, fill);
        }
        text.setTypeface(sansBold);
        text.setTextSize(AMPM_CAP / 0.711f * s);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(CLOCK_COLOR);
        // Letter by letter at the original's ink positions and widths; Roboto's M is wider than the original's.
        drawInkLetter(c, pm ? "P" : "A", AMPM_X, AMPM_P_W);
        drawInkLetter(c, "M", AMPM_M_X, AMPM_M_W);
    }

    /** Draws one letter so its ink starts at inkLeft and spans inkWidth ref px, on the AM/PM baseline. */
    private void drawInkLetter(Canvas c, String letter, float inkLeft, float inkWidth) {
        android.graphics.Rect b = new android.graphics.Rect();
        text.getTextBounds(letter, 0, 1, b);
        float sx = b.width() > 0 ? inkWidth * s / b.width() : 1f;
        int save = c.save();
        c.translate(inkLeft * s - b.left * sx, AMPM_BASE * s);
        c.scale(sx, 1f);
        c.drawText(letter, 0f, 0f, text);
        c.restoreToCount(save);
    }

    /**
     * One clock digit as on the original: the outer segments are mitred into the box corners, the ones that
     * meet the middle bar end in a point, and every joint keeps a small gap.
     */
    private void drawDigit(Canvas c, float xRef, int digit) {
        int bits = SEGMENTS[digit];
        float t = DIGIT_T, g = DIGIT_GAP, k = t / 2f;
        float xa = xRef, xb = xRef + DIGIT_W;
        float ya = CLOCK_TOP, yb = CLOCK_TOP + DIGIT_H, ym = CLOCK_TOP + DIGIT_H / 2f;
        fill.setPathEffect(digitCorners);
        if ((bits & 0x01) != 0) poly(c, xa + g, ya, xb - g, ya, xb - t - g, ya + t, xa + t + g, ya + t);
        if ((bits & 0x08) != 0) poly(c, xa + t + g, yb - t, xb - t - g, yb - t, xb - g, yb, xa + g, yb);
        if ((bits & 0x20) != 0) poly(c, xa, ya + g, xa + t, ya + t + g, xa + t, ym - k - g, xa + k, ym - g, xa, ym - k - g);
        if ((bits & 0x02) != 0) poly(c, xb, ya + g, xb, ym - k - g, xb - k, ym - g, xb - t, ym - k - g, xb - t, ya + t + g);
        if ((bits & 0x10) != 0) poly(c, xa, ym + k + g, xa + k, ym + g, xa + t, ym + k + g, xa + t, yb - t - g, xa, yb - g);
        if ((bits & 0x04) != 0) poly(c, xb, ym + k + g, xb, yb - g, xb - t, yb - t - g, xb - t, ym + k + g, xb - k, ym + g);
        if ((bits & 0x40) != 0) {
            poly(c, xa + k + g, ym, xa + t + g, ym - k, xb - t - g, ym - k, xb - k - g, ym, xb - t - g, ym + k,
                    xa + t + g, ym + k);
        }
        fill.setPathEffect(null);
    }

    /** A filled polygon through the given ref-px points. */
    private void poly(Canvas c, float... pts) {
        path.reset();
        path.moveTo(pts[0] * s, pts[1] * s);
        for (int i = 2; i < pts.length; i += 2) path.lineTo(pts[i] * s, pts[i + 1] * s);
        path.close();
        c.drawPath(path, fill);
    }

    private void drawDate(Canvas c) {
        String date;
        if (refPose) {
            date = "Fri 3/22";
        } else {
            Locale locale = getResources().getConfiguration().getLocales().get(0);
            java.util.Date now = new java.util.Date();
            String day = new SimpleDateFormat("EEE", locale).format(now).replace(".", "");
            if (!day.isEmpty()) day = day.substring(0, 1).toUpperCase(locale) + day.substring(1);
            boolean monthFirst = "US".equals(locale.getCountry()) || "en".equals(locale.getLanguage());
            date = day + " " + new SimpleDateFormat(monthFirst ? "M/d" : "d/M", locale).format(now);
        }
        text.setTypeface(sansBold);
        text.setTextSize(DATE_CAP / 0.711f * s);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(DATE_COLOR);
        // The original sets its date looser than Roboto: about 2 px more between letters, 3 more at the space.
        text.setLetterSpacing(DATE_TRACKING / (DATE_CAP / 0.711f));
        int space = date.indexOf(' ');
        String day = space < 0 ? "" : date.substring(0, space);
        String rest = space < 0 ? date : date.substring(space + 1);
        float gap = text.measureText(" ") + DATE_EXTRA_SPACE * s;
        float wDay = day.isEmpty() ? 0f : text.measureText(day), wRest = text.measureText(rest);
        float total = wDay + (day.isEmpty() ? 0f : gap) + wRest;
        float x = WiiBase.CX * s - total / 2f;
        if (!day.isEmpty()) c.drawText(day, x, DATE_BASE * s, text);
        c.drawText(rest, x + total - wRest, DATE_BASE * s, text);
        text.setLetterSpacing(0f);
    }

    private void drawHand(Canvas c) {
        float u = HAND_UNIT * s;
        int save = c.save();
        c.translate(handX, handY);
        c.rotate(-8f);
        c.translate(2.5f * u, 3.5f * u);
        fill.setColor(0x38000000);
        c.drawPath(handPath, fill);
        c.translate(-2.5f * u, -3.5f * u);
        fill.setColor(0xFFFFFFFF);
        c.drawPath(handPath, fill);
        stroke.setColor(0xFF3A3A3A);
        stroke.setStrokeWidth(2.2f * u);
        c.drawPath(handPath, stroke);
        stroke.setStrokeWidth(1.5f * u);
        c.drawPath(handLines, stroke);
        tmp.set(-4.5f * u, 47f * u, 23.5f * u, 60f * u);
        fill.setColor(0xFF47B6EA);
        c.drawRoundRect(tmp, 4f * u, 4f * u, fill);
        stroke.setColor(0xFF2C86BA);
        stroke.setStrokeWidth(1.6f * u);
        c.drawRoundRect(tmp, 4f * u, 4f * u, stroke);
        text.setTypeface(sansBold);
        text.setTextSize(10.5f * u);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0xFFFFFFFF);
        c.drawText("1", tmp.centerX(), tmp.centerY() + 3.8f * u, text);
        c.restoreToCount(save);
    }

    private void drawMenu(Canvas c) {
        int a = Math.round(menuAlpha * 255);
        int save = a < 255 ? c.saveLayerAlpha(0, 0, w, h, a) : c.save();
        fill.setColor(0x78000000);
        c.drawRect(0, 0, w, h, fill);
        int n = menuItems.size();
        float pw = 1000f * s, itemH = 92f * s, gap = 22f * s, head = 150f * s, pad = 56f * s;
        float ph = head + n * itemH + (n - 1) * gap + pad;
        float px = (w - pw) / 2f, py = (h - ph) / 2f;
        c.translate(0f, (1f - menuAlpha) * 36f * s);
        float rad = 40f * s;
        rect.set(px, py, px + pw, py + ph);
        if (base != null) {
            fill.setColor(0xFFFFFFFF);
            fill.setShader(new BitmapShader(base, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, rad, rad, fill);
            fill.setShader(null);
        } else {
            fill.setColor(0xFFEDEDED);
            c.drawRoundRect(rect, rad, rad, fill);
        }
        stroke.setColor(TILE_BORDER_COLOR);
        stroke.setStrokeWidth(TILE_BORDER * s);
        c.drawRoundRect(rect, rad, rad, stroke);

        text.setTypeface(sansBold);
        text.setTextSize(48f * s);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(DATE_COLOR);
        c.drawText(menuTitle, w / 2f, py + 92f * s, text);

        text.setTextSize(40f * s);
        for (int i = 0; i < n; i++) {
            float iy = py + head + i * (itemH + gap);
            tmp.set(px + 90f * s, iy, px + pw - 90f * s, iy + itemH);
            float lf = i < menuLift.length ? menuLift[i] : 0f;
            float ir = itemH / 2f;
            fill.setColor(0xFFFFFFFF);
            fill.setShader(new LinearGradient(0, tmp.top, 0, tmp.bottom, 0xFFFFFFFF, 0xFFE2E2E2, Shader.TileMode.CLAMP));
            c.drawRoundRect(tmp, ir, ir, fill);
            fill.setShader(null);
            if (lf > 0.01f) {
                for (int k = 3; k >= 1; k--) {
                    stroke.setColor(withAlpha(BLUE, (int) (lf * 70 / k)));
                    stroke.setStrokeWidth((4f + k * 6f) * s);
                    c.drawRoundRect(tmp, ir, ir, stroke);
                }
            }
            stroke.setColor(blend(TILE_BORDER_COLOR, BLUE, lf));
            stroke.setStrokeWidth((TILE_BORDER + 2f * lf) * s);
            c.drawRoundRect(tmp, ir, ir, stroke);
            text.setColor(0xFF666666);
            CharSequence label = TextUtils.ellipsize(menuItems.get(i).label, text, tmp.width() - 70f * s,
                    TextUtils.TruncateAt.END);
            c.drawText(label, 0, label.length(), tmp.centerX(), tmp.centerY() + 14f * s, text);
        }
        c.restoreToCount(save);
    }

    private void drawIntro(Canvas c) {
        int a = Math.round(introAlpha * 255);
        int save = a < 255 ? c.saveLayerAlpha(0, 0, w, h, a) : c.save();
        c.drawColor(0xFFFFFFFF);
        float left = 240f * s;
        float headY = 340f * s;
        drawWarningSign(c, left + 34f * s, headY - 22f * s, 38f * s);
        text.setTypeface(sansBold);
        text.setTextSize(64f * s);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(0xFF1E1E1E);
        c.drawText(introTitle, left + 98f * s, headY, text);
        stroke.setColor(0xFFDADADA);
        stroke.setStrokeWidth(3f * s);
        c.drawLine(left, headY + 40f * s, w - left, headY + 40f * s, stroke);

        if (introBody == null) {
            TextPaint body = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            body.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            body.setTextSize(50f * s);
            body.setColor(0xFF3A3A3A);
            int width = Math.max(1, Math.round(w - 2 * left));
            introBody = StaticLayout.Builder.obtain(introBodyText, 0, introBodyText.length(), body, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.2f)
                    .build();
        }
        int bs = c.save();
        c.translate(left, headY + 100f * s);
        introBody.draw(c);
        c.restoreToCount(bs);

        float t = (SystemClock.uptimeMillis() - introStartMs) / 1000f;
        float pulse = 0.58f + 0.42f * (float) Math.cos(t * Math.PI * 2 / 1.6);
        int ca = Math.round(255 * Math.max(0.16f, pulse));
        text.setTypeface(sansBold);
        text.setTextSize(52f * s);
        text.setTextAlign(Paint.Align.LEFT);
        float tw = text.measureText(introContinue);
        float iconR = 32f * s, gapIcon = 22f * s;
        float total = 2 * iconR + gapIcon + tw;
        float sx = (w - total) / 2f, baseY = 880f * s;
        float icx = sx + iconR, icy = baseY - 18f * s;
        fill.setColor(withAlpha(0xFFFFFFFF, ca));
        c.drawCircle(icx, icy, iconR, fill);
        stroke.setColor(withAlpha(0xFF9A9A9A, ca));
        stroke.setStrokeWidth(4.5f * s);
        c.drawCircle(icx, icy, iconR, stroke);
        text.setColor(withAlpha(0xFF4A4A4A, ca));
        c.drawText(introContinue, sx + 2 * iconR + gapIcon, baseY, text);
        text.setTextSize(22f * s);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(withAlpha(0xFF777777, ca));
        c.drawText("OK", icx, icy + 8f * s, text);
        c.restoreToCount(save);
    }

    private void drawWarningSign(Canvas c, float cx, float cy, float r) {
        path.reset();
        path.moveTo(cx, cy - r);
        path.lineTo(cx + r * 1.1f, cy + r * 0.85f);
        path.lineTo(cx - r * 1.1f, cy + r * 0.85f);
        path.close();
        CornerPathEffect round = new CornerPathEffect(6f * s);
        fill.setPathEffect(round);
        stroke.setPathEffect(round);
        fill.setColor(0xFFFFC61E);
        c.drawPath(path, fill);
        stroke.setColor(0xFF1E1E1E);
        stroke.setStrokeWidth(4.5f * s);
        c.drawPath(path, stroke);
        fill.setPathEffect(null);
        stroke.setPathEffect(null);
        text.setTypeface(sansBold);
        text.setTextSize(r * 1.25f);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0xFF1E1E1E);
        c.drawText("!", cx, cy + r * 0.65f, text);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static int blend(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
