package com.shrimpfarm.app.feedtable;

/**
 * 记录表的单元格点击 / 长按回调。R4 的编辑器与 R6 的产品选择器都挂在这里。
 *
 * <p>只在命中某个真实单元格时回调；表头、冻结日期列、空白处一律不回调。</p>
 */
public interface OnRecordCellTapListener {

    /** 单元格单击。columnId 取 RecordTableColumns 的字符串常量（如 "breakfast" / "remark"） */
    void onCellTap(int day, String columnId);

    /** 单元格长按（约 500ms，不移动）。 */
    void onCellLongPress(int day, String columnId);
}
