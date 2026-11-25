package com.shrimpfarm.app.feedtable;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;

/**
 * 记录表的视觉参数：颜色、字号、2px 网格线，以及一组在绘制期复用的 {@link Paint}。
 *
 * <p>数值逐条照抄现版 {@code FeedingRecordActivity}（见每个常量的注释），升级后用户不应
 * 觉得换了页面。2px 网格线是 <b>px</b> 而不是 dp —— 现版
 * {@code createCellBorder} 用的是 {@code setStroke(2, …)}。</p>
 *
 * <p>本类只在构造时 new Paint，绘制期只读；{@link #fillPaint} 的 color 由绘制方按格改，
 * 因为改 color 不分配对象。</p>
 */
public final class RecordTableStyle {

    /** 表头底色（setupHeader / headerFixedDate） */
    public static final int HEADER_BG = 0xFF2D84C2;
    /** 表头文字（createHeaderCell） */
    public static final int HEADER_TEXT_COLOR = 0xFFFFFFFF;
    /** 表头字号 12sp（createHeaderCell） */
    public static final float HEADER_TEXT_SP = 12f;

    /** 日期列底色（createRowView） */
    public static final int DATE_BG = 0xFFE8F5E9;
    /** 日期列文字（createRowView） */
    public static final int DATE_TEXT_COLOR = 0xFF2E7D32;
    /** 日期列字号 13sp（createRowView） */
    public static final float DATE_TEXT_SP = 13f;

    /** 普通数字格文字（buildFeedEdit） */
    public static final int NUMBER_TEXT_COLOR = 0xFF444444;
    /** 普通数字格字号 16sp（buildFeedEdit） */
    public static final float NUMBER_TEXT_SP = 16f;

    /** 产品格文字（buildProductEdit） */
    public static final int PRODUCT_TEXT_COLOR = 0xFF000000;
    /** 产品格字号 12sp（buildProductEdit） */
    public static final float PRODUCT_TEXT_SP = 12f;

    /** 备注格文字（remark 单元格） */
    public static final int REMARK_TEXT_COLOR = 0xFF444444;
    /** 备注格字号 12sp（remark 单元格） */
    public static final float REMARK_TEXT_SP = 12f;

    /** 普通格底色（createCellBorder(0xFFFFFFFF)） */
    public static final int CELL_BG = 0xFFFFFFFF;
    /** 灰显格底色（bindNumberCell） */
    public static final int GRAY_BG = 0xFFF5F5F5;
    /** 灰显格文字（bindNumberCell） */
    public static final int GRAY_TEXT_COLOR = 0xFFCCCCCC;

    /** 网格线颜色（createCellBorder 的 setStroke(2, 0xFF000000)） */
    public static final int GRID_COLOR = 0xFF000000;
    /** 网格线宽度，单位 <b>px</b>（现版 setStroke(2, …)） */
    public static final int GRID_STROKE_PX = 2;

    /** 行高 40dp（现版 rowHeight = 40 * density） */
    public static final float ROW_HEIGHT_DP = 40f;

    /** 文本与格子边线的水平留白，等于网格线宽，保证字形不压线 */
    public static final float TEXT_INSET_PX = GRID_STROKE_PX;

    /** 铺底色用；color 由绘制方逐格改（改 color 不分配） */
    public final Paint fillPaint;
    /** 2px 黑边框；STROKE 画在格子内侧 */
    public final Paint gridPaint;

    public final TextSpec headerText;
    public final TextSpec dateText;
    public final TextSpec numberText;
    public final TextSpec productText;
    public final TextSpec remarkText;
    private final TextSpec numberGrayText;
    private final TextSpec productGrayText;
    private final TextSpec remarkGrayText;
    /** 产品格原始字号；每次 rebuild 都从它重算，避免多次缩小后无法恢复 */
    private final float productBaseTextSizePx;
    /** 最近一次 rebuild 应用的字号：产品与备注共用，编辑器也照这个值设 */
    private float appliedTextSizePx;

