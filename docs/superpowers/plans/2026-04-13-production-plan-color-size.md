# 生产计划颜色和尺码功能实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为生产计划管理功能添加颜色（Color）和尺码（Size）字段，支持前端表单输入、后端数据存储和列表展示。

**Architecture:** 采用轻量级实现方案，直接在现有MongoDB文档模型中添加color和size字段，前端使用Element Plus组件进行表单录入和数据展示，后端通过DTO校验确保数据完整性。

**Tech Stack:** Vue 3 + Element Plus (前端) | Spring Boot + Spring Data MongoDB (后端) | MongoDB (数据库)

---

## 文件结构概览

### 后端修改文件（5个）
```
src/main/java/com/garment/
├── model/
│   └── ProductionPlan.java              # 数据模型 - 新增2个字段
├── dto/
│   ├── PlanCreateRequest.java           # 创建请求DTO - 新增字段+校验
│   ├── PlanUpdateRequest.java           # 更新请求DTO - 新增字段
│   └── PlanVO.java                      # 视图对象 - 新增字段
└── service/impl/
    └── ProductionPlanServiceImpl.java   # 服务层 - 更新3个方法
```

### 前端修改文件（1个）
```
frontend/src/views/production/
└── Plan.vue                             # 页面组件 - 表单+表格+验证+逻辑
```

---

## Task 1: 修改ProductionPlan数据模型

**Files:**
- Modify: `src/main/java/com/garment/model/ProductionPlan.java`

- [ ] **Step 1: 在description字段之前添加color和size字段**

在第44行（`private String description;`）之前插入以下代码：

```java
private String color;

private String size;
```

**完整上下文参考：**

原代码（第42-45行）：
```java
private String status;

private String description;

private String createBy;
```

修改后应为：
```java
private String status;

private String color;

private String size;

private String description;

private String createBy;
```

**验证点:** 字段位置正确，与设计规范一致，位于status之后、description之前。

---

## Task 2: 修改PlanCreateRequest DTO

**Files:**
- Modify: `src/main/java/com/garment/dto/PlanCreateRequest.java`

- [ ] **Step 1: 在description字段定义之前添加color和size字段及校验注解**

在第27行（`private String description;`）之前插入以下代码：

```java
@NotBlank(message = "颜色不能为空")
private String color;

@NotBlank(message = "尺码不能为空")
private String size;
```

**完整上下文参考：**

原代码（第22-28行）：
```java
private Date endDate;

private String description;
}
```

修改后应为：
```java
private Date endDate;

@NotBlank(message = "颜色不能为空")
private String color;

@NotBlank(message = "尺码不能为空")
private String size;

private String description;
}
```

**验证点:** 
- 包含@NotBlank注解确保必填校验
- 错误提示信息清晰明确
- 字段顺序与数据模型保持一致

---

## Task 3: 修改PlanUpdateRequest DTO

**Files:**
- Modify: `src/main/java/com/garment/dto/PlanUpdateRequest.java`

- [ ] **Step 1: 在description字段定义之前添加color和size字段**

在第24行（`private String description;`）之前插入以下代码：

```java
private String color;

private String size;
```

**完整上下文参考：**

原代码（第20-25行）：
```java
private Date endDate;

private String description;
}
```

修改后应为：
```java
private Date endDate;

private String color;

private String size;

private String description;
}
```

**注意:** 更新请求DTO不添加@NotBlank注解，允许部分更新操作。

**验证点:** 字段存在但不强制必填，支持灵活的部分更新场景。

---

## Task 4: 修改PlanVO视图对象

**Files:**
- Modify: `src/main/java/com/garment/dto/PlanVO.java`

- [ ] **Step 1: 在description字段声明之前添加color和size字段**

在第30行（`private String description;`）之前插入以下代码：

```java
private String color;

private String size;
```

**完整上下文参考：**

原代码（第29-31行）：
```java
private String status;
private String description;
private String createBy;
```

修改后应为：
```java
private String status;
private String color;
private String size;
private String description;
private String createBy;
```

**验证点:** VO对象包含新字段，用于API响应数据传输。

---

## Task 5: 修改ProductionPlanServiceImpl服务层

**Files:**
- Modify: `src/main/java/com/garment/service/impl/ProductionPlanServiceImpl.java`

