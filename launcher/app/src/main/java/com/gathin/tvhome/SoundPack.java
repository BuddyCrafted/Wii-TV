package com.gathin.tvhome;

import android.content.Context;

import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Your own music and sound effects, found by name. Nothing is bundled: the files stay on your flash drive
 * or TV. Looked for, in order:
 *
 *  1. a WiiOS folder at the top of a flash drive: WiiOS/music/menu.mp3, WiiOS/sounds/select.wav ... (needs
 *     the storage permission, asked for from WiiOS Settings > Your sounds and music);
 *  2. WiiOS's own folder on the flash drive or the TV (Android/data/com.gathin.tvhome/files/music or sounds),
 *     which needs no permission and is where the installer's MUSIC_DIR copies to.
 *
 * Music names: menu, menu_start, shop, games, winter, mii, forecast. Sound names: move, select, page, back,
 * menu, start, open. Any of .mp3, .ogg, .m4a, .flac, .wav.
 */
final class SoundPack {
    private SoundPack() {}

    static final String MUSIC = "music", SOUNDS = "sounds";
    static final String[] EXTENSIONS = {".mp3", ".ogg", ".m4a", ".flac", ".wav"};

    /** The file for this name, or null to use the built-in sound. */
    static File find(Context c, String kind, String name) {
        for (File dir : folders(c, kind)) {
            for (String ext : EXTENSIONS) {
                File f = new File(dir, name + ext);
                if (f.isFile() && f.canRead() && f.length() > 0) return f;
            }
        }
        return null;
    }

    /** Where to look, flash drives' WiiOS folders first. */
    static List<File> folders(Context c, String kind) {
        List<File> out = new ArrayList<>();
        File[] own = c.getExternalFilesDirs(kind);   // the TV's own storage first, then each drive
        for (File d : own) {
            if (d == null) continue;
            File root = volumeRoot(d);
            if (root != null && !isPrimary(c, root)) out.add(new File(root, "WiiOS/" + kind));
        }
        for (File d : own) {
            if (d != null) out.add(d);
        }
        return out;
    }

    /** /storage/ABCD-1234/Android/data/<pkg>/files/<kind> -> /storage/ABCD-1234. */
    private static File volumeRoot(File appDir) {
        String p = appDir.getAbsolutePath();
        int i = p.indexOf("/Android/data/");
        return i > 0 ? new File(p.substring(0, i)) : null;
    }

    private static boolean isPrimary(Context c, File root) {
        File primary = c.getExternalFilesDir(null);
        File pr = primary == null ? null : volumeRoot(primary);
        return pr != null && pr.equals(root);
    }

    /**
     * A personal build (gradle -PpersonalPack) carries your files as assets/WiiOS/music and assets/WiiOS/sounds:
     * copied into WiiOS's folders once per install of that build, so they stay after a later update too. Public
     * builds carry none and this does nothing.
     */
    static void unbundle(Context c) {
        AssetManager am = c.getAssets();
        long stamp;
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            stamp = pi.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }
        SharedPreferences prefs = Profiles.main(c);
        if (prefs.getLong("bundled_pack", 0) == stamp) return;
        for (String kind : new String[]{MUSIC, SOUNDS}) {
            String[] files;
            try {
                files = am.list("WiiOS/" + kind);
            } catch (IOException e) {
                continue;
            }
            File dir = c.getExternalFilesDir(kind);
            if (files == null || dir == null) continue;
            for (String file : files) {
                int dot = file.lastIndexOf('.');
                if (dot <= 0) continue;
                String name = file.substring(0, dot), ext = file.substring(dot).toLowerCase(java.util.Locale.ROOT);
                boolean extOk = false;
                for (String x : EXTENSIONS) if (x.equals(ext)) extOk = true;
                if (!extOk) continue;
                File tmp = new File(dir, name + ext + ".part");
                try (InputStream in = am.open("WiiOS/" + kind + "/" + file); FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[1 << 16];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                } catch (IOException e) {
                    tmp.delete();
                    continue;
                }
                for (String x : EXTENSIONS) new File(dir, name + x).delete();
                if (!tmp.renameTo(new File(dir, name + ext))) tmp.delete();
            }
        }
        prefs.edit().putLong("bundled_pack", stamp).apply();
    }

    /** A flash drive is plugged in (so the storage permission is worth asking for). */
    static boolean drivePresent(Context c) {
        for (File d : c.getExternalFilesDirs(null)) {
            if (d == null) continue;
            File root = volumeRoot(d);
            if (root != null && !isPrimary(c, root)) return true;
        }
        return false;
    }

    /** A flash drive with a WiiOS folder is plugged in (readable once the storage permission is given). */
    static boolean driveFolder(Context c) {
        for (File d : c.getExternalFilesDirs(null)) {
            if (d == null) continue;
            File root = volumeRoot(d);
            if (root != null && !isPrimary(c, root) && new File(root, "WiiOS").isDirectory()) return true;
        }
        return false;
    }
}
