# 生产计划与生产任务联动机制设计文档

**日期**: 2026-04-12  
**状态**: 已批准 ✅  
**方案**: 方案一 - 增强现有架构  

---

## 1. 项目背景与目标

### 1.1 背景
当前系统已实现生产计划和生产任务的独立管理，但两者之间缺少深度联动机制。用户需要手动在两个模块间切换操作，导致：
- 计划审批后无法自动启动任务生成
- 任务进度更新无法实时同步到计划状态
- 缺少端到端的流程可视化

### 1.2 目标
建立完整的**计划→任务→完成**闭环流程：
1. 审批通过后自动生成生产任务
2. 任务进度实时同步至计划
3. 手动确认机制保证数据准确性
4. 提供清晰的状态展示和异常处理

### 1.3 核心需求（用户确认）
- ✅ **任务生成方式**: 自动生成（审批通过后触发）
- ✅ **生成规则**: 按计划生成1个总任务（通过进度百分比跟踪）
- ✅ **状态联动**: 手动确认模式（按钮触发状态变更）
- ✅ **分配时机**: 先生成任务，后分配负责人

---

## 2. 整体架构设计

### 2.1 业务流程图

```
[创建计划] → [待审批 PENDING]
                │
                ▼
          [审批操作] ──────┬──────────┐
                  │           │          │
              [通过]       [拒绝]    (其他)
                  │       CANCELLED     │
                  ▼                     │
          [已审批 APPROVED]              │
                  │                     │
                  ▼
      ┌─── [点击"开始生产"] ◄────────────┘
      │
      │  系统自动执行：
      │  ① 创建生产任务（状态=PENDING）
      │  ② 计划状态 → IN_PROGRESS
      │
      ▼
[进行中 IN_PROGRESS] ←→ [生产任务管理]
      │                      │
      │   任务操作：         │
      │  ├─ 分配负责人       │
      │  ├─ 更新进度(0-100%) │
      │  └─ 任务完成         │
      │                      │
      ▼                      │
[所有任务完成?] ──NO──► (继续生产)
      │
     YES
      │
      ▼
┌─── [点击"完成确认"]
│
│  系统自动执行：
│  ① 验证所有任务已完成
│  ② 计划状态 → COMPLETED
│
▼
[已完成 COMPLETED]
```

### 2.2 状态机定义

#### 生产计划状态流转
| 当前状态 | 可转换到 | 触发条件 | 操作 |
|---------|---------|---------|------|
| PENDING | APPROVED, CANCELLED | 管理员审批 | 审批按钮 |
| APPROVED | IN_PROGRESS | 点击"开始生产" | 开始生产按钮 |
| IN_PROGRESS | COMPLETED | 所有任务完成 + 确认 | 完成确认按钮 |
| CANCELLED | - | 终态 | - |
| COMPLETED | - | 终态 | - |

#### 生产任务状态流转
| 当前状态 | 可转换到 | 触发条件 |
|---------|---------|---------|
| PENDING | IN_PROGRESS | 分配负责人或更新进度 |
| IN_PROGRESS | COMPLETED | 进度达到100% |
| COMPLETED | - | 终态 |

---

## 3. 后端实现设计

### 3.1 新增/修改的接口清单

| 接口名称 | HTTP方法 | 路径 | 功能描述 | 所属Controller |
|---------|---------|------|---------|---------------|
| 开始生产 | POST | `/api/plans/{id}/start` | 生成任务+状态变更 | ProductionPlanController |
| 完成确认 | POST | `/api/plans/{id}/complete` | 验证并完成计划 | ProductionPlanController |
| 查看任务 | GET | `/api/plans/{id}/tasks` | 获取关联任务列表 | ProductionPlanController |
| 更新进度 | PUT | `/api/tasks/{id}/progress` | 增强联动逻辑 | ProductionTaskController |

### 3.2 ProductionPlanServiceImpl 新增方法

#### 3.2.1 startProduction(String planId, String userId)

**功能**: 审批通过后启动生产，自动生成任务

**业务逻辑**:
1. 验证计划存在且状态为 `APPROVED`
2. 检查是否已存在关联任务（防止重复生成）
3. 创建1个生产任务（继承计划信息）
4. 更新计划状态为 `IN_PROGRESS`
5. 返回更新后的计划信息

