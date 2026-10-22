package com.gathin.tvhome;

/**
 * The plaza: a white floor with grey squares laid in rings round a checkerboard, seen from a camera tilted down over it. The
 * camera maps ground points (X right, Z away, in head units) to 1920 x 1080 reference pixels and back.
 * Plain Java so the floor can be computed off the UI thread.
 */
final class MiiFloor {
    private MiiFloor() {}

    private static final double PITCH = Math.toRadians(50);
    /** A long lens, as in the original: squares at the top of the screen are barely smaller than at the bottom. */
    private static final double F = 3500;
    private static final double H = 50.5;
    private static final double CX = 960, CY = 540;
    /** Screen row the rings' centre sits on. */
    private static final double ORIGIN_Y = 640;
    private static final double CAM_Z = -H / Math.tan(PITCH + Math.atan((ORIGIN_Y - CY) / F));
    /** Width of one ring of squares, in head units. */
    private static final double RING = 3.0;
    /** The middle is a checkerboard turned 45 degrees, as wide as this many rings, with squares CHECK units a side. */
    private static final int CENTRE_RINGS = 2;
    private static final double CHECK = 1.55;
    /** The squares are paler under the middle of the plaza: their shade runs from LIGHT within NEAR to DARK past FAR. */
    private static final double NEAR = 4, FAR = 12, LIGHT = 0.74, DARK = 1.02;
    /** Per-channel scale of the tile's shade, which is a touch green beside the original's warm grey. */
    private static final double[] GAIN = {0.85, 0.93, 1.0};

    /** Ground point to reference pixels: out = {x, y, px per unit}. */
    static void project(float x, float z, float[] out) {
        double d = Math.max(0.5, z - CAM_Z);
        double a = Math.atan2(H, d);
        double zc = Math.sqrt(H * H + d * d) * Math.cos(a - PITCH);
        out[0] = (float) (CX + F * x / zc);
        out[1] = (float) (CY + F * Math.tan(a - PITCH));
        out[2] = (float) (F / zc);
    }

    /** Reference pixel to the ground point under it: out = {X, Z, px per unit}; false above the horizon. */
    static boolean ground(float sx, float sy, float[] out) {
        double a = PITCH + Math.atan((sy - CY) / F);
        if (a <= 0.02) return false;
        double d = H / Math.tan(a);
        double zc = (H / Math.sin(a)) * Math.cos(a - PITCH);
        out[0] = (float) ((sx - CX) * zc / F);
        out[1] = (float) (CAM_Z + d);
        out[2] = (float) (F / zc);
        return true;
    }

    private static int hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static float unit(int h) {
        return (h & 0xFFFF) / 65535f;
    }

    private static final int WHITE = 0xFCFCFA;
    private static final int[] TILES = {0xE4E4DC, 0xE4E4DC, 0xE4E4DC, 0xE8E6DE, 0xE8E6DE, 0xECE8DC, 0xDFE0DA, 0xE6E5E0};

    /** The floor tile's side and the pitch of its 8 x 8 squares, in texels. */
    private static final int TILE = 256, CELL = 32;

