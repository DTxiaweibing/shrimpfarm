package com.shrimpfarm.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.shrimpfarm.app.model.PriceCategory;
import com.shrimpfarm.app.model.PriceData;
import com.shrimpfarm.app.model.PriceItem;
import com.shrimpfarm.app.model.PricePoint;
import com.shrimpfarm.app.utils.EncryptUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "FeedingRecord.db";
    private static final int DATABASE_VERSION = 15;

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

    // ==================== 财务模块表名 ====================
    public static final String TABLE_FIN_CATEGORY = "fin_category";
    public static final String TABLE_FIN_PRODUCT = "fin_product";
    public static final String TABLE_FIN_PRODUCT_CATEGORY = "fin_product_category";
    public static final String TABLE_FIN_SUPPLIER = "fin_supplier";
    public static final String TABLE_FIN_SUPPLIER_PRODUCT = "fin_supplier_product";
    public static final String TABLE_FIN_EXPENSE = "fin_expense";
    public static final String TABLE_FIN_TICKET = "fin_ticket";
    public static final String TABLE_FIN_TICKET_ITEM = "fin_ticket_item";
    public static final String TABLE_FIN_INCOME = "fin_income";
    public static final String TABLE_FIN_SEED = "fin_seed";
    public static final String TABLE_FIN_WAGE = "fin_wage";
    public static final String TABLE_FIN_REST = "fin_rest";
    public static final String TABLE_FIN_LEAVE = "fin_leave";
    public static final String TABLE_FIN_SALARY = "fin_salary";
    public static final String TABLE_FIN_PAYMENT_ACCOUNT = "fin_payment_account";
    public static final String TABLE_FIN_PAYMENT = "fin_payment";
    public static final String TABLE_FIN_PAYMENT_TICKET = "fin_payment_ticket";
    public static final String TABLE_FIN_ADVANCE = "fin_advance";
    public static final String TABLE_FIN_CONTACT_ACCOUNT = "fin_contact_account";
    public static final String TABLE_FIN_PURCHASE_ORDER = "fin_purchase_order";
    public static final String TABLE_FIN_PURCHASE_ITEM = "fin_purchase_item";

    // ==================== 财务模块公共字段 ====================
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_FIN_NAME = "name";
    public static final String COLUMN_FIN_ENABLED = "enabled";
    public static final String COLUMN_FIN_SORT_ORDER = "sort_order";
    public static final String COLUMN_FIN_IS_CUSTOM = "is_custom";
    public static final String COLUMN_FIN_UNIT = "unit";
    public static final String COLUMN_FIN_CREATE_TIME = "create_time";
    public static final String COLUMN_FIN_PRODUCT_ID = "product_id";
    public static final String COLUMN_FIN_CATEGORY_ID = "category_id";
    public static final String COLUMN_FIN_SUPPLIER_ID = "supplier_id";
    public static final String COLUMN_FIN_PRODUCT_NAME = "product_name";
    public static final String COLUMN_FIN_SPEC = "spec";
    public static final String COLUMN_FIN_CONTACT = "contact";
    public static final String COLUMN_FIN_PHONE = "phone";
    public static final String COLUMN_FIN_PERSON_TYPE = "person_type";
    public static final String COLUMN_FIN_SUPPLIER_NO = "supplier_no";
    public static final String COLUMN_FIN_AMOUNT = "amount";
    public static final String COLUMN_FIN_DATE = "date";
    public static final String COLUMN_FIN_TOTAL_AMOUNT = "total_amount";
    public static final String COLUMN_FIN_TICKET_ID = "ticket_id";
    public static final String COLUMN_FIN_ORDER_ID = "order_id";
    public static final String COLUMN_FIN_ORDER_NO = "order_no";
    public static final String COLUMN_FIN_HISTORY_PRICE = "history_price";
    public static final String COLUMN_FIN_SUPPLIER_PRODUCT_ID = "supplier_product_id";
    public static final String COLUMN_FIN_QUANTITY = "quantity";
    public static final String COLUMN_FIN_UNIT_PRICE = "unit_price";
    public static final String COLUMN_FIN_CATEGORY = "category";
    public static final String COLUMN_FIN_SUB_CATEGORY = "sub_category";
    public static final String COLUMN_FIN_CAR_NO = "car_no";
    public static final String COLUMN_FIN_SHED_NO = "shed_no";
    public static final String COLUMN_FIN_SPECS = "specs";
    public static final String COLUMN_FIN_WEIGHT = "weight";
    public static final String COLUMN_FIN_PRICE = "price";
    public static final String COLUMN_FIN_NET_FEE = "net_fee";
    public static final String COLUMN_FIN_NET_FEE_PAID = "net_fee_paid";
    public static final String COLUMN_FIN_NET_FEE_PAY_DATE = "net_fee_pay_date";
    public static final String COLUMN_FIN_NET_FEE_ACCOUNT_ID = "net_fee_account_id";
    public static final String COLUMN_FIN_ACTUAL_AMOUNT = "actual_amount";
    public static final String COLUMN_FIN_AGENT = "agent";
    public static final String COLUMN_FIN_CUSTOMER = "customer";
    public static final String COLUMN_FIN_PAYEE = "payee";
    public static final String COLUMN_FIN_START_DATE = "start_date";
    public static final String COLUMN_FIN_DAILY_WAGE = "daily_wage";
    public static final String COLUMN_FIN_REST_DATE = "rest_date";
    public static final String COLUMN_FIN_SETTLE_TYPE = "settle_type";
    public static final String COLUMN_FIN_SETTLED = "settled";
    public static final String COLUMN_FIN_ACCOUNT_ID = "account_id";
    public static final String COLUMN_FIN_ACCOUNT_NAME = "account_name";
    public static final String COLUMN_FIN_ACCOUNT_TYPE = "account_type";
    public static final String COLUMN_FIN_ACCOUNT_NO = "account_no";
    public static final String COLUMN_FIN_RECEIVE_ACCOUNT_ID = "receive_account_id";
    public static final String COLUMN_FIN_PAYMENT_ID = "payment_id";
    public static final String COLUMN_FIN_DUE_AMOUNT = "due_amount";
    public static final String COLUMN_FIN_PAID_AMOUNT = "paid_amount";
    public static final String COLUMN_FIN_DIFF_AMOUNT = "diff_amount";
    public static final String COLUMN_FIN_DIFF_REASON = "diff_reason";

    // ==================== 请假/工资单字段 ====================
    public static final String COLUMN_FIN_AM_TYPE = "am_type";
    public static final String COLUMN_FIN_PM_TYPE = "pm_type";
    public static final String COLUMN_FIN_YEAR = "year";
    public static final String COLUMN_FIN_MONTH = "month";
    public static final String COLUMN_FIN_WORK_DAYS = "work_days";
    public static final String COLUMN_FIN_PERSONAL_DAYS = "personal_days";
    public static final String COLUMN_FIN_SICK_DAYS = "sick_days";
    public static final String COLUMN_FIN_ABSENT_DAYS = "absent_days";
    public static final String COLUMN_FIN_BASE_SALARY = "base_salary";
    public static final String COLUMN_FIN_BONUS = "bonus";
    public static final String COLUMN_FIN_DEDUCTION = "deduction";
    public static final String COLUMN_FIN_ACTUAL_SALARY = "actual_salary";
    public static final String COLUMN_FIN_PAID = "paid";
    public static final String COLUMN_FIN_PAY_DATE = "pay_date";
    public static final String COLUMN_FIN_PAY_DAY = "pay_day";
    public static final String COLUMN_FIN_SETTLED_AMOUNT = "settled_amount";

    // ==================== 进货单字段 ====================
    public static final String COLUMN_FIN_PURCHASE_PAID = "purchase_paid";
    public static final String COLUMN_FIN_PURCHASE_ACCOUNT_ID = "purchase_account_id";

    // ==================== 供应商商品字段（按品类建品） ====================
    public static final String COLUMN_FIN_SP_CATEGORY_ID = "sp_category_id";
    public static final String COLUMN_FIN_SP_UNIT = "sp_unit";
    public static final String COLUMN_FIN_SP_PRICE = "sp_price";
    public static final String COLUMN_FIN_SP_PACK_UNIT = "sp_pack_unit";
    public static final String COLUMN_FIN_SP_PACK_QTY = "sp_pack_qty";

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
    public static final String COLUMN_BD_KEY = "`key`";
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

    // ==================== 水质检测导出数据类 ====================
    public static class WaterQualityRecord {
        public String date;
        public String vibrio;
        public String salinity;
        public String ammonia;
        public String nitrite;
        public String ph;
        public String dissolvedOxygen;
        public String maxTemp;
        public String minTemp;
        public String chlorine;
        public String hydrogenSulfide;
        public String orp;
    }

    // ==================== 内部类 ====================
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

    // ==================== 财务模块建表SQL ====================
    private static final String CREATE_FIN_CATEGORY_TABLE =
            "CREATE TABLE " + TABLE_FIN_CATEGORY + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_FIN_NAME + " TEXT NOT NULL, " +
                    COLUMN_FIN_IS_CUSTOM + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_ENABLED + " INTEGER DEFAULT 1, " +
                    COLUMN_FIN_SORT_ORDER + " INTEGER DEFAULT 0)";

    private static final String CREATE_FIN_PRODUCT_TABLE =
            "CREATE TABLE " + TABLE_FIN_PRODUCT + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_FIN_NAME + " TEXT NOT NULL, " +
                    COLUMN_FIN_UNIT + " TEXT, " +
                    COLUMN_FIN_ENABLED + " INTEGER DEFAULT 1, " +
                    COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)";

    private static final String CREATE_FIN_PRODUCT_CATEGORY_TABLE =
            "CREATE TABLE " + TABLE_FIN_PRODUCT_CATEGORY + " (" +
                    COLUMN_FIN_PRODUCT_ID + " INTEGER NOT NULL, " +
                    COLUMN_FIN_CATEGORY_ID + " INTEGER NOT NULL, " +
                    "PRIMARY KEY (" + COLUMN_FIN_PRODUCT_ID + ", " + COLUMN_FIN_CATEGORY_ID + "))";

    private static final String CREATE_FIN_SUPPLIER_TABLE =
            "CREATE TABLE " + TABLE_FIN_SUPPLIER + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_FIN_NAME + " TEXT NOT NULL, " +
                    COLUMN_FIN_PERSON_TYPE + " INTEGER DEFAULT 1, " +
                    COLUMN_FIN_CONTACT + " TEXT, " +
                    COLUMN_FIN_PHONE + " TEXT, " +
                    COLUMN_FIN_START_DATE + " TEXT, " +
                    COLUMN_FIN_DAILY_WAGE + " REAL DEFAULT 0, " +
                    COLUMN_FIN_ENABLED + " INTEGER DEFAULT 1, " +
                    COLUMN_REMARK + " TEXT, " +
                    COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)";

    private static final String CREATE_FIN_SUPPLIER_PRODUCT_TABLE =
            "CREATE TABLE " + TABLE_FIN_SUPPLIER_PRODUCT + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_FIN_SUPPLIER_ID + " INTEGER NOT NULL, " +
                    COLUMN_FIN_PRODUCT_ID + " INTEGER, " +
                    COLUMN_FIN_PRODUCT_NAME + " TEXT, " +
                    COLUMN_FIN_SPEC + " TEXT, " +
                    COLUMN_FIN_SP_CATEGORY_ID + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_SP_UNIT + " TEXT, " +
                    COLUMN_FIN_SP_PRICE + " REAL DEFAULT 0, " +
                    COLUMN_FIN_SP_PACK_UNIT + " TEXT, " +
                    COLUMN_FIN_SP_PACK_QTY + " REAL DEFAULT 1, " +
                    COLUMN_FIN_ENABLED + " INTEGER DEFAULT 1, " +
                    COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)";

    private static final String CREATE_FIN_EXPENSE_TABLE =
            "CREATE TABLE " + TABLE_FIN_EXPENSE + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_BATCH_ID + " TEXT, " +
                    COLUMN_FIN_CATEGORY + " INTEGER NOT NULL, " +
                    COLUMN_FIN_SUB_CATEGORY + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_AMOUNT + " REAL NOT NULL, " +
                    COLUMN_FIN_DATE + " TEXT, " +
                    COLUMN_REMARK + " TEXT, " +
                    COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)";

    private static final String CREATE_FIN_PURCHASE_ORDER_TABLE =
            "CREATE TABLE " + TABLE_FIN_PURCHASE_ORDER + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_BATCH_ID + " TEXT, " +
                    COLUMN_FIN_ORDER_NO + " TEXT, " +
                    COLUMN_FIN_DATE + " TEXT, " +
                    COLUMN_FIN_SUPPLIER_ID + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_PURCHASE_PAID + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_PURCHASE_ACCOUNT_ID + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_TOTAL_AMOUNT + " REAL DEFAULT 0, " +
                    COLUMN_REMARK + " TEXT, " +
                    COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)";

    private static final String CREATE_FIN_PURCHASE_ITEM_TABLE =
            "CREATE TABLE " + TABLE_FIN_PURCHASE_ITEM + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_FIN_ORDER_ID + " INTEGER NOT NULL, " +
                    COLUMN_BATCH_ID + " TEXT, " +
                    COLUMN_FIN_CATEGORY_ID + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_SUPPLIER_PRODUCT_ID + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_PRODUCT_NAME + " TEXT, " +
                    COLUMN_FIN_SPEC + " TEXT, " +
                    COLUMN_FIN_UNIT + " TEXT, " +
                    COLUMN_FIN_QUANTITY + " REAL DEFAULT 0, " +
                    COLUMN_FIN_HISTORY_PRICE + " REAL DEFAULT 0, " +
                    COLUMN_FIN_UNIT_PRICE + " REAL DEFAULT 0, " +
                    COLUMN_FIN_AMOUNT + " REAL DEFAULT 0, " +
                    COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)";

    private static final String CREATE_FIN_TICKET_TABLE =
            "CREATE TABLE " + TABLE_FIN_TICKET + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_BATCH_ID + " TEXT, " +
                    COLUMN_FIN_SUPPLIER_ID + " INTEGER NOT NULL, " +
                    COLUMN_FIN_DATE + " TEXT, " +
                    COLUMN_FIN_SUPPLIER_NO + " TEXT, " +
                    COLUMN_FIN_TOTAL_AMOUNT + " REAL DEFAULT 0, " +
                    COLUMN_FIN_SETTLE_TYPE + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_SETTLED + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_ACCOUNT_ID + " INTEGER DEFAULT 0, " +
                    COLUMN_REMARK + " TEXT, " +
                    COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)";

    private static final String CREATE_FIN_TICKET_ITEM_TABLE =
            "CREATE TABLE " + TABLE_FIN_TICKET_ITEM + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_FIN_TICKET_ID + " INTEGER NOT NULL, " +
                    COLUMN_FIN_SUPPLIER_PRODUCT_ID + " INTEGER NOT NULL, " +
                    COLUMN_FIN_QUANTITY + " REAL DEFAULT 0, " +
                    COLUMN_FIN_UNIT_PRICE + " REAL DEFAULT 0, " +
                    COLUMN_FIN_AMOUNT + " REAL DEFAULT 0)";

    private static final String CREATE_FIN_PAYMENT_ACCOUNT_TABLE =
            "CREATE TABLE " + TABLE_FIN_PAYMENT_ACCOUNT + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_FIN_NAME + " TEXT NOT NULL, " +
                    COLUMN_FIN_ENABLED + " INTEGER DEFAULT 1, " +
                    COLUMN_FIN_SORT_ORDER + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)";

    private static final String CREATE_FIN_CONTACT_ACCOUNT_TABLE =
            "CREATE TABLE " + TABLE_FIN_CONTACT_ACCOUNT + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_FIN_SUPPLIER_ID + " INTEGER NOT NULL, " +
                    COLUMN_FIN_ACCOUNT_TYPE + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_ACCOUNT_NAME + " TEXT, " +
                    COLUMN_FIN_ACCOUNT_NO + " TEXT, " +
                    COLUMN_FIN_ENABLED + " INTEGER DEFAULT 1, " +
                    COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)";

    private static final String CREATE_FIN_PAYMENT_TABLE =
            "CREATE TABLE " + TABLE_FIN_PAYMENT + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_BATCH_ID + " TEXT, " +
                    COLUMN_FIN_SUPPLIER_ID + " INTEGER NOT NULL, " +
                    COLUMN_FIN_DATE + " TEXT, " +
                    COLUMN_FIN_ACCOUNT_ID + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_RECEIVE_ACCOUNT_ID + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_DUE_AMOUNT + " REAL DEFAULT 0, " +
                    COLUMN_FIN_PAID_AMOUNT + " REAL DEFAULT 0, " +
                    COLUMN_FIN_DIFF_AMOUNT + " REAL DEFAULT 0, " +
                    COLUMN_FIN_DIFF_REASON + " TEXT, " +
                    COLUMN_REMARK + " TEXT, " +
                    COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)";

    private static final String CREATE_FIN_PAYMENT_TICKET_TABLE =
            "CREATE TABLE " + TABLE_FIN_PAYMENT_TICKET + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_FIN_PAYMENT_ID + " INTEGER NOT NULL, " +
                    COLUMN_FIN_TICKET_ID + " INTEGER NOT NULL, " +
                    COLUMN_FIN_AMOUNT + " REAL DEFAULT 0)";

    private static final String CREATE_FIN_INCOME_TABLE =
            "CREATE TABLE " + TABLE_FIN_INCOME + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_BATCH_ID + " TEXT, " +
                    COLUMN_FIN_DATE + " TEXT, " +
                    COLUMN_FIN_CAR_NO + " TEXT, " +
                    COLUMN_FIN_SHED_NO + " TEXT, " +
                    COLUMN_FIN_SPECS + " TEXT, " +
                    COLUMN_FIN_WEIGHT + " REAL DEFAULT 0, " +
                    COLUMN_FIN_PRICE + " REAL DEFAULT 0, " +
                    COLUMN_FIN_NET_FEE + " REAL DEFAULT 0, " +
                    COLUMN_FIN_AMOUNT + " REAL DEFAULT 0, " +
                    COLUMN_FIN_NET_FEE_PAID + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_NET_FEE_PAY_DATE + " TEXT, " +
                    COLUMN_FIN_NET_FEE_ACCOUNT_ID + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_ACTUAL_AMOUNT + " REAL DEFAULT 0, " +
                    COLUMN_FIN_ACCOUNT_ID + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_PAID + " INTEGER DEFAULT 1, " +
                    COLUMN_FIN_PAY_DATE + " TEXT, " +
                    COLUMN_FIN_AGENT + " TEXT, " +
                    COLUMN_FIN_CUSTOMER + " TEXT, " +
                    COLUMN_FIN_PHONE + " TEXT, " +
                    COLUMN_REMARK + " TEXT, " +
                    COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)";

    private static final String CREATE_FIN_SEED_TABLE =
            "CREATE TABLE " + TABLE_FIN_SEED + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_BATCH_ID + " TEXT, " +
                    COLUMN_FIN_SUPPLIER_ID + " INTEGER NOT NULL, " +
                    COLUMN_FIN_DATE + " TEXT, " +
                    COLUMN_FIN_QUANTITY + " REAL DEFAULT 0, " +
                    COLUMN_FIN_UNIT_PRICE + " REAL DEFAULT 0, " +
                    COLUMN_FIN_TOTAL_AMOUNT + " REAL DEFAULT 0, " +
                    COLUMN_FIN_ACTUAL_AMOUNT + " REAL DEFAULT 0, " +
                    COLUMN_REMARK + " TEXT, " +
                    COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)";

    private static final String CREATE_FIN_WAGE_TABLE =
            "CREATE TABLE " + TABLE_FIN_WAGE + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_BATCH_ID + " TEXT, " +
                    COLUMN_FIN_SUPPLIER_ID + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_PAYEE + " TEXT, " +
                    COLUMN_FIN_AMOUNT + " REAL NOT NULL, " +
                    COLUMN_FIN_DATE + " TEXT, " +
                    COLUMN_FIN_RECEIVE_ACCOUNT_ID + " INTEGER DEFAULT 0, " +
                    COLUMN_REMARK + " TEXT, " +
                    COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)";

    private static final String CREATE_FIN_REST_TABLE =
            "CREATE TABLE " + TABLE_FIN_REST + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_FIN_SUPPLIER_ID + " INTEGER NOT NULL, " +
                    COLUMN_FIN_REST_DATE + " TEXT NOT NULL, " +
                    COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0, " +
                    "UNIQUE (" + COLUMN_FIN_SUPPLIER_ID + ", " + COLUMN_FIN_REST_DATE + "))";

    private static final String CREATE_FIN_LEAVE_TABLE =
            "CREATE TABLE " + TABLE_FIN_LEAVE + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_FIN_SUPPLIER_ID + " INTEGER NOT NULL, " +
                    COLUMN_FIN_DATE + " TEXT NOT NULL, " +
                    COLUMN_FIN_AM_TYPE + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_PM_TYPE + " INTEGER DEFAULT 0, " +
                    COLUMN_REMARK + " TEXT, " +
                    COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0, " +
                    "UNIQUE (" + COLUMN_FIN_SUPPLIER_ID + ", " + COLUMN_FIN_DATE + "))";

    private static final String CREATE_FIN_SALARY_TABLE =
            "CREATE TABLE " + TABLE_FIN_SALARY + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_FIN_SUPPLIER_ID + " INTEGER NOT NULL, " +
                    COLUMN_FIN_YEAR + " INTEGER NOT NULL, " +
                    COLUMN_FIN_MONTH + " INTEGER NOT NULL, " +
                    COLUMN_FIN_WORK_DAYS + " REAL DEFAULT 0, " +
                    COLUMN_FIN_PERSONAL_DAYS + " REAL DEFAULT 0, " +
                    COLUMN_FIN_SICK_DAYS + " REAL DEFAULT 0, " +
                    COLUMN_FIN_ABSENT_DAYS + " REAL DEFAULT 0, " +
                    COLUMN_FIN_BASE_SALARY + " REAL DEFAULT 0, " +
                    COLUMN_FIN_BONUS + " REAL DEFAULT 0, " +
                    COLUMN_FIN_DEDUCTION + " REAL DEFAULT 0, " +
                    COLUMN_FIN_ACTUAL_SALARY + " REAL DEFAULT 0, " +
                    COLUMN_FIN_PAID + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_PAY_DATE + " TEXT, " +
                    COLUMN_FIN_ACCOUNT_ID + " INTEGER DEFAULT 0, " +
                    COLUMN_FIN_RECEIVE_ACCOUNT_ID + " INTEGER DEFAULT 0, " +
                    COLUMN_REMARK + " TEXT, " +
                    COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0, " +
                    "UNIQUE (" + COLUMN_FIN_SUPPLIER_ID + ", " + COLUMN_FIN_YEAR + ", " + COLUMN_FIN_MONTH + "))";

    private static final String CREATE_FIN_ADVANCE_TABLE =
            "CREATE TABLE " + TABLE_FIN_ADVANCE + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_BATCH_ID + " TEXT, " +
                    COLUMN_FIN_SUPPLIER_ID + " INTEGER NOT NULL, " +
                    COLUMN_FIN_DATE + " TEXT, " +
                    COLUMN_FIN_AMOUNT + " REAL DEFAULT 0, " +
                    COLUMN_FIN_SETTLED_AMOUNT + " REAL DEFAULT 0, " +
                    COLUMN_FIN_SETTLED + " INTEGER DEFAULT 0, " +
                    COLUMN_REMARK + " TEXT, " +
                    COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)";

    private static DatabaseHelper instance;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
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
            try { instance.close(); } catch (Exception ignored) { /* ignored */ }
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
        createFinanceTables(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // 已是 v14 的老库不会触发 onCreate/onUpgrade，这里每次打开补一次内置品类。
        // seedBuiltinCategories 幂等（按名称对齐、只更新 sort_order），代价仅 19 条。
        try {
            seedBuiltinCategories(db);
        } catch (Exception ignored) { /* 忽略 */ }
        // fin_ticket.account_id 由 case 14 的 ALTER 加入，但建表语句里没有它。
        // 已经升到 v15 的设备不会再走 onUpgrade，因此每次打开补一次，保证不会缺列。
        ensureColumn(db, TABLE_FIN_TICKET, COLUMN_FIN_ACCOUNT_ID, "INTEGER DEFAULT 0");
    }

    /** 该列存在则直接返回，不存在则补上（自愈存量库，避免建表语句与迁移脱节）。 */
    private void ensureColumn(SQLiteDatabase db, String table, String column, String ddl) {
        try {
            Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
            try {
                int nameIdx = c.getColumnIndex("name");
                while (c.moveToNext()) {
                    if (column.equalsIgnoreCase(c.getString(nameIdx))) return;
                }
            } finally {
                c.close();
            }
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddl);
        } catch (Exception ignored) { /* 并发/已存在等异常一律忽略 */ }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        for (int i = oldVersion; i < newVersion; i++) {
            switch (i) {
                case 1:
                    createFinanceTables(db);
                    break;
                case 2:
                    // v2 → v3：联系人类型 + 苗种表 + 工资表 + 员工字段 + 休息表
                    try {
                        db.execSQL("ALTER TABLE " + TABLE_FIN_SUPPLIER + " ADD COLUMN "
                                + COLUMN_FIN_PERSON_TYPE + " INTEGER DEFAULT 1");
                    } catch (Exception ignored) { /* 已存在则忽略 */ }
                    try {
                        db.execSQL("ALTER TABLE " + TABLE_FIN_SUPPLIER + " ADD COLUMN "
                                + COLUMN_FIN_START_DATE + " TEXT");
                    } catch (Exception ignored) { /* 已存在则忽略 */ }
                    try {
                        db.execSQL("ALTER TABLE " + TABLE_FIN_SUPPLIER + " ADD COLUMN "
                                + COLUMN_FIN_DAILY_WAGE + " REAL DEFAULT 0");
                    } catch (Exception ignored) { /* 已存在则忽略 */ }
                    db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FIN_SEED + " (" +
                            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            COLUMN_BATCH_ID + " TEXT, " +
                            COLUMN_FIN_SUPPLIER_ID + " INTEGER NOT NULL, " +
                            COLUMN_FIN_DATE + " TEXT, " +
                            COLUMN_FIN_QUANTITY + " REAL DEFAULT 0, " +
                            COLUMN_FIN_UNIT_PRICE + " REAL DEFAULT 0, " +
                            COLUMN_FIN_TOTAL_AMOUNT + " REAL DEFAULT 0, " +
                            COLUMN_FIN_ACTUAL_AMOUNT + " REAL DEFAULT 0, " +
                            COLUMN_REMARK + " TEXT, " +
                            COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)");
                    db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FIN_WAGE + " (" +
                            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            COLUMN_BATCH_ID + " TEXT, " +
                            COLUMN_FIN_SUPPLIER_ID + " INTEGER DEFAULT 0, " +
                            COLUMN_FIN_PAYEE + " TEXT, " +
                            COLUMN_FIN_AMOUNT + " REAL NOT NULL, " +
                            COLUMN_FIN_DATE + " TEXT, " +
                            COLUMN_REMARK + " TEXT, " +
                            COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)");
                    db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FIN_REST + " (" +
                            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            COLUMN_FIN_SUPPLIER_ID + " INTEGER NOT NULL, " +
                            COLUMN_FIN_REST_DATE + " TEXT NOT NULL, " +
                            COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0, " +
                            "UNIQUE (" + COLUMN_FIN_SUPPLIER_ID + ", " + COLUMN_FIN_REST_DATE + "))");
                    break;
                case 3:
                    // v3 → v4：票据结算状态 + 支付账户/付款记录/付款-票据关联
                    try {
                        db.execSQL("ALTER TABLE " + TABLE_FIN_TICKET + " ADD COLUMN "
                                + COLUMN_FIN_SETTLE_TYPE + " INTEGER DEFAULT 0");
                    } catch (Exception ignored) { /* 已存在则忽略 */ }
                    try {
                        db.execSQL("ALTER TABLE " + TABLE_FIN_TICKET + " ADD COLUMN "
                                + COLUMN_FIN_SETTLED + " INTEGER DEFAULT 0");
                    } catch (Exception ignored) { /* 已存在则忽略 */ }
                    db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FIN_PAYMENT_ACCOUNT + " (" +
                            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            COLUMN_FIN_NAME + " TEXT NOT NULL, " +
                            COLUMN_FIN_ENABLED + " INTEGER DEFAULT 1, " +
                            COLUMN_FIN_SORT_ORDER + " INTEGER DEFAULT 0, " +
                            COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)");
                    db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FIN_PAYMENT + " (" +
                            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            COLUMN_BATCH_ID + " TEXT, " +
                            COLUMN_FIN_SUPPLIER_ID + " INTEGER NOT NULL, " +
                            COLUMN_FIN_DATE + " TEXT, " +
                            COLUMN_FIN_ACCOUNT_ID + " INTEGER DEFAULT 0, " +
                            COLUMN_FIN_DUE_AMOUNT + " REAL DEFAULT 0, " +
                            COLUMN_FIN_PAID_AMOUNT + " REAL DEFAULT 0, " +
                            COLUMN_FIN_DIFF_AMOUNT + " REAL DEFAULT 0, " +
                            COLUMN_FIN_DIFF_REASON + " TEXT, " +
                            COLUMN_REMARK + " TEXT, " +
                            COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)");
                    db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FIN_PAYMENT_TICKET + " (" +
                            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            COLUMN_FIN_PAYMENT_ID + " INTEGER NOT NULL, " +
                            COLUMN_FIN_TICKET_ID + " INTEGER NOT NULL, " +
                            COLUMN_FIN_AMOUNT + " REAL DEFAULT 0)");
                    break;
                case 4:
                    // v4 → v5：半天请假表 + 按月工资单表
                    db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FIN_LEAVE + " (" +
                            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            COLUMN_FIN_SUPPLIER_ID + " INTEGER NOT NULL, " +
                            COLUMN_FIN_DATE + " TEXT NOT NULL, " +
                            COLUMN_FIN_AM_TYPE + " INTEGER DEFAULT 0, " +
                            COLUMN_FIN_PM_TYPE + " INTEGER DEFAULT 0, " +
                            COLUMN_REMARK + " TEXT, " +
                            COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0, " +
                            "UNIQUE (" + COLUMN_FIN_SUPPLIER_ID + ", " + COLUMN_FIN_DATE + "))");
                    db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FIN_SALARY + " (" +
                            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            COLUMN_FIN_SUPPLIER_ID + " INTEGER NOT NULL, " +
                            COLUMN_FIN_YEAR + " INTEGER NOT NULL, " +
                            COLUMN_FIN_MONTH + " INTEGER NOT NULL, " +
                            COLUMN_FIN_WORK_DAYS + " REAL DEFAULT 0, " +
                            COLUMN_FIN_PERSONAL_DAYS + " REAL DEFAULT 0, " +
                            COLUMN_FIN_SICK_DAYS + " REAL DEFAULT 0, " +
                            COLUMN_FIN_ABSENT_DAYS + " REAL DEFAULT 0, " +
                            COLUMN_FIN_BASE_SALARY + " REAL DEFAULT 0, " +
                            COLUMN_FIN_BONUS + " REAL DEFAULT 0, " +
                            COLUMN_FIN_DEDUCTION + " REAL DEFAULT 0, " +
                            COLUMN_FIN_ACTUAL_SALARY + " REAL DEFAULT 0, " +
                            COLUMN_FIN_PAID + " INTEGER DEFAULT 0, " +
                            COLUMN_FIN_PAY_DATE + " TEXT, " +
                            COLUMN_FIN_ACCOUNT_ID + " INTEGER DEFAULT 0, " +
                            COLUMN_REMARK + " TEXT, " +
                            COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0, " +
                            "UNIQUE (" + COLUMN_FIN_SUPPLIER_ID + ", " + COLUMN_FIN_YEAR + ", " + COLUMN_FIN_MONTH + "))");
                    break;
                case 5:
                    // v5 → v6：员工借款/预支表（发放工资时抵扣，剩余挂账）
                    db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FIN_ADVANCE + " (" +
                            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            COLUMN_BATCH_ID + " TEXT, " +
                            COLUMN_FIN_SUPPLIER_ID + " INTEGER NOT NULL, " +
                            COLUMN_FIN_DATE + " TEXT, " +
                            COLUMN_FIN_AMOUNT + " REAL DEFAULT 0, " +
                            COLUMN_FIN_SETTLED_AMOUNT + " REAL DEFAULT 0, " +
                            COLUMN_FIN_SETTLED + " INTEGER DEFAULT 0, " +
                            COLUMN_REMARK + " TEXT, " +
                            COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)");
                    break;
                case 6:
                    // v6 → v7：卖虾单客户字段
                    try {
                        db.execSQL("ALTER TABLE " + TABLE_FIN_INCOME + " ADD COLUMN "
                                + COLUMN_FIN_CUSTOMER + " TEXT");
                    } catch (Exception ignored) { /* 已存在则忽略 */ }
                    break;
                case 7:
                    // v7 → v8：联系人收款账户表 + 付款/工资/工资流水记录收款账户
                    db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FIN_CONTACT_ACCOUNT + " (" +
                            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            COLUMN_FIN_SUPPLIER_ID + " INTEGER NOT NULL, " +
                            COLUMN_FIN_ACCOUNT_TYPE + " INTEGER DEFAULT 0, " +
                            COLUMN_FIN_ACCOUNT_NAME + " TEXT, " +
                            COLUMN_FIN_ACCOUNT_NO + " TEXT, " +
                            COLUMN_FIN_ENABLED + " INTEGER DEFAULT 1, " +
                            COLUMN_FIN_CREATE_TIME + " INTEGER DEFAULT 0)");
                    try {
                        db.execSQL("ALTER TABLE " + TABLE_FIN_PAYMENT + " ADD COLUMN "
                                + COLUMN_FIN_RECEIVE_ACCOUNT_ID + " INTEGER DEFAULT 0");
                    } catch (Exception ignored) { /* 已存在则忽略 */ }
                    try {
                        db.execSQL("ALTER TABLE " + TABLE_FIN_SALARY + " ADD COLUMN "
                                + COLUMN_FIN_RECEIVE_ACCOUNT_ID + " INTEGER DEFAULT 0");
                    } catch (Exception ignored) { /* 已存在则忽略 */ }
                    try {
                        db.execSQL("ALTER TABLE " + TABLE_FIN_WAGE + " ADD COLUMN "
                                + COLUMN_FIN_RECEIVE_ACCOUNT_ID + " INTEGER DEFAULT 0");
                    } catch (Exception ignored) { /* 已存在则忽略 */ }
                    break;
                case 8:
                    // v8 → v9：卖虾单收款账户 + 支付状态（未付计入应收款）
                    try {
                        db.execSQL("ALTER TABLE " + TABLE_FIN_INCOME + " ADD COLUMN "
                                + COLUMN_FIN_ACCOUNT_ID + " INTEGER DEFAULT 0");
                    } catch (Exception ignored) { /* 已存在则忽略 */ }
                    try {
                        db.execSQL("ALTER TABLE " + TABLE_FIN_INCOME + " ADD COLUMN "
                                + COLUMN_FIN_PAID + " INTEGER DEFAULT 1");
                    } catch (Exception ignored) { /* 已存在则忽略 */ }
                    try {
                        db.execSQL("ALTER TABLE " + TABLE_FIN_INCOME + " ADD COLUMN "
                                + COLUMN_FIN_PAY_DATE + " TEXT");
                    } catch (Exception ignored) { /* 已存在则忽略 */ }
                    break;
