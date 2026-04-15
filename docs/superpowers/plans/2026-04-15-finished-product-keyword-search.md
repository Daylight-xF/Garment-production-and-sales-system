# 成品库存关键字搜索改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让成品库存页面的单个搜索框同时支持按批次号和产品名称查询，并保持类别筛选不变。

**Architecture:** 后端先把成品库存列表查询参数从 `name` 收敛为 `keyword`，并在服务层统一匹配 `batchNo` 与 `name`，这样前端只需要像生产计划页一样传一个关键字字段。实现顺序遵循 TDD，先补服务层失败测试，再做后端最小实现，最后改前端界面和请求参数。

**Tech Stack:** Spring Boot, JUnit 5, Mockito, Vue 3, Element Plus

---

### Task 1: 后端关键字过滤改造

**Files:**
- Modify: `src/test/java/com/garment/service/impl/InventoryServiceImplTest.java`
- Modify: `src/main/java/com/garment/controller/InventoryController.java`
- Modify: `src/main/java/com/garment/service/impl/InventoryServiceImpl.java`

- [ ] **Step 1: 写服务层失败测试，覆盖批次号、产品名和类别联合筛选**

```java
@Test
void getFinishedProductListShouldFilterByKeywordAcrossBatchNoAndName() {
    FinishedProduct batchMatched = buildFinishedProduct("finished-20", "PC-20260415-9710", "T恤-y1", "Y1", "红色", "M");
    batchMatched.setCategory("上衣");

    FinishedProduct nameMatched = buildFinishedProduct("finished-21", "PC-20260415-3440", "休闲裤-n1", "N1", "红色", "S");
    nameMatched.setCategory("裤子");

    FinishedProduct excluded = buildFinishedProduct("finished-22", "PC-20260414-7483", "方法-ff", "FF", "白色", "L");
    excluded.setCategory("上衣");

    when(finishedProductRepository.findAll()).thenReturn(Arrays.asList(batchMatched, nameMatched, excluded));
    when(productDefinitionRepository.findByProductCode(any())).thenReturn(Optional.empty());

    Page<FinishedProductVO> batchResult = inventoryService.getFinishedProductList("9710", "", PageRequest.of(0, 10));
    Page<FinishedProductVO> nameResult = inventoryService.getFinishedProductList("休闲裤", "", PageRequest.of(0, 10));
    Page<FinishedProductVO> categoryResult = inventoryService.getFinishedProductList("休闲裤", "裤子", PageRequest.of(0, 10));

    assertThat(batchResult.getContent()).extracting(FinishedProductVO::getBatchNo)
            .containsExactly("PC-20260415-9710");
    assertThat(nameResult.getContent()).extracting(FinishedProductVO::getName)
            .containsExactly("休闲裤-n1");
    assertThat(categoryResult.getContent()).extracting(FinishedProductVO::getCategory)
            .containsExactly("裤子");
}
```

- [ ] **Step 2: 运行测试，确认它先失败**

Run: `mvn -Dtest=InventoryServiceImplTest test`
Expected: FAIL，新增测试里批次号关键字 `9710` 查不到结果，因为当前实现只按 `name` 过滤。

- [ ] **Step 3: 写最小后端实现**

`src/main/java/com/garment/controller/InventoryController.java`

```java
@GetMapping("/finished-products")
@PreAuthorize("hasAnyAuthority('INVENTORY_READ', 'ORDER_CREATE')")
public Result<Map<String, Object>> getFinishedProductList(
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "") String category,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size) {
    Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
    Page<FinishedProductVO> productPage = inventoryService.getFinishedProductList(keyword, category, pageable);
```

`src/main/java/com/garment/service/impl/InventoryServiceImpl.java`

```java
@Override
public Page<FinishedProductVO> getFinishedProductList(String keyword, String category, Pageable pageable) {
    List<FinishedProduct> all = finishedProductRepository.findAll();
    List<FinishedProduct> filtered = all.stream()
            .filter(p -> !StringUtils.hasText(keyword)
                    || (p.getBatchNo() != null && p.getBatchNo().contains(keyword))
                    || (p.getName() != null && p.getName().contains(keyword)))
            .filter(p -> !StringUtils.hasText(category) || category.equals(p.getCategory()))
            .collect(Collectors.toList());
```

