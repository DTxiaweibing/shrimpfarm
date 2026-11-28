package com.shrimpfarm.app.finance;

/**
 * Pure rules for A9 net-worker person type. No Android or DB dependency.
 */
public final class NetWorkerRules {

    private NetWorkerRules() {
    }

    /** Net-worker rows are detected by the screen filter or by the record's own type. */
    public static boolean isNetWorker(int filterType, int personType) {
        return filterType == FinanceDb.PERSON_NET_WORKER
                || personType == FinanceDb.PERSON_NET_WORKER;
    }
}
