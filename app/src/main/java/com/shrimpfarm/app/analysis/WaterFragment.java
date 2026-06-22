package com.shrimpfarm.app.analysis;

import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.utils.EncryptUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WaterFragment extends Fragment {

    private LineChart chartWater;
    private CheckBox cbPh, cbAmmonia, cbNitrite, cbDo, cbTemp, cbSalinity, cbChlorine, cbVibrio, cbOrp;
    private LinearLayout statsContainer;
    private TextView tvChartTitle;
    private TextView tvStatsTitle;
    private View cardChart;
    private View cardStats;

    private boolean isShowingChart = true;

    private DatabaseHelper dbHelper;
    private String currentBatchId;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
    private SimpleDateFormat displayFormat = new SimpleDateFormat("MM/dd", Locale.getDefault());

    private int selectedDays = 7;

    private String currentStartDate;
    private String currentEndDate;

    private static class Indicator {
        String name;
        String column;
        int color;
        boolean selected;
        Indicator(String name, String column, int color, boolean selected) {
            this.name = name;
            this.column = column;
            this.color = color;
            this.selected = selected;
        }
    }

    private Map<String, Indicator> indicatorMap = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_water, container, false);

        initIndicators();
        initViews(view);
        setupChart();

        dbHelper = new DatabaseHelper(getContext());
        if (getActivity() != null) {
            currentBatchId = getActivity()
                .getSharedPreferences("app_prefs", getContext().MODE_PRIVATE)
                .getString("current_batch_id", "");
        }

        if (currentBatchId != null && !currentBatchId.isEmpty()) {
            loadWaterData();
        } else {
            chartWater.setNoDataText("请先选择批次");
        }

        return view;
    }

    private void initIndicators() {
        indicatorMap.put("ph", new Indicator("pH", DatabaseHelper.COLUMN_PH, Color.BLUE, true));
        indicatorMap.put("ammonia", new Indicator("氨氮", DatabaseHelper.COLUMN_AMMONIA, Color.RED, false));
        indicatorMap.put("nitrite", new Indicator("亚硝酸盐", DatabaseHelper.COLUMN_NITRITE, Color.parseColor("#FF9800"), false));
        indicatorMap.put("do", new Indicator("溶解氧", DatabaseHelper.COLUMN_DISSOLVED_OXYGEN, Color.parseColor("#009688"), false));
        indicatorMap.put("temp", new Indicator("水温(高)", DatabaseHelper.COLUMN_MAX_TEMP, Color.parseColor("#9C27B0"), false));
        indicatorMap.put("salinity", new Indicator("盐度", DatabaseHelper.COLUMN_SALINITY, Color.parseColor("#795548"), false));
        indicatorMap.put("chlorine", new Indicator("余氯", DatabaseHelper.COLUMN_CHLORINE, Color.parseColor("#00BCD4"), false));
        indicatorMap.put("vibrio", new Indicator("弧菌数", DatabaseHelper.COLUMN_VIBRIO, Color.parseColor("#E91E63"), false));
        indicatorMap.put("orp", new Indicator("氧化还原", DatabaseHelper.COLUMN_ORP, Color.parseColor("#607D8B"), false));
    }

    private void initViews(View view) {
        chartWater = view.findViewById(R.id.chart_water);
        cbPh = view.findViewById(R.id.cb_ph);
        cbAmmonia = view.findViewById(R.id.cb_ammonia);
        cbNitrite = view.findViewById(R.id.cb_nitrite);
        cbDo = view.findViewById(R.id.cb_do);
        cbTemp = view.findViewById(R.id.cb_temp);
        cbSalinity = view.findViewById(R.id.cb_salinity);
        cbChlorine = view.findViewById(R.id.cb_chlorine);
        cbVibrio = view.findViewById(R.id.cb_vibrio);
        cbOrp = view.findViewById(R.id.cb_orp);
        statsContainer = view.findViewById(R.id.layout_stats_container);
        tvChartTitle = view.findViewById(R.id.tv_chart_title);
        tvStatsTitle = view.findViewById(R.id.tv_stats_title);
        cardChart = view.findViewById(R.id.card_chart);
        cardStats = view.findViewById(R.id.card_stats);

        cbPh.setTextColor(indicatorMap.get("ph").color);
        cbAmmonia.setTextColor(indicatorMap.get("ammonia").color);
        cbNitrite.setTextColor(indicatorMap.get("nitrite").color);
        cbDo.setTextColor(indicatorMap.get("do").color);
        cbTemp.setTextColor(indicatorMap.get("temp").color);
        cbSalinity.setTextColor(indicatorMap.get("salinity").color);
        cbChlorine.setTextColor(indicatorMap.get("chlorine").color);
        cbVibrio.setTextColor(indicatorMap.get("vibrio").color);
        cbOrp.setTextColor(indicatorMap.get("orp").color);

        syncCheckBoxesFromMap();

        cbPh.setOnCheckedChangeListener((buttonView, isChecked) -> onCheckChanged("ph", isChecked));
        cbAmmonia.setOnCheckedChangeListener((buttonView, isChecked) -> onCheckChanged("ammonia", isChecked));
        cbNitrite.setOnCheckedChangeListener((buttonView, isChecked) -> onCheckChanged("nitrite", isChecked));
        cbDo.setOnCheckedChangeListener((buttonView, isChecked) -> onCheckChanged("do", isChecked));
        cbTemp.setOnCheckedChangeListener((buttonView, isChecked) -> onCheckChanged("temp", isChecked));
        cbSalinity.setOnCheckedChangeListener((buttonView, isChecked) -> onCheckChanged("salinity", isChecked));
        cbChlorine.setOnCheckedChangeListener((buttonView, isChecked) -> onCheckChanged("chlorine", isChecked));
        cbVibrio.setOnCheckedChangeListener((buttonView, isChecked) -> onCheckChanged("vibrio", isChecked));
        cbOrp.setOnCheckedChangeListener((buttonView, isChecked) -> onCheckChanged("orp", isChecked));

        tvChartTitle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cardChart.setVisibility(View.GONE);
                cardStats.setVisibility(View.VISIBLE);
                isShowingChart = false;
                List<WaterDataPoint> rawPoints = queryWaterRawData(currentStartDate, currentEndDate);
                updateStatsSummary(rawPoints, getSelectedIndicators());
            }
        });

        tvStatsTitle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cardStats.setVisibility(View.GONE);
                cardChart.setVisibility(View.VISIBLE);
                isShowingChart = true;
                loadWaterData();
            }
        });
    }

    private Date getStartDate() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -selectedDays + 1);
        return cal.getTime();
    }

    private List<Indicator> getSelectedIndicators() {
        List<Indicator> selected = new ArrayList<>();
        for (Indicator ind : indicatorMap.values()) {
            if (ind.selected) selected.add(ind);
        }
        return selected;
    }

    private void onCheckChanged(String key, boolean isChecked) {
        indicatorMap.get(key).selected = isChecked;
        loadWaterData();
        if (!isShowingChart) {
            updateStatsSummary(queryWaterData(
                dateFormat.format(getStartDate()),
                dateFormat.format(Calendar.getInstance().getTime())),
                getSelectedIndicators());
        }
    }

    private void syncCheckBoxesFromMap() {
        cbPh.setChecked(indicatorMap.get("ph").selected);
        cbAmmonia.setChecked(indicatorMap.get("ammonia").selected);
        cbNitrite.setChecked(indicatorMap.get("nitrite").selected);
        cbDo.setChecked(indicatorMap.get("do").selected);
        cbTemp.setChecked(indicatorMap.get("temp").selected);
        cbSalinity.setChecked(indicatorMap.get("salinity").selected);
        cbChlorine.setChecked(indicatorMap.get("chlorine").selected);
        cbVibrio.setChecked(indicatorMap.get("vibrio").selected);
        cbOrp.setChecked(indicatorMap.get("orp").selected);
    }

    private void setupChart() {
        chartWater.getDescription().setEnabled(false);
        chartWater.setTouchEnabled(true);
        chartWater.setDragEnabled(true);
        chartWater.setScaleEnabled(true);
        chartWater.setPinchZoom(true);
        chartWater.setDrawGridBackground(false);

        XAxis xAxis = chartWater.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(-45);
        xAxis.setDrawGridLines(false);
    }

    public void setDateRange(int days) {
        this.selectedDays = days;
        if (isAdded()) loadWaterData();
    }

    public void loadDataByDateRange(String startDate, String endDate) {
        currentStartDate = startDate;
        currentEndDate = endDate;
        queryAndUpdateChart(startDate, endDate);
    }

    public void loadWaterData() {
        Calendar endCal = Calendar.getInstance();
        currentEndDate = dateFormat.format(endCal.getTime());
        endCal.add(Calendar.DAY_OF_YEAR, -selectedDays + 1);
        currentStartDate = dateFormat.format(endCal.getTime());
        queryAndUpdateChart(currentStartDate, currentEndDate);
    }

    private void queryAndUpdateChart(String startDate, String endDate) {
        List<Indicator> selectedIndicators = getSelectedIndicators();
        if (selectedIndicators.isEmpty()) {
            chartWater.clear();
            chartWater.setNoDataText("请至少选择一个指标");
            statsContainer.removeAllViews();
            return;
        }

        List<WaterDataPoint> allPoints = queryWaterData(startDate, endDate);
        if (allPoints.isEmpty()) {
            chartWater.clear();
            chartWater.setNoDataText("暂无水质数据");
            statsContainer.removeAllViews();
            return;
        }

        List<ILineDataSet> dataSets = new ArrayList<>();
        final List<String> xLabels = new ArrayList<>();
        for (WaterDataPoint point : allPoints) {
            String label = displayFormat.format(point.date);
            if (!xLabels.contains(label)) xLabels.add(label);
        }

        for (Indicator ind : selectedIndicators) {
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < xLabels.size(); i++) {
                String label = xLabels.get(i);
                Float value = null;
                for (WaterDataPoint point : allPoints) {
                    if (displayFormat.format(point.date).equals(label)) {
                        value = point.values.get(ind.column);
                        break;
                    }
                }
                if (value != null) entries.add(new Entry(i, value));
            }
            LineDataSet dataSet = new LineDataSet(entries, ind.name);
            dataSet.setColor(ind.color);
            dataSet.setCircleColor(ind.color);
            dataSet.setLineWidth(2.5f);
            dataSet.setCircleRadius(4f);
            dataSet.setDrawValues(false);
            dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            dataSets.add(dataSet);
        }

        LineData lineData = new LineData(dataSets);
        chartWater.setData(lineData);
        chartWater.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = (int) value;
                if (idx >= 0 && idx < xLabels.size()) return xLabels.get(idx);
                return "";
            }
        });
        chartWater.animateX(1000);
        chartWater.invalidate();
    }

    private List<WaterDataPoint> queryWaterData(String startDate, String endDate) {
        List<WaterDataPoint> result = new ArrayList<>();
        if (dbHelper == null || currentBatchId == null || currentBatchId.isEmpty()) return result;

        Map<String, WaterDataPoint> dailyMap = new LinkedHashMap<>();
        Map<String, Integer> countMap = new HashMap<>();

        try {
            Cursor cursor = dbHelper.getReadableDatabase().query(
                DatabaseHelper.TABLE_WATER_QUALITY, null,
                DatabaseHelper.COLUMN_BATCH_ID + "=? AND " + DatabaseHelper.COLUMN_WQ_DATE + " BETWEEN ? AND ?",
                new String[]{currentBatchId, startDate, endDate},
                null, null, DatabaseHelper.COLUMN_WQ_DATE + " ASC");

            while (cursor.moveToNext()) {
                try {
                    int dateColIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_WQ_DATE);
                    if (dateColIndex < 0) continue;
                    String dateStr = cursor.getString(dateColIndex);
                    if (dateStr == null || dateStr.isEmpty()) continue;

                    if (!dailyMap.containsKey(dateStr)) {
                        dailyMap.put(dateStr, new WaterDataPoint(dateFormat.parse(dateStr)));
                        countMap.put(dateStr, 0);
                    }
                    WaterDataPoint point = dailyMap.get(dateStr);
                    countMap.put(dateStr, countMap.get(dateStr) + 1);

                    for (Indicator ind : indicatorMap.values()) {
                        int colIndex = cursor.getColumnIndexOrThrow(ind.column);
                        if (colIndex < 0) continue;
                        String encrypted = cursor.getString(colIndex);
                        String decrypted = EncryptUtils.decrypt(encrypted);
                        if (decrypted != null && !decrypted.isEmpty()) {
                            try {
                                float val = Float.parseFloat(decrypted);
                                Float existing = point.values.get(ind.column);
                                point.values.put(ind.column, existing == null ? val : existing + val);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
            cursor.close();

            for (Map.Entry<String, WaterDataPoint> entry : dailyMap.entrySet()) {
                WaterDataPoint point = entry.getValue();
                int count = countMap.get(entry.getKey());
                if (count > 1) {
                    for (String key : point.values.keySet()) {
                        point.values.put(key, point.values.get(key) / count);
                    }
                }
                result.add(point);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return result;
    }

    private List<WaterDataPoint> queryWaterRawData(String startDate, String endDate) {
        List<WaterDataPoint> result = new ArrayList<>();
        if (dbHelper == null || currentBatchId == null || currentBatchId.isEmpty()) return result;
        try {
            Cursor cursor = dbHelper.getReadableDatabase().query(
                DatabaseHelper.TABLE_WATER_QUALITY, null,
                DatabaseHelper.COLUMN_BATCH_ID + "=? AND " + DatabaseHelper.COLUMN_WQ_DATE + " BETWEEN ? AND ?",
                new String[]{currentBatchId, startDate, endDate},
                null, null, DatabaseHelper.COLUMN_WQ_DATE + " ASC");
            while (cursor.moveToNext()) {
                try {
                    int dateColIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_WQ_DATE);
                    if (dateColIndex < 0) continue;
                    String dateStr = cursor.getString(dateColIndex);
                    if (dateStr == null || dateStr.isEmpty()) continue;
                    Date date = dateFormat.parse(dateStr);
                    WaterDataPoint point = new WaterDataPoint(date);
                    for (Indicator ind : indicatorMap.values()) {
                        int colIndex = cursor.getColumnIndexOrThrow(ind.column);
                        if (colIndex < 0) continue;
                        String encrypted = cursor.getString(colIndex);
                        String decrypted = EncryptUtils.decrypt(encrypted);
                        if (decrypted != null && !decrypted.isEmpty()) {
                            try {
                                float val = Float.parseFloat(decrypted);
                                point.values.put(ind.column, val);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    result.add(point);
                } catch (Exception e) { e.printStackTrace(); }
            }
            cursor.close();
        } catch (Exception e) { e.printStackTrace(); }
        return result;
    }

    private WaterDataPoint queryWaterDataForDate(String date) {
        if (dbHelper == null || currentBatchId == null || currentBatchId.isEmpty()) return null;
        List<WaterDataPoint> points = new ArrayList<>();
        try {
            Cursor cursor = dbHelper.getReadableDatabase().query(
                DatabaseHelper.TABLE_WATER_QUALITY, null,
                DatabaseHelper.COLUMN_BATCH_ID + "=? AND " + DatabaseHelper.COLUMN_WQ_DATE + " = ?",
                new String[]{currentBatchId, date},
                null, null, null);
            while (cursor.moveToNext()) {
                try {
                    WaterDataPoint point = new WaterDataPoint(dateFormat.parse(date));
                    for (Indicator ind : indicatorMap.values()) {
                        int colIndex = cursor.getColumnIndexOrThrow(ind.column);
                        if (colIndex < 0) continue;
                        String encrypted = cursor.getString(colIndex);
                        String decrypted = EncryptUtils.decrypt(encrypted);
                        if (decrypted != null && !decrypted.isEmpty()) {
                            try {
                                float val = Float.parseFloat(decrypted);
                                point.values.put(ind.column, val);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    points.add(point);
                } catch (Exception ignored) {}
            }
            cursor.close();
        } catch (Exception e) { e.printStackTrace(); }
        if (points.isEmpty()) return null;
        // 同一天多条记录时计算平均
        Date parsedDate;
        try {
            parsedDate = dateFormat.parse(date);
        } catch (Exception e) { return null; }
        WaterDataPoint result = new WaterDataPoint(parsedDate);
        Map<String, Float> sum = new HashMap<>();
        Map<String, Integer> count = new HashMap<>();
        for (WaterDataPoint p : points) {
            for (String key : p.values.keySet()) {
                Float v = p.values.get(key);
                sum.put(key, v + (sum.containsKey(key) ? sum.get(key) : 0));
                count.put(key, 1 + (count.containsKey(key) ? count.get(key) : 0));
            }
        }
        for (String key : sum.keySet()) {
            result.values.put(key, sum.get(key) / count.get(key));
        }
        return result;
    }

    private void updateStatsSummary(List<WaterDataPoint> points, List<Indicator> selectedIndicators) {
        statsContainer.removeAllViews();
        for (Indicator ind : selectedIndicators) {
            float sum = 0; int count = 0;
            float max = Float.MIN_VALUE; float min = Float.MAX_VALUE;
            for (WaterDataPoint point : points) {
                Float val = point.values.get(ind.column);
                if (val != null) {
                    sum += val; count++;
                    if (val > max) max = val;
                    if (val < min) min = val;
                }
            }
            if (count == 0) continue;
            float avg = sum / count;

            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT));
            row.setPadding(0, 8, 0, 8);

            TextView tvName = new TextView(getContext());
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvName.setText(ind.name);
            tvName.setTextColor(Color.BLACK);
            tvName.setTextSize(14);

            TextView tvAvg = new TextView(getContext());
            tvAvg.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvAvg.setText(formatValue(avg, ind.column));
            tvAvg.setGravity(android.view.Gravity.END);
            tvAvg.setTextColor(Color.BLUE);
            tvAvg.setTextSize(14);

            TextView tvMax = new TextView(getContext());
            tvMax.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvMax.setText(formatValue(max, ind.column));
            tvMax.setGravity(android.view.Gravity.END);
            tvMax.setTextColor(Color.RED);
            tvMax.setTextSize(14);

            TextView tvMin = new TextView(getContext());
            tvMin.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvMin.setText(formatValue(min, ind.column));
            tvMin.setGravity(android.view.Gravity.END);
            tvMin.setTextColor(Color.parseColor("#4CAF50"));
            tvMin.setTextSize(14);

            row.addView(tvName); row.addView(tvAvg); row.addView(tvMax); row.addView(tvMin);
            statsContainer.addView(row);
        }
    }

    private String formatValue(float value, String column) {
        if (DatabaseHelper.COLUMN_PH.equals(column)) {
            return String.format(Locale.getDefault(), "%.1f", value);
        } else if (DatabaseHelper.COLUMN_MAX_TEMP.equals(column) || DatabaseHelper.COLUMN_MIN_TEMP.equals(column)) {
            return String.format(Locale.getDefault(), "%.1f ℃", value);
        } else if (DatabaseHelper.COLUMN_SALINITY.equals(column)) {
            return String.format(Locale.getDefault(), "%.1f ‰", value);
        } else {
            return String.format(Locale.getDefault(), "%.2f mg/L", value);
        }
    }

    private static class WaterDataPoint {
        Date date;
        Map<String, Float> values = new HashMap<>();
        WaterDataPoint(Date date) { this.date = date; }
    }
}