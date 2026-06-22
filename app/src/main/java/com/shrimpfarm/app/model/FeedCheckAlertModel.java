package com.shrimpfarm.app.model;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.shrimpfarm.app.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;

public class FeedCheckAlertModel {

    public static List<AlertItem> check(SQLiteDatabase db, String batchId) {
        List<AlertItem> alerts = new ArrayList<>();

        Cursor c = db.query(DatabaseHelper.TABLE_FEEDING_CHECK_ANALYSIS, null,
                "batch_id=?", new String[]{batchId},
                null, null, "record_time DESC", "2");

        List<Double> ratios = new ArrayList<>();
        while (c.moveToNext()) {
            double avgSec = c.getDouble(c.getColumnIndexOrThrow("avg_seconds"));
            double stdSec = c.getDouble(c.getColumnIndexOrThrow("standard_seconds"));
            if (stdSec > 0) {
                ratios.add((avgSec - stdSec) / stdSec);
            }
        }
        c.close();

        if (ratios.size() < 2) return alerts;

        double r1 = ratios.get(0);
        double r2 = ratios.get(1);

        if (r1 > 0.20 && r2 > 0.20) {
            alerts.add(new AlertItem("查料连续超20%，请立即减料", "FEED_CHECK_OVER_20"));
        } else if (r1 > 0.10 && r2 > 0.10) {
            alerts.add(new AlertItem("查料连续超10%，请减小加料幅度", "FEED_CHECK_OVER_10"));
        }

        if (r1 < -0.20 && r2 < -0.20) {
            alerts.add(new AlertItem("查料连续少20%以上，喂料严重不足", "FEED_CHECK_UNDER_20"));
        } else if (r1 < -0.10 && r2 < -0.10) {
            alerts.add(new AlertItem("查料连续少10%以上，请加大加料幅度", "FEED_CHECK_UNDER_10"));
        }

        return alerts;
    }

    public static TimeoutResult checkShedTimeouts(long intervalPerRow, long[] durationsMillis, int[] shedNumbers) {
        List<Integer> singleTimeout = new ArrayList<>();
        List<Integer> combinedTimeout = new ArrayList<>();
        long twentyPercent = (long)(intervalPerRow * 0.2);

        for (int i = 0; i < durationsMillis.length; i++) {
            if (durationsMillis[i] <= intervalPerRow) continue;
            long overTime = durationsMillis[i] - intervalPerRow;
            if (overTime >= twentyPercent) {
                singleTimeout.add(shedNumbers[i]);
            } else {
                combinedTimeout.add(shedNumbers[i]);
            }
        }

        if (!singleTimeout.isEmpty()) {
            return new TimeoutResult(singleTimeout, true);
        }

        long totalOver = 0;
        for (int i = 0; i < durationsMillis.length; i++) {
            if (durationsMillis[i] > intervalPerRow) {
                totalOver += durationsMillis[i] - intervalPerRow;
            }
        }
        if (totalOver >= twentyPercent && combinedTimeout.size() >= 2) {
            return new TimeoutResult(combinedTimeout, false);
        }

        return new TimeoutResult(new ArrayList<>(), false);
    }

    public static class TimeoutResult {
        public final List<Integer> shedNumbers;
        public final boolean isSingleShed;

        public TimeoutResult(List<Integer> shedNumbers, boolean isSingleShed) {
            this.shedNumbers = shedNumbers;
            this.isSingleShed = isSingleShed;
        }
    }
}
