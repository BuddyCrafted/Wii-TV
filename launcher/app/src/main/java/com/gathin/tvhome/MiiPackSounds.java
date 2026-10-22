package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import org.json.JSONObject;

import java.io.File;

/** The channel's sounds from a MiiPack, by the names its pack.json maps to sample files. */
final class MiiPackSounds {
    /** By WiiSounds id, then by MiiAudio effect id. */
    private static final String[] UI = {"move", "select", "page", "back", "menu", "start"};
    private static final String[] SFX = {"whistle", "pop", "lift", "drop", "poof", "hop"};

    private final SoundPool pool;
    private final int[] ui = new int[UI.length], sfx = new int[SFX.length];
    private final float[] uiVol = new float[UI.length], sfxVol = new float[SFX.length];
    private final int[] uiLoop = new int[UI.length], sfxLoop = new int[SFX.length];

    MiiPackSounds(Context context, MiiPack pack) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build();
        load(pack, UI, ui, uiVol, uiLoop);
        load(pack, SFX, sfx, sfxVol, sfxLoop);
    }

    private void load(MiiPack pack, String[] names, int[] ids, float[] vol, int[] loop) {
        for (int i = 0; i < names.length; i++) {
            JSONObject o = pack.sounds.optJSONObject(names[i]);
            if (o == null) continue;
            File f = pack.sound(o.optString("file"));
            if (f == null) continue;
            ids[i] = pool.load(f.getPath(), 1);
            vol[i] = (float) o.optDouble("vol", 0.6);
            loop[i] = o.optInt("loop", 0);
        }
    }

    /** Plays an interface sound (a WiiSounds id); false if the pack has none for it. */
    boolean ui(int which) {
        return play(ui, uiVol, uiLoop, which);
    }

    /** Plays a plaza effect (a MiiAudio id); false if the pack has none for it. */
    boolean sfx(int which) {
        return play(sfx, sfxVol, sfxLoop, which);
    }

    private boolean play(int[] ids, float[] vol, int[] loop, int which) {
        if (which < 0 || which >= ids.length || ids[which] == 0) return false;
        pool.play(ids[which], vol[which], vol[which], 1, loop[which], 1f);
        return true;
    }

    void release() {
        pool.release();
    }
}
