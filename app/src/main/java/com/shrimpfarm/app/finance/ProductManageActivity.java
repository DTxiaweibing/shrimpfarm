package com.shrimpfarm.app.finance;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.shrimpfarm.app.R;
import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.finance.FinanceDb.FinProduct;

import java.util.ArrayList;
import java.util.List;

public class ProductManageActivity extends BaseActivity {

    private FinanceDb financeDb;
    private List<FinProduct> productList = new ArrayList<>();
    private ListView listView;
    private ProductAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_product_manage);
        financeDb = new FinanceDb(this);
        setupToolbar();
        listView = findViewById(R.id.list_view);
        adapter = new ProductAdapter();
        listView.setAdapter(adapter);
        ((TextView) findViewById(R.id.tv_tip)).setText(R.string.finance_product_filing_tip);
        findViewById(R.id.btn_add).setOnClickListener(v -> openSupplierFiling());
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
            getSupportActionBar().setTitle(R.string.finance_product_manage);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadData() {
        productList = financeDb.getProducts();
        adapter.notifyDataSetChanged();
    }

    /** A2: product filing always starts from a supplier; quick-add is offered in the picker. */
    private void openSupplierFiling() {
        ProductFilingUi.pickSupplier(this, financeDb, (id, name) -> {
            Intent intent = new Intent(this, SupplierProductActivity.class);
            intent.putExtra("supplierId", id);
            intent.putExtra("supplierName", name);
            startActivity(intent);
        });
    }

    private class ProductAdapter extends BaseAdapter {
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
                convertView = LayoutInflater.from(ProductManageActivity.this)
                        .inflate(R.layout.item_finance_product, parent, false);
            }
            final FinProduct p = productList.get(position);
            TextView tvName = convertView.findViewById(R.id.tv_name);
            TextView tvUnit = convertView.findViewById(R.id.tv_unit);
            TextView tvCats = convertView.findViewById(R.id.tv_categories);
            TextView tvStatus = convertView.findViewById(R.id.tv_status);

            tvName.setText(p.name);
            tvUnit.setText(getString(R.string.finance_unit) + ": " + p.unit);
            tvCats.setText(getString(R.string.finance_category) + ": "
                    + (p.categoryNames.isEmpty() ? "—" : p.categoryNames));
            tvStatus.setText(p.enabled ? "" : "✕ " + getString(R.string.finance_disable));
            tvStatus.setTextColor(0xFFFF6F00);
            convertView.setAlpha(p.enabled ? 1f : 0.55f);

            TextView tvDelete = convertView.findViewById(R.id.tv_delete);
            TextView tvToggle = convertView.findViewById(R.id.tv_toggle);
            TextView tvEdit = convertView.findViewById(R.id.tv_edit);

            tvDelete.setOnClickListener(v -> new AlertDialog.Builder(ProductManageActivity.this)
                    .setMessage(R.string.finance_confirm_delete)
                    .setPositiveButton(R.string.btn_ok, (d, w) -> {
                        financeDb.setProductEnabled(p.id, false);
                        loadData();
                    })
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show());

            tvToggle.setText(p.enabled ? R.string.finance_disable : R.string.finance_enable);
            tvToggle.setOnClickListener(v -> {
                if (p.enabled) {
                    new AlertDialog.Builder(ProductManageActivity.this)
                            .setMessage(R.string.finance_confirm_disable)
                            .setPositiveButton(R.string.btn_ok, (d, w) -> {
                                financeDb.setProductEnabled(p.id, false);
                                loadData();
                            })
                            .setNegativeButton(R.string.btn_cancel, null)
                            .show();
                } else {
                    financeDb.setProductEnabled(p.id, true);
                    loadData();
                }
            });

            tvEdit.setText(R.string.finance_add_product);
            tvEdit.setOnClickListener(v -> openSupplierFiling());
            return convertView;
        }
    }
}