# P1 财务扩展数据模型与迁移 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为应收应付/开单重构建立 v18 数据库 schema、迁移、备份钩子和 FinanceDb 基础数据访问/纯函数，不改变现有 UI 行为。

**Architecture:** 沿用现有 `DatabaseHelper` + `finance/*` 分层：`DatabaseHelper` 只做版本号、建表 SQL 常量、`onCreate/onUpgrade/onOpen/ensureColumn` 接线；新表/列定义放 `V18Schema`，数据迁移/标记放 `V18Migration`，升级前备份放 `backup/V18BackupHook`。AR/AP 可见性与金额汇总规则抽成纯 Java helper，供 JVM 单测与后续 P3/P5 UI 共用。

**Tech Stack:** Android Java, SQLiteOpenHelper/SQLiteDatabase, JUnit4 JVM tests (`app/src/test/java`), Gradle `testDebugUnitTest`.

**Spec:** `PLAN-应收应付与开单重构分期计划.md`（P0 A1-A14 已确认）、`AGENTS.md`、`~/.agents/skills/code-standards/SKILL.md`。

## Global Constraints

- DB 迁移只使用 `onUpgrade(SQLiteDatabase db, ...)` 传入的 `db`；禁止在迁移中通过 helper 打开新连接。
- `DatabaseHelper` 建表语句、`onUpgrade`、`onOpen/ensure*` 三处同步维护；新增列必须考虑老库自愈。
- 不改依赖、不改包名、不改签名配置。每期代码改动后运行 `.\gradlew.bat testDebugUnitTest`，0 失败后再打包。
- R2 函数 ≤ 50 行；R3 存量文件不净增长，新增逻辑优先新文件；R6 不吞异常（空 catch 禁止，迁移失败要可见）。
- 金额计算统一 `FinanceDb.round2`/显式 scale；不复制业务单据表达双边状态，用状态视图规则。
- 未发布无旧业务数据迁移负担（A1/A2/A7），但 v17->v18 升级路径必须安全、幂等、可备份。

## Review Focus

- **v17->v18 升级幂等：** 重复打开数据库或升级中断重试，不应重复插入 `fin_agent_customer`/结算记录，不应破坏已有 `fin_income`/`fin_seed` 行。
- **备份时机：** 备份必须在任何 `SQLiteOpenHelper.getWritableDatabase()` 触发升级前完成，只读探测版本，失败记录日志但不吞默导致数据错乱。
- **卖虾单可见性：** 同一 `fin_income` 在“货款结+捞网费未结”时，同时满足 AR/AP 捞网费应付视图和收入账目视图；全结只进收入；两未结只进 AR/AP。
- **随车结算语义：** `actual_amount` 保持原值；捞网费已结支出单独表达，净收入计算不得把实收重复扣减。
- **供应商/联系人类型扩展：** 新增 `supplier_kind`、捞网工/园区管理档案不能破坏现有 `PERSON_SUPPLIER/AGENT/CUSTOMER/EMPLOYEE` 查询与结算。

---

## File Map

- Create: `app/src/main/java/com/shrimpfarm/app/finance/V18Schema.java` — v18 表/列 DDL、常量、ensure。
- Create: `app/src/main/java/com/shrimpfarm/app/finance/V18Migration.java` — v18 迁移编排、幂等标记、必要回填。
- Create: `app/src/main/java/com/shrimpfarm/app/backup/V18BackupHook.java` — 升级前只读版本探测与 pre_v18 备份。
- Create: `app/src/main/java/com/shrimpfarm/app/finance/ArApLedgerRules.java` — 卖虾单三态可见性、AR/AP 行分类、金额计算纯函数。
- Create: `app/src/test/java/com/shrimpfarm/app/finance/V18SchemaTest.java` — 版本/表名/DDL 常量冻结。
- Create: `app/src/test/java/com/shrimpfarm/app/finance/V18MigrationTest.java` — 迁移标记/幂等纯逻辑（无 Android DB）。
- Create: `app/src/test/java/com/shrimpfarm/app/finance/ArApLedgerRulesTest.java` — 可见性与金额规则。
- Modify: `app/src/main/java/com/shrimpfarm/app/DatabaseHelper.java` — `DATABASE_VERSION` 17->18，`onCreate/onOpen/onUpgrade case 17` 接线，新表 SQL 常量与 `createFinanceTables` 同步。
- Modify: `app/src/main/java/com/shrimpfarm/app/MyApplication.java` — 启动早期调用 `V18BackupHook.run(this)`。
- Modify: `app/src/main/java/com/shrimpfarm/app/finance/FinanceDb.java` — 新常量、模型字段、CRUD/查询基础方法；保持旧方法兼容。

---

### Task 1: 冻结 v18 schema 常量与测试

