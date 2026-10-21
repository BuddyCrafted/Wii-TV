package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A store channel's catalog (the Shop's res/raw/shop_catalog.json or the Games channel's games_catalog.json, and
 * the newer copy on GitHub when reachable) and how each title is got: Google Play apps open their Play page (the
 * Amazon Appstore's on Fire TV), since Play installs its own apps; open-source apps are downloaded here (F-Droid,
 * a GitHub release matched to the CPU, or a direct link) and handed to the package installer; web games open in
 * WebPlayActivity.
 */
final class ShopCatalog {
    static final long FRESH_MS = 6L * 60 * 60 * 1000;

    /** Which catalog: the Shop's apps or the Games channel's games. */
    static final class Store {
        final String id, cacheKey, remotePath;
        final int raw;

        Store(String id, int raw) {
            this.id = id;
            this.raw = raw;
            this.cacheKey = id + "_catalog";
            this.remotePath = "launcher/app/src/main/res/raw/" + id + "_catalog.json";
        }

        boolean games() {
            return this == GAMES;
        }
    }

    static final Store SHOP = new Store("shop", R.raw.shop_catalog);
    static final Store GAMES = new Store("games", R.raw.games_catalog);

    static Store store(String id) {
        return GAMES.id.equals(id) ? GAMES : SHOP;
    }

    static final class Category {
        String id, name;
    }

    static final class App {
        /** Package name; one ending in "*" matches any installed package with that prefix (a series of apps). */
        String pkg, name, publisher, about, source, url, repo;
        /** Shown instead of "Free" when set, e.g. "$34.99" or "Subscription". */
        String price = "";
        /** The account a title asks you to sign in with, e.g. "NVIDIA"; empty if none. */
        String account = "";
        /** Store search terms, for a title whose package isn't known exactly. */
        String query = "";
        int color = 0xFF3FC0EC;
        final List<String> categories = new ArrayList<>();

        boolean direct() {
            return !"play".equals(source) && !web();
        }

        boolean web() {
            return "web".equals(source);
        }

        boolean prefix() {
            return pkg.endsWith("*");
        }
    }

    final List<Category> categories = new ArrayList<>();
    final List<App> apps = new ArrayList<>();

    List<App> in(String category) {
        List<App> out = new ArrayList<>();
        for (App a : apps) if (a.categories.contains(category)) out.add(a);
        return out;
    }

    static ShopCatalog parse(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        ShopCatalog c = new ShopCatalog();
        JSONArray cats = o.getJSONArray("categories");
        for (int i = 0; i < cats.length(); i++) {
            Category cat = new Category();
            cat.id = cats.getJSONObject(i).getString("id");
            cat.name = cats.getJSONObject(i).getString("name");
            c.categories.add(cat);
        }
        JSONArray apps = o.getJSONArray("apps");
        for (int i = 0; i < apps.length(); i++) {
            JSONObject j = apps.getJSONObject(i);
            App a = new App();
            a.pkg = j.getString("pkg");
            a.name = j.getString("name");
            a.publisher = j.optString("publisher", "");
            a.about = j.optString("about", "");
            a.source = j.optString("source", "play");
            a.url = j.optString("url", "");
            a.repo = j.optString("repo", "");
            a.price = j.optString("price", "");
            a.account = j.optString("account", "");
            a.query = j.optString("query", "");
            try {
                a.color = android.graphics.Color.parseColor(j.optString("color", "#3FC0EC"));
            } catch (IllegalArgumentException ignored) {
                // keep the default
            }
            JSONArray cs = j.optJSONArray("categories");
            if (cs != null) for (int k = 0; k < cs.length(); k++) a.categories.add(cs.getString(k));
            c.apps.add(a);
        }
        return c;
    }

    /** The cached catalog from GitHub if there is one, else the one in the APK. */
    static ShopCatalog local(Context c, Store store) {
        String cached = Weather.prefs(c).getString(store.cacheKey, "");
        if (!cached.isEmpty()) {
            try {
                return parse(cached);
            } catch (JSONException ignored) {
                // fall back to the bundled copy
            }
        }
        try (InputStream in = c.getResources().openRawResource(store.raw)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return parse(out.toString("UTF-8"));
        } catch (IOException | JSONException e) {
            return new ShopCatalog();
        }
    }

