package com.gathin.tvhome;

import java.util.Random;

/**
 * Synthesises the channel menu's background music: an original, gentle lounge loop in the spirit of a
 * console menu (not a copy of any real tune). Electric-piano chords with a bossa push, a soft bass, a
 * music-box melody and a light shaker; 16 bars at 100 BPM, about 38 seconds. Rendered at run time, so
 * the APK carries no audio for it, and wrapped so the end flows straight into the start.
 *
 * Plain Java (no Android types) so a JVM test can render it.
 */
final class MenuMusic {
    private MenuMusic() {}

    static final int RATE = 22050;
    private static final double BEAT = 60.0 / 100.0;
    private static final double BAR = 4 * BEAT;

    /** Chords (MIDI) per bar: an I-vi-ii-V turnaround in F with colour tones, then a variation. */
    private static final int[][] CHORDS = {
        {53, 57, 60, 64}, {50, 57, 60, 65}, {55, 58, 62, 65}, {48, 55, 58, 64},
        {53, 57, 60, 64}, {57, 60, 64, 67}, {58, 62, 65, 69}, {48, 52, 55, 58},
        {50, 53, 57, 60}, {55, 59, 62, 65}, {53, 57, 60, 64}, {58, 62, 65, 69},
        {57, 60, 64, 67}, {50, 53, 57, 60}, {55, 58, 62, 65}, {48, 55, 58, 64},
    };
    private static final int[] ROOTS = {41, 38, 43, 36, 41, 45, 46, 36, 38, 43, 41, 46, 45, 38, 43, 36};

    /** Melody: bar, beat, MIDI note, length in beats. */
    private static final double[][] MELODY = {
        {0, 0, 72, 1.5}, {0, 1.5, 74, 0.5}, {0, 2, 76, 1}, {0, 3, 72, 1},
        {1, 0, 74, 2}, {1, 2.5, 69, 1.5},
        {2, 0, 74, 1}, {2, 1, 77, 1}, {2, 2, 76, 1}, {2, 3, 74, 1},
        {3, 0, 72, 3},
        {4, 0, 72, 1.5}, {4, 1.5, 74, 0.5}, {4, 2, 76, 1}, {4, 3, 79, 1},
        {5, 0, 77, 2}, {5, 2, 76, 2},
        {6, 0, 74, 1}, {6, 1, 72, 1}, {6, 2, 74, 1}, {6, 3, 77, 1},
        {7, 0, 76, 3}, {7, 3.5, 72, 0.5},
        {8, 0, 77, 1.5}, {8, 1.5, 76, 0.5}, {8, 2, 74, 2},
        {9, 0, 71, 1}, {9, 1, 74, 1}, {9, 2, 77, 2},
        {10, 0, 76, 1.5}, {10, 1.5, 74, 0.5}, {10, 2, 72, 2},
        {11, 0, 74, 1}, {11, 1, 77, 1}, {11, 2, 81, 2},
        {12, 0, 79, 1.5}, {12, 1.5, 77, 0.5}, {12, 2, 76, 2},
        {13, 0, 74, 1}, {13, 1, 72, 1}, {13, 2, 69, 2},
        {14, 0, 70, 1}, {14, 1, 74, 1}, {14, 2, 77, 1}, {14, 3, 76, 1},
        {15, 0, 72, 4},
    };

    /** Chord hits and bass notes per bar: beat, length in beats. */
    private static final double[][] COMP = {{0, 0.9}, {1.5, 0.5}, {2.5, 1.0}, {3.5, 0.4}};
    private static final double[][] BASS = {{0, 1.4}, {1.5, 0.4}, {2, 1.4}, {3.5, 0.4}};

    static double hz(double midi) {
        return 440.0 * Math.pow(2, (midi - 69) / 12.0);
    }

    /** Samples in the loop. */
    static int length() {
        return (int) (RATE * BAR * CHORDS.length);
    }

