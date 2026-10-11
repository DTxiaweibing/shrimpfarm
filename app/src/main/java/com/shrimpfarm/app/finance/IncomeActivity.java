package com.shrimpfarm.app.finance;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.shrimpfarm.app.R;
import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.finance.FinanceDb.FinAccount;
import com.shrimpfarm.app.finance.FinanceDb.FinIncome;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class IncomeActivity extends BaseActivity {

    private FinanceDb financeDb;
    private SharedPreferences prefs;
    private String currentBatchId;
    private List<FinIncome> incomeList = new ArrayList<>();
    private ListView listView;
    private TextView tvTotal;
    private IncomeAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_income);
        financeDb = new FinanceDb(this);
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentBatchId = prefs.getString("current_batch_id", "");
        setupToolbar();
        listView = findViewById(R.id.list_view);
        tvTotal = findViewById(R.id.tv_total);
        adapter = new IncomeAdapter();
        listView.setAdapter(adapter);
        findViewById(R.id.btn_add).setOnClickListener(v ->
                startActivity(new Intent(IncomeActivity.this, IncomeEditActivity.class)));
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
            getSupportActionBar().setTitle(R.string.finance_income);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadData() {
        currentBatchId = prefs.getString("current_batch_id", "");
        incomeList = financeDb.getIncomes(currentBatchId);
        double income = 0, receivable = 0;
        for (FinIncome in : incomeList) {
            if (in.paid) income += in.actualAmount;
            else receivable += in.actualAmount;
        }
        double incomeTotal = FinanceDb.round2(income);
        double recvTotal = FinanceDb.round2(receivable);
        StringBuilder sb = new StringBuilder(getString(R.string.finance_income_total))
                .append(": ").append(round2s(incomeTotal));
        if (recvTotal > 0) {
            sb.append("   ").append(getString(R.string.finance_receivable)).append(": ").append(round2s(recvTotal));
        }
        sb.append("   ").append(getString(R.string.finance_unit_price)).append(": ")
                .append(round2s(totalWeightedUnitPrice()));
        tvTotal.setText(sb.toString());
        adapter.notifyDataSetChanged();
    }

    private String round2s(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(FinanceDb.round2(v));
    }

    private void collectReceivable(final FinIncome in) {
        List<FinAccount> accs = new ArrayList<>();
        for (FinAccount a : financeDb.getPaymentAccounts()) {
            if (a.enabled) accs.add(a);
        }
        if (accs.isEmpty()) {
            Toast.makeText(this, R.string.finance_no_receive_account, Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        final EditText etDate = new EditText(this);
        etDate.setText(today());
        etDate.setFocusable(false);
        etDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, (view, y, m, d) ->
                            etDate.setText(String.format(Locale.CHINA, "%04d/%02d/%02d", y, m + 1, d)),
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        layout.addView(label(R.string.finance_date));
        layout.addView(etDate);

        final Spinner spAccount = new Spinner(this);
        List<String> names = new ArrayList<>();
        for (FinAccount a : accs) names.add(a.name);
        spAccount.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, names));
        layout.addView(label(R.string.finance_receive_account));
        layout.addView(spAccount);

        final EditText etAmount = new EditText(this);
        etAmount.setText(String.valueOf(in.actualAmount));
        etAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(label(R.string.finance_actual));
        layout.addView(etAmount);

        new AlertDialog.Builder(this)
                .setTitle(R.string.finance_receive)
                .setMessage(getString(R.string.finance_confirm_receive, FinanceDb.round2(in.actualAmount)))
                .setView(layout)
                .setPositiveButton(R.string.btn_ok, (d, w) -> {
                    double amt = parseNum(etAmount.getText().toString());
                    long accId = accs.get(Math.max(0, spAccount.getSelectedItemPosition())).id;
                    financeDb.setIncomePaid(in.id, accId, etDate.getText().toString().trim(), amt);
                    Toast.makeText(this, R.string.finance_receive_done, Toast.LENGTH_SHORT).show();
                    loadData();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private TextView label(int res) {
        TextView tv = new TextView(this);
        tv.setText(res);
        tv.setTextColor(0xFF333333);
        tv.setTextSize(14);
        tv.setPadding(0, 10, 0, 4);
        return tv;
    }

    private String today() {
        return new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).format(Calendar.getInstance().getTime());
    }

    private double parseNum(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private double totalWeightedUnitPrice() {
        double totalWeight = 0;
        double totalAmount = 0;
        for (FinIncome in : incomeList) {
            totalWeight += in.weight;
            totalAmount += in.amount;
        }
        if (totalWeight <= 0) return 0;
        return totalAmount / totalWeight;
    }

    private class IncomeAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return incomeList.size();
        }

        @Override
        public Object getItem(int position) {
            return incomeList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return incomeList.get(position).id;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(IncomeActivity.this)
                        .inflate(R.layout.item_finance_income, parent, false);
            }
            final FinIncome in = incomeList.get(position);
            TextView tvDate = convertView.findViewById(R.id.tv_date);
            TextView tvShed = convertView.findViewById(R.id.tv_shed);
            TextView tvAmount = convertView.findViewById(R.id.tv_amount);
            TextView tvInfo = convertView.findViewById(R.id.tv_info);
            TextView tvStatus = convertView.findViewById(R.id.tv_status);
            TextView tvReceive = convertView.findViewById(R.id.tv_receive);

            tvDate.setText(in.date);
            String shed = in.shedNo.isEmpty() ? "" : getString(R.string.finance_shed_no) + " " + in.shedNo;
            if (!in.specs.isEmpty()) shed += (shed.isEmpty() ? "" : "  ") + getString(R.string.finance_specs) + " " + in.specs;
            tvShed.setText(shed);
            tvAmount.setText(String.format(Locale.CHINA, "%.2f", in.actualAmount));
            tvAmount.setTextColor(in.paid ? 0xFF2E7D32 : 0xFFE53935);
            if (in.paid) {
                StringBuilder st = new StringBuilder(getString(R.string.finance_received));
                if (!in.payDate.isEmpty()) st.append(" ").append(in.payDate);
                if (!in.accountName.isEmpty()) st.append(" ").append(in.accountName);
                tvStatus.setText(st.toString());
                tvStatus.setTextColor(0xFF2E7D32);
                tvReceive.setVisibility(View.GONE);
            } else {
                tvStatus.setText(getString(R.string.finance_receivable) + ": ¥"
                        + FinanceDb.round2(in.actualAmount));
                tvStatus.setTextColor(0xFFE53935);
                tvReceive.setVisibility(View.VISIBLE);
                tvReceive.setOnClickListener(v -> collectReceivable(in));
            }

            StringBuilder info = new StringBuilder();
            if (!in.carNo.isEmpty()) info.append(getString(R.string.finance_car_no)).append(" ").append(in.carNo);
            info.append("  ").append(getString(R.string.finance_weight)).append(" ").append(trimNum(in.weight));
            info.append("  ").append(getString(R.string.finance_unit_price)).append(" ").append(trimNum(in.price));
            if (!in.agent.isEmpty()) info.append("  ").append(getString(R.string.finance_agent)).append(" ").append(in.agent);
            if (!in.customer.isEmpty()) info.append("  ").append(getString(R.string.finance_customer)).append(" ").append(in.customer);
            tvInfo.setText(info);

            convertView.setOnClickListener(v -> new AlertDialog.Builder(IncomeActivity.this)
                    .setTitle(in.date)
                    .setItems(new String[]{getString(R.string.finance_edit), getString(R.string.finance_delete)},
                            (dialog, which) -> {
                                if (which == 0) {
                                    Intent intent = new Intent(IncomeActivity.this, IncomeEditActivity.class);
                                    intent.putExtra("incomeId", in.id);
                                    startActivity(intent);
                                } else {
                                    new AlertDialog.Builder(IncomeActivity.this)
                                            .setMessage(R.string.finance_confirm_delete)
                                            .setPositiveButton(R.string.btn_ok, (d, w) -> {
                                                financeDb.deleteIncome(in.id);
                                                loadData();
                                            })
                                            .setNegativeButton(R.string.btn_cancel, null)
                                            .show();
                                }
                            })
                    .show());
            return convertView;
        }
    }

    private String trimNum(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }
}