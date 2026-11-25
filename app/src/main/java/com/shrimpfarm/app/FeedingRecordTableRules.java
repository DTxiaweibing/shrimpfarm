package com.shrimpfarm.app;

import com.shrimpfarm.app.feedtable.RecordTableColumns;

/**
 * 投喂记录表宿主侧的纯规则：哪些列是产品列、只读/灰显怎么算、表头文案 key 怎么映射。
 *
 * <p>从 {@code FeedingRecordActivity} 抽出，零 Android 依赖，可在 JVM 单测里直接断言。
 * 放苗前只灰显/禁用四个数字投喂格（与现版 {@code bindNumberCell} 一致）；
 * 产品格与备注格在放苗前仍可操作，只受批次结束控制。批次结束后整表只读，但保持白底。</p>
 */
public final class FeedingRecordTableRules {

    private FeedingRecordTableRules() {
    }

    /** 产品列（拌料 / 调水）：点击弹产品选择框，不进编辑器 */
    public static boolean isProductColumn(String columnId) {
        return RecordTableColumns.MIX1.equals(columnId)
                || RecordTableColumns.MIX2.equals(columnId)
                || RecordTableColumns.MIX3.equals(columnId)
                || RecordTableColumns.MIX4.equals(columnId)
                || RecordTableColumns.WATER_MIX1.equals(columnId)
                || RecordTableColumns.WATER_MIX2.equals(columnId)
                || RecordTableColumns.WATER_MIX3.equals(columnId)
                || RecordTableColumns.WATER_MIX4.equals(columnId);
    }

    /** 四个数字投喂格：放苗前灰显/禁用只作用于这些列，与现版 bindNumberCell 一致 */
    public static boolean isNumberFeedColumn(String columnId) {
        return RecordTableColumns.BREAKFAST.equals(columnId)
                || RecordTableColumns.LUNCH.equals(columnId)
                || RecordTableColumns.DINNER.equals(columnId)
                || RecordTableColumns.NIGHT_SNACK.equals(columnId);
    }

    /**
     * 可编辑：批次未结束，且——数字投喂格还要求日期在放苗日（含）之后；
     * 产品格 / 备注格不受放苗日限制（与现版 bindProductCell / remarkEt 只判 batchFinished 一致）。
     */
    public static boolean isEditable(boolean afterStocking, boolean batchFinished, String columnId) {
        if (batchFinished) {
            return false;
        }
        return !isNumberFeedColumn(columnId) || afterStocking;
    }

    /** 灰显：仅「放苗前的数字投喂格且批次未结束」，与现版一致；批次结束整表只读但保持白底 */
    public static boolean isGrayed(boolean afterStocking, boolean batchFinished, String columnId) {
        return !batchFinished && !afterStocking && isNumberFeedColumn(columnId);
    }

    /** 日期列表头用专门的资源名；其余 key 就是 R.string 的名字 */
    public static String headerKeyFor(String key) {
        return RecordTableColumns.DATE.equals(key) ? "header_date" : key;
    }
}
