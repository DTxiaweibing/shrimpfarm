package com.shrimpfarm.app.finance;

import android.app.AlertDialog;
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
import com.shrimpfarm.app.finance.FinanceDb.FinCategory;
import com.shrimpfarm.app.finance.FinanceDb.FinIncome;
import com.shrimpfarm.app.finance.FinanceDb.FinProduct;
import com.shrimpfarm.app.finance.FinanceDb.FinSeed;
import com.shrimpfarm.app.finance.FinanceDb.FinSupplier;
import com.shrimpfarm.app.finance.FinanceDb.FinWage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class QueryActivity extends BaseActivity {

    private static final int MODE_PERSON = 0;
    private static final int MODE_CATEGORY = 1;
    private static final int MODE_PRODUCT = 2;
    private static final int MODE_CAR = 3;
    private static final int MODE_SEED = 4;
    private static final int MODE_WAGE = 5;

    private static final int KIND_PERSON = 0;
    private static final int KIND_CATEGORY = 1;
    private static final int KIND_PRODUCT = 2;
    private static final int KIND_INCOME = 3;
    private static final int KIND_SEED = 4;
    private static final int KIND_WAGE = 5;

    private FinanceDb financeDb;
    private int currentMode = MODE_PERSON;
    private FinCategory currentCategory;
    private ListView listView;
    private TextView tvBack, tvResultHint;
    private ResultAdapter adapter;
    private List<ResultRow> rows = new ArrayList<>();

    private static class ResultRow {
        String title;
        String sub;
        String tag;
        int kind;
        Object data;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_query);
        financeDb = new FinanceDb(this);
        setupToolbar();
        listView = findViewById(R.id.list_view);
        tvBack = findViewById(R.id.btn_back);
        tvResultHint = findViewById(R.id.tv_result_hint);
        adapter = new ResultAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, v, position, id) -> onRowClick(rows.get(position)));
        Spinner spMode = findViewById(R.id.sp_mode);
        final String[] modes = {
                getString(R.string.finance_query_person),
                getString(R.string.finance_query_category),
                getString(R.string.finance_query_product),
                getString(R.string.finance_query_car),
                getString(R.string.finance_query_seed),
                getString(R.string.finance_wage_list)
        };
        spMode.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, modes));
        spMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int position, long id) {
                currentMode = position;
                tvBack.setVisibility(View.GONE);
                tvResultHint.setText("");
                if (currentMode == MODE_CATEGORY) loadCategories();
                else rows.clear();
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { /* 忽略 */ }
        });

        findViewById(R.id.btn_search).setOnClickListener(v -> onSearch());
        tvBack.setOnClickListener(v -> {
            currentCategory = null;
            loadCategories();
        });
    }

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_home;
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.finance_query);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private String personTypeLabel(int type) {
        switch (type) {
            case FinanceDb.PERSON_AGENT: return getString(R.string.finance_person_agent);
            case FinanceDb.PERSON_CUSTOMER: return getString(R.string.finance_person_customer);
            case FinanceDb.PERSON_EMPLOYEE: return getString(R.string.finance_person_employee);
            default: return getString(R.string.finance_person_supplier);
        }
    }

    private void onSearch() {
        String kw = ((EditText) findViewById(R.id.et_keyword)).getText().toString().trim();
        if (currentMode == MODE_CATEGORY) {
            loadCategories();
            return;
        }
        rows.clear();
        switch (currentMode) {
            case MODE_PERSON:
                for (FinSupplier s : financeDb.searchPersons(kw)) {
                    ResultRow r = new ResultRow();
                    r.kind = KIND_PERSON;
                    r.data = s;
                    r.title = s.name;
                    r.tag = personTypeLabel(s.personType);
                    StringBuilder sub = new StringBuilder();
                    if (!s.contact.isEmpty()) sub.append(getString(R.string.finance_contact_person)).append(": ").append(s.contact);
                    if (!s.phone.isEmpty()) {
                        if (sub.length() > 0) sub.append("  ");
                        sub.append(getString(R.string.finance_phone)).append(": ").append(s.phone);
                    }
                    if (s.personType == FinanceDb.PERSON_EMPLOYEE && !s.startDate.isEmpty()) {
                        sub.append("\n").append(getString(R.string.finance_start_date)).append(": ").append(s.startDate);
                        double due = financeDb.calcDueWage(s.id, todayStr());
                        double paid = financeDb.sumWageByEmployee(s.id);
                        sub.append("  ").append(getString(R.string.finance_due_wage)).append(" ¥").append(FinanceDb.round2(due))
                                .append("  ").append(getString(R.string.finance_paid_wage)).append(" ¥").append(paid);
                    }
                    r.sub = sub.toString();
                    rows.add(r);
                }
                break;
            case MODE_PRODUCT:
                for (FinProduct p : financeDb.searchProducts(kw)) {
                    ResultRow r = new ResultRow();
                    r.kind = KIND_PRODUCT;
                    r.data = p;
                    r.title = p.name + (p.unit.isEmpty() ? "" : " (" + p.unit + ")");
                    r.tag = getString(R.string.finance_query_product);
                    List<String> names = financeDb.getSupplierNamesByProduct(p.id);
                    r.sub = getString(R.string.finance_related_suppliers) + ": "
                            + (names.isEmpty() ? "—" : android.text.TextUtils.join("、", names));
                    rows.add(r);
                }
                break;
            case MODE_CAR:
                for (FinIncome in : financeDb.searchIncomes(kw)) {
                    ResultRow r = new ResultRow();
                    r.kind = KIND_INCOME;
                    r.data = in;
                    r.title = getString(R.string.finance_car_no) + ": " + in.carNo;
                    r.tag = getString(R.string.finance_query_car);
                    r.sub = String.format(Locale.CHINA, "%s  棚%s  规格:%s  重量:%.1f市斤  实收:%.2f",
                            in.date, in.shedNo, in.specs, in.weight, in.actualAmount);
                    String status = in.paid
                            ? getString(R.string.finance_received)
                            + (in.payDate.isEmpty() ? "" : " " + in.payDate)
                            : getString(R.string.finance_receivable) + " " + FinanceDb.round2(in.actualAmount);
                    if (in.paid && !in.accountName.isEmpty()) status += " " + in.accountName;
                    r.sub += "  " + status;
                    rows.add(r);
                }
                break;
            case MODE_SEED:
                for (FinSeed s : financeDb.searchSeeds(kw)) {
                    ResultRow r = new ResultRow();
                    r.kind = KIND_SEED;
                    r.data = s;
                    r.title = s.supplierName;
                    r.tag = getString(R.string.finance_query_seed);
                    r.sub = String.format(Locale.CHINA, "%s  总量:%.0f  单价:%.2f  总价:%.2f  实付:%.2f%s",
                            s.date, s.quantity, s.unitPrice, s.totalAmount, s.actualAmount,
                            s.remark.isEmpty() ? "" : "  " + s.remark);
                    rows.add(r);
                }
                break;
            case MODE_WAGE:
                for (FinWage w : financeDb.searchWages(kw)) {
                    ResultRow r = new ResultRow();
                    r.kind = KIND_WAGE;
                    r.data = w;
                    r.title = w.payee;
                    r.tag = getString(R.string.finance_wage_list);
                    r.sub = String.format(Locale.CHINA, "%s  ¥%.2f%s",
                            w.date, w.amount, w.remark.isEmpty() ? "" : "  " + w.remark);
                    rows.add(r);
                }
                break;
            default:
                break;
        }
        if (rows.isEmpty()) {
            Toast.makeText(this, R.string.finance_no_result, Toast.LENGTH_SHORT).show();
        }
        tvResultHint.setText(getString(R.string.finance_query) + " " + rows.size());
        currentCategory = null;
        tvBack.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void loadCategories() {
        rows.clear();
        currentCategory = null;
        tvBack.setVisibility(View.GONE);
        for (FinCategory c : financeDb.getCategories()) {
            if (!c.enabled) continue;
            ResultRow r = new ResultRow();
            r.kind = KIND_CATEGORY;
            r.data = c;
            r.title = c.name;
            r.tag = getString(R.string.finance_query_category);
            r.sub = "";
            rows.add(r);
        }
        tvResultHint.setText(getString(R.string.finance_query_category) + " " + rows.size());
        adapter.notifyDataSetChanged();
    }

    private void loadProductsOfCategory(FinCategory cat) {
        rows.clear();
        currentCategory = cat;
        tvBack.setVisibility(View.VISIBLE);
        for (FinProduct p : financeDb.getProductsByCategory(cat.id)) {
            ResultRow r = new ResultRow();
            r.kind = KIND_PRODUCT;
            r.data = p;
            r.title = p.name + (p.unit.isEmpty() ? "" : " (" + p.unit + ")");
            r.tag = cat.name;
            List<String> names = financeDb.getSupplierNamesByProduct(p.id);
            r.sub = getString(R.string.finance_related_suppliers) + ": "
                    + (names.isEmpty() ? "—" : android.text.TextUtils.join("、", names));
            rows.add(r);
        }
        tvResultHint.setText(cat.name + " " + rows.size());
        adapter.notifyDataSetChanged();
    }

    private void onRowClick(ResultRow r) {
        switch (r.kind) {
            case KIND_PERSON:
                showPersonDialog((FinSupplier) r.data);
                break;
            case KIND_CATEGORY:
                loadProductsOfCategory((FinCategory) r.data);
                break;
            case KIND_PRODUCT:
                showProductDialog((FinProduct) r.data);
                break;
            case KIND_INCOME:
            case KIND_SEED:
            case KIND_WAGE:
                showDetailDialog(r.title, r.sub);
                break;
            default:
                break;
        }
    }

    private void showPersonDialog(final FinSupplier s) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        final String[] types = {
                getString(R.string.finance_person_supplier),
                getString(R.string.finance_person_agent),
                getString(R.string.finance_person_customer),
                getString(R.string.finance_person_employee)
        };
        final Spinner spType = new Spinner(this);
        spType.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, types));
        int typePos = Math.max(0, s.personType - 1);
        spType.setSelection(Math.min(typePos, types.length - 1));
        layout.addView(spType);

        final EditText etName = new EditText(this);
        etName.setHint(R.string.finance_input_name);
        etName.setText(s.name);
        final EditText etPhone = new EditText(this);
        etPhone.setHint(R.string.finance_phone);
        etPhone.setText(s.phone);
        final EditText etRemark = new EditText(this);
        etRemark.setHint(R.string.finance_remark);
        etRemark.setText(s.remark);
        layout.addView(etName);
        layout.addView(etPhone);
        layout.addView(etRemark);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.finance_edit) + " - " + s.name);
        builder.setView(layout);
        builder.setPositiveButton(R.string.btn_ok, (d, w) -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) return;
            int type = spType.getSelectedItemPosition() + 1;
            financeDb.updateSupplier(s.id, type, name, "",
                    etPhone.getText().toString().trim(), s.startDate, s.dailyWage,
                    etRemark.getText().toString().trim());
            onSearch();
        });
        builder.setNegativeButton(R.string.btn_cancel, null);
        builder.show();
    }

    private void showProductDialog(FinProduct p) {
        List<String> names = financeDb.getSupplierNamesByProduct(p.id);
        String body = getString(R.string.finance_related_suppliers) + ":\n"
                + (names.isEmpty() ? "—" : android.text.TextUtils.join("\n", names));
        showDetailDialog(p.name + (p.unit.isEmpty() ? "" : " (" + p.unit + ")"), body);
    }

    private void showDetailDialog(String title, String body) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(body)
                .setPositiveButton(R.string.btn_ok, null)
                .show();
    }

    private String todayStr() {
        return new java.text.SimpleDateFormat("yyyy/MM/dd", Locale.CHINA)
                .format(new java.util.Date());
    }

    private class ResultAdapter extends BaseAdapter {
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
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(QueryActivity.this)
                        .inflate(R.layout.item_finance_query, parent, false);
            }
            ResultRow r = rows.get(position);
            TextView tvTitle = convertView.findViewById(R.id.tv_title);
            TextView tvTag = convertView.findViewById(R.id.tv_tag);
            TextView tvSub = convertView.findViewById(R.id.tv_sub);
            tvTitle.setText(r.title);
            tvTag.setText(r.tag);
            tvSub.setText(r.sub);
            return convertView;
        }
    }
}