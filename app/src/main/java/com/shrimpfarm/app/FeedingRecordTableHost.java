package com.shrimpfarm.app;

import android.content.Context;
import android.os.Handler;
import android.widget.Toast;

import com.shrimpfarm.app.feedtable.OnRecordCellEditListener;
import com.shrimpfarm.app.feedtable.OnRecordCellTapListener;
import com.shrimpfarm.app.feedtable.RecordTextProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 投喂记录表与业务层之间的唯一接线宿主。
 *
 * <p>实现绘制层的三个回调接口，把「数据从哪来 / 编辑落到哪 / 什么时候保存 / 哪些格只读」集中在这里，
 * {@link FeedingRecordActivity} 只保留生命周期与产品弹窗 UI。绘制层不知道 {@code DayRecord} 与数据库，
 * 全部经 {@link RecordTextProvider} 与两个 listener 交互。</p>
 */
public final class FeedingRecordTableHost implements RecordTextProvider,
        OnRecordCellTapListener, OnRecordCellEditListener {

    /** 产品格点击交给 Activity 弹原选择框 */
    public interface ProductTapCallback {
        void onProductCell(int day, String columnId);
    }

    private final Context context;
    private final DatabaseHelper dbHelper;
    private final String batchId;
    private final String stockingDate;
    private final List<FeedingRecordActivity.DayRecord> records;
    private final ProductTapCallback productCallback;
    /** 批次结束状态只查一次（与现版 loadData 一致），避免每条可见格绘制时都查库+解密 */
    private final boolean batchFinished;
    private final Handler debounceHandler = new Handler();
    private final Map<String, Runnable> pendingSaves = new HashMap<>();

    public FeedingRecordTableHost(Context context, DatabaseHelper dbHelper, String batchId,
                                  String stockingDate, List<FeedingRecordActivity.DayRecord> records,
                                  ProductTapCallback productCallback) {
        this.context = context;
        this.dbHelper = dbHelper;
        this.batchId = batchId;
        this.stockingDate = stockingDate;
        this.records = records;
        this.productCallback = productCallback;
        this.batchFinished = dbHelper.isBatchFinished(batchId);
    }

    // ==================== RecordTextProvider ====================

    @Override
    public String getHeaderText(String key) {
        String mapped = FeedingRecordTableRules.headerKeyFor(key);
        int resId = context.getResources().getIdentifier(mapped, "string", context.getPackageName());
        return resId == 0 ? mapped : context.getString(resId);
    }

    @Override
    public String getDateText(int day) {
        FeedingRecordActivity.DayRecord record = recordAt(day);
        return record == null ? "" : formatDateDisplay(record.date);
    }

    @Override
    public String getCellText(int day, String columnId) {
        FeedingRecordActivity.DayRecord record = recordAt(day);
        if (record == null) {
            return "";
        }
        String value = record.getField(columnId);
        return FeedingRecordTableRules.isProductColumn(columnId)
                ? DatabaseHelper.extractProductName(value) : value;
    }

    @Override
    public boolean isCellGrayed(int day, String columnId) {
        FeedingRecordActivity.DayRecord record = recordAt(day);
        return record != null && FeedingRecordTableRules.isGrayed(
                isDateAfterStocking(record.date), batchFinished, columnId);
    }

    @Override
    public boolean isCellEditable(int day, String columnId) {
        FeedingRecordActivity.DayRecord record = recordAt(day);
        return record != null && FeedingRecordTableRules.isEditable(
                isDateAfterStocking(record.date), batchFinished, columnId);
    }

    // ==================== 点击 / 编辑回调 ====================

    @Override
    public void onCellTap(int day, String columnId) {
        // 数字 / 备注列由 RecordTableBridge 自己开编辑器；这里只处理产品格
        if (FeedingRecordTableRules.isProductColumn(columnId) && isCellEditable(day, columnId)
                && productCallback != null) {
            productCallback.onProductCell(day, columnId);
        }
    }

    @Override
    public void onCellLongPress(int day, String columnId) {
        onCellTap(day, columnId);
    }

    @Override
    public void onCellEditBegin(int day, String columnId) {
        // 编辑器自己负责定位与弹键盘，宿主无需处理
    }

    @Override
    public void onCellCommitted(int day, String columnId, String text) {
        FeedingRecordActivity.DayRecord record = recordAt(day);
        if (record == null) {
            return;
        }
        record.setField(columnId, text);
        scheduleSave(record);
    }

    @Override
    public void onEditNext(int day, String columnId) {
        // R5 的 RecordTableBridge 已在内部完成换格，不走外部 listener
    }

    @Override
    public void onCellEditEnd(int day, String columnId) {
        // 保存由 onCellCommitted 的防抖任务负责，不在这里重复触发
    }

    // ==================== 防抖保存 ====================

    /** 500ms 按 date 合并保存；批次结束后拒绝写入并提示 */
    public void scheduleSave(FeedingRecordActivity.DayRecord record) {
        if (dbHelper.isBatchFinished(batchId)) {
            Toast.makeText(context,
                    context.getString(R.string.batch_toast_finished_no_record), Toast.LENGTH_SHORT).show();
            return;
        }
        String key = record.date;
        Runnable existing = pendingSaves.get(key);
        if (existing != null) {
            debounceHandler.removeCallbacks(existing);
        }
        Runnable saveTask = () -> {
            dbHelper.saveRecordWithTransaction(batchId, record);
            pendingSaves.remove(key);
        };
        pendingSaves.put(key, saveTask);
        debounceHandler.postDelayed(saveTask, 500);
    }

    /** onPause：把待保存任务立即跑完，避免丢最后一次输入 */
    public void flushPendingSaves() {
        for (Runnable task : new ArrayList<>(pendingSaves.values())) {
            debounceHandler.removeCallbacks(task);
            task.run();
        }
        pendingSaves.clear();
    }

    /** onDestroy：只清未执行的回调，不再落库（正常退出已由 onPause flush） */
    public void destroy() {
        debounceHandler.removeCallbacksAndMessages(null);
        pendingSaves.clear();
    }

    // ==================== 辅助 ====================

    /** 供产品选择框在回调后取到记录并落字段 */
    public FeedingRecordActivity.DayRecord recordAt(int day) {
        return day < 0 || day >= records.size() ? null : records.get(day);
    }

    private String formatDateDisplay(String fullDate) {
        try {
            int month = Integer.parseInt(fullDate.substring(5, 7));
            int day = Integer.parseInt(fullDate.substring(8, 10));
            return String.format(Locale.ROOT, context.getString(R.string.feeding_date_format), month, day);
        } catch (Exception e) {
            return fullDate;
        }
    }

    private boolean isDateAfterStocking(String recordDate) {
        if (stockingDate == null || stockingDate.isEmpty() || "选择日期".equals(stockingDate)) {
            return true;
        }
        return recordDate.replace("/", "-").compareTo(stockingDate.replace("/", "-")) >= 0;
    }
}