    /**
     * The floor as w x h pixels of a screen w wide, its squares cut from a pack's 256 x 256 floor tile: each
     * place in a ring takes one of the tile's 64 squares, so the rings keep the original's squares and shading.
     */
    static int[] render(int w, int h, int[] tile) {
        if (tile == null || tile.length != TILE * TILE) return render(w, h);
        float[][] mips = mips(tile);
        double s = w / 1920.0;
        double texPerUnit = CELL / RING;
        int[] out = new int[w * h];
        float[] rgb = new float[3];
        double[] cov = new double[1];
        for (int y = 0; y < h; y++) {
            double ry = (y + 0.5) / s;
            double a = PITCH + Math.atan((ry - CY) / F);
            int row = y * w;
            if (a <= 0.02) {
                for (int x = 0; x < w; x++) out[row + x] = 0xFFFFFFFF;
                continue;
            }
            double d = H / Math.tan(a);
            double zc = (H / Math.sin(a)) * Math.cos(a - PITCH);
            double z = CAM_Z + d;
            double pxPerUnit = F / zc * s;
            double fw = Math.sqrt((1.0 / pxPerUnit) * (1.0 / (pxPerUnit * Math.sin(a))));
            double level = Math.log(Math.max(1e-6, fw * texPerUnit)) / Math.log(2);
            for (int x = 0; x < w; x++) {
                double gx = ((x + 0.5) / s - CX) * zc / F;
                double r = Math.hypot(gx, z);
                int k = (int) (r / RING);
                if (k < CENTRE_RINGS) {
                    // Each checkerboard square is flat: the middle of one of the tile's squares, averaged.
                    int hh = checker(gx, z, fw, cov);
                    trilinear(mips, (hh & 7) * CELL + CELL / 2.0, ((hh >>> 3) & 7) * CELL + CELL / 2.0, 3, rgb);
                    for (int ch = 0; ch < 3; ch++) rgb[ch] = (float) (255 + (rgb[ch] - 255) * cov[0]);
                    out[row + x] = shade(rgb, r);
                    continue;
                }
                int n = (int) Math.round(2 * Math.PI * (k + 0.5));
                double off = unit(hash(k, 91)) * 2 * Math.PI / n;
                double th = Math.atan2(z, gx) + off;
                if (th < 0) th += 2 * Math.PI;
                int j = (int) (th / (2 * Math.PI) * n) % n;
                int hh = hash(k, j);
                double tc = (j + 0.5) * 2 * Math.PI / n - off;
                double rc = (k + 0.5) * RING;
                double dx = gx - rc * Math.cos(tc), dz = z - rc * Math.sin(tc);
                double cr = Math.cos(tc), sr = Math.sin(tc);
                double du = (-dx * sr + dz * cr) * texPerUnit, dv = (dx * cr + dz * sr) * texPerUnit;
                if (Math.abs(du) >= CELL / 2.0 || Math.abs(dv) >= CELL / 2.0) {
                    out[row + x] = 0xFFFFFFFF;
                    continue;
                }
                double tx = (hh & 7) * CELL + CELL / 2.0 + du, ty = ((hh >>> 3) & 7) * CELL + CELL / 2.0 + dv;
                trilinear(mips, tx, ty, level, rgb);
                out[row + x] = shade(rgb, r);
            }
        }
        return out;
    }

    /**
     * The middle's checkerboard at ground point (gx, z): returns a number for the square there and puts in
     * cov[0] how much of a pixel fw units across is grey.
     */
    private static int checker(double gx, double z, double fw, double[] cov) {
        double p = (gx + z) / (Math.sqrt(2) * CHECK), q = (z - gx) / (Math.sqrt(2) * CHECK);
        double ip = Math.floor(p), iq = Math.floor(q);
        double fp = p - ip, fq = q - iq;
        double edge = Math.min(Math.min(fp, 1 - fp), Math.min(fq, 1 - fq)) * CHECK;
        boolean grey = (((long) ip + (long) iq) & 1) == 0;
        double c = 0.5 + (grey ? edge : -edge) / fw;
        cov[0] = c < 0 ? 0 : c > 1 ? 1 : c;
        return hash((int) ip, (int) iq + 7);
    }

    /** An RGB colour of the floor at r units from the middle, with the original's tint and lighting, as ARGB. */
    private static int shade(float[] rgb, double r) {
        double t = (r - NEAR) / (FAR - NEAR);
        t = t < 0 ? 0 : t > 1 ? 1 : t;
        double f = LIGHT + (DARK - LIGHT) * t * t * (3 - 2 * t);
        int c = 0xFF000000;
        for (int ch = 0; ch < 3; ch++) {
            long v = Math.round(255 - (255 - rgb[ch]) * GAIN[ch] * f);
            c |= (int) Math.max(0, Math.min(255, v)) << (16 - 8 * ch);
        }
        return c;
    }

    /** The tile and its halvings down to 16 x 16, as RGB floats. */
    private static float[][] mips(int[] tile) {
        float[][] m = new float[5][];
        float[] l0 = new float[TILE * TILE * 3];
        for (int i = 0; i < tile.length; i++) {
            l0[i * 3] = (tile[i] >> 16) & 255;
            l0[i * 3 + 1] = (tile[i] >> 8) & 255;
            l0[i * 3 + 2] = tile[i] & 255;
        }
        m[0] = l0;
        int size = TILE;
        for (int l = 1; l < m.length; l++) {
            int ns = size / 2;
            float[] src = m[l - 1], dst = new float[ns * ns * 3];
            for (int y = 0; y < ns; y++) {
                for (int x = 0; x < ns; x++) {
                    for (int ch = 0; ch < 3; ch++) {
                        int a = ((2 * y) * size + 2 * x) * 3 + ch, b = ((2 * y + 1) * size + 2 * x) * 3 + ch;
                        dst[(y * ns + x) * 3 + ch] = (src[a] + src[a + 3] + src[b] + src[b + 3]) * 0.25f;
                    }
                }
            }
            m[l] = dst;
            size = ns;
        }
        return m;
    }

