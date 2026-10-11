package com.shrimpfarm.app.finance;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.shrimpfarm.app.R;
import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.finance.FinanceDb.FinCategory;
import com.shrimpfarm.app.finance.FinanceDb.FinProduct;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        findViewById(R.id.btn_add).setOnClickListener(v -> showEditDialog(null));
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

    private void showEditDialog(final FinProduct product) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        final EditText etName = new EditText(this);
        etName.setHint(R.string.finance_product);
        final EditText etUnit = new EditText(this);
        etUnit.setHint(R.string.finance_unit);
        layout.addView(etName);
        layout.addView(etUnit);

        if (product != null) {
            etName.setText(product.name);
            etUnit.setText(product.unit);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(product == null ? R.string.finance_add : R.string.finance_edit);
        builder.setView(layout);
        builder.setPositiveButton(R.string.btn_ok, (d, w) -> {
            String name = etName.getText().toString().trim();
            String unit = etUnit.getText().toString().trim();
            if (name.isEmpty()) return;
            if (product == null) {
                financeDb.insertProduct(name, unit);
            } else {
                financeDb.renameProduct(product.id, name, unit);
            }
            loadData();
        });
        builder.setNegativeButton(R.string.btn_cancel, null);
        builder.show();
    }

    private void showCategoryPicker(final FinProduct product) {
        List<FinCategory> categories = financeDb.getCategories();
        final List<String> items = new ArrayList<>();
        final List<Long> ids = new ArrayList<>();
        Set<Long> selected = new HashSet<>(product.categoryIds);
        final boolean[] checked = new boolean[categories.size()];
        for (int i = 0; i < categories.size(); i++) {
            FinCategory cat = categories.get(i);
            items.add(cat.name);
            ids.add(cat.id);
            checked[i] = selected.contains(cat.id);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.finance_category);
        builder.setMultiChoiceItems(items.toArray(new String[0]), checked,
                (dialog, which, isChecked) -> checked[which] = isChecked);
        builder.setPositiveButton(R.string.btn_ok, (d, w) -> {
            List<Long> picked = new ArrayList<>();
            for (int i = 0; i < checked.length; i++) {
                if (checked[i]) picked.add(ids.get(i));
            }
            financeDb.setProductCategories(product.id, picked);
            loadData();
        });
        builder.setNegativeButton(R.string.btn_cancel, null);
        builder.show();
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

            tvEdit.setOnClickListener(v -> new AlertDialog.Builder(ProductManageActivity.this)
                    .setTitle(R.string.finance_product_manage)
                    .setItems(new String[]{
                            getString(R.string.finance_edit),
                            getString(R.string.finance_category)
                    }, (d, which) -> {
                        if (which == 0) showEditDialog(p);
                        else showCategoryPicker(p);
                    })
                    .show());
            return convertView;
        }
    }
}