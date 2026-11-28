package com.shrimpfarm.app.finance;

import static org.junit.Assert.*;

import org.junit.Test;

public class PersonTypeOptionsTest {

    @Test public void spinnerIncludesAllPersonTypesWithNetWorkerLast() {
        int[] types = PersonTypeOptions.types();
        assertEquals(5, types.length);
        assertEquals(FinanceDb.PERSON_SUPPLIER, types[0]);
        assertEquals(FinanceDb.PERSON_AGENT, types[1]);
        assertEquals(FinanceDb.PERSON_CUSTOMER, types[2]);
        assertEquals(FinanceDb.PERSON_EMPLOYEE, types[3]);
        assertEquals(FinanceDb.PERSON_NET_WORKER, types[4]);
    }

    @Test public void positionRoundTripsEveryTypeIncludingNetWorker() {
        for (int type : PersonTypeOptions.types()) {
            assertEquals(type, PersonTypeOptions.typeAt(PersonTypeOptions.positionOf(type)));
        }
        assertEquals(4, PersonTypeOptions.positionOf(FinanceDb.PERSON_NET_WORKER));
    }

    @Test public void unknownPositionOrTypeFallsBackToSupplier() {
        assertEquals(FinanceDb.PERSON_SUPPLIER, PersonTypeOptions.typeAt(-1));
        assertEquals(FinanceDb.PERSON_SUPPLIER, PersonTypeOptions.typeAt(99));
        assertEquals(0, PersonTypeOptions.positionOf(99));
    }

    @Test public void returnedArrayIsDefensiveCopy() {
        int[] types = PersonTypeOptions.types();
        types[0] = 99;
        assertEquals(FinanceDb.PERSON_SUPPLIER, PersonTypeOptions.types()[0]);
    }
}