**任务生成规则**:
```java
ProductionTask task = new ProductionTask();
task.setPlanId(plan.getId());
task.setPlanName(plan.getPlanName());
task.setTaskName(plan.getPlanName() + "-生产任务");  // 自动命名
task.setProgress(0);
task.setStatus("PENDING");
task.setPlanQuantity(plan.getQuantity());  // 继承计划数量
task.setCompletedQuantity(0);
task.setStartDate(new Date());
task.setEndDate(plan.getEndDate());
task.setDescription("自动从生产计划【" + plan.getPlanName() + "】生成");
task.setCreateBy(userId);
```

**错误处理**:
- 计划不存在 → `BusinessException("生产计划不存在")`
- 状态不是APPROVED → `BusinessException("只有已审批状态的计划才能开始生产")`
- 已有任务 → `BusinessException("该计划已生成任务，请勿重复操作")`

#### 3.2.2 completePlan(String planId)

**功能**: 手动确认计划完成

**业务逻辑**:
1. 验证计划存在且状态为 `IN_PROGRESS`
2. 查询所有关联任务
3. 验证所有任务状态为 `COMPLETED`
4. 更新计划状态为 `COMPLETED`
5. 设置 completedQuantity = quantity（全部完成）

**错误处理**:
- 计划不存在 → `BusinessException("生产计划不存在")`
- 状态不是IN_PROGRESS → `BusinessException("只有进行中状态的计划才能完成")`
- 无关联任务 → `BusinessException("该计划没有关联的生产任务")`
- 有未完成任务 → `BusinessException("还有X个任务未完成，无法确认完成")`

#### 3.2.3 getTasksByPlanId(String planId)

**功能**: 获取计划关联的任务列表

**业务逻辑**:
1. 验证计划存在
2. 查询 planId 匹配的所有任务
3. 转换为 TaskVO 列表返回

### 3.3 ProductionTaskServiceImpl 增强方法

#### 3.3.1 updateProgress(String id, Integer progress) - 增强

**新增逻辑**:
1. **计算实际完成数量**:
   ```java
   int completed = (int) Math.round(task.getPlanQuantity() * progress / 100.0);
   task.setCompletedQuantity(completed);
   ```

2. **增强联动更新**:
   ```java
   updatePlanCompletedQuantityEnhanced(task.getPlanId());
   ```
   
   该方法汇总所有任务的 completedQuantity 并更新计划的 completedQuantity 字段。

### 3.4 数据模型增强

#### ProductionTask.java 新增字段
```java
private Integer planQuantity = 0;      // 计划数量（从父计划继承）
private Integer completedQuantity = 0;  // 实际完成数量
```

#### TaskVO.java 新增字段
```java
private Integer planQuantity;
private Integer completedQuantity;
```

### 3.5 Controller 层实现

#### ProductionPlanController.java 新增接口
```java
@PostMapping("/{id}/start")
public Result<PlanVO> startProduction(@PathVariable String id, @RequestAttribute String userId) {
    PlanVO vo = productionPlanService.startProduction(id, userId);
    return Result.success(vo);
}

@PostMapping("/{id}/complete")
public Result<PlanVO> completePlan(@PathVariable String id) {
    PlanVO vo = productionPlanService.completePlan(id);
    return Result.success(vo);
}

@GetMapping("/{id}/tasks")
public Result<List<TaskVO>> getPlanTasks(@PathVariable String id) {
    List<TaskVO> tasks = productionPlanService.getTasksByPlanId(id);
    return Result.success(tasks);
}
```

### 3.6 事务控制

所有涉及多表操作的方法必须添加 `@Transactional` 注解：
- `startProduction()` - 创建任务 + 更新计划（2次DB操作）
- `completePlan()` - 验证任务 + 更新计划（多次查询+1次更新）
- `updateProgress()` - 更新任务 + 同步计划（2次DB操作）

---

## 4. 前端实现设计

### 4.1 Plan.vue 改造清单

#### 4.1.1 操作列增强（动态按钮）

**当前**: 固定显示 编辑、审批、删除（200px宽）  
**改造后**: 根据状态动态显示按钮（280px宽）

| 按钮 | 显示条件 | 样式 | 功能 |
|------|---------|------|------|
| 编辑 | 所有状态 | primary link | 打开编辑对话框 |
| 审批 | status === 'PENDING' | success link | 打开审批对话框 |
| 开始生产 | status === 'APPROVED' | warning link | 调用 startProduction API |
| 查看任务 | status === 'IN_PROGRESS' | primary link | 打开任务详情弹窗 |
| 完成确认 | status === 'IN_PROGRESS' && 可完成 | success link | 调用 completePlan API |
| 删除 | PENDING 或 CANCELLED | danger link | 删除计划 |

