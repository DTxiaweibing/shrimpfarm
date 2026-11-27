# Part B T1+T2 数据闭环实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `FeedingRecord.db` 从 v16 升到 v17，出货单迁移为「单头 + 明细」并通过六项对账；不碰任何 UI。

**Architecture:** 迁移逻辑独立到 `com.shrimpfarm.app.finance` 下的三个小类（解析 / 对账 / 编排），`DatabaseHelper` 只保留三处单行接线（版本号、`onCreate`、`onOpen`、`case 16`），避免让 2468 行的存量文件继续膨胀。所有迁移只用 `onUpgrade` 传入的 `SQLiteDatabase`，全程单事务；备份在应用启动、打开数据库之前完成。

**Tech Stack:** Android / Java、SQLiteOpenHelper、JUnit 4 (`junit:junit:4.13.2`)、Python 3 标准库 `sqlite3` + `unittest`（仅发版脚本）。

**Spec:**
- `D:\shrimpfarm\PLAN-记录表自绘与财务板块改造方案.md` Part B §0.5（K1–K10，唯一权威）、§3、§4、§5
- `D:\shrimpfarm\PLAN-出货单迁移与对账方案.md` §0–§12（S0–S7、DDL、对账 SQL、自测矩阵）

## Global Constraints

- `DATABASE_VERSION = 16 → 17`；`case 16` 是最后一个分支，循环 `for (int i = oldVersion; i < newVersion; i++)` 不变。
- 六项对账（K1）：单头数、明细行数、总重量、货款合计、捞网费合计、实收合计。金额 `abs(差异) ≤ 0.005` 通过，数量必须完全相等，超标抛 `IllegalStateException` 整体回滚。
- 历史 `amount` / `net_fee` 原值保真（K2），禁止按 0.35 重算；公式只用于新录入。
- 多棚 `shed_no` 只生成 **1 条明细**（K4），原文存 `shed_text`，`shed_id=0`，不拆行、不自动建多棚档案。
- 规格正则（K5）`(\d+(?:\.\d+)?)\s*尾?\s*/?\s*斤?`，斤可选、值域 `0 < v < 1000`；区间取首值并把原文记入 `fin_income_item.spec_range`。
- 不设 `UNIQUE(car_no, date)`（K3），同一车号同日可多单。
- 兼容列 `specs / weight / price` 原值写回首条明细的对应列（K8）。
- 升级前备份在 `MyApplication.onCreate()`、任何数据库打开之前（K9）；v16 副本保留 7 天。
- 迁移内只用传入的 `db`：**禁止** `getWritableDatabase()`、`DatabaseHelper.getInstance()`、`new FinanceDb(...)`、弹 UI。
- 代码规范：R2 单方法 ≤50 有效行、R3 新文件 ≤500 行、R6 新代码不许空 catch（记 `Log.w`）、R13 改逻辑必带测试且测试与源码同目录、R14 不改依赖/lockfile。
- 全量单测基线：`gradlew testDebugUnitTest` = 286/0；每个任务结束必须仍为 0 失败。
- 命令行打包按项目根 `AGENTS.md` 执行（`gradlew assembleDebug` 可用、签名已配置）；迁移文档 §12 末尾"不在命令行打包"一句以 `AGENTS.md` 为准。
- 本计划**不含** K6（利润应计口径）、K7（报表文案）、T3/T4 的 UI 与档案页、T7–T19；它们另立计划。

## Review Focus

以下五类规格暗示但现有代码/测试完全没覆盖的输入，最可能在真机出事；每条已在对应任务里钉了测试或验收：

1. **历史 `amount=0` 但 `weight>0 且 price>0`** → 必须保真为 0 并进待核对清单，不得"顺手重算"。Task 3 自测矩阵 #5 + `v17_fixup_json` 断言。
2. **多棚文本 `3-5`** → 必须 1 条明细、`shed_id=0`、`shed_text="3-5"`，不得拆成 3 行。Task 1 `V17ParseTest.singleShedNumberMultiReturnsZero` + Task 3 矩阵 #3。
3. **规格区间 `20-30尾/斤`** → `value=20` 且 `range` 保留原文，不得取均值或丢弃。Task 1 `V17ParseTest.rangeKeepsFirstValueAndOriginal`。
4. **对账差异恰好 0.005 / 0.006** → 前者通过、后者抛异常；数量差异 1 即失败。Task 1 `V17ReconcileCheckTest.thresholdBoundary`。
5. **版本号被手动改回 16 后重启** → 幂等守卫必须阻止重复明细。Task 3 矩阵 #7（双守卫：标记 + `fin_income_item` 行数）。

