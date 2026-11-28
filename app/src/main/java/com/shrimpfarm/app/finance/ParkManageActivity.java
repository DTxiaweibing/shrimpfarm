package com.shrimpfarm.app.finance;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.finance.FinanceDb.FinPark;

import java.util.ArrayList;
import java.util.List;

/** A14: park archive (park subject + receiving account), reused by P4 utility billing. */
public class ParkManageActivity extends BaseActivity {

    private FinanceDb financeDb;
    private final List<FinPark> parks = new ArrayList<>();
    private ParkAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_park_manage);
        financeDb = new FinanceDb(this);
        setupToolbar();
        adapter = new ParkAdapter();
        ListView listView = findViewById(R.id.list_view);
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
            getSupportActionBar().setTitle(R.string.finance_park_manage);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadData() {
        parks.clear();
        parks.addAll(financeDb.listParks());
        adapter.notifyDataSetChanged();
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    private void showEditDialog(final FinPark park) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_finance_park, null);
        final EditText etName = view.findViewById(R.id.et_park_name);
        final EditText etAccountName = view.findViewById(R.id.et_account_name);
        final EditText etAccountNo = view.findViewById(R.id.et_account_no);
        final CheckBox cbEnabled = view.findViewById(R.id.cb_enabled);
        if (park == null) {
            cbEnabled.setChecked(true);
        } else {
            etName.setText(park.name);
            etAccountName.setText(park.accountName);
            etAccountNo.setText(park.accountNo);
            cbEnabled.setChecked(park.enabled);
        }
        int title = park == null ? R.string.finance_park_add : R.string.finance_park_edit;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(R.string.btn_ok,
                        (d, w) -> savePark(park, etName, etAccountName, etAccountNo, cbEnabled))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void savePark(FinPark park, EditText etName, EditText etAccountName,
                          EditText etAccountNo, CheckBox cbEnabled) {
        String name = ParkRules.normalizedName(etName.getText().toString());
        if (!ParkRules.validName(name)) {
            toast(R.string.finance_name_required);
            return;
        }
        FinPark target = park == null ? new FinPark() : park;
        target.name = name;
        target.accountName = ParkRules.normalizedName(etAccountName.getText().toString());
        target.accountNo = ParkRules.normalizedName(etAccountNo.getText().toString());
        target.enabled = cbEnabled.isChecked();
        if (target.id == 0 && financeDb.insertPark(target) <= 0) {
            toast(R.string.finance_park_save_failed);
            return;
        }
        if (target.id != 0) financeDb.updatePark(target);
        toast(R.string.finance_park_saved);
        loadData();
    }

    private class ParkAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return parks.size();
        }

        @Override
        public Object getItem(int position) {
            return parks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return parks.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(ParkManageActivity.this)
                        .inflate(R.layout.item_finance_park, parent, false);
            }
            final FinPark park = parks.get(position);
            ((TextView) convertView.findViewById(R.id.tv_name)).setText(park.name);
            String summary = ParkRules.accountSummary(park.accountName, park.accountNo);
            TextView tvAccount = convertView.findViewById(R.id.tv_account);
            tvAccount.setText(summary.isEmpty() ? getString(R.string.finance_park_account_unset)
                    : getString(R.string.finance_park_account_line, summary));
            TextView tvStatus = convertView.findViewById(R.id.tv_status);
            tvStatus.setText(park.enabled ? R.string.finance_park_status_on
                    : R.string.finance_park_status_off);
            tvStatus.setTextColor(park.enabled ? 0xFF388E3C : 0xFF9E9E9E);
            convertView.findViewById(R.id.tv_edit).setOnClickListener(v -> showEditDialog(park));
            return convertView;
        }
    }
}
