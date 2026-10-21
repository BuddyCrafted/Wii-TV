package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class ShopCatalogTest {
    private static List<String[]> assets(String... names) {
        List<String[]> out = new ArrayList<>();
        for (String n : names) out.add(new String[] {n, "url/" + n});
        return out;
    }

    @Test
    public void picksTheApkForThisCpu() {
        List<String[]> a = assets("app-arm64-v8a-release.apk", "app-armeabi-v7a-release.apk", "app-x86_64-release.apk");
        // Many TV boxes run a 32-bit system on a 64-bit CPU: their first ABI is armeabi-v7a.
        assertEquals("url/app-armeabi-v7a-release.apk", ShopCatalog.pickApk(a, new String[] {"armeabi-v7a", "armeabi"}));
        assertEquals("url/app-arm64-v8a-release.apk", ShopCatalog.pickApk(a, new String[] {"arm64-v8a", "armeabi-v7a"}));
    }

    @Test
    public void fallsBackToUniversalThenFirst() {
        assertEquals("url/app-release.apk",
                ShopCatalog.pickApk(assets("app-x86_64-release.apk", "app-release.apk"), new String[] {"armeabi-v7a"}));
        assertEquals("url/a-x86.apk", ShopCatalog.pickApk(assets("a-x86.apk"), new String[] {"armeabi-v7a"}));
        assertNull(ShopCatalog.pickApk(assets(), new String[] {"arm64-v8a"}));
    }
}