    private static void trilinear(float[][] mips, double tx, double ty, double level, float[] out) {
        if (level <= 0) {
            bilinear(mips[0], TILE, tx, ty, out, 1f, true);
            return;
        }
        int l = (int) level;
        if (l >= mips.length - 1) {
            int size = TILE >> (mips.length - 1);
            double f = 1.0 / (1 << (mips.length - 1));
            bilinear(mips[mips.length - 1], size, tx * f, ty * f, out, 1f, true);
            return;
        }
        float t = (float) (level - l);
        double f0 = 1.0 / (1 << l), f1 = f0 / 2;
        bilinear(mips[l], TILE >> l, tx * f0, ty * f0, out, 1f - t, true);
        bilinear(mips[l + 1], TILE >> (l + 1), tx * f1, ty * f1, out, t, false);
    }

    /** Adds weight times the wrapped bilinear sample at texel (x, y) of a size x size level to out. */
    private static void bilinear(float[] px, int size, double x, double y, float[] out, float weight, boolean clear) {
        if (clear) out[0] = out[1] = out[2] = 0f;
        x -= 0.5;
        y -= 0.5;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = (float) (x - x0), fy = (float) (y - y0);
        int m = size - 1;
        int xa = x0 & m, xb = (x0 + 1) & m, ya = y0 & m, yb = (y0 + 1) & m;
        for (int ch = 0; ch < 3; ch++) {
            float p00 = px[(ya * size + xa) * 3 + ch], p10 = px[(ya * size + xb) * 3 + ch];
            float p01 = px[(yb * size + xa) * 3 + ch], p11 = px[(yb * size + xb) * 3 + ch];
            float top = p00 + (p10 - p00) * fx, bottom = p01 + (p11 - p01) * fx;
            out[ch] += weight * (top + (bottom - top) * fy);
        }
    }

    /** The floor as w x h pixels of a screen w wide (any size; the camera is in reference pixels). */
    static int[] render(int w, int h) {
        double s = w / 1920.0;
        int[] out = new int[w * h];
        float[] rgb = new float[3];
        double[] one = new double[1];
        for (int y = 0; y < h; y++) {
            double ry = (y + 0.5) / s;
            double a = PITCH + Math.atan((ry - CY) / F);
            int row = y * w;
            if (a <= 0.02) {
                for (int x = 0; x < w; x++) out[row + x] = 0xFF000000 | WHITE;
                continue;
            }
            double d = H / Math.tan(a);
            double zc = (H / Math.sin(a)) * Math.cos(a - PITCH);
            double z = CAM_Z + d;
            double pxPerUnit = F / zc * s;
            // Ground covered by one pixel: across, and stretched along Z by the tilt.
            double fw = Math.sqrt((1.0 / pxPerUnit) * (1.0 / (pxPerUnit * Math.sin(a))));
            double soft = Math.max(fw * 1.1, 0.03);
            for (int x = 0; x < w; x++) {
                double gx = ((x + 0.5) / s - CX) * zc / F;
                double r = Math.hypot(gx, z);
                int k = (int) (r / RING);
                double cov;
                int hh;
                if (k < CENTRE_RINGS) {
                    hh = checker(gx, z, fw, one);
                    cov = one[0];
                } else {
                    int n = (int) Math.round(2 * Math.PI * (k + 0.5));
                    double off = unit(hash(k, 91)) * 2 * Math.PI / n;
                    double th = Math.atan2(z, gx) + off;
                    if (th < 0) th += 2 * Math.PI;
                    int j = (int) (th / (2 * Math.PI) * n) % n;
                    hh = hash(k, j);
                    double tc = (j + 0.5) * 2 * Math.PI / n - off;
                    double rc = (k + 0.5) * RING;
                    double dx = gx - rc * Math.cos(tc), dz = z - rc * Math.sin(tc);
                    double rot = tc + (unit(hh >>> 8) - 0.5) * 0.36;
                    double cr = Math.cos(rot), sr = Math.sin(rot);
                    double u = dx * cr + dz * sr, v = -dx * sr + dz * cr;
                    double half = RING * 0.38 * (0.95 + 0.1 * unit(hh >>> 4));
                    cov = 0.5 - (Math.max(Math.abs(u), Math.abs(v)) - half) / soft;
                }
                cov = cov < 0 ? 0 : cov > 1 ? 1 : cov;
                int tile = TILES[(hh >>> 20) & 7];
                for (int ch = 0; ch < 3; ch++) {
                    int t = (tile >> (16 - 8 * ch)) & 255, wh = (WHITE >> (16 - 8 * ch)) & 255;
                    rgb[ch] = (float) (wh + (t - wh) * cov);
                }
                out[row + x] = shade(rgb, r);
            }
        }
        return out;
    }
}
