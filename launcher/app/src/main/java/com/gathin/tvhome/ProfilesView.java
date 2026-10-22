package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.List;

/**
 * "Who's watching?" and the look editor, in the menu's style. Picking: a row of characters on round cards
 * with their names, the focused one lifted with a glow, and a + card to add someone. Editing: the character
 * large on the left, bobbing and blinking, with rows on the right (skin, hair, hair style, shirt, name, done).
 */
final class ProfilesView extends SaverView {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent;
    private LinearGradient bg;
    private final android.graphics.Path clip = new android.graphics.Path();

    // Picking
    private String title = "", hint = "", addLabel = "";
    private List<String> names;
    private int[] looks;
    private boolean canAdd;
    private int focus;
    private float[] lift = new float[0];

    // Editing
    private boolean editing;
    private int editLook;
    private String editName = "";
    private String[] rows = new String[0];
    private int row;

    ProfilesView(Context context, int accent) {
        super(context);
        this.accent = accent;
        text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
    }

    void showPicker(String title, String hint, String addLabel, List<String> names, int[] looks, boolean canAdd, int focus) {
        editing = false;
        this.title = title;
        this.hint = hint;
        this.addLabel = addLabel;
        this.names = names;
        this.looks = looks;
        this.canAdd = canAdd;
        this.focus = focus;
        lift = new float[cards()];
        invalidate();
    }

    void showEditor(String title, String hint, int look, String name, String[] rows, int row) {
        editing = true;
        this.title = title;
        this.hint = hint;
        this.editLook = look;
        this.editName = name;
        this.rows = rows;
        this.row = row;
        invalidate();
    }

    int cards() {
        return names == null ? 0 : names.size() + (canAdd ? 1 : 0);
    }

    @Override
    void layout(int w, int h) {
        bg = new LinearGradient(0, 0, 0, h, 0xFFFFFFFF, 0xFFE6ECF0, Shader.TileMode.CLAMP);
    }

    @Override
    void frame(Canvas c, float t) {
        p.setColor(0xFFFFFFFF);
        p.setShader(bg);
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setColor(0x0F000000);
        for (float y = 0.7f; y < 1080f; y += 9.472f) c.drawRect(0, y * s, w, (y + 2.2f) * s, p);

        text.setColor(0xFF7C7C7C);
        text.setTextSize(62f * s);
        c.drawText(title, 960f * s, 200f * s, text);
        p.setColor(0xFFC4C4C4);
        c.drawRect(240f * s, 240f * s, 1680f * s, 243f * s, p);

        if (editing) editor(c, t);
        else picker(c, t);

        text.setColor(0xFF9A9A9A);
        text.setTextSize(34f * s);
        c.drawText(hint, 960f * s, 1000f * s, text);
    }

    private void picker(Canvas c, float t) {
        int n = cards();
        if (n == 0) return;
        float cardW = 240f, gap = 40f;
        float total = n * cardW + (n - 1) * gap;
        float x0 = 960f - total / 2f + cardW / 2f;
        for (int i = 0; i < n; i++) {
            float target = i == focus ? 1f : 0f;
            lift[i] += (target - lift[i]) * 0.25f;
            float cx = x0 + i * (cardW + gap), cy = 560f - 26f * lift[i];
            float r = 120f + 10f * lift[i];
            if (lift[i] > 0.02f) {
                for (int k = 3; k >= 1; k--) {
                    p.setColor(accent);
                    p.setAlpha((int) (lift[i] * 60 / k));
                    c.drawCircle(cx * s, cy * s, (r + 6f + k * 8f) * s, p);
                }
            }
            p.setColor(0x22000000);
            c.drawCircle(cx * s, (cy + 8f) * s, r * s, p);
            p.setColor(0xFFF7FAFC);
            c.drawCircle(cx * s, cy * s, r * s, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth((i == focus ? 6f : 3f) * s);
            p.setColor(i == focus ? accent : 0xFFC9D6DE);
            c.drawCircle(cx * s, cy * s, r * s, p);
            p.setStyle(Paint.Style.FILL);
            String label;
            if (i < names.size()) {
                int save = c.save();
                clip.reset();
                clip.addCircle(cx * s, cy * s, (r - 3f) * s, android.graphics.Path.Direction.CW);
                c.clipPath(clip);
                float bob = i == focus ? 4f * (float) Math.sin(t * 3f) : 0f;
                Avatar.draw(c, cx * s, (cy - 22f + bob) * s, 52f * s, looks[i], blink(t + i * 1.7f));
                c.restoreToCount(save);
                label = names.get(i);
            } else {
                p.setColor(0xFFB0BEC5);
                c.drawRect((cx - 40f) * s, (cy - 7f) * s, (cx + 40f) * s, (cy + 7f) * s, p);
                c.drawRect((cx - 7f) * s, (cy - 40f) * s, (cx + 7f) * s, (cy + 40f) * s, p);
                label = addLabel;
            }
            text.setColor(i == focus ? 0xFF4A4A4A : 0xFF8A8A8A);
            text.setTextSize(40f * s);
            c.drawText(label, cx * s, (cy + r + 70f) * s, text);
        }
    }

    private void editor(Canvas c, float t) {
        float cx = 560f, cy = 520f;
        p.setColor(0xFFF7FAFC);
        c.drawCircle(cx * s, (cy + 40f) * s, 260f * s, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5f * s);
        p.setColor(accent);
        c.drawCircle(cx * s, (cy + 40f) * s, 260f * s, p);
        p.setStyle(Paint.Style.FILL);
        int save = c.save();
        clip.reset();
        clip.addCircle(cx * s, (cy + 40f) * s, 255f * s, android.graphics.Path.Direction.CW);
        c.clipPath(clip);
        Avatar.draw(c, cx * s, (cy - 10f + 5f * (float) Math.sin(t * 2.4f)) * s, 110f * s, editLook, blink(t));
        c.restoreToCount(save);
        text.setColor(0xFF4A4A4A);
        text.setTextSize(48f * s);
        c.drawText(editName, cx * s, 900f * s, text);

        float x = 1000f, y0 = 330f, rowH = 92f;
        for (int i = 0; i < rows.length; i++) {
            float y = y0 + i * rowH;
            boolean f = i == row;
            p.setColor(f ? 0xFFFFFFFF : 0xFFF2F5F7);
            c.drawRoundRect(x * s, y * s, (x + 640f) * s, (y + 72f) * s, 36f * s, 36f * s, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth((f ? 5f : 2.5f) * s);
            p.setColor(f ? accent : 0xFFC9D6DE);
            c.drawRoundRect(x * s, y * s, (x + 640f) * s, (y + 72f) * s, 36f * s, 36f * s, p);
            p.setStyle(Paint.Style.FILL);
            text.setColor(f ? 0xFF3A3A3A : 0xFF7A7A7A);
            text.setTextSize(36f * s);
            c.drawText(rows[i], (x + 320f) * s, (y + 49f) * s, text);
        }
    }

    /** Closes the eyes for a moment every few seconds. */
    private static float blink(float t) {
        float ph = t % 4.2f;
        return ph < 0.12f ? 1f - Math.abs(ph - 0.06f) / 0.06f : 0f;
    }
}
