package com.gathin.tvhome;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Random;
import java.util.UUID;

/**
 * One character for the Mii channel. Every part is an index into MiiArt's tables or a small offset, kept in
 * one int array so the editor can treat all of them alike and a character saves as a small JSON object.
 */
final class Mii {
    static final int MAX_NAME = 10;

    static final int FEMALE = 0, FAVORITE = 1, MONTH = 2, DAY = 3, COLOR = 4, HEIGHT = 5, WEIGHT = 6;
    static final int FACE = 7, SKIN = 8, FEATURE = 9;
    static final int HAIR = 10, HAIR_COLOR = 11, HAIR_FLIP = 12;
    static final int BROW = 13, BROW_COLOR = 14, BROW_Y = 15, BROW_SPACE = 16, BROW_SIZE = 17, BROW_ROT = 18;
    static final int EYE = 19, EYE_COLOR = 20, EYE_Y = 21, EYE_SPACE = 22, EYE_SIZE = 23, EYE_ROT = 24;
    static final int NOSE = 25, NOSE_Y = 26, NOSE_SIZE = 27;
    static final int MOUTH = 28, MOUTH_COLOR = 29, MOUTH_Y = 30, MOUTH_SIZE = 31;
    static final int GLASSES = 32, GLASSES_COLOR = 33, GLASSES_Y = 34, GLASSES_SIZE = 35;
    static final int MUSTACHE = 36, BEARD = 37, BEARD_COLOR = 38, MUSTACHE_Y = 39, MUSTACHE_SIZE = 40;
    static final int MOLE = 41, MOLE_X = 42, MOLE_Y = 43, MOLE_SIZE = 44;
    static final int COUNT = 45;

    static final int HAIR_STYLES = 48;
    static final int EYE_TYPES = 32;
    static final int BROW_TYPES = 24;
    static final int NOSE_TYPES = 12;
    static final int MOUTH_TYPES = 24;
    static final int FACE_TYPES = 8;
    static final int FEATURE_TYPES = 12;
    static final int GLASSES_TYPES = 9;
    static final int MUSTACHE_TYPES = 5;
    static final int BEARD_TYPES = 5;

    private static final String[] KEYS = {"female", "favorite", "month", "day", "color", "height", "weight",
            "face", "skin", "feature", "hair", "hairColor", "hairFlip",
            "brow", "browColor", "browY", "browSpace", "browSize", "browRot",
            "eye", "eyeColor", "eyeY", "eyeSpace", "eyeSize", "eyeRot",
            "nose", "noseY", "noseSize", "mouth", "mouthColor", "mouthY", "mouthSize",
            "glasses", "glassesColor", "glassesY", "glassesSize",
            "mustache", "beard", "beardColor", "mustacheY", "mustacheSize",
            "mole", "moleX", "moleY", "moleSize"};

    /** True once a MiiPack set the original's part counts. */
    static boolean wii;

    static final int[] MIN = new int[COUNT];
    static final int[] MAX = new int[COUNT];

    static {
        range(FEMALE, 0, 1);
        range(FAVORITE, 0, 1);
        range(MONTH, 0, 12);
        range(DAY, 0, 31);
        range(COLOR, 0, 11);
        range(HEIGHT, 0, 127);
        range(WEIGHT, 0, 127);
        range(FACE, 0, FACE_TYPES - 1);
        range(SKIN, 0, 5);
        range(FEATURE, 0, FEATURE_TYPES - 1);
        range(HAIR, 0, HAIR_STYLES - 1);
        range(HAIR_COLOR, 0, 7);
        range(HAIR_FLIP, 0, 1);
        range(BROW, 0, BROW_TYPES - 1);
        range(BROW_COLOR, 0, 7);
        range(BROW_Y, -5, 5);
        range(BROW_SPACE, -5, 5);
        range(BROW_SIZE, -4, 4);
        range(BROW_ROT, -5, 5);
        range(EYE, 0, EYE_TYPES - 1);
        range(EYE_COLOR, 0, 5);
        range(EYE_Y, -6, 6);
        range(EYE_SPACE, -5, 5);
        range(EYE_SIZE, -4, 4);
        range(EYE_ROT, -5, 5);
        range(NOSE, 0, NOSE_TYPES - 1);
        range(NOSE_Y, -5, 5);
        range(NOSE_SIZE, -4, 4);
        range(MOUTH, 0, MOUTH_TYPES - 1);
        range(MOUTH_COLOR, 0, 2);
        range(MOUTH_Y, -5, 5);
        range(MOUTH_SIZE, -4, 4);
        range(GLASSES, 0, GLASSES_TYPES - 1);
        range(GLASSES_COLOR, 0, 5);
        range(GLASSES_Y, -5, 5);
        range(GLASSES_SIZE, -4, 4);
        range(MUSTACHE, 0, MUSTACHE_TYPES - 1);
        range(BEARD, 0, BEARD_TYPES - 1);
        range(BEARD_COLOR, 0, 7);
        range(MUSTACHE_Y, -4, 4);
        range(MUSTACHE_SIZE, -4, 4);
        range(MOLE, 0, 1);
        range(MOLE_X, -8, 8);
        range(MOLE_Y, -8, 8);
        range(MOLE_SIZE, -3, 3);
    }

