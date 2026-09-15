package com.shrimpfarm.app;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import com.shrimpfarm.app.model.FeedCheckAlertModel;
import com.shrimpfarm.app.utils.ExcelExporter;
import com.shrimpfarm.app.utils.StoragePermissionHelper;

import java.util.List;
import java.util.Map;

public class ExportActivity extends BaseActivity {

    public static final String EXTRA_BATCH_ID = "batch_id";
    public static final String EXTRA_BATCH_NAME = "batch_name";
    public static final String EXTRA_MODE = "mode";

    private TextView tvProgress;
    private String exportBatchId;
    private String exportBatchName;
    private int exportMode;

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_home;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export);
        tvProgress = findViewById(R.id.tv_export_progress);
        tvProgress.setText(getString(R.string.export_progress));

        exportBatchId = getIntent().getStringExtra(EXTRA_BATCH_ID);
        exportBatchName = getIntent().getStringExtra(EXTRA_BATCH_NAME);
        exportMode = getIntent().getIntExtra(EXTRA_MODE, ExcelExporter.MODE_RAW);

        if (!StoragePermissionHelper.hasStoragePermission(this)) {
            Toast.makeText(this, getString(R.string.export_toast_need_permission), Toast.LENGTH_LONG).show();
            StoragePermissionHelper.requestIfNeeded(this);
            return;
        }
        startExport();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == StoragePermissionHelper.REQUEST_CODE_MANAGE
                || requestCode == StoragePermissionHelper.REQUEST_CODE_STORAGE) {
            if (StoragePermissionHelper.hasStoragePermission(this)) {
                startExport();
            } else {
                toastAndFinish(getString(R.string.export_toast_need_permission));
            }
        } else {
            toastAndFinish(getString(R.string.export_toast_failed));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == StoragePermissionHelper.REQUEST_CODE_STORAGE) {
            if (StoragePermissionHelper.hasStoragePermission(this)) {
                startExport();
            } else {
                toastAndFinish(getString(R.string.export_toast_need_permission));
            }
        }
    }

    private void startExport() {
        if (exportBatchId == null || exportBatchId.isEmpty()) {
            toastAndFinish(getString(R.string.export_toast_failed));
            return;
        }
        final String batchId = exportBatchId;
        final String batchName = exportBatchName;
        final int mode = exportMode;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final DatabaseHelper db = DatabaseHelper.getInstance(ExportActivity.this);
                try {
                    final List<FeedingRecordActivity.DayRecord> records = db.getRecordsForExport(batchId);
                    if (records.isEmpty()) {
                        toastAndFinish(getString(R.string.export_toast_no_data));
                        return;
                    }
                    final Map<String, String> mixTags = db.getMixPresetTagsMap(batchId);
                    final Map<String, String> waterTags = db.getWaterPresetTagsMap(batchId);
                    final String stockingDate = db.getStockingDate(batchId);
                    final List<DatabaseHelper.CheckRecord> checkRecords = db.getCheckRecordsByBatch(batchId);
                    final List<DatabaseHelper.WaterQualityRecord> waterRecords = db.getWaterQualityList(batchId);
                    final boolean isFourMeals = FeedCheckAlertModel.isFourMeals(db.getReadableDatabase(), batchId);
                    if (mode == ExcelExporter.MODE_BEHAVIOR) {
                        ExcelExporter.UntaggedInfo untagged =
                                ExcelExporter.collectUntaggedProducts(records, mixTags, waterTags);
                        if (!untagged.isEmpty()) {
                            showUntaggedDialog(untagged);
                            return;
                        }
                    }
                    final String path = ExcelExporter.exportAndSave(ExportActivity.this, batchName,
                            stockingDate, records, mixTags, waterTags,
                            mode == ExcelExporter.MODE_BEHAVIOR, checkRecords, waterRecords, isFourMeals);
                    toastAndFinish(getString(R.string.export_toast_done, path));
                } catch (SecurityException e) {
                    Log.e("ExportActivity", "export security denied", e);
                    toastAndFinish(getString(R.string.export_toast_need_permission));
                } catch (Exception e) {
                    Log.e("ExportActivity", "export failed", e);
                    toastAndFinish(getString(R.string.export_toast_failed));
                }
            }
        }).start();
    }

    private void showUntaggedDialog(ExcelExporter.UntaggedInfo info) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) return;
                StringBuilder sb = new StringBuilder();
                int shown = 0;
                for (String p : info.all) {
                    if (shown >= 5) break;
                    if (sb.length() > 0) sb.append(getString(R.string.export_untagged_sep));
                    sb.append(p);
                    shown++;
                }
                if (info.all.size() > shown) {
                    sb.append(getString(R.string.export_untagged_more, info.all.size() - shown));
                }
                final String msg = getString(R.string.export_untagged_msg, sb.toString());
                final int tab = info.mix.size() > 0 ? 1 : 2;
                showStyledConfirmDialog(getString(R.string.export_untagged_title), msg,
                        new String[]{getString(R.string.export_btn_later), getString(R.string.export_btn_tag)},
                        new int[]{0xFF666666, 0xFF2D84C2},
                        new DialogInterface.OnClickListener[]{
                            (d, w) -> finish(),
                            (d, w) -> {
                                startActivity(new Intent(ExportActivity.this, BasicDataActivity.class)
                                        .putExtra("open_tab", tab));
                                finish();
                            }
                        });
            }
        });
    }

    private void toastAndFinish(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    Toast.makeText(ExportActivity.this, msg, Toast.LENGTH_LONG).show();
                }
                finish();
            }
        });
    }
}