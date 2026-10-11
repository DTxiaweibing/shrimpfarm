package com.shrimpfarm.app.finance;

import android.app.AlertDialog;
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

import androidx.appcompat.widget.Toolbar;

import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.finance.FinanceDb.FinCategory;
import com.shrimpfarm.app.finance.FinanceDb.FinProduct;
import com.shrimpfarm.app.finance.FinanceDb.FinSupplier;
import com.shrimpfarm.app.finance.FinanceDb.FinSupplierProduct;
import com.shrimpfarm.app.finance.FinanceDb.FinTicket;
import com.shrimpfarm.app.finance.FinanceDb.FinTicketItem;
import com.shrimpfarm.app.finance.FinanceDb.PriceHint;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 动保开单：#316 单据样式。
 * 每条明细占 4 列 x 2 行 ——
 * 上排：品类 / 规格 / 数量 / 金额
 * 下排：品名 / 历史价 / 当前价 / 涨跌幅
 * 明细按需点按钮添加，不预置空行。
 */
public class TicketEditActivity extends BaseActivity {

    private FinanceDb financeDb;
    private SharedPreferences prefs;
    private String currentBatchId;
    private FinTicket existing;

    private Spinner spSupplier;
    private Spinner spAccount;
    private Spinner spSettled;
    private TextView tvDate;
    private EditText etTicketNo;
    private EditText etRemark;
    private LinearLayout llRows;
    private TextView tvLineCount;
    private TextView tvTotal;
    private TextView tvLinesEmpty;

    private final List<FinSupplier> suppliers = new ArrayList<>();
    private final List<FinCategory> categories = new ArrayList<>();
    private final List<FinanceDb.FinAccount> accounts = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();

