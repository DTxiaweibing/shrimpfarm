package com.shrimpfarm.app.feedtable;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.shrimpfarm.app.R;

/**
 * 全表唯一的单元格编辑器：一个 {@link EditText} 贴到当前命中的数字格上。
 *
 * <p>生命周期：{@link #begin} 打开 → 用户输入 → {@link #end} / {@link #dismiss} 收起。
 * 提交只发生在「换格 / 失焦 / 手指按下 / 收起」，<b>不逐字写库</b>；只有用户真的改过文本
 * （{@code dirty}）才回调 {@link OnRecordCellEditListener#onCellCommitted}。</p>
 *
 * <p>不自己算几何：矩形一律问 {@link RecordTableView#cellRect}，滚动/尺寸变化后由
 * {@link #syncBounds()} 重新贴回该格；格被裁成不可见就自动收起。</p>
 */
public final class RecordCellEditor {

    private static final int TEXT_COLOR = 0xFF444444;
    private static final int CELL_BG = 0xFFFFFFFF;
    private static final int GRID_COLOR = 0xFF000000;
    private static final int GRID_STROKE_PX = 2;
    private static final InputFilter[] NO_FILTERS = new InputFilter[0];

    private final RecordTableView table;
    private final Context context;
    private final EditText editText;
    private final Rect cellBounds = new Rect();

    private int day = -1;
    private int column = -1;
    /** 当前格的列类型（数字 / 备注），决定输入配置与提交方式 */
    private int kind = RecordTableColumns.KIND_NUMBER;
    private boolean editing;
    /** 程序化 setText 期间压住文本回调 */
    private boolean suppress;
    /** 用户是否真的改过文本；只是点开又走开不该提交 */
    private boolean dirty;

