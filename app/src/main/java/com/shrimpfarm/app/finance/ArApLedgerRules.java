package com.shrimpfarm.app.finance;

public final class ArApLedgerRules {

    private ArApLedgerRules() {
    }

    public static boolean showInArApForGoods(boolean paid) {
        return !paid;
    }

    public static boolean showInArApForNetFee(boolean netFeePaid) {
        return !netFeePaid;
    }

    public static boolean showInIncomeLedger(boolean goodsPaid, boolean netFeePaid) {
        return goodsPaid;
    }

    public static double goodsIncomeActual(double actualAmount) {
        return FinanceDb.round2(actualAmount);
    }

    public static double netFeeExpense(double weight, double unitPrice) {
        return FinanceDb.round2(weight * unitPrice);
    }

    public static double incomeGoodsAmount(double actualAmount, boolean paid) {
        return paid ? FinanceDb.round2(actualAmount) : 0.0;
    }

    public static double incomeNetFeePayable(double netFee, boolean netFeePaid) {
        return netFeePaid ? 0.0 : FinanceDb.round2(netFee);
    }
}
