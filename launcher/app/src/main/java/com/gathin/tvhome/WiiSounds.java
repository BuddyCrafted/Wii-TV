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

    private static final float VOLUME = 0.6f, SCENE_VOLUME = 0.3f;

    private final SoundPool pool;
    private final int[] ids = new int[9];
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
        ids[PUNCH] = pool.load(context, R.raw.fx_punch, 1);
        ids[SLAPSHOT] = pool.load(context, R.raw.fx_slapshot, 1);
        ids[GOAL] = pool.load(context, R.raw.fx_goal, 1);
    }

    void play(int which) {
        float v = which >= PUNCH ? SCENE_VOLUME : VOLUME;
        if (enabled && ids[which] != 0) pool.play(ids[which], v, v, 1, 0, 1f);
    }

    void setEnabled(boolean on) {
        enabled = on;
    }

    boolean isEnabled() {
        return enabled;
    }

    void release() {
        pool.release();
    }
}
