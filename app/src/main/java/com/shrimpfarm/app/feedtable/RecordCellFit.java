package com.shrimpfarm.app.feedtable;

/**
 * 单元格显示策略的纯决策：字号、行数、留白、光标。
 *
 * <p>只做整数 / 浮点运算，不引用 Android 类型，可在普通 JVM 单测里钉死。
 * 绘制层按这里的结果去改 Paint / EditText，不再各自拍脑袋定常量。</p>
 */
public final class RecordCellFit {

    /** 行高约为字号的 1.2 倍，与 Android Paint 的 FontMetrics 量级一致 */
    public static final float LINE_HEIGHT_FACTOR = 1.2f;
    /** 产品格两行、每行四个汉字 */
    public static final int PRODUCT_LINE_COUNT = 2;
    public static final int PRODUCT_CHARS_PER_LINE = 4;
    /** 断行缓冲上限：备注 / 日期按格高最多排这么多行 */
    public static final int MAX_TEXT_LINES = 16;
    /** 备注 / 产品格文字可一直排到边框，不留内边距（与旧版 EditText padding=0 一致） */
    public static final float NO_INSET_PX = 0f;
    /** 可编辑格必须显式显示光标，避免部分软键盘 / 主题下光标不可见 */
    public static final boolean SHOW_CURSOR = true;
    /** 光标橘红色：白底上对比足够，不依赖主题的 colorAccent */
    public static final int CURSOR_COLOR = 0xFFFF5722;
    /** 光标宽度 dp：与网格线同量级，粗到能看见 */
    public static final float CURSOR_WIDTH_DP = 2f;
    /** {@link android.widget.TextView#setTextCursorDrawable(int)} 的起始 API */
    public static final int CURSOR_PUBLIC_API = 29;
    /** 备注格的显示与编辑字号都跟随产品格自适应结果 */
    public static final boolean REMARK_FOLLOWS_PRODUCT_TEXT_SIZE = true;

    private RecordCellFit() {
    }

    /** minSdk 28 走主题兜底；29 起可再显式调用公开 API 钉住 drawable */
    public static boolean usesPublicCursorApi(int sdkInt) {
        return sdkInt >= CURSOR_PUBLIC_API;
    }

    /**
     * 编辑器字号策略：备注 / 产品跟随产品格自适应字号，数字格保留自身字号。
     *
     * <p>返回值单位 px，调用方用 {@code COMPLEX_UNIT_PX} 设置，避免 sp 缩放把字号又放大。</p>
     */
    public static float editorTextSizePx(int kind, float numberTextSizePx, float productTextSizePx) {
        if (REMARK_FOLLOWS_PRODUCT_TEXT_SIZE
                && (kind == RecordTableColumns.KIND_REMARK
                    || kind == RecordTableColumns.KIND_PRODUCT)) {
            return productTextSizePx;
        }
        return numberTextSizePx;
    }

    /**
     * 产品格字号：保证每行 4 个汉字、两行不超格高，同时不放大原始字号。
     *
     * @return 整 px，最小 1；退化入参不会让字号变成 0 或负数
     */
    public static int productTextSizePx(float baseTextSizePx, float cellWidthPx,
                                        float cellHeightPx, float defaultInsetPx) {
        float usableWidth = cellWidthPx - 2f * defaultInsetPx;
        float byWidth = Math.max(0f, usableWidth) / PRODUCT_CHARS_PER_LINE;
        float byHeight = cellHeightPx / (PRODUCT_LINE_COUNT * LINE_HEIGHT_FACTOR);
        float size = Math.min(baseTextSizePx, Math.min(byWidth, byHeight));
        if (!Float.isFinite(size) || size < 1f) {
            return 1;
        }
        return (int) size;
    }

    /** 按格高算最多几行；至少 1 行，最多 {@link #MAX_TEXT_LINES}。 */
    public static int maxLinesForHeight(float cellHeightPx, float textSizePx) {
        float lineHeight = textSizePx * LINE_HEIGHT_FACTOR;
        if (!Float.isFinite(lineHeight) || lineHeight <= 0f) {
            return 1;
        }
        int lines = (int) (cellHeightPx / lineHeight);
        if (lines < 1) {
            return 1;
        }
        return Math.min(lines, MAX_TEXT_LINES);
    }

    /** 备注 / 产品格不留 padding（旧版 EditText 就是 0）；数字格沿用网格留白。 */
    public static float insetPxForKind(int kind, float defaultInsetPx) {
        return kind == RecordTableColumns.KIND_NUMBER ? defaultInsetPx : NO_INSET_PX;
    }
}
