package com.shrimpfarm.app.finance;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 进货开单：#316 样式单据，一屏 8 列横向表格。
 * 品类 -> 商品 级联，选中商品自动带出历史价与涨跌幅。
 */
public class PurchaseEditActivity extends BaseActivity {

    /** 底部预留的空行数，进去就能直接录入 */
    private static final int BLANK_ROWS = 10;

    private FinanceDb financeDb;
    private SharedPreferences prefs;
    private String batchId;

    private long editingOrderId = 0;
    private long editingSupplierId = 0;

    private Spinner spSupplier;
    private Spinner spPaid;
    private Spinner spAccount;
    private TextView etDate;
    private TextView tvOrderNo;
    private TextView tvLineCount;
    private TextView tvTotal;
    private LinearLayout llRows;
    private TextView tvLinesEmpty;
    private EditText etRemark;

    private final List<FinanceDb.FinSupplier> suppliers = new ArrayList<>();
    private final List<FinanceDb.FinCategory> categories = new ArrayList<>();
    private final List<FinanceDb.FinAccount> accounts = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();

    private long currentSupplierId = 0;
    /** 已有明细归属供应商，切换供应商时提示清空 */
    private long linesSupplierId = 0;
    private boolean paid = false;
    private long accountId = 0;

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_home;
    }

    /** 网格中的一行 */
    private static final class Row {
        View view;
        Spinner spCat;
        Spinner spName;
        TextView tvSpec;
        TextView tvHistory;
        EditText etCurrent;
        TextView tvChange;
        EditText etQty;
        TextView tvAmount;
        TextView btnDel;

        long categoryId = 0;
        FinanceDb.FinSupplierProduct product;
        /** 当前品类下可选的商品列表（spName adapter 的数据源） */
        List<FinanceDb.FinSupplierProduct> productList = new ArrayList<>();
        double historyPrice = 0;
        boolean hasHistory = false;
        /** 回填期间抑制联动，避免递归 */
        boolean binding = false;
        /** 该行是否已有有效内容 */
        boolean used() {
            return product != null || parse(etQty.getText().toString()) > 0
                    || parse(etCurrent.getText().toString()) > 0;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_purchase_edit);
        financeDb = new FinanceDb(this);
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        batchId = prefs.getString("current_batch_id", "");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.finance_purchase_add);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        spSupplier = findViewById(R.id.sp_supplier);
        spPaid = findViewById(R.id.sp_paid);
        spAccount = findViewById(R.id.sp_account);
        etDate = findViewById(R.id.et_date);
        tvOrderNo = findViewById(R.id.tv_order_no);
        tvLineCount = findViewById(R.id.tv_line_count);
        tvTotal = findViewById(R.id.tv_total);
        llRows = findViewById(R.id.ll_rows);
        tvLinesEmpty = findViewById(R.id.tv_lines_empty);
        etRemark = findViewById(R.id.et_remark);

        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        findViewById(R.id.btn_manage_supplier_product)
                .setOnClickListener(v -> manageSupplierProducts());
        etDate.setOnClickListener(v -> pickDate());

        setupHeader();
        loadEditTarget();

        if (editingOrderId <= 0) {
            Calendar c = Calendar.getInstance();
            etDate.setText(String.format(Locale.CHINA, "%04d-%02d-%02d",
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
                    c.get(Calendar.DAY_OF_MONTH)));
            tvOrderNo.setText(R.string.finance_po_order_no_auto);
        }

        // 空行预置，直接可录
        int blank = Math.max(0, BLANK_ROWS - rows.size());
        for (int i = 0; i < blank; i++) addRow();
        refreshSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从供应商商品建档页返回时刷新可选商品
        setupSupplierSpinner();
    }

    // ==================== 抬头 ====================

    private void setupHeader() {
        setupSupplierSpinner();
        loadCategories();
        setupPaidSpinner();
        setupAccountSpinner();
    }

    private void setupSupplierSpinner() {
        long keep = currentSupplierId > 0 ? currentSupplierId : editingSupplierId;
        suppliers.clear();
        suppliers.addAll(financeDb.getSuppliers());

        List<String> names = new ArrayList<>();
        for (FinanceDb.FinSupplier s : suppliers) names.add(s.name);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSupplier.setAdapter(ad);

        int target = -1;
        for (int i = 0; i < suppliers.size(); i++) {
            if (suppliers.get(i).id == keep) {
                target = i;
                break;
            }
        }
        if (target < 0 && !suppliers.isEmpty()) target = 0;
        if (target >= 0) spSupplier.setSelection(target);

        spSupplier.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos < 0 || pos >= suppliers.size()) return;
                long picked = suppliers.get(pos).id;
                if (!isEmptyOrder() && linesSupplierId > 0 && picked != linesSupplierId) {
                    new AlertDialog.Builder(PurchaseEditActivity.this)
                            .setMessage(R.string.finance_po_switch_supplier_tip)
                            .setPositiveButton(R.string.finance_po_clear_lines,
                                    (d, w) -> {
                                        clearLines();
                                        applySupplier(picked);
                                    })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                    return;
                }
                applySupplier(picked);
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });
    }

    private void applySupplier(long picked) {
        if (currentSupplierId == picked) return;
        currentSupplierId = picked;
        // 换供应商后商品需重选
        for (Row r : rows) {
            r.product = null;
            r.categoryId = 0;
            r.hasHistory = false;
            r.historyPrice = 0;
            r.etCurrent.setText("");
            r.etQty.setText("");
            r.tvSpec.setText("");
            r.tvHistory.setText("");
            r.tvChange.setText("");
            r.tvAmount.setText("");
            bindCategory(r);
            clearProduct(r);
        }
        refreshSummary();
    }

    private void loadCategories() {
        categories.clear();
        for (FinanceDb.FinCategory c : financeDb.getCategories()) {
            if (c.name == null || c.name.trim().isEmpty()) continue;
            if (c.enabled || c.isCustom) categories.add(c);
        }
    }

    private void setupPaidSpinner() {
        List<String> opts = new ArrayList<>();
        opts.add(getString(R.string.finance_po_settled_no));
        opts.add(getString(R.string.finance_po_settled_yes));
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, opts);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPaid.setAdapter(ad);
        spPaid.setSelection(paid ? 1 : 0);
        spPaid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                paid = pos == 1;
                spAccount.setEnabled(paid);
                spAccount.setAlpha(paid ? 1f : 0.4f);
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });
    }

    private void setupAccountSpinner() {
        accounts.clear();
        accounts.addAll(financeDb.getPaymentAccounts());
        List<String> names = new ArrayList<>();
        names.add(getString(R.string.finance_po_account_none));
        for (FinanceDb.FinAccount a : accounts) names.add(a.name);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAccount.setAdapter(ad);

        int target = 0;
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).id == accountId) {
                target = i + 1;
                break;
            }
        }
        spAccount.setSelection(target);
        spAccount.setEnabled(paid);
        spAccount.setAlpha(paid ? 1f : 0.4f);
        spAccount.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                accountId = (pos <= 0 || pos > accounts.size()) ? 0 : accounts.get(pos - 1).id;
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });
    }

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        String cur = etDate.getText().toString().trim();
        try {
            if (cur.length() >= 10) {
                c.set(Integer.parseInt(cur.substring(0, 4)),
                        Integer.parseInt(cur.substring(5, 7)) - 1,
                        Integer.parseInt(cur.substring(8, 10)));
            }
        } catch (Exception ignored) {
        }
        new android.app.DatePickerDialog(this,
                (v, y, m, d) -> etDate.setText(String.format(Locale.CHINA,
                        "%04d-%02d-%02d", y, m + 1, d)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void manageSupplierProducts() {
        if (currentSupplierId <= 0) {
            Toast.makeText(this, R.string.finance_po_pick_supplier_first, Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this, SupplierProductActivity.class)
                .putExtra("supplier_id", currentSupplierId));
    }

    // ==================== 编辑已有单 ====================

    private void loadEditTarget() {
        editingOrderId = getIntent().getLongExtra("order_id", 0);
        if (editingOrderId <= 0) return;

        FinanceDb.FinPurchaseOrder o = financeDb.getPurchaseOrder(editingOrderId);
        if (o == null) {
            finish();
            return;
        }
        editingSupplierId = o.supplierId;
        currentSupplierId = o.supplierId;
        paid = o.isPaid();
        accountId = o.accountId;

        etDate.setText(o.date);
        etRemark.setText(o.remark);
        tvOrderNo.setText(getString(R.string.finance_po_no, o.orderNo));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.finance_edit);
        }
        if (paid) {
            Toast.makeText(this, R.string.finance_po_settled_tip, Toast.LENGTH_SHORT).show();
        }

        for (FinanceDb.FinPurchaseItem it : financeDb.getPurchaseItems(editingOrderId)) {
            Row r = addRow();
            bindExisting(r, it);
        }
        linesSupplierId = o.supplierId;
        refreshSummary();
    }

    private void bindExisting(Row r, FinanceDb.FinPurchaseItem it) {
        r.binding = true;
        r.categoryId = it.categoryId;
        selectCategory(r, it.categoryId);
        List<FinanceDb.FinSupplierProduct> list = new ArrayList<>();
        for (FinanceDb.FinSupplierProduct sp : financeDb.getSupplierProducts(currentSupplierId)) {
            if (sp.id == it.supplierProductId || sp.categoryId == it.categoryId) {
                list.add(sp);
            }
        }
        fillProducts(r, list);
        int idx = indexOfProduct(r, it.supplierProductId);
        if (idx >= 0) {
            r.spName.setSelection(idx);
        } else {
            r.product = findProduct(r, it.supplierProductId);
        }
        r.tvSpec.setText(displaySpec(r.product));
        r.historyPrice = it.historyPrice;
        r.hasHistory = it.historyPrice > 0;
        r.tvHistory.setText(r.hasHistory ? PurchaseItemRowView.fmt(it.historyPrice) : "—");
        r.etCurrent.setText(it.hasPrice() ? PurchaseItemRowView.fmt(it.unitPrice) : "");
        r.etQty.setText(PurchaseItemRowView.trimNum(it.quantity));
        recalcRow(r);
        r.binding = false;
    }

    private FinanceDb.FinSupplierProduct findProduct(Row r, long spId) {
        List<FinanceDb.FinSupplierProduct> all =
                financeDb.getSupplierProducts(currentSupplierId);
        for (FinanceDb.FinSupplierProduct sp : all) {
            if (sp.id == spId) return sp;
        }
        return null;
    }

    // ==================== 网格行 ====================

    private Row addRow() {
        Row r = new Row();
        r.view = LayoutInflater.from(this)
                .inflate(R.layout.item_finance_purchase_line_edit, llRows, false);
        r.spCat = r.view.findViewById(R.id.sp_cat);
        r.spName = r.view.findViewById(R.id.sp_name);
        r.tvSpec = r.view.findViewById(R.id.tv_spec);
        r.tvHistory = r.view.findViewById(R.id.tv_history);
        r.etCurrent = r.view.findViewById(R.id.et_current);
        r.tvChange = r.view.findViewById(R.id.tv_change);
        r.etQty = r.view.findViewById(R.id.et_qty);
        r.tvAmount = r.view.findViewById(R.id.tv_amount);
        r.btnDel = r.view.findViewById(R.id.btn_del);

        llRows.addView(r.view);
        rows.add(r);

        bindCategory(r);
        wireRow(r);
        return r;
    }

    private void wireRow(final Row r) {
        r.spCat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (r.binding) return;
                if (pos == categories.size() + 1) {
                    // "+ 自定义" 恒为最后一项
                    promptCustomCategory(r);
                    return;
                }
                if (pos <= 0) {
                    r.categoryId = 0;
                    clearProduct(r);
                    return;
                }
                r.categoryId = categories.get(pos - 1).id;
                fillProducts(r);
                ensureTrailingBlank();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });

        r.spName.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (r.binding) return;
                List<FinanceDb.FinSupplierProduct> list = r.productList;
                if (list == null || list.isEmpty() || pos <= 0 || pos > list.size()) {
                    clearProduct(r);
                    return;
                }
                r.product = list.get(pos - 1);
                r.tvSpec.setText(displaySpec(r.product));
                applyHistoryPrice(r);
                recalcRow(r);
                ensureTrailingBlank();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });

        TextWatcher w = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (!r.binding) recalcRow(r);
            }
        };
        r.etCurrent.addTextChangedListener(w);
        r.etQty.addTextChangedListener(w);

        r.btnDel.setOnClickListener(v -> {
            llRows.removeView(r.view);
            rows.remove(r);
            refreshSummary();
        });
    }

    /** 品类下拉：占位 + 19 内置 + 自定义项 + 自定义 */
    private void bindCategory(Row r) {
        List<String> opts = new ArrayList<>();
        opts.add(getString(R.string.finance_po_pick_category_hint));
        for (FinanceDb.FinCategory c : categories) opts.add(c.name);
        opts.add(getString(R.string.finance_po_custom_category));
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, opts);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        boolean prev = r.binding;
        r.binding = true;
        r.spCat.setAdapter(ad);
        r.binding = prev;
    }

    private void selectCategory(Row r, long catId) {
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).id == catId) {
                r.spCat.setSelection(i + 1);
                return;
            }
        }
    }

    private void fillProducts(Row r) {
        List<FinanceDb.FinSupplierProduct> list = new ArrayList<>();
        if (currentSupplierId > 0 && r.categoryId > 0) {
            list.addAll(financeDb.getSupplierProductsByCategory(currentSupplierId, r.categoryId));
        }
        fillProducts(r, list);
    }

    private void fillProducts(Row r, List<FinanceDb.FinSupplierProduct> list) {
        List<String> opts = new ArrayList<>();
        opts.add(getString(R.string.finance_po_pick_name_hint));
        for (FinanceDb.FinSupplierProduct sp : list) opts.add(sp.productName);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, opts);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        boolean prev = r.binding;
        r.binding = true;
        r.spName.setAdapter(ad);
        r.binding = prev;
        r.productList = list;
        r.product = null;
    }

    private int indexOfProduct(Row r, long spId) {
        List<FinanceDb.FinSupplierProduct> list = r.productList;
        if (list == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == spId) return i + 1;
        }
        return -1;
    }

    private void clearProduct(Row r) {
        r.product = null;
        r.tvSpec.setText("");
        r.tvHistory.setText("");
        r.tvChange.setText("");
        r.tvAmount.setText("");
        List<String> opts = new ArrayList<>();
        opts.add(getString(R.string.finance_po_pick_name_hint));
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, opts);
        r.binding = true;
        r.spName.setAdapter(ad);
        r.binding = false;
        r.productList = new ArrayList<>();
    }

    private void applyHistoryPrice(Row r) {
        if (r.product == null) return;
        FinanceDb.LastPrice lp = financeDb.getLastPurchaseInfo(
                r.product.id, r.product.unit, 1);
        r.hasHistory = lp.has && lp.price > 0;
        r.historyPrice = r.hasHistory ? lp.price : 0;
        r.tvHistory.setText(r.hasHistory ? PurchaseItemRowView.fmt(lp.price) : "—");
    }

    private static String displaySpec(FinanceDb.FinSupplierProduct sp) {
        if (sp == null) return "";
        String pack = sp.packSpec();
        if (!pack.isEmpty()) return pack;
        return sp.spec;
    }

    // ==================== 金额 ====================

    private void recalcRow(Row r) {
        double qty = parse(r.etQty.getText().toString());
        double cur = parse(r.etCurrent.getText().toString());
        double used = cur > 0 ? cur : (r.hasHistory ? r.historyPrice : 0);

        double amount = FinanceDb.round2(used * qty);
        r.tvAmount.setText(qty > 0 ? "¥" + PurchaseItemRowView.fmt(amount) : "");

        if (r.hasHistory && cur > 0) {
            double pct = FinanceDb.round2((cur - r.historyPrice) / r.historyPrice * 100.0);
            r.tvChange.setText(PurchaseItemRowView.fmt(pct) + "%");
            r.tvChange.setTextColor(pct > 0 ? 0xFFE53935 : (pct < 0 ? 0xFF43A047 : 0xFF8A93A0));
        } else {
            r.tvChange.setText("");
        }
        refreshSummary();
    }

    private void refreshSummary() {
        int n = 0;
        double total = 0;
        for (Row r : rows) {
            if (r.product == null) continue;
            n++;
            double qty = parse(r.etQty.getText().toString());
            double cur = parse(r.etCurrent.getText().toString());
            double used = cur > 0 ? cur : (r.hasHistory ? r.historyPrice : 0);
            total += used * qty;
        }
        if (n > 0 && linesSupplierId == 0) linesSupplierId = currentSupplierId;
        tvTotal.setText("¥" + PurchaseItemRowView.fmt(FinanceDb.round2(total)));
        tvLineCount.setText(getString(R.string.finance_po_line_count_value, n));
        tvLinesEmpty.setVisibility(n == 0 ? View.VISIBLE : View.GONE);
    }

    private void ensureTrailingBlank() {
        if (rows.isEmpty()) return;
        Row last = rows.get(rows.size() - 1);
        if (last.product != null) addRow();
    }

    private void clearLines() {
        for (Row r : new ArrayList<>(rows)) {
            llRows.removeView(r.view);
        }
        rows.clear();
        linesSupplierId = 0;
    }

    private boolean isEmptyOrder() {
        for (Row r : rows) {
            if (r.used()) return false;
        }
        return true;
    }

    // ==================== 自定义品类 ====================

    private void promptCustomCategory(final Row r) {
        final EditText input = new EditText(this);
        input.setHint(R.string.finance_po_custom_category_hint);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle(R.string.finance_po_custom_category_title)
                .setView(input)
                .setPositiveButton(R.string.btn_confirm, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.finance_po_category_required,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    for (FinanceDb.FinCategory c : categories) {
                        if (c.name.equals(name)) {
                            Toast.makeText(this, R.string.finance_po_category_exists,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    long id = financeDb.insertCategory(name, true);
                    if (id <= 0) {
                        Toast.makeText(this, R.string.finance_po_category_exists,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    loadCategories();
                    for (Row row : rows) bindCategory(row);
                    selectCategory(r, id);
                    r.categoryId = id;
                    fillProducts(r);
                    Toast.makeText(this, R.string.finance_po_category_added,
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ==================== 保存 ====================

    private void save() {
        if (currentSupplierId <= 0) {
            Toast.makeText(this, R.string.finance_po_pick_supplier, Toast.LENGTH_SHORT).show();
            return;
        }
        List<FinanceDb.FinPurchaseItem> items = new ArrayList<>();
        for (Row r : rows) {
            if (r.product == null) continue;
            double qty = parse(r.etQty.getText().toString());
            if (qty <= 0) {
                Toast.makeText(this, R.string.finance_po_quantity_required,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            FinanceDb.FinPurchaseItem it = new FinanceDb.FinPurchaseItem();
            it.categoryId = r.categoryId;
            it.categoryName = categoryName(r.categoryId);
            it.supplierProductId = r.product.id;
            it.productName = r.product.productName;
            it.spec = r.product.spec.isEmpty() ? r.product.packSpec() : r.product.spec;
            it.unit = r.product.unit;
            it.quantity = qty;
            it.historyPrice = r.hasHistory ? r.historyPrice : 0;
            it.unitPrice = parse(r.etCurrent.getText().toString());
            it.amount = FinanceDb.round2(
                    (it.unitPrice > 0 ? it.unitPrice : it.historyPrice) * qty);
            items.add(it);
        }
        if (items.isEmpty()) {
            Toast.makeText(this, R.string.finance_po_no_lines, Toast.LENGTH_SHORT).show();
            return;
        }

        String date = etDate.getText().toString().trim();
        if (date.length() != 10) {
            Toast.makeText(this, R.string.finance_po_pick_date, Toast.LENGTH_SHORT).show();
            return;
        }

        FinanceDb.FinPurchaseOrder o = new FinanceDb.FinPurchaseOrder();
        o.id = editingOrderId;
        o.batchId = batchId;
        o.date = date;
        o.supplierId = currentSupplierId;
        o.paid = paid;
        o.accountId = paid ? accountId : 0;
        o.remark = etRemark.getText().toString().trim();

        if (editingOrderId > 0) {
            FinanceDb.FinPurchaseOrder old = financeDb.getPurchaseOrder(editingOrderId);
            if (old != null) {
                o.orderNo = old.orderNo;
                o.paid = old.isPaid();
                o.accountId = old.accountId;
            }
        } else {
            o.orderNo = financeDb.nextOrderNo(date);
        }

        financeDb.savePurchaseOrder(o, items);
        Toast.makeText(this, R.string.finance_save_success, Toast.LENGTH_SHORT).show();
        finish();
    }

    private String categoryName(long catId) {
        for (FinanceDb.FinCategory c : categories) {
            if (c.id == catId) return c.name;
        }
        return "";
    }

    private static double parse(String s) {
        try {
            return s == null || s.trim().isEmpty() ? 0 : Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
