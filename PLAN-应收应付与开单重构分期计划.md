# 应收应付与开单重构分期实施计划

> 状态：P0 口径已确认（待进入实施）
> 日期：2026-11-28
> 基线：`refactor/mixcalc-table @ 406f205`，v17 出货单迁移/T1-T5 已完成
> 约束：遵循 `AGENTS.md` / `code-standards`（R2/R3/R6/R13）；DB 迁移只用 `onUpgrade(db)` 传入的 `db`；不改依赖；每期 `.\gradlew.bat testDebugUnitTest` 0 失败后再打包。

## 1. 已确认决策

| 项 | 决策 |
|---|---|
| 结算模式 | 全额结清。卖虾单保留**货款/捞网费两条结算线**，可分别结清。 |
| AR/AP 范围 | 所有未结挂账进入应收应付中心：卖虾货款未结、采购/动保未结、捞网费未结。结清后进入各自账目。 |
| 卖虾单可见性 | 同一业务单，不复制数据。两者未结→仅应收应付；货款结+捞网费未结→应收应付中心（捞网费应付）+收入账目；两者都结→仅收入账单。 |
| 商品模型 | 供应商商品模型（`FinSupplierProduct` 方向），建档必选供应商+品类。 |
| 供应商属性 | 单选必填：`动保` / `苗种`。现有供应商迁移需给默认值。 |

## 2. 默认假设（需确认，确认前不进入对应实现）

> A1-A8 若有异议，在实现前改本计划；实现后改动需补迁移。

- **A1 供应商类型字段（已确认）**：`fin_supplier.supplier_kind`，`1=动保，2=苗种`，UI 新增必选；不复用 `personType`。当前未发布，无旧数据迁移压力，不做存量默认迁移。
- **A2 商品归属与建档（已确认）**：不采用“全局商品”概念。商品必须挂在供应商下；品类必选；规格可选/可选填。当前未发布，无历史数据兼容负担。实现上可复用/调整现有 `FinSupplierProduct` 结构，但用户心智与入口以“供应商商品”为准。
- **A3 中介-客户关联（已确认）**：新增关联表 `fin_agent_customer(agent_id, customer_id, UNIQUE)`；**一个客户可关联多个中介，一个中介可关联多个客户**；关联时可从现有客户选择，也可快速新增客户。
- **A4 应收应付结算金额（已确认）**：全额结清。UI 显示“本次结算金额”且默认=应结金额，锁定全额，不做部分结算。
- **A5 捞网费挂账双边（已确认）**：不复制单据。`fin_income` 保留/扩展货款结算状态与捞网费结算状态；应付视图查询“捞网费未结”记录；收入视图按第 1 节可见性规则过滤。
- **A6 捞网费单价可编辑（已确认）**：默认 0.35 元/斤，直接手动编辑单价数值（非下拉），无需选粒度；修改后自动重算捞网费。**历史数据保持原值不回填**。需同步更新 Part B 口径中“D2 固定 0.35 元/斤”的表述为“默认 0.35，可手动修改”，并明确对账/利润口径。
- **A7 支出分类映射（已确认）**：当前未发布，无旧数据迁移负担。P4 不做历史数据迁移；代码仍保留清晰的分类常量/映射层，便于后续扩展。
- **A8 卖虾单布局“一行两个可选单元格”（已确认）**：除备注外，卖虾记录表单字段全部改为一行两个单元格；备注单独一行。明细行仍按 v17 `fin_income_item` 结构，具体字段顺序 P5 实现时按现有字段整理。
- **A9 捞网工管理（已确认）**：管理板块新增“捞网工管理”，用于建立捞网工档案；在捞网费结算选择捞网工收款账户时，也支持直接新建捞网工档案/账户。捞网工收款账户来源于捞网工档案关联账户。
- **A10 动保开单无历史价（已确认）**：无历史价格照样允许保存；保存后本次价格成为历史，第二次开单可自动带出。
- **A11 苗种开单流程（已确认方向）**：先选苗种供应商；供应商下挂虾苗商品；开单时选择供应商、选择虾苗商品、填写数量（如万尾）和单价，自动计算金额；实际付款可去零头；选择收款账户（供应商）和付款账户（养殖场）。与现有 `FinSeed`/采购模型的落地方式在 P0 定稿。

