package com.shrimpfarm.app.finance;

import android.app.Activity;

import com.shrimpfarm.app.R;

/**
 * Person-type label resolution for finance manage screens (A9).
 * Keeps string resources statically referenced so release shrinking cannot drop them.
 */
public final class PersonTypeUi {

    private PersonTypeUi() {
    }

    public static String label(Activity activity, int type) {
        switch (type) {
            case FinanceDb.PERSON_AGENT: return activity.getString(R.string.finance_person_agent);
            case FinanceDb.PERSON_CUSTOMER: return activity.getString(R.string.finance_person_customer);
            case FinanceDb.PERSON_EMPLOYEE: return activity.getString(R.string.finance_person_employee);
            case FinanceDb.PERSON_NET_WORKER: return activity.getString(R.string.finance_person_net_worker);
            default: return activity.getString(R.string.finance_person_supplier);
        }
    }
}
