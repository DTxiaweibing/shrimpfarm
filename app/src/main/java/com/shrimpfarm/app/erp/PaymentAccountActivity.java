package com.shrimpfarm.app.erp;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.shrimpfarm.app.R;
import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.finance.FinanceDb;
import com.shrimpfarm.app.finance.FinanceDb.FinAccount;

import java.util.ArrayList;
import java.util.List;

public class PaymentAccountActivity extends BaseActivity {

    private FinanceDb financeDb;
    private List<FinAccount> accountList = new ArrayList<>();
    private AccountAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_payment_account);
        financeDb = new FinanceDb(this);
        setupToolbar();
        ListView listView = findViewById(R.id.list_view);
        adapter = new AccountAdapter();
        listView.setAdapter(adapter);
        findViewById(R.id.btn_add).setOnClickListener(v -> showAddDialog());
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
            getSupportActionBar().setTitle(R.string.finance_payment_account_manage);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadData() {
        accountList = financeDb.getPaymentAccounts();
        adapter.notifyDataSetChanged();
    }

    private void showAddDialog() {
        final EditText et = new EditText(this);
        et.setHint(R.string.finance_input_account);
        new AlertDialog.Builder(this)
                .setTitle(R.string.finance_add_account)
                .setView(et)
                .setPositiveButton(R.string.btn_ok, (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.finance_input_account, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    financeDb.insertPaymentAccount(name);
                    loadData();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private class AccountAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return accountList.size();
        }

        @Override
        public Object getItem(int position) {
            return accountList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return accountList.get(position).id;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(PaymentAccountActivity.this)
                        .inflate(android.R.layout.simple_list_item_2, parent, false);
            }
            final FinAccount a = accountList.get(position);
            TextView tvName = convertView.findViewById(android.R.id.text1);
            TextView tvStatus = convertView.findViewById(android.R.id.text2);
            tvName.setText(a.name);
            tvStatus.setText(getString(a.enabled ? R.string.finance_enable : R.string.finance_disable)
                    + "  ·  " + getString(R.string.finance_edit));
            convertView.setAlpha(a.enabled ? 1f : 0.55f);
            convertView.setOnClickListener(v -> {
                final String[] actions = {
                        getString(R.string.finance_edit),
                        getString(a.enabled ? R.string.finance_disable : R.string.finance_enable)};
                new AlertDialog.Builder(PaymentAccountActivity.this)
                        .setTitle(a.name)
                        .setItems(actions, (d, which) -> {
                            if (which == 0) {
                                showEditDialog(a);
                            } else {
                                if (a.enabled) {
                                    new AlertDialog.Builder(PaymentAccountActivity.this)
                                            .setMessage(R.string.finance_account_confirm_disable)
                                            .setPositiveButton(R.string.btn_ok, (dd, w) -> {
                                                a.enabled = false;
                                                financeDb.updatePaymentAccount(a);
                                                loadData();
                                            })
                                            .setNegativeButton(R.string.btn_cancel, null)
                                            .show();
                                } else {
                                    a.enabled = true;
                                    financeDb.updatePaymentAccount(a);
                                    loadData();
                                }
                            }
                        })
                        .show();
            });
            return convertView;
        }
    }

    private void showEditDialog(final FinAccount a) {
        final EditText et = new EditText(this);
        et.setText(a.name);
        new AlertDialog.Builder(this)
                .setTitle(R.string.finance_edit)
                .setView(et)
                .setPositiveButton(R.string.btn_ok, (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) return;
                    a.name = name;
                    financeDb.updatePaymentAccount(a);
                    loadData();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}