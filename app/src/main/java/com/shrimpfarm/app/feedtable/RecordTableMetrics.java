package com.shrimpfarm.app.feedtable;

/**
 * 记录表的几何度量与命中判定：把屏幕坐标换成「第几天 / 哪个子行 / 哪个槽位 → 哪个列」。
 *
 * <p>坐标约定（单位 px，原点在表体左上角）：</p>
 * <pre>
 * x：日期列固定 [0, dateWidth)；槽位 x = dateWidth + Σ(前面的槽宽) - scrollX
 *    （上下子行共用槽 0..5，备注独占槽 6）
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
        /** 逻辑列下标（不含冻结日期列） */
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
     * 槽位的左边界（已含 dateWidth 偏移与 scrollX）。
     *
     * @return 越界（{@code slot < 0} 或 {@code slot >= SLOT_COUNT}）返回 -1
     */
    public int xOfSlot(int slot, int scrollX) {
        if (slot < 0 || slot >= RecordTableColumns.SLOT_COUNT) {
            return -1;
        }
        int x = columns.dateWidth - scrollX;
        for (int i = 0; i < slot; i++) {
            x += columns.slotWidths[i];
        }
        return x;
    }

    /**
     * 逻辑列的左边界（含 dateWidth 偏移与 scrollX）；同槽的上下两列 x 相同。
     *
     * @return 越界（{@code column < 0} 或 {@code column >= count()}）返回 -1
     */
    public int xOf(int column, int scrollX) {
        if (column < 0 || column >= columns.count()) {
            return -1;
        }
        return xOfSlot(columns.slots[column], scrollX);
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
     * x 落在哪个横向槽位。
     *
     * @return 日期列内（{@code x < dateWidth}，被冻结列盖住）、超出最后一个槽、槽宽退化时返回 -1
     */
    public int slotAt(float x, int scrollX) {
        int dateWidth = columns.dateWidth;
        if (x < dateWidth) {
            return -1;
        }
        int left = dateWidth - scrollX;
        for (int slot = 0; slot < RecordTableColumns.SLOT_COUNT; slot++) {
            int width = columns.slotWidths[slot];
            if (x >= left && x < left + width) {
                return slot;
            }
            left += width;
        }
        return -1;
    }

    /**
     * 命中判定：先定槽位，再由「槽位 + 物理子行」定列。
     *
     * <p>上下子行共用槽 0..5，所以同一 x 在上子行命中 {@code breakfast}、在下子行命中
     * {@code mix1}；备注槽纵跨两子行，上下都命中 {@code remark} 且 subRow 为
     * {@link RecordTableColumns#SUB_SPAN}。</p>
     *
     * @return 不命中返回 null
     */
    public Hit cellAt(float x, float y, int scrollX, int scrollY) {
        int slot = slotAt(x, scrollX);
        if (slot < 0) {
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
        int column = columns.columnAtSlot(slot, subRow);
        if (column < 0) {
            return null;
        }
        return new Hit(day, columns.subRows[column], column);
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
