package com.shrimpfarm.app.finance;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.shrimpfarm.app.R;
import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.finance.FinanceDb.FinProduct;
import com.shrimpfarm.app.finance.FinanceDb.FinSupplierProduct;

import java.util.ArrayList;
import java.util.List;

public class SupplierProductActivity extends BaseActivity {

    private FinanceDb financeDb;
    private long supplierId;
    private String supplierName = "";
    private List<FinSupplierProduct> productList = new ArrayList<>();
    private List<FinanceDb.FinCategory> categories = new ArrayList<>();
    private ListView listView;
    private SupplierProductAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_supplier_product);
        financeDb = new FinanceDb(this);
        // 鍏煎涓ょ鍏ュ彛鐨?key锛氫緵搴斿晢绠＄悊椤电敤 supplierId锛岃繘璐у紑鍗曢〉鐢?supplier_id
        supplierId = getIntent().getLongExtra("supplierId", 0);
        if (supplierId <= 0) supplierId = getIntent().getLongExtra("supplier_id", 0);
        supplierName = getIntent().getStringExtra("supplierName");
        if (supplierName == null) supplierName = "";
        setupToolbar(supplierName);
        listView = findViewById(R.id.list_view);
        adapter = new SupplierProductAdapter();
        listView.setAdapter(adapter);
        findViewById(R.id.btn_add).setOnClickListener(v -> showAddDialog());
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

    private void setupToolbar(String supplierName) {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            String title = supplierName == null ? getString(R.string.finance_supplier_product_manage)
                    : getString(R.string.finance_supplier_product_manage) + " - " + supplierName;
            getSupportActionBar().setTitle(title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadData() {
        productList = financeDb.getSupplierProducts(supplierId);
        categories = financeDb.getCategories();
        if (supplierName.isEmpty() && supplierId > 0) {
            for (FinanceDb.FinSupplier s : financeDb.getSuppliers()) {
                if (s.id == supplierId) {
                    supplierName = s.name;
                    setupToolbar(supplierName);
                    break;
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void showAddDialog() {
        List<FinProduct> standard = financeDb.getProducts();
        final List<FinProduct> enabled = new ArrayList<>();
        for (FinProduct p : standard) if (p.enabled) enabled.add(p);
        final List<String> names = new ArrayList<>();
        for (FinProduct p : enabled) names.add(p.name + (p.unit.isEmpty() ? "" : " (" + p.unit + ")"));
        if (names.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.finance_product_manage)
                    .setPositiveButton(R.string.btn_ok, null)
                    .show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.finance_product);
        builder.setItems(names.toArray(new String[0]), (d, which) -> showSpecDialog(enabled.get(which)));
        builder.setNegativeButton(R.string.btn_cancel, null);
        builder.show();
    }

    /** 鍝佺被 / 鍙屽崟浣?/ 鍗曚环 寤烘。 */
    private void showSpecDialog(final FinProduct product) {
        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_finance_supplier_product, null);
        final EditText etSpec = view.findViewById(R.id.et_spec);
        final Spinner spCategory = view.findViewById(R.id.sp_category);
        final Spinner spUnit = view.findViewById(R.id.sp_unit);
        final Spinner spPackUnit = view.findViewById(R.id.sp_pack_unit);
        final EditText etPackQty = view.findViewById(R.id.et_pack_qty);
        final EditText etPrice = view.findViewById(R.id.et_price);
        final TextView tvPackSpec = view.findViewById(R.id.tv_pack_spec);

        spCategory.setAdapter(categoryAdapter());
        bindCategory(spCategory, 0);
        spUnit.setAdapter(unitAdapter(product.unit));
        bindPackUnit(spPackUnit, view, etPackQty, tvPackSpec, null);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(product.name)
                .setView(view)
                .setPositiveButton(R.string.btn_ok, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long catId = selectedCategoryId(spCategory);
            String unit = selectedText(spUnit);
            String packUnit = selectedText(spPackUnit);
            double packQty = parse(etPackQty.getText().toString());
            double price = parse(etPrice.getText().toString());
            String spec = etSpec.getText().toString().trim();

            if (unit.isEmpty()) unit = product.unit;
            if (unit.isEmpty()) {
                Toast.makeText(this, R.string.finance_purchase_pick_base_unit, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!packUnit.isEmpty() && packUnit.equals(unit)) packUnit = "";
            if (packUnit.isEmpty()) packQty = 1;

            if (financeDb.supplierProductExists(supplierId, catId, product.name)) {
                Toast.makeText(this, R.string.finance_supplier_product_exists,
                        Toast.LENGTH_SHORT).show();
                return;
            }

            financeDb.insertSupplierProduct(supplierId, product.id, product.name, spec,
                    catId, unit, packUnit, price, packQty);
            dialog.dismiss();
            loadData();
        }));
        dialog.show();
    }

    private void showEditSpecDialog(final FinSupplierProduct sp) {
        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_finance_supplier_product, null);
        final EditText etSpec = view.findViewById(R.id.et_spec);
        final Spinner spCategory = view.findViewById(R.id.sp_category);
        final Spinner spUnit = view.findViewById(R.id.sp_unit);
        final Spinner spPackUnit = view.findViewById(R.id.sp_pack_unit);
        final EditText etPackQty = view.findViewById(R.id.et_pack_qty);
        final EditText etPrice = view.findViewById(R.id.et_price);
        final TextView tvPackSpec = view.findViewById(R.id.tv_pack_spec);

        etSpec.setText(sp.spec);
        if (sp.price > 0) etPrice.setText(String.valueOf(sp.price));
        if (sp.packQty > 0) etPackQty.setText(String.valueOf(sp.packQty));

        spCategory.setAdapter(categoryAdapter());
        bindCategory(spCategory, sp.categoryId);
        spUnit.setAdapter(unitAdapter(sp.unit));
        bindPackUnit(spPackUnit, view, etPackQty, tvPackSpec, sp.packUnit);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(sp.productName)
                .setView(view)
                .setPositiveButton(R.string.btn_ok, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long catId = selectedCategoryId(spCategory);
            String unit = selectedText(spUnit);
            String packUnit = selectedText(spPackUnit);
            double packQty = parse(etPackQty.getText().toString());
            double price = parse(etPrice.getText().toString());
            String spec = etSpec.getText().toString().trim();

            if (unit.isEmpty()) {
                Toast.makeText(this, R.string.finance_purchase_pick_base_unit, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!packUnit.isEmpty() && packUnit.equals(unit)) packUnit = "";
            if (packUnit.isEmpty()) packQty = 1;

            financeDb.updateSupplierProduct(sp.id, sp.productName, spec, unit, packUnit, packQty, price);
            financeDb.setSupplierProductCategory(sp.id, catId);
            dialog.dismiss();
            loadData();
        }));
        dialog.show();
    }

    // ---------------- 鍝佺被 / 鍗曚綅 涓嬫媺 ----------------

    private ArrayAdapter<String> categoryAdapter() {
        List<String> names = new ArrayList<>();
        for (FinanceDb.FinCategory c : categories) names.add(c.name);
        if (names.isEmpty()) names.add(getString(R.string.finance_choose_category));
        return new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
    }

    private ArrayAdapter<String> unitAdapter(String prefer) {
        String[] builtins = FinanceDb.getBuiltinUnits();
        List<String> units = new ArrayList<>();
        if (prefer != null && !prefer.isEmpty()) units.add(prefer);
        for (String u : builtins) if (!units.contains(u)) units.add(u);
        return new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, units);
    }

    private void bindCategory(Spinner sp, long categoryId) {
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).id == categoryId) {
                sp.setSelection(i);
                return;
            }
        }
    }

    /** 鍖呰鍗曚綅涓嬫媺锛氱 0 椤逛负銆屾棤銆嶏紝鍚庨潰鏄唴缃崟浣?*/
    private void bindPackUnit(final Spinner sp, final View parent, final EditText etQty,
                              final TextView tvSpec, String current) {
        String[] builtins = FinanceDb.getBuiltinUnits();
        List<String> units = new ArrayList<>();
        units.add(getString(R.string.finance_purchase_no_pack_unit));
        for (String u : builtins) units.add(u);
        sp.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, units));
        if (current != null && !current.isEmpty()) {
            for (int i = 0; i < units.size(); i++) {
                if (units.get(i).equals(current)) {
                    sp.setSelection(i);
                    break;
                }
            }
        }
        refreshPackSpec(parent, sp, etQty, tvSpec);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View view, int position, long id) {
                refreshPackSpec(parent, sp, etQty, tvSpec);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) { }
        });
    }

    private void refreshPackSpec(View parent, Spinner spPackUnit, EditText etQty, TextView tvSpec) {
        String unit = selectedText((Spinner) parent.findViewById(R.id.sp_unit));
        String packUnit = selectedText(spPackUnit);
        double packQty = parse(etQty.getText().toString());
        if (packUnit.isEmpty() || packUnit.equals(getString(R.string.finance_purchase_no_pack_unit))
                || packQty <= 1) {
            tvSpec.setVisibility(View.GONE);
            return;
        }
        tvSpec.setVisibility(View.VISIBLE);
        tvSpec.setText(getString(R.string.finance_purchase_pack_spec_fmt,
                FinanceDb.stripTrailingZeros(packQty) + unit, packUnit));
    }

    private long selectedCategoryId(Spinner sp) {
        int i = sp.getSelectedItemPosition();
        if (i < 0 || i >= categories.size()) return 0;
        return categories.get(i).id;
    }

    private String selectedText(Spinner sp) {
        Object o = sp.getSelectedItem();
        if (o == null) return "";
        String s = o.toString().trim();
        return s.equals(getString(R.string.finance_purchase_no_pack_unit)) ? "" : s;
    }

    private static double parse(String s) {
        try {
            if (s == null || s.trim().isEmpty()) return 0;
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private class SupplierProductAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return productList.size();
        }

        @Override
        public Object getItem(int position) {
            return productList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return productList.get(position).id;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(SupplierProductActivity.this)
                        .inflate(R.layout.item_finance_supplier_product, parent, false);
            }
            final FinSupplierProduct sp = productList.get(position);
            TextView tvName = convertView.findViewById(R.id.tv_name);
            TextView tvSpec = convertView.findViewById(R.id.tv_spec);
            TextView tvPrice = convertView.findViewById(R.id.tv_price);
            TextView tvStatus = convertView.findViewById(R.id.tv_status);

            tvName.setText(sp.productName);
            StringBuilder spec = new StringBuilder();
            if (!sp.categoryName.isEmpty()) spec.append(sp.categoryName);
            if (!sp.spec.isEmpty()) {
                if (spec.length() > 0) spec.append(" 路 ");
                spec.append(sp.spec);
            }
            if (!sp.unit.isEmpty()) {
                if (spec.length() > 0) spec.append(" 路 ");
                spec.append("楼").append(FinanceDb.stripTrailingZeros(sp.price))
                        .append("/").append(sp.unit);
            }
            if (!sp.packUnit.isEmpty()) {
                spec.append(" (").append(sp.packSpec()).append(")");
            }
            tvSpec.setText(spec.toString());
            if (sp.lastPrice > 0) {
                double rounded = FinanceDb.round2(sp.lastPrice);
                tvPrice.setText(getString(R.string.finance_history_price) + ": " + rounded);
            } else {
                tvPrice.setText(R.string.finance_no_history);
            }
            tvStatus.setText(sp.enabled ? "" : "鉁?" + getString(R.string.finance_disable));
            tvStatus.setTextColor(0xFFFF6F00);
            convertView.setAlpha(sp.enabled ? 1f : 0.55f);

            TextView tvDelete = convertView.findViewById(R.id.tv_delete);
            TextView tvToggle = convertView.findViewById(R.id.tv_toggle);
            TextView tvEdit = convertView.findViewById(R.id.tv_edit);

            tvDelete.setOnClickListener(v -> new AlertDialog.Builder(SupplierProductActivity.this)
                    .setMessage(R.string.finance_confirm_delete)
                    .setPositiveButton(R.string.btn_ok, (d, w) -> {
                        financeDb.setSupplierProductEnabled(sp.id, false);
                        loadData();
                    })
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show());

            tvToggle.setText(sp.enabled ? R.string.finance_disable : R.string.finance_enable);
            tvToggle.setOnClickListener(v -> {
                if (sp.enabled) {
                    new AlertDialog.Builder(SupplierProductActivity.this)
                            .setMessage(R.string.finance_confirm_disable)
                            .setPositiveButton(R.string.btn_ok, (d, w) -> {
                                financeDb.setSupplierProductEnabled(sp.id, false);
                                loadData();
                            })
                            .setNegativeButton(R.string.btn_cancel, null)
                            .show();
                } else {
                    financeDb.setSupplierProductEnabled(sp.id, true);
                    loadData();
                }
            });

            tvEdit.setOnClickListener(v -> showEditSpecDialog(sp));
            return convertView;
        }
    }
}