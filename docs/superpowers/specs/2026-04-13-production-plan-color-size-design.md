# 生产计划颜色和尺码功能设计规范

**日期**: 2026-04-13
**状态**: 待审核
**方案**: 轻量级实现（方案A）

---

## 1. 需求概述

### 1.1 问题背景
当前生产计划管理功能的添加/编辑界面缺少**颜色（Color）**和**尺码（Size）**的选择功能，无法满足服装生产管理的实际业务需求。

### 1.2 业务目标
- 为生产计划添加颜色和尺码属性，完善产品规格信息
- 支持在创建和编辑生产计划时指定颜色和尺码
- 在生产计划列表中展示颜色和尺码信息
- 确保数据的完整性（必填校验）和一致性（前后端同步）

### 1.3 用户需求确认
- ✅ **尺码类型**: 标准服装尺码（XS, S, M, L, XL, XXL, XXXL）
- ✅ **字段属性**: 颜色和尺码均为必填项
- ✅ **UI风格**: 与现有Element Plus设计保持一致

---

## 2. 技术架构

### 2.1 技术栈
- **前端**: Vue 3 + Element Plus + Composition API
- **后端**: Spring Boot + Spring Data MongoDB
- **数据库**: MongoDB（文档型，无需迁移脚本）
- **数据校验**: javax.validation（后端）+ Element Plus表单验证（前端）

### 2.2 影响范围

#### 后端修改文件（5个）
```
src/main/java/com/garment/
├── model/
│   └── ProductionPlan.java              # 数据模型 - 添加color/size字段
├── dto/
│   ├── PlanCreateRequest.java           # 创建请求DTO - 添加字段+校验
│   ├── PlanUpdateRequest.java           # 更新请求DTO - 添加字段
│   └── PlanVO.java                      # 视图对象 - 添加字段
└── service/impl/
    └── ProductionPlanServiceImpl.java   # 服务层 - 更新业务逻辑
```

#### 前端修改文件（1个）
```
frontend/src/views/production/
└── Plan.vue                             # 页面组件 - 表单+表格+验证
```

---

## 3. 数据模型设计

### 3.1 字段定义

| 字段名 | 类型 | 长度 | 约束 | 说明 |
|--------|------|------|------|------|
| `color` | String | 50字符 | @NotBlank, 必填 | 产品颜色，支持中英文、色值等格式 |
| `size` | String | 10字符 | @NotBlank, 必填 | 产品尺码，枚举值 |

### 3.2 尺码选项定义

```javascript
// 前端尺码选项常量
const SIZE_OPTIONS = [
  { label: 'XS', value: 'XS' },
  { label: 'S', value: 'S' },
  { label: 'M', value: 'M' },
  { label: 'L', value: 'L' },
  { label: 'XL', value: 'XL' },
  { label: 'XXL', value: 'XXL' },
  { label: 'XXXL', value: 'XXXL' }
]
```

### 3.3 MongoDB文档结构示例

```json
{
  "_id": "ObjectId('...')",
  "batchNo": "PC-20260413-1234",
  "productDefinitionId": "prod_001",
  "productName": "夏季T恤",
  "color": "深蓝色",
  "size": "L",
  "quantity": 100,
  "unit": "件",
  "startDate": "2026-04-15",
  "endDate": "2026-04-30",
  "status": "PENDING",
  "description": "第一批次生产",
  "createBy": "user_001",
  "createTime": ISODate("2026-04-13T10:00:00Z"),
  "updateTime": ISODate("2026-04-13T10:00:00Z")
}
```

---

## 4. 后端详细设计

### 4.1 ProductionPlan.java 修改

**位置**: 第44行之后（description字段之前）

**新增字段**:
```java
private String color;      // 产品颜色
private String size;       // 产品尺码
```

### 4.2 PlanCreateRequest.java 修改

**新增字段**:
```java
@NotBlank(message = "颜色不能为空")
private String color;

@NotBlank(message = "尺码不能为空")
private String size;
```

**校验规则**: 使用javax.validation的@NotBlank注解确保必填

### 4.3 PlanUpdateRequest.java 修改

**新增字段**:
```java
private String color;
private String size;
```

**注意**: 更新请求不强制校验非空，允许部分更新

