package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.util.LongSparseArray;

/**
 * The channel's interface pieces from a pack's ui/ sprites. The sheets are white or grey and the original
 * colours them per use, so each draw names a tint: a flat colour over the shape, a multiply, or a two-tone map
 * from the sprite's black to its white.
 */
final class MiiUiSprites {
    /** Reference px per sprite texel: the console draws them 1:1 on a 456-line picture shown 1080 tall. */
    static final float K = 2.33f;

    static final int NONE = 0, FLAT = 1, MUL = 2, DUO = 3;

    /** Colours sampled from captures of the original plaza. */
    static final int FILL = 0xEFFCFC, RIM = 0x607373, INK = 0x839595, SILVER = 0xB4D2D0, WHISTLE = 0x9CB8B6,
            DARK = 0xB0D1D1, FACE = 0x869898, FACE_LIGHT = 0xEEF6F6, BADGE = 0xF4FAFA, BADGE_INK = 0x6F8383,
            ON_DARK = 0x4A5C5C;

    private final MiiPack pack;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final LongSparseArray<ColorFilter> filters = new LongSparseArray<>();
    private final RectF dst = new RectF();

    private MiiUiSprites(MiiPack pack) {
        this.pack = pack;
    }

    /** The interface sprites of a pack, or null when it has none. */
    static MiiUiSprites from(MiiPack pack) {
        return pack != null && pack.bitmap("ui/ring") != null ? new MiiUiSprites(pack) : null;
    }

    Bitmap bitmap(String name) {
        return pack.bitmap("ui/" + name);
    }

    /** ui/name centred on (cx, cy), sx and sy screen px per texel; a and b are the tint's RGB colours. */
    boolean draw(Canvas c, String name, float cx, float cy, float sx, float sy, int mode, int a, int b, int alpha) {
        Bitmap bmp = bitmap(name);
        if (bmp == null) return false;
        float hw = bmp.getWidth() * sx / 2f, hh = bmp.getHeight() * sy / 2f;
        dst.set(cx - hw, cy - hh, cx + hw, cy + hh);
        return drawInto(c, bmp, dst, mode, a, b, alpha);
    }

    boolean draw(Canvas c, String name, float cx, float cy, float k, int mode, int a) {
        return draw(c, name, cx, cy, k, k, mode, a, 0, 255);
    }

    boolean drawInto(Canvas c, Bitmap bmp, RectF r, int mode, int a, int b, int alpha) {
        paint.setColorFilter(filter(mode, a, b));
        paint.setAlpha(alpha);
        c.drawBitmap(bmp, null, r, paint);
        return true;
    }

    private ColorFilter filter(int mode, int a, int b) {
        if (mode == NONE) return null;
        long key = ((long) mode << 56) | ((long) (a & 0xFFFFFF) << 24) | (b & 0xFFFFFF);
        ColorFilter f = filters.get(key);
        if (f != null) return f;
        switch (mode) {
            case FLAT:
                f = new PorterDuffColorFilter(0xFF000000 | a, PorterDuff.Mode.SRC_IN);
                break;
            case MUL:
                f = new LightingColorFilter(a & 0xFFFFFF, 0);
                break;
            default:
                f = new ColorMatrixColorFilter(duotone(a, b));
                break;
        }
        filters.put(key, f);
        return f;
    }

    /** Maps a grey sprite's white to light and its black to dark, keeping its alpha. */
    static ColorMatrix duotone(int light, int dark) {
        float[] m = new float[20];
        for (int ch = 0; ch < 3; ch++) {
            int shift = 16 - ch * 8;
            float d = (dark >> shift) & 255, l = (light >> shift) & 255;
            float span = (l - d) / 255f;
            m[ch * 5] = span * 0.299f;
            m[ch * 5 + 1] = span * 0.587f;
            m[ch * 5 + 2] = span * 0.114f;
            m[ch * 5 + 4] = d;
        }
        m[18] = 1f;
        return new ColorMatrix(m);
    }
}
