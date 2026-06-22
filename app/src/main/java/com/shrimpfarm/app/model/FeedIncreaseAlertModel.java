package com.shrimpfarm.app.model;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.utils.EncryptUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class FeedIncreaseAlertModel {

    public static List<AlertItem> check(SQLiteDatabase db, String batchId) {
        List<AlertItem> alerts = new ArrayList<>();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();

        String todayStr = sdf.format(cal.getTime());
        cal.add(Calendar.DAY_OF_YEAR, -1);
        String yesterdayStr = sdf.format(cal.getTime());
        cal.add(Calendar.DAY_OF_YEAR, 2);
        String tomorrowStr = sdf.format(cal.getTime());

        float todayTotal = getDailyTotal(db, batchId, todayStr);
        float yesterdayTotal = getDailyTotal(db, batchId, yesterdayStr);
        float tomorrowTotal = getDailyTotal(db, batchId, tomorrowStr);

        if (yesterdayTotal > 0 && todayTotal > yesterdayTotal * 1.1f) {
            String msg = "今天料量(" + fmt(todayTotal) + "斤)比昨天(" + fmt(yesterdayTotal) + "斤)增加超10%";
            alerts.add(new AlertItem(msg, "FEED_INCREASE_DAY"));
        }

        if (todayTotal > 0 && tomorrowTotal > todayTotal * 1.1f) {
            String msg = "明天料量(" + fmt(tomorrowTotal) + "斤)比今天(" + fmt(todayTotal) + "斤)增加超10%";
            alerts.add(new AlertItem(msg, "FEED_INCREASE_TOMORROW"));
        }

        return alerts;
    }

    private static float getDailyTotal(SQLiteDatabase db, String batchId, String date) {
        Cursor cursor = db.query(DatabaseHelper.TABLE_DAILY_RECORDS, null,
                "batch_id=? AND date=?", new String[]{batchId, date},
                null, null, null);
        float total = 0;
        if (cursor.moveToFirst()) {
            String[] fields = {
                    DatabaseHelper.COLUMN_BREAKFAST,
                    DatabaseHelper.COLUMN_LUNCH,
                    DatabaseHelper.COLUMN_DINNER,
                    DatabaseHelper.COLUMN_NIGHT_SNACK
            };
            for (String field : fields) {
                String enc = cursor.getString(cursor.getColumnIndexOrThrow(field));
                if (enc != null && !enc.isEmpty()) {
                    try {
                        String dec = EncryptUtils.decrypt(enc);
                        if (dec != null && !dec.isEmpty()) {
                            total += Float.parseFloat(dec);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        cursor.close();
        return total;
    }

    private static String fmt(float v) {
        if (v == (int) v) return String.valueOf((int) v);
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
