package com.shrimpfarm.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.shrimpfarm.app.model.PriceCategory;
import com.shrimpfarm.app.model.PriceData;
import com.shrimpfarm.app.model.PriceItem;
import com.shrimpfarm.app.model.PricePoint;
import com.shrimpfarm.app.utils.EncryptUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "FeedingRecord.db";
    private static final int DATABASE_VERSION = 1;

    // ==================== 表名 ====================
    public static final String TABLE_DAILY_RECORDS = "daily_records";
    public static final String TABLE_FEEDING_STATS = "feeding_stats";
    public static final String TABLE_WATER_QUALITY = "water_quality";
    public static final String TABLE_BASIC_DATA = "basic_data";
    public static final String TABLE_MIX_PRESETS = "mix_presets";
    public static final String TABLE_WATER_PRESETS = "water_presets";
    public static final String TABLE_FEEDING_CHECK_RECORDS = "feeding_check_records";
    public static final String TABLE_MARKET_PRICES = "market_prices";
    public static final String TABLE_PLAN_TASKS = "plan_tasks";
    public static final String TABLE_FEEDING_CHECK_ANALYSIS = "feeding_check_analysis";

    // ==================== 批次ID字段 ====================
    public static final String COLUMN_BATCH_ID = "batch_id";

    // ==================== 每日记录表字段 ====================
    public static final String COLUMN_DATE = "date";
    public static final String COLUMN_BREAKFAST = "breakfast";
    public static final String COLUMN_LUNCH = "lunch";
    public static final String COLUMN_DINNER = "dinner";
    public static final String COLUMN_NIGHT_SNACK = "nightSnack";
    public static final String COLUMN_WATER_MIX1 = "waterMix1";
    public static final String COLUMN_WATER_MIX2 = "waterMix2";
    public static final String COLUMN_WATER_MIX3 = "waterMix3";
    public static final String COLUMN_WATER_MIX4 = "waterMix4";
    public static final String COLUMN_MIX1 = "mix1";
    public static final String COLUMN_MIX2 = "mix2";
    public static final String COLUMN_MIX3 = "mix3";
    public static final String COLUMN_MIX4 = "mix4";
    public static final String COLUMN_REMARK = "remark";

    // ==================== 统计表字段 ====================
    public static final String COLUMN_STATS_ID = "id";
    public static final String COLUMN_STATS_DATE = "date";
    public static final String COLUMN_AVG_DURATION = "avg_duration";
    public static final String COLUMN_RECORD_TIME = "record_time";

    // ==================== 水质检测表字段 ====================
    public static final String COLUMN_WQ_ID = "id";
    public static final String COLUMN_WQ_DATE = "date";
    public static final String COLUMN_VIBRIO = "vibrio";
    public static final String COLUMN_SALINITY = "salinity";
    public static final String COLUMN_AMMONIA = "ammonia";
    public static final String COLUMN_NITRITE = "nitrite";
    public static final String COLUMN_PH = "ph";
    public static final String COLUMN_DISSOLVED_OXYGEN = "dissolved_oxygen";
    public static final String COLUMN_MAX_TEMP = "max_temp";
    public static final String COLUMN_MIN_TEMP = "min_temp";
    public static final String COLUMN_CHLORINE = "chlorine";
    public static final String COLUMN_HYDROGEN_SULFIDE = "hydrogen_sulfide";
    public static final String COLUMN_ORP = "orp";

    // ==================== 基础数据表字段 ====================
    public static final String COLUMN_BD_KEY = "key";
    public static final String COLUMN_BD_VALUE = "value";

    // ==================== 拌料动保预设表字段 ====================
    public static final String COLUMN_MIX_ID = "id";
    public static final String COLUMN_MIX_ROW = "row_num";
    public static final String COLUMN_MIX_NAME = "name";
    public static final String COLUMN_MIX_TAGS = "tags";

    // ==================== 调水动保预设表字段 ====================
    public static final String COLUMN_WATER_ID = "id";
    public static final String COLUMN_WATER_ROW = "row_num";
    public static final String COLUMN_WATER_NAME = "name";
    public static final String COLUMN_WATER_TAGS = "tags";

    // ==================== 计划任务表字段 ====================
    public static final String COLUMN_TASK_ID = "task_id";
    public static final String COLUMN_PARENT_ID = "parent_id";
    public static final String COLUMN_TASK_NAME = "task_name";
    public static final String COLUMN_START_VALUE = "start_value";
    public static final String COLUMN_END_VALUE = "end_value";
    public static final String COLUMN_INTERVAL_VALUE = "interval_value";
    public static final String COLUMN_UNIT_TYPE = "unit_type";
    public static final String COLUMN_FREQUENCY = "frequency";
    public static final String COLUMN_NEXT_DUE_DATE = "next_due_date";
    public static final String COLUMN_TASK_STATUS = "task_status";
    public static final String COLUMN_IS_ACTIVE = "is_active";
    public static final String COLUMN_CREATED_AT = "created_at";
    public static final String COLUMN_LAST_TRIGGER_DAY = "last_trigger_day";
    public static final String COLUMN_LAST_TRIGGER_FEED = "last_trigger_feed";

    // ==================== 查料分析表字段 ====================
    public static final String COLUMN_FCA_AVG = "avg_seconds";
    public static final String COLUMN_FCA_STANDARD = "standard_seconds";

    // ==================== 查料记录数据类 ====================
    public static class CheckRecord {
        public long id;
        public String batchId;
        public String recordDate;
        public String startTime;
        public String endTime;
        public String shedNumber;
        public int shedRowIndex;
        public String checkTime;
        public long durationSeconds;
        public boolean excluded;
        public int waterPercentage;
        public long createdAt;
    }

    public static class CheckSummary {
        public List<DailyCheckSummary> dailySummaries;
    }

    public static class DailyCheckSummary {
        public String date;
        public int shedCount;
        public long avgDuration;
        public long minDuration;
        public long maxDuration;
    }

    // ==================== 新增内部类 ====================
    public static class DailyFeedSummary {
        public String date;
        public float totalFeed;
        public DailyFeedSummary(String date, float totalFeed) {
            this.date = date;
            this.totalFeed = totalFeed;
        }
    }

    public static class DurationSummary {
        public String date;
        public long avgDuration;
        public DurationSummary(String date, long avgDuration) {
            this.date = date;
            this.avgDuration = avgDuration;
        }
    }

    public static class DurationByShedSummary {
        public String date;
        public long avgDurationMillis;
        public DurationByShedSummary(String date, long avgDurationMillis) {
            this.date = date;
            this.avgDurationMillis = avgDurationMillis;
        }
    }

    public static class ShedDurationSummary {
        public String shedNumber;
        public long avgDurationMillis;
        public ShedDurationSummary(String shedNumber, long avgDurationMillis) {
            this.shedNumber = shedNumber;
            this.avgDurationMillis = avgDurationMillis;
        }
    }

    // ==================== 工具方法 ====================
    public static String removeUsagePrefix(String s) {
        if (s == null) return "";
        return s.replaceFirst("^【[^】]+】", "");
    }

    public static String extractProductName(String storedValue) {
        if (storedValue == null) return "";
        String withoutPrefix = removeUsagePrefix(storedValue);
        int plusIndex = withoutPrefix.indexOf('+');
        if (plusIndex >= 0) {
            return withoutPrefix.substring(plusIndex + 1);
        }
        return withoutPrefix;
    }

    public static String extractTagContent(String storedValue) {
        if (storedValue == null) return "";
        String withoutPrefix = removeUsagePrefix(storedValue);
        int plusIndex = withoutPrefix.indexOf('+');
        if (plusIndex >= 0) {
            return withoutPrefix.substring(0, plusIndex);
        }
        return "";
    }

    // ==================== 建表SQL ====================
    private static final String CREATE_DAILY_TABLE =
            "CREATE TABLE " + TABLE_DAILY_RECORDS + " (" +
                    COLUMN_DATE + " TEXT, " +
                    COLUMN_BATCH_ID + " TEXT, " +
                    COLUMN_BREAKFAST + " TEXT, " +
                    COLUMN_LUNCH + " TEXT, " +
                    COLUMN_DINNER + " TEXT, " +
                    COLUMN_NIGHT_SNACK + " TEXT, " +
                    COLUMN_WATER_MIX1 + " TEXT, " +
                    COLUMN_WATER_MIX2 + " TEXT, " +
                    COLUMN_WATER_MIX3 + " TEXT, " +
                    COLUMN_WATER_MIX4 + " TEXT, " +
                    COLUMN_MIX1 + " TEXT, " +
                    COLUMN_MIX2 + " TEXT, " +
                    COLUMN_MIX3 + " TEXT, " +
                    COLUMN_MIX4 + " TEXT, " +
                    COLUMN_REMARK + " TEXT, " +
                    "PRIMARY KEY (" + COLUMN_DATE + ", " + COLUMN_BATCH_ID + "))";

    private static final String CREATE_STATS_TABLE =
            "CREATE TABLE " + TABLE_FEEDING_STATS + " (" +
                    COLUMN_STATS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_STATS_DATE + " TEXT, " +
                    COLUMN_BATCH_ID + " TEXT, " +
                    COLUMN_AVG_DURATION + " INTEGER, " +
                    COLUMN_RECORD_TIME + " INTEGER)";

    private static final String CREATE_WATER_QUALITY_TABLE =
            "CREATE TABLE " + TABLE_WATER_QUALITY + " (" +
                    COLUMN_WQ_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_WQ_DATE + " TEXT, " +
                    COLUMN_BATCH_ID + " TEXT, " +
                    COLUMN_VIBRIO + " TEXT, " +
                    COLUMN_SALINITY + " TEXT, " +
                    COLUMN_AMMONIA + " TEXT, " +
                    COLUMN_NITRITE + " TEXT, " +
                    COLUMN_PH + " TEXT, " +
                    COLUMN_DISSOLVED_OXYGEN + " TEXT, " +
                    COLUMN_MAX_TEMP + " TEXT, " +
                    COLUMN_MIN_TEMP + " TEXT, " +
                    COLUMN_CHLORINE + " TEXT, " +
                    COLUMN_HYDROGEN_SULFIDE + " TEXT, " +
                    COLUMN_ORP + " TEXT)";

    private static final String CREATE_BASIC_DATA_TABLE =
            "CREATE TABLE " + TABLE_BASIC_DATA + " (" +
                    COLUMN_BD_KEY + " TEXT, " +
                    COLUMN_BATCH_ID + " TEXT, " +
                    COLUMN_BD_VALUE + " TEXT, " +
                    "PRIMARY KEY (" + COLUMN_BD_KEY + ", " + COLUMN_BATCH_ID + "))";

    private static final String CREATE_MIX_PRESETS_TABLE =
            "CREATE TABLE " + TABLE_MIX_PRESETS + " (" +
                    COLUMN_MIX_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_MIX_ROW + " INTEGER, " +
                    COLUMN_BATCH_ID + " TEXT, " +
                    COLUMN_MIX_NAME + " TEXT, " +
                    COLUMN_MIX_TAGS + " TEXT, " +
                    "UNIQUE(" + COLUMN_MIX_ROW + ", " + COLUMN_BATCH_ID + "))";

    private static final String CREATE_WATER_PRESETS_TABLE =
            "CREATE TABLE " + TABLE_WATER_PRESETS + " (" +
                    COLUMN_WATER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_WATER_ROW + " INTEGER, " +
                    COLUMN_BATCH_ID + " TEXT, " +
                    COLUMN_WATER_NAME + " TEXT, " +
                    COLUMN_WATER_TAGS + " TEXT, " +
                    "UNIQUE(" + COLUMN_WATER_ROW + ", " + COLUMN_BATCH_ID + "))";

    private static final String CREATE_FEEDING_CHECK_TABLE =
            "CREATE TABLE " + TABLE_FEEDING_CHECK_RECORDS + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "batch_id TEXT NOT NULL, " +
                    "record_date TEXT NOT NULL, " +
                    "start_time TEXT NOT NULL, " +
                    "end_time TEXT NOT NULL, " +
                    "shed_number TEXT NOT NULL, " +
                    "shed_row_index INTEGER NOT NULL, " +
                    "check_time TEXT NOT NULL, " +
                    "duration_seconds TEXT NOT NULL, " +
                    "is_excluded INTEGER DEFAULT 0, " +
                    "water_percentage INTEGER, " +
                    "created_at INTEGER NOT NULL)";

    private static final String CREATE_FEEDING_CHECK_ANALYSIS_TABLE =
            "CREATE TABLE " + TABLE_FEEDING_CHECK_ANALYSIS + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_BATCH_ID + " TEXT NOT NULL, " +
                    COLUMN_STATS_DATE + " TEXT NOT NULL, " +
                    COLUMN_FCA_AVG + " REAL NOT NULL, " +
                    COLUMN_FCA_STANDARD + " REAL NOT NULL, " +
                    COLUMN_RECORD_TIME + " INTEGER NOT NULL)";

    private static final String CREATE_MARKET_PRICES_TABLE =
            "CREATE TABLE " + TABLE_MARKET_PRICES + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "date TEXT NOT NULL, " +
                    "category TEXT, " +
                    "item_name TEXT, " +
                    "price TEXT, " +
                    "saved_at INTEGER NOT NULL)";

    private static final String CREATE_PLAN_TASKS_TABLE =
            "CREATE TABLE " + TABLE_PLAN_TASKS + " (" +
                    COLUMN_TASK_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_PARENT_ID + " INTEGER DEFAULT -1, " +
                    COLUMN_BATCH_ID + " TEXT, " +
                    COLUMN_TASK_NAME + " TEXT, " +
                    COLUMN_START_VALUE + " INTEGER DEFAULT 1, " +
                    COLUMN_END_VALUE + " INTEGER DEFAULT 60, " +
                    COLUMN_INTERVAL_VALUE + " REAL DEFAULT 5.0, " +
                    COLUMN_UNIT_TYPE + " INTEGER DEFAULT 0, " +
                    COLUMN_FREQUENCY + " INTEGER DEFAULT 1, " +
                    COLUMN_NEXT_DUE_DATE + " INTEGER DEFAULT 0, " +
                    COLUMN_TASK_STATUS + " INTEGER DEFAULT 0, " +
                    COLUMN_IS_ACTIVE + " INTEGER DEFAULT 1, " +
                    COLUMN_CREATED_AT + " INTEGER DEFAULT 0, " +
                    COLUMN_LAST_TRIGGER_DAY + " INTEGER DEFAULT 0, " +
                    COLUMN_LAST_TRIGGER_FEED + " REAL DEFAULT 0.0)";

    private static DatabaseHelper instance;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        instance = this;
    }

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        } else {
            try {
                instance.getWritableDatabase();
            } catch (Exception e) {
                instance = new DatabaseHelper(context.getApplicationContext());
            }
        }
        return instance;
    }

    public static void closeInstance() {
        if (instance != null) {
            try { instance.close(); } catch (Exception ignored) {}
            instance = null;
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_DAILY_TABLE);
        db.execSQL(CREATE_STATS_TABLE);
        db.execSQL(CREATE_WATER_QUALITY_TABLE);
        db.execSQL(CREATE_BASIC_DATA_TABLE);
        db.execSQL(CREATE_MIX_PRESETS_TABLE);
        db.execSQL(CREATE_WATER_PRESETS_TABLE);
        db.execSQL(CREATE_FEEDING_CHECK_TABLE);
        db.execSQL(CREATE_MARKET_PRICES_TABLE);
        db.execSQL(CREATE_PLAN_TASKS_TABLE);
        db.execSQL(CREATE_FEEDING_CHECK_ANALYSIS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    // ==================== 加密封装 ====================
    private ContentValues encryptContentValues(ContentValues values) {
        ContentValues encrypted = new ContentValues(values.size());
        for (String key : values.keySet()) {
            Object value = values.get(key);
            if (value instanceof String) {
                if (COLUMN_DATE.equals(key) || COLUMN_BATCH_ID.equals(key)) {
                    encrypted.put(key, (String) value);
                } else {
                    encrypted.put(key, EncryptUtils.encrypt((String) value));
                }
            } else {
                if (value instanceof Integer) {
                    encrypted.put(key, (Integer) value);
                } else if (value instanceof Long) {
                    encrypted.put(key, (Long) value);
                } else if (value instanceof Double) {
                    encrypted.put(key, (Double) value);
                } else {
                    encrypted.put(key, String.valueOf(value));
                }
            }
        }
        return encrypted;
    }

    // ==================== 每日记录表操作 ====================
    public void saveRecord(String batchId, FeedingRecordActivity.DayRecord record) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_DATE, record.date);
        values.put(COLUMN_BATCH_ID, batchId);
        values.put(COLUMN_BREAKFAST, record.breakfast);
        values.put(COLUMN_LUNCH, record.lunch);
        values.put(COLUMN_DINNER, record.dinner);
        values.put(COLUMN_NIGHT_SNACK, record.nightSnack);
        values.put(COLUMN_WATER_MIX1, record.waterMix1);
        values.put(COLUMN_WATER_MIX2, record.waterMix2);
        values.put(COLUMN_WATER_MIX3, record.waterMix3);
        values.put(COLUMN_WATER_MIX4, record.waterMix4);
        values.put(COLUMN_MIX1, record.mix1);
        values.put(COLUMN_MIX2, record.mix2);
        values.put(COLUMN_MIX3, record.mix3);
        values.put(COLUMN_MIX4, record.mix4);
        values.put(COLUMN_REMARK, record.remark);

        ContentValues encryptedValues = encryptContentValues(values);
        db.insertWithOnConflict(TABLE_DAILY_RECORDS, null, encryptedValues, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public FeedingRecordActivity.DayRecord getRecordByDate(String batchId, String date) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_DAILY_RECORDS, null,
                COLUMN_BATCH_ID + "=? AND " + COLUMN_DATE + "=?",
                new String[]{batchId, date}, null, null, null);
        FeedingRecordActivity.DayRecord record = new FeedingRecordActivity.DayRecord();
        record.date = date;
        if (cursor.moveToFirst()) {
            record.breakfast = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_BREAKFAST)));
            record.lunch = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LUNCH)));
            record.dinner = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DINNER)));
            record.nightSnack = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NIGHT_SNACK)));
            record.waterMix1 = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WATER_MIX1)));
            record.waterMix2 = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WATER_MIX2)));
            record.waterMix3 = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WATER_MIX3)));
            record.waterMix4 = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WATER_MIX4)));
            record.mix1 = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MIX1)));
            record.mix2 = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MIX2)));
            record.mix3 = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MIX3)));
            record.mix4 = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MIX4)));
            record.remark = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_REMARK)));
        }
        cursor.close();
        return record;
    }

    // ==================== 统计表操作 ====================
    public void insertFeedingStats(String batchId, String date, long avgDurationMillis, long recordTime) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_STATS_DATE, date);
        values.put(COLUMN_BATCH_ID, batchId);
        values.put(COLUMN_AVG_DURATION, avgDurationMillis);
        values.put(COLUMN_RECORD_TIME, recordTime);
        db.insert(TABLE_FEEDING_STATS, null, values);
    }

    public Cursor getFeedingStatsByDate(String batchId, String date) {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(TABLE_FEEDING_STATS, null,
                COLUMN_BATCH_ID + "=? AND " + COLUMN_STATS_DATE + "=?",
                new String[]{batchId, date}, null, null, COLUMN_RECORD_TIME + " ASC");
    }

    // ==================== 查料分析表操作 ====================
    public void insertFeedingCheckAnalysis(String batchId, String date, double avgSeconds, double standardSeconds) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_BATCH_ID, batchId);
        values.put(COLUMN_STATS_DATE, date);
        values.put(COLUMN_FCA_AVG, avgSeconds);
        values.put(COLUMN_FCA_STANDARD, standardSeconds);
        values.put(COLUMN_RECORD_TIME, System.currentTimeMillis());
        db.insert(TABLE_FEEDING_CHECK_ANALYSIS, null, values);
    }

    public Cursor getFeedingCheckAnalysis(String batchId, int limit) {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(TABLE_FEEDING_CHECK_ANALYSIS, null,
                COLUMN_BATCH_ID + "=?", new String[]{batchId},
                null, null, COLUMN_RECORD_TIME + " DESC",
                String.valueOf(limit));
    }

    // ==================== 水质检测表操作 ====================
    public void insertWaterQuality(String batchId, String date, String vibrio, String salinity, String ammonia,
                                   String nitrite, String ph, String dissolvedOxygen,
                                   String maxTemp, String minTemp,
                                   String chlorine, String hydrogenSulfide, String orp) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_WQ_DATE, date);
        values.put(COLUMN_BATCH_ID, batchId);
        values.put(COLUMN_VIBRIO, vibrio);
        values.put(COLUMN_SALINITY, salinity);
        values.put(COLUMN_AMMONIA, ammonia);
        values.put(COLUMN_NITRITE, nitrite);
        values.put(COLUMN_PH, ph);
        values.put(COLUMN_DISSOLVED_OXYGEN, dissolvedOxygen);
        values.put(COLUMN_MAX_TEMP, maxTemp);
        values.put(COLUMN_MIN_TEMP, minTemp);
        values.put(COLUMN_CHLORINE, chlorine);
        values.put(COLUMN_HYDROGEN_SULFIDE, hydrogenSulfide);
        values.put(COLUMN_ORP, orp);

        ContentValues encryptedValues = encryptContentValues(values);
        db.insert(TABLE_WATER_QUALITY, null, encryptedValues);
    }

    public Cursor getAllWaterQuality(String batchId) {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(TABLE_WATER_QUALITY, null,
                COLUMN_BATCH_ID + "=?", new String[]{batchId},
                null, null, COLUMN_WQ_DATE + " DESC, rowid DESC");
    }

    // ==================== 基础数据表操作 ====================
    public void saveBasicData(String batchId, String key, String value) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_BD_KEY, key);
        values.put(COLUMN_BATCH_ID, batchId);
        values.put(COLUMN_BD_VALUE, EncryptUtils.encrypt(value));
        db.insertWithOnConflict(TABLE_BASIC_DATA, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String getBasicData(String batchId, String key) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_BASIC_DATA, new String[]{COLUMN_BD_VALUE},
                COLUMN_BD_KEY + "=? AND " + COLUMN_BATCH_ID + "=?",
                new String[]{key, batchId}, null, null, null);
        String value = "";
        if (cursor.moveToFirst()) {
            value = EncryptUtils.decrypt(cursor.getString(0));
        }
        cursor.close();
        return value;
    }

    // ==================== 拌料动保预设表操作 ====================
    public void saveMixPreset(String batchId, int row, String name, String tags) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_MIX_ROW, row);
        values.put(COLUMN_BATCH_ID, batchId);
        values.put(COLUMN_MIX_NAME, EncryptUtils.encrypt(name));
        values.put(COLUMN_MIX_TAGS, EncryptUtils.encrypt(tags));
        db.insertWithOnConflict(TABLE_MIX_PRESETS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String getMixPresetByRow(String batchId, int row) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_MIX_PRESETS, new String[]{COLUMN_MIX_NAME},
                COLUMN_MIX_ROW + "=? AND " + COLUMN_BATCH_ID + "=?",
                new String[]{String.valueOf(row), batchId}, null, null, null);
        String name = "";
        if (cursor.moveToFirst()) {
            name = EncryptUtils.decrypt(cursor.getString(0));
        }
        cursor.close();
        return name;
    }

    public String getMixPresetTags(String batchId, int row) {
        String tags = "";
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_MIX_PRESETS, new String[]{COLUMN_MIX_TAGS},
                COLUMN_MIX_ROW + "=? AND " + COLUMN_BATCH_ID + "=?",
                new String[]{String.valueOf(row), batchId}, null, null, null);
        if (cursor.moveToFirst()) {
            tags = EncryptUtils.decrypt(cursor.getString(0));
        }
        cursor.close();
        return tags != null ? tags : "";
    }

    public List<String> getAllMixPresetNames(String batchId) {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_MIX_PRESETS, new String[]{COLUMN_MIX_NAME},
                COLUMN_BATCH_ID + "=?", new String[]{batchId},
                null, null, COLUMN_MIX_ROW + " ASC");
        while (cursor.moveToNext()) {
            String fullName = EncryptUtils.decrypt(cursor.getString(0));
            if (fullName != null && !fullName.isEmpty()) {
                String pure = removeUsagePrefix(fullName);
                list.add(pure);
            }
        }
        cursor.close();
        list.add("");
        return list;
    }

    public Map<String, String> getMixPresetTagsMap(String batchId) {
        Map<String, String> map = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_MIX_PRESETS, new String[]{COLUMN_MIX_NAME, COLUMN_MIX_TAGS},
                COLUMN_BATCH_ID + "=?", new String[]{batchId},
                null, null, COLUMN_MIX_ROW + " ASC");
        while (cursor.moveToNext()) {
            String fullName = EncryptUtils.decrypt(cursor.getString(0));
            String tagsWithPrefix = EncryptUtils.decrypt(cursor.getString(1));
            if (fullName != null && !fullName.isEmpty()) {
                String product = removeUsagePrefix(fullName);
                String tagContent = "";
                if (tagsWithPrefix != null && !tagsWithPrefix.isEmpty()) {
                    String[] tagsArr = tagsWithPrefix.split(",");
                    for (String t : tagsArr) {
                        String pure = removeUsagePrefix(t);
                        if (!pure.isEmpty()) {
                            tagContent = pure;
                            break;
                        }
                    }
                }
                map.put(product, tagContent);
            }
        }
        cursor.close();
        return map;
    }

    // ==================== 调水动保预设表操作 ====================
    public void saveWaterPreset(String batchId, int row, String name, String tags) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_WATER_ROW, row);
        values.put(COLUMN_BATCH_ID, batchId);
        values.put(COLUMN_WATER_NAME, EncryptUtils.encrypt(name));
        values.put(COLUMN_WATER_TAGS, EncryptUtils.encrypt(tags));
        db.insertWithOnConflict(TABLE_WATER_PRESETS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String getWaterPresetByRow(String batchId, int row) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_WATER_PRESETS, new String[]{COLUMN_WATER_NAME},
                COLUMN_WATER_ROW + "=? AND " + COLUMN_BATCH_ID + "=?",
                new String[]{String.valueOf(row), batchId}, null, null, null);
        String name = "";
        if (cursor.moveToFirst()) {
            name = EncryptUtils.decrypt(cursor.getString(0));
        }
        cursor.close();
        return name;
    }

    public String getWaterPresetTags(String batchId, int row) {
        String tags = "";
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_WATER_PRESETS, new String[]{COLUMN_WATER_TAGS},
                COLUMN_WATER_ROW + "=? AND " + COLUMN_BATCH_ID + "=?",
                new String[]{String.valueOf(row), batchId}, null, null, null);
        if (cursor.moveToFirst()) {
            tags = EncryptUtils.decrypt(cursor.getString(0));
        }
        cursor.close();
        return tags != null ? tags : "";
    }

    public List<String> getAllWaterPresetNames(String batchId) {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_WATER_PRESETS, new String[]{COLUMN_WATER_NAME},
                COLUMN_BATCH_ID + "=?", new String[]{batchId},
                null, null, COLUMN_WATER_ROW + " ASC");
        while (cursor.moveToNext()) {
            String fullName = EncryptUtils.decrypt(cursor.getString(0));
            if (fullName != null && !fullName.isEmpty()) {
                String pure = removeUsagePrefix(fullName);
                list.add(pure);
            }
        }
        cursor.close();
        list.add("");
        return list;
    }

    public Map<String, String> getWaterPresetTagsMap(String batchId) {
        Map<String, String> map = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_WATER_PRESETS, new String[]{COLUMN_WATER_NAME, COLUMN_WATER_TAGS},
                COLUMN_BATCH_ID + "=?", new String[]{batchId},
                null, null, COLUMN_WATER_ROW + " ASC");
        while (cursor.moveToNext()) {
            String fullName = EncryptUtils.decrypt(cursor.getString(0));
            String tagsWithPrefix = EncryptUtils.decrypt(cursor.getString(1));
            if (fullName != null && !fullName.isEmpty()) {
                String product = removeUsagePrefix(fullName);
                String tagContent = "";
                if (tagsWithPrefix != null && !tagsWithPrefix.isEmpty()) {
                    String[] tagsArr = tagsWithPrefix.split(",");
                    for (String t : tagsArr) {
                        String pure = removeUsagePrefix(t);
                        if (!pure.isEmpty()) {
                            tagContent = pure;
                            break;
                        }
                    }
                }
                map.put(product, tagContent);
            }
        }
        cursor.close();
        return map;
    }

    // ==================== 查料记录相关方法（完整实现） ====================
    public void deleteFeedRecordsByBatchId(String batchId) {
        if (batchId == null || batchId.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_DAILY_RECORDS, COLUMN_BATCH_ID + " = ?", new String[]{batchId});
        db.delete(TABLE_FEEDING_STATS, COLUMN_BATCH_ID + " = ?", new String[]{batchId});
    }

    public void deleteCheckRecordsByBatchId(String batchId) {
        if (batchId == null || batchId.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_FEEDING_CHECK_RECORDS, "batch_id = ?", new String[]{batchId});
    }

    public void deleteWaterRecordsByBatchId(String batchId) {
        if (batchId == null || batchId.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_WATER_QUALITY, COLUMN_BATCH_ID + " = ?", new String[]{batchId});
    }

    public void deleteAllDataByBatchId(String batchId) {
        deleteFeedRecordsByBatchId(batchId);
        deleteCheckRecordsByBatchId(batchId);
        deleteWaterRecordsByBatchId(batchId);
    }

    public void deleteCheckRecordsByDate(String batchId, String date) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_FEEDING_CHECK_RECORDS,
                "batch_id = ? AND record_date = ?",
                new String[]{batchId, date});
    }

    public void insertCheckRecords(List<ContentValues> valuesList) {
        if (valuesList == null || valuesList.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (ContentValues values : valuesList) {
                ContentValues encrypted = new ContentValues();
                encrypted.put("batch_id", values.getAsString("batch_id"));
                encrypted.put("record_date", values.getAsString("record_date"));
                encrypted.put("shed_row_index", values.getAsInteger("shed_row_index"));
                encrypted.put("is_excluded", values.getAsInteger("is_excluded"));
                encrypted.put("water_percentage", values.getAsInteger("water_percentage"));
                encrypted.put("created_at", values.getAsLong("created_at"));

                encrypted.put("start_time", EncryptUtils.encrypt(values.getAsString("start_time")));
                encrypted.put("end_time", EncryptUtils.encrypt(values.getAsString("end_time")));
                encrypted.put("shed_number", EncryptUtils.encrypt(values.getAsString("shed_number")));
                encrypted.put("check_time", EncryptUtils.encrypt(values.getAsString("check_time")));
                encrypted.put("duration_seconds", EncryptUtils.encrypt(String.valueOf(values.getAsLong("duration_seconds"))));

                db.insert(TABLE_FEEDING_CHECK_RECORDS, null, encrypted);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<CheckRecord> getCheckRecordsByDate(String batchId, String date) {
        List<CheckRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_FEEDING_CHECK_RECORDS, null,
                "batch_id = ? AND record_date = ?",
                new String[]{batchId, date}, null, null, "shed_row_index ASC");
        while (cursor.moveToNext()) {
            CheckRecord record = new CheckRecord();
            record.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
            record.batchId = cursor.getString(cursor.getColumnIndexOrThrow("batch_id"));
            record.recordDate = cursor.getString(cursor.getColumnIndexOrThrow("record_date"));
            record.shedRowIndex = cursor.getInt(cursor.getColumnIndexOrThrow("shed_row_index"));
            record.excluded = cursor.getInt(cursor.getColumnIndexOrThrow("is_excluded")) == 1;
            record.waterPercentage = cursor.getInt(cursor.getColumnIndexOrThrow("water_percentage"));
            record.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));

            record.startTime = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("start_time")));
            record.endTime = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("end_time")));
            record.shedNumber = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("shed_number")));
            record.checkTime = EncryptUtils.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("check_time")));

            String encryptedDuration = cursor.getString(cursor.getColumnIndexOrThrow("duration_seconds"));
            if (encryptedDuration != null && !encryptedDuration.isEmpty()) {
                try {
                    record.durationSeconds = Long.parseLong(EncryptUtils.decrypt(encryptedDuration));
                } catch (NumberFormatException e) {
                    record.durationSeconds = 0;
                }
            } else {
                record.durationSeconds = 0;
            }
            list.add(record);
        }
        cursor.close();
        return list;
    }

    public CheckSummary getCheckRecordsSummary(String batchId, String startDate, String endDate) {
        CheckSummary summary = new CheckSummary();
        SQLiteDatabase db = getReadableDatabase();
        String countSql = "SELECT record_date, COUNT(*) as shed_count " +
                "FROM " + TABLE_FEEDING_CHECK_RECORDS + " " +
                "WHERE batch_id = ? AND record_date BETWEEN ? AND ? AND is_excluded = 0 " +
                "GROUP BY record_date ORDER BY record_date ASC";
        Cursor countCursor = db.rawQuery(countSql, new String[]{batchId, startDate, endDate});
        List<CheckRecord> allRecords = new ArrayList<>();
        Cursor detailCursor = db.query(TABLE_FEEDING_CHECK_RECORDS, null,
                "batch_id = ? AND record_date BETWEEN ? AND ? AND is_excluded = 0",
                new String[]{batchId, startDate, endDate}, null, null, "record_date ASC, shed_row_index ASC");
        while (detailCursor.moveToNext()) {
            CheckRecord record = new CheckRecord();
            record.recordDate = detailCursor.getString(detailCursor.getColumnIndexOrThrow("record_date"));
            String encDuration = detailCursor.getString(detailCursor.getColumnIndexOrThrow("duration_seconds"));
            if (encDuration != null && !encDuration.isEmpty()) {
                try {
                    record.durationSeconds = Long.parseLong(EncryptUtils.decrypt(encDuration));
                } catch (NumberFormatException e) {
                    record.durationSeconds = 0;
                }
            }
            allRecords.add(record);
        }
        detailCursor.close();

        Map<String, List<Long>> dateDurationMap = new HashMap<>();
        for (CheckRecord rec : allRecords) {
            if (!dateDurationMap.containsKey(rec.recordDate)) {
                dateDurationMap.put(rec.recordDate, new ArrayList<Long>());
            }
            dateDurationMap.get(rec.recordDate).add(rec.durationSeconds);
        }

        summary.dailySummaries = new ArrayList<>();
        if (countCursor.moveToFirst()) {
            do {
                String date = countCursor.getString(0);
                int shedCount = countCursor.getInt(1);
                DailyCheckSummary daily = new DailyCheckSummary();
                daily.date = date;
                daily.shedCount = shedCount;
                List<Long> durations = dateDurationMap.get(date);
                if (durations != null && !durations.isEmpty()) {
                    long sum = 0, min = Long.MAX_VALUE, max = Long.MIN_VALUE;
                    for (long d : durations) {
                        sum += d;
                        if (d < min) min = d;
                        if (d > max) max = d;
                    }
                    daily.avgDuration = sum / durations.size();
                    daily.minDuration = min;
                    daily.maxDuration = max;
                }
                summary.dailySummaries.add(daily);
            } while (countCursor.moveToNext());
        }
        countCursor.close();
        return summary;
    }

    public List<DailyFeedSummary> getDailyFeedSummary(String batchId, String startDate, String endDate) {
        List<DailyFeedSummary> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_DAILY_RECORDS,
                new String[]{COLUMN_DATE, COLUMN_BREAKFAST, COLUMN_LUNCH, COLUMN_DINNER, COLUMN_NIGHT_SNACK},
                COLUMN_BATCH_ID + "=? AND " + COLUMN_DATE + " BETWEEN ? AND ?",
                new String[]{batchId, startDate, endDate},
                null, null, COLUMN_DATE + " ASC");
        while (cursor.moveToNext()) {
            String date = cursor.getString(0);
            float total = 0;
            for (int i = 1; i <= 4; i++) {
                String encVal = cursor.getString(i);
                if (encVal != null && !encVal.isEmpty()) {
                    String val = EncryptUtils.decrypt(encVal);
                    if (val != null && !val.isEmpty()) {
                        total += Float.parseFloat(val);
                    }
                }
            }
            list.add(new DailyFeedSummary(date, total));
        }
        cursor.close();
        return list;
    }

    public List<DurationSummary> getFeedingDurationSummary(String batchId, String startDate, String endDate) {
        List<DurationSummary> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_FEEDING_STATS,
                new String[]{COLUMN_STATS_DATE, COLUMN_AVG_DURATION},
                COLUMN_BATCH_ID + "=? AND " + COLUMN_STATS_DATE + " BETWEEN ? AND ?",
                new String[]{batchId, startDate, endDate},
                null, null, COLUMN_STATS_DATE + " ASC");
        while (cursor.moveToNext()) {
            String date = cursor.getString(0);
            long duration = cursor.getLong(1);
            list.add(new DurationSummary(date, duration));
        }
        cursor.close();
        return list;
    }

    public List<DurationByShedSummary> getFeedingDurationByShed(String batchId, String shedNumber, String startDate, String endDate) {
        List<DurationByShedSummary> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String selection = COLUMN_BATCH_ID + "=? AND record_date BETWEEN ? AND ?";
        String[] selectionArgs = new String[]{batchId, startDate, endDate};
        if (shedNumber != null && !shedNumber.isEmpty() && !shedNumber.equals("全部")) {
            selection = COLUMN_BATCH_ID + "=? AND shed_number=? AND record_date BETWEEN ? AND ?";
            selectionArgs = new String[]{batchId, EncryptUtils.encrypt(shedNumber), startDate, endDate};
        }
        Cursor cursor = db.query(TABLE_FEEDING_CHECK_RECORDS,
                new String[]{"record_date", "duration_seconds", "is_excluded"},
                selection, selectionArgs,
                null, null, "record_date ASC");
        Map<String, List<Long>> dateDurationMap = new HashMap<>();
        while (cursor.moveToNext()) {
            try {
                String date = cursor.getString(cursor.getColumnIndexOrThrow("record_date"));
                String encDuration = cursor.getString(cursor.getColumnIndexOrThrow("duration_seconds"));
                int isExcluded = cursor.getInt(cursor.getColumnIndexOrThrow("is_excluded"));
                if (isExcluded == 0 && encDuration != null && !encDuration.isEmpty()) {
                    long duration = Long.parseLong(EncryptUtils.decrypt(encDuration));
                    if (!dateDurationMap.containsKey(date)) {
                        dateDurationMap.put(date, new ArrayList<Long>());
                    }
                    dateDurationMap.get(date).add(duration);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        cursor.close();
        for (Map.Entry<String, List<Long>> entry : dateDurationMap.entrySet()) {
            List<Long> durations = entry.getValue();
            long total = 0;
            for (Long d : durations) total += d;
            long avgMillis = total / durations.size();
            list.add(new DurationByShedSummary(entry.getKey(), avgMillis));
        }
        return list;
    }

    public List<String> getShedNumbers(String batchId) {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(true, TABLE_FEEDING_CHECK_RECORDS,
                new String[]{"shed_number"},
                COLUMN_BATCH_ID + "=?",
                new String[]{batchId},
                null, null, null, null);
        while (cursor.moveToNext()) {
            String encShedNumber = cursor.getString(0);
            if (encShedNumber != null && !encShedNumber.isEmpty()) {
                try {
                    String decrypted = EncryptUtils.decrypt(encShedNumber);
                    if (!list.contains(decrypted)) list.add(decrypted);
                } catch (Exception e) {
                    if (!list.contains(encShedNumber)) list.add(encShedNumber);
                }
            }
        }
        cursor.close();
        Collections.sort(list, (a, b) -> {
            try { return Integer.parseInt(a) - Integer.parseInt(b); }
            catch (NumberFormatException e) { return a.compareTo(b); }
        });
        return list;
    }

    public List<ShedDurationSummary> getFeedingDurationByShedGrouped(String batchId, String startDate, String endDate) {
        List<ShedDurationSummary> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_FEEDING_CHECK_RECORDS,
                new String[]{"shed_number", "duration_seconds", "is_excluded"},
                COLUMN_BATCH_ID + "=? AND record_date BETWEEN ? AND ?",
                new String[]{batchId, startDate, endDate},
                null, null, "shed_number ASC");
        Map<String, List<Long>> shedDurationMap = new HashMap<>();
        while (cursor.moveToNext()) {
            try {
                String encShedNumber = cursor.getString(cursor.getColumnIndexOrThrow("shed_number"));
                String encDuration = cursor.getString(cursor.getColumnIndexOrThrow("duration_seconds"));
                int isExcluded = cursor.getInt(cursor.getColumnIndexOrThrow("is_excluded"));
                if (isExcluded == 0 && encDuration != null && !encDuration.isEmpty()) {
                    long duration = Long.parseLong(EncryptUtils.decrypt(encDuration));
                    String shedNumber = EncryptUtils.decrypt(encShedNumber);
                    if (!shedDurationMap.containsKey(shedNumber)) {
                        shedDurationMap.put(shedNumber, new ArrayList<Long>());
                    }
                    shedDurationMap.get(shedNumber).add(duration);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        cursor.close();
        for (Map.Entry<String, List<Long>> entry : shedDurationMap.entrySet()) {
            List<Long> durations = entry.getValue();
            if (durations.isEmpty()) continue;
            long total = 0;
            for (Long d : durations) total += d;
            long avg = total / durations.size();
            list.add(new ShedDurationSummary(entry.getKey(), avg));
        }
        Collections.sort(list, (a, b) -> {
            try { return Integer.parseInt(a.shedNumber) - Integer.parseInt(b.shedNumber); }
            catch (NumberFormatException e) { return a.shedNumber.compareTo(b.shedNumber); }
        });
        return list;
    }

    public long getTodayLastAverageDuration(String batchId) {
        String today = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(new Date());
        String sql = "SELECT " + COLUMN_AVG_DURATION + " FROM " + TABLE_FEEDING_STATS +
                " WHERE " + COLUMN_BATCH_ID + " = ? AND " + COLUMN_STATS_DATE + " = ? " +
                " ORDER BY " + COLUMN_RECORD_TIME + " DESC LIMIT 1";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{batchId, today});
        long result = 0;
        if (c.moveToFirst()) {
            result = c.getLong(0);
        }
        c.close();
        return result;
    }

    public List<DurationSummary> getDailyAverageDurations(String batchId, String startDate, String endDate) {
        List<DurationSummary> list = new ArrayList<>();
        String sql = "SELECT " + COLUMN_STATS_DATE + ", AVG(" + COLUMN_AVG_DURATION + ") " +
                " FROM " + TABLE_FEEDING_STATS +
                " WHERE " + COLUMN_BATCH_ID + " = ? AND " + COLUMN_STATS_DATE + " BETWEEN ? AND ? " +
                " GROUP BY " + COLUMN_STATS_DATE + " ORDER BY " + COLUMN_STATS_DATE;
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{batchId, startDate, endDate});
        while (c.moveToNext()) {
            list.add(new DurationSummary(c.getString(0), (long) c.getDouble(1)));
        }
        c.close();
        return list;
    }

    // ==================== 行情相关 ====================
    public List<PriceCategory> getMarketPricesByDate(String date) {
        HashMap<String, PriceCategory> map = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT category, item_name, price FROM " + TABLE_MARKET_PRICES +
                        " WHERE date = ? ORDER BY category, item_name",
                new String[]{date});
        while (cursor.moveToNext()) {
            String catTitle = cursor.getString(0);
            String name = cursor.getString(1);
            String price = cursor.getString(2);
            PriceCategory cat = map.get(catTitle);
            if (cat == null) {
                cat = new PriceCategory();
                cat.title = catTitle;
                cat.items = new ArrayList<>();
                map.put(catTitle, cat);
            }
            cat.items.add(new PriceItem(name, price));
        }
        cursor.close();
        return new ArrayList<>(map.values());
    }

    public void saveMarketPrices(PriceData data) {
        if (data == null || data.date == null || data.categories == null) return;
        String dateStr = normalizeDate(data.date);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_MARKET_PRICES, "date = ?", new String[]{dateStr});
            for (PriceCategory cat : data.categories) {
                if (cat.items == null) continue;
                for (PriceItem item : cat.items) {
                    ContentValues cv = new ContentValues();
                    cv.put("date", dateStr);
                    cv.put("category", cat.title);
                    cv.put("item_name", item.name);
                    cv.put("price", item.price);
                    cv.put("saved_at", System.currentTimeMillis());
                    db.insert(TABLE_MARKET_PRICES, null, cv);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public PriceData getLatestMarketPrices() {
        HashMap<String, PriceCategory> map = new HashMap<>();
        String latestDate = null;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT DISTINCT date FROM " + TABLE_MARKET_PRICES +
                        " ORDER BY date DESC LIMIT 1", null);
        if (cursor.moveToNext()) {
            latestDate = cursor.getString(0);
        }
        cursor.close();
        if (latestDate == null) return null;
        cursor = db.rawQuery(
                "SELECT category, item_name, price FROM " + TABLE_MARKET_PRICES +
                        " WHERE date = ? ORDER BY category, item_name",
                new String[]{latestDate});
        while (cursor.moveToNext()) {
            String catTitle = cursor.getString(0);
            String name = cursor.getString(1);
            String price = cursor.getString(2);
            PriceCategory cat = map.get(catTitle);
            if (cat == null) {
                cat = new PriceCategory();
                cat.title = catTitle;
                cat.items = new ArrayList<>();
                map.put(catTitle, cat);
            }
            cat.items.add(new PriceItem(name, price));
        }
        cursor.close();
        return new PriceData(latestDate, new ArrayList<>(map.values()));
    }

    public List<PricePoint> getPriceHistory(String itemName) {
        List<PricePoint> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_MARKET_PRICES,
                new String[]{"date", "price"}, "item_name = ?",
                new String[]{itemName}, null, null, "date ASC");
        while (cursor.moveToNext()) {
            String date = cursor.getString(0);
            String price = cursor.getString(1);
            float mid = parseMidPrice(price);
            PricePoint point = new PricePoint(date, mid);
            list.add(point);
        }
        cursor.close();
        return list;
    }

    private String normalizeDate(String date) {
        if (date == null) return "";
        return date.replace("/", "-");
    }

    private float parseMidPrice(String price) {
        if (price == null) return 0;
        try { return Float.parseFloat(price); } catch (NumberFormatException ignored) {}
        return 0;
    }

    // ==================== 放苗天数工具 ====================
    public int getStockingDay(String batchId) {
        String dateStr = getBasicData(batchId, "stocking_date");
        if (dateStr == null || dateStr.isEmpty() || "选择日期".equals(dateStr)) return 0;
        String[] formats = {"yyyy/MM/dd", "yyyy-MM-dd", "yyyy.M.d"};
        for (String fmt : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.CHINA);
                java.util.Date parsed = sdf.parse(dateStr);
                if (parsed == null) continue;
                Calendar cal = Calendar.getInstance();
                cal.setTime(parsed);
                Calendar now = Calendar.getInstance();
                long diff = now.getTimeInMillis() - cal.getTimeInMillis();
                int days = (int) (diff / (1000 * 60 * 60 * 24)) + 1;
                return Math.max(days, 1);
            } catch (Exception ignored) {}
        }
        return 0;
    }

    public double getAccumulatedFeed(String batchId, int fromDay, int toDay) {
        String dateStr = getBasicData(batchId, "stocking_date");
        if (dateStr == null || dateStr.isEmpty() || "选择日期".equals(dateStr)) return 0;
        double total = 0;
        try {
            String[] formats = {"yyyy/MM/dd", "yyyy-MM-dd", "yyyy.M.d"};
            java.util.Date stockingDate = null;
            for (String fmt : formats) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.CHINA);
                    stockingDate = sdf.parse(dateStr);
                    if (stockingDate != null) break;
                } catch (Exception ignored) {}
            }
            if (stockingDate == null) return 0;

            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(stockingDate.getTime());
            cal.add(Calendar.DAY_OF_YEAR, fromDay - 1);
            String startDate = new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).format(cal.getTime());
            cal.add(Calendar.DAY_OF_YEAR, toDay - fromDay);
            String endDate = new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).format(cal.getTime());

            Cursor c = getReadableDatabase().rawQuery(
                    "SELECT " + COLUMN_BREAKFAST + "," + COLUMN_LUNCH + "," + COLUMN_DINNER + "," + COLUMN_NIGHT_SNACK +
                    " FROM " + TABLE_DAILY_RECORDS +
                    " WHERE " + COLUMN_BATCH_ID + "=? AND " + COLUMN_DATE + ">=? AND " + COLUMN_DATE + "<=?",
                    new String[]{batchId, startDate, endDate});
            while (c.moveToNext()) {
                for (int i = 0; i < 4; i++) {
                    String enc = c.getString(i);
                    if (enc != null && !enc.isEmpty()) {
                        try { total += Double.parseDouble(EncryptUtils.decrypt(enc)); } catch (Exception ignored) {}
                    }
                }
            }
            c.close();
        } catch (Exception ignored) {}
        return total;
    }

    // ==================== 计划任务操作 ====================
    public long addMainTask(String batchId, String taskName) {
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_PARENT_ID, -1);
        cv.put(COLUMN_BATCH_ID, batchId);
        cv.put(COLUMN_TASK_NAME, taskName);
        cv.put(COLUMN_CREATED_AT, System.currentTimeMillis());
        return getWritableDatabase().insert(TABLE_PLAN_TASKS, null, cv);
    }

    public long addSubTask(long parentId, String batchId, String taskName, int startValue, int endValue,
                           double intervalValue, int unitType, int frequency) {
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_PARENT_ID, parentId);
        cv.put(COLUMN_BATCH_ID, batchId);
        cv.put(COLUMN_TASK_NAME, taskName);
        cv.put(COLUMN_START_VALUE, startValue);
        cv.put(COLUMN_END_VALUE, endValue);
        cv.put(COLUMN_INTERVAL_VALUE, intervalValue);
        cv.put(COLUMN_UNIT_TYPE, unitType);
        cv.put(COLUMN_FREQUENCY, frequency);
        cv.put(COLUMN_CREATED_AT, System.currentTimeMillis());
        return getWritableDatabase().insert(TABLE_PLAN_TASKS, null, cv);
    }

    public Cursor getAllMainTasks(String batchId) {
        return getReadableDatabase().query(TABLE_PLAN_TASKS, null,
                COLUMN_PARENT_ID + "=-1 AND " + COLUMN_BATCH_ID + "=? AND " + COLUMN_IS_ACTIVE + "=1",
                new String[]{batchId}, null, null, COLUMN_CREATED_AT + " ASC");
    }

    public Cursor getSubTasks(long parentId) {
        return getReadableDatabase().query(TABLE_PLAN_TASKS, null,
                COLUMN_PARENT_ID + "=? AND " + COLUMN_IS_ACTIVE + "=1",
                new String[]{String.valueOf(parentId)}, null, null, COLUMN_CREATED_AT + " ASC");
    }

    public void updateTaskName(long taskId, String newName) {
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_TASK_NAME, newName);
        getWritableDatabase().update(TABLE_PLAN_TASKS, cv, COLUMN_TASK_ID + "=?", new String[]{String.valueOf(taskId)});
    }

    public void updateSubTask(long taskId, int startValue, int endValue, double intervalValue,
                              int unitType, int frequency) {
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_START_VALUE, startValue);
        cv.put(COLUMN_END_VALUE, endValue);
        cv.put(COLUMN_INTERVAL_VALUE, intervalValue);
        cv.put(COLUMN_UNIT_TYPE, unitType);
        cv.put(COLUMN_FREQUENCY, frequency);
        getWritableDatabase().update(TABLE_PLAN_TASKS, cv, COLUMN_TASK_ID + "=?", new String[]{String.valueOf(taskId)});
    }

    public void deleteTask(long taskId) {
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_IS_ACTIVE, 0);
        SQLiteDatabase db = getWritableDatabase();
        db.update(TABLE_PLAN_TASKS, cv, COLUMN_TASK_ID + "=?", new String[]{String.valueOf(taskId)});
        db.update(TABLE_PLAN_TASKS, cv, COLUMN_PARENT_ID + "=?", new String[]{String.valueOf(taskId)});
    }

    public void completeTask(long taskId, String batchId) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.query(TABLE_PLAN_TASKS, null, COLUMN_TASK_ID + "=?", new String[]{String.valueOf(taskId)}, null, null, null);
        if (c.moveToFirst()) {
            int startValue = c.getInt(c.getColumnIndexOrThrow(COLUMN_START_VALUE));
            int unitType = c.getInt(c.getColumnIndexOrThrow(COLUMN_UNIT_TYPE));
            int stockingDay = getStockingDay(batchId);
            c.close();

            ContentValues cv = new ContentValues();
            if (unitType == 0) {
                cv.put(COLUMN_LAST_TRIGGER_DAY, stockingDay);
            } else {
                double feed = getAccumulatedFeed(batchId, startValue, stockingDay);
                cv.put(COLUMN_LAST_TRIGGER_FEED, feed);
            }
            cv.put(COLUMN_TASK_STATUS, 1);
            db.update(TABLE_PLAN_TASKS, cv, COLUMN_TASK_ID + "=?", new String[]{String.valueOf(taskId)});
        } else {
            c.close();
        }
    }

    public Cursor getAllSubTasks(String batchId) {
        return getReadableDatabase().query(TABLE_PLAN_TASKS, null,
                COLUMN_BATCH_ID + "=? AND " + COLUMN_IS_ACTIVE + "=1 AND " + COLUMN_PARENT_ID + "!=-1",
                new String[]{batchId}, null, null, COLUMN_CREATED_AT + " ASC");
    }

    public String getMainTaskName(long subTaskId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_PLAN_TASKS, new String[]{COLUMN_PARENT_ID},
                COLUMN_TASK_ID + "=?", new String[]{String.valueOf(subTaskId)}, null, null, null);
        long parentId = -1;
        if (c.moveToFirst()) parentId = c.getLong(0);
        c.close();
        if (parentId == -1) return "";
        c = db.query(TABLE_PLAN_TASKS, new String[]{COLUMN_TASK_NAME},
                COLUMN_TASK_ID + "=?", new String[]{String.valueOf(parentId)}, null, null, null);
        String name = "";
        if (c.moveToFirst()) name = c.getString(0);
        c.close();
        return name;
    }

    public int getActiveTaskCount(String batchId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_PLAN_TASKS +
                        " WHERE " + COLUMN_BATCH_ID + "=? AND " + COLUMN_IS_ACTIVE + "=1 AND " + COLUMN_PARENT_ID + "=-1",
                new String[]{batchId});
        int count = 0;
        if (c.moveToFirst()) count = c.getInt(0);
        c.close();
        return count;
    }
}
