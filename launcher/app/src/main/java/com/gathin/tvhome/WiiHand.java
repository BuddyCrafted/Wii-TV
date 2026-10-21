package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * The pointing hand, after the console's cursor: upright, index finger up, three folded fingers to its right,
 * thumb on the left, a thick dark outline, light blue over the lower palm and the player number on it.
 * Drawn with its fingertip at the given point; `u` is one hand unit in pixels (the hand is ~55 units tall).
 */
final class WiiHand {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float builtFor = -1f;
    private Path hand, lines;

    WiiHand(Typeface face) {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setTypeface(face);
        text.setTextAlign(Paint.Align.CENTER);
    }

    private void build(float u) {
        Path p = new Path();
        p.addRoundRect(new RectF(-5.5f * u, 0f, 5.5f * u, 36f * u), 5.5f * u, 5.5f * u, Path.Direction.CW);
        union(p, u, 4.5f, 17f, 12.5f, 34f, 4f);     // middle finger, folded
        union(p, u, 11.5f, 19f, 19f, 35f, 3.8f);    // ring finger
        union(p, u, 18f, 21f, 24.5f, 36f, 3.3f);    // little finger
        union(p, u, -9f, 27f, 24.5f, 55f, 8.5f);    // palm
        union(p, u, -15.5f, 25.5f, -3f, 41f, 6f);   // thumb
        Path l = new Path();
        l.moveTo(5f * u, 24f * u);
        l.lineTo(5f * u, 32f * u);
        l.moveTo(12f * u, 25f * u);
        l.lineTo(12f * u, 33f * u);
        l.moveTo(18.5f * u, 27f * u);
        l.lineTo(18.5f * u, 34f * u);
        hand = p;
        lines = l;
        builtFor = u;
    }

    private static void union(Path p, float u, float l, float t, float r, float b, float rad) {
        Path q = new Path();
        q.addRoundRect(new RectF(l * u, t * u, r * u, b * u), rad * u, rad * u, Path.Direction.CW);
        p.op(q, Path.Op.UNION);
    }

    void draw(Canvas c, float x, float y, float u) {
        if (u != builtFor) build(u);
        int save = c.save();
        c.translate(x, y);
        // Soft shadow down and to the right.
        c.translate(2.5f * u, 3.5f * u);
        fill.setShader(null);
        fill.setColor(0x40000000);
        c.drawPath(hand, fill);
        c.translate(-2.5f * u, -3.5f * u);
        // White hand, turning light blue over the lower palm.
        fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(0f, 30f * u, 0f, 55f * u, 0xFFFFFFFF, 0xFFA9D8F6, Shader.TileMode.CLAMP));
        c.drawPath(hand, fill);
        fill.setShader(null);
        stroke.setColor(0xFF161616);
        stroke.setStrokeWidth(2.8f * u);
        c.drawPath(hand, stroke);
        stroke.setStrokeWidth(1.6f * u);
        c.drawPath(lines, stroke);
        // The player number on the palm.
        text.setTextSize(19f * u);
        text.setColor(0xFF1C8CE3);
        c.drawText("1", 9f * u, 50.5f * u, text);
        c.restoreToCount(save);
    }
}