**可完成判断逻辑**:
```javascript
function canCompletePlan(row) {
  return row.completedQuantity >= row.quantity
}
```

#### 4.1.2 新增响应式变量
```javascript
const taskDialogVisible = ref(false)      // 任务详情弹窗可见性
const loadingTasks = ref(false)           // 任务列表加载状态
const planTaskList = ref([])              // 关联任务列表
```

#### 4.1.3 新增功能函数

**handleStartProduction(row)**:
- 弹出确认框："确定开始执行计划【xxx】吗？系统将自动创建1个生产任务。"
- 调用 `startProduction(row.id)` API
- 成功提示 + 刷新列表

**handleViewTasks(row)**:
- 设置 currentPlan = row
- 打开 taskDialogVisible
- 调用 `getPlanTasks(row.id)` 加载任务列表

**handleCompletePlan(row)**:
- 弹出确认框："确定完成计划【xxx】吗？已完成数量：X/Y"
- 调用 `completePlan(row.id)` API
- 成功提示 + 刷新列表

#### 4.1.4 任务详情弹窗组件

**布局结构**:
```
┌─────────────────────────────────────────────────────┐
│  生产任务详情                                  [×]  │
├─────────────────────────────────────────────────────┤
│  [ℹ️] 计划：xxx | 产品：yyy                          │
├─────────────────────────────────────────────────────┤
│  ┌─────────┐  ┌─────────┐  ┌─────────┐             │
│  │  100    │  │   80    │  │   20    │             │
│  │ 计划总数│  │ 已完成  │  │ 剩余数量│             │
│  └─────────┘  └─────────┘  └─────────┘             │
├─────────────────────────────────────────────────────┤
│  任务名称  │ 负责人  │ 进度条     │ 状态  │ 操作    │
│  xxx-生产任务│ 张三   │ ████████░░ │ 进行中│ [更新]  │
├─────────────────────────────────────────────────────┤
│                        [关闭]  [前往任务管理]        │
└─────────────────────────────────────────────────────┘
```

**核心元素**:
1. **信息概览**: el-alert 显示计划和产品信息
2. **统计卡片**: 3列卡片展示计划总数、已完成、剩余数量
3. **任务表格**: 
   - 列：任务名称、负责人、进度（el-progress）、状态（el-tag）、操作
   - 操作：未分配显示"分配"、非完成状态显示"更新进度"
4. **底部按钮**: 关闭弹窗、跳转到任务管理页面

### 4.2 API 层新增

**文件**: `frontend/src/api/production.js`

```javascript
// 开始生产
export function startProduction(planId) {
  return request({
    url: `/api/plans/${planId}/start`,
    method: 'post'
  })
}

// 完成确认
export function completePlan(planId) {
  return request({
    url: `/api/plans/${planId}/complete`,
    method: 'post'
  })
}

// 获取计划的任务列表
export function getPlanTasks(planId) {
  return request({
    url: `/api/plans/${planId}/tasks`,
    method: 'get'
  })
}
```

### 4.3 Task.vue 增强（可选）

**新增筛选条件**: 所属计划下拉框
- 数据源：调用计划列表API获取进行中的计划
- 筛选逻辑：传递 planId 参数给后端

---

## 5. 错误处理机制

### 5.1 后端错误码定义

| 错误场景 | 异常消息 | HTTP Code | 建议前端处理 |
|---------|---------|-----------|------------|
| 计划不存在 | "生产计划不存在" | 404 | 提示 + 刷新列表 |
| 状态不匹配 | "只有已审批状态的计划才能开始生产" | 400 | ElMessage.warning |
| 重复操作 | "该计划已生成任务，请勿重复操作" | 400 | ElMessage.warning + 刷新 |
| 无关联任务 | "该计划没有关联的生产任务" | 400 | ElMessage.error |
| 任务未完成 | "还有X个任务未完成，无法确认完成" | 400 | ElMessage.warning |
| 进度超范围 | "进度必须在0-100之间" | 400 | 表单验证拦截 |

### 5.2 前端错误处理规范

所有API调用统一使用 try-catch：
```javascript
try {
  await apiFunction()
  ElMessage.success('操作成功')
  fetchList()  // 刷新数据
} catch (error) {
  if (error !== 'cancel') {  // 排除用户取消操作
    ElMessage.error(error.message || '操作失败')
  }
}
```

---

