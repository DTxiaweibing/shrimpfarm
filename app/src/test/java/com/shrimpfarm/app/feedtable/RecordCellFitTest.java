package com.shrimpfarm.app.feedtable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 单元格显示策略的纯逻辑单测：产品格按格宽缩字号、备注/日期按格高算行数、备注不留 padding。
 */
public class RecordCellFitTest {

    @Test
    public void productTextShrinksToFourChinesePerLine() {
        // (100 - 2*2) / 4 = 24px；高度充足时不额外缩小
        assertEquals(24, RecordCellFit.productTextSizePx(40f, 100f, 200f, 2f));
    }

    @Test
    public void productTextShrinksToFitTwoLinesInCellHeight() {
        // 48 / (2 * 1.2) = 20px；宽度充足时由高度决定
        assertEquals(20, RecordCellFit.productTextSizePx(40f, 400f, 48f, 0f));
    }

    @Test
    public void productTextNeverGrowsBeyondItsBaseSize() {
        assertEquals(20, RecordCellFit.productTextSizePx(20f, 400f, 400f, 2f));
    }

    @Test
    public void productTextKeepsPositiveFloorForDegenerateCells() {
        assertEquals(1, RecordCellFit.productTextSizePx(40f, 0f, 0f, 2f));
        assertEquals(1, RecordCellFit.productTextSizePx(40f, -10f, 100f, 2f));
    }

    @Test
    public void maxLinesComesFromCellHeightAndLineHeight() {
        assertEquals(6, RecordCellFit.maxLinesForHeight(72f, 10f));
        assertEquals(5, RecordCellFit.maxLinesForHeight(60f, 10f));
        assertEquals(1, RecordCellFit.maxLinesForHeight(11f, 10f));
    }

    @Test
    public void maxLinesStaysWithinOneAndCap() {
        assertEquals(1, RecordCellFit.maxLinesForHeight(0f, 10f));
        assertEquals(1, RecordCellFit.maxLinesForHeight(100f, 0f));
        assertEquals(RecordCellFit.MAX_TEXT_LINES,
                RecordCellFit.maxLinesForHeight(10000f, 10f));
    }

    @Test
    public void productAndRemarkHaveNoPaddingButNumberKeepsGridInset() {
        assertEquals(0f, RecordCellFit.insetPxForKind(
                RecordTableColumns.KIND_REMARK, 2f), 0.001f);
        assertEquals(0f, RecordCellFit.insetPxForKind(
                RecordTableColumns.KIND_PRODUCT, 2f), 0.001f);
        assertEquals(2f, RecordCellFit.insetPxForKind(
                RecordTableColumns.KIND_NUMBER, 2f), 0.001f);
    }

    @Test
    public void editableCellCursorIsExplicitlyVisible() {
        assertTrue(RecordCellFit.SHOW_CURSOR);
    }

    @Test
    public void cursorIsOrangeRedAndWideEnoughToSee() {
        assertEquals(0xFFFF5722, RecordCellFit.CURSOR_COLOR);
        assertEquals(2f, RecordCellFit.CURSOR_WIDTH_DP, 0.001f);
    }

    @Test
    public void cursorDrawableUsesPublicApiFrom29Only() {
        assertFalse(RecordCellFit.usesPublicCursorApi(28));
        assertTrue(RecordCellFit.usesPublicCursorApi(29));
        assertTrue(RecordCellFit.usesPublicCursorApi(36));
    }

    @Test
    public void remarkEditorFontFollowsProductButNumberKeepsItsOwn() {
        assertTrue(RecordCellFit.REMARK_FOLLOWS_PRODUCT_TEXT_SIZE);
        assertEquals(18f, RecordCellFit.editorTextSizePx(
                RecordTableColumns.KIND_REMARK, 24f, 18f), 0.001f);
        assertEquals(18f, RecordCellFit.editorTextSizePx(
                RecordTableColumns.KIND_PRODUCT, 24f, 18f), 0.001f);
        assertEquals(24f, RecordCellFit.editorTextSizePx(
                RecordTableColumns.KIND_NUMBER, 24f, 18f), 0.001f);
    }
}
