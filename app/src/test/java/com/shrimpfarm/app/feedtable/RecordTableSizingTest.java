package com.shrimpfarm.app.feedtable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 列宽公式（计划 A-3 冻结版）的回归测试。
 *
 * <p>基准：手机 cellWidth = (int)(w / 6.5)、remarkWidth = (int)(w / 3.5)；
 * 平板 cellWidth = w / 9、remarkWidth = cellWidth * 2；对角线 7.0 英寸是分界。</p>
 */
public class RecordTableSizingTest {

    @Test
    public void phoneUsesSixAndHalfColumns() {
        RecordTableSizing sizing = RecordTableSizing.compute(650, 5.0);
        assertFalse(sizing.tablet);
        assertEquals(100, sizing.cellWidth);   // 650 / 6.5 = 100
        assertEquals(185, sizing.remarkWidth); // 650 / 3.5 = 185.71 → 185
    }

    @Test
    public void tabletUsesNineColumnsAndDoubleRemark() {
        RecordTableSizing sizing = RecordTableSizing.compute(900, 10.0);
        assertTrue(sizing.tablet);
        assertEquals(100, sizing.cellWidth);   // 900 / 9
        assertEquals(200, sizing.remarkWidth); // 100 * 2
    }

    @Test
    public void diagonalBoundarySwitchesAtSevenInches() {
        RecordTableSizing phone = RecordTableSizing.compute(900, 6.99);
        assertFalse(phone.tablet);
        assertEquals(138, phone.cellWidth);    // 900 / 6.5 = 138.46 → 138

        RecordTableSizing tablet = RecordTableSizing.compute(900, 7.0);
        assertTrue(tablet.tablet);
        assertEquals(100, tablet.cellWidth);   // 900 / 9
    }

    @Test
    public void nonPositiveScreenWidthDegradesToZero() {
        RecordTableSizing zero = RecordTableSizing.compute(0, 5.0);
        assertEquals(0, zero.cellWidth);
        assertEquals(0, zero.remarkWidth);
        assertFalse(zero.tablet);

        RecordTableSizing negative = RecordTableSizing.compute(-100, 10.0);
        assertEquals(0, negative.cellWidth);
        assertEquals(0, negative.remarkWidth);
        assertTrue(negative.tablet);
    }

    @Test
    public void widthsTruncateInsteadOfRounding() {
        RecordTableSizing sizing = RecordTableSizing.compute(700, 5.0);
        assertEquals(107, sizing.cellWidth);   // 700 / 6.5 = 107.69，截断为 107 而不是 108
        assertEquals(200, sizing.remarkWidth); // 700 / 3.5 = 200
    }
}
