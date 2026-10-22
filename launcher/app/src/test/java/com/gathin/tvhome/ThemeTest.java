package com.gathin.tvhome;

import static org.junit.Assert.assertSame;

import java.util.Calendar;
import org.junit.Test;

public class ThemeTest {

    @Test
    public void automaticFollowsTheSeasons() {
        assertSame(Theme.WINTER, Theme.forMonth(Calendar.DECEMBER));
        assertSame(Theme.WINTER, Theme.forMonth(Calendar.FEBRUARY));
        assertSame(Theme.ISLAND, Theme.forMonth(Calendar.APRIL));
        assertSame(Theme.BEACH, Theme.forMonth(Calendar.JULY));
        assertSame(Theme.FALL, Theme.forMonth(Calendar.SEPTEMBER));
        assertSame(Theme.HALLOWEEN, Theme.forMonth(Calendar.OCTOBER));
        assertSame(Theme.FALL, Theme.forMonth(Calendar.NOVEMBER));
    }
}
