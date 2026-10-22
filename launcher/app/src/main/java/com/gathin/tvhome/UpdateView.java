package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * The System Update screen, in the menu's style: pale stripes, the title, a ring of dots turning, a progress
 * bar with the percentage, what is happening, and a reminder not to turn the power off. It keeps moving the
 * whole time, so the update never looks stuck.
 */
final class UpdateView extends SaverView {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent;
    private String title = "", status = "", note = "", footer = "";
    /** 0..1, or below 0 for no bar (an error, or waiting with no size known). */
    private float progress = 0f;
    private boolean spinning = true;
    private LinearGradient bg;

    UpdateView(Context context, int accent) {
        super(context);
        this.accent = accent;
        text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
    }

    void set(String title, String status, String note, String footer, float progress, boolean spinning) {
        this.title = title;
        this.status = status;
        this.note = note;
        this.footer = footer;
        this.progress = progress;
        this.spinning = spinning;
        invalidate();
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
        // The menu's faint stripes.
        p.setColor(0x0F000000);
        for (float y = 0.7f; y < 1080f; y += 9.472f) c.drawRect(0, y * s, w, (y + 2.2f) * s, p);

        text.setColor(0xFF7C7C7C);
        text.setTextSize(62f * s);
        c.drawText(title, 960f * s, 250f * s, text);
        p.setColor(0xFFC4C4C4);
        c.drawRect(240f * s, 290f * s, 1680f * s, 293f * s, p);

        if (spinning) {
            // Twelve dots round a circle, the brightest one going round.
            float cx = 960f * s, cy = 470f * s, rr = 62f * s;
            int lead = (int) (t * 12f) % 12;
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI * 2 / 12 - Math.PI / 2;
                int age = (lead - i + 12) % 12;
                p.setColor(accent);
                p.setAlpha(Math.max(40, 255 - age * 20));
                c.drawCircle(cx + (float) Math.cos(a) * rr, cy + (float) Math.sin(a) * rr, (11f - age * 0.4f) * s, p);
            }
        }

        text.setColor(0xFF555555);
        text.setTextSize(48f * s);
        c.drawText(status, 960f * s, 640f * s, text);

        if (progress >= 0f) {
            float l = 460f * s, r = 1460f * s, top = 690f * s, bot = 744f * s, rad = 27f * s;
            p.setColor(0xFFF0F0F0);
            c.drawRoundRect(l, top, r, bot, rad, rad, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4f * s);
            p.setColor(0xFFC9D6DE);
            c.drawRoundRect(l, top, r, bot, rad, rad, p);
            p.setStyle(Paint.Style.FILL);
            float f = Math.min(1f, progress);
            if (f > 0f) {
                p.setColor(accent);
                c.drawRoundRect(l + 6f * s, top + 6f * s, l + 6f * s + (r - l - 12f * s) * f, bot - 6f * s, rad - 6f * s, rad - 6f * s, p);
                // A soft shine moving along the filled part.
                float sx = l + (r - l) * f * ((t * 0.6f) % 1f);
                p.setColor(0x55FFFFFF);
                c.drawRoundRect(Math.max(l + 6f * s, sx - 40f * s), top + 10f * s, Math.min(l + (r - l) * f - 6f * s, sx + 40f * s),
                        top + 22f * s, 6f * s, 6f * s, p);
            }
            text.setColor(0xFF555555);
            text.setTextSize(40f * s);
            c.drawText(Math.round(f * 100) + "%", 960f * s, 810f * s, text);
        }

        text.setColor(0xFF8A8A8A);
        text.setTextSize(38f * s);
        c.drawText(note, 960f * s, 900f * s, text);
        text.setTextSize(30f * s);
        text.setColor(0xFFAAAAAA);
        c.drawText(footer, 960f * s, 1020f * s, text);
    }
}
