package com.shrimpfarm.app.finance;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.widget.Toolbar;
import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.finance.FinanceDb.FinCategory;
import com.shrimpfarm.app.finance.FinanceDb.FinSupplierProduct;
import java.util.*;

public class ProductManageActivity extends BaseActivity {
    @Override protected int getCurrentNavId() { return R.id.nav_home; }
    private FinanceDb financeDb;
    private final List<FinSupplierProduct> rows = new ArrayList<>();
    private List<FinCategory> categories = new ArrayList<>();
    private BaseAdapter adapter;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_product_manage);
        financeDb = new FinanceDb(this);
        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.finance_product_manage);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        tb.setNavigationOnClickListener(v -> finish());
        ListView lv = findViewById(R.id.list_view);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int i) { return rows.get(i); }
            @Override public long getItemId(int i) { return rows.get(i).id; }
            @Override public View getView(int i, View c, ViewGroup p) { return bindRow(c, p, rows.get(i)); }
        };
        lv.setAdapter(adapter);
        findViewById(R.id.btn_add).setOnClickListener(v -> ProductFilingUi.pickSupplier(this, financeDb,
                (sid, name) -> showSpecDialog(0, sid, null)));
        loadData();
    }

    private void loadData() { rows.clear(); rows.addAll(financeDb.getAllSupplierProducts()); categories = financeDb.getCategories(); adapter.notifyDataSetChanged(); }

    private View bindRow(View c, ViewGroup p, FinSupplierProduct sp) {
        if (c == null) c = LayoutInflater.from(this).inflate(R.layout.item_finance_supplier_product, p, false);
        TextView tvSupplier = c.findViewById(R.id.tv_supplier);
        tvSupplier.setVisibility(View.VISIBLE); tvSupplier.setText(sp.supplierName);
        ((TextView) c.findViewById(R.id.tv_name)).setText(sp.productName);
        ((TextView) c.findViewById(R.id.tv_spec)).setText(ProductFilingUi.productDetail(sp));
        ((TextView) c.findViewById(R.id.tv_price)).setText(sp.lastPrice > 0 ? getString(R.string.finance_history_price) + ": " + FinanceDb.round2(sp.lastPrice) : getString(R.string.finance_no_history));
        TextView tvStatus = c.findViewById(R.id.tv_status);
        tvStatus.setText(sp.enabled ? "" : "✗ " + getString(R.string.finance_disable));
        tvStatus.setTextColor(0xFFFF6F00); c.setAlpha(sp.enabled ? 1f : 0.55f);
        c.findViewById(R.id.tv_delete).setOnClickListener(v -> disable(sp));
        TextView tvToggle = c.findViewById(R.id.tv_toggle);
        tvToggle.setText(sp.enabled ? R.string.finance_disable : R.string.finance_enable);
        tvToggle.setOnClickListener(v -> { if (sp.enabled) disable(sp); else { financeDb.setSupplierProductEnabled(sp.id, true); loadData(); } });
        c.findViewById(R.id.tv_edit).setOnClickListener(v -> showSpecDialog(sp.id, sp.supplierId, sp));
        return c;
    }

    private void disable(FinSupplierProduct sp) {
        new AlertDialog.Builder(this).setMessage(R.string.finance_confirm_delete)
                .setPositiveButton(R.string.btn_ok, (d, w) -> { financeDb.setSupplierProductEnabled(sp.id, false); loadData(); })
                .setNegativeButton(R.string.btn_cancel, null).show();
    }

    private void showSpecDialog(long spId, long supplierId, FinSupplierProduct old) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_finance_supplier_product, null);
        EditText etName = new EditText(this); etName.setHint(R.string.finance_product_name_hint);
        ((ViewGroup) ((ViewGroup) view).getChildAt(0)).addView(etName, 0);
        EditText etSpec = view.findViewById(R.id.et_spec); Spinner spCat = view.findViewById(R.id.sp_category);
        Spinner spUnit = view.findViewById(R.id.sp_unit); Spinner spPack = view.findViewById(R.id.sp_pack_unit);
        EditText etPackQty = view.findViewById(R.id.et_pack_qty); EditText etPrice = view.findViewById(R.id.et_price);
        TextView tvPackSpec = view.findViewById(R.id.tv_pack_spec);
        bindCategory(spCat, old == null ? 0 : old.categoryId);
        spUnit.setAdapter(ProductFilingUi.unitAdapter(this, old == null ? "" : old.unit));
        ProductFilingUi.bindPackUnit(this, spPack, view, etPackQty, tvPackSpec, old == null ? null : old.packUnit);
        if (old != null) { etName.setText(old.productName); etSpec.setText(old.spec); etPrice.setText(FinanceDb.stripTrailingZeros(old.price)); if (old.packQty > 0) etPackQty.setText(FinanceDb.stripTrailingZeros(old.packQty)); }
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle(old == null ? R.string.finance_product : R.string.finance_edit).setView(view)
                .setPositiveButton(R.string.btn_ok, null).setNegativeButton(R.string.btn_cancel, null).create();
        dlg.setOnShowListener(d -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            long catId = ProductFilingOptions.categoryIdAt(categories, spCat.getSelectedItemPosition());
            String unit = sel(spUnit), packUnit = sel(spPack);
            double packQty = ProductFilingUi.parseAmount(etPackQty.getText().toString());
            double price = ProductFilingUi.parseAmount(etPrice.getText().toString());
            String spec = etSpec.getText().toString().trim();
            if (name.isEmpty()) { Toast.makeText(this, R.string.finance_product_name_required, Toast.LENGTH_SHORT).show(); return; }
            if (catId <= 0) { ProductFilingUi.quickAddCategory(this, financeDb, id -> { categories = financeDb.getCategories(); bindCategory(spCat, id); }); return; }
            if (unit.isEmpty()) { Toast.makeText(this, R.string.finance_purchase_pick_base_unit, Toast.LENGTH_SHORT).show(); return; }
            long pid = financeDb.insertProduct(name, unit);
            if (pid <= 0) { Toast.makeText(this, R.string.finance_save_failed, Toast.LENGTH_SHORT).show(); return; }
            financeDb.setProductCategories(pid, Collections.singletonList(catId));
            if (spId > 0) {
                financeDb.updateSupplierProduct(spId, name, spec, unit, packUnit, packQty <= 0 ? 1 : packQty, price);
                financeDb.setSupplierProductCategory(spId, catId);
                financeDb.setSupplierProductProductId(spId, pid);
            } else {
                financeDb.insertSupplierProduct(supplierId, pid, name, spec, catId, unit, packUnit, price, packQty);
            }
            dlg.dismiss(); loadData();
        }));
        dlg.show();
    }

    private void bindCategory(Spinner sp, long catId) { ProductFilingUi.bindCategorySpinner(this, sp, categories, catId, financeDb, id -> { categories = financeDb.getCategories(); bindCategory(sp, id); }); }
    private String sel(Spinner sp) { Object o = sp.getSelectedItem(); String s = o == null ? "" : o.toString().trim(); return s.equals(getString(R.string.finance_purchase_no_pack_unit)) ? "" : s; }
}