package com.gathin.tvhome;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Plays MenuMusic's loop while the channel menu is in front, on the media stream so the remote's volume
 * keys set it, quietly enough to sit under the interface sounds. The loop is rendered once, off the UI
 * thread, the first time music is wanted; a feeder thread streams it round and round.
 */
final class WiiMusic {
    private static final float VOLUME = 0.28f;
    private static final int CHUNK = 4096;

    private final Object lock = new Object();
    private boolean enabled;
    private boolean wanted;
    private boolean released;
    private Thread feeder;
    private AudioTrack track;

    private final java.io.File cache;

    WiiMusic(android.content.Context context, boolean enabled) {
        this.enabled = enabled;
        // The rendered loop is kept on disk: Android often kills the launcher on a 1 GB stick while another app
        // runs, and reading 1.7 MB back is much cheaper than rendering it again on a 1.7 GHz CPU.
        this.cache = new java.io.File(context.getApplicationContext().getFilesDir(), "menu_music_v1.pcm");
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
            // stop() also wakes a write() that is blocked on a paused track.
            if (track != null) track.stop();
            lock.notifyAll();
        }
    }

    private void update() {
        boolean play = enabled && wanted && !released;
        if (play && feeder == null) {
            feeder = new Thread(this::feed, "wii-music");
            feeder.setPriority(Thread.NORM_PRIORITY - 1);
            feeder.start();
        }
        if (track != null) {
            if (play) track.play();
            else track.pause();
        }
        lock.notifyAll();
    }

    private short[] loadOrRender() {
        int n = MenuMusic.length();
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
        short[] pcm = MenuMusic.render();
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
            t.setVolume(VOLUME);
        } catch (RuntimeException e) {
            return; // no audio output: the menu simply stays quiet
        }
        synchronized (lock) {
            track = t;
            update();
        }
        int pos = 0;
        try {
            while (true) {
                synchronized (lock) {
                    while (!released && !(enabled && wanted)) lock.wait();
                    if (released) break;
                }
                int n = Math.min(CHUNK, pcm.length - pos);
                int wrote = t.write(pcm, pos, n);
                if (wrote < 0) break;
                pos = (pos + wrote) % pcm.length;
            }
        } catch (InterruptedException ignored) {
            // released
        } finally {
            t.release();
        }
    }
}
