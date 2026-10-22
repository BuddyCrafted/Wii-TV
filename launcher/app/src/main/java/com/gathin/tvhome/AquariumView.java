package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * The Aquarium screensaver: deep blue water with light rays from the surface, swaying seaweed on a sandy floor,
 * fish of a few kinds swimming across at their own speeds, bubbles rising, and a small clock that drifts.
 * The water, sand and rocks are drawn once; everything that moves is a few paths a frame.
 */
final class AquariumView extends SaverView {
    private static final int FISH = 10, BUBBLES = 28, WEEDS = 9;
    /** Body, fin, stripe (0 = none) per kind: clownfish, blue tang, yellow tang, pink, silver. */
    private static final int[][] KINDS = {
        {0xFFFF7A1A, 0xFFE0560A, 0xFFFFFFFF},
        {0xFF2F6BFF, 0xFFFFD21F, 0},
        {0xFFFFD21F, 0xFFF2B600, 0},
        {0xFFFF6FAE, 0xFFD94C8C, 0},
        {0xFFC9D6E2, 0xFF8EA4B8, 0},
    };

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private final SegmentClock clock = new SegmentClock();
    private final float[] fy = new float[FISH], fx0 = new float[FISH], fv = new float[FISH], fl = new float[FISH],
            fph = new float[FISH];
    private final int[] fkind = new int[FISH], fdir = new int[FISH];
    private final float[] bx = new float[BUBBLES], bv = new float[BUBBLES], br = new float[BUBBLES], boff = new float[BUBBLES];
    private final float[] wx = new float[WEEDS], wh = new float[WEEDS];
    private Bitmap water;
    private LinearGradient ray;
    private final RectF screen = new RectF();

    AquariumView(Context context) {
        super(context);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        Random r = new Random(3);
        for (int i = 0; i < FISH; i++) {
            fkind[i] = i % KINDS.length;
            fdir[i] = r.nextBoolean() ? 1 : -1;
            fy[i] = 170f + 640f * r.nextFloat();
            fx0[i] = r.nextFloat() * 2400f;
            fv[i] = 55f + 90f * r.nextFloat();
            fl[i] = 70f + 80f * r.nextFloat();
            fph[i] = r.nextFloat() * 6.283f;
        }
        float[] sources = {260f, 1010f, 1640f};
        for (int i = 0; i < BUBBLES; i++) {
            bx[i] = sources[i % 3] + (r.nextFloat() - 0.5f) * 40f;
            bv[i] = 70f + 70f * r.nextFloat();
            br[i] = 4f + 9f * r.nextFloat();
            boff[i] = r.nextFloat() * 1100f;
        }
        for (int i = 0; i < WEEDS; i++) {
            wx[i] = 90f + i * 215f + (r.nextFloat() - 0.5f) * 80f;
            wh[i] = 180f + 200f * r.nextFloat();
        }
    }

    @Override
    void layout(int w, int h) {
        int bw = Math.max(1, w / 2), bh = Math.max(1, h / 2);
        Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float u = bw / 1920f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, bh, new int[] {0xFF2A8ED0, 0xFF0E5C9E, 0xFF07335F, 0xFF052443},
                new float[] {0f, 0.25f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, bw, bh, p);
        // Sand with a gently wavy top, and three rocks.
        Path sand = new Path();
        sand.moveTo(0, bh);
        for (int x = 0; x <= 1920; x += 40) sand.lineTo(x * u, (965f + 14f * (float) Math.sin(x / 170f)) * u);
        sand.lineTo(bw, bh);
        sand.close();
        p.setShader(new LinearGradient(0, 940f * u, 0, bh, 0xFFE2CD94, 0xFFA88B4E, Shader.TileMode.CLAMP));
        c.drawPath(sand, p);
        p.setShader(null);
        float[][] rocks = {{420, 985, 120, 55}, {1350, 995, 160, 62}, {1470, 1005, 90, 40}};
        for (float[] rk : rocks) {
            p.setShader(new RadialGradient((rk[0] - rk[2] * 0.3f) * u, (rk[1] - rk[3] * 0.4f) * u, rk[2] * u,
                    0xFF8C949C, 0xFF4E555D, Shader.TileMode.CLAMP));
            c.drawOval((rk[0] - rk[2]) * u, (rk[1] - rk[3]) * u, (rk[0] + rk[2]) * u, (rk[1] + rk[3]) * u, p);
        }
        p.setShader(null);
        water = b;
        ray = new LinearGradient(0, 0, 0, 900f * s, 0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        screen.set(0, 0, w, h);
    }

    @Override
    void frame(Canvas c, float t) {
        if (water != null) c.drawBitmap(water, null, screen, bmp);
        drawRays(c, t);
        for (int i = 0; i < WEEDS; i++) drawWeed(c, i, t);
        for (int i = 0; i < FISH; i++) drawFish(c, i, t);
        drawBubbles(c, t);
        float cx = 1500f + 120f * (float) Math.sin(t * 2 * Math.PI / 200f);
        float cy = 70f + 30f * (float) Math.sin(t * 2 * Math.PI / 131f);
        fill.setShader(null);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xB3FFFFFF);
        clock.draw(c, fill, cx * s, cy * s, 0.8f * s, ((int) t) % 2 == 0);
    }

