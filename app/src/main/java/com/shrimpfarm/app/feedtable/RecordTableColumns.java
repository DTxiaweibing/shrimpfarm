package com.shrimpfarm.app.feedtable;

/**
 * 记录表的列模型：13 个横向滚动列 + 1 个冻结日期列 + 6 格表头。
 *
 * <p>横向坐标约定：日期列冻结在最左，永远占 {@code [0, dateWidth)}，不随 scrollX 移动；
 * 其余 13 列按 {@link #ids} 顺序从左到右排，未滚动时的左边界是
 * {@code dateWidth + Σ(前面各列宽度)}。本类只描述「有哪些列、多宽、属于哪个子行」，
 * 滚动换算与命中判定归 {@link RecordTableMetrics}。</p>
 *
 * <p>纵向约定：一天 = 2 个等高子行（上子行 {@link #SUB_TOP} / 下子行 {@link #SUB_BOTTOM}），
 * 备注列纵跨两子行（{@link #SUB_SPAN}）。</p>
 *
 * <p>纯逻辑，不引用任何 Android 类型，可在普通 JVM 单测里直接构造。</p>
 */
public final class RecordTableColumns {

    /** 上子行 */
    public static final int SUB_TOP = 0;
    /** 下子行 */
    public static final int SUB_BOTTOM = 1;
    /** 跨两子行（备注列） */
    public static final int SUB_SPAN = -1;

    /** 数值格：直接输入数字 */
    public static final int KIND_NUMBER = 0;
    /** 产物格：选料 + 用量 */
    public static final int KIND_PRODUCT = 1;
    /** 备注格：多行文本，纵跨两子行 */
    public static final int KIND_REMARK = 2;

    /** 冻结日期列的 id，不在 {@link #ids} 里（日期列不可编辑） */
    public static final String DATE = "date";
    public static final String BREAKFAST = "breakfast";
    public static final String LUNCH = "lunch";
    public static final String DINNER = "dinner";
    public static final String NIGHT_SNACK = "nightSnack";
    public static final String WATER_MIX1 = "waterMix1";
    public static final String WATER_MIX2 = "waterMix2";
    public static final String MIX1 = "mix1";
    public static final String MIX2 = "mix2";
    public static final String MIX3 = "mix3";
    public static final String MIX4 = "mix4";
    public static final String WATER_MIX3 = "waterMix3";
    public static final String WATER_MIX4 = "waterMix4";
    public static final String REMARK = "remark";

    /** 滚动列的固定顺序，同时决定横向先后。id 与 DayRecord 字段名一一对应。 */
    private static final String[] ID_TEMPLATE = {
            BREAKFAST, LUNCH, DINNER, NIGHT_SNACK,
            WATER_MIX1, WATER_MIX2,
            MIX1, MIX2, MIX3, MIX4, WATER_MIX3, WATER_MIX4,
            REMARK,
    };

    /** 与 {@link #ID_TEMPLATE} 平行：0 上子行、1 下子行、-1 跨两子行 */
    private static final int[] SUB_ROW_TEMPLATE = {
            SUB_TOP, SUB_TOP, SUB_TOP, SUB_TOP,
            SUB_TOP, SUB_TOP,
            SUB_BOTTOM, SUB_BOTTOM, SUB_BOTTOM, SUB_BOTTOM, SUB_BOTTOM, SUB_BOTTOM,
            SUB_SPAN,
    };

    /** 与 {@link #ID_TEMPLATE} 平行 */
    private static final int[] KIND_TEMPLATE = {
            KIND_NUMBER, KIND_NUMBER, KIND_NUMBER, KIND_NUMBER,
            KIND_PRODUCT, KIND_PRODUCT,
            KIND_PRODUCT, KIND_PRODUCT, KIND_PRODUCT, KIND_PRODUCT,
            KIND_PRODUCT, KIND_PRODUCT,
            KIND_REMARK,
    };

    /** 13 个滚动列的 id，下标即横向列下标 */
    public final String[] ids;
    /** 与 {@link #ids} 平行：0 / 1 / {@link #SUB_SPAN} */
    public final int[] subRows;
    /** 与 {@link #ids} 平行：各列宽度（px） */
    public final int[] widths;
    /** 与 {@link #ids} 平行：{@link #KIND_NUMBER} / {@link #KIND_PRODUCT} / {@link #KIND_REMARK} */
    public final int[] kinds;

    /** 冻结日期列宽度（= cellWidth，与现版日期格等宽） */
    public final int dateWidth;
    /** 单个子行高度；一天 = 2 个子行 */
    public final int subRowHeight;
    /** 表头高度，表头下方才是表体 */
    public final int headerHeight;

    private final Header[] headers;

    private RecordTableColumns(int cellWidth, int remarkWidth,
                               int subRowHeight, int headerHeight) {
        this.dateWidth = cellWidth;
        this.subRowHeight = subRowHeight;
        this.headerHeight = headerHeight;
        this.ids = ID_TEMPLATE.clone();
        this.subRows = SUB_ROW_TEMPLATE.clone();
        this.kinds = KIND_TEMPLATE.clone();
        this.widths = new int[ID_TEMPLATE.length];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = i == widths.length - 1 ? remarkWidth : cellWidth;
        }
        this.headers = buildHeaders();
    }

    /**
     * @param cellWidth    普通格宽度；日期列也用它
     * @param remarkWidth  备注列宽度（纵跨两子行）
     * @param subRowHeight 子行高度
     * @param headerHeight 表头高度
     */
    public static RecordTableColumns create(int cellWidth, int remarkWidth,
                                            int subRowHeight, int headerHeight) {
        return new RecordTableColumns(cellWidth, remarkWidth, subRowHeight, headerHeight);
    }

    /** 滚动列数量（13，不含日期列） */
    public int count() {
        return ids.length;
    }

    /**
     * @return 列下标；找不到返回 -1（日期列、未知 id 都返回 -1）
     */
    public int indexOf(String id) {
        if (id == null) {
            return -1;
        }
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** 一天的高度 = 2 个子行 */
    public int dayHeight() {
        return subRowHeight * 2;
    }

    /** 13 个滚动列的宽度之和，不含冻结日期列 */
    public int totalWidth() {
        int sum = 0;
        for (int width : widths) {
            sum += width;
        }
        return sum;
    }

    /**
     * 表头 6 格。调用方只读，所以每次给一份拷贝，避免被就地改动。
     */
    public Header[] headers() {
        return headers.clone();
    }

    private static Header[] buildHeaders() {
        return new Header[]{
                new Header("feeding_header_breakfast", 0, 1),
                new Header("feeding_header_lunch", 1, 1),
                new Header("feeding_header_dinner", 2, 1),
                new Header("feeding_header_night_snack", 3, 1),
                // 「调水」横跨 waterMix1 + waterMix2
                new Header("feeding_header_water", 4, 2),
                new Header("feeding_header_remark", 12, 1),
        };
    }

    /**
     * 表头一格：文本资源名 + 起始列 + 跨列数。
     *
     * <p>日期列的表头不在这里，绘制方直接用 {@code R.string.header_date}。</p>
     */
    public static final class Header {

        /** 字符串资源名，不含 {@code "R.string."} 前缀 */
        public final String textKey;
        /** 起始列下标（滚动列下标） */
        public final int startColumn;
        /** 跨几列 */
        public final int span;

        public Header(String textKey, int startColumn, int span) {
            this.textKey = textKey;
            this.startColumn = startColumn;
            this.span = span;
        }
    }
}
