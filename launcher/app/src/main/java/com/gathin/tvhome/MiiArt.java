package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.HashMap;

/**
 * Draws Miis from their part indices, all in code. Coordinates are head units: the head is one unit wide, x
 * runs right and y down from the middle of the eye line. Bodies stand on y = 0 in the same units. Callers
 * scale the canvas; every stroke width here is in units too.
 *
 * Not thread-safe: each thread that draws needs its own instance (the paints and path caches are fields).
 */
final class MiiArt {
    static final int FRONT = 0, LEFT = 1, RIGHT = 2, BACK = 3;

    static final float SKULL_TOP = -0.71f;
    private static final float[] CHIN = {0.63f, 0.56f, 0.70f, 0.585f, 0.685f, 0.57f, 0.68f, 0.68f};
    /** Widths of the stacked strokes that lighten a face's rim, widest first. */
    private static final float[] RIM = {0.11f, 0.075f, 0.04f};
    private static final float MOUTH_SCALE = 1.4f;
    private static final int EYE_INK = 0xFF161414, NOSE_INK = 0xFF2E2A28;

    // ---- hair ----------------------------------------------------------------------------------------
    static final int F_BALD = 0, F_CROP = 1, F_STRAIGHT = 2, F_SIDE = 3, F_MIDDLE = 4, F_SPIKY = 5, F_RECEDE = 6,
            F_BUZZ = 7, F_BOWL = 8, F_QUIFF = 9, F_SIDES = 10, F_UP = 11, F_COMB = 12, F_WAVY = 13;
    static final int S_SHORT = 0, S_EAR = 1, S_CHIN = 2, S_SHOULDER = 3, S_LONG = 4;
    static final int X_NONE = 0, X_PONY = 1, X_PIGTAILS = 2, X_BUN = 3, X_BUNS = 4, X_AFRO = 5, X_CURLY = 6,
            X_BRAIDS = 7, X_MOHAWK = 8;
    private static final float[] SIDE_BOTTOM = {-0.05f, 0.18f, 0.52f, 0.95f, 1.42f};

    /** Hair styles: fringe, side length, volume (hundredths of a unit), extra. */
    static final int[][] HAIR = {
            {F_CROP, S_SHORT, 6, X_NONE}, {F_SPIKY, S_SHORT, 8, X_NONE}, {F_SIDE, S_SHORT, 7, X_NONE},
            {F_COMB, S_SHORT, 6, X_NONE}, {F_BUZZ, S_SHORT, 1, X_NONE}, {F_QUIFF, S_SHORT, 9, X_NONE},
            {F_UP, S_SHORT, 9, X_NONE}, {F_RECEDE, S_SHORT, 5, X_NONE}, {F_SIDES, S_SHORT, 4, X_NONE},
            {F_BALD, S_SHORT, 0, X_NONE}, {F_CROP, S_EAR, 8, X_NONE}, {F_MIDDLE, S_EAR, 8, X_NONE},
            {F_BOWL, S_EAR, 8, X_NONE}, {F_STRAIGHT, S_SHORT, 7, X_NONE}, {F_WAVY, S_EAR, 9, X_NONE},
            {F_CROP, S_SHORT, 6, X_CURLY}, {F_CROP, S_EAR, 26, X_AFRO}, {F_BUZZ, S_SHORT, 1, X_MOHAWK},
            {F_SIDE, S_EAR, 9, X_NONE}, {F_SPIKY, S_EAR, 9, X_NONE},
            {F_STRAIGHT, S_CHIN, 8, X_NONE}, {F_SIDE, S_CHIN, 8, X_NONE}, {F_MIDDLE, S_CHIN, 8, X_NONE},
            {F_STRAIGHT, S_SHOULDER, 8, X_NONE}, {F_SIDE, S_SHOULDER, 8, X_NONE}, {F_MIDDLE, S_SHOULDER, 8, X_NONE},
            {F_STRAIGHT, S_LONG, 8, X_NONE}, {F_SIDE, S_LONG, 8, X_NONE}, {F_MIDDLE, S_LONG, 8, X_NONE},
            {F_STRAIGHT, S_SHORT, 8, X_PONY}, {F_SIDE, S_SHORT, 8, X_PONY}, {F_STRAIGHT, S_EAR, 8, X_PIGTAILS},
            {F_MIDDLE, S_EAR, 8, X_PIGTAILS}, {F_CROP, S_SHORT, 8, X_BUN}, {F_STRAIGHT, S_EAR, 8, X_BUNS},
            {F_MIDDLE, S_SHORT, 8, X_BRAIDS}, {F_WAVY, S_SHOULDER, 10, X_CURLY}, {F_WAVY, S_CHIN, 10, X_NONE},
            {F_SPIKY, S_CHIN, 9, X_NONE}, {F_BOWL, S_CHIN, 8, X_NONE}, {F_CROP, S_CHIN, 8, X_NONE},
            {F_RECEDE, S_CHIN, 6, X_NONE}, {F_SIDES, S_EAR, 6, X_NONE}, {F_MIDDLE, S_LONG, 10, X_CURLY},
            {F_SIDE, S_SHORT, 8, X_BUN}, {F_QUIFF, S_EAR, 12, X_NONE}, {F_STRAIGHT, S_LONG, 8, X_PONY},
            {F_CROP, S_SHORT, 16, X_CURLY},
    };

    // ---- eyes: half width, top height, bottom height, outer-corner lift, iris radius, lid, lashes, style ----
    private static final float[][] EYES = {
            {0.085f, 0.070f, 0.062f, 0f, 0.060f, 0f, 0, 0}, {0.080f, 0.076f, 0.068f, 0f, 0.064f, 0f, 0, 0},
            {0.090f, 0.055f, 0.050f, 0.020f, 0.050f, 0f, 0, 0}, {0.090f, 0.060f, 0.055f, -0.025f, 0.052f, 0f, 0, 0},
            {0.085f, 0.070f, 0.060f, 0f, 0.058f, 0f, 1, 0}, {0.080f, 0.080f, 0.070f, 0.015f, 0.062f, 0f, 2, 0},
            {0.095f, 0.045f, 0.040f, 0.030f, 0.042f, 0f, 0, 0}, {0.085f, 0.068f, 0.060f, 0f, 0.058f, 0.35f, 0, 0},
            {0.060f, 0.075f, 0.075f, 0f, 0f, 0f, 0, 1}, {0.042f, 0.045f, 0.045f, 0f, 0f, 0f, 0, 1},
            {0.085f, 0.060f, 0f, 0f, 0f, 0f, 0, 2}, {0.085f, 0f, 0.050f, 0f, 0f, 0f, 0, 3},
            {0.085f, 0f, 0f, 0f, 0f, 0f, 0, 4}, {0.070f, 0.085f, 0.075f, 0f, 0.060f, 0f, 0, 0},
            {0.100f, 0.065f, 0.055f, 0f, 0.055f, 0f, 0, 0}, {0.090f, 0.070f, 0.050f, 0.030f, 0.058f, 0f, 1, 0},
            {0.085f, 0.072f, 0.066f, 0f, 0.034f, 0f, 0, 0}, {0.085f, 0.060f, 0.060f, -0.010f, 0.058f, 0.20f, 1, 0},
            {0.080f, 0.075f, 0.045f, 0.020f, 0.060f, 0f, 0, 0}, {0.090f, 0.050f, 0.070f, -0.010f, 0.058f, 0f, 0, 0},
            {0.070f, 0.062f, 0.062f, 0f, 0.068f, 0f, 0, 0}, {0.095f, 0.058f, 0.052f, 0.040f, 0.050f, 0.15f, 0, 0},
            {0.088f, 0.066f, 0.060f, -0.035f, 0.055f, 0.10f, 0, 0}, {0.082f, 0.078f, 0.070f, 0f, 0.070f, 0f, 2, 0},
            {0.090f, 0.040f, 0.040f, 0f, 0.040f, 0f, 0, 0}, {0.060f, 0.060f, 0.056f, 0f, 0.050f, 0f, 0, 0},
            {0.085f, 0.070f, 0.060f, 0.010f, 0.060f, 0.45f, 1, 0}, {0.080f, 0.066f, 0.064f, 0f, 0.064f, 0f, 0, 5},
            {0.052f, 0.060f, 0.060f, 0f, 0f, 0f, 0, 1}, {0.090f, 0.060f, 0f, 0.020f, 0f, 0f, 1, 2},
            {0.085f, 0.050f, 0.065f, 0.020f, 0.055f, 0f, 0, 0}, {0.092f, 0.072f, 0.064f, 0f, 0.060f, 0f, 0, 6},
    };

    // ---- eyebrows: half length, thickness, arch, inner-end drop, taper, style ----
    private static final float[][] BROWS = {
            {0.11f, 0.040f, 0f, 0.040f, 0.8f, 0}, {0.11f, 0.030f, 0.035f, 0f, 0.6f, 0},
            {0.10f, 0.045f, 0f, 0f, 1.0f, 0}, {0.11f, 0.040f, 0.015f, 0.030f, 0.8f, 0},
            {0.11f, 0.035f, 0.020f, -0.030f, 0.7f, 0}, {0.12f, 0.055f, 0.020f, 0f, 0.9f, 1},
            {0.11f, 0.018f, 0.030f, 0f, 0.6f, 0}, {0.09f, 0.020f, 0.040f, 0f, 0.5f, 0},
            {0.07f, 0.045f, 0f, 0f, 1.0f, 2}, {0.12f, 0.045f, 0.010f, 0.040f, 0.9f, 3},
            {0.11f, 0.030f, -0.020f, 0f, 0.7f, 0}, {0.13f, 0.035f, 0.030f, 0f, 0.4f, 0},
            {0.10f, 0.022f, 0.045f, 0f, 0.5f, 0}, {0.11f, 0.026f, 0.030f, -0.010f, 0.45f, 0},
            {0.10f, 0.028f, 0.025f, 0f, 0.5f, 0}, {0.12f, 0.020f, 0.020f, 0.010f, 0.4f, 0},
            {0.08f, 0.030f, 0.020f, 0f, 0.7f, 0}, {0.11f, 0.060f, 0.010f, 0.020f, 1.0f, 1},
            {0.10f, 0.040f, 0.035f, -0.040f, 0.6f, 0}, {0.12f, 0.032f, 0f, 0.050f, 0.6f, 0},
            {0.05f, 0.035f, 0f, 0f, 1.0f, 2}, {0.11f, 0.035f, 0.050f, 0f, 0.6f, 3},
            {0.12f, 0.050f, 0.020f, -0.010f, 0.5f, 4}, {0.10f, 0.014f, 0.020f, 0f, 1.0f, 0},
    };

