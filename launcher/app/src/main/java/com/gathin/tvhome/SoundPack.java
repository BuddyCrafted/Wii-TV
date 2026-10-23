package com.gathin.tvhome;

import android.content.Context;

import java.io.File;
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
