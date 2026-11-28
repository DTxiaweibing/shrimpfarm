package com.shrimpfarm.app.finance;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
        if (supplierId <= 0) {
            ProductFilingUi.pickSupplier(this, financeDb, (id, name) -> {
                supplierId = id;
                supplierName = name;
                setupToolbar(name);
                showAddDialog();
            });
            return;
        }
        // P2 feedback: 新增商品不需要关联已有商品，直接创建新商品
        ProductFilingUi.promptName(this, R.string.finance_quick_add_product, name -> {
            FinProduct p = new FinProduct();
            p.name = name;
            p.enabled = true;
            showSpecDialog(p);
        });
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

        bindCategory(spCategory, 0);
        spUnit.setAdapter(ProductFilingUi.unitAdapter(this, product.unit));
        ProductFilingUi.bindPackUnit(this, spPackUnit, view, etPackQty, tvPackSpec, null);

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
            double packQty = ProductFilingUi.parseAmount(etPackQty.getText().toString());
            double price = ProductFilingUi.parseAmount(etPrice.getText().toString());
            String spec = etSpec.getText().toString().trim();

            if (!ensureCategory(spCategory, catId)) return;
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

            long productId = product.id;
            if (productId <= 0) {
                productId = financeDb.insertProduct(product.name, unit);
                if (productId <= 0) { Toast.makeText(this, R.string.finance_save_failed, Toast.LENGTH_SHORT).show(); return; }
                financeDb.setProductCategories(productId, java.util.Collections.singletonList(catId));
            }
            financeDb.insertSupplierProduct(supplierId, productId, product.name, spec,
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

        bindCategory(spCategory, sp.categoryId);
        spUnit.setAdapter(ProductFilingUi.unitAdapter(this, sp.unit));
        ProductFilingUi.bindPackUnit(this, spPackUnit, view, etPackQty, tvPackSpec, sp.packUnit);

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
            double packQty = ProductFilingUi.parseAmount(etPackQty.getText().toString());
            double price = ProductFilingUi.parseAmount(etPrice.getText().toString());
            String spec = etSpec.getText().toString().trim();

            if (!ensureCategory(spCategory, catId)) return;
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

    private void bindCategory(final Spinner sp, long categoryId) {
        ProductFilingUi.bindCategorySpinner(this, sp, categories, categoryId, financeDb, id -> {
            categories = financeDb.getCategories();
            bindCategory(sp, id);
        });
    }

    private boolean ensureCategory(Spinner sp, long catId) {
        if (catId > 0) return true;
        ProductFilingUi.quickAddCategory(this, financeDb, id -> {
            categories = financeDb.getCategories();
            bindCategory(sp, id);
        });
        return false;
    }

    /** 鍖呰鍗曚綅涓嬫媺锛氱 0 椤逛负銆屾棤銆嶏紝鍚庨潰鏄唴缃崟浣?*/
    private long selectedCategoryId(Spinner sp) {
        return ProductFilingOptions.categoryIdAt(categories, sp.getSelectedItemPosition());
    }

    private String selectedText(Spinner sp) {
        Object o = sp.getSelectedItem();
        if (o == null) return "";
        String s = o.toString().trim();
        return s.equals(getString(R.string.finance_purchase_no_pack_unit)) ? "" : s;
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
            tvSpec.setText(ProductFilingUi.productDetail(sp));
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