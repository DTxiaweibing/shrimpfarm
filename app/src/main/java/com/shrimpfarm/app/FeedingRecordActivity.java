package com.shrimpfarm.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public class FeedingRecordActivity extends BaseActivity {

    // 视图组件
    private LinearLayout headerScrollContainer;
    private TextView headerFixedDate;
    private LinearLayout dataContainer;
    private ScrollView verticalScroll;
    private HorizontalScrollView headerScrollView;
    private List<HorizontalScrollView> rowScrollViews = new ArrayList<>();
    private ImageView loadingOverlay;

    // 数据
    private List<DayRecord> dayRecords = new ArrayList<>();
    private DatabaseHelper dbHelper;
    private String currentBatchId;
    private String stockingDate;

    // 尺寸
    private int cellWidth;
    private int remarkWidth;
    private int rowHeight;
    private boolean isSyncing = false;

    private android.graphics.drawable.GradientDrawable headerBorderCache;
    private android.graphics.drawable.GradientDrawable cellBorderCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feeding_record);

        dbHelper = new DatabaseHelper(this);

        headerFixedDate = findViewById(R.id.header_fixed_date);
        headerScrollContainer = findViewById(R.id.header_scroll_container);
        headerScrollView = findViewById(R.id.header_scroll);
        dataContainer = findViewById(R.id.data_container);
        verticalScroll = findViewById(R.id.vertical_scroll);
        loadingOverlay = findViewById(R.id.loading_overlay);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        cellWidth = (int)(screenWidth / 6.5);
        remarkWidth = (int)(screenWidth / 3.5);
        rowHeight = (int) (40 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) headerFixedDate.getLayoutParams();
        params.width = cellWidth;
        headerFixedDate.setLayoutParams(params);
        headerFixedDate.setBackground(createCellBorder(0xFF4CAF50));
        headerFixedDate.setTextColor(0xFFFFFFFF);

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

        new Handler().postDelayed(() -> {
            setupHeader();
            loadAllData();
            setupScrollSync();
            scrollToYesterday();
        }, 50);

        setupBottomNavigation();
    }

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_record;
    }

    @Override
    protected void onResume() {
        super.onResume();
        scrollToYesterday();
    }

    private boolean isBasicDataComplete() {
        String waterPrepDate = dbHelper.getBasicData(currentBatchId, "water_prep_date");
        return !waterPrepDate.isEmpty() && !waterPrepDate.equals("选择日期");
    }

    private void showNoBatchDialog() {
        showStyledConfirmDialog("提示", "请先在批次管理中创建至少一个批次",
            new String[]{"退出", "去创建"},
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
        showStyledConfirmDialog("提示", "请先在基础数据中设置「做水日(拉漂白粉)」",
            new String[]{"取消", "去设置"},
            new int[]{0xFF666666, 0xFF4CAF50},
            new DialogInterface.OnClickListener[]{
                (dialog, which) -> finish(),
                (dialog, which) -> {
                    startActivity(new Intent(FeedingRecordActivity.this, BasicDataActivity.class));
                    finish();
                }
            });
    }

    private void setupHeader() {
        headerScrollContainer.removeAllViews();
        headerScrollContainer.addView(createHeaderCell("早餐", cellWidth, 0xFF4CAF50));
        headerScrollContainer.addView(createHeaderCell("午餐", cellWidth, 0xFF4CAF50));
        headerScrollContainer.addView(createHeaderCell("晚餐", cellWidth, 0xFF4CAF50));
        headerScrollContainer.addView(createHeaderCell("夜宵", cellWidth, 0xFF4CAF50));
        TextView waterHeader = createHeaderCell("调水", cellWidth * 2, 0xFF4CAF50);
        headerScrollContainer.addView(waterHeader);
        headerScrollContainer.addView(createHeaderCell("备注", remarkWidth, 0xFF4CAF50));
    }

    private TextView createHeaderCell(String text, int width, int bgColor) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(createHeaderBorder(bgColor));
        tv.setLayoutParams(new LinearLayout.LayoutParams(width, rowHeight));
        return tv;
    }

    private void loadAllData() {
        dayRecords.clear();
        dataContainer.removeAllViews();
        rowScrollViews.clear();

        String waterPrepDate = dbHelper.getBasicData(currentBatchId, "water_prep_date");
        if (waterPrepDate == null || waterPrepDate.isEmpty() || "选择日期".equals(waterPrepDate)) {
            if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
            return;
        }

        Calendar startCal = Calendar.getInstance();
        String[] formats = {"yyyy/MM/dd", "yyyy-MM-dd", "yyyy.M.d"};
        boolean parsed = false;
        for (String fmt : formats) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(fmt, Locale.CHINA);
                java.util.Date d = sdf.parse(waterPrepDate);
                if (d != null) {
                    startCal.setTime(d);
                    parsed = true;
                    break;
                }
            } catch (Exception ignored) {}
        }
        if (!parsed) {
            if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
            return;
        }

        Calendar endCal = (Calendar) startCal.clone();
        endCal.add(Calendar.MONTH, 6);

        Calendar current = (Calendar) startCal.clone();
        while (!current.after(endCal)) {
            String dateStr = String.format(Locale.getDefault(), "%d/%02d/%02d",
                    current.get(Calendar.YEAR), current.get(Calendar.MONTH) + 1, current.get(Calendar.DAY_OF_MONTH));
            DayRecord record = dbHelper.getRecordByDate(currentBatchId, dateStr);
            dayRecords.add(record);
            addDayRow(record);
            current.add(Calendar.DAY_OF_MONTH, 1);
        }

        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
    }

    private void saveRecord(final DayRecord record) {
        dbHelper.saveRecord(currentBatchId, record);
    }

    private boolean isDateAfterStocking(String recordDate) {
        if (stockingDate == null || stockingDate.isEmpty() || "选择日期".equals(stockingDate)) {
            return true;
        }
        String normalizedRecordDate = recordDate.replace("/", "-");
        int cmp = normalizedRecordDate.compareTo(stockingDate);
        return cmp >= 0;
    }

    private void addDayRow(final DayRecord record) {
        LinearLayout dayRow = new LinearLayout(this);
        dayRow.setOrientation(LinearLayout.HORIZONTAL);
        dayRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, rowHeight * 2));

        TextView dateView = new TextView(this);
        dateView.setText(formatDateDisplay(record.date));
        dateView.setTextSize(13);
        dateView.setTextColor(0xFF2E7D32);
        dateView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        dateView.setGravity(Gravity.CENTER);
        dateView.setBackground(createCellBorder(0xFFE8F5E9));
        dateView.setLayoutParams(new LinearLayout.LayoutParams(cellWidth, rowHeight * 2));
        dayRow.addView(dateView);

        final HorizontalScrollView scrollView = new HorizontalScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(0, rowHeight * 2, 1f));
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.HORIZONTAL);
        int totalWidth = cellWidth * 6 + remarkWidth;
        scrollContent.setLayoutParams(new LinearLayout.LayoutParams(totalWidth, rowHeight * 2));

        LinearLayout middleArea = new LinearLayout(this);
        middleArea.setOrientation(LinearLayout.VERTICAL);
        middleArea.setLayoutParams(new LinearLayout.LayoutParams(cellWidth * 6, rowHeight * 2));

        // 第一行：早餐～调水二
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setLayoutParams(new LinearLayout.LayoutParams(cellWidth * 6, rowHeight));
        row1.setGravity(Gravity.CENTER_VERTICAL);

        row1.addView(createFeedCell(record.breakfast, new OnTextChanged() {
            public void onChanged(String text) { record.breakfast = text; saveRecord(record); }
        }, true));
        row1.addView(createFeedCell(record.lunch, new OnTextChanged() {
            public void onChanged(String text) { record.lunch = text; saveRecord(record); }
        }, true));
        row1.addView(createFeedCell(record.dinner, new OnTextChanged() {
            public void onChanged(String text) { record.dinner = text; saveRecord(record); }
        }, true));
        row1.addView(createFeedCell(record.nightSnack, new OnTextChanged() {
            public void onChanged(String text) { record.nightSnack = text; saveRecord(record); }
        }, true));
        row1.addView(createDataCell("waterMix1", record.waterMix1, false, 12, false, new OnTextChanged() {
            public void onChanged(String text) { record.waterMix1 = text; saveRecord(record); }
        }));
        row1.addView(createDataCell("waterMix2", record.waterMix2, false, 12, false, new OnTextChanged() {
            public void onChanged(String text) { record.waterMix2 = text; saveRecord(record); }
        }));
        middleArea.addView(row1);

        // 第二行：拌料一～调水四
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setLayoutParams(new LinearLayout.LayoutParams(cellWidth * 6, rowHeight));
        row2.setGravity(Gravity.CENTER_VERTICAL);

        row2.addView(createDataCell("mix1", record.mix1, false, 12, false, new OnTextChanged() {
            public void onChanged(String text) { record.mix1 = text; saveRecord(record); }
        }));
        row2.addView(createDataCell("mix2", record.mix2, false, 12, false, new OnTextChanged() {
            public void onChanged(String text) { record.mix2 = text; saveRecord(record); }
        }));
        row2.addView(createDataCell("mix3", record.mix3, false, 12, false, new OnTextChanged() {
            public void onChanged(String text) { record.mix3 = text; saveRecord(record); }
        }));
        row2.addView(createDataCell("mix4", record.mix4, false, 12, false, new OnTextChanged() {
            public void onChanged(String text) { record.mix4 = text; saveRecord(record); }
        }));
        row2.addView(createDataCell("waterMix3", record.waterMix3, false, 12, false, new OnTextChanged() {
            public void onChanged(String text) { record.waterMix3 = text; saveRecord(record); }
        }));
        row2.addView(createDataCell("waterMix4", record.waterMix4, false, 12, false, new OnTextChanged() {
            public void onChanged(String text) { record.waterMix4 = text; saveRecord(record); }
        }));
        middleArea.addView(row2);

        scrollContent.addView(middleArea);

        // 备注列
        EditText remarkEdit = new EditText(this);
        remarkEdit.setText(record.remark);
        remarkEdit.setTextSize(12);
        remarkEdit.setTextColor(0xFF444444);
        remarkEdit.setGravity(Gravity.CENTER);
        remarkEdit.setBackground(createCellBorder(0xFFFFFFFF));
        remarkEdit.setSingleLine(false);
        remarkEdit.setPadding(0, 0, 0, 0);
        remarkEdit.setMovementMethod(android.text.method.ScrollingMovementMethod.getInstance());
        remarkEdit.setVerticalScrollBarEnabled(true);
        remarkEdit.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(android.text.Editable s) {
                record.remark = s.toString();
                saveRecord(record);
            }
        });
        remarkEdit.setLayoutParams(new LinearLayout.LayoutParams(remarkWidth, rowHeight * 2));
        scrollContent.addView(remarkEdit);

        scrollView.addView(scrollContent);
        dayRow.addView(scrollView);
        rowScrollViews.add(scrollView);

        dataContainer.addView(dayRow);
    }

    private EditText createFeedCell(String text, final OnTextChanged callback, boolean enabled) {
        final EditText et = new EditText(this);
        et.setText(text);
        et.setTextSize(16);
        et.setTextColor(enabled ? 0xFF444444 : 0xFFCCCCCC);
        et.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        et.setGravity(Gravity.CENTER);
        et.setBackground(createCellBorder(enabled ? 0xFFFFFFFF : 0xFFF5F5F5));
        et.setPadding(0, 0, 0, 0);
        et.setOverScrollMode(View.OVER_SCROLL_NEVER);
        et.setInputType(enabled ? (android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL) : android.text.InputType.TYPE_NULL);
        et.setSingleLine(true);
        et.setEnabled(enabled);
        et.setClickable(enabled);
        et.setFocusable(enabled);
        et.setFocusableInTouchMode(enabled);

        if (!enabled) {
            et.setKeyListener(null);
            et.setInputType(0);
        }

        et.setFilters(new InputFilter[]{
                new InputFilter() {
                    @Override
                    public CharSequence filter(CharSequence source, int start, int end,
                                               Spanned dest, int dstart, int dend) {
                        if (!enabled) return "";
                        String newText = dest.toString().substring(0, dstart)
                                + source.toString()
                                + dest.toString().substring(dend);
                        if (newText.isEmpty()) return null;
                        if (!newText.matches("^\\d*(\\.\\d{0,1})?$")) {
                            return "";
                        }
                        return null;
                    }
                }
        });

        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus || !enabled) {
                String s = et.getText().toString().trim();
                if (s.isEmpty()) return;
                try {
                    double value = Double.parseDouble(s);
                    double rounded = Math.round(value * 10.0) / 10.0;
                    String formatted;
                    if (rounded == (long) rounded) {
                        formatted = String.valueOf((long) rounded);
                    } else {
                        formatted = String.valueOf(rounded);
                    }
                    et.setText(formatted);
                    et.setSelection(formatted.length());
                    callback.onChanged(formatted);
                } catch (NumberFormatException ignored) {}
            }
        });

        et.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(android.text.Editable s) {
                if (et.hasFocus() && enabled) {
                    callback.onChanged(s.toString());
                }
            }
        });

        et.setLayoutParams(new LinearLayout.LayoutParams(cellWidth, rowHeight));
        return et;
    }

    private EditText createDataCell(final String columnId, String text, boolean isNumber, int textSize, boolean isBold, final OnTextChanged callback) {
        final EditText et = new EditText(this);
        // 显示时提取产品名（去掉前缀和+后面的标签）
        String displayText = DatabaseHelper.extractProductName(text);
        et.setText(displayText);
        et.setTextSize(textSize);
        et.setTextColor(0xFF000000);
        et.setGravity(Gravity.CENTER);
        et.setBackground(createCellBorder(0xFFFFFFFF));
        et.setPadding(0, 0, 0, 0);
        et.setOverScrollMode(View.OVER_SCROLL_NEVER);

        if (isBold) et.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        if (isNumber) {
            et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            et.setSingleLine(true);
        } else {
            et.setSingleLine(false);
            et.setMaxLines(2);
        }

        if (columnId.startsWith("mix") || columnId.startsWith("waterMix")) {
            et.setFocusable(false);
            et.setFocusableInTouchMode(false);
            et.setClickable(true);

            et.setOnClickListener(v -> {
                final String title;
                final Map<String, String> productToTag;
                if (columnId.startsWith("mix")) {
                    productToTag = dbHelper.getMixPresetTagsMap(currentBatchId);
                    title = "选择拌料动保";
                } else {
                    productToTag = dbHelper.getWaterPresetTagsMap(currentBatchId);
                    title = "选择调水动保";
                }
                List<String> productNames = new ArrayList<>(productToTag.keySet());
                productNames.add("");

                if (productToTag.isEmpty()) {
                    String msg = "请先在基础数据中设置" + (columnId.startsWith("mix") ? "拌料动保" : "调水动保");
                    showStyledConfirmDialog("提示", msg,
                            new String[]{"取消", "去设置"}, null,
                            new DialogInterface.OnClickListener[]{ null, (d, w) -> {
                                Intent intent = new Intent(FeedingRecordActivity.this, BasicDataActivity.class);
                                intent.putExtra("open_tab", columnId.startsWith("mix") ? 1 : 2);
                                startActivity(intent);
                            } });
                    return;
                }

                android.app.Dialog dialog = new android.app.Dialog(FeedingRecordActivity.this);
                @android.annotation.SuppressLint("InflateParams")
                View dialogView = LayoutInflater.from(FeedingRecordActivity.this).inflate(R.layout.dialog_simple_list, null);
                dialog.setContentView(dialogView);

                TextView tvTitle = dialogView.findViewById(R.id.tv_title);
                tvTitle.setText(title);

                android.widget.ListView listView = dialogView.findViewById(R.id.list_view);
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        FeedingRecordActivity.this,
                        android.R.layout.simple_list_item_1,
                        productNames
                );
                listView.setAdapter(adapter);
                listView.setOnItemClickListener((parent, view, which, id) -> {
                    String selectedProduct = productNames.get(which);
                    if (selectedProduct.isEmpty()) {
                        et.setText("");
                        callback.onChanged("");
                    } else {
                        String tagContent = productToTag.get(selectedProduct);
                        String prefix = columnId.startsWith("mix") ? "【拌料】" : "【调水】";
                        String fullValue = prefix + tagContent + "+" + selectedProduct;
                        et.setText(selectedProduct);
                        callback.onChanged(fullValue);
                    }
                    dialog.dismiss();
                });
                dialog.show();
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                dialog.getWindow().setLayout(screenWidth / 2, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            });
        }

        et.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(android.text.Editable s) {
                callback.onChanged(s.toString());
            }
        });

        et.setLayoutParams(new LinearLayout.LayoutParams(cellWidth, rowHeight));
        return et;
    }

    private android.graphics.drawable.GradientDrawable createCellBorder(int color) {
        if (cellBorderCache == null) {
            cellBorderCache = new android.graphics.drawable.GradientDrawable();
            cellBorderCache.setStroke(2, 0xFF000000);
        }
        android.graphics.drawable.GradientDrawable d = (android.graphics.drawable.GradientDrawable) cellBorderCache.getConstantState().newDrawable().mutate();
        d.setColor(color);
        return d;
    }

    private android.graphics.drawable.GradientDrawable createHeaderBorder(int color) {
        if (headerBorderCache == null) {
            headerBorderCache = new android.graphics.drawable.GradientDrawable();
            headerBorderCache.setStroke(2, 0xFF000000);
        }
        android.graphics.drawable.GradientDrawable d = (android.graphics.drawable.GradientDrawable) headerBorderCache.getConstantState().newDrawable().mutate();
        d.setColor(color);
        return d;
    }

    private String formatDateDisplay(String fullDate) {
        try {
            int month = Integer.parseInt(fullDate.substring(5, 7));
            int day = Integer.parseInt(fullDate.substring(8, 10));
            return month + "月" + day + "日";
        } catch (Exception e) {
            return fullDate;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupScrollSync() {
        ViewTreeObserver.OnScrollChangedListener syncListener = () -> {
            if (isSyncing) return;
            isSyncing = true;
            int scrollX = headerScrollView.getScrollX();
            for (HorizontalScrollView sv : rowScrollViews) {
                if (sv != null) {
                    int x = sv.getScrollX();
                    if (x != scrollX) {
                        scrollX = x;
                        break;
                    }
                }
            }
            final int finalScrollX = scrollX;
            headerScrollView.scrollTo(finalScrollX, 0);
            for (HorizontalScrollView sv : rowScrollViews) {
                if (sv != null) sv.scrollTo(finalScrollX, 0);
            }
            isSyncing = false;
        };

        headerScrollView.getViewTreeObserver().addOnScrollChangedListener(syncListener);
        for (final HorizontalScrollView rowScroll : rowScrollViews) {
            if (rowScroll != null) {
                rowScroll.getViewTreeObserver().addOnScrollChangedListener(syncListener);
                rowScroll.setOnTouchListener((v, event) -> {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            lastX = event.getX();
                            lastY = event.getY();
                            break;
                        case MotionEvent.ACTION_MOVE:
                            float dx = Math.abs(event.getX() - lastX);
                            float dy = Math.abs(event.getY() - lastY);
                            if (dx > dy && dx > 5) {
                                verticalScroll.requestDisallowInterceptTouchEvent(true);
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            verticalScroll.requestDisallowInterceptTouchEvent(false);
                            break;
                    }
                    v.performClick();
                    return false;
                });
            }
        }

        headerScrollView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = Math.abs(event.getX() - lastX);
                    float dy = Math.abs(event.getY() - lastY);
                    if (dx > dy && dx > 5) {
                        verticalScroll.requestDisallowInterceptTouchEvent(true);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    verticalScroll.requestDisallowInterceptTouchEvent(false);
                    break;
            }
            v.performClick();
            return false;
        });
    }

    private float lastX, lastY;

    private void scrollToYesterday() {
        verticalScroll.post(() -> {
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_MONTH, -1);
            String target = String.format(Locale.getDefault(), "%d/%02d/%02d",
                    yesterday.get(Calendar.YEAR), yesterday.get(Calendar.MONTH) + 1, yesterday.get(Calendar.DAY_OF_MONTH));
            int index = 0;
            for (DayRecord r : dayRecords) {
                if (r.date.equals(target)) {
                    int scrollY = index * rowHeight * 2;
                    verticalScroll.scrollTo(0, scrollY);
                    break;
                }
                index++;
            }
        });
    }

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
                e.printStackTrace();
            }
            return json;
        }

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

    interface OnTextChanged {
        void onChanged(String text);
    }
}
