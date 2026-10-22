package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The plaza's characters, kept as one JSON array in the app's private preferences. */
final class MiiStore {
    static final int MAX = 100;
    private static final String PREFS = "mii_plaza";

    private final SharedPreferences prefs;

    MiiStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<Mii> load() {
        List<Mii> out = new ArrayList<>();
        String raw = prefs.getString("miis", null);
        if (raw == null) return out;
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length() && out.size() < MAX; i++) {
                if (a.optJSONObject(i) != null) out.add(Mii.fromJson(a.getJSONObject(i)));
            }
        } catch (JSONException e) {
            // A damaged list starts the plaza over rather than crashing the channel.
        }
        return out;
    }

    /** The saved characters; the very first time, a few random ones so the plaza is not empty. */
    List<Mii> loadOrSeed(Random r, int count) {
        List<Mii> miis = load();
        if (miis.isEmpty() && !seeded()) {
            for (int i = 0; i < count; i++) miis.add(Mii.random(r, i % 2 == 1));
            save(miis);
            markSeeded();
        }
        return miis;
    }

    void save(List<Mii> miis) {
        JSONArray a = new JSONArray();
        try {
            for (Mii m : miis) a.put(m.toJson());
        } catch (JSONException e) {
            return;
        }
        prefs.edit().putString("miis", a.toString()).putInt("version", version() + 1).apply();
    }

    /** Bumped on every save, so the channel menu knows to redraw its banner. */
    int version() {
        return prefs.getInt("version", 0);
    }

    boolean seeded() {
        return prefs.getBoolean("seeded", false);
    }

    void markSeeded() {
        prefs.edit().putBoolean("seeded", true).apply();
    }

    boolean welcomed() {
        return prefs.getBoolean("welcomed", false);
    }

    void markWelcomed() {
        prefs.edit().putBoolean("welcomed", true).apply();
    }
}
