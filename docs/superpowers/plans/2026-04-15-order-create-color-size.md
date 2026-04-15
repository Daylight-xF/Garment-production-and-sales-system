# 创建订单页颜色尺码联动 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将创建订单页的“规格”替换为基于成品库存的产品、颜色、尺码联动选择，并把颜色、尺码正式保存到订单、订单详情和销售归档中。

**Architecture:** 后端先把订单项和销售归档明细的数据结构从 `specification` 迁移到 `color`、`size`，同时保留单件成本仅前端展示。前端则继续复用成品库存列表作为唯一数据源，把联动筛选逻辑抽到独立工具函数中，再由 `Create.vue` 驱动下拉框、唯一命中记录和金额展示。

**Tech Stack:** Spring Boot, JUnit 5, Mockito, Vue 3, Element Plus, Node.js built-in test runner

---

> 用户已明确要求：本次执行过程中不主动做任何 `git` 操作，因此本计划中的常规提交步骤改为“本地检查点”。

## File Structure

- Modify: `src/main/java/com/garment/dto/OrderItemDTO.java`
  - 订单项 DTO，从 `specification` 迁移到 `color`、`size`
- Modify: `src/main/java/com/garment/model/OrderItem.java`
  - 订单项 Mongo 模型，从 `specification` 迁移到 `color`、`size`
- Modify: `src/main/java/com/garment/model/SalesRecord.java`
  - 销售归档明细模型，从 `specification` 迁移到 `color`、`size`
- Modify: `src/main/java/com/garment/dto/SalesRecordVO.java`
  - 销售归档返回对象，从 `specification` 迁移到 `color`、`size`
- Modify: `src/main/java/com/garment/service/impl/OrderServiceImpl.java`
  - 创建订单、查询订单详情、完成订单归档时同步保存和返回颜色尺码
- Modify: `src/main/java/com/garment/service/impl/SalesServiceImpl.java`
  - 销售记录详情/列表从颜色尺码返回和兼容旧数据
- Modify: `src/test/java/com/garment/service/impl/OrderServiceImplTest.java`
  - 回归测试订单创建与归档的颜色尺码持久化
- Create: `src/test/java/com/garment/service/impl/SalesServiceImplTest.java`
  - 测试销售记录 VO 转换与旧数据兼容
- Create: `frontend/src/utils/orderItemSelection.js`
  - 纯函数：产品去重、颜色尺码可选项、唯一命中记录计算
- Create: `frontend/src/utils/orderItemSelection.test.js`
  - 纯函数测试，覆盖双向联动与唯一命中逻辑
- Modify: `frontend/src/views/order/Create.vue`
  - 订单创建页 UI、联动逻辑、单件成本展示
- Modify: `frontend/src/views/order/Detail.vue`
  - 订单详情页明细表展示颜色与尺码
- Modify: `frontend/src/views/sales/Record.vue`
  - 销售归档详情弹窗展示颜色与尺码

---

### Task 1: 后端订单项字段迁移

**Files:**
- Modify: `src/test/java/com/garment/service/impl/OrderServiceImplTest.java`
- Modify: `src/main/java/com/garment/dto/OrderItemDTO.java`
- Modify: `src/main/java/com/garment/model/OrderItem.java`
- Modify: `src/main/java/com/garment/service/impl/OrderServiceImpl.java`

- [ ] **Step 1: 写失败测试，验证创建订单会保存产品编号、颜色和尺码**

在 `src/test/java/com/garment/service/impl/OrderServiceImplTest.java` 增加测试：

```java
@Test
void createOrderShouldPersistProductCodeColorAndSizeFromRequest() {
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
            .color("红色")
            .size("M")
            .quantity(2)
            .unitPrice(88.0)
            .amount(176.0)
            .build()));

    when(userRepository.findById("sales-1")).thenReturn(Optional.of(creator));
    when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
        Order order = invocation.getArgument(0);
        order.setId("order-new");
        return order;
    });
    when(orderItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    orderService.createOrder(request, "sales-1");

    ArgumentCaptor<Iterable<OrderItem>> itemCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(orderItemRepository).saveAll(itemCaptor.capture());
    OrderItem savedItem = itemCaptor.getValue().iterator().next();

    assertThat(savedItem.getProductId()).isEqualTo("finished-1");
    assertThat(savedItem.getProductCode()).isEqualTo("N1");
    assertThat(savedItem.getProductName()).isEqualTo("T恤");
    assertThat(savedItem.getColor()).isEqualTo("红色");
    assertThat(savedItem.getSize()).isEqualTo("M");
}
```

