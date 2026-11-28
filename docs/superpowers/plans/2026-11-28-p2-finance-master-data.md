# P2 Finance Master Data Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver P2 基础资料与配置: supplier kind selection/filtering, supplier-product filing with quick-add category/supplier, agent-customer association UI, net-worker management, and park management UI.

**Architecture:** Build on P1 v18 schema/models (`supplierKind`, `fin_agent_customer`, `fin_park`, `FinAgentCustomer`, `FinPark`, FinanceDb CRUD already present). Keep changes additive in finance UI/DB access layer; no new DB migration unless a real schema gap is found (prefer v18 tables/columns already landed). Extract small pure helpers for kind/filter/label mapping where JVM-testable; Android UI behavior is verified by compile + manual smoke because project has no Robolectric/instrumentation DB harness.

**Tech Stack:** Android Java (app module), SQLite via `DatabaseHelper`/`FinanceDb`, AndroidX AppCompat/AlertDialog/ListView XML layouts, JUnit4 JVM tests (`:app:testDebugUnitTest`), Gradle wrapper.

**Spec:** `PLAN-应收应付与开单重构分期计划.md` §1, §4 (A1-A14), §6 (P2). Also honor P1 decisions in `docs/superpowers/plans/2026-11-28-p1-finance-schema-foundation.md` and §13 execution record.

## Global Constraints

- **Branch/workflow:** work on `feat/p2-finance-master-data` from `main`; Subagent-driven execution preferred; merge target `main` only after verification/user confirmation.
- **DB safety:** no destructive migration; DB migrations only via `onUpgrade(db)` with the passed `db`; keep `DatabaseHelper` `onCreate` / `onUpgrade` / `onOpen/ensure*` in sync. P2 should avoid schema changes; if unavoidable, add new version with three-sync and backup hook pattern.
- **No dependency/package/signature changes.** Do not touch Gradle deps, applicationId, signing, keystore.
- **R2:** functions <= 50 effective lines. **R3:** existing files must not net-grow in effective lines; if unavoidable, add `standards-exempt: 存量...` in first 5 lines with specific reason. **R6:** no swallowed exceptions. **R10:** exempt only allowed categories with comment. **R12:** no secrets, validate input at boundaries. **R13:** logic changes get tests; tests same directory as finance tests.
- **A1:** `supplier_kind` `1=动保,2=苗种`, UI required for suppliers. **A2:** no global product concept; product must belong to supplier, category required, spec optional; no historical-data compatibility burden. **A3:** agent-customer many-to-many; pick existing or quick-create customer. **A9:** net-worker archive + receiving accounts; settlement quick-create is later (P3/P5) - P2 provides management/archive. **A14:** park management archive (park subject + receiving account), reused by electricity/rent in P4.
- **Verification gate:** `.\gradlew.bat :app:testDebugUnitTest` 0 failures before commit/pack; `.\gradlew.bat :app:assembleDebug` for packaging when needed. Do not commit `.hermes/`, temp dex, build outputs.

## Review Focus

- **Supplier kind default/regression:** existing supplier edit paths must preserve `supplierKind`; non-supplier person types must not be forced into kind UI/filter behavior.
- **Agent-customer isolation:** associating customers to agents must not affect supplier/employee/customer/person management records or unrelated M2M data; unlink must only remove the selected relation.
- **Quick-add boundaries:** "新增供应商/新增品类" quick-add must create/select the new record in the current flow without duplicating records or silently ignoring invalid empty names.
- **Net-worker account reuse:** net-worker management should reuse contact account archive patterns; accounts must belong to the selected net-worker and not leak into unrelated person flows.
- **No P3-P5 scope creep:** P2 must not implement AR/AP settlement, expense board refactor, ticket/sale income settlement behavior, or new settlement quick-create dialogs beyond management/archive entries.

---

## File Map