    RecordCellEditor(RecordTableView table) {
        this.table = table;
        this.context = table.getContext();
        // 主题里显式给 cursor drawable：minSdk 28 也能拿到橘红光标，不用反射私有字段
        this.editText = new EditText(new ContextThemeWrapper(context, R.style.RecordCellCursor));
        configure();
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppress || !editing) {
                    return;
                }
                dirty = true;
            }
        });
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && editing) {
                // 重新置一次才会重启闪烁，否则部分机型焦点到了也不画光标
                editText.setCursorVisible(RecordCellFit.SHOW_CURSOR);
                return;
            }
            if (!hasFocus && editing) {
                end();
            }
        });
        // 数字格「下一项」换格 / 备注格「完成」提交收起
        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (!editing) {
                return false;
            }
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                table.editBridge().handleEditNext(day, column);
                return true;
            }
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                dismiss();
                return true;
            }
            return false;
        });
    }

    private void configure() {
        editText.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                table.editorTextSizePx(RecordTableColumns.KIND_NUMBER));
        editText.setTextColor(TEXT_COLOR);
        editText.setTypeface(Typeface.DEFAULT_BOLD);
        editText.setGravity(Gravity.CENTER);
        editText.setPadding(0, 0, 0, 0);
        editText.setOverScrollMode(View.OVER_SCROLL_NEVER);
        editText.setBackground(buildCellBackground());
        editText.setVisibility(View.GONE);
    }

    /** 主题兜底已在构造时生效；29+ 用策略常量再显式钉一次，确保不被外部样式覆盖 */
    private void applyCursorDrawable() {
        if (RecordCellFit.usesPublicCursorApi(Build.VERSION.SDK_INT)) {
            editText.setTextCursorDrawable(buildCursorDrawable());
        }
    }

    /** 颜色 / 宽度取自 {@link RecordCellFit}，与 XML 兜底资源同值 */
    private Drawable buildCursorDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(RecordCellFit.CURSOR_COLOR);
        int widthPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                RecordCellFit.CURSOR_WIDTH_DP, context.getResources().getDisplayMetrics());
        drawable.setSize(widthPx, -1);
        return drawable;
    }

    /** 按列类型重配输入：数字单行 + NEXT，备注多行 + DONE；换格时调用，不留上一格状态 */
    private void applyKindConfig(int kind) {
        editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, table.editorTextSizePx(kind));
        if (RecordCellRules.multiLine(kind)) {
            editText.setSingleLine(false);
            editText.setMaxLines(
                    RecordCellFit.maxLinesForHeight(cellBounds.height(), editText.getTextSize()));
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            editText.setMovementMethod(android.text.method.ScrollingMovementMethod.getInstance());
        } else {
            editText.setMovementMethod(null);
            editText.setSingleLine(true);
            editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            // R5：软键盘右下角显示「下一项」，换格由 bridge.handleEditNext 负责
            editText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        }
        applyCursorDrawable();
        editText.setCursorVisible(RecordCellFit.SHOW_CURSOR);
    }

    private InputFilter[] filtersFor(int kind) {
        return RecordCellRules.filtersNumber(kind)
                ? new InputFilter[]{CellInputRules::filterNumber}
                : NO_FILTERS;
    }

    /** 与自绘网格一致：白底 + 2px 黑描边，不新增 drawable 资源 */
    private static GradientDrawable buildCellBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(CELL_BG);
        background.setStroke(GRID_STROKE_PX, GRID_COLOR);
        background.setCornerRadius(0f);
        return background;
    }

    EditText view() {
        return editText;
    }

    boolean isEditing() {
        return editing;
    }

    // ==================== 打开 / 收起 ====================

    /**
     * 打开某格的编辑器。只接受可编辑的数字格 / 备注格；正在编辑别的格时先提交再切换（不收起键盘）。
     */
    void begin(int day, int column) {
        RecordTableColumns columns = table.getColumns();
        RecordTextProvider provider = table.getTextProvider();
        if (columns == null || provider == null
                || column < 0 || column >= columns.count()
                || !RecordCellRules.opensEditor(columns.kinds[column])) {
            return;
        }
        String columnId = columns.ids[column];
        if (!provider.isCellEditable(day, columnId)) {
            return;
        }
        if (editing) {
            if (this.day == day && this.column == column) {
                syncBounds();
                return;
            }
            end();
        }
        if (!table.cellRect(day, column, cellBounds)) {
            return; // 格不可见（被冻结列/表头裁掉）：不打开，R5 的 reveal 之后才会遇到
        }
        this.day = day;
        this.column = column;
        this.kind = columns.kinds[column];
        applyKindConfig(kind);
        editing = true;
        dirty = false;

        suppress = true;
        try {
            // setText 也会过 InputFilter：先摘掉，历史数据（如 "12.34"）才不会被吞
            editText.setFilters(NO_FILTERS);
            String text = provider.getCellText(day, columnId);
            editText.setText(text == null ? "" : text);
            editText.setSelection(editText.getText().length());
            editText.setFilters(filtersFor(kind));
        } finally {
            suppress = false;
        }

        editText.setVisibility(View.VISIBLE);
        layoutInto(cellBounds);
        editText.requestFocus();
        showIme();
        table.editBridge().notifyEditBegin(day, columnId);
    }

    /** 提交（仅 dirty）并收起，不主动收键盘（换格时避免键盘闪一下） */
    void end() {
        if (!editing) {
            return;
        }
        commitInternal();
        editing = false;
        editText.setVisibility(View.GONE);
        int finishedDay = day;
        int finishedColumn = column;
        day = -1;
        column = -1;
        editText.clearFocus();
        table.editBridge().notifyEditEnd(finishedDay, finishedColumn);
    }

    /** 收起并收键盘：点非数字格、返回键、onPause、detach 时用 */
    void dismiss() {
        boolean wasEditing = editing;
        end();
        if (wasEditing) {
            hideIme();
        }
    }

    /** 手指按下时先把当前编辑提交掉（A4 #6），编辑器继续跟着该格 */
    void commitNow() {
        if (editing) {
            commitInternal();
        }
    }

    private void commitInternal() {
        if (!dirty || column < 0 || day < 0) {
            return;
        }
        dirty = false;
        String text = RecordCellRules.commitText(kind, editText.getText().toString());
        table.editBridge().notifyEditCommitted(day, column, text);
    }

    // ==================== 定位 ====================

    /**
     * 尺寸变化（键盘弹出挤矮视图）后先把编辑格滚回可视区再重新贴；
     * 留不住才收起。对齐计算器 CellEditorOverlay.onMetricsChanged。
     */
    void onMetricsChanged() {
        if (!editing || day < 0 || column < 0) {
            return;
        }
        if (!table.keepEditingCellVisible(day, column)) {
            dismiss();
            return;
        }
        syncBounds();
    }

    /** 滚动 / 尺寸变化后重新贴到该格；格被裁没了就收起并收键盘 */
    void syncBounds() {
        if (!editing || column < 0) {
            return;
        }
        if (!table.cellRect(day, column, cellBounds)) {
            dismiss();
            return;
        }
        layoutInto(cellBounds);
    }

    private void layoutInto(Rect bounds) {
        editText.measure(
                View.MeasureSpec.makeMeasureSpec(bounds.width(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(bounds.height(), View.MeasureSpec.EXACTLY));
        editText.layout(bounds.left, bounds.top, bounds.right, bounds.bottom);
    }

    // ==================== 键盘 ====================

    private void showIme() {
        editText.post(() -> {
            if (!editing || !editText.isAttachedToWindow()) {
                return;
            }
            InputMethodManager imm = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void hideIme() {
        InputMethodManager imm = (InputMethodManager)
                context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
        }
    }
}
