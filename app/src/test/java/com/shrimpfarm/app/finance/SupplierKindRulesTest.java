package com.shrimpfarm.app.finance;

import static org.junit.Assert.*;
import org.junit.Test;

import com.shrimpfarm.app.finance.FinanceDb.FinSupplier;

import java.util.Arrays;
import java.util.List;

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

    @Test public void filterSupplierListByKind() {
        FinSupplier med = new FinSupplier();
        med.supplierKind = 1;
        FinSupplier seed = new FinSupplier();
        seed.supplierKind = 2;
        List<FinSupplier> all = Arrays.asList(med, seed);
        assertEquals(2, SupplierKindRules.filterSuppliers(all, 0).size());
        assertEquals(1, SupplierKindRules.filterSuppliers(all, 1).size());
        assertSame(med, SupplierKindRules.filterSuppliers(all, 1).get(0));
        assertSame(seed, SupplierKindRules.filterSuppliers(all, 2).get(0));
        assertEquals(0, SupplierKindRules.filterSuppliers(all, 99).size());
    }
}
