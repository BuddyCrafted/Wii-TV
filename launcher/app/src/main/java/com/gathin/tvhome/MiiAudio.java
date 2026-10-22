package com.gathin.tvhome;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The Mii channel's music and effects, synthesised as they play: a small sequencer runs an original
 * bossa-flavoured loop (bell lead, plucked bass, electric-piano stabs, soft drums) and mixes in the
 * channel's effects. Nothing is sampled, so it adds nothing to the APK.
 */
final class MiiAudio {
    static final int WHISTLE = 0, POP = 1, LIFT = 2, DROP = 3, POOF = 4, HOP = 5;

    private static final int RATE = 32000;
    private static final int BLOCK = 512;
    private static final double BEAT = 60.0 / 108.0;
    private static final double SWING = 0.12 * BEAT;
    private static final float MASTER = 0.42f;

    private static final int V_BELL = 0, V_BASS = 1, V_EP = 2, V_HAT = 3, V_KICK = 4, V_RIM = 5, V_WHISTLE = 6,
            V_POP = 7, V_LIFT = 8, V_BOING = 9, V_POOF = 10, V_HOP = 11;

    private static final String[] MELODY = {
            "E5 . G5 E5 . C5 D5 E5", "C5 . A4 . E5 . D5 C5", "D5 . F5 D5 . A4 C5 D5", "B4 . G4 . D5 - . .",
            "E5 . G5 E5 . C6 B5 A5", "G5 - E5 . C#5 . E5 G5", "F5 . A5 F5 D5 . B4 D5", "C5 . E5 G5 C6 - . .",
            "A5 . . G5 A5 . F5 .", "G5 . . E5 D5 . B4 .", "F5 . . E5 F5 . A5 .", "G5 - F5 - D5 - B4 .",
            "A5 . C6 A5 . F5 G5 A5", "G#5 . B5 G#5 . E5 . D5", "C5 . E5 A5 . F#5 A5 .", "F5 . D5 . B4 . G4 .",
    };
    /** One chord per half bar: bass root, then three voicing notes (MIDI). */
    private static final int[][] CHORDS;

    static {
        int[] cmaj = {48, 64, 67, 71}, am7 = {45, 60, 64, 67}, dm7 = {50, 65, 69, 72}, g7 = {43, 59, 65, 69};
        int[] a7 = {45, 61, 67, 69}, c6 = {48, 64, 69, 72}, fmaj = {53, 64, 69, 72}, em7 = {52, 62, 67, 71};
        int[] e7 = {52, 62, 68, 71}, d7 = {50, 60, 66, 69};
        CHORDS = new int[][] {cmaj, cmaj, am7, am7, dm7, dm7, g7, g7, cmaj, cmaj, a7, a7, dm7, g7, c6, c6,
                fmaj, fmaj, em7, em7, dm7, dm7, g7, g7, fmaj, fmaj, e7, e7, am7, d7, dm7, g7};
    }

    private static final int STEPS = MELODY.length * 8;
    private static final int[] MEL_NOTE = new int[STEPS];
    private static final int[] MEL_LEN = new int[STEPS];

    static {
        int i = 0;
        for (String bar : MELODY) {
            for (String tok : bar.trim().split("\\s+")) MEL_NOTE[i++] = parse(tok);
        }
        for (int k = 0; k < STEPS; k++) {
            if (MEL_NOTE[k] < 0) continue;
            int len = 1;
            while (len < 8 && MEL_NOTE[(k + len) % STEPS] == -2) len++;
            MEL_LEN[k] = len;
        }
    }

    private static int parse(String tok) {
        if (tok.equals(".")) return -1;
        if (tok.equals("-")) return -2;
        int[] base = {9, 11, 0, 2, 4, 5, 7};
        int n = base[tok.charAt(0) - 'A'];
        int i = 1;
        if (tok.charAt(i) == '#') {
            n++;
            i++;
        } else if (tok.charAt(i) == 'b') {
            n--;
            i++;
        }
        int oct = tok.charAt(i) - '0';
        return 12 * (oct + 1) + n;
    }

    private static double hz(int midi) {
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
    }

    private static final class Voice {
        int type;
        double f, phase, t, amp, decay;
        long wait;
        float lp, prev;
        boolean on;
    }

    private final boolean enabled;
    private final Voice[] voices = new Voice[40];
    private final ConcurrentLinkedQueue<Integer> pending = new ConcurrentLinkedQueue<>();
    private final Random rnd = new Random(7);
    private AudioTrack track;
    private Thread thread;
    private volatile boolean running;
    private volatile boolean calm;
    private volatile boolean music = true;
    private long pos;
    private long nextStep;
    private float musicGain;

    MiiAudio(boolean enabled) {
        this.enabled = enabled;
        for (int i = 0; i < voices.length; i++) voices[i] = new Voice();
    }