    /** Switches the part counts to the pack's (the original's), keyed by property. */
    static void usePack(java.util.Map<Integer, Integer> counts) {
        for (java.util.Map.Entry<Integer, Integer> e : counts.entrySet()) range(e.getKey(), 0, e.getValue() - 1);
        wii = true;
    }

    private static int count(int prop) {
        return MAX[prop] + 1;
    }

    private static void range(int prop, int lo, int hi) {
        MIN[prop] = lo;
        MAX[prop] = hi;
    }

    /** Shirt colours, in the order the editor shows them. */
    static final int[] FAVORITE_COLORS = {0xFFD21E14, 0xFFFF6E19, 0xFFFFD820, 0xFF78D220, 0xFF007830, 0xFF0A48B4,
            0xFF3CAADE, 0xFFF55A7D, 0xFF7328AD, 0xFF483818, 0xFFE0E0E0, 0xFF181814};
    static final int[] SKIN_COLORS = {0xFFFEE8C9, 0xFFFFC976, 0xFFDA8846, 0xFFFFBDB2, 0xFFB0591A, 0xFF6D481C};
    static final int[] HAIR_COLORS = {0xFF1F1B1A, 0xFF3B2016, 0xFF5E2B17, 0xFF8A3A1C, 0xFF7C7874, 0xFF4E3A22,
            0xFF9C6A34, 0xFFD9B266};
    static final int[] EYE_COLORS = {0xFF161616, 0xFF6E7378, 0xFF6A3E1E, 0xFF7A7432, 0xFF2F5FC8, 0xFF2C8A48};
    static final int[] LIP_COLORS = {0xFFD9583C, 0xFFE0303C, 0xFFF0879A};
    static final int[] GLASSES_COLORS = {0xFF2A2A2A, 0xFF7A5020, 0xFFB02828, 0xFF2850A8, 0xFFC89A20, 0xFF8C8C8C};

    String id;
    String name = "";
    String creator = "";
    final int[] v = new int[COUNT];

    Mii() {
        id = UUID.randomUUID().toString();
        v[HEIGHT] = 64;
        v[WEIGHT] = 64;
        v[HAIR] = 1;
        v[HAIR_COLOR] = 1;
        v[BROW_COLOR] = 1;
    }

    boolean female() {
        return v[FEMALE] != 0;
    }

    boolean favorite() {
        return v[FAVORITE] != 0;
    }

    int shirt() {
        return FAVORITE_COLORS[v[COLOR]];
    }

    void set(int prop, int value) {
        v[prop] = Math.max(MIN[prop], Math.min(MAX[prop], value));
    }

    /** The name the plaza shows, falling back to a placeholder for one not named yet. */
    String label() {
        return name.isEmpty() ? "?" : name;
    }

    /** A copy with the same id, for the editor to change without touching the original until saved. */
    Mii copy() {
        Mii m = new Mii();
        m.id = id;
        m.name = name;
        m.creator = creator;
        System.arraycopy(v, 0, m.v, 0, COUNT);
        return m;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("creator", creator);
        for (int i = 0; i < COUNT; i++) o.put(KEYS[i], v[i]);
        return o;
    }

    static Mii fromJson(JSONObject o) {
        Mii m = new Mii();
        m.id = o.optString("id", m.id);
        m.name = clip(o.optString("name", ""));
        m.creator = clip(o.optString("creator", ""));
        for (int i = 0; i < COUNT; i++) {
            if (o.has(KEYS[i])) m.set(i, o.optInt(KEYS[i], m.v[i]));
        }
        return m;
    }

