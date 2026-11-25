package com.shrimpfarm.app.feedtable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * R8 平板 / 大屏验收的纯逻辑回归：把「屏幕 → 列宽公式 → 列模型 → 滚动范围」整条链串起来断言。
 *
 * <p>平板沿用现版 {@code cellWidth = w/9}、备注 = 2 格，日期列 + 滚动列正好铺满屏宽（无横滚）；
 * 手机沿用 {@code w/6.5}、{@code remarkWidth = w/3.5}，仍保留横向滚动。没有引入 TabletLayout 填满算法。</p>
 */
public class RecordTableTabletLayoutTest {

    private static final int SCREEN_WIDTH = 1080;
    private static final int SUB_ROW_H = 40;
    private static final int HEADER_H = 40;

    @Test
    public void tabletFormulaFillsScreenWithoutHorizontalScroll() {
        RecordTableSizing sizing = RecordTableSizing.compute(SCREEN_WIDTH, 8.0);
        assertTrue(sizing.tablet);
        int cell = SCREEN_WIDTH / 9;
        assertEquals(cell, sizing.cellWidth);
        assertEquals(cell * 2, sizing.remarkWidth);

        RecordTableColumns cols = RecordTableColumns.create(
                sizing.cellWidth, sizing.remarkWidth, SUB_ROW_H, HEADER_H);
        assertEquals(cell, cols.dateWidth);
        assertEquals(cell * 2, cols.widths[cols.count() - 1]);
        assertEquals("滚动列 = 6 格 + 备注 2 格", cell * 8, cols.totalWidth());

        RecordTableMetrics m = new RecordTableMetrics();
        m.rebuild(cols, 3, SCREEN_WIDTH, 600);
        assertEquals("日期列 + 滚动列正好铺满屏宽", 0, m.maxScrollX);
        assertEquals(0, m.clampScrollX(50));
    }

    @Test
    public void phoneFormulaStillNeedsHorizontalScroll() {
        RecordTableSizing sizing = RecordTableSizing.compute(SCREEN_WIDTH, 5.0);
        assertFalse(sizing.tablet);

        RecordTableColumns cols = RecordTableColumns.create(
                sizing.cellWidth, sizing.remarkWidth, SUB_ROW_H, HEADER_H);
        RecordTableMetrics m = new RecordTableMetrics();
        m.rebuild(cols, 3, SCREEN_WIDTH, 600);
        assertTrue("手机仍保留横向滚动", m.maxScrollX > 0);
    }

    @Test
    public void tabletRemarkSpansBothSubRowsAndIsNotSqueezed() {
        RecordTableSizing sizing = RecordTableSizing.compute(SCREEN_WIDTH, 8.0);
        RecordTableColumns cols = RecordTableColumns.create(
                sizing.cellWidth, sizing.remarkWidth, SUB_ROW_H, HEADER_H);
        int remark = cols.count() - 1;
        assertEquals("备注列 = 2 格宽", sizing.cellWidth * 2, cols.widths[remark]);
        assertEquals("备注列纵跨两子行", RecordTableColumns.SUB_SPAN, cols.subRows[remark]);
        assertEquals("一天 = 2 个子行", SUB_ROW_H * 2, cols.dayHeight());
    }

    @Test
    public void tabletDiagonalBoundaryStillUsesNineColumns() {
        // 恰好 7 英寸：算平板公式，仍不填满/不挤压
        RecordTableSizing sizing = RecordTableSizing.compute(SCREEN_WIDTH,
                RecordTableSizing.TABLET_MIN_DIAGONAL_INCHES);
        assertTrue(sizing.tablet);
        assertEquals(SCREEN_WIDTH / 9, sizing.cellWidth);
        assertEquals(SCREEN_WIDTH / 9 * 2, sizing.remarkWidth);
    }
}
