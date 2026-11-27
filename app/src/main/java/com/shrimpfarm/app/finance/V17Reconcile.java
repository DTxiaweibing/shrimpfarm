package com.shrimpfarm.app.finance;

/** V17 六项对账守卫：金额按 0.005 容差，计数要求完全相等，不一致立即抛异常。 */
public final class V17Reconcile {

    private static final double AMOUNT_TOLERANCE = 0.005;

    private V17Reconcile() {
    }

    /** 对账用快照：六项数值先取齐，再逐项交给 check/checkCount。 */
    public static final class Snapshot {
        public final long count;
        public final long itemCount;
        public final double weight;
        public final double amount;
        public final double netFee;
        public final double actual;

        public Snapshot(long count, long itemCount, double weight, double amount,
                        double netFee, double actual) {
            this.count = count;
            this.itemCount = itemCount;
            this.weight = weight;
            this.amount = amount;
            this.netFee = netFee;
            this.actual = actual;
        }
    }

    /** 金额/数量类字段对账：差值绝对值超过 0.005 视为不一致。 */
    public static void check(String batch, String field, double before, double after) {
        if (Math.abs(before - after) > AMOUNT_TOLERANCE) {
            throw mismatch(batch, field, String.valueOf(before), String.valueOf(after));
        }
    }

    /** 计数字段对账：必须完全相等。 */
    public static void checkCount(String batch, String field, long before, long after) {
        if (before != after) {
            throw mismatch(batch, field, String.valueOf(before), String.valueOf(after));
        }
    }

    private static IllegalStateException mismatch(String batch, String field,
                                                   String before, String after) {
        return new IllegalStateException("批次 " + batch + " 的 " + field
                + " 对账不一致：修复前=" + before + "，修复后=" + after);
    }
}
