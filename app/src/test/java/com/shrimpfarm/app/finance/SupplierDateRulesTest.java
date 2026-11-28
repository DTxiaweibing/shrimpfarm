package com.shrimpfarm.app.finance;

import static org.junit.Assert.*;

import org.junit.Test;

public class SupplierDateRulesTest {

    @Test public void validDateParsedToParts() {
        assertArrayEquals(new int[]{2026, 11, 28}, SupplierDateRules.parseDate("2026/11/28"));
        assertArrayEquals(new int[]{2026, 1, 2}, SupplierDateRules.parseDate("2026/1/2"));
    }

    @Test public void invalidDateFallsBackToToday() {
        int[] fallback = SupplierDateRules.todayParts();
        assertArrayEquals(fallback, SupplierDateRules.parseDate(null));
        assertArrayEquals(fallback, SupplierDateRules.parseDate(""));
        assertArrayEquals(fallback, SupplierDateRules.parseDate("2026/11"));
        assertArrayEquals(fallback, SupplierDateRules.parseDate("bad/date/here"));
    }

    @Test public void todayIsFormattedAndMatchesParts() {
        assertTrue(SupplierDateRules.today().matches("\\d{4}/\\d{2}/\\d{2}"));
        int[] p = SupplierDateRules.todayParts();
        assertEquals(p[0], Integer.parseInt(SupplierDateRules.today().substring(0, 4)));
    }
}
