package com.gathin.tvhome;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Over-the-air updates from this repository's GitHub releases. CI publishes every build of main as a
 * release tagged build-<N> with the APK attached; this checks the latest one, and when N is higher than the
 * installed build, downloads the APK and hands it to Android's package installer.
 *
 * Android 12+ installs it with no prompt once this app is the installer of record (after the first update);
 * older systems, Fire OS included, show one Install confirmation. Installing needs "install unknown apps",
 * which build-and-install.sh grants over ADB.
 */
final class Updater {
    private Updater() {}

    static final String KEY_AUTO = "auto_update";
    static final String KEY_LAST_CHECK = "update_last_check";
    static final long CHECK_EVERY_MS = 24L * 60 * 60 * 1000;

    static final class Release {
        final int build;
        final String name;
        final String notes;
        final String apkUrl;

        Release(int build, String name, String notes, String apkUrl) {
            this.build = build;
            this.name = name;
            this.notes = notes;
            this.apkUrl = apkUrl;
        }
    }

    interface Result<T> {
        void done(T value, String error);
    }

    /** Created on use, so the class also loads in plain JVM tests. */
    private static Handler main() {
        return new Handler(Looper.getMainLooper());
    }

    /** The latest release, or null with an error message. Runs off the UI thread; answers on it. */
    static void latest(Context context, Result<Release> result) {
        String repo = context.getString(R.string.update_repo);
        new Thread(() -> {
            Release r = null;
            String error = null;
            try {
                r = parse(get("https://api.github.com/repos/" + repo + "/releases/latest"));
                if (r == null) error = "no APK in the latest release";
            } catch (IOException | RuntimeException | org.json.JSONException e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final Release fr = r;
            final String fe = error;
            main().post(() -> result.done(fr, fe));
        }, "wii-update-check").start();
    }

    /** A release JSON from the GitHub API to a Release: tag build-<N> and an .apk asset. */
    static Release parse(String json) throws org.json.JSONException {
        JSONObject o = new JSONObject(json);
        int build = buildOf(o.optString("tag_name", ""));
        if (build < 0) return null;
        JSONArray assets = o.optJSONArray("assets");
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            if (a.optString("name", "").endsWith(".apk")) {
                return new Release(build, o.optString("name", "build " + build), o.optString("body", ""),
                        a.getString("browser_download_url"));
            }
        }
        return null;
    }

    /** "build-42" → 42; anything else → -1. */
    static int buildOf(String tag) {
        if (!tag.startsWith("build-")) return -1;
        try {
            return Integer.parseInt(tag.substring(6));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static boolean isNewer(Release r) {
        return r != null && r.build > BuildConfig.WIIOS_BUILD;
    }

    /** Downloads the release's APK and starts installing it; errors come back on the UI thread. */
    static void install(Context context, Release r, Result<Void> result) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            String error = null;
            try {
                File apk = new File(app.getCacheDir(), "update.apk");
                download(r.apkUrl, apk);
                commit(app, apk);
            } catch (IOException | RuntimeException e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String fe = error;
            main().post(() -> result.done(null, fe));
        }, "wii-update-install").start();
    }

    private static void commit(Context context, File apk) throws IOException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = session.openWrite("wiios.apk", 0, apk.length())) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                session.fsync(out);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT
                    | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent status = PendingIntent.getBroadcast(context, id,
                    new Intent(context, UpdateReceiver.class).setAction(UpdateReceiver.ACTION_STATUS), flags);
            session.commit(status.getIntentSender());
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "wiiOS-updater");
        c.setInstanceFollowRedirects(true);
        return c;
    }

    static String get(String url) throws IOException {
        HttpURLConnection c = open(url);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }

    private static void download(String url, File to) throws IOException {
        HttpURLConnection c = open(url);
        c.setRequestProperty("Accept", "application/octet-stream");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(to)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
        } finally {
            c.disconnect();
        }
    }
}