    /** Fetches the catalog on main (same repository as updates); answers on the UI thread with null if unchanged or offline. */
    static void refresh(Context context, Store store, Updater.Result<ShopCatalog> result) {
        Context app = context.getApplicationContext();
        String timeKey = store.cacheKey + "_time";
        if (System.currentTimeMillis() - Weather.prefs(app).getLong(timeKey, 0) < FRESH_MS) {
            result.done(null, null);
            return;
        }
        String url = "https://raw.githubusercontent.com/" + app.getString(R.string.update_repo) + "/main/" + store.remotePath;
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            ShopCatalog c = null;
            String error = null;
            try {
                String json = Updater.get(url);
                c = parse(json);
                Weather.prefs(app).edit().putString(store.cacheKey, json).putLong(timeKey, System.currentTimeMillis()).apply();
            } catch (IOException | JSONException | RuntimeException e) {
                error = String.valueOf(e.getMessage());
            }
            final ShopCatalog fc = c;
            final String fe = error;
            main.post(() -> result.done(fc, fe));
        }, "wii-shop-catalog").start();
    }

    // ---- what is installed ----------------------------------------------------------------------

    /** The installed package an app stands for (the first match for a prefix), or null. Web games have none. */
    static String installedPkg(Context c, App a) {
        if (a.web()) return null;
        if (!a.prefix()) return installed(c, a.pkg) != null ? a.pkg : null;
        String start = a.pkg.substring(0, a.pkg.length() - 1);
        for (PackageInfo pi : c.getPackageManager().getInstalledPackages(0)) {
            if (pi.packageName.startsWith(start)) return pi.packageName;
        }
        return null;
    }

    /** Installed versionName of the app, or null. */
    static String installed(Context c, App a) {
        String pkg = installedPkg(c, a);
        return pkg == null ? null : installed(c, pkg);
    }

    /** Installed versionName, or null. */
    static String installed(Context c, String pkg) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(pkg, 0);
            return pi.versionName == null ? "" : pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    static Intent launchIntent(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        return i == null ? null : i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /**
     * The store page for a Play app: Google Play, or the Amazon Appstore on Fire TV; a store search when the app
     * has search terms (its exact package isn't known); null if there's no store.
     */
    static Intent storeIntent(Context c, App a) {
        String q = Uri.encode(a.query);
        Intent play = new Intent(Intent.ACTION_VIEW, Uri.parse(a.query.isEmpty()
                ? "market://details?id=" + a.pkg : "market://search?q=" + q + "&c=apps"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (DeviceProfile.fireTv()) {
            Intent amazon = new Intent(Intent.ACTION_VIEW, Uri.parse(a.query.isEmpty()
                    ? "amzn://apps/android?p=" + a.pkg : "amzn://apps/android?s=" + q))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (amazon.resolveActivity(c.getPackageManager()) != null) return amazon;
        }
        return play.resolveActivity(c.getPackageManager()) != null ? play : null;
    }

    // ---- getting an APK ---------------------------------------------------------------------------

    /** Latest version name and download link for a direct app. Blocking: call off the UI thread. */
    static String[] resolve(App a) throws IOException, JSONException {
        switch (a.source) {
            case "fdroid": {
                JSONObject o = new JSONObject(Updater.get("https://f-droid.org/api/v1/packages/" + a.pkg));
                int vc = o.getInt("suggestedVersionCode");
                String name = "";
                JSONArray ps = o.optJSONArray("packages");
                if (ps != null) {
                    for (int i = 0; i < ps.length(); i++) {
                        if (ps.getJSONObject(i).optInt("versionCode") == vc) name = ps.getJSONObject(i).optString("versionName");
                    }
                }
                return new String[] {name, "https://f-droid.org/repo/" + a.pkg + "_" + vc + ".apk"};
            }
            case "github": {
                JSONObject o = new JSONObject(Updater.get("https://api.github.com/repos/" + a.repo + "/releases/latest"));
                List<String[]> apks = new ArrayList<>();
                JSONArray as = o.getJSONArray("assets");
                for (int i = 0; i < as.length(); i++) {
                    JSONObject x = as.getJSONObject(i);
                    if (x.getString("name").endsWith(".apk")) apks.add(new String[] {x.getString("name"), x.getString("browser_download_url")});
                }
                String url = pickApk(apks, Build.SUPPORTED_ABIS);
                if (url == null) throw new IOException("no APK in " + a.repo);
                return new String[] {o.optString("tag_name", "").replaceFirst("^v", ""), url};
            }
            default:
                return new String[] {"", a.url};
        }
    }

    /**
     * The APK for this CPU from a release's assets: one named for the first supported ABI that any asset names,
     * else a universal one (no ABI in its name), else the first.
     */
    static String pickApk(List<String[]> apks, String[] abis) {
        if (apks.isEmpty()) return null;
        String[] all = {"arm64-v8a", "armeabi-v7a", "x86_64", "x86", "arm64", "armv7"};
        for (String abi : abis) {
            for (String[] a : apks) if (a[0].contains(abi)) return a[1];
        }
        for (String[] a : apks) {
            boolean named = false;
            for (String abi : all) if (a[0].contains(abi)) named = true;
            if (!named) return a[1];
        }
        return apks.get(0)[1];
    }
}
