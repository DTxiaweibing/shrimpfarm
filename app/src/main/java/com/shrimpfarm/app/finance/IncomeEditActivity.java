package com.shrimpfarm.app.finance;

import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.Toolbar;

import com.shrimpfarm.app.R;
import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.finance.FinanceDb.FinAccount;
import com.shrimpfarm.app.finance.FinanceDb.FinIncome;
import com.shrimpfarm.app.finance.FinanceDb.FinSupplier;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class IncomeEditActivity extends BaseActivity {

    private static final double NET_FEE_PER_UNIT = 0.35;

    private FinanceDb financeDb;
    private SharedPreferences prefs;
    private String currentBatchId;
    private FinIncome existing;
    private EditText etDate, etCarNo, etShedNo, etSpecs, etWeight, etPrice, etAmount, etActual, etRemark;
    private TextView tvDiff, tvNetFee, tvNetFeeStatus;
    private Spinner spAgent, spCustomer, spAccount, spPaid;
    private List<FinSupplier> agents = new ArrayList<>();
    private List<FinSupplier> customers = new ArrayList<>();
    private List<FinAccount> accounts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_income_edit);
        financeDb = new FinanceDb(this);
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentBatchId = prefs.getString("current_batch_id", "");
        setupToolbar();

        etDate = findViewById(R.id.et_date);
        etCarNo = findViewById(R.id.et_car_no);
        etShedNo = findViewById(R.id.et_shed_no);
        etSpecs = findViewById(R.id.et_specs);
        etWeight = findViewById(R.id.et_weight);
        etPrice = findViewById(R.id.et_price);
        etAmount = findViewById(R.id.et_amount);
        etActual = findViewById(R.id.et_actual);
        etRemark = findViewById(R.id.et_remark);
        tvDiff = findViewById(R.id.tv_diff);
        tvNetFee = findViewById(R.id.tv_net_fee);
        tvNetFeeStatus = findViewById(R.id.tv_net_fee_status);
        spAgent = findViewById(R.id.sp_agent);
        spCustomer = findViewById(R.id.sp_customer);
        spAccount = findViewById(R.id.sp_account);
        spPaid = findViewById(R.id.sp_paid);

        loadOptions();
        long incomeId = getIntent().getLongExtra("incomeId", -1);
        if (incomeId > 0) {
            for (FinIncome in : financeDb.getIncomes(currentBatchId)) {
                if (in.id == incomeId) existing = in;
            }
        }
        fillFields();
        wireAutoCalc();
        etDate.setFocusable(false);
        etDate.setOnClickListener(v -> pickDate());
        findViewById(R.id.btn_save).setOnClickListener(v -> save());
    }

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_home;
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(existing == null ? R.string.finance_income_new : R.string.finance_edit);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadOptions() {
        agents.clear();
        for (FinSupplier p : financeDb.getSuppliersByType(FinanceDb.PERSON_AGENT)) {
            if (p.enabled) agents.add(p);
        }
        customers.clear();
        for (FinSupplier p : financeDb.getSuppliersByType(FinanceDb.PERSON_CUSTOMER)) {
            if (p.enabled) customers.add(p);
        }
        accounts.clear();
        for (FinAccount a : financeDb.getPaymentAccounts()) {
            if (a.enabled) accounts.add(a);
        }
        spAgent.setAdapter(personAdapter(agents, R.string.finance_person_pick_agent));
        spCustomer.setAdapter(personAdapter(customers, R.string.finance_person_pick_customer));

        List<String> anames = new ArrayList<>();
        if (accounts.isEmpty()) {
            anames.add(getString(R.string.finance_no_receive_account));
        } else {
            for (FinAccount a : accounts) anames.add(a.name);
        }
        spAccount.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, anames));

        spPaid.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new String[]{
                getString(R.string.finance_status_paid),
                getString(R.string.finance_status_unpaid)}));
    }

    private ArrayAdapter<String> personAdapter(List<FinSupplier> persons, int emptyRes) {
        List<String> names = new ArrayList<>();
        if (persons.isEmpty()) {
            names.add(getString(emptyRes));
        } else {
            for (FinSupplier p : persons) names.add(p.name);
        }
        return new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
    }

    private void fillFields() {
        String today = new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).format(Calendar.getInstance().getTime());
        spPaid.setSelection(0);
        if (existing == null) {
            etDate.setText(today);
            etActual.setText("0");
            tvNetFee.setText("0");
            tvNetFeeStatus.setText(getString(R.string.finance_picking_unsettled));
            tvNetFeeStatus.setTextColor(0xFFE53935);
            return;
        }
        etDate.setText(existing.date);
        etCarNo.setText(existing.carNo);
        etShedNo.setText(existing.shedNo);
        etSpecs.setText(existing.specs);
        etWeight.setText(existing.weight == 0 ? "" : String.valueOf(existing.weight));
        etPrice.setText(existing.price == 0 ? "" : String.valueOf(existing.price));
        etAmount.setText(String.valueOf(existing.amount));
        etActual.setText(String.valueOf(existing.actualAmount));
        tvNetFee.setText(String.valueOf(existing.netFee));
        if (existing.netFeePaid) {
            tvNetFeeStatus.setText(getString(R.string.finance_picking_settled)
                    + (existing.netFeeAccountName.isEmpty() ? "" : " · " + existing.netFeeAccountName)
                    + (existing.netFeePayDate.isEmpty() ? "" : " @" + existing.netFeePayDate));
            tvNetFeeStatus.setTextColor(0xFF2E7D32);
        } else {
            tvNetFeeStatus.setText(getString(R.string.finance_picking_unsettled));
            tvNetFeeStatus.setTextColor(0xFFE53935);
        }
        etRemark.setText(existing.remark);
        selectByName(spAgent, agents, existing.agent);
        selectByName(spCustomer, customers, existing.customer);
        selectById(spAccount, accounts, existing.accountId);
        spPaid.setSelection(existing.paid ? 0 : 1);
    }

    private void selectByName(Spinner sp, List<FinSupplier> persons, String name) {
        for (int i = 0; i < persons.size(); i++) {
            if (persons.get(i).name.equals(name)) {
                sp.setSelection(i);
                return;
            }
        }
        sp.setSelection(0);
    }

    private void selectById(Spinner sp, List<FinAccount> accs, long id) {
        if (id <= 0 || accs.isEmpty()) return;
        for (int i = 0; i < accs.size(); i++) {
            if (accs.get(i).id == id) {
                sp.setSelection(i);
                return;
            }
        }
    }

    private void wireAutoCalc() {
        TextWatcher calcWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                recalc();
            }
        };
        etWeight.addTextChangedListener(calcWatcher);
        etPrice.addTextChangedListener(calcWatcher);
        TextWatcher diffWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateDiff();
            }
        };
        etAmount.addTextChangedListener(diffWatcher);
        etActual.addTextChangedListener(diffWatcher);
        updateDiff();
    }

    private void recalc() {
        double w = parse(etWeight.getText().toString());
        double p = parse(etPrice.getText().toString());
        double net = FinanceDb.round2(w * NET_FEE_PER_UNIT);
        double amount = w * p;
        tvNetFee.setText(String.valueOf(net));
        etAmount.setText(amount == 0 ? "" : String.valueOf(FinanceDb.round2(amount)));
        etActual.setText(amount == 0 ? "" : String.valueOf(FinanceDb.round2(amount)));
        updateDiff();
    }

    private void updateDiff() {
        double amount = parse(etAmount.getText().toString());
        double actual = parse(etActual.getText().toString());
        double diff = FinanceDb.round2(actual - amount);
        tvDiff.setText(String.format(Locale.CHINA, "%+.2f", diff));
        tvDiff.setTextColor(diff < 0 ? 0xFFD32F2F : diff > 0 ? 0xFF2E7D32 : 0xFF333333);
    }

    private double parse(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private void pickDate() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) ->
                        etDate.setText(String.format(Locale.CHINA, "%d/%02d/%02d", year, month + 1, dayOfMonth)),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void save() {
        if (currentBatchId.isEmpty()) {
            Toast.makeText(this, R.string.finance_no_batch, Toast.LENGTH_SHORT).show();
            return;
        }
        String date = etDate.getText().toString().trim();
        double weight = parse(etWeight.getText().toString());
        double price = parse(etPrice.getText().toString());
        boolean paid = spPaid.getSelectedItemPosition() == 0;
        FinIncome in = new FinIncome();
        in.batchId = currentBatchId;
        in.date = date;
        in.carNo = etCarNo.getText().toString().trim();
        in.shedNo = etShedNo.getText().toString().trim();
        in.specs = etSpecs.getText().toString().trim();
        in.weight = weight;
        in.price = price;
        in.netFee = FinanceDb.round2(weight * NET_FEE_PER_UNIT);
        in.amount = parse(etAmount.getText().toString());
        in.actualAmount = parse(etActual.getText().toString());
        in.agent = selectedPersonName(spAgent, agents);
        in.customer = selectedPersonName(spCustomer, customers);
        in.accountId = accounts.isEmpty() ? 0 : accounts.get(Math.max(0, spAccount.getSelectedItemPosition())).id;
        in.paid = paid;
        in.payDate = paid ? date : "";
        in.remark = etRemark.getText().toString().trim();
        if (date.isEmpty() || weight <= 0 || price <= 0) {
            Toast.makeText(this, R.string.finance_input_name, Toast.LENGTH_SHORT).show();
            return;
        }
        if (existing == null) {
            financeDb.insertIncome(in);
        } else {
            in.id = existing.id;
            in.netFeePaid = existing.netFeePaid;
            in.netFeePayDate = existing.netFeePayDate;
            in.netFeeAccountId = existing.netFeeAccountId;
            financeDb.updateIncome(in);
        }
        finish();
    }

    private String selectedPersonName(Spinner sp, List<FinSupplier> persons) {
        if (persons.isEmpty()) return "";
        int pos = Math.max(0, sp.getSelectedItemPosition());
        if (pos >= persons.size()) return "";
        return persons.get(pos).name;
    }
}