    // ---- mouths: half width, curve (corners up), opening, lips, teeth, style ----
    private static final float[][] MOUTHS = {
            {0.10f, 0f, 0f, 0, 0, 0}, {0.10f, 0.025f, 0f, 0, 0, 0}, {0.11f, 0.040f, 0.050f, 0, 1, 1},
            {0.09f, -0.025f, 0f, 0, 0, 0}, {0.08f, 0.020f, 0f, 1, 0, 0}, {0.06f, 0f, 0.050f, 0, 0, 2},
            {0.10f, 0.030f, 0f, 0, 0, 3}, {0.12f, 0.050f, 0.070f, 0, 1, 1}, {0.09f, 0.015f, 0f, 1, 0, 0},
            {0.08f, 0f, 0f, 1, 0, 4}, {0.10f, 0.030f, 0.035f, 0, 1, 1}, {0.11f, 0.035f, 0.060f, 0, 0, 1},
            {0.07f, 0f, 0f, 0, 0, 5}, {0.10f, 0.020f, 0f, 0, 0, 6}, {0.12f, 0f, 0f, 0, 0, 0},
            {0.06f, 0.010f, 0f, 0, 0, 0}, {0.10f, -0.040f, 0.030f, 0, 1, 1}, {0.10f, 0.020f, 0f, 1, 1, 7},
            {0.09f, 0.030f, 0f, 1, 0, 0}, {0.11f, 0f, 0.025f, 0, 1, 8}, {0.08f, -0.010f, 0f, 1, 0, 0},
            {0.10f, 0.045f, 0f, 0, 0, 9}, {0.05f, 0f, 0.035f, 0, 0, 2}, {0.10f, 0.025f, 0f, 1, 0, 10},
    };

    /** A body pose; headX/headY come back as where the eye line's middle goes. */
    static final class Pose {
        int view = FRONT;
        float phase;
        float walk;
        float wave;
        float wavePhase;
        float tilt;
        float dangle;
        boolean closed;
        float headX, headY;

        Pose reset() {
            view = FRONT;
            phase = walk = wave = wavePhase = tilt = dangle = 0f;
            closed = false;
            return this;
        }
    }

    private static final class Hair {
        Path front, mass, back, extra, top, backView;
        float topY, hw;
        int fringe;
        boolean thin;
        float partX = Float.NaN;
        float bottom;
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path path2 = new Path();
    private final RectF rect = new RectF();
    private final Matrix gm = new Matrix();
    private final HashMap<Integer, Shader> cylinders = new HashMap<>();
    private final HashMap<Integer, Shader> balls = new HashMap<>();
    private final Path[] faces = new Path[CHIN.length];
    private final Hair[] hairs = new Hair[HAIR.length * 2];
    private final HashMap<Integer, Path> beards = new HashMap<>();
    private final Matrix probe = new Matrix();
    private final float[] probeV = new float[9];
    private MiiSprites sprites;

    MiiArt() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    // ---- colour helpers --------------------------------------------------------------------------------

    static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 255, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24) & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    /** Lightens toward white for f > 0, darkens toward black for f < 0; keeps the alpha. */
    static int shade(int c, float f) {
        int target = f >= 0 ? (c | 0x00FFFFFF) : (c & 0xFF000000);
        return mix(c, target, Math.abs(f));
    }