    static String clip(String s) {
        if (s == null) return "";
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME) : s;
    }

    /** The plain starting character the editor opens with for "start from scratch". */
    static Mii blank(boolean female) {
        Mii m = new Mii();
        m.v[FEMALE] = female ? 1 : 0;
        if (female && wii) {
            m.v[HAIR] = 37;
            m.v[COLOR] = 7;
        } else if (female) {
            m.v[HAIR] = 23;
            m.v[BROW] = 14;
            m.v[EYE] = 4;
            m.v[MOUTH] = 8;
            m.v[COLOR] = 7;
        }
        return m;
    }

    private static final String[] BOYS = {"Tomi", "Juan", "Leo", "Mateo", "Santi", "Nico", "Lucas", "Bruno",
            "Facu", "Benja", "Joaco", "Fede", "Gonza", "Lauti", "Pipo", "Tito", "Rami", "Dani", "Emi", "Teo"};
    private static final String[] GIRLS = {"Sofi", "Lu", "Mica", "Valen", "Cami", "Agus", "Flor", "Juli", "Mia",
            "Emma", "Pili", "Abril", "Delfi", "Ani", "Lola", "Zoe", "Martu", "Caro", "Vicky", "Ine"};
    private static final int[] BOY_HAIR = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 45, 47};
    private static final int[] GIRL_HAIR = {20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37,
            38, 39, 40, 43, 44, 46, 11, 14};
    private static final int[] GIRL_BROWS = {1, 6, 7, 12, 13, 14, 15, 23, 4, 0};
    private static final int[] GIRL_EYES = {4, 5, 15, 17, 23, 26, 0, 1, 13, 20, 27, 29};
    private static final int[] GIRL_MOUTHS = {4, 8, 9, 18, 20, 1, 5, 10, 22, 12};
    private static final float[] SKIN_WEIGHTS = {0.40f, 0.14f, 0.12f, 0.16f, 0.10f, 0.08f};
    private static final float[] HAIR_WEIGHTS = {0.26f, 0.24f, 0.14f, 0.06f, 0.06f, 0.08f, 0.08f, 0.08f};

    /** A random character, for the plaza's first guests and the look-alike picker. */
    static Mii random(Random r, boolean female) {
        Mii m = blank(female);
        m.name = female ? GIRLS[r.nextInt(GIRLS.length)] : BOYS[r.nextInt(BOYS.length)];
        m.v[COLOR] = r.nextInt(12);
        m.v[HEIGHT] = 30 + r.nextInt(70);
        m.v[WEIGHT] = 30 + r.nextInt(70);
        m.v[FACE] = r.nextInt(count(FACE));
        m.v[SKIN] = weighted(r, SKIN_WEIGHTS);
        if (wii) {
            // The original lists the short styles first and the long ones from the fourth page on.
            m.v[HAIR] = female ? 36 + r.nextInt(36) : r.nextInt(36);
        } else {
            m.v[HAIR] = female ? GIRL_HAIR[r.nextInt(GIRL_HAIR.length)] : BOY_HAIR[r.nextInt(BOY_HAIR.length)];
        }
        m.v[HAIR_COLOR] = weighted(r, HAIR_WEIGHTS);
        m.v[HAIR_FLIP] = r.nextInt(2);
        if (wii) m.v[BROW] = r.nextInt(MAX[BROW]);
        else m.v[BROW] = female ? GIRL_BROWS[r.nextInt(GIRL_BROWS.length)] : r.nextInt(BROW_TYPES);
        m.v[BROW_COLOR] = m.v[HAIR_COLOR];
        m.v[BROW_Y] = r.nextInt(5) - 2;
        if (wii) m.v[EYE] = r.nextInt(count(EYE));
        else m.v[EYE] = female ? GIRL_EYES[r.nextInt(GIRL_EYES.length)] : r.nextInt(EYE_TYPES);
        m.v[EYE_COLOR] = r.nextFloat() < 0.6f ? 0 : r.nextInt(6);
        m.v[EYE_Y] = r.nextInt(5) - 2;
        m.v[EYE_SPACE] = r.nextInt(5) - 2;
        m.v[EYE_SIZE] = r.nextInt(3) - 1;
        m.v[NOSE] = r.nextInt(count(NOSE));
        m.v[NOSE_SIZE] = r.nextInt(3) - 1;
        if (wii) m.v[MOUTH] = r.nextInt(count(MOUTH));
        else m.v[MOUTH] = female ? GIRL_MOUTHS[r.nextInt(GIRL_MOUTHS.length)] : r.nextInt(MOUTH_TYPES);
        m.v[MOUTH_COLOR] = female ? r.nextInt(3) : 0;
        m.v[MOUTH_Y] = r.nextInt(3) - 1;
        if (r.nextFloat() < 0.16f) {
            m.v[GLASSES] = 1 + r.nextInt(MAX[GLASSES]);
            m.v[GLASSES_COLOR] = r.nextInt(6);
        }
        if (!female && r.nextFloat() < 0.16f) m.v[MUSTACHE] = 1 + r.nextInt(MAX[MUSTACHE]);
        if (!female && r.nextFloat() < 0.12f) m.v[BEARD] = 1 + r.nextInt(MAX[BEARD]);
        m.v[BEARD_COLOR] = m.v[HAIR_COLOR];
        if (r.nextFloat() < 0.08f) {
            m.v[MOLE] = 1;
            m.v[MOLE_X] = r.nextInt(9) - 4;
            m.v[MOLE_Y] = r.nextInt(9) - 4;
        }
        if (r.nextFloat() < (female ? 0.35f : 0.12f)) {
            m.v[FEATURE] = female ? (r.nextBoolean() ? 1 : 2) : 1 + r.nextInt(MAX[FEATURE]);
        }
        m.v[MONTH] = 1 + r.nextInt(12);
        m.v[DAY] = 1 + r.nextInt(28);
        return m;
    }

    private static int weighted(Random r, float[] w) {
        float x = r.nextFloat(), acc = 0f;
        for (int i = 0; i < w.length; i++) {
            acc += w[i];
            if (x < acc) return i;
        }
        return w.length - 1;
    }
}
