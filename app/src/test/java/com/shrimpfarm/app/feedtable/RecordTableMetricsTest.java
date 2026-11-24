package com.shrimpfarm.app.feedtable;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * 记录表几何度量的回归测试。
 *
 * <p>基准布局：cellWidth=100、remarkWidth=300、dateWidth=100、subRowHeight=40、headerHeight=40，
 * 因此 dayHeight=80、totalWidth=1500。列 x 的基准：col0 左 100、col1 左 200、col12 左 1300。</p>
 */
public class RecordTableMetricsTest {

    private static final int CELL_W = 100;
    private static final int REMARK_W = 300;
    private static final int SUB_ROW_H = 40;
    private static final int HEADER_H = 40;

    private static RecordTableColumns columns() {
        return RecordTableColumns.create(CELL_W, REMARK_W, SUB_ROW_H, HEADER_H);
    }

    private static RecordTableMetrics metrics(int dayCount, int viewWidth, int viewHeight) {
        RecordTableMetrics m = new RecordTableMetrics();
        m.rebuild(columns(), dayCount, viewWidth, viewHeight);
        return m;
    }

    private static void assertHit(String what, RecordTableMetrics.Hit hit,
                                  int day, int subRow, int column) {
        assertNotNull(what, hit);
        assertEquals(what + " day", day, hit.day);
        assertEquals(what + " subRow", subRow, hit.subRow);
        assertEquals(what + " column", column, hit.column);
    }

    @Test
    public void columnXAccumulatesFromDateWidth() {
        RecordTableMetrics m = metrics(1, 400, 200);
        assertEquals(100, m.xOf(0, 0));
        assertEquals(200, m.xOf(1, 0));
        // 前 12 列各 100 宽：100 + 1200
        assertEquals(1300, m.xOf(12, 0));
        // 越界列
        assertEquals(-1, m.xOf(13, 0));
        assertEquals(-1, m.xOf(-1, 0));
    }

    @Test
    public void dateColumnStaysFrozen() {
        RecordTableMetrics m = metrics(1, 400, 200);
        assertEquals(0, m.dateX());
        assertEquals(100, m.dateWidth());
        // 滚动列随 scrollX 左移，日期列宽度不变
        assertEquals(50, m.xOf(0, 50));
        assertEquals(100, m.dateWidth());
        assertEquals(0, m.dateX());
    }

    @Test
    public void subRowBoundariesFollowHeaderAndDayHeight() {
        RecordTableMetrics m = metrics(2, 400, 200);
        assertEquals(40, m.yOfDay(0, 0));
        assertEquals(120, m.yOfDay(1, 0));
        // 表头内不算任何一天
        assertEquals(-1, m.dayAt(39, 0));
        assertEquals(0, m.dayAt(40, 0));
        assertEquals(RecordTableColumns.SUB_TOP, m.subRowAt(40, 0));
        assertEquals(RecordTableColumns.SUB_TOP, m.subRowAt(79, 0));
        assertEquals(RecordTableColumns.SUB_BOTTOM, m.subRowAt(80, 0));
        assertEquals(RecordTableColumns.SUB_BOTTOM, m.subRowAt(119, 0));
        assertEquals(1, m.dayAt(120, 0));
    }

    @Test
    public void hitsTopSubRowColumn() {
        RecordTableMetrics m = metrics(2, 400, 200);
        assertHit("breakfast 上子行", m.cellAt(150, 60, 0, 0),
                0, RecordTableColumns.SUB_TOP, 0);
    }

    @Test
    public void rejectsColumnOnWrongSubRow() {
        RecordTableMetrics m = metrics(2, 400, 200);
        // breakfast 只在上子行，下子行同一 x 不算命中
        assertNull("breakfast 下子行", m.cellAt(150, 100, 0, 0));
        // mix1 只在下子行
        assertHit("mix1 下子行", m.cellAt(750, 100, 0, 0),
                0, RecordTableColumns.SUB_BOTTOM, 6);
        assertNull("mix1 上子行", m.cellAt(750, 60, 0, 0));
    }

    @Test
    public void rejectsDateColumnAndHeader() {
        RecordTableMetrics m = metrics(2, 400, 200);
        // 日期列不可编辑
        assertNull("日期列", m.cellAt(50, 60, 0, 0));
        // 表头不是表体
        assertNull("表头", m.cellAt(150, 20, 0, 0));
    }

    @Test
    public void remarkHitsBothSubRowsAsSpan() {
        RecordTableMetrics m = metrics(2, 400, 200);
        assertHit("备注上子行", m.cellAt(1350, 60, 0, 0),
                0, RecordTableColumns.SUB_SPAN, 12);
        assertHit("备注下子行", m.cellAt(1350, 100, 0, 0),
                0, RecordTableColumns.SUB_SPAN, 12);
    }