---

## 文件结构（先锁定边界）

| 文件 | 职责 |
| --- | --- |
| `app/src/main/java/com/shrimpfarm/app/finance/V17Parse.java`（新建） | 纯逻辑：规格解析、棚号单值判定、异常数据判定 |
| `app/src/main/java/com/shrimpfarm/app/finance/V17Reconcile.java`（新建） | `Snapshot`、快照查询、六项对账、阈值校验 |
| `app/src/main/java/com/shrimpfarm/app/finance/V17Migration.java`（新建） | S0–S7 编排、回填、档案关联、流水初始化、标记读写 |
| `app/src/main/java/com/shrimpfarm/app/finance/V17Schema.java`（新建） | 3 张新表 DDL、13 条加列的幂等兜底；只被 `DatabaseHelper` 调用 |
| `app/src/main/java/com/shrimpfarm/app/backup/V17BackupHook.java`（新建） | 启动前版本探测、v16 文件副本、7 天清理 |
| `app/src/main/java/com/shrimpfarm/app/DatabaseHelper.java`（改） | 仅 4 处：版本号、`onCreate` 一行、`onOpen` 一行、`case 16` 三行 |
| `app/src/main/java/com/shrimpfarm/app/MyApplication.java`（改） | `onCreate` 开头一行调用备份钩子 |
| `tools/reconcile_v17.py`（新建） | 发版闸门：两个 .db 的六项明细对比 |
| 4 个测试文件 | 与源码同目录（`app/src/test/java/...`）；Python 测试放 `tools/` |

---

### Task 1: 纯逻辑解析与对账阈值（V17Parse / V17Reconcile 的纯函数部分）

**Files:**
- Create: `app/src/main/java/com/shrimpfarm/app/finance/V17Parse.java`
- Create: `app/src/main/java/com/shrimpfarm/app/finance/V17Reconcile.java`
- Test: `app/src/test/java/com/shrimpfarm/app/finance/V17ParseTest.java`
- Test: `app/src/test/java/com/shrimpfarm/app/finance/V17ReconcileCheckTest.java`

**Interfaces:**
- Consumes: `ShedCoverage.parseShedNumbers(String) -> List<Integer>`、`ShedCoverage.MAX_SHED_NUMBER == 99`（`app/src/main/java/com/shrimpfarm/app/mixcalc/model/ShedCoverage.java`，复用不改）
- Produces:
  - `V17Parse.SpecParse`（`public final double value; public final String range;`）
  - `static SpecParse parseSpec(String specs)`、`static double parseTailPerJin(String specs)`
  - `static int singleShedNumber(String shedNo)`（0 = 空/多棚/超限，>0 = 唯一棚号）
  - `static boolean needsAmountFixup(double amount, double weight, double price)`
  - `V17Reconcile.Snapshot`（`long count, itemCount; double weight, amount, netFee, actual;`）
  - `static void check(String batch, String field, double before, double after)`
  - `static void checkCount(String batch, String field, long before, long after)`

- [ ] **Step 1: 写失败测试 `V17ParseTest.java`**

