# MongoDB Concurrency Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add atomic MongoDB write guards, optimistic locking, and idempotency protections so the highest-risk inventory, order, and production workflows stop silently corrupting state under concurrent writes.

**Architecture:** Keep controllers and request/response DTOs unchanged. Introduce one focused Mongo atomic operations component for sequence generation, conditional status transitions, and total-quantity inventory updates; use `@Version` on document models for ordinary edit flows; then refactor the existing service implementations to call the new component on high-risk paths and preserve current service-layer behavior everywhere else.

**Tech Stack:** Java 8, Spring Boot 2.7, Spring Data MongoDB, Lombok, JUnit 5, Mockito, AssertJ

---

## File Structure

**Create**
- `src/main/java/com/garment/model/CounterSequence.java`
- `src/main/java/com/garment/service/support/MongoAtomicOpsService.java`
- `src/test/java/com/garment/service/support/MongoAtomicOpsServiceTest.java`
- `src/test/java/com/garment/service/impl/ProductionTaskServiceImplTest.java`

**Modify**
- `src/main/java/com/garment/model/RawMaterial.java`
- `src/main/java/com/garment/model/FinishedProduct.java`
- `src/main/java/com/garment/model/Order.java`
- `src/main/java/com/garment/model/ProductionPlan.java`
- `src/main/java/com/garment/model/ProductionTask.java`
- `src/main/java/com/garment/model/SalesRecord.java`
- `src/main/java/com/garment/service/impl/OrderServiceImpl.java`
- `src/main/java/com/garment/service/impl/InventoryServiceImpl.java`
- `src/main/java/com/garment/service/impl/ProductionPlanServiceImpl.java`
- `src/main/java/com/garment/service/impl/ProductionTaskServiceImpl.java`
- `src/test/java/com/garment/service/impl/OrderServiceImplTest.java`
- `src/test/java/com/garment/service/impl/InventoryServiceImplTest.java`
- `src/test/java/com/garment/service/impl/ProductionPlanServiceImplTest.java`

### Task 1: Atomic Mongo Support And Persistence Metadata

**Files:**
- Create: `src/main/java/com/garment/model/CounterSequence.java`
- Create: `src/main/java/com/garment/service/support/MongoAtomicOpsService.java`
- Create: `src/test/java/com/garment/service/support/MongoAtomicOpsServiceTest.java`
- Modify: `src/main/java/com/garment/model/RawMaterial.java`
- Modify: `src/main/java/com/garment/model/FinishedProduct.java`
- Modify: `src/main/java/com/garment/model/Order.java`
- Modify: `src/main/java/com/garment/model/ProductionPlan.java`
- Modify: `src/main/java/com/garment/model/ProductionTask.java`
- Modify: `src/main/java/com/garment/model/SalesRecord.java`
- Test: `src/test/java/com/garment/service/support/MongoAtomicOpsServiceTest.java`

- [ ] **Step 1: Write the failing tests for order sequence generation and conditional status updates**

```java
package com.garment.service.support;

import com.garment.model.CounterSequence;
import com.garment.model.Order;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoAtomicOpsServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private MongoAtomicOpsService mongoAtomicOpsService;

    @Test
    void nextOrderNoShouldUseDailyAtomicCounter() {
        CounterSequence sequence = new CounterSequence();
        sequence.setId("ORD20260424");
        sequence.setSeq(7L);

        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(CounterSequence.class)))
                .thenReturn(sequence);

        String orderNo = mongoAtomicOpsService.nextOrderNo(new Date(1776998400000L));

        assertThat(orderNo).isEqualTo("ORD20260424007");
    }

    @Test
    void transitionOrderStatusShouldReturnFalseWhenCurrentStatusDoesNotMatch() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Order.class)))
                .thenReturn(null);

        boolean changed = mongoAtomicOpsService.transitionOrderStatus(
                "order-1",
                "PENDING_APPROVAL",
                "APPROVED",
                new Document("approveBy", "manager-1").append("approveByName", "审核员")
        );

        assertThat(changed).isFalse();
    }
}
```

- [ ] **Step 2: Run the new test to verify it fails**

Run: `mvn -Dtest=MongoAtomicOpsServiceTest test`
Expected: FAIL with compilation errors because `CounterSequence` and `MongoAtomicOpsService` do not exist yet.

- [ ] **Step 3: Add optimistic locking metadata, the unique archive key, and the atomic Mongo support component**