- Modify: `app/src/main/java/com/shrimpfarm/app/finance/FinanceDb.java` — add `PERSON_NET_WORKER` constant, supplier-kind filter/query helpers, any small archive helpers needed by P2; keep R3 exempt comment updated if touched.
- Modify: `app/src/main/java/com/shrimpfarm/app/finance/SupplierManageActivity.java` — supplier kind required selector, kind tag, kind filter, agent "关联客户" action, net-worker type label support.
- Modify: `app/src/main/java/com/shrimpfarm/app/finance/SupplierProductActivity.java` — category required/quick-add in add/edit flow; supplier quick-add path where flow allows; keep supplier context.
- Modify: `app/src/main/java/com/shrimpfarm/app/finance/ProductManageActivity.java` — align with A2 by making product filing supplier/category-aware or clearly routing to supplier-product flow; avoid global-only concept.
- Create: `app/src/main/java/com/shrimpfarm/app/finance/AgentCustomerActivity.java` — agent-customer association screen/dialog-backed list.
- Create: `app/src/main/java/com/shrimpfarm/app/finance/ParkManageActivity.java` — park archive CRUD UI over `FinanceDb.FinPark`.
- Modify: `app/src/main/java/com/shrimpfarm/app/finance/FinanceActivity.java` — add net-worker and park management entries.
- Modify: `app/src/main/res/layout/activity_finance.xml` — buttons for 捞网工管理/园区管理.
- Modify: `app/src/main/res/layout/activity_finance_supplier_manage.xml` — optional kind filter row.
- Modify: `app/src/main/res/layout/item_finance_supplier.xml` — kind tag display (or reuse existing text line).
- Create: `app/src/main/res/layout/activity_finance_agent_customer.xml`, `item_finance_agent_customer.xml` (if list screen chosen).
- Create: `app/src/main/res/layout/activity_finance_park_manage.xml`, `item_finance_park.xml`, `dialog_finance_park.xml` (or simple dialog layout).
- Modify: `app/src/main/res/values/strings.xml` — all P2 labels.
- Modify: `app/src/main/AndroidManifest.xml` — register new activities.
- Test: `app/src/test/java/com/shrimpfarm/app/finance/SupplierKindRulesTest.java` — pure rules for kind labels/filtering/validation.
- Test (optional pure): `app/src/test/java/com/shrimpfarm/app/finance/AgentCustomerRulesTest.java` only if we extract pure logic; otherwise cover via code review + compile and document gap.
- Modify: `PLAN-应收应付与开单重构分期计划.md` — add §14 P2 execution record when complete.

## Task 1: P2 Branch Baseline and Pure Supplier-Kind Rules

**Files:**
- Create: `app/src/test/java/com/shrimpfarm/app/finance/SupplierKindRulesTest.java`
- Create: `app/src/main/java/com/shrimpfarm/app/finance/SupplierKindRules.java`
- Modify: `app/src/main/java/com/shrimpfarm/app/finance/FinanceDb.java` (only if constant/helper placement requires; prefer new helper to avoid R3 growth)

**Interfaces:**
- Produces: `SupplierKindRules.KIND_MED=1`, `KIND_SEED=2`, `isValidKind(int)`, `normalizeKind(int)`, `labelResKey`/mapping helper usable by UI; optional `matchesFilter(int supplierKind, int filter)` where filter `0=all`.

- [ ] **Step 1: Write the failing test**

```java
package com.shrimpfarm.app.finance;

import static org.junit.Assert.*;
import org.junit.Test;

public class SupplierKindRulesTest {
    @Test public void kindConstantsAndValidation() {
        assertEquals(1, SupplierKindRules.KIND_MED);
        assertEquals(2, SupplierKindRules.KIND_SEED);
        assertTrue(SupplierKindRules.isValidKind(1));
        assertTrue(SupplierKindRules.isValidKind(2));
        assertFalse(SupplierKindRules.isValidKind(0));
    }

    @Test public void normalizeAndFilter() {
        assertEquals(1, SupplierKindRules.normalizeKind(99));
        assertTrue(SupplierKindRules.matchesFilter(1, 0));
        assertTrue(SupplierKindRules.matchesFilter(2, 0));
        assertTrue(SupplierKindRules.matchesFilter(1, 1));
        assertFalse(SupplierKindRules.matchesFilter(1, 2));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.shrimpfarm.app.finance.SupplierKindRulesTest`
Expected: FAIL (class not found / symbols missing).

- [ ] **Step 3: Implement minimal `SupplierKindRules`**

