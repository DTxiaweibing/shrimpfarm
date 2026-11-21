# 出货单迁移与对账方案（v16 → v17）

> 状态：待开工（2026-11-21）
> 适用范围：`FeedingRecord.db` 从 v16 升级到 v17，核心是把出货单从"单行"改成"单头 + 明细"
> 前置文档：《ShrimpFarm 财务板块改造方案 v2》第 2、4、5 节
> 目标：迁移可重入、可回滚、五项对账 0 差异

______________________________________________________________________

## 0. 迁移的三条铁律

| 原则         | 内容                                                                               | 原因                                                                          |
| ------------ | ---------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| **保真优先** | 迁移生成的明细 `amount` / `net_fee` **直接取旧单头值**，不按公式重算               | 保证五项对账天然为 0；公式只用于新录入                                        |
| **绝不拆行** | 旧 `shed_no` 是多棚文本（如 `3-5`）时，**只生成 1 条明细**，棚号原文存 `shed_text` | 没有每棚重量，拆行即为造假账                                                  |
| **可重入**   | 迁移标记写在**同一事务内**；异常回滚时标记与明细一起回滚                           | SQLiteOpenHelper 会把版本号也放在事务里，回滚后下次启动重试，不会产生重复明细 |

______________________________________________________________________

## 1. 执行位置与禁忌

```java
// DatabaseHelper.java
@Override
public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    for (int i = oldVersion; i < newVersion; i++) {
        switch (i) {
            // ... case 1 ~ case 15 保持不变
            case 16:                       // v16 → v17
                migrateV17(db);
                break;
        }
    }
}
```

**必须遵守：**

1. 只用传入的 `db`（事务内连接）。**禁止**调用 `getWritableDatabase()`，会重入死锁；
2. **禁止**在迁移里 `new FinanceDb(context)` 或 `DatabaseHelper.getInstance()`——它们持有另一个连接；
3. 每条 `ALTER TABLE` 单独 `try/catch`（与 `DatabaseHelper:990-995` 现有风格一致）；
4. 新列必须在 `onOpen` 的 `ensureColumn()` 再登记一次（`ALTER` 被吞掉时兜底）；
5. 迁移方法内不得弹 UI、不得读 `SharedPreferences` 做耗时操作；
6. 内层 `beginTransaction()` 与 SQLiteOpenHelper 的外层事务是**嵌套关系**（Android 用事务栈，只有最外层真正提交），可以用来做"分组异常回滚"，不会与外层冲突。

______________________________________________________________________

## 2. 迁移流水线（S0–S7）

| 阶段 | 动作                                    | 章节 | 失败处理                                       |
| ---- | --------------------------------------- | ---- | ---------------------------------------------- |
| S0   | `onUpgrade(db, 16, 17)` 进入            | §1   | —                                              |
| S1   | 建 3 张表 + 所有加列                    | §3   | 单条失败仅记录，不中断（列可能已存在）         |
| S2   | 迁移前快照：按 `batch_id` 聚合旧单头    | §4   | 失败则终止迁移（没有对账基准不能继续）         |
| S3   | 回填 `fin_income_item`（1 单 → 1 明细） | §5   | 失败抛异常，整体回滚                           |
| S4   | 从旧 `shed_no` 展开建档 `shed_archive`  | §5.4 | 单条解析失败仅记清单                           |
| S5   | 初始化 `fin_doc_seq`（采购流水反推）    | §6   | 失败不中断（可从空开始）                       |
| S6   | 五项对账                                | §7   | 差异 > 0.005 → 抛 `IllegalStateException` 回滚 |
| S7   | 写迁移标记 + 待核对清单                 | §5.2 | 与 S6 同事务                                   |

______________________________________________________________________

## 3. S1：DDL