- [ ] **Step 2: 运行测试，确认先失败**

Run: `mvn -Dtest=OrderServiceImplTest#createOrderShouldPersistProductCodeColorAndSizeFromRequest test`

Expected: FAIL，因为 `OrderItemDTO`、`OrderItem` 还没有 `color` 和 `size` 字段，或 `createOrder` 还未写入这些值。

- [ ] **Step 3: 写最小实现，让创建订单测试通过**

在 `src/main/java/com/garment/dto/OrderItemDTO.java` 中把订单项改成：

```java
private String productId;
private String productCode;
private String productName;
private String color;
private String size;
private Integer quantity;
private Double unitPrice;
private Double amount;
```

在 `src/main/java/com/garment/model/OrderItem.java` 中把模型改成：

```java
private String productId;
private String productCode;
private String productName;
private String color;
private String size;
private Integer quantity;
private Double unitPrice;
private Double amount;
```

在 `src/main/java/com/garment/service/impl/OrderServiceImpl.java` 的 `createOrder(...)` 循环中改为：

```java
OrderItem item = new OrderItem();
item.setOrderId(order.getId());
item.setProductId(itemDTO.getProductId());
item.setProductCode(itemDTO.getProductCode());
item.setProductName(itemDTO.getProductName());
item.setColor(itemDTO.getColor());
item.setSize(itemDTO.getSize());
item.setQuantity(itemDTO.getQuantity());
item.setUnitPrice(itemDTO.getUnitPrice());
item.setAmount(amount);
```

同时删除旧的补码逻辑：

```java
if (itemDTO.getProductId() != null) {
    productDefinitionRepository.findById(itemDTO.getProductId())
            .ifPresent(productDef -> item.setProductCode(productDef.getProductCode()));
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `mvn -Dtest=OrderServiceImplTest#createOrderShouldPersistProductCodeColorAndSizeFromRequest test`

Expected: PASS，新增测试通过，说明订单创建已保存 `productCode`、`color`、`size`。

- [ ] **Step 5: 写失败测试，验证完成订单归档会把颜色尺码同步到销售记录**

把 `src/test/java/com/garment/service/impl/OrderServiceImplTest.java` 中现有的 `completeOrderShouldSetCompleteTimeAndCreateOrderLevelSalesRecord` 改成断言颜色尺码：

```java
shirt.setColor("蓝色");
shirt.setSize("L");
coat.setColor("黑色");
coat.setSize("XL");

assertThat(savedRecord.getItems()).extracting(SalesRecord.SalesRecordItem::getColor)
        .containsExactly("蓝色", "黑色");
assertThat(savedRecord.getItems()).extracting(SalesRecord.SalesRecordItem::getSize)
        .containsExactly("L", "XL");
```

- [ ] **Step 6: 运行测试，确认先失败**

Run: `mvn -Dtest=OrderServiceImplTest#completeOrderShouldSetCompleteTimeAndCreateOrderLevelSalesRecord test`

Expected: FAIL，因为 `SalesRecord.SalesRecordItem` 还没有 `color` 和 `size`，归档逻辑也还没有同步。

- [ ] **Step 7: 写最小实现，让归档测试通过**

在 `src/main/java/com/garment/model/SalesRecord.java` 的内部类改成：

```java
public static class SalesRecordItem {
    private String productId;
    private String productCode;
    private String productName;
    private String color;
    private String size;
    private Integer quantity;
    private Double unitPrice;
    private Double amount;
}
```

并在 `src/main/java/com/garment/service/impl/OrderServiceImpl.java` 的 `archiveCompletedOrder(...)` 中把映射改成：

```java
.map(item -> new SalesRecord.SalesRecordItem(
        item.getProductId(),
        item.getProductCode(),
        item.getProductName(),
        item.getColor(),
        item.getSize(),
        item.getQuantity(),
        item.getUnitPrice(),
        item.getAmount()))
```

