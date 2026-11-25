package com.shrimpfarm.app.feedtable;

/**
 *  tapped-cell 分流 + 编辑事件转发。
 *
 * <p>从 {@link RecordTableView} 抽出这块纯胶水逻辑，让视图留在上限以内；不含绘制、几何和手势。</p>
 */
final class RecordTableBridge implements OnRecordCellTapListener {

    private final RecordTableView table;
    private RecordCellEditor editor;
    private OnRecordCellTapListener externalTapListener;
    private OnRecordCellEditListener editListener;

    RecordTableBridge(RecordTableView table) {
        this.table = table;
    }

    void setEditor(RecordCellEditor editor) {
        this.editor = editor;
    }

    void setOnCellTapListener(OnRecordCellTapListener listener) {
        this.externalTapListener = listener;
    }

    void setEditListener(OnRecordCellEditListener listener) {
        this.editListener = listener;
    }

    @Override
    public void onCellTap(int day, String columnId) {
        RecordTableColumns columns = table.getColumns();
        if (columns != null) {
            int column = columns.indexOf(columnId);
            if (column >= 0 && columns.kinds[column] == RecordTableColumns.KIND_NUMBER) {
                editor.begin(day, column);
            } else {
                // 产品格 / 备注格 / 只读格：先结算并收起当前编辑（产品弹窗由 R6/R7 接）
                editor.dismiss();
            }
        }
        if (externalTapListener != null) {
            externalTapListener.onCellTap(day, columnId);
        }
    }

    @Override
    public void onCellLongPress(int day, String columnId) {
        editor.commitNow();
        if (externalTapListener != null) {
            externalTapListener.onCellLongPress(day, columnId);
        }
    }

    /** IME「下一项」：找下一格 → 先结算 → 滚进可视区 → 再开编辑器；表尾则收起 */
    void handleEditNext(int day, int column) {
        RecordTableColumns columns = table.getColumns();
        RecordTextProvider provider = table.getTextProvider();
        if (columns == null || provider == null || !table.allowNextAction()) {
            return;
        }
        RecordCellNavigator.Target target = RecordCellNavigator.next(
                columns, table.getDayCount(), day, column,
                (d, c) -> provider.isCellEditable(d, columns.ids[c]));
        if (target == null) {
            // 表尾：这是最后一次编辑，提交并把键盘收起来
            editor.dismiss();
            return;
        }
        // end() 提交旧格但保留键盘；editing 归零后 reveal 的 syncBounds 不会误收起
        editor.end();
        table.revealCell(target.day, target.column);
        editor.begin(target.day, target.column);
    }

    void notifyEditBegin(int day, String columnId) {
        if (editListener != null) {
            editListener.onCellEditBegin(day, columnId);
        }
    }

    void notifyEditCommitted(int day, int column, String text) {
        RecordTableColumns columns = table.getColumns();
        if (editListener == null || columns == null
                || column < 0 || column >= columns.count()) {
            return;
        }
        editListener.onCellCommitted(day, columns.ids[column], text);
    }

    void notifyEditEnd(int day, int column) {
        RecordTableColumns columns = table.getColumns();
        if (editListener == null || columns == null
                || column < 0 || column >= columns.count()) {
            return;
        }
        editListener.onCellEditEnd(day, columns.ids[column]);
    }
}
