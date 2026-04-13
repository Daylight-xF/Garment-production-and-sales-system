# Sales Record Order Archive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate one read-only sales record per completed order and show full product detail in a sales record dialog.

**Architecture:** Extend the existing order and sales domains instead of adding a new module. Persist explicit order lifecycle timestamps, create a single order-level sales record during order completion, and refactor the sales record page into a summary table plus detail dialog that renders embedded product lines.

**Tech Stack:** Spring Boot 2.7, Spring Data MongoDB, JUnit 5, Mockito, Vue 3, Element Plus, Vite

---

### Task 1: Lock the new completion behavior with failing backend tests

**Files:**
- Create: `src/test/java/com/garment/service/impl/OrderServiceImplTest.java`

- [ ] **Step 1: Write a failing test that completes a shipped order and expects `completeTime` to be set**
- [ ] **Step 2: Write a failing test that expects one sales record with aggregated totals and embedded items**
- [ ] **Step 3: Run `mvn "-Dmaven.repo.local=.m2/repository" "-Dtest=OrderServiceImplTest" test` and verify the tests fail for the expected missing behavior**

### Task 2: Persist explicit order lifecycle timestamps

**Files:**
- Modify: `src/main/java/com/garment/model/Order.java`
- Modify: `src/main/java/com/garment/dto/OrderVO.java`
- Modify: `src/main/java/com/garment/service/impl/OrderServiceImpl.java`

- [ ] **Step 1: Add `shipTime` and `completeTime` to the order model and DTO**
- [ ] **Step 2: Write `shipTime` during `shipOrder`**
- [ ] **Step 3: Write `completeTime` during `completeOrder`**

### Task 3: Upgrade sales records to order-level archives

**Files:**
- Modify: `src/main/java/com/garment/model/SalesRecord.java`
- Modify: `src/main/java/com/garment/dto/SalesRecordVO.java`
- Modify: `src/main/java/com/garment/repository/SalesRecordRepository.java`
- Modify: `src/main/java/com/garment/service/impl/SalesServiceImpl.java`

- [ ] **Step 1: Add order summary fields and embedded item detail types**
- [ ] **Step 2: Add repository lookup by `orderId` for duplicate prevention**
- [ ] **Step 3: Update VO conversion to expose order summary and item detail fields**

### Task 4: Generate one sales record when an order is completed

**Files:**
- Modify: `src/main/java/com/garment/service/impl/OrderServiceImpl.java`

- [ ] **Step 1: Inject `SalesRecordRepository` into `OrderServiceImpl`**
- [ ] **Step 2: Build one sales record from the completed order and its items**
- [ ] **Step 3: Skip creation when a record already exists for the same `orderId`**
- [ ] **Step 4: Re-run `mvn "-Dmaven.repo.local=.m2/repository" "-Dtest=OrderServiceImplTest" test` and verify it passes**

### Task 5: Refactor the sales record page into a read-only archive

**Files:**
- Modify: `frontend/src/views/sales/Record.vue`

- [ ] **Step 1: Remove manual add/edit/delete flows and related dialog form state**
- [ ] **Step 2: Replace product-level columns with order-level summary columns**
- [ ] **Step 3: Add a `查看明细` dialog with summary cards and embedded item table**
- [ ] **Step 4: Keep the styling aligned with the current admin theme while improving hierarchy and readability**

### Task 6: Verify backend and frontend integration

**Files:**
- Test: `src/test/java/com/garment/service/impl/OrderServiceImplTest.java`
- Test: `src/test/java/com/garment/service/impl/UserServiceImplTest.java`
- Test: `src/test/java/com/garment/controller/InventoryControllerSecurityTest.java`

- [ ] **Step 1: Run `mvn "-Dmaven.repo.local=.m2/repository" "-Dtest=OrderServiceImplTest,InventoryControllerSecurityTest,UserServiceImplTest" test`**
- [ ] **Step 2: Run `npm run build` in `frontend`**
- [ ] **Step 3: Confirm the sales record page now models a read-only order archive**
