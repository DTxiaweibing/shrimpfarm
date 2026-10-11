package com.shrimpfarm.app.finance;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.R;

import java.util.List;

public class PurchaseActivity extends BaseActivity {

    private FinanceDb financeDb;
    private SharedPreferences prefs;
    private String currentBatchId;
    private String keyword = "";

    private ListView listView;
    private TextView tvEmpty;
    private TextView tvPaid;
    private TextView tvPayable;
    private TextView tvPayableEstimate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_purchase);
        financeDb = new FinanceDb(this);
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentBatchId = prefs.getString("current_batch_id", "");

        setupToolbar();
        listView = findViewById(R.id.list_view);
        tvEmpty = findViewById(R.id.tv_empty);
        tvPaid = findViewById(R.id.tv_paid);
        tvPayable = findViewById(R.id.tv_payable);
        tvPayableEstimate = findViewById(R.id.tv_payable_estimate);

        setupSearch();
        findViewById(R.id.btn_add).setOnClickListener(v ->
                startActivity(new Intent(this, PurchaseEditActivity.class)));
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
            getSupportActionBar().setTitle(R.string.finance_purchase);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupSearch() {
        EditText etSearch = findViewById(R.id.et_search);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                keyword = s.toString();
                loadData();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        currentBatchId = prefs.getString("current_batch_id", "");
        List<FinanceDb.FinPurchaseOrder> list = financeDb.getPurchaseOrders(currentBatchId, keyword);

        double paid = 0, payable = 0, estimate = 0;
        for (FinanceDb.FinPurchaseOrder o : list) {
            if (o.isPaid()) {
                paid += o.totalAmount;
            } else {
                List<FinanceDb.FinPurchaseItem> items = financeDb.getPurchaseItems(o.id);
                double est = 0;
                for (FinanceDb.FinPurchaseItem it : items) {
                    if (!it.hasPrice()) est += it.amount;
                }
                estimate += est;
                payable += o.totalAmount - est;
            }
        }
        tvPaid.setText(FinanceDb.round2(paid) + "");
        tvPayable.setText(FinanceDb.round2(payable) + "");
        tvPayableEstimate.setText(FinanceDb.round2(estimate) + "");

        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        listView.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        listView.setAdapter(new PurchaseAdapter(list));
    }

    private void showItemMenu(final FinanceDb.FinPurchaseOrder o) {
        String settleLabel = o.isPaid()
                ? getString(R.string.finance_purchase_unsettle)
                : getString(R.string.finance_settle_confirm);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(o.orderNo + " · " + o.supplierName)
                .setItems(new String[]{
                        settleLabel,
                        getString(R.string.finance_edit),
                        getString(R.string.finance_delete)}, (d, which) -> {
                    if (which == 0) {
                        if (o.isPaid()) {
                            financeDb.setOrderPaid(o.id, false, 0);
                            loadData();
                        } else {
                            settleDialog(o);
                        }
                    } else if (which == 1) {
                        Intent i = new Intent(this, PurchaseEditActivity.class);
                        i.putExtra("order_id", o.id);
                        startActivity(i);
                    } else deleteConfirm(o);
                }).show();
    }

    private void settleDialog(final FinanceDb.FinPurchaseOrder o) {
        if (o.totalAmount <= 0) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setMessage(R.string.finance_purchase_settle_no_amount)
                    .setPositiveButton(R.string.btn_ok, null).show();
            return;
        }
        final List<FinanceDb.FinAccount> accounts = financeDb.getPaymentAccounts();
        if (accounts.isEmpty()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setMessage(R.string.finance_purchase_no_account)
                    .setPositiveButton(R.string.btn_ok, null).show();
            return;
        }
        final String[] names = new String[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) names[i] = accounts.get(i).name;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.finance_account_pay)
                        + " ¥" + PurchaseItemRowView.fmt(o.totalAmount))
                .setItems(names, (d, which) -> {
                    financeDb.setOrderPaid(o.id, true, accounts.get(which).id);
                    loadData();
                }).show();
    }

    private void deleteConfirm(final FinanceDb.FinPurchaseOrder o) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage(R.string.finance_po_delete_msg)
                .setPositiveButton(R.string.finance_delete, (d, w) -> {
                    financeDb.deletePurchaseOrder(o.id);
                    loadData();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private class PurchaseAdapter extends BaseAdapter {
        private final List<FinanceDb.FinPurchaseOrder> data;

        PurchaseAdapter(List<FinanceDb.FinPurchaseOrder> data) {
            this.data = data;
        }

        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int i) { return data.get(i); }
        @Override public long getItemId(int i) { return data.get(i).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(PurchaseActivity.this)
                        .inflate(R.layout.item_finance_purchase, parent, false);
            }
            final FinanceDb.FinPurchaseOrder o = data.get(position);
            TextView tvSupplier = v.findViewById(R.id.tv_supplier);
            TextView tvAmount = v.findViewById(R.id.tv_amount);
            TextView tvOrderNo = v.findViewById(R.id.tv_order_no);
            TextView tvDate = v.findViewById(R.id.tv_date);
            TextView tvPreview = v.findViewById(R.id.tv_preview);
            TextView tvItemCount = v.findViewById(R.id.tv_item_count);
            TextView tvAccount = v.findViewById(R.id.tv_account);
            TextView tvSettle = v.findViewById(R.id.tv_settle);

            tvSupplier.setText(o.supplierName);
            tvAmount.setText("¥" + PurchaseItemRowView.fmt(o.totalAmount));
            tvOrderNo.setText(o.orderNo);
            tvDate.setText(o.date);
            tvItemCount.setText(getString(R.string.finance_po_line_count_value, o.itemCount));

            List<FinanceDb.FinPurchaseItem> items = financeDb.getPurchaseItems(o.id);
            StringBuilder sb = new StringBuilder();
            // 暂估判定需覆盖全部明细，预览只取前三条
            boolean hasEstimate = false;
            for (FinanceDb.FinPurchaseItem it : items) {
                if (!it.hasPrice()) hasEstimate = true;
            }
            for (int i = 0; i < items.size() && i < 3; i++) {
                if (i > 0) sb.append("，");
                sb.append(items.get(i).productName);
                if (!items.get(i).unit.isEmpty()) {
                    sb.append(" ").append(PurchaseItemRowView.trimNum(items.get(i).quantity))
                            .append(items.get(i).unit);
                }
            }
            if (items.size() > 3) sb.append(" 等").append(items.size()).append("项");
            tvPreview.setText(sb.toString());

            if (o.isPaid()) {
                tvAccount.setText(o.accountName);
            } else {
                tvAccount.setText(hasEstimate
                        ? getString(R.string.finance_purchase_badge_estimate)
                        : getString(R.string.finance_purchase_badge_payable));
            }

            if (o.isPaid()) {
                tvSettle.setText(R.string.finance_purchase_badge_paid);
                tvSettle.setTextColor(0xFF2196F3);
                tvSettle.setBackgroundColor(0xFFE3F2FD);
            } else if (hasEstimate) {
                tvSettle.setText(R.string.finance_purchase_badge_estimate);
                tvSettle.setTextColor(0xFFFB8C00);
                tvSettle.setBackgroundColor(0xFFFFF3E0);
            } else {
                tvSettle.setText(R.string.finance_purchase_badge_payable);
                tvSettle.setTextColor(0xFFE53935);
                tvSettle.setBackgroundColor(0xFFFFEBEE);
            }

            v.setOnClickListener(x -> {
                startActivity(new Intent(PurchaseActivity.this, PurchaseOrderActivity.class)
                        .putExtra("order_id", o.id));
            });
            v.setOnLongClickListener(x -> {
                showItemMenu(o);
                return true;
            });
            return v;
        }
    }
}