Create `SupplierKindRules.java` with constants and pure methods. Keep it small and R2-compliant. `normalizeKind` returns 1 for invalid values (A1 default). `matchesFilter(kind, filter)` returns true when filter==0 or kind==filter.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.shrimpfarm.app.finance.SupplierKindRulesTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/shrimpfarm/app/finance/SupplierKindRulesTest.java app/src/main/java/com/shrimpfarm/app/finance/SupplierKindRules.java
git commit -m "feat(finance): add supplier kind rules for P2"
```

## Task 2: Supplier Kind UI in Supplier Management

**Files:**
- Modify: `app/src/main/java/com/shrimpfarm/app/finance/SupplierManageActivity.java`
- Modify: `app/src/main/java/com/shrimpfarm/app/finance/FinanceDb.java` (only if needed for filtered query; prefer in-memory filter to avoid R3 growth)
- Modify: `app/src/main/res/layout/activity_finance_supplier_manage.xml`
- Modify: `app/src/main/res/layout/item_finance_supplier.xml` (if kind tag needs dedicated view; else reuse name line)
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `SupplierKindRules`, `FinanceDb.FinSupplier.supplierKind`, existing `insertSupplier/updateSupplier(..., supplierKind)`.
- Produces: supplier create/edit requires kind when `filterType==PERSON_SUPPLIER`; list shows 动保/苗种 tag; filter row visible only for suppliers with All/动保/苗种.

- [ ] **Step 1: Add strings and layout filter row**

Add strings: `finance_supplier_kind`, `finance_supplier_kind_med`, `finance_supplier_kind_seed`, `finance_filter_all`, `finance_supplier_kind_required`. Add a `Spinner`/filter row in `activity_finance_supplier_manage.xml` below `tv_total`, `visibility="gone"` by default. Add a kind tag `TextView` in `item_finance_supplier.xml` or include kind in display.

- [ ] **Step 2: Implement kind selection in edit dialog**

In `showEditDialog`, when `filterType==PERSON_SUPPLIER`, add a required kind selector (Spinner/RadioGroup) defaulting to existing supplier kind or MED. On save, pass selected kind to `insertSupplier/updateSupplier` overloads that include `supplierKind`. For non-supplier person types, keep existing behavior and kind default.

- [ ] **Step 3: Implement kind tag and filter**

In `loadData/getView`, for suppliers show `[动保]`/`[苗种]` tag. Add filter state `supplierKindFilter` (0 all,1 med,2 seed); when supplier screen, show filter row and reload/apply filter. Keep employee/supplier unpaid stats behavior intact.

- [ ] **Step 4: Verify compile/tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.shrimpfarm.app.finance.SupplierKindRulesTest`
Run: `.\gradlew.bat :app:compileDebugJavaWithJavac`
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/shrimpfarm/app/finance/SupplierManageActivity.java app/src/main/res/layout/activity_finance_supplier_manage.xml app/src/main/res/layout/item_finance_supplier.xml app/src/main/res/values/strings.xml
git commit -m "feat(finance): add supplier kind selection and filter"
```

## Task 3: Supplier Product Filing Quick-Add (Category/Supplier)

**Files:**
- Modify: `app/src/main/java/com/shrimpfarm/app/finance/SupplierProductActivity.java`
- Modify: `app/src/main/java/com/shrimpfarm/app/finance/ProductManageActivity.java`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/layout/dialog_finance_supplier_product.xml` only if needed

**Interfaces:**
- Consumes: `FinanceDb.getCategories/insertCategory/getSuppliersByType/getProducts`, existing supplier-product CRUD.
- Produces: category dropdown includes final "新增品类..." option; selecting it prompts name, inserts category, refreshes and selects it. Product filing flow makes supplier/category selection explicit where applicable. Empty name is rejected with toast; no silent duplicate.

- [ ] **Step 1: Write failing JVM test only if pure helper extracted**

If you extract category/supplier option-list or validation helper, add a small test first. If no pure helper is extracted, document in commit/plan note that UI flow is compile/manual verified (no Robolectric).

- [ ] **Step 2: Implement category quick-add in SupplierProductActivity**

In category adapter/build, append "新增品类..."/"新增品类" sentinel. On selection, show input dialog; on OK with non-empty name call `financeDb.insertCategory(name)`, reload categories, select new one. Ensure save requires category per A2/P2 acceptance (if category missing, prompt quick-add). Preserve existing spec/unit/price behavior.

- [ ] **Step 3: Align ProductManageActivity with A2**

Adjust product management so it does not present a global-only concept as primary path. Minimum acceptable P2: from ProductManageActivity, creating/filing a product guides user to pick supplier and category (or routes into SupplierProductActivity with supplier picker). Include quick-add "新增供应商/新增品类" options in pickers. Avoid breaking existing entry from FinanceActivity.

