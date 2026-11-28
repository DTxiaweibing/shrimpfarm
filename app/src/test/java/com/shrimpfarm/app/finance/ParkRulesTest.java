package com.shrimpfarm.app.finance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ParkRulesTest {

    @Test
    public void parkNameIsTrimmedAndBlankRejected() {
        assertEquals("园区甲", ParkRules.normalizedName("  园区甲  "));
        assertEquals("", ParkRules.normalizedName(null));
        assertEquals("", ParkRules.normalizedName("  "));
        assertTrue(ParkRules.validName(" 园区甲 "));
        assertFalse(ParkRules.validName(null));
        assertFalse(ParkRules.validName("  "));
    }

    @Test
    public void accountSummaryJoinsOnlyNonBlankOptionalFields() {
        assertEquals("园区社 6222", ParkRules.accountSummary(" 园区社 ", " 6222 "));
        assertEquals("园区社", ParkRules.accountSummary("园区社", "  "));
        assertEquals("6222", ParkRules.accountSummary(null, "6222"));
        assertEquals("", ParkRules.accountSummary("", null));
    }
}
