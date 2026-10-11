package com.shrimpfarm.app.finance;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.shrimpfarm.app.R;
import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.finance.FinanceDb.ProfitResult;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class ProfitActivity extends BaseActivity {

    private static final int RANGE_TODAY = 0;
    private static final int RANGE_MONTH = 1;
    private static final int RANGE_YEAR = 2;
    private static final int RANGE_CUSTOM = 3;

    private FinanceDb financeDb;
    private SharedPreferences prefs;
    private String currentBatchId;
    private int currentRange = RANGE_MONTH;
    private String customStart = "";
    private String customEnd = "";
    private TextView tvProfit, tvIncome, tvExpense, tvExpenseLife, tvExpenseAnimal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_profit);
        financeDb = new FinanceDb(this);
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentBatchId = prefs.getString("current_batch_id", "");
        setupToolbar();
        tvProfit = findViewById(R.id.tv_profit);
        tvIncome = findViewById(R.id.tv_income);
        tvExpense = findViewById(R.id.tv_expense);
        tvExpenseLife = findViewById(R.id.tv_expense_life);
        tvExpenseAnimal = findViewById(R.id.tv_expense_animal);
        setupRanges();
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_home;
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.finance_profit);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRanges() {
        TextView[] ranges = {findViewById(R.id.range_today), findViewById(R.id.range_month),
                findViewById(R.id.range_year), findViewById(R.id.range_custom)};
        for (int i = 0; i < ranges.length; i++) {
            final int index = i;
            ranges[i].setOnClickListener(v -> {
                currentRange = index;
                if (index == RANGE_CUSTOM && customStart.isEmpty()) {
                    pickCustomRange();
                } else {
                    updateRangeStyles(ranges);
                    loadData();
                }
            });
        }
        updateRangeStyles(ranges);
    }

    private void updateRangeStyles(TextView[] ranges) {
        for (int i = 0; i < ranges.length; i++) {
            if (i == currentRange) {
                ranges[i].setTextColor(0xFF4CAF50);
                ranges[i].setTextSize(16);
            } else {
                ranges[i].setTextColor(0xFF666666);
                ranges[i].setTextSize(14);
            }
        }
    }

    private void pickCustomRange() {
        final Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, y1, m1, d1) -> {
            customStart = String.format(Locale.CHINA, "%d/%02d/%02d", y1, m1 + 1, d1);
            new DatePickerDialog(this, (view2, y2, m2, d2) -> {
                customEnd = String.format(Locale.CHINA, "%d/%02d/%02d", y2, m2 + 1, d2);
                currentRange = RANGE_CUSTOM;
                updateRangeStyles(new TextView[]{findViewById(R.id.range_today),
                        findViewById(R.id.range_month), findViewById(R.id.range_year),
                        findViewById(R.id.range_custom)});
                loadData();
            }, y1, m1, d1).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void loadData() {
        currentBatchId = prefs.getString("current_batch_id", "");
        if (currentBatchId.isEmpty()) {
            tvProfit.setText("—");
            tvIncome.setText("—");
            tvExpense.setText("—");
            tvExpenseLife.setText("—");
            tvExpenseAnimal.setText("—");
            return;
        }
        String start = null;
        String end = null;
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA);
        switch (currentRange) {
            case RANGE_TODAY:
                start = fmt.format(cal.getTime());
                end = start;
                break;
            case RANGE_MONTH:
                cal.set(Calendar.DAY_OF_MONTH, 1);
                start = fmt.format(cal.getTime());
                end = today();
                break;
            case RANGE_YEAR:
                cal.set(Calendar.DAY_OF_YEAR, 1);
                start = fmt.format(cal.getTime());
                end = today();
                break;
            case RANGE_CUSTOM:
                start = customStart;
                end = customEnd;
                break;
        }
        ProfitResult r = financeDb.calcProfit(currentBatchId, start, end);
        double life = r.expense;
        double animal = r.animalHealth;
        tvProfit.setText(String.format(Locale.CHINA, "%.2f", r.profit));
        tvProfit.setTextColor(r.profit >= 0 ? 0xFF4CAF50 : 0xFFE53935);
        tvIncome.setText(String.format(Locale.CHINA, "%.2f", r.income));
        tvExpense.setText(String.format(Locale.CHINA, "%.2f", r.expense + animal));
        tvExpenseLife.setText(String.format(Locale.CHINA, "%.2f", life));
        tvExpenseAnimal.setText(String.format(Locale.CHINA, "%.2f", animal));
    }

    private String today() {
        return new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).format(Calendar.getInstance().getTime());
    }
}