### 4.4 PlanVO.java 修改

**新增字段**（第31行之后）:
```java
private String color;
private String size;
```

### 4.5 ProductionPlanServiceImpl.java 修改

#### 4.5.1 createPlan方法（第57-84行）
**修改点**:
- 第78行之后添加：
  ```java
  plan.setColor(request.getColor());
  plan.setSize(request.getSize());
  ```

#### 4.5.2 updatePlan方法（第238-291行）
**修改点**:
- 第287行（description设置之后）添加：
  ```java
  if (request.getColor() != null) {
      plan.setColor(request.getColor());
  }
  if (request.getSize() != null) {
      plan.setSize(request.getSize());
  }
  ```

**权限控制**: 已审批（APPROVED）或进行中（IN_PROGRESS）的计划也允许修改颜色和尺码（与quantity等字段的逻辑保持一致）

#### 4.5.3 convertToVO方法（第493-521行）
**修改点**:
- 第517行（materialsDeducted设置之前）添加：
  ```java
  .color(plan.getColor())
  .size(plan.getSize())
  ```

---

## 5. 前端详细设计

### 5.1 UI布局设计

#### 5.1.1 表单区域布局顺序
```
1. 批次号 (batchNo)
2. 选择产品 (productDefinitionId)
3. 产品信息卡片 (自动填充显示)
4. 【新增】颜色 (color) ← 文本输入框
5. 【新增】尺码 (size)  ← 下拉选择框
6. 计划数量 (quantity)
7. 开始日期 (startDate)
8. 结束日期 (endDate)
9. 描述 (description)
```

#### 5.1.2 表格列布局顺序
```
批次号 | 产品名称 | 【新增】颜色 | 【新增】尺码 | 计划数量 | 已完成数量 | 开始日期 | 结束日期 | 状态 | 操作
```

### 5.2 组件设计

#### 5.2.1 颜色输入框
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
```

**特性**:
- 类型：文本输入框（el-input）
- 占位符提示：支持多种格式说明
- 最大长度：50字符
- 字数统计显示
- 编辑限制：已审批计划可编辑（与现有逻辑一致）

#### 5.2.2 尺码下拉选择
```vue
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

**特性**:
- 类型：下拉选择框（el-select）
- 选项：7个标准尺码（XS-XXXL）
- 宽度：100%自适应
- 编辑限制：已审批计划可编辑

### 5.3 数据绑定

#### 5.3.1 planForm响应式对象扩展
```javascript
const planForm = reactive({
  // ... 现有字段
  color: '',           // 新增：颜色
  size: ''             // 新增：尺码
})
```

#### 5.3.2 尺码选项常量
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

### 5.4 表单验证规则

#### 5.4.1 planFormRules扩展
```javascript
const planFormRules = {
  // ... 现有规则
  color: [
    { required: true, message: '请输入颜色', trigger: 'blur' },
    { max: 50, message: '颜色长度不能超过50个字符', trigger: 'blur' }
  ],
  size: [
    { required: true, message: '请选择尺码', trigger: 'change' }
  ]
}
```

### 5.5 表单初始化逻辑

#### 5.5.1 handleAdd方法（第523-536行）
**修改点**: Object.assign中添加color和size字段的重置
```javascript
Object.assign(planForm, {
  // ... 现有字段
  color: '',
  size: ''
})
```

#### 5.5.2 handleEdit方法（第538-553行）
**修改点**: Object.assign中添加color和size字段的赋值
```javascript
Object.assign(planForm, {
  // ... 现有字段
  color: row.color || '',
  size: row.size || ''
})
```

### 5.6 提交数据处理

#### 5.6.1 handleSubmit方法（第555-604行）
**无需额外修改** - planForm对象已经包含color和size字段，会自动提交到后端

**注意**: 对于已审批计划的编辑（isEditApproved为true），需要在submitData中也包含color和size字段：
```javascript
const submitData = isEditApproved ? {
  quantity: planForm.quantity,
  unit: planForm.unit,
  startDate: planForm.startDate,
  endDate: planForm.endDate,
  description: planForm.description,
  color: planForm.color,        // 新增
  size: planForm.size           // 新增
} : { ...planForm }
```

