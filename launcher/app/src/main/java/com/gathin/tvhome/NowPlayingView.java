package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The Now Playing screensaver: on black, the clock and date, and while music or a podcast plays (any app
 * with a media session: Spotify, YouTube Music, Pocket Casts...) its artwork, title, artist and progress.
 * Every minute the whole block drifts to a new spot so nothing burns in. After `darkAfterMs` it fades to
 * a fully black screen; the audio keeps playing, and any key brings the picture back.
 *
 * Reading other apps' sessions needs MediaListener enabled as a notification listener (build-and-install.sh
 * does it over ADB). Without it the screensaver is the clock alone.
 */
final class NowPlayingView extends View {
    private static final long DRIFT_MS = 60_000L;
    private static final int BLUE = 0xFF3FC0EC;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Typeface light, medium;

    private MediaSessionManager sessions;
    private MediaController controller;
    private Bitmap art;
    private BitmapShader artShader;
    private String appLabel = "";
    private float driftX, driftY;
    private long lastDrift;
    private long startedAt;
    private long darkAfterMs;
    private float alpha = 1f;

    /** What is playing, read from the media session (or handed in directly). */
    static final class Track {
        String title = "", artist = "", album = "", app = "";
        Bitmap art;
        long duration, position;
        boolean paused;
    }