    static int alpha(int c, int a) {
        return (c & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
    }

    /** A paint's alpha also scales its shader, so one that last drew a shadow must be reset first. */
    static Paint opaque(Paint p) {
        p.setAlpha(255);
        return p;
    }

    // ---- placement of the parts, from the Mii's offsets ---------------------------------------------------

    static float eyeX(Mii m) {
        return 0.225f + m.v[Mii.EYE_SPACE] * 0.018f;
    }

    static float eyeY(Mii m) {
        return m.v[Mii.EYE_Y] * 0.028f;
    }

    static float mouthY(Mii m) {
        return 0.39f + m.v[Mii.MOUTH_Y] * 0.026f;
    }

    static float noseY(Mii m) {
        return 0.20f + m.v[Mii.NOSE_Y] * 0.026f;
    }

    static float chin(int shape) {
        return CHIN[shape];
    }

    // ---- head ---------------------------------------------------------------------------------------------

    /** The face outline for a shape: a dome on top, a jaw that differs per shape. */
    Path face(int shape) {
        if (faces[shape] != null) return faces[shape];
        Path h = new Path();
        float top = SKULL_TOP, w = shape == 3 ? 0.47f : 0.5f, cy = -0.06f;
        h.moveTo(-0.01f, top);
        h.lineTo(0f, top);
        h.cubicTo(w * 0.6f, top, w, cy + (top - cy) * 0.6f, w, cy);
        float chin = CHIN[shape];
        switch (shape) {
            case 0:
                h.lineTo(0.5f, 0.12f);
                h.cubicTo(0.49f, 0.30f, 0.30f, 0.48f, 0.16f, 0.575f);
                h.quadTo(0.08f, chin, 0f, chin);
                break;
            case 1:
                h.lineTo(0.5f, 0.10f);
                h.cubicTo(0.5f, 0.38f, 0.30f, chin, 0f, chin);
                break;
            case 2:
                h.lineTo(0.5f, 0.10f);
                h.cubicTo(0.5f, 0.38f, 0.33f, 0.60f, 0.14f, 0.655f);
                h.quadTo(0.07f, chin, 0f, chin);
                break;
            case 3:
                h.lineTo(0.47f, 0.05f);
                h.cubicTo(0.47f, 0.20f, 0.545f, 0.26f, 0.545f, 0.40f);
                h.cubicTo(0.545f, 0.53f, 0.47f, chin, 0.30f, chin);
                break;
            case 4:
                h.lineTo(0.5f, 0.34f);
                h.quadTo(0.5f, 0.40f, 0.46f, 0.43f);
                h.lineTo(0.06f, chin - 0.018f);
                h.quadTo(0.03f, chin, 0f, chin);
                break;
            case 5:
                h.lineTo(0.5f, 0.10f);
                h.cubicTo(0.5f, 0.30f, 0.45f, 0.40f, 0.44f, 0.50f);
                h.quadTo(0.44f, chin, 0.37f, chin);
                break;
            case 6:
                h.lineTo(0.5f, 0.30f);
                h.quadTo(0.5f, 0.36f, 0.47f, 0.40f);
                h.lineTo(0.25f, chin - 0.02f);
                h.quadTo(0.23f, chin, 0.18f, chin);
                break;
            default:
                h.lineTo(0.5f, 0.20f);
                h.cubicTo(0.5f, 0.52f, 0.40f, chin, 0.12f, chin);
                break;
        }
        h.lineTo(-0.01f, chin);
        h.close();
        faces[shape] = mirrorUnion(h);
        return faces[shape];
    }

    private static Path mirrorUnion(Path half) {
        Path m = new Path(half);
        Matrix mx = new Matrix();
        mx.setScale(-1f, 1f);
        m.transform(mx);
        Path out = new Path(half);
        out.op(m, Path.Op.UNION);
        return out;
    }

    /** A skin colour as the lit head shows it: a touch greyer than its swatch. */
    static int skinTone(int skin) {
        return mix(skin, 0xFFC4C0CC, 0.16f);
    }

    private static float lum(int c) {
        return (0.299f * ((c >> 16) & 255) + 0.587f * ((c >> 8) & 255) + 0.114f * (c & 255)) / 255f;
    }

    /**
     * One head. back = true draws only the hair that hangs behind the head and body (long hair, a ponytail),
     * which goes before the body; back = false draws the rest.
     */
    void head(Canvas c, Mii m, int view, boolean back, boolean closed) {
        MiiSprites sp = sprites();
        if (sp != null) {
            sp.head(c, m, view, back, closed, pixelsPerUnit(c) > 150f);
            return;
        }
        int[] v = m.v;
        int skin = skinTone(Mii.SKIN_COLORS[v[Mii.SKIN]]);
        int hairC = Mii.HAIR_COLORS[v[Mii.HAIR_COLOR]];
        Hair h = hair(v[Mii.HAIR], v[Mii.HAIR_FLIP] != 0);
        int shape = v[Mii.FACE];
        if (back) {
            if (view != BACK && h.back != null) {
                fill.setShader(null);
                fill.setColor(shade(hairC, -0.22f));
                c.drawPath(h.back, fill);
            }
            return;
        }
        Path face = face(shape);
        if (view == BACK) {
            ear(c, skin, -0.49f, 1f);
            ear(c, skin, 0.49f, 1f);
            opaque(fill).setShader(new RadialGradient(0f, -0.2f, 0.8f, shade(skin, 0.02f), shade(skin, -0.12f),
                    Shader.TileMode.CLAMP));
            c.drawPath(face, fill);
            fill.setShader(null);
            if (h.backView != null) hairFill(c, h, h.backView, hairC, skin);
            if (h.extra != null) hairFill(c, h, h.extra, hairC, skin);
            return;
        }
        float dir = view == LEFT ? -1f : view == RIGHT ? 1f : 0f;
        if (dir == 0f) {
            ear(c, skin, -0.49f, 1f);
            ear(c, skin, 0.49f, 1f);
        }
        skinFill(c, face, skin, dir);

        int save = c.save();
        if (dir != 0f) {
            c.translate(dir * 0.1f, 0f);
            c.scale(0.9f, 1f);
        }
        float ex = eyeX(m), ey = eyeY(m), my = mouthY(m), ny = noseY(m);
        feature(c, v[Mii.FEATURE], skin, ex, ey, my, ny, shape);
        beard(c, v[Mii.BEARD], Mii.HAIR_COLORS[v[Mii.BEARD_COLOR]], shape, my, ny);
        mouth(c, v[Mii.MOUTH], Mii.LIP_COLORS[v[Mii.MOUTH_COLOR]], my,
                MOUTH_SCALE * (1f + v[Mii.MOUTH_SIZE] * 0.09f));
        mustache(c, v[Mii.MUSTACHE], Mii.HAIR_COLORS[v[Mii.BEARD_COLOR]], my - 0.085f + v[Mii.MUSTACHE_Y] * 0.02f,
                1.3f * (1f + v[Mii.MUSTACHE_SIZE] * 0.1f));
        nose(c, v[Mii.NOSE], NOSE_INK, skin, ny, 1f + v[Mii.NOSE_SIZE] * 0.1f);
        eyes(c, v[Mii.EYE], Mii.EYE_COLORS[v[Mii.EYE_COLOR]], skin, ex, ey, 1f + v[Mii.EYE_SIZE] * 0.09f,
                v[Mii.EYE_ROT] * 4f, closed);
        if (v[Mii.MOLE] != 0) {
            fill.setColor(0xFF4A3426);
            c.drawCircle(0.2f + v[Mii.MOLE_X] * 0.035f, 0.3f + v[Mii.MOLE_Y] * 0.035f,
                    0.022f * (1f + v[Mii.MOLE_SIZE] * 0.22f), fill);
        }
        // Brows are part of the face, so a fringe hangs over them as on the original.
        brows(c, v[Mii.BROW], Mii.HAIR_COLORS[v[Mii.BROW_COLOR]], 0.24f + v[Mii.BROW_SPACE] * 0.02f,
                -0.16f + v[Mii.BROW_Y] * 0.026f + ey * 0.5f, 1f + v[Mii.BROW_SIZE] * 0.09f, v[Mii.BROW_ROT] * 5f);
        c.restoreToCount(save);
        if (dir != 0f) ear(c, skin, -dir * 0.41f, 0.8f);

        save = c.save();
        c.translate(dir * 0.03f, 0f);
        if (h.front != null) hairFill(c, h, h.front, hairC, skin);
        if (h.extra != null) hairFill(c, h, h.extra, hairC, skin);
        if (h.top != null) {
            fill.setColor(hairC);
            c.drawPath(h.top, fill);
        }
        c.restoreToCount(save);

        save = c.save();
        if (dir != 0f) {
            c.translate(dir * 0.1f, 0f);
            c.scale(0.9f, 1f);
        }
        glasses(c, v[Mii.GLASSES], Mii.GLASSES_COLORS[v[Mii.GLASSES_COLOR]], ex,
                ey + 0.005f + v[Mii.GLASSES_Y] * 0.026f, 1.4f * (1f + v[Mii.GLASSES_SIZE] * 0.08f));
        c.restoreToCount(save);
    }

    /** The pack's sprite renderer once a MiiPack is loaded, else null (everything is drawn in code). */
    private MiiSprites sprites() {
        if (sprites == null) {
            MiiPack p = MiiPack.peek();
            if (p != null) sprites = new MiiSprites(p);
        }
        return sprites;
    }

    @SuppressWarnings("deprecation")
    private float pixelsPerUnit(Canvas c) {
        c.getMatrix(probe);
        probe.getValues(probeV);
        return (float) Math.hypot(probeV[Matrix.MSCALE_X], probeV[Matrix.MSKEW_Y]);
    }

    /**
     * The face's soft lighting: a bright patch over the nose and eyes from the upper right, and a lighter rim
     * all round the edge. The rim is a few stacked strokes, which works on hardware canvases without a blur.
     */
    private void skinFill(Canvas c, Path face, int skin, float dir) {
        int hi = mix(skin, 0xFFFFFFF4, 0.5f + 0.3f * lum(skin));
        opaque(fill).setShader(new RadialGradient(0.07f + dir * 0.12f, -0.02f, 0.52f,
                new int[] {hi, mix(skin, hi, 0.6f), skin}, new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(face, fill);
        fill.setShader(null);
        int save = c.save();
        c.clipPath(face);
        stroke.setShader(null);
        stroke.setColor(alpha(mix(skin, 0xFFFFFFFF, 0.4f), 0x3C));
        for (float w : RIM) {
            stroke.setStrokeWidth(w);
            c.drawPath(face, stroke);
        }
        c.restoreToCount(save);
    }

    private void ear(Canvas c, int skin, float x, float wScale) {
        fill.setShader(null);
        fill.setColor(shade(skin, -0.04f));
        rect.set(x - 0.085f * wScale, -0.08f, x + 0.085f * wScale, 0.15f);
        c.drawOval(rect, fill);
        stroke.setShader(null);
        stroke.setColor(shade(skin, -0.16f));
        stroke.setStrokeWidth(0.014f);
        float sg = Math.signum(x);
        rect.set(x - 0.04f * wScale, -0.04f, x + 0.04f * wScale, 0.1f);
        c.drawArc(rect, sg > 0 ? -70f : 110f, 140f, false, stroke);
    }

    /** Hair: darker on the left, and on the crown a soft sheen up to the right like the original's. */
    private void hairFill(Canvas c, Hair h, Path p, int hairC, int skin) {
        int base = h.thin ? mix(hairC, skin, 0.38f) : mix(hairC, 0xFFFFFFFF, 0.05f);
        opaque(fill).setShader(new LinearGradient(-h.hw, 0f, h.hw, 0f,
                new int[] {shade(base, -0.18f), base, shade(base, 0.06f)}, new float[] {0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
        c.drawPath(p, fill);
        fill.setShader(null);
        if (h.thin || p != h.front) return;
        int save = c.save();
        c.clipPath(p);
        float sx = 0.15f, sy = h.topY + 0.3f;
        RadialGradient g = new RadialGradient(sx, sy, 0.27f,
                new int[] {mix(base, 0xFFFFFFFF, 0.42f), alpha(mix(base, 0xFFFFFFFF, 0.2f), 0x90), alpha(base, 0)},
                new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        gm.setScale(1f, 0.85f, sx, sy);
        g.setLocalMatrix(gm);
        opaque(fill).setShader(g);
        c.drawRect(sx - 0.3f, sy - 0.3f, sx + 0.3f, sy + 0.3f, fill);
        fill.setShader(null);
        if (!Float.isNaN(h.partX)) {
            stroke.setShader(null);
            stroke.setColor(shade(base, -0.32f));
            stroke.setStrokeWidth(0.014f);
            path2.reset();
            path2.moveTo(h.partX * 0.8f, h.topY + 0.1f);
            path2.quadTo(h.partX, h.topY + 0.3f, h.partX * 1.1f, -0.42f);
            c.drawPath(path2, stroke);
        }
        c.restoreToCount(save);
    }

    // ---- hair geometry ---------------------------------------------------------------------------------------

    private Hair hair(int style, boolean flip) {
        int key = style * 2 + (flip ? 1 : 0);
        if (hairs[key] == null) hairs[key] = buildHair(style, flip);
        return hairs[key];
    }

    private static float hairline(int fr, float x, float fs) {
        float u = x / 0.40f, uf = u * fs;
        switch (fr) {
            case F_CROP:
                return -0.35f - 0.04f * u * u;
            case F_STRAIGHT:
                return -0.2f + 0.015f * u * u;
            case F_SIDE: {
                float t = (uf + 1f) / 2f;
                return -0.46f + 0.30f * (float) Math.pow(Math.max(0f, t), 0.8);
            }
            case F_MIDDLE:
                return -0.5f + 0.36f * (float) Math.pow(Math.abs(u), 1.3);
            case F_SPIKY: {
                float ph = uf * 2.25f + 0.5f;
                float f = ph - (float) Math.floor(ph);
                float tip = 1f - Math.abs(2f * f - 1f);
                return -0.34f + 0.16f * tip + 0.05f * u * u;
            }
            case F_RECEDE:
                return -0.4f - 0.2f * (float) Math.sin(Math.PI * 0.5 * Math.min(1f, Math.abs(u) / 0.9f));
            case F_BUZZ:
                return -0.37f - 0.04f * u * u;
            case F_BOWL:
                return -0.15f;
            case F_QUIFF:
                return -0.42f - 0.05f * u * u;
            case F_SIDES:
                return -3f;
            case F_UP:
                return -0.36f - 0.04f * u * u;
            case F_COMB: {
                float t = (uf + 1f) / 2f;
                return -0.24f - 0.18f * t * t;
            }
            case F_WAVY: {
                float ph = (u + 1f) * 2f;
                float f = ph - (float) Math.floor(ph);
                float d = 2f * f - 1f;
                return -0.3f + 0.09f * (float) Math.sqrt(Math.max(0f, 1f - d * d));
            }
            default:
                return -0.35f;
        }
    }

    /** The hair's crown: half an ellipse, widest at the temples like the original's helmet of hair. */
    private static Path dome(float hw, float top, float bottom) {
        float cy = -0.22f, k = 0.5523f;
        Path p = new Path();
        p.moveTo(-hw, bottom);
        p.lineTo(-hw, cy);
        p.cubicTo(-hw, cy + (top - cy) * k, -hw * k, top, 0f, top);
        p.cubicTo(hw * k, top, hw, cy + (top - cy) * k, hw, cy);
        p.lineTo(hw, bottom);
        p.close();
        return p;
    }

    private static void strip(Path p, float sg, float inner, float outer, float topY, float bottom, float flare) {
        float o = outer + flare;
        p.moveTo(sg * inner, topY);
        p.lineTo(sg * outer, topY);
        p.lineTo(sg * o, bottom - 0.12f);
        p.quadTo(sg * o, bottom, sg * (o + inner) / 2f, bottom);
        p.quadTo(sg * inner, bottom, sg * inner, bottom - 0.12f);
        p.close();
    }

    private static void union(Path into, Path other) {
        into.op(other, Path.Op.UNION);
    }

    private Hair buildHair(int style, boolean flip) {
        int[] st = HAIR[style];
        int fr = st[0], side = st[1], extra = st[3];
        float v = st[2] / 100f;
        Hair h = new Hair();
        h.fringe = fr;
        if (fr == F_BALD) return h;
        float fs = flip ? -1f : 1f;
        float top = SKULL_TOP - v * 0.75f;
        float hw = 0.52f + v * 0.85f;
        float sb = SIDE_BOTTOM[side];
        h.topY = top;
        h.hw = hw;
        h.bottom = Math.max(sb, -0.05f);
        h.thin = fr == F_BUZZ;

        Path mass;
        if (fr == F_SIDES) {
            mass = new Path();
            strip(mass, -1f, 0.36f, hw, -0.34f, Math.max(sb, 0.06f), 0f);
            strip(mass, 1f, 0.36f, hw, -0.34f, Math.max(sb, 0.06f), 0f);
            Path band = new Path();
            band.addRoundRect(new RectF(-hw, -0.36f, hw, Math.max(sb, 0.1f)), 0.2f, 0.2f, Path.Direction.CW);
            h.backView = new Path(band);
            union(h.backView, mass);
        } else if (extra == X_AFRO) {
            mass = new Path();
            float cy = (top + 0.05f) / 2f, ry = (0.05f - top) / 2f, rx = hw + 0.03f;
            mass.addOval(new RectF(-rx, cy - ry, rx, cy + ry), Path.Direction.CW);
            for (int i = 0; i < 22; i++) {
                double a = Math.PI * 2 * i / 22.0;
                Path b = new Path();
                b.addCircle((float) (Math.cos(a) * rx * 0.94), (float) (cy + Math.sin(a) * ry * 0.94), 0.11f,
                        Path.Direction.CW);
                union(mass, b);
            }
        } else {
            mass = dome(hw, top, -0.12f);
        }
        if (fr != F_SIDES) {
            Path sides = new Path();
            if (side >= S_EAR) {
                float flare = side >= S_SHOULDER ? 0.06f : side == S_CHIN ? 0.03f : 0f;
                strip(sides, -1f, 0.38f, hw, -0.3f, sb, flare);
                strip(sides, 1f, 0.38f, hw, -0.3f, sb, flare);
            } else {
                // Short sideburns tapering to a point just past the cheek, in front of the ears.
                for (int s = -1; s <= 1; s += 2) {
                    sides.moveTo(s * 0.44f, -0.24f);
                    sides.lineTo(s * hw, -0.24f);
                    sides.lineTo(s * hw, -0.14f);
                    sides.quadTo(s * (hw - 0.005f), 0f, s * 0.525f, 0.11f);
                    sides.quadTo(s * 0.49f, 0.02f, s * 0.475f, -0.04f);
                    sides.lineTo(s * 0.44f, -0.12f);
                    sides.close();
                }
            }
            union(mass, sides);
        }
        if (extra == X_CURLY) {
            float ry = -0.14f - top;
            for (int i = 0; i <= 14; i++) {
                double a = Math.PI + Math.PI * i / 14.0;
                Path b = new Path();
                b.addCircle((float) (Math.cos(a) * hw * 0.96), (float) (-0.14 + Math.sin(a) * ry * 0.96), 0.075f,
                        Path.Direction.CW);
                union(mass, b);
            }
            if (side >= S_EAR) {
                for (int s = -1; s <= 1; s += 2) {
                    for (float y = -0.2f; y < sb - 0.05f; y += 0.13f) {
                        Path b = new Path();
                        b.addCircle(s * (hw + 0.01f), y, 0.07f, Path.Direction.CW);
                        union(mass, b);
                    }
                }
            }
        }
        if (fr == F_UP) {
            float ry = -0.14f - top;
            Path spikes = new Path();
            for (int i = 0; i < 7; i++) {
                double a0 = Math.PI * (1.08 + i * 0.12), a1 = a0 + Math.PI * 0.12, am = (a0 + a1) / 2;
                spikes.moveTo((float) (Math.cos(a0) * hw), (float) (-0.14 + Math.sin(a0) * ry));
                spikes.lineTo((float) (Math.cos(am) * (hw + 0.17)), (float) (-0.14 + Math.sin(am) * (ry + 0.17)));
                spikes.lineTo((float) (Math.cos(a1) * hw), (float) (-0.14 + Math.sin(a1) * ry));
                spikes.lineTo(0f, -0.3f);
                spikes.close();
            }
            union(mass, spikes);
            h.topY = top - 0.12f;
        }
        if (fr == F_QUIFF) {
            Path q = new Path();
            q.addOval(new RectF(-0.36f + 0.06f * fs, top - 0.09f, 0.42f + 0.06f * fs, top + 0.27f), Path.Direction.CW);
            union(mass, q);
            h.topY = top - 0.09f;
        }
        if (extra == X_BUN) {
            Path b = new Path();
            b.addCircle(0f, top - 0.07f, 0.2f, Path.Direction.CW);
            union(mass, b);
            h.topY = top - 0.27f;
        } else if (extra == X_BUNS) {
            Path b = new Path();
            b.addCircle(-0.42f, top + 0.16f, 0.17f, Path.Direction.CW);
            b.addCircle(0.42f, top + 0.16f, 0.17f, Path.Direction.CW);
            union(mass, b);
        }
        h.mass = mass;
        if (h.backView == null) h.backView = new Path(mass);

        Path mask = new Path();
        mask.moveTo(-2f, -3f);
        mask.lineTo(2f, -3f);
        mask.lineTo(2f, 3f);
        mask.lineTo(0.40f, 3f);
        int n = 72;
        for (int i = 0; i <= n; i++) {
            float x = 0.40f - 0.80f * i / n;
            mask.lineTo(x, hairline(fr, x, fs));
        }
        mask.lineTo(-0.40f, 3f);
        mask.lineTo(-2f, 3f);
        mask.close();
        Path front = new Path(mass);
        front.op(mask, Path.Op.INTERSECT);
        h.front = front;
        if (fr == F_SIDE || fr == F_COMB) h.partX = -0.22f * fs;
        else if (fr == F_MIDDLE) h.partX = 0f;

        if (side >= S_CHIN) {
            float bw = hw + 0.03f + (side >= S_SHOULDER ? 0.05f : 0f);
            Path back = new Path();
            back.addRoundRect(new RectF(-bw, -0.35f, bw, sb + 0.02f), 0.3f, 0.3f, Path.Direction.CW);
            union(back, dome(hw, top, -0.1f));
            h.back = back;
            union(h.backView, back);
        }
        if (extra == X_PONY) {
            Path tail = new Path();
            tail.addOval(new RectF(0.52f * fs - 0.15f, 0.0f, 0.52f * fs + 0.15f, 0.78f), Path.Direction.CW);
            if (h.back == null) h.back = tail;
            else union(h.back, tail);
            Path hang = new Path();
            hang.addOval(new RectF(-0.15f, -0.25f, 0.15f, 0.8f), Path.Direction.CW);
            union(h.backView, hang);
        }
        if (extra == X_PIGTAILS) {
            Path e = new Path();
            e.addOval(new RectF(-0.84f, 0.04f, -0.54f, 0.68f), Path.Direction.CW);
            e.addOval(new RectF(0.54f, 0.04f, 0.84f, 0.68f), Path.Direction.CW);
            h.extra = e;
        } else if (extra == X_BRAIDS) {
            Path e = new Path();
            for (int s = -1; s <= 1; s += 2) {
                for (int k = 0; k < 6; k++) {
                    float y = 0.12f + k * 0.14f, x = s * (0.47f + (k % 2) * 0.015f);
                    e.addOval(new RectF(x - 0.075f, y - 0.085f, x + 0.075f, y + 0.085f), Path.Direction.CW);
                }
            }
            h.extra = e;
        }
        if (extra == X_MOHAWK) {
            Path t = new Path();
            t.addRoundRect(new RectF(-0.09f, top - 0.26f, 0.09f, -0.38f), 0.08f, 0.08f, Path.Direction.CW);
            h.top = t;
            h.topY = top - 0.26f;
        }
        return h;
    }

    // ---- face parts ---------------------------------------------------------------------------------------

    private void line(Canvas c, Path p, int color, float width) {
        stroke.setShader(null);
        stroke.setColor(color);
        stroke.setStrokeWidth(width);
        c.drawPath(p, stroke);
    }

    void eyes(Canvas c, int type, int color, int skin, float ex, float ey, float size, float rot, boolean closed) {
        float[] e = EYES[type];
        for (int s = -1; s <= 1; s += 2) {
            int save = c.save();
            c.translate(s * ex, ey);
            c.scale(s * size, size);
            c.rotate(-rot);
            eye(c, e, color, skin, closed);
            c.restoreToCount(save);
        }
    }

    private void eye(Canvas c, float[] e, int color, int skin, boolean closed) {
        float w = e[0] * 1.6f, ht = e[1], hb = e[2], sl = e[3], ir = e[4], lid = e[5];
        int lash = (int) e[6], style = (int) e[7];
        int ink = EYE_INK;
        Path p = path;
        if (style == 2) {
            p.reset();
            p.moveTo(-w, 0.015f);
            p.quadTo(0f, -ht * 1.7f, w, 0.015f - sl);
            line(c, p, ink, 0.03f);
            lashes(c, w, sl, ht, lash, ink);
            return;
        }
        if (style == 3) {
            p.reset();
            p.moveTo(-w, -0.01f);
            p.quadTo(0f, hb * 1.8f, w, -0.01f - sl);
            line(c, p, ink, 0.03f);
            return;
        }
        if (style == 4 || closed) {
            p.reset();
            p.moveTo(-w, 0f);
            if (style == 4) p.lineTo(w, -sl);
            else p.quadTo(0f, Math.max(0.02f, hb) * 0.9f, w, -sl);
            line(c, p, ink, 0.028f);
            if (style != 4) lashes(c, w, sl, ht, lash, ink);
            return;
        }
        int iris = color == Mii.EYE_COLORS[0] ? 0xFF141414 : color;
        if (style == 1) {
            float dw = e[0] * 1.3f, dh = ht * 1.3f;
            fill.setColor(iris);
            rect.set(-dw, -dh, dw, dh);
            c.drawOval(rect, fill);
            fill.setColor(0xFFFFFFFF);
            c.drawCircle(-dw * 0.3f, -dh * 0.38f, Math.min(dw, dh) * 0.3f, fill);
            return;
        }
        // A heavy upper lid, a big dark iris hanging from it and a little white either side of the iris.
        float cy = -ht * 1.33f;
        p.reset();
        p.moveTo(-w, 0f);
        p.cubicTo(-w * 0.6f, cy, w * 0.6f, cy - sl, w, -sl);
        p.cubicTo(w * 0.55f, hb * 0.9f - sl * 0.5f, -w * 0.55f, hb * 0.9f, -w, 0f);
        p.close();
        fill.setColor(0xFFFFFFFF);
        c.drawPath(p, fill);
        int save = c.save();
        path2.reset();
        path2.moveTo(-w, 0f);
        path2.cubicTo(-w * 0.6f, cy, w * 0.6f, cy - sl, w, -sl);
        path2.lineTo(w, 1f);
        path2.lineTo(-w, 1f);
        path2.close();
        c.clipPath(path2);
        float irx = ir * 1.13f, iry = ir * 1.33f, icx = -w * 0.03f, icy = -sl * 0.5f;
        fill.setColor(iris);
        rect.set(icx - irx, icy - iry, icx + irx, icy + iry);
        c.drawOval(rect, fill);
        if (iris != 0xFF141414) {
            fill.setColor(0xFF101010);
            rect.set(icx - irx * 0.5f, icy - iry * 0.5f, icx + irx * 0.5f, icy + iry * 0.5f);
            c.drawOval(rect, fill);
        }
        if (style == 5) {
            fill.setColor(0xFFFFFFFF);
            c.drawCircle(icx - irx * 0.32f, icy - iry * 0.3f, irx * 0.3f, fill);
            c.drawCircle(icx + irx * 0.34f, icy + iry * 0.3f, irx * 0.12f, fill);
            c.drawCircle(icx + irx * 0.2f, icy - iry * 0.5f, irx * 0.16f, fill);
        }
        if (lid > 0f) {
            float ly = cy * 0.75f + lid * (ht + hb) * 1.1f;
            fill.setColor(shade(skin, -0.04f));
            c.drawRect(-w * 1.3f, -ht * 3f, w * 1.3f, ly, fill);
            path2.reset();
            path2.moveTo(-w * 1.05f, ly);
            path2.quadTo(0f, ly - ht * 0.15f, w * 1.05f, ly - sl * 0.5f);
            line(c, path2, ink, 0.02f);
        }
        c.restoreToCount(save);
        // The lid is a crescent, thickest over the iris, stroked as well so its ends come out round.
        path2.reset();
        path2.moveTo(-w, 0f);
        path2.cubicTo(-w * 0.6f, cy - 0.032f, w * 0.6f, cy - 0.032f - sl, w, -sl);
        path2.cubicTo(w * 0.6f, cy - sl, -w * 0.6f, cy, -w, 0f);
        path2.close();
        fill.setColor(ink);
        c.drawPath(path2, fill);
        p.reset();
        p.moveTo(-w, 0f);
        p.cubicTo(-w * 0.6f, cy, w * 0.6f, cy - sl, w, -sl);
        line(c, p, ink, 0.017f);
        if (style == 6) {
            p.reset();
            p.moveTo(-w * 0.6f, hb * 1.7f);
            p.quadTo(0f, hb * 2.4f, w * 0.6f, hb * 1.7f - sl * 0.5f);
            line(c, p, shade(skin, -0.28f), 0.012f);
        }
        lashes(c, w, sl, ht, lash, ink);
    }

    private void lashes(Canvas c, float w, float sl, float ht, int lash, int ink) {
        if (lash <= 0) return;
        path2.reset();
        path2.moveTo(w * 0.92f, -sl - 0.004f);
        path2.lineTo(w + 0.04f, -sl - 0.035f);
        if (lash > 1) {
            path2.moveTo(w * 0.66f, -sl * 0.8f - ht * 0.95f);
            path2.lineTo(w * 0.86f, -sl - ht * 1.35f);
        }
        line(c, path2, ink, 0.018f);
    }

    void brows(Canvas c, int type, int color, float bx, float by, float size, float rot) {
        float[] b = BROWS[type];
        for (int s = -1; s <= 1; s += 2) {
            int save = c.save();
            c.translate(s * bx, by);
            c.scale(s * size, size);
            c.rotate(-rot);
            brow(c, b, color);
            c.restoreToCount(save);
        }
    }

    private static float browY(float[] b, float t) {
        float tilt = b[3], arch = b[2];
        if ((int) b[5] == 3) {
            float peak = -arch - 0.015f;
            return t < 0.65f ? tilt + (peak - tilt) * (t / 0.65f) : peak + (0.012f - peak) * ((t - 0.65f) / 0.35f);
        }
        return tilt * (1f - t) - arch * 4f * t * (1f - t);
    }

    private void brow(Canvas c, float[] b, int color) {
        float len = b[0] * 1.5f, th = b[1] * 1.25f, taper = b[4];
        int style = (int) b[5];
        fill.setShader(null);
        fill.setColor(color);
        if (style == 2) {
            rect.set(-len, -th / 2f, len, th / 2f);
            c.drawRoundRect(rect, th / 2f, th / 2f, fill);
            return;
        }
        if (style == 4) {
            stroke.setColor(color);
            stroke.setStrokeWidth(th * 0.32f);
            for (int i = 0; i < 7; i++) {
                float t = i / 6f, x = -len + 2f * len * t, y = browY(b, t);
                float tt = th * (1f - (1f - taper) * t) * 0.55f;
                c.drawLine(x - 0.012f, y + tt, x + 0.018f, y - tt, stroke);
            }
            return;
        }
        int n = 16;
        Path p = path;
        p.reset();
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n, x = -len + 2f * len * t, y = browY(b, t);
            float tt = th * (1f - (1f - taper) * t) * 0.5f;
            float jag = style == 1 && (i % 2 == 1) ? th * 0.3f : 0f;
            if (i == 0) p.moveTo(x, y - tt - jag);
            else p.lineTo(x, y - tt - jag);
        }
        for (int i = n; i >= 0; i--) {
            float t = i / (float) n, x = -len + 2f * len * t, y = browY(b, t);
            float tt = th * (1f - (1f - taper) * t) * 0.5f;
            p.lineTo(x, y + tt);
        }
        p.close();
        c.drawPath(p, fill);
        stroke.setColor(color);
        stroke.setStrokeWidth(Math.min(th, 0.03f) * 0.5f);
        c.drawPath(p, stroke);
    }

    void nose(Canvas c, int type, int ink, int skin, float y, float size) {
        int save = c.save();
        c.translate(0f, y);
        c.scale(size, size);
        Path p = path;
        p.reset();
        float wdt = 0.022f;
        boolean shaded = (skin >>> 24) != 0;
        int light = shaded ? shade(skin, 0.22f) : 0;
        fill.setShader(null);
        switch (type) {
            case 0:
                if (shaded) {
                    // The tip catches the light: a pale bump right of the line and a faint shadow under it.
                    fill.setColor(alpha(shade(skin, -0.06f), 0x50));
                    rect.set(-0.06f, 0.05f, 0.07f, 0.09f);
                    c.drawOval(rect, fill);
                    RadialGradient g = new RadialGradient(0.022f, -0.035f, 0.06f,
                            new int[] {0xE6FFFFFF, alpha(mix(skin, 0xFFFFFFFF, 0.5f), 0x80), alpha(skin, 0)},
                            new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
                    gm.setScale(1f, 1.55f, 0.022f, -0.035f);
                    g.setLocalMatrix(gm);
                    opaque(fill).setShader(g);
                    c.drawRect(-0.05f, -0.14f, 0.1f, 0.07f, fill);
                    fill.setShader(null);
                }
                p.moveTo(0f, -0.135f);
                p.quadTo(-0.012f, -0.136f, -0.018f, -0.125f);
                p.cubicTo(-0.035f, -0.07f, -0.07f, -0.03f, -0.066f, 0.02f);
                p.cubicTo(-0.062f, 0.065f, -0.03f, 0.078f, 0.016f, 0.072f);
                wdt = 0.024f;
                break;
            case 1:
                p.moveTo(-0.05f, 0.03f);
                p.quadTo(0f, 0.085f, 0.05f, 0.03f);
                fill.setColor(ink);
                c.drawCircle(-0.024f, 0.036f, 0.01f, fill);
                c.drawCircle(0.024f, 0.036f, 0.01f, fill);
                break;
            case 2:
                if ((light >>> 24) != 0) {
                    path2.reset();
                    path2.moveTo(0f, -0.08f);
                    path2.lineTo(-0.045f, 0.04f);
                    path2.lineTo(0.045f, 0.04f);
                    path2.close();
                    fill.setColor(light);
                    c.drawPath(path2, fill);
                }
                p.moveTo(0f, -0.08f);
                p.lineTo(-0.045f, 0.04f);
                p.lineTo(0.045f, 0.04f);
                p.close();
                break;
            case 3:
                p.moveTo(0f, -0.12f);
                p.quadTo(0.07f, 0.02f, 0.012f, 0.052f);
                p.quadTo(-0.03f, 0.065f, -0.042f, 0.032f);
                break;
            case 4:
                p.moveTo(-0.03f, 0f);
                p.quadTo(-0.075f, 0.03f, -0.042f, 0.056f);
                p.moveTo(0.03f, 0f);
                p.quadTo(0.075f, 0.03f, 0.042f, 0.056f);
                p.moveTo(-0.02f, 0.062f);
                p.quadTo(0f, 0.072f, 0.02f, 0.062f);
                break;
            case 5:
                fill.setColor(ink);
                rect.set(-0.036f, 0.02f, -0.012f, 0.056f);
                c.drawOval(rect, fill);
                rect.set(0.012f, 0.02f, 0.036f, 0.056f);
                c.drawOval(rect, fill);
                break;
            case 6:
                p.moveTo(0f, -0.14f);
                p.lineTo(0f, 0.03f);
                p.quadTo(0f, 0.06f, 0.035f, 0.05f);
                break;
            case 7:
                p.moveTo(-0.03f, -0.02f);
                p.cubicTo(-0.042f, 0.07f, 0.042f, 0.07f, 0.03f, -0.02f);
                break;
            case 8:
                fill.setColor((light >>> 24) != 0 ? light : 0x22000000);
                c.drawCircle(0f, 0.025f, 0.05f, fill);
                rect.set(-0.05f, -0.025f, 0.05f, 0.075f);
                p.addArc(rect, 20f, 140f);
                break;
            case 9:
                p.moveTo(-0.026f, 0.018f);
                p.lineTo(0f, 0.045f);
                p.lineTo(0.026f, 0.018f);
                break;
            case 10:
                p.moveTo(-0.062f, 0.038f);
                p.quadTo(0f, 0.068f, 0.062f, 0.038f);
                break;
            default:
                p.moveTo(0.02f, -0.08f);
                p.lineTo(-0.042f, 0.04f);
                p.lineTo(0.02f, 0.052f);
                break;
        }
        if (!p.isEmpty()) line(c, p, ink, wdt);
        c.restoreToCount(save);
    }

    void mouth(Canvas c, int type, int lip, float y, float size) {
        float[] m = MOUTHS[type];
        float w = m[0], cu = m[1], op = m[2];
        boolean lips = m[3] > 0f, teeth = m[4] > 0f;
        int style = (int) m[5];
        int ink = 0xFF1E1716;
        int save = c.save();
        c.translate(0f, y);
        c.scale(size, size);
        Path p = path;
        fill.setShader(null);
        switch (style) {
            case 1: {
                p.reset();
                p.moveTo(-w, -cu);
                p.quadTo(0f, cu * 0.6f - 0.012f, w, -cu);
                p.quadTo(0f, 2f * cu + op * 2f, -w, -cu);
                p.close();
                fill.setColor(0xFF6E2026);
                c.drawPath(p, fill);
                int s2 = c.save();
                c.clipPath(p);
                if (teeth) {
                    fill.setColor(0xFFFFFFFF);
                    c.drawRect(-w, -0.3f, w, cu * 0.3f + 0.022f, fill);
                } else {
                    fill.setColor(0xFFE8737A);
                    rect.set(-w * 0.55f, cu + op * 0.4f, w * 0.55f, cu + op * 1.6f);
                    c.drawOval(rect, fill);
                }
                c.restoreToCount(s2);
                line(c, p, lips ? lip : ink, 0.02f);
                break;
            }
            case 2: {
                rect.set(-w * 0.6f, -op, w * 0.6f, op);
                fill.setColor(0xFF6E2026);
                c.drawOval(rect, fill);
                stroke.setColor(lips ? lip : ink);
                stroke.setStrokeWidth(0.02f);
                c.drawOval(rect, stroke);
                break;
            }
            case 3:
                p.reset();
                p.moveTo(-w, 0f);
                p.quadTo(0f, 0.025f, w, -cu * 1.5f);
                line(c, p, ink, 0.022f);
                break;
            case 4: {
                p.reset();
                p.moveTo(-w, 0f);
                p.quadTo(-w * 0.5f, -0.05f, 0f, -0.028f);
                p.quadTo(w * 0.5f, -0.05f, w, 0f);
                p.quadTo(0f, 0.075f, -w, 0f);
                p.close();
                fill.setColor(lip);
                c.drawPath(p, fill);
                path2.reset();
                path2.moveTo(-w * 0.7f, 0f);
                path2.quadTo(0f, 0.012f, w * 0.7f, 0f);
                line(c, path2, shade(lip, -0.4f), 0.016f);
                break;
            }
            case 5:
                p.reset();
                p.moveTo(-w, -0.012f);
                p.quadTo(-w / 2f, 0.04f, 0f, 0f);
                p.quadTo(w / 2f, 0.04f, w, -0.012f);
                line(c, p, ink, 0.022f);
                break;
            case 6: {
                fill.setColor(0xFFE8737A);
                rect.set(w * 0.05f, -0.01f, w * 0.05f + 0.07f, 0.075f);
                c.drawRoundRect(rect, 0.035f, 0.035f, fill);
                stroke.setColor(0xFFB04450);
                stroke.setStrokeWidth(0.012f);
                c.drawRoundRect(rect, 0.035f, 0.035f, stroke);
                p.reset();
                p.moveTo(-w, -cu);
                p.quadTo(0f, 2f * cu, w, -cu);
                line(c, p, ink, 0.022f);
                break;
            }
            case 7: {
                plainMouth(c, w, cu, lips, lip, ink);
                fill.setColor(0xFFFFFFFF);
                rect.set(-0.03f, cu * 0.8f, 0f, cu * 0.8f + 0.04f);
                c.drawRect(rect, fill);
                stroke.setColor(ink);
                stroke.setStrokeWidth(0.01f);
                c.drawRect(rect, stroke);
                rect.offset(0.03f, 0f);
                c.drawRect(rect, fill);
                c.drawRect(rect, stroke);
                break;
            }
            case 8: {
                rect.set(-w, -op, w, op);
                fill.setColor(0xFFFFFFFF);
                c.drawRoundRect(rect, op, op, fill);
                stroke.setColor(ink);
                stroke.setStrokeWidth(0.02f);
                c.drawRoundRect(rect, op, op, stroke);
                stroke.setStrokeWidth(0.01f);
                c.drawLine(-w, 0f, w, 0f, stroke);
                for (int i = -2; i <= 2; i++) c.drawLine(i * w / 3f, -op, i * w / 3f, op, stroke);
                break;
            }
            case 9:
                p.reset();
                p.moveTo(-w, -cu);
                p.cubicTo(-w * 0.6f, cu * 2.2f, w * 0.6f, cu * 2.2f, w, -cu);
                line(c, p, ink, 0.022f);
                break;
            case 10:
                if (lips) lipShapes(c, w, 0f, lip);
                p.reset();
                p.moveTo(-w, 0f);
                p.cubicTo(-w / 3f, -0.03f, w / 3f, 0.03f, w, 0f);
                line(c, p, ink, 0.02f);
                break;
            default:
                plainMouth(c, w, cu, lips, lip, ink);
                break;
        }
        c.restoreToCount(save);
    }

    private void plainMouth(Canvas c, float w, float cu, boolean lips, int lip, int ink) {
        if (lips) lipShapes(c, w, cu, lip);
        path.reset();
        path.moveTo(-w, -cu);
        path.quadTo(0f, 2f * cu, w, -cu);
        line(c, path, ink, 0.019f);
    }

    private void lipShapes(Canvas c, float w, float cu, int lip) {
        path2.reset();
        path2.moveTo(-w, -cu);
        path2.quadTo(-w * 0.45f, -0.034f - cu * 0.5f, 0f, -0.024f + cu * 0.3f);
        path2.quadTo(w * 0.45f, -0.034f - cu * 0.5f, w, -cu);
        path2.quadTo(0f, 2f * cu, -w, -cu);
        path2.close();
        fill.setColor(lip);
        c.drawPath(path2, fill);
        path2.reset();
        path2.moveTo(-w, -cu);
        path2.quadTo(0f, 2f * cu, w, -cu);
        path2.quadTo(0f, 2f * cu + 0.06f, -w, -cu);
        path2.close();
        fill.setColor(shade(lip, 0.12f));
        c.drawPath(path2, fill);
    }

    void glasses(Canvas c, int type, int color, float gx, float gy, float size) {
        if (type <= 0) return;
        float lw = 0.11f * size;
        float th = type == 5 ? 0.034f : type == 1 || type == 4 ? 0.016f : 0.022f;
        int lens = type == 6 ? 0xE0202830 : type == 7 ? 0x707890A0 : 0x30FFFFFF;
        for (int s = -1; s <= 1; s += 2) {
            int save = c.save();
            c.translate(s * gx, gy);
            c.scale(s * size, size);
            Path p = path;
            p.reset();
            switch (type) {
                case 1:
                    p.addRoundRect(new RectF(-0.105f, -0.072f, 0.105f, 0.072f), 0.02f, 0.02f, Path.Direction.CW);
                    break;
                case 2:
                    p.addCircle(0f, 0f, 0.09f, Path.Direction.CW);
                    break;
                case 3:
                    p.addOval(new RectF(-0.11f, -0.072f, 0.11f, 0.072f), Path.Direction.CW);
                    break;
                case 4:
                    p.addRoundRect(new RectF(-0.105f, -0.07f, 0.105f, 0.065f), 0.03f, 0.03f, Path.Direction.CW);
                    break;
                case 5:
                    p.addRoundRect(new RectF(-0.112f, -0.085f, 0.112f, 0.08f), 0.025f, 0.025f, Path.Direction.CW);
                    break;
                case 6:
                    p.addRoundRect(new RectF(-0.11f, -0.075f, 0.112f, 0.07f), 0.045f, 0.045f, Path.Direction.CW);
                    break;
                case 7:
                    p.moveTo(-0.1f, -0.07f);
                    p.lineTo(0.112f, -0.07f);
                    p.quadTo(0.125f, 0.08f, 0f, 0.088f);
                    p.quadTo(-0.11f, 0.08f, -0.1f, -0.07f);
                    p.close();
                    break;
                default:
                    p.moveTo(-0.1f, -0.04f);
                    p.quadTo(-0.02f, -0.085f, 0.13f, -0.1f);
                    p.quadTo(0.115f, 0.07f, 0f, 0.07f);
                    p.quadTo(-0.11f, 0.065f, -0.1f, -0.04f);
                    p.close();
                    break;
            }
            fill.setShader(null);
            fill.setColor(lens);
            c.drawPath(p, fill);
            if (type == 4) {
                line(c, p, alpha(color, 0x60), 0.01f);
                path2.reset();
                path2.moveTo(-0.105f, -0.02f);
                path2.quadTo(-0.105f, -0.07f, -0.07f, -0.07f);
                path2.lineTo(0.07f, -0.07f);
                path2.quadTo(0.105f, -0.07f, 0.105f, -0.02f);
                line(c, path2, color, 0.032f);
            } else {
                line(c, p, color, th);
            }
            if (type != 6) {
                stroke.setColor(0x70FFFFFF);
                stroke.setStrokeWidth(0.012f);
                c.drawLine(-0.05f, -0.03f, -0.02f, -0.055f, stroke);
            }
            c.restoreToCount(save);
        }
        path.reset();
        path.moveTo(-gx + lw * 0.95f, gy - 0.02f);
        path.quadTo(0f, gy - 0.05f, gx - lw * 0.95f, gy - 0.02f);
        path.moveTo(gx + lw, gy - 0.025f);
        path.lineTo(Math.max(gx + lw + 0.01f, 0.5f), gy - 0.04f);
        path.moveTo(-gx - lw, gy - 0.025f);
        path.lineTo(Math.min(-gx - lw - 0.01f, -0.5f), gy - 0.04f);
        line(c, path, color, Math.max(0.016f, th * 0.8f));
    }

    void mustache(Canvas c, int type, int color, float y, float size) {
        if (type <= 0) return;
        int save = c.save();
        c.translate(0f, y);
        c.scale(size, size);
        Path p = path;
        p.reset();
        fill.setShader(null);
        fill.setColor(color);
        switch (type) {
            case 1:
                p.moveTo(-0.085f, 0.018f);
                p.quadTo(0f, -0.024f, 0.085f, 0.018f);
                line(c, p, color, 0.018f);
                break;
            case 2:
                p.moveTo(-0.135f, 0.045f);
                p.cubicTo(-0.125f, -0.05f, -0.02f, -0.05f, 0f, -0.028f);
                p.cubicTo(0.02f, -0.05f, 0.125f, -0.05f, 0.135f, 0.045f);
                p.cubicTo(0.06f, 0.032f, 0.02f, 0.012f, 0f, 0.022f);
                p.cubicTo(-0.02f, 0.012f, -0.06f, 0.032f, -0.135f, 0.045f);
                p.close();
                c.drawPath(p, fill);
                break;
            case 3:
                p.moveTo(-0.075f, 0.012f);
                p.quadTo(0f, -0.045f, 0.075f, 0.012f);
                p.quadTo(0f, -0.004f, -0.075f, 0.012f);
                p.close();
                c.drawPath(p, fill);
                path2.reset();
                path2.moveTo(0.065f, 0.008f);
                path2.quadTo(0.135f, 0.02f, 0.13f, -0.03f);
                path2.moveTo(-0.065f, 0.008f);
                path2.quadTo(-0.135f, 0.02f, -0.13f, -0.03f);
                line(c, path2, color, 0.018f);
                break;
            default:
                p.moveTo(0f, -0.022f);
                p.quadTo(-0.06f, -0.045f, -0.105f, 0.022f);
                p.quadTo(-0.05f, 0.012f, 0f, 0.016f);
                p.quadTo(0.05f, 0.012f, 0.105f, 0.022f);
                p.quadTo(0.06f, -0.045f, 0f, -0.022f);
                p.close();
                c.drawPath(p, fill);
                break;
        }
        c.restoreToCount(save);
    }

    void beard(Canvas c, int type, int color, int shape, float mouthY, float noseY) {
        if (type <= 0) return;
        int key = ((type * 8 + shape) * 64 + Math.round(mouthY * 100f)) * 64 + Math.round(noseY * 100f);
        Path region = beards.get(key);
        if (region == null) {
            region = beardPath(type, shape, mouthY, noseY);
            beards.put(key, region);
        }
        fill.setShader(null);
        fill.setColor(type == 4 ? alpha(color, 0x58) : color);
        c.drawPath(region, fill);
        if (type == 4) {
            fill.setColor(alpha(color, 0x90));
            for (int i = 0; i < 26; i++) {
                float x = ((i * 37) % 26) / 26f * 0.7f - 0.35f, y = noseY + 0.1f + ((i * 17) % 13) / 13f * 0.4f;
                if (Math.abs(x) < 0.13f && Math.abs(y - mouthY) < 0.05f) continue;
                c.drawCircle(x, y, 0.008f, fill);
            }
        }
    }

    private Path beardPath(int type, int shape, float mouthY, float noseY) {
        Path face = face(shape);
        Path r = new Path();
        float chin = CHIN[shape];
        switch (type) {
            case 1:
                r.addOval(new RectF(-0.08f, mouthY + 0.035f, 0.08f, chin + 0.02f), Path.Direction.CW);
                r.addRoundRect(new RectF(-0.022f, mouthY + 0.02f, 0.022f, mouthY + 0.08f), 0.02f, 0.02f,
                        Path.Direction.CW);
                r.op(face, Path.Op.INTERSECT);
                break;
            case 2: {
                r.addRect(-1f, -0.02f, 1f, 2f, Path.Direction.CW);
                r.op(face, Path.Op.INTERSECT);
                Path inner = new Path(face);
                Matrix mx = new Matrix();
                mx.setScale(0.84f, 0.86f, 0f, -0.12f);
                inner.transform(mx);
                r.op(inner, Path.Op.DIFFERENCE);
                break;
            }
            default: {
                r.addRect(-1f, mouthY - 0.06f, 1f, 2f, Path.Direction.CW);
                Path cheeks = new Path();
                cheeks.addRect(-1f, noseY + 0.02f, -0.2f, 2f, Path.Direction.CW);
                cheeks.addRect(0.2f, noseY + 0.02f, 1f, 2f, Path.Direction.CW);
                r.op(cheeks, Path.Op.UNION);
                r.op(face, Path.Op.INTERSECT);
                if (type == 3) {
                    Path hole = new Path();
                    hole.addOval(new RectF(-0.18f, mouthY - 0.05f, 0.18f, mouthY + 0.06f), Path.Direction.CW);
                    r.op(hole, Path.Op.DIFFERENCE);
                }
                break;
            }
        }
        return r;
    }

    void feature(Canvas c, int type, int skin, float ex, float ey, float mouthY, float noseY, int shape) {
        if (type <= 0) return;
        fill.setShader(null);
        int ink = shade(skin, -0.3f);
        switch (type) {
            case 1:
            case 2:
            case 8: {
                int col = type == 8 ? 0x70FF5A6E : 0x50FF6F7F;
                fill.setColor(col);
                for (int s = -1; s <= 1; s += 2) {
                    if (type == 8) c.drawCircle(s * 0.28f, ey + 0.17f, 0.07f, fill);
                    else {
                        rect.set(s * 0.27f - 0.085f, ey + 0.14f, s * 0.27f + 0.085f, ey + 0.24f);
                        c.drawOval(rect, fill);
                    }
                }
                if (type == 2) {
                    fill.setColor(0x387A4AB0);
                    for (int s = -1; s <= 1; s += 2) {
                        rect.set(s * ex - 0.1f, ey - 0.12f, s * ex + 0.1f, ey - 0.02f);
                        c.drawOval(rect, fill);
                    }
                }
                break;
            }
            case 3: {
                fill.setColor(alpha(shade(skin, -0.38f), 0xA0));
                float[] pts = {-0.3f, 0.16f, -0.25f, 0.2f, -0.33f, 0.21f, -0.28f, 0.25f, -0.21f, 0.17f};
                for (int s = -1; s <= 1; s += 2) {
                    for (int i = 0; i < pts.length; i += 2) c.drawCircle(s * -pts[i], ey + pts[i + 1], 0.01f, fill);
                }
                break;
            }
            case 4:
            case 10:
                for (int s = -1; s <= 1; s += 2) {
                    path.reset();
                    path.moveTo(s * ex - 0.06f, ey + 0.088f);
                    path.quadTo(s * ex, ey + 0.125f, s * ex + 0.06f, ey + 0.088f);
                    if (type == 10) {
                        path.moveTo(s * ex - 0.05f, ey + 0.12f);
                        path.quadTo(s * ex, ey + 0.15f, s * ex + 0.05f, ey + 0.12f);
                    }
                    line(c, path, ink, 0.011f);
                }
                break;
            case 5:
                path.reset();
                for (int s = -1; s <= 1; s += 2) {
                    path.moveTo(s * 0.1f, noseY + 0.03f);
                    path.quadTo(s * 0.19f, mouthY - 0.03f, s * 0.18f, mouthY + 0.03f);
                }
                line(c, path, ink, 0.012f);
                break;
            case 6:
                path.reset();
                for (int s = -1; s <= 1; s += 2) {
                    float x = s * (ex + 0.11f);
                    path.moveTo(x, ey - 0.02f);
                    path.lineTo(x + s * 0.04f, ey - 0.04f);
                    path.moveTo(x, ey + 0.005f);
                    path.lineTo(x + s * 0.045f, ey + 0.005f);
                    path.moveTo(x, ey + 0.03f);
                    path.lineTo(x + s * 0.04f, ey + 0.05f);
                }
                line(c, path, ink, 0.01f);
                break;
            case 7:
                path.reset();
                path.moveTo(0f, CHIN[shape] - 0.09f);
                path.lineTo(0f, CHIN[shape] - 0.03f);
                line(c, path, ink, 0.012f);
                break;
            case 9: {
                Path region = beards.get(-1 - shape * 64 - Math.round(noseY * 100f));
                if (region == null) {
                    region = beardPath(4, shape, mouthY, noseY);
                    beards.put(-1 - shape * 64 - Math.round(noseY * 100f), region);
                }
                fill.setColor(0x30405068);
                c.drawPath(region, fill);
                break;
            }
            default:
                path.reset();
                for (int s = -1; s <= 1; s += 2) {
                    path.moveTo(s * 0.19f, mouthY - 0.035f);
                    path.quadTo(s * 0.21f, mouthY, s * 0.19f, mouthY + 0.035f);
                }
                line(c, path, ink, 0.011f);
                break;
        }
    }

    // ---- body ---------------------------------------------------------------------------------------------

    /** Lengths at the middle height, in head units: hip to floor, shoulder to hip, eye line to shoulder. */
    static final float LEG = 1.16f, TORSO = 0.90f, NECK = 0.585f, CROWN = 0.82f;
    private static final int SHOES = 0xFF34353B;
    private static final float[] CLOTH_STOPS = {0f, 0.4f, 0.66f, 0.84f, 1f};

    static float heightScale(Mii m) {
        return 0.72f + m.v[Mii.HEIGHT] / 127f * 0.56f;
    }

    static float weightScale(Mii m) {
        return 0.80f + m.v[Mii.WEIGHT] / 127f * 0.50f;
    }

    /** y of the top of the head (hair included, roughly) above the feet. */
    static float figureTop(Mii m) {
        return -((LEG + TORSO) * heightScale(m) + NECK + CROWN);
    }

    /** Fills in pose.headX/headY without drawing, for callers that draw the head from a cache. */
    static void place(Mii m, Pose p) {
        float bob = -p.walk * 0.04f * Math.abs((float) Math.sin(p.phase));
        float shY = -(LEG + TORSO) * heightScale(m) + bob;
        float dir = p.view == RIGHT ? 1f : p.view == LEFT ? -1f : 0f;
        p.headX = dir * 0.03f;
        p.headY = shY - NECK;
    }

    void body(Canvas c, Mii m, Pose p) {
        float hs = heightScale(m), ws = weightScale(m);
        boolean fem = m.female();
        int shirt = m.shirt();
        int pants = m.favorite() ? 0xFFC8322C : 0xFF4A4C55;
        place(m, p);
        float bob = -p.walk * 0.04f * Math.abs((float) Math.sin(p.phase));
        float hipY = -LEG * hs + bob;
        float shY = hipY - TORSO * hs;
        float lw = 0.29f * ws;
        boolean side = p.view == LEFT || p.view == RIGHT;
        float dir = p.view == RIGHT ? 1f : -1f;
        float topW = 0.72f * ws, botW = (fem ? 0.9f : 0.78f) * ws;
        if (side) {
            topW *= 0.62f;
            botW *= fem ? 0.78f : 0.7f;
        }
        float swing = (float) Math.sin(p.phase) * p.walk;
        float dangle = (float) Math.sin(p.phase * 1.7f) * p.dangle;
        float armLen = 0.98f * hs, armT = 0.125f * (0.6f + 0.4f * ws);
        float waist = hipY + (fem ? 0.14f : 0.03f);

        fill.setShader(null);
        fill.setColor(shade(skinTone(Mii.SKIN_COLORS[m.v[Mii.SKIN]]), -0.14f));
        rect.set(-0.08f, shY - 0.12f, 0.08f, shY + 0.06f);
        c.drawRect(rect, fill);
        if (!side) {
            for (int s = -1; s <= 1; s += 2) {
                float lift = p.walk * 0.11f * Math.max(0f, s * (float) Math.sin(p.phase));
                float lx = s * 0.155f * ws + s * dangle * 0.03f;
                leg(c, lx, hipY, -0.04f - lift, lw, pants, s * dangle * 8f);
            }
            torso(c, shY, waist, topW, botW, shirt, fem);
            for (int s = -1; s <= 1; s += 2) {
                float ang = 20f + 4f * swing * s + p.dangle * 18f;
                if (s == 1 && p.wave > 0f) {
                    ang = ang + (150f - ang) * p.wave + p.wave * 16f * (float) Math.sin(p.wavePhase);
                }
                arm(c, s * (topW / 2f - 0.07f), shY + 0.1f, s * ang, armLen, armT, shirt);
            }
        } else {
            float a = 26f * swing;
            leg(c, -dir * 0.02f, hipY, -0.04f, lw, shade(pants, -0.18f), -a * dir);
            arm(c, -dir * 0.02f, shY + 0.1f, a * dir, armLen, armT, shade(shirt, -0.18f));
            torso(c, shY, waist, topW, botW, shirt, fem);
            leg(c, dir * 0.02f, hipY, -0.04f, lw, pants, a * dir);
            float ang = -a * dir;
            if (p.wave > 0f) ang = ang + (dir * 150f - ang) * p.wave + p.wave * 16f * (float) Math.sin(p.wavePhase);
            arm(c, dir * 0.03f, shY + 0.1f, ang, armLen, armT, shirt);
        }
        fill.setShader(null);
    }

    /** Cloth under the plaza's soft light: greyed on the shaded side, washed toward white where it catches it. */
    private static int[] cloth(int c) {
        return new int[] {mix(c, 0xFF7A7A7A, 0.28f), mix(c, 0xFFB0B0B0, 0.30f), mix(c, 0xFFFFFFFF, 0.35f + 0.2f * lum(c)),
                mix(c, 0xFFB0B0B0, 0.30f), mix(c, 0xFF8A8A8A, 0.25f)};
    }

    /** A cylinder's shading across x = -1..1; callers scale the canvas to the limb's width. */
    private Shader cylinder(int col) {
        Shader s = cylinders.get(col);
        if (s == null) {
            s = new LinearGradient(-1f, 0f, 1f, 0f, cloth(col), CLOTH_STOPS, Shader.TileMode.CLAMP);
            cylinders.put(col, s);
        }
        return s;
    }

    /** A ball's shading for a unit circle, lit from the upper right. */
    private Shader ball(int col) {
        Shader s = balls.get(col);
        if (s == null) {
            s = new RadialGradient(0.3f, -0.35f, 1.35f, new int[] {mix(col, 0xFFFFFFFF, 0.5f),
                    mix(col, 0xFFB0B0B0, 0.25f), mix(col, 0xFF6A6A6A, 0.3f)}, new float[] {0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP);
            balls.put(col, s);
        }
        return s;
    }

    private void leg(Canvas c, float x, float hipY, float footY, float lw, int pants, float rot) {
        int save = c.save();
        c.rotate(rot, x, hipY);
        c.translate(x, 0f);
        c.scale(lw / 2f, 1f);
        opaque(fill).setShader(cylinder(pants));
        rect.set(-1f, hipY - 0.04f, 1f, footY);
        c.drawRoundRect(rect, 1f, lw / 2f, fill);
        fill.setShader(null);
        fill.setColor(SHOES);
        rect.set(-1.15f, footY - 0.05f, 1.15f, footY + 0.04f);
        c.drawOval(rect, fill);
        c.restoreToCount(save);
    }

    /** An arm hanging from (x, y) with a round hand in the shirt's colour; ang is in degrees toward +x. */
    private void arm(Canvas c, float x, float y, float ang, float len, float t, int shirt) {
        int save = c.save();
        c.translate(x, y);
        c.rotate(-ang);
        int s2 = c.save();
        c.scale(t / 2f, 1f);
        opaque(fill).setShader(cylinder(shirt));
        rect.set(-1f, -t / 2f, 1f, len);
        c.drawRoundRect(rect, 1f, t / 2f, fill);
        c.restoreToCount(s2);
        float r = t * 0.82f;
        c.translate(0f, len);
        c.scale(r, r);
        opaque(fill).setShader(ball(shirt));
        c.drawCircle(0f, 0f, 1f, fill);
        fill.setShader(null);
        c.restoreToCount(save);
    }

    private void torso(Canvas c, float ty, float by, float topW, float botW, int shirt, boolean fem) {
        // Built with x divided by the half-span, then stretched back, so one cached gradient fits any width.
        float span = Math.max(topW, botW) / 2f;
        float tw = topW / 2f / span, bw = botW / 2f / span, r = 0.13f / span, b = 0.04f / span;
        Path t = path;
        t.reset();
        t.moveTo(-tw + r, ty);
        t.lineTo(tw - r, ty);
        t.quadTo(tw, ty, tw, ty + 0.13f);
        if (fem) t.quadTo(tw * 0.94f, (ty + by) / 2f, bw, by - 0.03f);
        else t.lineTo(bw, by - 0.03f);
        t.quadTo(bw, by, bw - b, by);
        t.lineTo(-bw + b, by);
        t.quadTo(-bw, by, -bw, by - 0.03f);
        if (fem) t.quadTo(-tw * 0.94f, (ty + by) / 2f, -tw, ty + 0.13f);
        else t.lineTo(-tw, ty + 0.13f);
        t.quadTo(-tw, ty, -tw + r, ty);
        t.close();
        int save = c.save();
        c.scale(span, 1f);
        opaque(fill).setShader(cylinder(shirt));
        c.drawPath(t, fill);
        fill.setShader(null);
        c.restoreToCount(save);
    }

    // ---- editor icons -------------------------------------------------------------------------------------

    /**
     * The picture on one option tile of the editor: the part on its own, drawn dark on the tile like the
     * original's option grid. cx/cy is the tile centre and size its height, in canvas pixels.
     */
    void icon(Canvas c, int prop, int index, Mii m, float cx, float cy, float size) {
        MiiSprites sp = sprites();
        if (sp != null && sp.icon(c, prop, index, m, cx, cy, size)) return;
        int save = c.save();
        c.translate(cx, cy);
        int dark = 0xFF2E2E2E;
        switch (prop) {
            case Mii.FACE: {
                float u = size * 0.6f;
                c.scale(u, u);
                c.translate(0f, -0.02f);
                Path f = face(index);
                fill.setShader(null);
                fill.setColor(0xFFFFFFFF);
                c.drawPath(f, fill);
                line(c, f, dark, 0.04f);
                fill.setColor(0xFFC8C8C8);
                c.drawCircle(-0.16f, 0.02f, 0.035f, fill);
                c.drawCircle(0.16f, 0.02f, 0.035f, fill);
                path2.reset();
                path2.moveTo(-0.12f, 0.24f);
                path2.quadTo(0f, 0.34f, 0.12f, 0.24f);
                line(c, path2, 0xFFC8C8C8, 0.03f);
                break;
            }
            case Mii.FEATURE: {
                float u = size * 0.6f;
                c.scale(u, u);
                int skin = Mii.SKIN_COLORS[m.v[Mii.SKIN]];
                Path f = face(m.v[Mii.FACE]);
                fill.setShader(null);
                fill.setColor(skin);
                c.drawPath(f, fill);
                line(c, f, dark, 0.035f);
                if (index == 0) none(c, 0.3f);
                else feature(c, index, skin, 0.225f, 0f, 0.39f, 0.2f, m.v[Mii.FACE]);
                break;
            }
            case Mii.HAIR: {
                float u = size * 0.48f;
                c.scale(u, u);
                c.translate(0f, 0.24f);
                Hair h = hair(index, m.v[Mii.HAIR_FLIP] != 0);
                int hc = 0xFF474747;
                fill.setShader(null);
                if (h.back != null) {
                    fill.setColor(0xFF2E2E2E);
                    c.drawPath(h.back, fill);
                }
                Path f = face(0);
                fill.setColor(0xFFFFFFFF);
                c.drawPath(f, fill);
                line(c, f, dark, 0.04f);
                fill.setColor(hc);
                if (h.front != null) c.drawPath(h.front, fill);
                if (h.extra != null) c.drawPath(h.extra, fill);
                if (h.top != null) c.drawPath(h.top, fill);
                break;
            }
            case Mii.BROW: {
                float u = size * 1.15f;
                c.scale(u, u);
                brows(c, index, dark, 0.21f, 0f, 1f, 0f);
                break;
            }
            case Mii.EYE: {
                float u = size * 1.15f;
                c.scale(u, u);
                eyes(c, index, Mii.EYE_COLORS[0], 0xFFFFFFFF, 0.19f, 0f, 1f, 0f, false);
                break;
            }
            case Mii.NOSE: {
                float u = size * 2.2f;
                c.scale(u, u);
                nose(c, index, dark, 0, -0.01f, 1f);
                break;
            }
            case Mii.MOUTH: {
                float u = size * 1.9f;
                c.scale(u, u);
                mouth(c, index, Mii.LIP_COLORS[m.v[Mii.MOUTH_COLOR]], 0f, 1f);
                break;
            }
            case Mii.GLASSES: {
                float u = size * 1.25f;
                c.scale(u, u);
                if (index == 0) none(c, 0.3f);
                else glasses(c, index, dark, 0.21f, 0f, 1f);
                break;
            }
            case Mii.MUSTACHE: {
                float u = size * 2.4f;
                c.scale(u, u);
                if (index == 0) none(c, 0.14f);
                else mustache(c, index, dark, 0f, 1f);
                break;
            }
            case Mii.BEARD: {
                float u = size * 0.62f;
                c.scale(u, u);
                c.translate(0f, -0.1f);
                Path f = face(m.v[Mii.FACE]);
                fill.setShader(null);
                fill.setColor(0xFFFFFFFF);
                c.drawPath(f, fill);
                line(c, f, dark, 0.035f);
                if (index == 0) none(c, 0.3f);
                else beard(c, index, dark, m.v[Mii.FACE], 0.43f, 0.2f);
                break;
            }
            case Mii.MOLE: {
                float u = size * 0.6f;
                c.scale(u, u);
                Path f = face(m.v[Mii.FACE]);
                fill.setShader(null);
                fill.setColor(0xFFFFFFFF);
                c.drawPath(f, fill);
                line(c, f, dark, 0.035f);
                if (index == 0) none(c, 0.3f);
                else {
                    fill.setColor(dark);
                    c.drawCircle(0.2f, 0.3f, 0.045f, fill);
                }
                break;
            }
            default:
                break;
        }
        c.restoreToCount(save);
    }

    /** The "none" mark: a ring with a slash. */
    private void none(Canvas c, float r) {
        stroke.setColor(0xFFB4B4B4);
        stroke.setStrokeWidth(r * 0.16f);
        c.drawCircle(0f, 0f, r, stroke);
        c.drawLine(-r * 0.7f, r * 0.7f, r * 0.7f, -r * 0.7f, stroke);
    }
}
