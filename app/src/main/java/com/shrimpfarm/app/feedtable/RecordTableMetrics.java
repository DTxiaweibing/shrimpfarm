package com.shrimpfarm.app.feedtable;

/**
 * 记录表的几何度量与命中判定：把屏幕坐标换成「第几天 / 哪个子行 / 哪一列」。
 *
 * <p>坐标约定（单位 px，原点在表体左上角）：</p>
 * <pre>
 * x：日期列固定 [0, dateWidth)；滚动列 x = dateWidth + Σ(前面的列宽) - scrollX
 * y：表头固定 [0, headerHeight)；第 day 天顶部 y = headerHeight + day*dayHeight - scrollY
 * 一天 = 2 子行，子行高 subRowHeight
 * </pre>
 *
 * <p>纯计算，不碰 View、不存滚动位置：scrollX / scrollY 一律由调用方传入，所有结果都基于
 * 「已经 clamp 过的滚动值」推导；越界一律返回 -1 / null，不抛异常。</p>
 */
public final class RecordTableMetrics {

    /** 没 rebuild 之前的空列模型：任何命中都落空，避免空指针判断 */
    private static final RecordTableColumns EMPTY = RecordTableColumns.create(0, 0, 0, 0);

    /** 一次命中的结果。命中备注列时 subRow 为 {@link RecordTableColumns#SUB_SPAN}。 */
    public static final class Hit {

        /** 天下标 */
        public final int day;
        /** {@link RecordTableColumns#SUB_TOP} / {@link RecordTableColumns#SUB_BOTTOM} / {@link RecordTableColumns#SUB_SPAN} */
        public final int subRow;
        /** 滚动列下标（不含冻结日期列） */
        public final int column;

        public Hit(int day, int subRow, int column) {
            this.day = day;
            this.subRow = subRow;
            this.column = column;
        }
    }

    public int dayCount;
    public int viewWidth;
    public int viewHeight;
    /** 表体高度 = viewHeight - headerHeight，最小 0 */
    public int bodyHeight;
    public int maxScrollX;
    public int maxScrollY;

    private RecordTableColumns columns = EMPTY;

    /**
     * 重算派生尺寸。可反复调用（尺寸变化时），每次都从入参整份重算，不累积状态。
     */
    public void rebuild(RecordTableColumns columns, int dayCount, int viewWidth, int viewHeight) {
        this.columns = columns == null ? EMPTY : columns;
        this.dayCount = Math.max(0, dayCount);
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
        this.bodyHeight = Math.max(0, viewHeight - this.columns.headerHeight);
        int visibleWidth = Math.max(0, viewWidth - this.columns.dateWidth);
        this.maxScrollX = Math.max(0, this.columns.totalWidth() - visibleWidth);
        this.maxScrollY = this.dayCount <= 0
                ? 0
                : Math.max(0, this.dayCount * dayHeight() - bodyHeight);
    }

    /** 冻结日期列的左边界，恒为 0 */
    public int dateX() {
        return 0;
    }

    /** 冻结日期列宽度 */
    public int dateWidth() {
        return columns.dateWidth;
    }

    /**
     * 列的左边界（已含 dateWidth 偏移与 scrollX）。
     *
     * @return 越界（{@code column < 0} 或 {@code column >= count()}）返回 -1
     */
    public int xOf(int column, int scrollX) {
        if (column < 0 || column >= columns.count()) {
            return -1;
        }
        int x = columns.dateWidth - scrollX;
        for (int i = 0; i < column; i++) {
            x += columns.widths[i];
        }
        return x;
    }

    /**
     * 第 day 天顶部的 y。
     *
     * @return 越界（{@code day < 0} 或 {@code day >= dayCount}）返回 -1
     */
    public int yOfDay(int day, int scrollY) {
        if (day < 0 || day >= dayCount) {
            return -1;
        }
        return columns.headerHeight + day * dayHeight() - scrollY;
    }

