package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class AvatarTest {

    @Test
    public void fieldsPackIndependentlyAndWrap() {
        int look = 0;
        for (int f = 0; f < Avatar.FIELDS; f++) look = Avatar.with(look, f, f + 1);
        for (int f = 0; f < Avatar.FIELDS; f++) assertEquals(f + 1, Avatar.get(look, f));
        // Stepping past the end wraps round, and leaves the other fields alone.
        int last = Avatar.count(2) - 1;
        int stepped = Avatar.with(Avatar.with(look, 2, last), 2, last + 1);
        assertEquals(0, Avatar.get(stepped, 2));
        assertEquals(Avatar.get(look, 3), Avatar.get(stepped, 3));
        assertEquals(Avatar.count(0) - 1, Avatar.get(Avatar.with(look, 0, -1), 0));
    }

    @Test
    public void newProfilesStartDifferent() {
        assertNotEquals(Avatar.defaultLook(0), Avatar.defaultLook(1));
        assertNotEquals(Avatar.defaultLook(1), Avatar.defaultLook(2));
    }
}
