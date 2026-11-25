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
    public void onlyFourNumberFeedColumnsAreGrayedBeforeStocking() {
        String[] numbers = {
                RecordTableColumns.BREAKFAST, RecordTableColumns.LUNCH,
                RecordTableColumns.DINNER, RecordTableColumns.NIGHT_SNACK,
        };
        for (String id : numbers) {
            assertTrue(id, FeedingRecordTableRules.isNumberFeedColumn(id));
        }
        assertFalse(FeedingRecordTableRules.isNumberFeedColumn(RecordTableColumns.MIX1));
        assertFalse(FeedingRecordTableRules.isNumberFeedColumn(RecordTableColumns.REMARK));
        assertFalse(FeedingRecordTableRules.isNumberFeedColumn(RecordTableColumns.DATE));
    }

    @Test
    public void numberCellsEditableOnlyAfterStockingAndBeforeFinish() {
        String id = RecordTableColumns.BREAKFAST;
        assertTrue(FeedingRecordTableRules.isEditable(true, false, id));
        assertFalse(FeedingRecordTableRules.isEditable(false, false, id));
        assertFalse(FeedingRecordTableRules.isEditable(true, true, id));
        assertFalse(FeedingRecordTableRules.isEditable(false, true, id));
    }

    @Test
    public void productAndRemarkStayEditableBeforeStockingButNotAfterFinish() {
        String[] notDateGated = {
                RecordTableColumns.MIX1, RecordTableColumns.WATER_MIX4, RecordTableColumns.REMARK,
        };
        for (String id : notDateGated) {
            assertTrue(id + " 放苗前可编辑", FeedingRecordTableRules.isEditable(false, false, id));
            assertTrue(id + " 放苗后可编辑", FeedingRecordTableRules.isEditable(true, false, id));
            assertFalse(id + " 批次结束只读", FeedingRecordTableRules.isEditable(false, true, id));
            assertFalse(id + " 批次结束只读", FeedingRecordTableRules.isEditable(true, true, id));
        }
    }

    @Test
    public void onlyNumberCellsGrayedBeforeStockingAndBeforeFinish() {
        String number = RecordTableColumns.BREAKFAST;
        assertTrue(FeedingRecordTableRules.isGrayed(false, false, number));
        assertFalse(FeedingRecordTableRules.isGrayed(true, false, number));
        assertFalse(FeedingRecordTableRules.isGrayed(false, true, number));
        assertFalse(FeedingRecordTableRules.isGrayed(true, true, number));

        // 产品格 / 备注格 / 日期列放苗前都不灰
        String[] notGrayed = {
                RecordTableColumns.MIX1, RecordTableColumns.WATER_MIX2,
                RecordTableColumns.REMARK, RecordTableColumns.DATE,
        };
        for (String id : notGrayed) {
            assertFalse(id + " 放苗前不灰显", FeedingRecordTableRules.isGrayed(false, false, id));
        }
    }

    @Test
    public void grayAndEditableAreMutuallyExclusiveForEveryColumnAndCombo() {
        String[] ids = {
                RecordTableColumns.BREAKFAST, RecordTableColumns.MIX1,
                RecordTableColumns.REMARK, RecordTableColumns.DATE,
        };
        boolean[][] combos = {{true, false}, {false, false}, {true, true}, {false, true}};
        for (String id : ids) {
            for (boolean[] combo : combos) {
                boolean grayed = FeedingRecordTableRules.isGrayed(combo[0], combo[1], id);
                boolean editable = FeedingRecordTableRules.isEditable(combo[0], combo[1], id);
                assertFalse(id + " grayed 与 editable 不得同时为真", grayed && editable);
            }
        }
    }

    @Test
    public void dateHeaderMapsToStringResourceName() {
        assertEquals("header_date", FeedingRecordTableRules.headerKeyFor(RecordTableColumns.DATE));
        assertEquals("feeding_header_breakfast",
                FeedingRecordTableRules.headerKeyFor("feeding_header_breakfast"));
    }
}
