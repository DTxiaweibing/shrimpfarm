package com.shrimpfarm.app.feedtable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.ViewGroup;

/**
 * 记录表的绘制层：一个自绘 {@link ViewGroup}，不创建任何子 View。
 *
 * <p>画什么、怎么裁由本类决定，数据全部经 {@link RecordTextProvider} 取，几何全部经
 * {@link RecordTableMetrics} 算。本卡不含手势、惯性、编辑器；{@code setTableScroll} 供 R3 驱动滚动，
 * 编辑器留给 R4。</p>
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

    private RecordTextProvider textProvider;
    private RecordTableSizing sizing;
    private RecordTableColumns columns;
    /** 表头 6 格；rebuild 时缓存，避免 onDraw 里调 headers() 的拷贝分配 */
    private RecordTableColumns.Header[] headers;
    private int dayCount;
    private int tableScrollX;
    private int tableScrollY;

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

    /** R3 会用它驱动滚动；越界值先夹住再存 */
    public void setTableScroll(int x, int y) {
        int clampedX = metrics.clampScrollX(x);
        int clampedY = metrics.clampScrollY(y);
        if (clampedX == tableScrollX && clampedY == tableScrollY) {
            return;
        }
        tableScrollX = clampedX;
        tableScrollY = clampedY;
        invalidate();
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

    // ==================== measure / layout ====================

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
                resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // 本卡没有子 View；R4 的编辑器会在这里自己摆位置
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
        headers = columns == null ? null : columns.headers();
        metrics.rebuild(columns, dayCount, getWidth(), getHeight());
        tableScrollX = metrics.clampScrollX(tableScrollX);
        tableScrollY = metrics.clampScrollY(tableScrollY);
        invalidate();
    }

    // ==================== 绘制 ====================

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (columns == null || headers == null || width <= 0 || height <= 0) {
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
        drawCellFrame(canvas, cellRect, 1, style.headerText,
                headerText(RecordTableColumns.DATE));

        canvas.save();
        canvas.clipRect(columns.dateWidth, 0, width, headerHeight);
        for (int i = 0; i < headers.length; i++) {
            int left = metrics.xOf(headers[i].startColumn, scrollX);
            int w = spanWidth(headers[i]);
            if (left >= width || left + w <= columns.dateWidth) {
                continue;
            }
            cellRect.set(left, 0, left + w, headerHeight);
            drawCellFrame(canvas, cellRect, 1, style.headerText,
                    headerText(headers[i].textKey));
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
        int maxLines = kind == RecordTableColumns.KIND_NUMBER ? 1 : RecordCellText.MAX_LINES;
        style.fillPaint.setColor(grayed ? RecordTableStyle.GRAY_BG : RecordTableStyle.CELL_BG);
        cellRect.set(left, top, left + w, top + h);
        drawCellFrame(canvas, cellRect, maxLines, style.textFor(kind, grayed), cellTextOf(day, id));
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
        drawCellFrame(canvas, cellRect, 1, style.dateText, text);
    }

    /** 底色（调用方已设好 fillPaint 颜色）→ 文本 → 2px 边框 */
    private void drawCellFrame(Canvas canvas, RectF rect, int maxLines,
                               RecordTableStyle.TextSpec spec, String text) {
        canvas.drawRect(rect, style.fillPaint);
        drawText(canvas, rect, spec, maxLines, text);
        drawBorder(canvas, rect);
    }

    private void drawText(Canvas canvas, RectF rect, RecordTableStyle.TextSpec spec,
                          int maxLines, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float maxWidth = rect.width() - 2f * RecordTableStyle.TEXT_INSET_PX;
        if (maxWidth <= 0f) {
            return;
        }
        cellText.layout(text, spec.paint, maxWidth, maxLines);
        int lines = cellText.lineCount();
        if (lines == 0) {
            return;
        }
        float inset = RecordTableStyle.GRID_STROKE_PX / 2f;
        canvas.save();
        canvas.clipRect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset);
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

    /** 表头一格横跨的宽度 = 覆盖的各列宽之和 */
    private int spanWidth(RecordTableColumns.Header header) {
        int end = Math.min(header.startColumn + header.span, columns.count());
        int w = 0;
        for (int i = header.startColumn; i < end; i++) {
            w += columns.widths[i];
        }
        return w;
    }
}