    /**
     * 命中的天下标。
     *
     * @return 表头内、越界、或行高退化时返回 -1
     */
    public int dayAt(float y, int scrollY) {
        int dayHeight = dayHeight();
        if (dayHeight <= 0 || dayCount <= 0 || y < columns.headerHeight) {
            return -1;
        }
        int day = (int) ((y - columns.headerHeight + scrollY) / dayHeight);
        return day < 0 || day >= dayCount ? -1 : day;
    }

    /**
     * 命中的物理子行（只看 y，不看列）。
     *
     * @return {@link RecordTableColumns#SUB_TOP} / {@link RecordTableColumns#SUB_BOTTOM}；表头内或越界返回 -1
     */
    public int subRowAt(float y, int scrollY) {
        int dayHeight = dayHeight();
        if (dayHeight <= 0 || dayAt(y, scrollY) < 0) {
            return -1;
        }
        int offset = (int) ((y - columns.headerHeight + scrollY) % dayHeight);
        return offset / columns.subRowHeight <= 0
                ? RecordTableColumns.SUB_TOP
                : RecordTableColumns.SUB_BOTTOM;
    }

    /**
     * 命中的滚动列下标。
     *
     * @return 日期列内（{@code x < dateWidth}）、超出最后一列、行宽退化时返回 -1
     */
    public int columnAt(float x, int scrollX) {
        int dateWidth = columns.dateWidth;
        if (x < dateWidth) {
            return -1;
        }
        int left = dateWidth - scrollX;
        for (int i = 0; i < columns.count(); i++) {
            int width = columns.widths[i];
            if (x >= left && x < left + width) {
                return i;
            }
            left += width;
        }
        return -1;
    }

    /**
     * 命中判定：列、天、子行三者都对上才算命中。
     *
     * <p>备注列纵跨两子行，上下子行都返回 {@link RecordTableColumns#SUB_SPAN}；
     * 其余列只能命中它自己所在的子行（例如点上子行的 {@code mix1} 位置不算命中）。</p>
     *
     * @return 不命中返回 null
     */
    public Hit cellAt(float x, float y, int scrollX, int scrollY) {
        int column = columnAt(x, scrollX);
        if (column < 0) {
            return null;
        }
        int day = dayAt(y, scrollY);
        if (day < 0) {
            return null;
        }
        int subRow = subRowAt(y, scrollY);
        if (subRow < 0) {
            return null;
        }
        int columnSubRow = columns.subRows[column];
        if (columnSubRow == RecordTableColumns.SUB_SPAN) {
            return new Hit(day, RecordTableColumns.SUB_SPAN, column);
        }
        if (columnSubRow != subRow) {
            return null;
        }
        return new Hit(day, subRow, column);
    }

    public int clampScrollX(int scrollX) {
        return clamp(scrollX, 0, maxScrollX);
    }

    public int clampScrollY(int scrollY) {
        return clamp(scrollY, 0, maxScrollY);
    }

    /**
     * 可见区第一天的下标。
     *
     * @return 没有数据时返回 0（调用方仍需用 dayCount 判空）
     */
    public int firstVisibleDay(int scrollY) {
        int dayHeight = dayHeight();
        if (dayCount <= 0 || dayHeight <= 0) {
            return 0;
        }
        return clamp(scrollY / dayHeight, 0, dayCount - 1);
    }

    /**
     * 可见区最后一天的下标。
     *
     * @return 没有数据时返回 -1；表体高度为 0 时退化为 {@link #firstVisibleDay(int)}
     */
    public int lastVisibleDay(int scrollY) {
        int dayHeight = dayHeight();
        if (dayCount <= 0 || dayHeight <= 0) {
            return -1;
        }
        if (bodyHeight <= 0) {
            return firstVisibleDay(scrollY);
        }
        return clamp((scrollY + bodyHeight - 1) / dayHeight, 0, dayCount - 1);
    }

    private int dayHeight() {
        return columns.dayHeight();
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
