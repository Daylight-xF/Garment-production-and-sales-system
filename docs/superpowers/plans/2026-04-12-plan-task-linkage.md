# 生产计划与任务联动机制 - 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现生产计划与生产任务的完整联动机制，包括自动任务生成、进度同步、状态流转和手动确认功能。

**Architecture:** 采用方案一（增强现有架构），在 ProductionPlanServiceImpl 和 ProductionTaskServiceImpl 中新增联动方法，通过 ProductionPlanController 暴露3个新接口，前端 Plan.vue 增加动态按钮和任务详情弹窗。

**Tech Stack:** Spring Boot + MongoDB + Vue 3 + Element Plus + RESTful API

---

## 文件结构总览

### 后端文件（6个）
| 文件 | 操作 | 职责 |
|------|------|------|
| `ProductionTask.java` | 修改 | 增加 planQuantity、completedQuantity 字段 |
| `TaskVO.java` | 修改 | 增加 planQuantity、completedQuantity 字段 |
| `ProductionPlanService.java` | 修改 | 增加 startProduction、completePlan、getTasksByPlanId 方法签名 |
| `ProductionPlanServiceImpl.java` | 修改 | 实现3个新方法 + @Transactional |
| `ProductionPlanController.java` | 修改 | 增加 /start、/complete、/tasks 三个接口 |
| `ProductionTaskServiceImpl.java` | 修改 | 增强 updateProgress 方法的联动逻辑 |

### 前端文件（2个）
| 文件 | 操作 | 职责 |
|------|------|------|
| `production.js` | 修改 | 增加 startProduction、completePlan、getPlanTasks 3个API函数 |
| `Plan.vue` | 修改 | 动态按钮列、任务详情弹窗、新增处理函数 |

---

### Task 1: 增强 ProductionTask 数据模型

**Files:**
- Modify: `src/main/java/com/garment/model/ProductionTask.java`
- Modify: `src/main/java/com/garment/dto/TaskVO.java`

- [ ] **Step 1: 在 ProductionTask.java 中增加2个字段**

在 `private String description;` 之后添加：
```java
private Integer planQuantity = 0;

private Integer completedQuantity = 0;
```

- [ ] **Step 2: 在 TaskVO.java 中增加2个字段**

在 `private Integer progress;` 之后添加：
```java
private Integer planQuantity;

private Integer completedQuantity;
```

- [ ] **Step 3: 验证编译通过**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/garment/model/ProductionTask.java src/main/java/com/garment/dto/TaskVO.java
git commit -m "feat(task): 增加 planQuantity 和 completedQuantity 字段"
```

---

### Task 2: 扩展 ProductionPlanService 接口

**Files:**
- Modify: `src/main/java/com/garment/service/ProductionPlanService.java`

- [ ] **Step 1: 在接口末尾增加3个方法签名**

```java
import com.garment.dto.TaskVO;
import java.util.List;

PlanVO startProduction(String planId, String userId);

PlanVO completePlan(String planId);

List<TaskVO> getTasksByPlanId(String planId);
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS (会有编译错误因为实现类还没更新)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/garment/service/ProductionPlanService.java
git commit -m "feat(plan): 扩展 Service 接口增加联动方法"
```

---

### Task 3: 实现 ProductionPlanService 联动方法

**Files:**
- Modify: `src/main/java/com/garment/service/impl/ProductionPlanServiceImpl.java`

- [ ] **Step 1: 添加 import 语句**

在文件顶部 import 区域添加：
```java
import com.garment.dto.TaskVO;
import com.garment.model.ProductionTask;
import com.garment.repository.ProductionTaskRepository;
import javax.transaction.Transactional;
import java.util.stream.Collectors;
```

- [ ] **Step 2: 在构造函数中注入 ProductionTaskRepository**

修改构造函数为：
```java
private final ProductionTaskRepository productionTaskRepository;

