package com.shrimpfarm.app.feedtable;

/**
 * 单元格编辑事件。R4 只触发 begin / committed / end，{@code onEditNext} 由 R5 接线。
 *
 * <p>全部回调都在主线程、由 {@link RecordTableView} 转发，宿主不需要再做线程切换。</p>
 */
public interface OnRecordCellEditListener {

    /** 编辑器已定位并弹出，开始编辑某格 */
    void onCellEditBegin(int day, String columnId);

    /** 文本提交（已归一化的最终值；空串表示清空） */
    void onCellCommitted(int day, String columnId, String text);

    /** IME「下一项」；R5 才接线 */
    void onEditNext(int day, String columnId);

    /** 编辑结束（收起 / 换格 / 失焦后），宿主可据此做一次 flush */
    void onCellEditEnd(int day, String columnId);
}