```sql
-- ===== 新表 =====
CREATE TABLE IF NOT EXISTS fin_income_item (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  income_id INTEGER NOT NULL,
  shed_id INTEGER DEFAULT 0,
  shed_text TEXT,
  tail_per_jin REAL DEFAULT 0,
  weight REAL DEFAULT 0,
  price REAL DEFAULT 0,
  amount REAL DEFAULT 0,
  net_fee REAL DEFAULT 0,
  sort_order INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_fin_income_item ON fin_income_item(income_id);

CREATE TABLE IF NOT EXISTS shed_archive (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  sort_order INTEGER DEFAULT 0,
  enabled INTEGER DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_shed_name ON shed_archive(name);

CREATE TABLE IF NOT EXISTS fin_doc_seq (
  prefix TEXT, doc_date TEXT, next_seq INTEGER DEFAULT 1,
  PRIMARY KEY (prefix, doc_date)
);

-- ===== 加列（每条独立 try/catch）=====
ALTER TABLE fin_income         ADD COLUMN doc_no TEXT;
ALTER TABLE fin_purchase_order ADD COLUMN pay_type INTEGER DEFAULT 0;
ALTER TABLE fin_purchase_order ADD COLUMN settle_date TEXT;
ALTER TABLE fin_purchase_order ADD COLUMN settle_account_id INTEGER DEFAULT 0;
ALTER TABLE fin_purchase_order ADD COLUMN supplier_doc_no TEXT;
ALTER TABLE fin_purchase_order ADD COLUMN supplier_doc_date TEXT;
ALTER TABLE fin_expense        ADD COLUMN payee_id INTEGER DEFAULT 0;
ALTER TABLE fin_expense        ADD COLUMN account_id INTEGER DEFAULT 0;
ALTER TABLE fin_expense        ADD COLUMN doc_no TEXT;
ALTER TABLE fin_wage           ADD COLUMN doc_no TEXT;
ALTER TABLE fin_supplier       ADD COLUMN address TEXT;
ALTER TABLE fin_supplier       ADD COLUMN bank_account TEXT;
ALTER TABLE fin_supplier       ADD COLUMN bank_name TEXT;
```

`onOpen` 需同时登记上述所有列。

______________________________________________________________________

## 4. S2：迁移前快照

用内存 `Map<String, Snapshot>`（10 万单以内无压力，不需要临时表）：

```java
private static final class Snapshot {
    long count, itemCount;
    double weight, amount, netFee, actual;
}

private Map<String, Snapshot> snapshotIncome(SQLiteDatabase db) {
    Map<String, Snapshot> map = new HashMap<>();
    Cursor c = db.rawQuery(
        "SELECT batch_id, COUNT(*), "
      + "IFNULL(SUM(weight),0), IFNULL(SUM(amount),0), "
      + "IFNULL(SUM(net_fee),0), IFNULL(SUM(actual_amount),0) "
      + "FROM fin_income GROUP BY batch_id", null);
    try {
        while (c.moveToNext()) {
            Snapshot s = new Snapshot();
            s.count  = c.getLong(1);
            s.weight = c.getDouble(2);
            s.amount = c.getDouble(3);
            s.netFee = c.getDouble(4);
            s.actual = c.getDouble(5);
            map.put(nonNull(c.getString(0)), s);
        }
    } finally { c.close(); }
    return map;
}
```

______________________________________________________________________

## 5. S3：回填明细（核心）

### 5.1 幂等守卫

```java
// 已迁移过则跳过（双保险：标记 + 明细行数）
if ("1".equals(getFlag(db, "migrated_v17_income_items"))) return;
if (tableCount(db, "fin_income_item") > 0) return;
```

`getFlag/setFlag` 读写 `basic_data`（`key` + `batch_id` 复合主键，`batch_id` 传空串）。

### 5.2 回填算法

