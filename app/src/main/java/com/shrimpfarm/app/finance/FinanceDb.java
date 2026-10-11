package com.shrimpfarm.app.finance;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.utils.EncryptUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 财务模块数据访问层。
 * 遵循项目约定：除 date/batch_id 外所有字符串字段 AES 加密存储，
 * 关联与统计均按整型 id / REAL 金额进行。
 */
public class FinanceDb {

    public static final int CAT_LIFE = 1;
    public static final int CAT_DAILY = 2;
    public static final int CAT_BREEDING = 3;
    public static final int SUB_REPAIR = 31;
    public static final int SUB_POND = 32;
    public static final int SUB_SEED = 33;
    public static final int SUB_ELECTRIC = 34;
    public static final int SUB_LABOR = 35;
    public static final int SUB_DEPRECIATION = 36;

    public static final int PERSON_SUPPLIER = 1;
    public static final int PERSON_AGENT = 2;
    public static final int PERSON_CUSTOMER = 3;
    public static final int PERSON_EMPLOYEE = 4;

    private final DatabaseHelper helper;

    public FinanceDb(Context context) {
        helper = DatabaseHelper.getInstance(context);
    }

    private SQLiteDatabase db() {
        return helper.getWritableDatabase();
    }

    // ==================== 品类 ====================
    public static class FinCategory {
        public long id;
        public String name;
        public boolean isCustom;
        public boolean enabled;
        public int sortOrder;
    }

