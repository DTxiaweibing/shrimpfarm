package com.shrimpfarm.app.analysis;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FeedAmountFragment extends Fragment {

    // UI 组件
    private BarChart chartFeedAmount;
    private LineChart chartFeedAmountLine;
    private TextView tvChartTitle;
    private TextView tvTotalFeed;
    private TextView tvAvgFeed;
    private TextView tvMaxFeedDate;
    private TextView tvMaxFeedValue;
    private TextView tvMinFeedDate;
    private TextView tvMinFeedValue;

    // 当前显示类型：true=柱状图，false=折线图
    private boolean isBarChart = true;

    // 缓存当前数据，用于切换图表时重新绘制
    private List<DatabaseHelper.DailyFeedSummary> currentSummaries = new ArrayList<>();

    // 数据相关
    private DatabaseHelper dbHelper;
    private String currentBatchId;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
    private SimpleDateFormat displayFormat = new SimpleDateFormat("MM/dd", Locale.getDefault());

    private int selectedDays = 7;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_feed_amount, container, false);

        // 初始化视图
        chartFeedAmount = view.findViewById(R.id.chart_feed_amount);
        chartFeedAmountLine = view.findViewById(R.id.chart_feed_amount_line);
        tvChartTitle = view.findViewById(R.id.tv_chart_title);
        tvTotalFeed = view.findViewById(R.id.tv_total_feed);
        tvAvgFeed = view.findViewById(R.id.tv_avg_feed);
        tvMaxFeedDate = view.findViewById(R.id.tv_max_feed_date);
        tvMaxFeedValue = view.findViewById(R.id.tv_max_feed_value);
        tvMinFeedDate = view.findViewById(R.id.tv_min_feed_date);
        tvMinFeedValue = view.findViewById(R.id.tv_min_feed_value);

        // 初始化数据库
        dbHelper = new DatabaseHelper(getContext());
        if (getActivity() != null) {
            currentBatchId = getActivity()
                .getSharedPreferences("app_prefs", getContext().MODE_PRIVATE)
                .getString("current_batch_id", "");
        }

        // 设置图表基本样式
        setupCharts();

        // 设置标题点击切换图表类型
        tvChartTitle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isBarChart) {
                        switchToLineChart();
                    } else {
                        switchToBarChart();
                    }
                }
            });

        // 加载数据
        if (currentBatchId != null && !currentBatchId.isEmpty()) {
            loadData(selectedDays);
        } else {
            chartFeedAmount.setNoDataText("请先选择批次");
            chartFeedAmountLine.setNoDataText("请先选择批次");
            resetSummary();
        }

        return view;
    }

    private void setupCharts() {
        // 柱状图设置
        chartFeedAmount.getDescription().setEnabled(false);
        chartFeedAmount.setDrawBarShadow(false);
        chartFeedAmount.setDrawValueAboveBar(true);
        chartFeedAmount.setPinchZoom(false);
        chartFeedAmount.setDrawGridBackground(false);
        XAxis barXAxis = chartFeedAmount.getXAxis();
        barXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        barXAxis.setGranularity(1f);
        barXAxis.setLabelRotationAngle(-45);
        barXAxis.setDrawGridLines(false);

        // 折线图设置
        chartFeedAmountLine.getDescription().setEnabled(false);
        chartFeedAmountLine.setTouchEnabled(true);
        chartFeedAmountLine.setDragEnabled(true);
        chartFeedAmountLine.setScaleEnabled(true);
        chartFeedAmountLine.setPinchZoom(false);
        chartFeedAmountLine.setDrawGridBackground(false);
        XAxis lineXAxis = chartFeedAmountLine.getXAxis();
        lineXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        lineXAxis.setGranularity(1f);
        lineXAxis.setLabelRotationAngle(-45);
        lineXAxis.setDrawGridLines(false);
    }

    private void switchToLineChart() {
        isBarChart = false;
        chartFeedAmount.setVisibility(View.GONE);
        chartFeedAmountLine.setVisibility(View.VISIBLE);
        updateLineChart();
    }

    private void switchToBarChart() {
        isBarChart = true;
        chartFeedAmountLine.setVisibility(View.GONE);
        chartFeedAmount.setVisibility(View.VISIBLE);
        updateBarChart();
    }

    public void setDateRange(int days) {
        this.selectedDays = days;
        if (isAdded()) {
            loadData(days);
        }
    }

    private void loadData(int days) {
        Calendar endCal = Calendar.getInstance();
        String endDate = dateFormat.format(endCal.getTime());
        endCal.add(Calendar.DAY_OF_YEAR, -days + 1);
        String startDate = dateFormat.format(endCal.getTime());
        loadDataByDateRange(startDate, endDate);
    }

    public void loadDataByDateRange(String startDate, String endDate) {
        if (dbHelper == null || currentBatchId == null || currentBatchId.isEmpty()) {
            if (chartFeedAmount != null) {
                chartFeedAmount.setNoDataText("请先选择批次");
            }
            if (chartFeedAmountLine != null) {
                chartFeedAmountLine.setNoDataText("请先选择批次");
            }
            resetSummary();
            return;
        }

        try {
            List<DatabaseHelper.DailyFeedSummary> summaries = dbHelper.getDailyFeedSummary(
                currentBatchId, startDate, endDate);
            currentSummaries = summaries;
            updateSummaryCards(summaries);

            if (isBarChart) {
                updateBarChart();
            } else {
                updateLineChart();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (chartFeedAmount != null) {
                chartFeedAmount.setNoDataText("数据加载失败");
            }
            if (chartFeedAmountLine != null) {
                chartFeedAmountLine.setNoDataText("数据加载失败");
            }
            resetSummary();
        }
    }

    private void updateBarChart() {
        if (currentSummaries.isEmpty()) {
            chartFeedAmount.clear();
            chartFeedAmount.setNoDataText("暂无投喂数据");
            return;
        }

        List<BarEntry> entries = new ArrayList<>();
        final List<String> xLabels = new ArrayList<>();

        for (int i = 0; i < currentSummaries.size(); i++) {
            DatabaseHelper.DailyFeedSummary summary = currentSummaries.get(i);
            entries.add(new BarEntry(i, summary.totalFeed));
            try {
                Date date = dateFormat.parse(summary.date);
                xLabels.add(displayFormat.format(date));
            } catch (Exception e) {
                xLabels.add(summary.date);
            }
        }

        BarDataSet dataSet = new BarDataSet(entries, "投喂量(kg)");
        dataSet.setColor(Color.parseColor("#4CAF50"));
        dataSet.setValueTextSize(10f);
        dataSet.setValueTextColor(Color.BLACK);

        BarData barData = new BarData(dataSet);
        chartFeedAmount.setData(barData);

        chartFeedAmount.getXAxis().setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    int idx = (int) value;
                    if (idx >= 0 && idx < xLabels.size()) {
                        return xLabels.get(idx);
                    }
                    return "";
                }
            });

        chartFeedAmount.animateY(1000);
        chartFeedAmount.invalidate();
    }

    private void updateLineChart() {
        if (currentSummaries.isEmpty()) {
            chartFeedAmountLine.clear();
            chartFeedAmountLine.setNoDataText("暂无投喂数据");
            return;
        }

        List<Entry> entries = new ArrayList<>();
        final List<String> xLabels = new ArrayList<>();

        for (int i = 0; i < currentSummaries.size(); i++) {
            DatabaseHelper.DailyFeedSummary summary = currentSummaries.get(i);
            entries.add(new Entry(i, summary.totalFeed));
            try {
                Date date = dateFormat.parse(summary.date);
                xLabels.add(displayFormat.format(date));
            } catch (Exception e) {
                xLabels.add(summary.date);
            }
        }

        LineDataSet dataSet = new LineDataSet(entries, "投喂量(kg)");
        dataSet.setColor(Color.parseColor("#4CAF50"));
        dataSet.setCircleColor(Color.parseColor("#4CAF50"));
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextSize(10f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData lineData = new LineData(dataSet);
        chartFeedAmountLine.setData(lineData);

        chartFeedAmountLine.getXAxis().setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    int idx = (int) value;
                    if (idx >= 0 && idx < xLabels.size()) {
                        return xLabels.get(idx);
                    }
                    return "";
                }
            });

        chartFeedAmountLine.animateX(1000);
        chartFeedAmountLine.invalidate();
    }

    private void updateSummaryCards(List<DatabaseHelper.DailyFeedSummary> summaries) {
        if (summaries.isEmpty()) {
            resetSummary();
            return;
        }

        float total = 0;
        float max = Float.MIN_VALUE;
        float min = Float.MAX_VALUE;
        String maxDate = "";
        String minDate = "";

        for (DatabaseHelper.DailyFeedSummary summary : summaries) {
            float amount = summary.totalFeed;
            total += amount;
            if (amount > max) {
                max = amount;
                maxDate = summary.date;
            }
            if (amount < min) {
                min = amount;
                minDate = summary.date;
            }
        }

        float avg = total / summaries.size();

        tvTotalFeed.setText(String.format(Locale.getDefault(), "%.1f", total));
        tvAvgFeed.setText(String.format(Locale.getDefault(), "%.1f", avg));

        try {
            Date maxD = dateFormat.parse(maxDate);
            Date minD = dateFormat.parse(minDate);
            tvMaxFeedDate.setText(displayFormat.format(maxD));
            tvMinFeedDate.setText(displayFormat.format(minD));
        } catch (Exception e) {
            tvMaxFeedDate.setText(maxDate);
            tvMinFeedDate.setText(minDate);
        }

        tvMaxFeedValue.setText(String.format(Locale.getDefault(), "%.1f", max));
        tvMinFeedValue.setText(String.format(Locale.getDefault(), "%.1f", min));
    }

    private void resetSummary() {
        tvTotalFeed.setText("0");
        tvAvgFeed.setText("0");
        tvMaxFeedDate.setText("--");
        tvMaxFeedValue.setText("0");
        tvMinFeedDate.setText("--");
        tvMinFeedValue.setText("0");
    }
}