**Files:**
- Create: `app/src/test/java/com/shrimpfarm/app/finance/V18SchemaTest.java`
- Create: `app/src/main/java/com/shrimpfarm/app/finance/V18Schema.java`
- Modify: `app/src/main/java/com/shrimpfarm/app/DatabaseHelper.java`
- Test: `app/src/test/java/com/shrimpfarm/app/finance/V18SchemaTest.java`

**Interfaces:**
- Consumes: 现有 `DatabaseHelper.TABLE_FIN_*` 命名风格、`V17Schema` 模式。
- Produces: `V18Schema.VERSION = 18`（或由 DatabaseHelper 断言）、表名常量 `TABLE_FIN_AGENT_CUSTOMER`, `TABLE_FIN_SETTLEMENT`, `TABLE_FIN_PARK`; 列常量/DDL 片段供 DatabaseHelper/迁移复用。

- [ ] **Step 1: Write the failing test**

```java
package com.shrimpfarm.app.finance;

import static org.junit.Assert.*;
import java.lang.reflect.Field;
import org.junit.Test;

public class V18SchemaTest {
    @Test
    public void versionAndTableNamesAreFrozen() throws Exception {
        assertEquals("fin_agent_customer", V18Schema.TABLE_FIN_AGENT_CUSTOMER);
        assertEquals("fin_settlement", V18Schema.TABLE_FIN_SETTLEMENT);
        assertEquals("fin_park", V18Schema.TABLE_FIN_PARK);
        Field v = Class.forName("com.shrimpfarm.app.DatabaseHelper")
                .getDeclaredField("DATABASE_VERSION");
        v.setAccessible(true);
        assertEquals(18, v.getInt(null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.shrimpfarm.app.finance.V18SchemaTest`
Expected: FAIL，`V18Schema`/表名常量或 DATABASE_VERSION 未达预期。

- [ ] **Step 3: Implement minimal schema constants**

在 `V18Schema.java` 定义表名常量、`createAll(SQLiteDatabase db)`、`ensureAll(SQLiteDatabase db)`。DDL 覆盖 A1-A14 所需基础字段：

```sql
-- fin_supplier
supplier_kind INTEGER DEFAULT 1 -- 1动保 2苗种

-- fin_agent_customer
id PK, agent_id INTEGER NOT NULL, customer_id INTEGER NOT NULL, create_time INTEGER DEFAULT 0, UNIQUE(agent_id,customer_id)

-- fin_income
net_fee_mode INTEGER DEFAULT 0        -- 0随车结算 1挂账
net_fee_unit_price REAL DEFAULT 0.35
net_fee_payee_id INTEGER DEFAULT 0    -- 捞网工联系人/档案ID
net_fee_payee_account_id INTEGER DEFAULT 0 -- 捞网工收款账户(contact account)

-- fin_seed
pay_account_id INTEGER DEFAULT 0
receive_account_id INTEGER DEFAULT 0
settle_type INTEGER DEFAULT 0         -- 0现结 1挂账
settled INTEGER DEFAULT 1
settle_date TEXT

-- fin_park (园区管理)
id PK, name TEXT, account_name TEXT, account_no TEXT, enabled INTEGER DEFAULT 1, create_time INTEGER DEFAULT 0

-- fin_settlement (全额结清留痕)
id PK, direction INTEGER NOT NULL, biz_type TEXT NOT NULL, biz_id INTEGER NOT NULL,
amount REAL DEFAULT 0, settle_date TEXT, pay_account_id INTEGER DEFAULT 0,
receive_account_id INTEGER DEFAULT 0, remark TEXT, create_time INTEGER DEFAULT 0
```

