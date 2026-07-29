package com.shrimpfarm.app.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.model.AlertItem;
import com.shrimpfarm.app.model.FeedCheckAlertModel;
import com.shrimpfarm.app.model.FeedIncreaseAlertModel;
import com.shrimpfarm.app.model.WaterQualityAlertModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AlertGenerator {

    public static final String PREF_SMART_MASTER = "smart_assistant_master";
    public static final String PREF_SMART_PREFIX = "smart_agent_";

    public static List<AlertItem> generate(Context context, DatabaseHelper dbHelper, SharedPreferences sp, String batchId) {
        List<AlertItem> alerts = new ArrayList<>();

        if (dbHelper == null || !sp.getBoolean(PREF_SMART_MASTER, true)) {
            return alerts;
        }

        if (sp.getBoolean(PREF_SMART_PREFIX + "feed_increase", true))
            alerts.addAll(FeedIncreaseAlertModel.check(context, dbHelper.getReadableDatabase(), batchId));

        if (sp.getBoolean(PREF_SMART_PREFIX + "feed_timeout", true)) {
            String today = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(new Date());
            String stockingDate = dbHelper.getBasicData(batchId, "stocking_date");
            boolean isFourMeals = FeedCheckAlertModel.isFourMeals(dbHelper.getReadableDatabase(), batchId);
            long standardSeconds = FeedCheckAlertModel.getStandardSeconds(stockingDate, isFourMeals);
            if (standardSeconds > 0) {
                List<DatabaseHelper.CheckRecord> records = dbHelper.getCheckRecordsByDate(batchId, today);
                long[] durations = new long[records.size()];
                int[] shedNums = new int[records.size()];
                int validCount = 0;
                for (DatabaseHelper.CheckRecord r : records) {
                    if (!r.excluded && r.durationSeconds > 0) {
                        durations[validCount] = r.durationSeconds * 1000;
                        try {
                            shedNums[validCount] = Integer.parseInt(r.shedNumber);
                        } catch (NumberFormatException e) {
                            shedNums[validCount] = r.shedRowIndex + 1;
                        }
                        validCount++;
                    }
                }
                if (validCount > 0) {
                    FeedCheckAlertModel.TimeoutResult result = FeedCheckAlertModel.checkShedTimeouts(
                            standardSeconds,
                            Arrays.copyOf(durations, validCount),
                            Arrays.copyOf(shedNums, validCount));
                    if (!result.shedNumbers.isEmpty()) {
                        for (int shed : result.shedNumbers) {
                            alerts.add(new AlertItem(context.getString(R.string.alert_timeout_shed, shed), "SHED_TIMEOUT"));
                        }
                    }
                }
            }
        }

        if (sp.getBoolean(PREF_SMART_PREFIX + "feed_check", true))
            alerts.addAll(FeedCheckAlertModel.check(context, dbHelper.getReadableDatabase(), batchId));

        if (sp.getBoolean(PREF_SMART_PREFIX + "feed_time", true)) {
            Cursor ft = dbHelper.getReadableDatabase().query(
                    DatabaseHelper.TABLE_FEEDING_CHECK_ANALYSIS, null,
                    "batch_id=?", new String[]{batchId},
                    null, null, "record_time DESC", "1");
            try {
                if (ft.moveToFirst()) {
                    double avgSec = ft.getDouble(ft.getColumnIndexOrThrow("avg_seconds"));
                    double stdSec = ft.getDouble(ft.getColumnIndexOrThrow("standard_seconds"));
                    if (stdSec > 0) {
                        double ratio = (avgSec - stdSec) / stdSec;
                        if (ratio > 0.20) {
                            alerts.add(new AlertItem(context.getString(R.string.alert_timeout_overall_severe), "FEED_TIME"));
                        } else if (ratio > 0.10) {
                            alerts.add(new AlertItem(context.getString(R.string.alert_timeout_overall_moderate), "FEED_TIME"));
                        }
                    }
                }
            } finally {
                if (ft != null && !ft.isClosed()) ft.close();
            }
        }

        if (sp.getBoolean(PREF_SMART_PREFIX + "water_quality", true))
            alerts.addAll(WaterQualityAlertModel.check(dbHelper.getReadableDatabase(), batchId,
                    sp.getBoolean(PREF_SMART_PREFIX + "water_core", true),
                    sp.getBoolean(PREF_SMART_PREFIX + "nitrite", true),
                    sp.getBoolean(PREF_SMART_PREFIX + "vibrio", true),
                    sp.getBoolean(PREF_SMART_PREFIX + "chlorine", true),
                    sp.getBoolean(PREF_SMART_PREFIX + "h2s", true),
                    sp.getBoolean(PREF_SMART_PREFIX + "orp", true),
                    sp.getBoolean(PREF_SMART_PREFIX + "do", true)));

        return alerts;
    }
}