### 5.7 表格展示

#### 5.7.1 新增颜色列（产品名称列之后）
```vue
<el-table-column prop="color" label="颜色" width="100" align="center">
  <template #default="{ row }">
    {{ row.color || '-' }}
  </template>
</el-table-column>
```

#### 5.7.2 新增尺码列（颜色列之后）
```vue
<el-table-column prop="size" label="尺码" width="80" align="center">
  <template #default="{ row }">
    <el-tag size="small">{{ row.size || '-' }}</el-tag>
  </template>
</el-table-column>
```

**样式特点**:
- 颜色列：宽度100px，居中对齐，显示纯文本
- 尺码列：宽度80px，居中对齐，使用el-tag标签组件突出显示

---

## 6. 数据库影响分析

### 6.1 MongoDB特性优势
由于项目使用MongoDB文档型数据库，**无需编写传统的SQL迁移脚本**。MongoDB具有以下特性：

✅ **Schema-less特性**: 同一集合中的文档可以有不同的字段结构
✅ **自动适配**: 新插入的文档会自动包含新字段
✅ **向后兼容**: 已有文档不会因新字段的存在而产生错误（查询时返回null）
✅ **零停机部署**: 可以直接部署代码而无需执行数据库变更操作

### 6.2 数据一致性保障
虽然不需要迁移脚本，但仍需注意：

1. **新建计划**: 自动包含color和size字段（通过DTO校验确保必填）
2. **已有计划**: 查询时color和size字段为null，前端需做兼容处理（显示'-'）
3. **编辑已有计划**: 如果用户补充填写了color和size，更新时会保存这两个字段

### 6.3 兼容性处理策略
```javascript
// 前端显示时的null安全处理
{{ row.color || '-' }}
{{ row.size || '-' }}
```

---

## 7. 功能验证清单

### 7.1 单元测试要点

#### 后端测试
- [ ] 创建生产计划时不填颜色 → 返回400错误提示"颜色不能为空"
- [ ] 创建生产计划时不选尺码 → 返回400错误提示"尺码不能为空"
- [ ] 创建生产计划时填写有效color和size → 成功创建并返回完整数据
- [ ] 更新生产计划时修改color和size → 成功更新并返回新值
- [ ] 查询生产计划列表 → 返回的数据包含color和size字段
- [ ] 查询生产计划详情 → 返回的VO包含color和size字段

#### 前端测试
- [ ] 打开新增表单 → 显示颜色输入框和尺码下拉框
- [ ] 不填颜色直接提交 → 触发前端校验提示"请输入颜色"
- [ ] 不选尺码直接提交 → 触发前端校验提示"请选择尺码"
- [ ] 填写有效的color和size并提交 → 成功创建并在列表中显示
- [ ] 编辑已有计划 → 表单正确回显color和size值
- [ ] 列表页面 → 正确显示颜色和尺码列及数据

### 7.2 集成测试场景

#### 完整流程测试
1. **创建流程**
   ```
   点击"新增计划" → 填写批次号 → 选择产品 → 输入颜色 → 选择尺码 → 
   填写数量和日期 → 点击确定 → 验证列表显示正确的color和size
   ```

2. **编辑流程**
   ```
   点击"编辑"按钮 → 修改颜色和尺码 → 点击确定 → 
   验证列表数据更新成功
   ```

3. **审批后编辑流程**
   ```
   创建计划 → 审批通过 → 点击"编辑" → 修改颜色和尺码 → 
   验证已审批计划可以编辑这两个字段
   ```

### 7.3 边界情况测试

- [ ] 颜色输入超长字符串（>50字符）→ 前端拦截或后端校验
- [ ] 颜色输入特殊字符（如emoji）→ 正常存储和显示
- [ ] 颜色输入色值格式（#FF0000）→ 正确保存
- [ ] 快速连续点击提交按钮 → 防重复提交机制验证
- [ ] 网络异常时提交 → 错误提示和表单数据保留

---

## 8. 实施计划概要

### 8.1 开发阶段划分

#### 阶段1：后端数据层（预计30分钟）
1. 修改ProductionPlan.java - 添加字段
2. 修改PlanCreateRequest.java - 添加字段和校验
3. 修改PlanUpdateRequest.java - 添加字段
4. 修改PlanVO.java - 添加字段