    @Test
    public void outOfRangeHitsReturnNull() {
        RecordTableMetrics m = metrics(2, 400, 200);
        // 第 3 天位置：只有 2 天
        assertNull("第 3 天", m.cellAt(150, 200, 0, 0));
        assertEquals(-1, m.columnAt(5000, 0));
        // 最后一列右边界 1600 是开区间
        assertEquals(12, m.columnAt(1599, 0));
        assertEquals(-1, m.columnAt(1600, 0));
    }

    @Test
    public void columnBoundariesAreHalfOpen() {
        RecordTableMetrics m = metrics(2, 400, 200);
        assertEquals(-1, m.columnAt(99, 0));
        assertEquals(0, m.columnAt(100, 0));
        assertEquals(0, m.columnAt(199, 0));
        assertEquals(1, m.columnAt(200, 0));
        assertEquals(11, m.columnAt(1299, 0));
        assertEquals(12, m.columnAt(1300, 0));
    }

    @Test
    public void rebuildComputesScrollRange() {
        RecordTableMetrics m = metrics(3, 400, 200);
        assertEquals(160, m.bodyHeight);
        // 3*80 - 160
        assertEquals(80, m.maxScrollY);
        // 1500 - (400 - 100)
        assertEquals(1200, m.maxScrollX);
        assertEquals(0, m.clampScrollX(-5));
        assertEquals(1200, m.clampScrollX(99999));
        assertEquals(0, m.clampScrollY(-1));
        assertEquals(80, m.clampScrollY(999));
    }

    @Test
    public void wideViewHasNoHorizontalScroll() {
        RecordTableMetrics m = metrics(1, 2000, 200);
        assertEquals(0, m.maxScrollX);
        assertEquals(0, m.clampScrollX(50));
    }

    @Test
    public void hitsFollowScrollOffsets() {
        RecordTableMetrics m = metrics(2, 400, 200);
        // scrollX=100 后 col0 左边界落到 0，整列被日期列盖住，不可命中
        assertEquals(0, m.xOf(0, 100));
        assertEquals(-1, m.columnAt(50, 100));
        assertHit("滚动后命中 lunch", m.cellAt(150, 60, 100, 0),
                0, RecordTableColumns.SUB_TOP, 1);
        // scrollY=80 后第 0 天整体上移一天高
        assertEquals(-40, m.yOfDay(0, 80));
        assertEquals(1, m.dayAt(40, 80));
    }

    @Test
    public void visibleDayRangeCoversBody() {
        RecordTableMetrics m = metrics(10, 400, 200);
        assertEquals(0, m.firstVisibleDay(0));
        // 160/80 = 2 天，覆盖 day0..day1
        assertEquals(1, m.lastVisibleDay(0));
        assertEquals(1, m.firstVisibleDay(80));
        assertEquals(2, m.lastVisibleDay(80));
        assertEquals(9, m.lastVisibleDay(99999));
        assertEquals(9, m.firstVisibleDay(99999));
    }

    @Test
    public void emptyDayCountHasNoBody() {
        RecordTableMetrics m = metrics(0, 400, 200);
        assertEquals(0, m.maxScrollY);
        assertEquals(-1, m.dayAt(40, 0));
        assertEquals(0, m.firstVisibleDay(0));
        assertEquals(-1, m.lastVisibleDay(0));
        assertNull("空数据", m.cellAt(150, 60, 0, 0));
    }

    @Test
    public void rebuildDoesNotAccumulateState() {
        RecordTableMetrics m = metrics(5, 400, 200);
        assertEquals(5 * 80 - 160, m.maxScrollY);
        m.rebuild(columns(), 1, 400, 200);
        assertEquals(1, m.dayCount);
        assertEquals(160, m.bodyHeight);
        // 1*80 - 160 < 0 → 夹到 0，不残留上一次的 240
        assertEquals(0, m.maxScrollY);
        assertEquals(1200, m.maxScrollX);
    }

    @Test
    public void columnModelIsFrozen() {
        RecordTableColumns c = columns();
        assertEquals(13, c.count());
        assertEquals(13, c.ids.length);
        assertEquals(12, c.indexOf(RecordTableColumns.REMARK));
        assertEquals(12, c.indexOf("remark"));
        assertEquals(6, c.indexOf(RecordTableColumns.MIX1));
        assertEquals("mix1", c.ids[6]);
        assertEquals(-1, c.indexOf(RecordTableColumns.DATE));
        assertEquals(-1, c.indexOf("date"));
        assertEquals(-1, c.indexOf("nope"));
        assertEquals(-1, c.indexOf(null));
        assertEquals(RecordTableColumns.SUB_SPAN, c.subRows[12]);
        assertEquals(RecordTableColumns.SUB_TOP, c.subRows[0]);
        assertEquals(RecordTableColumns.SUB_BOTTOM, c.subRows[6]);
        assertEquals(RecordTableColumns.KIND_NUMBER, c.kinds[0]);
        assertEquals(RecordTableColumns.KIND_NUMBER, c.kinds[3]);
        assertEquals(RecordTableColumns.KIND_PRODUCT, c.kinds[4]);
        assertEquals(RecordTableColumns.KIND_REMARK, c.kinds[12]);
        assertEquals(12 * CELL_W + REMARK_W, c.totalWidth());
        assertEquals(2 * SUB_ROW_H, c.dayHeight());
        assertEquals(100, c.dateWidth);
        assertEquals(40, c.subRowHeight);
        assertEquals(40, c.headerHeight);
    }

