package com.shrimpfarm.app.finance;

import static org.junit.Assert.*;

import org.junit.Test;

public class NetWorkerRulesTest {

    @Test public void netWorkerDetectedByScreenOrRecordType() {
        assertTrue(NetWorkerRules.isNetWorker(
                FinanceDb.PERSON_NET_WORKER, FinanceDb.PERSON_SUPPLIER));
        assertTrue(NetWorkerRules.isNetWorker(
                FinanceDb.PERSON_SUPPLIER, FinanceDb.PERSON_NET_WORKER));
        assertFalse(NetWorkerRules.isNetWorker(
                FinanceDb.PERSON_SUPPLIER, FinanceDb.PERSON_SUPPLIER));
        assertFalse(NetWorkerRules.isNetWorker(
                FinanceDb.PERSON_EMPLOYEE, FinanceDb.PERSON_EMPLOYEE));
    }

    @Test public void netWorkerTypeIsDistinctFromExistingPersonTypes() {
        assertEquals(5, FinanceDb.PERSON_NET_WORKER);
        assertFalse(FinanceDb.PERSON_NET_WORKER == FinanceDb.PERSON_SUPPLIER);
        assertFalse(FinanceDb.PERSON_NET_WORKER == FinanceDb.PERSON_AGENT);
        assertFalse(FinanceDb.PERSON_NET_WORKER == FinanceDb.PERSON_CUSTOMER);
        assertFalse(FinanceDb.PERSON_NET_WORKER == FinanceDb.PERSON_EMPLOYEE);
    }
}
