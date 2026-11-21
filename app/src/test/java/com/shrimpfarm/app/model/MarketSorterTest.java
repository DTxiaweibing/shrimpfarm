package com.shrimpfarm.app.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MarketSorterTest {

    private static PriceItem item(String name) {
        return new PriceItem(name, "10");
    }

    private static List<String> sortedNames(String... names) {
        List<PriceItem> items = new ArrayList<>();
        for (String name : names) items.add(item(name));
        MarketSorter.sortItems(items);
        List<String> result = new ArrayList<>();
        for (PriceItem i : items) result.add(i.name);
        return result;
    }

    @Test
    public void sortsSizeSpecsFromLargeToSmall() {
        List<String> names = sortedNames(
                "基围虾 · 小", "基围虾 · 中", "基围虾 · 大");
        assertEquals(Arrays.asList("基围虾 · 大", "基围虾 · 中", "基围虾 · 小"), names);
    }

    @Test
    public void sortsWeightsNumericallyNotAsText() {
        // 字符串排序会把 2.5两 排到 2两 前面（'两' > '.'），这里必须按数值。
        List<String> names = sortedNames(
                "公蟹 · 5两", "公蟹 · 2两", "公蟹 · 3.5两", "公蟹 · 2.5两", "公蟹 · 3两");
        assertEquals(Arrays.asList(
                "公蟹 · 2两", "公蟹 · 2.5两", "公蟹 · 3两", "公蟹 · 3.5两", "公蟹 · 5两"), names);
    }

    @Test
    public void keepsRangeSpecAfterSingleWeight() {
        List<String> names = sortedNames(
                "母蟹 · 3.5-3.9两", "母蟹 · 1.8两", "母蟹 · 2两", "母蟹 · 1.5两", "母蟹 · 3两");
        assertEquals(Arrays.asList(
                "母蟹 · 1.5两", "母蟹 · 1.8两", "母蟹 · 2两", "母蟹 · 3两", "母蟹 · 3.5-3.9两"), names);
    }

    @Test
    public void sortsHeadCountBeforePlainSize() {
        List<String> names = sortedNames(
                "河虾 · 大中小", "河虾 · 180头", "河虾 · 100头", "河虾 · 150头");
        assertEquals(Arrays.asList(
                "河虾 · 100头", "河虾 · 150头", "河虾 · 180头", "河虾 · 大中小"), names);
    }

    @Test
    public void putsDefaultSpecLast() {
        List<String> names = sortedNames("梭子蟹 · 常规", "梭子蟹 · 大中小");
        assertEquals("梭子蟹 · 大中小", names.get(0));
        assertEquals("梭子蟹 · 常规", names.get(1));
    }

    @Test
    public void sortsNamesWithoutSeparator() {
        assertTrue(MarketSorter.compareBySpec("2两", "2.5两") < 0);
        assertTrue(MarketSorter.compareBySpec("大", "中") < 0);
        assertTrue(MarketSorter.compareBySpec("小", "中") > 0);
    }

    @Test
    public void putsShrimpCategoryFirst() {
        List<PriceCategory> categories = new ArrayList<>();
        categories.add(category("杂货"));
        categories.add(category("鱼类"));
        categories.add(category("虾"));
        categories.add(category("螃蟹"));

        MarketSorter.sortCategories(categories);

        assertEquals("虾", categories.get(0).title);
        assertEquals("螃蟹", categories.get(1).title);
        assertEquals("鱼类", categories.get(2).title);
        assertEquals("杂货", categories.get(3).title);
    }

    @Test
    public void keepsUnknownCategoryLast() {
        List<PriceCategory> categories = new ArrayList<>();
        categories.add(category("其他"));
        categories.add(category("虾"));

        MarketSorter.sortCategories(categories);

        assertEquals("虾", categories.get(0).title);
        assertEquals("其他", categories.get(1).title);
    }

    private static PriceCategory category(String title) {
        PriceCategory category = new PriceCategory();
        category.title = title;
        category.items = new ArrayList<>();
        return category;
    }
}
