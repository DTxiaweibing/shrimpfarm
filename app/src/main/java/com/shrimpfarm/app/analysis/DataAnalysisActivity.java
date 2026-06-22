package com.shrimpfarm.app.analysis;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;
import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.BatchManageActivity;
import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DataAnalysisActivity extends BaseActivity {

    private TabLayout tabLayout;
    private ViewPager viewPager;
    private Spinner spinnerDateRange;
    private Button btnCustomDate;
    private TextView tvBatchName;

    private AnalysisPagerAdapter pagerAdapter;

    private SharedPreferences appPrefs;
    private String currentBatchId;
    private String currentBatchName;
    private DatabaseHelper dbHelper;

    private boolean isCustomMode = false;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_analysis);

        appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentBatchId = appPrefs.getString("current_batch_id", "");
        currentBatchName = appPrefs.getString("current_batch_name", "");
        dbHelper = new DatabaseHelper(this);

        if (currentBatchId.isEmpty()) {
            showNoBatchDialog();
            return;
        }

        initViews();
        setupViewPager();
        setupSpinner();
        updateBatchDisplay();

        setupBottomNavigation();
    }

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_my;
    }

    private void initViews() {
        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);
        spinnerDateRange = findViewById(R.id.spinner_date_range);
        btnCustomDate = findViewById(R.id.btn_custom_date);
        tvBatchName = findViewById(R.id.tv_batch_name);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pagerAdapter != null) {
            pagerAdapter.refreshAllFragments();
        }
    }

    private void setupViewPager() {
        pagerAdapter = new AnalysisPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(pagerAdapter);
        tabLayout.setupWithViewPager(viewPager);
    }

    private void setupSpinner() {
        String[] dateRanges = {"最近7天", "最近14天", "最近30天", "最近90天"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                                                          android.R.layout.simple_spinner_item, dateRanges);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDateRange.setAdapter(adapter);

        spinnerDateRange.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (isCustomMode) {
                        setPresetStyle();
                        isCustomMode = false;
                        return;
                    }
                    int days = 7;
                    switch (position) {
                        case 0: days = 7; break;
                        case 1: days = 14; break;
                        case 2: days = 30; break;
                        case 3: days = 90; break;
                    }
                    pagerAdapter.setDateRange(days);
                    setPresetStyle();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });

        btnCustomDate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showCustomDatePicker();
                }
            });

        setPresetStyle();
    }

    private void setPresetStyle() {
        spinnerDateRange.setBackgroundResource(R.drawable.bg_button_secondary);
        btnCustomDate.setBackgroundResource(R.drawable.bg_spinner);
        btnCustomDate.setTextColor(0xFF333333);
    }

    private void setCustomStyle() {
        btnCustomDate.setBackgroundResource(R.drawable.bg_button_secondary);
        btnCustomDate.setTextColor(0xFF2D8C42);
        spinnerDateRange.setBackgroundResource(R.drawable.bg_spinner);
    }

    private void showCustomDatePicker() {
        // 获取当前显示的日期范围作为默认值（与当前选中Fragment一致，这里简单用最近7天）
        Calendar defaultStart = Calendar.getInstance();
        defaultStart.add(Calendar.DAY_OF_YEAR, -6); // 最近7天
        Calendar defaultEnd = Calendar.getInstance();

        // 创建对话框布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        // 开始日期行
        LinearLayout rowStart = new LinearLayout(this);
        rowStart.setOrientation(LinearLayout.HORIZONTAL);
        rowStart.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView tvStartLabel = new TextView(this);
        tvStartLabel.setText("开始日期：");
        tvStartLabel.setTextSize(16);
        final TextView tvStartDate = new TextView(this);
        tvStartDate.setText(dateFormat.format(defaultStart.getTime()));
        tvStartDate.setTextSize(16);
        tvStartDate.setPadding(20, 12, 20, 12);
        tvStartDate.setBackgroundResource(R.drawable.bg_spinner);
        tvStartDate.setClickable(true);
        rowStart.addView(tvStartLabel);
        rowStart.addView(tvStartDate);
        layout.addView(rowStart);

        // 间隔
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                                   LinearLayout.LayoutParams.MATCH_PARENT, 24));
        layout.addView(spacer);

        // 结束日期行
        LinearLayout rowEnd = new LinearLayout(this);
        rowEnd.setOrientation(LinearLayout.HORIZONTAL);
        rowEnd.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView tvEndLabel = new TextView(this);
        tvEndLabel.setText("结束日期：");
        tvEndLabel.setTextSize(16);
        final TextView tvEndDate = new TextView(this);
        tvEndDate.setText(dateFormat.format(defaultEnd.getTime()));
        tvEndDate.setTextSize(16);
        tvEndDate.setPadding(20, 12, 20, 12);
        tvEndDate.setBackgroundResource(R.drawable.bg_spinner);
        tvEndDate.setClickable(true);
        rowEnd.addView(tvEndLabel);
        rowEnd.addView(tvEndDate);
        layout.addView(rowEnd);

        // 用于保存用户选择的日期
        final Calendar startCal = Calendar.getInstance();
        startCal.setTime(defaultStart.getTime());
        final Calendar endCal = Calendar.getInstance();
        endCal.setTime(defaultEnd.getTime());

        // 开始日期点击
        tvStartDate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DatePickerDialog dialog = new DatePickerDialog(DataAnalysisActivity.this,
                        new DatePickerDialog.OnDateSetListener() {
                            @Override
                            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                                startCal.set(year, month, dayOfMonth);
                                tvStartDate.setText(dateFormat.format(startCal.getTime()));
                            }
                        },
                        startCal.get(Calendar.YEAR),
                        startCal.get(Calendar.MONTH),
                        startCal.get(Calendar.DAY_OF_MONTH));
                    dialog.show();
                }
            });

        // 结束日期点击
        tvEndDate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DatePickerDialog dialog = new DatePickerDialog(DataAnalysisActivity.this,
                        new DatePickerDialog.OnDateSetListener() {
                            @Override
                            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                                endCal.set(year, month, dayOfMonth);
                                tvEndDate.setText(dateFormat.format(endCal.getTime()));
                            }
                        },
                        endCal.get(Calendar.YEAR),
                        endCal.get(Calendar.MONTH),
                        endCal.get(Calendar.DAY_OF_MONTH));
                    dialog.show();
                }
            });

        // 间隔
        View spacer2 = new View(this);
        spacer2.setLayoutParams(new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, 24));
        layout.addView(spacer2);

        // 棚号选择行（仅吃料用时页面显示）
        int currentTab = tabLayout.getSelectedTabPosition();
        final boolean showShedSelector = (currentTab == 1);
        final LinearLayout rowShed = new LinearLayout(this);
        rowShed.setOrientation(LinearLayout.HORIZONTAL);
        rowShed.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView tvShedLabel = new TextView(this);
        tvShedLabel.setText("棚号：");
        tvShedLabel.setTextSize(16);
        final Spinner spinnerCustomShed = new Spinner(this);
        List<String> shedList = new ArrayList<>();
        if (dbHelper != null && currentBatchId != null && !currentBatchId.isEmpty()) {
            shedList.addAll(dbHelper.getShedNumbers(currentBatchId));
        }
        if (shedList.isEmpty()) {
            Toast.makeText(this, "暂无棚号数据", Toast.LENGTH_SHORT).show();
        }
        ArrayAdapter<String> shedAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, shedList);
        shedAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCustomShed.setAdapter(shedAdapter);
        rowShed.addView(tvShedLabel);
        rowShed.addView(spinnerCustomShed);
        if (showShedSelector) {
            layout.addView(rowShed);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("自定义日期范围");
        builder.setView(layout);
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // 验证日期有效性
                    if (endCal.before(startCal)) {
                        Toast.makeText(DataAnalysisActivity.this, "结束日期不能早于开始日期", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String startDate = dateFormat.format(startCal.getTime());
                    String endDate = dateFormat.format(endCal.getTime());
                    String selectedCustomShed = showShedSelector ?
                        shedList.get(spinnerCustomShed.getSelectedItemPosition()) : "全部";

                    // 更新三个Fragment
                    pagerAdapter.setCustomDateRange(startDate, endDate, selectedCustomShed);
                    setCustomStyle();
                    isCustomMode = true;

                    Toast.makeText(DataAnalysisActivity.this, "已应用自定义日期", Toast.LENGTH_SHORT).show();
                }
            });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void updateBatchDisplay() {
        if (currentBatchName != null && !currentBatchName.isEmpty()) {
            tvBatchName.setText(currentBatchName);
        } else {
            tvBatchName.setText("未选择批次");
        }
    }

    private void showNoBatchDialog() {
        showStyledConfirmDialog("提示", "请先在批次管理中创建至少一个批次",
            new String[]{"退出", "去创建"},
            new int[]{0xFF666666, 0xFF4CAF50},
            new DialogInterface.OnClickListener[]{
                (dialog, which) -> finish(),
                (dialog, which) -> {
                    startActivity(new Intent(DataAnalysisActivity.this, BatchManageActivity.class));
                    finish();
                }
            });
    }

    // ViewPager适配器
    private static class AnalysisPagerAdapter extends FragmentPagerAdapter {

        private FragmentManager fragmentManager;

        public AnalysisPagerAdapter(@NonNull FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.fragmentManager = fm;
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0: return new FeedAmountFragment();
                case 1: return new FeedDurationFragment();
                case 2: return new WaterFragment();
                default: return new Fragment();
            }
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0: return "吃料量";
                case 1: return "吃料用时";
                case 2: return "水质";
                default: return "";
            }
        }

        private Fragment findFragmentByPosition(int position) {
            return fragmentManager.findFragmentByTag(
                "android:switcher:" + R.id.view_pager + ":" + position);
        }

        public void refreshAllFragments() {
            for (int i = 0; i < getCount(); i++) {
                Fragment f = findFragmentByPosition(i);
                if (f != null && f.isAdded()) {
                    if (f instanceof FeedAmountFragment) {
                        ((FeedAmountFragment) f).loadDataByDateRange(
                            getDefaultStartDate(7), getDefaultEndDate());
                    } else if (f instanceof FeedDurationFragment) {
                        ((FeedDurationFragment) f).setDateRange(7);
                    } else if (f instanceof WaterFragment) {
                        ((WaterFragment) f).loadWaterData();
                    }
                }
            }
        }

        public void setDateRange(int days) {
            String endDate = getDefaultEndDate();
            String startDate = getDefaultStartDate(days);
            for (int i = 0; i < getCount(); i++) {
                Fragment f = findFragmentByPosition(i);
                if (f != null && f.isAdded()) {
                    try {
                        if (f instanceof FeedAmountFragment) {
                            ((FeedAmountFragment) f).setDateRange(days);
                        } else if (f instanceof FeedDurationFragment) {
                            ((FeedDurationFragment) f).setDateRange(days);
                        } else if (f instanceof WaterFragment) {
                            ((WaterFragment) f).setDateRange(days);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private String getDefaultEndDate() {
            return new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                .format(Calendar.getInstance().getTime());
        }

        private String getDefaultStartDate(int days) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -days + 1);
            return new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(cal.getTime());
        }

        public void setCustomDateRange(String startDate, String endDate) {
            setCustomDateRange(startDate, endDate, "全部");
        }

        public void setCustomDateRange(String startDate, String endDate, String shedNumber) {
            for (int i = 0; i < getCount(); i++) {
                Fragment f = findFragmentByPosition(i);
                if (f != null && f.isAdded()) {
                    try {
                        if (f instanceof FeedAmountFragment) {
                            ((FeedAmountFragment) f).loadDataByDateRange(startDate, endDate);
                        } else if (f instanceof FeedDurationFragment) {
                            ((FeedDurationFragment) f).setCustomDateRange(startDate, endDate, shedNumber);
                        } else if (f instanceof WaterFragment) {
                            ((WaterFragment) f).loadDataByDateRange(startDate, endDate);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
