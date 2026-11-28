package com.shrimpfarm.app.finance;

import static org.junit.Assert.*;
import org.junit.Test;

public class ArApLedgerRulesTest {
    @Test public void incomeVisibility_threeStates() {
        assertTrue(ArApLedgerRules.showInArApForGoods(false));
        assertTrue(ArApLedgerRules.showInArApForNetFee(false));
        assertFalse(ArApLedgerRules.showInIncomeLedger(false, false));

        assertFalse(ArApLedgerRules.showInArApForGoods(true));
        assertTrue(ArApLedgerRules.showInArApForNetFee(false));
        assertTrue(ArApLedgerRules.showInIncomeLedger(true, false));

        assertFalse(ArApLedgerRules.showInArApForGoods(true));
        assertFalse(ArApLedgerRules.showInArApForNetFee(true));
        assertTrue(ArApLedgerRules.showInIncomeLedger(true, true));
    }

    @Test public void actualAmountStaysOriginalForCarSettle() {
        assertEquals(1000.0, ArApLedgerRules.goodsIncomeActual(1000.0), 0.0001);
        assertEquals(35.0, ArApLedgerRules.netFeeExpense(100.0, 0.35), 0.0001);
    }

    @Test public void incomeGoodsAmount_paidAndUnpaidWithRounding() {
        assertEquals(1000.13, ArApLedgerRules.incomeGoodsAmount(1000.126, true), 0.0001);
        assertEquals(0.0, ArApLedgerRules.incomeGoodsAmount(1000.126, false), 0.0001);
    }

    @Test public void incomeNetFeePayable_unpaidAndPaidWithRounding() {
        assertEquals(35.57, ArApLedgerRules.incomeNetFeePayable(35.567, false), 0.0001);
        assertEquals(0.0, ArApLedgerRules.incomeNetFeePayable(35.567, true), 0.0001);
    }
}
