package com.shrimpfarm.app.finance;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.finance.FinanceDb.FinAccount;
import com.shrimpfarm.app.finance.FinanceDb.FinIncome;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PickingFeeActivity extends BaseActivity {

    private FinanceDb financeDb;
    private SharedPreferences prefs;
    private String currentBatchId;
    private List<FinIncome> rows = new ArrayList<>();
    private List<FinAccount> payAccounts = new ArrayList<>();
    private Set<Long> checked = new HashSet<>();
    private boolean batchMode = false;
    private FeeAdapter adapter;
    private TextView tvTotal;
    private Button btnBatch, btnSettle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_picking_fee);
        financeDb = new FinanceDb(this);
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentBatchId = prefs.getString("current_batch_id", "");
        setupToolbar();
        tvTotal = findViewById(R.id.tv_total);
        btnBatch = findViewById(R.id.btn_batch);
        btnSettle = findViewById(R.id.btn_settle);
        ListView listView = findViewById(R.id.list_view);
        adapter = new FeeAdapter();
        listView.setAdapter(adapter);
        btnBatch.setOnClickListener(v -> toggleBatchMode());
        btnSettle.setOnClickListener(v -> settleChecked());
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
            getSupportActionBar().setTitle(R.string.finance_picking_fee);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadData() {
        currentBatchId = prefs.getString("current_batch_id", "");
        rows.clear();
        for (FinIncome in : financeDb.getIncomes(currentBatchId)) {
            if (in.netFee > 0) rows.add(in);
        }
        tvTotal.setText(getString(R.string.finance_picking_due) + ": "
                + FinanceDb.round2(financeDb.sumUnpaidNetFee(currentBatchId, null, null))
                + "  " + getString(R.string.finance_picking_settled) + ": "
                + FinanceDb.round2(financeDb.sumPaidNetFee(currentBatchId, null, null)));
        updateButtons();
        adapter.notifyDataSetChanged();
    }

    private void toggleBatchMode() {
        if (batchMode) {
            batchMode = false;
            checked.clear();
        } else {
            boolean any = false;
            for (FinIncome in : rows) {
                if (!in.netFeePaid) any = true;
            }
            if (!any) {
                Toast.makeText(this, R.string.finance_no_unpaid_picking, Toast.LENGTH_SHORT).show();
                return;
            }
            batchMode = true;
            checked.clear();
        }
        updateButtons();
        adapter.notifyDataSetChanged();
    }

    private void updateButtons() {
        if (batchMode) {
            btnBatch.setText(R.string.finance_picking_cancel);
            btnSettle.setVisibility(View.VISIBLE);
            btnSettle.setText(getString(R.string.finance_picking_batch_settle, checked.size()));
            btnSettle.setAlpha(checked.isEmpty() ? 0.5f : 1f);
        } else {
            btnBatch.setText(R.string.finance_picking_batch);
            btnSettle.setVisibility(View.GONE);
        }
    }

    private void settleChecked() {
        List<Long> ids = new ArrayList<>(checked);
        if (ids.isEmpty()) {
            Toast.makeText(this, R.string.finance_no_unpaid_picking, Toast.LENGTH_SHORT).show();
            return;
        }
        showSettleDialog(ids);
    }

    private void showSettleDialog(final List<Long> ids) {
        double total = 0;
        for (Long id : ids) {
            for (FinIncome in : rows) {
                if (in.id == id) total += in.netFee;
            }
        }
        payAccounts.clear();
        for (FinAccount a : financeDb.getPaymentAccounts()) {
            if (a.enabled) payAccounts.add(a);
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        final EditText etDate = new EditText(this);
        etDate.setText(today());
        etDate.setFocusable(false);
        etDate.setOnClickListener(v -> pickDate(etDate));
        etDate.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) pickDate(etDate);
        });
        layout.addView(label(R.string.finance_date));
        layout.addView(etDate);

        final Spinner spAccount = new Spinner(this);
        List<String> names = new ArrayList<>();
        for (FinAccount a : payAccounts) names.add(a.name);
        spAccount.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, names));
        layout.addView(label(R.string.finance_account_pay));
        layout.addView(spAccount);

        layout.addView(label(R.string.finance_amount));
        TextView tvAmount = new TextView(this);
        tvAmount.setText("¥" + FinanceDb.round2(total));
        tvAmount.setTextColor(0xFF333333);
        tvAmount.setTextSize(15);
        tvAmount.setPadding(8, 4, 8, 4);
        layout.addView(tvAmount);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.finance_confirm_picking_pay, FinanceDb.round2(total)))
                .setMessage(getString(R.string.finance_picking_due) + " " + ids.size()
                        + " " + getString(R.string.finance_vehicle))
                .setView(layout)
                .setPositiveButton(R.string.btn_ok, (d, w) -> {
                    long accountId = 0;
                    if (!payAccounts.isEmpty()) {
                        accountId = payAccounts.get(Math.max(0, spAccount.getSelectedItemPosition())).id;
                    }
                    financeDb.setIncomeNetFeePaid(ids, accountId, etDate.getText().toString().trim());
                    Toast.makeText(this, R.string.finance_picking_pay_done, Toast.LENGTH_SHORT).show();
                    if (batchMode) {
                        batchMode = false;
                        checked.clear();
                    }
                    loadData();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private TextView label(int res) {
        TextView tv = new TextView(this);
        tv.setText(res);
        tv.setTextColor(0xFF333333);
        tv.setTextSize(13);
        tv.setPadding(0, 8, 0, 2);
        return tv;
    }

    private String today() {
        return new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(new Date());
    }

    private void pickDate(EditText et) {
        Calendar c = Calendar.getInstance();
        DatePickerDialog d = new DatePickerDialog(this,
                (view, year, month, day) -> et.setText(year + "/" + (month + 1) + "/" + day),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        d.show();
    }

    private class FeeAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return rows.get(position).id;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(PickingFeeActivity.this)
                        .inflate(R.layout.item_finance_picking_fee, parent, false);
            }
            final FinIncome in = rows.get(position);
            TextView tvDate = convertView.findViewById(R.id.tv_date);
            TextView tvCar = convertView.findViewById(R.id.tv_car);
            TextView tvAmount = convertView.findViewById(R.id.tv_amount);
            TextView tvInfo = convertView.findViewById(R.id.tv_info);
            TextView tvStatus = convertView.findViewById(R.id.tv_status);
            TextView tvPay = convertView.findViewById(R.id.tv_pay);

            tvDate.setText(in.date);
            StringBuilder car = new StringBuilder();
            if (in.carNo != null && !in.carNo.isEmpty()) car.append(in.carNo);
            if (in.shedNo != null && !in.shedNo.isEmpty()) {
                if (car.length() > 0) car.append(" · ");
                car.append("棚").append(in.shedNo);
            }
            tvCar.setText(car.toString());
            tvAmount.setText("¥" + FinanceDb.round2(in.netFee));
            tvInfo.setText(getString(R.string.finance_weight) + ": "
                    + FinanceDb.round2(in.weight) + " · " + getString(R.string.finance_amount) + ": "
                    + FinanceDb.round2(in.amount));
            if (in.netFeePaid) {
                tvStatus.setText(getString(R.string.finance_picking_settled)
                        + (in.netFeeAccountName.isEmpty() ? "" : " · " + in.netFeeAccountName)
                        + (in.netFeePayDate.isEmpty() ? "" : " @" + in.netFeePayDate));
                tvStatus.setTextColor(0xFF2E7D32);
                tvPay.setVisibility(View.GONE);
            } else {
                tvStatus.setText(getString(R.string.finance_picking_due) + " ¥"
                        + FinanceDb.round2(in.netFee));
                tvStatus.setTextColor(0xFFE53935);
                tvPay.setVisibility(View.VISIBLE);
                tvPay.setOnClickListener(v -> showSettleDialog(single(in.id)));
            }
            convertView.setBackgroundColor(checked.contains(in.id) ? 0xFFDDEFDD : 0xFFFFFFFF);
            convertView.setOnClickListener(v -> {
                if (!batchMode) {
                    if (!in.netFeePaid) showSettleDialog(single(in.id));
                    return;
                }
                if (in.netFeePaid) return;
                if (checked.contains(in.id)) checked.remove(in.id);
                else checked.add(in.id);
                updateButtons();
                adapter.notifyDataSetChanged();
            });
            return convertView;
        }
    }

    private List<Long> single(long id) {
        List<Long> l = new ArrayList<>();
        l.add(id);
        return l;
    }
}