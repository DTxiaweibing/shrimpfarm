package com.shrimpfarm.app.finance;

import static org.junit.Assert.*;
import org.junit.Test;

public class V17ParseTest {
    @Test public void plainSpecs() {
        assertEquals(30.0, V17Parse.parseTailPerJin("30尾/斤"), 0);
        assertEquals(30.0, V17Parse.parseTailPerJin("30 尾/斤"), 0);
        assertEquals(30.0, V17Parse.parseTailPerJin("30/斤"), 0);
        assertEquals(30.0, V17Parse.parseTailPerJin("30"), 0);
        assertEquals(12.5, V17Parse.parseTailPerJin("12.5尾/斤"), 0);
    }
    @Test public void rangeKeepsFirstValueAndOriginal() {
        V17Parse.SpecParse p = V17Parse.parseSpec("20-30尾/斤");
        assertEquals(20.0, p.value, 0);
        assertEquals("20-30尾/斤", p.range);
    }
    @Test public void invalidSpecsReturnZero() {
        assertEquals(0.0, V17Parse.parseTailPerJin("大虾"), 0);
        assertEquals(0.0, V17Parse.parseTailPerJin(""), 0);
        assertEquals(0.0, V17Parse.parseTailPerJin("0尾/斤"), 0);
        assertEquals(0.0, V17Parse.parseTailPerJin("1000尾/斤"), 0);
    }
    @Test public void singleShedNumberMultiReturnsZero() {
        assertEquals(3, V17Parse.singleShedNumber("3"));
        assertEquals(0, V17Parse.singleShedNumber("3-5"));
        assertEquals(0, V17Parse.singleShedNumber("1,3,5"));
        assertEquals(0, V17Parse.singleShedNumber("A棚"));
        assertEquals(0, V17Parse.singleShedNumber("100"));
    }
    @Test public void amountFixupOnlyWhenZeroWithBothFactors() {
        assertTrue(V17Parse.needsAmountFixup(0, 10, 4.5));
        assertFalse(V17Parse.needsAmountFixup(45, 10, 4.5));
        assertFalse(V17Parse.needsAmountFixup(0, 10, 0));
    }
}