同时在 `convertToVO(...)` 中把订单项返回改成：

```java
.map(item -> OrderItemDTO.builder()
        .productId(item.getProductId())
        .productCode(item.getProductCode())
        .productName(item.getProductName())
        .color(item.getColor())
        .size(item.getSize())
        .quantity(item.getQuantity())
        .unitPrice(item.getUnitPrice())
        .amount(item.getAmount())
        .build())
```

- [ ] **Step 8: 运行整组订单服务测试**

Run: `mvn -Dtest=OrderServiceImplTest test`

Expected: PASS，`OrderServiceImplTest` 全部通过。

- [ ] **Step 9: 本地检查点**

核对以下事实：

- `OrderItemDTO` 和 `OrderItem` 不再使用 `specification`
- 创建订单直接保存前端提交的 `productCode`
- 订单详情返回中已经包含 `color` 和 `size`
- 完成订单归档时销售记录明细已经包含 `color` 和 `size`

---

### Task 2: 销售记录服务与兼容数据转换

**Files:**
- Create: `src/test/java/com/garment/service/impl/SalesServiceImplTest.java`
- Modify: `src/main/java/com/garment/dto/SalesRecordVO.java`
- Modify: `src/main/java/com/garment/service/impl/SalesServiceImpl.java`

- [ ] **Step 1: 写失败测试，验证销售记录详情返回颜色和尺码**

创建 `src/test/java/com/garment/service/impl/SalesServiceImplTest.java`：

```java
@ExtendWith(MockitoExtension.class)
class SalesServiceImplTest {

    @Mock
    private SalesRecordRepository salesRecordRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductDefinitionRepository productDefinitionRepository;

    @InjectMocks
    private SalesServiceImpl salesService;

    @Test
    void getSalesRecordByIdShouldReturnColorAndSizeInItems() {
        SalesRecord record = new SalesRecord();
        record.setId("sales-1");
        record.setCustomerName("星河服饰");
        record.setItems(Arrays.asList(
                new SalesRecord.SalesRecordItem("finished-1", "N1", "T恤", "红色", "M", 2, 88.0, 176.0)
        ));

        when(salesRecordRepository.findById("sales-1")).thenReturn(Optional.of(record));

        SalesRecordVO result = salesService.getSalesRecordById("sales-1");

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getColor()).isEqualTo("红色");
        assertThat(result.getItems().get(0).getSize()).isEqualTo("M");
    }
}
```

- [ ] **Step 2: 运行测试，确认先失败**

Run: `mvn -Dtest=SalesServiceImplTest#getSalesRecordByIdShouldReturnColorAndSizeInItems test`

Expected: FAIL，因为 `SalesRecordVO.SalesRecordItemVO` 还没有 `color` 和 `size` 字段。

- [ ] **Step 3: 写最小实现，让详情转换通过**

在 `src/main/java/com/garment/dto/SalesRecordVO.java` 中把内部类改成：

```java
public static class SalesRecordItemVO {
    private String productId;
    private String productCode;
    private String productName;
    private String color;
    private String size;
    private Integer quantity;
    private Double unitPrice;
    private Double amount;
}
```

在 `src/main/java/com/garment/service/impl/SalesServiceImpl.java` 的 `convertToSalesRecordVO(...)` 中改成：

```java
.map(item -> SalesRecordVO.SalesRecordItemVO.builder()
        .productId(item.getProductId())
        .productCode(item.getProductCode())
        .productName(item.getProductName())
        .color(item.getColor())
        .size(item.getSize())
        .quantity(item.getQuantity())
        .unitPrice(item.getUnitPrice())
        .amount(item.getAmount())
        .build())
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `mvn -Dtest=SalesServiceImplTest#getSalesRecordByIdShouldReturnColorAndSizeInItems test`

Expected: PASS。

- [ ] **Step 5: 写失败测试，验证旧销售记录兼容转换不报错**

在同一个测试文件里增加：

