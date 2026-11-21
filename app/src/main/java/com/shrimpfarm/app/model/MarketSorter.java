package com.shrimpfarm.app.model;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 行情列表的显示顺序。
 *
 * <p>规格名必须按「大小 / 重量」排，不能按字符串排：字符串排序会把
 * {@code 2.5两} 排在 {@code 2两} 前面（{@code 两} 的码点比 {@code .} 大），
 * 也会把 {@code 大 / 中 / 小} 排成 {@code 中 / 大 / 小}。</p>
 */
public final class MarketSorter {

    /** 分类固定顺序：虾排最前，其余按字典序兜底。 */
    private static final String[] CATEGORY_ORDER = {"虾", "螃蟹", "鱼类", "杂货"};

    /** 品种与规格之间的分隔符，与 HqSupabaseClient 拼接展示名时保持一致。 */
    private static final String SEPARATOR = " · ";

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    /** 纯大小类规格名，按从大到小排。 */
    private static final String[] SIZE_NAMES = {"特大", "大", "中", "小", "特小"};

    private MarketSorter() {
    }

    /** 分类排序：虾永远第一。 */
    public static int compareCategory(String a, String b) {
        int ra = categoryRank(a);
        int rb = categoryRank(b);
        if (ra != rb) return Integer.compare(ra, rb);
        return ra < CATEGORY_ORDER.length ? 0 : safe(a).compareTo(safe(b));
    }

    /** 按规格排序；入参是完整展示名，形如 {@code 公蟹 · 2.5两}。 */
    public static int compareBySpec(String a, String b) {
        return compareSpec(specOf(a), specOf(b));
    }

    /** 规格排序：先比数字，再比大/中/小，最后才退回字典序。 */
    public static int compareSpec(String a, String b) {
        String x = safe(a);
        String y = safe(b);

        Double nx = firstNumber(x);
        Double ny = firstNumber(y);
        if (nx != null && ny != null) {
            int byNumber = Double.compare(nx, ny);
            return byNumber != 0 ? byNumber : x.compareTo(y);
        }
        // 带数字的（按重量、按头数）排在纯大小规格前面。
        if (nx != null) return -1;
        if (ny != null) return 1;

        int rx = sizeRank(x);
        int ry = sizeRank(y);
        return rx != ry ? Integer.compare(rx, ry) : x.compareTo(y);
    }

    /** 原地调整分类顺序，保证虾在最前。 */
    public static void sortCategories(List<PriceCategory> categories) {
        if (categories == null || categories.size() < 2) return;
        categories.sort((a, b) -> compareCategory(titleOf(a), titleOf(b)));
    }

    /** 原地排序同一品种下的规格行。 */
    public static void sortItems(List<PriceItem> items) {
        if (items == null || items.size() < 2) return;
        items.sort((a, b) -> compareBySpec(nameOf(a), nameOf(b)));
    }

    private static String titleOf(PriceCategory category) {
        return category == null ? "" : category.title;
    }

    private static String nameOf(PriceItem item) {
        return item == null ? "" : item.name;
    }

    private static int categoryRank(String category) {
        String value = safe(category);
        for (int i = 0; i < CATEGORY_ORDER.length; i++) {
            if (CATEGORY_ORDER[i].equals(value)) return i;
        }
        return CATEGORY_ORDER.length;
    }

    private static String specOf(String itemName) {
        String value = safe(itemName);
        int index = value.lastIndexOf(SEPARATOR);
        return index < 0 ? value : value.substring(index + SEPARATOR.length());
    }

    private static Double firstNumber(String text) {
        Matcher matcher = NUMBER.matcher(text);
        if (!matcher.find()) return null;
        try {
            return Double.valueOf(matcher.group());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int sizeRank(String text) {
        for (int i = 0; i < SIZE_NAMES.length; i++) {
            if (SIZE_NAMES[i].equals(text)) return i;
        }
        return SIZE_NAMES.length;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