### 5.1 修改createPlan方法

- [ ] **Step 1: 在createPlan方法的plan.setDescription()之后添加color和size设置**

在第78行（`plan.setDescription(request.getDescription());`）之后插入：

```java
plan.setColor(request.getColor());
plan.setSize(request.getSize());
```

**完整上下文参考：**

原代码（第76-80行）：
```java
plan.setStartDate(request.getStartDate());
plan.setEndDate(request.getEndDate());
plan.setStatus("PENDING");
plan.setDescription(request.getDescription());
plan.setCreateBy(userId);
```

修改后应为：
```java
plan.setStartDate(request.getStartDate());
plan.setEndDate(request.getEndDate());
plan.setStatus("PENDING");
plan.setDescription(request.getDescription());
plan.setColor(request.getColor());
plan.setSize(request.getSize());
plan.setCreateBy(userId);
```

**验证点:** 创建计划时正确保存用户提交的颜色和尺码值。

### 5.2 修改updatePlan方法

- [ ] **Step 2: 在updatePlan方法的description更新逻辑之后添加color和size更新逻辑**

在第287行（`plan.setDescription(request.getDescription());`）的闭合大括号`}`之后、第289行（`ProductionPlan saved = productionPlanRepository.save(plan);`）之前插入：

```java
if (request.getColor() != null) {
    plan.setColor(request.getColor());
}
if (request.getSize() != null) {
    plan.setSize(request.getSize());
}
```

**完整上下文参考：**

原代码（第285-290行）：
```java
if (request.getDescription() != null) {
    plan.setDescription(request.getDescription());
}

ProductionPlan saved = productionPlanRepository.save(plan);
return convertToVO(saved);
```

修改后应为：
```java
if (request.getDescription() != null) {
    plan.setDescription(request.getDescription());
}
if (request.getColor() != null) {
    plan.setColor(request.getColor());
}
if (request.getSize() != null) {
    plan.setSize(request.getSize());
}

ProductionPlan saved = productionPlanRepository.save(plan);
return convertToVO(saved);
```

**权限说明:** 已审批（APPROVED）或进行中（IN_PROGRESS）的计划也允许修改颜色和尺码字段，这与quantity等字段的编辑逻辑保持一致。

**验证点:** 更新计划时能正确处理color和size字段的非空判断和赋值。

### 5.3 修改convertToVO方法

- [ ] **Step 3: 在convertToVO方法的Builder链中添加color和size字段映射**

在第517行（`.materialsDeducted(plan.getMaterialsDeducted())`）之前插入：

```java
.color(plan.getColor())
.size(plan.getSize())
```

**完整上下文参考：**

原代码（第514-518行）：
```java
.description(plan.getDescription())
.createBy(plan.getCreateBy())
.createByName(createByName)
.materialsDeducted(plan.getMaterialsDeducted())
.createTime(plan.getCreateTime())
```

修改后应为：
```java
.description(plan.getDescription())
.color(plan.getColor())
.size(plan.getSize())
.createBy(plan.getCreateBy())
.createByName(createByName)
.materialsDeducted(plan.getMaterialsDeducted())
.createTime(plan.getCreateTime())
```

**验证点:** 查询计划和返回VO时包含color和size字段值。

---

## Task 6: 修改前端Plan.vue组件

**Files:**
- Modify: `frontend/src/views/production/Plan.vue`

### 6.1 添加数据和常量定义

- [ ] **Step 1: 在script setup区域添加尺码选项常量**

在第378行（`const router = useRouter()`）之后插入：

```javascript
const sizeOptions = [
  { label: 'XS', value: 'XS' },
  { label: 'S', value: 'S' },
  { label: 'M', value: 'M' },
  { label: 'L', value: 'L' },
  { label: 'XL', value: 'XL' },
  { label: 'XXL', value: 'XXL' },
  { label: 'XXXL', value: 'XXXL' }
]
```

**验证点:** 尺码选项数组包含7个标准服装尺码。

- [ ] **Step 2: 在planForm响应式对象中添加color和size字段**

修改第402-411行的planForm定义，在unit字段后添加color和size：