    public RecordTableStyle(Context context) {
        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(CELL_BG);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(GRID_STROKE_PX);
        gridPaint.setColor(GRID_COLOR);

        headerText = text(context, HEADER_TEXT_SP, HEADER_TEXT_COLOR, true);
        dateText = text(context, DATE_TEXT_SP, DATE_TEXT_COLOR, true);
        numberText = text(context, NUMBER_TEXT_SP, NUMBER_TEXT_COLOR, true);
        productText = text(context, PRODUCT_TEXT_SP, PRODUCT_TEXT_COLOR, false);
        remarkText = text(context, REMARK_TEXT_SP, REMARK_TEXT_COLOR, false);
        numberGrayText = text(context, NUMBER_TEXT_SP, GRAY_TEXT_COLOR, true);
        productGrayText = text(context, PRODUCT_TEXT_SP, GRAY_TEXT_COLOR, false);
        remarkGrayText = text(context, REMARK_TEXT_SP, GRAY_TEXT_COLOR, false);
        productBaseTextSizePx = productText.paint.getTextSize();
        appliedTextSizePx = productBaseTextSizePx;
    }

    /** dp 转 px；与现版 {@code (int)(v * density)} 一样截断 */
    public static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    /** 表头高度 = 40dp（现版表头 40dp） */
    public static int headerHeightPx(Context context) {
        return dp(context, ROW_HEIGHT_DP);
    }

    /** 子行高度 = 40dp（现版每行 40dp） */
    public static int subRowHeightPx(Context context) {
        return dp(context, ROW_HEIGHT_DP);
    }

    /** 产品格原始字号（px）：绘制层按列宽缩小时从这里起算 */
    public float productBaseTextSizePx() {
        return productBaseTextSizePx;
    }

    /** 日期 / 备注格原始字号（px）：绘制层据此按格高算行数 */
    public float dateTextSizePx() {
        return dateText.paint.getTextSize();
    }

    public float remarkTextSizePx() {
        return remarkText.paint.getTextSize();
    }

    /** 数字格字号（px）：编辑器按 px 设置，避免 sp 缩放 */
    public float numberTextSizePx() {
        return numberText.paint.getTextSize();
    }

    /** 产品格当前字号（px）；备注显示与编辑都跟随它 */
    public float productTextSizePx() {
        return appliedTextSizePx;
    }

    /**
     * 按当前列宽统一缩字号；产品与备注的普通 / 灰显四份 Paint 一起更新，
     * 保证「调水动保/拌料动宝」和备注显示、编辑时字号一致。
     */
    public void applyShrunkTextSize(int sizePx) {
        productText.setTextSizePx(sizePx);
        productGrayText.setTextSizePx(sizePx);
        remarkText.setTextSizePx(sizePx);
        remarkGrayText.setTextSizePx(sizePx);
        appliedTextSizePx = sizePx;
    }

    /** 按格类型与灰显状态取文本样式，绘制期只做字段选择 */
    public TextSpec textFor(int kind, boolean grayed) {
        if (grayed) {
            if (kind == RecordTableColumns.KIND_NUMBER) {
                return numberGrayText;
            }
            return kind == RecordTableColumns.KIND_REMARK ? remarkGrayText : productGrayText;
        }
        if (kind == RecordTableColumns.KIND_NUMBER) {
            return numberText;
        }
        return kind == RecordTableColumns.KIND_REMARK ? remarkText : productText;
    }

    private static TextSpec text(Context context, float sp, int color, boolean bold) {
        // 不用 DisplayMetrics.scaledDensity（API 36 起废弃），applyDimension 等价且不废弃
        float sizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                context.getResources().getDisplayMetrics());
        return new TextSpec(sizePx, color, bold);
    }

    /**
     * 一套文本样式：Paint + 预取的 FontMetrics。
     *
     * <p>FontMetrics 在构造时 {@code getFontMetrics(fm)} 填入复用对象，绘制期取基线不再分配。</p>
     */
    public static final class TextSpec {

        public final Paint paint;
        private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();

        TextSpec(float sizePx, int color, boolean bold) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sizePx);
            paint.setColor(color);
            if (bold) {
                paint.setTypeface(Typeface.DEFAULT_BOLD);
            }
            paint.getFontMetrics(fontMetrics);
        }

        /**
         * 让整块文本的垂直中心落在 {@code top + height / 2} 时，第一行的基线。
         *
         * @param lineCount 已排好的行数（&gt;= 1）
         */
        public float blockBaselineFor(float top, float height, int lineCount) {
            float block = lineCount * lineHeight();
            return top + (height - block) / 2f - fontMetrics.ascent;
        }

        /** 行高 = descent - ascent */
        public float lineHeight() {
            return fontMetrics.descent - fontMetrics.ascent;
        }

        /** 改字号后必须重取 FontMetrics，否则基线会按旧字号算 */
        public void setTextSizePx(float sizePx) {
            paint.setTextSize(sizePx);
            paint.getFontMetrics(fontMetrics);
        }
    }
}
