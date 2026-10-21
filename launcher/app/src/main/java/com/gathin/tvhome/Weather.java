package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Forecast Channel's data: current conditions and a 6-day forecast from Open-Meteo (free, no API key),
 * for a place found from the network's IP address or picked by name in Settings. The last answer is kept
 * in preferences so the channel shows something at once and offline.
 */
final class Weather {
    static final String PREFS = AppLanguage.PREFS;
    static final String KEY_CACHE = "weather_cache";
    static final String KEY_CACHE_TIME = "weather_cache_time";
    static final String KEY_PLACE = "weather_place";       // "name|lat|lon", empty = automatic
    static final String KEY_UNITS = "weather_units";       // "f" or "c", empty = by language/country
    static final long FRESH_MS = 30L * 60 * 1000;

    /** Kinds of sky, for the drawings and the backdrop. */
    enum Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

    static final class Day {
        long dateMs;
        String date;   // yyyy-mm-dd
        int code;
        double max, min;
        int rainChance;
    }

    static final class Report {
        String place = "";
        double temp, wind;
        int humidity, code;
        boolean day = true;
        boolean fahrenheit;
        long fetchedAt;
        final List<Day> days = new ArrayList<>();
    }

    interface Result<T> {
        void done(T value, String error);
    }

    /** Created on use, so the class also loads in plain JVM tests. */
    private static Handler main() {
        return new Handler(Looper.getMainLooper());
    }

    /** WMO weather interpretation codes (Open-Meteo's weather_code) to a kind of sky. */
    static Sky sky(int code) {
        if (code == 0 || code == 1) return Sky.CLEAR;
        if (code == 2) return Sky.PARTLY;
        if (code == 3) return Sky.CLOUDY;
        if (code == 45 || code == 48) return Sky.FOG;
        if (code >= 51 && code <= 57) return Sky.DRIZZLE;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return Sky.RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Sky.SNOW;
        if (code >= 95) return Sky.STORM;
        return Sky.CLOUDY;
    }

    static int label(int code) {
        switch (sky(code)) {
            case CLEAR: return R.string.wx_clear;
            case PARTLY: return R.string.wx_partly;
            case CLOUDY: return R.string.wx_cloudy;
            case FOG: return R.string.wx_fog;
            case DRIZZLE: return R.string.wx_drizzle;
            case RAIN: return R.string.wx_rain;
            case SNOW: return R.string.wx_snow;
            default: return R.string.wx_storm;
        }
    }

