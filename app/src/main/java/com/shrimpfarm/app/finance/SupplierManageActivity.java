package com.shrimpfarm.app.finance;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.shrimpfarm.app.R;
import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.finance.FinanceDb.FinSupplier;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SupplierManageActivity extends BaseActivity {

    private FinanceDb financeDb;
    private List<FinSupplier> supplierList = new ArrayList<>();
    private ListView listView;
    private SupplierAdapter adapter;
    private int filterType = FinanceDb.PERSON_SUPPLIER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_supplier_manage);
        financeDb = new FinanceDb(this);
        filterType = getIntent().getIntExtra("personType", FinanceDb.PERSON_SUPPLIER);
        setupToolbar();
        listView = findViewById(R.id.list_view);
        adapter = new SupplierAdapter();
        listView.setAdapter(adapter);
        findViewById(R.id.btn_add).setOnClickListener(v -> showEditDialog(null));
        loadData();
    }

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_home;
    }

    private boolean isEmployee() {
        return filterType == FinanceDb.PERSON_EMPLOYEE;
    }

    private String typeTitle() {
        switch (filterType) {
            case FinanceDb.PERSON_AGENT: return getString(R.string.finance_person_agent);
            case FinanceDb.PERSON_CUSTOMER: return getString(R.string.finance_person_customer);
            case FinanceDb.PERSON_EMPLOYEE: return getString(R.string.finance_person_employee);
            default: return getString(R.string.finance_person_supplier);
        }
    }

    private String typeLabel(int type) {
        switch (type) {
            case FinanceDb.PERSON_AGENT: return getString(R.string.finance_person_agent);
            case FinanceDb.PERSON_CUSTOMER: return getString(R.string.finance_person_customer);
            case FinanceDb.PERSON_EMPLOYEE: return getString(R.string.finance_person_employee);
            default: return getString(R.string.finance_person_supplier);
        }
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(typeTitle() + getString(R.string.finance_manage));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadData() {
        supplierList = financeDb.getSuppliersByType(filterType);
        TextView tvTotal = findViewById(R.id.tv_total);
        if (filterType == FinanceDb.PERSON_SUPPLIER) {
            double total = 0;
            int count = 0;
            for (FinSupplier s : supplierList) {
                double unpaid = financeDb.sumUnsettledBySupplier(s.id);
                if (unpaid > 0) {
                    total += unpaid;
                    count++;
                }
            }
            if (count > 0) {
                tvTotal.setText(getString(R.string.erp_total_unsettled,
                        FinanceDb.round2(total), count));
                tvTotal.setVisibility(View.VISIBLE);
            } else {
                tvTotal.setVisibility(View.GONE);
            }
        } else {
            tvTotal.setVisibility(View.GONE);
        }
        adapter.notifyDataSetChanged();
    }

    private void showEditDialog(final FinSupplier supplier) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        final EditText etName = new EditText(this);
        etName.setHint(R.string.finance_input_name);
        final EditText etContact = new EditText(this);
        etContact.setHint(R.string.finance_contact_person);
        final EditText etPhone = new EditText(this);
        etPhone.setHint(R.string.finance_phone);
        etPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        layout.addView(etName);
        layout.addView(etContact);
        layout.addView(etPhone);

        final EditText etStart = new EditText(this);
        etStart.setHint(R.string.finance_start_date);
        etStart.setFocusable(false);
        final EditText etWage = new EditText(this);
        etWage.setHint(R.string.finance_daily_wage);
        etWage.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (isEmployee()) {
            layout.addView(etStart);
            layout.addView(etWage);
        }

        final EditText etRemark = new EditText(this);
        etRemark.setHint(R.string.finance_remark);
        layout.addView(etRemark);

        String preStart = today();
        double preWage = 0;
        if (supplier != null) {
            etName.setText(supplier.name);
            etContact.setText(supplier.contact);
            etPhone.setText(supplier.phone);
            etRemark.setText(supplier.remark);
            if (!supplier.startDate.isEmpty()) preStart = supplier.startDate;
            preWage = supplier.dailyWage;
        }
        final String[] startDate = {preStart};
        etStart.setText(startDate[0]);
        etWage.setText(preWage > 0 ? String.valueOf(preWage) : "");
        etStart.setOnClickListener(v -> {
            int[] p = parseDate(startDate[0]);
            new DatePickerDialog(this, (view, y, m, d) -> {
                startDate[0] = String.format(Locale.CHINA, "%d/%02d/%02d", y, m + 1, d);
                etStart.setText(startDate[0]);
            }, p[0], p[1] - 1, p[2]).show();
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(supplier == null ? R.string.finance_add : R.string.finance_edit);
        builder.setView(layout);
        builder.setPositiveButton(R.string.btn_ok, (d, w) -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) return;
            String contact = etContact.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String remark = etRemark.getText().toString().trim();
            double wage = 0;
            try { wage = Double.parseDouble(etWage.getText().toString().trim()); } catch (Exception ignored) { /* 0 */ }
            if (supplier == null) {
                financeDb.insertSupplier(filterType, name, contact, phone,
                        isEmployee() ? startDate[0] : "", isEmployee() ? wage : 0, remark);
            } else {
                financeDb.updateSupplier(supplier.id, filterType, name, contact, phone,
                        isEmployee() ? startDate[0] : "", isEmployee() ? wage : 0, remark);
            }
            loadData();
        });
        builder.setNegativeButton(R.string.btn_cancel, null);
        builder.show();
    }

    private String today() {
        return new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).format(Calendar.getInstance().getTime());
    }

    private int[] parseDate(String date) {
        try {
            String[] parts = date.split("/");
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (Exception e) {
            Calendar c = Calendar.getInstance();
            return new int[]{c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)};
        }
    }

    private void showRestDialog(final FinSupplier s) {
        final String[] splits = s.startDate.split("/");
        final int[] start = new int[3];
        if (splits.length == 3) {
            try {
                start[0] = Integer.parseInt(splits[0]);
                start[1] = Integer.parseInt(splits[1]);
                start[2] = Integer.parseInt(splits[2]);
            } catch (Exception e) { resetToday(start); }
        } else {
            resetToday(start);
        }
        Calendar cal = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        cal.set(start[0], start[1] - 1, start[2]);

        Set<String> restSet = new HashSet<>(financeDb.getRestDays(s.id));
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (20 * getResources().getDisplayMetrics().density), 0, 0, 0);
        final List<CheckBox> boxes = new ArrayList<>();
        int guard = 0;
        while (!cal.after(end) && guard < 1000) {
            final String d = fmt.format(cal.getTime());
            CheckBox cb = new CheckBox(this);
            cb.setText(d + (restSet.contains(d) ? "  休息" : ""));
            cb.setChecked(restSet.contains(d));
            boxes.add(cb);
            box.addView(cb);
            cal.add(Calendar.DATE, 1);
            guard++;
        }
        if (boxes.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.finance_no_rest_period)
                    .setPositiveButton(R.string.btn_ok, null)
                    .show();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.finance_rest_reg) + " - " + s.name)
                .setView(scroll)
                .setPositiveButton(R.string.btn_ok, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            List<String> checked = new ArrayList<>();
            for (CheckBox cb : boxes) if (cb.isChecked()) checked.add(cb.getText().toString().replace("  休息", ""));
            financeDb.setRestDays(s.id, checked);
            dialog.dismiss();
            loadData();
        }));
        dialog.show();
    }

    private void resetToday(int[] out) {
        Calendar c = Calendar.getInstance();
        out[0] = c.get(Calendar.YEAR);
        out[1] = c.get(Calendar.MONTH) + 1;
        out[2] = c.get(Calendar.DAY_OF_MONTH);
    }

    private void openSupplierProducts(FinSupplier supplier) {
        Intent intent = new Intent(this, SupplierProductActivity.class);
        intent.putExtra("supplierId", supplier.id);
        intent.putExtra("supplierName", supplier.name);
        startActivity(intent);
    }

    private void openContactAccounts(FinSupplier s) {
        Intent intent = new Intent(this, ContactAccountActivity.class);
        intent.putExtra("supplierId", s.id);
        intent.putExtra("supplierName", s.name);
        startActivity(intent);
    }

    private class SupplierAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return supplierList.size();
        }

        @Override
        public Object getItem(int position) {
            return supplierList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return supplierList.get(position).id;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(SupplierManageActivity.this)
                        .inflate(R.layout.item_finance_supplier, parent, false);
            }
            final FinSupplier s = supplierList.get(position);
            TextView tvName = convertView.findViewById(R.id.tv_name);
            TextView tvContact = convertView.findViewById(R.id.tv_contact);
            TextView tvRemark = convertView.findViewById(R.id.tv_remark);
            TextView tvExtra = convertView.findViewById(R.id.tv_extra);

            tvName.setText(s.name + "  [" + typeLabel(s.personType) + "]");
            String contactLine = "";
            if (!s.contact.isEmpty()) contactLine = getString(R.string.finance_contact_person) + ": " + s.contact;
            if (!s.phone.isEmpty()) {
                if (!contactLine.isEmpty()) contactLine += "  ";
                contactLine += getString(R.string.finance_phone) + ": " + s.phone;
            }
            tvContact.setText(contactLine);
            tvRemark.setText(s.remark.isEmpty() ? "" : getString(R.string.finance_remark) + ": " + s.remark);
            convertView.setAlpha(s.enabled ? 1f : 0.55f);

            if (isEmployee()) {
                String base = "";
                if (!s.startDate.isEmpty()) base = getString(R.string.finance_start_date) + ": " + s.startDate;
                if (s.dailyWage > 0) {
                    if (!base.isEmpty()) base += "  ";
                    base += getString(R.string.finance_daily_wage) + ": " + FinanceDb.round2(s.dailyWage);
                }
                int restCount = financeDb.getRestDays(s.id).size();
                double due = financeDb.calcDueWage(s.id, today());
                double paid = financeDb.sumWageByEmployee(s.id);
                String stat = base + (base.isEmpty() ? "" : "\n")
                        + getString(R.string.finance_rest_count) + ":" + restCount + "天  "
                        + getString(R.string.finance_due_wage) + ": ¥" + FinanceDb.round2(due) + "  "
                        + getString(R.string.finance_paid_wage) + ": ¥" + paid;
                tvExtra.setText(stat);
                tvExtra.setVisibility(View.VISIBLE);
            } else {
                double unpaid = financeDb.sumUnsettledBySupplier(s.id);
                if (filterType == FinanceDb.PERSON_SUPPLIER && unpaid > 0) {
                    tvExtra.setText(getString(R.string.finance_unsettled) + ": ¥" + FinanceDb.round2(unpaid));
                    tvExtra.setVisibility(View.VISIBLE);
                } else {
                    tvExtra.setText("");
                    tvExtra.setVisibility(View.GONE);
                }
            }

            TextView tvProducts = convertView.findViewById(R.id.tv_products);
            TextView tvDelete = convertView.findViewById(R.id.tv_delete);
            TextView tvToggle = convertView.findViewById(R.id.tv_toggle);
            TextView tvEdit = convertView.findViewById(R.id.tv_edit);
            TextView tvAccounts = convertView.findViewById(R.id.tv_accounts);

            if (isEmployee()) {
                tvProducts.setText(R.string.finance_rest_reg);
                tvProducts.setOnClickListener(v -> showRestDialog(s));
            } else {
                tvProducts.setText(R.string.finance_supplier_product_manage);
                tvProducts.setOnClickListener(v -> openSupplierProducts(s));
            }

            tvAccounts.setOnClickListener(v -> openContactAccounts(s));

            tvDelete.setOnClickListener(v -> new AlertDialog.Builder(SupplierManageActivity.this)
                    .setMessage(R.string.finance_confirm_delete)
                    .setPositiveButton(R.string.btn_ok, (d, w) -> {
                        financeDb.setSupplierEnabled(s.id, false);
                        loadData();
                    })
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show());

            tvToggle.setText(s.enabled ? R.string.finance_disable : R.string.finance_enable);
            tvToggle.setOnClickListener(v -> {
                if (s.enabled) {
                    new AlertDialog.Builder(SupplierManageActivity.this)
                            .setMessage(R.string.finance_confirm_disable)
                            .setPositiveButton(R.string.btn_ok, (d, w) -> {
                                financeDb.setSupplierEnabled(s.id, false);
                                loadData();
                            })
                            .setNegativeButton(R.string.btn_cancel, null)
                            .show();
                } else {
                    financeDb.setSupplierEnabled(s.id, true);
                    loadData();
                }
            });

            tvEdit.setOnClickListener(v -> showEditDialog(s));
            return convertView;
        }
    }
}