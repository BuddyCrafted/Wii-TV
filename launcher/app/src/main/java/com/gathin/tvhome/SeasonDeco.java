package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * The seasonal themes' scenery. Winter: pine trees and snow on the hills either side of the clock (the blue
 * line's curves), snow falling in two layers, and sledders riding down the hills and looping round. Fall: leaf
 * piles on the hills and leaves tumbling down, behind the channels and a few in front. The trees and piles are
 * baked into the backdrop; everything that moves is computed from the clock, so nothing is allocated per frame.
 * Geometry in 1920 x 1080 reference px; s converts to screen px.
 */
final class SeasonDeco {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    // ---- baked scenery --------------------------------------------------------------------------

    /** Pine trees with snow on them: small ones on the hills, tall ones in the side margins. */
    static void bakeWinter(Canvas c, float s) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path path = new Path();
        float[][] trees = {{30, 150}, {100, 120}, {520, 64}, {590, 78}, {665, 58}};
        for (float[] t : trees) {
            for (int side = 0; side < 2; side++) {
                float x = side == 0 ? t[0] : 1920f - t[0] + (t[0] < 200 ? 0 : 14f);
                float h = side == 0 ? t[1] : t[1] * 0.9f;
                float base = WiiBase.lineY(x) - 3f;
                // A soft snow mound under each tree.
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFFFFF);
                c.drawOval((x - h * 0.42f) * s, (base - h * 0.08f) * s, (x + h * 0.42f) * s, (base + h * 0.06f) * s, p);
                tree(c, p, path, x, base, h, s);
            }
        }
    }

    private static void tree(Canvas c, Paint p, Path path, float x, float base, float h, float s) {
        float w = h * 0.62f;
        p.setColor(0xFF7A4E2D);
        c.drawRect((x - h * 0.05f) * s, (base - h * 0.16f) * s, (x + h * 0.05f) * s, base * s, p);
        int[] greens = {0xFF2F6B4F, 0xFF367A5A, 0xFF3F8A66};
        for (int k = 0; k < 3; k++) {
            float top = base - h * (0.42f + k * 0.27f) - h * 0.2f, bottom = base - h * (0.12f + k * 0.25f);
            float half = w / 2f * (1f - k * 0.24f);
            path.reset();
            path.moveTo(x * s, top * s);
            path.lineTo((x + half) * s, bottom * s);
            path.lineTo((x - half) * s, bottom * s);
            path.close();
            p.setColor(greens[k]);
            c.drawPath(path, p);
            // Snow along the top of each tier.
            float cap = (bottom - top) * 0.38f;
            path.reset();
            path.moveTo(x * s, top * s);
            path.lineTo((x + half * 0.42f) * s, (top + cap) * s);
            path.lineTo((x + half * 0.14f) * s, (top + cap * 0.8f) * s);
            path.lineTo((x - half * 0.14f) * s, (top + cap) * s);
            path.lineTo((x - half * 0.42f) * s, (top + cap * 0.82f) * s);
            path.close();
            p.setColor(0xF2FFFFFF);
            c.drawPath(path, p);
        }
    }

    /** Fall: little piles of leaves on the hills. */
    static void bakeFall(Canvas c, float s) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        java.util.Random r = new java.util.Random(23);
        float[] piles = {60f, 470f, 640f, 1280f, 1450f, 1860f};
        for (float px : piles) {
            for (int i = 0; i < 9; i++) {
                float x = px + (r.nextFloat() - 0.5f) * 70f;
                float y = WiiBase.lineY(x) - 4f - r.nextFloat() * 9f;
                float len = 9f + r.nextFloat() * 7f;
                int save = c.save();
                c.translate(x * s, y * s);
                c.rotate((r.nextFloat() - 0.5f) * 70f);
                p.setColor(LEAF_COLOURS[i % LEAF_COLOURS.length]);
                c.drawOval(-len * s, -len * 0.45f * s, len * s, len * 0.45f * s, p);
                c.restoreToCount(save);
            }
        }
    }

    // ---- moving parts ---------------------------------------------------------------------------

    /** Falling snow; the back layer goes behind the channels, the front one (bigger, faster, fewer) over them. */
    void snow(Canvas c, float s, float t, boolean front) {
        int n = front ? 22 : 70;
        for (int i = 0; i < n; i++) {
            int k = front ? i + 101 : i;
            float seedX = ((k * 7919) % 1920) / 1920f, seedY = ((k * 104729) % 1080) / 1080f;
            float speed = (front ? 60f : 22f) + (k % 7) * (front ? 12f : 9f);
            float size = (front ? 4f : 2.5f) + (k % 5) * 1.3f;
            float y = (seedY * 1180f + t * speed) % 1180f - 50f;
            float x = seedX * 1920f + (float) Math.sin(t * 0.7f + k) * (front ? 34f : 22f);
            p.setColor(front ? 0x66A9C9E8 : 0x55A9C9E8);
            c.drawCircle(x * s, y * s, (size + 1.2f) * s, p);
            p.setColor(front ? 0xD9FFFFFF : 0xF0FFFFFF);
            c.drawCircle(x * s, y * s, size * s, p);
        }
    }

    /** Two sledders, one down each hill toward the clock, taking turns. */
    void sledders(Canvas c, float s, float t) {
        rider(c, s, t, false, 0xFFE53935, 0xFF1E88E5);
        rider(c, s, t + 4.5f, true, 0xFF43A047, 0xFFFDD835);
    }

    private static final float RUN = 5.5f, GAP = 3.5f;

    private void rider(Canvas c, float s, float t, boolean right, int coat, int hat) {
        float phase = t % (RUN + GAP);
        if (phase > RUN) return;
        float u = phase / RUN;
        // Downhill: slow at the top, fastest on the slope, gliding to a stop on the flat.
        float eased = u * u * (3f - 2f * u);
        float x = 150f + 620f * eased;
        float y = WiiBase.lineY(x) - 3f;
        float slope = (WiiBase.lineY(x + 3f) - WiiBase.lineY(x - 3f)) / 6f;
        float alpha = Math.min(1f, Math.min(u / 0.08f, (1f - u) / 0.18f));
        int a = Math.round(255 * Math.max(0f, alpha));
        if (a <= 0) return;
        float sx = right ? 1920f - x : x;
        int save = c.save();
        c.translate(sx * s, y * s);
        // A third bigger than the reference px, so the riders read across the room.
        c.scale((right ? -s : s) * 1.35f, s * 1.35f);
        c.rotate((float) Math.toDegrees(Math.atan(slope)));
        // Snow spray behind the runners.
        p.setColor(0xFFFFFFFF);
        for (int i = 0; i < 5; i++) {
            float back = -30f - i * 9f, up = -3f - (i % 2) * 4f;
            p.setAlpha(Math.round(a * (0.7f - i * 0.12f) * Math.min(1f, eased * 3f)));
            c.drawCircle(back, up, 3.2f - i * 0.4f, p);
        }
        // Sled: runner curling up at the front, wooden deck.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.6f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFF5D4037);
        p.setAlpha(a);
        path.reset();
        path.moveTo(-28f, 0f);
        path.lineTo(22f, 0f);
        path.quadTo(32f, 0f, 30f, -9f);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFA0522D);
        p.setAlpha(a);
        c.drawRoundRect(-26f, -9f, 22f, -4f, 2f, 2f, p);
        // Rider: legs forward, coat, head, hat with a bobble, scarf streaming back.
        p.setColor(0xFF283593);
        p.setAlpha(a);
        c.drawRoundRect(-4f, -15f, 20f, -8f, 3f, 3f, p);
        p.setColor(coat);
        p.setAlpha(a);
        c.drawRoundRect(-18f, -33f, 2f, -9f, 7f, 7f, p);
        float flutter = 3f * (float) Math.sin(t * 14f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4f);
        p.setColor(hat);
        p.setAlpha(a);
        path.reset();
        path.moveTo(-6f, -33f);
        path.quadTo(-18f, -35f + flutter, -28f, -31f - flutter);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFF2C9A0);
        p.setAlpha(a);
        c.drawCircle(-7f, -41f, 8f, p);
        p.setColor(hat);
        p.setAlpha(a);
        c.drawArc(-15.5f, -51f, 1.5f, -33f, 180f, 180f, true, p);
        p.setColor(0xFFFFFFFF);
        p.setAlpha(a);
        c.drawCircle(-7f, -51f, 3.2f, p);
        c.restoreToCount(save);
        p.setAlpha(255);
    }

    static final int[] LEAF_COLOURS = {0xFFC2410C, 0xFFEA580C, 0xFFB45309, 0xFFDC2626, 0xFFCA8A04};

    /** Leaves tumbling down and swaying; the front layer is a few over the channels. */
    void leaves(Canvas c, float s, float t, boolean front) {
        int n = front ? 6 : 26;
        for (int i = 0; i < n; i++) {
            int k = front ? i + 61 : i;
            float seedX = ((k * 7919) % 1920) / 1920f, seedY = ((k * 104729) % 1200) / 1200f;
            float speed = (front ? 70f : 38f) + (k % 6) * 8f;
            float y = (seedY * 1220f + t * speed) % 1220f - 70f;
            float x = seedX * 1920f + 46f * (float) Math.sin(t * 0.55f + k * 1.3f);
            float len = (front ? 27f : 19f) + (k % 4) * 4f;
            int save = c.save();
            c.translate(x * s, y * s);
            c.rotate(t * (25f + (k % 5) * 12f) * (k % 2 == 0 ? 1 : -1) + k * 47f);
            // Tumbling: the leaf turns edge-on and back as it falls.
            c.scale(s * (0.45f + 0.55f * Math.abs((float) Math.cos(t * 1.4f + k))), s);
            path.reset();
            path.moveTo(-len, 0f);
            path.quadTo(0f, -len * 0.62f, len, 0f);
            path.quadTo(0f, len * 0.62f, -len, 0f);
            path.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(LEAF_COLOURS[k % LEAF_COLOURS.length]);
            c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.3f);
            p.setColor(0x73000000);
            c.drawLine(-len * 1.15f, 0f, len * 0.9f, 0f, p);
            p.setStyle(Paint.Style.FILL);
            c.restoreToCount(save);
        }
    }
}
