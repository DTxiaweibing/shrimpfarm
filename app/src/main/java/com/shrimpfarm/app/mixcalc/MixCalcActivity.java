package com.shrimpfarm.app.mixcalc;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.BasicDataActivity;
import com.shrimpfarm.app.BatchManageActivity;
import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.checkfeed.CheckFeedActivity;
import com.shrimpfarm.app.mixcalc.model.FeedData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MixCalcActivity extends BaseActivity {

    private static final String TAG = "MixCalc";
    private static final String PREFS_NAME = "MixCalcPrefs";
    private static final String KEY_WATER_PERCENTAGE = "water_percentage";
    private static final String KEY_COLUMN_VISIBILITY = "column_visibility_";
    private static final String KEY_FIXED_COLUMN = "fixed_column_";
    private static final String KEY_SAVED_DATA = "saved_data";
    private static final String KEY_GLOBAL_WATER = "global_water";
    private static final String KEY_NEVER_SHOW_DOWNLOAD_DIALOG = "never_show_download_dialog";
    private static final String KEY_FERMENTED_INCLUDED_IN_WATER = "fermented_included_in_water";
    private static final String KEY_FERMENTED_INCLUDED_IN_AVERAGE = "fermented_included_in_average";

    private static final String KEY_SHED_COUNT_LIMIT = "shed_count_limit";

    // 视图组件
    private LinearLayout dataContent;
    private LinearLayout headerScrollContent;
    private LinearLayout footerScrollContent;
    private LinearLayout fixedHeaderContainer;
    private LinearLayout fixedFooterContainer;
    private HorizontalScrollView headerScrollView;
    private HorizontalScrollView footerScrollView;
    private ScrollView verticalScrollView;

    // 数据
    private List<FeedData> dataList;
    private List<HorizontalScrollView> rowScrollViews;
    private List<LinearLayout> fixedDataRows;
    private HashSet<String> listenersAddedSet = new HashSet<>();
    private int globalWaterPercentage = 20;

    // 发酵料计算标志
    private boolean fermentedIncludedInWater = false;
    private boolean fermentedIncludedInAverage = false;

    // 棚数限制相关变量
    private int shedCountLimit = 0;
    private boolean isShedCountExceeded = false;
    private Drawable normalShedCountBg;
    private Drawable warningShedCountBg;

    // 懒加载相关变量
    private int screenVisibleRows = 10;
    private int currentlyLoadedRows = 0;
    private Handler lazyHandler = new Handler();

    // 列管理
    private Map<String, Boolean> columnVisibility = new HashMap<String, Boolean>();
    private Map<String, Boolean> columnFixed = new HashMap<String, Boolean>();
    private final String[] columnIds = {
        "seq", "shed_number", "shed_count", "average_feed", 
        "fermented", "powder", "feed03", "feed05", "feed10", 
        "water", "weighed_feed"
    };

    private final String[] columnNames = {
        "序号", "棚号", "棚数", "平均吃料", 
        "发酵料", "粉料", "0.3料", "0.5料", "1.0料", 
        "水", "称料"
    };

    private final int[] columnWidths = {40, 60, 40, 40, 60, 60, 60, 60, 60, 60, 60};

    // 平板适配相关成员变量
    private boolean isTabletMode = false;
    private int[] adaptiveColumnWidths;

    // 当前滚动位置
    private int currentScrollX = 0;

    // 防止递归调用标志
    private boolean isSyncing = false;

    // 触摸事件相关
    private float lastX, lastY;
    private boolean isHorizontalScroll = false;

    // 滚动监听器列表
    private List<ViewTreeObserver.OnScrollChangedListener> scrollListeners = new ArrayList<>();

    // 单元格映射
    private Map<String, View> cellViewMap = new HashMap<>();

    // 数据行容器映射
    private Map<Integer, LinearLayout> rowLayoutMap = new HashMap<>();

    // 统计行单元格映射
    private Map<String, TextView> footerCellMap = new HashMap<>();

    // 新增：数据是否已加载标志
    private boolean isDataLoaded = false;

    // 总数据行数
    private static final int TOTAL_ROWS = 30;

    // 序号列统计行View引用
    private TextView seqStatTextView;

    // 批次与数据库相关
    private String currentBatchId;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_mix_calc);

            // ---------- 批次与基础数据检查 ----------
            SharedPreferences appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
            currentBatchId = appPrefs.getString("current_batch_id", "");
            dbHelper = new DatabaseHelper(this);

            if (currentBatchId.isEmpty()) {
                showNoBatchDialog();
                return;
            }

            if (!isBasicDataComplete()) {
                showBasicDataIncompleteDialog();
                return;
            }

            // 从基础数据读取池塘数量作为棚数上限
            String pondCountStr = dbHelper.getBasicData(currentBatchId, "pond_count");
            try {
                shedCountLimit = Integer.parseInt(pondCountStr);
            } catch (NumberFormatException e) {
                shedCountLimit = 0;
            }
            // 保存到 SharedPreferences 供内部使用
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(KEY_SHED_COUNT_LIMIT, shedCountLimit).apply();

            // ---------- 原有初始化 ----------
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            // 第1步：初始化视图（立即）
            initViews();

            // 初始化背景资源
            normalShedCountBg = ContextCompat.getDrawable(this, R.drawable.cell_border);
            warningShedCountBg = ContextCompat.getDrawable(this, R.drawable.cell_border_warning);

            // 第2步：快速加载设置
            loadQuickSettings();

            // 第3步：计算屏幕能显示多少行
            calculateScreenVisibleRows();

            // 第4步：先创建空表格（不带监听器）
            buildEmptyTableWithoutListeners();

            // 第5步：立即加载监听器和数据（优化：只为有数据的行）
            lazyInitializeListenersAndData();

        } catch (Exception e) {
            Log.e(TAG, "启动异常: " + e.getMessage(), e);
        }

        setupBottomNavigation();
    }

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_mix;
    }

    // 快速加载设置
    private void loadQuickSettings() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            globalWaterPercentage = prefs.getInt(KEY_GLOBAL_WATER, 20);
            fermentedIncludedInWater = prefs.getBoolean(KEY_FERMENTED_INCLUDED_IN_WATER, false);
            fermentedIncludedInAverage = prefs.getBoolean(KEY_FERMENTED_INCLUDED_IN_AVERAGE, false);

        } catch (Exception e) {
            Log.e(TAG, "快速加载设置异常", e);
        }
    }

    // 计算屏幕能显示多少行
    private void calculateScreenVisibleRows() {
        try {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int screenHeight = metrics.heightPixels;

            isTabletMode = isTabletDevice(this);

            if (isTabletMode) {
                screenVisibleRows = 25;
            } else {
                int rowHeight = dpToPx(40);
                int statusBarHeight = getStatusBarHeight();
                int headerHeight = dpToPx(40);
                int footerHeight = dpToPx(40);
                int availableHeight = screenHeight - statusBarHeight - headerHeight - footerHeight;
                screenVisibleRows = (availableHeight / rowHeight) + 2;
                screenVisibleRows = Math.max(5, Math.min(screenVisibleRows, 30));
            }
        } catch (Exception e) {
            Log.e(TAG, "计算可见行数异常", e);
            screenVisibleRows = 15;
        }
    }

    private int getStatusBarHeight() {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(getWindow().getDecorView());
        if (insets != null) {
            return insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
        }
        return 0;
    }

    private boolean isTabletDevice(Context context) {
        try {
            DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
            float widthInches = displayMetrics.widthPixels / displayMetrics.xdpi;
            float heightInches = displayMetrics.heightPixels / displayMetrics.ydpi;
            double diagonalInches = Math.sqrt(Math.pow(widthInches, 2) + Math.pow(heightInches, 2));
            return diagonalInches >= 7.0;
        } catch (Exception e) {
            Log.e(TAG, "检测平板设备异常: " + e.getMessage());
            return false;
        }
    }

    // 创建空表格（不带监听器）
    private void buildEmptyTableWithoutListeners() {
        try {
            // 清理监听器记录
            listenersAddedSet.clear();
            
            // 1. 初始化数据结构
            initData();

            // 2. 初始化列设置
            initTabletAdaptiveSettings();
            initColumnVisibility();
            initColumnFixedState();
            loadColumnVisibility();
            loadColumnFixedState();

            // 3. 构建表头和统计行
            int scrollWidth = calculateScrollWidth();
            buildHeader(scrollWidth);
            buildFooter(scrollWidth);

            // 4. 创建所有行的空表格
            for (int i = 0; i < TOTAL_ROWS; i++) {
                buildEmptyRowWithoutListeners(i, scrollWidth);
                currentlyLoadedRows++;
            }

            // 5. 设置滚动同步
            setupScrollSync();

        } catch (Exception e) {
            Log.e(TAG, "创建空表格异常", e);
        }
    }

    // 创建空行（不带监听器）
    private void buildEmptyRowWithoutListeners(final int rowIndex, int scrollWidth) {
        LinearLayout rowLayout = new LinearLayout(this);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                                      LinearLayout.LayoutParams.MATCH_PARENT, 
                                      dpToPx(40)
                                  ));

        LinearLayout fixedLayout = createFixedColumnsWithoutListeners(rowIndex);
        rowLayout.addView(fixedLayout);

        HorizontalScrollView scrollView = createScrollColumnsWithoutListeners(rowIndex, scrollWidth);
        rowLayout.addView(scrollView);

        dataContent.addView(rowLayout);

        rowLayoutMap.put(rowIndex, rowLayout);
        rowScrollViews.add(scrollView);
        fixedDataRows.add(fixedLayout);
    }

    // 创建固定列（不带监听器）
    private LinearLayout createFixedColumnsWithoutListeners(final int rowIndex) {
        LinearLayout fixedLayout = new LinearLayout(this);
        fixedLayout.setOrientation(LinearLayout.HORIZONTAL);

        int fixedWidth = 0;

        View seqView = createCellWithoutListeners("seq", rowIndex, 0);
        fixedLayout.addView(seqView);
        fixedWidth += adaptiveColumnWidths[0];

        for (int i = 1; i < columnIds.length; i++) {
            String columnId = columnIds[i];
            boolean visible = columnVisibility.get(columnId);
            boolean fixed = columnFixed.get(columnId);

            if (visible && fixed) {
                View cellView = createCellWithoutListeners(columnId, rowIndex, i);
                fixedLayout.addView(cellView);
                fixedWidth += adaptiveColumnWidths[i];
            }
        }

        fixedLayout.setLayoutParams(new LinearLayout.LayoutParams(
                                        fixedWidth,
                                        dpToPx(40)
                                    ));

        return fixedLayout;
    }

    // 创建滚动列（不带监听器）
    private HorizontalScrollView createScrollColumnsWithoutListeners(final int rowIndex, int scrollWidth) {
        HorizontalScrollView scrollView = new HorizontalScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                                       0,
                                       dpToPx(40),
                                       1.0f
                                   ));
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.HORIZONTAL);
        scrollContent.setLayoutParams(new LinearLayout.LayoutParams(
                                          scrollWidth,
                                          dpToPx(40)
                                      ));

        for (int i = 0; i < columnIds.length; i++) {
            String columnId = columnIds[i];
            boolean visible = columnVisibility.get(columnId);
            boolean fixed = columnFixed.get(columnId);

            if (visible && !fixed) {
                View cellView = createCellWithoutListeners(columnId, rowIndex, i);
                scrollContent.addView(cellView);
            }
        }

        scrollView.addView(scrollContent);
        return scrollView;
    }

    // 创建不带监听器的单元格
    private View createCellWithoutListeners(String columnId, int rowIndex, int columnIndex) {
        boolean isEditable = isEditableColumn(columnId);

        if (isEditable) {
            EditText cellView = new EditText(this);
            cellView.setLayoutParams(new LinearLayout.LayoutParams(
                                         adaptiveColumnWidths[columnIndex],
                                         dpToPx(40)
                                     ));
            cellView.setGravity(Gravity.CENTER);
            cellView.setTextSize(12);
            cellView.setTypeface(cellView.getTypeface(), android.graphics.Typeface.BOLD);
            cellView.setBackgroundResource(R.drawable.cell_border);
            cellView.setPadding(0, 0, 0, 0);
            cellView.setSingleLine(true);
            cellView.setTag(rowIndex + "_" + columnId);
            cellView.setEnabled(false);
            cellView.setImeOptions(EditorInfo.IME_ACTION_NEXT);

            if ("shed_number".equals(columnId)) {
                cellView.setInputType(InputType.TYPE_CLASS_TEXT);
                cellView.setSingleLine(false);
                cellView.setMaxLines(2);
            } else {
                cellView.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            }

            cellViewMap.put(rowIndex + "_" + columnId, cellView);
            return cellView;
        } else {
            TextView cellView = new TextView(this);
            cellView.setLayoutParams(new LinearLayout.LayoutParams(
                                         adaptiveColumnWidths[columnIndex],
                                         dpToPx(40)
                                     ));
            cellView.setGravity(Gravity.CENTER);
            cellView.setTextSize(12);
            cellView.setTypeface(cellView.getTypeface(), android.graphics.Typeface.BOLD);
            cellView.setBackgroundResource(R.drawable.cell_border);
            cellView.setPadding(0, 0, 0, 0);
            cellView.setTag(rowIndex + "_" + columnId);
            cellViewMap.put(rowIndex + "_" + columnId, cellView);

            if ("seq".equals(columnId)) {
                cellView.setText(String.valueOf(rowIndex + 1));
                cellView.setTextColor(0xFF666666);
            } else if ("shed_number".equals(columnId)) {
                cellView.setText(getString(R.string.shed_number_format, rowIndex + 1));
                cellView.setTextColor(Color.BLACK);
                cellView.setSingleLine(false);
                cellView.setMaxLines(2);
            } else {
                cellView.setText("");
            }

            return cellView;
        }
    }

    // 立即初始化监听器和数据（移除延迟）
    private void lazyInitializeListenersAndData() {
        try {
            // 确保清理监听器记录
            listenersAddedSet.clear();
            
            loadAllDataInBackground();
            
            // 初始时所有行只添加棚号/棚数监听器
            for (int i = 0; i < dataList.size(); i++) {
                FeedData data = dataList.get(i);
                addShedListenersToRow(i);
                
                // 如果该行已有其他列数据，需要加载其他列监听器
                if (hasOtherColumnData(data)) {
                    loadOtherColumnListenersIfNeeded(i);
                }
                refreshRowCells(i);
            }
            
            updateFooterRow();
        } catch (Exception e) {
            Log.e(TAG, "初始化异常", e);
        }
    }
    
    // 检查行是否有其他列（除棚号/棚数外）的数据
    private boolean hasOtherColumnData(FeedData data) {
        return data != null && (
            data.getFermentedFeed() > 0 ||
            data.getPowderFeed() > 0 ||
            data.getFeed03() > 0 ||
            data.getFeed05() > 0 ||
            data.getFeed10() > 0
        );
    }
    
    // 检查行是否有棚号或棚数数据
    private boolean hasShedData(FeedData data) {
        return data != null && (!data.getShedNumber().isEmpty() || data.getShedCount() > 0);
    }

    // 在后台加载数据
    private void loadAllDataInBackground() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String dataJson = prefs.getString(KEY_SAVED_DATA, null);

            if (dataJson == null || dataJson.isEmpty()) {
                return;
            }

            JSONArray jsonArray = new JSONArray(dataJson);
            int count = Math.min(jsonArray.length(), TOTAL_ROWS);

            for (int i = 0; i < count; i++) {
                try {
                    JSONObject json = jsonArray.getJSONObject(i);
                    FeedData data = dataList.get(i);
                    if (data == null) {
                        data = new FeedData();
                        dataList.set(i, data);
                    }

                    data.fromJson(json);
                    data.updateAllCalculations(globalWaterPercentage, fermentedIncludedInWater, fermentedIncludedInAverage);

                } catch (Exception e) {
                    Log.e(TAG, "解析第" + i + "行数据异常", e);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "后台加载异常", e);
        }
    }

    // 为单行添加监听器（棚号和棚数）
    private void addShedListenersToRow(int rowIndex) {
        try {
            FeedData data = dataList.get(rowIndex);
            if (data == null) return;

            // 只为棚号和棚数列添加监听器
            String[] shedColumns = {"shed_number", "shed_count"};
            
            for (String columnId : shedColumns) {
                int columnIndex = getColumnIndex(columnId);
                if (columnIndex < 0) continue;
                
                View view = cellViewMap.get(rowIndex + "_" + columnId);
                if (view != null) {
                    if (view instanceof TextView && !(view instanceof EditText)) {
                        replaceTextViewWithEditText(rowIndex, columnId, columnIndex, (TextView)view);
                    } else if (view instanceof EditText) {
                        EditText editText = (EditText) view;

                        String initialValue = getCellInitialValue(data, columnId);
                        editText.setText(initialValue);

                        addShedTextWatcher(editText, rowIndex, columnId, columnIndex);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "为行添加监听器异常: row=" + rowIndex, e);
        }
    }
    
    // 获取列索引
    private int getColumnIndex(String columnId) {
        for (int i = 0; i < columnIds.length; i++) {
            if (columnIds[i].equals(columnId)) {
                return i;
            }
        }
        return -1;
    }

    // 为棚号/棚数添加TextWatcher（使用 HashSet 检查）
    private void addShedTextWatcher(final EditText editText, final int rowIndex, final String columnId, final int columnIndex) {
        String key = rowIndex + "_" + columnId;
        if (listenersAddedSet.contains(key)) {
            return; // 已添加过
        }
        listenersAddedSet.add(key);
        
        // 启用编辑
        editText.setEnabled(true);
        
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if ("shed_number".equals(columnId)) {
                    String text = s.toString().trim();
                    final FeedData data = dataList.get(rowIndex);
                    data.setShedNumber(text);
                    data.updateAllCalculations(globalWaterPercentage, fermentedIncludedInWater, fermentedIncludedInAverage);
                    updateShedCountDisplay(rowIndex, data);
                    updateCalculatedCells(rowIndex);
                    updateRowInputEnabled(rowIndex);
                    updateFooterRow();
                    saveAllData();
                    checkShedCountLimitAndUpdateInput();
                } else if ("shed_count".equals(columnId)) {
                    // 棚数的处理在焦点监听器中
                }
            }
        });
        
        // 棚数的焦点监听器单独添加
        if ("shed_count".equals(columnId)) {
            editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (!hasFocus) {
                        String text = editText.getText().toString().trim();
                        final FeedData data = dataList.get(rowIndex);

                        double newValue = 0;
                        if (!text.isEmpty()) {
                            try {
                                newValue = Double.parseDouble(text);
                            } catch (NumberFormatException e) {
                                newValue = 0;
                                editText.setText("");
                            }
                        }

                        data.setShedCount((int)newValue);

                        if (newValue == 0 || text.isEmpty()) {
                            data.setShedCountManuallyModified(false);
                        } else {
                            data.setShedCountManuallyModified(true);
                        }

                        data.updateAllCalculations(globalWaterPercentage, fermentedIncludedInWater, fermentedIncludedInAverage);
                        updateCalculatedCells(rowIndex);
                        updateRowInputEnabled(rowIndex);
                        updateFooterRow();
                        saveAllData();
                        checkShedCountLimitAndUpdateInput();
                        checkAndLoadOtherColumnListeners(rowIndex);
                    }
                }
            });
        }
        
        // 键盘下一步导航
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_NEXT) {
                    moveFocusToNextEditableCell(rowIndex, columnIndex);
                    return true;
                }
                return false;
            }
        });
    }
    
    // 在输入完棚号/棚数后，检查是否需要加载其他列监听器
    private void checkAndLoadOtherColumnListeners(int rowIndex) {
        FeedData data = dataList.get(rowIndex);
        if (data == null) return;
        
        if (hasShedData(data)) {
            loadOtherColumnListenersIfNeeded(rowIndex);
        }
    }
    
    // 延迟加载/移除其他列的监听器（当棚号或棚数有数据时加载，无数据时移除）
    private void loadOtherColumnListenersIfNeeded(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= dataList.size()) return;
        
        FeedData data = dataList.get(rowIndex);
        if (data == null) return;
        
        // 检查棚号或棚数是否有数据
        if (hasShedData(data)) {
            // 有数据：加载其他列监听器（使用Tag避免重复添加）
            String[] otherColumns = {"fermented", "powder", "feed03", "feed05", "feed10"};
            
            for (String columnId : otherColumns) {
                int columnIndex = getColumnIndex(columnId);
                if (columnIndex < 0) continue;
                
                String key = rowIndex + "_" + columnId;
                View view = cellViewMap.get(key);
                if (view == null) continue;
                
                if (view instanceof EditText) {
                    EditText editText = (EditText) view;
                    
                    // 检查是否已添加过监听器（使用 HashSet 检查）
                    if (listenersAddedSet.contains(key)) {
                        continue; // 已添加过，跳过
                    }
                    
                    // 启用编辑
                    editText.setEnabled(true);
                    
                    String initialValue = getCellInitialValue(data, columnId);
                    editText.setText(initialValue);
                    
                    addOriginalTextWatcher(editText, rowIndex, columnId, columnIndex);
                }
            }
            
            updateCalculatedCells(rowIndex);
        } else {
            // 无数据：移除其他列监听器并禁用
            removeOtherColumnListeners(rowIndex);
        }
    }
    
    // 移除其他列的监听器并禁用
    private void removeOtherColumnListeners(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= dataList.size()) return;
        
        String[] otherColumns = {"fermented", "powder", "feed03", "feed05", "feed10"};
        
        for (String columnId : otherColumns) {
            String key = rowIndex + "_" + columnId;
            View view = cellViewMap.get(key);
            if (view == null) continue;
            
            if (view instanceof EditText) {
                EditText editText = (EditText) view;
                editText.setEnabled(false);
                editText.setText("");
            }
        }
        
        // 更新计算单元格
        FeedData data = dataList.get(rowIndex);
        if (data != null) {
            data.setFermentedFeed(0);
            data.setPowderFeed(0);
            data.setFeed03(0);
            data.setFeed05(0);
            data.setFeed10(0);
            data.setWater(0);
            data.setWeighedFeed(0);
            data.setAverage(0);
        }
        
        updateCalculatedCells(rowIndex);
        updateRowInputEnabled(rowIndex);
        updateFooterRow();
    }

    // 替换TextView为EditText（用于棚号列）
    private void replaceTextViewWithEditText(int rowIndex, String columnId, int columnIndex, TextView textView) {
        ViewParent parent = textView.getParent();
        if (parent instanceof LinearLayout) {
            LinearLayout container = (LinearLayout) parent;

            int position = -1;
            for (int i = 0; i < container.getChildCount(); i++) {
                if (container.getChildAt(i) == textView) {
                    position = i;
                    break;
                }
            }

            if (position != -1) {
                container.removeViewAt(position);

                EditText editText = new EditText(this);
                editText.setLayoutParams(new LinearLayout.LayoutParams(
                                             adaptiveColumnWidths[columnIndex],
                                             dpToPx(40)
                                         ));
                editText.setGravity(Gravity.CENTER);
                editText.setTextSize(12);
                editText.setTypeface(editText.getTypeface(), android.graphics.Typeface.BOLD);
                editText.setBackgroundResource(R.drawable.cell_border);
                editText.setPadding(0, 0, 0, 0);
                editText.setTag(rowIndex + "_" + columnId);
                editText.setInputType(InputType.TYPE_CLASS_TEXT);
                editText.setSingleLine(false);
                editText.setMaxLines(2);
                editText.setGravity(Gravity.CENTER);

                FeedData data = dataList.get(rowIndex);
                String initialValue = data.getShedNumber();
                if (initialValue == null || initialValue.isEmpty()) {
                    initialValue = textView.getText().toString();
                    if (initialValue.startsWith("棚")) {
                        initialValue = "";
                    }
                }
                editText.setText(initialValue);

                addOriginalTextWatcher(editText, rowIndex, columnId, columnIndex);
                container.addView(editText, position);
                cellViewMap.put(rowIndex + "_" + columnId, editText);
            }
        }
    }

    // 添加原始的TextWatcher（使用 HashSet 来标记已添加）
    private void addOriginalTextWatcher(final EditText editText, final int rowIndex, final String columnId, final int columnIndex) {
        String key = rowIndex + "_" + columnId;
        if (listenersAddedSet.contains(key)) {
            return; // 已添加过
        }
        listenersAddedSet.add(key);
        
        if ("shed_number".equals(columnId)) {
            editText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        String text = s.toString().trim();
                        final FeedData data = dataList.get(rowIndex);
                        data.setShedNumber(text);
                        data.updateAllCalculations(globalWaterPercentage, fermentedIncludedInWater, fermentedIncludedInAverage);
                        updateShedCountDisplay(rowIndex, data);
                        updateCalculatedCells(rowIndex);
                        updateRowInputEnabled(rowIndex);
                        updateFooterRow();
                        saveAllData();
                        checkShedCountLimitAndUpdateInput();
                    }
                });
        } else if ("shed_count".equals(columnId)) {
            editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        if (!hasFocus) {
                            String text = editText.getText().toString().trim();
                            final FeedData data = dataList.get(rowIndex);

                            double newValue = 0;
                            if (!text.isEmpty()) {
                                try {
                                    newValue = Double.parseDouble(text);
                                } catch (NumberFormatException e) {
                                    newValue = 0;
                                    editText.setText("");
                                }
                            }

                            data.setShedCount((int)newValue);

                            if (newValue == 0 || text.isEmpty()) {
                                data.setShedCountManuallyModified(false);
                            } else {
                                data.setShedCountManuallyModified(true);
                            }

                            data.updateAllCalculations(globalWaterPercentage, fermentedIncludedInWater, fermentedIncludedInAverage);
                            updateCalculatedCells(rowIndex);
                            updateRowInputEnabled(rowIndex);
                            updateFooterRow();
                            saveAllData();
                            checkShedCountLimitAndUpdateInput();
                            checkAndLoadOtherColumnListeners(rowIndex);
                        }
                    }
                });
        } else {
            editText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        String text = s.toString().trim();
                        final FeedData data = dataList.get(rowIndex);

                        double value = 0;
                        if (!text.isEmpty()) {
                            try {
                                value = Double.parseDouble(text);
                            } catch (NumberFormatException e) {
                                value = 0;
                            }
                        }

                        switch (columnId) {
                            case "fermented":
                                data.setFermentedFeed(value);
                                break;
                            case "powder":
                                data.setPowderFeed(value);
                                break;
                            case "feed03":
                                data.setFeed03(value);
                                break;
                            case "feed05":
                                data.setFeed05(value);
                                break;
                            case "feed10":
                                data.setFeed10(value);
                                break;
                        }

                        data.updateAllCalculations(globalWaterPercentage, fermentedIncludedInWater, fermentedIncludedInAverage);
                        updateCalculatedCells(rowIndex);
                        updateFooterRow();
                        saveAllData();
                        checkShedCountLimitAndUpdateInput();
                    }
                });
        }

        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_NEXT) {
                        moveFocusToNextEditableCell(rowIndex, columnIndex);
                        return true;
                    }
                    return false;
                }
            });
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkShedCountLimitAndUpdateInput();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveAllData();
        saveGlobalWaterPercentage();
        saveFermentedIncludedInWater();
        saveFermentedIncludedInAverage();
        clearAllScrollListeners();
        clearAllCellListeners();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveAllData();
        saveGlobalWaterPercentage();
        saveFermentedIncludedInWater();
        saveFermentedIncludedInAverage();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        isTabletMode = isTabletDevice(this);
        if (isTabletMode) {
            initTabletAdaptiveSettings();
            rebuildCompleteTable();
        }
    }



    // 检查棚数限制并更新输入状态
    private void checkShedCountLimitAndUpdateInput() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int loadedLimit = prefs.getInt(KEY_SHED_COUNT_LIMIT, 0);
            shedCountLimit = loadedLimit;

            if (shedCountLimit > 0) {
                Log.d(TAG, "当前棚数限制: " + shedCountLimit);

                double totalShedCount = 0;
                for (FeedData data : dataList) {
                    totalShedCount += data.getShedCount();
                }

                Log.d(TAG, "当前总棚数: " + totalShedCount + ", 限制: " + shedCountLimit);

                boolean newExceedStatus = totalShedCount > shedCountLimit;

                if (newExceedStatus != isShedCountExceeded) {
                    isShedCountExceeded = newExceedStatus;

                    if (isShedCountExceeded) {
                        Log.d(TAG, "棚数超过限制，总棚数=" + totalShedCount + " > 限制=" + shedCountLimit);
                    } else {
                        Log.d(TAG, "棚数恢复正常，总棚数=" + totalShedCount + " ≤ 限制=" + shedCountLimit);
                    }

                    for (int i = 0; i < dataList.size(); i++) {
                        updateRowInputEnabled(i);
                    }
                }
            } else {
                Log.d(TAG, "未设置棚数限制");
                if (isShedCountExceeded) {
                    isShedCountExceeded = false;
                    for (int i = 0; i < dataList.size(); i++) {
                        updateRowInputEnabled(i);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查棚数限制异常: " + e.getMessage());
        }
    }

    // 数据格式化和传递方法
    private String formatDataToMessage() {
        Log.d(TAG, "开始格式化数据为简洁消息");

        try {
            StringBuilder messageBuilder = new StringBuilder();

            messageBuilder.append("拌料数据汇总\n");
            messageBuilder.append("时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date())).append("\n");
            messageBuilder.append("水百分比: ").append(globalWaterPercentage).append("%\n\n");
            messageBuilder.append("料槽数据:\n");
            messageBuilder.append("──────────────────────────────────\n");

            String header = buildHeaderWithExtraSpacing();
            messageBuilder.append(header).append("\n");

            double totalShedCount = 0;
            double totalFermented = 0;
            double totalPowder = 0;
            double totalFeed03 = 0;
            double totalFeed05 = 0;
            double totalFeed10 = 0;
            double totalWater = 0;
            double totalWeighed = 0;

            for (int i = 0; i < dataList.size(); i++) {
                FeedData data = dataList.get(i);
                if (data == null) continue;

                String shedNumber = data.getShedNumber();
                double shedCount = data.getShedCount();
                double fermentedFeed = data.getFermentedFeed();
                double powderFeed = data.getPowderFeed();
                double feed03 = data.getFeed03();
                double feed05 = data.getFeed05();
                double feed10 = data.getFeed10();
                double water = data.getWater();
                double weighedFeed = data.getWeighedFeed();

                double averageFeed = 0;
                if (shedCount > 0) {
                    double totalDryFeed = powderFeed + feed03 + feed05 + feed10;
                    if (fermentedIncludedInAverage) {
                        totalDryFeed += fermentedFeed;
                    }
                    averageFeed = totalDryFeed / shedCount;
                }

                if ((shedNumber != null && !shedNumber.trim().isEmpty()) || shedCount > 0) {
                    String[] rowData = new String[]{
                        formatWithPadding(shedNumber.isEmpty() ? String.valueOf(i + 1) : shedNumber, 8),
                        formatNumberWithPadding(shedCount, 8),
                        formatNumberWithPadding(averageFeed, 8),
                        formatNumberWithPadding(fermentedFeed, 8),
                        formatNumberWithPadding(powderFeed, 8),
                        formatNumberWithPadding(feed03, 8),
                        formatNumberWithPadding(feed05, 8),
                        formatNumberWithPadding(feed10, 8),
                        formatNumberWithPadding(water, 8),
                        formatNumberWithPadding(weighedFeed, 8)
                    };

                    String row = buildDataRowWithSpacing(rowData);
                    messageBuilder.append(row).append("\n");

                    totalShedCount += shedCount;
                    totalFermented += fermentedFeed;
                    totalPowder += powderFeed;
                    totalFeed03 += feed03;
                    totalFeed05 += feed05;
                    totalFeed10 += feed10;
                    totalWater += water;
                    totalWeighed += weighedFeed;
                }
            }

            double totalDryFeed = totalPowder + totalFeed03 + totalFeed05 + totalFeed10;
            if (fermentedIncludedInAverage) {
                totalDryFeed += totalFermented;
            }
            double overallAverageFeed = totalShedCount > 0 ? totalDryFeed / totalShedCount : 0;
            double totalIngredients = totalFermented + totalPowder + totalFeed03 + totalFeed05 + totalFeed10 + totalWater;
            double averageWeighed = totalShedCount > 0 ? totalIngredients / totalShedCount : 0;

            messageBuilder.append("\n汇总统计:\n");
            messageBuilder.append("──────────────────────────────────\n");

            String summary = String.format(Locale.getDefault(), 
                                           "总棚数: %s\n" +
                                           "平均吃料: %s\n" +
                                           "总发酵料: %s\n" +
                                           "总粉料: %s\n" + 
                                           "总03料: %s\n" +
                                           "总05料: %s\n" +
                                           "总10料: %s\n" +
                                           "总水分: %s\n" +
                                           "平均称料: %s\n" +
                                           "总干料: %s\n" +
                                           "水百分比: %d%%",
                                           formatNumberWithPadding(totalShedCount, 8),
                                           formatNumberWithPadding(overallAverageFeed, 8),
                                           formatNumberWithPadding(totalFermented, 8),
                                           formatNumberWithPadding(totalPowder, 8),
                                           formatNumberWithPadding(totalFeed03, 8),
                                           formatNumberWithPadding(totalFeed05, 8),
                                           formatNumberWithPadding(totalFeed10, 8),
                                           formatNumberWithPadding(totalWater, 8),
                                           formatNumberWithPadding(averageWeighed, 8),
                                           formatNumberWithPadding(totalDryFeed, 8),
                                           globalWaterPercentage
                                           );

            messageBuilder.append(summary);

            String message = messageBuilder.toString();
            Log.d(TAG, "两边补空格格式数据格式化完成");
            return message;

        } catch (Exception e) {
            Log.e(TAG, "格式化数据异常: " + e.getMessage());
            return "数据格式化失败: " + e.getMessage();
        }
    }

    // 辅助方法
    private String formatNumberWithPadding(double value, int width) {
        String numberStr;
        if (value == (int) value) {
            numberStr = String.valueOf((int) value);
        } else {
            numberStr = String.format(Locale.getDefault(), "%.2f", value);
        }

        return formatWithPadding(numberStr, width);
    }

    private String formatWithPadding(String content, int width) {
        if (content == null) content = "";

        int totalSpaces = width - content.length();
        if (totalSpaces <= 0) {
            return content.substring(0, width);
        }

        int leftSpaces = totalSpaces / 2;
        int rightSpaces = totalSpaces - leftSpaces;

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < leftSpaces; i++) {
            result.append(" ");
        }
        result.append(content);
        for (int i = 0; i < rightSpaces; i++) {
            result.append(" ");
        }

        return result.toString();
    }

    // 表头行方法
    private String buildHeaderWithExtraSpacing() {
        String[] headers = {"料槽", "棚数", "平均", "发酵料", "粉料", "03料", "05料", "10料", "水分", "称料"};

        StringBuilder headerRow = new StringBuilder();

        for (int i = 0; i < headers.length; i++) {
            String header = headers[i];
            headerRow.append(" ").append(header).append(" ");
            if (i < headers.length - 1) {
                headerRow.append(" ");
            }
        }

        return headerRow.toString();
    }

    private String buildDataRowWithSpacing(String[] rowData) {
        StringBuilder dataRow = new StringBuilder();

        for (int i = 0; i < rowData.length; i++) {
            dataRow.append(rowData[i]);
            if (i < rowData.length - 1) {
                dataRow.append(" ");
            }
        }

        return dataRow.toString();
    }

    // 准备数据并跳转到查料表
    private void prepareDataAndNavigate() {
        Log.d(TAG, "准备数据并跳转到查料表");

        try {
            String message = formatDataToMessage();

            Intent intent = new Intent(MixCalcActivity.this, CheckFeedActivity.class);
            intent.putExtra("FEED_DATA_MESSAGE", message);
            intent.putExtra("WATER_PERCENTAGE", globalWaterPercentage);
            intent.putExtra("TIMESTAMP", System.currentTimeMillis());
            intent.putExtra("SOURCE", "MAIN_ACTIVITY");

            startActivity(intent);
            finish();

            Log.d(TAG, "数据已准备并跳转到查料表");
        } catch (Exception e) {
            Log.e(TAG, "准备数据异常: " + e.getMessage());
            Toast.makeText(this, "数据处理失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // 创建统计单元格方法
    private TextView createStatCell(String columnId, int columnIndex) {
        TextView statView = new TextView(this);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            adaptiveColumnWidths[columnIndex],
            dpToPx(40)
        );

        statView.setLayoutParams(params);
        statView.setGravity(Gravity.CENTER);
        statView.setTextSize(12);
        statView.setTypeface(statView.getTypeface(), android.graphics.Typeface.BOLD);
        statView.setBackgroundResource(R.drawable.cell_border);

        if (columnIndex == columnIds.length - 1) {
            statView.setText("重置");
            statView.setTextColor(0xFFFFFFFF);
            statView.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
            statView.setBackgroundResource(R.drawable.cell_border_carmine);
            statView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showResetConfirmationDialog();
                    }
                });
        } else if ("shed_number".equals(columnId)) {
            statView.setText("去查料");
            statView.setTextColor(0xFFFFFFFF);
            statView.setBackgroundResource(R.drawable.cell_border_blue);
            statView.setTextSize(12);
            statView.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
            statView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        prepareDataAndNavigate();
                    }
                });
        } else if ("seq".equals(columnId)) {
            int filledRows = getFilledRowCount();
            statView.setText(getString(R.string.seq_stat_format, filledRows, TOTAL_ROWS));
            statView.setTextColor(0xFF0000FF);
            seqStatTextView = statView;
        } else {
            statView.setText("0");
            statView.setTextColor(0xFF0000FF);
            footerCellMap.put(columnId, statView);
        }

        return statView;
    }

    // 更新统计行
    private void updateFooterRow() {
        try {
            double totalShedCount = 0;
            double totalFermented = 0;
            double totalPowder = 0;
            double totalFeed03 = 0;
            double totalFeed05 = 0;
            double totalFeed10 = 0;
            double totalWater = 0;
            double totalAverageFeed = 0;
            int validAverageCount = 0;

            for (FeedData data : dataList) {
                totalShedCount += data.getShedCount();
                totalFermented += data.getFermentedFeed();
                totalPowder += data.getPowderFeed();
                totalFeed03 += data.getFeed03();
                totalFeed05 += data.getFeed05();
                totalFeed10 += data.getFeed10();
                totalWater += data.getWater();

                // 使用正确的平均吃料计算方法
                double avgValue = data.getAverage(fermentedIncludedInAverage);
                if (avgValue > 0) {
                    totalAverageFeed += avgValue;
                    validAverageCount++;
                }
            }

            updateFooterCell("shed_count", totalShedCount);
            updateFooterCell("fermented", totalFermented);
            updateFooterCell("powder", totalPowder);
            updateFooterCell("feed03", totalFeed03);
            updateFooterCell("feed05", totalFeed05);
            updateFooterCell("feed10", totalFeed10);
            updateFooterCell("water", totalWater);

            double avgAverageFeed = validAverageCount > 0 ? totalAverageFeed / validAverageCount : 0;
            updateFooterCell("average_feed", avgAverageFeed);

            updateSeqStatView();
            checkShedCountLimitAndUpdateInput();

        } catch (Exception e) {
            Log.e(TAG, "更新统计行异常: " + e.getMessage());
        }
    }

    // 平板适配核心方法
    private void initTabletAdaptiveSettings() {
        try {
            isTabletMode = isTabletDevice(this);

            adaptiveColumnWidths = new int[columnWidths.length];

            if (isTabletMode) {
                calculateAdaptiveColumnWidths();
            } else {
                for (int i = 0; i < columnWidths.length; i++) {
                    adaptiveColumnWidths[i] = dpToPx(columnWidths[i]);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "初始化平板自适应设置异常: " + e.getMessage());
            isTabletMode = false;
            adaptiveColumnWidths = new int[columnWidths.length];
            for (int i = 0; i < columnWidths.length; i++) {
                adaptiveColumnWidths[i] = dpToPx(columnWidths[i]);
            }
        }
    }

    private void calculateAdaptiveColumnWidths() {
        try {
            int screenWidth = getScreenWidth();

            // 如果 columnVisibility 为空，先初始化
            if (columnVisibility.isEmpty()) {
                for (int i = 0; i < columnIds.length; i++) {
                    columnVisibility.put(columnIds[i], true);
                }
            }

            int visibleColumnCount = 0;
            for (int i = 0; i < columnIds.length; i++) {
                String columnId = columnIds[i];
                Boolean visible = columnVisibility.get(columnId);
                if (visible != null && visible) {
                    visibleColumnCount++;
                }
            }

            if (visibleColumnCount == 0) {
                visibleColumnCount = 1;
            }

            int baseColumnWidth = screenWidth / visibleColumnCount;

            for (int i = 0; i < columnIds.length; i++) {
                String columnId = columnIds[i];
                Boolean visible = columnVisibility.get(columnId);

                if (visible != null && visible) {
                    int minWidth = dpToPx(50);
                    int maxWidth = dpToPx(120);
                    int adjustedWidth = baseColumnWidth;

                    if ("shed_number".equals(columnId) || "weighed_feed".equals(columnId)) {
                        adjustedWidth = (int)(baseColumnWidth * 1.2);
                    }

                    if ("seq".equals(columnId) || "average_feed".equals(columnId)) {
                        adjustedWidth = (int)(baseColumnWidth * 0.8);
                    }

                    adjustedWidth = Math.max(minWidth, Math.min(adjustedWidth, maxWidth));
                    adaptiveColumnWidths[i] = adjustedWidth;
                } else {
                    adaptiveColumnWidths[i] = 0;
                }
            }

            adjustTotalWidth(screenWidth);

        } catch (Exception e) {
            Log.e(TAG, "计算自适应列宽异常: " + e.getMessage());
            for (int i = 0; i < columnWidths.length; i++) {
                adaptiveColumnWidths[i] = dpToPx(columnWidths[i]);
            }
        }
    }

    private void adjustTotalWidth(int targetWidth) {
        try {
            int currentTotal = 0;
            for (int width : adaptiveColumnWidths) {
                currentTotal += width;
            }

            if (currentTotal <= 0) return;

            if (Math.abs(currentTotal - targetWidth) > 50) {
                float scale = (float) targetWidth / currentTotal;

                int adjustedTotal = 0;
                for (int i = 0; i < adaptiveColumnWidths.length; i++) {
                    adaptiveColumnWidths[i] = (int)(adaptiveColumnWidths[i] * scale);
                    adjustedTotal += adaptiveColumnWidths[i];
                }

                Log.d(TAG, "列宽调整: 缩放因子=" + scale + ", 调整后总宽=" + adjustedTotal);
            }

        } catch (Exception e) {
            Log.e(TAG, "调整总宽度异常: " + e.getMessage());
        }
    }

    private int getScreenWidth() {
        try {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            return displayMetrics.widthPixels;
        } catch (Exception e) {
            Log.e(TAG, "获取屏幕宽度异常: " + e.getMessage());
            return 1080;
        }
    }

    // 刷新所有单元格显示
    private void refreshAllCells() {
        try {
            Log.d(TAG, "开始刷新所有单元格显示");

            for (int i = 0; i < dataList.size(); i++) {
                refreshRowCells(i);
            }

            updateFooterRow();

            Log.d(TAG, "所有单元格刷新完成");
        } catch (Exception e) {
            Log.e(TAG, "刷新所有单元格异常: " + e.getMessage(), e);
        }
    }

    // 刷新指定行的所有单元格
    private void refreshRowCells(int rowIndex) {
        try {
            FeedData data = dataList.get(rowIndex);

            for (int i = 0; i < columnIds.length; i++) {
                String columnId = columnIds[i];
                View cellView = cellViewMap.get(rowIndex + "_" + columnId);

                if (cellView != null) {
                    if (cellView instanceof EditText) {
                        EditText editText = (EditText) cellView;
                        String currentValue = editText.getText().toString().trim();
                        String newValue = getCellInitialValue(data, columnId);

                        if (!currentValue.equals(newValue)) {
                            TextWatcher[] watchers = getTextWatchers(editText);
                            for (TextWatcher watcher : watchers) {
                                editText.removeTextChangedListener(watcher);
                            }

                            editText.setText(newValue);

                            for (TextWatcher watcher : watchers) {
                                editText.addTextChangedListener(watcher);
                            }
                        }

                        boolean isEnabled = isCellEnabled(columnId, data, rowIndex);
                        editText.setEnabled(isEnabled);

                        if ("shed_count".equals(columnId)) {
                            if (data.getShedCount() > 0 && isShedCountExceeded) {
                                editText.setBackground(warningShedCountBg);
                            } else {
                                editText.setBackground(normalShedCountBg);
                            }
                        } else {
                            if (isEnabled) {
                                editText.setBackgroundResource(R.drawable.cell_border);
                            } else {
                                editText.setBackgroundResource(R.drawable.cell_border_disabled);
                            }
                        }

                    } else if (cellView instanceof TextView) {
                        // TextView处理逻辑
                    }
                }
            }

            updateRowInputEnabled(rowIndex);

        } catch (Exception e) {
            Log.e(TAG, "刷新行单元格异常，行索引: " + rowIndex + ", 错误: " + e.getMessage());
        }
    }

    // 获取EditText的TextWatcher列表
    private TextWatcher[] getTextWatchers(EditText editText) {
        try {
            Object[] listeners = (Object[]) editText.getClass()
                .getMethod("getListeners", Class.forName("android.text.TextWatcher"))
                .invoke(editText, (Object) null);

            if (listeners != null && listeners.length > 0) {
                TextWatcher[] watchers = new TextWatcher[listeners.length];
                for (int i = 0; i < listeners.length; i++) {
                    watchers[i] = (TextWatcher) listeners[i];
                }
                return watchers;
            }
        } catch (Exception e) {
            Log.e(TAG, "获取TextWatcher异常: " + e.getMessage());
        }
        return new TextWatcher[0];
    }

    private void initViews() {
        try {
            dataContent = (LinearLayout) findViewById(R.id.data_content);
            headerScrollContent = (LinearLayout) findViewById(R.id.header_scroll_content);
            footerScrollContent = (LinearLayout) findViewById(R.id.footer_scroll_content);
            fixedHeaderContainer = (LinearLayout) findViewById(R.id.fixed_header_container);
            fixedFooterContainer = (LinearLayout) findViewById(R.id.fixed_footer_container);
            headerScrollView = (HorizontalScrollView) findViewById(R.id.header_scroll_view);
            footerScrollView = (HorizontalScrollView) findViewById(R.id.footer_scroll_view);
            verticalScrollView = (ScrollView) findViewById(R.id.vertical_scroll_view);
            
            // 禁用橡皮筋效果
            if (headerScrollView != null) {
                headerScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            }
            if (footerScrollView != null) {
                footerScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            }
        } catch (Exception e) {
            Log.e(TAG, "initViews异常: " + e.getMessage());
        }
    }

    private void initData() {
        try {
            dataList = new ArrayList<FeedData>();
            rowScrollViews = new ArrayList<HorizontalScrollView>();
            fixedDataRows = new ArrayList<LinearLayout>();

            for (int i = 0; i < TOTAL_ROWS; i++) {
                dataList.add(new FeedData());
            }
        } catch (Exception e) {
            Log.e(TAG, "initData异常: " + e.getMessage());
        }
    }

    private void initColumnVisibility() {
        try {
            columnVisibility.put("seq", true);
            for (int i = 1; i < columnIds.length; i++) {
                columnVisibility.put(columnIds[i], true);
            }

            Log.d(TAG, "列可见性初始化完成");
        } catch (Exception e) {
            Log.e(TAG, "initColumnVisibility异常: " + e.getMessage());
        }
    }

    private void initColumnFixedState() {
        try {
            columnFixed.put("seq", true);
            for (int i = 1; i < columnIds.length; i++) {
                columnFixed.put(columnIds[i], false);
            }

            Log.d(TAG, "列固定状态初始化完成");
        } catch (Exception e) {
            Log.e(TAG, "initColumnFixedState异常: " + e.getMessage());
        }
    }

    private void loadColumnVisibility() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            for (int i = 1; i < columnIds.length; i++) {
                String columnId = columnIds[i];
                boolean visible = prefs.getBoolean(KEY_COLUMN_VISIBILITY + columnId, true);
                columnVisibility.put(columnId, visible);
            }
            Log.d(TAG, "列可见性加载完成");
        } catch (Exception e) {
            Log.e(TAG, "加载列可见性异常: " + e.getMessage());
        }
    }

    private void loadColumnFixedState() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            for (int i = 1; i < columnIds.length; i++) {
                String columnId = columnIds[i];
                boolean fixed = prefs.getBoolean(KEY_FIXED_COLUMN + columnId, false);
                columnFixed.put(columnId, fixed);
            }
            Log.d(TAG, "列固定状态加载完成");
        } catch (Exception e) {
            Log.e(TAG, "加载列固定状态异常: " + e.getMessage());
        }
    }

    private void saveColumnVisibility() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            for (int i = 1; i < columnIds.length; i++) {
                String columnId = columnIds[i];
                editor.putBoolean(KEY_COLUMN_VISIBILITY + columnId, columnVisibility.get(columnId));
            }
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "保存列可见性异常: " + e.getMessage());
        }
    }

    private void saveColumnFixedState() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            for (int i = 1; i < columnIds.length; i++) {
                String columnId = columnIds[i];
                editor.putBoolean(KEY_FIXED_COLUMN + columnId, columnFixed.get(columnId));
            }
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "保存列固定状态异常: " + e.getMessage());
        }
    }

    // 保存全局水百分比
    private void saveGlobalWaterPercentage() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(KEY_GLOBAL_WATER, globalWaterPercentage);
            editor.apply();
            Log.d(TAG, "保存全局水百分比: " + globalWaterPercentage);
        } catch (Exception e) {
            Log.e(TAG, "保存全局水百分比异常: " + e.getMessage());
        }
    }

    // 加载全局水百分比
    private void loadGlobalWaterPercentage() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            globalWaterPercentage = prefs.getInt(KEY_GLOBAL_WATER, 20);
            Log.d(TAG, "加载全局水百分比: " + globalWaterPercentage);
        } catch (Exception e) {
            Log.e(TAG, "加载全局水百分比异常: " + e.getMessage());
            globalWaterPercentage = 20;
        }
    }

    // 保存发酵料参与水分计算设置
    private void saveFermentedIncludedInWater() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_FERMENTED_INCLUDED_IN_WATER, fermentedIncludedInWater);
            editor.apply();
            Log.d(TAG, "保存发酵料参与水分计算设置: " + fermentedIncludedInWater);
        } catch (Exception e) {
            Log.e(TAG, "保存发酵料参与水分计算设置异常: " + e.getMessage());
        }
    }

    // 加载发酵料参与水分计算设置
    private void loadFermentedIncludedInWater() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            fermentedIncludedInWater = prefs.getBoolean(KEY_FERMENTED_INCLUDED_IN_WATER, false);
            Log.d(TAG, "加载发酵料参与水分计算设置: " + fermentedIncludedInWater);
        } catch (Exception e) {
            Log.e(TAG, "加载发酵料参与水分计算设置异常: " + e.getMessage());
            fermentedIncludedInWater = false;
        }
    }

    // 保存发酵料参与平均吃料计算设置
    private void saveFermentedIncludedInAverage() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_FERMENTED_INCLUDED_IN_AVERAGE, fermentedIncludedInAverage);
            editor.apply();
            Log.d(TAG, "保存发酵料参与平均吃料计算设置: " + fermentedIncludedInAverage);
        } catch (Exception e) {
            Log.e(TAG, "保存发酵料参与平均吃料计算设置异常: " + e.getMessage());
        }
    }

    // 加载发酵料参与平均吃料计算设置
    private void loadFermentedIncludedInAverage() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            fermentedIncludedInAverage = prefs.getBoolean(KEY_FERMENTED_INCLUDED_IN_AVERAGE, false);
            Log.d(TAG, "加载发酵料参与平均吃料计算设置: " + fermentedIncludedInAverage);
        } catch (Exception e) {
            Log.e(TAG, "加载发酵料参与平均吃料计算设置异常: " + e.getMessage());
            fermentedIncludedInAverage = false;
        }
    }

    // 保存所有数据
    private void saveAllData() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            JSONArray jsonArray = new JSONArray();
            for (int i = 0; i < dataList.size(); i++) {
                FeedData data = dataList.get(i);
                jsonArray.put(data.toJson());
            }

            String dataJson = jsonArray.toString();
            editor.putString(KEY_SAVED_DATA, dataJson);
            editor.apply();

            Log.d(TAG, "数据保存完成，共保存" + dataList.size() + "条记录");
        } catch (Exception e) {
            Log.e(TAG, "保存所有数据异常: " + e.getMessage());
        }
    }

    private void rebuildCompleteTable() {
        try {
            if (isTabletMode) {
                calculateAdaptiveColumnWidths();
            }

            clearAllScrollListeners();
            clearAllCellListeners();
            cleanupAllViews();
            resetAllStates();
            buildTableFromScratch();
            
            // 重建后重新加载监听器：所有行只添加棚号/棚数监听器
            // 如果某行已有其他列数据，会自动加载其他列监听器
            for (int i = 0; i < dataList.size(); i++) {
                FeedData data = dataList.get(i);
                addShedListenersToRow(i);
                
                // 如果该行已有其他列数据，需要加载其他列监听器
                if (hasOtherColumnData(data)) {
                    loadOtherColumnListenersIfNeeded(i);
                }
            }
            
            updateFooterRow();

        } catch (Exception e) {
            Log.e(TAG, "重建完整表格异常: " + e.getMessage(), e);
            Toast.makeText(this, "表格重建失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearAllScrollListeners() {
        try {
            for (ViewTreeObserver.OnScrollChangedListener listener : scrollListeners) {
                if (headerScrollView != null) {
                    headerScrollView.getViewTreeObserver().removeOnScrollChangedListener(listener);
                }
                if (footerScrollView != null) {
                    footerScrollView.getViewTreeObserver().removeOnScrollChangedListener(listener);
                }
            }
            scrollListeners.clear();

            for (HorizontalScrollView scrollView : rowScrollViews) {
                if (scrollView != null) {
                    ViewTreeObserver observer = scrollView.getViewTreeObserver();
                    observer.removeOnScrollChangedListener(null);
                }
            }

            Log.d(TAG, "所有滚动监听器已清理");
        } catch (Exception e) {
            Log.e(TAG, "清理滚动监听器异常: " + e.getMessage());
        }
    }

    private void clearAllCellListeners() {
        try {
            cellViewMap.clear();
            rowLayoutMap.clear();
            footerCellMap.clear();

            Log.d(TAG, "所有单元格监听器已清理");
        } catch (Exception e) {
            Log.e(TAG, "清理单元格监听器异常: " + e.getMessage());
        }
    }

    private void cleanupAllViews() {
        try {
            if (headerScrollContent != null) headerScrollContent.removeAllViews();
            if (footerScrollContent != null) footerScrollContent.removeAllViews();
            if (fixedHeaderContainer != null) fixedHeaderContainer.removeAllViews();
            if (fixedFooterContainer != null) fixedFooterContainer.removeAllViews();
            if (dataContent != null) dataContent.removeAllViews();

            rowScrollViews.clear();
            fixedDataRows.clear();

            Log.d(TAG, "所有视图已清理");
        } catch (Exception e) {
            Log.e(TAG, "清理视图异常: " + e.getMessage());
        }
    }

    private void resetAllStates() {
        try {
            currentScrollX = 0;
            isSyncing = false;
            isHorizontalScroll = false;
            lastX = 0;
            lastY = 0;
            isShedCountExceeded = false;
            listenersAddedSet.clear();
        } catch (Exception e) {
            Log.e(TAG, "重置状态异常: " + e.getMessage());
        }
    }

    private void buildTableFromScratch() {
        try {
            int scrollWidth = calculateScrollWidth();
            Log.d(TAG, "滚动宽度: " + scrollWidth + "px");

            buildHeader(scrollWidth);
            buildFooter(scrollWidth);
            buildDataRows(scrollWidth);
            setupScrollSync();

        } catch (Exception e) {
            Log.e(TAG, "从零构建表格异常: " + e.getMessage());
        }
    }

    private int calculateScrollWidth() {
        int width = 0;
        for (int i = 0; i < columnIds.length; i++) {
            String columnId = columnIds[i];
            if (columnVisibility.get(columnId) && !columnFixed.get(columnId)) {
                width += adaptiveColumnWidths[i];
            }
        }
        return width;
    }

    private void buildHeader(int scrollWidth) {
        try {
            buildFixedHeader();
            buildScrollHeader();

            if (headerScrollContent != null && scrollWidth > 0) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) headerScrollContent.getLayoutParams();
                params.width = scrollWidth;
                headerScrollContent.setLayoutParams(params);
                headerScrollContent.requestLayout();
            }

        } catch (Exception e) {
            Log.e(TAG, "构建表头异常: " + e.getMessage());
        }
    }

    private void buildFixedHeader() {
        TextView seqHeader = createHeaderCell("seq", 0);
        fixedHeaderContainer.addView(seqHeader);

        for (int i = 1; i < columnIds.length; i++) {
            final int columnIndex = i;
            String columnId = columnIds[i];
            boolean visible = columnVisibility.get(columnId);
            boolean fixed = columnFixed.get(columnId);

            if (visible && fixed) {
                TextView headerView = createHeaderCell(columnId, columnIndex);
                headerView.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            String columnId = columnIds[columnIndex];
                            showColumnOperationMenu(columnIndex, columnId, v);
                            return true;
                        }
                    });
                fixedHeaderContainer.addView(headerView);
            }
        }
    }

    private void buildScrollHeader() {
        for (int i = 1; i < columnIds.length; i++) {
            final int columnIndex = i;
            String columnId = columnIds[i];
            boolean visible = columnVisibility.get(columnId);
            boolean fixed = columnFixed.get(columnId);

            if (visible && !fixed) {
                TextView headerView = createHeaderCell(columnId, columnIndex);
                headerView.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            String columnId = columnIds[columnIndex];
                            showColumnOperationMenu(columnIndex, columnId, v);
                            return true;
                        }
                    });
                headerScrollContent.addView(headerView);
            }
        }
    }

    // 创建表头单元格
    private TextView createHeaderCell(String columnId, int columnIndex) {
        TextView headerView = new TextView(this);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            adaptiveColumnWidths[columnIndex],
            dpToPx(40)
        );

        headerView.setLayoutParams(params);
        headerView.setGravity(Gravity.CENTER);

        if ("average_feed".equals(columnId)) {
            headerView.setText("平均"); 
            headerView.setGravity(Gravity.CENTER);
            headerView.setSingleLine(false);
            headerView.setLines(2);
            headerView.setMaxLines(2);
        } else if ("water".equals(columnId)) {
            headerView.setText(getString(R.string.water_percentage_header, globalWaterPercentage));
            headerView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showWaterPercentageDialog();
                    }
                });
        } else {
            headerView.setText(columnNames[columnIndex]);
        }

        // 为发酵料表头设置背景和点击事件
        if ("fermented".equals(columnId)) {
            // 根据当前设置初始化背景
            updateFermentedHeaderCellBackground(headerView);

            // 设置点击监听器，显示四个选项的对话框
            headerView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showFermentedCalculationDialog(); // 改为新的四个选项对话框
                    }
                });
        } else {
            headerView.setBackgroundResource(R.drawable.cell_border);
        }

        if (isTabletMode) {
            headerView.setTextSize(16);
            headerView.setTypeface(headerView.getTypeface(), android.graphics.Typeface.BOLD);
        } else {
            headerView.setTextSize(12);
            headerView.setTypeface(headerView.getTypeface(), android.graphics.Typeface.BOLD);
        }

        return headerView;
    }

    @android.annotation.SuppressLint("InflateParams")
    private void showStyledConfirmDialog70(String title, String message, String[] buttonTexts, final int[] buttonColors, final DialogInterface.OnClickListener[] listeners) {
        Dialog dialog = new Dialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_simple_confirm, null);
        dialog.setContentView(dialogView);

        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        tvTitle.setText(title);

        TextView tvMessage = dialogView.findViewById(R.id.tv_message);
        tvMessage.setText(message);

        LinearLayout buttonLayout = dialogView.findViewById(R.id.layout_buttons);
        for (int i = 0; i < buttonTexts.length; i++) {
            Button btn = new Button(this);
            btn.setText(buttonTexts[i]);
            btn.setTextSize(15);
            btn.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
            btn.setTextColor(buttonColors != null && i < buttonColors.length ? buttonColors[i] : 0xFF333333);
            btn.setBackgroundResource(android.R.color.transparent);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i < buttonTexts.length - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));
                divider.setBackgroundColor(0xFFE0E0E0);
                buttonLayout.addView(divider);
            }
            final int index = i;
            btn.setOnClickListener(v -> {
                if (listeners != null && index < listeners.length && listeners[index] != null) {
                    listeners[index].onClick(dialog, index);
                }
                dialog.dismiss();
            });
            buttonLayout.addView(btn, lp);
        }

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        dialog.getWindow().setLayout((int)(screenWidth * 0.7), ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
    }

    @android.annotation.SuppressLint("InflateParams")
    private void showStyledListDialog(String title, String[] items, final DialogInterface.OnClickListener listener) {
        Dialog dialog = new Dialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_simple_list, null);
        dialog.setContentView(dialogView);

        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        tvTitle.setText(title);

        ListView listView = dialogView.findViewById(R.id.list_view);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, which, id) -> {
            if (listener != null) {
                listener.onClick(dialog, which);
            }
            dialog.dismiss();
        });

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        dialog.getWindow().setLayout(screenWidth / 2, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
    }

    // 显示发酵料计算设置的对话框（四个选项）
    private void showFermentedCalculationDialog() {
        try {
            String[] options = new String[4];

            if (fermentedIncludedInWater) {
                options[0] = "● 参与算水";
                options[1] = "○ 不参与算水";
            } else {
                options[0] = "○ 参与算水";
                options[1] = "● 不参与算水";
            }

            if (fermentedIncludedInAverage) {
                options[2] = "● 参与算料";
                options[3] = "○ 不参与算料";
            } else {
                options[2] = "○ 参与算料";
                options[3] = "● 不参与算料";
            }

            showStyledListDialog("发酵料计算设置", options, (dialog, which) -> {
                boolean needRefresh = false;

                switch (which) {
                    case 0:
                        if (!fermentedIncludedInWater) {
                            fermentedIncludedInWater = true;
                            needRefresh = true;
                        }
                        break;
                    case 1:
                        if (fermentedIncludedInWater) {
                            fermentedIncludedInWater = false;
                            needRefresh = true;
                        }
                        break;
                    case 2:
                        if (!fermentedIncludedInAverage) {
                            fermentedIncludedInAverage = true;
                            needRefresh = true;
                        }
                        break;
                    case 3:
                        if (fermentedIncludedInAverage) {
                            fermentedIncludedInAverage = false;
                            needRefresh = true;
                        }
                        break;
                }

                if (needRefresh) {
                    saveFermentedIncludedInWater();
                    saveFermentedIncludedInAverage();
                    updateFermentedHeaderBackground();
                    updateAllCalculations();
                    String status = "当前设置：";
                    status += fermentedIncludedInWater ? "参与算水" : "不参与算水";
                    status += "，";
                    status += fermentedIncludedInAverage ? "参与算料" : "不参与算料";
                    Toast.makeText(MixCalcActivity.this, status, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MixCalcActivity.this, "设置未改变", Toast.LENGTH_SHORT).show();
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "显示发酵料计算设置对话框异常: " + e.getMessage());
            Toast.makeText(this, "设置失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 更新发酵料表头背景
    private void updateFermentedHeaderBackground() {
        updateFermentedHeaderInContainer(fixedHeaderContainer);
        updateFermentedHeaderInContainer(headerScrollContent);
    }

    private void updateFermentedHeaderInContainer(LinearLayout container) {
        if (container == null) return;

        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                String text = tv.getText().toString();

                if (text.contains("发酵")) {
                    updateFermentedHeaderCellBackground(tv);
                }
            }
        }
    }

    // 为单个发酵料表头单元格设置背景
    private void updateFermentedHeaderCellBackground(TextView headerView) {
        if (headerView == null) return;

        // 根据两个标志的组合选择不同的样式
        if (fermentedIncludedInAverage) {
            // 参与算料 - 使用褐色边框样式
            if (fermentedIncludedInWater) {
                // 参与算水 - 褐色边框 + 绿色背景
                headerView.setBackgroundResource(R.drawable.cell_border_brown_green);
            } else {
                // 不参与算水 - 褐色边框 + 灰色背景
                headerView.setBackgroundResource(R.drawable.cell_border_brown_gray);
            }
        } else {
            // 不参与算料 - 使用原有的黑色边框样式
            if (fermentedIncludedInWater) {
                // 参与算水 - 黑色边框 + 绿色背景
                headerView.setBackgroundResource(R.drawable.cell_border_green);
            } else {
                // 不参与算水 - 黑色边框 + 灰色背景
                headerView.setBackgroundResource(R.drawable.cell_border_fermented_default);
            }
        }
    }

    private void buildFooter(int scrollWidth) {
        try {
            buildFixedFooter();
            buildScrollFooter();

            if (footerScrollContent != null && scrollWidth > 0) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) footerScrollContent.getLayoutParams();
                params.width = scrollWidth;
                footerScrollContent.setLayoutParams(params);
                footerScrollContent.requestLayout();
            }

        } catch (Exception e) {
            Log.e(TAG, "构建统计行异常: " + e.getMessage());
        }
    }

    private void buildFixedFooter() {
        TextView seqStat = createStatCell("seq", 0);
        fixedFooterContainer.addView(seqStat);

        for (int i = 1; i < columnIds.length; i++) {
            final int columnIndex = i;
            String columnId = columnIds[i];
            boolean visible = columnVisibility.get(columnId);
            boolean fixed = columnFixed.get(columnId);

            if (visible && fixed) {
                TextView statView = createStatCell(columnId, columnIndex);
                fixedFooterContainer.addView(statView);
            }
        }
    }

    private void buildScrollFooter() {
        for (int i = 1; i < columnIds.length; i++) {
            final int columnIndex = i;
            String columnId = columnIds[i];
            boolean visible = columnVisibility.get(columnId);
            boolean fixed = columnFixed.get(columnId);

            if (visible && !fixed) {
                TextView statView = createStatCell(columnId, columnIndex);
                footerScrollContent.addView(statView);
            }
        }
    }

    // 在统计行更新单元格时考虑超限状态
    private void updateFooterCell(String columnId, double value) {
        TextView statView = footerCellMap.get(columnId);
        if (statView != null) {
            if (value == 0) {
                statView.setText("0");
            } else {
                if (value == (int) value) {
                    statView.setText(String.valueOf((int) value));
                } else {
                    statView.setText(String.format(java.util.Locale.ROOT, "%.2f", value));
                }
            }
            statView.setTextColor(0xFF0000FF);
        }
    }

    // 计算有数据的行数
    private int getFilledRowCount() {
        int count = 0;
        for (FeedData data : dataList) {
            if (data.getShedCount() > 0) {
                count++;
            }
        }
        return count;
    }

    // 显示下载对话框
    private void showDownloadDialog() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean neverShow = prefs.getBoolean(KEY_NEVER_SHOW_DOWNLOAD_DIALOG, false);

            if (neverShow) {
                return;
            }

            final String downloadUrl = "https://github.com/DTxiaweibing/-2/releases/download/%E5%85%BB%E8%99%BE%E5%8A%A9%E6%89%8B1.63.8.61/1.63.8.61.apk";
            String message = "查料功能需要配合'养虾助手'应用使用。\n\n该应用提供以下功能：\n• 实时查看料台\n• 养殖数据记录\n• 智能分析报告\n\n是否立即下载安装？";

            showStyledConfirmDialog70("需要安装养虾助手", message,
                new String[]{"以后再说", "不再提示", "立即下载安装"},
                new int[]{0xFF666666, 0xFF999999, 0xFF2196F3},
                new DialogInterface.OnClickListener[]{
                    null,
                    (dialog, which) -> {
                        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        sp.edit().putBoolean(KEY_NEVER_SHOW_DOWNLOAD_DIALOG, true).apply();
                        Toast.makeText(MixCalcActivity.this, "不再提示下载", Toast.LENGTH_SHORT).show();
                    },
                    (dialog, which) -> {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                        try {
                            startActivity(browserIntent);
                        } catch (Exception e) {
                            showStyledConfirmDialog70("无法打开下载链接",
                                "请手动访问以下链接进行下载：\n\n" + downloadUrl,
                                new String[]{"取消", "复制链接"},
                                new int[]{0xFF666666, 0xFF2196F3},
                                new DialogInterface.OnClickListener[]{
                                    null,
                                    (d, w) -> {
                                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                        android.content.ClipData clip = android.content.ClipData.newPlainText("下载链接", downloadUrl);
                                        clipboard.setPrimaryClip(clip);
                                        Toast.makeText(MixCalcActivity.this, "链接已复制到剪贴板", Toast.LENGTH_SHORT).show();
                                    }
                                });
                        }
                    }
                });

        } catch (Exception e) {
            Log.e(TAG, "显示下载对话框异常: " + e.getMessage());
            Toast.makeText(this, "无法显示下载提示", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetAllData() {
        try {
            for (FeedData data : dataList) {
                data.setShedNumber("");
                data.setShedCountDirectly(0, false);
                data.setFermentedFeed(0);
                data.setPowderFeed(0);
                data.setFeed03(0);
                data.setFeed05(0);
                data.setFeed10(0);
                data.setWater(0);
                data.setWeighedFeed(0);
                data.setAverage(0);
                data.setShedCountManuallyModified(false);
            }

            rebuildCompleteTable();
            saveAllData();

            Toast.makeText(this, "所有数据已重置", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "重置数据异常: " + e.getMessage());
        }
    }

    private void buildDataRows(int scrollWidth) {
        try {
            for (int i = 0; i < dataList.size(); i++) {
                buildDataRow(i, scrollWidth);
            }
            Log.d(TAG, "构建了" + dataList.size() + "行数据");
        } catch (Exception e) {
            Log.e(TAG, "构建数据行异常: " + e.getMessage());
        }
    }

    private void buildDataRow(final int rowIndex, int scrollWidth) {
        LinearLayout rowLayout = new LinearLayout(this);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                                      LinearLayout.LayoutParams.MATCH_PARENT, 
                                      dpToPx(40)
                                  ));
        rowLayoutMap.put(rowIndex, rowLayout);

        LinearLayout fixedLayout = buildFixedColumns(rowIndex);
        rowLayout.addView(fixedLayout);
        fixedDataRows.add(fixedLayout);

        HorizontalScrollView scrollView = buildScrollColumns(rowIndex, scrollWidth);
        rowLayout.addView(scrollView);
        rowScrollViews.add(scrollView);

        dataContent.addView(rowLayout);
    }

    private LinearLayout buildFixedColumns(final int rowIndex) {
        LinearLayout fixedLayout = new LinearLayout(this);
        fixedLayout.setOrientation(LinearLayout.HORIZONTAL);

        int fixedWidth = 0;

        View seqView = createDataCell("seq", rowIndex, 0);
        fixedLayout.addView(seqView);
        fixedWidth += adaptiveColumnWidths[0];

        for (int i = 1; i < columnIds.length; i++) {
            final int columnIndex = i;
            String columnId = columnIds[i];
            boolean visible = columnVisibility.get(columnId);
            boolean fixed = columnFixed.get(columnId);

            if (visible && fixed) {
                View cellView = createDataCell(columnId, rowIndex, columnIndex);
                fixedLayout.addView(cellView);
                fixedWidth += adaptiveColumnWidths[columnIndex];
            }
        }

        fixedLayout.setLayoutParams(new LinearLayout.LayoutParams(
                                        fixedWidth,
                                        dpToPx(40)
                                    ));

        return fixedLayout;
    }

    @SuppressLint("ClickableViewAccessibility")
    private HorizontalScrollView buildScrollColumns(final int rowIndex, int scrollWidth) {
        HorizontalScrollView scrollView = new HorizontalScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                                       0,
                                       dpToPx(40),
                                       1.0f
                                   ));
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        scrollView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            lastX = event.getX();
                            lastY = event.getY();
                            isHorizontalScroll = false;
                            break;

                        case MotionEvent.ACTION_MOVE:
                            float deltaX = Math.abs(event.getX() - lastX);
                            float deltaY = Math.abs(event.getY() - lastY);

                            if (deltaX > deltaY && deltaX > 5) {
                                isHorizontalScroll = true;
                                if (verticalScrollView != null) {
                                    verticalScrollView.requestDisallowInterceptTouchEvent(true);
                                }
                            }
                            break;

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            isHorizontalScroll = false;
                            if (verticalScrollView != null) {
                                verticalScrollView.requestDisallowInterceptTouchEvent(false);
                            }
                            break;
                    }
                    v.performClick();
                    return false;
                }
            });

        LinearLayout scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.HORIZONTAL);
        scrollContent.setLayoutParams(new LinearLayout.LayoutParams(
                                          scrollWidth,
                                          dpToPx(40)
                                      ));

        for (int i = 0; i < columnIds.length; i++) {
            final int columnIndex = i;
            String columnId = columnIds[i];
            boolean visible = columnVisibility.get(columnId);
            boolean fixed = columnFixed.get(columnId);

            if (visible && !fixed) {
                View cellView = createDataCell(columnId, rowIndex, columnIndex);
                scrollContent.addView(cellView);
            }
        }

        scrollView.addView(scrollContent);
        return scrollView;
    }

    private View createDataCell(final String columnId, final int rowIndex, final int columnIndex) {
        boolean isEditable = isEditableColumn(columnId);

        if (isEditable) {
            final EditText cellView = new EditText(this);

            cellView.setLayoutParams(new LinearLayout.LayoutParams(
                                         adaptiveColumnWidths[columnIndex],
                                         dpToPx(40)
                                     ));

            cellView.setGravity(Gravity.CENTER);
            cellView.setTextSize(12);
            cellView.setTypeface(cellView.getTypeface(), android.graphics.Typeface.BOLD);
            cellView.setBackgroundResource(R.drawable.cell_border);
            cellView.setPadding(0, 0, 0, 0);
            cellView.setSingleLine(true);
            cellView.setTag(rowIndex + "_" + columnId);

            if ("shed_number".equals(columnId)) {
                cellView.setInputType(InputType.TYPE_CLASS_TEXT);
                cellView.setSingleLine(false);
                cellView.setMaxLines(2);
                cellView.setGravity(Gravity.CENTER);
            } else {
                cellView.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                cellView.setHint("");
            }

            cellView.setImeOptions(EditorInfo.IME_ACTION_NEXT);

            FeedData data = dataList.get(rowIndex);
            String initialValue = getCellInitialValue(data, columnId);
            cellView.setText(initialValue);

            boolean isEnabled = isCellEnabled(columnId, data, rowIndex);
            cellView.setEnabled(isEnabled);

            if (!isEnabled) {
                cellView.setBackgroundResource(R.drawable.cell_border_disabled);
            }

            cellViewMap.put(rowIndex + "_" + columnId, cellView);

            if ("shed_number".equals(columnId)) {
                cellView.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            String text = s.toString().trim();
                            final FeedData data = dataList.get(rowIndex);
                            data.setShedNumber(text);
                            data.updateAllCalculations(globalWaterPercentage, fermentedIncludedInWater, fermentedIncludedInAverage);
                            updateShedCountDisplay(rowIndex, data);
                            updateCalculatedCells(rowIndex);
                            updateRowInputEnabled(rowIndex);
                            updateFooterRow();
                            saveAllData();
                            checkShedCountLimitAndUpdateInput();
                        }
                    });
            } else if ("shed_count".equals(columnId)) {
                final int[] oldValue = {data.getShedCount()};

                cellView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                        @Override
                        public void onFocusChange(View v, boolean hasFocus) {
                            if (!hasFocus) {
                                String text = ((EditText)v).getText().toString().trim();
                                final FeedData data = dataList.get(rowIndex);

                                double newValue = 0;
                                if (!text.isEmpty()) {
                                    try {
                                        newValue = Double.parseDouble(text);
                                    } catch (NumberFormatException e) {
                                        newValue = 0;
                                        ((EditText)v).setText("");
                                    }
                                }

                                if (oldValue[0] != (int)newValue) {
                                    data.setShedCount((int)newValue);

                                    if (newValue == 0 || text.isEmpty()) {
                                        data.setShedCountManuallyModified(false);
                                    } else {
                                        data.setShedCountManuallyModified(true);
                                    }
                                }

                                oldValue[0] = (int)newValue;

                                data.updateAllCalculations(globalWaterPercentage, fermentedIncludedInWater, fermentedIncludedInAverage);
                                updateCalculatedCells(rowIndex);
                                updateRowInputEnabled(rowIndex);
                                updateFooterRow();
                                saveAllData();
                                checkShedCountLimitAndUpdateInput();
                            } else {
                                String text = ((EditText)v).getText().toString().trim();
                                if (!text.isEmpty()) {
                                    try {
                                        oldValue[0] = Integer.parseInt(text);
                                    } catch (NumberFormatException e) {
                                        oldValue[0] = 0;
                                    }
                                } else {
                                    oldValue[0] = 0;
                                }
                            }
                        }
                    });
            } else {
                cellView.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            String text = s.toString().trim();
                            final FeedData data = dataList.get(rowIndex);

                            double value = 0;
                            if (!text.isEmpty()) {
                                try {
                                    value = Double.parseDouble(text);
                                } catch (NumberFormatException e) {
                                    value = 0;
                                }
                            }

                            switch (columnId) {
                                case "fermented":
                                    data.setFermentedFeed(value);
                                    break;
                                case "powder":
                                    data.setPowderFeed(value);
                                    break;
                                case "feed03":
                                    data.setFeed03(value);
                                    break;
                                case "feed05":
                                    data.setFeed05(value);
                                    break;
                                case "feed10":
                                    data.setFeed10(value);
                                    break;
                            }

                            data.updateAllCalculations(globalWaterPercentage, fermentedIncludedInWater, fermentedIncludedInAverage);
                            updateCalculatedCells(rowIndex);
                            updateFooterRow();
                            saveAllData();
                            checkShedCountLimitAndUpdateInput();
                        }
                    });
            }

            return cellView;
        } else {
            TextView cellView = new TextView(this);

            cellView.setLayoutParams(new LinearLayout.LayoutParams(
                                         adaptiveColumnWidths[columnIndex],
                                         dpToPx(40)
                                     ));

            cellView.setGravity(Gravity.CENTER);
            cellView.setTextSize(12);
            cellView.setTypeface(cellView.getTypeface(), android.graphics.Typeface.BOLD);
            cellView.setBackgroundResource(R.drawable.cell_border);
            cellView.setPadding(0, 0, 0, 0);

            cellView.setTag(rowIndex + "_" + columnId);
            cellViewMap.put(rowIndex + "_" + columnId, cellView);

            FeedData data = dataList.get(rowIndex);

            if ("seq".equals(columnId)) {
                cellView.setText(String.valueOf(rowIndex + 1));
                cellView.setTextColor(0xFF666666);
            } else if ("shed_number".equals(columnId)) {
                String shedName = data.getShedNumber();
                if (shedName == null || shedName.isEmpty()) {
                    cellView.setText(getString(R.string.shed_number_format, rowIndex + 1));
                } else {
                    cellView.setText(shedName);
                    cellView.setSingleLine(false);
                    cellView.setMaxLines(2);
                    cellView.setIncludeFontPadding(false);
                    cellView.setLineSpacing(0, 1.0f);
                    cellView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
                    cellView.setPadding(0, 0, 0, 0);
                }
                cellView.setTextColor(Color.BLACK);
            } else {
                String displayValue = getCellDisplayValue(data, columnId);
                cellView.setText(displayValue);

                if ("average_feed".equals(columnId) || "water".equals(columnId) || "weighed_feed".equals(columnId)) {
                    cellView.setTextColor(0xFF0000FF);
                } else {
                    cellView.setTextColor(Color.BLACK);
                }
            }

            return cellView;
        }
    }

    // 移动到下一个可编辑单元格
    private void moveFocusToNextEditableCell(int currentRow, int currentColumnIndex) {
        try {
            String currentColumnId = columnIds[currentColumnIndex];

            for (int nextColIndex = currentColumnIndex + 1; nextColIndex < columnIds.length; nextColIndex++) {
                String nextColumnId = columnIds[nextColIndex];

                if (columnVisibility.get(nextColumnId) && 
                    isEditableColumn(nextColumnId)) {

                    FeedData data = dataList.get(currentRow);
                    if (isCellEnabled(nextColumnId, data, currentRow)) {
                        View nextCell = cellViewMap.get(currentRow + "_" + nextColumnId);
                        if (nextCell != null && nextCell instanceof EditText) {
                            nextCell.requestFocus();
                            ((EditText)nextCell).selectAll();
                            return;
                        }
                    }
                }
            }

            int nextRow = currentRow + 1;
            if (nextRow < dataList.size()) {
                for (int colIndex = 0; colIndex < columnIds.length; colIndex++) {
                    String columnId = columnIds[colIndex];

                    if (columnVisibility.get(columnId) && 
                        isEditableColumn(columnId)) {

                        FeedData data = dataList.get(nextRow);
                        if (isCellEnabled(columnId, data, nextRow)) {
                            View nextCell = cellViewMap.get(nextRow + "_" + columnId);
                            if (nextCell != null && nextCell instanceof EditText) {
                                nextCell.requestFocus();
                                ((EditText)nextCell).selectAll();
                                return;
                            }
                        }
                    }
                }
            }

            View currentView = cellViewMap.get(currentRow + "_" + currentColumnId);
            if (currentView != null && currentView.hasFocus()) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(currentView.getWindowToken(), 0);
            }

        } catch (Exception e) {
            Log.e(TAG, "移动焦点异常: " + e.getMessage());
        }
    }

    // 检查单元格是否应该启用
    private boolean isCellEnabled(String columnId, FeedData data, int rowIndex) {
        if ("shed_number".equals(columnId) || "shed_count".equals(columnId)) {
            return true;
        } else {
            return data.getShedCount() > 0;
        }
    }

    // 更新行的输入状态
    private void updateRowInputEnabled(int rowIndex) {
        FeedData data = dataList.get(rowIndex);

        String[] editableColumns = {"fermented", "powder", "feed03", "feed05", "feed10", "shed_number", "shed_count"};
        for (String columnId : editableColumns) {
            View view = cellViewMap.get(rowIndex + "_" + columnId);
            if (view instanceof EditText) {
                EditText editText = (EditText) view;
                boolean isEnabled = isCellEnabled(columnId, data, rowIndex);

                editText.setEnabled(isEnabled);

                if ("shed_count".equals(columnId)) {
                    if (data.getShedCount() > 0 && isShedCountExceeded) {
                        editText.setBackground(warningShedCountBg);
                    } else {
                        editText.setBackground(normalShedCountBg);
                    }
                } else {
                    if (isEnabled) {
                        editText.setBackgroundResource(R.drawable.cell_border);
                    } else {
                        editText.setBackgroundResource(R.drawable.cell_border_disabled);
                        if (!"shed_number".equals(columnId)) {
                            editText.setText("");

                            switch (columnId) {
                                case "fermented":
                                    data.setFermentedFeed(0);
                                    break;
                                case "powder":
                                    data.setPowderFeed(0);
                                    break;
                                case "feed03":
                                    data.setFeed03(0);
                                    break;
                                case "feed05":
                                    data.setFeed05(0);
                                    break;
                                case "feed10":
                                    data.setFeed10(0);
                                    break;
                            }
                        }
                    }
                }
            }
        }

        data.updateAllCalculations(globalWaterPercentage, fermentedIncludedInWater, fermentedIncludedInAverage);
        updateCalculatedCells(rowIndex);
        saveAllData();
    }

    private void updateShedCountDisplay(int rowIndex, FeedData data) {
        if (rowIndex < 0 || data == null) return;
        
        View shedCountView = cellViewMap.get(rowIndex + "_shed_count");
        if (shedCountView instanceof EditText) {
            if (!data.isShedCountManuallyModified()) {
                String displayValue = data.getShedCount() == 0 ? "" : String.valueOf(data.getShedCount());
                ((EditText)shedCountView).setText(displayValue);
            }
        }
        // 检查是否需要加载其他列监听器
        checkAndLoadOtherColumnListeners(rowIndex);
    }

    // 更新计算列
    private void updateCalculatedCells(int rowIndex) {
        try {
            FeedData data = dataList.get(rowIndex);

            // 更新平均吃料单元格
            View averageFeedView = cellViewMap.get(rowIndex + "_average_feed");
            if (averageFeedView instanceof TextView) {
                // 使用新的方法获取平均吃料值，考虑是否包含发酵料
                double averageValue = data.getAverage(fermentedIncludedInAverage);
                String displayValue = averageValue == 0 ? "" : formatNumber(averageValue);
                ((TextView)averageFeedView).setText(displayValue);
                ((TextView)averageFeedView).setTextColor(0xFF0000FF);
            }

            // 更新水单元格
            View waterView = cellViewMap.get(rowIndex + "_water");
            if (waterView instanceof TextView) {
                double waterValue = data.getWater();
                String displayValue = waterValue == 0 ? "" : formatNumber(waterValue);
                ((TextView)waterView).setText(displayValue);
                ((TextView)waterView).setTextColor(0xFF0000FF);
            }

            // 更新称料单元格
            View weighedFeedView = cellViewMap.get(rowIndex + "_weighed_feed");
            if (weighedFeedView instanceof TextView) {
                double weighedFeed = data.getWeighedFeed();
                int shedCount = data.getShedCount();

                String displayValue = "";
                if (shedCount > 0 && weighedFeed > 0) {
                    if (weighedFeed == (int) weighedFeed) {
                        displayValue = String.valueOf((int) weighedFeed) + "×" + shedCount;
                    } else {
                        displayValue = String.format(java.util.Locale.ROOT, "%.2f", weighedFeed) + "×" + shedCount;
                    }
                }
                ((TextView)weighedFeedView).setText(displayValue);
                ((TextView)weighedFeedView).setTextColor(0xFF0000FF);
            }

        } catch (Exception e) {
            Log.e(TAG, "更新计算单元格异常: " + e.getMessage());
        }
    }

    // 更新所有计算
    private void updateAllCalculations() {
        try {
            for (int i = 0; i < dataList.size(); i++) {
                FeedData data = dataList.get(i);
                // 重新计算所有行，传入两个标志
                data.updateAllCalculations(globalWaterPercentage, fermentedIncludedInWater, fermentedIncludedInAverage);
                updateCalculatedCells(i);
            }

            updateFooterRow();
            saveAllData();

        } catch (Exception e) {
            Log.e(TAG, "更新所有计算异常: " + e.getMessage());
        }
    }

    // 更新序号列统计行
    private void updateSeqStatView() {
        if (seqStatTextView != null) {
            int filledRows = getFilledRowCount();
            seqStatTextView.setText(getString(R.string.seq_stat_format, filledRows, TOTAL_ROWS));
        }
    }

    private String getCellInitialValue(FeedData data, String columnId) {
        switch (columnId) {
            case "shed_number":
                return data.getShedNumber();
            case "shed_count":
                return data.getShedCount() == 0 ? "" : String.valueOf(data.getShedCount());
            case "fermented":
                return data.getFermentedFeed() == 0 ? "" : formatNumber(data.getFermentedFeed());
            case "powder":
                return data.getPowderFeed() == 0 ? "" : formatNumber(data.getPowderFeed());
            case "feed03":
                return data.getFeed03() == 0 ? "" : formatNumber(data.getFeed03());
            case "feed05":
                return data.getFeed05() == 0 ? "" : formatNumber(data.getFeed05());
            case "feed10":
                return data.getFeed10() == 0 ? "" : formatNumber(data.getFeed10());
            default:
                return "";
        }
    }

    private String getCellDisplayValue(FeedData data, String columnId) {
        switch (columnId) {
            case "seq":
                return "";
            case "shed_number":
                String shedName = data.getShedNumber();
                return (shedName == null || shedName.isEmpty()) ? "" : shedName;
            case "average_feed":
                // 使用新的方法获取平均吃料值，考虑是否包含发酵料
                double averageValue = data.getAverage(fermentedIncludedInAverage);
                return averageValue == 0 ? "" : formatNumber(averageValue);
            case "water":
                double waterValue = data.getWater();
                return waterValue == 0 ? "" : formatNumber(waterValue);
            case "weighed_feed":
                double weighedFeed = data.getWeighedFeed();
                int shedCount = data.getShedCount();
                if (shedCount > 0 && weighedFeed > 0) {
                    if (weighedFeed == (int) weighedFeed) {
                        return String.valueOf((int) weighedFeed) + "×" + shedCount;
                    } else {
                        return String.format(java.util.Locale.ROOT, "%.2f", weighedFeed) + "×" + shedCount;
                    }
                }
                return "";
            default:
                return "";
        }
    }

    private String formatNumber(double value) {
        if (value == 0) {
            return "";
        }

        if (value == (int) value) {
            return String.valueOf((int) value);
        } else {
            return String.format(java.util.Locale.ROOT, "%.2f", value);
        }
    }

    private boolean isEditableColumn(String columnId) {
        return "shed_number".equals(columnId) || 
            "shed_count".equals(columnId) ||
            "fermented".equals(columnId) ||
            "powder".equals(columnId) ||
            "feed03".equals(columnId) ||
            "feed05".equals(columnId) ||
            "feed10".equals(columnId);
    }

    private void showColumnOperationMenu(final int columnIndex, final String columnId, View view) {
        try {
            boolean isVisible = columnVisibility.get(columnId);
            boolean isFixed = columnFixed.get(columnId);

            List<String> menuItems = new ArrayList<String>();

            if (isFixed) {
                menuItems.add("取消固定此列");
            } else {
                menuItems.add("固定此列");
            }

            if (isVisible) {
                menuItems.add("隐藏此列");
            } else {
                menuItems.add("显示此列");
            }

            menuItems.add("显示所有列");
            menuItems.add("取消所有固定");

            final String[] items = menuItems.toArray(new String[0]);

            showStyledListDialog("列操作 - " + columnNames[columnIndex], items, (dialog, which) -> {
                handleColumnOperation(columnIndex, columnId, items[which]);
            });

        } catch (Exception e) {
            Log.e(TAG, "显示列操作菜单异常: " + e.getMessage());
        }
    }

    private void handleColumnOperation(int columnIndex, String columnId, String operation) {
        try {
            switch (operation) {
                case "固定此列":
                case "取消固定此列":
                    boolean newFixedState = !columnFixed.get(columnId);
                    columnFixed.put(columnId, newFixedState);
                    saveColumnFixedState();
                    Toast.makeText(this, columnNames[columnIndex] + "列已" + (newFixedState ? "固定" : "取消固定"), Toast.LENGTH_SHORT).show();
                    break;

                case "隐藏此列":
                case "显示此列":
                    boolean newVisibility = !columnVisibility.get(columnId);
                    columnVisibility.put(columnId, newVisibility);
                    saveColumnVisibility();
                    Toast.makeText(this, columnNames[columnIndex] + "列已" + (newVisibility ? "显示" : "隐藏"), Toast.LENGTH_SHORT).show();
                    break;

                case "显示所有列":
                    showAllColumns();
                    return;

                case "取消所有固定":
                    unfixAllColumns();
                    return;
            }

            new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        rebuildCompleteTable();
                    }
                }, 100);

        } catch (Exception e) {
            Log.e(TAG, "处理列操作异常: " + e.getMessage());
        }
    }

    private void showAllColumns() {
        try {
            boolean hasChanged = false;

            for (int i = 1; i < columnIds.length; i++) {
                String columnId = columnIds[i];
                if (!columnVisibility.get(columnId)) {
                    columnVisibility.put(columnId, true);
                    hasChanged = true;
                }
            }

            if (hasChanged) {
                saveColumnVisibility();
                rebuildCompleteTable();
                Toast.makeText(this, "已显示所有列", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "显示所有列异常: " + e.getMessage());
        }
    }

    private void unfixAllColumns() {
        try {
            boolean hasChanged = false;

            for (int i = 1; i < columnIds.length; i++) {
                String columnId = columnIds[i];
                if (columnFixed.get(columnId)) {
                    columnFixed.put(columnId, false);
                    hasChanged = true;
                }
            }

            if (hasChanged) {
                saveColumnFixedState();
                rebuildCompleteTable();
                Toast.makeText(this, "已取消所有固定列", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "取消所有固定列异常: " + e.getMessage());
        }
    }

    private void setupScrollSync() {
        try {
            headerScrollView.setHorizontalScrollBarEnabled(false);
            footerScrollView.setHorizontalScrollBarEnabled(false);

            for (HorizontalScrollView scrollView : rowScrollViews) {
                scrollView.setHorizontalScrollBarEnabled(false);
            }

            setupScrollViewListener(headerScrollView);
            setupScrollViewListener(footerScrollView);

            for (HorizontalScrollView scrollView : rowScrollViews) {
                setupScrollViewListener(scrollView);
            }

            Log.d(TAG, "滚动同步设置完成");

        } catch (Exception e) {
            Log.e(TAG, "设置滚动同步异常: " + e.getMessage());
        }
    }

    private void setupScrollViewListener(final HorizontalScrollView scrollView) {
        if (scrollView == null) return;

        ViewTreeObserver.OnScrollChangedListener listener = new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                if (!isSyncing && scrollView != null) {
                    isSyncing = true;
                    int newScrollX = scrollView.getScrollX();
                    if (newScrollX != currentScrollX) {
                        currentScrollX = newScrollX;
                        syncAllScrollViews(currentScrollX, scrollView);
                    }
                    isSyncing = false;
                }
            }
        };

        scrollView.getViewTreeObserver().addOnScrollChangedListener(listener);
        scrollListeners.add(listener);
    }

    private void syncAllScrollViews(int scrollX, HorizontalScrollView source) {
        try {
            if (headerScrollView != null && headerScrollView != source) {
                headerScrollView.scrollTo(scrollX, 0);
            }

            if (footerScrollView != null && footerScrollView != source) {
                footerScrollView.scrollTo(scrollX, 0);
            }

            for (HorizontalScrollView rowScrollView : rowScrollViews) {
                if (rowScrollView != null && rowScrollView != source) {
                    rowScrollView.scrollTo(scrollX, 0);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "同步滚动视图异常: " + e.getMessage());
        }
    }

    private void showWaterPercentageDialog() {
        try {
            final String[] percentages = {"10", "15", "20", "25", "30", "35", "40"};
            final String[] displayTexts = {
                "水10%", "水15%", "水20%", "水25%", "水30%", "水35%", "水40%"
            };

            showStyledListDialog("选择水百分比", displayTexts, (dialog, which) -> {
                try {
                    globalWaterPercentage = Integer.parseInt(percentages[which]);
                    updateWaterHeader();
                    saveGlobalWaterPercentage();
                    updateAllCalculations();
                    Toast.makeText(MixCalcActivity.this, "水百分比已设置为" + globalWaterPercentage + "%", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "选择水百分比异常: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "显示水百分比对话框异常: " + e.getMessage());
        }
    }

    private void updateWaterHeader() {
        try {
            updateWaterCells(fixedHeaderContainer);
            updateWaterCells(headerScrollContent);
        } catch (Exception e) {
            Log.e(TAG, "更新水列表头异常: " + e.getMessage());
        }
    }

    private void updateWaterCells(LinearLayout container) {
        if (container != null) {
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    if (tv.getText().toString().startsWith("水")) {
                        tv.setText(getString(R.string.water_percentage_header, globalWaterPercentage));
                    }
                }
            }
        }
    }

    private int dpToPx(int dp) {
        try {
            float density = getResources().getDisplayMetrics().density;
            return Math.round(dp * density);
        } catch (Exception e) {
            return dp;
        }
    }

    // 显示重置确认对话框
    private void showResetConfirmationDialog() {
        try {
            int filledRows = getFilledRowCount();
            String message;

            if (filledRows > 0) {
                message = "确定要重置所有数据吗？\n\n当前已有 " + filledRows + " 行数据，重置后将全部清空。";
            } else {
                message = "确定要重置所有数据吗？\n\n当前没有数据需要重置。";
            }

            showStyledConfirmDialog70("确认重置", message,
                new String[]{"取消", "确定重置"},
                new int[]{0xFF666666, 0xFFD32F2F},
                new DialogInterface.OnClickListener[]{null, (dialog, which) -> resetAllData()});

        } catch (Exception e) {
            Log.e(TAG, "显示重置确认对话框异常: " + e.getMessage());
            resetAllData();
        }
    }

    // ---------- 批次与基础数据检查相关方法 ----------
    private boolean isBasicDataComplete() {
        String seedQuantity = dbHelper.getBasicData(currentBatchId, "seed_quantity");
        String pondCount = dbHelper.getBasicData(currentBatchId, "pond_count");
        String pondLength = dbHelper.getBasicData(currentBatchId, "pond_length");
        String aeratorCount = dbHelper.getBasicData(currentBatchId, "aerator_count");
        String aerationPower = dbHelper.getBasicData(currentBatchId, "aeration_power");
        String stockingDate = dbHelper.getBasicData(currentBatchId, "stocking_date");
        String feedBrand = dbHelper.getBasicData(currentBatchId, "feed_brand");

        return !seedQuantity.isEmpty() &&
               !pondCount.isEmpty() &&
               !pondLength.isEmpty() &&
               !aeratorCount.isEmpty() &&
               !aerationPower.isEmpty() &&
               !stockingDate.isEmpty() &&
               !stockingDate.equals("选择日期") &&
               !feedBrand.isEmpty();
    }

    private void showNoBatchDialog() {
        showStyledConfirmDialog("提示", "请先在批次管理中创建至少一个批次",
            new String[]{"退出", "去创建"},
            new int[]{0xFF666666, 0xFF4CAF50},
            new DialogInterface.OnClickListener[]{
                (dialog, which) -> finish(),
                (dialog, which) -> {
                    startActivity(new Intent(MixCalcActivity.this, BatchManageActivity.class));
                    finish();
                }
            });
    }

    private void showBasicDataIncompleteDialog() {
        showStyledConfirmDialog("提示", "请先完成基础数据中的所有必填项",
            new String[]{"取消", "去设置"},
            new int[]{0xFF666666, 0xFF4CAF50},
            new DialogInterface.OnClickListener[]{
                (dialog, which) -> finish(),
                (dialog, which) -> {
                    startActivity(new Intent(MixCalcActivity.this, BasicDataActivity.class));
                    finish();
                }
            });
    }
}
