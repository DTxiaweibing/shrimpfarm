package com.shrimpfarm.app.feedtable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * {@link RecordCellNavigator} 的纯逻辑单测：行优先顺序、跳过产品格 / 备注格 / 不可编辑格、无下一格。
 */
public class RecordCellNavigatorTest {

    private static final int DAYS = 3;

    private static RecordTableColumns columns() {
        return RecordTableColumns.create(100, 200, 40, 40);
    }

    /** 全部可编辑 */
    private static final RecordCellNavigator.Editable ALL = (day, column) -> true;

    @Test
    public void nextCellInSameRowMovesRight() {
        RecordCellNavigator.Target t = RecordCellNavigator.next(columns(), DAYS, 0, 0, ALL);
        assertTarget(0, 1, t);
    }

    @Test
    public void lastColumnInRowWrapsToFirstEditableColumnOfNextDay() {
        // 列 4..11 是产品格、列 12 是备注格，都不参与「下一项」；行末直接到下一天的第一格
        RecordCellNavigator.Target t = RecordCellNavigator.next(columns(), DAYS, 0, 3, ALL);
        assertTarget(1, 0, t);
    }

    @Test
    public void skipsProductAndRemarkColumns() {
        // 从备注格本身出发也跳过它，落到下一天第一格（正常路径备注用「完成」，不会触发 NEXT）
        RecordCellNavigator.Target t = RecordCellNavigator.next(columns(), DAYS, 0, 12, ALL);
        assertTarget(1, 0, t);
    }

    @Test
    public void skipsNonEditableCellWithinSameRow() {
        RecordCellNavigator.Editable skipSecondColumn = (day, column) -> !(day == 0 && column == 1);
        RecordCellNavigator.Target t = RecordCellNavigator.next(
                columns(), DAYS, 0, 0, skipSecondColumn);
        assertTarget(0, 2, t);
    }

    @Test
    public void skipsWholeRowWhenNoEditableCellLeft() {
        RecordCellNavigator.Editable onlyFirstRow = (day, column) -> day == 0;
        RecordCellNavigator.Target t = RecordCellNavigator.next(
                columns(), DAYS, 0, 3, onlyFirstRow);
        assertNull(t);
    }

    @Test
    public void noNextReturnsNull() {
        // 最后一天最后一个可编辑格，之后没有行了
        assertNull(RecordCellNavigator.next(columns(), DAYS, 2, 3, ALL));
        // 单天数据，起点之后没有可编辑格
        assertNull(RecordCellNavigator.next(columns(), 1, 0, 3, ALL));
    }

    @Test
    public void outOfRangeReturnsNull() {
        RecordTableColumns cols = columns();
        assertNull(RecordCellNavigator.next(cols, DAYS, -1, 0, ALL));
        assertNull(RecordCellNavigator.next(cols, DAYS, 3, 0, ALL));
        assertNull(RecordCellNavigator.next(cols, DAYS, 0, -1, ALL));
        assertNull(RecordCellNavigator.next(cols, DAYS, 0, 13, ALL));
        assertNull(RecordCellNavigator.next(cols, 0, 0, 0, ALL));
        assertNull(RecordCellNavigator.next(cols, DAYS, 0, 0, null));
        assertNull(RecordCellNavigator.next(null, DAYS, 0, 0, ALL));
    }

    @Test
    public void walkFromFirstCellCoversEveryEditableCellInRowOrder() {
        // 起点 (0,0) 已算访问过；expected 是随后每次 next() 应返回的格
        int[][] expected = {
                {0, 1}, {0, 2}, {0, 3},
                {1, 0}, {1, 1}, {1, 2}, {1, 3},
                {2, 0}, {2, 1}, {2, 2}, {2, 3},
        };
        int day = 0;
        int column = 0;
        for (int[] exp : expected) {
            RecordCellNavigator.Target t = RecordCellNavigator.next(
                    columns(), DAYS, day, column, ALL);
            assertTarget(exp[0], exp[1], t);
            day = t.day;
            column = t.column;
        }
        assertNull(RecordCellNavigator.next(columns(), DAYS, day, column, ALL));
    }

    private static void assertTarget(int day, int column, RecordCellNavigator.Target t) {
        assertNotNull("target", t);
        assertEquals("day", day, t.day);
        assertEquals("column", column, t.column);
    }
}