```java
@Test
void querySalesRecordsShouldKeepLegacySingleItemCompatible() {
    SalesRecord legacy = new SalesRecord();
    legacy.setId("legacy-1");
    legacy.setCustomerName("老客户");
    legacy.setProductId("finished-legacy");
    legacy.setProductCode("OLD1");
    legacy.setProductName("老款T恤");
    legacy.setQuantity(1);
    legacy.setUnitPrice(66.0);
    legacy.setAmount(66.0);

    when(salesRecordRepository.findAll()).thenReturn(Arrays.asList(legacy));

    Page<SalesRecordVO> result = salesService.querySalesRecords("", null, null, "", PageRequest.of(0, 10));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getItems()).hasSize(1);
    assertThat(result.getContent().get(0).getItems().get(0).getColor()).isNull();
    assertThat(result.getContent().get(0).getItems().get(0).getSize()).isNull();
}
```

- [ ] **Step 6: 运行测试，确认先失败**

Run: `mvn -Dtest=SalesServiceImplTest#querySalesRecordsShouldKeepLegacySingleItemCompatible test`

Expected: FAIL，如果旧兼容路径 `new SalesRecord.SalesRecordItem(...)` 的参数数量与新模型不一致，或转换未补空值。

- [ ] **Step 7: 写最小实现，补齐旧数据兼容**

在 `src/main/java/com/garment/service/impl/SalesServiceImpl.java` 的 `getNormalizedItems(...)` 中把旧数据兜底改成：

```java
items.add(new SalesRecord.SalesRecordItem(
        record.getProductId(),
        record.getProductCode(),
        record.getProductName(),
        null,
        null,
        record.getQuantity(),
        record.getUnitPrice(),
        record.getAmount()));
```

并在 `matchesKeyword(...)` 中删除旧的 `specification` 匹配：

```java
if (contains(item.getProductName(), keyword)
        || contains(item.getProductCode(), keyword)
        || contains(item.getColor(), keyword)
        || contains(item.getSize(), keyword)) {
    return true;
}
```

- [ ] **Step 8: 运行整组销售服务测试**

Run: `mvn -Dtest=SalesServiceImplTest test`

Expected: PASS。

- [ ] **Step 9: 本地检查点**

核对以下事实：

- 销售记录 VO 明细不再使用 `specification`
- 旧数据仍能被归一化成单条明细
- 关键字搜索可以命中 `productName`、`productCode`、`color`、`size`

---

### Task 3: 创建订单页联动与单件成本展示

**Files:**
- Create: `frontend/src/utils/orderItemSelection.js`
- Create: `frontend/src/utils/orderItemSelection.test.js`
- Modify: `frontend/src/views/order/Create.vue`

- [ ] **Step 1: 写前端纯函数失败测试，覆盖产品去重、双向联动和唯一命中**

创建 `frontend/src/utils/orderItemSelection.test.js`：

```js
import assert from 'node:assert/strict'
import {
  buildProductOptions,
  getAvailableColors,
  getAvailableSizes,
  findMatchedFinishedProduct
} from './orderItemSelection.js'

const inventory = [
  { id: '1', name: 'T恤', productCode: 'n1', color: '红色', size: 'M', costPrice: 20 },
  { id: '2', name: 'T恤', productCode: 'n1', color: '红色', size: 'L', costPrice: 22 },
  { id: '3', name: 'T恤', productCode: 'n1', color: '白色', size: 'M', costPrice: 21 },
  { id: '4', name: '卫衣', productCode: 'w1', color: '黑色', size: 'XL', costPrice: 40 }
]

assert.deepEqual(buildProductOptions(inventory), [
  { key: 'T恤||n1', label: 'T恤-n1', productName: 'T恤', productCode: 'n1' },
  { key: '卫衣||w1', label: '卫衣-w1', productName: '卫衣', productCode: 'w1' }
])

assert.deepEqual(
  getAvailableColors(inventory, { selectedProductKey: 'T恤||n1', color: '', size: '' }),
  ['白色', '红色']
)

assert.deepEqual(
  getAvailableSizes(inventory, { selectedProductKey: 'T恤||n1', color: '红色', size: '' }),
  ['L', 'M']
)

assert.equal(
  findMatchedFinishedProduct(inventory, { selectedProductKey: 'T恤||n1', color: '白色', size: 'M' }).id,
  '3'
)
```