- [ ] **Step 4: Verify**

Run: `.\gradlew.bat :app:compileDebugJavaWithJavac`
Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL / 0 failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/shrimpfarm/app/finance/SupplierProductActivity.java app/src/main/java/com/shrimpfarm/app/finance/ProductManageActivity.java app/src/main/res/values/strings.xml app/src/main/res/layout/dialog_finance_supplier_product.xml
git commit -m "feat(finance): add quick-add supplier/category in product filing"
```

## Task 4: Agent-Customer Association

**Files:**
- Create: `app/src/main/java/com/shrimpfarm/app/finance/AgentCustomerActivity.java`
- Create: `app/src/main/res/layout/activity_finance_agent_customer.xml`
- Create: `app/src/main/res/layout/item_finance_agent_customer.xml` (if needed)
- Modify: `app/src/main/java/com/shrimpfarm/app/finance/SupplierManageActivity.java`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `FinanceDb.linkAgentCustomer/unlinkAgentCustomer/getAgentCustomers`, `getSuppliersByType(PERSON_CUSTOMER)`, `insertSupplier` for quick-create customer.
- Produces: for agent rows, button "供应商商品" becomes "关联客户" and opens `AgentCustomerActivity`. Screen shows linked customers, allows pick existing customer, quick-create customer, unlink.

- [ ] **Step 1: Add strings/layout/manifest**

Add strings: `finance_agent_customer_manage`, `finance_agent_customer_link`, `finance_agent_customer_unlink`, `finance_pick_customer`, `finance_quick_add_customer`, `finance_customer_linked`, `finance_customer_unlinked`. Create simple list layout with add/link button. Register activity.

- [ ] **Step 2: Implement AgentCustomerActivity**

Intent extras: `agentId`, `agentName`. Load linked customers via `financeDb.getAgentCustomers(agentId)` then resolve customer names via `getSupplierById`. Add action to pick existing customers (multi/single select from PERSON_CUSTOMER). Add quick-create customer dialog (name required; creates PERSON_CUSTOMER via `insertSupplier`, then links). Unlink with confirmation.

- [ ] **Step 3: Wire SupplierManageActivity agent card**

When `filterType==PERSON_AGENT` or row is agent, change `tvProducts` text to "关联客户" and click opens `AgentCustomerActivity`. Do not affect supplier/employee behavior.

- [ ] **Step 4: Verify**

Run: `.\gradlew.bat :app:compileDebugJavaWithJavac`
Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL / 0 failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/shrimpfarm/app/finance/AgentCustomerActivity.java app/src/main/java/com/shrimpfarm/app/finance/SupplierManageActivity.java app/src/main/res/layout/activity_finance_agent_customer.xml app/src/main/res/layout/item_finance_agent_customer.xml app/src/main/res/values/strings.xml app/src/main/AndroidManifest.xml
git commit -m "feat(finance): add agent-customer association management"
```

## Task 5: Net Worker Management

**Files:**
- Modify: `app/src/main/java/com/shrimpfarm/app/finance/FinanceDb.java`
- Modify: `app/src/main/java/com/shrimpfarm/app/finance/SupplierManageActivity.java`
- Modify: `app/src/main/java/com/shrimpfarm/app/finance/FinanceActivity.java`
- Modify: `app/src/main/res/layout/activity_finance.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: `FinanceDb.PERSON_NET_WORKER = 5` (or next available constant). FinanceActivity has "捞网工管理" entry opening `SupplierManageActivity` with `personType=PERSON_NET_WORKER`. SupplierManageActivity supports net-worker title/label; account button opens ContactAccountActivity; no kind filter shown.

- [ ] **Step 1: Add constant and strings**

Add `PERSON_NET_WORKER` in `FinanceDb` (and update any person-type switch helpers in SupplierManageActivity/typeLabel). Add strings `finance_person_net_worker`, `finance_person_net_worker_manage`.

- [ ] **Step 2: Add FinanceActivity entry**

Add button in `activity_finance.xml` management section. Wire click in `FinanceActivity` to open `SupplierManageActivity` with `PERSON_NET_WORKER`.

- [ ] **Step 3: Support net worker in SupplierManageActivity**

Update `typeTitle/typeLabel` to include net worker. For net worker, show accounts button (ContactAccountActivity already works) and hide supplier-kind filter/tags. "供应商商品" button can be hidden or repurposed only if meaningful; default hide for net worker unless needed.

- [ ] **Step 4: Verify**

Run: `.\gradlew.bat :app:compileDebugJavaWithJavac`
Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL / 0 failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/shrimpfarm/app/finance/FinanceDb.java app/src/main/java/com/shrimpfarm/app/finance/SupplierManageActivity.java app/src/main/java/com/shrimpfarm/app/finance/FinanceActivity.java app/src/main/res/layout/activity_finance.xml app/src/main/res/values/strings.xml
git commit -m "feat(finance): add net worker management entry"
```

