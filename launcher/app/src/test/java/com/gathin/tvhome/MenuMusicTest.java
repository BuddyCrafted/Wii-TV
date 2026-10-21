package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MenuMusicTest {

    @Test
    public void loopIsSixteenBarsAndAudible() {
        short[] pcm = MenuMusic.render();
        // 16 bars of 4 beats at 100 BPM.
        assertEquals((int) (MenuMusic.RATE * 16 * 4 * 0.6), pcm.length, MenuMusic.RATE / 100);
        int peak = 0;
        long energy = 0;
        for (short s : pcm) {
            peak = Math.max(peak, Math.abs(s));
            energy += (long) s * s;
        }
        // Normalised to 70% with headroom, and not silent anywhere near on average.
        assertTrue(peak > 20000 && peak < 24000);
        assertTrue(Math.sqrt(energy / (double) pcm.length) > 1000);
    }

    @Test
    public void loopSeamIsSmooth() {
        short[] pcm = MenuMusic.render();
        // The last sample flows into the first: no click larger than ordinary sample-to-sample motion.
        int seam = Math.abs(pcm[0] - pcm[pcm.length - 1]);
        int typical = 0;
        for (int i = 1; i < pcm.length; i++) typical = Math.max(typical, Math.abs(pcm[i] - pcm[i - 1]));
        assertTrue(seam <= typical);
    }

    @Test
    public void midiToHz() {
        assertEquals(440.0, MenuMusic.hz(69), 1e-9);
        assertEquals(261.63, MenuMusic.hz(60), 0.01);
    }
}
