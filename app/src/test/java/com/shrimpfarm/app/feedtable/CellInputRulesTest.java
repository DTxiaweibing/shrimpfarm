package com.shrimpfarm.app.feedtable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** {@link CellInputRules} 的纯逻辑单测：过滤、归一化、InputFilter 候选串拼接。 */
public class CellInputRulesTest {

    // ---------- 1. isAcceptableNumber ----------

    @Test
    public void acceptsEmptyAndPlainDigits() {
        assertTrue(CellInputRules.isAcceptableNumber(""));
        assertTrue(CellInputRules.isAcceptableNumber("0"));
        assertTrue(CellInputRules.isAcceptableNumber("12"));
        assertTrue(CellInputRules.isAcceptableNumber("1234567890"));
    }

    @Test
    public void acceptsSingleDecimalDigit() {
        assertTrue(CellInputRules.isAcceptableNumber("12."));
        assertTrue(CellInputRules.isAcceptableNumber("12.5"));
        assertTrue(CellInputRules.isAcceptableNumber("0.5"));
    }

    @Test
    public void rejectsBareLeadingDot() {
        assertFalse(CellInputRules.isAcceptableNumber("."));
        assertFalse(CellInputRules.isAcceptableNumber(".5"));
    }

    @Test
    public void rejectsMoreThanOneDecimalDigit() {
        assertFalse(CellInputRules.isAcceptableNumber("12.34"));
        assertFalse(CellInputRules.isAcceptableNumber("1.2.3"));
        assertFalse(CellInputRules.isAcceptableNumber("12.."));
    }

    @Test
    public void rejectsSignsLettersAndWhitespace() {
        assertFalse(CellInputRules.isAcceptableNumber("-1"));
        assertFalse(CellInputRules.isAcceptableNumber("a"));
        assertFalse(CellInputRules.isAcceptableNumber(" 1"));
        assertFalse(CellInputRules.isAcceptableNumber("1 "));
    }

    // ---------- 2. normalizeNumber ----------

    @Test
    public void normalizesTrailingZeroAndBareDot() {
        assertEquals("12", CellInputRules.normalizeNumber("12.0"));
        assertEquals("12", CellInputRules.normalizeNumber("12."));
        assertEquals("0", CellInputRules.normalizeNumber("0.0"));
    }

    @Test
    public void normalizesWhitespaceAndEmpty() {
        assertEquals("12.5", CellInputRules.normalizeNumber(" 12.5 "));
        assertEquals("", CellInputRules.normalizeNumber(""));
        assertEquals("", CellInputRules.normalizeNumber("."));
        assertEquals("", CellInputRules.normalizeNumber("   "));
    }

    @Test
    public void normalizesLegacyTwoDecimalsByRounding() {
        assertEquals("12.3", CellInputRules.normalizeNumber("12.34"));
        assertEquals("12.5", CellInputRules.normalizeNumber("12.50"));
    }

    @Test
    public void keepsUnparseableTextTrimmed() {
        assertEquals("abc", CellInputRules.normalizeNumber(" abc "));
    }

    // ---------- 3. filterNumber 接受 ----------

    @Test
    public void filterAcceptsValidInsertion() {
        assertNull(CellInputRules.filterNumber("12.5", 0, 4, "", 0, 0));
        assertNull(CellInputRules.filterNumber(".", 0, 1, "12", 2, 2));
    }

    // ---------- 4. filterNumber 拒绝 ----------

    @Test
    public void filterRejectsSecondDecimalDigit() {
        assertEquals("", CellInputRules.filterNumber("4", 0, 1, "12.3", 4, 4));
    }

    @Test
    public void filterRejectsBareDotAndSign() {
        assertEquals("", CellInputRules.filterNumber(".", 0, 1, "", 0, 0));
        assertEquals("", CellInputRules.filterNumber("-", 0, 1, "", 0, 0));
    }

    // ---------- 5. filterNumber 删除永远放行 ----------

    @Test
    public void filterAllowsDeletion() {
        assertNull(CellInputRules.filterNumber("", 0, 0, "12.5", 2, 3));
    }

    // ---------- 6. filterNumber 只取 [start, end) ----------

    @Test
    public void filterUsesOnlyTheInsertedSlice() {
        // source 整串是 "xx5yy"，但真正插入的只有下标 [2,3) 的 "5"
        assertNull(CellInputRules.filterNumber("xx5yy", 2, 3, "", 0, 0));
    }
}
