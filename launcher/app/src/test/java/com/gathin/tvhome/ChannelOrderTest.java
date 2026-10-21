package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class ChannelOrderTest {

    private static List<String> sorted(List<String> saved, List<String> dock, String... labels) {
        List<String> items = new ArrayList<>(Arrays.asList(labels));
        // Key and label are the same string here; keys are lower case, labels mixed.
        ChannelOrder.sort(items, s -> s.toLowerCase(), s -> s, saved, dock);
        return items;
    }

    @Test
    public void savedOrderWinsThenDockThenLabel() {
        List<String> out = sorted(Arrays.asList("netflix", "youtube"), Arrays.asList("spotify", "netflix"),
                "YouTube", "Twitch", "Netflix", "Spotify", "apple");
        assertEquals(Arrays.asList("Netflix", "YouTube", "Spotify", "apple", "Twitch"), out);
    }

    @Test
    public void withNothingSavedItIsAlphabetical() {
        assertEquals(Arrays.asList("a", "B", "c"), sorted(new ArrayList<>(), new ArrayList<>(), "c", "B", "a"));
    }

    @Test
    public void splitAndJoinRoundTripAndDropDuplicates() {
        List<String> keys = ChannelOrder.split("a.b, c.d,,a.b");
        assertEquals(Arrays.asList("a.b", "c.d"), keys);
        assertEquals("a.b,c.d", ChannelOrder.join(keys));
        assertEquals(0, ChannelOrder.split(null).size());
    }

    @Test
    public void recentMovesToFrontAndIsCapped() {
        List<String> r = new ArrayList<>();
        for (int i = 0; i < 12; i++) r = ChannelOrder.touchRecent(r, "p" + i);
        assertEquals(ChannelOrder.RECENT_MAX, r.size());
        assertEquals("p11", r.get(0));
        r = ChannelOrder.touchRecent(r, "p8");
        assertEquals("p8", r.get(0));
        assertEquals(ChannelOrder.RECENT_MAX, r.size());
    }
}
