package com.gathin.tvhome;

import android.graphics.Canvas;

/**
 * Draws a Mii standing, the way the Mii Channel's tile does (hair behind, body, then the head), placed so
 * its head sits at a given point: for profile cards and the profile editor.
 */
final class MiiPortrait {
    private final MiiArt art = new MiiArt();
    private final MiiArt.Pose pose = new MiiArt.Pose();

    /**
     * Head centred at (cx, headY), u px per Mii unit; `closed` shuts the eyes (a blink), `tilt` sways the
     * head a few degrees.
     */
    void draw(Canvas c, Mii m, float cx, float headY, float u, boolean closed, float tilt) {
        MiiArt.Pose p = pose.reset();
        MiiArt.place(m, p);
        int save = c.save();
        c.translate(cx - p.headX * u, headY - p.headY * u);
        c.scale(u, u);
        int hs = c.save();
        c.translate(p.headX, p.headY);
        c.rotate(tilt, 0f, 0.6f);
        art.head(c, m, MiiArt.FRONT, true, false);
        c.restoreToCount(hs);
        art.body(c, m, p);
        c.translate(p.headX, p.headY);
        c.rotate(tilt, 0f, 0.6f);
        art.head(c, m, MiiArt.FRONT, false, closed);
        c.restoreToCount(save);
    }
}
