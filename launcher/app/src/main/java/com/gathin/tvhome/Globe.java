package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The Forecast Channel's globe: NASA's Blue Marble (public domain, downloaded once and kept) wrapped on a
 * sphere and drawn in software at half the screen's resolution, then scaled up.
 *
 * Fast enough for a 1.7 GHz stick because the sphere is precomputed: for a given size, position and tilt,
 * a table holds each pixel's texture row, its longitude offset and its light. Spinning east or west only
 * shifts a column index; only zoom and tilt rebuild the table.
 */
final class Globe {
    /** Render size: half the screen (960 x 540), or a third (640 x 360) on 1 GB devices. */
    final int W, H;
    static final String TEXTURE_URL =
            "https://eoimages.gsfc.nasa.gov/images/imagerecords/57000/57752/land_shallow_topo_2048.jpg";

    private int[] tex;
    private int tw, th;
    private final int[] out;
    private final short[] row;
    private final short[] col;
    private final byte[] light;
    private float tCx = -1, tCy, tR;
    private double tTilt = Double.NaN;
    final Bitmap bitmap;

    Globe(int w, int h) {
        W = w;
        H = h;
        out = new int[w * h];
        row = new short[w * h];
        col = new short[w * h];
        light = new byte[w * h];
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    }

    boolean ready() {
        return tex != null;
    }

    static File file(Context c) {
        return new File(c.getFilesDir(), "earth_bluemarble_2048.jpg");
    }

    /** Downloads the texture the first time; true when it is on disk. Blocking: call off the UI thread. */
    static boolean fetch(Context c) {
        File f = file(c);
        if (f.length() > 50_000) return true;
        File tmp = new File(c.getFilesDir(), "earth.tmp");
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(TEXTURE_URL).openConnection();
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(60_000);
            conn.setRequestProperty("User-Agent", "wiiOS");
            try (InputStream in = conn.getInputStream(); OutputStream o = new FileOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            } finally {
                conn.disconnect();
            }
            return tmp.renameTo(f);
        } catch (IOException e) {
            tmp.delete();
            return false;
        }
    }

    /** Decodes the texture (half size on 1 GB devices) and brightens the deep navy ocean to the console's blue. */
    boolean load(Context c, boolean lowMemory) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = lowMemory ? 2 : 1;
        Bitmap b = BitmapFactory.decodeFile(file(c).getPath(), o);
        if (b == null) return false;
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        b.recycle();
        for (int i = 0; i < px.length; i++) {
            int p = px[i], r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, bl = p & 0xFF;
            if (bl > r + 25 && bl > g + 25 && r < 70 && g < 90) {
                // Ocean: keep its texture, lift it from navy to a bright royal blue.
                int nr = 20 + r, ng = 70 + g * 2, nb = Math.min(255, 140 + bl);
                px[i] = 0xFF000000 | (nr << 16) | (Math.min(255, ng) << 8) | nb;
            }
        }
        tw = w;
        th = h;
        tex = px;
        tCx = -1;
        return true;
    }

    /**
     * Renders the globe centred at (cx, cy) with radius r, all in render pixels (W x H), viewed from above
     * latitude `tilt` (degrees) and longitude `lon`. Pixels off the globe are transparent.
     */
    Bitmap render(float cx, float cy, float r, double tilt, double lon) {
        if (cx != tCx || cy != tCy || r != tR || tilt != tTilt) buildTable(cx, cy, r, tilt);
        int shift = (int) Math.round((lon + 180.0) / 360.0 * tw);
        for (int i = 0; i < out.length; i++) {
            int rw = row[i];
            if (rw < 0) {
                out[i] = 0;
                continue;
            }
            int c = (col[i] + shift) % tw;
            if (c < 0) c += tw;
            int p = tex[rw * tw + c];
            int l = light[i] & 0xFF;
            int R = Math.min(255, ((p >> 16) & 0xFF) * l >> 7);
            int G = Math.min(255, ((p >> 8) & 0xFF) * l >> 7);
            int B = Math.min(255, (p & 0xFF) * l >> 7);
            out[i] = 0xFF000000 | (R << 16) | (G << 8) | B;
        }
        bitmap.setPixels(out, 0, W, 0, 0, W, H);
        return bitmap;
    }

    private void buildTable(float cx, float cy, float r, double tiltDeg) {
        tCx = cx;
        tCy = cy;
        tR = r;
        tTilt = tiltDeg;
        double t = Math.toRadians(tiltDeg), ct = Math.cos(t), st = Math.sin(t);
        // Light from the upper left, toward the viewer.
        double lx = -0.45, ly = 0.5, lz = 0.74;
        for (int j = 0; j < H; j++) {
            double y = (cy - (j + 0.5)) / r;
            for (int i = 0; i < W; i++) {
                int k = j * W + i;
                double x = (i + 0.5 - cx) / r, d = x * x + y * y;
                if (d >= 1.0) {
                    row[k] = -1;
                    continue;
                }
                double z = Math.sqrt(1.0 - d);
                double lat = Math.asin(y * ct + z * st);
                double lonRel = Math.atan2(x, z * ct - y * st);
                row[k] = (short) Math.min(th - 1, Math.max(0, (int) ((0.5 - lat / Math.PI) * th)));
                col[k] = (short) Math.round(lonRel / (2 * Math.PI) * tw);
                double lambert = Math.max(0, x * lx + y * ly + z * lz);
                // 128 = unchanged; darker toward the night side and the rim, a little brighter facing the light.
                light[k] = (byte) Math.min(255, (int) (128 * (0.38 + 0.82 * lambert) * (0.7 + 0.3 * z)));
            }
        }
    }

    /**
     * Where a place appears: {x, y} in the same pixels as cx, cy, r and z (toward the viewer, > 0 when on the
     * visible side), for the same view as render().
     */
    static float[] project(double lat, double lon, float cx, float cy, float r, double tilt, double centerLon) {
        double la = Math.toRadians(lat), dl = Math.toRadians(lon - centerLon), t = Math.toRadians(tilt);
        double X = Math.cos(la) * Math.sin(dl), Y = Math.sin(la), Z = Math.cos(la) * Math.cos(dl);
        double y = Y * Math.cos(t) - Z * Math.sin(t);
        double z = Y * Math.sin(t) + Z * Math.cos(t);
        return new float[] {(float) (cx + X * r), (float) (cy - y * r), (float) z};
    }
}