## 3. 分期总览

| 期 | 目标 | 主要产出 | 依赖 | 预估 |
|---|---:|---|---|---:|
| P0 | 口径冻结 | 确认 A1-A8、更新 Part B/ARAP 口径文档 | 无 | 0.5d |
| P1 | 数据模型与迁移 | DB v18+ schema、迁移、纯函数、单测 | P0 | 2-3d |
| P2 | 档案与基础管理 | 供应商动保/苗种、供应商商品建档流程、中介关联客户 | P1 | 2d |
| P3 | 应收应付中心 | AR/AP 汇总页、两 tab 明细、全额结算、状态回写 | P1,P2 | 3d |
| P4 | 支出板块重构 | 新一级/二级分类、旧数据映射、新表单入口 | P1 | 2-3d |
| P5 | 开单与卖虾记录 | 动保/苗种开单、卖虾记录 v17 UI、捞网费结算线 | P1,P2,P3 | 3-4d |
| P6 | 验收与发布 | 全量测试、真机矩阵、打包、对账闸门 | P2-P5 | 1-2d |

## 4. P0 - 口径冻结

### 目标
把剩余会影响数据模型/统计口径的细节变成书面决策。

### 已确认（A1-A14）
- A1：`supplier_kind` 取值 `1=动保，2=苗种`，UI 必选；无旧数据迁移压力。
- A2：不采用全局商品概念；商品必须挂供应商，品类必选，规格可选；无历史数据兼容负担。
- A3：中介-客户多对多；可选现有客户，也可快速新增客户。
- A4：应收应付全额结清，结算金额锁定全额。
- A5：捞网费挂账双边为同一单据状态视图，不复制数据。
- A6：捞网费单价直接手动编辑，默认 0.35，历史不回填。
- A7：未发布，无旧数据迁移负担。
- A8：卖虾记录除备注外全部一行两个单元格。
- A9：管理板块新增“捞网工管理”建立捞网工档案；捞网费结算选捞网工收款账户时支持直接新建捞网工档案/账户；收款账户来源于捞网工档案。
- A10：动保开单无历史价格照样保存；保存后本次价格成为历史，下次可带出。
- A11：苗种开单先选苗种供应商，再选虾苗商品、填数量（万尾）/单价自动算金额，实际付款可去零头，选择供应商收款账户和养殖场付款账户。
- **A12 随车结算字段语义（已确认）**：`actual_amount`（实收）保持原值不变；捞网费另记为已结支出，产生捞网费支出+卖虾收入两笔账务，并在净收入中体现。
- **A13 苗种开单落地模型（已确认）**：复用现有 `FinSeed` 单据模型，补充付款账号、收款账号字段；现结/挂账流向按应收应付规则处理。
- **A14 园区管理账户模型（已确认）**：管理板块新增“园区管理”档案（园区主体+收款账户）。电费、地租都作为支出账目，录入时选日期范围（覆盖周期）、金额、付款账户（养殖场）、收款账户（园区管理），复用同一套园区收款方。

### 待确认细节
- 无（P0 口径已确认）。

## 5. P1 - 数据模型与迁移

### 目标
建立新功能所需 schema 和兼容迁移，不改 UI 行为。

### 建议 DB 变更（v18 起，按实际拆版本）

