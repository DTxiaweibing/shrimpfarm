package com.shrimpfarm.app.feedtable;

/**
 * IME「下一项」的目标查找：纯逻辑，可单测。
 *
 * <p>顺序按「行内从左到右，再下一行」：先走完当天所有可编辑数字格，再到下一天。
 * 产品格不进编辑器、备注格多行会把光标带偏，两者都跳过。
 * 全部走完返回 {@code null}，调用方收起键盘。</p>
 */
public final class RecordCellNavigator {

    private RecordCellNavigator() {
    }

    /** 由宿主提供可编辑判定，导航器本身不碰 provider / DayRecord */
    public interface Editable {
        boolean isEditable(int day, int column);
    }

    /** 下一格坐标 */
    public static final class Target {
        public final int day;
        public final int column;

        public Target(int day, int column) {
            this.day = day;
            this.column = column;
        }
    }

    /**
     * @return 没有下一格返回 {@code null}；越界入参同样返回 {@code null}
     */
    public static Target next(RecordTableColumns columns, int dayCount, int day, int column,
                              Editable editable) {
        if (columns == null || dayCount <= 0 || editable == null
                || day < 0 || day >= dayCount
                || column < 0 || column >= columns.count()) {
            return null;
        }
        for (int d = day; d < dayCount; d++) {
            int from = d == day ? column + 1 : 0;
            for (int col = from; col < columns.count(); col++) {
                if (!opensEditor(columns.kinds[col])) {
                    continue;
                }
                if (editable.isEditable(d, col)) {
                    return new Target(d, col);
                }
            }
        }
        return null;
    }

    /** 只有数字格参与 NEXT；产品格由弹窗选料，备注格多行且用「完成」收起 */
    private static boolean opensEditor(int kind) {
        return kind == RecordTableColumns.KIND_NUMBER;
    }
}
