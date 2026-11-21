package com.shrimpfarm.app.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class PriceRangeParserTest {

    private void assertRange(String raw, float low, float high) {
        PriceRange range = PriceRangeParser.parse(raw);
        assertNotNull("应当解析成功: " + raw, range);
        assertEquals(low, range.low, 0.001f);
        assertEquals(high, range.high, 0.001f);
        assertEquals((low + high) / 2f, range.mid(), 0.001f);
    }

    @Test
    public void parsesHyphenRange() {
        assertRange("22-25", 22f, 25f);
        assertRange("8.5-9", 8.5f, 9f);
        assertRange("60-78", 60f, 78f);
    }

    @Test
    public void parsesSinglePrice() {
        assertRange("5", 5f, 5f);
        assertRange("12.5", 12.5f, 12.5f);
    }

    @Test
    public void parsesChineseAndFullWidthSeparators() {
        assertRange("22～25", 22f, 25f);
        assertRange("22～25元/斤", 22f, 25f);
        assertRange("50至60", 50f, 60f);
        assertRange("50到60", 50f, 60f);
        assertRange("22—25", 22f, 25f);
        assertRange("20/58", 20f, 58f);
        assertRange("20、58", 20f, 58f);
    }

    @Test
    public void stripsUnits() {
        assertRange("22元-25元", 22f, 25f);
        assertRange("22-25元/公斤", 22f, 25f);
        assertRange("5斤", 5f, 5f);
        assertRange("￥8.5~9", 8.5f, 9f);
    }

    @Test
    public void swapsReversedRange() {
        assertRange("30-20", 20f, 30f);
    }

    @Test
    public void rejectsInvalidInput() {
        assertNull(PriceRangeParser.parse(null));
        assertNull(PriceRangeParser.parse(""));
        assertNull(PriceRangeParser.parse("   "));
        assertNull(PriceRangeParser.parse("abc"));
        assertNull(PriceRangeParser.parse("0"));
        assertNull(PriceRangeParser.parse("-5"));
        assertNull(PriceRangeParser.parse("5-"));
        assertNull(PriceRangeParser.parse("22-25-30"));
    }
}
