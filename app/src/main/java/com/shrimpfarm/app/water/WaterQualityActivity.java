package com.shrimpfarm.app.water;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.BasicDataActivity;
import com.shrimpfarm.app.BatchManageActivity;
import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.utils.EncryptUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WaterQualityActivity extends BaseActivity {

    private EditText etVibrio, etSalinity, etAmmonia, etNitrite, etPh, etDissolvedOxygen;
    private EditText etMaxTemp, etMinTemp, etChlorine, etHydrogenSulfide, etOrp;
    private Button btnSave, btnClear;
    private DatabaseHelper dbHelper;
    private String currentBatchId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_water_quality);

        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentBatchId = prefs.getString("current_batch_id", "");
        if (currentBatchId.isEmpty()) {
            showNoBatchDialog();
            return;
        }

        dbHelper = new DatabaseHelper(this);

        if (!isBasicDataComplete()) {
            showBasicDataIncompleteDialog();
            return;
        }

        initViews();
        loadLastRecord();
        setupBottomNavigation();
    }

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_record;
    }

    private void initViews() {
        etVibrio = findViewById(R.id.et_vibrio);
        etSalinity = findViewById(R.id.et_salinity);
        etAmmonia = findViewById(R.id.et_ammonia);
        etNitrite = findViewById(R.id.et_nitrite);
        etPh = findViewById(R.id.et_ph);
        etDissolvedOxygen = findViewById(R.id.et_dissolved_oxygen);
        etMaxTemp = findViewById(R.id.et_max_temp);
        etMinTemp = findViewById(R.id.et_min_temp);
        etChlorine = findViewById(R.id.et_chlorine);
        etHydrogenSulfide = findViewById(R.id.et_hydrogen_sulfide);
        etOrp = findViewById(R.id.et_orp);
        btnSave = findViewById(R.id.btn_save);
        btnClear = findViewById(R.id.btn_clear);

        btnSave.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveData();
                }
            });

        btnClear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearAllFields();
                }
            });
    }

    private void loadLastRecord() {
        Cursor cursor = dbHelper.getAllWaterQuality(currentBatchId);
        if (cursor != null && cursor.moveToFirst()) {
            try {
                String vibrio = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_VIBRIO)));
                String salinity = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_SALINITY)));
                String ammonia = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_AMMONIA)));
                String nitrite = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NITRITE)));
                String ph = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PH)));
                String dissolvedOxygen = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_DISSOLVED_OXYGEN)));
                String maxTemp = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_MAX_TEMP)));
                String minTemp = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_MIN_TEMP)));
                String chlorine = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHLORINE)));
                String hydrogenSulfide = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_HYDROGEN_SULFIDE)));
                String orp = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ORP)));

                etVibrio.setText(vibrio);
                etSalinity.setText(salinity);
                etAmmonia.setText(ammonia);
                etNitrite.setText(nitrite);
                etPh.setText(ph);
                etDissolvedOxygen.setText(dissolvedOxygen);
                etMaxTemp.setText(maxTemp);
                etMinTemp.setText(minTemp);
                etChlorine.setText(chlorine);
                etHydrogenSulfide.setText(hydrogenSulfide);
                etOrp.setText(orp);
            } catch (Exception e) {
                // 解密失败，保持空字段
            }
        }
        if (cursor != null) cursor.close();
    }

    private void clearAllFields() {
        etVibrio.setText("");
        etSalinity.setText("");
        etAmmonia.setText("");
        etNitrite.setText("");
        etPh.setText("");
        etDissolvedOxygen.setText("");
        etMaxTemp.setText("");
        etMinTemp.setText("");
        etChlorine.setText("");
        etHydrogenSulfide.setText("");
        etOrp.setText("");
    }

    private boolean isBasicDataComplete() {
        String waterPrepDate = dbHelper.getBasicData(currentBatchId, "water_prep_date");
        return !waterPrepDate.isEmpty() && !waterPrepDate.equals("选择日期");
    }

    private void showNoBatchDialog() {
        showStyledConfirmDialog("提示", "请先在批次管理中创建至少一个批次",
            new String[]{"退出", "去创建"},
            new int[]{0xFF666666, 0xFF4CAF50},
            new DialogInterface.OnClickListener[]{
                (dialog, which) -> finish(),
                (dialog, which) -> {
                    startActivity(new Intent(WaterQualityActivity.this, BatchManageActivity.class));
                    finish();
                }
            });
    }

    private void showBasicDataIncompleteDialog() {
        showStyledConfirmDialog("提示", "请先在基础数据中设置「做水日(拉漂白粉)」",
            new String[]{"取消", "去设置"},
            new int[]{0xFF666666, 0xFF4CAF50},
            new DialogInterface.OnClickListener[]{
                (dialog, which) -> finish(),
                (dialog, which) -> {
                    startActivity(new Intent(WaterQualityActivity.this, BasicDataActivity.class));
                    finish();
                }
            });
    }

    private void saveData() {
        String date = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(new Date());

        String vibrio = etVibrio.getText().toString().trim();
        String salinity = etSalinity.getText().toString().trim();
        String ammonia = etAmmonia.getText().toString().trim();
        String nitrite = etNitrite.getText().toString().trim();
        String ph = etPh.getText().toString().trim();
        String dissolvedOxygen = etDissolvedOxygen.getText().toString().trim();
        String maxTemp = etMaxTemp.getText().toString().trim();
        String minTemp = etMinTemp.getText().toString().trim();
        String chlorine = etChlorine.getText().toString().trim();
        String hydrogenSulfide = etHydrogenSulfide.getText().toString().trim();
        String orp = etOrp.getText().toString().trim();

        dbHelper.insertWaterQuality(currentBatchId, date, vibrio, salinity, ammonia, nitrite, ph,
                                    dissolvedOxygen, maxTemp, minTemp, chlorine, hydrogenSulfide, orp);
        loadLastRecord();
        Toast.makeText(this, "数据已保存", Toast.LENGTH_SHORT).show();
    }
}