    private void drawRays(Canvas c, float t) {
        fill.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 5; i++) {
            float x = 250f + i * 380f + 60f * (float) Math.sin(t * 0.13f + i * 1.7f);
            float spread = 120f + 40f * (float) Math.sin(t * 0.21f + i);
            path.reset();
            path.moveTo((x - 40f) * s, 0);
            path.lineTo((x + 40f) * s, 0);
            path.lineTo((x + spread + 160f) * s, 900f * s);
            path.lineTo((x - spread + 160f) * s, 900f * s);
            path.close();
            fill.setShader(ray);
            c.drawPath(path, fill);
        }
        fill.setShader(null);
    }

    private void drawWeed(Canvas c, int i, float t) {
        float base = 975f, x = wx[i], hh = wh[i];
        path.reset();
        path.moveTo(x * s, base * s);
        int seg = 8;
        for (int k = 1; k <= seg; k++) {
            float f = k / (float) seg;
            float sway = 26f * f * f * (float) Math.sin(t * 0.9f + i * 0.8f + f * 2.2f);
            path.lineTo((x + sway) * s, (base - hh * f) * s);
        }
        stroke.setShader(null);
        stroke.setColor(i % 2 == 0 ? 0xFF2E8B57 : 0xFF3FA34D);
        stroke.setStrokeWidth(16f * s);
        c.drawPath(path, stroke);
        stroke.setColor(0x6698E0A0);
        stroke.setStrokeWidth(4f * s);
        c.drawPath(path, stroke);
    }

    private void drawFish(Canvas c, int i, float t) {
        float span = 1920f + 2f * 240f;
        float x = ((fx0[i] + fv[i] * t) % span) - 240f;
        if (fdir[i] < 0) x = 1920f - x;
        float y = fy[i] + 28f * (float) Math.sin(t * 0.45f + fph[i]);
        float L = fl[i];
        int[] kind = KINDS[fkind[i]];
        int save = c.save();
        c.translate(x * s, y * s);
        c.scale(fdir[i] * s, s);
        // Tail, wagging.
        float wag = 0.22f * L * (float) Math.sin(t * 7f + fph[i]);
        path.reset();
        path.moveTo(-0.42f * L, 0);
        path.lineTo(-0.82f * L, -0.3f * L + wag);
        path.lineTo(-0.74f * L, wag * 0.5f);
        path.lineTo(-0.82f * L, 0.3f * L + wag);
        path.close();
        fill.setShader(null);
        fill.setColor(kind[1]);
        c.drawPath(path, fill);
        // Top fin.
        path.reset();
        path.moveTo(-0.2f * L, -0.22f * L);
        path.quadTo(0f, -0.48f * L, 0.18f * L, -0.22f * L);
        path.close();
        c.drawPath(path, fill);
        // Body, lighter on top.
        oval.set(-0.5f * L, -0.27f * L, 0.5f * L, 0.27f * L);
        fill.setShader(new LinearGradient(0, -0.27f * L, 0, 0.27f * L, lighten(kind[0]), kind[0], Shader.TileMode.CLAMP));
        c.drawOval(oval, fill);
        fill.setShader(null);
        if (kind[2] != 0) {
            // Clownfish bands, kept inside the body.
            path.reset();
            path.addOval(oval, Path.Direction.CW);
            int sv = c.save();
            c.clipPath(path);
            fill.setColor(kind[2]);
            c.drawRect(0.12f * L, -0.3f * L, 0.24f * L, 0.3f * L, fill);
            c.drawRect(-0.22f * L, -0.3f * L, -0.1f * L, 0.3f * L, fill);
            c.restoreToCount(sv);
        }
        // Eye.
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(0.3f * L, -0.06f * L, 0.075f * L, fill);
        fill.setColor(0xFF111111);
        c.drawCircle(0.32f * L, -0.06f * L, 0.04f * L, fill);
        c.restoreToCount(save);
    }

    private void drawBubbles(Canvas c, float t) {
        stroke.setShader(null);
        for (int i = 0; i < BUBBLES; i++) {
            float travel = (t * bv[i] + boff[i]) % 1000f;
            float y = 960f - travel;
            float x = bx[i] + 12f * (float) Math.sin(t * 2.2f + i);
            float r = br[i] * (1f + travel / 1600f);
            int a = Math.round(170 * Math.min(1f, (y + 20f) / 200f));
            if (a <= 0) continue;
            stroke.setColor(0xFFFFFFFF);
            stroke.setAlpha(a);
            stroke.setStrokeWidth(2.2f * s);
            c.drawCircle(x * s, y * s, r * s, stroke);
            fill.setColor(0xFFFFFFFF);
            fill.setAlpha(a / 2);
            c.drawCircle((x - r * 0.35f) * s, (y - r * 0.35f) * s, r * 0.25f * s, fill);
        }
    }

    private static int lighten(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return 0xFF000000 | ((r + (255 - r) / 3) << 16) | ((g + (255 - g) / 3) << 8) | (b + (255 - b) / 3);
    }
}
