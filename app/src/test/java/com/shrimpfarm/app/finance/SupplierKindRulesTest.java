package com.shrimpfarm.app.finance;

import static org.junit.Assert.*;
import org.junit.Test;

public class SupplierKindRulesTest {
    @Test public void kindConstantsAndValidation() {
        assertEquals(1, SupplierKindRules.KIND_MED);
        assertEquals(2, SupplierKindRules.KIND_SEED);
        assertTrue(SupplierKindRules.isValidKind(1));
        assertTrue(SupplierKindRules.isValidKind(2));
        assertFalse(SupplierKindRules.isValidKind(0));
    }

    @Test public void normalizeAndFilter() {
        assertEquals(1, SupplierKindRules.normalizeKind(99));
        assertTrue(SupplierKindRules.matchesFilter(1, 0));
        assertTrue(SupplierKindRules.matchesFilter(2, 0));
        assertTrue(SupplierKindRules.matchesFilter(1, 1));
        assertFalse(SupplierKindRules.matchesFilter(1, 2));
    }

    @Test public void labelResKeyMapsToPlannedStringNames() {
        assertEquals("finance_supplier_kind_med", SupplierKindRules.labelResKey(1));
        assertEquals("finance_supplier_kind_seed", SupplierKindRules.labelResKey(2));
        assertEquals("finance_supplier_kind_med", SupplierKindRules.labelResKey(99));
    }
}