public ProductionPlanServiceImpl(ProductionPlanRepository productionPlanRepository,
                                  UserRepository userRepository,
                                  ProductDefinitionRepository productDefinitionRepository,
                                  ProductionTaskRepository productionTaskRepository) {
    this.productionPlanRepository = productionPlanRepository;
    this.userRepository = userRepository;
    this.productDefinitionRepository = productDefinitionRepository;
    this.productionTaskRepository = productionTaskRepository;
}
```

- [ ] **Step 3: 实现 startProduction 方法**

在 `approvePlan` 方法之后添加：
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

- [ ] **Step 4: 实现 completePlan 方法**

```java
@Override
@Transactional
public PlanVO completePlan(String planId) {
    ProductionPlan plan = productionPlanRepository.findById(planId)
            .orElseThrow(() -> new BusinessException("生产计划不存在"));

    if (!"IN_PROGRESS".equals(plan.getStatus())) {
        throw new BusinessException("只有进行中状态的计划才能完成");
    }

    List<ProductionTask> tasks = productionTaskRepository.findByPlanId(planId);
    if (tasks.isEmpty()) {
        throw new BusinessException("该计划没有关联的生产任务");
    }

    boolean allCompleted = tasks.stream()
            .allMatch(task -> "COMPLETED".equals(task.getStatus()));

    if (!allCompleted) {
        long completedCount = tasks.stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()))
                .count();
        throw new BusinessException(
                "还有" + (tasks.size() - completedCount) + "个任务未完成，无法确认完成"
        );
    }

    plan.setStatus("COMPLETED");
    plan.setCompletedQuantity(plan.getQuantity());
    ProductionPlan savedPlan = productionPlanRepository.save(plan);

    return convertToVO(savedPlan);
}
```

- [ ] **Step 5: 实现 getTasksByPlanId 方法**

```java
@Override
public List<TaskVO> getTasksByPlanId(String planId) {
    if (!productionPlanRepository.existsById(planId)) {
        throw new BusinessException("生产计划不存在");
    }

    List<ProductionTask> tasks = productionTaskRepository.findByPlanId(planId);

    return tasks.stream()
            .map(this::convertTaskToVO)
            .collect(Collectors.toList());
}

private TaskVO convertTaskToVO(ProductionTask task) {
    return TaskVO.builder()
            .id(task.getId())
            .planId(task.getPlanId())
            .planName(task.getPlanName())
            .taskName(task.getTaskName())
            .assignee(task.getAssignee())
            .assigneeName(task.getAssigneeName())
            .progress(task.getProgress())
            .planQuantity(task.getPlanQuantity())
            .completedQuantity(task.getCompletedQuantity())
            .status(task.getStatus())
            .startDate(task.getStartDate())
            .endDate(task.getEndDate())
            .description(task.getDescription())
            .createBy(task.getCreateBy())
            .createTime(task.getCreateTime())
            .updateTime(task.getUpdateTime())
            .build();
}
```

- [ ] **Step 6: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/garment/service/impl/ProductionPlanServiceImpl.java
git commit -m "feat(plan): 实现开始生产、完成确认、查看任务的联动方法"
```

---

### Task 4: 扩展 ProductionPlanController 接口

**Files:**
- Modify: `src/main/java/com/garment/controller/ProductionPlanController.java`

- [ ] **Step 1: 添加 import**

在文件顶部添加：
```java
import com.garment.dto.TaskVO;
import java.util.List;
```

- [ ] **Step 2: 在 approvePlan 方法后添加3个新接口**

```java
@PostMapping("/{id}/start")
@PreAuthorize("hasAuthority('PLAN_UPDATE')")
public Result<PlanVO> startProduction(@PathVariable String id,
                                       Authentication authentication) {
    String userId = (String) authentication.getPrincipal();
    PlanVO planVO = productionPlanService.startProduction(id, userId);
    return Result.success(planVO);
}

@PostMapping("/{id}/complete")
@PreAuthorize("hasAuthority('PLAN_UPDATE')")
public Result<PlanVO> completePlan(@PathVariable String id) {
    PlanVO planVO = productionPlanService.completePlan(id);
    return Result.success(planVO);
}

@GetMapping("/{id}/tasks")
@PreAuthorize("hasAuthority('PLAN_READ')")
public Result<List<TaskVO>> getPlanTasks(@PathVariable String id) {
    List<TaskVO> tasks = productionPlanService.getTasksByPlanId(id);
    return Result.success(tasks);
}
```