```java
package com.shrimpfarm.app.finance;

import static org.junit.Assert.*;
import org.junit.Test;

public class V17ParseTest {
    @Test public void plainSpecs() {
        assertEquals(30.0, V17Parse.parseTailPerJin("30尾/斤"), 0);
        assertEquals(30.0, V17Parse.parseTailPerJin("30 尾/斤"), 0);
        assertEquals(30.0, V17Parse.parseTailPerJin("30/斤"), 0);
        assertEquals(30.0, V17Parse.parseTailPerJin("30"), 0);
        assertEquals(12.5, V17Parse.parseTailPerJin("12.5尾/斤"), 0);
    }
    @Test public void rangeKeepsFirstValueAndOriginal() {
        V17Parse.SpecParse p = V17Parse.parseSpec("20-30尾/斤");
        assertEquals(20.0, p.value, 0);
        assertEquals("20-30尾/斤", p.range);
    }
    @Test public void invalidSpecsReturnZero() {
        assertEquals(0.0, V17Parse.parseTailPerJin("大虾"), 0);
        assertEquals(0.0, V17Parse.parseTailPerJin(""), 0);
        assertEquals(0.0, V17Parse.parseTailPerJin("0尾/斤"), 0);
        assertEquals(0.0, V17Parse.parseTailPerJin("1000尾/斤"), 0);
    }
    @Test public void singleShedNumberMultiReturnsZero() {
        assertEquals(3, V17Parse.singleShedNumber("3"));
        assertEquals(0, V17Parse.singleShedNumber("3-5"));
        assertEquals(0, V17Parse.singleShedNumber("1,3,5"));
        assertEquals(0, V17Parse.singleShedNumber("A棚"));
        assertEquals(0, V17Parse.singleShedNumber("100"));
    }
    @Test public void amountFixupOnlyWhenZeroWithBothFactors() {
        assertTrue(V17Parse.needsAmountFixup(0, 10, 4.5));
        assertFalse(V17Parse.needsAmountFixup(45, 10, 4.5));
        assertFalse(V17Parse.needsAmountFixup(0, 10, 0));
    }
}
```

- [ ] **Step 2: 写失败测试 `V17ReconcileCheckTest.java`**

```java
package com.shrimpfarm.app.finance;

import static org.junit.Assert.*;
import org.junit.Test;

public class V17ReconcileCheckTest {
    @Test public void thresholdBoundary() {
        V17Reconcile.check("B1", "货款合计", 100, 100.005);  // 不抛
        try {
            V17Reconcile.check("B1", "货款合计", 100, 100.006);
            fail("差异 0.006 必须抛异常");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("货款合计"));
        }
        V17Reconcile.checkCount("B1", "明细行数", 3, 3);
        try {
            V17Reconcile.checkCount("B1", "明细行数", 3, 2);
            fail("数量必须完全相等");
        } catch (IllegalStateException expected) { }
    }
}
```

- [ ] **Step 3: 运行确认失败**

Run: `gradlew testDebugUnitTest --tests "com.shrimpfarm.app.finance.*"`
Expected: 编译失败（`cannot find symbol: V17Parse / V17Reconcile`）

- [ ] **Step 4: 实现两个类（只写纯函数，`Snapshot` 字段先建好）**

`V17Parse.parseSpec` 用 `Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*尾?\\s*/?\\s*斤?")` + `m.find()`，命中后解析 `group(1)`；`0 < v < 1000` 之外视为失败；`SpecParse.range` 在旧文本含 `-`、`到`、`至`、`~` 时保存原文本，否则为 `""`。`singleShedNumber` 调 `ShedCoverage.parseShedNumbers`，只有 `size()==1` 且 `<= MAX_SHED_NUMBER` 时返回该号。`V17Reconcile.check` 用 `Math.abs(before-after) > 0.005` 抛 `IllegalStateException`（消息含批次、项目、前后值），`checkCount` 用 `before != after` 抛同类异常。每个方法 ≤50 有效行。

- [ ] **Step 5: 运行确认通过**

Run: `gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL，286 + 6 个测试全过，0 失败

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/shrimpfarm/app/finance/V17Parse.java app/src/main/java/com/shrimpfarm/app/finance/V17Reconcile.java app/src/test/java/com/shrimpfarm/app/finance/V17ParseTest.java app/src/test/java/com/shrimpfarm/app/finance/V17ReconcileCheckTest.java
git commit -m "feat(finance): add pure v17 spec/shed parsers and six-item reconcile guards"
```

---

### Task 2: DDL 三同步（V17Schema + DatabaseHelper 最小接线）