原代码：
```javascript
const planForm = reactive({
  batchNo: '',
  productDefinitionId: '',
  productName: '',
  quantity: 1,
  unit: '件',
  startDate: '',
  endDate: '',
  description: ''
})
```

修改后：
```javascript
const planForm = reactive({
  batchNo: '',
  productDefinitionId: '',
  productName: '',
  quantity: 1,
  unit: '件',
  color: '',
  size: '',
  startDate: '',
  endDate: '',
  description: ''
})
```

**验证点:** 表单数据对象包含color和size字段，初始值为空字符串。

### 6.2 扩展表单验证规则

- [ ] **Step 3: 在planFormRules中添加color和size的校验规则**

修改第443-447行的规则定义：

原代码：
```javascript
const planFormRules = {
  batchNo: [{ required: true, message: '请输入批次号', trigger: 'blur' }],
  productDefinitionId: [{ required: true, message: '请选择产品定义', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入计划数量', trigger: 'blur' }]
}
```

修改后：
```javascript
const planFormRules = {
  batchNo: [{ required: true, message: '请输入批次号', trigger: 'blur' }],
  productDefinitionId: [{ required: true, message: '请选择产品定义', trigger: 'change' }],
  color: [
    { required: true, message: '请输入颜色', trigger: 'blur' },
    { max: 50, message: '颜色长度不能超过50个字符', trigger: 'blur' }
  ],
  size: [{ required: true, message: '请选择尺码', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入计划数量', trigger: 'blur' }]
}
```

**验证点:** 
- color字段：必填 + 最大长度50字符
- size字段：必填，触发时机为change事件

### 6.3 修改表单UI - 添加颜色和尺码输入组件

- [ ] **Step 4: 在产品信息卡片（el-card）之后、计划数量（el-form-item）之前插入颜色和尺码表单项**

在第179行（`</el-card>`）之后、第181行（`<el-form-item label="计划数量" prop="quantity">`）之前插入：

```vue
<el-form-item label="颜色" prop="color">
  <el-input
    v-model="planForm.color"
    placeholder="请输入颜色（如：红色、深蓝色、#FF0000）"
    :disabled="isEditApproved"
    maxlength="50"
    show-word-limit
  />
</el-form-item>
<el-form-item label="尺码" prop="size">
  <el-select
    v-model="planForm.size"
    placeholder="请选择尺码"
    style="width: 100%"
    :disabled="isEditApproved"
  >
    <el-option
      v-for="item in sizeOptions"
      :key="item.value"
      :label="item.label"
      :value="item.value"
    />
  </el-select>
</el-form-item>
```

**UI布局说明:**
- 颜色输入框：文本类型，带占位符提示多种格式，显示字数统计
- 尺码选择器：下拉类型，宽度100%自适应，遍历sizeOptions渲染选项
- 编辑限制：已审批计划可编辑（通过isEditApproved控制disabled状态）

**验证点:** 
- 组件位置正确（产品信息卡片后、计划数量前）
- 样式与现有表单项保持一致
- 占位符文字友好且具有指导性

### 6.4 修改表格UI - 添加颜色和尺码展示列

- [ ] **Step 5: 在表格的产品名称列之后、计划数量列之前插入颜色和尺码列**

在第45行（`</el-table-column>` 产品名称列结束标签）之后、第46行（`<el-table-column prop="quantity"` 计划数量列开始标签）之前插入：

```vue
<el-table-column prop="color" label="颜色" width="100" align="center">
  <template #default="{ row }">
    {{ row.color || '-' }}
  </template>
</el-table-column>
<el-table-column prop="size" label="尺码" width="80" align="center">
  <template #default="{ row }">
    <el-tag size="small">{{ row.size || '-' }}</el-tag>
  </template>
</el-table-column>
```

**样式特点:**
- 颜色列：宽度100px，居中对齐，纯文本显示
- 尺码列：宽度80px，居中对齐，使用el-tag标签突出显示
- 空值兼容：使用`|| '-'`处理null或undefined情况

**验证点:** 
- 列位置正确（产品名称后、计划数量前）
- 空数据显示为'-'
- 尺码使用tag样式增强视觉效果

### 6.5 修改handleAdd方法 - 重置新增表单

- [ ] **Step 6: 在handleAdd方法的Object.assign中添加color和size字段重置**

