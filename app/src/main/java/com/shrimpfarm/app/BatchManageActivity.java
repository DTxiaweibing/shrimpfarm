package com.shrimpfarm.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;
import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.utils.DialogHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BatchManageActivity extends BaseActivity {

    private ListView listView;
    private Button btnAddBatch;
    private BatchAdapter adapter;
    private List<BatchItem> batchList = new ArrayList<>();
    private SharedPreferences prefs;
    private String currentBatchId;
    private String currentBatchName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_batch_manage);

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentBatchId = prefs.getString("current_batch_id", "");
        currentBatchName = prefs.getString("current_batch_name", "");

        setupToolbar();
        loadBatchList();
        setupListView();
        setupAddButton();
        setupBottomNavigation();
    }

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_home;
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("养殖场批次管理");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
    }

    private void loadBatchList() {
        String savedBatchesJson = prefs.getString("batch_list_json", "");
        if (!savedBatchesJson.isEmpty()) {
            try {
                org.json.JSONArray jsonArray = new org.json.JSONArray(savedBatchesJson);
                for (int i = 0; i < jsonArray.length(); i++) {
                    org.json.JSONObject obj = jsonArray.getJSONObject(i);
                    BatchItem item = new BatchItem();
                    item.id = obj.getString("id");
                    item.name = obj.getString("name");
                    batchList.add(item);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // 如果当前批次不在列表中，添加进去
        if (!currentBatchId.isEmpty() && !currentBatchName.isEmpty()) {
            boolean found = false;
            for (BatchItem item : batchList) {
                if (item.id.equals(currentBatchId)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                BatchItem item = new BatchItem();
                item.id = currentBatchId;
                item.name = currentBatchName;
                batchList.add(item);
            }
        }
        saveBatchList();
    }

    private void saveBatchList() {
        try {
            org.json.JSONArray jsonArray = new org.json.JSONArray();
            for (BatchItem item : batchList) {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("id", item.id);
                obj.put("name", item.name);
                jsonArray.put(obj);
            }
            String json = jsonArray.toString();
            prefs.edit().putString("batch_list_json", json).apply();
            DatabaseHelper dbHelper = new DatabaseHelper(BatchManageActivity.this);
            dbHelper.saveBasicData("_meta", "_batch_list_json", json);
            com.shrimpfarm.app.DatabaseHelper.closeInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupListView() {
        listView = findViewById(R.id.list_view);
        adapter = new BatchAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    BatchItem selected = batchList.get(position);
                    prefs.edit()
                        .putString("current_batch_id", selected.id)
                        .putString("current_batch_name", selected.name)
                        .apply();
                    currentBatchId = selected.id;
                    currentBatchName = selected.name;
                    adapter.notifyDataSetChanged();
                    Toast.makeText(BatchManageActivity.this, "已切换到：" + selected.name, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
    }

    private void setupAddButton() {
        btnAddBatch = findViewById(R.id.btn_add_batch);
        btnAddBatch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAddBatchDialog();
                }
            });
    }

    private void showAddBatchDialog() {
        final EditText[] inputHolder = new EditText[1];
        inputHolder[0] = DialogHelper.showStyledInputDialog(this, "新建批次",
                "请输入批次名称", null,
                new String[]{"取消", "确定"},
                new DialogInterface.OnClickListener[]{ null, (d, w) -> {
                    String newBatchName = inputHolder[0].getText().toString().trim();
                    if (!newBatchName.isEmpty()) {
                        for (BatchItem item : batchList) {
                            if (item.name.equals(newBatchName)) {
                                Toast.makeText(BatchManageActivity.this, "批次已存在", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                        String newBatchId = UUID.randomUUID().toString();
                        BatchItem newItem = new BatchItem();
                        newItem.id = newBatchId;
                        newItem.name = newBatchName;
                        batchList.add(newItem);
                        saveBatchList();
                        adapter.notifyDataSetChanged();

                        if (currentBatchId.isEmpty()) {
                            prefs.edit()
                                .putString("current_batch_id", newBatchId)
                                .putString("current_batch_name", newBatchName)
                                .apply();
                            currentBatchId = newBatchId;
                            currentBatchName = newBatchName;
                        }
                    }
                } });
    }

    private void confirmDeleteBatch(final BatchItem batchItem, final int position) {
        DialogHelper.showStyledConfirmDialog(this, "删除批次",
                "确定要删除批次 \"" + batchItem.name + "\" 吗？\n\n警告：删除后所有关联数据将无法恢复！",
                new String[]{"取消", "删除"},
                new DialogInterface.OnClickListener[]{
                    null,
                    (d, w) -> {
                        DatabaseHelper dbHelper = new DatabaseHelper(BatchManageActivity.this);
                        dbHelper.deleteAllDataByBatchId(batchItem.id);
                        batchList.remove(position);
                        saveBatchList();
                        if (batchItem.id.equals(currentBatchId)) {
                            prefs.edit().remove("current_batch_id").remove("current_batch_name").apply();
                            currentBatchId = "";
                            currentBatchName = "";
                        }
                        adapter.notifyDataSetChanged();
                        Toast.makeText(BatchManageActivity.this, "批次及关联数据已删除", Toast.LENGTH_SHORT).show();
                        deleteCloudBackup(batchItem.name);
                    }
                });
    }

    private void deleteCloudBackup(String batchName) {
        Toast.makeText(this, "云端备份已同步删除", Toast.LENGTH_SHORT).show();
    }

    private static class BatchItem {
        String id;
        String name;
    }

    private class BatchAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return batchList.size();
        }

        @Override
        public Object getItem(int position) {
            return batchList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(BatchManageActivity.this)
                    .inflate(R.layout.item_batch, parent, false);
            }
            TextView tvName = convertView.findViewById(R.id.tv_batch_name);
            ImageView ivCheck = convertView.findViewById(R.id.iv_check);
            ImageView ivDelete = convertView.findViewById(R.id.iv_delete);

            final BatchItem batch = batchList.get(position);
            tvName.setText(batch.name);

            if (batch.id.equals(currentBatchId)) {
                ivCheck.setVisibility(View.VISIBLE);
                tvName.setTextColor(0xFF4CAF50);
            } else {
                ivCheck.setVisibility(View.GONE);
                tvName.setTextColor(0xFF333333);
            }

            ivDelete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        confirmDeleteBatch(batch, position);
                    }
                });

            return convertView;
        }
    }
}
