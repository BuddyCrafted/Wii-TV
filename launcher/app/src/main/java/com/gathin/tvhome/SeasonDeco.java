package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * The seasonal themes' scenery. Winter: pine trees and snow on the hills either side of the clock (the blue
 * line's curves), snow falling in two layers, sledders riding down the hills and walking back up, and two
 * frozen ponds below the line, a skater on one and two hockey players on the other. Fall: leaf
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
        bakePonds(c, s);
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

    /**
     * Two sledders, one on each hill. Each rides down toward the clock, gets up, walks back up the hill pulling
     * the sled, sits down and goes again; the two are half a cycle apart.
     */
    // ---- Winter's kids take turns: one scene at a time, each walking in from the edge and back off ----------

    private static final float WALK_IN = 4f;
    private static final float SKATE_PERIOD = (float) (2 * Math.PI / 0.5);
    /** Sledding (two full runs each), skating (three figure-eights), hockey, then a snowball fight. */
    private static final float[] SCENES = {2 * WALK_IN + 2 * (5f + 0.7f + 8f + 0.6f), 2 * WALK_IN + 3 * SKATE_PERIOD,
            2 * WALK_IN + 32f, 2 * WALK_IN + 24f};
    private static float SCENES_TOTAL = 0f;
    static {
        for (float d : SCENES) SCENES_TOTAL += d;
    }

    /** Which time round the scenes are on; every third hockey game ends in a fight. */
    private int round;

    void winterKids(Canvas c, float s, float t) {
        round = (int) (t / SCENES_TOTAL);
        float lt = t % SCENES_TOTAL;
        int k = 0;
        while (k < SCENES.length - 1 && lt >= SCENES[k]) lt -= SCENES[k++];
        float d = SCENES[k];
        switch (k) {
            case 0: sledScene(c, s, lt, d); break;
            case 1: skateScene(c, s, lt, d); break;
            case 2: hockeyScene(c, s, lt, d); break;
            default: snowballScene(c, s, lt, d); break;
        }
    }

    /** 0..1 across the walk in, then 1, then 1..0 across the walk out. */
    private static float walk(float lt, float d) {
        if (lt < WALK_IN) return lt / WALK_IN;
        if (lt > d - WALK_IN) return (d - lt) / WALK_IN;
        return 1f;
    }

    private void sledScene(Canvas c, float s, float lt, float d) {
        if (lt < WALK_IN || lt > d - WALK_IN) {
            // Walking in from the screen edge to the top of the hill (and back off), sled in tow.
            float x = -40f + (TOP + 40f) * walk(lt, d);
            boolean towardHill = lt < WALK_IN;
            walkWithSled(c, s, x, false, towardHill, 0xFFE53935, 0xFF1E88E5, lt);
            walkWithSled(c, s, 1920f - x, true, towardHill, 0xFF43A047, 0xFFFDD835, lt);
            return;
        }
        float phase = (lt - WALK_IN) % CYCLE;
        rider(c, s, phase, lt, false, 0xFFE53935, 0xFF1E88E5);
        rider(c, s, phase, lt, true, 0xFF43A047, 0xFFFDD835);
    }

    private void walkWithSled(Canvas c, float s, float x, boolean right, boolean in, int coat, int hat, float t) {
        float y = WiiBase.lineY(Math.max(0f, Math.min(1920f, x))) - 3f;
        int save = c.save();
        c.translate(x * s, y * s);
        // Walking toward the middle when coming in, toward the edge when leaving.
        boolean facingRight = in != right;
        c.scale((facingRight ? s : -s) * BIG, s * BIG);
        sled(c, -46f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.6f);
        p.setColor(0xFF6D4C41);
        c.drawLine(-16f, 0f, 2f, -24f, p);
        p.setStyle(Paint.Style.FILL);
        walker(c, coat, hat, t, true);
        c.restoreToCount(save);
    }

    /** A kid walking along the ground at (x, y), facing `facingRight`; `still` stands instead of stepping. */
    private void kid(Canvas c, float s, float x, float y, boolean facingRight, int coat, int hat, float t, boolean still) {
        int save = c.save();
        c.translate(x * s, y * s);
        c.scale((facingRight ? s : -s) * BIG, s * BIG);
        walker(c, coat, hat, t, !still);
        c.restoreToCount(save);
    }

    private void skateScene(Canvas c, float s, float lt, float d) {
        float startX = POND_L_X - POND_L_RX * 0.68f, y = POND_L_Y + 2f;
        if (lt < WALK_IN || lt > d - WALK_IN) {
            float x = -40f + (startX + 40f) * walk(lt, d);
            kid(c, s, x, y, lt < WALK_IN, 0xFFEC407A, 0xFFFFFFFF, lt, false);
            return;
        }
        skater(c, s, lt - WALK_IN);
    }

    private void hockeyScene(Canvas c, float s, float lt, float d) {
        if (lt < WALK_IN || lt > d - WALK_IN) {
            float f = walk(lt, d);
            float ax = 1960f + (POND_R_X - 55f - 1960f) * f, bx = 1990f + (POND_R_X + 55f - 1990f) * f;
            boolean in = lt < WALK_IN;
            kid(c, s, ax, POND_R_Y, !in, 0xFFE53935, 0xFFE53935, lt, false);
            kid(c, s, bx, POND_R_Y, !in, 0xFF1E88E5, 0xFF1E88E5, lt + 0.4f, false);
            return;
        }
        hockey(c, s, lt - WALK_IN, round % 3 == 2);
    }

    /** Two people on the snow beside the ponds throwing snowballs at each other over the date. */
    private void snowballScene(Canvas c, float s, float lt, float d) {
        float ax0 = 448f, bx0 = 1488f, ground = 1012f;
        if (lt < WALK_IN || lt > d - WALK_IN) {
            float f = walk(lt, d);
            boolean in = lt < WALK_IN;
            float ax = -40f + (ax0 + 40f) * f, bx = 1960f - (1960f - bx0) * f;
            kid(c, s, ax, ground, in, 0xFFFF7043, 0xFF5C6BC0, lt, false);
            kid(c, s, bx, ground, !in, 0xFF26A69A, 0xFFFFCA28, lt, false);
            return;
        }
        float pt = lt - WALK_IN;
        float throwEvery = 3f, flight = 1.3f;
        int n = (int) (pt / throwEvery);
        float since = pt - n * throwEvery;
        boolean fromLeft = n % 2 == 0;
        float ay = ground, by = ground;
        kid(c, s, ax0, ay, true, 0xFFFF7043, 0xFF5C6BC0, pt, true);
        kid(c, s, bx0, by, false, 0xFF26A69A, 0xFFFFCA28, pt, true);
        if (since < flight) {
            // The snowball arcs high over the clock to the other kid.
            float u = since / flight;
            float x0 = fromLeft ? ax0 + 14f : bx0 - 14f, x1 = fromLeft ? bx0 - 6f : ax0 + 6f;
            float x = x0 + (x1 - x0) * u;
            float y = (ay - 66f) + ((by - 66f) - (ay - 66f)) * u - 300f * 4f * u * (1f - u);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(x * s, y * s, 9f * s, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f * s);
            p.setColor(0xFFB0C8DC);
            c.drawCircle(x * s, y * s, 9f * s, p);
            p.setStyle(Paint.Style.FILL);
        } else if (since < flight + 0.45f) {
            // A puff of snow where it lands.
            float u = (since - flight) / 0.45f;
            float hx = fromLeft ? bx0 : ax0, hy = (fromLeft ? by : ay) - 55f;
            p.setColor(0xFFFFFFFF);
            p.setAlpha(Math.round(255 * (1f - u)));
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                c.drawCircle((hx + (float) Math.cos(a) * 18f * u) * s, (hy + (float) Math.sin(a) * 18f * u) * s, (6f - 3f * u) * s, p);
            }
            p.setAlpha(255);
        }
    }

    private static final float SLIDE = 5f, STAND = 0.7f, WALK = 8f, SIT = 0.6f, CYCLE = SLIDE + STAND + WALK + SIT;
    // The run ends where the line flattens, clear of the clock's AM/PM on the right-hand side.
    private static final float TOP = 150f, BOTTOM = 700f;
    /** Riders and skaters are a third bigger than reference px, so they read across the room. */
    private static final float BIG = 1.35f;

    private void rider(Canvas c, float s, float phase, float t, boolean right, int coat, int hat) {
        float x;
        boolean sliding = phase < SLIDE;
        if (sliding) {
            float u = phase / SLIDE;
            x = TOP + (BOTTOM - TOP) * u * u * (3f - 2f * u);   // slow at the top, gliding to a stop on the flat
        } else if (phase < SLIDE + STAND) {
            x = BOTTOM;
        } else if (phase < SLIDE + STAND + WALK) {
            x = BOTTOM - (BOTTOM - TOP) * (phase - SLIDE - STAND) / WALK;
        } else {
            x = TOP;
        }
        float y = WiiBase.lineY(x) - 3f;
        float slope = (WiiBase.lineY(x + 3f) - WiiBase.lineY(x - 3f)) / 6f;
        float sx = right ? 1920f - x : x;
        int save = c.save();
        c.translate(sx * s, y * s);
        c.scale((right ? -s : s) * BIG, s * BIG);
        c.rotate((float) Math.toDegrees(Math.atan(slope)));
        if (sliding) {
            float u = phase / SLIDE;
            spray(c, Math.min(1f, u * 3f) * (1f - u));
            sled(c, 0f);
            sitter(c, coat, hat, t);
        } else {
            boolean walking = phase >= SLIDE + STAND && phase < SLIDE + STAND + WALK;
            // Facing uphill to walk back up; the sled waits downhill on its rope.
            int sv = c.save();
            c.scale(-1f, 1f);
            sled(c, -46f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f);
            p.setColor(0xFF6D4C41);
            c.drawLine(-16f, 0f, 2f, -24f, p);
            p.setStyle(Paint.Style.FILL);
            walker(c, coat, hat, walking ? t : 0f, walking);
            c.restoreToCount(sv);
        }
        c.restoreToCount(save);
    }

    private void spray(Canvas c, float amount) {
        p.setColor(0xFFFFFFFF);
        for (int i = 0; i < 5; i++) {
            p.setAlpha(Math.round(255 * (0.7f - i * 0.12f) * amount));
            c.drawCircle(-30f - i * 9f, -3f - (i % 2) * 4f, 3.2f - i * 0.4f, p);
        }
        p.setAlpha(255);
    }

    /** The sled, its runner curling up at the front; `dx` moves it along the ground. */
    private void sled(Canvas c, float dx) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.6f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFF5D4037);
        path.reset();
        path.moveTo(dx - 28f, 0f);
        path.lineTo(dx + 22f, 0f);
        path.quadTo(dx + 32f, 0f, dx + 30f, -9f);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFA0522D);
        c.drawRoundRect(dx - 26f, -9f, dx + 22f, -4f, 2f, 2f, p);
    }

    /** Sitting on the sled: legs forward, coat, head, hat with a bobble, scarf streaming back. */
    private void sitter(Canvas c, int coat, int hat, float t) {
        p.setColor(0xFF283593);
        c.drawRoundRect(-4f, -15f, 20f, -8f, 3f, 3f, p);
        p.setColor(coat);
        c.drawRoundRect(-18f, -33f, 2f, -9f, 7f, 7f, p);
        scarf(c, hat, -6f, -33f, t);
        head(c, hat, -7f, -41f);
    }

    /** Standing and walking: legs stepping, coat, an arm forward. */
    private void walker(Canvas c, int coat, int hat, float t, boolean walking) {
        float step = walking ? (float) Math.sin(t * 6f) : 0f;
        float bob = walking ? Math.abs(step) * 1.5f : 0f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(5f);
        p.setColor(0xFF283593);
        c.drawLine(0f, -18f - bob, 6f * step, 0f, p);
        c.drawLine(0f, -18f - bob, -6f * step, 0f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(coat);
        c.drawRoundRect(-8f, -42f - bob, 8f, -16f - bob, 7f, 7f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4f);
        c.drawLine(4f, -36f - bob, 2f, -24f - bob, p);
        p.setStyle(Paint.Style.FILL);
        scarf(c, hat, 0f, -41f - bob, t);
        head(c, hat, 0f, -50f - bob);
    }

    private void scarf(Canvas c, int colour, float x, float y, float t) {
        float flutter = 3f * (float) Math.sin(t * 14f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4f);
        p.setColor(colour);
        path.reset();
        path.moveTo(x, y);
        path.quadTo(x - 12f, y - 2f + flutter, x - 22f, y + 2f - flutter);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void head(Canvas c, int hat, float x, float y) {
        p.setColor(0xFFF2C9A0);
        c.drawCircle(x, y, 8f, p);
        p.setColor(hat);
        c.drawArc(x - 8.5f, y - 10f, x + 8.5f, y + 8f, 180f, 180f, true, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(x, y - 10f, 3.2f, p);
    }

    // ---- the frozen ponds: a skater on the left, two hockey players on the right ------------------

    static final float POND_L_X = 585f, POND_L_Y = 990f, POND_L_RX = 115f, POND_L_RY = 38f;
    static final float POND_R_X = 1335f, POND_R_Y = 992f, POND_R_RX = 125f, POND_R_RY = 40f;

    /** The two ponds and the hockey nets, baked into the backdrop. */
    static void bakePonds(Canvas c, float s) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (float[] pond : new float[][] {{POND_L_X, POND_L_Y, POND_L_RX, POND_L_RY}, {POND_R_X, POND_R_Y, POND_R_RX, POND_R_RY}}) {
            float x = pond[0], y = pond[1], rx = pond[2], ry = pond[3];
            p.setColor(0xFFFFFFFF);
            c.drawOval((x - rx - 8f) * s, (y - ry - 6f) * s, (x + rx + 8f) * s, (y + ry + 6f) * s, p);
            p.setColor(0xFFC9E7F7);
            c.drawOval((x - rx) * s, (y - ry) * s, (x + rx) * s, (y + ry) * s, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f * s);
            p.setColor(0xFF9CCBE6);
            c.drawOval((x - rx) * s, (y - ry) * s, (x + rx) * s, (y + ry) * s, p);
            p.setStrokeWidth(2.5f * s);
            p.setColor(0xCCFFFFFF);
            c.drawLine((x - rx * 0.5f) * s, (y - ry * 0.35f) * s, (x - rx * 0.1f) * s, (y - ry * 0.45f) * s, p);
            c.drawLine((x + rx * 0.15f) * s, (y + ry * 0.3f) * s, (x + rx * 0.55f) * s, (y + ry * 0.2f) * s, p);
            p.setStyle(Paint.Style.FILL);
        }
        // Hockey nets at the right pond's ends.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f * s);
        p.setColor(0xFFE53935);
        for (int side = -1; side <= 1; side += 2) {
            float nx = POND_R_X + side * (POND_R_RX - 16f);
            c.drawRect((nx - 7f) * s, (POND_R_Y - 16f) * s, (nx + 7f) * s, (POND_R_Y + 6f) * s, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    /** A skater gliding figure-eights on the left pond, arms out, ponytail swinging. */
    void skater(Canvas c, float s, float t) {
        float w = 0.5f;
        double a = t * w - Math.PI / 2;   // starts at the pond's left end
        float x = POND_L_X + POND_L_RX * 0.68f * (float) Math.sin(a);
        float y = POND_L_Y + 2f + POND_L_RY * 0.45f * (float) Math.sin(2 * a);
        boolean right = Math.cos(a) > 0;
        int save = c.save();
        c.translate(x * s, y * s);
        c.scale((right ? s : -s) * BIG, s * BIG);
        c.rotate(-8f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        // Skates and legs, one leg trailing.
        p.setStrokeWidth(2f);
        p.setColor(0xFF37474F);
        c.drawLine(-4f, 0f, 6f, 0f, p);
        c.drawLine(-22f, -6f, -12f, -5f, p);
        p.setStrokeWidth(4.5f);
        p.setColor(0xFFF8BBD0);
        c.drawLine(0f, -16f, 1f, -1f, p);
        c.drawLine(-2f, -16f, -17f, -6f, p);
        // Dress.
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFEC407A);
        path.reset();
        path.moveTo(-6f, -38f);
        path.lineTo(5f, -38f);
        path.lineTo(10f, -16f);
        path.lineTo(-12f, -16f);
        path.close();
        c.drawPath(path, p);
        // Arms out.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3.5f);
        p.setColor(0xFFF2C9A0);
        c.drawLine(-1f, -34f, 16f, -40f, p);
        c.drawLine(-1f, -34f, -18f, -30f, p);
        // Head, hair and a swinging ponytail.
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFF2C9A0);
        c.drawCircle(0f, -46f, 7.5f, p);
        p.setColor(0xFF6D4C41);
        c.drawArc(-8f, -54.5f, 8f, -40f, 180f, 180f, true, p);
        float swing = 4f * (float) Math.sin(t * 5f);
        c.drawOval(-16f, -52f + swing, -6f, -44f + swing, p);
        c.restoreToCount(save);
        p.setStyle(Paint.Style.FILL);
    }

    // The fight: they square up, drop their sticks, trade punches, then pick the sticks up and play on.
    private static final float FIGHT_FROM = 14f, STICKS_DOWN = 15.5f, STICKS_UP = 25.5f, FIGHT_TO = 27f;

    /** Two hockey players on the right pond passing the puck back and forth; `fight` is the easter egg. */
    void hockey(Canvas c, float s, float t, boolean fight) {
        float ax = playerX(t, false), ay = playerY(t, false), bx = playerX(t, true), by = playerY(t, true);
        // The puck sits still while they fight, then play carries on from where it stopped.
        float pt = !fight || t < FIGHT_FROM ? t : t > FIGHT_TO ? t - (FIGHT_TO - FIGHT_FROM) : FIGHT_FROM;
        float pax = playerX(pt, false), pay = playerY(pt, false), pbx = playerX(pt, true), pby = playerY(pt, true);
        // The puck travels A -> B -> A, slowing as it reaches each stick.
        float ph = (pt % 3.2f) / 1.6f;
        float u = ph < 1f ? ph : 2f - ph;
        u = u * u * (3f - 2f * u);
        float fromX = pax + 16f, toX = pbx - 16f;
        float px = fromX + (toX - fromX) * u, py = pay + (pby - pay) * u + 2f;
        float f = 0f, punchA = 0f, punchB = 0f;
        boolean sticks = true;
        if (fight && t > FIGHT_FROM && t < FIGHT_TO) {
            f = t < FIGHT_FROM + 1.5f ? (t - FIGHT_FROM) / 1.5f : t > FIGHT_TO - 1.5f ? (FIGHT_TO - t) / 1.5f : 1f;
            f = f * f * (3f - 2f * f);
            sticks = t < STICKS_DOWN || t > STICKS_UP;
            float midY = POND_R_Y + 2f;
            // Square up nose to nose, shoving back and forth a little as the punches land.
            float shove = t > STICKS_DOWN + 0.4f && t < STICKS_UP - 0.4f ? 7f * (float) Math.sin(t * 1.7f) : 0f;
            ax += (POND_R_X - 21f + shove - ax) * f;
            bx += (POND_R_X + 21f + shove - bx) * f;
            ay += (midY - ay) * f;
            by += (midY - by) * f;
            if (!sticks) {
                // Sticks lie on the ice behind each of them.
                dropStick(c, s, POND_R_X - 70f, POND_R_Y + 8f, 0.25f);
                dropStick(c, s, POND_R_X + 62f, POND_R_Y + 12f, -0.2f);
                if (t > STICKS_DOWN + 0.6f && t < STICKS_UP - 0.6f) {
                    punchA = Math.max(0f, (float) Math.sin(t * 8f));
                    punchB = Math.max(0f, (float) Math.sin(t * 8f + Math.PI));
                }
            }
        }
        player(c, s, ax, ay, true, 0xFFE53935, t, sticks, punchA, f);
        player(c, s, bx, by, false, 0xFF1E88E5, t + 1f, sticks, punchB, f);
        p.setColor(0xFF212121);
        c.drawOval((px - 4.5f) * s, (py - 2f) * s, (px + 4.5f) * s, (py + 2f) * s, p);
        // A little burst where a punch lands.
        float hit = Math.max(punchA, punchB);
        if (hit > 0.85f) {
            float hx = (ax + bx) / 2f + (punchA > punchB ? 10f : -10f), hy = (ay + by) / 2f - 66f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(2.4f * s);
            p.setColor(0xFFFFEB3B);
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + t * 3;
                float r0 = 5f, r1 = 5f + 9f * (hit - 0.85f) / 0.15f;
                c.drawLine((hx + r0 * (float) Math.cos(a)) * s, (hy + r0 * (float) Math.sin(a)) * s,
                        (hx + r1 * (float) Math.cos(a)) * s, (hy + r1 * (float) Math.sin(a)) * s, p);
            }
            p.setStyle(Paint.Style.FILL);
        }
    }

    private static float playerX(float t, boolean b) {
        return POND_R_X + (b ? 55f : -55f) + 18f * (float) Math.sin(t * 0.8f);
    }

    private static float playerY(float t, boolean b) {
        return POND_R_Y + 6f * (float) Math.sin(t * (b ? 1.3f : 1.1f));
    }

    private void dropStick(Canvas c, float s, float x, float y, float tilt) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(2.4f * s * BIG);
        p.setColor(0xFF8D6E63);
        float dx = 22f * BIG, dy = 22f * BIG * tilt;
        c.drawLine((x - dx) * s, (y - dy) * s, (x + dx) * s, (y + dy) * s, p);
        c.drawLine((x + dx) * s, (y + dy) * s, (x + dx + 7f) * s, (y + dy - 3f) * s, p);
        p.setStyle(Paint.Style.FILL);
    }

    /**
     * One player. Without the stick their fists are up: `punch` 0..1 throws the front fist, and `anger` 0..1
     * flushes the face red.
     */
    private void player(Canvas c, float s, float x, float y, boolean facingRight, int jersey, float t,
                        boolean stick, float punch, float anger) {
        int save = c.save();
        c.translate(x * s, y * s);
        c.scale((facingRight ? s : -s) * BIG, s * BIG);
        float lean = 3f * (float) Math.sin(t * 2f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(2f);
        p.setColor(0xFF37474F);
        c.drawLine(-8f, 0f, 2f, 0f, p);
        c.drawLine(2f, 0f, 12f, 0f, p);
        p.setStrokeWidth(5f);
        p.setColor(0xFF263238);
        c.drawLine(-2f, -16f, -4f, -1f, p);
        c.drawLine(3f, -16f, 7f, -1f, p);
        if (stick) {
            // Stick from the hands down to the ice in front.
            p.setStrokeWidth(2.4f);
            p.setColor(0xFF8D6E63);
            c.drawLine(4f + lean, -30f, 16f, -2f, p);
            c.drawLine(16f, -2f, 22f, -2f, p);
        } else {
            lean = 2f * punch;
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(jersey);
        c.drawRoundRect(-8f + lean * 0.3f, -40f, 9f + lean * 0.3f, -15f, 6f, 6f, p);
        if (!stick) {
            // Guard fist held up by the chin, the other one jabbing forward.
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4.5f);
            p.setColor(jersey);
            c.drawLine(2f, -35f, 9f, -42f, p);
            float fx = 10f + 14f * punch, fy = -40f - 4f * punch;
            c.drawLine(4f, -34f, fx, fy, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFF2C9A0);
            c.drawCircle(10f, -43f, 3.4f, p);
            c.drawCircle(fx + 1f, fy, 3.6f, p);
        }
        int skin = 0xFFF2C9A0;
        if (anger > 0f) {
            int g = (int) (0xC9 - 0x50 * anger), b = (int) (0xA0 - 0x50 * anger);
            skin = 0xFF000000 | 0xF2 << 16 | g << 8 | b;
        }
        p.setColor(skin);
        c.drawCircle(1f + lean * 0.4f, -47f, 7.5f, p);
        p.setColor(jersey);
        c.drawArc(-7f + lean * 0.4f, -56f, 9f + lean * 0.4f, -40f, 180f, 180f, true, p);
        if (anger > 0.5f) {
            // An angry brow.
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f);
            p.setColor(0xFF3E2723);
            c.drawLine(2f + lean * 0.4f, -50f, 7f + lean * 0.4f, -48f, p);
            p.setStyle(Paint.Style.FILL);
        }
        c.restoreToCount(save);
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
