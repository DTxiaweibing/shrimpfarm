package com.shrimpfarm.app.model;

import java.util.regex.Pattern;

/**
 * 解析行情价格文本。
 *
 * <p>上游价格字段是文本，可能是区间（{@code "22-25"}）、单价（{@code "5"}）
 * 或空串。解析失败必须返回 {@code null}，不能返回 0 —— 否则图表会把
 * 缺失数据画成跌到 0 的假点。</p>
 */
public final class PriceRangeParser {

    /** 长单位必须排在短单位前面，避免 "公斤" 被 "斤" 拆坏。 */
    private static final String[] UNITS = {
            "元/公斤", "元/千克", "元/斤",
            "块/公斤", "块/斤",
            "/公斤", "/千克", "/斤",
            "每公斤", "每千克", "每斤",
            "公斤", "千克", "元", "块", "斤",
            "¥", "￥"
    };

    private static final Pattern SEPARATOR = Pattern.compile("[-~至到/、]");

    private PriceRangeParser() {
    }

    public static PriceRange parse(String raw) {
        if (raw == null) return null;

        String text = normalize(raw);
        if (text.isEmpty()) return null;

        String[] parts = SEPARATOR.split(text, -1);
        if (parts.length == 1) {
            Float single = toPrice(parts[0]);
            return single == null ? null : new PriceRange(single, single);
        }
        if (parts.length != 2) return null;

        Float low = toPrice(parts[0]);
        Float high = toPrice(parts[1]);
        if (low == null || high == null) return null;
        return new PriceRange(low, high);
    }

    /** 全角转半角、去掉单位和空白，保留数字与分隔符。 */
    private static String normalize(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '０' && c <= '９') {
                c = (char) ('0' + (c - '０'));
            }
            switch (c) {
                case '～':
                case '〜':
                    c = '~';
                    break;
                case '－':
                case '—':
                case '–':
                case '−':
                    c = '-';
                    break;
                case '　':
                    continue;
                default:
                    break;
            }
            if (Character.isWhitespace(c)) continue;
            sb.append(c);
        }

        String text = sb.toString();
        for (String unit : UNITS) {
            text = text.replace(unit, "");
        }
        return text.trim();
    }

    private static Float toPrice(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            float value = Float.parseFloat(text);
            if (value <= 0f || Float.isNaN(value) || Float.isInfinite(value)) return null;
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
