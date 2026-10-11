package com.shrimpfarm.app.erp;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
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
import com.shrimpfarm.app.finance.FinanceDb;
import com.shrimpfarm.app.finance.FinanceDb.FinAccount;
import com.shrimpfarm.app.finance.FinanceDb.FinContactAccount;
import com.shrimpfarm.app.finance.FinanceDb.FinSalary;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SalaryActivity extends BaseActivity {

    private FinanceDb financeDb;
    private ListView listView;
    private SalaryAdapter adapter;
    private List<FinSalary> salaries = new ArrayList<>();
    private int year, month;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_salary);
        financeDb = new FinanceDb(this);
        Calendar c = Calendar.getInstance();
        year = c.get(Calendar.YEAR);
        month = c.get(Calendar.MONTH) + 1;
        setupToolbar();
        listView = findViewById(R.id.list_view);
        adapter = new SalaryAdapter();
        listView.setAdapter(adapter);
        findViewById(R.id.btn_prev).setOnClickListener(v -> {
            month--;
            if (month < 1) { month = 12; year--; }
            loadData();
        });
        findViewById(R.id.btn_next).setOnClickListener(v -> {
            month++;
            if (month > 12) { month = 1; year++; }
            loadData();
        });
        findViewById(R.id.tv_month).setOnClickListener(v -> pickMonth());
        findViewById(R.id.btn_batch_generate).setOnClickListener(v -> confirmGenerate());
        findViewById(R.id.btn_record_advance).setOnClickListener(v -> recordAdvance());
        findViewById(R.id.btn_batch_pay).setOnClickListener(v -> batchPay());
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (financeDb != null) loadData();
    }

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_home;
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.erp_salary_sheet);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void pickMonth() {
        new DatePickerDialog(this, (view, y, m, d) -> {
            year = y;
            month = m + 1;
            loadData();
        }, year, month - 1, 1).show();
    }

    private void loadData() {
        TextView tvMonth = findViewById(R.id.tv_month);
        tvMonth.setText(String.format(Locale.CHINA, "%d-%02d", year, month));
        salaries = financeDb.getSalaries(year, month);
        adapter.notifyDataSetChanged();
    }

    private void confirmGenerate() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.erp_generate_salary)
                .setMessage(R.string.erp_batch_generate_confirm)
                .setPositiveButton(R.string.btn_ok, (d, w) -> doGenerate())
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void doGenerate() {
        List<FinanceDb.FinSupplier> emps = financeDb.getSuppliersByType(FinanceDb.PERSON_EMPLOYEE);
        int count = 0;
        for (FinanceDb.FinSupplier s : emps) {
            FinSalary one = financeDb.generateSalary(s.id, year, month);
            if (one != null) count++;
        }
        Toast.makeText(this, getString(R.string.erp_salary_generated) + " (" + count + ")",
                Toast.LENGTH_SHORT).show();
        loadData();
    }

    private void batchPay() {
        List<FinSalary> unpaid = unpaid();
        if (unpaid.isEmpty()) {
            Toast.makeText(this, R.string.erp_already_paid, Toast.LENGTH_SHORT).show();
            return;
        }
        showPayDialog(unpaid);
    }

    private List<FinSalary> unpaid() {
        List<FinSalary> list = new ArrayList<>();
        for (FinSalary s : salaries) if (!s.paid) list.add(s);
        return list;
    }

    private void recordAdvance() {
        final List<FinanceDb.FinSupplier> emps = financeDb.getSuppliersByType(FinanceDb.PERSON_EMPLOYEE);
        final List<FinanceDb.FinSupplier> enabled = new ArrayList<>();
        for (FinanceDb.FinSupplier e : emps) if (e.enabled) enabled.add(e);
        if (enabled.isEmpty()) {
            Toast.makeText(this, R.string.erp_no_employee, Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        final Spinner spEmp = new Spinner(this);
        List<String> names = new ArrayList<>();
        for (FinanceDb.FinSupplier e : enabled) names.add(e.name);
        spEmp.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, names));
        layout.addView(label(R.string.finance_person_employee));
        layout.addView(spEmp);

        final EditText etDate = new EditText(this);
        etDate.setText(today());
        etDate.setFocusable(false);
        etDate.setOnClickListener(v -> {
            int[] p = parseDate(etDate.getText().toString());
            new DatePickerDialog(this, (view, y, m, d) ->
                            etDate.setText(String.format(Locale.CHINA, "%04d/%02d/%02d", y, m + 1, d)),
                    p[0], p[1] - 1, p[2]).show();
        });
        layout.addView(label(R.string.erp_advance_date));
        layout.addView(etDate);

        final EditText etAmount = new EditText(this);
        etAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etAmount.setHint(R.string.erp_advance_amount);
        layout.addView(label(R.string.erp_advance_amount));
        layout.addView(etAmount);

        final EditText etRemark = new EditText(this);
        etRemark.setHint(R.string.finance_remark);
        layout.addView(label(R.string.finance_remark));
        layout.addView(etRemark);

        new AlertDialog.Builder(this)
                .setTitle(R.string.erp_advance_record)
                .setMessage(R.string.erp_advance_effect)
                .setView(layout)
                .setPositiveButton(R.string.btn_ok, (d, w) -> {
                    double amount = parseAmount(etAmount.getText().toString());
                    if (amount <= 0) {
                        Toast.makeText(this, R.string.erp_advance_wrong, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int idx = spEmp.getSelectedItemPosition();
                    final FinanceDb.FinSupplier emp = enabled.get(idx);
                    final String date = etDate.getText().toString().trim();
                    final String remark = etRemark.getText().toString().trim();
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.erp_advance_record)
                            .setMessage(getString(R.string.erp_confirm_advance, emp.name,
                                    FinanceDb.round2(amount)))
                            .setPositiveButton(R.string.btn_ok, (d2, w2) -> {
                                financeDb.recordAdvance(emp.id, date, amount, remark, prefsBatchId());
                                Toast.makeText(this, R.string.erp_advance_saved, Toast.LENGTH_SHORT).show();
                                loadData();
                            })
                            .setNegativeButton(R.string.btn_cancel, null)
                            .show();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private double parseAmount(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void showPayDialog(final List<FinSalary> target) {
        double due = 0, adv = 0;
        for (FinSalary s : target) {
            if (s.paid) continue;
            due += s.actualSalary;
            adv += Math.min(s.actualSalary, s.advance);
        }
        double net = FinanceDb.round2(due - adv);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        final Spinner spAccount = new Spinner(this);
        final List<FinAccount> enabled = new ArrayList<>();
        for (FinAccount a : financeDb.getPaymentAccounts()) if (a.enabled) enabled.add(a);
        if (enabled.isEmpty()) {
            Toast.makeText(this, R.string.finance_pick_account, Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> names = new ArrayList<>();
        for (FinAccount a : enabled) names.add(a.name);
        spAccount.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, names));
        layout.addView(label(R.string.finance_pick_account));
        layout.addView(spAccount);

        final Spinner spReceive;
        final List<Long> receiveIds = new ArrayList<>();
        FinSalary first = target.get(0);
        boolean single = true;
        for (FinSalary s : target) {
            if (s.employeeId != first.employeeId) {
                single = false;
                break;
            }
        }
        if (single) {
            List<FinContactAccount> accs = new ArrayList<>();
            for (FinContactAccount a : financeDb.getContactAccounts(first.employeeId)) {
                if (a.enabled) accs.add(a);
            }
            spReceive = new Spinner(this);
            List<String> rn = new ArrayList<>();
            rn.add(getString(R.string.finance_no_receive_account));
            receiveIds.add(0L);
            for (FinContactAccount a : accs) {
                rn.add(a.displayName());
                receiveIds.add(a.id);
            }
            spReceive.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item, rn));
            layout.addView(label(R.string.finance_receive_account));
            layout.addView(spReceive);
        } else {
            spReceive = null;
        }

        final EditText etDate = new EditText(this);
        etDate.setText(today());
        etDate.setFocusable(false);
        etDate.setOnClickListener(v -> {
            int[] p = parseDate(etDate.getText().toString());
            new DatePickerDialog(this, (view, y, m, d) ->
                            etDate.setText(String.format(Locale.CHINA, "%04d/%02d/%02d", y, m + 1, d)),
                    p[0], p[1] - 1, p[2]).show();
        });
        layout.addView(label(R.string.finance_date));
        layout.addView(etDate);

        new AlertDialog.Builder(this)
                .setTitle(R.string.erp_pay_now)
                .setMessage(String.format(getString(R.string.erp_confirm_pay_net),
                        fmt(net), fmt(due), fmt(adv)))
                .setView(layout)
                .setPositiveButton(R.string.btn_ok, (d, w) -> {
                    long accountId = enabled.get(spAccount.getSelectedItemPosition()).id;
                    long receiveId = spReceive == null ? 0 : receiveIds.get(spReceive.getSelectedItemPosition());
                    String payDate = etDate.getText().toString().trim();
                    if (payDate.isEmpty()) return;
                    String batchId = prefsBatchId();
                    for (FinSalary s : target) {
                        long rid = receiveId;
                        if (spReceive == null) {
                            for (FinContactAccount a : financeDb.getContactAccounts(s.employeeId)) {
                                if (a.enabled) {
                                    rid = a.id;
                                    break;
                                }
                            }
                        }
                        financeDb.paySalary(s.id, batchId, accountId, rid, payDate);
                    }
                    Toast.makeText(this, R.string.erp_salary_paid, Toast.LENGTH_SHORT).show();
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

    private int[] parseDate(String date) {
        try {
            String[] p = date.split("/");
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
        } catch (Exception e) {
            Calendar c = Calendar.getInstance();
            return new int[]{c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)};
        }
    }

    private String today() {
        return new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA)
                .format(Calendar.getInstance().getTime());
    }

    private String prefsBatchId() {
        return getSharedPreferences("app_prefs", MODE_PRIVATE).getString("current_batch_id", "");
    }

    private String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.CHINA, "%.1f", v);
    }

    private class SalaryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return salaries.size();
        }

        @Override
        public Object getItem(int position) {
            return salaries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return salaries.get(position).id;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(SalaryActivity.this)
                        .inflate(R.layout.item_salary, parent, false);
            }
            final FinSalary s = salaries.get(position);
            TextView tvName = convertView.findViewById(R.id.tv_name);
            TextView tvStatus = convertView.findViewById(R.id.tv_status);
            TextView tvInfo = convertView.findViewById(R.id.tv_info);
            TextView tvAmounts = convertView.findViewById(R.id.tv_amounts);
            TextView tvPay = convertView.findViewById(R.id.tv_pay);

            tvName.setText(s.employeeName);
            if (s.paid) {
                String status;
                if (s.accountName.isEmpty()) {
                    status = getString(R.string.erp_paid) + (s.payDate.isEmpty() ? "" : " " + s.payDate);
                } else {
                    status = getString(R.string.erp_paid_with, s.payDate, s.accountName);
                }
                if (!s.receiveAccountName.isEmpty()) {
                    status += "  " + getString(R.string.erp_paid_to, s.receiveAccountName);
                }
                tvStatus.setText(status);
                tvStatus.setTextColor(0xFF4CAF50);
                tvPay.setVisibility(View.GONE);
            } else {
                tvStatus.setText(R.string.erp_not_paid);
                tvStatus.setTextColor(0xFFE53935);
                tvPay.setVisibility(View.VISIBLE);
                tvPay.setOnClickListener(v -> {
                    List<FinSalary> one = new ArrayList<>();
                    one.add(s);
                    showPayDialog(one);
                });
            }
            tvInfo.setText(getString(R.string.erp_attend_days) + ": " + fmt(s.workDays + s.personalDays + s.sickDays + s.absentDays)
                    + "  " + getString(R.string.erp_leave_personal) + ":" + fmt(s.personalDays)
                    + "  " + getString(R.string.erp_leave_sick) + ":" + fmt(s.sickDays)
                    + "  " + getString(R.string.erp_work_days) + ":" + fmt(s.workDays));
            String amounts = getString(R.string.erp_base_salary) + " ¥" + FinanceDb.round2(s.baseSalary);
            double base = s.actualSalary;
            if (s.paid && s.deduction > 0) {
                amounts += "  " + getString(R.string.erp_advance_deduct_short) + " ¥" + FinanceDb.round2(s.deduction)
                        + "  " + getString(R.string.erp_actual_salary) + " ¥" + FinanceDb.round2(s.actualSalary);
            } else if (!s.paid && s.advance > 0) {
                double net = FinanceDb.round2(base - Math.min(base, s.advance));
                amounts += "  " + getString(R.string.erp_advance_info) + " ¥" + FinanceDb.round2(s.advance)
                        + "  " + getString(R.string.erp_expected_net) + " ¥" + FinanceDb.round2(net);
            } else {
                amounts += "  " + getString(R.string.erp_actual_salary) + " ¥" + FinanceDb.round2(s.actualSalary);
            }
            tvAmounts.setText(amounts);
            return convertView;
        }
    }
}