```sql
-- 供应商子类型
ALTER TABLE fin_supplier ADD COLUMN supplier_kind INTEGER DEFAULT 1; -- 1动保 2苗种

-- 中介-客户关联
CREATE TABLE IF NOT EXISTS fin_agent_customer (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  agent_id INTEGER NOT NULL,
  customer_id INTEGER NOT NULL,
  create_time INTEGER DEFAULT 0,
  UNIQUE(agent_id, customer_id)
);

-- 卖虾捞网费结算扩展（示例，最终以 P0 定稿）
ALTER TABLE fin_income ADD COLUMN net_fee_mode INTEGER DEFAULT 0; -- 0随车结算 1挂账
ALTER TABLE fin_income ADD COLUMN net_fee_payee_id INTEGER DEFAULT 0;
ALTER TABLE fin_income ADD COLUMN net_fee_payee_account_id INTEGER DEFAULT 0;
ALTER TABLE fin_income ADD COLUMN net_fee_unit_price REAL DEFAULT 0.35;

-- 应收应付结算记录（全额结清也建议留痕）
CREATE TABLE IF NOT EXISTS fin_settlement (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  direction INTEGER NOT NULL, -- 1应收 2应付
  biz_type TEXT NOT NULL,     -- income_goods/income_net_fee/purchase/...
  biz_id INTEGER NOT NULL,
  amount REAL DEFAULT 0,
  settle_date TEXT,
  pay_account_id INTEGER DEFAULT 0,
  receive_account_id INTEGER DEFAULT 0,
  remark TEXT,
  create_time INTEGER DEFAULT 0
);

-- 支出分类重构（如采用新表）
CREATE TABLE IF NOT EXISTS fin_expense_category (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  parent_id INTEGER DEFAULT 0,
  name TEXT,
  code TEXT,
  sort_order INTEGER DEFAULT 0,
  enabled INTEGER DEFAULT 1
);
```

> 以上为草案；最终字段以 P0 冻结后定稿。禁止在实现前把草案当已确认。

### 代码任务
1. `DatabaseHelper`：版本号递增、`onCreate`/`onUpgrade`/`ensure*` 三同步；迁移只用传入 `db`。
2. `FinanceDb`：新增模型/CRUD/查询；旧 API 保持兼容。
3. 纯函数抽离：AR/AP 聚合、可见性判定、分类映射、金额计算放 `finance/*` helper，便于 JVM 单测。
4. 迁移备份：复用 `V17BackupHook` 模式，升级前备份。

### 测试
- JVM：可见性规则、分类映射、金额 round2、供应商 kind 默认值、settlement 记录生成。
- 回归：`testDebugUnitTest` 全绿。
- 设备验收前置：备份文件生成、旧库升级不崩溃。

### 验收
- 新装/升级库表齐全。
- 旧数据不丢，供应商默认 kind 可用。
- 无 UI 行为变化或仅兼容性变化。

## 6. P2 - 档案与基础管理

### 目标
完成供应商/商品/中介基础档案改造。

### 涉及文件（预估）
- `finance/SupplierManageActivity.java`
- `finance/SupplierProductActivity.java`
- `finance/ProductManageActivity.java`（或调整入口）
- `finance/FinanceDb.java`
- `res/layout/activity_finance_supplier_manage.xml`, `item_finance_supplier.xml`, `dialog_*`
- `res/values/strings.xml`

### 任务
1. **供应商新增/编辑**：第一步必选动保/苗种；列表展示类型标签；筛选支持类型。
2. **供应商商品入口**：供应商卡片内“供应商商品”保留；商品建档流程改为先选供应商、品类，下拉末项“新增供应商/新增品类”。
3. **中介关联客户**：中介卡片按钮“供应商商品”改“关联客户”；新页面/弹窗可选择现有客户、快速新增客户、解除关联。
4. **捞网工管理**：管理板块新增捞网工档案与收款账户；捞网费结算时可快捷新建。
5. **园区管理**：新增园区主体/收款账户管理，供电费、地租复用同一套园区收款方。

### 测试/验收
- 新增动保/苗种供应商必选类型。
- 供应商商品可新增，供应商/品类缺失时可快捷新增。
- 中介可关联客户并回显；不影响现有供应商/员工/客户管理。

## 7. P3 - 应收应付中心

### 目标
用“应收应付”替换“待结算赊账”，统一未结账单视图与全额结算。

### 涉及文件（预估）
- `finance/FinanceActivity.java` + `activity_finance.xml`
- 新增 `finance/ReceivablePayableActivity.java`（或改造 SettleActivity）
- `erp/SettleActivity.java`（兼容/下线策略待定）
- `finance/IncomeActivity.java`、`PickingFeeActivity.java` 过滤规则
- `FinanceDb` 查询与结算事务