```java
private void backfillIncomeItems(SQLiteDatabase db) {
    SQLiteStatement ins = db.compileStatement(
        "INSERT INTO fin_income_item("
      + "income_id, shed_id, shed_text, tail_per_jin, weight, price, amount, net_fee, sort_order"
      + ") VALUES (?,?,?,?,?,?,?,?,0)");

    Cursor c = db.rawQuery(
        "SELECT id, shed_no, specs, weight, price, net_fee, amount "
      + "FROM fin_income ORDER BY id", null);
    List<String> fixups = new ArrayList<>();
    try {
        db.beginTransaction();
        try {
            while (c.moveToNext()) {
                long id       = c.getLong(0);
                String shed   = nonNull(c.getString(1));
                String specs  = nonNull(c.getString(2));
                double weight = c.getDouble(3);
                double price  = c.getDouble(4);
                double netFee = c.getDouble(5);   // ← 保真：沿用旧值
                double amount = c.getDouble(6);   // ← 保真：沿用旧值

                double tail = parseTailPerJin(specs);
                long shedId = resolveSingleShed(db, shed);   // 多棚返回 0

                ins.clearBindings();
                ins.bindLong(1, id);
                ins.bindLong(2, shedId);
                ins.bindString(3, shed);
                ins.bindDouble(4, tail);
                ins.bindDouble(5, weight);
                ins.bindDouble(6, price);
                ins.bindDouble(7, amount);
                ins.bindDouble(8, netFee);
                ins.executeInsert();

                // 异常数据只记录，不改写
                if (tail == 0 && !specs.trim().isEmpty()) {
                    fixups.add(String.format(Locale.CHINA,
                        "{\"income\":%d,\"issue\":\"spec_unparsed\",\"detail\":\"%s\"}", id, esc(specs)));
                }
                if (amount == 0 && weight > 0 && price > 0) {
                    fixups.add(String.format(Locale.CHINA,
                        "{\"income\":%d,\"issue\":\"amount_zero\",\"detail\":\"%.2f×%.2f\"}", id, weight, price));
                }
                if (netFee == 0 && weight > 0) {
                    fixups.add(String.format(Locale.CHINA,
                        "{\"income\":%d,\"issue\":\"net_fee_zero\",\"detail\":\"%.2f\"}", id, weight));
                }
            }
            putFlag(db, "v17_fixup_json", "[" + String.join(",", fixups) + "]");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    } finally { c.close(); ins.close(); }
}
```

> **性能**：`SQLiteStatement` 预编译 + 事务内批量，1 万单约 300–800 ms；10 万单约 5 s，可接受（首次升级一次性成本）。
>
> **ANR 提醒**：`onUpgrade` 在首次 `getWritableDatabase()` 的调用线程执行。本项目数据量（单批次通常数千单）在 1 s 内，可接受；若担心，可在启动页把"打开数据库"放到后台线程并显示"正在升级数据…"。

### 5.3 规格解析规则

```java
private static final Pattern TAIL_PATTERN =
    Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*尾?\\s*/?\\s*斤?");

private static double parseTailPerJin(String specs) {
    if (specs == null) return 0;
    Matcher m = TAIL_PATTERN.matcher(specs.trim());
    if (m.find()) {
        try {
            double v = Double.parseDouble(m.group(1));
            return (v > 0 && v < 1000) ? v : 0;   // 明显异常值视为失败
        } catch (NumberFormatException ignored) { }
    }
    return 0;
}
```

| 旧文本                                  | 解析结果                                     |
| --------------------------------------- | -------------------------------------------- |
| `30尾/斤` / `30 尾/斤` / `30/斤` / `30` | 30                                           |
| `20-30尾/斤`                            | 20（区间断言取首值，记 `spec_range` 待核对） |
| `大虾` / `中虾` / 空                    | 0（进待核对清单）                            |

### 5.4 棚号解析与关联

```java
private long resolveSingleShed(SQLiteDatabase db, String shedNo) {
    List<Integer> nums = ShedCoverage.parseShedNumbers(shedNo);  // 复用养殖侧解析引擎
    if (nums.size() != 1) return 0;        // 空 / 多棚 / 解析失败 → 不关联，保留文本
    return ensureShedArchive(db, nums.get(0));
}

private long ensureShedArchive(SQLiteDatabase db, int n) {
    String name = n + "号棚";
    Cursor c = db.rawQuery("SELECT id FROM shed_archive WHERE name=?", new String[]{name});
    try { if (c.moveToFirst()) return c.getLong(0); } finally { c.close(); }
    ContentValues v = new ContentValues();
    v.put("name", name); v.put("enabled", 1); v.put("sort_order", n);
    return db.insert("shed_archive", null, v);
}
```