    public List<FinCategory> getCategories() {
        List<FinCategory> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id,name,is_custom,enabled,sort_order FROM "
                + DatabaseHelper.TABLE_FIN_CATEGORY + " ORDER BY sort_order, id", null);
        while (c.moveToNext()) {
            FinCategory cat = new FinCategory();
            cat.id = c.getLong(0);
            cat.name = EncryptUtils.decrypt(c.getString(1));
            cat.isCustom = c.getInt(2) == 1;
            cat.enabled = c.getInt(3) == 1;
            cat.sortOrder = c.getInt(4);
            list.add(cat);
        }
        c.close();
        return list;
    }

    public long insertCategory(String name, boolean isCustom) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_NAME, EncryptUtils.encrypt(name));
        v.put(DatabaseHelper.COLUMN_FIN_IS_CUSTOM, isCustom ? 1 : 0);
        v.put(DatabaseHelper.COLUMN_FIN_ENABLED, 1);
        v.put(DatabaseHelper.COLUMN_FIN_SORT_ORDER, 999);
        return db().insert(DatabaseHelper.TABLE_FIN_CATEGORY, null, v);
    }

    public void setCategoryEnabled(long id, boolean enabled) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_ENABLED, enabled ? 1 : 0);
        db().update(DatabaseHelper.TABLE_FIN_CATEGORY, v, "id=?", new String[]{String.valueOf(id)});
    }

    public void renameCategory(long id, String name) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_NAME, EncryptUtils.encrypt(name));
        db().update(DatabaseHelper.TABLE_FIN_CATEGORY, v, "id=?", new String[]{String.valueOf(id)});
    }

    // ==================== 标准商品 ====================
    public static class FinProduct {
        public long id;
        public String name;
        public String unit;
        public boolean enabled;
        public List<Long> categoryIds = new ArrayList<>();
        public String categoryNames = "";
    }

    public long insertProduct(String name, String unit) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_NAME, EncryptUtils.encrypt(name));
        v.put(DatabaseHelper.COLUMN_FIN_UNIT, EncryptUtils.encrypt(unit));
        v.put(DatabaseHelper.COLUMN_FIN_ENABLED, 1);
        v.put(DatabaseHelper.COLUMN_FIN_CREATE_TIME, System.currentTimeMillis());
        return db().insert(DatabaseHelper.TABLE_FIN_PRODUCT, null, v);
    }

    public void setProductCategories(long productId, List<Long> categoryIds) {
        db().delete(DatabaseHelper.TABLE_FIN_PRODUCT_CATEGORY,
                DatabaseHelper.COLUMN_FIN_PRODUCT_ID + "=?", new String[]{String.valueOf(productId)});
        for (long cid : categoryIds) {
            ContentValues v = new ContentValues();
            v.put(DatabaseHelper.COLUMN_FIN_PRODUCT_ID, productId);
            v.put(DatabaseHelper.COLUMN_FIN_CATEGORY_ID, cid);
            db().insert(DatabaseHelper.TABLE_FIN_PRODUCT_CATEGORY, null, v);
        }
    }

    public void renameProduct(long id, String name, String unit) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_NAME, EncryptUtils.encrypt(name));
        v.put(DatabaseHelper.COLUMN_FIN_UNIT, EncryptUtils.encrypt(unit));
        db().update(DatabaseHelper.TABLE_FIN_PRODUCT, v, "id=?", new String[]{String.valueOf(id)});
    }

    public void setProductEnabled(long id, boolean enabled) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_ENABLED, enabled ? 1 : 0);
        db().update(DatabaseHelper.TABLE_FIN_PRODUCT, v, "id=?", new String[]{String.valueOf(id)});
    }

    public List<FinProduct> getProducts() {
        List<FinProduct> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id,name,unit,enabled FROM "
                + DatabaseHelper.TABLE_FIN_PRODUCT + " ORDER BY id DESC", null);
        while (c.moveToNext()) {
            FinProduct p = new FinProduct();
            p.id = c.getLong(0);
            p.name = EncryptUtils.decrypt(c.getString(1));
            p.unit = EncryptUtils.decrypt(c.getString(2));
            p.enabled = c.getInt(3) == 1;
            list.add(p);
        }
        c.close();
        fillProductCategories(list);
        return list;
    }

    private void fillProductCategories(List<FinProduct> products) {
        for (FinProduct p : products) {
            Cursor c = db().rawQuery(
                    "SELECT pc.category_id, cat.name FROM " + DatabaseHelper.TABLE_FIN_PRODUCT_CATEGORY
                            + " pc LEFT JOIN " + DatabaseHelper.TABLE_FIN_CATEGORY + " cat"
                            + " ON pc.category_id=cat.id WHERE pc." + DatabaseHelper.COLUMN_FIN_PRODUCT_ID + "=?",
                    new String[]{String.valueOf(p.id)});
            StringBuilder sb = new StringBuilder();
            while (c.moveToNext()) {
                p.categoryIds.add(c.getLong(0));
                if (sb.length() > 0) sb.append("、");
                sb.append(EncryptUtils.decrypt(c.getString(1)));
            }
            c.close();
            p.categoryNames = sb.toString();
        }
    }

    public List<FinProduct> getProductsByCategory(long categoryId) {
        List<FinProduct> all = getProducts();
        List<FinProduct> result = new ArrayList<>();
        for (FinProduct p : all) {
            if (p.enabled && p.categoryIds.contains(categoryId)) result.add(p);
        }
        return result;
    }

    public FinProduct getProduct(long id) {
        Cursor c = db().rawQuery("SELECT id,name,unit,enabled FROM "
                + DatabaseHelper.TABLE_FIN_PRODUCT + " WHERE id=?", new String[]{String.valueOf(id)});
        FinProduct p = null;
        if (c.moveToFirst()) {
            p = new FinProduct();
            p.id = c.getLong(0);
            p.name = EncryptUtils.decrypt(c.getString(1));
            p.unit = EncryptUtils.decrypt(c.getString(2));
            p.enabled = c.getInt(3) == 1;
        }
        c.close();
        if (p != null && p.id > 0) {
            List<FinProduct> single = new ArrayList<>();
            single.add(p);
            fillProductCategories(single);
        }
        return p;
    }

    // ==================== 联系人（供应商/中介/客户/员工） ====================
    public static class FinSupplier {
        public long id;
        public String name;
        public String contact;
        public String phone;
        public boolean enabled;
        public String remark;
        public int personType = PERSON_SUPPLIER;
        public String startDate = "";
        public double dailyWage = 0;
    }

    public long insertSupplier(int personType, String name, String contact, String phone, String remark) {
        return insertSupplier(personType, name, contact, phone, "", 0, remark);
    }

    public long insertSupplier(int personType, String name, String contact, String phone,
                               String startDate, double dailyWage, String remark) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_NAME, EncryptUtils.encrypt(name));
        v.put(DatabaseHelper.COLUMN_FIN_PERSON_TYPE, personType);
        v.put(DatabaseHelper.COLUMN_FIN_CONTACT, EncryptUtils.encrypt(contact));
        v.put(DatabaseHelper.COLUMN_FIN_PHONE, EncryptUtils.encrypt(phone));
        v.put(DatabaseHelper.COLUMN_FIN_START_DATE, startDate);
        v.put(DatabaseHelper.COLUMN_FIN_DAILY_WAGE, dailyWage);
        v.put(DatabaseHelper.COLUMN_FIN_ENABLED, 1);
        v.put(DatabaseHelper.COLUMN_REMARK, EncryptUtils.encrypt(remark));
        v.put(DatabaseHelper.COLUMN_FIN_CREATE_TIME, System.currentTimeMillis());
        return db().insert(DatabaseHelper.TABLE_FIN_SUPPLIER, null, v);
    }

    public void updateSupplier(long id, int personType, String name, String contact, String phone, String remark) {
        updateSupplier(id, personType, name, contact, phone, "", 0, remark);
    }

    public void updateSupplier(long id, int personType, String name, String contact, String phone,
                               String startDate, double dailyWage, String remark) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_NAME, EncryptUtils.encrypt(name));
        v.put(DatabaseHelper.COLUMN_FIN_PERSON_TYPE, personType);
        v.put(DatabaseHelper.COLUMN_FIN_CONTACT, EncryptUtils.encrypt(contact));
        v.put(DatabaseHelper.COLUMN_FIN_PHONE, EncryptUtils.encrypt(phone));
        v.put(DatabaseHelper.COLUMN_FIN_START_DATE, startDate);
        v.put(DatabaseHelper.COLUMN_FIN_DAILY_WAGE, dailyWage);
        v.put(DatabaseHelper.COLUMN_REMARK, EncryptUtils.encrypt(remark));
        db().update(DatabaseHelper.TABLE_FIN_SUPPLIER, v, "id=?", new String[]{String.valueOf(id)});
    }

    public void setSupplierEnabled(long id, boolean enabled) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_ENABLED, enabled ? 1 : 0);
        db().update(DatabaseHelper.TABLE_FIN_SUPPLIER, v, "id=?", new String[]{String.valueOf(id)});
    }

    private FinSupplier parseSupplier(Cursor c) {
        FinSupplier s = new FinSupplier();
        s.id = c.getLong(0);
        s.personType = c.getInt(1);
        s.name = EncryptUtils.decrypt(c.getString(2));
        s.contact = EncryptUtils.decrypt(c.getString(3));
        s.phone = EncryptUtils.decrypt(c.getString(4));
        s.startDate = c.isNull(5) ? "" : c.getString(5);
        s.dailyWage = c.getDouble(6);
        s.enabled = c.getInt(7) == 1;
        s.remark = EncryptUtils.decrypt(c.getString(8));
        return s;
    }

    public List<FinSupplier> getSuppliers() {
        List<FinSupplier> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id,person_type,name,contact,phone,start_date,daily_wage,enabled,remark FROM "
                + DatabaseHelper.TABLE_FIN_SUPPLIER + " ORDER BY id DESC", null);
        while (c.moveToNext()) list.add(parseSupplier(c));
        c.close();
        return list;
    }

    public List<FinSupplier> getSuppliersByType(int personType) {
        List<FinSupplier> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id,person_type,name,contact,phone,start_date,daily_wage,enabled,remark FROM "
                + DatabaseHelper.TABLE_FIN_SUPPLIER + " WHERE " + DatabaseHelper.COLUMN_FIN_PERSON_TYPE
                + "=? AND " + DatabaseHelper.COLUMN_FIN_ENABLED + "=1 ORDER BY id DESC",
                new String[]{String.valueOf(personType)});
        while (c.moveToNext()) list.add(parseSupplier(c));
        c.close();
        return list;
    }

    public FinSupplier getSupplierById(long id) {
        Cursor c = db().rawQuery("SELECT id,person_type,name,contact,phone,start_date,daily_wage,enabled,remark FROM "
                + DatabaseHelper.TABLE_FIN_SUPPLIER + " WHERE id=?",
                new String[]{String.valueOf(id)});
        FinSupplier s = null;
        if (c.moveToFirst()) s = parseSupplier(c);
        c.close();
        return s;
    }

    // ==================== 供应商商品 ====================
    public static class FinSupplierProduct {
        public long id;
        public long supplierId;
        public long productId;
        public String productName;
        public String spec;
        public boolean enabled;
        public String standardName = "";
        public String unit = "";          // 基本单位：瓶 / 斤 / 毫升 …
        public double lastPrice = 0;
        public long categoryId;
        public double price = 0;          // 基本单位单价
        public String packUnit = "";      // 包装单位：箱 / 袋 / 瓶 …
        public double packQty = 1;        // 换算率：1箱 = 24瓶
        public String categoryName = "";

        // 包装规格文案：24瓶/箱
        public String packSpec() {
            if (packUnit.isEmpty()) return "";
            long q = (long) (packQty + 0.5);
            return q + unit + "/" + packUnit;
        }

        // 指定单位下的单价（包装单位单价 = 基本单位单价 × 换算率）
        public double priceOf(String u) {
            if (u != null && !u.isEmpty() && u.equals(packUnit)) {
                return FinanceDb.round2(price * packQty);
            }
            return price;
        }
    }

    // 内置单位下拉
    public static String[] getBuiltinUnits() {
        return DatabaseHelper.BUILTIN_UNITS;
    }

    public long insertSupplierProduct(long supplierId, long productId, String productName, String spec) {
        return insertSupplierProduct(supplierId, productId, productName, spec, 0, "", "", 0, 1);
    }

    public long insertSupplierProduct(long supplierId, long productId, String productName, String spec,
                                      long categoryId, String unit, String packUnit, double price, double packQty) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_ID, supplierId);
        v.put(DatabaseHelper.COLUMN_FIN_PRODUCT_ID, productId);
        v.put(DatabaseHelper.COLUMN_FIN_PRODUCT_NAME, EncryptUtils.encrypt(productName));
        v.put(DatabaseHelper.COLUMN_FIN_SPEC, EncryptUtils.encrypt(spec));
        v.put(DatabaseHelper.COLUMN_FIN_SP_CATEGORY_ID, categoryId);
        v.put(DatabaseHelper.COLUMN_FIN_SP_UNIT, EncryptUtils.encrypt(nonNull(unit)));
        v.put(DatabaseHelper.COLUMN_FIN_SP_PACK_UNIT, EncryptUtils.encrypt(nonNull(packUnit)));
        v.put(DatabaseHelper.COLUMN_FIN_SP_PACK_QTY, packQty <= 0 ? 1 : packQty);
        v.put(DatabaseHelper.COLUMN_FIN_SP_PRICE, price);
        v.put(DatabaseHelper.COLUMN_FIN_ENABLED, 1);
        v.put(DatabaseHelper.COLUMN_FIN_CREATE_TIME, System.currentTimeMillis());
        return db().insert(DatabaseHelper.TABLE_FIN_SUPPLIER_PRODUCT, null, v);
    }

    public void setSupplierProductEnabled(long id, boolean enabled) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_ENABLED, enabled ? 1 : 0);
        db().update(DatabaseHelper.TABLE_FIN_SUPPLIER_PRODUCT, v, "id=?", new String[]{String.valueOf(id)});
    }

    public void renameSupplierProduct(long id, String spec) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_SPEC, EncryptUtils.encrypt(spec));
        db().update(DatabaseHelper.TABLE_FIN_SUPPLIER_PRODUCT, v, "id=?", new String[]{String.valueOf(id)});
    }

    public void setSupplierProductCategory(long id, long categoryId) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_SP_CATEGORY_ID, categoryId);
        db().update(DatabaseHelper.TABLE_FIN_SUPPLIER_PRODUCT, v, "id=?", new String[]{String.valueOf(id)});
    }

    public void updateSupplierProduct(long id, String productName, String spec,
                                     String unit, String packUnit, double packQty, double price) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_PRODUCT_NAME, EncryptUtils.encrypt(nonNull(productName)));
        v.put(DatabaseHelper.COLUMN_FIN_SPEC, EncryptUtils.encrypt(nonNull(spec)));
        v.put(DatabaseHelper.COLUMN_FIN_SP_UNIT, EncryptUtils.encrypt(nonNull(unit)));
        v.put(DatabaseHelper.COLUMN_FIN_SP_PACK_UNIT, EncryptUtils.encrypt(nonNull(packUnit)));
        v.put(DatabaseHelper.COLUMN_FIN_SP_PACK_QTY, packQty <= 0 ? 1 : packQty);
        v.put(DatabaseHelper.COLUMN_FIN_SP_PRICE, price);
        db().update(DatabaseHelper.TABLE_FIN_SUPPLIER_PRODUCT, v, "id=?", new String[]{String.valueOf(id)});
    }

    private static final String SP_SELECT =
            "id,supplier_id,product_id,product_name,spec,enabled,"
                    + "sp_category_id,sp_unit,sp_price,sp_pack_unit,sp_pack_qty FROM "
                    + DatabaseHelper.TABLE_FIN_SUPPLIER_PRODUCT;

    private FinSupplierProduct readSupplierProduct(Cursor c) {
        FinSupplierProduct sp = new FinSupplierProduct();
        sp.id = c.getLong(0);
        sp.supplierId = c.getLong(1);
        sp.productId = c.getLong(2);
        sp.productName = nonNull(EncryptUtils.decrypt(c.getString(3)));
        sp.spec = nonNull(EncryptUtils.decrypt(c.getString(4)));
        sp.enabled = c.getInt(5) == 1;
        sp.categoryId = c.getLong(6);
        sp.unit = nonNull(EncryptUtils.decrypt(c.getString(7)));
        sp.price = c.getDouble(8);
        sp.packUnit = nonNull(EncryptUtils.decrypt(c.getString(9)));
        sp.packQty = c.isNull(10) ? 1 : c.getDouble(10);
        if (sp.packQty <= 0) sp.packQty = 1;
        if (sp.categoryId > 0) {
            Cursor cc = db().rawQuery("SELECT name FROM " + DatabaseHelper.TABLE_FIN_CATEGORY
                    + " WHERE id=?", new String[]{String.valueOf(sp.categoryId)});
            if (cc.moveToFirst()) sp.categoryName = nonNull(EncryptUtils.decrypt(cc.getString(0)));
            cc.close();
        }
        if (sp.productId > 0 && sp.unit.isEmpty()) {
            Cursor pc = db().rawQuery("SELECT name,unit FROM " + DatabaseHelper.TABLE_FIN_PRODUCT
                    + " WHERE id=?", new String[]{String.valueOf(sp.productId)});
            if (pc.moveToFirst()) {
                sp.standardName = nonNull(EncryptUtils.decrypt(pc.getString(0)));
                sp.unit = nonNull(EncryptUtils.decrypt(pc.getString(1)));
            }
            pc.close();
        }
        // 历史价：进货历史价（基本单位）→ 销售历史价 → 建档价
        double p1 = getLastPurchaseInfo(sp.id, sp.unit, 1).price;
        sp.lastPrice = p1 > 0 ? p1 : (getLastPrice(sp.id) > 0 ? getLastPrice(sp.id) : sp.price);
        return sp;
    }

    public FinSupplierProduct getSupplierProduct(long id) {
        Cursor c = db().rawQuery("SELECT " + SP_SELECT + " WHERE id=?",
                new String[]{String.valueOf(id)});
        FinSupplierProduct sp = null;
        if (c.moveToFirst()) sp = readSupplierProduct(c);
        c.close();
        return sp;
    }

    public List<FinSupplierProduct> getSupplierProducts(long supplierId) {
        List<FinSupplierProduct> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT " + SP_SELECT
                + " WHERE supplier_id=? ORDER BY sp_category_id, id DESC",
                new String[]{String.valueOf(supplierId)});
        while (c.moveToNext()) list.add(readSupplierProduct(c));
        c.close();
        return list;
    }

    // 进货开单三级联动用：某供应商在某品类下的启用商品
    public List<FinSupplierProduct> getSupplierProductsByCategory(long supplierId, long categoryId) {
        List<FinSupplierProduct> out = new ArrayList<>();
        for (FinSupplierProduct sp : getSupplierProducts(supplierId)) {
            if (sp.enabled && sp.categoryId == categoryId) out.add(sp);
        }
        return out;
    }

    // 供应商在某品类下是否已有同名商品（新建时防重复）
    public boolean supplierProductExists(long supplierId, long categoryId, String productName) {
        for (FinSupplierProduct sp : getSupplierProducts(supplierId)) {
            if (sp.categoryId == categoryId && sp.productName.equals(productName)) return true;
        }
        return false;
    }

    // ==================== 普通支出 ====================
    public static class FinExpense {
        public long id;
        public String batchId;
        public int category;
        public int subCategory;
        public double amount;
        public String date;
        public String remark;
    }

    public long insertExpense(String batchId, int category, int subCategory, double amount, String date, String remark) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_BATCH_ID, batchId);
        v.put(DatabaseHelper.COLUMN_FIN_CATEGORY, category);
        v.put(DatabaseHelper.COLUMN_FIN_SUB_CATEGORY, subCategory);
        v.put(DatabaseHelper.COLUMN_FIN_AMOUNT, amount);
        v.put(DatabaseHelper.COLUMN_FIN_DATE, date);
        v.put(DatabaseHelper.COLUMN_REMARK, EncryptUtils.encrypt(remark));
        v.put(DatabaseHelper.COLUMN_FIN_CREATE_TIME, System.currentTimeMillis());
        return db().insert(DatabaseHelper.TABLE_FIN_EXPENSE, null, v);
    }

    public void deleteExpense(long id) {
        db().delete(DatabaseHelper.TABLE_FIN_EXPENSE, "id=?", new String[]{String.valueOf(id)});
    }

    public List<FinExpense> getExpenses(String batchId, int category) {
        List<FinExpense> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id,batch_id,category,sub_category,amount,date,remark FROM "
                + DatabaseHelper.TABLE_FIN_EXPENSE + " WHERE batch_id=? AND category=? ORDER BY date, id",
                new String[]{batchId, String.valueOf(category)});
        while (c.moveToNext()) {
            FinExpense e = new FinExpense();
            e.id = c.getLong(0);
            e.batchId = c.getString(1);
            e.category = c.getInt(2);
            e.subCategory = c.getInt(3);
            e.amount = c.getDouble(4);
            e.date = c.getString(5);
            e.remark = EncryptUtils.decrypt(c.getString(6));
            list.add(e);
        }
        c.close();
        return list;
    }

    public double sumExpense(String batchId, Integer category, Integer subCategory, String start, String end) {
        StringBuilder sql = new StringBuilder("SELECT IFNULL(SUM(amount),0) FROM " + DatabaseHelper.TABLE_FIN_EXPENSE
                + " WHERE batch_id=?");
        List<String> args = new ArrayList<>();
        args.add(batchId);
        if (category != null) { sql.append(" AND category=?"); args.add(String.valueOf(category)); }
        if (subCategory != null) { sql.append(" AND sub_category=?"); args.add(String.valueOf(subCategory)); }
        if (start != null) { sql.append(" AND date>=?"); args.add(start); }
        if (end != null) { sql.append(" AND date<=?"); args.add(end); }
        Cursor c = db().rawQuery(sql.toString(), args.toArray(new String[0]));
        double sum = 0;
        if (c.moveToFirst()) sum = c.getDouble(0);
        c.close();
        return sum;
    }

    // ==================== 苗种购买 ====================
    public static class FinSeed {
        public long id;
        public String batchId;
        public long supplierId;
        public String date;
        public double quantity;
        public double unitPrice;
        public double totalAmount;
        public double actualAmount;
        public String remark;
        public String supplierName = "";
    }

    public long insertSeed(String batchId, long supplierId, String date, double quantity, double unitPrice,
                           double actualAmount, String remark) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_BATCH_ID, batchId);
        v.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_ID, supplierId);
        v.put(DatabaseHelper.COLUMN_FIN_DATE, date);
        v.put(DatabaseHelper.COLUMN_FIN_QUANTITY, quantity);
        v.put(DatabaseHelper.COLUMN_FIN_UNIT_PRICE, unitPrice);
        v.put(DatabaseHelper.COLUMN_FIN_TOTAL_AMOUNT, round2(quantity * unitPrice));
        v.put(DatabaseHelper.COLUMN_FIN_ACTUAL_AMOUNT, actualAmount);
        v.put(DatabaseHelper.COLUMN_REMARK, EncryptUtils.encrypt(remark));
        v.put(DatabaseHelper.COLUMN_FIN_CREATE_TIME, System.currentTimeMillis());
        return db().insert(DatabaseHelper.TABLE_FIN_SEED, null, v);
    }

    public void updateSeed(FinSeed s) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_ID, s.supplierId);
        v.put(DatabaseHelper.COLUMN_FIN_DATE, s.date);
        v.put(DatabaseHelper.COLUMN_FIN_QUANTITY, s.quantity);
        v.put(DatabaseHelper.COLUMN_FIN_UNIT_PRICE, s.unitPrice);
        v.put(DatabaseHelper.COLUMN_FIN_TOTAL_AMOUNT, round2(s.quantity * s.unitPrice));
        v.put(DatabaseHelper.COLUMN_FIN_ACTUAL_AMOUNT, s.actualAmount);
        v.put(DatabaseHelper.COLUMN_REMARK, EncryptUtils.encrypt(s.remark));
        db().update(DatabaseHelper.TABLE_FIN_SEED, v, "id=?", new String[]{String.valueOf(s.id)});
    }

    public void deleteSeed(long id) {
        db().delete(DatabaseHelper.TABLE_FIN_SEED, "id=?", new String[]{String.valueOf(id)});
    }

    public List<FinSeed> getSeeds(String batchId) {
        List<FinSeed> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id,supplier_id,date,quantity,unit_price,total_amount,actual_amount,remark FROM "
                + DatabaseHelper.TABLE_FIN_SEED + " WHERE batch_id=? ORDER BY date, id",
                new String[]{batchId});
        while (c.moveToNext()) {
            FinSeed s = new FinSeed();
            s.id = c.getLong(0);
            s.batchId = batchId;
            s.supplierId = c.getLong(1);
            s.date = c.getString(2);
            s.quantity = c.getDouble(3);
            s.unitPrice = c.getDouble(4);
            s.totalAmount = c.getDouble(5);
            s.actualAmount = c.getDouble(6);
            s.remark = EncryptUtils.decrypt(c.getString(7));
            list.add(s);
        }
        c.close();
        for (FinSeed s : list) {
            Cursor sc = db().rawQuery("SELECT name FROM " + DatabaseHelper.TABLE_FIN_SUPPLIER
                    + " WHERE id=?", new String[]{String.valueOf(s.supplierId)});
            if (sc.moveToFirst()) s.supplierName = EncryptUtils.decrypt(sc.getString(0));
            sc.close();
        }
        return list;
    }

    public double sumSeed(String batchId, String start, String end) {
        StringBuilder sql = new StringBuilder("SELECT IFNULL(SUM(actual_amount),0) FROM "
                + DatabaseHelper.TABLE_FIN_SEED + " WHERE batch_id=?");
        List<String> args = new ArrayList<>();
        args.add(batchId);
        if (start != null) { sql.append(" AND date>=?"); args.add(start); }
        if (end != null) { sql.append(" AND date<=?"); args.add(end); }
        Cursor c = db().rawQuery(sql.toString(), args.toArray(new String[0]));
        double sum = 0;
        if (c.moveToFirst()) sum = c.getDouble(0);
        c.close();
        return sum;
    }

    // ==================== 工资发放 ====================
    public static class FinWage {
        public long id;
        public String batchId;
        public long employeeId;
        public String payee;
        public double amount;
        public String date;
        public String remark;
    }

    public long insertWage(String batchId, long employeeId, String payee, double amount, String date, String remark) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_BATCH_ID, batchId);
        v.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_ID, employeeId);
        v.put(DatabaseHelper.COLUMN_FIN_PAYEE, EncryptUtils.encrypt(payee));
        v.put(DatabaseHelper.COLUMN_FIN_AMOUNT, amount);
        v.put(DatabaseHelper.COLUMN_FIN_DATE, date);
        v.put(DatabaseHelper.COLUMN_REMARK, EncryptUtils.encrypt(remark));
        v.put(DatabaseHelper.COLUMN_FIN_CREATE_TIME, System.currentTimeMillis());
        return db().insert(DatabaseHelper.TABLE_FIN_WAGE, null, v);
    }

    public void updateWage(FinWage w) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_ID, w.employeeId);
        v.put(DatabaseHelper.COLUMN_FIN_PAYEE, EncryptUtils.encrypt(w.payee));
        v.put(DatabaseHelper.COLUMN_FIN_AMOUNT, w.amount);
        v.put(DatabaseHelper.COLUMN_FIN_DATE, w.date);
        v.put(DatabaseHelper.COLUMN_REMARK, EncryptUtils.encrypt(w.remark));
        db().update(DatabaseHelper.TABLE_FIN_WAGE, v, "id=?", new String[]{String.valueOf(w.id)});
    }

    public void deleteWage(long id) {
        db().delete(DatabaseHelper.TABLE_FIN_WAGE, "id=?", new String[]{String.valueOf(id)});
    }

    public List<FinWage> getWages(String batchId) {
        List<FinWage> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id,supplier_id,payee,amount,date,remark FROM "
                + DatabaseHelper.TABLE_FIN_WAGE + " WHERE batch_id=? ORDER BY date, id",
                new String[]{batchId});
        while (c.moveToNext()) {
            FinWage w = new FinWage();
            w.id = c.getLong(0);
            w.batchId = batchId;
            w.employeeId = c.getLong(1);
            w.payee = EncryptUtils.decrypt(c.getString(2));
            w.amount = c.getDouble(3);
            w.date = c.getString(4);
            w.remark = EncryptUtils.decrypt(c.getString(5));
            list.add(w);
        }
        c.close();
        return list;
    }

    public double sumWage(String batchId, String start, String end) {
        StringBuilder sql = new StringBuilder("SELECT IFNULL(SUM(amount),0) FROM "
                + DatabaseHelper.TABLE_FIN_WAGE + " WHERE batch_id=?");
        List<String> args = new ArrayList<>();
        args.add(batchId);
        if (start != null) { sql.append(" AND date>=?"); args.add(start); }
        if (end != null) { sql.append(" AND date<=?"); args.add(end); }
        Cursor c = db().rawQuery(sql.toString(), args.toArray(new String[0]));
        double sum = 0;
        if (c.moveToFirst()) sum = c.getDouble(0);
        c.close();
        return sum;
    }

    public List<FinWage> searchWages(String keyword) {
        List<FinWage> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id,batch_id,supplier_id,payee,amount,date,remark FROM "
                + DatabaseHelper.TABLE_FIN_WAGE + " ORDER BY date DESC, id DESC", null);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        while (c.moveToNext()) {
            FinWage w = new FinWage();
            w.id = c.getLong(0);
            w.batchId = c.getString(1);
            w.employeeId = c.getLong(2);
            w.payee = EncryptUtils.decrypt(c.getString(3));
            w.amount = c.getDouble(4);
            w.date = c.getString(5);
            w.remark = EncryptUtils.decrypt(c.getString(6));
            String hay = (w.payee + " " + w.remark).toLowerCase();
            if (kw.isEmpty() || hay.contains(kw)) list.add(w);
        }
        c.close();
        return list;
    }

    public double sumWageByEmployee(long employeeId) {
        Cursor c = db().rawQuery("SELECT IFNULL(SUM(amount),0) FROM " + DatabaseHelper.TABLE_FIN_WAGE
                + " WHERE " + DatabaseHelper.COLUMN_FIN_SUPPLIER_ID + "=?",
                new String[]{String.valueOf(employeeId)});
        double sum = 0;
        if (c.moveToFirst()) sum = c.getDouble(0);
        c.close();
        return round2(sum);
    }

    // ==================== 员工考勤（休息日） ====================
    public List<String> getRestDays(long employeeId) {
        List<String> dates = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT " + DatabaseHelper.COLUMN_FIN_REST_DATE + " FROM "
                + DatabaseHelper.TABLE_FIN_REST + " WHERE " + DatabaseHelper.COLUMN_FIN_SUPPLIER_ID
                + "=? ORDER BY " + DatabaseHelper.COLUMN_FIN_REST_DATE,
                new String[]{String.valueOf(employeeId)});
        while (c.moveToNext()) dates.add(c.getString(0));
        c.close();
        return dates;
    }

    public void setRestDays(long employeeId, List<String> dates) {
        db().delete(DatabaseHelper.TABLE_FIN_REST, DatabaseHelper.COLUMN_FIN_SUPPLIER_ID + "=?",
                new String[]{String.valueOf(employeeId)});
        for (String d : dates) {
            ContentValues v = new ContentValues();
            v.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_ID, employeeId);
            v.put(DatabaseHelper.COLUMN_FIN_REST_DATE, d);
            v.put(DatabaseHelper.COLUMN_FIN_CREATE_TIME, System.currentTimeMillis());
            db().insertWithOnConflict(DatabaseHelper.TABLE_FIN_REST, null, v,
                    SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    public static int countWorkingDays(String startDate, String endDate, java.util.Set<String> rests) {
        try {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.CHINA);
            java.util.Calendar cal = java.util.Calendar.getInstance(java.util.Locale.CHINA);
            cal.setTime(fmt.parse(startDate));
            long endTime = fmt.parse(endDate).getTime();
            int days = 0;
            int guard = 0;
            while (cal.getTimeInMillis() <= endTime && guard < 4000) {
                if (!rests.contains(fmt.format(cal.getTime()))) days++;
                cal.add(java.util.Calendar.DATE, 1);
                guard++;
            }
            return days;
        } catch (Exception e) {
            return 0;
        }
    }

    // 应得工资：从上班日起到 endDate，出勤天数 × 工价
    public double calcDueWage(long employeeId, String endDate) {
        FinSupplier emp = getSupplierById(employeeId);
        if (emp == null || emp.dailyWage <= 0 || emp.startDate == null || emp.startDate.isEmpty()) return 0;
        int days = countWorkingDays(emp.startDate, endDate, new java.util.HashSet<>(getRestDays(employeeId)));
        return round2(days * emp.dailyWage);
    }

    // ==================== 请假（半天粒度） ====================
    public static class FinLeave {
        public long id;
        public String date;
        public int amType;
        public int pmType;
        public String remark;
    }

    public static class LeaveSummary {
        public double personalDays = 0;
        public double sickDays = 0;
        public double absentDays = 0;
    }

    public void saveLeave(long employeeId, String date, int amType, int pmType, String remark) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_ID, employeeId);
        v.put(DatabaseHelper.COLUMN_FIN_DATE, date);
        v.put(DatabaseHelper.COLUMN_FIN_AM_TYPE, amType);
        v.put(DatabaseHelper.COLUMN_FIN_PM_TYPE, pmType);
        v.put(DatabaseHelper.COLUMN_REMARK, EncryptUtils.encrypt(remark));
        db().insertWithOnConflict(DatabaseHelper.TABLE_FIN_LEAVE, null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public FinLeave getLeave(long employeeId, String date) {
        Cursor c = db().rawQuery("SELECT id,date,am_type,pm_type,remark FROM "
                + DatabaseHelper.TABLE_FIN_LEAVE + " WHERE " + DatabaseHelper.COLUMN_FIN_SUPPLIER_ID
                + "=? AND " + DatabaseHelper.COLUMN_FIN_DATE + "=?",
                new String[]{String.valueOf(employeeId), date});
        FinLeave l = null;
        if (c.moveToFirst()) {
            l = new FinLeave();
            l.id = c.getLong(0);
            l.date = c.getString(1);
            l.amType = c.getInt(2);
            l.pmType = c.getInt(3);
            l.remark = EncryptUtils.decrypt(c.getString(4));
        }
        c.close();
        return l;
    }

    public List<FinLeave> getLeavesByMonth(long employeeId, String yearMonth) {
        List<FinLeave> list = new ArrayList<>();
        String prefix = yearMonth.replace('-', '/') + "/%";
        Cursor c = db().rawQuery("SELECT id,date,am_type,pm_type,remark FROM "
                + DatabaseHelper.TABLE_FIN_LEAVE + " WHERE " + DatabaseHelper.COLUMN_FIN_SUPPLIER_ID
                + "=? AND " + DatabaseHelper.COLUMN_FIN_DATE + " LIKE ?"
                + " ORDER BY " + DatabaseHelper.COLUMN_FIN_DATE,
                new String[]{String.valueOf(employeeId), prefix});
        while (c.moveToNext()) {
            FinLeave l = new FinLeave();
            l.id = c.getLong(0);
            l.date = c.getString(1);
            l.amType = c.getInt(2);
            l.pmType = c.getInt(3);
            l.remark = EncryptUtils.decrypt(c.getString(4));
            list.add(l);
        }
        c.close();
        return list;
    }

    public void deleteLeave(long employeeId, String date) {
        db().delete(DatabaseHelper.TABLE_FIN_LEAVE,
                DatabaseHelper.COLUMN_FIN_SUPPLIER_ID + "=? AND " + DatabaseHelper.COLUMN_FIN_DATE + "=?",
                new String[]{String.valueOf(employeeId), date});
    }

    // 汇总某员工某月各类型请假天数，半天计 0.5
    public LeaveSummary sumLeaveByMonth(long employeeId, String yearMonth) {
        LeaveSummary sum = new LeaveSummary();
        for (FinLeave l : getLeavesByMonth(employeeId, yearMonth)) {
            double am = l.amType >= 1 ? 0.5 : 0;
            double pm = l.pmType >= 1 ? 0.5 : 0;
            if (l.amType == 1 || l.pmType == 1) sum.personalDays += (l.amType == 1 ? am : 0) + (l.pmType == 1 ? pm : 0);
            if (l.amType == 2 || l.pmType == 2) sum.sickDays += (l.amType == 2 ? am : 0) + (l.pmType == 2 ? pm : 0);
            if (l.amType == 3 || l.pmType == 3) sum.absentDays += (l.amType == 3 ? am : 0) + (l.pmType == 3 ? pm : 0);
        }
        return sum;
    }

    // ==================== 工资单（按月） ====================
    public static class FinAdvance {
        public long id;
        public String date;
        public double amount;
        public double settledAmount;
        public boolean settled;
        public String remark;
    }

    public static class FinSalary {
        public long id;
        public long employeeId;
        public String employeeName;
        public int year;
        public int month;
        public double workDays;
        public double personalDays;
        public double sickDays;
        public double absentDays;
        public double baseSalary;
        public double bonus;
        public double deduction;
        public double actualSalary;
        public boolean paid;
        public String payDate;
        public long accountId;
        public String accountName;
        public long receiveAccountId;
        public String receiveAccountName;
        public String remark;
        public double advance;
    }

    private static String yearMonth(int year, int month) {
        return String.format(Locale.CHINA, "%d-%02d", year, month);
    }

    private static int daysInMonth(int year, int month) {
        java.util.Calendar cal = java.util.Calendar.getInstance(Locale.CHINA);
        cal.set(year, month - 1, 1);
        return cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
    }

    private static String intToDate(int year, int month, int day) {
        return String.format(Locale.CHINA, "%04d/%02d/%02d", year, month, day);
    }

    // 生成某员工某月工资单（日薪模型：应发 = (出勤 + 病假×0.5) × 日薪）
    public FinSalary generateSalary(long employeeId, int year, int month) {
        FinSupplier emp = getSupplierById(employeeId);
        if (emp == null || emp.dailyWage <= 0) return null;
        String ym = yearMonth(year, month);
        LeaveSummary leave = sumLeaveByMonth(employeeId, ym);

        // 应勤天数：从入职日起到月末，扣除休息日
        String start = emp.startDate;
        String end = intToDate(year, month, daysInMonth(year, month));
        int totalWork = 0;
        if (start != null && !start.isEmpty() && start.compareTo(end) <= 0) {
            String actualStart = start.compareTo(intToDate(year, month, 1)) >= 0 ? start : intToDate(year, month, 1);
            totalWork = countWorkingDays(actualStart, end, new java.util.HashSet<>(getRestDays(employeeId)));
        }

        double workDays = round2(totalWork - leave.personalDays - leave.sickDays - leave.absentDays);
        double base = round2((workDays + leave.sickDays * 0.5) * emp.dailyWage);
        double deduction = 0;
        double bonus = 0;
        double actual = round2(base + bonus - deduction);

        FinSalary s = new FinSalary();
        s.employeeId = employeeId;
        s.employeeName = emp.name;
        s.year = year;
        s.month = month;
        s.workDays = workDays;
        s.personalDays = leave.personalDays;
        s.sickDays = leave.sickDays;
        s.absentDays = leave.absentDays;
        s.baseSalary = base;
        s.bonus = bonus;
        s.deduction = deduction;
        s.actualSalary = actual;
        s.remark = "";

        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_ID, employeeId);
        v.put(DatabaseHelper.COLUMN_FIN_YEAR, year);
        v.put(DatabaseHelper.COLUMN_FIN_MONTH, month);
        v.put(DatabaseHelper.COLUMN_FIN_WORK_DAYS, workDays);
        v.put(DatabaseHelper.COLUMN_FIN_PERSONAL_DAYS, leave.personalDays);
        v.put(DatabaseHelper.COLUMN_FIN_SICK_DAYS, leave.sickDays);
        v.put(DatabaseHelper.COLUMN_FIN_ABSENT_DAYS, leave.absentDays);
        v.put(DatabaseHelper.COLUMN_FIN_BASE_SALARY, base);
        v.put(DatabaseHelper.COLUMN_FIN_BONUS, bonus);
        v.put(DatabaseHelper.COLUMN_FIN_DEDUCTION, deduction);
        v.put(DatabaseHelper.COLUMN_FIN_ACTUAL_SALARY, actual);
        v.put(DatabaseHelper.COLUMN_FIN_PAID, 0);
        v.put(DatabaseHelper.COLUMN_REMARK, EncryptUtils.encrypt(""));
        v.put(DatabaseHelper.COLUMN_FIN_CREATE_TIME, System.currentTimeMillis());
        long id = db().insertWithOnConflict(DatabaseHelper.TABLE_FIN_SALARY, null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
        s.id = id;
        return s;
    }

    public List<FinSalary> getSalaries(int year, int month) {
        List<FinSalary> list = new ArrayList<>();
        String sql = "SELECT s.id,s.supplier_id,s.year,s.month,s.work_days,s.personal_days,s.sick_days,"
                + "s.absent_days,s.base_salary,s.bonus,s.deduction,s.actual_salary,s.paid,s.pay_date,"
                + "s.account_id,s.remark,u.name,a.name,s.receive_account_id,rc.account_name,"
                + "(SELECT COALESCE(SUM(ad.amount - ad.settled_amount),0) FROM "
                + DatabaseHelper.TABLE_FIN_ADVANCE
                + " ad WHERE ad.supplier_id=s.supplier_id AND ad.settled=0) FROM "
                + DatabaseHelper.TABLE_FIN_SALARY
                + " s LEFT JOIN " + DatabaseHelper.TABLE_FIN_SUPPLIER
                + " u ON u.id=s.supplier_id LEFT JOIN " + DatabaseHelper.TABLE_FIN_PAYMENT_ACCOUNT
                + " a ON a.id=s.account_id LEFT JOIN " + DatabaseHelper.TABLE_FIN_CONTACT_ACCOUNT
                + " rc ON rc.id=s.receive_account_id WHERE s.year=? AND s.month=? ORDER BY u.id DESC";
        Cursor c = db().rawQuery(sql, new String[]{String.valueOf(year), String.valueOf(month)});
        while (c.moveToNext()) {
            FinSalary s = new FinSalary();
            s.id = c.getLong(0);
            s.employeeId = c.getLong(1);
            s.year = c.getInt(2);
            s.month = c.getInt(3);
            s.workDays = c.getDouble(4);
            s.personalDays = c.getDouble(5);
            s.sickDays = c.getDouble(6);
            s.absentDays = c.getDouble(7);
            s.baseSalary = c.getDouble(8);
            s.bonus = c.getDouble(9);
            s.deduction = c.getDouble(10);
            s.actualSalary = c.getDouble(11);
            s.paid = c.getInt(12) == 1;
            s.payDate = c.isNull(13) ? "" : c.getString(13);
            s.accountId = c.getLong(14);
            s.remark = c.isNull(15) ? "" : EncryptUtils.decrypt(c.getString(15));
            s.employeeName = c.isNull(16) ? "" : EncryptUtils.decrypt(c.getString(16));
            s.accountName = c.isNull(17) ? "" : EncryptUtils.decrypt(c.getString(17));
            s.receiveAccountId = c.getLong(18);
            s.receiveAccountName = c.isNull(19) ? "" : EncryptUtils.decrypt(c.getString(19));
            s.advance = c.getDouble(20);
            list.add(s);
        }
        c.close();
        return list;
    }

    // 员工借款总额（未抵扣部分）
    public double sumOutstandingAdvance(long employeeId) {
        double sum = 0;
        Cursor c = db().rawQuery("SELECT COALESCE(SUM(" + DatabaseHelper.COLUMN_FIN_AMOUNT
                        + " - " + DatabaseHelper.COLUMN_FIN_SETTLED_AMOUNT + "),0) FROM "
                        + DatabaseHelper.TABLE_FIN_ADVANCE + " WHERE "
                        + DatabaseHelper.COLUMN_FIN_SUPPLIER_ID + "=? AND "
                        + DatabaseHelper.COLUMN_FIN_SETTLED + "=0",
                new String[]{String.valueOf(employeeId)});
        if (c.moveToFirst()) sum = c.getDouble(0);
        c.close();
        return sum;
    }

    // 记录一笔员工借款/预支
    public void recordAdvance(long employeeId, String date, double amount, String remark, String batchId) {
        if (amount <= 0) return;
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_BATCH_ID, batchId == null ? "" : batchId);
        v.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_ID, employeeId);
        v.put(DatabaseHelper.COLUMN_FIN_DATE, date);
        v.put(DatabaseHelper.COLUMN_FIN_AMOUNT, round2(amount));
        v.put(DatabaseHelper.COLUMN_FIN_SETTLED_AMOUNT, 0);
        v.put(DatabaseHelper.COLUMN_FIN_SETTLED, 0);
        v.put(DatabaseHelper.COLUMN_REMARK, EncryptUtils.encrypt(remark));
        v.put(DatabaseHelper.COLUMN_FIN_CREATE_TIME, System.currentTimeMillis());
        db().insert(DatabaseHelper.TABLE_FIN_ADVANCE, null, v);
    }

    public List<FinAdvance> getAdvances(long employeeId) {
        List<FinAdvance> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id,date,amount,settled_amount,settled,remark FROM "
                        + DatabaseHelper.TABLE_FIN_ADVANCE + " WHERE "
                        + DatabaseHelper.COLUMN_FIN_SUPPLIER_ID + "=? ORDER BY "
                        + DatabaseHelper.COLUMN_FIN_DATE + ",id",
                new String[]{String.valueOf(employeeId)});
        while (c.moveToNext()) {
            FinAdvance a = new FinAdvance();
            a.id = c.getLong(0);
            a.date = c.getString(1);
            a.amount = c.getDouble(2);
            a.settledAmount = c.getDouble(3);
            a.settled = c.getInt(4) == 1;
            a.remark = c.isNull(5) ? "" : EncryptUtils.decrypt(c.getString(5));
            list.add(a);
        }
        c.close();
        return list;
    }

    // 按借款时间从旧到新抵扣 amount 元，最后一笔可部分抵扣
    public void settleAdvances(long employeeId, double amount) {
        if (amount <= 0) return;
        double remain = amount;
        Cursor c = db().rawQuery("SELECT id,amount,settled_amount FROM "
                        + DatabaseHelper.TABLE_FIN_ADVANCE + " WHERE "
                        + DatabaseHelper.COLUMN_FIN_SUPPLIER_ID + "=? AND "
                        + DatabaseHelper.COLUMN_FIN_SETTLED + "=0 ORDER BY "
                        + DatabaseHelper.COLUMN_FIN_DATE + ",id",
                new String[]{String.valueOf(employeeId)});
        List<long[]> rows = new ArrayList<>();
        while (c.moveToNext()) {
            rows.add(new long[]{c.getLong(0), Math.round(c.getDouble(1) * 100),
                    Math.round(c.getDouble(2) * 100)});
        }
        c.close();
        if (rows.isEmpty()) return;
        for (long[] r : rows) {
            if (remain <= 0) break;
            long open = r[1] - r[2];
            if (open <= 0) continue;
            long take = Math.min(open, Math.round(remain * 100));
            long newSettled = r[2] + take;
            ContentValues v = new ContentValues();
            v.put(DatabaseHelper.COLUMN_FIN_SETTLED_AMOUNT, newSettled / 100.0);
            v.put(DatabaseHelper.COLUMN_FIN_SETTLED, newSettled >= r[1] ? 1 : 0);
            db().update(DatabaseHelper.TABLE_FIN_ADVANCE, v, "id=?",
                    new String[]{String.valueOf(r[0])});
            remain -= take / 100.0;
        }
    }

    // 发放工资：扣员工未结借款，实发 = 应发 − 借款；net>0 写 fin_wage（计入利润），不足部分借款继续挂账
    public void paySalary(long salaryId, String batchId, long accountId, long receiveAccountId, String payDate) {
        FinSalary s = null;
        Cursor c = db().rawQuery("SELECT id,supplier_id,actual_salary FROM "
                + DatabaseHelper.TABLE_FIN_SALARY + " WHERE id=?", new String[]{String.valueOf(salaryId)});
        if (c.moveToFirst()) {
            s = new FinSalary();
            s.id = c.getLong(0);
            s.employeeId = c.getLong(1);
            s.actualSalary = c.getDouble(2);
        }
        c.close();
        if (s == null) return;

        double outstanding = sumOutstandingAdvance(s.employeeId);
        double deduct = round2(Math.min(s.actualSalary, outstanding));
        double net = round2(s.actualSalary - deduct);

        SQLiteDatabase db = db();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put(DatabaseHelper.COLUMN_FIN_PAID, 1);
            v.put(DatabaseHelper.COLUMN_FIN_PAY_DATE, payDate);
            v.put(DatabaseHelper.COLUMN_FIN_ACCOUNT_ID, accountId);
            v.put(DatabaseHelper.COLUMN_FIN_RECEIVE_ACCOUNT_ID, receiveAccountId);
            v.put(DatabaseHelper.COLUMN_FIN_DEDUCTION, deduct);
            v.put(DatabaseHelper.COLUMN_FIN_ACTUAL_SALARY, net);
            db.update(DatabaseHelper.TABLE_FIN_SALARY, v, "id=?", new String[]{String.valueOf(s.id)});

            if (deduct > 0) settleAdvances(s.employeeId, deduct);

            if (net > 0) {
                FinSupplier emp = getSupplierById(s.employeeId);
                String payee = emp == null ? "" : emp.name;
                ContentValues w = new ContentValues();
                w.put(DatabaseHelper.COLUMN_BATCH_ID, batchId == null ? "" : batchId);
                w.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_ID, s.employeeId);
                w.put(DatabaseHelper.COLUMN_FIN_PAYEE, EncryptUtils.encrypt(payee));
                w.put(DatabaseHelper.COLUMN_FIN_AMOUNT, net);
                w.put(DatabaseHelper.COLUMN_FIN_DATE, payDate);
                w.put(DatabaseHelper.COLUMN_FIN_RECEIVE_ACCOUNT_ID, receiveAccountId);
                w.put(DatabaseHelper.COLUMN_REMARK, EncryptUtils.encrypt("工资"));
                w.put(DatabaseHelper.COLUMN_FIN_CREATE_TIME, System.currentTimeMillis());
                db.insert(DatabaseHelper.TABLE_FIN_WAGE, null, w);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // ==================== 综合查询 ====================
    public List<FinSupplier> searchPersons(String keyword) {
        List<FinSupplier> all = getSuppliers();
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        List<FinSupplier> result = new ArrayList<>();
        for (FinSupplier s : all) {
            String hay = (s.name + " " + s.contact + " " + s.phone + " " + s.remark).toLowerCase();
            if (kw.isEmpty() || hay.contains(kw)) result.add(s);
        }
        return result;
    }

    public List<FinProduct> searchProducts(String keyword) {
        List<FinProduct> all = getProducts();
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        List<FinProduct> result = new ArrayList<>();
        for (FinProduct p : all) {
            if (!p.enabled) continue;
            if (kw.isEmpty() || p.name.toLowerCase().contains(kw)) result.add(p);
        }
        return result;
    }

    public List<String> getSupplierNamesByProduct(long productId) {
        List<String> names = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT s.name, sp.spec FROM " + DatabaseHelper.TABLE_FIN_SUPPLIER
                + " s INNER JOIN " + DatabaseHelper.TABLE_FIN_SUPPLIER_PRODUCT
                + " sp ON sp." + DatabaseHelper.COLUMN_FIN_SUPPLIER_ID + "=s.id"
                + " WHERE sp." + DatabaseHelper.COLUMN_FIN_PRODUCT_ID + "=? AND sp."
                + DatabaseHelper.COLUMN_FIN_ENABLED + "=1 AND s." + DatabaseHelper.COLUMN_FIN_ENABLED
                + "=1 ORDER BY s.id DESC", new String[]{String.valueOf(productId)});
        while (c.moveToNext()) {
            String spec = EncryptUtils.decrypt(c.getString(1));
            names.add(EncryptUtils.decrypt(c.getString(0)) + (spec.isEmpty() ? "" : "(" + spec + ")"));
        }
        c.close();
        return names;
    }

    // ==================== 动保票据 + 明细 ====================
    public static class FinTicketItem {
        public long id;
        public long supplierProductId;
        public double quantity;
        public double unitPrice;
        public double amount;
        public String productName = "";
        public String spec = "";
        public String unit = "";
    }

    public static class FinTicket {
        public long id;
        public String batchId;
        public long supplierId;
        public String date;
        public String ticketNo;
        public double totalAmount;
        public int settleType = 0;
        public boolean settled;
        /** 付款账户（fin_payment_account.id），0 = 未指定 */
        public long accountId;
        public String remark;
        public String supplierName = "";
        public List<FinTicketItem> items = new ArrayList<>();
    }

    public long saveTicket(FinTicket t, List<FinTicketItem> items) {
        SQLiteDatabase db = db();
        db.beginTransaction();
        try {
            double total = 0;
            for (FinTicketItem it : items) {
                it.amount = it.quantity * it.unitPrice;
                total += it.amount;
            }
            ContentValues v = new ContentValues();
            v.put(DatabaseHelper.COLUMN_BATCH_ID, t.batchId);
            v.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_ID, t.supplierId);
            v.put(DatabaseHelper.COLUMN_FIN_DATE, t.date);
            v.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_NO, EncryptUtils.encrypt(t.ticketNo));
            v.put(DatabaseHelper.COLUMN_FIN_TOTAL_AMOUNT, total);
