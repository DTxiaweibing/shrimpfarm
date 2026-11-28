package com.shrimpfarm.app.finance;

import static org.junit.Assert.*;

import org.junit.Test;

import com.shrimpfarm.app.finance.FinanceDb.FinCategory;
import com.shrimpfarm.app.finance.FinanceDb.FinSupplier;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ProductFilingOptionsTest {

    private static FinCategory category(long id, String name) {
        FinCategory c = new FinCategory();
        c.id = id;
        c.name = name;
        return c;
    }

    private static FinSupplier supplier(long id, String name) {
        FinSupplier s = new FinSupplier();
        s.id = id;
        s.name = name;
        return s;
    }

    @Test public void namesAreTrimmedAndRejectBlank() {
        assertFalse(ProductFilingOptions.validName(null));
        assertFalse(ProductFilingOptions.validName(""));
        assertFalse(ProductFilingOptions.validName("   "));
        assertTrue(ProductFilingOptions.validName("  苗种  "));
    }

    @Test public void categoryLabelsAlwaysEndWithQuickAdd() {
        List<FinCategory> categories = Arrays.asList(category(7, "动保"), category(9, "苗种"));
        assertEquals(Arrays.asList("动保", "苗种", "新增品类..."),
                ProductFilingOptions.categoryLabels(categories, "选择品类", "新增品类..."));
        List<String> empty = ProductFilingOptions.categoryLabels(
                Collections.<FinCategory>emptyList(), "选择品类", "新增品类...");
        assertEquals(Arrays.asList("选择品类", "新增品类..."), empty);
    }

    @Test public void categoryPositionMapsIdsAndQuickAddSentinel() {
        List<FinCategory> categories = Arrays.asList(category(7, "动保"), category(9, "苗种"));
        assertEquals(7L, ProductFilingOptions.categoryIdAt(categories, 0));
        assertEquals(9L, ProductFilingOptions.categoryIdAt(categories, 1));
        assertEquals(ProductFilingOptions.QUICK_ADD,
                ProductFilingOptions.categoryIdAt(categories, 2));
        assertEquals(ProductFilingOptions.QUICK_ADD,
                ProductFilingOptions.categoryIdAt(categories, -1));
        assertEquals(1, ProductFilingOptions.categoryPosition(categories, 9));
        assertEquals(0, ProductFilingOptions.categoryPosition(categories, 99));
    }

    @Test public void duplicateCategoryResolvesToExistingId() {
        List<FinCategory> categories = Arrays.asList(category(7, "动保"), category(9, "苗种"));
        assertEquals(9L, ProductFilingOptions.existingCategoryId(categories, "  苗种 "));
        assertEquals(0L, ProductFilingOptions.existingCategoryId(categories, "饲料"));
        assertEquals(0L, ProductFilingOptions.existingCategoryId(categories, "  "));
    }

    @Test public void supplierLabelsAlwaysEndWithQuickAdd() {
        List<FinSupplier> suppliers = Arrays.asList(supplier(3, "A供应商"), supplier(4, "B供应商"));
        assertEquals(Arrays.asList("A供应商", "B供应商", "新增供应商..."),
                ProductFilingOptions.supplierLabels(suppliers, "新增供应商..."));
        assertEquals(Collections.singletonList("新增供应商..."),
                ProductFilingOptions.supplierLabels(Collections.<FinSupplier>emptyList(),
                        "新增供应商..."));
    }

    @Test public void supplierPositionAndDuplicateFollowSameRules() {
        List<FinSupplier> suppliers = Arrays.asList(supplier(3, "A供应商"), supplier(4, "B供应商"));
        assertEquals(3L, ProductFilingOptions.supplierIdAt(suppliers, 0));
        assertEquals(ProductFilingOptions.QUICK_ADD, ProductFilingOptions.supplierIdAt(suppliers, 2));
        assertEquals(4L, ProductFilingOptions.existingSupplierId(suppliers, " B供应商 "));
        assertEquals(0L, ProductFilingOptions.existingSupplierId(suppliers, "C供应商"));
    }
}