- `ShedCoverage.parseShedNumbers` 是纯逻辑类（`mixcalc/model`），可直接在迁移中调用，与养殖侧规则完全一致；该引擎已随计算器替换在**生产版本**运行（`MixCalcTableView` 自绘表格在用它解析棚号区间），不是新代码；
- 上限 99（`ShedCoverage.MAX_SHED_NUMBER`），超限的棚号进待核对清单，不建档；
- 非数字棚号（如 `A棚`）：原文入清单，不自动建。

______________________________________________________________________

## 6. S5：单号流水初始化

```java
// 采购前缀 JR：从现有 order_no 反推当日最大流水，保证连续不重号
Cursor c = db.rawQuery(
    "SELECT substr(order_no,3,8) d, MAX(CAST(substr(order_no,11) AS INTEGER)) n "
  + "FROM fin_purchase_order WHERE order_no LIKE 'JR%' GROUP BY d", null);
while (c.moveToNext()) insertDocSeq(db, "JR", c.getString(0), c.getInt(1));
```

- `XS` / `FY` / `GZ` 不预置：首次生成时 `INSERT OR IGNORE` + 递增即可；
- 历史出货/费用/工资单 **不补号**（`doc_no` 留空），避免伪造历史；界面显示"—"。

______________________________________________________________________

## 7. S6：五项对账

```java
private void reconcileV17(SQLiteDatabase db, Map<String, Snapshot> before) {
    Map<String, Snapshot> after = new HashMap<>();
    Cursor c = db.rawQuery(
        "SELECT i.batch_id, COUNT(DISTINCT i.id), COUNT(it.id), "
      + "IFNULL(SUM(it.weight),0), IFNULL(SUM(it.amount),0), IFNULL(SUM(it.net_fee),0) "
      + "FROM fin_income i LEFT JOIN fin_income_item it ON it.income_id=i.id "
      + "GROUP BY i.batch_id", null);
    // ... 聚合到 Snapshot
    for (String batch : before.keySet()) {
        Snapshot b = before.get(batch), a = after.get(batch);
        check(batch, "单头数",   b.count,  a.count);
        check(batch, "明细行数", b.count,  a.itemCount);   // 1:1
        check(batch, "总重量",   b.weight, a.weight);
        check(batch, "货款合计", b.amount, a.amount);
        check(batch, "捞网费合计", b.netFee, a.netFee);
        check(batch, "实收合计",  b.actual, a.actual);      // 迁移不应改动实收
    }
}

private void check(String batch, String field, double before, double after) {
    if (Math.abs(before - after) > 0.005) {
        throw new IllegalStateException(String.format(Locale.CHINA,
            "v17 迁移对账失败：批次=%s 项目=%s 迁移前=%.4f 迁移后=%.4f",
            batch, field, before, after));
    }
}
```

**对账口径表**

| #   | 项目       | 迁移前                | 迁移后                                 |
| --- | ---------- | --------------------- | -------------------------------------- |
| 1   | 单头数     | `COUNT(*) fin_income` | `COUNT(DISTINCT income_id)`            |
| 2   | 明细行数   | 同上                  | `COUNT(*) fin_income_item`（必须 1:1） |
| 3   | 总重量     | `SUM(weight)`         | `SUM(item.weight)`                     |
| 4   | 货款合计   | `SUM(amount)`         | `SUM(item.amount)`                     |
| 5   | 捞网费合计 | `SUM(net_fee)`        | `SUM(item.net_fee)`                    |
| 6   | 实收合计   | `SUM(actual_amount)`  | `SUM(actual_amount)`（不变）           |

差异阈值 0.005 元（对应 `round2` 精度）。

______________________________________________________________________

## 8. 发布前的外部对账（可选但建议）

在电脑上对**导出的 .db 副本**跑同一套 SQL，绕开手机环境：

```
python tools/reconcile_v17.py backup.db
# 输出：每批次 6 项对比，全部一致打印 PASS
```

