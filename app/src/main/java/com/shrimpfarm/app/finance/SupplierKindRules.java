package com.shrimpfarm.app.finance;

import java.util.ArrayList;
import java.util.List;

/**
 * A1 supplier-kind rules: 1=动保(med), 2=苗种(seed).
 * Pure helper with no Android/database dependencies so it is JVM-testable.
 */
public final class SupplierKindRules {

    public static final int KIND_MED = 1;
    public static final int KIND_SEED = 2;

    private static final String KEY_MED = "finance_supplier_kind_med";
    private static final String KEY_SEED = "finance_supplier_kind_seed";

    private SupplierKindRules() {
    }

    public static boolean isValidKind(int kind) {
        return kind == KIND_MED || kind == KIND_SEED;
    }

    /** Invalid values fall back to the A1 default kind (MED). */
    public static int normalizeKind(int kind) {
        return isValidKind(kind) ? kind : KIND_MED;
    }

    /** filter 0 = all. */
    public static boolean matchesFilter(int supplierKind, int filter) {
        return filter == 0 || supplierKind == filter;
    }

    /** String resource name for UI label lookup; invalid kinds fall back to MED. */
    public static String labelResKey(int kind) {
        return normalizeKind(kind) == KIND_SEED ? KEY_SEED : KEY_MED;
    }

    /** In-memory supplier list filter; filter 0 keeps all entries. */
    public static List<FinanceDb.FinSupplier> filterSuppliers(
            List<FinanceDb.FinSupplier> all, int filter) {
        if (filter == 0) return all;
        List<FinanceDb.FinSupplier> out = new ArrayList<>();
        for (FinanceDb.FinSupplier s : all) {
            if (matchesFilter(s.supplierKind, filter)) out.add(s);
        }
        return out;
    }
}
