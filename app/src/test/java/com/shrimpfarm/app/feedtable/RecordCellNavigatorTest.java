package com.shrimpfarm.app.feedtable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * {@link RecordCellNavigator} 的纯逻辑单测：列优先顺序、末日回卷、跳过产品格 / 不可编辑格、无下一格。
 */
public class RecordCellNavigatorTest {

    private static final int DAYS = 3;

    private static RecordTableColumns columns() {
        return RecordTableColumns.create(100, 200, 40, 40);
    }

    /** 全部可编辑 */
    private static final RecordCellNavigator.Editable ALL =
            (day, column) -> true;

    @Test
    public void sameColumnMovesToNextDay() {
        RecordCellNavigator.Target t = RecordCellNavigator.next(columns(), DAYS, 0, 0, ALL);
        assertTarget(1, 0, t);
    }

    @Test
    public void lastDayWrapsToFirstDayOfNextColumn() {
        RecordCellNavigator.Target t = RecordCellNavigator.next(columns(), DAYS, 2, 0, ALL);
        assertTarget(0, 1, t);
    }

    @Test
    public void skipsProductColumns() {
        // 列 4..11 全是 KIND_PRODUCT；从列 3 末日应直接跳到备注列 12 的第 1 天
        RecordCellNavigator.Target t = RecordCellNavigator.next(columns(), DAYS, 2, 3, ALL);
        assertTarget(0, 12, t);
    }

    @Test
    public void skipsNonEditableCells() {
        // 列 0 的第 1、2 天不可编辑，其余列全部可编辑：从 (0,0) 应跳到列 1 第 1 天
        RecordCellNavigator.Editable onlyFirstDayOfCol0 = (day, column) ->
                column != 0 || day == 0;
        RecordCellNavigator.Target t = RecordCellNavigator.next(
                columns(), DAYS, 0, 0, onlyFirstDayOfCol0);
        assertTarget(0, 1, t);
    }

    @Test
    public void skipsNonEditableCellWithinSameColumn() {
        // 列 0 第 1 天不可编辑、第 2 天可编辑：从 (0,0) 应落到 (2,0)
        RecordCellNavigator.Editable skipDay1 = (day, column) -> !(column == 0 && day == 1);
        RecordCellNavigator.Target t = RecordCellNavigator.next(
                columns(), DAYS, 0, 0, skipDay1);
        assertTarget(2, 0, t);
    }

    @Test
    public void noNextReturnsNull() {
        // 备注列最后一天，之后没有列了
        assertNull(RecordCellNavigator.next(columns(), DAYS, 2, 12, ALL));
        // 单天数据，第一个可编辑列也不是起点时同样没有下一格
        assertNull(RecordCellNavigator.next(columns(), 1, 0, 12, ALL));
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
    public void walkFromFirstCellCoversEveryEditableCellInColumnOrder() {
        // 起点 (0,0) 已算访问过；expected 是随后每次 next() 应返回的格
        int[][] expected = {
                {1, 0}, {2, 0},
                {0, 1}, {1, 1}, {2, 1},
                {0, 2}, {1, 2}, {2, 2},
                {0, 3}, {1, 3}, {2, 3},
                {0, 12}, {1, 12}, {2, 12},
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
        assertEquals("day", day, t.day);
        assertEquals("column", column, t.column);
    }
}