    /** The whole loop as 16-bit mono PCM at RATE. */
    static short[] render() {
        float[] buf = new float[length()];
        Random rng = new Random(11);
        for (int b = 0; b < CHORDS.length; b++) {
            double t0 = b * BAR;
            for (double[] hit : COMP) {
                for (int k = 0; k < 4; k++) {
                    double at = t0 + hit[0] * BEAT + k * 0.008;
                    epiano(buf, hz(CHORDS[b][k]), hit[1] * BEAT + 0.6, k == 0 ? 0.07 : 0.06, at);
                }
            }
            for (int i = 0; i < BASS.length; i++) {
                int note = i % 2 == 0 ? ROOTS[b] : ROOTS[b] + 7;
                bass(buf, hz(note), BASS[i][1] * BEAT + 0.2, 0.22, t0 + BASS[i][0] * BEAT);
            }
            for (int e = 0; e < 8; e++) {
                shaker(buf, 0.12, 0.035 * (e % 2 == 1 ? 1.0 : 0.55), t0 + e * BEAT / 2, rng);
            }
        }
        for (double[] m : MELODY) {
            double at = m[0] * BAR + m[1] * BEAT;
            musicBox(buf, hz(m[2]), m[3] * BEAT + 0.5, 0.16, at);
            musicBox(buf, hz(m[2] + 12), m[3] * BEAT + 0.3, 0.03, at + 0.004);
        }
        // Soft limiter, then normalise with headroom under the interface sounds.
        float peak = 1e-6f;
        for (int i = 0; i < buf.length; i++) {
            buf[i] = (float) Math.tanh(buf[i] * 1.4);
            peak = Math.max(peak, Math.abs(buf[i]));
        }
        short[] pcm = new short[buf.length];
        float k = 0.7f * 32767f / peak;
        for (int i = 0; i < buf.length; i++) pcm[i] = (short) Math.round(buf[i] * k);
        return pcm;
    }

    // Each voice runs its oscillators by recurrence (y[n+1] = 2cos(w)y[n] - y[n-1]) and its envelopes by a
    // per-sample factor, so rendering takes well under a second on a TV CPU. Notes past the end wrap.

    private static void epiano(float[] buf, double f, double seconds, double gain, double at) {
        Osc o1 = new Osc(f), o2 = new Osc(f * 2), o3 = new Osc(f * 3);
        int n = (int) (seconds * RATE), start = (int) (at * RATE);
        double env = 1, bell = 1, envK = Math.exp(-1.0 / (0.9 * RATE)), bellK = Math.exp(-1.0 / (0.15 * RATE));
        double attack = 0.006 * RATE;
        for (int i = 0; i < n; i++) {
            double v = o1.next() + 0.12 * o2.next() + 0.35 * bell * o3.next();
            mix(buf, start + i, v * env * Math.min(1.0, i / attack) * gain);
            env *= envK;
            bell *= bellK;
        }
    }

    private static void musicBox(float[] buf, double f, double seconds, double gain, double at) {
        Osc o1 = new Osc(f), o4 = new Osc(f * 4);
        int n = (int) (seconds * RATE), start = (int) (at * RATE);
        double env = 1, ping = 1, envK = Math.exp(-1.0 / (0.45 * RATE)), pingK = Math.exp(-1.0 / (0.08 * RATE));
        double attack = 0.002 * RATE;
        for (int i = 0; i < n; i++) {
            double v = o1.next() + 0.25 * ping * o4.next();
            mix(buf, start + i, v * env * Math.min(1.0, i / attack) * gain);
            env *= envK;
            ping *= pingK;
        }
    }

    private static void bass(float[] buf, double f, double seconds, double gain, double at) {
        Osc o1 = new Osc(f), o2 = new Osc(f * 2);
        int n = (int) (seconds * RATE), start = (int) (at * RATE);
        double env = 1, envK = Math.exp(-1.0 / (0.5 * RATE)), attack = 0.01 * RATE;
        for (int i = 0; i < n; i++) {
            mix(buf, start + i, (o1.next() + 0.2 * o2.next()) * env * Math.min(1.0, i / attack) * gain);
            env *= envK;
        }
    }

    private static void shaker(float[] buf, double seconds, double gain, double at, Random rng) {
        int n = (int) (seconds * RATE), start = (int) (at * RATE);
        double env = 1, envK = Math.exp(-1.0 / (0.025 * RATE)), attack = 0.003 * RATE, prev = 0;
        for (int i = 0; i < n; i++) {
            double x = rng.nextGaussian();
            mix(buf, start + i, (x - prev) * env * Math.min(1.0, i / attack) * gain);
            prev = x;
            env *= envK;
        }
    }

    private static void mix(float[] buf, int i, double v) {
        buf[i % buf.length] += (float) v;
    }

    /** A sine oscillator by the second-order recurrence; starts at phase 0. */
    private static final class Osc {
        private final double c;
        private double y1, y2;

        Osc(double f) {
            double w = 2 * Math.PI * f / RATE;
            c = 2 * Math.cos(w);
            y1 = 0;
            y2 = -Math.sin(w);
        }

        double next() {
            double y = c * y1 - y2;
            y2 = y1;
            y1 = y;
            return y2;
        }
    }
}
