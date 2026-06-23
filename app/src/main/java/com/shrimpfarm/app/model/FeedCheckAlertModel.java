package com.shrimpfarm.app.model;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.shrimpfarm.app.DatabaseHelper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

    public static boolean isFourMeals(SQLiteDatabase db, String batchId) {
        try {
            SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -1);
            int nightSnackCount = 0;
            for (int i = 0; i < 3; i++) {
                String date = dateFmt.format(cal.getTime());
                Cursor cursor = db.rawQuery(
                    "SELECT nightSnack FROM daily_records WHERE batch_id = ? AND date = ?",
                    new String[]{batchId, date});
                boolean hasNightSnack = false;
                if (cursor.moveToFirst()) {
                    String nightSnack = cursor.getString(0);
                    hasNightSnack = nightSnack != null && !nightSnack.trim().isEmpty()
                        && !nightSnack.equals("0") && !nightSnack.equals("0.0");
                }
                cursor.close();
                if (hasNightSnack) nightSnackCount++;
                cal.add(Calendar.DAY_OF_YEAR, -1);
            }
            return nightSnackCount >= 2;
        } catch (Exception e) {
            return true;
        }
    }

    public static long getStandardSeconds(String stockingDate, boolean isFourMeals) {
        if (stockingDate == null || stockingDate.isEmpty() || stockingDate.equals("选择日期")) return 0;
        try {
            SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
            Date stocking = dateFmt.parse(stockingDate);
            if (stocking == null) return 0;
            Date today = new Date();
            long diffMs = today.getTime() - stocking.getTime();
            if (diffMs < 0) return 0;
            int dayIndex = (int)(diffMs / (24 * 60 * 60 * 1000)) + 1;
            return FeedingTimeStandard.getStandardSeconds(dayIndex, isFourMeals);
        } catch (ParseException e) {
            return 0;
        }
    }

    public static TimeoutResult checkShedTimeouts(long standardSeconds, long[] durationsMillis, int[] shedNumbers) {
        List<Integer> timeoutSheds = new ArrayList<>();
        long standardMillis = standardSeconds * 1000;
        long twentyPercent = (long)(standardMillis * 0.2);

        for (int i = 0; i < durationsMillis.length; i++) {
            if (durationsMillis[i] <= standardMillis) continue;
            long overTime = durationsMillis[i] - standardMillis;
            if (overTime >= twentyPercent) {
                timeoutSheds.add(shedNumbers[i]);
            }
        }

        return new TimeoutResult(timeoutSheds, !timeoutSheds.isEmpty());
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