- [ ] **Step 3: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/garment/controller/ProductionPlanController.java
git commit -m "feat(plan-controller): 添加开始生产、完成确认、查看任务接口"
```

---

### Task 5: 增强 ProductionTaskServiceImpl 进度联动逻辑

**Files:**
- Modify: `src/main/java/com/garment/service/impl/ProductionTaskServiceImpl.java`

- [ ] **Step 1: 修改 updateProgress 方法**

将现有的 updateProgress 方法替换为：
```java
@Override
@Transactional
public TaskVO updateProgress(String id, Integer progress) {
    ProductionTask task = productionTaskRepository.findById(id)
            .orElseThrow(() -> new BusinessException("生产任务不存在"));

    if (progress < 0 || progress > 100) {
        throw new BusinessException("进度必须在0-100之间");
    }

    task.setProgress(progress);

    if (task.getPlanQuantity() != null && task.getPlanQuantity() > 0) {
        int completed = (int) Math.round(task.getPlanQuantity() * progress / 100.0);
        task.setCompletedQuantity(completed);
    }

    if (progress == 100) {
        task.setStatus("COMPLETED");
    } else if ("PENDING".equals(task.getStatus()) || "COMPLETED".equals(task.getStatus())) {
        task.setStatus("IN_PROGRESS");
    }

    ProductionTask saved = productionTaskRepository.save(task);

    updatePlanCompletedQuantityEnhanced(task.getPlanId());

    return convertToVO(saved);
}
```

- [ ] **Step 2: 替换 updatePlanCompletedQuantity 为增强版本**

将原有的 `updatePlanCompletedQuantity` 方法替换为：
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

- [ ] **Step 3: 更新 convertToVO 方法中的新字段**

在 convertToVO 方法的 builder 中添加：
```java
.planQuantity(task.getPlanQuantity())
.completedQuantity(task.getCompletedQuantity())
```

位置：在 `.progress(task.getProgress())` 之后

- [ ] **Step 4: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/garment/service/impl/ProductionTaskServiceImpl.java
git commit -m "feat(task-service): 增强进度更新的联动逻辑和数量计算"
```

---

### Task 6: 扩展前端 API 层

**Files:**
- Modify: `frontend/src/api/production.js`

- [ ] **Step 1: 在文件末尾添加3个API函数**

```javascript
export function startProduction(planId) {
  return request({
    url: `/production/plans/${planId}/start`,
    method: 'post'
  })
}

export function completePlan(planId) {
  return request({
    url: `/production/plans/${planId}/complete`,
    method: 'post'
  })
}

export function getPlanTasks(planId) {
  return request({
    url: `/production/plans/${planId}/tasks`,
    method: 'get'
  })
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/api/production.js
git commit -m "feat(api): 添加开始生产、完成确认、查看任务的前端API"
```

---

### Task 7: 改造 Plan.vue - 动态操作按钮

**Files:**
- Modify: `frontend/src/views/production/Plan.vue`

- [ ] **Step 1: 更新 script 中的 import 语句**

将现有的 import 行：
```javascript
import { getPlanList, createPlan, updatePlan, deletePlan, approvePlan } from '../../api/production'
```

替换为：
```javascript
import { getPlanList, createPlan, updatePlan, deletePlan, approvePlan, startProduction, completePlan, getPlanTasks } from '../../api/production'
```

- [ ] **Step 2: 添加新的响应式变量**

在 `const approveForm = reactive(...)` 之后添加：
```javascript
const taskDialogVisible = ref(false)
const loadingTasks = ref(false)
const planTaskList = ref([])
```

- [ ] **Step 3: 替换操作列为动态按钮**

