package com.shrimpfarm.app.feedtable;

/**
 * 列类型 → 编辑器行为的纯策略：点击是否进编辑器、是否多行、是否挂数字过滤、提交文本怎么落地。
 *
 * <p>把「数字 / 产品 / 备注」三类的差异集中在这里，编辑器只按策略配置自己，
 * 便于单测钉死「备注不按数字归一化」「产品不进编辑器」等规则。零 Android 依赖。</p>
 */
public final class RecordCellRules {

    private RecordCellRules() {
    }

    /** 该 kind 点击是否进共用编辑器：数字 / 备注 true，产品 false */
    public static boolean opensEditor(int kind) {
        return kind == RecordTableColumns.KIND_NUMBER
                || kind == RecordTableColumns.KIND_REMARK;
    }

    /** 该 kind 是否多行编辑：仅备注 */
    public static boolean multiLine(int kind) {
        return kind == RecordTableColumns.KIND_REMARK;
    }

    /** 该 kind 是否挂数字过滤器：仅数字 */
    public static boolean filtersNumber(int kind) {
        return kind == RecordTableColumns.KIND_NUMBER;
    }

    /** 提交文本：数字走 1 位小数归一化，备注原样返回（含换行） */
    public static String commitText(int kind, String raw) {
        if (kind == RecordTableColumns.KIND_NUMBER) {
            return CellInputRules.normalizeNumber(raw);
        }
        return raw;
    }
}
