package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;

/**
 * Base of the animated screensavers (Wii, Aquarium): draws a frame from the time since start, about 30 times a
 * second while running. Geometry is in 1920 x 1080 reference px scaled by s, like the menu. Every frame is a
 * function of time alone, so the scene never sits still long enough to burn in.
 */
abstract class SaverView extends View {
    protected float s = 1f;
    protected int w = 1, h = 1;
    private long startMs;
    private boolean running;

    SaverView(Context context) {
        super(context);
        setBackgroundColor(0xFF000000);
    }

    void start() {
        running = true;
        startMs = SystemClock.uptimeMillis();
        invalidate();
    }

    void stop() {
        running = false;
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        w = Math.max(1, nw);
        h = Math.max(1, nh);
        s = w / 1920f;
        layout(w, h);
    }

    /** The size changed (also the first time): rebuild anything cached. */
    abstract void layout(int w, int h);

    abstract void frame(Canvas c, float t);

    @Override
    protected void onDraw(Canvas c) {
        frame(c, (SystemClock.uptimeMillis() - startMs) / 1000f);
        if (running) postInvalidateDelayed(33);
    }
}