## 6. 数据一致性保证

### 6.1 联动更新链路

```
用户更新任务进度
    ↓
ProductionTaskServiceImpl.updateProgress()
    ↓
① 计算 completedQuantity = planQuantity * progress / 100
② 更新 task.setProgress(progress)
③ 更新 task.setCompletedQuantity(completed)
④ 保存 task 到数据库
    ↓
调用 updatePlanCompletedQuantityEnhanced(planId)
    ↓
① 查询该 planId 下所有任务
② 汇总: totalCompleted = Σ task.completedQuantity
③ 更新 plan.setCompletedQuantity(totalCompleted)
④ 保存 plan 到数据库
    ↓
返回 TaskVO 给前端（含最新进度和数量）
```

### 6.2 并发控制

当前采用**乐观锁**策略（MongoDB文档级锁）：
- 每次更新都是原子操作
- @Transactional 保证多步操作的原子性
- 未来如需高并发，可考虑：
  - Redis 分布式锁
  - MongoDB 乐观锁版本号

---

## 7. 性能考虑

### 7.1 查询优化

**getTasksByPlanId()**:
- 使用已有的 `findByPlanId()` 方法（Repository层已优化）
- 避免N+1问题：批量查询而非循环查询

**updatePlanCompletedQuantityEnhanced()**:
- 仅在任务进度更新时触发
- 单次查询汇总，避免频繁IO

### 7.2 前端性能

**任务详情弹窗**:
- 懒加载：打开时才请求数据
- v-loading 避免空白闪烁
- destroy-on-close 释放内存

**列表刷新**:
- 操作成功后才刷新，避免无效请求
- 使用 pagination 参数保持分页状态

---

## 8. 测试策略

### 8.1 单元测试（后端）

**测试用例**:

1. **startProduction 正常流程**
   - 输入：APPROVED状态的计划ID
   - 预期：创建1个任务，计划状态变为IN_PROGRESS

2. **startProduction 异常场景**
   - 输入：不存在的计划ID → 抛出BusinessException
   - 输入：PENDING状态的计划 → 抛出BusinessException
   - 输入：已有任务的计划 → 抛出BusinessException

3. **completePlan 正常流程**
   - 输入：IN_PROGRESS状态 + 所有任务COMPLETED
   - 预期：计划状态变为COMPLETED

4. **completePlan 异常场景**
   - 存在未完成任务 → 抛出带数量的异常消息

5. **updateProgress 联动测试**
   - 更新进度50% → 任务的completedQuantity = planQuantity * 0.5
   - 计划的completedQuantity = 所有任务之和

### 8.2 集成测试（前端）

**测试场景**:

1. **按钮显隐测试**
   - PENDING状态：显示[审批]、隐藏[开始生产]
   - APPROVED状态：显示[开始生产]、隐藏[查看任务]
   - IN_PROGRESS状态：显示[查看任务][完成确认]

2. **交互流程测试**
   - 创建计划 → 审批通过 → 点击开始生产 → 验证任务生成
   - 更新任务进度 → 验证计划已完成数量变化
   - 所有任务完成 → 点击完成确认 → 验证计划状态

3. **边界情况测试**
   - 快速连续点击按钮（防抖处理）
   - 网络异常时的错误提示
   - 权限不足时的接口拦截

---

## 9. 实施计划概览

### 9.1 开发阶段划分

**阶段一：后端基础（预计30分钟）**
1. 修改 ProductionTask.java 和 TaskVO.java（增加字段）
2. 在 ProductionPlanService 接口添加3个方法签名
3. 实现 ProductionPlanServiceImpl 的3个新方法
4. 在 ProductionPlanController 添加3个接口
5. 增强 ProductionTaskServiceImpl.updateProgress()

**阶段二：前端开发（预计40分钟）**
1. 在 production.js 添加3个API函数
2. 修改 Plan.vue 的操作列（动态按钮）
3. 添加3个新的处理函数
4. 创建任务详情弹窗组件
5. 添加样式和统计卡片

**阶段三：联调测试（预计20分钟）**
1. 启动后端服务，测试接口
2. 前端页面功能测试
3. 完整流程端到端测试
4. 异常场景测试
5. Bug修复和优化

**总计预估时间**: 90分钟

### 9.2 文件修改清单

