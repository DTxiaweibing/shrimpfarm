package com.shrimpfarm.app.feedtable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewGroup;

import com.shrimpfarm.app.table.NextActionGate;
import com.shrimpfarm.app.table.RevealMath;

/**
 * 记录表的绘制层：一个自绘 {@link ViewGroup}，不创建任何子 View。
 *
 * <p>画什么、怎么裁由本类决定，数据全部经 {@link RecordTextProvider} 取，几何全部经
 * {@link RecordTableMetrics} 算。手势（拖动 / 惯性 / 点击长按）全部委托给
 * {@link RecordTableGesture}，本类只留委托；编辑器留给 R4。</p>
 *
 * <p>绘制顺序固定（后画的盖住先画的）：</p>
 * <ol>
 *   <li>整块填白；</li>
 *   <li>表头（y ∈ [0, headerHeight)）：底色蓝，日期列表头冻结，其余 6 格随 scrollX；</li>
 *   <li>表体滚动格：只画 {@link RecordTableMetrics#firstVisibleDay}..{@link RecordTableMetrics#lastVisibleDay}，
 *       裁到 x ≥ dateWidth、y ≥ headerHeight；</li>
 *   <li>冻结日期列：裁到 x &lt; dateWidth，<b>最后画</b>，滚动内容压不过来。</li>
 * </ol>
 *
 * <p>{@code onDraw} 不 new 对象：Paint / RectF / 断行缓冲全部是字段，复用。</p>
 */
public class RecordTableView extends ViewGroup {

    private final RecordTableStyle style;
    private final RecordTableMetrics metrics = new RecordTableMetrics();
    private final RecordCellText cellText = new RecordCellText();
    /** 复用的格子矩形，绘制期只 set 不 new */
    private final RectF cellRect = new RectF();
    /** 拖动 / 惯性 / 点击长按全部在这里 */
    private final RecordTableGesture gesture;
    /** 全表唯一的 EditText（唯一子 View） */
    private final RecordCellEditor editor;
    /** 命中分流 + 编辑事件转发；从本类抽出，避免视图逼近 500 行上限 */
    private final RecordTableBridge bridge;
    /** IME「下一项」节流时间戳（单调时钟） */
    private long lastNextAt;

    private RecordTextProvider textProvider;
    private RecordTableSizing sizing;
    private RecordTableColumns columns;
    /** 表头 6 格；rebuild 时缓存，避免 onDraw 里调 headers() 的拷贝分配 */
    private RecordTableColumns.Header[] headers;
    private int dayCount;
    /** 实际绘制用的滚动值（已按当前度量夹住） */
    private int tableScrollX;
    private int tableScrollY;
    /** 最近一次 setTableScroll 的原始请求值；rebuild 后据此重新夹，避免尺寸变化前设置的值被丢弃 */
    private int requestedScrollX;
    private int requestedScrollY;

    public RecordTableView(Context context) {
        this(context, null);
    }