case 9:
                    // v9 → v10
                    break;
                case 10:
                    // v10 → v11：供应商商品按品类建品（品类/单位/价格）
                    try {
                        db.execSQL("ALTER TABLE " + TABLE_FIN_SUPPLIER_PRODUCT + " ADD COLUMN "
                                + COLUMN_FIN_SP_CATEGORY_ID + " INTEGER DEFAULT 0");
                    } catch (Exception ignored) { /* 已存在则忽略 */ }
                    try {
                        db.execSQL("ALTER TABLE " + TABLE_FIN_SUPPLIER_PRODUCT + " ADD COLUMN "
                                + COLUMN_FIN_SP_UNIT + " TEXT");
                    } catch (Exception ignored) { /* 已存在则忽略 */ }
                    try {
                        db.execSQL("ALTER TABLE " + TABLE_FIN_SUPPLIER_PRODUCT + " ADD COLUMN "
                                + COLUMN_FIN_SP_PRICE + " REAL DEFAULT 0");
                    } catch (Exception ignored) { /* 已存在则忽略 */ }
                    break;
                case 11:
                    // v11 → v12：供应商商品双单位（包装单位 + 换算率，如 24瓶/箱）
                    try {
                        db.execSQL("ALTER TABLE " + TABLE_FIN_SUPPLIER_PRODUCT + " ADD COLUMN "
                                + COLUMN_FIN_SP_PACK_UNIT + " TEXT");
                    } catch (Exception ignored) { /* 已存在则忽略 */ }
                    try {
                        db.execSQL("ALTER TABLE " + TABLE_FIN_SUPPLIER_PRODUCT + " ADD COLUMN "
                                + COLUMN_FIN_SP_PACK_QTY + " REAL DEFAULT 1");
                    } catch (Exception ignored) { /* 已存在则忽略 */ }
                    break;
                case 12:
                case 13:
                    // v13 → v14：单据式进货（单头 + 明细行）
                    try {
                        db.execSQL(CREATE_FIN_PURCHASE_ORDER_TABLE);
                    } catch (Exception ignored) { /* 已存在则忽略 */ }
