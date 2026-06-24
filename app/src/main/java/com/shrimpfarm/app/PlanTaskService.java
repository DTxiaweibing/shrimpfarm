package com.shrimpfarm.app;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class PlanTaskService extends Service {

    private static final String TAG = "PlanTaskService";
    private static final long CHECK_INTERVAL = 60 * 1000;
    private Handler handler = new Handler();
    private boolean running = false;

    private Runnable checkRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            try {
                checkAndSaveTasks();
            } catch (Exception e) {
                Log.e(TAG, "Check error", e);
            }
            handler.postDelayed(this, CHECK_INTERVAL);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        handler.post(checkRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(checkRunnable);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void checkAndSaveTasks() {
        DatabaseHelper db = new DatabaseHelper(this);
        SharedPreferences sp = getSharedPreferences("app_prefs", MODE_PRIVATE);
        String batchId = sp.getString("current_batch_id", "");
        if (batchId.isEmpty()) return;

        if (!sp.getBoolean("plan_task_master_switch", true)) return;

        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        if (hour >= 6 && hour < 17) {
            if (!sp.getBoolean("plan_task_day_switch", true)) return;
        } else if (hour >= 17 && (hour < 23 || (hour == 23 && minute < 30))) {
            if (!sp.getBoolean("plan_task_night_switch", true)) return;
        } else if ((hour == 23 && minute >= 30) || hour < 6) {
            if (!sp.getBoolean("plan_task_midnight_switch", false)) return;
        } else {
            return;
        }

        int stockingDay = db.getStockingDay(batchId);
        if (stockingDay <= 0) return;

        int overdueCount = 0;
        int todayCount = 0;
        int tomorrowCount = 0;

        Cursor c = db.getAllSubTasks(batchId);
        if (c != null) {
            while (c.moveToNext()) {
                long taskId = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TASK_ID));
                int unitType = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_UNIT_TYPE));
                int startValue = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_START_VALUE));
                int endValue = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_END_VALUE));
                double intervalValue = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_INTERVAL_VALUE));
                int lastTriggerDay = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LAST_TRIGGER_DAY));
                double lastTriggerFeed = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LAST_TRIGGER_FEED));

                if (unitType == 0) {
                    int nextDay = (lastTriggerDay > 0) ? lastTriggerDay + (int)intervalValue : startValue;
                    if (stockingDay < startValue || stockingDay > endValue) continue;

                    if (stockingDay > nextDay) {
                        overdueCount++;
                    } else if (stockingDay == nextDay) {
                        todayCount++;
                    } else if (stockingDay == nextDay - 1) {
                        tomorrowCount++;
                    }
                } else {
                    double currentFeed = db.getAccumulatedFeed(batchId, startValue, stockingDay);
                    double nextThreshold = lastTriggerFeed + intervalValue;
                    if (currentFeed >= nextThreshold) {
                        todayCount++;
                    }
                }
            }
            c.close();
        }

        sp.edit()
            .putInt("plan_task_overdue_count", overdueCount)
            .putInt("plan_task_today_count", todayCount)
            .putInt("plan_task_tomorrow_count", tomorrowCount)
            .putLong("plan_task_last_check", System.currentTimeMillis())
            .apply();

        if (overdueCount > 0 || todayCount > 0 || tomorrowCount > 0) {
            sendBroadcast(new Intent("com.shrimpfarm.app.TASK_UPDATE"));
        }
    }
}