**Files:**
- Create: `app/src/main/java/com/shrimpfarm/app/finance/V17Schema.java`
- Modify: `app/src/main/java/com/shrimpfarm/app/DatabaseHelper.java:33`（版本号）、`:725-737`（`onCreate`）、`:739-753`（`onOpen`）
- Test: `app/src/test/java/com/shrimpfarm/app/finance/V17SchemaTest.java`

**Interfaces:**
- Consumes: Task 1 的 `V17Reconcile.Snapshot`（本任务不涉及）
- Produces:
  - `V17Schema.TABLE_FIN_INCOME_ITEM = "fin_income_item"`、`TABLE_SHED_ARCHIVE = "shed_archive"`、`TABLE_FIN_DOC_SEQ = "fin_doc_seq"`
  - `V17Schema.createAll(SQLiteDatabase db)`、`V17Schema.ensureAll(SQLiteDatabase db)`
  - `DatabaseHelper.DATABASE_VERSION == 17`

- [ ] **Step 1: 写失败测试**

```java
package com.shrimpfarm.app.finance;

import static org.junit.Assert.*;
import java.lang.reflect.*;
import org.junit.Test;

public class V17SchemaTest {
    @Test public void tableNamesAndVersionAreFrozen() throws Exception {
        assertEquals("fin_income_item", V17Schema.TABLE_FIN_INCOME_ITEM);
        assertEquals("shed_archive", V17Schema.TABLE_SHED_ARCHIVE);
        assertEquals("fin_doc_seq", V17Schema.TABLE_FIN_DOC_SEQ);
        Field v = Class.forName("com.shrimpfarm.app.DatabaseHelper")
                .getDeclaredField("DATABASE_VERSION");
        v.setAccessible(true);
        assertEquals(17, v.getInt(null));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `gradlew testDebugUnitTest --tests "com.shrimpfarm.app.finance.V17SchemaTest"`
Expected: 编译失败（`cannot find symbol: V17Schema`）

- [ ] **Step 3: 新建 `V17Schema.java`**

3 张表按迁移文档 §3：`fin_income_item` 含 `id, income_id, shed_id, shed_text, tail_per_jin, weight, price, amount, net_fee, sort_order, spec_range TEXT`；`shed_archive` 含 `id, name, sort_order, enabled` + `UNIQUE(name)` 索引；`fin_doc_seq` 主键 `(prefix, doc_date)`。`createAll` 执行 3 条 CREATE + `idx_fin_income_item` / `idx_shed_name` 两条索引（全部 `IF NOT EXISTS`）。`ensureAll` 用 `PRAGMA table_info` 逐列兜底 13 条 ALTER：`fin_income.doc_no`；`fin_purchase_order.pay_type/settle_date/settle_account_id/supplier_doc_no/supplier_doc_date`；`fin_expense.payee_id/account_id/doc_no`；`fin_wage.doc_no`；`fin_supplier.address/bank_account/bank_name`。类型照 §3 原文。新代码的 catch 用 `Log.w("V17Schema", "ensure " + table + "." + column, e)`，不许空 catch。**不添加任何 `UNIQUE(car_no, date)` 约束**（K3）。

- [ ] **Step 4: `DatabaseHelper` 只加 3 行**

`DATABASE_VERSION` 改 17；`onCreate` 末尾（`createFinanceTables(db);` 之后）加 `V17Schema.createAll(db);`；`onOpen` 末尾加 `V17Schema.ensureAll(db);`。不改任何既有 `CREATE TABLE`、不改 `ensureColumn()`、不加 `case 16`（ Task 3 加）。删掉测试里用到的 import 警告即可，不做其他重构。

- [ ] **Step 5: 运行确认通过**

Run: `gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL，0 失败

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/shrimpfarm/app/finance/V17Schema.java app/src/main/java/com/shrimpfarm/app/DatabaseHelper.java app/src/test/java/com/shrimpfarm/app/finance/V17SchemaTest.java
git commit -m "feat(finance): add v17 schema constants and three-sync DDL wiring"
```

---

### Task 3: 迁移流水线 S0–S7（V17Migration + 对账查询 + case 16）

**Files:**
- Create: `app/src/main/java/com/shrimpfarm/app/finance/V17Migration.java`
- Modify: `app/src/main/java/com/shrimpfarm/app/finance/V17Reconcile.java`（加快照与对账查询）
- Modify: `app/src/main/java/com/shrimpfarm/app/DatabaseHelper.java:997`（`case 16`）
- Test: `app/src/test/java/com/shrimpfarm/app/finance/V17MigrationFlagTest.java`

**Interfaces:**
- Consumes: `V17Schema.createAll/ensureAll`（Task 2）、`V17Parse.*`（Task 1）、`V17Reconcile.check/checkCount/Snapshot`（Task 1）、`ShedCoverage.parseShedNumbers`
- Produces:
  - `static void V17Migration.migrate(SQLiteDatabase db)`
  - `V17Migration.FLAG_MIGRATED = "migrated_v17_income_items"`、`V17Migration.FLAG_FIXUP_JSON = "v17_fixup_json"`
  - `static String V17Migration.getFlag(SQLiteDatabase db, String key)`、`static void putFlag(SQLiteDatabase db, String key, String value)`
  - `static Map<String, V17Reconcile.Snapshot> V17Reconcile.snapshotIncome(SQLiteDatabase db)`
  - `static void V17Reconcile.reconcileV17(SQLiteDatabase db, Map<String, Snapshot> before)`

- [ ] **Step 1: 写失败测试（标记键名冻结）**

```java
package com.shrimpfarm.app.finance;