- [ ] **Step 4: 运行测试，确认后端通过**

Run: `mvn -Dtest=InventoryServiceImplTest test`
Expected: PASS，`Tests run:` 显示 `InventoryServiceImplTest` 全部通过。

- [ ] **Step 5: 提交后端改动**

```bash
git add src/test/java/com/garment/service/impl/InventoryServiceImplTest.java src/main/java/com/garment/controller/InventoryController.java src/main/java/com/garment/service/impl/InventoryServiceImpl.java
git commit -m "feat: 支持成品库存批次号关键字搜索"
```

### Task 2: 前端搜索框与接口参数对齐

**Files:**
- Modify: `frontend/src/views/inventory/FinishedProduct.vue`
- Modify: `frontend/src/api/inventory.js`

- [ ] **Step 1: 先写前端参数改造目标，确保字段名统一为 keyword**

`frontend/src/views/inventory/FinishedProduct.vue`

```js
const searchForm = reactive({
  keyword: '',
  category: ''
})
```

```js
const params = {
  keyword: searchForm.keyword,
  category: searchForm.category,
  page: pagination.page,
  size: pagination.size
}
```

```js
function handleReset() {
  searchForm.keyword = ''
  searchForm.category = ''
  pagination.page = 1
  fetchList()
}
```

- [ ] **Step 2: 修改模板文案与绑定，做最小前端实现**

```vue
<el-form-item label="批次号（产品名称）">
  <el-input
    v-model="searchForm.keyword"
    placeholder="请输入批次号或产品名"
    clearable
    @keyup.enter="handleSearch"
  />
</el-form-item>
```

`frontend/src/api/inventory.js`

```js
export function getFinishedProductList(params) {
  return request({
    url: '/inventory/finished-products',
    method: 'get',
    params
  })
}
```

- [ ] **Step 3: 运行针对性验证，确认请求参数和界面行为一致**

Run: `npm run build`
Expected: PASS，前端构建成功，没有因为 `searchForm.name` 已移除而报未定义错误。

- [ ] **Step 4: 做手工回归检查**

Run: 打开成品库存页面并验证以下场景：
Expected:
- 输入 `PC-20260415` 时可以筛出对应批次
- 输入 `T恤` 时可以筛出对应产品
- 选择类别后仍可叠加搜索
- 点击“重置”后关键字和类别都恢复为空

- [ ] **Step 5: 提交前端改动**

```bash
git add frontend/src/views/inventory/FinishedProduct.vue frontend/src/api/inventory.js
git commit -m "feat: 对齐成品库存关键字搜索交互"
```

### Task 3: 整体验证与收尾

**Files:**
- Modify: `src/test/java/com/garment/service/impl/InventoryServiceImplTest.java`
- Modify: `src/main/java/com/garment/controller/InventoryController.java`
- Modify: `src/main/java/com/garment/service/impl/InventoryServiceImpl.java`
- Modify: `frontend/src/views/inventory/FinishedProduct.vue`
- Modify: `frontend/src/api/inventory.js`

- [ ] **Step 1: 运行后端测试和前端构建，做完成前验证**

Run: `mvn -Dtest=InventoryServiceImplTest test`
Expected: PASS，服务层回归通过。

Run: `npm run build`
Expected: PASS，前端打包成功。

- [ ] **Step 2: 对照 spec 逐项核验**

Checklist:
- 搜索框标签是否为“批次号（产品名称）”
- 占位文案是否为“请输入批次号或产品名”
- 单输入框是否同时支持 `batchNo` 和 `name`
- 类别筛选是否保持可用
- 重置是否清空关键字和类别

- [ ] **Step 3: 提交最终整合结果**

```bash
git add src/test/java/com/garment/service/impl/InventoryServiceImplTest.java src/main/java/com/garment/controller/InventoryController.java src/main/java/com/garment/service/impl/InventoryServiceImpl.java frontend/src/views/inventory/FinishedProduct.vue frontend/src/api/inventory.js
git commit -m "feat: 完成成品库存关键字搜索改造"
```
