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
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FeedDurationFragment extends Fragment {

    private BarChart chartFeedDuration;
    private TextView tvChartTitle;
    private TextView tvAvgDuration;
    private TextView tvFastestDate;
    private TextView tvFastestValue;
    private TextView tvSlowestDate;
    private TextView tvSlowestValue;

    private DatabaseHelper dbHelper;
    private String currentBatchId;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
    private SimpleDateFormat displayFormat = new SimpleDateFormat("MM/dd", Locale.getDefault());

    private int selectedDays = 7;
    private boolean isDateMode = true;
    private boolean isCustomMode = false;
    private String customShedNumber = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_feed_duration, container, false);

        chartFeedDuration = view.findViewById(R.id.chart_feed_duration);
        tvChartTitle = view.findViewById(R.id.tv_chart_title);
        tvAvgDuration = view.findViewById(R.id.tv_avg_duration);
        tvFastestDate = view.findViewById(R.id.tv_fastest_date);
        tvFastestValue = view.findViewById(R.id.tv_fastest_value);
        tvSlowestDate = view.findViewById(R.id.tv_slowest_date);
        tvSlowestValue = view.findViewById(R.id.tv_slowest_value);

        dbHelper = new DatabaseHelper(getContext());
        if (getActivity() != null) {
            currentBatchId = getActivity()
                .getSharedPreferences("app_prefs", getContext().MODE_PRIVATE)
                .getString("current_batch_id", "");
        }

        try {
            setupChart();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (currentBatchId != null && !currentBatchId.isEmpty()) {
            try {
                loadData(selectedDays);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            chartFeedDuration.setNoDataText("请先选择批次");
            resetSummary();
        }

        return view;
    }

    private void setupChart() {
        chartFeedDuration.getDescription().setEnabled(false);
        chartFeedDuration.setTouchEnabled(true);
        chartFeedDuration.setDragEnabled(true);
        chartFeedDuration.setScaleEnabled(true);
        chartFeedDuration.setPinchZoom(true);
        chartFeedDuration.setDrawGridBackground(false);
        chartFeedDuration.setDrawValueAboveBar(true);

        XAxis xAxis = chartFeedDuration.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(-45);
        xAxis.setDrawGridLines(false);

        tvChartTitle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isCustomMode) {
                    setDateRange(selectedDays);
                } else {
                    toggleChartMode();
                }
            }
        });
    }

    public void setDateRange(int days) {
        isCustomMode = false;
        customShedNumber = null;
        this.selectedDays = days;
        updateTitleText();
        if (isAdded() && currentBatchId != null && !currentBatchId.isEmpty()) {
            loadData(days);
        }
    }

    public void setCustomDateRange(String startDate, String endDate, String shedNumber) {
        isCustomMode = true;
        isDateMode = true;
        if (shedNumber != null && !shedNumber.isEmpty() && !shedNumber.equals("全部")) {
            customShedNumber = shedNumber;
            tvChartTitle.setText(getString(R.string.chart_title_shed_feed_duration, shedNumber));
        } else {
            customShedNumber = null;
            tvChartTitle.setText("平均吃料用时趋势（自定义）");
        }
        loadDataByDateRange(startDate, endDate);
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
            if (chartFeedDuration != null) chartFeedDuration.setNoDataText("请先选择批次");
            resetSummary();
            return;
        }

        try {
            long todayAvg = dbHelper.getTodayLastAverageDuration(currentBatchId);
            tvAvgDuration.setText(formatDuration(todayAvg / 60000f));
        } catch (Exception e) {
            tvAvgDuration.setText("--");
        }

        try {
            if (isDateMode) {
                if (isCustomMode && customShedNumber != null) {
                    List<DatabaseHelper.DurationByShedSummary> summaries = dbHelper.getFeedingDurationByShed(
                        currentBatchId, customShedNumber, startDate, endDate);
                    updateChartAndSummary(summaries);
                } else {
                    List<DatabaseHelper.DurationSummary> dailyAvg = dbHelper.getDailyAverageDurations(
                        currentBatchId, startDate, endDate);
                    updateChartWithDailyAverage(dailyAvg);
                }
            } else {
                List<DatabaseHelper.ShedDurationSummary> summaries = dbHelper.getFeedingDurationByShedGrouped(
                    currentBatchId, startDate, endDate);
                updateChartForShedMode(summaries);
            }
        } catch (Exception e) {
            e.printStackTrace();
            chartFeedDuration.setNoDataText("数据加载失败");
            resetSummary();
        }
    }

    private void updateChartWithDailyAverage(List<DatabaseHelper.DurationSummary> summaries) {
        if (summaries.isEmpty()) {
            chartFeedDuration.clear();
            chartFeedDuration.setNoDataText("暂无查料数据");
            resetSummary();
            return;
        }
        List<BarEntry> entries = new ArrayList<>();
        final List<String> xLabels = new ArrayList<>();
        float fastest = Float.MAX_VALUE, slowest = Float.MIN_VALUE;
        String fastestDate = "", slowestDate = "";

        for (int i = 0; i < summaries.size(); i++) {
            DatabaseHelper.DurationSummary s = summaries.get(i);
            float minutes = s.avgDuration / 60000f;
            entries.add(new BarEntry(i, minutes));
            try {
                Date date = dateFormat.parse(s.date);
                xLabels.add(displayFormat.format(date));
            } catch (Exception e) {
                xLabels.add(s.date);
            }
            if (minutes < fastest) { fastest = minutes; fastestDate = s.date; }
            if (minutes > slowest) { slowest = minutes; slowestDate = s.date; }
        }

        BarDataSet dataSet = new BarDataSet(entries, "平均用时(分钟)");
        dataSet.setColor(Color.parseColor("#FF9800"));
        dataSet.setValueTextSize(10f);
        dataSet.setValueTextColor(Color.BLACK);
        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.6f);
        chartFeedDuration.setData(barData);
        chartFeedDuration.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = (int) value;
                return idx >= 0 && idx < xLabels.size() ? xLabels.get(idx) : "";
            }
        });
        chartFeedDuration.animateY(1000);
        chartFeedDuration.invalidate();

        try {
            if (!fastestDate.isEmpty()) { Date d = dateFormat.parse(fastestDate); tvFastestDate.setText(displayFormat.format(d)); }
            else tvFastestDate.setText("--");
            if (!slowestDate.isEmpty()) { Date d = dateFormat.parse(slowestDate); tvSlowestDate.setText(displayFormat.format(d)); }
            else tvSlowestDate.setText("--");
        } catch (Exception e) {
            tvFastestDate.setText(fastestDate);
            tvSlowestDate.setText(slowestDate);
        }
        tvFastestValue.setText(formatDuration(fastest));
        tvSlowestValue.setText(formatDuration(slowest));
    }

    private void updateChartAndSummary(List<DatabaseHelper.DurationByShedSummary> summaries) {
        if (summaries.isEmpty()) {
            chartFeedDuration.clear();
            chartFeedDuration.setNoDataText("暂无查料数据");
            resetSummary();
            return;
        }
        List<BarEntry> entries = new ArrayList<>();
        final List<String> xLabels = new ArrayList<>();
        float fastest = Float.MAX_VALUE, slowest = Float.MIN_VALUE;
        String fastestDate = "", slowestDate = "";

        for (int i = 0; i < summaries.size(); i++) {
            DatabaseHelper.DurationByShedSummary s = summaries.get(i);
            float minutes = s.avgDurationMillis / 60f;
            entries.add(new BarEntry(i, minutes));
            try { Date date = dateFormat.parse(s.date); xLabels.add(displayFormat.format(date)); }
            catch (Exception e) { xLabels.add(s.date); }
            if (minutes < fastest) { fastest = minutes; fastestDate = s.date; }
            if (minutes > slowest) { slowest = minutes; slowestDate = s.date; }
        }
        BarDataSet dataSet = new BarDataSet(entries, "平均用时(分钟)");
        dataSet.setColor(Color.parseColor("#FF9800"));
        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.6f);
        chartFeedDuration.setData(barData);
        chartFeedDuration.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = (int) value;
                return idx >= 0 && idx < xLabels.size() ? xLabels.get(idx) : "";
            }
        });
        chartFeedDuration.animateY(1000);
        chartFeedDuration.invalidate();
        try {
            tvFastestDate.setText(!fastestDate.isEmpty() ? displayFormat.format(dateFormat.parse(fastestDate)) : "--");
            tvSlowestDate.setText(!slowestDate.isEmpty() ? displayFormat.format(dateFormat.parse(slowestDate)) : "--");
        } catch (Exception e) {
            tvFastestDate.setText(fastestDate);
            tvSlowestDate.setText(slowestDate);
        }
        tvFastestValue.setText(formatDuration(fastest));
        tvSlowestValue.setText(formatDuration(slowest));
    }

    private void resetSummary() {
        tvAvgDuration.setText("--");
        tvFastestDate.setText("--"); tvFastestValue.setText("--");
        tvSlowestDate.setText("--"); tvSlowestValue.setText("--");
    }

    private void updateChartForShedMode(List<DatabaseHelper.ShedDurationSummary> summaries) {
        if (summaries.isEmpty()) {
            chartFeedDuration.clear(); chartFeedDuration.setNoDataText("暂无查料数据");
            resetSummary(); return;
        }
        List<BarEntry> entries = new ArrayList<>();
        List<String> xLabels = new ArrayList<>();
        float fastest = Float.MAX_VALUE, slowest = Float.MIN_VALUE;
        String fastestShed = "", slowestShed = "";
        for (int i = 0; i < summaries.size(); i++) {
            DatabaseHelper.ShedDurationSummary s = summaries.get(i);
            float minutes = s.avgDurationMillis / 60f;
            entries.add(new BarEntry(i, minutes));
            xLabels.add(s.shedNumber);
            if (minutes < fastest) { fastest = minutes; fastestShed = s.shedNumber; }
            if (minutes > slowest) { slowest = minutes; slowestShed = s.shedNumber; }
        }
        BarDataSet dataSet = new BarDataSet(entries, "平均用时(分钟)");
        dataSet.setColor(Color.parseColor("#FF9800"));
        chartFeedDuration.setData(new BarData(dataSet));
        chartFeedDuration.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                int idx = (int) value;
                return idx >= 0 && idx < xLabels.size() ? xLabels.get(idx) : "";
            }
        });
        chartFeedDuration.animateY(1000); chartFeedDuration.invalidate();
        tvFastestDate.setText(fastestShed.isEmpty() ? "--" : fastestShed);
        tvSlowestDate.setText(slowestShed.isEmpty() ? "--" : slowestShed);
        tvFastestValue.setText(formatDuration(fastest));
        tvSlowestValue.setText(formatDuration(slowest));
    }

    private void toggleChartMode() {
        isDateMode = !isDateMode;
        updateTitleText();
        loadData(selectedDays);
    }

    private void updateTitleText() {
        if (isDateMode) {
            tvChartTitle.setText("平均吃料用时趋势（点击此处切换为按棚号）");
        } else {
            tvChartTitle.setText("各棚平均吃料用时趋势（点击此处切换为按日期）");
        }
    }

    private String formatDuration(float minutes) {
        int totalMinutes = (int) Math.round(minutes);
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        if (hours > 0) return hours + "小时" + mins + "分钟";
        else return mins + "分钟";
    }
}