实现要点：`sqlite3` 只读打开，逐项打印 `batch_id / 项目 / 期望 / 实际 / 差值`，任一超阈值退出码 1。这条脚本不进 App，只作发版闸门。

______________________________________________________________________

## 9. 失败、回滚与备份

| 场景           | 行为                                                                                                            |
| -------------- | --------------------------------------------------------------------------------------------------------------- |
| S6 对账失败    | 抛 `IllegalStateException` → `onUpgrade` 事务回滚 → DB 仍是 v16、明细与标记一起回滚                             |
| 回滚后再次启动 | 版本仍是 16 → 重新进入 `case 16` → 幂等守卫保证不重复                                                           |
| 用户强制杀进程 | 事务未提交，SQLite 自动回滚；不会留下半迁移状态                                                                 |
| 迁移前备份     | 建议在**应用启动时**检测"上次版本 15/16" → 同步触发一次 `LocalBackupManager` 文件拷贝（几十毫秒），再打开数据库 |
| 迁移后数据异常 | 提示"升级未完成，请用最近备份恢复"，不要自动覆盖原库                                                            |

> 注意：`onUpgrade` 内不要做文件备份（同一数据库连接正在事务中），备份要放在打开数据库**之前**。

______________________________________________________________________

## 10. 自测矩阵（真机 + 单元测试）

| #   | 场景                         | 期望                                               |
| --- | ---------------------------- | -------------------------------------------------- |
| 1   | 全新安装（空库）             | v17 建表成功，`fin_income_item` 0 行，对账跳过     |
| 2   | v16 有 100 单（单规格单棚）  | 100 头 + 100 明细，六项对账 0，`tail_per_jin` 正确 |
| 3   | v16 多棚文本单（`3-5`）      | 1 条明细，`shed_id=0`，`shed_text=3-5`，不拆行     |
| 4   | v16 规格为"大虾"             | `tail_per_jin=0`，进待核对清单                     |
| 5   | v16 金额为 0 但重量/单价有值 | 保真为 0，进待核对清单，对账仍 0                   |
| 6   | v16 备份文件恢复到 v17 应用  | 走同一迁移，对账 0                                 |
| 7   | 把版本号手动改回 16 再启动   | 不重复插入明细（幂等）                             |
| 8   | 迁移中强杀进程               | 下次启动重新迁移，数据不重复不丢失                 |

**单元测试**（`app/src/test`，纯 JVM，不需要真机）：
`parseTailPerJin`（10 组样例）、`resolveSingleShed` 的判定（0/1/多棚）、`check()` 的阈值行为。

______________________________________________________________________

## 11. 涉及文件

| 文件                              | 改动                                                                                                                                                                      |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `DatabaseHelper.java`             | `case 16` 分支、`migrateV17 / snapshotIncome / backfillIncomeItems / reconcileV17 / ensureShedArchive / getFlag / setFlag`、`onOpen` 的 `ensureColumn` 登记、3 条建表常量 |
| `FinanceDb.java`                  | 新增 `getIncomeItems / saveIncomeWithItems`（明细读写），后续任务卡里完成                                                                                                 |
| `MyApplication.java`              | 启动时的"升级前备份"钩子                                                                                                                                                  |
| `mixcalc/model/ShedCoverage.java` | 复用，不改                                                                                                                                                                |
| `build.gradle`                    | 无改动                                                                                                                                                                    |

______________________________________________________________________

## 12. 上线步骤

1. 单元测试通过（`parseTailPerJin` / `resolveSingleShed` / `check`）；
2. Android Studio 构建 debug 包，真机跑自测矩阵 1–5；
3. 导入真实 v16 备份（矩阵 6），跑外部对账脚本；
4. 手动回退版本号测幂等（矩阵 7）；
5. 迁移中杀进程测回滚（矩阵 8）；
6. 全绿后进 T7–T10（出货开单页重做）。

> 遵守 `AGENTS.md`：不在命令行打包，release 签名与出包一律在 Android Studio 完成。
