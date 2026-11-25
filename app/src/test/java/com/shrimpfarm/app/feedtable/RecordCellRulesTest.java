package com.shrimpfarm.app.feedtable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** {@link RecordCellRules} 的纯逻辑单测：三列类型的编辑器策略与提交文本。 */
public class RecordCellRulesTest {

    @Test
    public void opensEditorOnlyForNumberAndRemark() {
        assertTrue(RecordCellRules.opensEditor(RecordTableColumns.KIND_NUMBER));
        assertTrue(RecordCellRules.opensEditor(RecordTableColumns.KIND_REMARK));
        assertFalse(RecordCellRules.opensEditor(RecordTableColumns.KIND_PRODUCT));
    }

    @Test
    public void multiLineOnlyForRemark() {
        assertTrue(RecordCellRules.multiLine(RecordTableColumns.KIND_REMARK));
        assertFalse(RecordCellRules.multiLine(RecordTableColumns.KIND_NUMBER));
        assertFalse(RecordCellRules.multiLine(RecordTableColumns.KIND_PRODUCT));
    }

    @Test
    public void filtersNumberOnlyForNumber() {
        assertTrue(RecordCellRules.filtersNumber(RecordTableColumns.KIND_NUMBER));
        assertFalse(RecordCellRules.filtersNumber(RecordTableColumns.KIND_REMARK));
        assertFalse(RecordCellRules.filtersNumber(RecordTableColumns.KIND_PRODUCT));
    }

    @Test
    public void commitNumberNormalizesToOneDecimal() {
        assertEquals("12", RecordCellRules.commitText(RecordTableColumns.KIND_NUMBER, "12.0"));
        assertEquals("12.3", RecordCellRules.commitText(RecordTableColumns.KIND_NUMBER, "12.34"));
        assertEquals("0", RecordCellRules.commitText(RecordTableColumns.KIND_NUMBER, "0.0"));
        assertEquals("", RecordCellRules.commitText(RecordTableColumns.KIND_NUMBER, ""));
    }

    @Test
    public void commitRemarkKeepsRawTextIncludingNewlinesAndChinese() {
        String raw = "第一行\n第二行【拌料】abc";
        assertEquals(raw, RecordCellRules.commitText(RecordTableColumns.KIND_REMARK, raw));
        assertEquals("含空格 与\t制表符",
                RecordCellRules.commitText(RecordTableColumns.KIND_REMARK, "含空格 与\t制表符"));
        assertEquals("", RecordCellRules.commitText(RecordTableColumns.KIND_REMARK, ""));
    }

    @Test
    public void commitProductFallsThroughToRaw() {
        // 产品格不进编辑器，理论上不会提交；这里断言兜底行为是原样返回，防止误归一化
        assertEquals("【拌料】料A+标签",
                RecordCellRules.commitText(RecordTableColumns.KIND_PRODUCT, "【拌料】料A+标签"));
    }

    @Test
    public void numberCellKeepsNativeMovementSoCaretCanDraw() {
        // 数字格不能 setMovementMethod(null)：那会让光标完全不画（与计算器 CellEditorOverlay 一致）
        assertEquals(RecordCellRules.MovementMethodPolicy.NATIVE,
                RecordCellRules.movementMethodFor(RecordTableColumns.KIND_NUMBER));
    }

    @Test
    public void remarkCellUsesScrollingMovementForMultipleLines() {
        assertEquals(RecordCellRules.MovementMethodPolicy.SCROLLING,
                RecordCellRules.movementMethodFor(RecordTableColumns.KIND_REMARK));
    }
}
