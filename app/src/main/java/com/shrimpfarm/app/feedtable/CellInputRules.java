package com.shrimpfarm.app.feedtable;

/**
 * 数字格的输入规则：纯逻辑，零 Android 依赖，可单测。
 *
 * <p>与现版 {@code FeedingRecordActivity.buildFeedEdit} 的过滤器等价（最多 1 位小数），
 * 但额外禁止「裸小数点」开头，并把提交归一化从失焦回调里抽出来，便于复用与测试。</p>
 */
public final class CellInputRules {

    private CellInputRules() {
    }

    /**
     * 整串是否可接受：空串、纯数字、数字 + 一个小数点 + 0~1 位小数。
     * 裸小数点（"." / ".5"）、多小数点、超过 1 位小数、负号、空格一律拒绝。
     */
    public static boolean isAcceptableNumber(CharSequence text) {
        if (text == null || text.length() == 0) {
            return true;
        }
        int length = text.length();
        int dot = -1;
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c == '.') {
                if (dot >= 0 || i == 0) {
                    return false;
                }
                dot = i;
            } else if (c < '0' || c > '9') {
                return false;
            }
        }
        return dot < 0 || length - dot - 1 <= 1;
    }

    /**
     * 提交归一化：trim → 四舍五入到 1 位小数 → 整数去掉 ".0"。
     * 解析失败时原样返回 trim 后的字符串（与现版失焦逻辑一致，不吞用户输入）。
     */
    public static String normalizeNumber(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || ".".equals(trimmed)) {
            return "";
        }
        double value;
        try {
            value = Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            return trimmed;
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return trimmed;
        }
        double rounded = Math.round(value * 10.0) / 10.0;
        if (rounded == (long) rounded) {
            return String.valueOf((long) rounded);
        }
        return String.valueOf(rounded);
    }

    /**
     * {@code InputFilter} 内核：按「整串是否合法」判断，合法返回 {@code null}（接受），
     * 否则返回 {@code ""}（整段拒绝）。只取 {@code source[start, end)}，不能用整串 source。
     */
    public static CharSequence filterNumber(CharSequence source, int start, int end,
                                            CharSequence dest, int dstart, int dend) {
        String candidate = dest.subSequence(0, dstart).toString()
                + source.subSequence(start, end).toString()
                + dest.subSequence(dend, dest.length()).toString();
        return isAcceptableNumber(candidate) ? null : "";
    }
}
