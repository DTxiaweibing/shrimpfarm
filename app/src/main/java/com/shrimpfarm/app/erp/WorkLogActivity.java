package com.shrimpfarm.app.erp;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
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
import com.shrimpfarm.app.finance.FinanceDb;
import com.shrimpfarm.app.finance.FinanceDb.FinLeave;
import com.shrimpfarm.app.finance.FinanceDb.FinSupplier;
import com.shrimpfarm.app.finance.FinanceDb.LeaveSummary;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class WorkLogActivity extends BaseActivity {

    private FinanceDb financeDb;
    private ListView listView;
    private WorkLogAdapter adapter;
    private List<FinSupplier> employees = new ArrayList<>();
    private int year, month;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_work_log);
        financeDb = new FinanceDb(this);
        Calendar c = Calendar.getInstance();
        year = c.get(Calendar.YEAR);
        month = c.get(Calendar.MONTH) + 1;
        setupToolbar();
        listView = findViewById(R.id.list_view);
        adapter = new WorkLogAdapter();
        listView.setAdapter(adapter);
        findViewById(R.id.btn_prev).setOnClickListener(v -> {
            month--;
            if (month < 1) { month = 12; year--; }
            loadData();
        });
        findViewById(R.id.btn_next).setOnClickListener(v -> {
            month++;
            if (month > 12) { month = 1; year++; }
            loadData();
        });
        findViewById(R.id.tv_month).setOnClickListener(v -> pickMonth());
        findViewById(R.id.btn_record_leave).setOnClickListener(v ->
                showLeaveDialog(null, -1));
        findViewById(R.id.btn_continuous_leave).setOnClickListener(v ->
                showContinuousDialog());
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (financeDb != null) loadData();
    }

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_home;
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.erp_work_log);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void pickMonth() {
        new DatePickerDialog(this, (view, y, m, d) -> {
            year = y;
            month = m + 1;
            loadData();
        }, year, month - 1, 1).show();
    }

    private void loadData() {
        TextView tvMonth = findViewById(R.id.tv_month);
        tvMonth.setText(String.format(Locale.CHINA, "%d-%02d", year, month));
        employees = financeDb.getSuppliersByType(FinanceDb.PERSON_EMPLOYEE);
        adapter.notifyDataSetChanged();
    }

    private String yearMonth() {
        return String.format(Locale.CHINA, "%d-%02d", year, month);
    }

    private List<FinSupplier> activeEmployees() {
        List<FinSupplier> list = new ArrayList<>();
        for (FinSupplier s : financeDb.getSuppliersByType(FinanceDb.PERSON_EMPLOYEE)) {
            if (!s.startDate.isEmpty() && s.startDate.compareTo(monthEnd()) <= 0) list.add(s);
        }
        return list;
    }

    private String monthEnd() {
        Calendar c = Calendar.getInstance();
        c.set(year, month - 1, 1);
        int last = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        return String.format(Locale.CHINA, "%04d/%02d/%02d", year, month, last);
    }

    private String today() {
        return new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA)
                .format(Calendar.getInstance().getTime());
    }

    private int[] parseDate(String date) {
        try {
            String[] p = date.split("/");
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
        } catch (Exception e) {
            Calendar c = Calendar.getInstance();
            return new int[]{c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)};
        }
    }

    private void showDatePicker(EditText et, String current) {
        int[] p = parseDate(current);
        new DatePickerDialog(this, (view, y, m, d) ->
                et.setText(String.format(Locale.CHINA, "%04d/%02d/%02d", y, m + 1, d)),
                p[0], p[1] - 1, p[2]).show();
    }

    private void showLeaveDialog(final FinLeave preset, final long presetEmployeeId) {
        List<FinSupplier> emps = activeEmployees();
        if (emps.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.erp_no_employee)
                    .setPositiveButton(R.string.btn_ok, null)
                    .show();
            return;
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        final Spinner spEmp = new Spinner(this);
        List<String> names = new ArrayList<>();
        final List<Long> ids = new ArrayList<>();
        for (FinSupplier s : emps) {
            names.add(s.name);
            ids.add(s.id);
        }
        spEmp.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, names));
        int presetPos = 0;
        if (presetEmployeeId > 0) {
            for (int i = 0; i < ids.size(); i++) {
                if (ids.get(i) == presetEmployeeId) { presetPos = i; break; }
            }
        }
        spEmp.setSelection(presetPos);
        layout.addView(createLabel(R.string.erp_leave_employee));
        layout.addView(spEmp);

        final EditText etDate = new EditText(this);
        etDate.setText(today());
        etDate.setFocusable(false);
        layout.addView(createLabel(R.string.erp_leave_date));
        layout.addView(etDate);
        etDate.setOnClickListener(v -> showDatePicker(etDate, etDate.getText().toString()));

        final int[] amType = {0};
        final int[] pmType = {0};
        layout.addView(createLabel(R.string.erp_am));
        final LinearLayout amRow = new LinearLayout(this);
        layout.addView(amRow);
        layout.addView(createLabel(R.string.erp_pm));
        final LinearLayout pmRow = new LinearLayout(this);
        layout.addView(pmRow);
        buildTypeRow(amRow, amType);
        buildTypeRow(pmRow, pmType);

        final EditText etRemark = new EditText(this);
        etRemark.setHint(R.string.finance_remark);
        layout.addView(createLabel(R.string.finance_remark));
        layout.addView(etRemark);

        if (preset != null) {
            etDate.setText(preset.date);
            amType[0] = preset.amType;
            pmType[0] = preset.pmType;
            etRemark.setText(preset.remark);
            applyTypeButtons(amRow, amType);
            applyTypeButtons(pmRow, pmType);
        }

        new AlertDialog.Builder(this)
                .setTitle(preset == null ? R.string.erp_record_leave : R.string.finance_edit)
                .setView(layout)
                .setPositiveButton(R.string.btn_ok, (d, w) -> {
                    String date = etDate.getText().toString().trim();
                    if (date.isEmpty()) return;
                    long empId = ids.get(spEmp.getSelectedItemPosition());
                    financeDb.saveLeave(empId, date, amType[0], pmType[0],
                            etRemark.getText().toString().trim());
                    Toast.makeText(this, R.string.erp_leave_saved, Toast.LENGTH_SHORT).show();
                    loadData();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private TextView createLabel(int res) {
        TextView tv = new TextView(this);
        tv.setText(res);
        tv.setTextColor(0xFF333333);
        tv.setTextSize(14);
        tv.setPadding(0, 10, 0, 4);
        return tv;
    }

    private void buildTypeRow(LinearLayout row, final int[] holder) {
        String[] labels = {getString(R.string.erp_attendance),
                getString(R.string.erp_leave_personal),
                getString(R.string.erp_leave_sick),
                getString(R.string.erp_leave_absent)};
        for (int i = 0; i < labels.length; i++) {
            final int type = i;
            final TextView tv = new TextView(this);
            tv.setText(labels[i]);
            tv.setTextSize(14);
            tv.setTag(type);
            tv.setPadding(8, 8, 8, 8);
            tv.setOnClickListener(v -> {
                holder[0] = type;
                applyTypeButtons(row, holder);
            });
            row.addView(tv);
        }
        applyTypeButtons(row, holder);
    }

    private void applyTypeButtons(LinearLayout row, int[] holder) {
        int selected = holder[0];
        for (int i = 0; i < row.getChildCount(); i++) {
            TextView tv = (TextView) row.getChildAt(i);
            int t = (int) tv.getTag();
            tv.setTextColor(t == selected ? 0xFF4CAF50 : 0xFF888888);
            tv.setTextSize(t == selected ? 15 : 14);
        }
    }

    private void showContinuousDialog() {
        List<FinSupplier> emps = activeEmployees();
        if (emps.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.erp_no_employee)
                    .setPositiveButton(R.string.btn_ok, null)
                    .show();
            return;
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        final Spinner spEmp = new Spinner(this);
        final List<Long> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (FinSupplier s : emps) {
            names.add(s.name);
            ids.add(s.id);
        }
        spEmp.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, names));
        layout.addView(createLabel(R.string.erp_leave_employee));
        layout.addView(spEmp);

        final int[] type = {1};
        final TextView tvType = new TextView(this);
        tvType.setText(getString(R.string.erp_all_day_personal));
        tvType.setTextColor(0xFF4CAF50);
        tvType.setTextSize(15);
        tvType.setPadding(8, 8, 8, 8);
        tvType.setOnClickListener(v -> {
            type[0] = type[0] == 1 ? 2 : 1;
            tvType.setText(type[0] == 1 ? R.string.erp_all_day_personal : R.string.erp_all_day_sick);
        });
        layout.addView(createLabel(R.string.erp_leave_type));
        layout.addView(tvType);

        final EditText etStart = new EditText(this);
        etStart.setText(today());
        etStart.setFocusable(false);
        final EditText etEnd = new EditText(this);
        etEnd.setText(today());
        etEnd.setFocusable(false);
        layout.addView(createLabel(R.string.erp_leave_start));
        layout.addView(etStart);
        etStart.setOnClickListener(v -> showDatePicker(etStart, etStart.getText().toString()));
        layout.addView(createLabel(R.string.erp_leave_end));
        layout.addView(etEnd);
        etEnd.setOnClickListener(v -> showDatePicker(etEnd, etEnd.getText().toString()));

        final TextView tvDays = new TextView(this);
        tvDays.setTextColor(0xFF333333);
        tvDays.setTextSize(14);
        tvDays.setPadding(0, 8, 0, 4);
        updateDays(tvDays, etStart, etEnd);
        layout.addView(createLabel(R.string.erp_leave_days_calc));
        layout.addView(tvDays);

        final EditText etRemark = new EditText(this);
        layout.addView(createLabel(R.string.finance_remark));
        layout.addView(etRemark);

        new AlertDialog.Builder(this)
                .setTitle(R.string.erp_continuous_leave)
                .setView(layout)
                .setPositiveButton(R.string.btn_ok, (d, w) -> {
                    String start = etStart.getText().toString().trim();
                    String end = etEnd.getText().toString().trim();
                    if (start.isEmpty() || end.isEmpty()) return;
                    if (start.compareTo(end) > 0) {
                        Toast.makeText(this, R.string.erp_leave_period, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long empId = ids.get(spEmp.getSelectedItemPosition());
                    java.util.Set<String> dates = datesBetween(start, end);
                    for (String date : dates) {
                        financeDb.saveLeave(empId, date, type[0], type[0],
                                etRemark.getText().toString().trim());
                    }
                    Toast.makeText(this, R.string.erp_leave_saved, Toast.LENGTH_SHORT).show();
                    loadData();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void updateDays(TextView tv, EditText etStart, EditText etEnd) {
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA);
            long s = fmt.parse(etStart.getText().toString()).getTime();
            long e = fmt.parse(etEnd.getText().toString()).getTime();
            int days = (int) ((e - s) / 86400000L) + 1;
            tv.setText(days + " " + getString(R.string.erp_leave_days));
        } catch (Exception ignored) {
            tv.setText("0");
        }
    }

    private java.util.Set<String> datesBetween(String start, String end) {
        java.util.Set<String> set = new java.util.HashSet<>();
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA);
            Calendar cal = Calendar.getInstance();
            cal.setTime(fmt.parse(start));
            long endTime = fmt.parse(end).getTime();
            int guard = 0;
            while (cal.getTimeInMillis() <= endTime && guard < 400) {
                set.add(fmt.format(cal.getTime()));
                cal.add(Calendar.DATE, 1);
                guard++;
            }
        } catch (Exception ignored) { /* empty */ }
        return set;
    }

    private class WorkLogAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return employees.size();
        }

        @Override
        public Object getItem(int position) {
            return employees.get(position);
        }

        @Override
        public long getItemId(int position) {
            return employees.get(position).id;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(WorkLogActivity.this)
                        .inflate(R.layout.item_work_log, parent, false);
            }
            final FinSupplier s = employees.get(position);
            TextView tvName = convertView.findViewById(R.id.tv_name);
            TextView tvSummary = convertView.findViewById(R.id.tv_summary);
            TextView tvActions = convertView.findViewById(R.id.tv_actions);

            tvName.setText(s.name);
            String ym = yearMonth();
            LeaveSummary sum = financeDb.sumLeaveByMonth(s.id, ym);
            String monthStart = String.format(Locale.CHINA, "%04d/%02d/%02d", year, month, 1);
            String actualStart = (!s.startDate.isEmpty() && s.startDate.compareTo(monthStart) >= 0)
                    ? s.startDate : monthStart;
            int totalWork = FinanceDb.countWorkingDays(actualStart, monthEnd(),
                    new java.util.HashSet<>(financeDb.getRestDays(s.id)));
            double workDays = FinanceDb.round2(totalWork - sum.personalDays - sum.sickDays - sum.absentDays);
            tvSummary.setText(getString(R.string.erp_leave_personal) + ": " + fmt(sum.personalDays)
                    + "  " + getString(R.string.erp_leave_sick) + ": " + fmt(sum.sickDays)
                    + "  " + getString(R.string.erp_leave_absent) + ": " + fmt(sum.absentDays)
                    + "  " + getString(R.string.erp_attendance) + ": " + fmt(workDays));
            tvActions.setText(R.string.finance_edit);
            tvActions.setOnClickListener(v -> {
                List<FinanceDb.FinLeave> leaves = financeDb.getLeavesByMonth(s.id, ym);
                if (leaves.isEmpty()) {
                    showLeaveDialog(null, s.id);
                    return;
                }
                String[] items = new String[leaves.size()];
                for (int i = 0; i < leaves.size(); i++) {
                    FinanceDb.FinLeave l = leaves.get(i);
                    items[i] = l.date + "  " + typeLabel(l.amType) + "/" + typeLabel(l.pmType)
                            + (l.remark.isEmpty() ? "" : "  (" + l.remark + ")");
                }
                new AlertDialog.Builder(WorkLogActivity.this)
                        .setTitle(s.name)
                        .setItems(items, (d, which) -> {
                            FinanceDb.FinLeave l = leaves.get(which);
                            new AlertDialog.Builder(WorkLogActivity.this)
                                    .setTitle(l.date)
                                    .setMessage(typeLabel(l.amType) + " / " + typeLabel(l.pmType)
                                            + (l.remark.isEmpty() ? "" : "\n" + l.remark))
                                    .setPositiveButton(R.string.btn_ok, (d2, w2) -> {
                                        financeDb.deleteLeave(s.id, l.date);
                                        Toast.makeText(WorkLogActivity.this, R.string.erp_leave_deleted,
                                                Toast.LENGTH_SHORT).show();
                                        loadData();
                                    })
                                    .setNegativeButton(R.string.btn_cancel, null)
                                    .show();
                        })
                        .setNegativeButton(R.string.btn_cancel, null)
                        .show();
            });
            return convertView;
        }
    }

    private String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.CHINA, "%.1f", v);
    }

    private String typeLabel(int type) {
        switch (type) {
            case 1: return getString(R.string.erp_leave_personal);
            case 2: return getString(R.string.erp_leave_sick);
            case 3: return getString(R.string.erp_leave_absent);
            default: return getString(R.string.erp_attendance);
        }
    }
}