    /** Set by show(): drawn instead of the session; for previews and tests. */
    private Track fixed;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = list -> pick(list);
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            loadArt();
            invalidate();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            invalidate();
        }

        @Override
        public void onSessionDestroyed() {
            refresh();
        }
    };
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            handler.postDelayed(this, 1000);
        }
    };

    NowPlayingView(Context context, long darkAfterMs) {
        super(context);
        this.darkAfterMs = darkAfterMs;
        light = font(context, R.font.outfit_light);
        medium = font(context, R.font.outfit_medium);
        setBackgroundColor(0xFF000000);
    }

    private static Typeface font(Context c, int id) {
        try {
            return c.getResources().getFont(id);
        } catch (RuntimeException e) {
            return Typeface.SANS_SERIF;
        }
    }

    void start() {
        startedAt = SystemClock.uptimeMillis();
        alpha = 1f;
        try {
            sessions = (MediaSessionManager) getContext().getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName listener = new ComponentName(getContext(), MediaListener.class);
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener);
            pick(sessions.getActiveSessions(listener));
        } catch (RuntimeException e) {
            sessions = null; // listener not enabled: clock only
        }
        handler.post(tick);
    }

    void stop() {
        handler.removeCallbacks(tick);
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
        if (controller != null) controller.unregisterCallback(callback);
        controller = null;
    }

    /** A key was pressed: the picture comes back from dark. */
    void wake() {
        startedAt = SystemClock.uptimeMillis();
        alpha = 1f;
        invalidate();
    }

    private void refresh() {
        if (sessions == null) return;
        try {
            pick(sessions.getActiveSessions(new ComponentName(getContext(), MediaListener.class)));
        } catch (RuntimeException ignored) {
            pick(null);
        }
    }

    private void pick(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController mc : list) {
                PlaybackState st = mc.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                    best = mc;
                    break;
                }
                if (best == null && mc.getMetadata() != null) best = mc;
            }
        }
        if (controller != null) controller.unregisterCallback(callback);
        controller = best;
        appLabel = "";
        if (best != null) {
            best.registerCallback(callback, handler);
            try {
                PackageManager pm = getContext().getPackageManager();
                appLabel = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(best.getPackageName(), 0)));
            } catch (PackageManager.NameNotFoundException ignored) {
                appLabel = best.getPackageName();
            }
        }
        loadArt();
        invalidate();
    }

    private void loadArt() {
        art = null;
        artShader = null;
        MediaMetadata md = controller == null ? null : controller.getMetadata();
        if (md == null) return;
        Bitmap b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        art = b;
        if (b != null) artShader = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    }

    /** Shows this track instead of reading media sessions. */
    void show(Track t) {
        fixed = t;
        art = t.art;
        artShader = art == null ? null : new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        invalidate();
    }

    /** The playing (or paused) track, or null when nothing plays. */
    private Track track() {
        if (fixed != null) return fixed;
        if (controller == null || controller.getMetadata() == null) return null;
        PlaybackState st = controller.getPlaybackState();
        if (st == null || (st.getState() != PlaybackState.STATE_PLAYING
                && st.getState() != PlaybackState.STATE_PAUSED && st.getState() != PlaybackState.STATE_BUFFERING)) {
            return null;
        }
        MediaMetadata md = controller.getMetadata();
        Track t = new Track();
        t.title = str(md, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        t.artist = str(md, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        t.album = str(md, MediaMetadata.METADATA_KEY_ALBUM, MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION);
        t.app = appLabel;
        t.duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long pos = st.getPosition();
        if (st.getState() == PlaybackState.STATE_PLAYING) {
            pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
        }
        t.position = Math.max(0, t.duration > 0 ? Math.min(t.duration, pos) : pos);
        t.paused = st.getState() == PlaybackState.STATE_PAUSED;
        return t;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float s = w / 1920f;
        long now = SystemClock.uptimeMillis();
        if (now - lastDrift > DRIFT_MS || lastDrift == 0) {
            lastDrift = now;
            driftX = (random.nextFloat() - 0.5f) * 260f;
            driftY = (random.nextFloat() - 0.5f) * 160f;
        }
        if (darkAfterMs > 0 && now - startedAt > darkAfterMs) {
            alpha = Math.max(0f, alpha - 0.05f);
        }
        if (alpha <= 0f) return;
        int save = c.saveLayerAlpha(0, 0, w, h, Math.round(alpha * 255));
        c.translate(driftX * s, driftY * s);

        Locale locale = getResources().getConfiguration().getLocales().get(0);
        Date date = new Date();
        String time = android.text.format.DateFormat.getTimeFormat(getContext()).format(date);
        String day = new SimpleDateFormat("EEEE, MMMM d", locale).format(date);

        Track track = track();
        if (track != null) {
            String title = track.title, artist = track.artist, album = track.album;

            float artSize = 420f * s, ax = 300f * s, ay = 300f * s;
            rect.set(ax, ay, ax + artSize, ay + artSize);
            if (artShader != null) {
                Matrix m = new Matrix();
                float sc = Math.max(artSize / art.getWidth(), artSize / art.getHeight());
                m.setScale(sc, sc);
                m.postTranslate(ax + (artSize - art.getWidth() * sc) / 2f, ay + (artSize - art.getHeight() * sc) / 2f);
                artShader.setLocalMatrix(m);
                paint.setShader(artShader);
                c.drawRoundRect(rect, 28f * s, 28f * s, paint);
                paint.setShader(null);
            } else {
                paint.setColor(0xFF1E2A33);
                c.drawRoundRect(rect, 28f * s, 28f * s, paint);
                text.setTypeface(medium);
                text.setTextSize(160f * s);
                text.setTextAlign(Paint.Align.CENTER);
                text.setColor(BLUE);
                c.drawText("\u266A", rect.centerX(), rect.centerY() + 55f * s, text);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f * s);
            paint.setColor(0x55FFFFFF);
            c.drawRoundRect(rect, 28f * s, 28f * s, paint);
            paint.setStyle(Paint.Style.FILL);

            float tx = ax + artSize + 80f * s, maxW = 1920f * s - tx - 220f * s;
            text.setTextAlign(Paint.Align.LEFT);
            text.setTypeface(medium);
            text.setColor(0xFFF2F2F2);
            text.setTextSize(72f * s);
            line(c, title, tx, ay + 110f * s, maxW);
            text.setTypeface(light);
            text.setColor(0xFFBDBDBD);
            text.setTextSize(50f * s);
            line(c, artist, tx, ay + 190f * s, maxW);
            text.setTextSize(40f * s);
            text.setColor(0xFF8A8A8A);
            line(c, album, tx, ay + 255f * s, maxW);

            // Progress bar and times, when the app reports a duration.
            long dur = track.duration, pos = track.position;
            if (dur > 0) {
                float by = ay + 340f * s, bw = maxW;
                paint.setColor(0xFF333333);
                c.drawRoundRect(tx, by, tx + bw, by + 12f * s, 6f * s, 6f * s, paint);
                paint.setColor(BLUE);
                c.drawRoundRect(tx, by, tx + bw * pos / dur, by + 12f * s, 6f * s, 6f * s, paint);
                text.setTextSize(34f * s);
                text.setColor(0xFF8A8A8A);
                c.drawText(clock(pos), tx, by + 60f * s, text);
                text.setTextAlign(Paint.Align.RIGHT);
                c.drawText(clock(dur), tx + bw, by + 60f * s, text);
                text.setTextAlign(Paint.Align.LEFT);
            }
            text.setTextSize(34f * s);
            text.setColor(0xFF6A6A6A);
            line(c, track.paused ? "\u275A\u275A  " + track.app : track.app, tx, ay + 470f * s, maxW);

            text.setTypeface(light);
            text.setTextAlign(Paint.Align.RIGHT);
            text.setColor(0xFF9A9A9A);
            text.setTextSize(64f * s);
            c.drawText(time, 1620f * s, 200f * s, text);
        } else {
            text.setTypeface(light);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(0xFFDADADA);
            text.setTextSize(200f * s);
            c.drawText(time, w / 2f, h / 2f + 40f * s, text);
            text.setColor(0xFF8A8A8A);
            text.setTextSize(52f * s);
            c.drawText(day, w / 2f, h / 2f + 140f * s, text);
        }
        c.restoreToCount(save);
        if (alpha < 1f && alpha > 0f) postInvalidateOnAnimation();
    }

    private void line(Canvas c, String s, float x, float y, float maxW) {
        if (s == null || s.isEmpty()) return;
        CharSequence t = TextUtils.ellipsize(s, text, maxW, TextUtils.TruncateAt.END);
        c.drawText(t, 0, t.length(), x, y, text);
    }

    private static String str(MediaMetadata md, String... keys) {
        for (String k : keys) {
            CharSequence v = md.getText(k);
            if (v != null && v.length() > 0) return v.toString();
        }
        return "";
    }

    static String clock(long ms) {
        long sec = ms / 1000;
        long h = sec / 3600, m = (sec % 3600) / 60, ss = sec % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, ss) : String.format(Locale.ROOT, "%d:%02d", m, ss);
    }
}
