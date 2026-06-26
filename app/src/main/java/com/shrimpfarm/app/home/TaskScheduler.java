package com.shrimpfarm.app.home;

import android.content.SharedPreferences;
import android.database.Cursor;

import com.shrimpfarm.app.DatabaseHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class TaskScheduler {

    public static boolean isTaskTimeVisible(SharedPreferences sp) {
        if (!sp.getBoolean("plan_task_master_switch", true)) return false;

        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        if (hour >= 6 && hour < 17) {
            return sp.getBoolean("plan_task_day_switch", true);
        } else if (hour >= 17 && (hour < 23 || minute < 30)) {
            return sp.getBoolean("plan_task_night_switch", true);
        } else {
            return sp.getBoolean("plan_task_midnight_switch", false);
        }
    }

    public static List<TaskItem> computeTasks(DatabaseHelper dbHelper, String batchId) {
        List<TaskItem> overdue = new ArrayList<>();
        List<TaskItem> today = new ArrayList<>();
        List<TaskItem> tomorrow = new ArrayList<>();
        List<TaskItem> result = new ArrayList<>();

        int stockingDay = dbHelper.getStockingDay(batchId);
        if (stockingDay <= 0) return result;

        Cursor c = dbHelper.getAllSubTasks(batchId);
        if (c == null) return result;

        while (c.moveToNext()) {
            long taskId = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TASK_ID));
            long parentId = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PARENT_ID));
            String subName = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TASK_NAME));
            int unitType = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_UNIT_TYPE));
            int startValue = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_START_VALUE));
            int endValue = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_END_VALUE));
            double intervalValue = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_INTERVAL_VALUE));
            int lastTriggerDay = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LAST_TRIGGER_DAY));
            double lastTriggerFeed = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LAST_TRIGGER_FEED));

            String mainName = subName;
            if (parentId > 0) {
                Cursor pc = dbHelper.getReadableDatabase().query(
                        DatabaseHelper.TABLE_PLAN_TASKS,
                        new String[]{DatabaseHelper.COLUMN_TASK_NAME},
                        DatabaseHelper.COLUMN_TASK_ID + "=?", new String[]{String.valueOf(parentId)},
                        null, null, null);
                if (pc.moveToFirst()) mainName = pc.getString(0);
                pc.close();
            }
            if (mainName == null || mainName.isEmpty()) mainName = "任务";

            boolean showOverdue = false, showToday = false, showTomorrow = false;

            if (unitType == 0) {
                int inter = (int) intervalValue;
                if (inter <= 0) inter = 1;
                boolean isPast17 = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 17;

                if (stockingDay >= startValue && stockingDay <= endValue &&
                        (stockingDay - startValue) % inter == 0 && stockingDay > lastTriggerDay) {
                    if (isPast17) showOverdue = true;
                    else showToday = true;
                }
                if (stockingDay + 1 >= startValue && stockingDay + 1 <= endValue &&
                        (stockingDay + 1 - startValue) % inter == 0) {
                    showTomorrow = true;
                }
                int intervalsPast = (stockingDay - 1 - startValue) / inter;
                if (intervalsPast >= 0) {
                    int lastPastDue = startValue + intervalsPast * inter;
                    if (lastPastDue >= startValue && lastPastDue <= endValue && lastPastDue > lastTriggerDay) {
                        showOverdue = true;
                    }
                }
            } else {
                double currentFeed = dbHelper.getAccumulatedFeed(batchId, startValue, stockingDay);
                double nextThreshold = lastTriggerFeed + intervalValue;
                if (currentFeed >= nextThreshold) {
                    showToday = true;
                }
            }

            String taskLabel = mainName;
            if (subName != null && !subName.isEmpty() && !subName.equals(mainName)) {
                taskLabel = mainName + " - " + subName;
            }

            if (showOverdue) overdue.add(new TaskItem(taskId, batchId, taskLabel, "已超期", 0xFFFF0000));
            if (showToday) today.add(new TaskItem(taskId, batchId, taskLabel, "今天", 0xFF0000FF));
            if (showTomorrow) tomorrow.add(new TaskItem(taskId, batchId, taskLabel, "明天", 0xFF0C8918));
        }
        c.close();

        result.addAll(overdue);
        result.addAll(today);
        result.addAll(tomorrow);
        return result;
    }

    public static class TaskItem {
        public final long taskId;
        public final String batchId;
        public final String label;
        public final String badgeText;
        public final int bgColor;

        public TaskItem(long taskId, String batchId, String label, String badgeText, int bgColor) {
            this.taskId = taskId;
            this.batchId = batchId;
            this.label = label;
            this.badgeText = badgeText;
            this.bgColor = bgColor;
        }
    }
}