- [ ] **Step 2: 运行测试，确认先失败**

Run: `node --test src/utils/orderItemSelection.test.js`

Workdir: `frontend`

Expected: FAIL，因为工具文件还不存在。

- [ ] **Step 3: 写最小纯函数实现**

创建 `frontend/src/utils/orderItemSelection.js`：

```js
function normalizeText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function buildProductKey(productName, productCode) {
  return `${normalizeText(productName)}||${normalizeText(productCode)}`
}

function filterInventoryBySelection(inventory, selection) {
  return inventory.filter(item => {
    if (!normalizeText(item.color) || !normalizeText(item.size)) {
      return false
    }
    if (selection.selectedProductKey) {
      const itemKey = buildProductKey(item.name || item.productName, item.productCode)
      if (itemKey !== selection.selectedProductKey) return false
    }
    if (selection.color && item.color !== selection.color) return false
    if (selection.size && item.size !== selection.size) return false
    return true
  })
}

export function buildProductOptions(inventory) {
  const unique = new Map()
  for (const item of inventory) {
    if (!normalizeText(item.color) || !normalizeText(item.size)) continue
    const productName = item.name || item.productName || ''
    const productCode = item.productCode || ''
    const key = buildProductKey(productName, productCode)
    if (!unique.has(key)) {
      unique.set(key, { key, label: productCode ? `${productName}-${productCode}` : productName, productName, productCode })
    }
  }
  return Array.from(unique.values())
}

export function getAvailableColors(inventory, selection) {
  return Array.from(new Set(filterInventoryBySelection(inventory, { ...selection, color: '', size: selection.size }).map(item => item.color))).sort()
}

export function getAvailableSizes(inventory, selection) {
  return Array.from(new Set(filterInventoryBySelection(inventory, { ...selection, size: '', color: selection.color }).map(item => item.size))).sort()
}

export function findMatchedFinishedProduct(inventory, selection) {
  const matches = filterInventoryBySelection(inventory, selection)
  return matches.length === 1 ? matches[0] : null
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `node --test src/utils/orderItemSelection.test.js`

Workdir: `frontend`

Expected: PASS。

- [ ] **Step 5: 改创建订单页模板与数据结构**

在 `frontend/src/views/order/Create.vue` 中把单行订单项从：

```js
{ productId: '', productName: '', specification: '', quantity: 1, unitPrice: 0, amount: 0 }
```

改成：

```js
{
  productId: '',
  productCode: '',
  productName: '',
  selectedProductKey: '',
  color: '',
  size: '',
  quantity: 1,
  unitPrice: 0,
  costPrice: null,
  amount: 0
}
```

并把表格列改成：

```vue
<el-table-column label="产品" min-width="180">...</el-table-column>
<el-table-column label="颜色" width="140">...</el-table-column>
<el-table-column label="尺码" width="140">...</el-table-column>
<el-table-column label="数量" width="175">...</el-table-column>
<el-table-column label="单价" width="175">...</el-table-column>
<el-table-column label="单件成本" width="130">...</el-table-column>
<el-table-column label="金额" width="120">...</el-table-column>
```

- [ ] **Step 6: 接入联动逻辑**

在 `frontend/src/views/order/Create.vue` 中引入工具函数：

```js
import {
  buildProductOptions,
  getAvailableColors,
  getAvailableSizes,
  findMatchedFinishedProduct
} from '../../utils/orderItemSelection'
```

并新增核心逻辑：

```js
function syncMatchedItem(row) {
  const matched = findMatchedFinishedProduct(productList.value, row)
  if (!matched) {
    row.productId = ''
    row.productName = ''
    row.productCode = ''
    row.costPrice = null
    return
  }
  row.productId = matched.id
  row.productName = matched.name
  row.productCode = matched.productCode || ''
  row.color = matched.color || ''
  row.size = matched.size || ''
  row.costPrice = matched.costPrice ?? null
}

function onProductKeyChange(index, productKey) {
  const row = form.value.items[index]
  row.selectedProductKey = productKey
  if (!getAvailableColors(productList.value, row).includes(row.color)) row.color = ''
  if (!getAvailableSizes(productList.value, row).includes(row.size)) row.size = ''
  syncMatchedItem(row)
  calcAmount(row)
}

