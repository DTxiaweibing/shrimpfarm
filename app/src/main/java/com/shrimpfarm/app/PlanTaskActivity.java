package com.shrimpfarm.app;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import com.shrimpfarm.app.utils.DialogHelper;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class PlanTaskActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private TaskAdapter adapter;
    private List<TaskItem> taskList = new ArrayList<>();
    private DatabaseHelper dbHelper;
    private String currentBatchId;
    private Handler autoCollapseHandler = new Handler();
    private int expandedPosition = -1;
    private static final long AUTO_COLLAPSE_DELAY = 8000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plan_task);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("计划任务");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        dbHelper = new DatabaseHelper(this);
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentBatchId = prefs.getString("current_batch_id", "");
        if (currentBatchId.isEmpty()) {
            Toast.makeText(this, "请先选择批次", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        loadTasks();
    }

    private void loadTasks() {
        int stockingDay = dbHelper.getStockingDay(currentBatchId);
        taskList.clear();
        Cursor cursor = dbHelper.getAllMainTasks(currentBatchId);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                TaskItem task = new TaskItem();
                task.id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TASK_ID));
                task.title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TASK_NAME));
                Cursor subCursor = dbHelper.getSubTasks(task.id);
                while (subCursor.moveToNext()) {
                    SubTaskItem sub = new SubTaskItem();
                    sub.id = subCursor.getLong(subCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TASK_ID));
                    sub.startValue = subCursor.getInt(subCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_START_VALUE));
                    sub.endValue = subCursor.getInt(subCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_END_VALUE));
                    sub.intervalValue = subCursor.getDouble(subCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_INTERVAL_VALUE));
                    sub.unitType = subCursor.getInt(subCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_UNIT_TYPE));
                    sub.frequency = subCursor.getInt(subCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_FREQUENCY));
                    int lastTriggerDay = subCursor.getInt(subCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LAST_TRIGGER_DAY));
                    double lastTriggerFeed = subCursor.getDouble(subCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LAST_TRIGGER_FEED));

                    if (stockingDay > 0 && stockingDay >= sub.startValue && stockingDay <= sub.endValue) {
                        if (sub.unitType == 0) {
                            int nextDay = (lastTriggerDay > 0) ? lastTriggerDay + (int) sub.intervalValue : sub.startValue;
                            sub.isDue = stockingDay >= nextDay;
                        } else {
                            double currentFeed = dbHelper.getAccumulatedFeed(currentBatchId, sub.startValue, stockingDay);
                            double nextThreshold = lastTriggerFeed + sub.intervalValue;
                            sub.isDue = currentFeed >= nextThreshold;
                        }
                    }
                    task.subTasks.add(sub);
                }
                subCursor.close();
                taskList.add(task);
            }
            cursor.close();
        }
        adapter = new TaskAdapter();
        recyclerView.setAdapter(adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_plan_task, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_add_task) {
            showAddTaskDialog();
            return true;
        } else if (item.getItemId() == R.id.action_settings) {
            showSettingsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAddTaskDialog() {
        final EditText[] inputHolder = new EditText[1];
        inputHolder[0] = DialogHelper.showStyledInputDialog(this, "新建任务",
                "任务名称", null,
                new String[]{"取消", "确定"},
                new DialogInterface.OnClickListener[]{ null, (d, w) -> {
                    String name = inputHolder[0].getText().toString().trim();
                    if (!name.isEmpty()) {
                        long taskId = dbHelper.addMainTask(currentBatchId, name);
                        TaskItem newTask = new TaskItem();
                        newTask.id = taskId;
                        newTask.title = name;
                        taskList.add(newTask);
                        adapter.notifyItemInserted(taskList.size() - 1);
                        Toast.makeText(this, "任务已创建，展开后添加子计划", Toast.LENGTH_SHORT).show();
                    }
                } });
    }

    @android.annotation.SuppressLint("InflateParams")
    private void showSettingsDialog() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        Dialog dialog = new Dialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_plan_task_settings, null);
        dialog.setContentView(view);
        dialog.setCanceledOnTouchOutside(false);

        SwitchCompat swMaster = view.findViewById(R.id.sw_master);
        SwitchCompat swDay = view.findViewById(R.id.sw_day);
        SwitchCompat swNight = view.findViewById(R.id.sw_night);
        SwitchCompat swMidnight = view.findViewById(R.id.sw_midnight);
        swMaster.setChecked(prefs.getBoolean("plan_task_master_switch", true));
        swDay.setChecked(prefs.getBoolean("plan_task_day_switch", true));
        swNight.setChecked(prefs.getBoolean("plan_task_night_switch", true));
        swMidnight.setChecked(prefs.getBoolean("plan_task_midnight_switch", false));

        LinearLayout buttonLayout = view.findViewById(R.id.layout_buttons);
        String[] btnTexts = {"取消", "确定"};
        android.content.DialogInterface.OnClickListener[] listeners = {
            (d, w) -> {},
            (d, w) -> {
                prefs.edit()
                    .putBoolean("plan_task_master_switch", swMaster.isChecked())
                    .putBoolean("plan_task_day_switch", swDay.isChecked())
                    .putBoolean("plan_task_night_switch", swNight.isChecked())
                    .putBoolean("plan_task_midnight_switch", swMidnight.isChecked())
                    .apply();
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
            }
        };
        for (int i = 0; i < btnTexts.length; i++) {
            Button btn = new Button(this);
            btn.setText(btnTexts[i]);
            btn.setTextSize(15);
            btn.setTypeface(android.graphics.Typeface.defaultFromStyle(android.graphics.Typeface.BOLD));
            btn.setTextColor(0xFF333333);
            btn.setBackgroundResource(android.R.color.transparent);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i < btnTexts.length - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));
                divider.setBackgroundColor(0xFFE0E0E0);
                buttonLayout.addView(divider);
            }
            final int index = i;
            btn.setOnClickListener(v -> {
                if (listeners != null && index < listeners.length && listeners[index] != null) {
                    listeners[index].onClick(dialog, index);
                }
                dialog.dismiss();
            });
            buttonLayout.addView(btn, lp);
        }

        dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
    }

    @Override
    protected int getCurrentNavId() {
        return -1;
    }

    class SubTaskItem {
        long id;
        int startValue = 1;
        int endValue = 60;
        double intervalValue = 5;
        int unitType = 0;
        int frequency = 1;
        boolean isDue;
    }

    class TaskItem {
        long id;
        String title = "";
        boolean isExpanded = false;
        List<SubTaskItem> subTasks = new ArrayList<>();
    }

    private void resetAutoCollapse() {
        if (expandedPosition >= 0) {
            autoCollapseHandler.removeCallbacksAndMessages(null);
            autoCollapseHandler.postDelayed(() -> {
                if (expandedPosition >= 0) {
                    int pos = expandedPosition;
                    expandedPosition = -1;
                    adapter.notifyItemChanged(pos);
                }
            }, AUTO_COLLAPSE_DELAY);
        }
    }

    class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_COLLAPSED = 0;
        private static final int TYPE_EXPANDED = 1;

        @Override
        public int getItemViewType(int position) {
            return position == expandedPosition ? TYPE_EXPANDED : TYPE_COLLAPSED;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_EXPANDED) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task_expanded, parent, false);
                return new ExpandedViewHolder(v);
            }
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task_collapsed, parent, false);
            return new CollapsedViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TaskItem task = taskList.get(position);
            if (holder instanceof CollapsedViewHolder) {
                CollapsedViewHolder vh = (CollapsedViewHolder) holder;
                vh.tvTitle.setText(task.title);
                int pendingCount = task.subTasks.size();
                int dueNowCount = 0;
                for (SubTaskItem sub : task.subTasks) {
                    if (sub.isDue) dueNowCount++;
                }
                if (dueNowCount > 0) {
                    vh.tvStatus.setText(getString(R.string.task_due_count, dueNowCount));
                    vh.tvStatus.setTextColor(0xFFFF4444);
                } else if (pendingCount > 0) {
                    vh.tvStatus.setText(getString(R.string.task_pending_count, pendingCount));
                    vh.tvStatus.setTextColor(0xFF666666);
                } else {
                    vh.tvStatus.setText("无子计划");
                    vh.tvStatus.setTextColor(0xFF999999);
                }
                vh.btnDelete.setOnClickListener(v -> {
                    int adapterPos = holder.getAdapterPosition();
                    if (adapterPos == RecyclerView.NO_POSITION) return;
                    DialogHelper.showStyledConfirmDialog(PlanTaskActivity.this, "删除任务",
                        "确定要删除此任务及其所有子计划吗？",
                        new String[]{"取消", "删除"},
                        new DialogInterface.OnClickListener[]{ null, (d, w) -> {
                            dbHelper.deleteTask(task.id);
                            taskList.remove(adapterPos);
                            notifyItemRemoved(adapterPos);
                            notifyItemRangeChanged(adapterPos, taskList.size());
                        } });
                });
                vh.itemView.setOnClickListener(v -> {
                    int adapterPos = holder.getAdapterPosition();
                    if (adapterPos == RecyclerView.NO_POSITION) return;
                    int prev = expandedPosition;
                    if (prev == adapterPos) {
                        expandedPosition = -1;
                        notifyItemChanged(adapterPos);
                    } else {
                        expandedPosition = adapterPos;
                        if (prev >= 0) notifyItemChanged(prev);
                        notifyItemChanged(adapterPos);
                        autoCollapseHandler.removeCallbacksAndMessages(null);
                        autoCollapseHandler.postDelayed(() -> {
                            if (expandedPosition == adapterPos) {
                                expandedPosition = -1;
                                notifyItemChanged(adapterPos);
                            }
                        }, AUTO_COLLAPSE_DELAY);
                    }
                });
            } else {
                final ExpandedViewHolder vh = (ExpandedViewHolder) holder;
                vh.etTitle.setText(task.title);

                vh.itemView.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        resetAutoCollapse();
                    }
                    v.performClick();
                    return false;
                });

                vh.etTitle.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override public void afterTextChanged(Editable s) { resetAutoCollapse(); }
                });

                final SubTaskAdapter subAdapter = new SubTaskAdapter(task.subTasks, task.id) {
                    @Override void onInteraction() { resetAutoCollapse(); }
                };
                vh.rvSubTasks.setLayoutManager(new LinearLayoutManager(PlanTaskActivity.this));
                vh.rvSubTasks.setAdapter(subAdapter);
                vh.btnAddSub.setOnClickListener(v -> {
                    SubTaskItem newSub = new SubTaskItem();
                    newSub.startValue = 1;
                    newSub.endValue = 60;
                    newSub.intervalValue = 5;
                    newSub.unitType = 0;
                    newSub.frequency = 1;
                    long id = dbHelper.addSubTask(task.id, currentBatchId, "", newSub.startValue, newSub.endValue,
                            newSub.intervalValue, newSub.unitType, newSub.frequency);
                    newSub.id = id;
                    task.subTasks.add(newSub);
                    subAdapter.notifyItemInserted(task.subTasks.size() - 1);
                    resetAutoCollapse();
                });
                vh.btnCollapse.setOnClickListener(v -> {
                    int adapterPos = holder.getAdapterPosition();
                    if (adapterPos == RecyclerView.NO_POSITION) return;
                    String newName = vh.etTitle.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(task.title)) {
                        dbHelper.updateTaskName(task.id, newName);
                        task.title = newName;
                    }
                    expandedPosition = -1;
                    notifyItemChanged(adapterPos);
                    autoCollapseHandler.removeCallbacksAndMessages(null);
                });
            }
        }

        @Override
        public int getItemCount() {
            return taskList.size();
        }

        class CollapsedViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvStatus, btnDelete;
            CollapsedViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_task_title);
                tvStatus = v.findViewById(R.id.tv_task_countdown);
                btnDelete = v.findViewById(R.id.btn_delete_task);
            }
        }

        class ExpandedViewHolder extends RecyclerView.ViewHolder {
            EditText etTitle;
            RecyclerView rvSubTasks;
            View btnAddSub, btnCollapse;
            ExpandedViewHolder(View v) {
                super(v);
                etTitle = v.findViewById(R.id.et_task_title);
                rvSubTasks = v.findViewById(R.id.rv_rules);
                btnAddSub = v.findViewById(R.id.btn_add_sub_task);
                btnCollapse = v.findViewById(R.id.btn_save_task);
            }
        }
    }

    class SubTaskAdapter extends RecyclerView.Adapter<SubTaskAdapter.ViewHolder> {
        private List<SubTaskItem> list;
        private long parentId;
        void onInteraction() {}

        SubTaskAdapter(List<SubTaskItem> list, long parentId) {
            this.list = list;
            this.parentId = parentId;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rule, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SubTaskItem sub = list.get(position);
            holder.etStart.setText(String.valueOf(sub.startValue));
            holder.etEnd.setText(String.valueOf(sub.endValue));
            holder.etInterval.setText(String.valueOf((int)sub.intervalValue));
            holder.etInterval.setFilters(new InputFilter[]{new InputFilter.LengthFilter(sub.unitType == 0 ? 1 : 3)});
            holder.tvUnit.setText(sub.unitType == 0 ? "天" : "斤");
            holder.etTimes.setText(String.valueOf(sub.frequency));

            holder.itemView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP) onInteraction();
                v.performClick();
                return false;
            });

            holder.ivDelete.setOnClickListener(v -> {
                int adapterPos = holder.getAdapterPosition();
                if (adapterPos == RecyclerView.NO_POSITION) return;
                DialogHelper.showStyledConfirmDialog(PlanTaskActivity.this, "删除子计划",
                    "确定要删除此子计划吗？",
                    new String[]{"取消", "删除"},
                    new DialogInterface.OnClickListener[]{ null, (d, w) -> {
                        dbHelper.deleteTask(sub.id);
                        list.remove(adapterPos);
                        notifyItemRemoved(adapterPos);
                    } });
            });

            holder.tvUnit.setOnClickListener(v -> {
                onInteraction();
                final String[] units = {"天", "斤"};
                AlertDialog.Builder b = new AlertDialog.Builder(PlanTaskActivity.this);
                b.setTitle("选择单位");
                b.setItems(units, (d, which) -> {
                    sub.unitType = which;
                    holder.tvUnit.setText(units[which]);
                    if (which == 0) {
                        holder.etInterval.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1)});
                    } else {
                        holder.etInterval.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
                    }
                    dbHelper.updateSubTask(sub.id, sub.startValue, sub.endValue,
                            sub.intervalValue, sub.unitType, sub.frequency);
                });
                b.show();
            });

            holder.etStart.addTextChangedListener(new SimpleTextWatcher() {
                @Override void onValueChanged(int value) {
                    sub.startValue = value;
                    dbHelper.updateSubTask(sub.id, sub.startValue, sub.endValue,
                            sub.intervalValue, sub.unitType, sub.frequency);
                }
                @Override public void afterTextChanged(Editable s) { super.afterTextChanged(s); onInteraction(); }
            });
            holder.etEnd.addTextChangedListener(new SimpleTextWatcher() {
                @Override void onValueChanged(int value) {
                    sub.endValue = value;
                    dbHelper.updateSubTask(sub.id, sub.startValue, sub.endValue,
                            sub.intervalValue, sub.unitType, sub.frequency);
                }
                @Override public void afterTextChanged(Editable s) { super.afterTextChanged(s); onInteraction(); }
            });
            holder.etInterval.addTextChangedListener(new SimpleTextWatcher() {
                @Override void onValueChanged(int value) {
                    sub.intervalValue = value;
                    dbHelper.updateSubTask(sub.id, sub.startValue, sub.endValue,
                            sub.intervalValue, sub.unitType, sub.frequency);
                }
                @Override public void afterTextChanged(Editable s) { super.afterTextChanged(s); onInteraction(); }
            });
            holder.etTimes.addTextChangedListener(new SimpleTextWatcher() {
                @Override void onValueChanged(int value) {
                    sub.frequency = value;
                    dbHelper.updateSubTask(sub.id, sub.startValue, sub.endValue,
                            sub.intervalValue, sub.unitType, sub.frequency);
                }
                @Override public void afterTextChanged(Editable s) { super.afterTextChanged(s); onInteraction(); }
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            EditText etStart, etEnd, etInterval, etTimes;
            TextView tvUnit;
            ImageView ivDelete;
            ViewHolder(View v) {
                super(v);
                etStart = v.findViewById(R.id.et_start_day);
                etEnd = v.findViewById(R.id.et_end_day);
                etInterval = v.findViewById(R.id.et_interval);
                etTimes = v.findViewById(R.id.et_times);
                tvUnit = v.findViewById(R.id.tv_unit);
                ivDelete = v.findViewById(R.id.iv_delete_rule);
            }
        }
    }

    abstract class SimpleTextWatcher implements TextWatcher {
        abstract void onValueChanged(int value);
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) {
            try { onValueChanged(Integer.parseInt(s.toString())); } catch (NumberFormatException e) {}
        }
    }
}