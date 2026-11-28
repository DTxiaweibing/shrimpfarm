package com.shrimpfarm.app.finance;

/**
 * Ordered person types for finance edit spinners (A9 net worker included).
 * Pure mapping: no Android or DB dependency.
 */
public final class PersonTypeOptions {

    private static final int[] TYPES = {
            FinanceDb.PERSON_SUPPLIER,
            FinanceDb.PERSON_AGENT,
            FinanceDb.PERSON_CUSTOMER,
            FinanceDb.PERSON_EMPLOYEE,
            FinanceDb.PERSON_NET_WORKER
    };

    private PersonTypeOptions() {
    }

    /** Defensive copy so callers cannot mutate the shared spinner order. */
    public static int[] types() {
        return TYPES.clone();
    }

    /** Unknown types fall back to the first option instead of clamping to employee. */
    public static int positionOf(int personType) {
        for (int i = 0; i < TYPES.length; i++) {
            if (TYPES[i] == personType) return i;
        }
        return 0;
    }

    public static int typeAt(int position) {
        if (position < 0 || position >= TYPES.length) return TYPES[0];
        return TYPES[position];
    }
}