### 任务
1. **首页入口**：`待结算赊账` 改 `应收应付`；卡片显示“应收款X条，合计¥xx；应付款X条，合计¥xx”。
2. **明细页**：横向两标签 `应收账单` / `应付账单`，默认应付或按 P0 定；列表展示单号、类型、对象、日期、金额、状态。
3. **去结算**：弹窗显示账单明细、收款账户、付款账户、实际结算金额（全额锁定）；确认后写 `fin_settlement`，更新业务单结算状态。
4. **可见性规则**：实现卖虾单三态可见性；货款/捞网费分开结算。
5. **兼容旧入口**：`SettleActivity`、`PickingFeeActivity` 保留或重定向，避免旧路径漏结。

### 测试/验收
- 应收/应付条数合计正确。
- 卖虾单：未结仅AR/AP；货款结捞网费未结→AR/AP+收入；全结→仅收入。
- 结算后账单从 AR/AP 消失，进入对应账目；事务失败不写半条。

## 8. P4 - 支出板块重构

### 目标
按新分类重排支出，并接入新开单/关联单据。

### 新结构

| 一级 | 二级 | 数据来源 |
|---|---|---|
| 生活支出 | 伙食相关、交通相关、杂项 | `fin_expense` |
| 设施设备维护 | 塘口维护、机械维护、房屋维护 | `fin_expense` |
| 养殖成本 | 动保开单、苗种开单、工资发放、捞网费、电费、地租 | ticket/seed/wage/income.net_fee/expense |

### 任务
1. `FinanceExpenseActivity` 重构 tab/subtab；按新一级/二级分类组织。
2. 普通支出表单按二级分类保存，保留日期/金额/备注/账户。
3. 动保开单入口跳转 `TicketEditActivity`（供应商过滤动保）。
4. 苗种开单：复用 `FinSeed`，补充付款账号/收款账号字段；供应商过滤苗种；现结/挂账按应收应付规则处理。
5. 工资发放关联工资单（`SalaryActivity`/`FinWage`）。
6. 捞网费不新增，只关联未结/已结捞网费记录。
7. 电费/地租：选择日期范围（覆盖周期）、金额、养殖场付款账户、园区收款账户；复用园区管理档案。

### 测试/验收
- 新分类录入后在列表/利润/报表口径正确。
- 未发布无旧数据迁移负担；若后续出现存量数据，再补映射迁移。

## 9. P5 - 开单与卖虾记录

### 目标
落地动保开单和卖虾记录的新交互。

### P5A 动保开单（`TicketEditActivity`）
1. 首行供应商：Hint“请选择供应商”，下拉末项“添加供应商”；仅显示动保供应商（或按 P0）。
2. 第二行日期+票号：日期默认当天，可点选；票号手填。
3. 明细横向可滑动：列对齐，品类/品名规格点击下拉；无选项可快捷新增。
4. 价格逻辑：有历史价自动带出并计算金额；无历史价不自动填账户/金额（是否允许保存按 P0）。
5. 底部合计 + 收款账户（供应商）+付款账户（养殖场）+现结/挂账。
6. 现结→进入对应支出账目；挂账→进入应收应付。

### P5B 卖虾记录（`IncomeEditActivity` / 新明细 UI）
1. 表单改为一行两格布局；底部中介+客户同排两选择框。
2. 捞网费单价可编辑，默认 0.35 元/斤；自动计算捞网费。
3. 捞网费下方选择：随车结算（`actual_amount` 实收保持原值；另记捞网费已结支出，产生捞网费支出+卖虾收入两笔账务）/挂账。
4. 挂账：同一单据进入应付款视图和收入账目视图，显示未结捞网费，旁边结算按钮；结算必须选收款账户（捞网工人账户）和付款账户（养殖场账户）。
5. 基于 v17 `fin_income + fin_income_item` 明细模型实现，不回退旧单行逻辑。

