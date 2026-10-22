package com.gathin.tvhome;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Plays MenuMusic's loop while the channel menu is in front, on the media stream so the remote's volume
 * keys set it, quietly enough to sit under the interface sounds. The loop is rendered once, off the UI
 * thread, the first time music is wanted; a feeder thread streams it round and round.
 *
 * Your own music file plays instead when there is one in the app's music folder on the device
 * (Android/data/com.gathin.tvhome/files/music/), named for the screen: menu, winter (the Winter theme's menu;
 * menu is used when there's none), shop or games, as .mp3, .ogg, .m4a, .flac or .wav. It loops without a
 * jump: the end crossfades into the start. The installer copies them there with MUSIC_DIR; nothing is
 * bundled in the APK.
 *
 * Like the console, the music fades in when the screen comes up and fades out when you leave it, starts
 * from the top each time you come back, and holds off while another app is playing sound.
 */
final class WiiMusic {
    private static final float VOLUME = 0.28f;
    private static final int CHUNK = 4096;
    private static final float FADE_IN_S = 1.2f, FADE_OUT_S = 0.35f;
    /** Your own file's end blends into its start over this long. */
    private static final long CROSSFADE_MS = 2500;
    private static final long OTHER_AUDIO_RECHECK_MS = 2000, STEP_MS = 16, LOOP_WATCH_MS = 100;

    /** How many of WiiOS's own players are sounding, so they aren't mistaken for another app's sound. */
    private static final java.util.concurrent.atomic.AtomicInteger OWN = new java.util.concurrent.atomic.AtomicInteger();

    private final Object lock = new Object();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private boolean enabled;
    private boolean wanted;
    private boolean released;
    /** The output is running (or fading out); the feeder writes only while this is set. */
    private boolean playing;
    /** Start from the top the next time the output starts. */
    private boolean restartNext;
    private volatile boolean rewind;
    /** Master fade, 0..1, and where it is heading. */
    private float gain, target;
    private float fadeSeconds = FADE_IN_S;
    private Thread feeder;
    private AudioTrack track;

    private final java.io.File cache;

    private final MenuMusic.Song song;

    /** The user's own file for this screen, or null to play the built-in tune. */
    private final java.io.File custom;
    /** Two players over the same file, so the end can crossfade into the start. */
    private MediaPlayer a, b;
    private float xa = 1f, xb = 0f;
    private boolean crossing;
    private long crossStart;

    WiiMusic(android.content.Context context, boolean enabled) {
        this(context, enabled, MenuMusic.MENU, "menu_music_v1.pcm", "menu");
    }

    WiiMusic(android.content.Context context, boolean enabled, MenuMusic.Song song, String cacheName, String slot) {
        this.enabled = enabled;
        this.song = song;
        java.io.File own = customFile(context, slot);
        if (own == null && "winter".equals(slot)) own = customFile(context, "menu");
        this.custom = own;
        this.audio = (AudioManager) context.getApplicationContext().getSystemService(android.content.Context.AUDIO_SERVICE);
        // The rendered loop is kept on disk: Android often kills the launcher on a 1 GB stick while another app
        // runs, and reading 1.7 MB back is much cheaper than rendering it again on a 1.7 GHz CPU.
        this.cache = new java.io.File(context.getApplicationContext().getFilesDir(), cacheName);
    }

    static final String[] EXTENSIONS = {".mp3", ".ogg", ".m4a", ".flac", ".wav"};

    static java.io.File customFile(android.content.Context context, String slot) {
        java.io.File dir = context.getExternalFilesDir("music");
        if (dir == null) return null;
        for (String ext : EXTENSIONS) {
            java.io.File f = new java.io.File(dir, slot + ext);
            if (f.isFile() && f.length() > 0) return f;
        }
        return null;
    }

    /** The menu's music for a theme: Winter plays your own winter file when you've added one. */
    static WiiMusic forTheme(android.content.Context context, boolean enabled, Theme theme) {
        if (theme.deco == Theme.Deco.SNOW) {
            return new WiiMusic(context, enabled, MenuMusic.MENU, "menu_music_v1.pcm", "winter");
        }
        return new WiiMusic(context, enabled);
    }

    /** Whether the menu is in front and wants music (for handing over to another tune). */
    boolean isWanted() {
        synchronized (lock) {
            return wanted;
        }
    }

    /** The menu came to the front. */
    void resume() {
        synchronized (lock) {
            wanted = true;
            update();
        }
    }

    /** The menu left the front. */
    void pause() {
        synchronized (lock) {
            wanted = false;
            update();
        }
    }

    void setEnabled(boolean on) {
        synchronized (lock) {
            enabled = on;
            update();
        }
    }

    boolean isEnabled() {
        synchronized (lock) {
            return enabled;
        }
    }

    void release() {
        synchronized (lock) {
            released = true;
            if (playing) OWN.decrementAndGet();
            playing = false;
            main.removeCallbacksAndMessages(null);
            if (a != null) a.release();
            if (b != null) b.release();
            a = b = null;
            // stop() also wakes a write() that is blocked on a paused track.
            if (track != null) track.stop();
            lock.notifyAll();
        }
    }

    private final Runnable recheck = () -> {
        synchronized (lock) {
            update();
        }
    };

    /** Called with the lock held, on the main thread. */
    private void update() {
        boolean play = enabled && wanted && !released;
        main.removeCallbacks(recheck);
        if (play && !playing && othersPlaying()) {
            // Someone else is playing (Spotify, a video in the background): wait until they stop.
            main.postDelayed(recheck, OTHER_AUDIO_RECHECK_MS);
            return;
        }
        if (play) {
            startOutput();
            fadeTo(1f, FADE_IN_S);
        } else if (playing) {
            fadeTo(0f, FADE_OUT_S);
        }
    }

    private boolean othersPlaying() {
        try {
            return audio != null && audio.isMusicActive() && OWN.get() == 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void startOutput() {
        if (playing) return;
        playing = true;
        OWN.incrementAndGet();
        if (custom != null) {
            if (a == null) a = open();
            if (a == null) {
                playing = false;
                OWN.decrementAndGet();
                return; // unreadable file: stay quiet rather than fall back mid-session
            }
            if (restartNext) {
                a.seekTo(0);
                if (b != null) b.seekTo(0);
            }
            xa = 1f;
            xb = 0f;
            crossing = false;
            applyVolume();
            a.start();
            main.removeCallbacks(loopWatch);
            main.postDelayed(loopWatch, LOOP_WATCH_MS);
        } else {
            if (feeder == null) {
                feeder = new Thread(this::feed, "wii-music");
                feeder.setPriority(Thread.NORM_PRIORITY - 1);
                feeder.start();
            }
            if (track != null) {
                if (restartNext) {
                    track.pause();
                    track.flush();
                    rewind = true;
                }
                applyVolume();
                track.play();
            }
            lock.notifyAll();
        }
        restartNext = false;
    }

    private void stopOutput() {
        if (playing) OWN.decrementAndGet();
        playing = false;
        restartNext = true;
        main.removeCallbacks(loopWatch);
        crossing = false;
        if (a != null && a.isPlaying()) a.pause();
        if (b != null && b.isPlaying()) b.pause();
        if (track != null) track.pause();
    }

    private MediaPlayer open() {
        MediaPlayer m = null;
        try {
            m = new MediaPlayer();
            m.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            m.setDataSource(custom.getPath());
            m.prepare();
            return m;
        } catch (java.io.IOException | RuntimeException e) {
            if (m != null) m.release();
            return null;
        }
    }

    // ---- fades -------------------------------------------------------------------------------------

    private long lastStep;

    private void fadeTo(float to, float seconds) {
        target = to;
        fadeSeconds = seconds;
        lastStep = SystemClock.uptimeMillis();
        main.removeCallbacks(fadeStep);
        main.post(fadeStep);
    }

    private final Runnable fadeStep = new Runnable() {
        @Override
        public void run() {
            synchronized (lock) {
                if (released) return;
                long now = SystemClock.uptimeMillis();
                float step = (now - lastStep) / 1000f / fadeSeconds;
                lastStep = now;
                gain = gain < target ? Math.min(target, gain + step) : Math.max(target, gain - step);
                applyVolume();
                if (gain != target) {
                    main.postDelayed(this, STEP_MS);
                } else if (target == 0f) {
                    stopOutput();
                }
            }
        }
    };

    private void applyVolume() {
        // Squared, so the fade sounds even to the ear rather than rushing at the start.
        float v = VOLUME * gain * gain;
        if (track != null) track.setVolume(v);
        if (a != null) a.setVolume(v * xa, v * xa);
        if (b != null) b.setVolume(v * xb, v * xb);
    }

    /** Your own file: shortly before its end, start it again underneath and crossfade, so the loop never jumps. */
    private final Runnable loopWatch = new Runnable() {
        @Override
        public void run() {
            synchronized (lock) {
                if (!playing || released || a == null) return;
                long now = SystemClock.uptimeMillis();
                int duration = a.getDuration();
                if (!crossing) {
                    if (duration > 0 && duration < CROSSFADE_MS * 3) {
                        a.setLooping(true);   // too short to crossfade: a plain loop
                        return;
                    }
                    if (duration > 0 && a.getCurrentPosition() >= duration - CROSSFADE_MS) {
                        if (b == null) b = open();
                        if (b != null) {
                            b.seekTo(0);
                            xb = 0f;
                            applyVolume();
                            b.start();
                            crossing = true;
                            crossStart = now;
                        }
                    }
                } else {
                    float f = Math.min(1f, (now - crossStart) / (float) CROSSFADE_MS);
                    // Equal power, so the blend doesn't dip in the middle.
                    xa = (float) Math.cos(f * Math.PI / 2);
                    xb = (float) Math.sin(f * Math.PI / 2);
                    applyVolume();
                    if (f >= 1f) {
                        a.pause();
                        a.seekTo(0);
                        MediaPlayer t = a;
                        a = b;
                        b = t;
                        xa = 1f;
                        xb = 0f;
                        crossing = false;
                        applyVolume();
                    }
                }
                main.postDelayed(this, crossing ? STEP_MS * 2 : LOOP_WATCH_MS);
            }
        }
    };

    private short[] loadOrRender() {
        int n = MenuMusic.length(song);
        if (cache.length() == n * 2L) {
            try (java.io.DataInputStream in = new java.io.DataInputStream(
                    new java.io.BufferedInputStream(new java.io.FileInputStream(cache), 1 << 16))) {
                byte[] b = new byte[n * 2];
                in.readFully(b);
                short[] pcm = new short[n];
                java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
                return pcm;
            } catch (java.io.IOException ignored) {
                // fall through and render
            }
        }
        short[] pcm = MenuMusic.render(song);
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(cache)) {
            java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(pcm.length * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            b.asShortBuffer().put(pcm);
            out.write(b.array());
        } catch (java.io.IOException ignored) {
            // no cache this time
        }
        return pcm;
    }

    private void feed() {
        short[] pcm = loadOrRender();
        AudioTrack t;
        try {
            int min = AudioTrack.getMinBufferSize(MenuMusic.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            t = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(MenuMusic.RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(min, CHUNK * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
        } catch (RuntimeException e) {
            return; // no audio output: the menu simply stays quiet
        }
        main.post(() -> {
            synchronized (lock) {
                if (released) {
                    t.release();
                    return;
                }
                track = t;
                applyVolume();
                if (playing) t.play();
                lock.notifyAll();
            }
        });
        int pos = 0;
        try {
            while (true) {
                synchronized (lock) {
                    while (!released && (!playing || track == null)) lock.wait();
                    if (released) break;
                }
                if (rewind) {
                    rewind = false;
                    pos = 0;
                }
                int n = Math.min(CHUNK, pcm.length - pos);
                int wrote = t.write(pcm, pos, n);
                if (wrote < 0) break;
                pos = (pos + wrote) % pcm.length;
            }
        } catch (InterruptedException ignored) {
            // released
        } finally {
            if (track != t) t.release();
            else main.post(t::release);
        }
    }
}
