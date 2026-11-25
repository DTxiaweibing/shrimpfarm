package com.shrimpfarm.app.feedtable;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * 单元格文本的断行与绘制辅助，供 {@link RecordTableView#onDraw} 使用。
 *
 * <p>只记「第几行从第几个字符到第几个字符」，不复制子串：{@link Canvas#drawText(CharSequence, int, int, float, float, Paint)}
 * 直接按区间画，所以整个绘制期零分配，也不需要每次 onDraw 造 {@code StaticLayout}。</p>
 *
 * <p>断行规则：先按 {@code '\n'} 切段，再在段内用 {@link Paint#breakText} 贪心塞满宽度；
 * 最后一行放不下时在末尾补 {@code …}。中文按字断，不做英文单词整体换行。</p>
 */
public final class RecordCellText {

    /** 断行缓冲上限；实际行数由 {@link RecordCellFit#maxLinesForHeight} 按格高决定 */
    public static final int MAX_LINES = RecordCellFit.MAX_TEXT_LINES;

    private static final String ELLIPSIS = "\u2026";

    private final int[] lineStarts = new int[MAX_LINES];
    private final int[] lineEnds = new int[MAX_LINES];
    private final boolean[] lineTruncated = new boolean[MAX_LINES];
    private int lineCount;

    /** 上一次 {@link #layout} 排出的行数（0 表示空串或宽度退化，不画文本） */
    public int lineCount() {
        return lineCount;
    }

    /**
     * 把文本按宽度断成最多 {@code maxLines} 行。
     *
     * @param text     文本，null / 空串时 lineCount 为 0
     * @param paint    已设好字号的文本 Paint
     * @param maxWidth 单行可用宽度（px）
     * @param maxLines 1（数字 / 产品格）或按格高算出的行数（日期 / 备注格）
     */
    public void layout(CharSequence text, Paint paint, float maxWidth, int maxLines) {
        lineCount = 0;
        if (text == null || paint == null || maxWidth <= 0f) {
            return;
        }
        int length = text.length();
        int limit = Math.min(Math.max(1, maxLines), MAX_LINES);
        int pos = 0;
        while (pos < length && lineCount < limit) {
            int segmentEnd = segmentEnd(text, pos, length);
            if (lineCount == limit - 1) {
                layoutLastLine(text, paint, pos, segmentEnd, length, maxWidth);
                return;
            }
            int fit = paint.breakText(text, pos, segmentEnd, true, maxWidth, null);
            if (fit <= 0 && segmentEnd > pos) {
                fit = 1; // 极端窄格：至少前进 1 个字符，避免死循环
            }
            addLine(pos, pos + fit, false);
            pos += fit;
            if (pos == segmentEnd && pos < length) {
                pos++; // 跳过 '\n'
            }
        }
    }

    /**
     * 画出上一次 {@link #layout} 的结果。所有行以 {@code centerX} 水平居中。
     *
     * @param firstBaseline 第一行基线；行间距用 {@code lineHeight}
     */
    public void draw(Canvas canvas, CharSequence text, Paint paint, float centerX,
                     float firstBaseline, float lineHeight) {
        if (text == null || paint == null) {
            return;
        }
        for (int i = 0; i < lineCount; i++) {
            float baseline = firstBaseline + i * lineHeight;
            if (lineTruncated[i]) {
                drawEllipsized(canvas, text, paint, lineStarts[i], lineEnds[i], centerX, baseline);
            } else if (lineEnds[i] > lineStarts[i]) {
                canvas.drawText(text, lineStarts[i], lineEnds[i], centerX, baseline, paint);
            }
        }
    }

    private void layoutLastLine(CharSequence text, Paint paint, int start, int end,
                                int length, float maxWidth) {
        // 空段（连续 '\n'）不占可见行：跳到下一段，把最后一行让给真正的内容，
        // 否则 "abc\n\ndef" 会渲染成 "abc" + 一个只有 "…" 的第二行
        while (start == end && end < length) {
            start++;
            end = segmentEnd(text, start, length);
        }
        if (start >= length) {
            return;
        }
        int fit = paint.breakText(text, start, end, true, maxWidth, null);
        if (fit >= end - start && end >= length) {
            addLine(start, end, false);
            return;
        }
        // 还有下一段（换行后的内容）或这一行就放不下：末尾补省略号
        float ellipsisWidth = paint.measureText(ELLIPSIS);
        int fitWithEllipsis = paint.breakText(text, start, end, true,
                Math.max(0f, maxWidth - ellipsisWidth), null);
        addLine(start, start + Math.max(0, fitWithEllipsis), true);
    }

    private void addLine(int start, int end, boolean truncated) {
        lineStarts[lineCount] = start;
        lineEnds[lineCount] = end;
        lineTruncated[lineCount] = truncated;
        lineCount++;
    }

    /** 从 start 起下一个 '\n' 的下标；没有则返回 length */
    private static int segmentEnd(CharSequence text, int start, int length) {
        for (int i = start; i < length; i++) {
            if (text.charAt(i) == '\n') {
                return i;
            }
        }
        return length;
    }

    /**
     * 画「前缀 + …」：整体宽度 = 前缀宽 + 省略号宽，两者拼起来以 centerX 居中。
     */
    private static void drawEllipsized(Canvas canvas, CharSequence text, Paint paint,
                                       int start, int end, float centerX, float baseline) {
        float textWidth = paint.measureText(text, start, end);
        float ellipsisWidth = paint.measureText(ELLIPSIS);
        if (end > start) {
            canvas.drawText(text, start, end, centerX - ellipsisWidth / 2f, baseline, paint);
        }
        canvas.drawText(ELLIPSIS, centerX + textWidth / 2f, baseline, paint);
    }
}