### 测试/验收
- 动保开单：现结入支出，挂账入应付；合计正确。
- 卖虾单：两列布局、捞网费单价编辑、随车结算/挂账、两条结算线状态正确。
- 货款已结+捞网费未结的可见性符合第 1 节规则。

## 10. P6 - 验收、回归与发布

### 测试矩阵
1. 单元测试：`.\gradlew.bat testDebugUnitTest` 全绿。
2. 迁移：v16/v17 真实库升级到新版本，备份生成，关键表/列/数据存在。
3. AR/AP：应收/应付条数合计、全额结算、卖虾三态可见性、捞网费挂账双边视图。
4. 开单：动保/苗种供应商过滤、快捷新增、现结/挂账流向。
5. 卖虾：捞网费单价编辑、随车结算扣减、挂账结算账户必选。
6. 支出：新分类录入、利润/报表口径正确。

### 发布闸门
- 关键金额计算有 JVM 单测。
- 无空 catch/吞异常（R6）。
- DB 迁移幂等、可回滚（升级前备份）。
- 真机安装验证；若同包名旧签名先卸载。

## 11. 风险清单

| 风险 | 影响 | 缓解 |
|---|---|---|
| 部分结算未来需求 | 若后续要首付款分期，当前 settlement 模型可能不够 | P1 settlement 表预留金额/余额扩展位，但 P3 只做全额 |
| 捞网费单价可编辑与 K1/K6 冲突 | 历史对账、利润口径变化 | P0 明确是否纳入对账；历史不回填；版本说明标注 |
| 卖虾单双边视图双写 | 数据不一致 | 单一业务单+状态视图，不复制记录 |
| 支出分类调整 | 统计口径错乱 | 当前未发布无旧数据迁移负担；仍统一分类常量/统计口径，避免后续存量数据问题 |
| UI 大改回归 | 老用户路径丢失 | 保留旧入口重定向一版，真机矩阵覆盖 |

## 12. 建议执行顺序

1. **先确认 P0 待确认细节**（A1-A8 已确认）。
2. 从 **P1** 开始，不跳过迁移和测试。
3. P3/P5 涉及卖虾单状态规则，必须共用同一套可见性函数，避免页面各算各的。
4. 每期结束都打 debug 包给你真机测，再进下一期。
## 13. P1 执行记录（2026-11-28）

- **基线**：`feat/p1-finance-schema-v18 @ f0e0a7421ad40e094a23216802bac2aa8f3a2842`（Task 1-4 完成）。
- **全量 JVM 测试**：`.\gradlew.bat :app:testDebugUnitTest` BUILD SUCCESSFUL，315 tests / 0 failures / 0 errors / 0 skipped（48 test classes）。
- **Debug APK**：`.\gradlew.bat :app:assembleDebug` BUILD SUCCESSFUL，产物 `app/build/outputs/apk/debug/app-debug.apk`（279,388,583 bytes）。

### 已完成项（P1 范围）

- v18 schema/迁移基础：`fin_supplier.supplier_kind`、`fin_agent_customer`、`fin_income` 捞网费结算扩展字段、`fin_settlement`、`fin_expense_category`（见 Task 1/2/3 交付）。
- FinanceDb v18 模型与 CRUD 基础、AR/AP 可见性纯规则、ArApLedgerRules amount 接口方法（见 Task 3/4）。
- 迁移前备份钩子沿用 V17BackupHook 模式（Task 1）。

### 未覆盖项 / 已知缺口

- **真机 v17->v18 升级**：未在本次任务执行，需真机安装验证升级不崩溃、关键表/列/数据存在。
- **备份文件**：未验证升级前备份文件实际生成与内容；需真机/真实库验收。
- **AR/AP UI**：本期为 schema/foundation，无应收应付中心 UI、结算交互、卖虾单三态可见性 UI；按计划属于 P3/P5。
- **自动化缺口（已接受）**：v18 迁移/备份/FinanceDb 新 CRUD 缺少 Android DB 行为自动化测试；v17->v18 真实设备升级、pre_v18 备份文件、7 天清理、真实备份/对账待用户验收。V17SchemaTest 版本断言更新为已接受维护项。FinanceDb R3 债务按 R10 豁免记录。
