package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Who's watching: up to six profiles, each with a name, a Mii from the Mii Channel and its own preferences (theme,
 * accent, channel order, hidden channels, recently opened). The list lives in the menu's main preferences;
 * profile 0 keeps its settings there too, so everything set up before profiles existed becomes the first
 * profile's. The others each get a preferences file of their own.
 */
final class Profiles {
    private Profiles() {}

    static final int MAX = 6;
    private static final String KEY_LIST = "profiles", KEY_CURRENT = "profile_current";
    private static final String KEY_NAME = "profile_name.", KEY_MII = "profile_mii.";
    /** Show "Who's watching?" when the TV is switched on (only with more than one profile). */
    static final String KEY_ASK = "profile_ask";

    static SharedPreferences main(Context c) {
        return c.getSharedPreferences(WiiMenuActivity.PREFS, Context.MODE_PRIVATE);
    }

    static List<Integer> ids(Context c) {
        List<Integer> out = new ArrayList<>();
        for (String s : ChannelOrder.split(main(c).getString(KEY_LIST, "0"))) {
            try {
                out.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
                // skip a damaged entry
            }
        }
        if (out.isEmpty()) out.add(0);
        return out;
    }

    static int current(Context c) {
        int id = main(c).getInt(KEY_CURRENT, 0);
        return ids(c).contains(id) ? id : ids(c).get(0);
    }

    static void setCurrent(Context c, int id) {
        main(c).edit().putInt(KEY_CURRENT, id).apply();
    }

    /** A profile's own preferences; profile 0's are the menu's main ones. */
    static SharedPreferences prefs(Context c, int id) {
        return id == 0 ? main(c) : c.getSharedPreferences(WiiMenuActivity.PREFS + "_p" + id, Context.MODE_PRIVATE);
    }

    static SharedPreferences currentPrefs(Context c) {
        return prefs(c, current(c));
    }

    static String name(Context c, int id) {
        String n = main(c).getString(KEY_NAME + id, null);
        return n == null || n.trim().isEmpty() ? c.getString(R.string.prof_default_name, id + 1) : n;
    }

    /**
     * The profile's Mii from the Mii Channel. A profile without one (or whose Mii was deleted) gets one of the
     * plaza's Miis, a different one for each profile, and keeps it.
     */
    static Mii mii(Context c, int id, List<Mii> miis) {
        String want = main(c).getString(KEY_MII + id, null);
        if (want != null) {
            for (Mii m : miis) if (m.id.equals(want)) return m;
        }
        if (miis.isEmpty()) return Mii.random(new java.util.Random(id * 7919L + 11), id % 2 == 1);
        Mii m = miis.get(id % miis.size());
        main(c).edit().putString(KEY_MII + id, m.id).apply();
        return m;
    }

    static void save(Context c, int id, String name, String miiId) {
        main(c).edit().putString(KEY_NAME + id, name).putString(KEY_MII + id, miiId).apply();
    }

    /** The id a new profile would get, or -1 when there are already MAX. */
    static int nextId(Context c) {
        List<Integer> ids = ids(c);
        if (ids.size() >= MAX) return -1;
        int id = 0;
        while (ids.contains(id)) id++;
        return id;
    }

    static void add(Context c, int id, String name, String miiId) {
        List<Integer> ids = ids(c);
        if (!ids.contains(id)) ids.add(id);
        save(c, id, name, miiId);
        main(c).edit().putString(KEY_LIST, join(ids)).apply();
    }

    /** Removes a profile and its settings. The first profile can't be removed. */
    static void remove(Context c, int id) {
        if (id == 0) return;
        List<Integer> ids = ids(c);
        ids.remove(Integer.valueOf(id));
        prefs(c, id).edit().clear().apply();
        main(c).edit().putString(KEY_LIST, join(ids)).remove(KEY_NAME + id).remove(KEY_MII + id).apply();
        if (main(c).getInt(KEY_CURRENT, 0) == id) setCurrent(c, 0);
    }

    static boolean ask(Context c) {
        return ids(c).size() > 1 && main(c).getBoolean(KEY_ASK, true);
    }

    private static String join(List<Integer> ids) {
        List<String> s = new ArrayList<>();
        for (int id : ids) s.add(String.valueOf(id));
        return ChannelOrder.join(s);
    }
}
