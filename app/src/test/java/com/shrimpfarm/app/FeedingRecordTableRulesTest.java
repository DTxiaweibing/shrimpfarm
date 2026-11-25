package com.shrimpfarm.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.shrimpfarm.app.feedtable.RecordTableColumns;

import org.junit.Test;

/** {@link FeedingRecordTableRules} 的纯逻辑单测。 */
public class FeedingRecordTableRulesTest {

    @Test
    public void productColumnsAreMixAndWaterMix() {
        String[] products = {
                RecordTableColumns.MIX1, RecordTableColumns.MIX2,
                RecordTableColumns.MIX3, RecordTableColumns.MIX4,
                RecordTableColumns.WATER_MIX1, RecordTableColumns.WATER_MIX2,
                RecordTableColumns.WATER_MIX3, RecordTableColumns.WATER_MIX4,
        };
        for (String id : products) {
            assertTrue(id, FeedingRecordTableRules.isProductColumn(id));
        }
        assertFalse(FeedingRecordTableRules.isProductColumn(RecordTableColumns.BREAKFAST));
        assertFalse(FeedingRecordTableRules.isProductColumn(RecordTableColumns.REMARK));
        assertFalse(FeedingRecordTableRules.isProductColumn(RecordTableColumns.DATE));
        assertFalse(FeedingRecordTableRules.isProductColumn(null));
    }

    @Test
    public void editableOnlyAfterStockingAndBeforeFinish() {
        assertTrue(FeedingRecordTableRules.isEditable(true, false));
        assertFalse(FeedingRecordTableRules.isEditable(false, false));
        assertFalse(FeedingRecordTableRules.isEditable(true, true));
        assertFalse(FeedingRecordTableRules.isEditable(false, true));
    }

    @Test
    public void grayedOnlyBeforeStockingAndBeforeFinish() {
        assertTrue(FeedingRecordTableRules.isGrayed(false, false));
        assertFalse(FeedingRecordTableRules.isGrayed(true, false));
        assertFalse(FeedingRecordTableRules.isGrayed(false, true));
        assertFalse(FeedingRecordTableRules.isGrayed(true, true));
    }

    @Test
    public void grayAndEditableAreMutuallyExclusive() {
        boolean[][] combos = {{true, false}, {false, false}, {true, true}, {false, true}};
        for (boolean[] combo : combos) {
            boolean grayed = FeedingRecordTableRules.isGrayed(combo[0], combo[1]);
            boolean editable = FeedingRecordTableRules.isEditable(combo[0], combo[1]);
            assertFalse("grayed 与 editable 不得同时为真", grayed && editable);
        }
    }

    @Test
    public void dateHeaderMapsToStringResourceName() {
        assertEquals("header_date", FeedingRecordTableRules.headerKeyFor(RecordTableColumns.DATE));
        assertEquals("feeding_header_breakfast",
                FeedingRecordTableRules.headerKeyFor("feeding_header_breakfast"));
    }
}
