package com.example.wiitv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JavaScript interface exposed to the page as window.WiiTV. */
final class WiiBridge {
    private final Activity activity;

    WiiBridge(Activity activity) {
        this.activity = activity;
    }

    /** JSON array of {pkg, label, hasBanner} for every launchable app except ourselves. */
    @JavascriptInterface
    public String getApps() {
        PackageManager pm = activity.getPackageManager();
        Map<String, JSONObject> apps = new LinkedHashMap<>();
        // TV apps first so their entry (and banner) wins over a phone-style launcher entry.
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, apps);
        collect(pm, Intent.CATEGORY_LAUNCHER, apps);
        JSONArray out = new JSONArray();
        for (JSONObject o : apps.values()) out.put(o);
        return out.toString();
    }

    private void collect(PackageManager pm, String category, Map<String, JSONObject> into) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(activity.getPackageName()) || into.containsKey(pkg)) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("pkg", pkg);
                o.put("label", String.valueOf(ri.loadLabel(pm)));
                o.put("hasBanner", ri.activityInfo.banner != 0 || ri.activityInfo.applicationInfo.banner != 0);
                into.put(pkg, o);
            } catch (JSONException ignored) {
                // Only thrown for non-finite numbers; cannot happen here.
            }
        }
    }

    @JavascriptInterface
    public boolean launchApp(String pkg) {
        PackageManager pm = activity.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return start(i);
    }

    @JavascriptInterface
    public boolean openSettings() {
        return start(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private boolean start(Intent i) {
        activity.runOnUiThread(() -> {
            try {
                activity.startActivity(i);
            } catch (ActivityNotFoundException | SecurityException e) {
                android.util.Log.w("WiiTV", "Cannot start " + i, e);
            }
        });
        return true;
    }
}