修改第525-535行的代码：

原代码：
```javascript
function handleAdd() {
  dialogType.value = 'add'
  Object.assign(planForm, {
    batchNo: generateUniqueBatchNo(),
    productDefinitionId: '',
    productName: '',
    quantity: 1,
    unit: '件',
    startDate: '',
    endDate: '',
    description: ''
  })
  dialogVisible.value = true
}
```

修改后：
```javascript
function handleAdd() {
  dialogType.value = 'add'
  Object.assign(planForm, {
    batchNo: generateUniqueBatchNo(),
    productDefinitionId: '',
    productName: '',
    quantity: 1,
    unit: '件',
    color: '',
    size: '',
    startDate: '',
    endDate: '',
    description: ''
  })
  dialogVisible.value = true
}
```

**验证点:** 打开新增对话框时，color和size字段被重置为空字符串。

### 6.6 修改handleEdit方法 - 回显编辑数据

- [ ] **Step 7: 在handleEdit方法的Object.assign中添加color和size字段赋值**

修改第538-553行的代码：

原代码：
```javascript
function handleEdit(row) {
  dialogType.value = 'edit'
  currentPlan.value = row
  originalQuantity.value = row.quantity || 0
  Object.assign(planForm, {
    batchNo: row.batchNo,
    productDefinitionId: row.productDefinitionId || '',
    productName: row.productName || '',
    quantity: row.quantity,
    unit: row.unit || '件',
    startDate: row.startDate ? row.startDate.substring(0, 10) : '',
    endDate: row.endDate ? row.endDate.substring(0, 10) : '',
    description: row.description || ''
  })
  dialogVisible.value = true
}
```

修改后：
```javascript
function handleEdit(row) {
  dialogType.value = 'edit'
  currentPlan.value = row
  originalQuantity.value = row.quantity || 0
  Object.assign(planForm, {
    batchNo: row.batchNo,
    productDefinitionId: row.productDefinitionId || '',
    productName: row.productName || '',
    quantity: row.quantity,
    unit: row.unit || '件',
    color: row.color || '',
    size: row.size || '',
    startDate: row.startDate ? row.startDate.substring(0, 10) : '',
    endDate: row.endDate ? row.endDate.substring(0, 10) : '',
    description: row.description || ''
  })
  dialogVisible.value = true
}
```

**验证点:** 打开编辑对话框时，color和size字段正确回显已有数据。

### 6.7 修改handleSubmit方法 - 提交数据处理

- [ ] **Step 8: 在handleSubmit方法的已审批计划编辑分支中添加color和size字段**

修改第586-592行的submitData构建逻辑：

原代码：
```javascript
const submitData = isEditApproved.value ? {
  quantity: planForm.quantity,
  unit: planForm.unit,
  startDate: planForm.startDate,
  endDate: planForm.endDate,
  description: planForm.description
} : { ...planForm }
```

修改后：
```javascript
const submitData = isEditApproved.value ? {
  quantity: planForm.quantity,
  unit: planForm.unit,
  startDate: planForm.startDate,
  endDate: planForm.endDate,
  description: planForm.description,
  color: planForm.color,
  size: planForm.size
} : { ...planForm }
```

**业务逻辑说明:** 已审批计划的编辑模式下，只允许修改特定字段（包括新增的color和size），其他字段保持不变。

**验证点:** 提交时无论新建还是编辑模式，都包含color和size数据。

---

## Task 7: 功能验证测试清单

### 7.1 后端验证（需启动Spring Boot应用）

- [ ] **Test 1: 验证创建计划时的必填校验**

发送POST请求到 `/api/production/plans`，body中不包含color字段  
**预期结果**: 返回400错误，message包含"颜色不能为空"

- [ ] **Test 2: 验证创建计划时的尺码必填校验**

发送POST请求到 `/api/production/plans`，body中不包含size字段  
**预期结果**: 返回400错误，message包含"尺码不能为空"

- [ ] **Test 3: 验证成功创建包含color和size的计划**

发送POST请求到 `/api/production/plans`，body包含完整数据：
```json
{
  "batchNo": "PC-TEST-001",
  "productDefinitionId": "有效的产品ID",
  "color": "红色",
  "size": "L",
  "quantity": 100,
  "startDate": "2026-04-15",
  "endDate": "2026-04-30"
}
```
**预期结果**: 返回201成功，response body中的PlanVO包含color="红色", size="L"

