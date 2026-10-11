package com.shrimpfarm.app.erp;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.Toolbar;

import com.shrimpfarm.app.R;
import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.finance.FinanceDb;
import com.shrimpfarm.app.finance.FinanceDb.FinAccount;
import com.shrimpfarm.app.finance.FinanceDb.FinContactAccount;
import com.shrimpfarm.app.finance.FinanceDb.FinPayment;
import com.shrimpfarm.app.finance.FinanceDb.FinPaymentTicket;
import com.shrimpfarm.app.finance.FinanceDb.FinSupplier;
import com.shrimpfarm.app.finance.FinanceDb.FinTicket;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettleActivity extends BaseActivity {

    private FinanceDb financeDb;
    private List<FinSupplier> suppliers = new ArrayList<>();
    private List<FinTicket> tickets = new ArrayList<>();
    private List<FinAccount> accounts = new ArrayList<>();
    private List<View> rows = new ArrayList<>();
    private AppCompatSpinner spSupplier;
    private AppCompatSpinner spDiffReason;
    private AppCompatSpinner spAccount;
    private AppCompatSpinner spReceive;
    private EditText etPaid;
    private TextView tvDue, tvDiff;
    private TicketAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_settle);
        financeDb = new FinanceDb(this);
        setupToolbar();

        spSupplier = findViewById(R.id.sp_supplier);
        spDiffReason = findViewById(R.id.sp_diff_reason);
        spAccount = findViewById(R.id.sp_account);
        spReceive = findViewById(R.id.sp_receive);
        etPaid = findViewById(R.id.et_paid);
        tvDue = findViewById(R.id.tv_due);
        tvDiff = findViewById(R.id.tv_diff);
        ListView listView = findViewById(R.id.list_view);
        adapter = new TicketAdapter();
        listView.setAdapter(adapter);

        spDiffReason.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new String[]{
                getString(R.string.finance_diff_waive)}));
        findViewById(R.id.btn_manage_account).setOnClickListener(v ->
                startActivity(new Intent(this, PaymentAccountActivity.class)));
        findViewById(R.id.btn_settle).setOnClickListener(v -> doSettle());

        etPaid.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateSummary();
            }
        });
        spSupplier.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                loadReceiveAccounts();
                loadTickets();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        long oldId = selectedSupplierId();
        loadData();
        if (oldId > 0) selectSupplier(oldId);
        etPaid.setText("");
        updateSummary();
    }

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_home;
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.finance_settle);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadData() {
        suppliers = financeDb.getSuppliersByType(FinanceDb.PERSON_SUPPLIER);
        List<FinSupplier> filtered = new ArrayList<>();
        for (FinSupplier s : suppliers) {
            if (financeDb.sumUnsettledBySupplier(s.id) > 0) filtered.add(s);
        }
        suppliers = filtered;
        List<String> names = new ArrayList<>();
        final List<Long> ids = new ArrayList<>();
        for (FinSupplier s : suppliers) {
            names.add(s.name);
            ids.add(s.id);
        }
        if (names.isEmpty()) names.add(getString(R.string.finance_no_supplier));
        ArrayAdapter<String> sa = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSupplier.setAdapter(sa);
        spSupplier.setTag(ids);

        accounts = financeDb.getPaymentAccounts();
        List<String> anames = new ArrayList<>();
        final List<Long> aids = new ArrayList<>();
        for (FinAccount a : accounts) {
            if (!a.enabled) continue;
            anames.add(a.name);
            aids.add(a.id);
        }
        if (anames.isEmpty()) anames.add(getString(R.string.finance_pick_account));
        ArrayAdapter<String> aa = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, anames);
        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAccount.setAdapter(aa);
        spAccount.setTag(aids);
        loadReceiveAccounts();
        loadTickets();
    }

    private void loadReceiveAccounts() {
        long sid = selectedSupplierId();
        List<FinContactAccount> accs = sid <= 0 ? new ArrayList<>() : financeDb.getContactAccounts(sid);
        List<String> names = new ArrayList<>();
        final List<Long> ids = new ArrayList<>();
        names.add(getString(R.string.finance_no_receive_account));
        ids.add(0L);
        for (FinContactAccount a : accs) {
            if (!a.enabled) continue;
            names.add(a.displayName());
            ids.add(a.id);
        }
        ArrayAdapter<String> aa = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spReceive.setAdapter(aa);
        spReceive.setTag(ids);
    }

    private long selectedSupplierId() {
        List<Long> ids = (List<Long>) spSupplier.getTag();
        if (ids == null || ids.isEmpty()) return -1;
        int pos = spSupplier.getSelectedItemPosition();
        if (pos < 0 || pos >= ids.size()) return -1;
        return ids.get(pos);
    }

    private void selectSupplier(long id) {
        List<Long> ids = (List<Long>) spSupplier.getTag();
        if (ids == null) return;
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i) == id) {
                spSupplier.setSelection(i);
                return;
            }
        }
    }

    private void loadTickets() {
        long supplierId = selectedSupplierId();
        tickets.clear();
        rows.clear();
        if (supplierId > 0) {
            tickets = financeDb.getUnsettledTickets(supplierId);
            for (FinTicket t : tickets) rows.add(null);
        }
        adapter.notifyDataSetChanged();
        updateSummary();
    }

    private void updateSummary() {
        double due = 0;
        List<Long> ids = (List<Long>) spSupplier.getTag();
        for (int i = 0; i < tickets.size(); i++) {
            CheckBox cb = rows.get(i) == null ? null : (CheckBox) rows.get(i).findViewById(R.id.cb_check);
            if (cb != null && cb.isChecked()) due += tickets.get(i).totalAmount;
        }
        tvDue.setText(getString(R.string.finance_due_amount) + ": ¥" + FinanceDb.round2(due));
        double paid = parse(etPaid.getText().toString());
        double diff = due - paid;
        tvDiff.setText(getString(R.string.finance_diff_amount) + ": ¥" + FinanceDb.round2(diff));
    }

    private double parse(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private void doSettle() {
        List<FinPaymentTicket> ticketsToPay = new ArrayList<>();
        double due = 0;
        for (int i = 0; i < tickets.size(); i++) {
            CheckBox cb = rows.get(i) == null ? null : (CheckBox) rows.get(i).findViewById(R.id.cb_check);
            if (cb != null && cb.isChecked()) {
                FinPaymentTicket pt = new FinPaymentTicket();
                pt.ticketId = tickets.get(i).id;
                pt.amount = tickets.get(i).totalAmount;
                ticketsToPay.add(pt);
                due += tickets.get(i).totalAmount;
            }
        }
        if (ticketsToPay.isEmpty()) {
            Toast.makeText(this, R.string.finance_select_ticket, Toast.LENGTH_SHORT).show();
            return;
        }
        double paid = parse(etPaid.getText().toString());
        double diff = due - paid;
        if (diff < 0) {
            Toast.makeText(this, R.string.finance_paid_amount, Toast.LENGTH_SHORT).show();
            return;
        }
        long supplierId = selectedSupplierId();
        long accountId = selectedAccountId();
        if (accountId <= 0) {
            Toast.makeText(this, R.string.finance_pick_account, Toast.LENGTH_SHORT).show();
            return;
        }
        FinPayment p = new FinPayment();
        p.supplierId = supplierId;
        p.batchId = prefsBatchId();
        p.date = today();
        p.accountId = accountId;
        p.receiveAccountId = selectedReceiveAccountId();
        p.dueAmount = FinanceDb.round2(due);
        p.paidAmount = FinanceDb.round2(paid);
        p.diffAmount = FinanceDb.round2(diff);
        p.diffReason = diff > 0 ? getString(R.string.finance_diff_waive) : "";
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.finance_confirm_settle, FinanceDb.round2(paid)))
                .setPositiveButton(R.string.btn_ok, (d, w) -> {
                    financeDb.savePayment(p, ticketsToPay);
                    Toast.makeText(this, R.string.finance_settle_success, Toast.LENGTH_SHORT).show();
                    loadData();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private long selectedAccountId() {
        List<Long> ids = (List<Long>) spAccount.getTag();
        if (ids == null || ids.isEmpty()) return -1;
        int pos = spAccount.getSelectedItemPosition();
        if (pos < 0 || pos >= ids.size()) return -1;
        return ids.get(pos);
    }

    private long selectedReceiveAccountId() {
        List<Long> ids = (List<Long>) spReceive.getTag();
        if (ids == null || ids.isEmpty()) return 0;
        int pos = spReceive.getSelectedItemPosition();
        if (pos < 0 || pos >= ids.size()) return 0;
        return ids.get(pos);
    }

    private String prefsBatchId() {
        return getSharedPreferences("app_prefs", MODE_PRIVATE).getString("current_batch_id", "");
    }

    private String today() {
        return new java.text.SimpleDateFormat("yyyy/MM/dd", Locale.CHINA)
                .format(java.util.Calendar.getInstance().getTime());
    }

    private class TicketAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return tickets.size();
        }

        @Override
        public Object getItem(int position) {
            return tickets.get(position);
        }

        @Override
        public long getItemId(int position) {
            return tickets.get(position).id;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(SettleActivity.this)
                        .inflate(R.layout.item_finance_settle, parent, false);
            }
            rows.set(position, convertView);
            final FinTicket t = tickets.get(position);
            CheckBox cb = convertView.findViewById(R.id.cb_check);
            TextView tvDate = convertView.findViewById(R.id.tv_date);
            TextView tvNo = convertView.findViewById(R.id.tv_no);
            TextView tvRemark = convertView.findViewById(R.id.tv_remark);
            TextView tvAmount = convertView.findViewById(R.id.tv_amount);

            tvDate.setText(t.date);
            tvNo.setText(t.ticketNo.isEmpty() ? "" : "单号:" + t.ticketNo);
            tvRemark.setText(t.remark.isEmpty() ? "" : t.remark);
            tvAmount.setText(String.format(Locale.CHINA, "¥%.2f", t.totalAmount));
            cb.setChecked(false);
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> updateSummary());
            return convertView;
        }
    }
}