v.put(DatabaseHelper.COLUMN_FIN_SETTLE_TYPE, t.settled ? 0 : 1);
        v.put(DatabaseHelper.COLUMN_FIN_SETTLED, t.settled ? 1 : 0);
        v.put(DatabaseHelper.COLUMN_FIN_ACCOUNT_ID, t.accountId);
            v.put(DatabaseHelper.COLUMN_REMARK, EncryptUtils.encrypt(t.remark));
            v.put(DatabaseHelper.COLUMN_FIN_CREATE_TIME, System.currentTimeMillis());
            long id = db.insert(DatabaseHelper.TABLE_FIN_TICKET, null, v);
            for (FinTicketItem it : items) {
                ContentValues iv = new ContentValues();
                iv.put(DatabaseHelper.COLUMN_FIN_TICKET_ID, id);
                iv.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_PRODUCT_ID, it.supplierProductId);
                iv.put(DatabaseHelper.COLUMN_FIN_QUANTITY, it.quantity);
                iv.put(DatabaseHelper.COLUMN_FIN_UNIT_PRICE, it.unitPrice);
                iv.put(DatabaseHelper.COLUMN_FIN_AMOUNT, it.amount);
                db.insert(DatabaseHelper.TABLE_FIN_TICKET_ITEM, null, iv);
            }
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    public void updateTicket(FinTicket t, List<FinTicketItem> items) {
        SQLiteDatabase db = db();
        db.beginTransaction();
        try {
            double total = 0;
            for (FinTicketItem it : items) {
                it.amount = it.quantity * it.unitPrice;
                total += it.amount;
            }
            ContentValues v = new ContentValues();
            v.put(DatabaseHelper.COLUMN_BATCH_ID, t.batchId);
            v.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_ID, t.supplierId);
            v.put(DatabaseHelper.COLUMN_FIN_DATE, t.date);
            v.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_NO, EncryptUtils.encrypt(t.ticketNo));
            v.put(DatabaseHelper.COLUMN_FIN_TOTAL_AMOUNT, total);
