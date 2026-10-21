package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class RemoteButtonsTest {
    @Test
    public void savedChoicesRoundTrip() {
        Map<Integer, String> m = RemoteButtons.parse("190:com.google.android.youtube.tv,191:,x:bad");
        assertEquals("com.google.android.youtube.tv", m.get(190));
        assertEquals("", m.get(191));
        assertEquals(2, m.size());
        assertEquals("190:com.google.android.youtube.tv,191:", RemoteButtons.join(m));
    }

    @Test
    public void viewerChoicesOverrideConfigAndEmptyClears() {
        Map<Integer, String> config = new LinkedHashMap<>();
        config.put(190, "youtube");
        config.put(191, "netflix");
        Map<Integer, String> mine = new LinkedHashMap<>();
        mine.put(190, "spotify");
        mine.put(191, "");
        mine.put(200, "plex");
        Map<Integer, String> out = RemoteButtons.effective(config, mine, true);
        assertEquals("spotify", out.get(190));
        assertTrue(!out.containsKey(191));
        assertEquals("plex", out.get(200));
        assertTrue(RemoteButtons.effective(config, mine, false).isEmpty());
    }
}