将第61-73行的 `<el-table-column label="操作">` 整个替换为：
```vue
<el-table-column label="操作" width="280" fixed="right">
  <template #default="{ row }">
    <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
    
    <el-button
      v-if="row.status === 'PENDING'"
      type="success"
      link
      size="small"
      @click="handleApprove(row)"
    >审批</el-button>
    
    <el-button
      v-if="row.status === 'APPROVED'"
      type="warning"
      link
      size="small"
      @click="handleStartProduction(row)"
    >开始生产</el-button>
    
    <el-button
      v-if="row.status === 'IN_PROGRESS'"
      type="primary"
      link
      size="small"
      @click="handleViewTasks(row)"
    >查看任务</el-button>
    
    <el-button
      v-if="row.status === 'IN_PROGRESS' && canCompletePlan(row)"
      type="success"
      link
      size="small"
      @click="handleCompletePlan(row)"
    >完成确认</el-button>
    
    <el-button
      v-if="row.status === 'PENDING' || row.status === 'CANCELLED'"
      type="danger"
      link
      size="small"
      @click="handleDelete(row)"
    >删除</el-button>
  </template>
</el-table-column>
```

- [ ] **Step 4: 在 approveDialog 后添加任务详情弹窗**

在第208行 `</el-dialog>` 之后添加完整的任务详情对话框组件（见下方完整代码）

- [ ] **Step 5: 添加新的处理函数**

在 `handleDelete` 函数之后、`statusTagType` 函数之前添加：
```javascript
async function handleStartProduction(row) {
  try {
    await ElMessageBox.confirm(
      `确定开始执行计划【${row.planName}】吗？<br/>系统将自动创建1个生产任务。`,
      '开始生产',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info', dangerouslyUseHTMLString: true }
    )
    
    await startProduction(row.id)
    ElMessage.success('已开始生产，任务已生成')
    fetchPlanList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

async function handleViewTasks(row) {
  currentPlan.value = row
  taskDialogVisible.value = true
  
  loadingTasks.value = true
  try {
    const res = await getPlanTasks(row.id)
    planTaskList.value = res.data || res
  } catch (error) {
    ElMessage.error(error.message || '获取任务列表失败')
    planTaskList.value = []
  } finally {
    loadingTasks.value = false
  }
}

function canCompletePlan(row) {
  return row.completedQuantity >= row.quantity
}

async function handleCompletePlan(row) {
  try {
    await ElMessageBox.confirm(
      `确定完成计划【${row.planName}】吗？<br/>已完成数量：${row.completedQuantity}/${row.quantity}`,
      '完成确认',
      { confirmButtonText: '确定完成', cancelButtonText: '取消', type: 'success', dangerouslyUseHTMLString: true }
    )
    
    await completePlan(row.id)
    ElMessage.success('计划已完成')
    fetchPlanList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

function goToTaskPage() {
  window.location.href = '/#/production/task'
}
```

- [ ] **Step 6: 添加辅助函数用于任务状态显示**

在现有 `statusText` 函数之后添加：
```javascript
function taskStatusTagType(status) {
  const map = {
    PENDING: 'warning',
    IN_PROGRESS: '',
    COMPLETED: 'success'
  }
  return map[status] || ''
}

function taskStatusText(status) {
  const map = {
    PENDING: '待分配',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成'
  }
  return map[status] || status
}
```

- [ ] **Step 7: 添加任务详情弹窗的样式**

在 `<style scoped>` 的 `.info-title` 样式之后添加：
```css
.stat-card {
  text-align: center;
  padding: 10px;
}
.stat-card .stat-number {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}
.stat-card .stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 8px;
}
.stat-card.success .stat-number {
  color: #67c23a;
}
.stat-card.warning .stat-number {
  color: #e6a23c;
}
```

- [ ] **Step 8: 任务详情弹窗完整代码**

