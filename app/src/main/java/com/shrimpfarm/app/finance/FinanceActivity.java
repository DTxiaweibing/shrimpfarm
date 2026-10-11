package com.shrimpfarm.app.finance;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.shrimpfarm.app.R;
import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.erp.PaymentAccountActivity;
import com.shrimpfarm.app.erp.SalaryActivity;
import com.shrimpfarm.app.erp.SettleActivity;
import com.shrimpfarm.app.erp.WorkLogActivity;
import com.shrimpfarm.app.finance.FinanceDb.ProfitResult;

import java.util.Locale;

public class FinanceActivity extends BaseActivity {

    private FinanceDb financeDb;
    private SharedPreferences prefs;
    private String currentBatchId;
    private TextView tvExpenseAmount, tvIncomeAmount, tvProfitAmount, tvCreditCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance);
        financeDb = new FinanceDb(this);
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentBatchId = prefs.getString("current_batch_id", "");
        setupToolbar();
        tvExpenseAmount = findViewById(R.id.tv_expense_amount);
        tvIncomeAmount = findViewById(R.id.tv_income_amount);
        tvProfitAmount = findViewById(R.id.tv_profit_amount);
        tvCreditCount = findViewById(R.id.tv_credit_count);

        findViewById(R.id.card_expense).setOnClickListener(v ->
                startActivity(new Intent(this, FinanceExpenseActivity.class)));
        findViewById(R.id.card_income).setOnClickListener(v ->
                startActivity(new Intent(this, IncomeActivity.class)));
        findViewById(R.id.card_profit).setOnClickListener(v ->
                startActivity(new Intent(this, ProfitActivity.class)));
        findViewById(R.id.card_credit).setOnClickListener(v ->
                startActivity(new Intent(this, IncomeActivity.class)));
        findViewById(R.id.btn_product).setOnClickListener(v ->
                startActivity(new Intent(this, ProductManageActivity.class)));
        findViewById(R.id.btn_supplier).setOnClickListener(v ->
                startActivity(new Intent(this, SupplierManageActivity.class)
                        .putExtra("personType", FinanceDb.PERSON_SUPPLIER)));
        findViewById(R.id.btn_agent).setOnClickListener(v ->
                startActivity(new Intent(this, SupplierManageActivity.class)
                        .putExtra("personType", FinanceDb.PERSON_AGENT)));
        findViewById(R.id.btn_customer).setOnClickListener(v ->
                startActivity(new Intent(this, SupplierManageActivity.class)
                        .putExtra("personType", FinanceDb.PERSON_CUSTOMER)));
        findViewById(R.id.btn_employee).setOnClickListener(v ->
                startActivity(new Intent(this, SupplierManageActivity.class)
                        .putExtra("personType", FinanceDb.PERSON_EMPLOYEE)));
        findViewById(R.id.btn_query).setOnClickListener(v ->
                startActivity(new Intent(this, QueryActivity.class)));
        findViewById(R.id.btn_settle).setOnClickListener(v ->
                startActivity(new Intent(this, SettleActivity.class)));
        findViewById(R.id.btn_payment_account).setOnClickListener(v ->
                startActivity(new Intent(this, PaymentAccountActivity.class)));
        findViewById(R.id.btn_work_log).setOnClickListener(v ->
                startActivity(new Intent(this, WorkLogActivity.class)));
        findViewById(R.id.btn_salary).setOnClickListener(v ->
                startActivity(new Intent(this, SalaryActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        currentBatchId = prefs.getString("current_batch_id", "");
        loadTotals();
    }

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_home;
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.finance_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadTotals() {
        if (currentBatchId.isEmpty()) {
            tvExpenseAmount.setText("—");
            tvIncomeAmount.setText("—");
            tvProfitAmount.setText("—");
            tvCreditCount.setText("");
            return;
        }
        ProfitResult r = financeDb.calcProfit(currentBatchId, null, null);
        tvExpenseAmount.setText(String.format(Locale.CHINA, "%.2f", r.expense + r.animalHealth));
        tvIncomeAmount.setText(String.format(Locale.CHINA, "%.2f", r.income));
        tvProfitAmount.setText(String.format(Locale.CHINA, "%.2f", r.profit));
        tvProfitAmount.setTextColor(r.profit >= 0 ? 0xFF1E88E5 : 0xFFE53935);
        int credit = financeDb.countUnpaidIncome(currentBatchId, null, null);
        tvCreditCount.setText(credit + " " + getString(R.string.finance_home_credit_unit));
    }
}