    static boolean fahrenheit(Context c) {
        String u = prefs(c).getString(KEY_UNITS, "");
        if (!u.isEmpty()) return u.equals("f");
        String country = Locale.getDefault().getCountry();
        return country.isEmpty() || "US".equals(country) || "LR".equals(country) || "MM".equals(country);
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The cached report, or null. */
    static Report cached(Context c) {
        String json = prefs(c).getString(KEY_CACHE, "");
        if (json.isEmpty()) return null;
        try {
            return parse(new JSONObject(json));
        } catch (JSONException e) {
            return null;
        }
    }

    static boolean fresh(Context c) {
        return System.currentTimeMillis() - prefs(c).getLong(KEY_CACHE_TIME, 0) < FRESH_MS;
    }

    /** Fetches a new report (off the UI thread) and caches it; answers on the UI thread. */
    static void refresh(Context context, Result<Report> result) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            Report r = null;
            String error = null;
            try {
                String[] place = place(app);
                boolean f = fahrenheit(app);
                String url = "https://api.open-meteo.com/v1/forecast?latitude=" + place[1] + "&longitude=" + place[2]
                        + "&current=temperature_2m,weather_code,is_day,wind_speed_10m,relative_humidity_2m"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                        + "&timezone=auto&forecast_days=6"
                        + (f ? "&temperature_unit=fahrenheit&wind_speed_unit=mph" : "&wind_speed_unit=kmh");
                JSONObject o = new JSONObject(Updater.get(url));
                o.put("wiios_place", place[0]);
                o.put("wiios_f", f);
                o.put("wiios_time", System.currentTimeMillis());
                prefs(app).edit().putString(KEY_CACHE, o.toString())
                        .putLong(KEY_CACHE_TIME, System.currentTimeMillis()).apply();
                r = parse(o);
            } catch (IOException | JSONException | RuntimeException e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final Report fr = r;
            final String fe = error;
            main().post(() -> result.done(fr, fe));
        }, "wii-weather").start();
    }

    /** name, latitude, longitude: the chosen place, or one from the IP address. */
    private static String[] place(Context c) throws IOException, JSONException {
        String saved = prefs(c).getString(KEY_PLACE, "");
        if (!saved.isEmpty()) {
            String[] p = saved.split("\\|");
            if (p.length == 3) return p;
        }
        JSONObject geo = new JSONObject(Updater.get("https://get.geojs.io/v1/ip/geo.json"));
        String city = text(geo, "city");
        if (city.isEmpty()) city = text(geo, "region");
        if (city.isEmpty()) city = text(geo, "country");
        return new String[] {city, geo.getString("latitude"), geo.getString("longitude")};
    }

    /** Places matching a name, as "name|lat|lon" with a readable label ("Austin, Texas, United States"). */
    static void search(String name, Result<List<String[]>> result) {
        new Thread(() -> {
            List<String[]> out = new ArrayList<>();
            String error = null;
            try {
                JSONObject o = new JSONObject(Updater.get("https://geocoding-api.open-meteo.com/v1/search?count=6&name="
                        + URLEncoder.encode(name, "UTF-8")));
                JSONArray rs = o.optJSONArray("results");
                if (rs != null) {
                    for (int i = 0; i < rs.length(); i++) {
                        JSONObject p = rs.getJSONObject(i);
                        StringBuilder label = new StringBuilder(p.optString("name"));
                        if (!text(p, "admin1").isEmpty()) label.append(", ").append(text(p, "admin1"));
                        if (!text(p, "country").isEmpty()) label.append(", ").append(text(p, "country"));
                        out.add(new String[] {label.toString(),
                                p.optString("name") + "|" + p.getDouble("latitude") + "|" + p.getDouble("longitude")});
                    }
                }
            } catch (IOException | JSONException | RuntimeException e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String fe = error;
            main().post(() -> result.done(out, fe));
        }, "wii-weather-search").start();
    }

    /** A string field, or "" when missing or JSON null (optString would give "null"). */
    private static String text(JSONObject o, String key) {
        return o.isNull(key) ? "" : o.optString(key, "");
    }

    static Report parse(JSONObject o) throws JSONException {
        Report r = new Report();
        r.place = o.optString("wiios_place", "");
        r.fahrenheit = o.optBoolean("wiios_f", true);
        r.fetchedAt = o.optLong("wiios_time", 0);
        JSONObject cur = o.getJSONObject("current");
        r.temp = cur.getDouble("temperature_2m");
        r.code = cur.getInt("weather_code");
        r.day = cur.optInt("is_day", 1) == 1;
        r.wind = cur.optDouble("wind_speed_10m", 0);
        r.humidity = cur.optInt("relative_humidity_2m", 0);
        JSONObject d = o.getJSONObject("daily");
        JSONArray time = d.getJSONArray("time"), codes = d.getJSONArray("weather_code");
        JSONArray max = d.getJSONArray("temperature_2m_max"), min = d.getJSONArray("temperature_2m_min");
        JSONArray rain = d.optJSONArray("precipitation_probability_max");
        for (int i = 0; i < time.length(); i++) {
            Day day = new Day();
            day.date = time.getString(i);
            day.code = codes.optInt(i, 3);
            day.max = max.optDouble(i, 0);
            day.min = min.optDouble(i, 0);
            day.rainChance = rain == null ? 0 : rain.optInt(i, 0);
            r.days.add(day);
        }
        return r;
    }

    static String temp(double t) {
        return Math.round(t) + "°";
    }
}
