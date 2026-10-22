package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * An optional pack of original Mii parts: miipack.zip in the app's external files folder, copied there by hand
 * (adb push). It never ships with the app; without it the channel draws everything in code.
 *
 * Sprites are PNGs placed in head units (see MiiArt): pack.json gives the head-unit position of each one's
 * top-left pixel, and each set ("hi" for the maker, "lo" for the plaza) has its own pixels per unit.
 */
final class MiiPack {
    static final String FILE = "miipack.zip";
    private static final String TAG = "MiiPack";

    private static MiiPack instance;
    private static boolean tried;

    private final ZipFile zip;
    private final File soundDir;
    final float shadeMax;
    final float pxHi, pxLo;
    final float[] texBox = new float[4];
    final int gridW, gridH;
    final float[] faceTop, faceHw;
    final int[] noseMap;
    final float[] headBox = new float[4], backBox = new float[4];
    final float calEye, calBrow, calMouth, calFeature, calGlasses, calMustache;
    final float featureY, glassesY, mustacheY;
    /** For the parts cut from round icons: the icon's centre (x, y) and face diameter, in its texture pixels. */
    final float[] featureRing, glassesRing, mustacheRing;
    final int keyedUp;
    final int browIcons;
    final HashMap<Integer, Integer> counts = new HashMap<>();
    final JSONObject sounds;
    /** The channel menu tile's crowd: each face's sprite, and its centre x, y and width as fractions of the tile. */
    final ArrayList<String> crowdNames = new ArrayList<>();
    final ArrayList<float[]> crowdPlaces = new ArrayList<>();
    private final HashMap<String, float[]> origins = new HashMap<>();
    private final HashMap<String, float[]> grids = new HashMap<>();
    private final HashSet<String> missing = new HashSet<>();
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(40 << 20) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };

    /** The pack, loading it the first time; null when there is none or it cannot be read. */
    static synchronized MiiPack get(Context context) {
        if (!tried) {
            tried = true;
            File dir = context.getExternalFilesDir(null);
            File f = dir == null ? null : new File(dir, FILE);
            if (f != null && f.isFile()) {
                try {
                    instance = new MiiPack(f, new File(context.getCacheDir(), "miipack"));
                    Mii.usePack(instance.counts);
                } catch (IOException | JSONException | RuntimeException e) {
                    Log.w(TAG, "Mii pack unreadable, drawing in code", e);
                    instance = null;
                }
            }
        }
        return instance;
    }

    /** The pack if something already loaded it. */
    static synchronized MiiPack peek() {
        return instance;
    }

    private MiiPack(File file, File soundDir) throws IOException, JSONException {
        zip = new ZipFile(file);
        this.soundDir = soundDir;
        JSONObject j = new JSONObject(text("pack.json"));
        shadeMax = (float) j.getDouble("shadeMax");
        JSONObject px = j.getJSONObject("px");
        pxHi = (float) px.getDouble("hi");
        pxLo = (float) px.getDouble("lo");
        floats(j.getJSONArray("texBox"), texBox);
        JSONArray g = j.getJSONArray("grid");
        gridW = g.getInt(0);
        gridH = g.getInt(1);
        JSONArray faces = j.getJSONArray("faces");
        faceTop = new float[faces.length()];
        faceHw = new float[faces.length()];
        for (int i = 0; i < faces.length(); i++) {
            faceTop[i] = (float) faces.getJSONObject(i).getDouble("top");
            faceHw[i] = (float) faces.getJSONObject(i).getDouble("hw");
        }
        JSONArray nm = j.getJSONArray("noseMap");
        noseMap = new int[nm.length()];
        for (int i = 0; i < noseMap.length; i++) noseMap[i] = nm.getInt(i);
        JSONObject lo = j.getJSONObject("boxes").getJSONObject("lo");
        floats(lo.getJSONArray("head"), headBox);
        floats(lo.getJSONArray("back"), backBox);
        JSONObject cal = j.getJSONObject("cal");
        calEye = (float) cal.getDouble("eye");
        calBrow = (float) cal.getDouble("brow");
        calMouth = (float) cal.getDouble("mouth");
        calFeature = (float) cal.getDouble("feature");
        calGlasses = (float) cal.getDouble("glasses");
        calMustache = (float) cal.getDouble("mustache");
        featureY = (float) cal.getDouble("featureY");
        glassesY = (float) cal.getDouble("glassesY");
        mustacheY = (float) cal.getDouble("mustacheY");
        JSONObject parts = j.getJSONObject("parts");
        featureRing = ring(parts.getJSONObject("feature"));
        glassesRing = ring(parts.getJSONObject("glasses"));
        mustacheRing = ring(parts.getJSONObject("mustache"));
        keyedUp = parts.getJSONObject("eye").getInt("px");
        browIcons = j.getJSONObject("icons").getInt("brow");
        JSONObject sp = j.getJSONObject("sprites");
        for (Iterator<String> it = sp.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONArray a = sp.getJSONArray(k);
            origins.put(k, new float[] {(float) a.getDouble(0), (float) a.getDouble(1)});
        }
        JSONObject gr = j.getJSONObject("grids");
        for (Iterator<String> it = gr.keys(); it.hasNext(); ) {
            String face = it.next();
            JSONObject views = gr.getJSONObject(face);
            for (Iterator<String> vt = views.keys(); vt.hasNext(); ) {
                String v = vt.next();
                JSONArray a = views.getJSONArray(v);
                float[] f = new float[a.length()];
                for (int i = 0; i < f.length; i++) f[i] = (float) a.getDouble(i);
                grids.put(face + "_" + v, f);
            }
        }
        JSONObject c = j.getJSONObject("counts");
        counts.put(Mii.HAIR, c.getInt("hair"));
        counts.put(Mii.EYE, c.getInt("eye"));
        counts.put(Mii.BROW, c.getInt("brow"));
        counts.put(Mii.NOSE, c.getInt("nose"));
        counts.put(Mii.MOUTH, c.getInt("mouth"));
        counts.put(Mii.FACE, c.getInt("face"));
        counts.put(Mii.FEATURE, c.getInt("feature"));
        counts.put(Mii.GLASSES, c.getInt("glasses"));
        counts.put(Mii.MUSTACHE, c.getInt("mustache"));
        counts.put(Mii.BEARD, c.getInt("beard"));
        sounds = j.getJSONObject("sounds");
        JSONObject ui = j.optJSONObject("ui");
        JSONArray crowd = ui == null ? null : ui.optJSONArray("crowd");
        if (crowd != null) {
            for (int i = 0; i < crowd.length(); i++) {
                JSONArray a = crowd.getJSONArray(i);
                crowdNames.add(a.getString(0));
                crowdPlaces.add(new float[] {(float) a.getDouble(1), (float) a.getDouble(2), (float) a.getDouble(3)});
            }
        }
    }

    private static float[] ring(JSONObject o) throws JSONException {
        return new float[] {(float) o.getDouble("cx"), (float) o.getDouble("cy"), (float) o.getDouble("d"),
                (float) o.getDouble("px")};
    }

    private static void floats(JSONArray a, float[] out) throws JSONException {
        for (int i = 0; i < out.length; i++) out[i] = (float) a.getDouble(i);
    }

    private String text(String name) throws IOException {
        ZipEntry e = zip.getEntry(name);
        if (e == null) throw new IOException(name + " missing");
        try (InputStream in = zip.getInputStream(e)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Head-unit position of a sprite's top-left pixel, or null if the pack has no such sprite. */
    float[] origin(String name) {
        return origins.get(name);
    }

    boolean has(String name) {
        return origins.containsKey(name);
    }

    float px(String name) {
        return name.startsWith("hi/") ? pxHi : pxLo;
    }

    /** Where the face texture grid's vertices land for a turned view ("L" or "R"), in head units. */
    float[] grid(int face, String view) {
        return grids.get(face + "_" + view);
    }

    /** A sprite or texture by name (no extension); null if missing. Safe from any thread. */
    Bitmap bitmap(String name) {
        Bitmap b = cache.get(name);
        if (b != null) return b;
        synchronized (zip) {
            if (missing.contains(name)) return null;
            ZipEntry e = zip.getEntry(name + ".png");
            if (e == null) {
                missing.add(name);
                return null;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream in = zip.getInputStream(e)) {
                b = BitmapFactory.decodeStream(in, null, o);
            } catch (IOException ex) {
                Log.w(TAG, "cannot read " + name, ex);
            }
            if (b == null) {
                missing.add(name);
                return null;
            }
        }
        cache.put(name, b);
        return b;
    }

    /** A sound copied out of the zip so SoundPool can open it; null if missing. */
    File sound(String name) {
        String entry = "sound/" + name + ".wav";
        synchronized (zip) {
            ZipEntry e = zip.getEntry(entry);
            if (e == null) return null;
            File out = new File(soundDir, name + ".wav");
            if (out.isFile() && out.length() == e.getSize()) return out;
            if (!soundDir.isDirectory() && !soundDir.mkdirs()) return null;
            try (InputStream in = zip.getInputStream(e); OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            } catch (IOException ex) {
                Log.w(TAG, "cannot copy " + entry, ex);
                return null;
            }
            return out;
        }
    }
}
