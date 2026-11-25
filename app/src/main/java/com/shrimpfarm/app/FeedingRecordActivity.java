package com.shrimpfarm.app;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.shrimpfarm.app.feedtable.RecordTableSizing;
import com.shrimpfarm.app.feedtable.RecordTableStyle;
import com.shrimpfarm.app.feedtable.RecordTableView;

public class FeedingRecordActivity extends BaseActivity {

    private RecordTableView recordTableView;
    private FeedingRecordTableHost tableHost;
    private FrameLayout loadingOverlay;
    private DatabaseHelper dbHelper;
    private String currentBatchId;
    private String stockingDate;

    private boolean isTabletMode = false;
    private boolean batchFinished = false;

    /** 全量日记录：绘制层只通过 tableHost 读，不认识本类 */
    private final List<DayRecord> allRecords = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feeding_record);

        dbHelper = DatabaseHelper.getInstance(this);
        recordTableView = findViewById(R.id.record_table_view);
        loadingOverlay = findViewById(R.id.loading_overlay);
        applySizing();

        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentBatchId = prefs.getString("current_batch_id", "");
        if (currentBatchId.isEmpty()) {
            showNoBatchDialog();
            return;
        }
        if (!isBasicDataComplete()) {
            showBasicDataIncompleteDialog();
            return;
        }

        stockingDate = dbHelper.getBasicData(currentBatchId, "stocking_date");
        batchFinished = dbHelper.isBatchFinished(currentBatchId);

        tableHost = new FeedingRecordTableHost(this, dbHelper, currentBatchId, stockingDate,
                allRecords, this::showProductSelector);
        recordTableView.setTextProvider(tableHost);
        recordTableView.setOnCellTapListener(tableHost);
        recordTableView.setEditListener(tableHost);

        loadInitialData();
        setupBottomNavigation();
        if (isTabletMode) {
            enableSwipeNavigation();
        }
    }

    /** 列宽沿用现版公式（A-3 冻结），交给 RecordTableSizing 算，视图只接收结果 */
    private void applySizing() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        double diagonalInches = Math.sqrt(
                Math.pow(dm.widthPixels / dm.xdpi, 2)
                        + Math.pow(dm.heightPixels / dm.ydpi, 2));
        isTabletMode = diagonalInches >= RecordTableSizing.TABLET_MIN_DIAGONAL_INCHES;
        recordTableView.setSizing(RecordTableSizing.compute(dm.widthPixels, diagonalInches));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tableHost != null) {
            scrollToYesterday();
        }
    }

    @Override
    protected void onPause() {
        // A4 #12：先提交编辑器，再把未落库的防抖任务一次性 flush，避免丢最后一次输入
        if (recordTableView != null) {
            recordTableView.endEditing();
        }
        if (tableHost != null) {
            tableHost.flushPendingSaves();
        }
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        // A4 #8：有编辑先提交收键盘，再退出页面
        if (recordTableView != null && recordTableView.isEditing()) {
            recordTableView.endEditing();
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (tableHost != null) {
            tableHost.destroy();
        }
        super.onDestroy();
    }

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_record;
    }

    // ==================== Dialog helpers ====================

    private boolean isBasicDataComplete() {
        String waterPrepDate = dbHelper.getBasicData(currentBatchId, "water_prep_date");
        return !waterPrepDate.isEmpty() && !waterPrepDate.equals("选择日期");
    }

    private void showNoBatchDialog() {
        showStyledConfirmDialog(getString(R.string.basic_title_prompt), getString(R.string.basic_msg_no_batch),
                new String[]{getString(R.string.btn_quit), getString(R.string.basic_btn_goto_create)},
                new int[]{0xFF666666, 0xFF4CAF50},
                new DialogInterface.OnClickListener[]{
                        (dialog, which) -> finish(),
                        (dialog, which) -> {
                            startActivity(new Intent(FeedingRecordActivity.this, BatchManageActivity.class));
                            finish();
                        }
                });
    }

    private void showBasicDataIncompleteDialog() {
        showStyledConfirmDialog(getString(R.string.basic_title_prompt), getString(R.string.feeding_msg_no_water_prep),
                new String[]{getString(R.string.btn_cancel), getString(R.string.check_btn_go_settings)},
                new int[]{0xFF666666, 0xFF4CAF50},
                new DialogInterface.OnClickListener[]{
                        (dialog, which) -> finish(),
                        (dialog, which) -> {
                            startActivity(new Intent(FeedingRecordActivity.this, BasicDataActivity.class));
                            finish();
                        }
                });
    }

    // ==================== Data loading ====================

    private void loadInitialData() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
        Thread t = new Thread(() -> {
            int totalDays = dbHelper.getTotalDaysInBatch(currentBatchId);
            List<DayRecord> records = dbHelper.getRecordsByPage(currentBatchId, totalDays, 0);
            runOnUiThread(() -> {
                allRecords.addAll(records);
                recordTableView.setDayCount(allRecords.size());
                if (loadingOverlay != null) {
                    loadingOverlay.setVisibility(View.GONE);
                }
                scrollToYesterday();
            });
        }, "FeedingRecord-loadInitial");
        t.setDaemon(true);
        t.start();
    }

    /** 打开时定位到昨天（若无昨天记录则定位到最后一条不晚于今天的记录） */
    private void scrollToYesterday() {
        Calendar today = Calendar.getInstance();
        String waterPrepDate = dbHelper.getBasicData(currentBatchId, "water_prep_date");
        Calendar startCal = Calendar.getInstance();
        String[] formats = {"yyyy/MM/dd", "yyyy-MM-dd", "yyyy.M.d"};
        boolean parsed = false;
        for (String fmt : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.CHINA);
                Date d = sdf.parse(waterPrepDate);
                if (d != null) {
                    startCal.setTime(d);
                    parsed = true;
                    break;
                }
            } catch (Exception ignored) { /* ignored */ }
        }
        if (!parsed) {
            return;
        }

        int targetIndex = -1;
        for (int i = 0; i < allRecords.size(); i++) {
            try {
                String dateStr = allRecords.get(i).date;
                int year = Integer.parseInt(dateStr.substring(0, 4));
                int month = Integer.parseInt(dateStr.substring(5, 7));
                int day = Integer.parseInt(dateStr.substring(8, 10));
                Calendar recordCal = Calendar.getInstance();
                recordCal.set(year, month - 1, day);
                if (!recordCal.after(today)) {
                    targetIndex = i;
                }
            } catch (Exception ignored) { /* ignored */ }
        }
        if (targetIndex >= 0) {
            int finalTarget = targetIndex;
            recordTableView.post(() -> {
                int dayHeight = RecordTableStyle.subRowHeightPx(this) * 2;
                recordTableView.setTableScroll(0, finalTarget * dayHeight);
            });
        }
    }

    // ==================== Product selector ====================

    /** 产品格点击回调（由 tableHost 转发）；选择后写回记录、防抖保存并局部重绘 */
    private void showProductSelector(int day, String columnId) {
        final DayRecord record = tableHost.recordAt(day);
        if (record == null || batchFinished) {
            return;
        }
        final boolean feed = columnId.startsWith("mix");
        final List<DatabaseHelper.PresetItem> presets = feed
                ? dbHelper.getMixPresetsSorted(currentBatchId)
                : dbHelper.getWaterPresetsSorted(currentBatchId);
        if (presets.isEmpty()) {
            showNoPresetsDialog(feed);
            return;
        }
        showProductListDialog(presets, record, columnId, feed);
    }

    /** 预设为空：提示并引导去基础数据页 */
    private void showNoPresetsDialog(boolean feed) {
        String msg = getString(feed
                ? R.string.feeding_msg_no_feed_presets : R.string.feeding_msg_no_water_presets);
        showStyledConfirmDialog(getString(R.string.basic_title_prompt), msg,
                new String[]{getString(R.string.btn_cancel), getString(R.string.check_btn_go_settings)}, null,
                new DialogInterface.OnClickListener[]{null, (d, w) -> {
                    Intent intent = new Intent(FeedingRecordActivity.this, BasicDataActivity.class);
                    intent.putExtra("open_tab", feed ? 1 : 2);
                    startActivity(intent);
                }});
    }

    /** 构建并显示预设列表弹窗 */
    private void showProductListDialog(final List<DatabaseHelper.PresetItem> presets,
                                       final DayRecord record, final String columnId, final boolean feed) {
        final String title = getString(feed
                ? R.string.feeding_title_select_feed : R.string.feeding_title_select_water);
        List<String> displayNames = new ArrayList<>();
        for (DatabaseHelper.PresetItem item : presets) {
            displayNames.add(item.displayName);
        }
        displayNames.add("");

        Dialog dialog = new Dialog(FeedingRecordActivity.this);
        @SuppressLint("InflateParams")
        View dialogView = LayoutInflater.from(FeedingRecordActivity.this).inflate(R.layout.dialog_simple_list, null);
        dialog.setContentView(dialogView);
        ((TextView) dialogView.findViewById(R.id.tv_title)).setText(title);

        ListView listView = dialogView.findViewById(R.id.list_view);
        listView.setAdapter(new ArrayAdapter<>(FeedingRecordActivity.this,
                android.R.layout.simple_list_item_1, displayNames));
        listView.setOnItemClickListener((parent, view, which, id) ->
                onProductSelected(dialog, presets, record, columnId, feed, which));
        dialog.show();
        sizeProductDialog(dialog);
    }

    /** 选中一个预设（或清空）：写回字段、防抖保存、局部重绘 */
    private void onProductSelected(Dialog dialog, List<DatabaseHelper.PresetItem> presets,
                                   DayRecord record, String columnId, boolean feed, int which) {
        if (which >= presets.size()) {
            record.setField(columnId, "");
        } else {
            DatabaseHelper.PresetItem selected = presets.get(which);
            String prefix = feed ? "【拌料】" : "【调水】";
            record.setField(columnId, prefix + selected.tagContent + "+" + selected.displayName);
        }
        tableHost.scheduleSave(record);
        dialog.dismiss();
        recordTableView.notifyDataChanged();
    }

    /** 弹窗宽度取屏宽一半，透明背景交给 dialog_simple_list 自己画 */
    private void sizeProductDialog(Dialog dialog) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(screenWidth / 2, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    // ==================== Data class ====================

    public static class DayRecord {
        public String date = "";
        public String breakfast = "";
        public String lunch = "";
        public String dinner = "";
        public String nightSnack = "";
        public String waterMix1 = "";
        public String waterMix2 = "";
        public String waterMix3 = "";
        public String waterMix4 = "";
        public String mix1 = "";
        public String mix2 = "";
        public String mix3 = "";
        public String mix4 = "";
        public String remark = "";

        /** 按列名写字段；与 RecordTableColumns 的 id 一一对应 */
        public void setField(String field, String value) {
            switch (field) {
                case "breakfast": breakfast = value; break;
                case "lunch": lunch = value; break;
                case "dinner": dinner = value; break;
                case "nightSnack": nightSnack = value; break;
                case "waterMix1": waterMix1 = value; break;
                case "waterMix2": waterMix2 = value; break;
                case "waterMix3": waterMix3 = value; break;
                case "waterMix4": waterMix4 = value; break;
                case "mix1": mix1 = value; break;
                case "mix2": mix2 = value; break;
                case "mix3": mix3 = value; break;
                case "mix4": mix4 = value; break;
                case "remark": remark = value; break;
                default: break;
            }
        }

        /** 按列名读字段；未知列返回空串 */
        public String getField(String field) {
            switch (field) {
                case "breakfast": return breakfast;
                case "lunch": return lunch;
                case "dinner": return dinner;
                case "nightSnack": return nightSnack;
                case "waterMix1": return waterMix1;
                case "waterMix2": return waterMix2;
                case "waterMix3": return waterMix3;
                case "waterMix4": return waterMix4;
                case "mix1": return mix1;
                case "mix2": return mix2;
                case "mix3": return mix3;
                case "mix4": return mix4;
                case "remark": return remark;
                default: return "";
            }
        }

        @SuppressWarnings("unused")
        public ContentValues toContentValues() {
            ContentValues cv = new ContentValues();
            cv.put("date", date);
            cv.put("breakfast", breakfast);
            cv.put("lunch", lunch);
            cv.put("dinner", dinner);
            cv.put("nightSnack", nightSnack);
            cv.put("waterMix1", waterMix1);
            cv.put("waterMix2", waterMix2);
            cv.put("waterMix3", waterMix3);
            cv.put("waterMix4", waterMix4);
            cv.put("mix1", mix1);
            cv.put("mix2", mix2);
            cv.put("mix3", mix3);
            cv.put("mix4", mix4);
            cv.put("remark", remark);
            return cv;
        }

        @SuppressWarnings("unused")
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("date", date);
                json.put("breakfast", breakfast);
                json.put("lunch", lunch);
                json.put("dinner", dinner);
                json.put("nightSnack", nightSnack);
                json.put("waterMix1", waterMix1);
                json.put("waterMix2", waterMix2);
                json.put("waterMix3", waterMix3);
                json.put("waterMix4", waterMix4);
                json.put("mix1", mix1);
                json.put("mix2", mix2);
                json.put("mix3", mix3);
                json.put("mix4", mix4);
                json.put("remark", remark);
            } catch (JSONException e) {
                android.util.Log.e("DayRecord", "toJson failed", e);
            }
            return json;
        }

        @SuppressWarnings("unused")
        public void fromJson(JSONObject json) {
            date = json.optString("date", "");
            breakfast = json.optString("breakfast", "");
            lunch = json.optString("lunch", "");
            dinner = json.optString("dinner", "");
            nightSnack = json.optString("nightSnack", "");
            waterMix1 = json.optString("waterMix1", "");
            waterMix2 = json.optString("waterMix2", "");
            waterMix3 = json.optString("waterMix3", "");
            waterMix4 = json.optString("waterMix4", "");
            mix1 = json.optString("mix1", "");
            mix2 = json.optString("mix2", "");
            mix3 = json.optString("mix3", "");
            mix4 = json.optString("mix4", "");
            remark = json.optString("remark", "");
        }
    }
}