    /** Editor mode: the same tune without drums, a little quieter. */
    void setCalm(boolean on) {
        calm = on;
    }

    void setMusic(boolean on) {
        music = on;
    }

    void sfx(int id) {
        if (enabled && running) pending.add(id);
    }

    synchronized void resume() {
        if (!enabled || running) return;
        if (track == null) {
            try {
                int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setSampleRate(RATE)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(Math.max(min, BLOCK * 8))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();
            } catch (RuntimeException e) {
                track = null;
                return;
            }
        }
        musicGain = 0f;
        running = true;
        track.play();
        thread = new Thread(this::loop, "mii-audio");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    synchronized void pause() {
        if (!running) return;
        running = false;
        try {
            if (thread != null) thread.join(400);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        thread = null;
        if (track != null) {
            track.pause();
            track.flush();
        }
        for (Voice v : voices) v.on = false;
        pending.clear();
    }

    synchronized void release() {
        pause();
        if (track != null) track.release();
        track = null;
    }

    private void loop() {
        short[] buf = new short[BLOCK];
        float[] mix = new float[BLOCK];
        while (running) {
            Integer id;
            while ((id = pending.poll()) != null) startSfx(id);
            schedule();
            render(mix);
            for (int i = 0; i < BLOCK; i++) {
                float x = mix[i] * MASTER;
                x = x / (1f + Math.abs(x));
                buf[i] = (short) Math.round(x * 32000f);
            }
            AudioTrack t = track;
            if (t == null || t.write(buf, 0, BLOCK) < 0) break;
            pos += BLOCK;
        }
    }

    private double stepTime(long step) {
        double t = (step / 2) * BEAT;
        if ((step & 1) == 1) t += BEAT / 2 + SWING;
        return t;
    }

    private void schedule() {
        double horizon = (pos + BLOCK) / (double) RATE;
        while (stepTime(nextStep) < horizon) {
            long wait = Math.max(0, Math.round(stepTime(nextStep) * RATE) - pos);
            int st = (int) (nextStep % STEPS);
            int half = st / 4, inHalf = st % 4;
            int[] ch = CHORDS[half % CHORDS.length];
            boolean c = calm;
            if (MEL_NOTE[st] >= 0) {
                note(V_BELL, hz(MEL_NOTE[st]), c ? 0.22 : 0.3, 0.16 + 0.1 * MEL_LEN[st], wait);
            }
            if (inHalf == 0) {
                note(V_BASS, hz(ch[0]), 0.34, 0.32, wait);
                if (!c) note(V_KICK, 0, 0.32, 0.12, wait);
            } else if (inHalf == 2) {
                int fifth = ch[0] + 7 > 57 ? ch[0] - 5 : ch[0] + 7;
                note(V_BASS, hz(fifth), 0.28, 0.28, wait);
                for (int k = 1; k < 4; k++) note(V_EP, hz(ch[k]), c ? 0.06 : 0.08, 0.22, wait);
                if (!c) note(V_RIM, 0, 0.09, 0.03, wait);
            }
            note(V_HAT, 0, (c ? 0.5 : 1.0) * ((st & 1) == 1 ? 0.07 : 0.045), 0.02, wait);
            nextStep++;
        }
    }

    private void note(int type, double f, double amp, double decay, long wait) {
        Voice v = free();
        v.type = type;
        v.f = f;
        v.amp = amp;
        v.decay = decay;
        v.wait = wait;
        v.t = 0;
        v.phase = 0;
        v.lp = 0f;
        v.prev = 0f;
        v.on = true;
    }

    private Voice free() {
        Voice oldest = voices[0];
        for (Voice v : voices) {
            if (!v.on) return v;
            if (v.t > oldest.t) oldest = v;
        }
        return oldest;
    }

    private void startSfx(int id) {
        switch (id) {
            case WHISTLE:
                note(V_WHISTLE, 2350, 0.34, 0.125, 0);
                break;
            case POP:
                note(V_POP, 500, 0.4, 0.05, 0);
                break;
            case LIFT:
                note(V_LIFT, 340, 0.32, 0.04, 0);
                break;
            case DROP:
                note(V_BOING, 190, 0.42, 0.22, 0);
                break;
            case POOF:
                note(V_POOF, 0, 0.5, 0.13, 0);
                break;
            default:
                note(V_HOP, 420, 0.3, 0.07, 0);
                break;
        }
    }

    private void render(float[] mix) {
        boolean m = music;
        float targetGain = m ? (calm ? 0.8f : 1f) : 0f;
        float gStep = (targetGain - musicGain) / BLOCK * 0.08f;
        for (int i = 0; i < BLOCK; i++) mix[i] = 0f;
        double dt = 1.0 / RATE;
        for (Voice v : voices) {
            if (!v.on) continue;
            boolean isMusic = v.type <= V_RIM;
            for (int i = 0; i < BLOCK; i++) {
                if (v.wait > 0) {
                    v.wait--;
                    continue;
                }
                double t = v.t;
                double s;
                switch (v.type) {
                    case V_BELL: {
                        double env = Math.min(1.0, t / 0.004) * Math.exp(-t / v.decay);
                        double p = 2 * Math.PI * v.f * t;
                        s = env * (Math.sin(p) + 0.32 * Math.sin(2 * p) * Math.exp(-t / 0.06)
                                + 0.12 * Math.sin(4 * p) * Math.exp(-t / 0.025));
                        break;
                    }
                    case V_BASS: {
                        v.phase += v.f * dt;
                        double ph = v.phase - Math.floor(v.phase);
                        double tri = ph < 0.5 ? 4 * ph - 1 : 3 - 4 * ph;
                        double env = Math.min(1.0, t / 0.003) * Math.exp(-t / v.decay);
                        s = env * (0.8 * tri + 0.4 * Math.sin(2 * Math.PI * v.phase));
                        break;
                    }
                    case V_EP: {
                        double p = 2 * Math.PI * v.f * t;
                        double env = Math.min(1.0, t / 0.006) * Math.exp(-t / v.decay);
                        s = env * (Math.sin(p) + 0.25 * Math.sin(2 * p) * Math.exp(-t / 0.08));
                        break;
                    }
                    case V_HAT: {
                        float n = rnd.nextFloat() * 2f - 1f;
                        float hp = n - v.prev;
                        v.prev = n;
                        s = hp * 0.5 * Math.exp(-t / v.decay);
                        break;
                    }
                    case V_KICK: {
                        double f = 48 + 95 * Math.exp(-t / 0.03);
                        v.phase += f * dt;
                        s = Math.sin(2 * Math.PI * v.phase) * Math.exp(-t / v.decay);
                        break;
                    }
                    case V_RIM: {
                        float n = rnd.nextFloat() * 2f - 1f;
                        s = (0.6 * n + Math.sin(2 * Math.PI * 1750 * t)) * Math.exp(-t / v.decay);
                        break;
                    }
                    case V_WHISTLE: {
                        double env;
                        double f = v.f;
                        if (t < 0.13) env = Math.min(1.0, t / 0.01) * Math.min(1.0, (0.13 - t) / 0.02);
                        else if (t > 0.19 && t < 0.6) {
                            env = Math.min(1.0, (t - 0.19) / 0.015) * Math.min(1.0, (0.6 - t) / 0.05);
                            f = v.f + 220 * Math.min(1.0, (t - 0.19) / 0.08);
                        } else env = 0;
                        f += 85 * Math.sin(2 * Math.PI * 27 * t);
                        v.phase += f * dt;
                        float n = rnd.nextFloat() * 2f - 1f;
                        s = env * (Math.sin(2 * Math.PI * v.phase) * 0.85 + n * 0.12);
                        break;
                    }
                    case V_POP: {
                        double f = v.f * Math.pow(2.8, Math.min(1.0, t / 0.07));
                        v.phase += f * dt;
                        s = Math.sin(2 * Math.PI * v.phase) * Math.exp(-t / 0.05);
                        break;
                    }
                    case V_LIFT: {
                        double f = v.f * Math.pow(2.9, Math.min(1.0, t / 0.18));
                        v.phase += f * dt;
                        s = Math.sin(2 * Math.PI * v.phase) * Math.max(0.0, 1 - t / 0.2);
                        break;
                    }
                    case V_BOING: {
                        double f = v.f + 150 * Math.exp(-t / 0.12) * (1 + 0.35 * Math.sin(2 * Math.PI * 13 * t));
                        v.phase += f * dt;
                        s = Math.sin(2 * Math.PI * v.phase) * Math.exp(-t / 0.22);
                        break;
                    }
                    case V_POOF: {
                        float n = rnd.nextFloat() * 2f - 1f;
                        v.lp += (n - v.lp) * 0.08f;
                        s = v.lp * 3.0 * Math.min(1.0, t / 0.01) * Math.exp(-t / 0.13);
                        break;
                    }
                    default: {
                        double f = v.f * (1 + 0.8 * Math.min(1.0, t / 0.1));
                        v.phase += f * dt;
                        s = Math.sin(2 * Math.PI * v.phase) * Math.exp(-t / 0.07);
                        break;
                    }
                }
                float g = isMusic ? musicGain : 1f;
                mix[i] += (float) (s * v.amp) * g;
                v.t = t + dt;
            }
            if (v.wait <= 0 && v.t > v.decay * 5 + 0.05) v.on = false;
        }
        musicGain += gStep * BLOCK;
        if (Math.abs(targetGain - musicGain) < 0.01f) musicGain = targetGain;
    }
}
