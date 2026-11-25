package com.shrimpfarm.app;

import com.shrimpfarm.app.feedtable.RecordTableColumns;

/**
 * 投喂记录表宿主侧的纯规则：哪些列是产品列、只读/灰显怎么算、表头文案 key 怎么映射。
 *
 * <p>从 {@code FeedingRecordActivity} 抽出，零 Android 依赖，可在 JVM 单测里直接断言。
 * 灰显规则与现版 {@code bindNumberCell} 一致：仅「放苗前且批次未结束」显示灰底灰字；
 * 批次结束后整表只读，但保持白底（与现版视觉一致）。</p>
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

    /** 可编辑：日期在放苗日（含）之后，且批次未结束 */
    public static boolean isEditable(boolean afterStocking, boolean batchFinished) {
        return afterStocking && !batchFinished;
    }

    /** 灰显：仅「放苗前且批次未结束」，与现版一致 */
    public static boolean isGrayed(boolean afterStocking, boolean batchFinished) {
        return !afterStocking && !batchFinished;
    }

    /** 日期列表头用专门的资源名；其余 key 就是 R.string 的名字 */
    public static String headerKeyFor(String key) {
        return RecordTableColumns.DATE.equals(key) ? "header_date" : key;
    }
}
