package com.shrimpfarm.app.finance;

import com.shrimpfarm.app.finance.FinanceDb.FinCategory;
import com.shrimpfarm.app.finance.FinanceDb.FinSupplier;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure option/validation rules for A2 supplier product filing.
 * No Android or DB dependency: every method is JVM-testable.
 */
public final class ProductFilingOptions {

    /** Sentinel for the trailing "add new" item in a picker. */
    public static final long QUICK_ADD = -1L;

    private ProductFilingOptions() {
    }

    public static boolean validName(String name) {
        return name != null && !name.trim().isEmpty();
    }

    public static List<String> categoryLabels(List<FinCategory> categories,
                                              String emptyLabel, String quickAddLabel) {
        List<String> labels = new ArrayList<>();
        for (FinCategory c : categories) labels.add(c.name);
        if (labels.isEmpty()) labels.add(emptyLabel);
        labels.add(quickAddLabel);
        return labels;
    }

    public static long categoryIdAt(List<FinCategory> categories, int position) {
        if (position >= 0 && position < categories.size()) return categories.get(position).id;
        return QUICK_ADD;
    }

    public static int categoryPosition(List<FinCategory> categories, long categoryId) {
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).id == categoryId) return i;
        }
        return 0;
    }

    public static long existingCategoryId(List<FinCategory> categories, String name) {
        if (!validName(name)) return 0;
        String key = name.trim();
        for (FinCategory c : categories) {
            if (key.equals(c.name)) return c.id;
        }
        return 0;
    }

    public static List<String> supplierLabels(List<FinSupplier> suppliers, String quickAddLabel) {
        List<String> labels = new ArrayList<>();
        for (FinSupplier s : suppliers) labels.add(s.name);
        labels.add(quickAddLabel);
        return labels;
    }

    public static long supplierIdAt(List<FinSupplier> suppliers, int position) {
        if (position >= 0 && position < suppliers.size()) return suppliers.get(position).id;
        return QUICK_ADD;
    }

    public static long existingSupplierId(List<FinSupplier> suppliers, String name) {
        if (!validName(name)) return 0;
        String key = name.trim();
        for (FinSupplier s : suppliers) {
            if (key.equals(s.name)) return s.id;
        }
        return 0;
    }
}
