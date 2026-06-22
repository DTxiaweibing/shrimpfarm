package com.shrimpfarm.app.checkfeed;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.BasicDataActivity;
import com.shrimpfarm.app.BatchManageActivity;
import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.model.FeedCheckAlertModel;
import com.shrimpfarm.app.model.FeedingTimeStandard;
import com.shrimpfarm.app.mixcalc.MixCalcActivity;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CheckFeedActivity extends BaseActivity {

    private static final String TAG = "CheckFeed";
    private static final String PREFS_NAME = "CheckFeedPrefs";
    private static final String KEY_SHED_COUNT = "shed_count";
    private static final String KEY_TABLE_INITIALIZED = "table_initialized";
    private static final String KEY_START_TIME = "start_time";
    private static final String KEY_END_TIME = "end_time";
    private static final String KEY_TABLE_DATA = "table_data";
    private static final String KEY_EXCLUDED_ROWS = "excluded_rows";

    // 颜色常量
    private static final int COLOR_EXCLUDED_ROW = 0xFF4CAF50;
    private static final int COLOR_MERGED_CELL = 0xFFF44336;
    private static final int COLOR_NORMAL_CELL = 0xFF000000;
    private static final int COLOR_WHITE_TEXT = 0xFFFFFFFF;

    private EditText etStartTime;
    private EditText etEndTime;
    private EditText etShedCount;
    private LinearLayout tableContainer;
    private Button btnClear;
    private Button btnBackToMix;
    private String currentBatchId;
    private DatabaseHelper dbHelper;
    private SharedPreferences sharedPreferences;
    private boolean isTableInitialized = false;

    // 时间格式化
    private SimpleDateFormat fullDateTimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());
    private SimpleDateFormat timeOnlyFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    // 标题栏标准用时与差值
    private TextView tvTimeDiff;
    private TextView tvTimeStandard;

    // 水百分比（从拌料计算器传入）
    private int waterPercentage = 20;

    // 超时提醒防重复标志
    private long lastTimeoutCheckHash = 0;

    // 临时格式化标志，防止递归
    private boolean isFormatting = false;

    // 吃料用时数据类
    private static class DurationData {
        int rowIndex;
        long durationMillis;
        TextView durationView;
        TextView rowNumber;
        DurationData(int rowIndex, long durationMillis, TextView durationView, TextView rowNumber) {
            this.rowIndex = rowIndex;
            this.durationMillis = durationMillis;
            this.durationView = durationView;
            this.rowNumber = rowNumber;
        }
    }

    @Override
    @SuppressLint("SourceLockedOrientationActivity")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
        setContentView(R.layout.activity_check_feed);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        SharedPreferences appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentBatchId = appPrefs.getString("current_batch_id", "");
        if (currentBatchId.isEmpty()) {
            showNoBatchDialog();
            return;
        }

        dbHelper = new DatabaseHelper(this);
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // 获取水百分比
        waterPercentage = getIntent().getIntExtra("WATER_PERCENTAGE", 20);

        initViews();
        setupClickListeners();
        setupTimeInputFilters();

        checkBasicDataAndInitialize();

        setupBottomNavigation();
    }

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_check;
    }

    private void initViews() {
        etStartTime = findViewById(R.id.et_start_time);
        etEndTime = findViewById(R.id.et_end_time);
        etShedCount = findViewById(R.id.et_shed_count);
        tableContainer = findViewById(R.id.table_container);
        btnClear = findViewById(R.id.btn_clear);
        btnBackToMix = findViewById(R.id.btn_back_to_mix);

        tvTimeDiff = findViewById(R.id.tv_time_diff);
        tvTimeStandard = findViewById(R.id.tv_time_standard);

        etShedCount.setFocusable(false);
        etShedCount.setFocusableInTouchMode(false);
        etShedCount.setClickable(true);
    }

    private void setupTimeInputFilters() {
        setupTimeInputFilter(etStartTime);
        setupTimeInputFilter(etEndTime);
        if (etStartTime != null) etStartTime.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        if (etEndTime != null) etEndTime.setImeOptions(EditorInfo.IME_ACTION_DONE);
        setupTimeChangeListener(etStartTime);
        setupTimeChangeListener(etEndTime);
    }

    private void setupTimeChangeListener(final EditText editText) {
        if (editText == null) return;
        editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    String timeStr = s.toString().trim();
                    if (!timeStr.isEmpty() && isValidTimeFormat(timeStr)) {
                        recalculateAllFeedingDurations();
                        markAllDurationsByRank();
                    }
                }
            });
    }

    private void setupTimeInputFilter(final EditText editText) {
        if (editText == null) return;
        InputFilter[] filters = new InputFilter[]{
            new InputFilter.LengthFilter(8),
            new InputFilter() {
                @Override
                public CharSequence filter(CharSequence source, int start, int end,
                                           Spanned dest, int dstart, int dend) {
                    for (int i = start; i < end; i++) {
                        char c = source.charAt(i);
                        if (!Character.isDigit(c) && c != ':' && c != '：') {
                            return "";
                        }
                    }
                    return null;
                }
            }
        };
        editText.setFilters(filters);
        if (editText == etStartTime) {
            editText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        } else if (editText == etEndTime) {
            editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        }
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    boolean isEnterKey = (event != null &&
                        event.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
                        event.getAction() == KeyEvent.ACTION_DOWN);
                    if (actionId == EditorInfo.IME_ACTION_NEXT ||
                        actionId == EditorInfo.IME_ACTION_DONE || isEnterKey) {
                        if (editText == etStartTime) {
                            etEndTime.requestFocus();
                        } else if (editText == etEndTime) {
                            moveFocusToFirstTableInput();
                        }
                        return true;
                    }
                    return false;
                }
            });
        final int[] previousLength = {0};
        editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    previousLength[0] = s.length();
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (isFormatting) return;
                    String text = s.toString().trim();
                    if (text.isEmpty()) return;
                    isFormatting = true;
                    String processedText = text.replace("：", ":");
                    if (!processedText.equals(text)) {
                        editText.setText(processedText);
                        editText.setSelection(processedText.length());
                    }
                    editText.setError(null);
                    isFormatting = false;
                }
            });
        editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (!hasFocus) {
                        String text = editText.getText().toString().trim();
                        validateAndCompleteTime(editText, text);
                    }
                }
            });
    }

    private void setupClickListeners() {
        if (btnBackToMix != null) {
            btnBackToMix.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!isTableInitialized) {
                            showShedCountRequiredDialog();
                            return;
                        }
                        String shedCountStr = etShedCount.getText().toString().trim();
                        if (shedCountStr.isEmpty()) {
                            showShedCountRequiredDialog();
                            return;
                        }
                        saveAllData();

                        Intent intent = new Intent(CheckFeedActivity.this, MixCalcActivity.class);
                        intent.putExtra("FROM_CHECK_FEED", true);
                        try {
                            int shedCount = Integer.parseInt(shedCountStr);
                            intent.putExtra("SHED_COUNT", shedCount);
                            intent.putExtra("SHED_COUNT_LIMIT", shedCount);
                        } catch (NumberFormatException ignored) {}
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        finish();
                    }
                });
        }

        if (btnClear != null) {
            btnClear.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showClearConfirmationDialog();
                    }
                });
        }

        if (etShedCount != null) {
            etShedCount.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        if (isTableInitialized) {
                            setTableInitialized(false);
                            clearTable();
                            clearExcludedRows();
                            loadShedCountFromBasicData();
                            Toast.makeText(CheckFeedActivity.this, "表格已重置，棚数已重新加载", Toast.LENGTH_SHORT).show();
                            return true;
                        }
                        return false;
                    }
                });
        }
    }

    private void validateAndCompleteTime(EditText editText, String text) {
        if (text.isEmpty()) {
            editText.setError(null);
            return;
        }
        text = text.replace("：", ":");
        String completedTime = tryCompleteTime(text);
        if (completedTime != null && isValidTimeFormat(completedTime)) {
            // 校验输入的时间是否晚于当前系统时间
            if (isTimeAfterNow(completedTime)) {
                Toast.makeText(this, "时间不能晚于当前时间，已自动修正", Toast.LENGTH_SHORT).show();
                String currentTime = getCurrentTimeOnly();
                editText.setText(currentTime);
                editText.setSelection(currentTime.length());
            } else {
                editText.setText(completedTime);
                editText.setSelection(completedTime.length());
            }
            editText.setError(null);
            recalculateAllFeedingDurations();
            markAllDurationsByRank();
        } else if (isValidTimeFormat(text)) {
            if (isTimeAfterNow(text)) {
                Toast.makeText(this, "时间不能晚于当前时间，已自动修正", Toast.LENGTH_SHORT).show();
                String currentTime = getCurrentTimeOnly();
                editText.setText(currentTime);
                editText.setSelection(currentTime.length());
            }
            editText.setError(null);
        } else {
            editText.setError("时间格式错误！");
        }
    }

    private boolean isTimeAfterNow(String timeStr) {
        try {
            String currentTimeStr = getCurrentTimeOnly();
            Calendar cal = Calendar.getInstance();
            String today = String.format(Locale.getDefault(), "%d/%02d/%02d",
                                         cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            Date inputDateTime = fullDateTimeFormat.parse(today + " " + timeStr);
            Date currentDateTime = new Date();
            return inputDateTime.after(currentDateTime);
        } catch (ParseException e) {
            return false;
        }
    }

    private String tryCompleteTime(String text) {
        if (text == null || text.isEmpty()) return null;
        if (text.length() == 8 && text.matches("([01]?[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]")) {
            return text;
        }
        String numbersOnly = text.replace(":", "");
        switch (numbersOnly.length()) {
            case 1: return "0" + numbersOnly + ":00:00";
            case 2: return numbersOnly + ":00:00";
            case 3: {
                    String hour3 = numbersOnly.substring(0, 2);
                    String minute3 = numbersOnly.substring(2, 3);
                    int minDigit3 = Integer.parseInt(minute3);
                    if (minDigit3 >= 0 && minDigit3 <= 5) {
                        return hour3 + ":" + (minDigit3 * 10) + ":00";
                    } else return null;
                }
            case 4: {
                    String hour4 = numbersOnly.substring(0, 2);
                    String minute4 = numbersOnly.substring(2, 4);
                    int minValue4 = Integer.parseInt(minute4);
                    if (minValue4 >= 0 && minValue4 <= 59) {
                        return hour4 + ":" + minute4 + ":00";
                    } else return null;
                }
            case 5: {
                    String hour5 = numbersOnly.substring(0, 2);
                    String minute5 = numbersOnly.substring(2, 4);
                    String second5 = numbersOnly.substring(4, 5);
                    int minValue5 = Integer.parseInt(minute5);
                    int secDigit5 = Integer.parseInt(second5);
                    if (minValue5 >= 0 && minValue5 <= 59 && secDigit5 >= 0 && secDigit5 <= 5) {
                        return hour5 + ":" + minute5 + ":" + (secDigit5 * 10);
                    } else return null;
                }
            case 6: {
                    String hour6 = numbersOnly.substring(0, 2);
                    String minute6 = numbersOnly.substring(2, 4);
                    String second6 = numbersOnly.substring(4, 6);
                    int minValue6 = Integer.parseInt(minute6);
                    int secValue6 = Integer.parseInt(second6);
                    if (minValue6 >= 0 && minValue6 <= 59 && secValue6 >= 0 && secValue6 <= 59) {
                        return hour6 + ":" + minute6 + ":" + second6;
                    } else return null;
                }
            default: return null;
        }
    }

    private boolean isValidTimeFormat(String time) {
        if (time == null || time.length() != 8) return false;
        try {
            String[] parts = time.split(":");
            if (parts.length != 3) return false;
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int seconds = Integer.parseInt(parts[2]);
            return hours >= 0 && hours <= 23 && minutes >= 0 && minutes <= 59 && seconds >= 0 && seconds <= 59;
        } catch (Exception e) {
            return false;
        }
    }

    private void setTableInitialized(boolean initialized) {
        isTableInitialized = initialized;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_TABLE_INITIALIZED, initialized);
        editor.apply();
    }

    private void saveShedCount(int shedCount) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_SHED_COUNT, shedCount);
        editor.apply();
    }

    private String getFullDateTimeFromTimeString(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return "";
        Calendar cal = Calendar.getInstance();
        String today = String.format(Locale.getDefault(), "%d/%02d/%02d",
                                     cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
        return today + " " + timeStr;
    }

    private String getTimeOnlyFromFullDateTime(String fullDateTime) {
        if (fullDateTime == null || fullDateTime.isEmpty()) return "";
        try {
            Date date = fullDateTimeFormat.parse(fullDateTime);
            return timeOnlyFormat.format(date);
        } catch (ParseException e) {
            if (fullDateTime.length() == 8 && fullDateTime.contains(":")) {
                return fullDateTime;
            }
            return "";
        }
    }

    private void saveAllData() {
        if (tableContainer == null) return;
        String startTimeDisplay = etStartTime.getText().toString().trim();
        String endTimeDisplay = etEndTime.getText().toString().trim();

        String startTimeFull = startTimeDisplay.isEmpty() ? "" : getFullDateTimeFromTimeString(startTimeDisplay);
        String endTimeFull = endTimeDisplay.isEmpty() ? "" : getFullDateTimeFromTimeString(endTimeDisplay);

        JSONArray tableData = new JSONArray();
        Set<String> excludedRows = new HashSet<>();
        int childCount = tableContainer.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = tableContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout rowLayout = (LinearLayout) child;
                TextView tvRowNumber = (TextView) rowLayout.getChildAt(0);
                boolean isExcluded = tvRowNumber.getCurrentTextColor() == COLOR_WHITE_TEXT &&
                    tvRowNumber.getBackground() instanceof android.graphics.drawable.ColorDrawable &&
                    ((android.graphics.drawable.ColorDrawable) tvRowNumber.getBackground()).getColor() == COLOR_EXCLUDED_ROW;
                if (isExcluded) {
                    String rowNumber = tvRowNumber.getText().toString();
                    excludedRows.add(rowNumber);
                }
                View[] rowViews = (View[]) rowLayout.getTag();
                if (rowViews != null && rowViews.length >= 3) {
                    EditText etInputCell = (EditText) rowViews[0];
                    TextView tvCheckTime = (TextView) rowViews[1];
                    TextView tvRowNumberView = (TextView) rowViews[3];
                    try {
                        JSONObject rowData = new JSONObject();
                        rowData.put("rowNumber", tvRowNumberView.getText().toString());
                        rowData.put("inputText", etInputCell.getText().toString());
                        String checkTimeDisplay = tvCheckTime.getText().toString();
                        String checkTimeFull = checkTimeDisplay.isEmpty() ? "" : getFullDateTimeFromTimeString(checkTimeDisplay);
                        rowData.put("checkTime", checkTimeFull);
                        rowData.put("isExcluded", isExcluded);
                        tableData.put(rowData);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        JSONArray excludedRowsArray = new JSONArray();
        for (String rowNumber : excludedRows) {
            excludedRowsArray.put(rowNumber);
        }
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_START_TIME, startTimeFull);
        editor.putString(KEY_END_TIME, endTimeFull);
        editor.putString(KEY_TABLE_DATA, tableData.toString());
        editor.putString(KEY_EXCLUDED_ROWS, excludedRowsArray.toString());
        editor.apply();

        // 同步保存到数据库
        saveToDatabase();
    }

    private void saveToDatabase() {
        String startTime = etStartTime.getText().toString().trim();
        String endTime = etEndTime.getText().toString().trim();
        if (startTime.isEmpty() || endTime.isEmpty()) return;

        String recordDate = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(new Date());

        dbHelper.deleteCheckRecordsByDate(currentBatchId, recordDate);

        List<ContentValues> allRecords = new ArrayList<>();
        int childCount = tableContainer.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = tableContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                TextView tvRowNumber = (TextView) row.getChildAt(0);
                String shedNumber = tvRowNumber.getText().toString();
                int rowIndex = i + 1;

                View[] views = (View[]) row.getTag();
                EditText etInput = (EditText) views[0];
                TextView tvCheckTime = (TextView) views[1];
                TextView tvDuration = (TextView) views[2];

                boolean excluded = tvRowNumber.getCurrentTextColor() == COLOR_WHITE_TEXT &&
                    tvRowNumber.getBackground() instanceof android.graphics.drawable.ColorDrawable &&
                    ((android.graphics.drawable.ColorDrawable) tvRowNumber.getBackground()).getColor() == COLOR_EXCLUDED_ROW;
                String checkTime = tvCheckTime.getText().toString();
                String durationStr = tvDuration.getText().toString();
                long durationSeconds = 0;
                if (!durationStr.isEmpty() && !durationStr.equals("本行不参与") &&
                    !durationStr.equals("时间格式错误") && !durationStr.equals("计算错误") &&
                    !durationStr.equals("时间解析错误") && !durationStr.equals("无有效行")) {
                    durationSeconds = parseDurationToSeconds(durationStr);
                }

                if (!etInput.getText().toString().trim().isEmpty() && !checkTime.isEmpty()) {
                    ContentValues values = new ContentValues();
                    values.put("batch_id", currentBatchId);
                    values.put("record_date", recordDate);
                    values.put("start_time", startTime);
                    values.put("end_time", endTime);
                    values.put("shed_number", shedNumber);
                    values.put("shed_row_index", rowIndex);
                    values.put("check_time", checkTime);
                    values.put("duration_seconds", durationSeconds);
                    values.put("is_excluded", excluded ? 1 : 0);
                    values.put("water_percentage", waterPercentage);
                    values.put("created_at", System.currentTimeMillis());
                    allRecords.add(values);
                }
            }
        }

        if (!allRecords.isEmpty()) {
            dbHelper.insertCheckRecords(allRecords);

            // 计算平均吃料时间并保存到 feeding_stats 表
            long totalDuration = 0;
            int validCount = 0;
            for (ContentValues record : allRecords) {
                Long duration = record.getAsLong("duration_seconds");
                if (duration != null && duration > 0) {
                    totalDuration += duration;
                    validCount++;
                }
            }
            if (validCount > 0) {
                long avgDurationMillis = (totalDuration / validCount) * 1000;
                dbHelper.insertFeedingStats(currentBatchId, recordDate, avgDurationMillis, System.currentTimeMillis());
            }
        }

        updateTitleTimeDisplay();
    }

    private long parseDurationToSeconds(String duration) {
        try {
            String[] parts = duration.split(":");
            if (parts.length == 3) {
                return Integer.parseInt(parts[0]) * 3600L + Integer.parseInt(parts[1]) * 60L + Integer.parseInt(parts[2]);
            } else if (parts.length == 2) {
                return Integer.parseInt(parts[0]) * 60L + Integer.parseInt(parts[1]);
            }
        } catch (Exception e) {
            return 0;
        }
        return 0;
    }

    private void loadAllData() {
        if (tableContainer == null) return;
        String savedStartTimeFull = sharedPreferences.getString(KEY_START_TIME, "");
        String savedEndTimeFull = sharedPreferences.getString(KEY_END_TIME, "");

        String startTimeDisplay = getTimeOnlyFromFullDateTime(savedStartTimeFull);
        String endTimeDisplay = getTimeOnlyFromFullDateTime(savedEndTimeFull);

        if (!startTimeDisplay.isEmpty() && isValidTimeFormat(startTimeDisplay)) {
            etStartTime.setText(startTimeDisplay);
        } else {
            etStartTime.setText("");
        }
        if (!endTimeDisplay.isEmpty() && isValidTimeFormat(endTimeDisplay)) {
            etEndTime.setText(endTimeDisplay);
        } else {
            etEndTime.setText("");
        }

        String tableDataJson = sharedPreferences.getString(KEY_TABLE_DATA, null);
        if (tableDataJson != null && !tableDataJson.isEmpty()) {
            restoreTableData(tableDataJson);
        } else {
            clearTableSecondColumn();
        }
    }

    private void restoreTableData(String tableDataJson) {
        try {
            JSONArray tableData = new JSONArray(tableDataJson);
            for (int i = 0; i < tableData.length(); i++) {
                JSONObject rowData = tableData.getJSONObject(i);
                String rowNumber = rowData.getString("rowNumber");
                String inputText = rowData.getString("inputText");
                String checkTimeFull = rowData.getString("checkTime");
                boolean isExcluded = rowData.getBoolean("isExcluded");

                String checkTimeDisplay = getTimeOnlyFromFullDateTime(checkTimeFull);

                for (int j = 0; j < tableContainer.getChildCount(); j++) {
                    View child = tableContainer.getChildAt(j);
                    if (child instanceof LinearLayout) {
                        LinearLayout rowLayout = (LinearLayout) child;
                        TextView tvRowNumber = (TextView) rowLayout.getChildAt(0);
                        if (tvRowNumber.getText().toString().equals(rowNumber)) {
                            View[] rowViews = (View[]) rowLayout.getTag();
                            if (rowViews != null && rowViews.length >= 3) {
                                EditText etInputCell = (EditText) rowViews[0];
                                TextView tvCheckTime = (TextView) rowViews[1];
                                if (!inputText.isEmpty()) {
                                    etInputCell.setText(inputText);
                                }
                                if (!checkTimeDisplay.isEmpty()) {
                                    tvCheckTime.setText(checkTimeDisplay);
                                }
                                if (isExcluded) {
                                    markRowAsExcluded(rowLayout, tvRowNumber);
                                }
                            }
                            break;
                        }
                    }
                }
            }
            recalculateAllFeedingDurations();
            markAllDurationsByRank();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void clearExcludedRows() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_EXCLUDED_ROWS);
        editor.apply();
    }

    private void showClearConfirmationDialog() {
        showStyledConfirmDialog("确认清除", "确定要清除所有时间和对钩数据吗？",
                new String[]{"取消", "确定"}, null,
                new DialogInterface.OnClickListener[]{ null, (d, w) -> clearAllInputs() });
    }

    private void saveFeedingCheckAnalysis() {
        try {
            String stockingDate = dbHelper.getBasicData(currentBatchId, "stocking_date");
            if (stockingDate == null || stockingDate.isEmpty() || stockingDate.equals("选择日期")) return;
            SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
            Date stocking = dateFmt.parse(stockingDate);
            if (stocking == null) return;
            Date today = new Date();
            long diffMs = today.getTime() - stocking.getTime();
            if (diffMs < 0) return;
            int dayIndex = (int)(diffMs / (24 * 60 * 60 * 1000)) + 1;

            boolean isFourMeals = detectMealCount();
            long standardSeconds = FeedingTimeStandard.getStandardSeconds(dayIndex, isFourMeals);
            long avgSeconds = calculateTodayAvgDuration();
            if (avgSeconds <= 0 || standardSeconds <= 0) return;

            String recordDate = dateFmt.format(today);
            dbHelper.insertFeedingCheckAnalysis(currentBatchId, recordDate, (double) avgSeconds, (double) standardSeconds);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void clearAllInputs() {
        saveFeedingCheckAnalysis();
        if (etStartTime != null) etStartTime.setText("");
        if (etEndTime != null) etEndTime.setText("");
        clearTableSecondColumn();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_START_TIME);
        editor.remove(KEY_END_TIME);
        editor.remove(KEY_TABLE_DATA);
        editor.apply();
        recalculateAllFeedingDurations();
        markAllDurationsByRank();
        Toast.makeText(this, "所有数据已清除", Toast.LENGTH_SHORT).show();
    }

    private void clearTableSecondColumn() {
        if (tableContainer == null) return;
        int childCount = tableContainer.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = tableContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout rowLayout = (LinearLayout) child;
                boolean isExcluded = false;
                if (rowLayout.getChildCount() > 0) {
                    View firstColumn = rowLayout.getChildAt(0);
                    if (firstColumn instanceof TextView) {
                        TextView tvRowNumber = (TextView) firstColumn;
                        isExcluded = tvRowNumber.getCurrentTextColor() == COLOR_WHITE_TEXT &&
                            tvRowNumber.getBackground() instanceof android.graphics.drawable.ColorDrawable &&
                            ((android.graphics.drawable.ColorDrawable) tvRowNumber.getBackground()).getColor() == COLOR_EXCLUDED_ROW;
                    }
                }
                if (isExcluded) continue;
                if (rowLayout.getChildCount() > 1) {
                    View secondColumn = rowLayout.getChildAt(1);
                    if (secondColumn instanceof EditText) ((EditText) secondColumn).setText("");
                }
                if (rowLayout.getChildCount() > 2) {
                    View thirdColumn = rowLayout.getChildAt(2);
                    if (thirdColumn instanceof TextView) ((TextView) thirdColumn).setText("");
                }
                if (rowLayout.getChildCount() > 3) {
                    View fourthColumn = rowLayout.getChildAt(3);
                    if (fourthColumn instanceof TextView) ((TextView) fourthColumn).setText("");
                }
            }
        }
    }

    private void generateTable() {
        if (etShedCount == null) return;
        String shedCountStr = etShedCount.getText().toString().trim();
        if (shedCountStr.isEmpty()) {
            clearTable();
            return;
        }
        try {
            int shedCount = Integer.parseInt(shedCountStr);
            if (shedCount <= 0) {
                clearTable();
                return;
            }
            generateTableRows(shedCount);
            loadAllData();
        } catch (NumberFormatException e) {
            clearTable();
        }
    }

    private void clearTable() {
        if (tableContainer != null) {
            tableContainer.removeAllViews();
        }
    }

    private void generateTableRows(int shedCount) {
        if (tableContainer == null) return;
        tableContainer.removeAllViews();
        int rowHeight = (int) (40 * getResources().getDisplayMetrics().density);
        for (int i = 1; i <= shedCount; i++) {
            addTableRow(i, rowHeight);
        }
    }

    private void addTableRow(final int rowNumber, int rowHeight) {
        final LinearLayout rowLayout = new LinearLayout(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, rowHeight);
        rowLayout.setLayoutParams(layoutParams);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setWeightSum(100f);

        final TextView tvRowNumber = createTableCell(String.valueOf(rowNumber), 15);
        tvRowNumber.setClickable(true);
        tvRowNumber.setFocusable(true);
        tvRowNumber.setLongClickable(true);
        tvRowNumber.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    boolean isCurrentlyExcluded = tvRowNumber.getCurrentTextColor() == COLOR_WHITE_TEXT &&
                        tvRowNumber.getBackground() instanceof android.graphics.drawable.ColorDrawable &&
                        ((android.graphics.drawable.ColorDrawable) tvRowNumber.getBackground()).getColor() == COLOR_EXCLUDED_ROW;
                    if (isCurrentlyExcluded) {
                        markRowAsIncluded(rowLayout, tvRowNumber, rowNumber);
                    } else {
                        markRowAsExcluded(rowLayout, tvRowNumber);
                    }
                    saveAllData();
                    recalculateAllFeedingDurations();
                    markAllDurationsByRank();
                    return true;
                }
            });
        rowLayout.addView(tvRowNumber);

        final EditText etInputCell = createInputCell(15);
        rowLayout.addView(etInputCell);

        final TextView tvCheckTime = createTableCell("", 35);
        rowLayout.addView(tvCheckTime);

        final TextView tvDuration = createTableCell("", 35);
        rowLayout.addView(tvDuration);

        rowLayout.setTag(new View[]{etInputCell, tvCheckTime, tvDuration, tvRowNumber});
        setupInputCellListener(etInputCell, tvCheckTime, tvDuration, rowNumber);
        tableContainer.addView(rowLayout);
    }

    private void markRowAsExcluded(LinearLayout rowLayout, TextView rowNumberView) {
        View[] otherViews = (View[]) rowLayout.getTag();
        EditText etInputCell = (EditText) otherViews[0];
        TextView tvCheckTime = (TextView) otherViews[1];
        TextView tvDuration = (TextView) otherViews[2];
        rowNumberView.setBackgroundColor(COLOR_EXCLUDED_ROW);
        rowNumberView.setTextColor(COLOR_WHITE_TEXT);
        rowNumberView.setText(rowNumberView.getText());
        etInputCell.setText("");
        etInputCell.setVisibility(View.GONE);
        etInputCell.setEnabled(false);
        tvCheckTime.setVisibility(View.GONE);
        tvCheckTime.setText("");
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) tvDuration.getLayoutParams();
        params.weight = 85f;
        tvDuration.setLayoutParams(params);
        tvDuration.setBackgroundColor(COLOR_MERGED_CELL);
        tvDuration.setTextColor(COLOR_WHITE_TEXT);
        tvDuration.setText("本行不参与计算");
        recalculateAllFeedingDurations();
        markAllDurationsByRank();
    }

    private void markRowAsIncluded(LinearLayout rowLayout, TextView rowNumberView, int rowNumber) {
        View[] otherViews = (View[]) rowLayout.getTag();
        EditText etInputCell = (EditText) otherViews[0];
        TextView tvCheckTime = (TextView) otherViews[1];
        TextView tvDuration = (TextView) otherViews[2];
        rowNumberView.setBackgroundResource(R.drawable.cell_border);
        rowNumberView.setTextColor(COLOR_NORMAL_CELL);
        rowNumberView.setText(String.valueOf(rowNumber));
        etInputCell.setVisibility(View.VISIBLE);
        etInputCell.setEnabled(true);
        etInputCell.setText("");
        tvCheckTime.setVisibility(View.VISIBLE);
        tvCheckTime.setText("");
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) tvDuration.getLayoutParams();
        params.weight = 35f;
        tvDuration.setLayoutParams(params);
        tvDuration.setBackgroundResource(R.drawable.cell_border);
        tvDuration.setTextColor(COLOR_NORMAL_CELL);
        tvDuration.setText("");
        recalculateAllFeedingDurations();
        markAllDurationsByRank();
    }

    private TextView createTableCell(String text, int weight) {
        TextView textView = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        textView.setLayoutParams(params);
        textView.setText(text);
        textView.setTextColor(COLOR_NORMAL_CELL);
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(14);
        textView.setBackgroundResource(R.drawable.cell_border);
        textView.setFocusable(false);
        textView.setFocusableInTouchMode(false);
        textView.setClickable(false);
        return textView;
    }

    private EditText createInputCell(int weight) {
        EditText editText = new EditText(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        editText.setLayoutParams(params);
        editText.setTextColor(COLOR_NORMAL_CELL);
        editText.setGravity(Gravity.CENTER);
        editText.setTextSize(14);
        editText.setBackgroundResource(R.drawable.cell_border);
        editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        editText.setFilters(new InputFilter[0]);
        return editText;
    }

    private void setupInputCellListener(final EditText inputCell, final TextView checkTimeView,
                                        final TextView durationView, final int rowNumber) {
        final String CHECKMARK = "✓";
        inputCell.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (!s.toString().trim().isEmpty() && !s.toString().equals(CHECKMARK)) {
                        inputCell.post(new Runnable() {
                                @Override
                                public void run() {
                                    inputCell.setText(CHECKMARK);
                                    inputCell.setSelection(inputCell.getText().length());
                                    String currentTime = getCurrentTimeOnly();
                                    checkTimeView.setText(currentTime);
                                    calculateFeedingDuration(rowNumber, checkTimeView, durationView);
                                    markAllDurationsByRank();
                                    saveAllData();
                                }
                            });
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        inputCell.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    String currentText = inputCell.getText().toString().trim();
                    if (!hasFocus) {
                        if (currentText.isEmpty()) {
                            checkTimeView.setText("");
                            durationView.setText("");
                            markAllDurationsByRank();
                            saveAllData();
                        } else if (currentText.equals(CHECKMARK)) {
                            String currentTime = getCurrentTimeOnly();
                            if (checkTimeView.getText().toString().isEmpty()) {
                                checkTimeView.setText(currentTime);
                                calculateFeedingDuration(rowNumber, checkTimeView, durationView);
                                markAllDurationsByRank();
                                saveAllData();
                            }
                        }
                    }
                }
            });
        inputCell.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    String currentText = s.toString();
                    if (currentText.isEmpty() && inputCell.hasFocus()) {
                        checkTimeView.setText("");
                        durationView.setText("");
                        markAllDurationsByRank();
                        saveAllData();
                    }
                }
            });
    }

    private void recalculateAllFeedingDurations() {
        if (tableContainer == null) return;
        resetAllDurationCellBackgrounds();
        int childCount = tableContainer.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = tableContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout rowLayout = (LinearLayout) child;
                View[] rowViews = (View[]) rowLayout.getTag();
                if (rowViews != null && rowViews.length >= 3) {
                    TextView tvCheckTime = (TextView) rowViews[1];
                    TextView tvDuration = (TextView) rowViews[2];
                    TextView tvRowNumber = (TextView) rowViews[3];
                    try {
                        int rowNum = Integer.parseInt(tvRowNumber.getText().toString());
                        String checkTime = tvCheckTime.getText().toString();
                        if (!checkTime.isEmpty()) {
                            calculateFeedingDuration(rowNum, tvCheckTime, tvDuration);
                        }
                    } catch (NumberFormatException e) {}
                }
            }
        }
        markAllDurationsByRank();
    }

    private void resetAllDurationCellBackgrounds() {
        if (tableContainer == null) return;
        int childCount = tableContainer.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = tableContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout rowLayout = (LinearLayout) child;
                View[] rowViews = (View[]) rowLayout.getTag();
                if (rowViews != null && rowViews.length >= 3) {
                    TextView tvDuration = (TextView) rowViews[2];
                    TextView tvRowNumber = (TextView) rowViews[3];
                    boolean isExcluded = tvRowNumber.getCurrentTextColor() == COLOR_WHITE_TEXT &&
                        tvRowNumber.getBackground() instanceof android.graphics.drawable.ColorDrawable &&
                        ((android.graphics.drawable.ColorDrawable) tvRowNumber.getBackground()).getColor() == COLOR_EXCLUDED_ROW;
                    if (!isExcluded) {
                        tvDuration.setBackgroundResource(R.drawable.cell_border);
                        tvDuration.setTextColor(COLOR_NORMAL_CELL);
                    }
                }
            }
        }
    }

    private void markAllDurationsByRank() {
        if (tableContainer == null) return;
        List<DurationData> durationList = new ArrayList<>();
        int childCount = tableContainer.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = tableContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout rowLayout = (LinearLayout) child;
                View[] rowViews = (View[]) rowLayout.getTag();
                if (rowViews != null && rowViews.length >= 3) {
                    TextView tvDuration = (TextView) rowViews[2];
                    TextView tvRowNumber = (TextView) rowViews[3];
                    boolean isExcluded = tvRowNumber.getCurrentTextColor() == COLOR_WHITE_TEXT &&
                        tvRowNumber.getBackground() instanceof android.graphics.drawable.ColorDrawable &&
                        ((android.graphics.drawable.ColorDrawable) tvRowNumber.getBackground()).getColor() == COLOR_EXCLUDED_ROW;
                    if (!isExcluded) {
                        String durationText = tvDuration.getText().toString().trim();
                        if (!durationText.isEmpty() && !durationText.equals("本行不参与") &&
                            !durationText.equals("时间格式错误") && !durationText.equals("计算错误") &&
                            !durationText.equals("时间解析错误") && !durationText.equals("无有效行")) {
                            try {
                                long millis = parseDurationToMillis(durationText);
                                if (millis > 0) {
                                    durationList.add(new DurationData(i, millis, tvDuration, tvRowNumber));
                                }
                            } catch (Exception e) {}
                        }
                    }
                }
            }
        }
        if (durationList.isEmpty()) return;
        Collections.sort(durationList, new Comparator<DurationData>() {
                @Override
                public int compare(DurationData d1, DurationData d2) {
                    return Long.compare(d1.durationMillis, d2.durationMillis);
                }
            });
        int markCount = calculateMarkCount(durationList.size());
        resetAllDurationCellBackgrounds();
        for (int i = 0; i < markCount && i < durationList.size(); i++) {
            DurationData data = durationList.get(i);
            data.durationView.setBackgroundResource(R.drawable.cell_border_green);
            data.durationView.setTextColor(COLOR_NORMAL_CELL);
        }
        for (int i = durationList.size() - markCount; i < durationList.size() && i >= 0; i++) {
            DurationData data = durationList.get(i);
            data.durationView.setBackgroundResource(R.drawable.cell_border_gray);
            data.durationView.setTextColor(COLOR_NORMAL_CELL);
        }
        checkForTimeoutWarnings();
    }

    private int calculateMarkCount(int validRowCount) {
        if (validRowCount < 6) return 0;
        else if (validRowCount < 12) return 1;
        else if (validRowCount < 18) return 2;
        else if (validRowCount < 24) return 3;
        else if (validRowCount < 30) return 4;
        else return 5;
    }

    private long parseDurationToMillis(String duration) {
        try {
            String[] parts = duration.split(":");
            long totalMillis = 0;
            if (parts.length == 3) {
                totalMillis += Long.parseLong(parts[0]) * 3600 * 1000;
                totalMillis += Long.parseLong(parts[1]) * 60 * 1000;
                totalMillis += Long.parseLong(parts[2]) * 1000;
            } else if (parts.length == 2) {
                totalMillis += Long.parseLong(parts[0]) * 60 * 1000;
                totalMillis += Long.parseLong(parts[1]) * 1000;
            }
            return totalMillis;
        } catch (Exception e) {
            return 0;
        }
    }

    private void calculateFeedingDuration(int rowNumber, TextView checkTimeView, TextView durationView) {
        try {
            String startTimeDisplay = etStartTime.getText().toString().trim();
            String endTimeDisplay = etEndTime.getText().toString().trim();
            String checkTimeDisplay = checkTimeView.getText().toString().trim();

            if (startTimeDisplay.isEmpty() || endTimeDisplay.isEmpty() || checkTimeDisplay.isEmpty()) {
                durationView.setText("");
                durationView.setBackgroundResource(R.drawable.cell_border);
                durationView.setTextColor(COLOR_NORMAL_CELL);
                return;
            }
            if (!isValidTimeFormat(startTimeDisplay) || !isValidTimeFormat(endTimeDisplay) || !isValidTimeFormat(checkTimeDisplay)) {
                durationView.setText("时间格式错误");
                durationView.setBackgroundResource(R.drawable.cell_border);
                durationView.setTextColor(COLOR_NORMAL_CELL);
                return;
            }

            String startFull = getFullDateTimeFromTimeString(startTimeDisplay);
            String endFull = getFullDateTimeFromTimeString(endTimeDisplay);
            String checkFull = getFullDateTimeFromTimeString(checkTimeDisplay);

            Date startTime = fullDateTimeFormat.parse(startFull);
            Date endTime = fullDateTimeFormat.parse(endFull);
            Date checkTime = fullDateTimeFormat.parse(checkFull);

            int[] validRowInfo = getValidRowInfo(rowNumber);
            int validRowCount = validRowInfo[0];
            int validRowIndex = validRowInfo[1];
            if (validRowCount == 0) {
                durationView.setText("无有效行");
                durationView.setBackgroundResource(R.drawable.cell_border);
                durationView.setTextColor(COLOR_NORMAL_CELL);
                return;
            }
            if (validRowIndex == -1) {
                durationView.setText("本行不参与");
                durationView.setBackgroundResource(R.drawable.cell_border);
                durationView.setTextColor(COLOR_NORMAL_CELL);
                return;
            }

            long totalFeedingTime = endTime.getTime() - startTime.getTime();
            if (totalFeedingTime <= 0) {
                totalFeedingTime += 24 * 60 * 60 * 1000;
            }
            long intervalPerRow = totalFeedingTime / validRowCount;
            long expectedStartTimeMillis = startTime.getTime() + intervalPerRow * (validRowIndex - 1);
            Date expectedStartTime = new Date(expectedStartTimeMillis);
            long feedingDurationMillis = checkTime.getTime() - expectedStartTime.getTime();
            if (feedingDurationMillis < 0) {
                feedingDurationMillis += 24 * 60 * 60 * 1000;
            }
            String durationStr = formatDuration(feedingDurationMillis);
            durationView.setText(durationStr);
        } catch (ParseException e) {
            durationView.setText("时间解析错误");
            durationView.setBackgroundResource(R.drawable.cell_border);
            durationView.setTextColor(COLOR_NORMAL_CELL);
        } catch (Exception e) {
            durationView.setText("计算错误");
            durationView.setBackgroundResource(R.drawable.cell_border);
            durationView.setTextColor(COLOR_NORMAL_CELL);
        }
    }

    private int[] getValidRowInfo(int currentRowNumber) {
        int validRowCount = 0;
        int currentValidIndex = -1;
        if (tableContainer == null) return new int[]{0, -1};
        int childCount = tableContainer.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = tableContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout rowLayout = (LinearLayout) child;
                TextView tvRowNumber = (TextView) rowLayout.getChildAt(0);
                boolean isExcluded = tvRowNumber.getCurrentTextColor() == COLOR_WHITE_TEXT &&
                    tvRowNumber.getBackground() instanceof android.graphics.drawable.ColorDrawable &&
                    ((android.graphics.drawable.ColorDrawable) tvRowNumber.getBackground()).getColor() == COLOR_EXCLUDED_ROW;
                if (!isExcluded) {
                    validRowCount++;
                    try {
                        int rowNum = Integer.parseInt(tvRowNumber.getText().toString());
                        if (rowNum == currentRowNumber) {
                            currentValidIndex = validRowCount;
                        }
                    } catch (NumberFormatException e) {}
                }
            }
        }
        return new int[]{validRowCount, currentValidIndex};
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, secs);
        }
    }

    private String getCurrentTimeOnly() {
        return timeOnlyFormat.format(new Date());
    }

    private void moveFocusToFirstTableInput() {
        if (tableContainer == null || tableContainer.getChildCount() == 0) return;
        for (int i = 0; i < tableContainer.getChildCount(); i++) {
            View child = tableContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout rowLayout = (LinearLayout) child;
                TextView tvRowNumber = (TextView) rowLayout.getChildAt(0);
                boolean isExcluded = tvRowNumber.getCurrentTextColor() == COLOR_WHITE_TEXT &&
                    tvRowNumber.getBackground() instanceof android.graphics.drawable.ColorDrawable &&
                    ((android.graphics.drawable.ColorDrawable) tvRowNumber.getBackground()).getColor() == COLOR_EXCLUDED_ROW;
                if (!isExcluded && rowLayout.getChildCount() > 1) {
                    View inputCell = rowLayout.getChildAt(1);
                    if (inputCell instanceof EditText) {
                        EditText editText = (EditText) inputCell;
                        if (editText.isEnabled()) {
                            editText.requestFocus();
                            return;
                        }
                    }
                }
            }
        }
    }

    private void showShedCountRequiredDialog() {
        showStyledConfirmDialog("提示", "请先设定棚数",
                new String[]{"确定"}, null,
                new DialogInterface.OnClickListener[]{ (d, w) -> {
                    etShedCount.requestFocus();
                    etShedCount.postDelayed(() -> showKeyboard(etShedCount), 300);
                } });
    }

    private void showKeyboard(View view) {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(view, 0);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveAllData();
    }



    private void loadShedCountFromBasicData() {
        String pondCountStr = dbHelper.getBasicData(currentBatchId, "pond_count");
        if (!pondCountStr.isEmpty()) {
            try {
                int shedCount = Integer.parseInt(pondCountStr);
                etShedCount.setText(String.valueOf(shedCount));
                setTableInitialized(true);
                saveShedCount(shedCount);
                generateTable();
                updateTitleTimeDisplay();
                if (etStartTime != null) {
                    etStartTime.setFocusable(true);
                    etStartTime.setFocusableInTouchMode(true);
                    etStartTime.setImeOptions(EditorInfo.IME_ACTION_NEXT);
                    etStartTime.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                etStartTime.requestFocus();
                                etStartTime.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            showKeyboard(etStartTime);
                                        }
                                    }, 200);
                            }
                        }, 100);
                }
                if (etEndTime != null) {
                    etEndTime.setFocusable(true);
                    etEndTime.setFocusableInTouchMode(true);
                    etEndTime.setImeOptions(EditorInfo.IME_ACTION_DONE);
                }
            } catch (NumberFormatException e) {
                etShedCount.setText("");
            }
        } else {
            etShedCount.setText("");
        }
    }

    private void checkBasicDataAndInitialize() {
        String seedQuantity = dbHelper.getBasicData(currentBatchId, "seed_quantity");
        String pondCount = dbHelper.getBasicData(currentBatchId, "pond_count");
        String pondLength = dbHelper.getBasicData(currentBatchId, "pond_length");
        String aeratorCount = dbHelper.getBasicData(currentBatchId, "aerator_count");
        String aerationPower = dbHelper.getBasicData(currentBatchId, "aeration_power");
        String stockingDate = dbHelper.getBasicData(currentBatchId, "stocking_date");

        boolean isComplete = !seedQuantity.isEmpty() &&
            !pondCount.isEmpty() &&
            !pondLength.isEmpty() &&
            !aeratorCount.isEmpty() &&
            !aerationPower.isEmpty() &&
            !stockingDate.isEmpty() &&
            !stockingDate.equals("选择日期");

        if (!isComplete) {
            showStyledConfirmDialog("提示", "请先完成基础数据中的所有必填项",
                new String[]{"取消", "去设置"},
                new int[]{0xFF666666, 0xFF4CAF50},
                new DialogInterface.OnClickListener[]{
                    (dialog, which) -> finish(),
                    (dialog, which) -> {
                        startActivity(new Intent(CheckFeedActivity.this, BasicDataActivity.class));
                        finish();
                    }
                });
        } else {
            loadShedCountFromBasicData();
        }
    }

    private void updateTitleTimeDisplay() {
        if (currentBatchId == null || currentBatchId.isEmpty()) return;

        String stockingDate = dbHelper.getBasicData(currentBatchId, "stocking_date");
        if (stockingDate == null || stockingDate.isEmpty() || stockingDate.equals("选择日期")) return;

        SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
        int dayIndex;
        try {
            Date stocking = dateFmt.parse(stockingDate);
            if (stocking == null) return;
            Date today = new Date();
            long diffMs = today.getTime() - stocking.getTime();
            if (diffMs < 0) return;
            dayIndex = (int)(diffMs / (24 * 60 * 60 * 1000)) + 1;
        } catch (ParseException e) {
            return;
        }

        boolean isFourMeals = detectMealCount();
        long standardSeconds = FeedingTimeStandard.getStandardSeconds(dayIndex, isFourMeals);
        long avgActualSeconds = calculateTodayAvgDuration();

        String stdDisplay = formatSeconds(standardSeconds);
        tvTimeStandard.setText(stdDisplay);

        if (avgActualSeconds > 0 && standardSeconds > 0) {
            long diffSeconds = avgActualSeconds - standardSeconds;
            String sign = diffSeconds > 0 ? "+" : (diffSeconds < 0 ? "-" : "");
            String diffDisplay = sign + formatDuration(Math.abs(diffSeconds) * 1000);
            tvTimeDiff.setText(diffDisplay);

            double diffPercent = Math.abs((double) diffSeconds / standardSeconds);
            if (diffPercent > 0.1) {
                tvTimeDiff.setTextColor(0xFFFFFFFF);
                tvTimeDiff.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
            } else {
                tvTimeDiff.setTextColor(0xFFFFFFFF);
                tvTimeDiff.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            }
        } else {
            tvTimeDiff.setText("--:--:--");
            tvTimeDiff.setTextColor(0xFFFFFFFF);
        }
    }

    private void checkForTimeoutWarnings() {
        if (tableContainer == null) return;
        String startTimeDisplay = etStartTime.getText().toString().trim();
        String endTimeDisplay = etEndTime.getText().toString().trim();
        if (startTimeDisplay.isEmpty() || endTimeDisplay.isEmpty()) return;
        if (!isValidTimeFormat(startTimeDisplay) || !isValidTimeFormat(endTimeDisplay)) return;

        try {
            String startFull = getFullDateTimeFromTimeString(startTimeDisplay);
            String endFull = getFullDateTimeFromTimeString(endTimeDisplay);
            Date startTime = fullDateTimeFormat.parse(startFull);
            Date endTime = fullDateTimeFormat.parse(endFull);
            long totalFeedingTime = endTime.getTime() - startTime.getTime();
            if (totalFeedingTime <= 0) totalFeedingTime += 24 * 60 * 60 * 1000;

            int validRowCount = 0;
            int childCount = tableContainer.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = tableContainer.getChildAt(i);
                if (child instanceof LinearLayout) {
                    TextView tvRowNumber = (TextView) ((LinearLayout) child).getChildAt(0);
                    boolean isExcluded = tvRowNumber.getCurrentTextColor() == COLOR_WHITE_TEXT &&
                        tvRowNumber.getBackground() instanceof android.graphics.drawable.ColorDrawable &&
                        ((android.graphics.drawable.ColorDrawable) tvRowNumber.getBackground()).getColor() == COLOR_EXCLUDED_ROW;
                    if (!isExcluded) validRowCount++;
                }
            }
            if (validRowCount == 0) return;

            long intervalPerRow = totalFeedingTime / validRowCount;

            List<Long> durations = new ArrayList<>();
            List<Integer> shedNums = new ArrayList<>();

            for (int i = 0; i < childCount; i++) {
                View child = tableContainer.getChildAt(i);
                if (!(child instanceof LinearLayout)) continue;
                LinearLayout rowLayout = (LinearLayout) child;
                View[] rowViews = (View[]) rowLayout.getTag();
                if (rowViews == null || rowViews.length < 3) continue;
                TextView tvDuration = (TextView) rowViews[2];
                TextView tvRowNumber = (TextView) rowViews[3];
                boolean isExcluded = tvRowNumber.getCurrentTextColor() == COLOR_WHITE_TEXT &&
                    tvRowNumber.getBackground() instanceof android.graphics.drawable.ColorDrawable &&
                    ((android.graphics.drawable.ColorDrawable) tvRowNumber.getBackground()).getColor() == COLOR_EXCLUDED_ROW;
                if (isExcluded) continue;
                String durationText = tvDuration.getText().toString().trim();
                if (durationText.isEmpty() || durationText.equals("本行不参与") ||
                    durationText.equals("时间格式错误") || durationText.equals("计算错误") ||
                    durationText.equals("时间解析错误") || durationText.equals("无有效行")) continue;
                long durationMillis = parseDurationToMillis(durationText);
                if (durationMillis <= intervalPerRow) continue;
                durations.add(durationMillis);
                shedNums.add(Integer.parseInt(tvRowNumber.getText().toString()));
            }

            if (durations.isEmpty()) return;

            long[] durArray = new long[durations.size()];
            int[] shedArray = new int[shedNums.size()];
            for (int i = 0; i < durations.size(); i++) {
                durArray[i] = durations.get(i);
                shedArray[i] = shedNums.get(i);
            }

            FeedCheckAlertModel.TimeoutResult result =
                FeedCheckAlertModel.checkShedTimeouts(intervalPerRow, durArray, shedArray);

            if (!result.shedNumbers.isEmpty()) {
                long hash = 0;
                for (int s : result.shedNumbers) hash = hash * 31 + s;
                if (hash == lastTimeoutCheckHash) return;
                lastTimeoutCheckHash = hash;

                StringBuilder sb = new StringBuilder();
                for (int s : result.shedNumbers) {
                    if (sb.length() > 0) sb.append("、");
                    sb.append(s);
                }
                showTimeoutWarning(sb.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showTimeoutWarning(String shedNumbers) {
        new AlertDialog.Builder(this)
            .setTitle("吃料异常提醒")
            .setMessage("注意检查" + shedNumbers + "号棚是否空肠空胃，或其他情况！")
            .setPositiveButton("知道了", null)
            .show();
    }

    private boolean detectMealCount() {
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
            Calendar cal = Calendar.getInstance();
            for (int i = 0; i < 3; i++) {
                String date = dateFmt.format(cal.getTime());
                Cursor cursor = db.rawQuery(
                    "SELECT nightSnack FROM daily_records WHERE batch_id = ? AND date = ?",
                    new String[]{currentBatchId, date});
                boolean hasNightSnack = false;
                if (cursor.moveToFirst()) {
                    String nightSnack = cursor.getString(0);
                    hasNightSnack = nightSnack != null && !nightSnack.trim().isEmpty()
                        && !nightSnack.equals("0") && !nightSnack.equals("0.0");
                }
                cursor.close();
                if (!hasNightSnack) {
                    return false;
                }
                cal.add(Calendar.DAY_OF_YEAR, -1);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private long calculateTodayAvgDuration() {
        try {
            String recordDate = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(new Date());
            List<DatabaseHelper.CheckRecord> records = dbHelper.getCheckRecordsByDate(currentBatchId, recordDate);
            if (records == null || records.isEmpty()) return 0;

            long totalSeconds = 0;
            int validCount = 0;
            for (DatabaseHelper.CheckRecord r : records) {
                if (!r.excluded) {
                    totalSeconds += r.durationSeconds;
                    validCount++;
                }
            }
            if (validCount == 0) return 0;
            return totalSeconds / validCount;
        } catch (Exception e) {
            return 0;
        }
    }

    private String formatSeconds(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
    }

    private void showNoBatchDialog() {
        showStyledConfirmDialog("提示", "请先在批次管理中创建至少一个批次",
            new String[]{"退出", "去创建"},
            new int[]{0xFF666666, 0xFF4CAF50},
            new DialogInterface.OnClickListener[]{
                (dialog, which) -> finish(),
                (dialog, which) -> {
                    startActivity(new Intent(CheckFeedActivity.this, BatchManageActivity.class));
                    finish();
                }
            });
    }
}
