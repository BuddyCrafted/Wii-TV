package com.example.wiitv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.LruCache;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Serves installed apps' artwork to the page as PNGs:
 *   app-art/banner/<package>  — the TV banner (16:9), 404 if the app has none
 *   app-art/icon/<package>    — the launcher icon
 */
final class AppArtHandler implements WebViewAssetLoader.PathHandler {
    private static final int BANNER_W = 640, BANNER_H = 360, ICON = 256;

    private final Context context;
    private final LruCache<String, byte[]> cache = new LruCache<String, byte[]>(16 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, byte[] value) {
            return value.length;
        }
    };

    AppArtHandler(Context context) {
        this.context = context.getApplicationContext();
    }

    @Nullable
    @Override
    public WebResourceResponse handle(@NonNull String path) {
        int slash = path.indexOf('/');
        if (slash < 0) return notFound();
        String kind = path.substring(0, slash);
        String pkg = Uri.decode(path.substring(slash + 1));
        String key = kind + "/" + pkg;

        byte[] png = cache.get(key);
        if (png == null) {
            png = render(kind, pkg);
            if (png == null) return notFound();
            cache.put(key, png);
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "max-age=3600");
        return new WebResourceResponse("image/png", null, 200, "OK", headers, new ByteArrayInputStream(png));
    }

    @Nullable
    private byte[] render(String kind, String pkg) {
        PackageManager pm = context.getPackageManager();
        try {
            Drawable d;
            int w, h;
            if ("banner".equals(kind)) {
                d = loadBanner(pm, pkg);
                w = BANNER_W;
                h = BANNER_H;
            } else if ("icon".equals(kind)) {
                d = pm.getApplicationIcon(pkg);
                w = h = ICON;
            } else {
                return null;
            }
            if (d == null) return null;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            d.setBounds(0, 0, w, h);
            d.draw(c);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            bmp.recycle();
            return out.toByteArray();
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static Drawable loadBanner(PackageManager pm, String pkg) throws PackageManager.NameNotFoundException {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(pkg);
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            ActivityInfo ai = ri.activityInfo;
            Drawable d = ai.loadBanner(pm);
            if (d != null) return d;
        }
        return pm.getApplicationBanner(pkg);
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, new ByteArrayInputStream(new byte[0]));
    }
}
