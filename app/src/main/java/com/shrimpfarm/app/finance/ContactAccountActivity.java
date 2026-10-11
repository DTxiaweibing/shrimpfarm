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
import com.shrimpfarm.app.finance.FinanceDb.FinContactAccount;

import java.util.ArrayList;
import java.util.List;

public class ContactAccountActivity extends BaseActivity {

    private FinanceDb financeDb;
    private long supplierId;
    private String supplierName = "";
    private List<FinContactAccount> accountList = new ArrayList<>();
    private AccountAdapter adapter;
    private String[] typeNames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_contact_account);
        financeDb = new FinanceDb(this);
        supplierId = getIntent().getLongExtra("supplierId", 0);
        supplierName = getIntent().getStringExtra("supplierName");
        if (supplierName == null) supplierName = "";
        typeNames = getResources().getStringArray(R.array.finance_account_types);
        setupToolbar();
        ListView listView = findViewById(R.id.list_view);
        adapter = new AccountAdapter();
        listView.setAdapter(adapter);
        findViewById(R.id.btn_add).setOnClickListener(v -> showAccountDialog(null));
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
            getSupportActionBar().setTitle(getString(R.string.finance_contact_account_title, supplierName));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadData() {
        accountList = financeDb.getContactAccounts(supplierId);
        adapter.notifyDataSetChanged();
    }

    private int typeIndex(int type) {
        return Math.max(0, Math.min(type, typeNames.length - 1));
    }

    private TextView label(int res) {
        TextView tv = new TextView(this);
        tv.setText(res);
        tv.setTextColor(0xFF333333);
        tv.setTextSize(13);
        tv.setPadding(0, 8, 0, 2);
        return tv;
    }

    private void showAccountDialog(final FinContactAccount account) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        final Spinner spType = new Spinner(this);
        spType.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, typeNames));
        layout.addView(label(R.string.finance_account_type_label));
        layout.addView(spType);

        final EditText etName = new EditText(this);
        etName.setHint(R.string.finance_account_name);
        layout.addView(label(R.string.finance_account_name));
        layout.addView(etName);

        final EditText etNo = new EditText(this);
        etNo.setHint(R.string.finance_account_no);
        layout.addView(label(R.string.finance_account_no));
        layout.addView(etNo);

        if (account != null) {
            spType.setSelection(typeIndex(account.type));
            etName.setText(account.accountName);
            etNo.setText(account.accountNo);
        }

        new AlertDialog.Builder(this)
                .setTitle(account == null ? R.string.finance_add_contact_account : R.string.finance_edit)
                .setView(layout)
                .setPositiveButton(R.string.btn_ok, (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.finance_input_account_name, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int type = Math.max(0, spType.getSelectedItemPosition());
                    if (account == null) {
                        financeDb.insertContactAccount(supplierId, type, name, etNo.getText().toString().trim());
                    } else {
                        account.type = type;
                        account.accountName = name;
                        account.accountNo = etNo.getText().toString().trim();
                        financeDb.updateContactAccount(account);
                    }
                    loadData();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private String mask(String s) {
        if (s.isEmpty()) return "";
        if (s.length() <= 4) return "****";
        return "****" + s.substring(s.length() - 4);
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
                convertView = LayoutInflater.from(ContactAccountActivity.this)
                        .inflate(android.R.layout.simple_list_item_2, parent, false);
            }
            final FinContactAccount a = accountList.get(position);
            TextView tvName = convertView.findViewById(android.R.id.text1);
            TextView tvStatus = convertView.findViewById(android.R.id.text2);
            StringBuilder first = new StringBuilder();
            if (!typeNames[typeIndex(a.type)].isEmpty()) first.append(typeNames[typeIndex(a.type)]).append("  ");
            first.append(a.accountName);
            String masked = mask(a.accountNo);
            if (!masked.isEmpty()) first.append("  ").append(masked);
            tvName.setText(first.toString());
            tvStatus.setText(getString(a.enabled ? R.string.finance_enable : R.string.finance_disable)
                    + "  ·  " + getString(R.string.finance_edit));
            convertView.setAlpha(a.enabled ? 1f : 0.55f);
            convertView.setOnClickListener(v -> {
                final String[] actions = {
                        getString(R.string.finance_edit),
                        getString(a.enabled ? R.string.finance_disable : R.string.finance_enable)};
                new AlertDialog.Builder(ContactAccountActivity.this)
                        .setTitle(a.accountName)
                        .setItems(actions, (d, which) -> {
                            if (which == 0) {
                                showAccountDialog(a);
                            } else {
                                if (a.enabled) {
                                    new AlertDialog.Builder(ContactAccountActivity.this)
                                            .setMessage(R.string.finance_confirm_delete_contact_account)
                                            .setPositiveButton(R.string.btn_ok, (dd, w) -> {
                                                financeDb.deleteContactAccount(a.id);
                                                loadData();
                                            })
                                            .setNegativeButton(R.string.btn_cancel, null)
                                            .show();
                                } else {
                                    a.enabled = true;
                                    financeDb.updateContactAccount(a);
                                    loadData();
                                }
                            }
                        })
                        .show();
            });
            return convertView;
        }
    }
}