package com.shrimpfarm.app.hq;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HqDatePagingTest {

    @Test
    public void firstPageIncludesStartAndLaterPagesSkipIt() {
        assertEquals("gte.", HqDatePaging.lowerOp(null));
        assertEquals("gt.", HqDatePaging.lowerOp("2026-10-22"));
        assertEquals("2026-10-01", HqDatePaging.lowerBound("2026-10-01", null));
        assertEquals("2026-10-22", HqDatePaging.lowerBound("2026-10-01", "2026-10-22"));
    }

    @Test
    public void cursorAdvancesOnlyWhenPageHasLaterDate() {
        assertEquals("2026-10-22", HqDatePaging.nextCursor(null, "2026-10-22"));
        assertEquals("2026-11-12", HqDatePaging.nextCursor("2026-10-22", "2026-11-12"));
        assertNull(HqDatePaging.nextCursor("2026-10-22", "2026-10-22"));
        assertNull(HqDatePaging.nextCursor(null, ""));
    }

    @Test
    public void stopsOnShortPageOrPageCap() {
        assertTrue(HqDatePaging.shouldContinue(HqDatePaging.PAGE_SIZE, 1));
        assertFalse(HqDatePaging.shouldContinue(848, 3));
        assertFalse(HqDatePaging.shouldContinue(0, 1));
        assertFalse(HqDatePaging.shouldContinue(HqDatePaging.PAGE_SIZE, HqDatePaging.MAX_PAGES));
    }
}
