package com.shrimpfarm.app.hq;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HqDateDotsTest {

    /** 绿点只能落在本地有行情的日期上，不能落到相邻的空日子。 */
    @Test
    public void marksOnlyDatesWithLocalData() {
        Set<String> data = HqDateDots.toSet(Arrays.asList("2026-11-25", "2026-11-27"));
        List<HqDateDots.Cell> cells = HqDateDots.month(2026, 11, data, "2026-01-01", "2026-12-31");

        assertTrue(cells.get(5 + 25).hasData);
        assertFalse(cells.get(5 + 26).hasData);
        assertTrue(cells.get(5 + 27).hasData);
    }

    /** 超出当前可翻页窗口的日期不可选，窗口两端仍可选。 */
    @Test
    public void disablesDatesOutsidePageWindow() {
        List<HqDateDots.Cell> cells = HqDateDots.month(
                2026, 11, Collections.<String>emptySet(), "2026-11-10", "2026-11-20");

        assertFalse(cells.get(5 + 9).selectable);
        assertTrue(cells.get(5 + 10).selectable);
        assertTrue(cells.get(5 + 20).selectable);
        assertFalse(cells.get(5 + 21).selectable);
    }

    /** 2026-11-01 是周日：周一开头的月历前面要补 6 个空格，共 6 行。 */
    @Test
    public void buildsMondayFirstGridWithLeadingAndTrailingBlanks() {
        List<HqDateDots.Cell> cells = HqDateDots.month(
                2026, 11, Collections.<String>emptySet(), "2026-01-01", "2026-12-31");

        assertEquals(42, cells.size());
        assertNull(cells.get(0).date);
        assertEquals("2026-11-01", cells.get(6).date);
        assertEquals("2026-11-30", cells.get(35).date);
        assertNull(cells.get(41).date);
    }

    /** Material 日历内部按 UTC 算日期，转换必须用 UTC，否则东八区会错位一天。 */
    @Test
    public void convertsDatesUsingUtc() {
        assertEquals(0L, HqDateDots.toUtcMillis("1970-01-01"));
        assertEquals(86400000L, HqDateDots.toUtcMillis("1970-01-02"));
        assertEquals("2026-11-27", HqDateDots.fromUtcMillis(HqDateDots.toUtcMillis("2026-11-27")));
    }

    /** DayViewDecorator 收到的是 0 基月份，转字符串时必须 +1 才能命中本地库日期。 */
    @Test
    public void matchesDecoratorMonthWithLocalDates() {
        Set<String> data = HqDateDots.toSet(Collections.singletonList("2026-11-27"));
        assertTrue(HqDateDots.hasDot(data, 2026, 10, 27));
        assertFalse(HqDateDots.hasDot(data, 2026, 11, 27));
        assertFalse(HqDateDots.hasDot(data, 2026, 10, 26));
    }

    /** setCompoundDrawables 不设 bounds 就不画，绿点必须是非零尺寸。 */
    @Test
    public void dotBoundsAreNonZero() {
        assertArrayEquals(new int[]{0, 0, 6, 6}, HqDateDots.dotBounds(6));
    }

    /** 同步未完成时禁止取绿点快照，完成后才允许打开日历。 */
    @Test
    public void waitsForSyncBeforeReadingDots() {
        assertTrue(HqDateDots.canReadDots(0, true));
        assertFalse(HqDateDots.canReadDots(1, true));
        assertFalse(HqDateDots.canReadDots(0, false));
    }
}
