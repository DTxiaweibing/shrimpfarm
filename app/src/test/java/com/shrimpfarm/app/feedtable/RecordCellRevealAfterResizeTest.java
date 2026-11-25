package com.shrimpfarm.app.feedtable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.shrimpfarm.app.table.RevealMath;

import org.junit.Test;

/**
 * Critical 回归：键盘弹出把表挤矮后，正在编辑的靠后格必须能被滚回可视区，
 * 否则 {@code RecordCellEditor.syncBounds} 会把它判为不可见而直接收起（违反 A7 #05）。
 *
 * <p>这里用纯几何把 View 侧 {@code keepEditingCellVisible → revealCell → RevealMath.scrollY}
 * 依赖的链条钉死：矮视图下沿用旧滚动会裁掉格子，按新 bodyHeight 重算 reveal 则格子完整可见。</p>
 */
public class RecordCellRevealAfterResizeTest {

    private static final int DAY_COUNT = 20;
    private static final int EDIT_DAY = 15;
    private static final int CELL_W = 100;
    private static final int REMARK_W = 300;
    private static final int SUB_ROW_H = 40;
    private static final int HEADER_H = 40;

    private static RecordTableMetrics metrics(int viewHeight) {
        RecordTableColumns columns =
                RecordTableColumns.create(CELL_W, REMARK_W, SUB_ROW_H, HEADER_H);
        RecordTableMetrics m = new RecordTableMetrics();
        m.rebuild(columns, DAY_COUNT, 800, viewHeight);
        return m;
    }

    @Test
    public void oldScrollClipsEditedCellAfterKeyboardShrinksView() {
        RecordTableMetrics tall = metrics(400);
        int revealOnTall = RevealMath.scrollY(EDIT_DAY, SUB_ROW_H * 2, tall.bodyHeight,
                0, tall.maxScrollY);

        // 键盘弹出：视图由 400 缩到 200，bodyHeight 缩水但滚动值还停在原处
        RecordTableMetrics shortView = metrics(200);
        int clippedTop = shortView.yOfDay(EDIT_DAY, revealOnTall);
        assertTrue("沿用旧滚动时编辑格应落到矮视图下方（证明必须 reveal）",
                clippedTop >= shortView.viewHeight);
    }

    @Test
    public void revealWithNewBodyHeightBringsEditedCellBackIntoView() {
        RecordTableMetrics tall = metrics(400);
        int oldScroll = RevealMath.scrollY(EDIT_DAY, SUB_ROW_H * 2, tall.bodyHeight,
                0, tall.maxScrollY);

        RecordTableMetrics shortView = metrics(200);
        int newScroll = RevealMath.scrollY(EDIT_DAY, SUB_ROW_H * 2,
                shortView.bodyHeight, oldScroll, shortView.maxScrollY);
        int top = shortView.yOfDay(EDIT_DAY, newScroll);
        int bottom = top + SUB_ROW_H * 2;

        assertTrue("编辑格顶部应在表头下方", top >= HEADER_H);
        assertTrue("编辑格底部应在可视区内（不被裁 → syncBounds 不会 dismiss）",
                bottom <= shortView.viewHeight);
        assertEquals("reveal 结果应落在滚动范围内", newScroll,
                shortView.clampScrollY(newScroll));
    }
}