try {
                    db.execSQL(CREATE_FIN_PURCHASE_ITEM_TABLE);
                } catch (Exception ignored) { /* 已存在则忽略 */ }
                break;
            case 14:
                // v14 → v15：动保票据增加付款账户
                try {
                    db.execSQL("ALTER TABLE " + TABLE_FIN_TICKET + " ADD COLUMN "
                            + COLUMN_FIN_ACCOUNT_ID + " INTEGER DEFAULT 0");
                } catch (Exception ignored) { /* 已存在则忽略 */ }
                break;
            }
        }
    }

    // ==================== 财务模块建表 ====================
    private void createFinanceTables(SQLiteDatabase db) {
        db.execSQL(CREATE_FIN_CATEGORY_TABLE);
        db.execSQL(CREATE_FIN_PRODUCT_TABLE);
        db.execSQL(CREATE_FIN_PRODUCT_CATEGORY_TABLE);
        db.execSQL(CREATE_FIN_SUPPLIER_TABLE);
        db.execSQL(CREATE_FIN_SUPPLIER_PRODUCT_TABLE);
        db.execSQL(CREATE_FIN_EXPENSE_TABLE);
        db.execSQL(CREATE_FIN_PURCHASE_ORDER_TABLE);
        db.execSQL(CREATE_FIN_PURCHASE_ITEM_TABLE);
        db.execSQL(CREATE_FIN_TICKET_TABLE);
        db.execSQL(CREATE_FIN_TICKET_ITEM_TABLE);
        db.execSQL(CREATE_FIN_INCOME_TABLE);
        db.execSQL(CREATE_FIN_SEED_TABLE);
        db.execSQL(CREATE_FIN_WAGE_TABLE);
        db.execSQL(CREATE_FIN_REST_TABLE);
        db.execSQL(CREATE_FIN_PAYMENT_ACCOUNT_TABLE);
        db.execSQL(CREATE_FIN_CONTACT_ACCOUNT_TABLE);
        db.execSQL(CREATE_FIN_PAYMENT_TABLE);
        db.execSQL(CREATE_FIN_PAYMENT_TICKET_TABLE);
        db.execSQL(CREATE_FIN_LEAVE_TABLE);
        db.execSQL(CREATE_FIN_SALARY_TABLE);
        db.execSQL(CREATE_FIN_ADVANCE_TABLE);
        seedBuiltinCategories(db);
    }

    // ==================== 内置品类初始化（19个，按拼音排序） ====================
    public static final String[] BUILTIN_CATEGORIES = {
            "保肝类", "补碱度", "补钙", "补硬度",
            "防治弧菌", "肥水类", "改底类", "护肠类",
            "护肝类", "解毒类", "抗应激类", "调pH",
            "营养类", "诱食", "有益菌类", "藻种",
            "增氧", "遮光控藻", "中药排毒类"
    };

    // 内置计量单位（下拉选择用，不做单位管理页）
    public static final String[] BUILTIN_UNITS = {
            "瓶", "袋", "箱", "包", "桶", "罐", "扎", "把", "片", "板",
            "斤", "公斤", "克", "吨", "毫升", "升", "个", "只", "条", "件",
            "套", "台", "筐", "提", "打"
    };

    private void seedBuiltinCategories(SQLiteDatabase db) {
        try {
            // 品类名加密存储，先解出已有项按名称对齐，避免重复插入并重排顺序。
            // 早期版本可能写入明文，解密会失败返回空串，这里额外按原始值兜底匹配并原地补加密，
            // 否则会再插一条同名加密品类，造成重复项。
            HashMap<String, Long> decrypted = new HashMap<>();
            HashMap<String, Long> plainRows = new HashMap<>();
            Cursor c = db.rawQuery("SELECT " + COLUMN_ID + "," + COLUMN_FIN_NAME
                    + " FROM " + TABLE_FIN_CATEGORY, null);
            while (c.moveToNext()) {
                String raw = c.getString(1);
                if (raw == null || raw.isEmpty()) continue;
                String n = null;
                try {
                    n = EncryptUtils.decrypt(raw);
                } catch (Exception ignored) { /* 明文等异常情况走兜底 */ }
                if (n != null && !n.isEmpty()) {
                    decrypted.put(n, c.getLong(0));
                } else {
                    plainRows.put(raw, c.getLong(0));
                }
            }
            c.close();

            for (int i = 0; i < BUILTIN_CATEGORIES.length; i++) {
                String name = BUILTIN_CATEGORIES[i];
                Long id = decrypted.get(name);
                boolean wasPlain = false;
                if (id == null) {
                    id = plainRows.get(name);
                    wasPlain = (id != null);
                }
                ContentValues v = new ContentValues();
                v.put(COLUMN_FIN_NAME, EncryptUtils.encrypt(name));
                v.put(COLUMN_FIN_IS_CUSTOM, 0);
                v.put(COLUMN_FIN_ENABLED, 1);
                v.put(COLUMN_FIN_SORT_ORDER, i);
                if (id != null) {
                    // 已是密文则保持原值，避免二次加密；明文行必须覆盖为密文
                    if (!wasPlain) v.remove(COLUMN_FIN_NAME);
                    db.update(TABLE_FIN_CATEGORY, v, COLUMN_ID + "=?",
                            new String[]{String.valueOf(id)});
                } else {
                    db.insert(TABLE_FIN_CATEGORY, null, v);
                }
            }
        } catch (Exception ignored) { /* 已存在则忽略 */ }
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

    // ==================== 统计表操作 ====================
    private boolean isSaveAllowed() {
        return AppIntegrityChecker.checkEverStarted;
    }

    // ==================== 批次完结 ====================
    private static final String BD_KEY_FINISHED = "_finished";
    private static final String BD_KEY_FINISH_DATE = "_finish_date";

    public void finishBatch(String batchId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues flag = new ContentValues();
        flag.put(COLUMN_BD_KEY, BD_KEY_FINISHED);
        flag.put(COLUMN_BATCH_ID, batchId);
        flag.put(COLUMN_BD_VALUE, EncryptUtils.encrypt("1"));
        db.insertWithOnConflict(TABLE_BASIC_DATA, null, flag, SQLiteDatabase.CONFLICT_REPLACE);

        String today = new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).format(new java.util.Date());
        ContentValues finDate = new ContentValues();
        finDate.put(COLUMN_BD_KEY, BD_KEY_FINISH_DATE);
        finDate.put(COLUMN_BATCH_ID, batchId);
        finDate.put(COLUMN_BD_VALUE, EncryptUtils.encrypt(today));
        db.insertWithOnConflict(TABLE_BASIC_DATA, null, finDate, SQLiteDatabase.CONFLICT_REPLACE);

        appendSoldTag(batchId);
    }

    public boolean isBatchFinished(String batchId) {
        if (batchId == null || batchId.isEmpty() || "_meta".equals(batchId)) return false;
        String saved = getBasicData(batchId, BD_KEY_FINISHED);
        return "1".equals(saved);
    }

    public void insertFeedingStats(String batchId, String date, long avgDurationMillis, long recordTime) {
        if (!isSaveAllowed()) return;
        if (isBatchFinished(batchId)) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_STATS_DATE, date);
        values.put(COLUMN_BATCH_ID, batchId);
        values.put(COLUMN_AVG_DURATION, avgDurationMillis);
        values.put(COLUMN_RECORD_TIME, recordTime);
        db.insert(TABLE_FEEDING_STATS, null, values);
    }

    // ==================== 查料分析表操作 ====================
    public void insertFeedingCheckAnalysis(String batchId, String date, double avgSeconds, double standardSeconds) {
        if (!isSaveAllowed()) return;
        if (isBatchFinished(batchId)) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_BATCH_ID, batchId);
        values.put(COLUMN_STATS_DATE, date);
        values.put(COLUMN_FCA_AVG, avgSeconds);
        values.put(COLUMN_FCA_STANDARD, standardSeconds);
        values.put(COLUMN_RECORD_TIME, System.currentTimeMillis());
        db.insert(TABLE_FEEDING_CHECK_ANALYSIS, null, values);
    }

    // ==================== 水质检测表操作 ====================
    public void insertWaterQuality(String batchId, String date, String vibrio, String salinity, String ammonia,
                                   String nitrite, String ph, String dissolvedOxygen,
                                   String maxTemp, String minTemp,
                                   String chlorine, String hydrogenSulfide, String orp) {
        if (!isSaveAllowed()) return;
        if (isBatchFinished(batchId)) return;
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

    public List<WaterQualityRecord> getWaterQualityList(String batchId) {
        List<WaterQualityRecord> list = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = getReadableDatabase().query(TABLE_WATER_QUALITY,
                    new String[]{COLUMN_WQ_DATE, COLUMN_VIBRIO, COLUMN_SALINITY, COLUMN_AMMONIA,
                            COLUMN_NITRITE, COLUMN_PH, COLUMN_DISSOLVED_OXYGEN, COLUMN_MAX_TEMP,
                            COLUMN_MIN_TEMP, COLUMN_CHLORINE, COLUMN_HYDROGEN_SULFIDE, COLUMN_ORP},
                    COLUMN_BATCH_ID + "=?", new String[]{batchId},
                    null, null, COLUMN_WQ_DATE + " ASC, rowid ASC");
            while (cursor.moveToNext()) {
                WaterQualityRecord r = new WaterQualityRecord();
                r.date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WQ_DATE));
                r.vibrio = decryptCell(cursor, COLUMN_VIBRIO);
                r.salinity = decryptCell(cursor, COLUMN_SALINITY);
                r.ammonia = decryptCell(cursor, COLUMN_AMMONIA);
                r.nitrite = decryptCell(cursor, COLUMN_NITRITE);
                r.ph = decryptCell(cursor, COLUMN_PH);
                r.dissolvedOxygen = decryptCell(cursor, COLUMN_DISSOLVED_OXYGEN);
                r.maxTemp = decryptCell(cursor, COLUMN_MAX_TEMP);
                r.minTemp = decryptCell(cursor, COLUMN_MIN_TEMP);
                r.chlorine = decryptCell(cursor, COLUMN_CHLORINE);
                r.hydrogenSulfide = decryptCell(cursor, COLUMN_HYDROGEN_SULFIDE);
                r.orp = decryptCell(cursor, COLUMN_ORP);
                list.add(r);
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
        return list;
    }

    private static String decryptCell(Cursor cursor, String column) {
        String raw = cursor.getString(cursor.getColumnIndexOrThrow(column));
        if (raw == null || raw.isEmpty()) return "";
        try {
            return EncryptUtils.decrypt(raw);
        } catch (Exception e) {
            return raw;
        }
    }

    // ==================== 基础数据表操作 ====================
    public void saveBasicData(String batchId, String key, String value) {
        if (!isSaveAllowed()) return;
        if (isBatchFinished(batchId)) return;
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
        try {
            if (cursor.moveToFirst()) {
                value = EncryptUtils.decrypt(cursor.getString(0));
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
        return value;
    }

    // ==================== 拌料动保预设表操作 ====================
    public void saveMixPreset(String batchId, int row, String name, String tags) {
        if (!isSaveAllowed()) return;
        if (isBatchFinished(batchId)) return;
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
        try {
            if (cursor.moveToFirst()) {
                name = EncryptUtils.decrypt(cursor.getString(0));
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
        return name;
    }

    public String getMixPresetTags(String batchId, int row) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_MIX_PRESETS, new String[]{COLUMN_MIX_TAGS},
                COLUMN_MIX_ROW + "=? AND " + COLUMN_BATCH_ID + "=?",
                new String[]{String.valueOf(row), batchId}, null, null, null);
        String tags = "";
        try {
            if (cursor.moveToFirst()) {
                tags = EncryptUtils.decrypt(cursor.getString(0));
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
        return tags != null ? tags : "";
    }

    public Map<String, String> getMixPresetTagsMap(String batchId) {
        Map<String, String> map = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_MIX_PRESETS, new String[]{COLUMN_MIX_NAME, COLUMN_MIX_TAGS},
                COLUMN_BATCH_ID + "=?", new String[]{batchId},
                null, null, COLUMN_MIX_ROW + " ASC");
        try {
            while (cursor.moveToNext()) {
                String fullName = EncryptUtils.decrypt(cursor.getString(0));
                String tagsWithPrefix = EncryptUtils.decrypt(cursor.getString(1));
                if (fullName != null && !fullName.isEmpty()) {
                    String product = removeUsagePrefix(fullName);
                    String tagContent = "";
                    if (tagsWithPrefix != null && !tagsWithPrefix.isEmpty()) {
                        String[] tagsArr = tagsWithPrefix.split(",");
                        StringBuilder sb = new StringBuilder();
                        for (String t : tagsArr) {
                            String pure = removeUsagePrefix(t);
                            if (!pure.isEmpty()) {
                                if (sb.length() > 0) sb.append(",");
                                sb.append(pure);
                            }
                        }
                        tagContent = sb.toString();
                    }
                    map.put(product, tagContent);
                }
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
        return map;
    }

    // ==================== 调水动保预设表操作 ====================
    public void saveWaterPreset(String batchId, int row, String name, String tags) {
        if (!isSaveAllowed()) return;
        if (isBatchFinished(batchId)) return;
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
        try {
            if (cursor.moveToFirst()) {
                name = EncryptUtils.decrypt(cursor.getString(0));
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
        return name;
    }

    public String getWaterPresetTags(String batchId, int row) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_WATER_PRESETS, new String[]{COLUMN_WATER_TAGS},
                COLUMN_WATER_ROW + "=? AND " + COLUMN_BATCH_ID + "=?",
                new String[]{String.valueOf(row), batchId}, null, null, null);
        String tags = "";
        try {
            if (cursor.moveToFirst()) {
                tags = EncryptUtils.decrypt(cursor.getString(0));
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
        return tags != null ? tags : "";
    }

    public Map<String, String> getWaterPresetTagsMap(String batchId) {
        Map<String, String> map = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_WATER_PRESETS, new String[]{COLUMN_WATER_NAME, COLUMN_WATER_TAGS},
                COLUMN_BATCH_ID + "=?", new String[]{batchId},
                null, null, COLUMN_WATER_ROW + " ASC");
        try {
            while (cursor.moveToNext()) {
                String fullName = EncryptUtils.decrypt(cursor.getString(0));
                String tagsWithPrefix = EncryptUtils.decrypt(cursor.getString(1));
                if (fullName != null && !fullName.isEmpty()) {
                    String product = removeUsagePrefix(fullName);
                    String tagContent = "";
                    if (tagsWithPrefix != null && !tagsWithPrefix.isEmpty()) {
                        String[] tagsArr = tagsWithPrefix.split(",");
                        StringBuilder sb = new StringBuilder();
                        for (String t : tagsArr) {
                            String pure = removeUsagePrefix(t);
                            if (!pure.isEmpty()) {
                                if (sb.length() > 0) sb.append(",");
                                sb.append(pure);
                            }
                        }
                        tagContent = sb.toString();
                    }
                    map.put(product, tagContent);
                }
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
        return map;
    }

    // ==================== 回填：把预设当前全量标签同步到历史记录 ====================
    public int backfillPresetTagsInRecords(String batchId) {
        if (batchId == null || batchId.isEmpty()) return 0;
        if (isBatchFinished(batchId)) return 0;
        Map<String, String> mixMap = getMixPresetTagsMap(batchId);
        Map<String, String> waterMap = getWaterPresetTagsMap(batchId);
        if (mixMap.isEmpty() && waterMap.isEmpty()) return 0;

        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = db.query(TABLE_DAILY_RECORDS,
                new String[]{COLUMN_DATE, COLUMN_WATER_MIX1, COLUMN_WATER_MIX2, COLUMN_WATER_MIX3, COLUMN_WATER_MIX4,
                        COLUMN_MIX1, COLUMN_MIX2, COLUMN_MIX3, COLUMN_MIX4},
                COLUMN_BATCH_ID + "=?", new String[]{batchId}, null, null, null);
        Map<String, ContentValues> pending = new HashMap<>();
        try {
            while (cursor.moveToNext()) {
                String date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE));
                ContentValues cv = new ContentValues();
                boolean need = backfillColumn(cursor, COLUMN_WATER_MIX1, "【调水】", waterMap, cv);
                need |= backfillColumn(cursor, COLUMN_WATER_MIX2, "【调水】", waterMap, cv);
                need |= backfillColumn(cursor, COLUMN_WATER_MIX3, "【调水】", waterMap, cv);
                need |= backfillColumn(cursor, COLUMN_WATER_MIX4, "【调水】", waterMap, cv);
                need |= backfillColumn(cursor, COLUMN_MIX1, "【拌料】", mixMap, cv);
                need |= backfillColumn(cursor, COLUMN_MIX2, "【拌料】", mixMap, cv);
                need |= backfillColumn(cursor, COLUMN_MIX3, "【拌料】", mixMap, cv);
                need |= backfillColumn(cursor, COLUMN_MIX4, "【拌料】", mixMap, cv);
                if (need) pending.put(date, cv);
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
        if (pending.isEmpty()) return 0;

        int changed = 0;
        db.beginTransaction();
        try {
            for (Map.Entry<String, ContentValues> entry : pending.entrySet()) {
                ContentValues encrypted = encryptContentValues(entry.getValue());
                db.update(TABLE_DAILY_RECORDS, encrypted,
                        COLUMN_DATE + "=? AND " + COLUMN_BATCH_ID + "=?",
                        new String[]{entry.getKey(), batchId});
                changed++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return changed;
    }

    private boolean backfillColumn(Cursor c, String column, String prefix, Map<String, String> tagMap, ContentValues cv) {
        if (tagMap.isEmpty()) return false;
        String raw = decryptField(c, column);
        String product = extractProductName(raw);
        if (product.isEmpty() || !tagMap.containsKey(product)) return false;
        String newTags = tagMap.get(product);
        if (newTags == null || newTags.isEmpty()) return false;
        String expected = prefix + newTags + "+" + product;
        if (expected.equals(raw)) return false;
        cv.put(column, expected);
        return true;
    }

    // ==================== 投喂记录优化：排序 / 批量查询 / 事务 ====================

    public static class PresetItem {
        public String displayName;
        public String tagContent;
    }

    public List<PresetItem> getMixPresetsSorted(String batchId) {
        return sortPresetItems(getMixPresetTagsMap(batchId));
    }

    public List<PresetItem> getWaterPresetsSorted(String batchId) {
        return sortPresetItems(getWaterPresetTagsMap(batchId));
    }

    private List<PresetItem> sortPresetItems(Map<String, String> rawMap) {
        List<PresetItem> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : rawMap.entrySet()) {
            PresetItem item = new PresetItem();
            item.displayName = entry.getKey();
            item.tagContent = entry.getValue();
            list.add(item);
        }
        java.text.Collator collator = java.text.Collator.getInstance(java.util.Locale.CHINA);
        list.sort((o1, o2) -> collator.compare(o1.displayName, o2.displayName));
        return list;
    }

    public List<FeedingRecordActivity.DayRecord> getRecordsByPage(String batchId, int limit, int offset) {
        List<FeedingRecordActivity.DayRecord> result = new ArrayList<>();
        String waterPrepDate = getBasicData(batchId, "water_prep_date");
        if (waterPrepDate == null || waterPrepDate.isEmpty() || "选择日期".equals(waterPrepDate)) return result;
        String startDate = standardizeDate(waterPrepDate);
        String endDate = addMonths(startDate);
        SQLiteDatabase db = getReadableDatabase();
        // 查询该批次所有记录（无分页），用于填充缺失日期
        String sql = "SELECT * FROM " + TABLE_DAILY_RECORDS +
            " WHERE " + COLUMN_BATCH_ID + "=? AND " + COLUMN_DATE + " BETWEEN ? AND ?" +
            " ORDER BY " + COLUMN_DATE + " ASC";
        Cursor cursor = db.rawQuery(sql, new String[]{batchId, startDate, endDate});
        Map<String, FeedingRecordActivity.DayRecord> recordMap = new LinkedHashMap<>();
        try {
            while (cursor.moveToNext()) {
                FeedingRecordActivity.DayRecord r = new FeedingRecordActivity.DayRecord();
                r.date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE));
                r.breakfast = decryptField(cursor, COLUMN_BREAKFAST);
                r.lunch = decryptField(cursor, COLUMN_LUNCH);
                r.dinner = decryptField(cursor, COLUMN_DINNER);
                r.nightSnack = decryptField(cursor, COLUMN_NIGHT_SNACK);
                r.waterMix1 = decryptField(cursor, COLUMN_WATER_MIX1);
                r.waterMix2 = decryptField(cursor, COLUMN_WATER_MIX2);
                r.waterMix3 = decryptField(cursor, COLUMN_WATER_MIX3);
                r.waterMix4 = decryptField(cursor, COLUMN_WATER_MIX4);
                r.mix1 = decryptField(cursor, COLUMN_MIX1);
                r.mix2 = decryptField(cursor, COLUMN_MIX2);
                r.mix3 = decryptField(cursor, COLUMN_MIX3);
                r.mix4 = decryptField(cursor, COLUMN_MIX4);
                r.remark = decryptField(cursor, COLUMN_REMARK);
                recordMap.put(r.date, r);
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
        Calendar cal = parseDate(startDate);
        Calendar endCal = parseDate(endDate);
        int skipCount = 0;
        while (!cal.after(endCal)) {
            String dateStr = String.format(Locale.CHINA, "%d/%02d/%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            if (skipCount >= offset && result.size() < limit) {
                FeedingRecordActivity.DayRecord r = recordMap.get(dateStr);
                if (r == null) { r = new FeedingRecordActivity.DayRecord(); r.date = dateStr; }
                result.add(r);
            }
            skipCount++;
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return result;
    }

    public int getTotalDaysInBatch(String batchId) {
        String waterPrepDate = getBasicData(batchId, "water_prep_date");
        if (waterPrepDate == null || waterPrepDate.isEmpty() || "选择日期".equals(waterPrepDate)) return 0;
        String startDate = standardizeDate(waterPrepDate);
        String endDate = addMonths(startDate);
        Calendar cal = parseDate(startDate);
        Calendar endCal = parseDate(endDate);
        int count = 0;
        while (!cal.after(endCal)) {
            count++;
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return count;
    }

    public List<FeedingRecordActivity.DayRecord> getRecordsForExport(String batchId) {
        List<FeedingRecordActivity.DayRecord> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_DAILY_RECORDS, null,
                COLUMN_BATCH_ID + "=?", new String[]{batchId}, null, null, COLUMN_DATE + " ASC");
        try {
            while (cursor.moveToNext()) {
                FeedingRecordActivity.DayRecord r = new FeedingRecordActivity.DayRecord();
                r.date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE));
                r.breakfast = decryptField(cursor, COLUMN_BREAKFAST);
                r.lunch = decryptField(cursor, COLUMN_LUNCH);
                r.dinner = decryptField(cursor, COLUMN_DINNER);
                r.nightSnack = decryptField(cursor, COLUMN_NIGHT_SNACK);
                r.waterMix1 = decryptField(cursor, COLUMN_WATER_MIX1);
                r.waterMix2 = decryptField(cursor, COLUMN_WATER_MIX2);
                r.waterMix3 = decryptField(cursor, COLUMN_WATER_MIX3);
                r.waterMix4 = decryptField(cursor, COLUMN_WATER_MIX4);
                r.mix1 = decryptField(cursor, COLUMN_MIX1);
                r.mix2 = decryptField(cursor, COLUMN_MIX2);
                r.mix3 = decryptField(cursor, COLUMN_MIX3);
                r.mix4 = decryptField(cursor, COLUMN_MIX4);
                r.remark = decryptField(cursor, COLUMN_REMARK);
                result.add(r);
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
        return result;
    }

    // ==================== 系统判断卖虾：超过7天无新记录则打标 ====================

    public boolean markSoldIfIdle(String batchId) {
        if (batchId == null || batchId.isEmpty()) return false;
        if (isBatchFinished(batchId)) return false;
        if (daysSinceLastRecord(batchId) > 7) return appendSoldTag(batchId);
        return revokeSoldTags(batchId);
    }

    private boolean revokeSoldTags(String batchId) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.query(TABLE_DAILY_RECORDS,
                new String[]{COLUMN_DATE, COLUMN_REMARK},
                COLUMN_BATCH_ID + "=?", new String[]{batchId},
                null, null, null);
        boolean changed = false;
        try {
            while (c != null && c.moveToNext()) {
                String date = c.getString(c.getColumnIndexOrThrow(COLUMN_DATE));
                String remark = decryptField(c, COLUMN_REMARK);
                String stripped = stripSoldTag(remark);
                if (stripped == null) continue;
                updateRemark(db, batchId, date, stripped);
                changed = true;
            }
        } finally {
            if (c != null && !c.isClosed()) c.close();
        }
        return changed;
    }

    private String stripSoldTag(String remark) {
        if (remark == null) return null;
        String tagWithSep = SOLD_SEP + SOLD_TAG;
        String stripped;
        if (remark.endsWith(tagWithSep)) {
            stripped = remark.substring(0, remark.length() - tagWithSep.length());
        } else if (remark.endsWith(SOLD_TAG)) {
            stripped = remark.substring(0, remark.length() - SOLD_TAG.length());
        } else {
            return null;
        }
        while (stripped.endsWith(SOLD_SEP)) {
            stripped = stripped.substring(0, stripped.length() - SOLD_SEP.length());
        }
        return stripped;
    }

    private void clearStaleSoldTags(String batchId, String keepDate) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.query(TABLE_DAILY_RECORDS,
                new String[]{COLUMN_DATE, COLUMN_REMARK},
                COLUMN_BATCH_ID + "=?", new String[]{batchId},
                null, null, null);
        try {
            while (c != null && c.moveToNext()) {
                String date = c.getString(c.getColumnIndexOrThrow(COLUMN_DATE));
                if (keepDate.equals(date)) continue;
                String remark = decryptField(c, COLUMN_REMARK);
                String stripped = stripSoldTag(remark);
                if (stripped == null) continue;
                updateRemark(db, batchId, date, stripped);
            }
        } finally {
            if (c != null && !c.isClosed()) c.close();
        }
    }

    public boolean appendSoldTag(String batchId) {
        if (batchId == null || batchId.isEmpty()) return false;
        SQLiteDatabase db = getWritableDatabase();
        String lastDate = null;
        String remark = null;
        Cursor c = db.query(TABLE_DAILY_RECORDS,
                new String[]{COLUMN_DATE, COLUMN_REMARK},
                COLUMN_BATCH_ID + "=?" + SOLD_FEED_FILTER, new String[]{batchId},
                null, null, COLUMN_DATE + " DESC", "1");
        try {
            if (c != null && c.moveToFirst()) {
                lastDate = c.getString(c.getColumnIndexOrThrow(COLUMN_DATE));
                remark = decryptField(c, COLUMN_REMARK);
            }
        } finally {
            if (c != null && !c.isClosed()) c.close();
        }
        if (lastDate == null || remark == null) return false;
        clearStaleSoldTags(batchId, lastDate);
        if (remark.contains(SOLD_TAG)) return false;
        String newRemark = remark.isEmpty() ? SOLD_TAG : remark + SOLD_SEP + SOLD_TAG;
        updateRemark(db, batchId, lastDate, newRemark);
        return true;
    }

    public static final String SOLD_TAG = "【系统判断】卖虾";
    private static final String SOLD_SEP = "，";
    private static final String SOLD_FEED_FILTER =
            " AND ( " + COLUMN_BREAKFAST + " IS NOT NULL AND " + COLUMN_BREAKFAST + " <> ''" +
            " OR " + COLUMN_LUNCH + " IS NOT NULL AND " + COLUMN_LUNCH + " <> ''" +
            " OR " + COLUMN_DINNER + " IS NOT NULL AND " + COLUMN_DINNER + " <> ''" +
            " OR " + COLUMN_NIGHT_SNACK + " IS NOT NULL AND " + COLUMN_NIGHT_SNACK + " <> '')";

    private int daysSinceLastRecord(String batchId) {
        SQLiteDatabase db = getReadableDatabase();
        String lastDate = null;
        Cursor c = db.query(TABLE_DAILY_RECORDS,
                new String[]{COLUMN_DATE},
                COLUMN_BATCH_ID + "=?" + SOLD_FEED_FILTER, new String[]{batchId},
                null, null, COLUMN_DATE + " DESC", "1");
        try {
            if (c != null && c.moveToFirst()) {
                lastDate = c.getString(c.getColumnIndexOrThrow(COLUMN_DATE));
            }
        } finally {
            if (c != null && !c.isClosed()) c.close();
        }
        if (lastDate == null) return 0;
        return daysSince(lastDate);
    }

    private int daysSince(String dateStr) {
        Calendar cal = parseDate(dateStr);
        Calendar now = Calendar.getInstance();
        long diff = now.getTimeInMillis() - cal.getTimeInMillis();
        return (int) (diff / (24L * 60 * 60 * 1000));
    }

    private void updateRemark(SQLiteDatabase db, String batchId, String date, String newRemark) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_REMARK, newRemark);
        ContentValues encrypted = encryptContentValues(values);
        db.update(TABLE_DAILY_RECORDS, encrypted,
                COLUMN_DATE + "=? AND " + COLUMN_BATCH_ID + "=?",
                new String[]{date, batchId});
    }

    public void saveRecordWithTransaction(String batchId, FeedingRecordActivity.DayRecord record) {
        if (!isSaveAllowed()) return;
        if (isBatchFinished(batchId)) return;
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
        ContentValues encrypted = encryptContentValues(values);
        db.beginTransaction();
        try {
            db.insertWithOnConflict(TABLE_DAILY_RECORDS, null, encrypted, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
            revokeSoldTags(batchId);
        } finally {
            db.endTransaction();
        }
    }

    private String decryptField(Cursor c, String column) {
        int idx = c.getColumnIndex(column);
        if (idx < 0) return "";
        String raw = c.getString(idx);
        return (raw != null) ? EncryptUtils.decrypt(raw) : "";
    }

    private String standardizeDate(String dateStr) {
        String[] parts = dateStr.split("/");
        if (parts.length != 3) parts = dateStr.split("-");
        if (parts.length != 3) parts = dateStr.split("\\.");
        if (parts.length == 3) return String.format(Locale.CHINA, "%s/%02d/%02d", parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        return dateStr;
    }

    private String addMonths(String startDate) {
        Calendar cal = parseDate(startDate);
        cal.add(Calendar.MONTH, 6);
        return String.format(Locale.CHINA, "%d/%02d/%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }

    private Calendar parseDate(String dateStr) {
        String[] parts = dateStr.split("/");
        if (parts.length != 3) parts = dateStr.split("-");
        if (parts.length != 3) parts = dateStr.split("\\.");
        Calendar cal = Calendar.getInstance();
        if (parts.length == 3) {
            cal.set(Calendar.YEAR, Integer.parseInt(parts[0]));
            cal.set(Calendar.MONTH, Integer.parseInt(parts[1]) - 1);
            cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(parts[2]));
        }
        return cal;
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
        if (isBatchFinished(batchId)) return;
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_FEEDING_CHECK_RECORDS,
                "batch_id = ? AND record_date = ?",
                new String[]{batchId, date});
    }

    public void insertCheckRecords(List<ContentValues> valuesList) {
        if (valuesList == null || valuesList.isEmpty()) return;
        if (!isSaveAllowed()) return;
        String batchId = valuesList.get(0).getAsString("batch_id");
        if (isBatchFinished(batchId)) return;
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
        try {
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
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
        return list;
    }

    public List<CheckRecord> getCheckRecordsByBatch(String batchId) {
        List<CheckRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_FEEDING_CHECK_RECORDS,
                new String[]{"id", "record_date", "shed_row_index", "is_excluded", "water_percentage",
                        "created_at", "start_time", "end_time", "shed_number", "check_time", "duration_seconds"},
                "batch_id = ?",
                new String[]{batchId}, null, null,
                "record_date ASC, shed_row_index ASC, id ASC");
        try {
            while (cursor.moveToNext()) {
                CheckRecord record = new CheckRecord();
                record.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
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
                    } catch (Exception e) {
                        record.durationSeconds = 0;
                    }
                } else {
                    record.durationSeconds = 0;
                }
                list.add(record);
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
        return list;
    }

    public List<DailyFeedSummary> getDailyFeedSummary(String batchId, String startDate, String endDate) {
        List<DailyFeedSummary> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_DAILY_RECORDS,
                new String[]{COLUMN_DATE, COLUMN_BREAKFAST, COLUMN_LUNCH, COLUMN_DINNER, COLUMN_NIGHT_SNACK},
                COLUMN_BATCH_ID + "=? AND " + COLUMN_DATE + " BETWEEN ? AND ?",
                new String[]{batchId, startDate, endDate},
                null, null, COLUMN_DATE + " ASC");
        try {
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
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
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
        try {
            while (cursor.moveToNext()) {
                try {
                    String date = cursor.getString(cursor.getColumnIndexOrThrow("record_date"));
                    String encDuration = cursor.getString(cursor.getColumnIndexOrThrow("duration_seconds"));
                    int isExcluded = cursor.getInt(cursor.getColumnIndexOrThrow("is_excluded"));
                    if (isExcluded == 0 && encDuration != null && !encDuration.isEmpty()) {
                        long duration = Long.parseLong(EncryptUtils.decrypt(encDuration));
                        dateDurationMap.computeIfAbsent(date, k -> new ArrayList<>()).add(duration);
                    }
                } catch (Exception e) { Log.e("DBHelper", "getFeedingDurationByShed", e); }
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
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
        try {
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
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
        list.sort((a, b) -> {
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
        try {
            while (cursor.moveToNext()) {
                try {
                    String encShedNumber = cursor.getString(cursor.getColumnIndexOrThrow("shed_number"));
                    String encDuration = cursor.getString(cursor.getColumnIndexOrThrow("duration_seconds"));
                    int isExcluded = cursor.getInt(cursor.getColumnIndexOrThrow("is_excluded"));
                    if (isExcluded == 0 && encDuration != null && !encDuration.isEmpty()) {
                        long duration = Long.parseLong(EncryptUtils.decrypt(encDuration));
                        String shedNumber = EncryptUtils.decrypt(encShedNumber);
                        shedDurationMap.computeIfAbsent(shedNumber, k -> new ArrayList<>()).add(duration);
                    }
                } catch (Exception e) { Log.e("DBHelper", "getFeedingDurationByShedGrouped", e); }
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
        for (Map.Entry<String, List<Long>> entry : shedDurationMap.entrySet()) {
            List<Long> durations = entry.getValue();
            if (durations.isEmpty()) continue;
            long total = 0;
            for (Long d : durations) total += d;
            long avg = total / durations.size();
            list.add(new ShedDurationSummary(entry.getKey(), avg));
        }
        list.sort((a, b) -> {
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
        try {
            if (c.moveToFirst()) {
                result = c.getLong(0);
            }
        } finally {
            if (c != null && !c.isClosed()) c.close();
        }
        return result;
    }

    public List<DurationSummary> getDailyAverageDurations(String batchId, String startDate, String endDate) {
        List<DurationSummary> list = new ArrayList<>();
        String sql = "SELECT " + COLUMN_STATS_DATE + ", AVG(" + COLUMN_AVG_DURATION + ") " +
                " FROM " + TABLE_FEEDING_STATS +
                " WHERE " + COLUMN_BATCH_ID + " = ? AND " + COLUMN_STATS_DATE + " BETWEEN ? AND ? " +
                " GROUP BY " + COLUMN_STATS_DATE + " ORDER BY " + COLUMN_STATS_DATE;
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{batchId, startDate, endDate});
        try {
            while (c.moveToNext()) {
                list.add(new DurationSummary(c.getString(0), (long) c.getDouble(1)));
            }
        } finally {
            if (c != null && !c.isClosed()) c.close();
        }
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
        try {
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
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
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
        try {
            if (cursor.moveToNext()) {
                latestDate = cursor.getString(0);
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
        if (latestDate == null) return null;
        cursor = db.rawQuery(
                "SELECT category, item_name, price FROM " + TABLE_MARKET_PRICES +
                        " WHERE date = ? ORDER BY category, item_name",
                new String[]{latestDate});
        try {
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
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
        return new PriceData(latestDate, new ArrayList<>(map.values()));
    }

    public List<PricePoint> getPriceHistory(String itemName) {
        List<PricePoint> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_MARKET_PRICES,
                new String[]{"date", "price"}, "item_name = ?",
                new String[]{itemName}, null, null, "date ASC");
        try {
            while (cursor.moveToNext()) {
                String date = cursor.getString(0);
                String price = cursor.getString(1);
                float mid = parseMidPrice(price);
                PricePoint point = new PricePoint(date, mid);
                list.add(point);
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
        return list;
    }

    private String normalizeDate(String date) {
        if (date == null) return "";
        return date.replace("/", "-");
    }

    private float parseMidPrice(String price) {
        if (price == null) return 0;
        try { return Float.parseFloat(price); } catch (NumberFormatException ignored) { /* ignored */ }
        return 0;
    }

    // ==================== 放苗天数工具 ====================
    public String getStockingDate(String batchId) {
        String dateStr = getBasicData(batchId, "stocking_date");
        if (dateStr == null || dateStr.trim().isEmpty() || "选择日期".equals(dateStr)) return null;
        return dateStr.trim();
    }

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
                java.util.Date now = new java.util.Date();
                String finishDateStr = getBasicData(batchId, BD_KEY_FINISH_DATE);
                if (finishDateStr != null && !finishDateStr.isEmpty()) {
                    try {
                        java.util.Date finishDate = new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).parse(finishDateStr);
                        if (finishDate != null && finishDate.before(now)) now = finishDate;
                    } catch (Exception ignored) { /* ignored */ }
                }
                long diff = now.getTime() - cal.getTimeInMillis();
                int days = (int) (diff / (1000 * 60 * 60 * 24)) + 1;
                return Math.max(days, 1);
            } catch (Exception ignored) { /* ignored */ }
        }
        return 0;
    }

    public double getAccumulatedFeed(String batchId, int fromDay, int toDay) {
        String dateStr = getBasicData(batchId, "stocking_date");
        if (dateStr == null || dateStr.isEmpty() || "选择日期".equals(dateStr)) return 0;
        double total = 0;
        Cursor c = null;
        try {
            String[] formats = {"yyyy/MM/dd", "yyyy-MM-dd", "yyyy.M.d"};
            java.util.Date stockingDate = null;
            for (String fmt : formats) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.CHINA);
                    stockingDate = sdf.parse(dateStr);
                    if (stockingDate != null) break;
                } catch (Exception ignored) { /* ignored */ }
            }
            if (stockingDate == null) return 0;

            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(stockingDate.getTime());
            cal.add(Calendar.DAY_OF_YEAR, fromDay - 1);
            String startDate = new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).format(cal.getTime());
            cal.add(Calendar.DAY_OF_YEAR, toDay - fromDay);
            String endDate = new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).format(cal.getTime());

            c = getReadableDatabase().rawQuery(
                    "SELECT " + COLUMN_BREAKFAST + "," + COLUMN_LUNCH + "," + COLUMN_DINNER + "," + COLUMN_NIGHT_SNACK +
                    " FROM " + TABLE_DAILY_RECORDS +
                    " WHERE " + COLUMN_BATCH_ID + "=? AND " + COLUMN_DATE + ">=? AND " + COLUMN_DATE + "<=?",
                    new String[]{batchId, startDate, endDate});
            while (c.moveToNext()) {
                for (int i = 0; i < 4; i++) {
                    String enc = c.getString(i);
                    if (enc != null && !enc.isEmpty()) {
                        try { total += Double.parseDouble(EncryptUtils.decrypt(enc)); } catch (Exception ignored) { /* ignored */ }
                    }
                }
            }
        } catch (Exception ignored) { /* ignored */ } finally {
            if (c != null && !c.isClosed()) c.close();
        }
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
        try {
            if (c.moveToFirst()) {
                int startValue = c.getInt(c.getColumnIndexOrThrow(COLUMN_START_VALUE));
                int unitType = c.getInt(c.getColumnIndexOrThrow(COLUMN_UNIT_TYPE));
                int stockingDay = getStockingDay(batchId);

                ContentValues cv = new ContentValues();
                if (unitType == 0) {
                    cv.put(COLUMN_LAST_TRIGGER_DAY, stockingDay);
                } else {
                    double feed = getAccumulatedFeed(batchId, startValue, stockingDay);
                    cv.put(COLUMN_LAST_TRIGGER_FEED, feed);
                }
                cv.put(COLUMN_TASK_STATUS, 1);
                db.update(TABLE_PLAN_TASKS, cv, COLUMN_TASK_ID + "=?", new String[]{String.valueOf(taskId)});
            }
        } finally {
            if (c != null && !c.isClosed()) c.close();
        }
    }

    public Cursor getAllSubTasks(String batchId) {
        return getReadableDatabase().query(TABLE_PLAN_TASKS, null,
                COLUMN_BATCH_ID + "=? AND " + COLUMN_IS_ACTIVE + "=1 AND " + COLUMN_PARENT_ID + "!=-1",
                new String[]{batchId}, null, null, COLUMN_CREATED_AT + " ASC");
    }

}