在第208行的 `</el-dialog>` （审批对话框结束标签）之后插入：
```vue
<el-dialog
  v-model="taskDialogVisible"
  title="生产任务详情"
  width="800px"
  destroy-on-close
>
  <el-alert
    :title="`计划：${currentPlan.planName} | 产品：${currentPlan.productName}`"
    type="info"
    :closable="false"
    show-icon
    style="margin-bottom: 16px"
  />
  
  <el-row :gutter="16" style="margin-bottom: 16px">
    <el-col :span="8">
      <el-card shadow="hover" class="stat-card">
        <div class="stat-number">{{ currentPlan.quantity || 0 }}</div>
        <div class="stat-label">计划总数</div>
      </el-card>
    </el-col>
    <el-col :span="8">
      <el-card shadow="hover" class="stat-card success">
        <div class="stat-number">{{ currentPlan.completedQuantity || 0 }}</div>
        <div class="stat-label">已完成</div>
      </el-card>
    </el-col>
    <el-col :span="8">
      <el-card shadow="hover" class="stat-card warning">
        <div class="stat-number">{{ (currentPlan.quantity || 0) - (currentPlan.completedQuantity || 0) }}</div>
        <div class="stat-label">剩余数量</div>
      </el-card>
    </el-col>
  </el-row>
  
  <el-table :data="planTaskList" v-loading="loadingTasks" border stripe>
    <el-table-column prop="taskName" label="任务名称" min-width="150" />
    <el-table-column prop="assigneeName" label="负责人" width="100" align="center">
      <template #default="{ row }">
        {{ row.assigneeName || '未分配' }}
      </template>
    </el-table-column>
    <el-table-column label="进度" width="200" align="center">
      <template #default="{ row }">
        <el-progress
          :percentage="row.progress || 0"
          :status="row.progress === 100 ? 'success' : ''"
        />
      </template>
    </el-table-column>
    <el-table-column prop="status" label="状态" width="100" align="center">
      <template #default="{ row }">
        <el-tag :type="taskStatusTagType(row.status)" size="small">
          {{ taskStatusText(row.status) }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="180" fixed="right">
      <template #default="{ row }">
        <el-button
          v-if="row.status === 'PENDING'"
          type="primary"
          link
          size="small"
          @click="handleAssignTaskInDialog(row)"
        >分配</el-button>
        <el-button
          v-if="row.status !== 'COMPLETED'"
          type="warning"
          link
          size="small"
          @click="handleUpdateProgressInDialog(row)"
        >更新进度</el-button>
      </template>
    </el-table-column>
  </el-table>
  
  <template #footer>
    <el-button @click="taskDialogVisible = false">关闭</el-button>
    <el-button type="primary" @click="goToTaskPage">前往任务管理</el-button>
  </template>
</el-dialog>

<el-dialog
  v-model="progressDialogVisible"
  title="更新任务进度"
  width="400px"
  destroy-on-close
>
  <el-form label-width="80px">
    <el-form-item label="当前任务">
      <span>{{ currentTask.taskName }}</span>
    </el-form-item>
    <el-form-item label="当前进度">
      <span>{{ currentTask.progress || 0 }}%</span>
    </el-form-item>
    <el-form-item label="新进度">
      <el-slider v-model="newProgress" :min="0" :max="100" show-input />
    </el-form-item>
  </el-form>
  <template #footer>
    <el-button @click="progressDialogVisible = false">取消</el-button>
    <el-button type="primary" :loading="submitLoading" @click="handleSubmitProgress">确定</el-button>
  </template>
</el-dialog>
```

- [ ] **Step 9: 添加进度更新相关的变量和函数**

在已有的响应式变量区域添加：
```javascript
const progressDialogVisible = ref(false)
const currentTask = ref({})
const newProgress = ref(0)
```

并在函数区域添加：
```javascript
function handleAssignTaskInDialog(row) {
  ElMessage.info(`请在任务管理页面分配任务：【${row.taskName}】`)
  goToTaskPage()
}

function handleUpdateProgressInDialog(row) {
  currentTask.value = row
  newProgress.value = row.progress || 0
  progressDialogVisible.value = true
}

async function handleSubmitProgress() {
  submitLoading.value = true
  try {
    await updateTaskProgress(currentTask.value.id, { progress: newProgress.value })
    ElMessage.success('进度更新成功')
    progressDialogVisible.value = false
    handleViewTasks(currentPlan.value)
  } catch (error) {
    ElMessage.error(error.message || '更新失败')
  } finally {
    submitLoading.value = false
  }
}
```