- [ ] **Test 4: 验证查询计划列表包含color和size字段**

发送GET请求到 `/api/production/plans?page=1&size=10`  
**预期结果**: 返回的list中每个元素都包含color和size字段

- [ ] **Test 5: 验证更新计划的color和size字段**

发送PUT请求到 `/api/production/plans/{id}`，body包含：
```json
{
  "color": "蓝色",
  "size": "XL"
}
```
**预期结果**: 返回200成功，更新后的PlanVO反映新的color和size值

### 7.2 前端验证（需访问浏览器页面）

- [ ] **Test 6: 验证新增表单UI完整性**

打开生产计划页面 → 点击"新增计划"按钮  
**预期结果**: 对话框显示完整表单，包含颜色输入框和尺码下拉选择，位置正确

- [ ] **Test 7: 验证前端必填校验 - 颜色**

在新增表单中不填颜色直接点击确定  
**预期结果**: 触发Element Plus表单验证，颜色输入框下方显示红色提示"请输入颜色"

- [ ] **Test 8: 验证前端必填校验 - 尺码**

在新增表单中不选尺码直接点击确定  
**预期结果**: 触发表单验证，尺码下拉框下方显示红色提示"请选择尺码"

- [ ] **Test 9: 验证创建流程完整性**

填写完整表单（含颜色"黑色"、尺码"M"）→ 点击确定  
**预期结果**: 成功提示"新增计划成功"，列表自动刷新，新记录显示正确的颜色和尺码

- [ ] **Test 10: 验证编辑流程数据回显**

点击某条记录的"编辑"按钮  
**预期结果**: 编辑对话框打开，颜色和尺码字段正确显示该记录的原始值

- [ ] **Test 11: 验证列表展示效果**

查看生产计划列表表格  
**预期结果**: 表格包含"颜色"和"尺码"两列，数据正确显示，空数据显示为"-"

- [ ] **Test 12: 验证已审批计划的编辑权限**

创建计划 → 审批通过 → 点击"编辑"  
**预期结果**: 编辑对话框打开，颜色和尺码字段可编辑（不是disabled状态）

### 7.3 兼容性验证

- [ ] **Test 13: 验证旧数据的兼容显示**

查询在功能上线前创建的生产计划记录  
**预期结果**: 列表中这些记录的颜色和尺码列显示为"-"，不会报错

---

## 实施总结

### 修改文件清单

| 序号 | 文件路径 | 修改类型 | 主要改动 |
|------|---------|---------|---------|
| 1 | `model/ProductionPlan.java` | 新增字段 | +2行 (color, size) |
| 2 | `dto/PlanCreateRequest.java` | 新增字段+校验 | +6行 (@NotBlank) |
| 3 | `dto/PlanUpdateRequest.java` | 新增字段 | +2行 |
| 4 | `dto/PlanVO.java` | 新增字段 | +2行 |
| 5 | `service/impl/ProductionPlanServiceImpl.java` | 方法更新 | +12行 (3处修改) |
| 6 | `views/production/Plan.vue` | UI+逻辑 | +85行 (8处修改) |

**总计**: 6个文件，约新增109行代码

### 实施顺序建议

**推荐执行顺序**: Task 1 → Task 2 → Task 3 → Task 4 → Task 5 → Task 6 → Task 7

**理由**: 
1. 先完成后端数据层（Task 1-4），建立稳固的数据基础
2. 再完成业务逻辑层（Task 5），确保前后端数据流转正确
3. 然后实现前端界面（Task 6），提供用户交互能力
4. 最后进行全面验证（Task 7），确保功能完整可靠

### 注意事项

1. **MongoDB无需迁移**: 文档型数据库自动适配新字段，可直接部署
2. **向后兼容**: 所有修改都考虑了null安全处理，不影响现有数据
3. **遵循现有模式**: 严格遵循项目现有的代码风格和架构模式
4. **渐进式验证**: 建议每个Task完成后立即进行局部测试

---

**下一步行动**: 选择执行方式（Subagent-Driven 或 Inline Execution）并开始实施。