    public RecordTableView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecordTableView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        style = new RecordTableStyle(context);
        // ViewGroup 默认跳过 onDraw，自绘必须显式打开
        setWillNotDraw(false);
        // 撞边即停，不要 EdgeEffect 的拉伸/回弹
        setOverScrollMode(OVER_SCROLL_NEVER);
        gesture = new RecordTableGesture(this);
        bridge = new RecordTableBridge(this);
        gesture.setListener(bridge);
        editor = new RecordCellEditor(this);
        bridge.setEditor(editor);
        addView(editor.view());
    }

    // ==================== 外部接口 ====================

    public void setTextProvider(RecordTextProvider provider) {
        this.textProvider = provider;
        invalidate();
    }

    public void setDayCount(int dayCount) {
        this.dayCount = Math.max(0, dayCount);
        rebuild();
    }

    /** 由 Activity 按屏幕算好传入（见 {@link RecordTableSizing#compute}） */
    public void setSizing(RecordTableSizing sizing) {
        this.sizing = sizing;
        rebuild();
    }

    /** 数据或尺寸变了：重算 metrics 并重绘 */
    public void notifyDataChanged() {
        rebuild();
    }

    /** R3 会用它驱动滚动；先原样存下请求值，再按当前度量夹一次 */
    public void setTableScroll(int x, int y) {
        requestedScrollX = x;
        requestedScrollY = y;
        applyScrollClamp();
    }

    public int getTableScrollX() {
        return tableScrollX;
    }

    public int getTableScrollY() {
        return tableScrollY;
    }

    /** R4 / R5 命中与定位要用 */
    public RecordTableMetrics getMetrics() {
        return metrics;
    }

    /** 列模型；未 setSizing 前为 null */
    public RecordTableColumns getColumns() {
        return columns;
    }

    /** R4 / R6 挂编辑器与产品选择器 */
    public void setOnCellTapListener(OnRecordCellTapListener listener) {
        bridge.setOnCellTapListener(listener);
    }

    /** 编辑事件（提交 / 开始 / 结束）；产品格点击仍走 {@link #setOnCellTapListener} */
    public void setEditListener(OnRecordCellEditListener listener) {
        bridge.setEditListener(listener);
    }

    /** 提交并收起编辑器；Activity 的返回键 / onPause 会调用 */
    public void endEditing() {
        editor.dismiss();
    }

    public boolean isEditing() {
        return editor.isEditing();
    }

    /**
     * 命中格的屏幕矩形，裁到日期列右侧、表头下方、视图范围内。
     *
     * @return 格完全不可见（或参数越界）时返回 false，{@code out} 不保证有意义
     */
    public boolean cellRect(int day, int column, Rect out) {
        if (columns == null || day < 0 || day >= dayCount
                || column < 0 || column >= columns.count()) {
            return false;
        }
        int scrollX = metrics.clampScrollX(tableScrollX);
        int scrollY = metrics.clampScrollY(tableScrollY);
        int left = metrics.xOf(column, scrollX);
        int right = left + columns.widths[column];
        int subRow = columns.subRows[column];
        int top = metrics.yOfDay(day, scrollY);
        int height = subRow == RecordTableColumns.SUB_SPAN
                ? columns.dayHeight() : columns.subRowHeight;
        if (subRow == RecordTableColumns.SUB_BOTTOM) {
            top += columns.subRowHeight;
        }
        int bottom = top + height;
        int clipLeft = columns.dateWidth;
        int clipTop = columns.headerHeight;
        if (right <= clipLeft || bottom <= clipTop || left >= getWidth() || top >= getHeight()) {
            return false;
        }
        out.set(Math.max(left, clipLeft), Math.max(top, clipTop),
                Math.min(right, getWidth()), Math.min(bottom, getHeight()));
        return out.width() > 0 && out.height() > 0;
    }

    /** 提交后只重绘这一格；越界 / 不可见时整屏兜底 */
    void invalidateCell(int day, int column) {
        Rect r = new Rect();
        if (cellRect(day, column, r)) {
            invalidate(r.left - 1, r.top - 1, r.right + 1, r.bottom + 1);
        } else {
            invalidate();
        }
    }

    /** 编辑器读取单元格文本用；不对外暴露 */
    RecordTextProvider getTextProvider() {
        return textProvider;
    }

    /** 编辑器回调用的桥接（R5 换格 / notify 都走它） */
    RecordTableBridge editBridge() {
        return bridge;
    }

    int getDayCount() {
        return dayCount;
    }

    /** IME「下一项」节流：与计算器同值 120ms */
    boolean allowNextAction() {
        long now = SystemClock.uptimeMillis();
        if (!NextActionGate.allow(now, lastNextAt)) {
            return false;
        }
        lastNextAt = now;
        return true;
    }

    /**
     * 尺寸变化（多半是键盘弹出把视图挤矮）后把正在编辑的格滚回可视区；
     * 返回 false 表示格子彻底留不住，编辑器该收起。对齐计算器 keepEditingCellVisible。
     */
    boolean keepEditingCellVisible(int day, int column) {
        if (columns == null || day < 0 || day >= dayCount
                || column < 0 || column >= columns.count()) {
            return false;
        }
        revealCell(day, column);
        return cellRect(day, column, new Rect());
    }

    /** 换格后把目标格滚进可视区（对齐计算器的 ensureEditorVisible） */
    void revealCell(int day, int column) {
        if (columns == null || day < 0 || day >= dayCount
                || column < 0 || column >= columns.count()) {
            return;
        }
        int newY = RevealMath.scrollY(day, columns.dayHeight(), metrics.bodyHeight,
                tableScrollY, metrics.maxScrollY);
        int baseLeft = metrics.xOf(column, 0);
        int newX = RevealMath.scrollX(baseLeft, columns.widths[column], columns.dateWidth,
                getWidth(), tableScrollX, metrics.maxScrollX, false);
        if (newX != tableScrollX || newY != tableScrollY) {
            setTableScroll(newX, newY);
        }
    }

    // ==================== 手势委托 ====================

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // A4 #6：手指按下先把当前编辑提交掉，拖动/换格都不会丢字
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            editor.commitNow();
        }
        return gesture.onTouchEvent(ev);
    }

    @Override
    protected void onDetachedFromWindow() {
        // 视图离开窗口：提交未落库的编辑，并停掉帧回调
        editor.dismiss();
        gesture.stopFling();
        super.onDetachedFromWindow();
    }

    // ==================== measure / layout ====================

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
                measureSize(widthMeasureSpec, getSuggestedMinimumWidth()),
                measureSize(heightMeasureSpec, getSuggestedMinimumHeight()));
    }

    /**
     * EXACTLY / AT_MOST 都用 spec 给的尺寸（AT_MOST 填满可用空间，不能退化成 0）；
     * UNSPECIFIED 才回落到 suggestedMinimum。
     */
    private static int measureSize(int measureSpec, int suggestedMinimum) {
        if (MeasureSpec.getMode(measureSpec) == MeasureSpec.UNSPECIFIED) {
            return suggestedMinimum;
        }
        return MeasureSpec.getSize(measureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // 唯一子 View 是编辑器：由它自己贴到命中格上（不在这里 add 任何东西）
        editor.syncBounds();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuild();
    }

    /** 尺寸 / 列宽 / 天数任一变化都从这里整份重算，不累积状态 */
    private void rebuild() {
        columns = sizing == null ? null : RecordTableColumns.create(
                sizing.cellWidth,
                sizing.remarkWidth,
                RecordTableStyle.subRowHeightPx(getContext()),
                RecordTableStyle.headerHeightPx(getContext()));
        if (columns != null) {
            int productWidth = columns.widths[columns.indexOf(RecordTableColumns.WATER_MIX1)];
            int size = RecordCellFit.productTextSizePx(style.productBaseTextSizePx(),
                    productWidth, columns.subRowHeight, RecordCellFit.NO_INSET_PX);
            style.applyShrunkTextSize(size);
        }
        headers = columns == null ? null : columns.headers();
        metrics.rebuild(columns, dayCount, getWidth(), getHeight());
        // 尺寸变化多半是键盘弹出把表挤矮了：先把正在编辑的格滚回可视区，
        // 否则紧接着的 applyScrollClamp → syncBounds 会把它判为不可见而直接收起（对齐计算器 onMetricsChanged）
        editor.onMetricsChanged();
        // 早先设置的滚动值在这里按新度量重新夹一次；请求值本身保留，尺寸变大后能恢复
        applyScrollClamp();
        invalidate();
    }

    /** 编辑器按列类型取字号：数字用自己的，备注跟随产品自适应结果（单位 px） */
    float editorTextSizePx(int kind) {
        return RecordCellFit.editorTextSizePx(kind,
                style.numberTextSizePx(), style.productTextSizePx());
    }

    /** 用当前度量把请求值夹成可画值；无变化时不重复重绘 */
    private void applyScrollClamp() {
        int clampedX = metrics.clampScrollX(requestedScrollX);
        int clampedY = metrics.clampScrollY(requestedScrollY);
        if (clampedX == tableScrollX && clampedY == tableScrollY) {
            return;
        }
        tableScrollX = clampedX;
        tableScrollY = clampedY;
        invalidate();
        // 编辑器跟着滚动走；格滚没了它会自己收起
        editor.syncBounds();
    }

    // ==================== 绘制 ====================

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (columns == null || headers == null || width <= 0 || height <= 0
                || columns.totalWidth() <= 0) {
            return;
        }
        // 1. 整块填白
        style.fillPaint.setColor(RecordTableStyle.CELL_BG);
        canvas.drawRect(0, 0, width, height, style.fillPaint);

        int scrollX = metrics.clampScrollX(tableScrollX);
        int scrollY = metrics.clampScrollY(tableScrollY);
        drawHeader(canvas, width, scrollX);
        drawBody(canvas, width, height, scrollX, scrollY);
    }

    /** 2. 表头：底色蓝 + 冻结日期列表头 + 6 个随 scrollX 的格，文字不随 scrollY 动 */
    private void drawHeader(Canvas canvas, int width, int scrollX) {
        int headerHeight = columns.headerHeight;
        if (headerHeight <= 0) {
            return;
        }
        style.fillPaint.setColor(RecordTableStyle.HEADER_BG);
        canvas.save();
        canvas.clipRect(0, 0, width, headerHeight);
        canvas.drawRect(0, 0, width, headerHeight, style.fillPaint);
        cellRect.set(0, 0, columns.dateWidth, headerHeight);
        drawCellFrame(canvas, cellRect, 1, RecordTableStyle.TEXT_INSET_PX,
                style.headerText, headerText(RecordTableColumns.DATE));

        canvas.save();
        canvas.clipRect(columns.dateWidth, 0, width, headerHeight);
        for (int i = 0; i < headers.length; i++) {
            int left = metrics.xOfSlot(headers[i].startSlot, scrollX);
            int w = spanWidth(headers[i]);
            if (left >= width || left + w <= columns.dateWidth) {
                continue;
            }
            cellRect.set(left, 0, left + w, headerHeight);
            drawCellFrame(canvas, cellRect, 1, RecordTableStyle.TEXT_INSET_PX,
                    style.headerText, headerText(headers[i].textKey));
        }
        canvas.restore();
        canvas.restore();
    }

    /** 3 + 4. 表体：先画滚动格，再最后画冻结日期列 */
    private void drawBody(Canvas canvas, int width, int height, int scrollX, int scrollY) {
        int first = metrics.firstVisibleDay(scrollY);
        int last = metrics.lastVisibleDay(scrollY);
        if (last < first) {
            return; // dayCount == 0
        }
        int bodyTop = columns.headerHeight;

        canvas.save();
        canvas.clipRect(columns.dateWidth, bodyTop, width, height);
        for (int day = first; day <= last; day++) {
            drawScrollCells(canvas, day, width, height, scrollX, scrollY);
        }
        canvas.restore();

        canvas.save();
        canvas.clipRect(0, bodyTop, columns.dateWidth, height);
        for (int day = first; day <= last; day++) {
            drawDateCell(canvas, day, height, scrollY);
        }
        canvas.restore();
    }

    private void drawScrollCells(Canvas canvas, int day, int width, int height,
                                 int scrollX, int scrollY) {
        int dayTop = metrics.yOfDay(day, scrollY);
        int dayHeight = columns.dayHeight();
        if (dayTop >= height || dayTop + dayHeight <= columns.headerHeight) {
            return;
        }
        for (int col = 0; col < columns.count(); col++) {
            int left = metrics.xOf(col, scrollX);
            int w = columns.widths[col];
            if (left >= width || left + w <= columns.dateWidth) {
                continue;
            }
            int subRow = columns.subRows[col];
            int top = dayTop;
            int h = dayHeight;
            if (subRow != RecordTableColumns.SUB_SPAN) {
                h = columns.subRowHeight;
                if (subRow == RecordTableColumns.SUB_BOTTOM) {
                    top = dayTop + h;
                }
            }
            drawBodyCell(canvas, day, col, left, top, w, h);
        }
    }

    private void drawBodyCell(Canvas canvas, int day, int column, int left, int top, int w, int h) {
        String id = columns.ids[column];
        boolean grayed = textProvider != null && textProvider.isCellGrayed(day, id);
        int kind = columns.kinds[column];
        int maxLines = kind == RecordTableColumns.KIND_NUMBER ? 1
                : kind == RecordTableColumns.KIND_REMARK
                        ? RecordCellFit.maxLinesForHeight(h, style.remarkTextSizePx())
                        : RecordCellFit.PRODUCT_LINE_COUNT;
        float inset = RecordCellFit.insetPxForKind(kind, RecordTableStyle.TEXT_INSET_PX);
        style.fillPaint.setColor(grayed ? RecordTableStyle.GRAY_BG : RecordTableStyle.CELL_BG);
        cellRect.set(left, top, left + w, top + h);
        drawCellFrame(canvas, cellRect, maxLines, inset,
                style.textFor(kind, grayed), cellTextOf(day, id));
    }

    private void drawDateCell(Canvas canvas, int day, int height, int scrollY) {
        int top = metrics.yOfDay(day, scrollY);
        int h = columns.dayHeight();
        if (top >= height || top + h <= columns.headerHeight) {
            return;
        }
        style.fillPaint.setColor(RecordTableStyle.DATE_BG);
        cellRect.set(0, top, columns.dateWidth, top + h);
        String text = textProvider == null ? null : textProvider.getDateText(day);
        int maxLines = RecordCellFit.maxLinesForHeight(h, style.dateTextSizePx());
        drawCellFrame(canvas, cellRect, maxLines, RecordTableStyle.TEXT_INSET_PX,
                style.dateText, text);
    }

    /** 底色（调用方已设好 fillPaint 颜色）→ 文本 → 2px 边框 */
    private void drawCellFrame(Canvas canvas, RectF rect, int maxLines, float inset,
                               RecordTableStyle.TextSpec spec, String text) {
        canvas.drawRect(rect, style.fillPaint);
        drawText(canvas, rect, spec, maxLines, inset, text);
        drawBorder(canvas, rect);
    }

    private void drawText(Canvas canvas, RectF rect, RecordTableStyle.TextSpec spec,
                          int maxLines, float inset, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float maxWidth = rect.width() - 2f * inset;
        if (maxWidth <= 0f) {
            return;
        }
        cellText.layout(text, spec.paint, maxWidth, maxLines);
        int lines = cellText.lineCount();
        if (lines == 0) {
            return;
        }
        // 备注 / 产品 inset=0：文字可排到边框；数字格仍按半根网格线裁，避免压线
        float clipInset = inset == 0f ? 0f : RecordTableStyle.GRID_STROKE_PX / 2f;
        canvas.save();
        canvas.clipRect(rect.left + clipInset, rect.top + clipInset,
                rect.right - clipInset, rect.bottom - clipInset);
        cellText.draw(canvas, text, spec.paint, rect.centerX(),
                spec.blockBaselineFor(rect.top, rect.height(), lines), spec.lineHeight());
        canvas.restore();
    }

    /** 2px 黑边：STROKE 居中，所以内缩 1px 画，整条边落在格子内侧 */
    private void drawBorder(Canvas canvas, RectF rect) {
        float half = RecordTableStyle.GRID_STROKE_PX / 2f;
        canvas.drawRect(rect.left + half, rect.top + half,
                rect.right - half, rect.bottom - half, style.gridPaint);
    }

    // ==================== 小工具 ====================

    private String headerText(String key) {
        return textProvider == null ? null : textProvider.getHeaderText(key);
    }

    private String cellTextOf(int day, String columnId) {
        return textProvider == null ? null : textProvider.getCellText(day, columnId);
    }

    /** 表头一格横跨的宽度 = 覆盖的各槽宽之和 */
    private int spanWidth(RecordTableColumns.Header header) {
        int end = Math.min(header.startSlot + header.span, RecordTableColumns.SLOT_COUNT);
        int w = 0;
        for (int slot = header.startSlot; slot < end; slot++) {
            w += columns.slotWidths[slot];
        }
        return w;
    }
}
