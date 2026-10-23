package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/**
 * The channel menu's interface sounds (res/raw/wii_*.wav, synthesised by tools/sounds/make_sounds.py).
 * They play on the media stream, so the remote's volume keys set how loud they are.
 */
final class WiiSounds {
    static final int MOVE = 0;
    static final int SELECT = 1;
    static final int PAGE = 2;
    static final int BACK = 3;
    static final int MENU = 4;
    static final int START = 5;
    /** Winter's hockey scene, quieter than the interface: a punch, a slapshot, the goal horn. */
    static final int PUNCH = 6;
    static final int SLAPSHOT = 7;
    static final int GOAL = 8;
    /** Opening a channel: only with your own sound (sounds/open). */
    static final int OPEN = 9;
    /** The SD Card and Message Board buttons: your own sounds (sounds/sd, sounds/board), else select and menu. */
    static final int SD = 10, BOARD = 11;
    /** WiiOS Settings closing (the Home Button menu's exit), a finished install, and something that failed. */
    static final int MENU_CLOSE = 12, DONE = 13, ERROR = 14;
    /** Your own sounds' names (SoundPack sounds/<name>), by id; null = built-in only. */
    private static final String[] NAMES = {"move", "select", "page", "back", "menu", "start", null, null, null, "open",
            "sd", "board", "menu_close", "done", "error"};

    private static final float VOLUME = 0.6f, SCENE_VOLUME = 0.3f;

    private final SoundPool pool;
    private final int[] ids = new int[15];
    private boolean enabled;

    WiiSounds(Context context, boolean enabled) {
        this.enabled = enabled;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build();
        // Loaded even when off, so the menu's Sounds switch can turn them on without a restart.
        ids[MOVE] = pool.load(context, R.raw.wii_move, 1);
        ids[SELECT] = pool.load(context, R.raw.wii_select, 1);
        ids[PAGE] = pool.load(context, R.raw.wii_page, 1);
        ids[BACK] = pool.load(context, R.raw.wii_back, 1);
        ids[MENU] = pool.load(context, R.raw.wii_menu, 1);
        ids[START] = pool.load(context, R.raw.wii_start, 1);
        jingle = WiiMusic.customFile(context, "menu_start");
        ids[PUNCH] = pool.load(context, R.raw.fx_punch, 1);
        ids[SLAPSHOT] = pool.load(context, R.raw.fx_slapshot, 1);
        ids[GOAL] = pool.load(context, R.raw.fx_goal, 1);
        // Your own sounds replace the built-in ones, name by name.
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i] == null) continue;
            java.io.File own = SoundPack.find(context, SoundPack.SOUNDS, NAMES[i]);
            if (own != null) ids[i] = pool.load(own.getPath(), 1);
        }
    }

    /** Your own start jingle (menu_start in the music folder), played instead of the built-in start sound. */
    private final java.io.File jingle;
    private android.media.MediaPlayer jinglePlayer;

    void play(int which) {
        // The corner buttons fall back to the usual sounds when there's no file of their own.
        if (which == SD && ids[SD] == 0) which = SELECT;
        if (which == BOARD && ids[BOARD] == 0) which = MENU;
        if (which == MENU_CLOSE && ids[MENU_CLOSE] == 0) which = BACK;
        // done and error have no built-in sound: quiet unless you've added one.
        if (which == START && jingle != null) {
            if (enabled) playJingle();
            return;
        }
        float v = which >= PUNCH && which <= GOAL ? SCENE_VOLUME : VOLUME;
        if (enabled && ids[which] != 0) pool.play(ids[which], v, v, 1, 0, 1f);
    }

    void setEnabled(boolean on) {
        enabled = on;
    }

    boolean isEnabled() {
        return enabled;
    }

    private void playJingle() {
        if (jinglePlayer != null) jinglePlayer.release();
        android.media.MediaPlayer m = new android.media.MediaPlayer();
        jinglePlayer = m;
        try {
            m.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            m.setDataSource(jingle.getPath());
            m.setVolume(VOLUME, VOLUME);
            m.setOnCompletionListener(done -> {
                done.release();
                if (jinglePlayer == done) jinglePlayer = null;
            });
            m.prepare();
            m.start();
        } catch (java.io.IOException | RuntimeException e) {
            m.release();
            jinglePlayer = null;
        }
    }

    void release() {
        if (jinglePlayer != null) jinglePlayer.release();
        jinglePlayer = null;
        pool.release();
    }
}
