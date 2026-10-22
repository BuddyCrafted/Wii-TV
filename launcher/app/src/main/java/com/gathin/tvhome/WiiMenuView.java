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
import java.util.Collections;
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
        /** OK held (or Menu pressed) on a channel. */
        void onChannelOptions(Channel channel);
        /** A move started with startMove() was dropped; the list is the new channel order. */
        void onOrderChanged(List<Channel> order);
        /** Start on a game controller, or Menu away from a channel. */
        void onMenuKey();
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
        /** Shown at the right of a Settings button (the current choice); null for none. */
        final String value;

        MenuItem(String label, Runnable action) {
            this(label, null, action);
        }

        MenuItem(String label, String value, Runnable action) {
            this.label = label;
            this.value = value;
            this.action = action;
        }
    }

    /** Builds a Settings page's buttons; called again after each choice so values stay current. */
    interface PageSource {
        List<MenuItem> items();
    }

    /** One full-screen Settings page in the style of the console's settings screens. */
    private static final class SettingsPage {
        final String title;
        final PageSource source;
        List<MenuItem> items;
        /** Focused button; SETTINGS_BACK for the Back button. */
        int focus;
        int sheet;

        SettingsPage(String title, PageSource source) {
            this.title = title;
            this.source = source;
            this.items = source.items();
        }
    }

    private static final int SLOTS_PER_PAGE = 12;
    private static final int MIN_PAGES = 4;
    static final int BTN_SETTINGS = -1;
    static final int BTN_SD = -2;
    static final int BTN_INPUTS = -3;
    private static final long INTRO_AUTO_MS = 12_000L;
    /** Menu rows shown at once; longer menus scroll with the focus. */
    private static final int MENU_ROWS = 6;

    // Settings screen, in ref px like the rest: four big buttons a sheet, arrows to the other sheets, Back below.
    private static final int SETTINGS_ROWS = 4;
    private static final int SETTINGS_BACK = -1;
    private static final float SET_BTN_W = 1120f;
    private static final float SET_BTN_H = 124f;
    private static final float SET_BTN_TOP = 250f;
    private static final float SET_BTN_PITCH = 152f;
    private static final float SET_BAND_TOP = 884f;
    private static final float SET_ARROW_Y = 478f;
    private static final float SET_BACK_L = 110f, SET_BACK_T = 930f, SET_BACK_R = 430f, SET_BACK_B = 1034f;

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

    /** Focus glows, ring and highlights: the theme's accent (the original 0xFF3FC0EC in Classic). */
    private int accentArgb = 0xFF3FC0EC;
    private Theme theme = Theme.CLASSIC;
    private final SeasonDeco season = new SeasonDeco();
    private final IslandScene island = new IslandScene();
    /** Segment bits a-g per digit; the original's 7 keeps the upper-left bar. */
    private static final int[] SEGMENTS = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x27, 0x7F, 0x6F};
    /** The previous version's hand was drawn in units of 1/1024 of the width. */
    private static final float HAND_UNIT = 1920f / 1024f;
    /** The console's cursor is a little smaller than the previous hand. */
    private static final float HAND_SCALE = 0.8f;

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
    private String settingsLabel = "WiiOS";
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
    private int menuFirst;

    private final List<SettingsPage> settings = new ArrayList<>();
    private float settingsAlpha;
    private float[] settingsLift = new float[SETTINGS_ROWS + 1];
    private String backLabel = "Back";
    /** 1 GB devices: channel art at 16 bits a pixel (tiles are opaque, so nothing is lost but a little banding). */
    private boolean lowMemory;

    /** OK went down on a channel; it opens on release, or shows the options if held. */
    private boolean okPending;
    /** Move mode: the focused channel travels with the arrows until OK or Back drops it. */
    private boolean moving;
    private long moveStartMs;

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
    private Path tilePath, arrowPath, sdPath;
    private WiiHand hand;
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

    private Runnable onIntroDone;

    /** Runs when the welcome screen is dismissed. */
    void setOnIntroDone(Runnable r) {
        onIntroDone = r;
    }

    void dismissIntro() {
        if (introAlpha <= 0f || introLeaving) return;
        introLeaving = true;
        if (onIntroDone != null) post(onIntroDone);
        removeCallbacks(autoContinue);
        playEntrance();
        invalidate();
    }

    void showMenu(String title, List<MenuItem> items) {
        showMenu(title, items, 0);
    }

    /** Opens the menu with row `selected` focused (kept on screen), e.g. to reopen it on a switch just flipped. */
    void showMenu(String title, List<MenuItem> items, int selected) {
        if (items.isEmpty()) return;
        menuTitle = title;
        menuItems = new ArrayList<>(items);
        menuFocus = Math.max(0, Math.min(items.size() - 1, selected));
        menuFirst = Math.max(0, menuFocus - MENU_ROWS + 1);
        menuLift = new float[menuItems.size()];
        menuOpen = true;
        invalidate();
    }

    void closeMenu() {
        menuOpen = false;
        invalidate();
    }

    void setLowMemory(boolean low) {
        lowMemory = low;
    }

    void setBackLabel(String label) {
        backLabel = label;
    }

    boolean settingsOpen() {
        return !settings.isEmpty();
    }

    /** Opens a Settings page on top of the menu, or on top of the Settings page already showing. */
    void pushSettings(String title, PageSource source) {
        settings.add(new SettingsPage(title, source));
        menuOpen = false;
        lastFrameNanos = 0;
        invalidate();
    }

    /** Rebuilds the showing page's buttons, e.g. after a switch changed; the focus stays put. */
    void refreshSettings() {
        if (settings.isEmpty()) return;
        SettingsPage p = top();
        p.items = p.source.items();
        if (p.focus >= p.items.size()) p.focus = p.items.size() - 1;
        int sheets = sheets(p);
        if (p.sheet >= sheets) p.sheet = sheets - 1;
        invalidate();
    }

    /** Back on a Settings page: back to the page before, or to the menu. */
    void popSettings() {
        if (settings.isEmpty()) return;
        settings.remove(settings.size() - 1);
        if (!settings.isEmpty()) refreshSettings();
        invalidate();
    }

    void closeSettings() {
        settings.clear();
        invalidate();
    }

    private SettingsPage top() {
        return settings.get(settings.size() - 1);
    }

    private static int sheets(SettingsPage p) {
        return Math.max(1, (p.items.size() + SETTINGS_ROWS - 1) / SETTINGS_ROWS);
    }

    private boolean settingsKey(int keyCode) {
        SettingsPage p = top();
        int sheets = sheets(p);
        int first = p.sheet * SETTINGS_ROWS;
        int onSheet = Math.min(SETTINGS_ROWS, p.items.size() - first);
        int row = p.focus == SETTINGS_BACK ? -1 : p.focus - first;
        int before = p.focus, sheetBefore = p.sheet;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (p.focus == SETTINGS_BACK) p.focus = first + onSheet - 1;
                else if (row > 0) p.focus--;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (p.focus != SETTINGS_BACK) p.focus = row < onSheet - 1 ? p.focus + 1 : SETTINGS_BACK;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (p.sheet > 0) p.sheet--;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (p.sheet < sheets - 1) p.sheet++;
                break;
            default:
                if (isOk(keyCode)) {
                    if (p.focus == SETTINGS_BACK) {
                        sound(WiiSounds.BACK);
                        popSettings();
                    } else if (p.focus >= 0 && p.focus < p.items.size()) {
                        sound(WiiSounds.SELECT);
                        p.items.get(p.focus).action.run();
                    }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
                    sound(WiiSounds.BACK);
                    popSettings();
                    return true;
                }
                return keyCode == KeyEvent.KEYCODE_MENU;
        }
        if (p.sheet != sheetBefore && p.focus != SETTINGS_BACK) {
            // Same row on the new sheet, or its last button.
            int nf = p.sheet * SETTINGS_ROWS + Math.max(0, row);
            p.focus = Math.min(nf, p.items.size() - 1);
        }
        if (p.focus != before || p.sheet != sheetBefore) {
            sound(p.sheet != sheetBefore ? WiiSounds.PAGE : WiiSounds.MOVE);
            invalidate();
        }
        return true;
    }

    /** Picks up the focused channel; the arrows then carry it and OK or Back puts it down. */
    void startMove() {
        if (focus < 0 || focus >= channels.size()) return;
        moving = true;
        moveStartMs = SystemClock.uptimeMillis();
        lastFrameNanos = 0;
        invalidate();
    }

    private void endMove() {
        moving = false;
        sound(WiiSounds.SELECT);
        listener.onOrderChanged(new ArrayList<>(channels));
        invalidate();
    }

    /** Back: closes the menu or the welcome screen. False when there was nothing to close. */
    boolean back() {
        if (!settings.isEmpty() && !menuOpen) {
            sound(WiiSounds.BACK);
            popSettings();
            return true;
        }
        if (moving) {
            endMove();
            return true;
        }
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
        season.sfx = this::sound;
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

    /** Key down from the activity; the event's repeat count tells a held OK apart from a press. */
    boolean handleKey(int keyCode, KeyEvent event) {
        if (!isOk(keyCode)) okPending = false;
        if (moving || menuOpen || !settings.isEmpty() || introAlpha > 0f || refPose) {
            if (event.getRepeatCount() > 0 && isOk(keyCode)) return true;
            return handleKey(keyCode);
        }
        boolean onChannel = focus >= 0 && focus < channels.size();
        if (onChannel && (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_BUTTON_Y
                || keyCode == KeyEvent.KEYCODE_BUTTON_X)) {
            if (event.getRepeatCount() == 0) {
                sound(WiiSounds.MENU);
                listener.onChannelOptions(channels.get(focus));
            }
            return true;
        }
        if (onChannel && isOk(keyCode)) {
            if (event.getRepeatCount() == 0) {
                okPending = true;
            } else if (okPending) {
                okPending = false;
                sound(WiiSounds.MENU);
                listener.onChannelOptions(channels.get(focus));
            }
            return true;
        }
        if (isOk(keyCode) && event.getRepeatCount() > 0) return true;
        return handleKey(keyCode);
    }

    /** Key up: a short press of OK on a channel opens it. */
    boolean handleKeyUp(int keyCode) {
        if (!isOk(keyCode) || !okPending) return false;
        okPending = false;
        if (focus >= 0 && focus < channels.size() && !menuOpen && !moving && settings.isEmpty()) activate();
        return true;
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
        if (!settings.isEmpty()) return settingsKey(keyCode);
        if (moving) return moveKey(keyCode);
        // Game controllers: B is Back, Start the WiiOS menu, the shoulder buttons turn the page.
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            back();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_START || keyCode == KeyEvent.KEYCODE_MENU) {
            sound(WiiSounds.MENU);
            listener.onMenuKey();
            return true;
        }
        // Shoulder buttons, and the Fire TV remote's rewind / fast-forward keys, turn the page.
        if (keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_PAGE_UP
                || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
            turnPage(-1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_R1 || keyCode == KeyEvent.KEYCODE_PAGE_DOWN
                || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
            turnPage(1);
            return true;
        }
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

    private void turnPage(int delta) {
        int np = Math.max(0, Math.min(pages - 1, page + delta));
        if (np == page) return;
        int i = focus >= 0 ? focus % SLOTS_PER_PAGE : 0;
        page = np;
        focus = page * SLOTS_PER_PAGE + i;
        sound(WiiSounds.PAGE);
        invalidate();
    }

    private boolean moveKey(int keyCode) {
        if (isOk(keyCode) || keyCode == KeyEvent.KEYCODE_MENU) {
            endMove();
            return true;
        }
        int dx = 0, dy = 0;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) dx = -1;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) dx = 1;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) dy = -1;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) dy = 1;
        else return false;
        int from = focus, pageBefore = page;
        move(dx, dy);
        if (focus < 0 || focus >= channels.size()) {
            // Only onto other channels: not the buttons, not past the last one.
            focus = from;
            page = pageBefore;
            invalidate();
            return true;
        }
        if (focus != from) {
            Collections.swap(channels, from, focus);
            float l = lift[from];
            lift[from] = lift[focus];
            lift[focus] = l;
        }
        return true;
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
                if (menuFocus < menuFirst) menuFirst = menuFocus;
                sound(WiiSounds.MOVE);
                invalidate();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (menuFocus < menuItems.size() - 1) {
                menuFocus++;
                if (menuFocus >= menuFirst + MENU_ROWS) menuFirst = menuFocus - MENU_ROWS + 1;
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
        if (!settings.isEmpty()) {
            SettingsPage p = top();
            if (p.focus == SETTINGS_BACK) {
                out[0] = (SET_BACK_L + (SET_BACK_R - SET_BACK_L) * 0.72f) * s;
                out[1] = (SET_BACK_T + (SET_BACK_B - SET_BACK_T) * 0.75f) * s;
            } else {
                // At the button's right end, clear of its text.
                int row = p.focus - p.sheet * SETTINGS_ROWS;
                out[0] = (WiiBase.CX + SET_BTN_W / 2f + 18f) * s;
                out[1] = (SET_BTN_TOP + row * SET_BTN_PITCH + SET_BTN_H * 0.62f) * s;
            }
            return;
        }
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
        final Theme th0 = theme;
        new Thread(() -> {
            boolean island = th0.deco == Theme.Deco.ISLAND;
            Bitmap bg;
            if (island) {
                bg = IslandScene.backdrop(bw, bh, bs);
            } else {
                int[] px = WiiBase.backdrop(bw, bh, th0);
                bg = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                bg.setPixels(px, 0, bw, 0, 0, bw, bh);
                px = null;
                drawDecorations(new Canvas(bg), bw, bh, bs, th0);
            }
            int size = (int) Math.ceil(2f * (WiiBase.BTN_RING + WiiBase.BTN_RING_HALF + 2f) * bs);
            Bitmap left = island ? IslandScene.button(size, bs)
                    : Bitmap.createBitmap(WiiBase.button(size, bs, th0), size, size, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true);
            Bitmap right = left.copy(Bitmap.Config.ARGB_8888, true);
            drawWordmark(new Canvas(left), size, bs, label);
            drawEnvelope(new Canvas(right), size, bs);
            int tw = Math.round(TILE_W * bs) + 8, th = Math.round(TILE_H * bs) + 8;
            Bitmap empty = Bitmap.createBitmap(WiiBase.emptySlot(tw, th, bs, th0), tw, th, Bitmap.Config.ARGB_8888);
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

    /** Switches the look; the static layers are rebuilt off the UI thread and the channel art redrawn. */
    void setTheme(Theme t) {
        if (t == null) t = Theme.CLASSIC;
        theme = t;
        accentArgb = Theme.argb(t.accent);
        if (hand != null) hand.setAccent(t);
        for (Channel ch : channels) {
            ch.art = null;
            ch.shader = null;
        }
        rebuild();
        invalidate();
    }

    /** Baked into the backdrop: Beach's wave above the line, Winter's pine trees, Fall's leaf piles. */
    private static void drawDecorations(Canvas c, int w, int h, float s, Theme t) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (t.deco == Theme.Deco.WAVE) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(4f * s);
            p.setColor((0x70 << 24) | (t.accent & 0xFFFFFF));
            Path wave = new Path();
            for (float rx = 0f; rx <= 1920f; rx += 6f) {
                float y = (WiiBase.lineY(rx) - 30f + 7f * (float) Math.sin(rx / 52f)) * s;
                if (rx == 0f) wave.moveTo(rx * s, y);
                else wave.lineTo(rx * s, y);
            }
            c.drawPath(wave, p);
        } else if (t.deco == Theme.Deco.SNOW) {
            SeasonDeco.bakeWinter(c, s);
        } else if (t.deco == Theme.Deco.LEAVES) {
            SeasonDeco.bakeFall(c, s);
        }
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
        tp.setColor(theme.classic ? 0x99FFFFFF : 0x33000000);
        c.drawText(label, size / 2f, y + 2f * bs, tp);
        tp.setColor(Theme.argb(theme.wordmark));
        c.drawText(label, size / 2f, y, tp);
    }

    private void drawEnvelope(Canvas c, int size, float bs) {
        float cx = size / 2f, cy = size / 2f;
        float ew = 97f * bs, eh = 65f * bs;
        RectF r = new RectF(cx - ew / 2f, cy - eh / 2f, cx + ew / 2f, cy + eh / 2f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(theme.classic ? 0xFF939393 : Theme.argb(theme.wordmark));
        c.drawRoundRect(r, 5f * bs, 5f * bs, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4.5f * bs);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(theme.classic ? 0xFFE2E2E2 : Theme.argb(theme.buttonLight));
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

        float st = settings.isEmpty() ? 0f : 1f;
        settingsAlpha = approach(settingsAlpha, st, kMenu, 0.004f);
        if (settingsAlpha != st) moving = true;
        int sf = settings.isEmpty() ? -2 : top().focus;
        int srow = settings.isEmpty() ? -2 : sf == SETTINGS_BACK ? SETTINGS_ROWS : sf - top().sheet * SETTINGS_ROWS;
        for (int i = 0; i < settingsLift.length; i++) {
            float t = i == srow ? 1f : 0f;
            settingsLift[i] = approach(settingsLift[i], t, kLift, 0.002f);
            if (settingsLift[i] != t) moving = true;
        }

        if (introLeaving) {
            introAlpha = approach(introAlpha, 0f, 1f - (float) Math.exp(-dt / 0.2f), 0.004f);
            if (introAlpha == 0f) {
                introLeaving = false;
            } else {
                moving = true;
            }
        }
        return moving;
    }

    // ---- drawing ---------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 1f / 60f : Math.min(0.05f, (now - lastFrameNanos) / 1e9f);
        lastFrameNanos = now;
        boolean animating = step(dt);

        if (base != null) c.drawBitmap(base, 0f, 0f, null);
        else c.drawColor(theme.classic ? 0xFFE9E9E9 : Theme.argb(theme.bgTopLight));
        boolean seasonal = theme.deco == Theme.Deco.SNOW || theme.deco == Theme.Deco.LEAVES
                || theme.deco == Theme.Deco.ISLAND;
        float seconds = SystemClock.uptimeMillis() / 1000f;
        if (theme.deco == Theme.Deco.SNOW) season.snow(c, s, seconds, false);
        else if (theme.deco == Theme.Deco.LEAVES) season.leaves(c, s, seconds, false);
        else if (theme.deco == Theme.Deco.ISLAND) island.clouds(c, s, seconds);
        drawTiles(c);
        if (theme.deco == Theme.Deco.ISLAND) island.butterflies(c, s, seconds);
        if (theme.deco == Theme.Deco.SNOW) {
            season.winterKids(c, s, seconds);
            season.snow(c, s, seconds, true);
        } else if (theme.deco == Theme.Deco.LEAVES) {
            season.leaves(c, s, seconds, true);
        }
        float panel = entering() ? entrance(ENTRANCE_PANEL_DELAY_MS) : 1f;
        if (panel > 0f) {
            // Entrance: the buttons, clock and date rise into place after the channels.
            int sv = panel < 1f ? c.saveLayerAlpha(0, 0, w, h, Math.round(panel * 255)) : c.save();
            c.translate(0f, (1f - ease(panel)) * 70f * s);
            if (panel >= 1f) drawArrows(c);
            drawButton(c, btnLeft, WiiBase.BTN_CX, BTN_SETTINGS);
            drawButton(c, btnRight, BTN_RIGHT_CX, BTN_INPUTS);
            drawSd(c);
            drawClock(c);
            drawDate(c);
            c.restoreToCount(sv);
        }
        if (settingsAlpha > 0f) drawSettings(c);
        drawHand(c);
        if (menuAlpha > 0f) drawMenu(c);
        if (introAlpha > 0f) drawIntro(c);

        if (animating || introAlpha > 0f || moving || entering()) postInvalidateOnAnimation();
        else {
            lastFrameNanos = 0;
            // Snow, sledders and leaves keep moving at 25 frames a second, light enough for a 1 GB stick.
            if (seasonal && started) postInvalidateDelayed(40);
        }
    }

    private void drawTiles(Canvas c) {
        int total = pages * SLOTS_PER_PAGE;
        for (int sl = 0; sl < total; sl++) {
            if (sl == focus) continue;
            slotRect(sl, pagePos, rect);
            if (rect.right < -10f * s || rect.left > w + 10f * s) continue;
            drawTileEntering(c, sl, rect, lift[sl]);
        }
        if (focus >= 0) {
            slotRect(focus, pagePos, rect);
            if (moving) {
                // Picked up: bigger, raised and gently bobbing, with a shadow under it.
                float t = (SystemClock.uptimeMillis() - moveStartMs) / 1000f;
                float bob = (float) Math.sin(t * Math.PI * 2 / 1.2f) * 5f * s;
                fill.setColor(0x30000000);
                tmp.set(rect.left + 10f * s, rect.bottom - 6f * s, rect.right - 10f * s, rect.bottom + 18f * s);
                c.drawOval(tmp, fill);
                rect.offset(0f, -14f * s + bob);
                int sv = c.save();
                c.scale(1.08f, 1.08f, rect.centerX(), rect.centerY());
                drawTile(c, focus, rect, 1f);
                c.restoreToCount(sv);
            } else if (rect.right >= 0 && rect.left <= w) {
                drawTileEntering(c, focus, rect, lift[focus]);
            }
        }
    }

    // ---- start-up entrance: after the welcome screen the channels pop in, a diagonal wave from the top left,
    // then the buttons, clock and date rise into place. Our own take on the console's start-up.

    private static final long ENTRANCE_STEP_MS = 70, ENTRANCE_TILE_MS = 420, ENTRANCE_PANEL_DELAY_MS = 520,
            ENTRANCE_MS = 1400;
    private long entranceStart = -1;

    /** Starts a moment after the welcome screen begins to fade, so the channels pop in as it clears. */
    void playEntrance() {
        entranceStart = SystemClock.uptimeMillis() + 180;
        lastFrameNanos = 0;
        invalidate();
    }

    private boolean entering() {
        if (entranceStart < 0) return false;
        if (SystemClock.uptimeMillis() - entranceStart < ENTRANCE_MS) return true;
        entranceStart = -1;
        return false;
    }

    /** 0..1 progress of a part that starts `delay` ms into the entrance. */
    private float entrance(long delay) {
        float p = (SystemClock.uptimeMillis() - entranceStart - delay) / (float) ENTRANCE_TILE_MS;
        return Math.max(0f, Math.min(1f, p));
    }

    /** Ease out with a small overshoot, so each tile pops past full size and settles. */
    private static float ease(float p) {
        float q = p - 1f;
        return 1f + q * q * (2.7f * q + 1.7f);
    }

    private void drawTileEntering(Canvas c, int slot, RectF r, float lf) {
        if (!entering()) {
            drawTile(c, slot, r, lf);
            return;
        }
        int i = slot % SLOTS_PER_PAGE;
        int pg = slot / SLOTS_PER_PAGE;
        int col = i % 4 + (pg - page) * 4, row = i / 4;
        float p = entrance((col + row) * ENTRANCE_STEP_MS);
        if (p <= 0f) return;
        float sc = 0.35f + 0.65f * ease(p);
        int sv = c.save();
        c.scale(sc, sc, r.centerX(), r.centerY());
        if (p < 1f) c.saveLayerAlpha(r.left - 20f * s, r.top - 20f * s, r.right + 20f * s, r.bottom + 20f * s, Math.round(p * 255));
        drawTile(c, slot, r, lf);
        c.restoreToCount(sv);
    }

    private void drawTile(Canvas c, int slot, RectF r, float lf) {
        int save = c.save();
        c.translate(r.left, r.top);
        Channel ch = slot < channels.size() ? channels.get(slot) : null;
        if (theme.deco == Theme.Deco.ISLAND) {
            // A cream signboard; its face shows the channel's banner, custom picture, or icon and name.
            if (ch != null) ensureArt(ch, IslandScene.innerW(r.width(), s), IslandScene.innerH(r.height(), s));
            island.sign(c, s, r.width(), r.height(), ch == null ? null : ch.shader, lf, accentArgb);
            c.restoreToCount(save);
            return;
        }
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
                stroke.setColor(withAlpha(accentArgb, (int) (lf * 64 / k)));
                stroke.setStrokeWidth((TILE_BORDER + k * 5f) * s);
                c.drawPath(tilePath, stroke);
            }
        }
        stroke.setColor(blend(Theme.argb(theme.tileBorder), accentArgb, lf));
        stroke.setStrokeWidth((TILE_BORDER + 1.5f * lf) * s);
        c.drawPath(tilePath, stroke);
        c.restoreToCount(save);
    }

    private void ensureArt(Channel ch, int tw, int th) {
        tw = Math.max(1, tw);
        th = Math.max(1, th);
        if (ch.art != null && ch.art.getWidth() == tw && ch.art.getHeight() == th) return;
        Bitmap bmp = Bitmap.createBitmap(tw, th, lowMemory ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.drawColor(0xFFFFFFFF);
        if (ch.banner != null) {
            drawCropped(cv, ch.banner, tw, th);
        } else {
            Paint bg = new Paint();
            bg.setShader(new LinearGradient(0, 0, 0, th, Theme.argb(theme.iconTileTop), Theme.argb(theme.iconTileBottom),
                    Shader.TileMode.CLAMP));
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
            tp.setColor(Theme.argb(theme.iconTileText));
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
                theme.untouched() ? new int[] {0xFF6FCDF3, 0xFFA9E6FB, 0xFFDDF8FF, 0xFF9FE0F8, 0xFF62C5F0} : new int[] {
                        Theme.argb(Theme.mix(theme.accent, 0xFFFFFF, 0.25f)), Theme.argb(Theme.mix(theme.accent, 0xFFFFFF, 0.6f)),
                        Theme.argb(Theme.mix(theme.accent, 0xFFFFFF, 0.88f)), Theme.argb(Theme.mix(theme.accent, 0xFFFFFF, 0.55f)),
                        Theme.argb(Theme.mix(theme.accent, 0xFFFFFF, 0.15f))},
                new float[] {0f, 0.35f, 0.5f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(arrowPath, fill);
        fill.setShader(new LinearGradient(l, 0f, 0f, 0f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawPath(arrowPath, fill);
        fill.setShader(null);
        stroke.setColor(theme.untouched() ? 0xFF2E66C2 : Theme.argb(Theme.mix(theme.accent, 0x000000, 0.35f)));
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
                stroke.setColor(withAlpha(accentArgb, (int) (lf * 70 / k)));
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
        fill.setColor(theme.untouched() ? 0xFF5CC8EE : accentArgb);
        c.drawPath(sdPath, fill);
        fill.setPathEffect(null);
        fill.setColor(theme.untouched() ? 0xFFA9DDF3 : Theme.argb(Theme.mix(theme.accent, 0xFFFFFF, 0.5f)));
        c.drawRect((SD_L + 4f) * s, (SD_T + 4f) * s, (SD_R - 10f) * s, 915f * s, fill);
        fill.setColor(0xFFFFFFFF);
        c.drawRect(366f * s, 915f * s, 422f * s, 975f * s, fill);
        fill.setColor(theme.untouched() ? 0xFF4FC1EA : accentArgb);
        c.drawRect(366f * s, 975f * s, 422f * s, 987f * s, fill);
        if (lf > 0.01f) {
            stroke.setPathEffect(round);
            for (int k = 3; k >= 1; k--) {
                stroke.setColor(withAlpha(accentArgb, (int) (lf * 80 / k)));
                stroke.setStrokeWidth((2f + k * 4f) * s);
                c.drawPath(sdPath, stroke);
            }
            stroke.setPathEffect(null);
        }
        text.setTypeface(Typeface.create(sansBold, Typeface.BOLD_ITALIC));
        text.setTextSize(24f * s);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(theme.untouched() ? 0xFF59BDE8 : accentArgb);
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
        if (theme.deco == Theme.Deco.ISLAND) {
            // Island village: plain time and the full date on the wooden sign.
            Locale locale = getResources().getConfiguration().getLocales().get(0);
            String date = refPose ? "Friday, March 22" : new SimpleDateFormat("EEEE, MMMM d", locale).format(new java.util.Date());
            island.clock(c, s, hour + ":" + (minute < 10 ? "0" : "") + minute, pm ? "PM" : "AM", date);
            return;
        }
        fill.setColor(Theme.argb(theme.clock));
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
        text.setColor(Theme.argb(theme.clock));
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
        if (theme.deco == Theme.Deco.ISLAND) return;   // on the clock's sign
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
        text.setColor(Theme.argb(theme.date));
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
        if (hand == null) {
            hand = new WiiHand(sansBold);
            hand.setAccent(theme);
        }
        hand.draw(c, handX, handY, HAND_UNIT * s * HAND_SCALE);
    }

    private void drawMenu(Canvas c) {
        int a = Math.round(menuAlpha * 255);
        int save = a < 255 ? c.saveLayerAlpha(0, 0, w, h, a) : c.save();
        fill.setColor(0x78000000);
        c.drawRect(0, 0, w, h, fill);
        int n = Math.min(MENU_ROWS, menuItems.size());
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
        for (int row = 0; row < n; row++) {
            int i = menuFirst + row;
            float iy = py + head + row * (itemH + gap);
            tmp.set(px + 90f * s, iy, px + pw - 90f * s, iy + itemH);
            float lf = i < menuLift.length ? menuLift[i] : 0f;
            float ir = itemH / 2f;
            fill.setColor(0xFFFFFFFF);
            fill.setShader(new LinearGradient(0, tmp.top, 0, tmp.bottom, 0xFFFFFFFF, 0xFFE2E2E2, Shader.TileMode.CLAMP));
            c.drawRoundRect(tmp, ir, ir, fill);
            fill.setShader(null);
            if (lf > 0.01f) {
                for (int k = 3; k >= 1; k--) {
                    stroke.setColor(withAlpha(accentArgb, (int) (lf * 70 / k)));
                    stroke.setStrokeWidth((4f + k * 6f) * s);
                    c.drawRoundRect(tmp, ir, ir, stroke);
                }
            }
            stroke.setColor(blend(TILE_BORDER_COLOR, accentArgb, lf));
            stroke.setStrokeWidth((TILE_BORDER + 2f * lf) * s);
            c.drawRoundRect(tmp, ir, ir, stroke);
            text.setColor(0xFF666666);
            CharSequence label = TextUtils.ellipsize(menuItems.get(i).label, text, tmp.width() - 70f * s,
                    TextUtils.TruncateAt.END);
            c.drawText(label, 0, label.length(), tmp.centerX(), tmp.centerY() + 14f * s, text);
        }
        // More rows above or below: small blue arrows at the panel's edge.
        fill.setColor(accentArgb);
        if (menuFirst > 0) drawChevron(c, w / 2f, py + head - 26f * s, true);
        if (menuFirst + n < menuItems.size()) drawChevron(c, w / 2f, py + ph - 22f * s, false);
        c.restoreToCount(save);
    }

    /** The Settings screen: title and sheet number, four big buttons, arrows to other sheets, Back below. */
    private void drawSettings(Canvas c) {
        if (settings.isEmpty() && settingsAlpha <= 0f) return;
        int a = Math.round(settingsAlpha * 255);
        int save = a < 255 ? c.saveLayerAlpha(0, 0, w, h, a) : c.save();
        if (base != null) {
            // Only the striped part of the backdrop, above the channel panel, stretched to the full screen.
            android.graphics.Rect src = new android.graphics.Rect(0, 0, base.getWidth(), Math.round(760f * s));
            c.drawBitmap(base, src, new RectF(0, 0, w, h), bmpPaint);
        } else {
            c.drawColor(0xFFE9E9E9);
        }
        if (settings.isEmpty()) {
            c.restoreToCount(save);
            return;
        }
        SettingsPage p = top();
        int sheets = sheets(p);

        text.setTypeface(sansBold);
        text.setTextSize(54f * s);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(DATE_COLOR);
        c.drawText(p.title, 150f * s, 150f * s, text);
        if (sheets > 1) {
            text.setTextAlign(Paint.Align.RIGHT);
            text.setTextSize(46f * s);
            c.drawText((p.sheet + 1) + " / " + sheets, 1770f * s, 150f * s, text);
        }
        stroke.setColor(0xFFC4C4C4);
        stroke.setStrokeWidth(3f * s);
        c.drawLine(120f * s, 186f * s, 1800f * s, 186f * s, stroke);

        int first = p.sheet * SETTINGS_ROWS;
        int onSheet = Math.min(SETTINGS_ROWS, p.items.size() - first);
        for (int r = 0; r < onSheet; r++) {
            MenuItem item = p.items.get(first + r);
            float top = SET_BTN_TOP + r * SET_BTN_PITCH;
            tmp.set((WiiBase.CX - SET_BTN_W / 2f) * s, top * s, (WiiBase.CX + SET_BTN_W / 2f) * s, (top + SET_BTN_H) * s);
            drawPill(c, tmp, settingsLift[r]);
            text.setTextSize(46f * s);
            text.setColor(0xFF5E5E5E);
            if (item.value == null) {
                text.setTextAlign(Paint.Align.CENTER);
                CharSequence l = TextUtils.ellipsize(item.label, text, tmp.width() - 80f * s, TextUtils.TruncateAt.END);
                c.drawText(l, 0, l.length(), tmp.centerX(), tmp.centerY() + 16f * s, text);
            } else {
                text.setTextAlign(Paint.Align.RIGHT);
                text.setColor(0xFF2E86BF);
                float vw = Math.min(text.measureText(item.value), tmp.width() * 0.45f);
                CharSequence v = TextUtils.ellipsize(item.value, text, vw, TextUtils.TruncateAt.END);
                c.drawText(v, 0, v.length(), tmp.right - 64f * s, tmp.centerY() + 16f * s, text);
                text.setTextAlign(Paint.Align.LEFT);
                text.setColor(0xFF5E5E5E);
                CharSequence l = TextUtils.ellipsize(item.label, text, tmp.width() - vw - 170f * s, TextUtils.TruncateAt.END);
                c.drawText(l, 0, l.length(), tmp.left + 64f * s, tmp.centerY() + 16f * s, text);
            }
        }
        if (p.sheet < sheets - 1) drawArrow(c, ARROW_TIP * s, SET_ARROW_Y * s, false);
        if (p.sheet > 0) drawArrow(c, (2f * WiiBase.CX - ARROW_TIP) * s, SET_ARROW_Y * s, true);

        // Bottom band with the blue rim, as under the channels, and the Back button on it.
        fill.setShader(new LinearGradient(0, SET_BAND_TOP * s, 0, h, 0xFFF2F2F2, 0xFFD3D3D6, Shader.TileMode.CLAMP));
        c.drawRect(0, SET_BAND_TOP * s, w, h, fill);
        fill.setShader(null);
        stroke.setColor(accentArgb);
        stroke.setStrokeWidth(5f * s);
        c.drawLine(0, SET_BAND_TOP * s, w, SET_BAND_TOP * s, stroke);
        tmp.set(SET_BACK_L * s, SET_BACK_T * s, SET_BACK_R * s, SET_BACK_B * s);
        drawPill(c, tmp, settingsLift[SETTINGS_ROWS]);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(44f * s);
        text.setColor(0xFF5E5E5E);
        c.drawText(backLabel, tmp.centerX(), tmp.centerY() + 15f * s, text);
        c.restoreToCount(save);
    }

    /** A white rounded button with the grey rim, glowing blue as it is focused. */
    private void drawPill(Canvas c, RectF r, float lf) {
        float rad = Math.min(r.height() / 2f, 40f * s);
        int sv = c.save();
        float sc = 1f + 0.03f * lf;
        c.scale(sc, sc, r.centerX(), r.centerY());
        fill.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0xFFFFFFFF, 0xFFE1E1E1, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, fill);
        fill.setShader(null);
        if (lf > 0.01f) {
            for (int k = 3; k >= 1; k--) {
                stroke.setColor(withAlpha(accentArgb, (int) (lf * 70 / k)));
                stroke.setStrokeWidth((4f + k * 6f) * s);
                c.drawRoundRect(r, rad, rad, stroke);
            }
        }
        stroke.setColor(blend(TILE_BORDER_COLOR, accentArgb, lf));
        stroke.setStrokeWidth((TILE_BORDER + 2f * lf) * s);
        c.drawRoundRect(r, rad, rad, stroke);
        c.restoreToCount(sv);
    }

    private void drawChevron(Canvas c, float cx, float cy, boolean up) {
        float hw = 18f * s, hh = 10f * s * (up ? -1f : 1f);
        path.reset();
        path.moveTo(cx - hw, cy - hh);
        path.lineTo(cx + hw, cy - hh);
        path.lineTo(cx, cy + hh);
        path.close();
        c.drawPath(path, fill);
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