同时在 import 中确保包含 `updateTaskProgress`:
```javascript
import { getPlanList, createPlan, updatePlan, deletePlan, approvePlan, startProduction, completePlan, getPlanTasks, updateTaskProgress } from '../../api/production'
```

- [ ] **Step 10: Commit**

```bash
git add frontend/src/views/production/Plan.vue
git commit -m "feat(plan-ui): 实现动态按钮、任务详情弹窗和进度更新功能"
```

---

### Task 8: 最终验证与测试

**Files:** 无需修改文件，仅测试验证

- [ ] **Step 1: 编译整个项目**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: 启动后端服务**

Run: `mvn spring-boot:run`
Expected: 服务启动成功，无报错

- [ ] **Step 3: 启动前端开发服务器**

Run: `cd frontend && npm run dev`
Expected: Vite 开发服务器启动成功

- [ ] **Step 4: 手动测试完整流程**
   1. 创建一个生产计划 → 状态应为 PENDING ✅
   2. 点击"审批" → 选择"通过" → 状态变为 APPROVED ✅
   3. 点击"开始生产" → 应弹出确认框 → 确认后状态变为 IN_PROGRESS ✅
   4. 点击"查看任务" → 弹出任务详情 → 显示1个任务，统计卡片显示数据 ✅
   5. 在弹窗中点击"更新进度" → 设置50% → 确认 → 进度条更新 ✅
   6. 再次查看任务 → 计划的"已完成"数量应变化 ✅
   7. 将任务进度设为100% → 任务状态变为已完成 ✅
   8. 关闭弹窗 → 列表中出现"完成确认"按钮 ✅
   9. 点击"完成确认" → 确认 → 计划状态变为 COMPLETED ✅

- [ ] **Step 5: 测试异常场景**
   1. 对非 APPROVED 状态点击"开始生产" → 应提示错误 ✅
   2. 对非 IN_PROGRESS 状态点击"完成确认" → 应提示错误 ✅
   3. 重复点击"开始生产" → 应提示"已生成任务" ✅
   4. 任务未完成时点击"完成确认" → 应提示还有X个任务未完成 ✅

- [ ] **Step 6: 提交最终版本**

```bash
git add -A
git commit -m "feat: 完成生产计划与任务联动机制的完整实现"
git push origin master
```

---

## 自检清单

### Spec 覆盖度检查
- ✅ 任务生成机制（审批通过→自动生成） → Task 3 Step 3
- ✅ 单任务模式（1个总任务+进度跟踪） → Task 3 Step 3
- ✅ 手动确认模式（按钮触发） → Task 3 Step 4, Task 7 Step 5
- ✅ 先生成后分配 → Task 3 Step 3 (assignee初始为null)
- ✅ 进度实时同步 → Task 5 Step 1-2
- ✅ 状态可视化 → Task 7 Step 4, 8 (弹窗+进度条)
- ✅ 错误处理机制 → Task 3, 5 (BusinessException)
- ✅ 数据一致性保证 → Task 5 (@Transactional + 汇总计算)

### 占位符扫描
- ✅ 无 TBD、TODO 或未完成章节
- ✅ 所有代码示例完整可执行
- ✅ 所有步骤都有具体的代码内容

### 类型一致性检查
- ✅ ProductionTask.planQuantity ↔ TaskVO.planQuantity
- ✅ ProductionTask.completedQuantity ↔ TaskVO.completedQuantity
- ✅ 方法名统一：startProduction, completePlan, getTasksByPlanId
- ✅ API路径统一：/{id}/start, /{id}/complete, /{id}/tasks

---

**计划版本**: v1.0  
**预估时间**: 90分钟（8个Task）  
**最后更新**: 2026-04-12