在 `DatabaseHelper`：把 `DATABASE_VERSION` 改为 `18`；`onCreate` 末尾调用 `V18Schema.createAll(db)`；`onOpen` 末尾调用 `V18Schema.ensureAll(db)`；`createFinanceTables`/建表常量同步新增三张表。

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.shrimpfarm.app.finance.V18SchemaTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/shrimpfarm/app/finance/V18SchemaTest.java app/src/main/java/com/shrimpfarm/app/finance/V18Schema.java app/src/main/java/com/shrimpfarm/app/DatabaseHelper.java
git commit -m "feat(finance): add v18 schema constants and tables"
```

### Task 2: v18 迁移与备份钩子

**Files:**
- Create: `app/src/test/java/com/shrimpfarm/app/finance/V18MigrationTest.java`
- Create: `app/src/main/java/com/shrimpfarm/app/finance/V18Migration.java`
- Create: `app/src/main/java/com/shrimpfarm/app/backup/V18BackupHook.java`
- Modify: `app/src/main/java/com/shrimpfarm/app/DatabaseHelper.java`
- Modify: `app/src/main/java/com/shrimpfarm/app/MyApplication.java`
- Test: `app/src/test/java/com/shrimpfarm/app/finance/V18MigrationTest.java`

**Interfaces:**
- Consumes: `V18Schema.createAll/ensureAll`, `DatabaseHelper.TABLE_BASIC_DATA` 标记模式（参考 V17Migration）。
- Produces: `V18Migration.migrate(SQLiteDatabase db)` 幂等；`V18BackupHook.run(Context)` 只读检查版本 `<18` 时备份到 `pre_v18/v{ver}_yyyyMMdd_HHmmss.db`。

- [ ] **Step 1: Write the failing test**

```java
public class V18MigrationTest {
    @Test
    public void migrationFlagsAreStable() {
        assertEquals("migrated_v18_finance_arap", V18Migration.FLAG_MIGRATED);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.shrimpfarm.app.finance.V18MigrationTest`
Expected: FAIL，类/常量不存在。

- [ ] **Step 3: Implement migration and backup hook**

`V18Migration.migrate(db)`：`createAll` -> `ensureAll` -> 若 flag 已设则 return -> 轻量回填（如 `net_fee_unit_price` 为空/0 时设 0.35 仅对已有 net_fee>0 行可选，**不得覆盖历史已结算语义**；A6 明确历史不回填，故默认不改旧 net_fee） -> 写 flag。使用 `insertWithOnConflict` 写 basic_data 标记，batch_id 空串。

`V18BackupHook` 复制 `V17BackupHook` 结构，改 `needsBackup(userVersion) -> userVersion < 18`，备份子目录 `pre_v18`，日志 tag `V18BackupHook`。保留 7 天清理。

`DatabaseHelper.onUpgrade` 添加 `case 17: V18Migration.migrate(db); break;`。`MyApplication.onCreate` 在 `V17BackupHook.run(this)` 之前或之后调用 `V18BackupHook.run(this)`（任一即可；v17<18 会触发）。

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.shrimpfarm.app.finance.V18MigrationTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/shrimpfarm/app/finance/V18MigrationTest.java app/src/main/java/com/shrimpfarm/app/finance/V18Migration.java app/src/main/java/com/shrimpfarm/app/backup/V18BackupHook.java app/src/main/java/com/shrimpfarm/app/DatabaseHelper.java app/src/main/java/com/shrimpfarm/app/MyApplication.java
git commit -m "feat(finance): add v18 migration and pre-upgrade backup hook"
```

### Task 3: FinanceDb 模型与基础 CRUD

**Files:**
- Modify: `app/src/main/java/com/shrimpfarm/app/finance/FinanceDb.java`
- Modify: `app/src/main/java/com/shrimpfarm/app/DatabaseHelper.java`（如需列常量）
- Test: `app/src/test/java/com/shrimpfarm/app/finance/V18SchemaTest.java`（可扩展断言常量）

**Interfaces:**
- Produces (additive, backward compatible):
  - `FinSupplier.supplierKind` (`int`, 1动保2苗种), `insertSupplier/updateSupplier/getSuppliers` 重载或扩展参数；旧签名保留并默认 `supplierKind=1`。
  - `FinIncome.netFeeMode/netFeeUnitPrice/netFeePayeeId/netFeePayeeAccountId`；`insertIncome/updateIncome/getIncomes/searchIncomes` 读写新列。
  - `FinSeed.payAccountId/receiveAccountId/settleType/settled/settleDate`；相关 insert/update/get 方法兼容。
  - 新模型 `FinAgentCustomer`, `FinSettlement`, `FinPark` 及基础 CRUD：`linkAgentCustomer`, `unlinkAgentCustomer`, `getAgentCustomers`, `getCustomerAgents`, `insertSettlement`, `listSettlements`, `insertPark/updatePark/listParks/getPark`.

- [ ] **Step 1: Write failing JVM tests for pure helpers first (Task 4) or add small API compile tests**

若无 Android DB 测试框架，不写 DB 集成测试；用 Task 4 纯函数测试覆盖规则。此任务以编译+现有测试回归为门槛。

- [ ] **Step 2: Add model fields and DB constants**

在 `DatabaseHelper` 增加列名常量：`COLUMN_FIN_SUPPLIER_KIND`, `COLUMN_FIN_NET_FEE_MODE`, `COLUMN_FIN_NET_FEE_UNIT_PRICE`, `COLUMN_FIN_NET_FEE_PAYEE_ID`, `COLUMN_FIN_NET_FEE_PAYEE_ACCOUNT_ID`, `COLUMN_FIN_PAY_ACCOUNT_ID`, `COLUMN_FIN_SETTLE_DATE`, `COLUMN_FIN_DIRECTION`, `COLUMN_FIN_BIZ_TYPE`, `COLUMN_FIN_BIZ_ID` 等；在 `create/ensure` SQL 中使用。

- [ ] **Step 3: Implement additive FinanceDb APIs**

保留旧方法签名；新签名放在旧签名旁。所有新 SQL 读取用 `isNull` 防老列缺省。`FinIncome` 默认 `netFeeUnitPrice=0.35`, `netFeeMode=0`。`FinSeed` 默认 `settled=true, settleType=0` 保持现状兼容。

- [ ] **Step 4: Run unit tests**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: PASS（现有测试不回归）。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/shrimpfarm/app/DatabaseHelper.java app/src/main/java/com/shrimpfarm/app/finance/FinanceDb.java
git commit -m "feat(finance): add v18 FinanceDb models and CRUD foundations"
```

### Task 4: AR/AP 可见性与金额纯函数

**Files:**
- Create: `app/src/test/java/com/shrimpfarm/app/finance/ArApLedgerRulesTest.java`
- Create: `app/src/main/java/com/shrimpfarm/app/finance/ArApLedgerRules.java`
- Test: `app/src/test/java/com/shrimpfarm/app/finance/ArApLedgerRulesTest.java`

**Interfaces:**
- Produces:
  - `ArApLedgerRules.Visibility` 或布尔方法：`showInArApForGoods(boolean paid)`, `showInArApForNetFee(boolean netFeePaid)`, `showInIncomeLedger(boolean goodsPaid, boolean netFeePaid)`。
  - `ArApLedgerRules.incomeGoodsAmount(double actualAmount, boolean paid)` / `incomeNetFeePayable(double netFee, boolean netFeePaid)`。
  - 规则实现 A5/A12：货款未结->ARAP；捞网费未结->ARAP应付；货款结+捞网费未结->ARAP应付 + 收入账目；全结->收入账目。实收保持原值。

- [ ] **Step 1: Write the failing tests**

```java
@Test public void incomeVisibility_threeStates() {
    assertTrue(ArApLedgerRules.showInArApForGoods(false));
    assertTrue(ArApLedgerRules.showInArApForNetFee(false));
    assertFalse(ArApLedgerRules.showInIncomeLedger(false, false));

    assertFalse(ArApLedgerRules.showInArApForGoods(true));
    assertTrue(ArApLedgerRules.showInArApForNetFee(false));
    assertTrue(ArApLedgerRules.showInIncomeLedger(true, false));

    assertFalse(ArApLedgerRules.showInArApForGoods(true));
    assertFalse(ArApLedgerRules.showInArApForNetFee(true));
    assertTrue(ArApLedgerRules.showInIncomeLedger(true, true));
}

@Test public void actualAmountStaysOriginalForCarSettle() {
    assertEquals(1000.0, ArApLedgerRules.goodsIncomeActual(1000.0), 0.0001);
    assertEquals(35.0, ArApLedgerRules.netFeeExpense(100.0, 0.35), 0.0001);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.shrimpfarm.app.finance.ArApLedgerRulesTest`
Expected: FAIL。

- [ ] **Step 3: Implement minimal pure rules**

实现纯静态方法，不访问 DB/Android。所有金额 `FinanceDb.round2` 或同等 round2。

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.shrimpfarm.app.finance.ArApLedgerRulesTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/shrimpfarm/app/finance/ArApLedgerRulesTest.java app/src/main/java/com/shrimpfarm/app/finance/ArApLedgerRules.java
git commit -m "feat(finance): add AR/AP visibility pure rules"
```

### Task 5: 全量回归与 P1 验收

**Files:**
- Modify: none expected; fix regressions if found.

- [ ] **Step 1: Run full JVM tests**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 2: Build debug APK**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL；产物 `app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **Step 3: Record P1 verification notes**

在 `PLAN-应收应付与开单重构分期计划.md` 或执行记录中补充 P1 已完成项、未覆盖项（真机 v17->v18 升级、备份文件、AR/AP UI）。

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore(finance): verify P1 schema foundation"
```

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-11-28-p1-finance-schema-foundation.md`. Please review the plan. Which execution approach would you prefer?

- **Subagent-driven** - A fresh subagent implements each task and a fresh reviewer checks it before the next one starts, then a whole-branch review at the end. Most thorough; costs a fresh context per task and per review.
- **Native** - I implement every task myself in this session, the way this harness runs work, then one fresh reviewer on the most capable model checks the whole branch. Cheapest and fastest; no independent review until the end. Runs well with a mid-tier session model, since the plan carries the design.

**For this plan I recommend Subagent-driven, because the tasks touch shared DB migration/schema contracts and later UI phases depend on these field names and visibility rules, so a bad P1 contract is expensive to unwind.** Does the plan capture what you want, and which approach should we use?