## Task 6: Park Management

**Files:**
- Create: `app/src/main/java/com/shrimpfarm/app/finance/ParkManageActivity.java`
- Create: `app/src/main/res/layout/activity_finance_park_manage.xml`
- Create: `app/src/main/res/layout/item_finance_park.xml`
- Create: `app/src/main/res/layout/dialog_finance_park.xml` (if dialog layout used)
- Modify: `app/src/main/java/com/shrimpfarm/app/finance/FinanceActivity.java`
- Modify: `app/src/main/res/layout/activity_finance.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `FinanceDb.FinPark`, `insertPark/updatePark/listParks`.
- Produces: park archive CRUD UI (name, accountName, accountNo, enabled). FinanceActivity has "园区管理" entry. No schema change (V18 table exists).

- [ ] **Step 1: Add strings/layout/manifest**

Strings: `finance_park_manage`, `finance_park_name`, `finance_park_account_name`, `finance_park_account_no`, `finance_park_add`, `finance_park_edit`. Create list + add button layout and item layout showing name/account/enabled. Register activity.

- [ ] **Step 2: Implement ParkManageActivity**

List parks via `financeDb.listParks()`. Add/edit dialog with name required; account name/no optional text. Enable/disable toggle. Use existing BaseActivity/Toolbar style.

- [ ] **Step 3: Wire FinanceActivity**

Add "园区管理" button in management section and click handler.

- [ ] **Step 4: Verify**

Run: `.\gradlew.bat :app:compileDebugJavaWithJavac`
Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL / 0 failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/shrimpfarm/app/finance/ParkManageActivity.java app/src/main/res/layout/activity_finance_park_manage.xml app/src/main/res/layout/item_finance_park.xml app/src/main/res/layout/dialog_finance_park.xml app/src/main/java/com/shrimpfarm/app/finance/FinanceActivity.java app/src/main/res/layout/activity_finance.xml app/src/main/res/values/strings.xml app/src/main/AndroidManifest.xml
git commit -m "feat(finance): add park management archive"
```

## Task 7: Full Verification, Plan Record, and Review Handoff

**Files:**
- Modify: `PLAN-应收应付与开单重构分期计划.md` (add P2 execution record section)
- Modify: `docs/superpowers/plans/2026-11-28-p2-finance-master-data.md` (checklist/status if desired)

**Interfaces:**
- Produces: verified P2 branch ready for user review/merge; execution record with commands, results, known gaps.

- [x] **Step 1: Run full JVM tests**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Result: BUILD SUCCESSFUL; 55 test classes / 343 tests / 0 failures / 0 errors (XML summary).

- [x] **Step 2: Build debug APK**

Run: `.\gradlew.bat :app:assembleDebug`
Result: BUILD SUCCESSFUL; `app/build/outputs/apk/debug/app-debug.apk`.

- [x] **Step 3: Update plan execution record**

Added `## 14. P2 执行记录（2026-11-28）` in `PLAN-应收应付与开单重构分期计划.md` with branch, commits, test/build results, scope delivered, known gaps.

- [x] **Step 4: Self-review against spec**

P2 acceptance confirmed: supplier kind required/tag/filter; supplier product quick-add category/supplier with A2 routing; agent-customer link/unlink; net-worker management; park management. No P3-P5 behavior added. R2/R3/R6/R10/R12/R13 reviewed in per-task and final whole-branch review.

- [x] **Step 5: Commit and report**

```bash
git add PLAN-应收应付与开单重构分期计划.md docs/superpowers/plans/2026-11-28-p2-finance-master-data.md
git commit -m "docs(plan): record P2 finance master data execution"
```

Final whole-branch review: Ready to merge (no Critical/Important findings). Reported to user for review before merge to `main`.
