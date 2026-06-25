package com.shrimpfarm.app;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FeedingRecordActivity extends BaseActivity {

    private RecyclerView recordRecyclerView;
    private HorizontalScrollView headerScrollContainer;
    private FrameLayout loadingOverlay;
    private DatabaseHelper dbHelper;
    private String currentBatchId;
    private String stockingDate;

    private int cellWidth;
    private int remarkWidth;
    private int rowHeight;

    private int masterScrollX = 0;
    private boolean isSyncingScroll = false;

    private RecordAdapter adapter;
    private final List<DayRecord> allRecords = new ArrayList<>();
    private final Handler debounceHandler = new Handler();
    private final Map<String, Runnable> pendingSaves = new HashMap<>();

    private volatile boolean isLoadingMore = false;
    private volatile boolean hasMoreData = true;
    private static final int PAGE_SIZE = 50;

    private android.graphics.drawable.GradientDrawable headerBorderCache;
    private android.graphics.drawable.GradientDrawable cellBorderCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feeding_record);

        dbHelper = new DatabaseHelper(this);

        TextView headerFixedDate = findViewById(R.id.header_fixed_date);
        headerScrollContainer = findViewById(R.id.header_scroll_container);
        recordRecyclerView = findViewById(R.id.record_recycler_view);
        loadingOverlay = findViewById(R.id.loading_overlay);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        cellWidth = (int) (screenWidth / 6.5);
        remarkWidth = (int) (screenWidth / 3.5);
        rowHeight = (int) (40 * getResources().getDisplayMetrics().density);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) headerFixedDate.getLayoutParams();
        params.width = cellWidth;
        headerFixedDate.setLayoutParams(params);
        headerFixedDate.setBackground(createCellBorder(0xFF2D8C42));
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

        setupHeader();
        setupRecyclerView();
        loadInitialData();

        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        scrollToYesterday();
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

    // ==================== Header ====================

    private void setupHeader() {
        headerScrollContainer.removeAllViews();
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setLayoutParams(new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.MATCH_PARENT));
        headerRow.addView(createHeaderCell("早餐", cellWidth, 0xFF2D8C42));
        headerRow.addView(createHeaderCell("午餐", cellWidth, 0xFF2D8C42));
        headerRow.addView(createHeaderCell("晚餐", cellWidth, 0xFF2D8C42));
        headerRow.addView(createHeaderCell("夜宵", cellWidth, 0xFF2D8C42));
        headerRow.addView(createHeaderCell("调水", cellWidth * 2, 0xFF2D8C42));
        headerRow.addView(createHeaderCell("备注", remarkWidth, 0xFF2D8C42));
        headerScrollContainer.addView(headerRow);

        headerScrollContainer.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (isSyncingScroll) return;
            isSyncingScroll = true;
            masterScrollX = scrollX;
            syncVisibleRows();
            isSyncingScroll = false;
        });
    }

    private TextView createHeaderCell(String text, int width, @SuppressWarnings("SameParameterValue") int bgColor) {
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

    // ==================== RecyclerView setup ====================

    private void setupRecyclerView() {
        adapter = new RecordAdapter();
        recordRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        recordRecyclerView.setAdapter(adapter);

        recordRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;
                int lastVisible = lm.findLastVisibleItemPosition();
                int totalCount = lm.getItemCount();
                if (!isLoadingMore && hasMoreData && lastVisible >= totalCount - 5) {
                    loadMoreData();
                }
            }
        });
    }

    // ==================== Data loading ====================

    private void loadInitialData() {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
        new Thread(() -> {
            int totalDays = dbHelper.getTotalDaysInBatch(currentBatchId);
            List<DayRecord> records = dbHelper.getRecordsByPage(currentBatchId, totalDays, 0);
            hasMoreData = false;
            runOnUiThread(() -> {
                allRecords.addAll(records);
                adapter.notifyItemRangeInserted(0, records.size());
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                scrollToYesterday();
            });
        }).start();
    }

    private void loadMoreData() {
        if (isLoadingMore || !hasMoreData) return;
        isLoadingMore = true;
        new Thread(() -> {
            int offset = allRecords.size();
            List<DayRecord> records = dbHelper.getRecordsByPage(currentBatchId, PAGE_SIZE, offset);
            hasMoreData = records.size() >= PAGE_SIZE;
            runOnUiThread(() -> {
                int startPos = allRecords.size();
                allRecords.addAll(records);
                adapter.notifyItemRangeInserted(startPos, records.size());
                isLoadingMore = false;
            });
        }).start();
    }

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
            } catch (Exception ignored) {}
        }
        if (!parsed) return;

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
            } catch (Exception ignored) {}
        }
        if (targetIndex >= 0) {
            int finalTarget = targetIndex;
            recordRecyclerView.post(() -> {
                LinearLayoutManager lm = (LinearLayoutManager) recordRecyclerView.getLayoutManager();
                if (lm != null) lm.scrollToPositionWithOffset(finalTarget, 0);
            });
        }
    }

    // ==================== Scroll sync ====================

    private void syncVisibleRows() {
        for (int i = 0; i < recordRecyclerView.getChildCount(); i++) {
            View child = recordRecyclerView.getChildAt(i);
            RecyclerView.ViewHolder vh = recordRecyclerView.getChildViewHolder(child);
            if (vh instanceof RecordViewHolder) {
                ((RecordViewHolder) vh).rowScroll.scrollTo(masterScrollX, 0);
            }
        }
    }

    // ==================== Debounced save ====================

    private void scheduleSave(DayRecord record) {
        String key = record.date;
        Runnable existing = pendingSaves.get(key);
        if (existing != null) debounceHandler.removeCallbacks(existing);
        Runnable saveTask = () -> {
            dbHelper.saveRecordWithTransaction(currentBatchId, record);
            pendingSaves.remove(key);
        };
        pendingSaves.put(key, saveTask);
        debounceHandler.postDelayed(saveTask, 500);
    }

    // ==================== Product selector ====================

    private void showProductSelector(final String columnId, final int adapterPosition, final DayRecord record) {
        final String title;
        final List<DatabaseHelper.PresetItem> presets;
        if (columnId.startsWith("mix")) {
            presets = dbHelper.getMixPresetsSorted(currentBatchId);
            title = "选择拌料动保";
        } else {
            presets = dbHelper.getWaterPresetsSorted(currentBatchId);
            title = "选择调水动保";
        }

        if (presets.isEmpty()) {
            String msg = "请先在基础数据中设置" + (columnId.startsWith("mix") ? "拌料动保" : "调水动保");
            showStyledConfirmDialog("提示", msg,
                    new String[]{"取消", "去设置"}, null,
                    new DialogInterface.OnClickListener[]{null, (d, w) -> {
                        Intent intent = new Intent(FeedingRecordActivity.this, BasicDataActivity.class);
                        intent.putExtra("open_tab", columnId.startsWith("mix") ? 1 : 2);
                        startActivity(intent);
                    }});
            return;
        }

        List<String> displayNames = new ArrayList<>();
        for (DatabaseHelper.PresetItem item : presets) displayNames.add(item.displayName);
        displayNames.add("");

        Dialog dialog = new Dialog(FeedingRecordActivity.this);
        @SuppressLint("InflateParams")
        View dialogView = LayoutInflater.from(FeedingRecordActivity.this).inflate(R.layout.dialog_simple_list, null);
        dialog.setContentView(dialogView);

        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        tvTitle.setText(title);

        ListView listView = dialogView.findViewById(R.id.list_view);
        ArrayAdapter<String> ad = new ArrayAdapter<>(FeedingRecordActivity.this, android.R.layout.simple_list_item_1, displayNames);
        listView.setAdapter(ad);
        listView.setOnItemClickListener((parent, view, which, id) -> {
            if (which >= presets.size()) {
                setDayRecordField(record, columnId, "");
            } else {
                DatabaseHelper.PresetItem selected = presets.get(which);
                String prefix = columnId.startsWith("mix") ? "【拌料】" : "【调水】";
                String fullValue = prefix + selected.tagContent + "+" + selected.displayName;
                setDayRecordField(record, columnId, fullValue);
            }
            scheduleSave(record);
            dialog.dismiss();
            adapter.notifyItemChanged(adapterPosition);
        });
        dialog.show();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(screenWidth / 2, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private void setDayRecordField(DayRecord record, String field, String value) {
        switch (field) {
            case "breakfast": record.breakfast = value; break;
            case "lunch": record.lunch = value; break;
            case "dinner": record.dinner = value; break;
            case "nightSnack": record.nightSnack = value; break;
            case "waterMix1": record.waterMix1 = value; break;
            case "waterMix2": record.waterMix2 = value; break;
            case "waterMix3": record.waterMix3 = value; break;
            case "waterMix4": record.waterMix4 = value; break;
            case "mix1": record.mix1 = value; break;
            case "mix2": record.mix2 = value; break;
            case "mix3": record.mix3 = value; break;
            case "mix4": record.mix4 = value; break;
            case "remark": record.remark = value; break;
        }
    }

    // ==================== Drawing helpers ====================

    private android.graphics.drawable.GradientDrawable createCellBorder(int color) {
        if (cellBorderCache == null) {
            cellBorderCache = new android.graphics.drawable.GradientDrawable();
            cellBorderCache.setStroke(2, 0xFF000000);
        }
        android.graphics.drawable.GradientDrawable.ConstantState cs = cellBorderCache.getConstantState();
        android.graphics.drawable.GradientDrawable d;
        if (cs != null) {
            d = (android.graphics.drawable.GradientDrawable) cs.newDrawable().mutate();
        } else {
            d = new android.graphics.drawable.GradientDrawable();
        }
        d.setColor(color);
        return d;
    }

    private android.graphics.drawable.GradientDrawable createHeaderBorder(int color) {
        if (headerBorderCache == null) {
            headerBorderCache = new android.graphics.drawable.GradientDrawable();
            headerBorderCache.setStroke(2, 0xFF000000);
        }
        android.graphics.drawable.GradientDrawable.ConstantState cs = headerBorderCache.getConstantState();
        android.graphics.drawable.GradientDrawable d;
        if (cs != null) {
            d = (android.graphics.drawable.GradientDrawable) cs.newDrawable().mutate();
        } else {
            d = new android.graphics.drawable.GradientDrawable();
        }
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

    private boolean isDateAfterStocking(String recordDate) {
        if (stockingDate == null || stockingDate.isEmpty() || "选择日期".equals(stockingDate)) {
            return true;
        }
        String a = recordDate.replace("/", "-");
        String b = stockingDate.replace("/", "-");
        return a.compareTo(b) >= 0;
    }

    // ==================== ViewHolder ====================

    static class RecordViewHolder extends RecyclerView.ViewHolder {
        TextView dateView;
        HorizontalScrollView rowScroll;

        RecordViewHolder(View itemView) {
            super(itemView);
            dateView = itemView.findViewById(R.id.record_date);
            rowScroll = itemView.findViewById(R.id.row_scroll);
        }
    }

    // ==================== Adapter ====================

    class RecordAdapter extends RecyclerView.Adapter<RecordViewHolder> {

        @Override
        public int getItemCount() {
            return allRecords.size();
        }

        @Override
        @SuppressLint("ClickableViewAccessibility")
        public @NonNull RecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout root = new LinearLayout(FeedingRecordActivity.this);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, rowHeight * 2));

            // Date cell (fixed left column)
            TextView dateView = new TextView(FeedingRecordActivity.this);
            dateView.setId(R.id.record_date);
            dateView.setTextSize(13);
            dateView.setTextColor(0xFF2E7D32);
            dateView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            dateView.setGravity(Gravity.CENTER);
            dateView.setBackground(createCellBorder(0xFFE8F5E9));
            dateView.setLayoutParams(new LinearLayout.LayoutParams(cellWidth, rowHeight * 2));
            root.addView(dateView);

            // Horizontal scrollable area
            HorizontalScrollView hsv = new HorizontalScrollView(FeedingRecordActivity.this);
            hsv.setId(R.id.row_scroll);
            hsv.setLayoutParams(new LinearLayout.LayoutParams(0, rowHeight * 2, 1f));
            hsv.setHorizontalScrollBarEnabled(false);
            hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);

            // Scroll content: contains [6 cells wide area + remark]
            LinearLayout scrollContent = new LinearLayout(FeedingRecordActivity.this);
            scrollContent.setOrientation(LinearLayout.HORIZONTAL);
            int totalCellWidth = cellWidth * 6 + remarkWidth;
            scrollContent.setLayoutParams(new LinearLayout.LayoutParams(totalCellWidth, rowHeight * 2));

            // Upper row: breakfast, lunch, dinner, nightSnack, waterMix1, waterMix2
            LinearLayout upperRow = new LinearLayout(FeedingRecordActivity.this);
            upperRow.setOrientation(LinearLayout.HORIZONTAL);
            upperRow.setLayoutParams(new LinearLayout.LayoutParams(cellWidth * 6, rowHeight));
            upperRow.setGravity(Gravity.CENTER_VERTICAL);

            upperRow.addView(buildFeedEdit(R.id.cell_breakfast));
            upperRow.addView(buildFeedEdit(R.id.cell_lunch));
            upperRow.addView(buildFeedEdit(R.id.cell_dinner));
            upperRow.addView(buildFeedEdit(R.id.cell_night_snack));
            upperRow.addView(buildProductEdit(R.id.cell_water_mix1));
            upperRow.addView(buildProductEdit(R.id.cell_water_mix2));

            // Lower row: mix1, mix2, mix3, mix4, waterMix3, waterMix4
            LinearLayout lowerRow = new LinearLayout(FeedingRecordActivity.this);
            lowerRow.setOrientation(LinearLayout.HORIZONTAL);
            lowerRow.setLayoutParams(new LinearLayout.LayoutParams(cellWidth * 6, rowHeight));
            lowerRow.setGravity(Gravity.CENTER_VERTICAL);

            lowerRow.addView(buildProductEdit(R.id.cell_mix1));
            lowerRow.addView(buildProductEdit(R.id.cell_mix2));
            lowerRow.addView(buildProductEdit(R.id.cell_mix3));
            lowerRow.addView(buildProductEdit(R.id.cell_mix4));
            lowerRow.addView(buildProductEdit(R.id.cell_water_mix3));
            lowerRow.addView(buildProductEdit(R.id.cell_water_mix4));

            LinearLayout middleArea = new LinearLayout(FeedingRecordActivity.this);
            middleArea.setOrientation(LinearLayout.VERTICAL);
            middleArea.setLayoutParams(new LinearLayout.LayoutParams(cellWidth * 6, rowHeight * 2));
            middleArea.addView(upperRow);
            middleArea.addView(lowerRow);

            scrollContent.addView(middleArea);

            // Remark cell (full height, rightmost)
            EditText remark = new EditText(FeedingRecordActivity.this);
            remark.setId(R.id.cell_remark);
            remark.setTextSize(12);
            remark.setTextColor(0xFF444444);
            remark.setGravity(Gravity.CENTER);
            remark.setBackground(createCellBorder(0xFFFFFFFF));
            remark.setSingleLine(false);
            remark.setPadding(0, 0, 0, 0);
            remark.setMovementMethod(android.text.method.ScrollingMovementMethod.getInstance());
            remark.setVerticalScrollBarEnabled(true);
            remark.setOnClickListener(v -> {
                remark.requestFocus();
                InputMethodManager imm = (InputMethodManager) FeedingRecordActivity.this.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(remark, InputMethodManager.SHOW_IMPLICIT);
            });
            remark.setLayoutParams(new LinearLayout.LayoutParams(remarkWidth, rowHeight * 2));
            scrollContent.addView(remark);

            hsv.addView(scrollContent);
            root.addView(hsv);

            // --- Scroll sync listener ---
            hsv.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (isSyncingScroll) return;
                isSyncingScroll = true;
                masterScrollX = scrollX;
                headerScrollContainer.scrollTo(masterScrollX, 0);
                syncVisibleRows();
                isSyncingScroll = false;
            });

            // Disallow RecyclerView intercept during horizontal scroll but allow vertical scroll
            hsv.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setTag(R.id.tag_watcher, new float[]{event.getX(), event.getY()});
                        break;
                    case MotionEvent.ACTION_MOVE:
                        Object tag = v.getTag(R.id.tag_watcher);
                        if (tag instanceof float[]) {
                            float[] start = (float[]) tag;
                            float dx = Math.abs(event.getX() - start[0]);
                            float dy = Math.abs(event.getY() - start[1]);
                            if (dx > dy && dx > 10) {
                                recordRecyclerView.requestDisallowInterceptTouchEvent(true);
                            }
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        v.performClick();
                        break;
                }
                return false;
            });

            return new RecordViewHolder(root);
        }

        @Override
        public void onBindViewHolder(RecordViewHolder holder, int position) {
            final DayRecord record = allRecords.get(position);

            holder.dateView.setText(formatDateDisplay(record.date));

            // --- Bind number cells (breakfast, lunch, dinner, nightSnack) ---
            boolean afterStocking = isDateAfterStocking(record.date);
            bindNumberCell(holder.itemView, R.id.cell_breakfast, record.breakfast, record, "breakfast", afterStocking);
            bindNumberCell(holder.itemView, R.id.cell_lunch, record.lunch, record, "lunch", afterStocking);
            bindNumberCell(holder.itemView, R.id.cell_dinner, record.dinner, record, "dinner", afterStocking);
            bindNumberCell(holder.itemView, R.id.cell_night_snack, record.nightSnack, record, "nightSnack", afterStocking);

            // --- Bind product cells ---
            bindProductCell(holder.itemView, R.id.cell_water_mix1, record.waterMix1, record, "waterMix1", position);
            bindProductCell(holder.itemView, R.id.cell_water_mix2, record.waterMix2, record, "waterMix2", position);
            bindProductCell(holder.itemView, R.id.cell_mix1, record.mix1, record, "mix1", position);
            bindProductCell(holder.itemView, R.id.cell_mix2, record.mix2, record, "mix2", position);
            bindProductCell(holder.itemView, R.id.cell_mix3, record.mix3, record, "mix3", position);
            bindProductCell(holder.itemView, R.id.cell_mix4, record.mix4, record, "mix4", position);
            bindProductCell(holder.itemView, R.id.cell_water_mix3, record.waterMix3, record, "waterMix3", position);
            bindProductCell(holder.itemView, R.id.cell_water_mix4, record.waterMix4, record, "waterMix4", position);

            // --- Bind remark ---
            EditText remarkEt = holder.itemView.findViewById(R.id.cell_remark);
            remarkEt.setOnFocusChangeListener(null);
            remarkEt.setText(record.remark);
            if (remarkEt.isFocused()) remarkEt.setSelection(remarkEt.length());
            remarkEt.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    ((EditText) v).setSelection(((EditText) v).length());
                } else {
                    record.remark = ((EditText) v).getText().toString();
                    scheduleSave(record);
                }
            });

            // Restore horizontal scroll position
            holder.rowScroll.scrollTo(masterScrollX, 0);
        }

        // ---- Cell builders (called once per view creation) ----

        private EditText buildFeedEdit(int id) {
            EditText et = new EditText(FeedingRecordActivity.this);
            et.setId(id);
            et.setTextSize(16);
            et.setTextColor(0xFF444444);
            et.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            et.setGravity(Gravity.CENTER);
            et.setBackground(createCellBorder(0xFFFFFFFF));
            et.setPadding(0, 0, 0, 0);
            et.setOverScrollMode(View.OVER_SCROLL_NEVER);
            et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            et.setSingleLine(true);
            et.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            et.setFilters(new InputFilter[]{
                    (source, start, end, dest, dstart, dend) -> {
                        String newText = dest.toString().substring(0, dstart)
                                + source.toString()
                                + dest.toString().substring(dend);
                        if (newText.isEmpty()) return null;
                        if (!newText.matches("^\\d*(\\.\\d?)?$")) return "";
                        return null;
                    }
            });
            et.setLayoutParams(new LinearLayout.LayoutParams(cellWidth, rowHeight));
            return et;
        }

        private EditText buildProductEdit(int id) {
            final EditText et = new EditText(FeedingRecordActivity.this);
            et.setId(id);
            et.setTextSize(12);
            et.setTextColor(0xFF000000);
            et.setGravity(Gravity.CENTER);
            et.setBackground(createCellBorder(0xFFFFFFFF));
            et.setPadding(0, 0, 0, 0);
            et.setOverScrollMode(View.OVER_SCROLL_NEVER);
            et.setSingleLine(false);
            et.setMaxLines(2);
            et.setFocusable(false);
            et.setFocusableInTouchMode(false);
            et.setClickable(true);
            et.setLayoutParams(new LinearLayout.LayoutParams(cellWidth, rowHeight));
            return et;
        }

        // ---- Binding helpers (called on every bind) ----

        private void bindNumberCell(View root, int cellId, String value, DayRecord record, String field, boolean enabled) {
            final EditText et = root.findViewById(cellId);
            Object tag = et.getTag(R.id.tag_watcher);
            if (tag instanceof TextWatcher) {
                et.removeTextChangedListener((TextWatcher) tag);
            }
            et.setOnFocusChangeListener(null);
            et.setText(value);
            et.setEnabled(enabled);
            et.setFocusable(enabled);
            et.setFocusableInTouchMode(enabled);
            et.setClickable(enabled);
            if (enabled) {
                et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                et.setTextColor(0xFF444444);
                et.setBackground(createCellBorder(0xFFFFFFFF));
            } else {
                et.setInputType(android.text.InputType.TYPE_NULL);
                et.setKeyListener(null);
                et.setTextColor(0xFFCCCCCC);
                et.setBackground(createCellBorder(0xFFF5F5F5));
            }
            if (et.isFocused()) et.setSelection(et.length());
            if (enabled) {
                TextWatcher watcher = new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override public void afterTextChanged(Editable s) {
                        setDayRecordField(record, field, s.toString().trim());
                        scheduleSave(record);
                    }
                };
                et.addTextChangedListener(watcher);
                et.setTag(R.id.tag_watcher, watcher);
                et.setOnFocusChangeListener((v, hasFocus) -> {
                    if (!hasFocus) {
                        String s = et.getText().toString().trim();
                        if (!s.isEmpty()) {
                            try {
                                double val = Double.parseDouble(s);
                                double rounded = Math.round(val * 10.0) / 10.0;
                                String formatted = (rounded == (long) rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
                                et.removeTextChangedListener(watcher);
                                et.setText(formatted);
                                et.setSelection(formatted.length());
                                et.addTextChangedListener(watcher);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                });
            }
        }

        private void bindProductCell(View root, int cellId, String value, DayRecord record, String field, int position) {
            final EditText et = root.findViewById(cellId);
            String display = DatabaseHelper.extractProductName(value);
            et.setText(display);
            et.setOnClickListener(v -> showProductSelector(field, position, record));
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
