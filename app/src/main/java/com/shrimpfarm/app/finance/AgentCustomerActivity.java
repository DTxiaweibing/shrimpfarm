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
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.finance.FinanceDb.FinAgentCustomer;
import com.shrimpfarm.app.finance.FinanceDb.FinSupplier;

import java.util.ArrayList;
import java.util.List;

/** A3: manager for the many-to-many relation between an agent and its customers. */
public class AgentCustomerActivity extends BaseActivity {

    private FinanceDb financeDb;
    private long agentId;
    private String agentName = "";
    private final List<FinAgentCustomer> links = new ArrayList<>();
    private LinkAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_agent_customer);
        financeDb = new FinanceDb(this);
        agentId = getIntent().getLongExtra("agentId", 0);
        agentName = getIntent().getStringExtra("agentName");
        if (agentName == null) agentName = "";
        setupToolbar();
        adapter = new LinkAdapter();
        ListView listView = findViewById(R.id.list_view);
        listView.setAdapter(adapter);
        findViewById(R.id.btn_link).setOnClickListener(v -> showPickCustomerDialog());
        findViewById(R.id.btn_quick_add).setOnClickListener(v -> showQuickAddDialog());
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
            getSupportActionBar().setTitle(getString(R.string.finance_agent_customer_manage, agentName));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadData() {
        links.clear();
        links.addAll(financeDb.getAgentCustomers(agentId));
        adapter.notifyDataSetChanged();
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    private void showPickCustomerDialog() {
        List<FinSupplier> candidates = AgentCustomerRules.linkableCustomers(
                financeDb.getSuppliersByType(FinanceDb.PERSON_CUSTOMER),
                AgentCustomerRules.linkedCustomerIds(links));
        if (candidates.isEmpty()) {
            toast(R.string.finance_no_customer_pick);
            return;
        }
        String[] names = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            names[i] = AgentCustomerRules.displayName(candidates.get(i), "");
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.finance_pick_customer)
                .setItems(names, (d, which) -> linkCustomer(candidates.get(which).id))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showQuickAddDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);
        final EditText etName = new EditText(this);
        etName.setHint(R.string.finance_input_name);
        layout.addView(etName);
        new AlertDialog.Builder(this)
                .setTitle(R.string.finance_quick_add_customer)
                .setView(layout)
                .setPositiveButton(R.string.btn_ok, (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (!AgentCustomerRules.validName(name)) {
                        toast(R.string.finance_name_required);
                        return;
                    }
                    quickAddOrReuse(name);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void quickAddOrReuse(String name) {
        long existing = AgentCustomerRules.existingCustomerId(
                financeDb.getSuppliersByType(FinanceDb.PERSON_CUSTOMER), name);
        long customerId = existing > 0 ? existing : financeDb.insertSupplier(
                FinanceDb.PERSON_CUSTOMER, name, "", "", "", 0, "", SupplierKindRules.KIND_MED);
        if (customerId <= 0) {
            toast(R.string.finance_customer_link_failed);
            return;
        }
        linkCustomer(customerId);
    }

    private void linkCustomer(long customerId) {
        financeDb.linkAgentCustomer(agentId, customerId);
        toast(R.string.finance_customer_linked);
        loadData();
    }

    private void confirmUnlink(FinAgentCustomer link) {
        FinSupplier customer = financeDb.getSupplierById(link.customerId);
        String name = AgentCustomerRules.displayName(customer,
                getString(R.string.finance_customer_missing));
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.finance_confirm_unlink_customer, name))
                .setPositiveButton(R.string.btn_ok, (d, w) -> {
                    financeDb.unlinkAgentCustomer(agentId, link.customerId);
                    toast(R.string.finance_customer_unlinked);
                    loadData();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private class LinkAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return links.size();
        }

        @Override
        public Object getItem(int position) {
            return links.get(position);
        }

        @Override
        public long getItemId(int position) {
            return links.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(AgentCustomerActivity.this)
                        .inflate(R.layout.item_finance_agent_customer, parent, false);
            }
            final FinAgentCustomer link = links.get(position);
            FinSupplier customer = financeDb.getSupplierById(link.customerId);
            TextView tvName = convertView.findViewById(R.id.tv_name);
            tvName.setText(AgentCustomerRules.displayName(customer,
                    getString(R.string.finance_customer_missing)));
            convertView.findViewById(R.id.tv_unlink).setOnClickListener(v -> confirmUnlink(link));
            return convertView;
        }
    }
}