#### 阶段2：后端业务层（预计20分钟）
5. 修改ProductionPlanServiceImpl.java - 更新3个方法

#### 阶段3：前端界面层（预计40分钟）
6. 修改Plan.vue - 表单区域添加组件
7. 修改Plan.vue - 表格区域添加列
8. 修改Plan.vue - 添加数据和验证逻辑

#### 阶段4：测试验证（预计30分钟）
9. 功能测试 - 创建/编辑/展示流程
10. 边界测试 - 校验规则和异常情况
11. 兼容性测试 - 已有数据显示

**总计预估时间**: 约2小时

### 8.2 文件修改清单

| 序号 | 文件路径 | 修改类型 | 代码行数变化 |
|------|---------|---------|-------------|
| 1 | `model/ProductionPlan.java` | 新增字段 | +2行 |
| 2 | `dto/PlanCreateRequest.java` | 新增字段+校验 | +6行 |
| 3 | `dto/PlanUpdateRequest.java` | 新增字段 | +2行 |
| 4 | `dto/PlanVO.java` | 新增字段 | +2行 |
| 5 | `service/impl/ProductionPlanServiceImpl.java` | 更新方法 | +12行 |
| 6 | `views/production/Plan.vue` | UI+逻辑 | +80行 |
| **合计** | **6个文件** | **-** | **约+104行** |

---

## 9. 风险评估与应对

### 9.1 技术风险

| 风险项 | 可能性 | 影响 | 应对措施 |
|--------|--------|------|---------|
| 已有数据缺少color/size字段导致前端报错 | 低 | 中 | 前端使用`|| '-'`做空值兼容 |
| 后端校验失败信息不准确 | 低 | 低 | 测试阶段验证错误提示文案 |
| MongoDB版本兼容性问题 | 极低 | 低 | Spring Data MongoDB自动处理 |

### 9.2 业务风险

| 风险项 | 可能性 | 影响 | 应对措施 |
|--------|--------|------|---------|
| 用户对必填规则的接受度 | 低 | 低 | 提供清晰的占位符提示文字 |
| 尺码选项不能满足未来需求 | 中 | 低 | 未来可升级为配置化方案（方案B）|

---

## 10. 扩展性考虑

### 10.1 当前设计的扩展能力

本设计方案虽然采用轻量级实现，但保留了良好的扩展性：

1. **颜色字段扩展**:
   - 当前：文本输入，支持任意格式
   - 未来可扩展为：颜色选择器组件（拾色器）、预设颜色下拉等

2. **尺码字段扩展**:
   - 当前：硬编码7个标准尺码
   - 未来可扩展为：从字典表/配置API动态获取尺码列表

3. **多属性扩展**:
   - 如需添加更多属性（材质、季节、款式等），只需重复本方案的步骤即可

### 10.2 升级路径

如果未来需要更复杂的规格管理（如多尺码多颜色组合），可以考虑：

- **方案B升级**: 将尺码选项提取到后端配置表
- **方案C升级**: 引入SKU（库存单位）概念，支持矩阵式规格管理
- **方案D升级**: 独立的规格管理模块，支持自定义属性模板

---

## 11. 总结

本设计规范基于**轻量级实现方案（方案A）**，在满足用户需求的前提下，最大化地保持了系统的简洁性和可维护性。核心优势包括：

✅ **改动范围小**: 仅修改6个文件，新增约104行代码
✅ **实施速度快**: 预计2小时内完成开发和测试
✅ **风险可控**: 利用MongoDB特性避免数据库迁移风险
✅ **用户体验好**: 符合Element Plus设计规范，交互流畅
✅ **扩展性强**: 为未来的功能增强预留了清晰的升级路径

该设计完全满足用户提出的4项核心要求：
1. ✅ 前端界面：添加颜色输入框和尺码下拉选择
2. ✅ 后端系统：数据模型、DTO、VO、Service全面支持
3. ✅ 数据库：MongoDB自动适配，零迁移成本
4. ✅ 功能验证：完整的单元测试和集成测试清单

---

**下一步行动**: 请审核此设计规范，确认无异议后将进入实施阶段。