```java
// src/main/java/com/garment/model/RawMaterial.java
import org.springframework.data.annotation.Version;

@Version
private Long version;

// src/main/java/com/garment/model/FinishedProduct.java
import org.springframework.data.annotation.Version;

@Version
private Long version;

// src/main/java/com/garment/model/Order.java
import org.springframework.data.annotation.Version;

@Version
private Long version;

// src/main/java/com/garment/model/ProductionPlan.java
import org.springframework.data.annotation.Version;

@Version
private Long version;

// src/main/java/com/garment/model/ProductionTask.java
import org.springframework.data.annotation.Version;

@Version
private Long version;

// src/main/java/com/garment/model/SalesRecord.java
import org.springframework.data.mongodb.core.index.Indexed;

@Indexed(unique = true, sparse = true)
private String orderId;
```

```java
// src/main/java/com/garment/model/CounterSequence.java
package com.garment.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document(collection = "counters")
public class CounterSequence {

    @Id
    private String id;

    private Long seq;

    @LastModifiedDate
    private Date updateTime;
}
```

```java
// src/main/java/com/garment/service/support/MongoAtomicOpsService.java
package com.garment.service.support;

import com.garment.model.CounterSequence;
import com.garment.model.FinishedProduct;
import com.garment.model.Order;
import com.garment.model.ProductionPlan;
import com.garment.model.RawMaterial;
import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;

@Service
public class MongoAtomicOpsService {

    private final MongoTemplate mongoTemplate;

    public MongoAtomicOpsService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public String nextOrderNo(Date now) {
        String prefix = "ORD" + new SimpleDateFormat("yyyyMMdd").format(now);
        Query query = Query.query(Criteria.where("_id").is(prefix));
        Update update = new Update().inc("seq", 1L).currentDate("updateTime");
        CounterSequence sequence = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                CounterSequence.class
        );
        long next = sequence != null && sequence.getSeq() != null ? sequence.getSeq() : 1L;
        return prefix + String.format("%03d", next);
    }

    public boolean transitionOrderStatus(String orderId, String expectedStatus,
                                         String nextStatus, Document extraFields) {
        Query query = Query.query(Criteria.where("_id").is(orderId).and("status").is(expectedStatus));
        Update update = new Update().set("status", nextStatus).currentDate("updateTime");
        if (extraFields != null) {
            extraFields.forEach((key, value) -> update.set(key, value));
        }
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Order.class
        ) != null;
    }

    public boolean transitionPlanStatus(String planId, String expectedStatus,
                                        String nextStatus, Document extraFields) {
        Query query = Query.query(Criteria.where("_id").is(planId).and("status").is(expectedStatus));
        Update update = new Update().set("status", nextStatus).currentDate("updateTime");
        if (extraFields != null) {
            extraFields.forEach((key, value) -> update.set(key, value));
        }
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                ProductionPlan.class
        ) != null;
    }

    public boolean changeRawMaterialQuantity(String materialId, int delta, Integer minimumAfterChange) {
        Query query = Query.query(Criteria.where("_id").is(materialId));
        if (minimumAfterChange != null) {
            query.addCriteria(Criteria.where("quantity").gte(minimumAfterChange - delta));
        }
        Update update = new Update().inc("quantity", delta).currentDate("updateTime");
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                RawMaterial.class
        ) != null;
    }

    public boolean changeFinishedProductQuantity(String productId, int delta, Integer minimumAfterChange) {
        Query query = Query.query(Criteria.where("_id").is(productId));
        if (minimumAfterChange != null) {
            query.addCriteria(Criteria.where("quantity").gte(minimumAfterChange - delta));
        }
        Update update = new Update().inc("quantity", delta).currentDate("updateTime");
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                FinishedProduct.class
        ) != null;
    }
}
```

- [ ] **Step 4: Run the atomic support test to verify it passes**

Run: `mvn -Dtest=MongoAtomicOpsServiceTest test`
Expected: PASS with `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit the metadata and atomic support layer**

```bash
git add src/main/java/com/garment/model/RawMaterial.java src/main/java/com/garment/model/FinishedProduct.java src/main/java/com/garment/model/Order.java src/main/java/com/garment/model/ProductionPlan.java src/main/java/com/garment/model/ProductionTask.java src/main/java/com/garment/model/SalesRecord.java src/main/java/com/garment/model/CounterSequence.java src/main/java/com/garment/service/support/MongoAtomicOpsService.java src/test/java/com/garment/service/support/MongoAtomicOpsServiceTest.java
git commit -m "feat: add Mongo atomic operations support"
```

### Task 2: Order Sequence, Status Transition, And Archive Idempotency

**Files:**
- Modify: `src/main/java/com/garment/service/impl/OrderServiceImpl.java`
- Modify: `src/test/java/com/garment/service/impl/OrderServiceImplTest.java`
- Test: `src/test/java/com/garment/service/impl/OrderServiceImplTest.java`

- [ ] **Step 1: Write the failing order service tests for atomic order numbers and transition conflicts**

```java
@Mock
private MongoAtomicOpsService mongoAtomicOpsService;

