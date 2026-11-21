package com.shrimpfarm.app.hq;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DateWindowBuilderTest {

    @Test
    public void buildsWindowEndingToday() {
        List<String> dates = DateWindowBuilder.build("2026-11-20", null, 7, 365);
        assertEquals(7, dates.size());
        assertEquals("2026-11-14", dates.get(0));
        assertEquals("2026-11-20", dates.get(6));
    }

    @Test
    public void extendsToLocalEarliest() {
        List<String> dates = DateWindowBuilder.build("2026-11-20", "2026-11-01", 7, 365);
        assertEquals(20, dates.size());
        assertEquals("2026-11-01", dates.get(0));
        assertEquals("2026-11-20", dates.get(19));
    }

    @Test
    public void capsLocalHistoryAtMaxDays() {
        List<String> dates = DateWindowBuilder.build("2026-11-20", "2020-01-01", 7, 30);
        assertEquals(30, dates.size());
        assertEquals("2026-10-22", dates.get(0));
        assertEquals("2026-11-20", dates.get(29));
    }

    @Test
    public void keepsWindowWhenLocalIsNewer() {
        List<String> dates = DateWindowBuilder.build("2026-11-20", "2026-11-19", 7, 365);
        assertEquals(7, dates.size());
        assertEquals("2026-11-14", dates.get(0));
    }

    @Test
    public void crossesMonthAndYear() {
        List<String> dates = DateWindowBuilder.build("2026-01-02", null, 5, 365);
        assertEquals(5, dates.size());
        assertEquals("2025-12-29", dates.get(0));
        assertEquals("2026-01-02", dates.get(4));
    }

    @Test
    public void rejectsMalformedToday() {
        assertTrue(DateWindowBuilder.build("2026/11/20", null, 7, 365).isEmpty());
        assertTrue(DateWindowBuilder.build("", null, 7, 365).isEmpty());
        assertTrue(DateWindowBuilder.build(null, null, 7, 365).isEmpty());
    }
}
