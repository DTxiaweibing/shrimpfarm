package com.shrimpfarm.app.finance;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
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

import androidx.appcompat.widget.Toolbar;

import com.shrimpfarm.app.R;
import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.finance.FinanceDb.FinExpense;
import com.shrimpfarm.app.finance.FinanceDb.FinSeed;
import com.shrimpfarm.app.finance.FinanceDb.FinSupplier;
import com.shrimpfarm.app.finance.FinanceDb.FinWage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class FinanceExpenseActivity extends BaseActivity {

    private static final int TAB_LIFE = 0;
    private static final int TAB_DAILY = 1;
    private static final int TAB_BREEDING = 2;
    private static final int[] TAB_CATEGORY = {FinanceDb.CAT_LIFE, FinanceDb.CAT_DAILY, FinanceDb.CAT_BREEDING};

    private static final int SUB_EXPENSE = 0;
    private static final int SUB_SEED = 1;
    private static final int SUB_TICKET = 2;
    private static final int SUB_WAGE = 3;
    private static final int SUB_PICKING = 4;

    private FinanceDb financeDb;
    private SharedPreferences prefs;
    private String currentBatchId;
    private int currentTab = TAB_LIFE;
    private int currentSub = SUB_EXPENSE;
    private ListView listView;
    private TextView tvTotal;
    private LinearLayout subBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_expense);
        financeDb = new FinanceDb(this);
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentBatchId = prefs.getString("current_batch_id", "");
        setupToolbar();
        listView = findViewById(R.id.list_view);
        tvTotal = findViewById(R.id.tv_total);
        subBar = findViewById(R.id.sub_bar);
        setupTabs();
        findViewById(R.id.btn_add).setOnClickListener(v -> onAdd());
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
            getSupportActionBar().setTitle(R.string.finance_expense);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupTabs() {
        TextView[] tabs = {findViewById(R.id.tab_life), findViewById(R.id.tab_daily),
                findViewById(R.id.tab_breeding)};
        final TextView[] subs = {findViewById(R.id.sub_exp), findViewById(R.id.sub_seed),
                findViewById(R.id.sub_ticket), findViewById(R.id.sub_wage),
                findViewById(R.id.sub_picking)};
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            tabs[i].setOnClickListener(v -> {
                currentTab = index;
                updateTabStyles(tabs, index);
                subBar.setVisibility(index == TAB_BREEDING ? View.VISIBLE : View.GONE);
                currentSub = SUB_EXPENSE;
                updateTabStyles(subs, currentSub);
                loadData();
            });
        }
        for (int i = 0; i < subs.length; i++) {
            final int index = i;
            subs[i].setOnClickListener(v -> {
                if (index == SUB_PICKING) {
                    startActivity(new Intent(this, PickingFeeActivity.class));
                    return;
                }
                currentSub = index;
                updateTabStyles(subs, index);
                loadData();
            });
        }
        updateTabStyles(tabs, currentTab);
        subBar.setVisibility(View.GONE);
        updateTabStyles(subs, currentSub);
    }

    private void updateTabStyles(TextView[] tabs, int selected) {
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setTextColor(i == selected ? 0xFF4CAF50 : 0xFF666666);
            tabs[i].setBackgroundColor(0xFFFFFFFF);
            tabs[i].setTextSize(i == selected ? 15 : 13);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void onAdd() {
        if (currentTab == TAB_BREEDING && currentSub == SUB_TICKET) {
            startActivity(new Intent(this, TicketEditActivity.class));
        } else if (currentTab == TAB_BREEDING && currentSub == SUB_SEED) {
            showSeedDialog(null);
        } else if (currentTab == TAB_BREEDING && currentSub == SUB_WAGE) {
            showWageDialog(null);
        } else {
            showExpenseDialog(null);
        }
    }

    private void loadData() {
        currentBatchId = prefs.getString("current_batch_id", "");
        if (currentTab == TAB_BREEDING) {
            if (currentSub == SUB_SEED) {
                List<FinSeed> seeds = financeDb.getSeeds(currentBatchId);
                double total = 0;
                for (FinSeed s : seeds) total += s.actualAmount;
                tvTotal.setText(getString(R.string.finance_total) + ": " + FinanceDb.round2(total));
                listView.setAdapter(new SeedAdapter(seeds));
            } else if (currentSub == SUB_TICKET) {
                List<FinanceDb.FinTicket> tickets = financeDb.getTickets(currentBatchId);
                tvTotal.setText(getString(R.string.finance_total) + ": "
                        + FinanceDb.round2(sumTickets(tickets)));
                listView.setAdapter(new TicketAdapter(tickets));
            } else if (currentSub == SUB_WAGE) {
                List<FinWage> wages = financeDb.getWages(currentBatchId);
                double total = 0;
                for (FinWage w : wages) total += w.amount;
                tvTotal.setText(getString(R.string.finance_total) + ": " + FinanceDb.round2(total));
                listView.setAdapter(new WageAdapter(wages));
            } else {
                loadExpenses();
            }
        } else {
            loadExpenses();
        }
    }

    private void loadExpenses() {
        int category = TAB_CATEGORY[currentTab];
        List<FinExpense> list = financeDb.getExpenses(currentBatchId, category);
        double total = 0;
        for (FinExpense e : list) total += e.amount;
        tvTotal.setText(getString(R.string.finance_total) + ": " + FinanceDb.round2(total));
        listView.setAdapter(new ExpenseAdapter(list));
    }

    private double sumTickets(List<FinanceDb.FinTicket> tickets) {
        double sum = 0;
        for (FinanceDb.FinTicket t : tickets) sum += t.totalAmount;
        return sum;
    }

    private String subName(int subCategory) {
        switch (subCategory) {
            case FinanceDb.SUB_REPAIR: return getString(R.string.finance_sub_repair);
            case FinanceDb.SUB_POND: return getString(R.string.finance_sub_pond);
            case FinanceDb.SUB_SEED: return getString(R.string.finance_sub_seed);
            case FinanceDb.SUB_ELECTRIC: return getString(R.string.finance_sub_electric);
            case FinanceDb.SUB_LABOR: return getString(R.string.finance_sub_labor);
            case FinanceDb.SUB_DEPRECIATION: return getString(R.string.finance_sub_depreciation);
            default: return "";
        }
    }

    private void showExpenseDialog(final FinExpense expense) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        final Spinner spSub = new Spinner(this);
        final List<String> subNames = new ArrayList<>();
        final List<Integer> subValues = new ArrayList<>();
        if (currentTab == TAB_BREEDING) {
            subNames.add(getString(R.string.finance_sub_repair));
            subValues.add(FinanceDb.SUB_REPAIR);
            subNames.add(getString(R.string.finance_sub_pond));
            subValues.add(FinanceDb.SUB_POND);
            subNames.add(getString(R.string.finance_sub_electric));
            subValues.add(FinanceDb.SUB_ELECTRIC);
            subNames.add(getString(R.string.finance_sub_depreciation));
            subValues.add(FinanceDb.SUB_DEPRECIATION);
        } else {
            subNames.add(getString(R.string.finance_expense));
            subValues.add(0);
            spSub.setEnabled(false);
            spSub.setVisibility(View.GONE);
        }
        spSub.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, subNames));

        final EditText etAmount = new EditText(this);
        etAmount.setHint(R.string.finance_amount);
        etAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        final EditText etRemark = new EditText(this);
        etRemark.setHint(R.string.finance_remark);

        layout.addView(spSub);
        layout.addView(etAmount);
        layout.addView(etRemark);

        final EditText etDate = new EditText(this);
        etDate.setHint(getString(R.string.finance_date) + " " + getString(R.string.finance_today));
        etDate.setText(expense != null ? expense.date
                : new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Calendar.getInstance().getTime()));
        etDate.setFocusable(false);
        etDate.setOnClickListener(v -> pickDate(etDate));
        layout.addView(etDate, 0);

        if (expense != null) {
            etAmount.setText(String.valueOf(expense.amount));
            etRemark.setText(expense.remark);
            if (currentTab == TAB_BREEDING) {
                for (int i = 0; i < subValues.size(); i++) {
                    if (subValues.get(i) == expense.subCategory) spSub.setSelection(i);
                }
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(expense == null ? R.string.finance_expense_new : R.string.finance_edit);
        builder.setView(layout);
        builder.setPositiveButton(R.string.finance_save, (d, w) -> {
            double amount;
            try {
                amount = Double.parseDouble(etAmount.getText().toString().trim());
            } catch (Exception ex) {
                amount = 0;
            }
            String date = etDate.getText().toString().trim();
            if (date.isEmpty() || amount <= 0) return;
            int category = TAB_CATEGORY[currentTab];
            int sub = currentTab == TAB_BREEDING ? subValues.get(spSub.getSelectedItemPosition()) : 0;
            String remark = etRemark.getText().toString().trim();
            if (expense == null) {
                financeDb.insertExpense(currentBatchId, category, sub, amount, date, remark);
            } else {
                financeDb.deleteExpense(expense.id);
                financeDb.insertExpense(currentBatchId, category, sub, amount, date, remark);
            }
            loadData();
        });
        builder.setNegativeButton(R.string.btn_cancel, null);
        builder.show();
    }

    private void showSeedDialog(final FinSeed seed) {
        final List<FinSupplier> suppliers = financeDb.getSuppliersByType(FinanceDb.PERSON_SUPPLIER);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        final Spinner spSupplier = new Spinner(this);
        List<String> supplierNames = new ArrayList<>();
        for (FinSupplier s : suppliers) supplierNames.add(s.name);
        if (supplierNames.isEmpty()) supplierNames.add(getString(R.string.finance_no_supplier));
        spSupplier.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, supplierNames));

        final EditText etQuantity = new EditText(this);
        etQuantity.setHint(getString(R.string.finance_seed_count));
        etQuantity.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        final EditText etUnitPrice = new EditText(this);
        etUnitPrice.setHint(getString(R.string.finance_unit_price));
        etUnitPrice.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        final TextView tvTotalPreview = new TextView(this);
        tvTotalPreview.setText(getString(R.string.finance_total_price) + ": 0");
        tvTotalPreview.setTextColor(0xFF4CAF50);
        tvTotalPreview.setTextSize(15);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                double q = parse(etQuantity.getText().toString());
                double p = parse(etUnitPrice.getText().toString());
                tvTotalPreview.setText(getString(R.string.finance_total_price) + ": "
                        + FinanceDb.round2(q * p));
            }
        };
        etQuantity.addTextChangedListener(watcher);
        etUnitPrice.addTextChangedListener(watcher);

        final EditText etActual = new EditText(this);
        etActual.setHint(getString(R.string.finance_actual_pay));
        etActual.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        final EditText etRemark = new EditText(this);
        etRemark.setHint(R.string.finance_remark);

        layout.addView(spSupplier);
        layout.addView(etQuantity);
        layout.addView(etUnitPrice);
        layout.addView(tvTotalPreview);
        layout.addView(etActual);
        layout.addView(etRemark);

        final EditText etDate = new EditText(this);
        etDate.setHint(getString(R.string.finance_date) + " " + getString(R.string.finance_today));
        etDate.setText(seed != null ? seed.date
                : new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Calendar.getInstance().getTime()));
        etDate.setFocusable(false);
        etDate.setOnClickListener(v -> pickDate(etDate));
        layout.addView(etDate, 0);

        if (seed != null) {
            etQuantity.setText(String.valueOf(seed.quantity));
            etUnitPrice.setText(String.valueOf(seed.unitPrice));
            etActual.setText(String.valueOf(seed.actualAmount));
            etRemark.setText(seed.remark);
            for (int i = 0; i < suppliers.size(); i++) {
                if (suppliers.get(i).id == seed.supplierId) spSupplier.setSelection(i);
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(seed == null ? R.string.finance_seed_list : R.string.finance_edit);
        builder.setView(layout);
        builder.setPositiveButton(R.string.finance_save, (d, w) -> {
            double qty = parse(etQuantity.getText().toString());
            double price = parse(etUnitPrice.getText().toString());
            double actual = parse(etActual.getText().toString());
            String date = etDate.getText().toString().trim();
            if (date.isEmpty() || suppliers.isEmpty() || qty <= 0 || price <= 0) return;
            long supplierId = suppliers.get(spSupplier.getSelectedItemPosition()).id;
            String remark = etRemark.getText().toString().trim();
            if (seed == null) {
                financeDb.insertSeed(currentBatchId, supplierId, date, qty, price, actual, remark);
            } else {
                seed.supplierId = supplierId;
                seed.date = date;
                seed.quantity = qty;
                seed.unitPrice = price;
                seed.actualAmount = actual;
                seed.remark = remark;
                financeDb.updateSeed(seed);
            }
            loadData();
        });
        builder.setNegativeButton(R.string.btn_cancel, null);
        builder.show();
    }

    private void showWageDialog(final FinWage wage) {
        final List<FinSupplier> employees = financeDb.getSuppliersByType(FinanceDb.PERSON_EMPLOYEE);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        final Spinner spEmployee = new Spinner(this);
        final List<String> employeeNames = new ArrayList<>();
        for (FinSupplier s : employees) {
            employeeNames.add(s.name + (s.dailyWage > 0 ? " (" + s.dailyWage + "/天)" : ""));
        }
        employeeNames.add(getString(R.string.finance_custom_input));
        spEmployee.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, employeeNames));

        final EditText etName = new EditText(this);
        etName.setHint(R.string.finance_input_name);
        etName.setVisibility(employees.isEmpty() ? View.VISIBLE : View.GONE);
        spEmployee.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int position, long id) {
                if (position < employees.size()) {
                    etName.setText(employees.get(position).name);
                    etName.setVisibility(View.GONE);
                } else {
                    etName.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        final EditText etAmount = new EditText(this);
        etAmount.setHint(R.string.finance_amount);
        etAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        final EditText etRemark = new EditText(this);
        etRemark.setHint(R.string.finance_remark);

        layout.addView(spEmployee);
        layout.addView(etName);
        layout.addView(etAmount);
        layout.addView(etRemark);

        final EditText etDate = new EditText(this);
        etDate.setHint(getString(R.string.finance_date) + " " + getString(R.string.finance_today));
        etDate.setText(wage != null ? wage.date
                : new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Calendar.getInstance().getTime()));
        etDate.setFocusable(false);
        etDate.setOnClickListener(v -> pickDate(etDate));
        layout.addView(etDate, 0);

        final long[] customId = {0};
        if (wage != null) {
            etName.setText(wage.payee);
            etAmount.setText(String.valueOf(wage.amount));
            etRemark.setText(wage.remark);
            boolean found = false;
            for (int i = 0; i < employees.size(); i++) {
                if (employees.get(i).id == wage.employeeId) {
                    spEmployee.setSelection(i);
                    found = true;
                    break;
                }
            }
            if (!found) {
                customId[0] = wage.employeeId;
                spEmployee.setSelection(employeeNames.size() - 1);
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(wage == null ? R.string.finance_wage_list : R.string.finance_edit);
        builder.setView(layout);
        builder.setPositiveButton(R.string.finance_save, (d, w) -> {
            double amount = parse(etAmount.getText().toString());
            String date = etDate.getText().toString().trim();
            if (date.isEmpty() || amount <= 0) return;
            int pos = spEmployee.getSelectedItemPosition();
            long employeeId;
            String payee;
            if (pos < employees.size()) {
                employeeId = employees.get(pos).id;
                payee = employees.get(pos).name;
            } else {
                employeeId = 0;
                payee = etName.getText().toString().trim();
                if (payee.isEmpty()) return;
            }
            String remark = etRemark.getText().toString().trim();
            if (wage == null) {
                financeDb.insertWage(currentBatchId, employeeId, payee, amount, date, remark);
            } else {
                wage.employeeId = employeeId;
                wage.payee = payee;
                wage.amount = amount;
                wage.date = date;
                wage.remark = remark;
                financeDb.updateWage(wage);
            }
            loadData();
        });
        builder.setNegativeButton(R.string.btn_cancel, null);
        builder.show();
    }

    private double parse(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void pickDate(final EditText et) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) ->
                        et.setText(String.format(Locale.CHINA, "%d/%02d/%02d", year, month + 1, dayOfMonth)),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showEditMenu(final String title, final Runnable onEdit, final Runnable onDelete) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(new String[]{getString(R.string.finance_edit), getString(R.string.finance_delete)},
                        (dialog, which) -> {
                            if (which == 0) onEdit.run();
                            else new AlertDialog.Builder(this)
                                    .setMessage(R.string.finance_confirm_delete)
                                    .setPositiveButton(R.string.btn_ok, (d, w) -> onDelete.run())
                                    .setNegativeButton(R.string.btn_cancel, null)
                                    .show();
                        })
                .show();
    }

    private class ExpenseAdapter extends BaseAdapter {
        private final List<FinExpense> list;

        ExpenseAdapter(List<FinExpense> list) {
            this.list = list;
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int position) {
            return list.get(position);
        }

        @Override
        public long getItemId(int position) {
            return list.get(position).id;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(FinanceExpenseActivity.this)
                        .inflate(R.layout.item_finance_expense, parent, false);
            }
            final FinExpense e = list.get(position);
            TextView tvDate = convertView.findViewById(R.id.tv_date);
            TextView tvSub = convertView.findViewById(R.id.tv_sub);
            TextView tvAmount = convertView.findViewById(R.id.tv_amount);
            TextView tvRemark = convertView.findViewById(R.id.tv_remark);

            tvDate.setText(e.date);
            String sub = subName(e.subCategory);
            tvSub.setText(sub.isEmpty() ? getString(R.string.finance_expense) : sub);
            tvAmount.setText(String.format(Locale.CHINA, "%.2f", e.amount));
            tvRemark.setText(e.remark.isEmpty() ? "" : getString(R.string.finance_remark) + ": " + e.remark);

            convertView.setOnClickListener(v -> showEditMenu(e.date,
                    () -> showExpenseDialog(e),
                    () -> {
                        financeDb.deleteExpense(e.id);
                        loadData();
                    }));
            return convertView;
        }
    }

    private class SeedAdapter extends BaseAdapter {
        private final List<FinSeed> list;

        SeedAdapter(List<FinSeed> list) {
            this.list = list;
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int position) {
            return list.get(position);
        }

        @Override
        public long getItemId(int position) {
            return list.get(position).id;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(FinanceExpenseActivity.this)
                        .inflate(R.layout.item_finance_expense, parent, false);
            }
            final FinSeed s = list.get(position);
            TextView tvDate = convertView.findViewById(R.id.tv_date);
            TextView tvSub = convertView.findViewById(R.id.tv_sub);
            TextView tvAmount = convertView.findViewById(R.id.tv_amount);
            TextView tvRemark = convertView.findViewById(R.id.tv_remark);

            tvDate.setText(s.date);
            tvSub.setText(s.supplierName);
            tvAmount.setText(String.format(Locale.CHINA, "%.2f", s.actualAmount));
            tvRemark.setText(String.format(Locale.CHINA, "%s:%.2f  %s:%.2f  %s:%.2f",
                    getString(R.string.finance_seed_count), s.quantity,
                    getString(R.string.finance_unit_price), s.unitPrice,
                    getString(R.string.finance_total_price), s.totalAmount)
                    + (s.remark.isEmpty() ? "" : "  " + s.remark));

            convertView.setOnClickListener(v -> showEditMenu(s.date,
                    () -> showSeedDialog(s),
                    () -> {
                        financeDb.deleteSeed(s.id);
                        loadData();
                    }));
            return convertView;
        }
    }

    private class WageAdapter extends BaseAdapter {
        private final List<FinWage> list;

        WageAdapter(List<FinWage> list) {
            this.list = list;
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int position) {
            return list.get(position);
        }

        @Override
        public long getItemId(int position) {
            return list.get(position).id;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(FinanceExpenseActivity.this)
                        .inflate(R.layout.item_finance_expense, parent, false);
            }
            final FinWage w = list.get(position);
            TextView tvDate = convertView.findViewById(R.id.tv_date);
            TextView tvSub = convertView.findViewById(R.id.tv_sub);
            TextView tvAmount = convertView.findViewById(R.id.tv_amount);
            TextView tvRemark = convertView.findViewById(R.id.tv_remark);

            tvDate.setText(w.date);
            tvSub.setText(w.payee);
            tvAmount.setText(String.format(Locale.CHINA, "%.2f", w.amount));
            tvRemark.setText(w.remark.isEmpty() ? "" : getString(R.string.finance_remark) + ": " + w.remark);

            convertView.setOnClickListener(v -> showEditMenu(w.date,
                    () -> showWageDialog(w),
                    () -> {
                        financeDb.deleteWage(w.id);
                        loadData();
                    }));
            return convertView;
        }
    }

    private class TicketAdapter extends BaseAdapter {
        private final List<FinanceDb.FinTicket> tickets;

        TicketAdapter(List<FinanceDb.FinTicket> tickets) {
            this.tickets = tickets;
        }

        @Override
        public int getCount() {
            return tickets.size();
        }

        @Override
        public Object getItem(int position) {
            return tickets.get(position);
        }

        @Override
        public long getItemId(int position) {
            return tickets.get(position).id;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(FinanceExpenseActivity.this)
                        .inflate(R.layout.item_finance_expense, parent, false);
            }
            final FinanceDb.FinTicket t = tickets.get(position);
            TextView tvDate = convertView.findViewById(R.id.tv_date);
            TextView tvSub = convertView.findViewById(R.id.tv_sub);
            TextView tvAmount = convertView.findViewById(R.id.tv_amount);
            TextView tvRemark = convertView.findViewById(R.id.tv_remark);

            tvDate.setText(t.date);
            tvSub.setText(t.supplierName + (t.ticketNo.isEmpty() ? "" : " 【" + t.ticketNo + "】"));
            tvAmount.setText(String.format(Locale.CHINA, "%.2f", t.totalAmount));
            tvRemark.setText(t.remark.isEmpty() ? "" : getString(R.string.finance_remark) + ": " + t.remark);

            convertView.setOnClickListener(v -> {
                Intent intent = new Intent(FinanceExpenseActivity.this, TicketEditActivity.class);
                intent.putExtra("ticketId", t.id);
                startActivity(intent);
            });
            return convertView;
        }
    }
}