function onColorChange(index, color) {
  const row = form.value.items[index]
  row.color = color
  if (!getAvailableSizes(productList.value, row).includes(row.size)) row.size = ''
  syncMatchedItem(row)
}

function onSizeChange(index, size) {
  const row = form.value.items[index]
  row.size = size
  if (!getAvailableColors(productList.value, row).includes(row.color)) row.color = ''
  syncMatchedItem(row)
}
```

提交时映射为：

```js
items: validItems.map(item => ({
  productId: item.productId,
  productCode: item.productCode,
  productName: item.productName,
  color: item.color,
  size: item.size,
  quantity: item.quantity,
  unitPrice: item.unitPrice,
  amount: item.amount
}))
```

并在提交前加校验：

```js
const invalidItem = validItems.find(item => !item.productId || !item.color || !item.size)
if (invalidItem) {
  ElMessage.warning('请为每个订单项完整选择产品、颜色和尺码')
  return
}
```

- [ ] **Step 7: 运行构建，确认前端通过**

Run: `npm run build`

Workdir: `frontend`

Expected: PASS，页面编译成功，没有遗留 `specification` 或未定义函数报错。

- [ ] **Step 8: 做本地手工核验**

手工验证：

- 选择 `T恤-n1` 后，颜色和尺码只显示该产品的库存组合
- 先选颜色或尺码，再选产品时，剩余下拉项仍会缩小
- 选满产品、颜色、尺码后，单件成本显示对应 `costPrice`
- 修改任一条件导致原组合失效时，失效值会被清空
- 单件成本变化不会影响 `amount = unitPrice * quantity`

- [ ] **Step 9: 本地检查点**

核对以下事实：

- 创建页不再使用 `specification`
- 下单提交体里没有 `costPrice`
- `productId` 仅在唯一命中成品库存记录后才有值

---

### Task 4: 订单详情页与销售归档页展示替换

**Files:**
- Modify: `frontend/src/views/order/Detail.vue`
- Modify: `frontend/src/views/sales/Record.vue`

- [ ] **Step 1: 修改订单详情页展示**

在 `frontend/src/views/order/Detail.vue` 中把：

```vue
<el-table-column prop="specification" label="规格" />
```

改成：

```vue
<el-table-column prop="color" label="颜色" width="110" align="center">
  <template #default="{ row }">
    {{ row.color || '-' }}
  </template>
</el-table-column>
<el-table-column prop="size" label="尺码" width="110" align="center">
  <template #default="{ row }">
    {{ row.size || '-' }}
  </template>
</el-table-column>
```

- [ ] **Step 2: 修改销售归档详情弹窗展示**

在 `frontend/src/views/sales/Record.vue` 中把：

```vue
<el-table-column prop="specification" label="规格" width="120" align="center">
  <template #default="{ row }">
    {{ row.specification || '-' }}
  </template>
</el-table-column>
```

改成：

```vue
<el-table-column prop="color" label="颜色" width="110" align="center">
  <template #default="{ row }">
    {{ row.color || '-' }}
  </template>
</el-table-column>
<el-table-column prop="size" label="尺码" width="110" align="center">
  <template #default="{ row }">
    {{ row.size || '-' }}
  </template>
</el-table-column>
```

- [ ] **Step 3: 运行前端构建，确认展示页通过**

Run: `npm run build`

Workdir: `frontend`

Expected: PASS。

- [ ] **Step 4: 做整体验证**

Run 1: `mvn -Dtest=OrderServiceImplTest,SalesServiceImplTest test`

Expected: PASS，后端订单与销售记录相关测试都通过。

Run 2: `npm run build`

Workdir: `frontend`

Expected: PASS，创建页、订单详情页、销售归档页都能通过编译。

手工验证：

- 创建订单后查看订单详情，明细显示颜色和尺码
- 完成订单后打开销售归档详情，明细显示颜色和尺码

- [ ] **Step 5: 本地检查点**

核对以下事实：

- 订单详情页不再显示“规格”
- 销售归档详情页不再显示“规格”
- 创建页、详情页、归档页三端字段名已统一为 `color` 和 `size`