v.put(DatabaseHelper.COLUMN_FIN_SETTLE_TYPE, t.settled ? 0 : 1);
        v.put(DatabaseHelper.COLUMN_FIN_SETTLED, t.settled ? 1 : 0);
        v.put(DatabaseHelper.COLUMN_FIN_ACCOUNT_ID, t.accountId);
            v.put(DatabaseHelper.COLUMN_REMARK, EncryptUtils.encrypt(t.remark));
            db.update(DatabaseHelper.TABLE_FIN_TICKET, v, "id=?", new String[]{String.valueOf(t.id)});
            db.delete(DatabaseHelper.TABLE_FIN_TICKET_ITEM, DatabaseHelper.COLUMN_FIN_TICKET_ID + "=?",
                    new String[]{String.valueOf(t.id)});
            for (FinTicketItem it : items) {
                ContentValues iv = new ContentValues();
                iv.put(DatabaseHelper.COLUMN_FIN_TICKET_ID, t.id);
                iv.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_PRODUCT_ID, it.supplierProductId);
                iv.put(DatabaseHelper.COLUMN_FIN_QUANTITY, it.quantity);
                iv.put(DatabaseHelper.COLUMN_FIN_UNIT_PRICE, it.unitPrice);
                iv.put(DatabaseHelper.COLUMN_FIN_AMOUNT, it.amount);
                db.insert(DatabaseHelper.TABLE_FIN_TICKET_ITEM, null, iv);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void deleteTicket(long id) {
        db().delete(DatabaseHelper.TABLE_FIN_TICKET_ITEM, DatabaseHelper.COLUMN_FIN_TICKET_ID + "=?",
                new String[]{String.valueOf(id)});
        db().delete(DatabaseHelper.TABLE_FIN_TICKET, "id=?", new String[]{String.valueOf(id)});
    }

    public List<FinTicket> getTickets(String batchId) {
        List<FinTicket> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id,supplier_id,date,supplier_no,total_amount,settle_type,settled,account_id,remark FROM "
                + DatabaseHelper.TABLE_FIN_TICKET + " WHERE batch_id=? ORDER BY date DESC, id DESC",
                new String[]{batchId});
        while (c.moveToNext()) {
            FinTicket t = new FinTicket();
            t.id = c.getLong(0);
            t.batchId = batchId;
            t.supplierId = c.getLong(1);
            t.date = c.getString(2);
            t.ticketNo = EncryptUtils.decrypt(c.getString(3));
            t.totalAmount = c.getDouble(4);
            t.settleType = c.getInt(5);
            t.settled = c.getInt(6) == 1;
            t.accountId = c.getLong(7);
            t.remark = EncryptUtils.decrypt(c.getString(8));
            list.add(t);
        }
        c.close();
        for (FinTicket t : list) {
            Cursor sc = db().rawQuery("SELECT name FROM " + DatabaseHelper.TABLE_FIN_SUPPLIER
                    + " WHERE id=?", new String[]{String.valueOf(t.supplierId)});
            if (sc.moveToFirst()) t.supplierName = EncryptUtils.decrypt(sc.getString(0));
            sc.close();
        }
        return list;
    }

    public FinTicket getTicket(long id) {
        FinTicket t = new FinTicket();
        Cursor c = db().rawQuery("SELECT batch_id,supplier_id,date,supplier_no,total_amount,settle_type,settled,account_id,remark FROM "
                + DatabaseHelper.TABLE_FIN_TICKET + " WHERE id=?", new String[]{String.valueOf(id)});
        if (c.moveToFirst()) {
            t.id = id;
            t.batchId = c.getString(0);
            t.supplierId = c.getLong(1);
            t.date = c.getString(2);
            t.ticketNo = EncryptUtils.decrypt(c.getString(3));
            t.totalAmount = c.getDouble(4);
            t.settleType = c.getInt(5);
            t.settled = c.getInt(6) == 1;
            t.accountId = c.getLong(7);
            t.remark = EncryptUtils.decrypt(c.getString(8));
        }
        c.close();
        t.items = getTicketItems(id);
        Cursor sc = db().rawQuery("SELECT name FROM " + DatabaseHelper.TABLE_FIN_SUPPLIER
                + " WHERE id=?", new String[]{String.valueOf(t.supplierId)});
        if (sc.moveToFirst()) t.supplierName = EncryptUtils.decrypt(sc.getString(0));
        sc.close();
        return t;
    }

    public List<FinTicketItem> getTicketItems(long ticketId) {
        List<FinTicketItem> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id,supplier_product_id,quantity,unit_price,amount FROM "
                + DatabaseHelper.TABLE_FIN_TICKET_ITEM + " WHERE ticket_id=? ORDER BY id",
                new String[]{String.valueOf(ticketId)});
        while (c.moveToNext()) {
            FinTicketItem it = new FinTicketItem();
            it.id = c.getLong(0);
            it.supplierProductId = c.getLong(1);
            it.quantity = c.getDouble(2);
            it.unitPrice = c.getDouble(3);
            it.amount = c.getDouble(4);
            list.add(it);
        }
        c.close();
        for (FinTicketItem it : list) {
            Cursor sc = db().rawQuery("SELECT sp.product_name, sp.spec, p.name, p.unit"
                    + " FROM " + DatabaseHelper.TABLE_FIN_SUPPLIER_PRODUCT + " sp"
                    + " LEFT JOIN " + DatabaseHelper.TABLE_FIN_PRODUCT + " p ON sp.product_id=p.id"
                    + " WHERE sp.id=?", new String[]{String.valueOf(it.supplierProductId)});
            if (sc.moveToFirst()) {
                it.productName = EncryptUtils.decrypt(sc.getString(0));
                it.spec = EncryptUtils.decrypt(sc.getString(1));
                it.unit = EncryptUtils.decrypt(sc.getString(3));
            }
            sc.close();
        }
        return list;
    }

    public List<FinSupplierProduct> getEnabledSupplierProducts(long supplierId) {
        List<FinSupplierProduct> all = getSupplierProducts(supplierId);
        List<FinSupplierProduct> result = new ArrayList<>();
        for (FinSupplierProduct sp : all) {
            if (sp.enabled) result.add(sp);
        }
        return result;
    }

    public long ensureSupplierProduct(long supplierId, long productId) {
        Cursor c = db().rawQuery("SELECT id FROM " + DatabaseHelper.TABLE_FIN_SUPPLIER_PRODUCT
                + " WHERE supplier_id=? AND product_id=? AND enabled=1 LIMIT 1",
                new String[]{String.valueOf(supplierId), String.valueOf(productId)});
        long spId = -1;
        if (c.moveToFirst()) spId = c.getLong(0);
        c.close();
        if (spId > 0) return spId;
        FinProduct p = getProduct(productId);
        if (p == null) return -1;
        return insertSupplierProduct(supplierId, productId, p.name, "");
    }

    public long ensureSupplierProductByCategory(long supplierId, long categoryId, long productId) {
        long spId = ensureSupplierProduct(supplierId, productId);
        if (spId <= 0) return spId;
        List<Long> cats = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT " + DatabaseHelper.COLUMN_FIN_CATEGORY_ID + " FROM "
                + DatabaseHelper.TABLE_FIN_PRODUCT_CATEGORY
                + " WHERE " + DatabaseHelper.COLUMN_FIN_PRODUCT_ID + "=?",
                new String[]{String.valueOf(productId)});
        while (c.moveToNext()) cats.add(c.getLong(0));
        c.close();
        if (!cats.contains(categoryId)) {
            cats.add(categoryId);
            setProductCategories(productId, cats);
        }
        return spId;
    }

    public double getLastPrice(long supplierProductId) {
        Cursor c = db().rawQuery("SELECT ti.unit_price FROM " + DatabaseHelper.TABLE_FIN_TICKET_ITEM + " ti"
                + " INNER JOIN " + DatabaseHelper.TABLE_FIN_TICKET + " t ON ti.ticket_id=t.id"
                + " WHERE ti." + DatabaseHelper.COLUMN_FIN_SUPPLIER_PRODUCT_ID + "=?"
                + " ORDER BY t.date DESC, t.id DESC LIMIT 1", new String[]{String.valueOf(supplierProductId)});
        double price = 0;
        if (c.moveToFirst()) price = c.getDouble(0);
        c.close();
        return price;
    }

    // ==================== 历史价提示 ====================
    public static class PriceHint {
        public double price;
        public String date;
        public String supplierName;
        public String ticketNo;
    }

    public List<PriceHint> getHistoryByProduct(long supplierProductId, int limit) {
        List<PriceHint> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT ti.unit_price, t.date, s.name, t.supplier_no"
                + " FROM " + DatabaseHelper.TABLE_FIN_TICKET_ITEM + " ti"
                + " INNER JOIN " + DatabaseHelper.TABLE_FIN_TICKET + " t ON ti.ticket_id=t.id"
                + " LEFT JOIN " + DatabaseHelper.TABLE_FIN_SUPPLIER + " s ON t.supplier_id=s.id"
                + " WHERE ti." + DatabaseHelper.COLUMN_FIN_SUPPLIER_PRODUCT_ID + "=?"
                + " ORDER BY t.date DESC, t.id DESC LIMIT " + limit,
                new String[]{String.valueOf(supplierProductId)});
        while (c.moveToNext()) {
            PriceHint h = new PriceHint();
            h.price = c.getDouble(0);
            h.date = c.getString(1);
            h.supplierName = EncryptUtils.decrypt(c.getString(2));
            h.ticketNo = EncryptUtils.decrypt(c.getString(3));
            list.add(h);
        }
        c.close();
        return list;
    }

    // ==================== 卖虾收入 ====================
    public static class FinIncome {
        public long id;
        public String batchId;
        public String date;
        public String carNo;
        public String shedNo;
        public String specs;
        public double weight;
        public double price;
        public double netFee;
        public double amount;
        public double actualAmount;
        public boolean netFeePaid = false;
        public String netFeePayDate = "";
        public long netFeeAccountId;
        public String netFeeAccountName = "";
        public long accountId;
        public boolean paid = true;
        public String payDate = "";
        public String accountName = "";
        public String agent;
        public String customer;
        public String remark;
    }

    public long insertIncome(FinIncome in) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_BATCH_ID, in.batchId);
        v.put(DatabaseHelper.COLUMN_FIN_DATE, in.date);
        v.put(DatabaseHelper.COLUMN_FIN_CAR_NO, EncryptUtils.encrypt(in.carNo));
        v.put(DatabaseHelper.COLUMN_FIN_SHED_NO, in.shedNo);
        v.put(DatabaseHelper.COLUMN_FIN_SPECS, in.specs);
        v.put(DatabaseHelper.COLUMN_FIN_WEIGHT, in.weight);
        v.put(DatabaseHelper.COLUMN_FIN_PRICE, in.price);
        v.put(DatabaseHelper.COLUMN_FIN_NET_FEE, in.netFee);
        v.put(DatabaseHelper.COLUMN_FIN_NET_FEE_PAID, in.netFeePaid ? 1 : 0);
        v.put(DatabaseHelper.COLUMN_FIN_NET_FEE_PAY_DATE, in.netFeePayDate == null ? "" : in.netFeePayDate);
        v.put(DatabaseHelper.COLUMN_FIN_NET_FEE_ACCOUNT_ID, in.netFeeAccountId);
        v.put(DatabaseHelper.COLUMN_FIN_AMOUNT, in.amount);
        v.put(DatabaseHelper.COLUMN_FIN_ACTUAL_AMOUNT, in.actualAmount);
        v.put(DatabaseHelper.COLUMN_FIN_ACCOUNT_ID, in.accountId);
        v.put(DatabaseHelper.COLUMN_FIN_PAID, in.paid ? 1 : 0);
        v.put(DatabaseHelper.COLUMN_FIN_PAY_DATE, in.payDate == null ? "" : in.payDate);
        v.put(DatabaseHelper.COLUMN_FIN_AGENT, EncryptUtils.encrypt(in.agent));
        v.put(DatabaseHelper.COLUMN_FIN_CUSTOMER, EncryptUtils.encrypt(in.customer));
        v.put(DatabaseHelper.COLUMN_REMARK, EncryptUtils.encrypt(in.remark));
        v.put(DatabaseHelper.COLUMN_FIN_CREATE_TIME, System.currentTimeMillis());
        return db().insert(DatabaseHelper.TABLE_FIN_INCOME, null, v);
    }

    public void updateIncome(FinIncome in) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_DATE, in.date);
        v.put(DatabaseHelper.COLUMN_FIN_CAR_NO, EncryptUtils.encrypt(in.carNo));
        v.put(DatabaseHelper.COLUMN_FIN_SHED_NO, in.shedNo);
        v.put(DatabaseHelper.COLUMN_FIN_SPECS, in.specs);
        v.put(DatabaseHelper.COLUMN_FIN_WEIGHT, in.weight);
        v.put(DatabaseHelper.COLUMN_FIN_PRICE, in.price);
        v.put(DatabaseHelper.COLUMN_FIN_NET_FEE, in.netFee);
        v.put(DatabaseHelper.COLUMN_FIN_NET_FEE_PAID, in.netFeePaid ? 1 : 0);
        v.put(DatabaseHelper.COLUMN_FIN_NET_FEE_PAY_DATE, in.netFeePayDate == null ? "" : in.netFeePayDate);
        v.put(DatabaseHelper.COLUMN_FIN_NET_FEE_ACCOUNT_ID, in.netFeeAccountId);
        v.put(DatabaseHelper.COLUMN_FIN_AMOUNT, in.amount);
        v.put(DatabaseHelper.COLUMN_FIN_ACTUAL_AMOUNT, in.actualAmount);
        v.put(DatabaseHelper.COLUMN_FIN_ACCOUNT_ID, in.accountId);
        v.put(DatabaseHelper.COLUMN_FIN_PAID, in.paid ? 1 : 0);
        v.put(DatabaseHelper.COLUMN_FIN_PAY_DATE, in.payDate == null ? "" : in.payDate);
        v.put(DatabaseHelper.COLUMN_FIN_AGENT, EncryptUtils.encrypt(in.agent));
        v.put(DatabaseHelper.COLUMN_FIN_CUSTOMER, EncryptUtils.encrypt(in.customer));
        v.put(DatabaseHelper.COLUMN_REMARK, EncryptUtils.encrypt(in.remark));
        db().update(DatabaseHelper.TABLE_FIN_INCOME, v, "id=?", new String[]{String.valueOf(in.id)});
    }

    public void deleteIncome(long id) {
        db().delete(DatabaseHelper.TABLE_FIN_INCOME, "id=?", new String[]{String.valueOf(id)});
    }

    public List<FinIncome> getIncomes(String batchId) {
        List<FinIncome> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id,date,car_no,shed_no,specs,weight,price,net_fee,net_fee_paid,net_fee_pay_date,net_fee_account_id,amount,actual_amount,account_id,paid,pay_date,agent,customer,remark FROM "
                + DatabaseHelper.TABLE_FIN_INCOME + " WHERE batch_id=? ORDER BY date DESC, id DESC",
                new String[]{batchId});
        while (c.moveToNext()) {
            FinIncome in = new FinIncome();
            in.id = c.getLong(0);
            in.batchId = batchId;
            in.date = c.getString(1);
            in.carNo = nonNull(EncryptUtils.decrypt(c.getString(2)));
            in.shedNo = nonNull(c.getString(3));
            in.specs = nonNull(c.getString(4));
            in.weight = c.getDouble(5);
            in.price = c.getDouble(6);
            in.netFee = c.getDouble(7);
            in.netFeePaid = c.getInt(8) == 1;
            in.netFeePayDate = c.isNull(9) ? "" : c.getString(9);
            in.netFeeAccountId = c.getLong(10);
            in.amount = c.getDouble(11);
            in.actualAmount = c.getDouble(12);
            in.accountId = c.getLong(13);
            in.paid = c.getInt(14) == 1;
            in.payDate = c.isNull(15) ? "" : c.getString(15);
            in.agent = nonNull(EncryptUtils.decrypt(c.getString(16)));
            in.customer = nonNull(EncryptUtils.decrypt(c.getString(17)));
            in.remark = nonNull(EncryptUtils.decrypt(c.getString(18)));
            FinAccount a = in.accountId <= 0 ? null : getPaymentAccount(in.accountId);
            in.accountName = a == null ? "" : a.name;
            FinAccount an = in.netFeeAccountId <= 0 ? null : getPaymentAccount(in.netFeeAccountId);
            in.netFeeAccountName = an == null ? "" : an.name;
            list.add(in);
        }
        c.close();
        return list;
    }

    public List<FinIncome> searchIncomes(String keyword) {
        List<FinIncome> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id,batch_id,date,car_no,shed_no,specs,weight,price,net_fee,net_fee_paid,net_fee_pay_date,net_fee_account_id,amount,actual_amount,account_id,paid,pay_date,agent,customer,remark FROM "
                + DatabaseHelper.TABLE_FIN_INCOME + " ORDER BY date DESC, id DESC", null);
        String kw = keyword == null ? "" : keyword.trim();
        while (c.moveToNext()) {
            FinIncome in = new FinIncome();
            in.id = c.getLong(0);
            in.batchId = c.getString(1);
            in.date = c.getString(2);
            in.carNo = nonNull(EncryptUtils.decrypt(c.getString(3)));
            in.shedNo = nonNull(c.getString(4));
            in.specs = nonNull(c.getString(5));
            in.weight = c.getDouble(6);
            in.price = c.getDouble(7);
            in.netFee = c.getDouble(8);
            in.netFeePaid = c.getInt(9) == 1;
            in.netFeePayDate = c.isNull(10) ? "" : c.getString(10);
            in.netFeeAccountId = c.getLong(11);
            in.amount = c.getDouble(12);
            in.actualAmount = c.getDouble(13);
            in.accountId = c.getLong(14);
            in.paid = c.getInt(15) == 1;
            in.payDate = c.isNull(16) ? "" : c.getString(16);
            in.agent = nonNull(EncryptUtils.decrypt(c.getString(17)));
            in.customer = nonNull(EncryptUtils.decrypt(c.getString(18)));
            in.remark = nonNull(EncryptUtils.decrypt(c.getString(19)));
            FinAccount a = in.accountId <= 0 ? null : getPaymentAccount(in.accountId);
            in.accountName = a == null ? "" : a.name;
            FinAccount an = in.netFeeAccountId <= 0 ? null : getPaymentAccount(in.netFeeAccountId);
            in.netFeeAccountName = an == null ? "" : an.name;
            if (kw.isEmpty() || in.carNo.contains(kw) || in.shedNo.contains(kw)) list.add(in);
        }
        c.close();
        return list;
    }

    // 收款：标记卖虾单已收（计入收入），记录养殖场收款账户
    public void setIncomePaid(long id, long accountId, String payDate, double actualAmount) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_PAID, 1);
        v.put(DatabaseHelper.COLUMN_FIN_ACCOUNT_ID, accountId);
        v.put(DatabaseHelper.COLUMN_FIN_PAY_DATE, payDate == null ? "" : payDate);
        v.put(DatabaseHelper.COLUMN_FIN_ACTUAL_AMOUNT, round2(actualAmount));
        db().update(DatabaseHelper.TABLE_FIN_INCOME, v, "id=?", new String[]{String.valueOf(id)});
    }

    public List<FinSeed> searchSeeds(String keyword) {
        List<FinSeed> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id,batch_id,supplier_id,date,quantity,unit_price,total_amount,actual_amount,remark FROM "
                + DatabaseHelper.TABLE_FIN_SEED + " ORDER BY date DESC, id DESC", null);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        while (c.moveToNext()) {
            FinSeed s = new FinSeed();
            s.id = c.getLong(0);
            s.batchId = c.getString(1);
            s.supplierId = c.getLong(2);
            s.date = c.getString(3);
            s.quantity = c.getDouble(4);
            s.unitPrice = c.getDouble(5);
            s.totalAmount = c.getDouble(6);
            s.actualAmount = c.getDouble(7);
            s.remark = EncryptUtils.decrypt(c.getString(8));
            list.add(s);
        }
        c.close();
        Map<Long, String> names = new HashMap<>();
        Cursor sc = db().rawQuery("SELECT id,name FROM " + DatabaseHelper.TABLE_FIN_SUPPLIER, null);
        while (sc.moveToNext()) names.put(sc.getLong(0), EncryptUtils.decrypt(sc.getString(1)));
        sc.close();
        List<FinSeed> result = new ArrayList<>();
        for (FinSeed s : list) {
            s.supplierName = names.get(s.supplierId) == null ? "" : names.get(s.supplierId);
            String hay = (s.supplierName + s.remark).toLowerCase();
            if (kw.isEmpty() || hay.contains(kw)) result.add(s);
        }
        return result;
    }

    // ==================== 支付账户 ====================
    public static class FinAccount {
        public long id;
        public String name = "";
        public boolean enabled = true;
        public int sortOrder = 0;
    }

    public List<FinAccount> getPaymentAccounts() {
        List<FinAccount> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id,name,enabled,sort_order FROM "
                + DatabaseHelper.TABLE_FIN_PAYMENT_ACCOUNT + " ORDER BY enabled DESC, sort_order, id",
                null);
        while (c.moveToNext()) {
            FinAccount a = new FinAccount();
            a.id = c.getLong(0);
            a.name = EncryptUtils.decrypt(c.getString(1));
            a.enabled = c.getInt(2) == 1;
            a.sortOrder = c.getInt(3);
            list.add(a);
        }
        c.close();
        return list;
    }

    public FinAccount getPaymentAccount(long id) {
        FinAccount a = null;
        Cursor c = db().rawQuery("SELECT id,name,enabled,sort_order FROM "
                + DatabaseHelper.TABLE_FIN_PAYMENT_ACCOUNT + " WHERE id=?", new String[]{String.valueOf(id)});
        if (c.moveToFirst()) {
            a = new FinAccount();
            a.id = c.getLong(0);
            a.name = EncryptUtils.decrypt(c.getString(1));
            a.enabled = c.getInt(2) == 1;
            a.sortOrder = c.getInt(3);
        }
        c.close();
        return a;
    }

    public long insertPaymentAccount(String name) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_NAME, EncryptUtils.encrypt(name));
        v.put(DatabaseHelper.COLUMN_FIN_ENABLED, 1);
        v.put(DatabaseHelper.COLUMN_FIN_CREATE_TIME, System.currentTimeMillis());
        return db().insert(DatabaseHelper.TABLE_FIN_PAYMENT_ACCOUNT, null, v);
    }

    public void updatePaymentAccount(FinAccount a) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_NAME, EncryptUtils.encrypt(a.name));
        v.put(DatabaseHelper.COLUMN_FIN_ENABLED, a.enabled ? 1 : 0);
        v.put(DatabaseHelper.COLUMN_FIN_SORT_ORDER, a.sortOrder);
        db().update(DatabaseHelper.TABLE_FIN_PAYMENT_ACCOUNT, v, "id=?",
                new String[]{String.valueOf(a.id)});
    }

    // ==================== 联系人收款账户 ====================
    public static class FinContactAccount {
        public static final int TYPE_BANK = 0;
        public static final int TYPE_WECHAT = 1;
        public static final int TYPE_ALIPAY = 2;
        public static final int TYPE_OTHER = 3;
        public long id;
        public long supplierId;
        public int type;
        public String accountName = "";
        public String accountNo = "";
        public boolean enabled = true;

        public String displayName() {
            if (accountNo.isEmpty()) return accountName;
            if (accountName.isEmpty()) return accountNo;
            return accountName + " " + accountNo;
        }
    }

    public List<FinContactAccount> getContactAccounts(long supplierId) {
        List<FinContactAccount> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id,supplier_id,account_type,account_name,account_no,enabled FROM "
                + DatabaseHelper.TABLE_FIN_CONTACT_ACCOUNT
                + " WHERE supplier_id=? ORDER BY enabled DESC, id",
                new String[]{String.valueOf(supplierId)});
        while (c.moveToNext()) {
            FinContactAccount a = new FinContactAccount();
            a.id = c.getLong(0);
            a.supplierId = c.getLong(1);
            a.type = c.getInt(2);
            a.accountName = nonNull(EncryptUtils.decrypt(c.getString(3)));
            a.accountNo = nonNull(EncryptUtils.decrypt(c.getString(4)));
            a.enabled = c.getInt(5) == 1;
            list.add(a);
        }
        c.close();
        return list;
    }

    public FinContactAccount getContactAccount(long id) {
        FinContactAccount a = null;
        Cursor c = db().rawQuery("SELECT id,supplier_id,account_type,account_name,account_no,enabled FROM "
                + DatabaseHelper.TABLE_FIN_CONTACT_ACCOUNT + " WHERE id=?",
                new String[]{String.valueOf(id)});
        if (c.moveToFirst()) {
            a = new FinContactAccount();
            a.id = c.getLong(0);
            a.supplierId = c.getLong(1);
            a.type = c.getInt(2);
            a.accountName = nonNull(EncryptUtils.decrypt(c.getString(3)));
            a.accountNo = nonNull(EncryptUtils.decrypt(c.getString(4)));
            a.enabled = c.getInt(5) == 1;
        }
        c.close();
        return a;
    }

    public long insertContactAccount(long supplierId, int type, String accountName, String accountNo) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_ID, supplierId);
        v.put(DatabaseHelper.COLUMN_FIN_ACCOUNT_TYPE, type);
        v.put(DatabaseHelper.COLUMN_FIN_ACCOUNT_NAME, EncryptUtils.encrypt(nonNull(accountName)));
        v.put(DatabaseHelper.COLUMN_FIN_ACCOUNT_NO, EncryptUtils.encrypt(nonNull(accountNo)));
        v.put(DatabaseHelper.COLUMN_FIN_ENABLED, 1);
        v.put(DatabaseHelper.COLUMN_FIN_CREATE_TIME, System.currentTimeMillis());
        return db().insert(DatabaseHelper.TABLE_FIN_CONTACT_ACCOUNT, null, v);
    }

    public void updateContactAccount(FinContactAccount a) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_ACCOUNT_TYPE, a.type);
        v.put(DatabaseHelper.COLUMN_FIN_ACCOUNT_NAME, EncryptUtils.encrypt(nonNull(a.accountName)));
        v.put(DatabaseHelper.COLUMN_FIN_ACCOUNT_NO, EncryptUtils.encrypt(nonNull(a.accountNo)));
        v.put(DatabaseHelper.COLUMN_FIN_ENABLED, a.enabled ? 1 : 0);
        db().update(DatabaseHelper.TABLE_FIN_CONTACT_ACCOUNT, v, "id=?",
                new String[]{String.valueOf(a.id)});
    }

    // 删除＝停用（保留历史付款记录引用）
    public void deleteContactAccount(long id) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_ENABLED, 0);
        db().update(DatabaseHelper.TABLE_FIN_CONTACT_ACCOUNT, v, "id=?",
                new String[]{String.valueOf(id)});
    }

    // ==================== 付款结算 ====================
    public static class FinPayment {
        public long id;
        public String batchId;
        public long supplierId;
        public String date;
        public long accountId;
        public long receiveAccountId;
        public double dueAmount;
        public double paidAmount;
        public double diffAmount;
        public String diffReason = "";
        public String remark = "";
        public String supplierName = "";
        public String accountName = "";
        public String receiveAccountName = "";
    }

    public static class FinPaymentTicket {
        public long ticketId;
        public double amount;
    }

    public long savePayment(FinPayment p, List<FinPaymentTicket> tickets) {
        SQLiteDatabase db = db();
        long paymentId;
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put(DatabaseHelper.COLUMN_BATCH_ID, p.batchId);
            v.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_ID, p.supplierId);
            v.put(DatabaseHelper.COLUMN_FIN_DATE, p.date);
            v.put(DatabaseHelper.COLUMN_FIN_ACCOUNT_ID, p.accountId);
            v.put(DatabaseHelper.COLUMN_FIN_RECEIVE_ACCOUNT_ID, p.receiveAccountId);
            v.put(DatabaseHelper.COLUMN_FIN_DUE_AMOUNT, p.dueAmount);
            v.put(DatabaseHelper.COLUMN_FIN_PAID_AMOUNT, p.paidAmount);
            v.put(DatabaseHelper.COLUMN_FIN_DIFF_AMOUNT, p.diffAmount);
            v.put(DatabaseHelper.COLUMN_FIN_DIFF_REASON, EncryptUtils.encrypt(p.diffReason));
            v.put(DatabaseHelper.COLUMN_REMARK, EncryptUtils.encrypt(p.remark));
            v.put(DatabaseHelper.COLUMN_FIN_CREATE_TIME, System.currentTimeMillis());
            paymentId = db.insert(DatabaseHelper.TABLE_FIN_PAYMENT, null, v);
            for (FinPaymentTicket t : tickets) {
                ContentValues iv = new ContentValues();
                iv.put(DatabaseHelper.COLUMN_FIN_PAYMENT_ID, paymentId);
                iv.put(DatabaseHelper.COLUMN_FIN_TICKET_ID, t.ticketId);
                iv.put(DatabaseHelper.COLUMN_FIN_AMOUNT, t.amount);
                db.insert(DatabaseHelper.TABLE_FIN_PAYMENT_TICKET, null, iv);
                setTicketSettled(db, t.ticketId);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return paymentId;
    }

    private void setTicketSettled(SQLiteDatabase db, long ticketId) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_SETTLED, 1);
        db.update(DatabaseHelper.TABLE_FIN_TICKET, v, "id=?",
                new String[]{String.valueOf(ticketId)});
    }

    public List<FinTicket> getUnsettledTickets(long supplierId) {
        List<FinTicket> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id,batch_id,date,supplier_no,total_amount,settle_type,settled,account_id,remark FROM "
                + DatabaseHelper.TABLE_FIN_TICKET + " WHERE supplier_id=? AND settled=0"
                + " ORDER BY date ASC, id ASC",
                new String[]{String.valueOf(supplierId)});
        while (c.moveToNext()) {
            FinTicket t = new FinTicket();
            t.id = c.getLong(0);
            t.batchId = c.getString(1);
            t.supplierId = supplierId;
            t.date = c.getString(2);
            t.ticketNo = EncryptUtils.decrypt(c.getString(3));
            t.totalAmount = c.getDouble(4);
            t.settleType = c.getInt(5);
            t.settled = c.getInt(6) == 1;
            t.accountId = c.getLong(7);
            t.remark = EncryptUtils.decrypt(c.getString(8));
            list.add(t);
        }
        c.close();
        return list;
    }

    public List<FinPayment> getPayments(long supplierId) {
        List<FinPayment> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id,batch_id,supplier_id,date,account_id,receive_account_id,"
                + "due_amount,paid_amount,diff_amount,diff_reason,remark FROM " + DatabaseHelper.TABLE_FIN_PAYMENT
                + " WHERE supplier_id=? ORDER BY date DESC, id DESC",
                new String[]{String.valueOf(supplierId)});
        while (c.moveToNext()) {
            FinPayment p = new FinPayment();
            p.id = c.getLong(0);
            p.batchId = c.getString(1);
            p.supplierId = c.getLong(2);
            p.date = c.getString(3);
            p.accountId = c.getLong(4);
            p.receiveAccountId = c.getLong(5);
            p.dueAmount = c.getDouble(6);
            p.paidAmount = c.getDouble(7);
            p.diffAmount = c.getDouble(8);
            p.diffReason = EncryptUtils.decrypt(c.getString(9));
            p.remark = EncryptUtils.decrypt(c.getString(10));
            FinAccount pa = getPaymentAccount(p.accountId);
            p.accountName = pa == null ? "" : pa.name;
            FinContactAccount ra = p.receiveAccountId <= 0 ? null : getContactAccount(p.receiveAccountId);
            p.receiveAccountName = ra == null ? "" : ra.displayName();
            list.add(p);
        }
        c.close();
        return list;
    }

    public List<FinPayment> searchPayments(String keyword) {
        List<FinPayment> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id,batch_id,supplier_id,date,account_id,receive_account_id,due_amount,"
                + "paid_amount,diff_amount,diff_reason,remark FROM " + DatabaseHelper.TABLE_FIN_PAYMENT
                + " ORDER BY date DESC, id DESC", null);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        while (c.moveToNext()) {
            FinPayment p = new FinPayment();
            p.id = c.getLong(0);
            p.batchId = c.getString(1);
            p.supplierId = c.getLong(2);
            p.date = c.getString(3);
            p.accountId = c.getLong(4);
            p.receiveAccountId = c.getLong(5);
            p.dueAmount = c.getDouble(6);
            p.paidAmount = c.getDouble(7);
            p.diffAmount = c.getDouble(8);
            p.diffReason = EncryptUtils.decrypt(c.getString(9));
            p.remark = EncryptUtils.decrypt(c.getString(10));
            FinAccount pa = getPaymentAccount(p.accountId);
            p.accountName = pa == null ? "" : pa.name;
            FinContactAccount ra = p.receiveAccountId <= 0 ? null : getContactAccount(p.receiveAccountId);
            p.receiveAccountName = ra == null ? "" : ra.displayName();
            if (kw.isEmpty() || (p.batchId + p.diffReason + p.remark).toLowerCase().contains(kw)) {
                list.add(p);
            }
        }
        c.close();
        return list;
    }

    public void deletePayment(long id) {
        SQLiteDatabase db = db();
        db.delete(DatabaseHelper.TABLE_FIN_PAYMENT_TICKET, DatabaseHelper.COLUMN_FIN_PAYMENT_ID + "=?",
                new String[]{String.valueOf(id)});
        db.delete(DatabaseHelper.TABLE_FIN_PAYMENT, "id=?",
                new String[]{String.valueOf(id)});
    }

    public double sumUnsettledBySupplier(long supplierId) {
        Cursor c = db().rawQuery("SELECT IFNULL(SUM(total_amount),0) FROM "
                + DatabaseHelper.TABLE_FIN_TICKET + " WHERE supplier_id=? AND settled=0",
                new String[]{String.valueOf(supplierId)});
        double sum = 0;
        if (c.moveToFirst()) sum = round2(c.getDouble(0));
        c.close();
        return sum;
    }

    public double sumPaidBySupplier(long supplierId) {
        Cursor c = db().rawQuery("SELECT IFNULL(SUM(paid_amount),0) FROM "
                + DatabaseHelper.TABLE_FIN_PAYMENT + " WHERE supplier_id=?",
                new String[]{String.valueOf(supplierId)});
        double sum = 0;
        if (c.moveToFirst()) sum = round2(c.getDouble(0));
        c.close();
        return sum;
    }

    // ==================== 捞网费（随卖虾单挂账，可单车/多车结算） ====================
    // 结算：标记捞网费已结算，记录养殖场支出账户与结算日期（同步回原卖虾单）
    public void setIncomeNetFeePaid(Long incomeId, long accountId, String payDate) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_NET_FEE_PAID, 1);
        v.put(DatabaseHelper.COLUMN_FIN_NET_FEE_ACCOUNT_ID, accountId);
        v.put(DatabaseHelper.COLUMN_FIN_NET_FEE_PAY_DATE, payDate == null ? "" : payDate);
        db().update(DatabaseHelper.TABLE_FIN_INCOME, v, "id=?", new String[]{String.valueOf(incomeId)});
    }

    public void setIncomeNetFeePaid(List<Long> incomeIds, long accountId, String payDate) {
        SQLiteDatabase db = db();
        db.beginTransaction();
        try {
            for (Long id : incomeIds) {
                ContentValues v = new ContentValues();
                v.put(DatabaseHelper.COLUMN_FIN_NET_FEE_PAID, 1);
                v.put(DatabaseHelper.COLUMN_FIN_NET_FEE_ACCOUNT_ID, accountId);
                v.put(DatabaseHelper.COLUMN_FIN_NET_FEE_PAY_DATE, payDate == null ? "" : payDate);
                db.update(DatabaseHelper.TABLE_FIN_INCOME, v, "id=?",
                        new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public double sumPaidNetFee(String batchId, String start, String end) {
        StringBuilder sql = new StringBuilder("SELECT IFNULL(SUM(net_fee),0) FROM "
                + DatabaseHelper.TABLE_FIN_INCOME + " WHERE batch_id=? AND net_fee_paid=1");
        List<String> args = new ArrayList<>();
        args.add(batchId);
        if (start != null) { sql.append(" AND date>=?"); args.add(start); }
        if (end != null) { sql.append(" AND date<=?"); args.add(end); }
        Cursor c = db().rawQuery(sql.toString(), args.toArray(new String[0]));
        double sum = 0;
        if (c.moveToFirst()) sum = round2(c.getDouble(0));
        c.close();
        return sum;
    }

    public double sumUnpaidNetFee(String batchId, String start, String end) {
        StringBuilder sql = new StringBuilder("SELECT IFNULL(SUM(net_fee),0) FROM "
                + DatabaseHelper.TABLE_FIN_INCOME + " WHERE batch_id=? AND net_fee_paid=0");
        List<String> args = new ArrayList<>();
        args.add(batchId);
        if (start != null) { sql.append(" AND date>=?"); args.add(start); }
        if (end != null) { sql.append(" AND date<=?"); args.add(end); }
        Cursor c = db().rawQuery(sql.toString(), args.toArray(new String[0]));
        double sum = 0;
        if (c.moveToFirst()) sum = round2(c.getDouble(0));
        c.close();
        return sum;
    }

    // ==================== 利润统计 ====================
    public static class ProfitResult {
        public double income = 0;
        public double expense = 0;
        public double animalHealth = 0;
        public double profit = 0;
    }

    public ProfitResult calcProfit(String batchId, String start, String end) {
        ProfitResult r = new ProfitResult();
        r.income = sumIncome(batchId, start, end);
        r.expense = sumExpense(batchId, null, null, start, end) + sumSeed(batchId, start, end)
                + sumWage(batchId, start, end) + sumPaidNetFee(batchId, start, end)
                + sumOrderPaid(batchId, start, end);
        r.animalHealth = sumTicket(batchId, start, end);
        r.profit = round2(r.income - r.expense - r.animalHealth);
        r.income = round2(r.income);
        r.expense = round2(r.expense);
        r.animalHealth = round2(r.animalHealth);
        return r;
    }

    public double sumIncome(String batchId, String start, String end) {
        StringBuilder sql = new StringBuilder("SELECT IFNULL(SUM(actual_amount),0) FROM "
                + DatabaseHelper.TABLE_FIN_INCOME + " WHERE batch_id=? AND paid=1");
        List<String> args = new ArrayList<>();
        args.add(batchId);
        if (start != null) { sql.append(" AND date>=?"); args.add(start); }
        if (end != null) { sql.append(" AND date<=?"); args.add(end); }
        Cursor c = db().rawQuery(sql.toString(), args.toArray(new String[0]));
        double sum = 0;
        if (c.moveToFirst()) sum = c.getDouble(0);
        c.close();
        return sum;
    }

    // 应收账款 = 未收到款的卖虾单（含已收期内的未收单）
    public double sumUnpaidIncome(String batchId, String start, String end) {
        StringBuilder sql = new StringBuilder("SELECT IFNULL(SUM(actual_amount),0) FROM "
                + DatabaseHelper.TABLE_FIN_INCOME + " WHERE batch_id=? AND paid=0");
        List<String> args = new ArrayList<>();
        args.add(batchId);
        if (start != null) { sql.append(" AND date>=?"); args.add(start); }
        if (end != null) { sql.append(" AND date<=?"); args.add(end); }
        Cursor c = db().rawQuery(sql.toString(), args.toArray(new String[0]));
        double sum = 0;
        if (c.moveToFirst()) sum = c.getDouble(0);
        c.close();
        return sum;
    }

    public int countUnpaidIncome(String batchId, String start, String end) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM "
                + DatabaseHelper.TABLE_FIN_INCOME + " WHERE batch_id=? AND paid=0");
        List<String> args = new ArrayList<>();
        args.add(batchId);
        if (start != null) { sql.append(" AND date>=?"); args.add(start); }
        if (end != null) { sql.append(" AND date<=?"); args.add(end); }
        Cursor c = db().rawQuery(sql.toString(), args.toArray(new String[0]));
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    public double sumTicket(String batchId, String start, String end) {
        StringBuilder sql = new StringBuilder("SELECT IFNULL(SUM(total_amount),0) FROM "
                + DatabaseHelper.TABLE_FIN_TICKET + " WHERE batch_id=?");
        List<String> args = new ArrayList<>();
        args.add(batchId);
        if (start != null) { sql.append(" AND date>=?"); args.add(start); }
        if (end != null) { sql.append(" AND date<=?"); args.add(end); }
        Cursor c = db().rawQuery(sql.toString(), args.toArray(new String[0]));
        double sum = 0;
        if (c.moveToFirst()) sum = c.getDouble(0);
        c.close();
        return sum;
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }

    // 数量显示：去掉多余小数尾零（10.0 → 10，2.50 → 2.5）
    public static String stripTrailingZeros(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        java.math.BigDecimal bd = new java.math.BigDecimal(String.valueOf(v))
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return bd.toPlainString();
    }

    // ==================== 单据式进货（单头 + 明细行） ====================

    /** 历史价：最近一次有价进货的单价 + 日期（按所选单位换算） */
    public static class LastPrice {
        public double price;      // 已换算到所选单位
        public String date = "";
        public boolean has;
    }

    /**
     * 取最近一次有价进货的单价。
     * unit 为空或 packQty<=1 时按基本单位返回；否则按 packQty 换算到包装单位。
     * 调用方负责传入正确的单位与换算率（基本单位传 1，包装单位传实际每包数量）。
     */
    public LastPrice getLastPurchaseInfo(long supplierProductId, String unit, double packQty) {
        LastPrice r = new LastPrice();
        Cursor c = db().rawQuery("SELECT i.unit_price,o.date FROM "
                        + DatabaseHelper.TABLE_FIN_PURCHASE_ITEM + " i JOIN "
                        + DatabaseHelper.TABLE_FIN_PURCHASE_ORDER + " o ON o.id=i.order_id "
                        + "WHERE i.supplier_product_id=? AND i.unit_price>0 "
                        + "ORDER BY o.date DESC, i.id DESC LIMIT 1",
                new String[]{String.valueOf(supplierProductId)});
        if (c.moveToFirst()) {
            double base = c.getDouble(0);
            r.date = nonNull(c.getString(1));
            r.price = packQty > 1 ? round2(base * packQty) : base;
            r.has = true;
        }
        c.close();
        return r;
    }

    /** 进货单头：一张单据 = 一个供应商 + N 条明细 */
    public static class FinPurchaseOrder {
        public long id;
        public String batchId;
        public String orderNo = "";
        public String date = "";
        public long supplierId;
        public String supplierName = "";
        public boolean paid;
        public long accountId;
        public String accountName = "";
        public double totalAmount = 0;
        public String remark = "";
        public int itemCount = 0;

        public boolean isPaid() {
            return paid;
        }
    }

    /** 进货明细行：品类 / 品名 / 规格 / 历史价 / 当前价 / 涨跌幅 / 数量 / 金额 */
    public static class FinPurchaseItem {
        public long id;
        public long orderId;
        public long categoryId;
        public String categoryName = "";
        public long supplierProductId;
        public String productName = "";
        public String spec = "";
        public String unit = "";
        public double quantity;
        public double historyPrice;   // 下单时的历史价快照
        public double unitPrice;      // 当前价；0 表示无价（按历史价估）
        public double amount;

        public boolean hasPrice() {
            return unitPrice > 0;
        }

        /** 涨跌幅 %：当前价 vs 历史价 */
        public double priceChangePct() {
            if (historyPrice <= 0) return 0;
            return round2((unitPrice - historyPrice) / historyPrice * 100.0);
        }

        /** 包装规格文案：20斤/桶 */
        public String packSpec() {
            if (spec.isEmpty()) return "";
            return spec;
        }
    }

    /** 单号：JR + yyyyMMdd + 3 位流水，如 JR20261008001 */
    public String nextOrderNo(String date) {
        String day = date == null ? "" : date.replace("-", "");
        if (day.length() != 8) day = todayStr();
        String prefix = "JR" + day;
        Cursor c = db().rawQuery("SELECT order_no FROM " + DatabaseHelper.TABLE_FIN_PURCHASE_ORDER
                + " WHERE order_no LIKE ? ORDER BY order_no DESC LIMIT 1",
                new String[]{prefix + "%"});
        int next = 1;
        if (c.moveToFirst()) {
            String last = nonNull(c.getString(0));
            if (last.length() > prefix.length()) {
                try {
                    next = Integer.parseInt(last.substring(prefix.length())) + 1;
                } catch (NumberFormatException ignored) { }
            }
        }
        c.close();
        return String.format(Locale.CHINA, "%s%03d", prefix, next);
    }

    private static String todayStr() {
        return new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
    }

    /** 保存（新建或整体覆盖），明细行按传入列表重写 */
    public long savePurchaseOrder(FinPurchaseOrder o, List<FinPurchaseItem> items) {
        SQLiteDatabase db = db();
        db.beginTransaction();
        try {
            double total = 0;
            for (FinPurchaseItem it : items) {
                double price = it.unitPrice > 0 ? it.unitPrice : it.historyPrice;
                it.amount = round2(price * it.quantity);
                total += it.amount;
            }
            o.totalAmount = round2(total);

            ContentValues v = orderValues(o);
            long id;
            if (o.id > 0) {
                db.update(DatabaseHelper.TABLE_FIN_PURCHASE_ORDER, v, "id=?",
                        new String[]{String.valueOf(o.id)});
                id = o.id;
                db.delete(DatabaseHelper.TABLE_FIN_PURCHASE_ITEM, "order_id=?",
                        new String[]{String.valueOf(id)});
            } else {
                id = db.insert(DatabaseHelper.TABLE_FIN_PURCHASE_ORDER, null, v);
            }
            for (FinPurchaseItem it : items) {
                it.orderId = id;
                db.insert(DatabaseHelper.TABLE_FIN_PURCHASE_ITEM, null, itemValues(it, o.batchId));
            }
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    private ContentValues orderValues(FinPurchaseOrder o) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_BATCH_ID, o.batchId == null ? "" : o.batchId);
        v.put(DatabaseHelper.COLUMN_FIN_ORDER_NO, nonNull(o.orderNo));
        v.put(DatabaseHelper.COLUMN_FIN_DATE, o.date == null ? "" : o.date);
        v.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_ID, o.supplierId);
        v.put(DatabaseHelper.COLUMN_FIN_PURCHASE_PAID, o.paid ? 1 : 0);
        v.put(DatabaseHelper.COLUMN_FIN_PURCHASE_ACCOUNT_ID, o.accountId);
        v.put(DatabaseHelper.COLUMN_FIN_TOTAL_AMOUNT, o.totalAmount);
        v.put(DatabaseHelper.COLUMN_REMARK, EncryptUtils.encrypt(nonNull(o.remark)));
        v.put(DatabaseHelper.COLUMN_FIN_CREATE_TIME, System.currentTimeMillis());
        return v;
    }

    private ContentValues itemValues(FinPurchaseItem it, String batchId) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_ORDER_ID, it.orderId);
        v.put(DatabaseHelper.COLUMN_BATCH_ID, batchId == null ? "" : batchId);
        v.put(DatabaseHelper.COLUMN_FIN_CATEGORY_ID, it.categoryId);
        v.put(DatabaseHelper.COLUMN_FIN_SUPPLIER_PRODUCT_ID, it.supplierProductId);
        v.put(DatabaseHelper.COLUMN_FIN_PRODUCT_NAME, EncryptUtils.encrypt(nonNull(it.productName)));
        v.put(DatabaseHelper.COLUMN_FIN_SPEC, EncryptUtils.encrypt(nonNull(it.spec)));
        v.put(DatabaseHelper.COLUMN_FIN_UNIT, EncryptUtils.encrypt(nonNull(it.unit)));
        v.put(DatabaseHelper.COLUMN_FIN_QUANTITY, it.quantity);
        v.put(DatabaseHelper.COLUMN_FIN_HISTORY_PRICE, it.historyPrice);
        v.put(DatabaseHelper.COLUMN_FIN_UNIT_PRICE, it.unitPrice);
        v.put(DatabaseHelper.COLUMN_FIN_AMOUNT, it.amount);
        v.put(DatabaseHelper.COLUMN_FIN_CREATE_TIME, System.currentTimeMillis());
        return v;
    }

    public void deletePurchaseOrder(long id) {
        db().delete(DatabaseHelper.TABLE_FIN_PURCHASE_ITEM, "order_id=?",
                new String[]{String.valueOf(id)});
        db().delete(DatabaseHelper.TABLE_FIN_PURCHASE_ORDER, "id=?", new String[]{String.valueOf(id)});
    }

    /** 整单结账 / 取消结账 */
    public void setOrderPaid(long id, boolean paid, long accountId) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_FIN_PURCHASE_PAID, paid ? 1 : 0);
        v.put(DatabaseHelper.COLUMN_FIN_PURCHASE_ACCOUNT_ID, paid ? accountId : 0);
        db().update(DatabaseHelper.TABLE_FIN_PURCHASE_ORDER, v, "id=?", new String[]{String.valueOf(id)});
    }

    private static final String ORDER_SELECT =
            "o.id,o.batch_id,o.order_no,o.date,o.supplier_id,o.purchase_paid,"
                    + "o.purchase_account_id,o.total_amount,o.remark,"
                    + "s.name,a.name,(SELECT COUNT(1) FROM " + DatabaseHelper.TABLE_FIN_PURCHASE_ITEM
                    + " i WHERE i.order_id=o.id) FROM "
                    + DatabaseHelper.TABLE_FIN_PURCHASE_ORDER + " o "
                    + "LEFT JOIN " + DatabaseHelper.TABLE_FIN_SUPPLIER + " s ON s.id=o.supplier_id "
                    + "LEFT JOIN " + DatabaseHelper.TABLE_FIN_PAYMENT_ACCOUNT
                    + " a ON a.id=o.purchase_account_id ";

    private FinPurchaseOrder readOrder(Cursor c) {
        FinPurchaseOrder o = new FinPurchaseOrder();
        o.id = c.getLong(0);
        o.batchId = nonNull(c.getString(1));
        o.orderNo = nonNull(c.getString(2));
        o.date = nonNull(c.getString(3));
        o.supplierId = c.getLong(4);
        o.paid = c.getInt(5) == 1;
        o.accountId = c.getLong(6);
        o.totalAmount = c.getDouble(7);
        o.remark = nonNull(EncryptUtils.decrypt(c.getString(8)));
        o.supplierName = c.isNull(9) ? "" : nonNull(EncryptUtils.decrypt(c.getString(9)));
        o.accountName = c.isNull(10) ? "" : nonNull(EncryptUtils.decrypt(c.getString(10)));
        o.itemCount = c.getInt(11);
        return o;
    }

    /** 单据列表（type<0 表示全部；keyword 对单号/供应商模糊匹配） */
    public List<FinPurchaseOrder> getPurchaseOrders(String batchId, String keyword) {
        List<FinPurchaseOrder> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT " + ORDER_SELECT
                + "WHERE o.batch_id=? ORDER BY o.date DESC, o.id DESC");
        Cursor c = db().rawQuery(sql.toString(), new String[]{batchId == null ? "" : batchId});
        String kw = keyword == null ? "" : keyword.trim();
        while (c.moveToNext()) {
            FinPurchaseOrder o = readOrder(c);
            if (kw.isEmpty() || o.orderNo.contains(kw) || o.supplierName.contains(kw)) {
                list.add(o);
            }
        }
        c.close();
        return list;
    }

    public FinPurchaseOrder getPurchaseOrder(long id) {
        Cursor c = db().rawQuery("SELECT " + ORDER_SELECT + "WHERE o.id=?",
                new String[]{String.valueOf(id)});
        FinPurchaseOrder o = null;
        if (c.moveToFirst()) o = readOrder(c);
        c.close();
        return o;
    }

    public List<FinPurchaseItem> getPurchaseItems(long orderId) {
        List<FinPurchaseItem> list = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT i.id,i.order_id,i.category_id,i.supplier_product_id,"
                        + "i.product_name,i.spec,i.unit,i.quantity,i.history_price,i.unit_price,"
                        + "i.amount,cat.name FROM " + DatabaseHelper.TABLE_FIN_PURCHASE_ITEM + " i "
                        + "LEFT JOIN " + DatabaseHelper.TABLE_FIN_CATEGORY
                        + " cat ON cat.id=i.category_id WHERE i.order_id=? ORDER BY i.id",
                new String[]{String.valueOf(orderId)});
        while (c.moveToNext()) {
            FinPurchaseItem it = new FinPurchaseItem();
            it.id = c.getLong(0);
            it.orderId = c.getLong(1);
            it.categoryId = c.getLong(2);
            it.supplierProductId = c.getLong(3);
            it.productName = nonNull(EncryptUtils.decrypt(c.getString(4)));
            it.spec = nonNull(EncryptUtils.decrypt(c.getString(5)));
            it.unit = nonNull(EncryptUtils.decrypt(c.getString(6)));
            it.quantity = c.getDouble(7);
            it.historyPrice = c.getDouble(8);
            it.unitPrice = c.getDouble(9);
            it.amount = c.getDouble(10);
            it.categoryName = c.isNull(11) ? "" : nonNull(EncryptUtils.decrypt(c.getString(11)));
            list.add(it);
        }
        c.close();
        return list;
    }

    // ---- 单据派生金额 ----

    /** 已结账合计（计入支出） */
    public double sumOrderPaid(String batchId, String start, String end) {
        StringBuilder sql = new StringBuilder("SELECT IFNULL(SUM(i.amount),0) FROM "
                + DatabaseHelper.TABLE_FIN_PURCHASE_ITEM + " i JOIN "
                + DatabaseHelper.TABLE_FIN_PURCHASE_ORDER
                + " o ON o.id=i.order_id WHERE o.batch_id=? AND o.purchase_paid=1");
        List<String> args = new ArrayList<>();
        args.add(batchId == null ? "" : batchId);
        if (start != null) { sql.append(" AND o.date>=?"); args.add(start); }
        if (end != null) { sql.append(" AND o.date<=?"); args.add(end); }
        return querySum(sql.toString(), args);
    }

    /** 应付款合计（未结账 且 行有价） */
    public double sumOrderPayable(String batchId) {
        return querySum("SELECT IFNULL(SUM(i.amount),0) FROM "
                        + DatabaseHelper.TABLE_FIN_PURCHASE_ITEM + " i JOIN "
                        + DatabaseHelper.TABLE_FIN_PURCHASE_ORDER
                        + " o ON o.id=i.order_id WHERE o.batch_id=? AND o.purchase_paid=0"
                        + " AND i.unit_price>0",
                Collections.singletonList(batchId == null ? "" : batchId));
    }

    /** 估计应付款合计（未结账 且 行无价，金额按历史价估算） */
    public double sumOrderEstimated(String batchId) {
        return querySum("SELECT IFNULL(SUM(i.amount),0) FROM "
                        + DatabaseHelper.TABLE_FIN_PURCHASE_ITEM + " i JOIN "
                        + DatabaseHelper.TABLE_FIN_PURCHASE_ORDER
                        + " o ON o.id=i.order_id WHERE o.batch_id=? AND o.purchase_paid=0"
                        + " AND i.unit_price<=0",
                Collections.singletonList(batchId == null ? "" : batchId));
    }

    private double querySum(String sql, List<String> args) {
        Cursor c = db().rawQuery(sql, args.toArray(new String[0]));
        double sum = 0;
        if (c.moveToFirst()) sum = round2(c.getDouble(0));
        c.close();
        return sum;
    }
}
