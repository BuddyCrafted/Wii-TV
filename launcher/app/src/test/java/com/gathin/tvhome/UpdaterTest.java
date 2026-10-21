package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UpdaterTest {
    @Test
    public void tagsMapToBuildNumbers() {
        assertEquals(42, Updater.buildOf("build-42"));
        assertEquals(-1, Updater.buildOf("v1.0"));
        assertEquals(-1, Updater.buildOf("build-x"));
    }
}