    @Test
    public void headersDescribeFixedLayout() {
        RecordTableColumns.Header[] headers = columns().headers();
        assertEquals(6, headers.length);
        assertEquals("feeding_header_breakfast", headers[0].textKey);
        assertEquals(0, headers[0].startColumn);
        assertEquals(1, headers[0].span);
        assertEquals("feeding_header_night_snack", headers[3].textKey);
        assertEquals(3, headers[3].startColumn);
        assertEquals("feeding_header_water", headers[4].textKey);
        assertEquals(4, headers[4].startColumn);
        assertEquals(2, headers[4].span);
        assertEquals("feeding_header_remark", headers[5].textKey);
        assertEquals(12, headers[5].startColumn);
        assertEquals(1, headers[5].span);
    }

    @Test
    public void headersAreReadOnlyCopy() {
        RecordTableColumns c = columns();
        c.headers()[0] = null;
        assertNotNull(c.headers()[0]);
    }

    @Test
    public void columnIdsAreInFrozenOrder() {
        // 列顺序就是用户输入的落点：任何两列互换都会把数字写进错误的 DayRecord 字段
        assertArrayEquals(new String[]{
                RecordTableColumns.BREAKFAST,
                RecordTableColumns.LUNCH,
                RecordTableColumns.DINNER,
                RecordTableColumns.NIGHT_SNACK,
                RecordTableColumns.WATER_MIX1,
                RecordTableColumns.WATER_MIX2,
                RecordTableColumns.MIX1,
                RecordTableColumns.MIX2,
                RecordTableColumns.MIX3,
                RecordTableColumns.MIX4,
                RecordTableColumns.WATER_MIX3,
                RecordTableColumns.WATER_MIX4,
                RecordTableColumns.REMARK,
        }, columns().ids);
    }

    @Test
    public void yOfDayRejectsOutOfRangeDays() {
        RecordTableMetrics m = metrics(2, 400, 200);
        assertEquals(-1, m.yOfDay(-1, 0));
        assertEquals(-1, m.yOfDay(2, 0));
        assertEquals(-1, m.yOfDay(m.dayCount, 0));
        // 边界内的两端仍然有效
        assertEquals(HEADER_H, m.yOfDay(0, 0));
        assertEquals(HEADER_H + SUB_ROW_H * 2, m.yOfDay(1, 0));
    }

    @Test
    public void subRowAtRejectsHeaderAndPastLastDay() {
        RecordTableMetrics m = metrics(2, 400, 200);
        // 表头内（headerHeight=40）
        assertEquals(-1, m.subRowAt(HEADER_H - 1, 0));
        // 最后一天之后
        assertEquals(-1, m.subRowAt(HEADER_H + m.dayCount * SUB_ROW_H * 2, 0));
        // 最后一天最后一个子行仍算命中
        assertEquals(RecordTableColumns.SUB_BOTTOM,
                m.subRowAt(HEADER_H + m.dayCount * SUB_ROW_H * 2 - 1, 0));
    }

    @Test
    public void lastVisibleDayFallsBackWhenBodyIsEmpty() {
        RecordTableMetrics m = new RecordTableMetrics();
        // viewHeight 正好等于表头高 → bodyHeight=0
        m.rebuild(columns(), 5, 400, HEADER_H);
        assertEquals(0, m.bodyHeight);
        assertEquals(m.firstVisibleDay(0), m.lastVisibleDay(0));
        assertEquals(0, m.lastVisibleDay(0));
    }

    @Test
    public void narrowerViewThanDateColumnScrollsWholeTable() {
        RecordTableColumns c = columns();
        RecordTableMetrics m = new RecordTableMetrics();
        m.rebuild(c, 3, c.dateWidth - 1, 200);
        // 可视宽被 max(0, viewWidth - dateWidth) 夹成 0 → 整表可滚
        assertEquals(c.totalWidth(), m.maxScrollX);
        assertEquals(0, m.clampScrollX(-1));
        assertEquals(c.totalWidth(), m.clampScrollX(99999));
    }
}
