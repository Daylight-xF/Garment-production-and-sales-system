# 成品库存分类自动带出 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让以后新创建的生产计划在入库自动生成成品库存记录时，成品分类自动继承产品定义中的分类。

**Architecture:** 使用计划快照方案，在 `ProductionPlan` 中新增 `category` 字段，并在创建计划时从 `ProductDefinition.category` 写入。后续生产计划入库自动创建 `FinishedProduct` 时直接透传 `plan.category`，不回填历史数据。

**Tech Stack:** Spring Boot, MongoDB, JUnit 5, Mockito

---

### Task 1: 为成品自动建档补充失败测试

**Files:**
- Modify: `src/test/java/com/garment/service/impl/InventoryServiceImplTest.java`
- Modify: `src/main/java/com/garment/service/impl/InventoryServiceImpl.java`

- [ ] **Step 1: 写失败测试，断言自动创建的成品继承计划分类**

```java
@Test
void stockInShouldCreateFinishedProductWithPlanCategory() {
    ProductionPlan plan = buildPlan("plan-2", "BATCH-002", "休闲裤", "P001", "蓝色", "L");
    plan.setCategory("下装");

    FinishedProduct existing = buildFinishedProduct("finished-1", "BATCH-001", "休闲裤", "P001", "黑色", "M");

    StockInOutRequest request = new StockInOutRequest();
    request.setItemType("FINISHED_PRODUCT");
    request.setItemId("plan-2");
    request.setQuantity(10);
    request.setReason("生产批次BATCH-002入库 | 位置:B-02 | 首次入库");

    when(productionPlanRepository.findById("plan-2")).thenReturn(Optional.of(plan));
    when(finishedProductRepository.findAll()).thenReturn(Arrays.asList(existing));
    when(finishedProductRepository.save(any(FinishedProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(productionPlanRepository.save(any(ProductionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

    inventoryService.stockIn(request, "admin-1");

    ArgumentCaptor<FinishedProduct> productCaptor = ArgumentCaptor.forClass(FinishedProduct.class);
    verify(finishedProductRepository, atLeastOnce()).save(productCaptor.capture());

    assertThat(productCaptor.getAllValues())
            .anySatisfy(product -> assertThat(product.getCategory()).isEqualTo("下装"));
}
```

- [ ] **Step 2: 运行测试，确认它先失败**

Run: `mvn -Dtest=InventoryServiceImplTest#stockInShouldCreateNewFinishedProductWhenBatchColorOrSizeDiffers test`
Expected: FAIL，因为当前自动创建成品时没有给 `category` 赋值。

- [ ] **Step 3: 写最小实现**

```java
FinishedProduct newProduct = new FinishedProduct();
newProduct.setProductCode(plan.getProductCode());
newProduct.setName(plan.getProductName());
newProduct.setCategory(plan.getCategory());
newProduct.setColor(plan.getColor());
newProduct.setSize(plan.getSize());
```

- [ ] **Step 4: 重新运行测试，确认通过**

Run: `mvn -Dtest=InventoryServiceImplTest#stockInShouldCreateNewFinishedProductWhenBatchColorOrSizeDiffers test`
Expected: PASS

### Task 2: 为生产计划分类快照补充失败测试并实现

**Files:**
- Create: `src/test/java/com/garment/service/impl/ProductionPlanServiceImplTest.java`
- Modify: `src/main/java/com/garment/model/ProductionPlan.java`
- Modify: `src/main/java/com/garment/service/impl/ProductionPlanServiceImpl.java`

- [ ] **Step 1: 写失败测试，断言创建计划时保存产品定义分类**

```java
@Test
void createPlanShouldPersistProductDefinitionCategory() {
    ProductDefinition definition = new ProductDefinition();
    definition.setId("def-1");
    definition.setProductCode("P001");
    definition.setProductName("休闲裤");
    definition.setCategory("下装");

    PlanCreateRequest request = new PlanCreateRequest();
    request.setBatchNo("BATCH-100");
    request.setProductDefinitionId("def-1");
    request.setQuantity(20);
    request.setColor("黑色");
    request.setSize("L");
    request.setUnit("件");

    when(productDefinitionRepository.findById("def-1")).thenReturn(Optional.of(definition));
    when(productionPlanRepository.save(any(ProductionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

    productionPlanService.createPlan(request, "admin-1");

    ArgumentCaptor<ProductionPlan> planCaptor = ArgumentCaptor.forClass(ProductionPlan.class);
    verify(productionPlanRepository).save(planCaptor.capture());
    assertThat(planCaptor.getValue().getCategory()).isEqualTo("下装");
}
```

- [ ] **Step 2: 运行测试，确认它先失败**

Run: `mvn -Dtest=ProductionPlanServiceImplTest#createPlanShouldPersistProductDefinitionCategory test`
Expected: FAIL，因为当前 `ProductionPlan` 还没有保存 `category`。

- [ ] **Step 3: 写最小实现**

`src/main/java/com/garment/model/ProductionPlan.java`

```java
private String category;
```

`src/main/java/com/garment/service/impl/ProductionPlanServiceImpl.java`

```java
plan.setCategory(productDef.getCategory());
```

- [ ] **Step 4: 重新运行测试，确认通过**

Run: `mvn -Dtest=ProductionPlanServiceImplTest#createPlanShouldPersistProductDefinitionCategory test`
Expected: PASS

### Task 3: 运行回归测试确认链路闭合

**Files:**
- Test: `src/test/java/com/garment/service/impl/InventoryServiceImplTest.java`
- Test: `src/test/java/com/garment/service/impl/ProductionPlanServiceImplTest.java`

- [ ] **Step 1: 运行本次相关测试**

Run: `mvn -Dtest=InventoryServiceImplTest,ProductionPlanServiceImplTest test`
Expected: PASS，新增分类透传测试与原有库存测试全部通过。

- [ ] **Step 2: 记录结果并准备交付**

确认说明：

```text
本次只影响以后新创建的生产计划和之后自动生成的新成品库存记录。
历史成品库存数据不会被补写。
```