import static org.junit.Assert.*;
import org.junit.Test;

public class V17MigrationFlagTest {
    @Test public void flagNamesAreFrozen() {
        assertEquals("migrated_v17_income_items", V17Migration.FLAG_MIGRATED);
        assertEquals("v17_fixup_json", V17Migration.FLAG_FIXUP_JSON);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `gradlew testDebugUnitTest --tests "com.shrimpfarm.app.finance.V17MigrationFlagTest"`
Expected: 编译失败（`cannot find symbol: V17Migration`）

- [ ] **Step 3: 实现 `V17Migration.java`（S0–S7）**

`migrate(db)` 顺序严格按规格 §2：`V17Schema.createAll(db)` → `V17Schema.ensureAll(db)` → `snapshotIncome` → 幂等守卫（`FLAG_MIGRATED` 为 `"1"` 或 `fin_income_item` 行数 > 0 即 return）→ `backfillIncomeItems` → `initDocSeq` → `reconcileV17` → `putFlag(FLAG_FIXUP_JSON, json)` → `putFlag(FLAG_MIGRATED, "1")`。快照失败直接抛；其余异常不吞，交给外层事务回滚。

`backfillIncomeItems`：`SELECT id, shed_no, specs, weight, price, net_fee, amount FROM fin_income ORDER BY id`，一条旧单插一条明细，`amount/net_fee` 用 `getDouble` 原值（K2），`weight/price/specs` 同时写回明细的兼容列（K8）；`tail_per_jin` 取 `V17Parse.parseSpec(specs).value`，`spec_range` 取 `.range`；`shed_id` 仅当 `V17Parse.singleShedNumber(shedNo) > 0` 时调 `ensureShedArchive` 取回 id，否则 0。用预编译 `SQLiteStatement` + `db.beginTransaction()/setTransactionSuccessful()/endTransaction()`。定长字符串字段落 `basic_data` 的 `key` 用 `DatabaseHelper.COLUMN_BD_KEY`（值为 `` `key` ``），`batch_id` 传 `""`，`insertWithOnConflict(..., CONFLICT_REPLACE)`。

异常数据只记 JSON 清单不改写：`spec_unparsed`（`tail=0` 且 specs 非空）、`amount_zero`（用 `V17Parse.needsAmountFixup`）、`net_fee_zero`（`netFee==0 && weight>0`）。JSON 拼接需转义引号/反斜杠（`esc` 私有方法）。

`initDocSeq`：执行规格 §6 的 `JR` 反推 SQL，`INSERT OR REPLACE` 到 `fin_doc_seq`；`XS/FY/GZ` 不预置；历史单据 `doc_no` 留空。

方法切分：`migrate` / `backfillIncomeItems` / `initDocSeq` / `resolveSingleShed` / `ensureShedArchive` / `getFlag` / `putFlag` / `tableCount` / `esc` 各一个方法，任一方法 ≤50 有效行。

- [ ] **Step 4: 实现 `V17Reconcile` 的 DB 部分**

`Snapshot` 六字段见 Task 1。`snapshotIncome(db)` 按 `batch_id` 分组聚合旧单头（SQL 照规格 §4，含 `IFNULL`）。`reconcileV17(db, before)` 按规格 §7 的 LEFT JOIN SQL 聚合 after，再逐批调用 `check`（重量/货款/捞网费/实收）与 `checkCount`（单头数、**明细行数 = 单头数**）。空 `before`（全新安装）直接跳过。

- [ ] **Step 5: 接线 `case 16`**

在 `DatabaseHelper.onUpgrade` 的 `case 15` 之后加：

```java
case 16:
    V17Migration.migrate(db);
    break;
```

不改 `case 1–15`，不引入 `getWritableDatabase()` / `getInstance()`。

- [ ] **Step 6: 验证**

Run: `gradlew testDebugUnitTest` → 0 失败
Run: `gradlew assembleDebug` → BUILD SUCCESSFUL（签名包，按 `AGENTS.md`）
真机自测矩阵（用户侧，`adb devices` 当前为空，本环境无法自动跑）：迁移文档 §10 的 8 个场景，重点 #3 多棚不拆行、#5 金额保真、#7 幂等、#8 强杀回滚。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/shrimpfarm/app/finance/V17Migration.java app/src/main/java/com/shrimpfarm/app/finance/V17Reconcile.java app/src/main/java/com/shrimpfarm/app/DatabaseHelper.java app/src/test/java/com/shrimpfarm/app/finance/V17MigrationFlagTest.java
git commit -m "feat(finance): migrate v16 incomes to header+items with six-item reconciliation"
```

---

### Task 4: 升级前备份钩子（V17BackupHook + MyApplication 一行）

**Files:**
- Create: `app/src/main/java/com/shrimpfarm/app/backup/V17BackupHook.java`
- Modify: `app/src/main/java/com/shrimpfarm/app/MyApplication.java:130-137`（`onCreate` 开头）
- Test: `app/src/test/java/com/shrimpfarm/app/backup/V17BackupHookTest.java`

**Interfaces:**
- Consumes: `LocalBackupManager.getLegacyBackupDir()`（`app/src/main/java/com/shrimpfarm/app/backup/LocalBackupManager.java:38`，public）
- Produces: `static void V17BackupHook.run(android.content.Context context)`、`static boolean needsBackup(int userVersion)`、`static boolean isExpired(long fileTime, long nowMs, long retainMs)`

- [ ] **Step 1: 写失败测试**

```java
package com.shrimpfarm.app.backup;

import static org.junit.Assert.*;
import org.junit.Test;

public class V17BackupHookTest {
    @Test public void onlyPreV17NeedsBackup() {
        assertTrue(V17BackupHook.needsBackup(15));
        assertTrue(V17BackupHook.needsBackup(16));
        assertFalse(V17BackupHook.needsBackup(17));
    }
    @Test public void sevenDayRetentionBoundary() {
        long day = 24L * 3600 * 1000;
        assertTrue(V17BackupHook.isExpired(0, 8 * day, 7 * day));
        assertFalse(V17BackupHook.isExpired(2 * day, 8 * day, 7 * day));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `gradlew testDebugUnitTest --tests "com.shrimpfarm.app.backup.V17BackupHookTest"`
Expected: 编译失败（`cannot find symbol: V17BackupHook`）

- [ ] **Step 3: 实现钩子**

`run(context)`：取 `context.getDatabasePath("FeedingRecord.db")`，文件不存在则 return；用 `SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)` 读 `PRAGMA user_version`（只读打开**不会触发** `SQLiteOpenHelper.onUpgrade`），关闭后 `needsBackup(v)` 为 false 则 return。副本写到 `new File(new LocalBackupManager(context).getLegacyBackupDir(), "pre_v17")`，文件名 `v16_<yyyyMMdd_HHmmss>.db`，用 `FileInputStream`/`FileOutputStream` 原样拷贝（**不调** `LocalBackupManager.exportToLocal()`，它内部会打开 helper 并触发升级）。拷贝成功后清理该目录内 `isExpired(lastModified, now, 7天)` 的文件。异常记 `Log.w` 后 return，绝不阻断启动。

- [ ] **Step 4: 接线 `MyApplication.onCreate`**

在 `super.onCreate();` 之后、`Thread.setDefaultUncaughtExceptionHandler(...)` 之前加一行 `V17BackupHook.run(this);`。此为全应用最早的数据库相关动作，确保在任何 helper 打开前完成。

- [ ] **Step 5: 运行确认通过**

Run: `gradlew testDebugUnitTest`
Expected: 0 失败
真机验收：装 v16 旧包产生数据 → 装新包启动 → `Download/ShrimpFarmBackup/pre_v17/` 出现 `v16_*.db`；第二次启动不再新增副本。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/shrimpfarm/app/backup/V17BackupHook.java app/src/main/java/com/shrimpfarm/app/MyApplication.java app/src/test/java/com/shrimpfarm/app/backup/V17BackupHookTest.java
git commit -m "feat(finance): back up pre-v17 database before any helper opens it"
```

---

### Task 5: 发版闸门脚本（tools/reconcile_v17.py）

**Files:**
- Create: `tools/reconcile_v17.py`
- Test: `tools/test_reconcile_v17.py`

**Interfaces:**
- Consumes: 两个 SQLite 文件（v16 备份、升级后的 v17 库）
- Produces: CLI `python tools/reconcile_v17.py <v16.db> <v17.db>`；一致打印每批 6 项 PASS，任一金额差异 > 0.005 或数量不等时退出码 1

- [ ] **Step 1: 写失败测试**

`tools/test_reconcile_v17.py` 用 `sqlite3` + `tempfile` 建两个库：旧库 `fin_income(batch_id, weight, amount, net_fee, actual_amount)`；新库同表 + `fin_income_item(income_id, weight, amount, net_fee)`。断言 `compare(old, new) == []`；再把一条明细 `amount` 改成 `+0.01`，断言返回项含 `"货款合计"`；把明细行数删 1，断言返回项含 `"明细行数"`。

- [ ] **Step 2: 运行确认失败**

Run: `python tools/test_reconcile_v17.py`
Expected: `ModuleNotFoundError` / `ImportError`（脚本尚不存在）

- [ ] **Step 3: 实现脚本**

`compare(old_path, new_path) -> list[str]`：只读打开两库；按 `batch_id` 分别算 6 项（单头数、明细行数、总重量、货款、捞网费、实收），金额用 `abs(diff) > 0.005`、数量用 `!=`；返回差异描述列表。`__main__` 调 `compare` 并打印，非空则 `sys.exit(1)`。只用标准库。

- [ ] **Step 4: 运行确认通过**

Run: `python tools/test_reconcile_v17.py`
Expected: `OK`（3 个测试通过）

- [ ] **Step 5: 提交**

```bash
git add tools/reconcile_v17.py tools/test_reconcile_v17.py
git commit -m "feat(finance): add offline six-item reconcile gate for v17 release"
```

---

## 完成定义（本计划）

1. `gradlew testDebugUnitTest` 全绿（286 + 新增测试，0 失败）。
2. `gradlew assembleDebug` 产出可安装签名包。
3. 真机按迁移文档 §10 的 8 场景跑完（用户侧设备；本环境 `adb devices` 为空）。
4. 用真实 v16 备份跑 `python tools/reconcile_v17.py <v16.db> <v17.db>` 退出码 0。
5. `git status` 干净，5 个提交已推 `origin` / `gitee` 的 `refactor/mixcalc-table`。

## 已知不在本计划内

- K6 `calcProfit` 应计口径、K7 报表文案（T10）。
- T3 规格输入页、T4 棚号档案页、T7 出货开单页重写（UI 层）。
- T15–T19 单号 UI、结算方式、筛选、导出、双语。
- 升级后 `FinanceDb.getIncomeItems / saveIncomeWithItems` 的明细读写 API（T7 一并做）。