**后端文件（6个）**:
- [x] `src/main/java/com/garment/model/ProductionTask.java` - 增加2个字段
- [x] `src/main/java/com/garment/dto/TaskVO.java` - 增加2个字段
- [x] `src/main/java/com/garment/service/ProductionPlanService.java` - 增加3个方法签名
- [x] `src/main/java/com/garment/service/impl/ProductionPlanServiceImpl.java` - 实现3个方法
- [x] `src/main/java/com/garment/controller/ProductionPlanController.java` - 增加3个接口
- [x] `src/main/java/com/garment/service/impl/ProductionTaskServiceImpl.java` - 增强updateProgress

**前端文件（2个）**:
- [x] `frontend/src/api/production.js` - 增加3个API函数
- [x] `frontend/src/views/production/Plan.vue` - 主要改造（按钮、弹窗、函数）

---

## 10. 风险与缓解措施

### 10.1 技术风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| MongoDB事务支持有限 | 数据一致性 | 低 | 使用@Transactional + 应用层补偿 |
| 前后端数据格式不一致 | 显示错误 | 中 | 统一使用VO对象，明确字段映射 |
| 并发更新冲突 | 数据覆盖 | 低 | 当前单用户场景风险低，未来加锁 |

### 10.2 业务风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| 用户误操作（重复点击） | 重复数据 | 中 | 前端防抖 + 后端校验 |
| 任务进度回退 | 数据混乱 | 低 | 前端限制只能增加进度 |
| 计划中途取消 | 孤立任务 | 中 | 取消时提示关联任务处理 |

---

## 11. 未来扩展方向

### 11.1 短期优化（1-2周内）
- [ ] 任务页面增加计划筛选功能
- [ ] 添加操作日志记录（谁在什么时间做了什么操作）
- [ ] 邮件/消息通知（任务分配、计划完成时通知相关人员）

### 11.2 中期增强（1个月内）
- [ ] 支持一个计划拆分为多个任务（按工序、按批次）
- [ ] 添加甘特图可视化展示计划和时间线
- [ ] 引入工作流引擎（如Flowable）管理复杂审批流

### 11.3 长期规划（3个月以上）
- [ ] 与库存模块联动（原材料消耗、成品入库）
- [ ] 与销售模块联动（订单→计划→任务→交付）
- [ ] 数据分析和报表（生产效率、瓶颈分析）

---

## 附录A：关键代码示例

### A.1 完整的 startProduction 方法

```java
@Override
@Transactional
public PlanVO startProduction(String planId, String userId) {
    ProductionPlan plan = productionPlanRepository.findById(planId)
        .orElseThrow(() -> new BusinessException("生产计划不存在"));
    
    if (!"APPROVED".equals(plan.getStatus())) {
        throw new BusinessException("只有已审批状态的计划才能开始生产");
    }
    
    List<ProductionTask> existingTasks = productionTaskRepository.findByPlanId(planId);
    if (!existingTasks.isEmpty()) {
        throw new BusinessException("该计划已生成任务，请勿重复操作");
    }
    
    ProductionTask task = new ProductionTask();
    task.setPlanId(plan.getId());
    task.setPlanName(plan.getPlanName());
    task.setTaskName(plan.getPlanName() + "-生产任务");
    task.setProgress(0);
    task.setStatus("PENDING");
    task.setPlanQuantity(plan.getQuantity());
    task.setCompletedQuantity(0);
    task.setStartDate(new Date());
    task.setEndDate(plan.getEndDate());
    task.setDescription("自动从生产计划【" + plan.getPlanName() + "】生成");
    task.setCreateBy(userId);
    
    productionTaskRepository.save(task);
    
    plan.setStatus("IN_PROGRESS");
    ProductionPlan savedPlan = productionPlanRepository.save(plan);
    
    return convertToVO(savedPlan);
}
```

### A.2 完整的 updatePlanCompletedQuantityEnhanced 方法

```java
private void updatePlanCompletedQuantityEnhanced(String planId) {
    if (!StringUtils.hasText(planId)) return;
    
    List<ProductionTask> tasks = productionTaskRepository.findByPlanId(planId);
    
    int totalCompleted = tasks.stream()
        .mapToInt(t -> t.getCompletedQuantity() != null ? t.getCompletedQuantity() : 0)
        .sum();
    
    ProductionPlan plan = productionPlanRepository.findById(planId).orElse(null);
    if (plan != null) {
        plan.setCompletedQuantity(totalCompleted);
        productionPlanRepository.save(plan);
    }
}
```

---

**文档版本**: v1.0  
**最后更新**: 2026-04-12  
**作者**: AI Assistant  
**审核状态**: 待用户最终确认