    private long currentSupplierId = 0;
    private long editingSupplierId = 0;
    /** 已有明细归属的供应商，切换时提示清空 */
    private long linesSupplierId = 0;
    private boolean editing;
    /** 已结算 / 未结算 */
    private boolean settled = false;
    /** 付款账户（fin_payment_account.id），0 = 未指定 */
    private long accountId = 0;

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_home;
    }

    /** 网格里的一条明细（两行） */
    private static final class Row {
        View view;
        Spinner spCat;
        TextView tvSpec;
        EditText etQty;
        TextView tvAmount;
        TextView btnDel;
        Spinner spName;
        TextView tvHistory;
        EditText etCurrent;
        TextView tvChange;

        long categoryId = 0;
        /** 选中的全局商品 */
        FinProduct product;
        /** 解析出的供应商商品，落库用 */
        FinSupplierProduct sp;
        List<FinProduct> productList = new ArrayList<>();
        double historyPrice = 0;
        boolean hasHistory = false;
        /** 回填期间抑制联动，避免递归 */
        boolean binding = false;

        boolean used() {
            return product != null
                    || parse(etQty.getText().toString()) > 0
                    || parse(etCurrent.getText().toString()) > 0;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_ticket_edit);
        financeDb = new FinanceDb(this);
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentBatchId = prefs.getString("current_batch_id", "");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        spSupplier = findViewById(R.id.sp_supplier);
        spAccount = findViewById(R.id.sp_account);
        spSettled = findViewById(R.id.sp_settled);
        tvDate = findViewById(R.id.tv_date);
        etTicketNo = findViewById(R.id.et_ticket_no);
        etRemark = findViewById(R.id.et_remark);
        llRows = findViewById(R.id.ll_rows);
        tvLineCount = findViewById(R.id.tv_line_count);
        tvTotal = findViewById(R.id.tv_total);
        tvLinesEmpty = findViewById(R.id.tv_lines_empty);

        findViewById(R.id.btn_manage_supplier)
                .setOnClickListener(v -> startActivity(new Intent(this, SupplierManageActivity.class)));
        findViewById(R.id.btn_add_line).setOnClickListener(v -> addRow());
        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        tvDate.setOnClickListener(v -> pickDate());

        loadCategories();
        // 先读出原票据，拿到它的供应商，再建下拉 —— 否则下拉会停在第一家供应商上
        loadEditTarget();
        setupSupplierSpinner();
        setupSettleSpinner();
        setupAccountSpinner();

        if (!editing) {
            tvDate.setText(new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA)
                    .format(Calendar.getInstance().getTime()));
        }

        // 不预置一堆空行，只给一行，按钮再加
        if (rows.isEmpty()) addRow();
        refreshSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupSupplierSpinner();
        // 从支付账户管理页返回，账户列表可能有变动
        setupAccountSpinner();
        if (!editing && suppliers.isEmpty()) {
            Toast.makeText(this, R.string.finance_supplier_pick, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        if (editing) getMenuInflater().inflate(R.menu.menu_finance_ticket, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_delete && existing != null) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.finance_confirm_delete)
                    .setPositiveButton(R.string.btn_ok, (d, w) -> {
                        financeDb.deleteTicket(existing.id);
                        finish();
                    })
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ==================== 抬头 ====================

    private void loadCategories() {
        categories.clear();
        for (FinCategory c : financeDb.getCategories()) {
            if (c.name == null || c.name.trim().isEmpty()) continue;
            if (c.enabled || c.isCustom) categories.add(c);
        }
    }

    private void setupSupplierSpinner() {
        long keep = currentSupplierId > 0 ? currentSupplierId : editingSupplierId;
        suppliers.clear();
        for (FinSupplier s : financeDb.getSuppliers()) {
            if (s.enabled) suppliers.add(s);
        }

        List<String> names = new ArrayList<>();
        for (FinSupplier s : suppliers) names.add(s.name);
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
                if (!isEmptyTicket() && linesSupplierId > 0 && picked != linesSupplierId) {
                    new AlertDialog.Builder(TicketEditActivity.this)
                            .setMessage(R.string.finance_po_switch_supplier_tip)
                            .setPositiveButton(R.string.finance_po_clear_lines, (d, w) -> {
                                clearLines();
                                applySupplier(picked);
                            })
                            .setNegativeButton(R.string.btn_cancel, null)
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

    /** 结算行第 2 个：已结算 / 未结算。未结算时付款账户置灰 */
    private void setupSettleSpinner() {
        List<String> opts = new ArrayList<>();
        opts.add(getString(R.string.finance_picking_unsettled));
        opts.add(getString(R.string.finance_picking_settled));
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, opts);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSettled.setAdapter(ad);
        spSettled.setSelection(settled ? 1 : 0);
        applyAccountEnabled();
        spSettled.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                settled = pos == 1;
                applyAccountEnabled();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });
    }

    /** 未结算时账户无意义，置灰并按“未指定”保存 */
    private void applyAccountEnabled() {
        spAccount.setEnabled(settled);
        spAccount.setAlpha(settled ? 1f : 0.4f);
    }

    /** 结算行第 1 个：付款账户，从支付账户管理里已添加的账户中选 */
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
        applyAccountEnabled();
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

    private void applySupplier(long picked) {
        if (currentSupplierId == picked) return;
        currentSupplierId = picked;
        // 换供应商后商品要重新解析
        for (Row r : rows) {
            r.sp = null;
            r.tvSpec.setText("");
            r.etCurrent.setText("");
            r.tvHistory.setText("");
            r.tvChange.setText("");
            r.tvAmount.setText("");
            r.hasHistory = false;
            r.historyPrice = 0;
            bindCategory(r);
            fillProducts(r);
        }
        refreshSummary();
    }

    private void pickDate() {
        Calendar cal = Calendar.getInstance();
        String cur = tvDate.getText().toString().trim();
        try {
            if (cur.length() >= 10) {
                cal.set(Integer.parseInt(cur.substring(0, 4)),
                        Integer.parseInt(cur.substring(5, 7)) - 1,
                        Integer.parseInt(cur.substring(8, 10)));
            }
        } catch (Exception ignored) {
        }
        new android.app.DatePickerDialog(this,
                (v, y, m, d) -> tvDate.setText(
                        String.format(Locale.CHINA, "%d/%02d/%02d", y, m + 1, d)),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ==================== 编辑已有票据 ====================

    private void loadEditTarget() {
        long ticketId = getIntent().getLongExtra("ticketId", -1);
        if (ticketId <= 0) return;
        existing = financeDb.getTicket(ticketId);
        if (existing == null) return;

        editing = true;
        editingSupplierId = existing.supplierId;
        currentSupplierId = existing.supplierId;
        tvDate.setText(existing.date);
        etTicketNo.setText(existing.ticketNo);
        settled = existing.settled;
        accountId = existing.accountId;
        etRemark.setText(existing.remark);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.finance_edit);
        }

        for (FinTicketItem it : existing.items) {
            Row r = addRow();
            bindExisting(r, it);
        }
        linesSupplierId = existing.supplierId;
    }

    private void bindExisting(Row r, FinTicketItem it) {
        FinSupplierProduct sp = financeDb.getSupplierProduct(it.supplierProductId);
        if (sp == null) return;

        r.binding = true;
        r.categoryId = sp.categoryId;
        selectCategory(r, sp.categoryId);

        List<FinProduct> list = new ArrayList<>();
        if (sp.categoryId > 0) list.addAll(financeDb.getProductsByCategory(sp.categoryId));
        FinProduct cur = financeDb.getProduct(sp.productId);
        if (cur != null && !containsProduct(list, cur.id)) list.add(cur);
        fillProducts(r, list);

        int idx = indexOfProduct(r, sp.productId);
        if (idx >= 0) {
            r.spName.setSelection(idx);
        }
        r.product = cur;
        r.sp = sp;

        applyHistory(r, false);
        r.etCurrent.setText(PurchaseItemRowView.trimNum(it.unitPrice));
        r.etQty.setText(PurchaseItemRowView.trimNum(it.quantity));
        r.binding = false;
        recalcRow(r);
    }

    private boolean containsProduct(List<FinProduct> list, long id) {
        for (FinProduct p : list) {
            if (p.id == id) return true;
        }
        return false;
    }

    // ==================== 明细行 ====================

    private Row addRow() {
        Row r = new Row();
        r.view = LayoutInflater.from(this)
                .inflate(R.layout.item_finance_ticket_line_edit, llRows, false);
        r.spCat = r.view.findViewById(R.id.sp_cat);
        r.tvSpec = r.view.findViewById(R.id.tv_spec);
        r.etQty = r.view.findViewById(R.id.et_qty);
        r.tvAmount = r.view.findViewById(R.id.tv_amount);
        r.btnDel = r.view.findViewById(R.id.btn_del);
        r.spName = r.view.findViewById(R.id.sp_name);
        r.tvHistory = r.view.findViewById(R.id.tv_history);
        r.etCurrent = r.view.findViewById(R.id.et_current);
        r.tvChange = r.view.findViewById(R.id.tv_change);

        llRows.addView(r.view);
        rows.add(r);

        bindCategory(r);
        fillProducts(r);
        wireRow(r);
        recalcRow(r);
        return r;
    }

    private void wireRow(final Row r) {
        r.spCat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (r.binding) return;
                if (pos == categories.size() + 1) {
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
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });

        r.spName.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (r.binding) return;
                List<FinProduct> list = r.productList;
                int size = list == null ? 0 : list.size();
                if (pos <= 0 || size == 0) {
                    clearProduct(r);
                    return;
                }
                if (pos == size + 1) {
                    // 末位是「添加品名」
                    promptAddProduct(r);
                    return;
                }
                r.product = list.get(pos - 1);
                if (resolveSupplierProduct(r)) {
                    applyHistory(r, true);
                } else {
                    clearProduct(r);
                    return;
                }
                recalcRow(r);
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
        r.etQty.addTextChangedListener(w);
        r.etCurrent.addTextChangedListener(w);

        r.tvHistory.setOnClickListener(v -> showHistory(r));

        r.btnDel.setOnClickListener(v -> {
            llRows.removeView(r.view);
            rows.remove(r);
            if (rows.isEmpty()) addRow();
            refreshSummary();
        });
    }

    /** 品类下拉：占位 + 全部品类 + 自定义项 + 自定义 */
    private void bindCategory(Row r) {
        List<String> opts = new ArrayList<>();
        opts.add(getString(R.string.finance_po_pick_category_hint));
        for (FinCategory c : categories) opts.add(c.name);
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
        List<FinProduct> list = new ArrayList<>();
        if (r.categoryId > 0) list.addAll(financeDb.getProductsByCategory(r.categoryId));
        fillProducts(r, list);
    }

    private void fillProducts(Row r, List<FinProduct> list) {
        List<String> opts = new ArrayList<>();
        opts.add(getString(R.string.finance_po_pick_name_hint));
        for (FinProduct p : list) {
            opts.add(p.name + (p.unit == null || p.unit.isEmpty() ? "" : " (" + p.unit + ")"));
        }
        opts.add(getString(R.string.finance_add_product));
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, opts);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        boolean prev = r.binding;
        r.binding = true;
        r.spName.setAdapter(ad);
        r.binding = prev;
        r.productList = list;
        r.product = null;
        r.sp = null;
    }

    private int indexOfProduct(Row r, long productId) {
        List<FinProduct> list = r.productList;
        if (list == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == productId) return i + 1;
        }
        return -1;
    }

    private void clearProduct(Row r) {
        r.product = null;
        r.sp = null;
        r.hasHistory = false;
        r.historyPrice = 0;
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

    /** 把全局商品挂到当前供应商下，拿到供应商商品 */
    private boolean resolveSupplierProduct(Row r) {
        if (currentSupplierId <= 0 || r.product == null || r.categoryId <= 0) {
            r.sp = null;
            return false;
        }
        long spId = financeDb.ensureSupplierProductByCategory(
                currentSupplierId, r.categoryId, r.product.id);
        if (spId <= 0) {
            Toast.makeText(this, R.string.finance_product_manage, Toast.LENGTH_SHORT).show();
            r.sp = null;
            return false;
        }
        r.sp = financeDb.getSupplierProduct(spId);
        return r.sp != null;
    }

    private void applyHistory(Row r, boolean fillCurrent) {
        r.hasHistory = false;
        r.historyPrice = 0;
        if (r.sp != null && r.sp.lastPrice > 0) {
            r.hasHistory = true;
            r.historyPrice = r.sp.lastPrice;
        }
        r.tvHistory.setText(r.hasHistory ? PurchaseItemRowView.fmt(r.historyPrice) : "—");
        r.tvSpec.setText(displaySpec(r.sp));
        if (fillCurrent && r.hasHistory) {
            r.etCurrent.setText(PurchaseItemRowView.trimNum(r.historyPrice));
        }
    }

    private static String displaySpec(FinSupplierProduct sp) {
        if (sp == null) return "";
        String pack = sp.packSpec();
        if (!pack.isEmpty()) return pack;
        return sp.spec == null ? "" : sp.spec;
    }

    private void showHistory(Row r) {
        if (r.sp == null) {
            Toast.makeText(this, R.string.finance_no_history, Toast.LENGTH_SHORT).show();
            return;
        }
        List<PriceHint> history = financeDb.getHistoryByProduct(r.sp.id, 5);
        if (history.isEmpty()) {
            Toast.makeText(this, R.string.finance_no_history, Toast.LENGTH_SHORT).show();
            return;
        }
        final List<String> lines = new ArrayList<>();
        final List<Double> prices = new ArrayList<>();
        for (PriceHint h : history) {
            String suffix = h.supplierName.isEmpty() || h.supplierName == null
                    ? "" : " · " + h.supplierName;
            lines.add(h.date + "  " + FinanceDb.round2(h.price) + suffix);
            prices.add(h.price);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.finance_history_price)
                .setItems(lines.toArray(new String[0]), (d, which) -> {
                    r.etCurrent.setText(PurchaseItemRowView.trimNum(prices.get(which)));
                    recalcRow(r);
                })
                .show();
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
            if (r.sp == null) continue;
            n++;
            double qty = parse(r.etQty.getText().toString());
            double cur = parse(r.etCurrent.getText().toString());
            double used = cur > 0 ? cur : (r.hasHistory ? r.historyPrice : 0);
            total += used * qty;
        }
        if (n > 0 && linesSupplierId == 0) linesSupplierId = currentSupplierId;
        tvTotal.setText("¥" + PurchaseItemRowView.fmt(FinanceDb.round2(total)));
        tvLineCount.setText(getString(R.string.finance_po_line_count_value, n));
        // 始终保留一行可录，所以只在真没行时提示
        tvLinesEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void clearLines() {
        for (Row r : new ArrayList<>(rows)) {
            llRows.removeView(r.view);
        }
        rows.clear();
        linesSupplierId = 0;
        if (rows.isEmpty()) addRow();
    }

    private boolean isEmptyTicket() {
        for (Row r : rows) {
            if (r.used()) return false;
        }
        return true;
    }

    // ==================== 自定义品类 / 新增品名 ====================

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
                    for (FinCategory c : categories) {
                        if (name.equals(c.name)) {
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
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void promptAddProduct(final Row r) {
        if (r.categoryId <= 0) {
            Toast.makeText(this, R.string.finance_po_pick_category_hint, Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);

        final EditText etName = new EditText(this);
        etName.setHint(R.string.finance_input_name);
        final EditText etUnit = new EditText(this);
        etUnit.setHint(R.string.finance_input_unit);
        box.addView(etName);
        box.addView(etUnit);

        new AlertDialog.Builder(this)
                .setTitle(R.string.finance_add_product)
                .setView(box)
                .setPositiveButton(R.string.btn_ok, (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) return;
                    long productId = financeDb.insertProduct(name, etUnit.getText().toString().trim());
                    if (productId <= 0) return;
                    List<Long> cats = new ArrayList<>();
                    cats.add(r.categoryId);
                    financeDb.setProductCategories(productId, cats);
                    fillProducts(r);
                    int idx = indexOfProduct(r, productId);
                    if (idx >= 0) r.spName.setSelection(idx);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    // ==================== 保存 ====================

    private void save() {
        if (currentSupplierId <= 0) {
            Toast.makeText(this, R.string.finance_po_pick_supplier, Toast.LENGTH_SHORT).show();
            return;
        }
        List<FinTicketItem> items = new ArrayList<>();
        for (Row r : rows) {
            if (r.sp == null) continue;
            double qty = parse(r.etQty.getText().toString());
            double price = parse(r.etCurrent.getText().toString());
            if (price <= 0 && r.hasHistory) price = r.historyPrice;
            if (qty <= 0 || price <= 0) continue;
            FinTicketItem it = new FinTicketItem();
            it.supplierProductId = r.sp.id;
            it.quantity = qty;
            it.unitPrice = price;
            items.add(it);
        }
        if (items.isEmpty()) {
            Toast.makeText(this, R.string.finance_detail, Toast.LENGTH_SHORT).show();
            return;
        }
        String date = tvDate.getText().toString().trim();
        if (date.length() != 10) {
            Toast.makeText(this, R.string.finance_po_pick_date, Toast.LENGTH_SHORT).show();
            return;
        }

        FinTicket t = new FinTicket();
        t.batchId = currentBatchId;
        t.supplierId = currentSupplierId;
        t.date = date;
        t.ticketNo = etTicketNo.getText().toString().trim();
        t.settled = settled;
        // 未结算时付款账户无意义，统一按未指定存
        t.accountId = settled ? accountId : 0;
        t.remark = etRemark.getText().toString().trim();

        if (editing && existing != null) {
            t.id = existing.id;
            financeDb.updateTicket(t, items);
        } else {
            financeDb.saveTicket(t, items);
        }
        Toast.makeText(this, R.string.finance_save_success, Toast.LENGTH_SHORT).show();
        finish();
    }

    private static double parse(String s) {
        try {
            return s == null || s.trim().isEmpty() ? 0 : Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}