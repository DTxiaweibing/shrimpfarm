package com.shrimpfarm.app.hq;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class HqAxisScaleTest {

    @Test
    public void snapsArbitraryPricesToHalfYuanGrid() {
        assertEquals(11f, HqAxisScale.snap(11.2f), 0f);
        assertEquals(11.5f, HqAxisScale.snap(11.3f), 0f);
        assertEquals(11.5f, HqAxisScale.snap(11.5f), 0f);
        assertEquals(12f, HqAxisScale.snap(11.8f), 0f);
        assertEquals(0f, HqAxisScale.snap(0.1f), 0f);
    }

    @Test
    public void formatsOnlyIntegersAndHalfYuan() {
        assertEquals("11", HqAxisScale.format(11.2f));
        assertEquals("11.5", HqAxisScale.format(11.5f));
        assertEquals("12", HqAxisScale.format(12f));
        assertEquals("12", HqAxisScale.format(11.8f));
    }

    @Test
    public void neverOutputsTenthsOtherThanFive() {
        for (int i = 0; i <= 200; i++) {
            String label = HqAxisScale.format(i / 10f);
            assertFalse(label, label.matches(".*\\.[12346789]"));
        }
    }
}
