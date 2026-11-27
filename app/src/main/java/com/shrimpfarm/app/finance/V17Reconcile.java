package com.shrimpfarm.app.finance;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;
import java.util.Map;

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

    /** 金额/重量类字段对账：绝对差超过 0.005 视为不一致。 */
    public static void check(String batch, String field, double before, double after) {
        if (Math.abs(before - after) > AMOUNT_TOLERANCE) {
            throw mismatch(batch, field, String.valueOf(before), String.valueOf(after));
        }
    }

    /** 计数字段对账：要求完全相等。 */
    public static void checkCount(String batch, String field, long before, long after) {
        if (before != after) {
            throw mismatch(batch, field, String.valueOf(before), String.valueOf(after));
        }
    }

    public static Map<String, Snapshot> snapshotIncome(SQLiteDatabase db) {
        Map<String, Snapshot> map = new HashMap<>();
        Cursor c = db.rawQuery("SELECT IFNULL(batch_id,''), COUNT(*), "
                + "IFNULL(SUM(weight),0), IFNULL(SUM(amount),0), IFNULL(SUM(net_fee),0), "
                + "IFNULL(SUM(actual_amount),0) FROM fin_income GROUP BY batch_id", null);
        try {
            while (c.moveToNext()) {
                long count = c.getLong(1);
                map.put(nonNull(c.getString(0)), new Snapshot(count, count,
                        c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getDouble(5)));
            }
        } finally {
            c.close();
        }
        return map;
    }

    public static void reconcileV17(SQLiteDatabase db, Map<String, Snapshot> before) {
        Map<String, Snapshot> after = snapshotIncomeAfter(db);
        java.util.TreeSet<String> batches = new java.util.TreeSet<>(before.keySet());
        batches.addAll(after.keySet());
        for (String batch : batches) {
            Snapshot b = before.get(batch);
            Snapshot a = after.get(batch);
            if (b == null) b = new Snapshot(0, 0, 0, 0, 0, 0);
            if (a == null) a = new Snapshot(0, 0, 0, 0, 0, 0);
            checkCount(batch, "单头数", b.count, a.count);
            checkCount(batch, "明细行数", b.count, a.itemCount);
            check(batch, "总重量", b.weight, a.weight);
            check(batch, "货款合计", b.amount, a.amount);
            check(batch, "捞网费合计", b.netFee, a.netFee);
            check(batch, "实收合计", b.actual, a.actual);
        }
    }

    private static Map<String, Snapshot> snapshotIncomeAfter(SQLiteDatabase db) {
        Map<String, Snapshot> map = new HashMap<>();
        Cursor c = db.rawQuery("SELECT t.batch, t.cnt, t.icnt, t.w, t.a, t.nf, "
                + "IFNULL((SELECT SUM(i2.actual_amount) FROM fin_income i2 "
                + "WHERE IFNULL(i2.batch_id,'') = t.batch), 0) "
                + "FROM (SELECT IFNULL(i.batch_id,'') batch, COUNT(DISTINCT i.id) cnt, "
                + "COUNT(it.id) icnt, IFNULL(SUM(it.weight),0) w, IFNULL(SUM(it.amount),0) a, "
                + "IFNULL(SUM(it.net_fee),0) nf "
                + "FROM fin_income i LEFT JOIN fin_income_item it ON it.income_id=i.id "
                + "GROUP BY i.batch_id) t", null);
        try {
            while (c.moveToNext()) {
                map.put(nonNull(c.getString(0)), new Snapshot(c.getLong(1), c.getLong(2),
                        c.getDouble(3), c.getDouble(4), c.getDouble(5), c.getDouble(6)));
            }
        } finally {
            c.close();
        }
        return map;
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }

    private static IllegalStateException mismatch(String batch, String field,
                                                   String before, String after) {
        return new IllegalStateException("批次 " + batch + " 的 " + field
                + " 对账不一致：修改前=" + before + "，修改后=" + after);
    }
}
