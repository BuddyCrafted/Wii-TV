package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

/**
 * What the device can take. The target is the 2020-2022 Fire TV Sticks (3rd gen, Lite: Fire OS 7 / Android 9,
 * 1 GB RAM, quad-core 1.7 GHz, PowerVR GE8300, 1080p; 4K Max 1st gen: 2 GB). On a 1 GB device the menu
 * keeps channel art at 16 bits a pixel, which halves its memory and keeps the launcher from being killed
 * while a big app runs.
 */
final class DeviceProfile {
    private DeviceProfile() {}

    /** About 1.5 GB: above the 1 GB sticks (which report a little under 1 GB), below the 2 GB ones. */
    static final long LOW_MEMORY_BYTES = 1_500_000_000L;

    static boolean lowMemory(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        if (am.isLowRamDevice()) return true;
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.totalMem > 0 && mi.totalMem < LOW_MEMORY_BYTES;
    }

    static boolean fireTv() {
        return "Amazon".equalsIgnoreCase(Build.MANUFACTURER);
    }
}
