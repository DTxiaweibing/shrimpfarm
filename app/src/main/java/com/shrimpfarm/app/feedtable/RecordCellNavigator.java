package com.shrimpfarm.app.feedtable;

/**
 * IME「下一项」的目标查找：纯逻辑，可单测。
 *
 * <p>顺序按 A4 #4「列优先」：同一列的第 2 天…最后一天，再回卷到下一可编辑列的第 1 天；
 * 产品格与 {@code isEditable == false} 的格跳过。全部走完返回 {@code null}，调用方收起键盘。</p>
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
        for (int col = column; col < columns.count(); col++) {
            if (columns.kinds[col] == RecordTableColumns.KIND_PRODUCT) {
                continue;
            }
            int firstDay = col == column ? day + 1 : 0;
            for (int d = firstDay; d < dayCount; d++) {
                if (editable.isEditable(d, col)) {
                    return new Target(d, col);
                }
            }
        }
        return null;
    }
}
