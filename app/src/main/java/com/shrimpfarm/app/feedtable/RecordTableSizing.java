package com.shrimpfarm.app.feedtable;

/**
 * 记录表的列宽决策：按屏幕宽度与对角线英寸数算出 cellWidth / remarkWidth。
 *
 * <p>公式来自计划 A-3（已冻结），与现版 {@code FeedingRecordActivity:104-117} 完全一致：</p>
 * <pre>
 * 手机（对角线 &lt; 7 英寸）：cellWidth = (int)(screenWidth / 6.5)，remarkWidth = (int)(screenWidth / 3.5)
 * 平板（对角线 &gt;= 7 英寸）：cellWidth = screenWidth / 9，remarkWidth = cellWidth * 2
 * </pre>
 *
 * <p>两个宽度都是<b>截断</b>取整，不是四舍五入（否则列宽会与现版差 1px）。
 * 纯逻辑，不引用任何 Android 类型，可在普通 JVM 单测里直接构造。</p>
 */
public final class RecordTableSizing {

    /** 平板判定阈值：对角线英寸数 &gt;= 该值即平板 */
    public static final double TABLET_MIN_DIAGONAL_INCHES = 7.0;

    /** 普通格宽度（px），日期列同宽 */
    public final int cellWidth;
    /** 备注列宽度（px） */
    public final int remarkWidth;
    /** 是否走平板公式 */
    public final boolean tablet;

    private RecordTableSizing(int cellWidth, int remarkWidth, boolean tablet) {
        this.cellWidth = cellWidth;
        this.remarkWidth = remarkWidth;
        this.tablet = tablet;
    }

    /**
     * 按屏幕宽度与对角线英寸数算列宽。
     *
     * @param screenWidth    屏幕宽（px），{@code <= 0} 时两个宽度都返回 0，不抛异常
     * @param diagonalInches 屏幕对角线英寸数；{@code >= 7.0} 走平板公式
     */
    public static RecordTableSizing compute(int screenWidth, double diagonalInches) {
        boolean tablet = diagonalInches >= TABLET_MIN_DIAGONAL_INCHES;
        if (screenWidth <= 0) {
            return new RecordTableSizing(0, 0, tablet);
        }
        int cellWidth;
        int remarkWidth;
        if (tablet) {
            cellWidth = screenWidth / 9;
            remarkWidth = cellWidth * 2;
        } else {
            // 与现版 (int)(screenWidth / 6.5) 一致：先算 double 再截断
            cellWidth = (int) (screenWidth / 6.5);
            remarkWidth = (int) (screenWidth / 3.5);
        }
        return new RecordTableSizing(cellWidth, remarkWidth, tablet);
    }
}