@Test
void createOrderShouldUseAtomicOrderNumberGenerator() {
    User creator = new User();
    creator.setId("sales-1");
    creator.setRealName("销售甲");

    OrderCreateRequest request = new OrderCreateRequest();
    request.setCustomerId("customer-1");
    request.setCustomerName("星河服饰");
    request.setItems(Arrays.asList(OrderItemDTO.builder()
            .productId("finished-1")
            .productCode("N1")
            .productName("T恤")
            .quantity(1)
            .unitPrice(88.0)
            .build()));

    when(userRepository.findById("sales-1")).thenReturn(Optional.of(creator));
    when(mongoAtomicOpsService.nextOrderNo(any(Date.class))).thenReturn("ORD20260424001");
    when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
        Order order = invocation.getArgument(0);
        order.setId("order-new");
        return order;
    });
    when(orderItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    OrderVO result = orderService.createOrder(request, "sales-1");

    assertThat(result.getOrderNo()).isEqualTo("ORD20260424001");
}

@Test
void approveOrderShouldThrowWhenAtomicStatusTransitionFails() {
    Order order = new Order();
    order.setId("order-approve-1");
    order.setStatus("PENDING_APPROVAL");

    User approver = new User();
    approver.setId("manager-1");
    approver.setRealName("审核员");

    OrderApproveRequest request = new OrderApproveRequest();
    request.setApproved(true);
    request.setRemark("ok");

    when(orderRepository.findById("order-approve-1")).thenReturn(Optional.of(order));
    when(userRepository.findById("manager-1")).thenReturn(Optional.of(approver));
    when(mongoAtomicOpsService.transitionOrderStatus(
            eq("order-approve-1"), eq("PENDING_APPROVAL"), eq("APPROVED"), any()))
            .thenReturn(false);

    assertThatThrownBy(() -> orderService.approveOrder("order-approve-1", request, "manager-1"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("订单状态已变化");
}

@Test
void completeOrderShouldTreatDuplicateArchiveAsAlreadyArchived() {
    Order order = new Order();
    order.setId("order-2");
    order.setStatus("SHIPPED");

    User operator = new User();
    operator.setId("manager-2");
    operator.setRealName("审核员乙");

    when(orderRepository.findById("order-2")).thenReturn(Optional.of(order));
    when(userRepository.findById("manager-2")).thenReturn(Optional.of(operator));
    when(orderItemRepository.findByOrderId("order-2")).thenReturn(Arrays.asList());
    when(mongoAtomicOpsService.transitionOrderStatus(eq("order-2"), eq("SHIPPED"), eq("COMPLETED"), any()))
            .thenReturn(true);
    when(salesRecordRepository.findByOrderId("order-2")).thenReturn(Optional.empty());
    when(salesRecordRepository.save(any(SalesRecord.class)))
            .thenThrow(new DuplicateKeyException("sales_records.orderId"));

    OrderVO result = orderService.completeOrder("order-2", "manager-2");

    assertThat(result.getStatus()).isEqualTo("COMPLETED");
}
```

- [ ] **Step 2: Run the order service tests to verify they fail**

Run: `mvn -Dtest=OrderServiceImplTest test`
Expected: FAIL because `OrderServiceImpl` still generates order numbers with `countByOrderNoStartingWith`, still mutates status with `orderRepository.save`, and does not handle duplicate-key archive conflicts.

- [ ] **Step 3: Refactor `OrderServiceImpl` to use the atomic support service**

```java
// constructor field
private final MongoAtomicOpsService mongoAtomicOpsService;

// createOrder
order.setOrderNo(mongoAtomicOpsService.nextOrderNo(new Date()));

// approveOrder
boolean changed = mongoAtomicOpsService.transitionOrderStatus(
        id,
        "PENDING_APPROVAL",
        Boolean.TRUE.equals(request.getApproved()) ? "APPROVED" : "CANCELLED",
        new Document("approveBy", userId)
                .append("approveByName", user.getRealName())
                .append("approveTime", new Date())
                .append("approveRemark", request.getRemark())
);
if (!changed) {
    throw new BusinessException("订单状态已变化，请刷新后再操作");
}

// shipOrder
boolean shipped = mongoAtomicOpsService.transitionOrderStatus(
        id,
        "APPROVED",
        "SHIPPED",
        new Document("shipTime", new Date())
);
if (!shipped) {
    throw new BusinessException("订单状态已变化，请刷新后再操作");
}

// completeOrder
boolean completed = mongoAtomicOpsService.transitionOrderStatus(
        id,
        "SHIPPED",
        "COMPLETED",
        new Document("completeTime", new Date())
);
if (!completed) {
    throw new BusinessException("订单状态已变化，请刷新后再操作");
}

// archiveCompletedOrder
try {
    salesRecordRepository.save(salesRecord);
} catch (DuplicateKeyException ex) {
    return;
}
```

- [ ] **Step 4: Run the order service tests to verify they pass**

Run: `mvn -Dtest=OrderServiceImplTest test`
Expected: PASS with all existing order tests plus the new concurrency tests green.

- [ ] **Step 5: Commit the order concurrency hardening**

```bash
git add src/main/java/com/garment/service/impl/OrderServiceImpl.java src/test/java/com/garment/service/impl/OrderServiceImplTest.java
git commit -m "feat: harden order state transitions and sequencing"
```

### Task 3: Inventory Atomic Quantity Updates On Total-Stock Paths

**Files:**
- Modify: `src/main/java/com/garment/service/impl/InventoryServiceImpl.java`
- Modify: `src/test/java/com/garment/service/impl/InventoryServiceImplTest.java`
- Test: `src/test/java/com/garment/service/impl/InventoryServiceImplTest.java`

- [ ] **Step 1: Write the failing inventory tests for atomic total-stock mutations**

```java
@Mock
private MongoAtomicOpsService mongoAtomicOpsService;

@Test
void stockOutShouldUseAtomicTotalQuantityDeductionWhenNoLocationsExist() {
    RawMaterial material = new RawMaterial();
    material.setId("raw-1");
    material.setName("Cotton");
    material.setQuantity(12);
    material.setLocations(new ArrayList<>());

    User operator = new User();
    operator.setId("warehouse-1");
    operator.setRealName("仓管");

    StockInOutRequest request = new StockInOutRequest();
    request.setItemType("RAW_MATERIAL");
    request.setItemId("raw-1");
    request.setQuantity(5);
    request.setReason("manual stock out");

    when(rawMaterialRepository.findById("raw-1")).thenReturn(Optional.of(material));
    when(userRepository.findById("warehouse-1")).thenReturn(Optional.of(operator));
    when(mongoAtomicOpsService.changeRawMaterialQuantity("raw-1", -5, 0)).thenReturn(true);
    when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

    inventoryService.stockOut(request, "warehouse-1");

    verify(mongoAtomicOpsService).changeRawMaterialQuantity("raw-1", -5, 0);
    verify(rawMaterialRepository, never()).save(any(RawMaterial.class));
}

@Test
void fifoDeductFinishedProductShouldFailWhenAtomicTotalQuantityDeductionMisses() {
    FinishedProduct product = new FinishedProduct();
    product.setId("finished-99");
    product.setName("Hoodie");
    product.setQuantity(2);
    product.setLocations(null);

    when(finishedProductRepository.findById("finished-99")).thenReturn(Optional.of(product));
    when(mongoAtomicOpsService.changeFinishedProductQuantity("finished-99", -3, 0)).thenReturn(false);

    assertThatThrownBy(() -> inventoryService.fifoDeductFinishedProduct("finished-99", 3, "ship"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("库存不足或已被其他操作更新");
}
```

- [ ] **Step 2: Run the inventory service tests to verify they fail**

Run: `mvn -Dtest=InventoryServiceImplTest test`
Expected: FAIL because the service still uses repository `save` on total-stock-only paths and does not call `MongoAtomicOpsService`.

- [ ] **Step 3: Refactor total-stock inventory paths to call the atomic support service**

```java
// constructor field
private final MongoAtomicOpsService mongoAtomicOpsService;

// stockOut RAW_MATERIAL without locations
boolean deducted = mongoAtomicOpsService.changeRawMaterialQuantity(request.getItemId(), -request.getQuantity(), 0);
if (!deducted) {
    throw new BusinessException("库存不足或已被其他操作更新，请刷新后重试");
}
material.setQuantity(material.getQuantity() - request.getQuantity());

// stockIn RAW_MATERIAL without locations
boolean increased = mongoAtomicOpsService.changeRawMaterialQuantity(request.getItemId(), request.getQuantity(), null);
if (!increased) {
    throw new BusinessException("库存更新失败，请刷新后重试");
}
material.setQuantity(material.getQuantity() + request.getQuantity());

// fifoDeductFinishedProduct without locations
boolean deducted = mongoAtomicOpsService.changeFinishedProductQuantity(finishedProductId, -quantity, 0);
if (!deducted) {
    throw new BusinessException("库存不足或已被其他操作更新，请刷新后重试");
}
product.setQuantity(available - quantity);

// fifoDeductRawMaterial without locations
boolean deducted = mongoAtomicOpsService.changeRawMaterialQuantity(materialId, -quantity, 0);
if (!deducted) {
    throw new BusinessException("库存不足或已被其他操作更新，请刷新后重试");
}
material.setQuantity((material.getQuantity() != null ? material.getQuantity() : 0) - quantity);
```

- [ ] **Step 4: Run the inventory service tests to verify they pass**

Run: `mvn -Dtest=InventoryServiceImplTest test`
Expected: PASS with both the existing FIFO coverage and the new atomic-total-stock coverage green.

- [ ] **Step 5: Commit the inventory concurrency hardening**

```bash
git add src/main/java/com/garment/service/impl/InventoryServiceImpl.java src/test/java/com/garment/service/impl/InventoryServiceImplTest.java
git commit -m "feat: harden inventory total-stock updates"
```

### Task 4: Production Plan And Task Concurrency Protections

**Files:**
- Modify: `src/main/java/com/garment/service/impl/ProductionPlanServiceImpl.java`
- Modify: `src/main/java/com/garment/service/impl/ProductionTaskServiceImpl.java`
- Modify: `src/main/java/com/garment/model/ProductionTask.java`
- Modify: `src/test/java/com/garment/service/impl/ProductionPlanServiceImplTest.java`
- Create: `src/test/java/com/garment/service/impl/ProductionTaskServiceImplTest.java`
- Test: `src/test/java/com/garment/service/impl/ProductionPlanServiceImplTest.java`
- Test: `src/test/java/com/garment/service/impl/ProductionTaskServiceImplTest.java`

- [ ] **Step 1: Write the failing production tests for atomic plan transitions and progress conflicts**

```java
// src/test/java/com/garment/service/impl/ProductionPlanServiceImplTest.java
@Mock
private MongoAtomicOpsService mongoAtomicOpsService;

@Test
void startProductionShouldThrowWhenAtomicPlanTransitionFails() {
    ProductionPlan plan = new ProductionPlan();
    plan.setId("plan-1");
    plan.setBatchNo("BATCH-1");
    plan.setStatus("APPROVED");
    plan.setQuantity(10);
    plan.setProductName("T恤");
    plan.setProductCode("Y1");

    when(productionPlanRepository.findById("plan-1")).thenReturn(Optional.of(plan));
    when(productionTaskRepository.findByPlanId("plan-1")).thenReturn(Collections.emptyList());
    when(mongoAtomicOpsService.transitionPlanStatus("plan-1", "APPROVED", "IN_PROGRESS", null)).thenReturn(false);

    assertThatThrownBy(() -> productionPlanService.startProduction("plan-1", "manager-1"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("生产计划状态已变化");
}
```

```java
// src/test/java/com/garment/service/impl/ProductionTaskServiceImplTest.java
package com.garment.service.impl;

import com.garment.dto.TaskVO;
import com.garment.exception.BusinessException;
import com.garment.model.ProductionPlan;
import com.garment.model.ProductionTask;
import com.garment.repository.ProductionPlanRepository;
import com.garment.repository.ProductionTaskRepository;
import com.garment.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionTaskServiceImplTest {

    @Mock
    private ProductionTaskRepository productionTaskRepository;

    @Mock
    private ProductionPlanRepository productionPlanRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProductionTaskServiceImpl productionTaskService;

    @Test
    void updateProgressShouldSurfaceOptimisticLockConflictOnPlanSummarySave() {
        ProductionTask task = new ProductionTask();
        task.setId("task-1");
        task.setPlanId("plan-1");
        task.setPlanQuantity(10);
        task.setCompletedQuantity(0);
        task.setStatus("PENDING");

        ProductionPlan plan = new ProductionPlan();
        plan.setId("plan-1");
        plan.setCompletedQuantity(0);

        when(productionTaskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(productionTaskRepository.save(any(ProductionTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productionTaskRepository.findByPlanId("plan-1")).thenReturn(java.util.Arrays.asList(task));
        when(productionPlanRepository.findById("plan-1")).thenReturn(Optional.of(plan));
        when(productionPlanRepository.save(any(ProductionPlan.class)))
                .thenThrow(new OptimisticLockingFailureException("plan conflict"));

        assertThatThrownBy(() -> productionTaskService.updateProgress("task-1", 50))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("生产计划数据已变化");
    }
}
```

- [ ] **Step 2: Run the production tests to verify they fail**

Run: `mvn -Dtest=ProductionPlanServiceImplTest,ProductionTaskServiceImplTest test`
Expected: FAIL because plan status changes still use repository `save`, no `ProductionTaskServiceImplTest` exists yet, and progress updates do not translate optimistic-lock conflicts into business errors.

- [ ] **Step 3: Refactor the production services for atomic plan transitions and conflict handling**

```java
// src/main/java/com/garment/model/ProductionTask.java
import org.springframework.data.mongodb.core.index.Indexed;

@Indexed(sparse = true)
private String autoCreateKey;
```

```java
// src/main/java/com/garment/service/impl/ProductionPlanServiceImpl.java
private final MongoAtomicOpsService mongoAtomicOpsService;

// startProduction
boolean started = mongoAtomicOpsService.transitionPlanStatus(planId, "APPROVED", "IN_PROGRESS", null);
if (!started) {
    throw new BusinessException("生产计划状态已变化，请刷新后重试");
}

task.setAutoCreateKey("AUTO:" + plan.getId());

// approvePlan
boolean changed = mongoAtomicOpsService.transitionPlanStatus(id, "PENDING", status, null);
if (!changed) {
    throw new BusinessException("生产计划状态已变化，请刷新后重试");
}

// completePlan
boolean completed = mongoAtomicOpsService.transitionPlanStatus(planId, "IN_PROGRESS", "COMPLETED", null);
if (!completed) {
    throw new BusinessException("生产计划状态已变化，请刷新后重试");
}
```

```java
// src/main/java/com/garment/service/impl/ProductionTaskServiceImpl.java
import org.springframework.dao.OptimisticLockingFailureException;

private void updatePlanCompletedQuantityEnhanced(String planId) {
    if (!StringUtils.hasText(planId)) return;

    List<ProductionTask> tasks = productionTaskRepository.findByPlanId(planId);
    int totalCompleted = tasks.stream()
            .mapToInt(t -> t.getCompletedQuantity() != null ? t.getCompletedQuantity() : 0)
            .sum();

    ProductionPlan plan = productionPlanRepository.findById(planId).orElse(null);
    if (plan != null) {
        plan.setCompletedQuantity(totalCompleted);
        try {
            productionPlanRepository.save(plan);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException("生产计划数据已变化，请刷新后重试");
        }
    }
}
```

- [ ] **Step 4: Run the production tests to verify they pass**

Run: `mvn -Dtest=ProductionPlanServiceImplTest,ProductionTaskServiceImplTest test`
Expected: PASS with the new transition-conflict and optimistic-lock-conflict scenarios green.

- [ ] **Step 5: Commit the production concurrency hardening**

```bash
git add src/main/java/com/garment/model/ProductionTask.java src/main/java/com/garment/service/impl/ProductionPlanServiceImpl.java src/main/java/com/garment/service/impl/ProductionTaskServiceImpl.java src/test/java/com/garment/service/impl/ProductionPlanServiceImplTest.java src/test/java/com/garment/service/impl/ProductionTaskServiceImplTest.java
git commit -m "feat: harden production plan and task concurrency"
```

## Self-Review Notes

- Spec coverage: this plan covers the spec’s atomic support layer, `@Version` and unique index metadata, order sequencing and status transitions, total-stock inventory protection, production plan transitions, and task optimistic-lock conflict handling. The known deferred item from the spec remains location-array fine-grained atomicity, which is intentionally out of scope and documented as such.
- Placeholder scan: no `TODO`, `TBD`, or “implement later” placeholders remain.
- Type consistency: the plan uses one consistent support class name, `MongoAtomicOpsService`, one counter document name, `CounterSequence`, and one production auto-create key field, `autoCreateKey`, across all tasks.
