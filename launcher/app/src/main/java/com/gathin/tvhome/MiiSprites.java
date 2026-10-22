package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Draws Miis from a MiiPack, in the same head units and calls as MiiArt. The face is one shaded faceline with a
 * texture laid over it: the texture holds the eyes, brows, mouth, moustache and mole, a second one the face
 * features (multiplied into the skin) and a third the glasses. Turned views bend those textures over the
 * faceline with a mesh. Hair, nose and beard are pre-lit sprites tinted here.
 *
 * Not thread-safe, like MiiArt: one instance per drawing thread.
 */
final class MiiSprites {
    private static final String[] VIEW = {"F", "L", "R", "B"};
    private static final int EYE_INK = 0xFF161414, MOUTH_INK = 0xFF2A1A18, NOSE_INK = 0xFF2A2420;
    private static final int LENS = 0xFF404040, MOLE = 0xFF4A3426, WHITE = 0xFFFFFFFF;

    private final MiiPack pack;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint atop = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint mul = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] cm = new float[20];
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final Map<String, Bitmap[]> textures = new LinkedHashMap<String, Bitmap[]>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap[]> e) {
            return size() > 6;
        }
    };

    MiiSprites(MiiPack pack) {
        this.pack = pack;
        atop.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        mul.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    // ---- colour filters --------------------------------------------------------------------------------

    /** Shaded sprites keep the light in red (divided by shadeMax) and a highlight in green. */
    private Paint shade(Paint p, int color) {
        Arrays.fill(cm, 0f);
        float k = pack.shadeMax / 255f;
        cm[0] = ((color >> 16) & 255) * k;
        cm[5] = ((color >> 8) & 255) * k;
        cm[10] = (color & 255) * k;
        cm[1] = cm[6] = cm[11] = 1f;
        cm[18] = 1f;
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        return p;
    }

    /** Keyed parts hold how much of each pixel is colour a, b and c in red, green and blue; the rest is ink. */
    private Paint keyed(Paint p, int a, int b, int c, int ink) {
        Arrays.fill(cm, 0f);
        for (int ch = 0; ch < 3; ch++) {
            int sh = 16 - ch * 8;
            float i = (ink >> sh) & 255;
            cm[ch * 5] = (((a >> sh) & 255) - i) / 255f;
            cm[ch * 5 + 1] = (((b >> sh) & 255) - i) / 255f;
            cm[ch * 5 + 2] = (((c >> sh) & 255) - i) / 255f;
            cm[ch * 5 + 4] = i;
        }
        cm[18] = 1f;
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        return p;
    }

    private Paint tint(Paint p, int color) {
        Arrays.fill(cm, 0f);
        cm[4] = (color >> 16) & 255;
        cm[9] = (color >> 8) & 255;
        cm[14] = color & 255;
        cm[18] = 1f;
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        return p;
    }

    private Paint plain(Paint p) {
        p.setColorFilter(null);
        return p;
    }

    // ---- heads -----------------------------------------------------------------------------------------

    /** Whether a hair style has anything to draw behind the body. */
    static boolean hasBack(MiiPack pack, Mii m) {
        int h = m.v[Mii.HAIR];
        for (String v : VIEW) {
            if (pack.has("lo/hair/" + h + "_" + v + "_b") || pack.has("lo/hair/" + h + "_" + v + "_hb")) return true;
        }
        return false;
    }

    /** One head, as MiiArt.head draws it; hi picks the maker's sharper set (front view only). */
    void head(Canvas c, Mii m, int view, boolean back, boolean closed, boolean hi) {
        String set = hi && view == MiiArt.FRONT ? "hi" : "lo";
        int[] v = m.v;
        int face = v[Mii.FACE];
        boolean flip = v[Mii.HAIR_FLIP] != 0;
        int hairC = Mii.HAIR_COLORS[v[Mii.HAIR_COLOR]];
        int skin = Mii.SKIN_COLORS[v[Mii.SKIN]];
        int shirt = m.shirt();
        String hv = VIEW[view];
        if (flip && view == MiiArt.LEFT) hv = "R";
        else if (flip && view == MiiArt.RIGHT) hv = "L";
        String hp = set + "/hair/" + v[Mii.HAIR] + "_" + hv + "_";
        // Hair is modelled on the first faceline; the others get it moved up and widened to fit.
        float dy = pack.faceTop[face] - pack.faceTop[0], sx = pack.faceHw[face] / pack.faceHw[0];
        if (back) {
            if (view == MiiArt.BACK) return;
            hair(c, hp + "b", shade(paint, hairC), flip, dy, sx);
            hair(c, hp + "hb", shade(paint, shirt), flip, dy, sx);
            return;
        }
        String vs = VIEW[view];
        if (view == MiiArt.BACK) {
            sprite(c, set + "/face/" + face + "_B", shade(paint, skin), 0f, 1f);
            hair(c, hp + "f", shade(paint, hairC), flip, dy, sx);
            hair(c, hp + "hf", shade(paint, shirt), flip, dy, sx);
            return;
        }
        Bitmap[] tex = faceTextures(m, closed, set);
        int layer = c.saveLayer(-1.2f, -1.4f, 1.2f, 1.3f, null);
        sprite(c, set + "/face/" + face + "_" + vs, shade(paint, skin), 0f, 1f);
        if (tex[1] != null) texture(c, tex[1], view, face, mul);
        texture(c, tex[0], view, face, atop);
        c.restoreToCount(layer);

        int nose = pack.noseMap[Math.min(v[Mii.NOSE], pack.noseMap.length - 1)];
        float ny = v[Mii.NOSE_Y] * 0.026f, ns = 1f + v[Mii.NOSE_SIZE] * 0.1f;
        sprite(c, set + "/nose/" + nose + "_" + vs + "_s", shade(paint, skin), ny, ns);
        sprite(c, set + "/nose/" + nose + "_" + vs + "_l", tint(paint, NOSE_INK), ny, ns);
        if (v[Mii.BEARD] > 0) {
            sprite(c, set + "/beard/" + (v[Mii.BEARD] - 1) + "_" + face + "_" + vs,
                    shade(paint, Mii.HAIR_COLORS[v[Mii.BEARD_COLOR]]), 0f, 1f);
        }
        hair(c, hp + "f", shade(paint, hairC), flip, dy, sx);
        hair(c, hp + "hf", shade(paint, shirt), flip, dy, sx);
        if (tex[2] != null) texture(c, tex[2], view, face, plain(paint));
    }

    private void sprite(Canvas c, String name, Paint p, float dy, float scale) {
        Bitmap b = pack.bitmap(name);
        float[] o = pack.origin(name);
        if (b == null || o == null) return;
        float px = pack.px(name);
        int save = c.save();
        c.translate(0f, dy);
        if (scale != 1f) {
            c.scale(scale, scale, o[0] + b.getWidth() / px / 2f, o[1] + b.getHeight() / px / 2f);
        }
        c.translate(o[0], o[1]);
        c.scale(1f / px, 1f / px);
        c.drawBitmap(b, 0f, 0f, p);
        c.restoreToCount(save);
    }

    private void hair(Canvas c, String name, Paint p, boolean flip, float dy, float sx) {
        Bitmap b = pack.bitmap(name);
        float[] o = pack.origin(name);
        if (b == null || o == null) return;
        float px = pack.px(name);
        int save = c.save();
        c.translate(0f, dy);
        c.scale(flip ? -sx : sx, 1f);
        c.translate(o[0], o[1]);
        c.scale(1f / px, 1f / px);
        c.drawBitmap(b, 0f, 0f, p);
        c.restoreToCount(save);
    }

    /** Lays a face texture over the faceline: flat for the front, bent over the turned face otherwise. */
    private void texture(Canvas c, Bitmap t, int view, int face, Paint p) {
        float[] tb = pack.texBox;
        if (view == MiiArt.FRONT) {
            rect.set(tb[0], tb[1], tb[2], tb[3]);
            c.drawBitmap(t, null, rect, p);
            return;
        }
        float[] g = pack.grid(face, VIEW[view]);
        if (g == null) return;
        c.drawBitmapMesh(t, pack.gridW - 1, pack.gridH - 1, g, 0, null, 0, p);
    }

    // ---- face textures ---------------------------------------------------------------------------------

    private Bitmap[] faceTextures(Mii m, boolean closed, String set) {
        String key = set + (closed ? 'c' : 'o') + Arrays.toString(m.v);
        Bitmap[] t = textures.get(key);
        if (t != null) return t;
        t = new Bitmap[3];
        t[0] = newTexture(set, 0);
        drawParts(new TexCanvas(t[0]).c, m, closed);
        if (m.v[Mii.FEATURE] > 0) {
            t[1] = newTexture(set, WHITE);
            Bitmap f = pack.bitmap("tex/feature_" + m.v[Mii.FEATURE]);
            if (f != null) ring(new TexCanvas(t[1]).c, f, pack.featureRing, 0f, pack.featureY * pack.calFeature,
                    pack.calFeature, plain(fill));
        }
        if (m.v[Mii.GLASSES] > 0) {
            t[2] = newTexture(set, 0);
            Bitmap g = pack.bitmap("tex/glasses_" + m.v[Mii.GLASSES]);
            if (g != null) {
                float k = pack.calGlasses * (1f + m.v[Mii.GLASSES_SIZE] * 0.08f);
                float y = MiiArt.eyeY(m) + 0.005f + m.v[Mii.GLASSES_Y] * 0.026f + pack.glassesY * k;
                ring(new TexCanvas(t[2]).c, g, pack.glassesRing, 0f, y, k,
                        keyed(fill, 0xFF000000, LENS, 0xFF000000, Mii.GLASSES_COLORS[m.v[Mii.GLASSES_COLOR]]));
            }
        }
        textures.put(key, t);
        return t;
    }

    private Bitmap newTexture(String set, int bg) {
        float px = set.equals("hi") ? pack.pxHi : pack.pxLo;
        float[] tb = pack.texBox;
        Bitmap b = Bitmap.createBitmap((int) Math.ceil((tb[2] - tb[0]) * px), (int) Math.ceil((tb[3] - tb[1]) * px),
                Bitmap.Config.ARGB_8888);
        if (bg != 0) b.eraseColor(bg);
        return b;
    }

    /** A canvas over a face texture that takes head units. */
    private final class TexCanvas {
        final Canvas c;

        TexCanvas(Bitmap b) {
            c = new Canvas(b);
            float[] tb = pack.texBox;
            c.scale(b.getWidth() / (tb[2] - tb[0]), b.getHeight() / (tb[3] - tb[1]));
            c.translate(-tb[0], -tb[1]);
        }
    }

    private void drawParts(Canvas c, Mii m, boolean closed) {
        int[] v = m.v;
        float ex = MiiArt.eyeX(m), ey = MiiArt.eyeY(m), my = MiiArt.mouthY(m);
        int up = pack.keyedUp;
        int beardC = Mii.HAIR_COLORS[v[Mii.BEARD_COLOR]];
        if (v[Mii.MUSTACHE] > 0) {
            Bitmap b = pack.bitmap("tex/mustache_" + v[Mii.MUSTACHE]);
            float k = pack.calMustache * (1f + v[Mii.MUSTACHE_SIZE] * 0.1f);
            if (b != null) ring(c, b, pack.mustacheRing, 0f, my - 0.085f + v[Mii.MUSTACHE_Y] * 0.02f
                    - pack.mustacheY * k, k, tint(fill, beardC));
        }
        int lip = Mii.LIP_COLORS[v[Mii.MOUTH_COLOR]];
        part(c, "tex/mouth_" + v[Mii.MOUTH], 0f, my, pack.calMouth * up / (1f + v[Mii.MOUTH_SIZE] * 0.09f), 0f,
                false, keyed(fill, lip, MiiArt.shade(lip, -0.28f), WHITE, MOUTH_INK));
        float eppu = pack.calEye * up / (1f + v[Mii.EYE_SIZE] * 0.09f), er = v[Mii.EYE_ROT] * 4f;
        String eye = "tex/eye_" + v[Mii.EYE];
        if (closed) {
            Bitmap b = pack.bitmap(eye);
            float hw = b == null ? 0.1f : b.getWidth() / eppu * 0.42f;
            closedEye(c, ex, ey, hw, er);
            closedEye(c, -ex, ey, hw, -er);
        } else {
            keyed(fill, EYE_INK, WHITE, Mii.EYE_COLORS[v[Mii.EYE_COLOR]], EYE_INK);
            part(c, eye, ex, ey, eppu, er, false, fill);
            part(c, eye, -ex, ey, eppu, -er, true, fill);
        }
        if (v[Mii.MOLE] != 0) {
            fill.setColorFilter(null);
            fill.setColor(MOLE);
            c.drawCircle(0.2f + v[Mii.MOLE_X] * 0.035f, 0.3f + v[Mii.MOLE_Y] * 0.035f,
                    0.022f * (1f + v[Mii.MOLE_SIZE] * 0.22f), fill);
        }
        if (v[Mii.BROW] < pack.browIcons) {
            float bx = 0.24f + v[Mii.BROW_SPACE] * 0.02f, by = -0.16f + v[Mii.BROW_Y] * 0.026f + ey * 0.5f;
            float bppu = pack.calBrow * up / (1f + v[Mii.BROW_SIZE] * 0.09f), br = v[Mii.BROW_ROT] * 5f;
            tint(fill, Mii.HAIR_COLORS[v[Mii.BROW_COLOR]]);
            part(c, "tex/brow_" + v[Mii.BROW], bx, by, bppu, br, false, fill);
            part(c, "tex/brow_" + v[Mii.BROW], -bx, by, bppu, -br, true, fill);
        }
        fill.setColorFilter(null);
    }

    private void part(Canvas c, String name, float x, float y, float ppu, float rot, boolean mirror, Paint p) {
        Bitmap b = pack.bitmap(name);
        if (b == null) return;
        int save = c.save();
        c.translate(x, y);
        c.rotate(rot);
        c.scale((mirror ? -1f : 1f) / ppu, 1f / ppu);
        c.drawBitmap(b, -b.getWidth() / 2f, -b.getHeight() / 2f, p);
        c.restoreToCount(save);
    }

    /** A part cut from a round icon: the icon's centre goes to (x, y) and its face disc spans k head units. */
    private void ring(Canvas c, Bitmap b, float[] r, float x, float y, float k, Paint p) {
        float ppu = r[2] * r[3] / k;
        int save = c.save();
        c.translate(x, y);
        c.scale(1f / ppu, 1f / ppu);
        c.drawBitmap(b, -r[0] * r[3], -r[1] * r[3], p);
        c.restoreToCount(save);
    }

    private void closedEye(Canvas c, float x, float y, float hw, float rot) {
        int save = c.save();
        c.translate(x, y);
        c.rotate(rot);
        path.reset();
        path.moveTo(-hw, -0.005f);
        path.quadTo(0f, hw * 0.45f, hw, -0.005f);
        line.setColor(EYE_INK);
        line.setStrokeWidth(0.03f);
        c.drawPath(path, line);
        c.restoreToCount(save);
    }

    // ---- the maker's option icons ------------------------------------------------------------------------

    /** Draws an option tile's icon from the pack; false when the pack has none for it. */
    boolean icon(Canvas c, int prop, int index, Mii m, float cx, float cy, float size) {
        String name;
        Paint p = plain(paint);
        float k = size * 0.8f / 60f;
        switch (prop) {
            case Mii.FACE:
                name = "icon/face_" + index;
                break;
            case Mii.FEATURE:
                name = "icon/feature_" + index;
                break;
            case Mii.HAIR:
                name = "icon/hair_" + index;
                break;
            case Mii.BROW:
                if (index >= pack.browIcons) return noneMark(c, cx, cy, size);
                name = "icon/brow_" + index;
                tint(p, Mii.HAIR_COLORS[m.v[Mii.BROW_COLOR]]);
                k = size * 0.8f / 52f;
                break;
            case Mii.EYE:
                name = "icon/eye_" + index;
                keyed(p, EYE_INK, WHITE, Mii.EYE_COLORS[m.v[Mii.EYE_COLOR]], EYE_INK);
                k = size * 0.8f / 52f;
                break;
            case Mii.NOSE:
                name = "icon/nose_" + index;
                tint(p, 0xFF3A3434);
                k = size * 0.8f / 52f;
                break;
            case Mii.MOUTH: {
                int lip = Mii.LIP_COLORS[m.v[Mii.MOUTH_COLOR]];
                name = "icon/mouth_" + index;
                keyed(p, lip, MiiArt.shade(lip, -0.28f), WHITE, MOUTH_INK);
                k = size * 0.8f / 52f;
                break;
            }
            case Mii.GLASSES:
                name = "icon/glasses_" + index;
                break;
            case Mii.MUSTACHE:
                name = "icon/mustache_" + index;
                break;
            case Mii.BEARD:
                name = index == 0 ? "icon/mustache_0" : "icon/beard_" + (index - 1);
                break;
            case Mii.MOLE:
                name = index == 0 ? "icon/mustache_0" : "icon/mole_0";
                break;
            default:
                return false;
        }
        Bitmap b = pack.bitmap(name);
        if (b == null) return false;
        boolean flip = prop == Mii.HAIR && m.v[Mii.HAIR_FLIP] != 0;
        int save = c.save();
        c.translate(cx, cy);
        c.scale(flip ? -k : k, k);
        c.drawBitmap(b, -b.getWidth() / 2f, -b.getHeight() / 2f, p);
        c.restoreToCount(save);
        p.setColorFilter(null);
        return true;
    }

    private boolean noneMark(Canvas c, float cx, float cy, float size) {
        float r = size * 0.22f;
        line.setColor(0xFFB4B4B4);
        line.setStrokeWidth(r * 0.16f);
        c.drawCircle(cx, cy, r, line);
        c.drawLine(cx - r * 0.7f, cy + r * 0.7f, cx + r * 0.7f, cy - r * 0.7f, line);
        return true;
    }
}
