<template>
  <div class="plan-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="批次号（产品名称）">
          <el-input
            v-model="searchForm.keyword"
            placeholder="请输入批次号或产品名"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="待审批" value="PENDING" />
            <el-option label="已审批" value="APPROVED" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span>生产计划列表</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>新增计划
          </el-button>
        </div>
      </template>

      <el-table :data="planList" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="batchNo" label="批次号" min-width="120" />
        <el-table-column label="产品名称" min-width="150">
          <template #default="{ row }">
            {{ row.productName }}{{ row.productCode ? '-' + row.productCode : '' }}
          </template>
        </el-table-column>
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
        <el-table-column prop="quantity" label="计划数量" width="100" align="center" />
        <el-table-column prop="completedQuantity" label="已完成数量" width="110" align="center" />
        <el-table-column prop="startDate" label="计划开始日期" width="110">
          <template #default="{ row }">
            {{ row.startDate ? row.startDate.substring(0, 10) : '' }}
          </template>
        </el-table-column>
        <el-table-column prop="endDate" label="计划结束日期" width="110">
          <template #default="{ row }">
            {{ row.endDate ? row.endDate.substring(0, 10) : '' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'COMPLETED' && !canCompletePlan(row)" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
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
              v-if="row.status === 'CANCELLED'"
              type="danger"
              link
              size="small"
              @click="handleDelete(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchPlanList"
          @current-change="fetchPlanList"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogType === 'add' ? '新增生产计划' : '编辑生产计划'"
      width="550px"
      destroy-on-close
    >
      <el-form
        ref="planFormRef"
        :model="planForm"
        :rules="planFormRules"
        label-width="100px"
      >
        <el-form-item label="批次号" prop="batchNo">
          <el-input
            v-model="planForm.batchNo"
            placeholder="请输入批次号"
            :disabled="isEditApproved"
          >
            <template v-if="isEditApproved" #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="选择产品" prop="productDefinitionId">
          <el-select
            v-model="planForm.productDefinitionId"
            filterable
            placeholder="请选择产品定义"
            style="width: 100%"
            :disabled="isEditApproved"
            @change="handleProductChange"
          >
            <el-option
              v-for="item in productDefinitionList"
              :key="item.id"
              :label="`${item.productCode} - ${item.productName}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-card v-if="planForm.productDefinitionId" class="product-info-card" shadow="never">
          <template #header>
            <span class="info-title">产品信息（自动填充）</span>
          </template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="产品编号">
              {{ getCurrentProduct()?.productCode || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="产品名称">
              {{ planForm.productName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="产品分类">
              {{ getCurrentProduct()?.category || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="单位">
              {{ planForm.unit || '件' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-form-item label="颜色" prop="color">
          <el-input
            v-model="planForm.color"
            placeholder="请输入颜色（如：红色、深蓝色）"
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
        <el-form-item label="计划数量" prop="quantity">
          <el-input-number v-model="planForm.quantity" :min="quantityMin" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker
            v-model="planForm.startDate"
            type="date"
            placeholder="选择开始日期"
            value-format="YYYY-MM-DD"
            :disabled-date="disabledStartDate"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker
            v-model="planForm.endDate"
            type="date"
            placeholder="选择结束日期"
            value-format="YYYY-MM-DD"
            :disabled-date="disabledEndDate"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="planForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="approveDialogVisible"
      title="审批生产计划"
      width="450px"
      destroy-on-close
    >
      <el-form label-width="80px">
        <el-form-item label="批次号">
          <span>{{ currentPlan.batchNo }}</span>
        </el-form-item>
        <el-form-item label="审批结果">
          <el-radio-group v-model="approveForm.status">
            <el-radio value="APPROVED">通过</el-radio>
            <el-radio value="CANCELLED">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input
            v-model="approveForm.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入审批意见"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleApproveSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="taskDialogVisible"
      title="生产任务详情"
      width="800px"
      destroy-on-close
    >
      <el-alert
        :title="`批次号：${currentPlan.batchNo} | 产品：${currentPlan.productName}`"
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
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING' && row.status !== 'COMPLETED'"
              type="primary"
              link
              size="small"
              @click="handleAssignTaskInDialog(row)"
            >分配</el-button>
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
      width="450px"
      destroy-on-close
    >
      <el-form label-width="100px">
        <el-form-item label="任务名称">
          <span>{{ currentTask.taskName }}</span>
        </el-form-item>
        <el-form-item label="计划数量">
          <el-input :model-value="currentTask.planQuantity || 0" disabled size="small" />
          <div class="quantity-hint">（只读，不可修改）</div>
        </el-form-item>
        <el-form-item label="已完成数量">
          <el-input-number
            v-model="newCompletedQuantity"
            :min="(currentTask.completedQuantity || 0)"
            :max="currentTask.planQuantity || 999999"
            :precision="0"
            step="1"
            size="small"
            style="width: 100%"
          />
          <div class="quantity-hint">最小值：{{ currentTask.completedQuantity || 0 }}，最大值：{{ currentTask.planQuantity || '-' }}</div>
        </el-form-item>
        <el-form-item label="当前进度">
          <el-progress
            :percentage="calculatedProgress"
            :status="calculatedProgress === 100 ? 'success' : ''"
            :show-text="false"
          />
          <div class="progress-text">
            {{ newCompletedQuantity }} / {{ currentTask.planQuantity || 0 }}
            <span class="progress-pct">{{ calculatedProgress }}%</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="progressDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmitProgress">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import { getPlanList, createPlan, updatePlan, deletePlan, approvePlan, startProduction, completePlan, getPlanTasks, updateTaskProgress } from '../../api/production'
import { getProductDefinitionList } from '../../api/productDefinition'

const loading = ref(false)
const submitLoading = ref(false)
const planList = ref([])
const dialogVisible = ref(false)
const approveDialogVisible = ref(false)
const dialogType = ref('add')
const currentPlan = ref({})
const originalQuantity = ref(0)
const planFormRef = ref(null)
const productDefinitionList = ref([])

const searchForm = reactive({
  keyword: '',
  status: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

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

const isEditApproved = computed(() => {
  return dialogType.value === 'edit' && (currentPlan.value?.status === 'APPROVED' || currentPlan.value?.status === 'IN_PROGRESS')
})

const quantityMin = computed(() => {
  if (dialogType.value === 'edit' && currentPlan.value?.status === 'IN_PROGRESS') {
    return currentPlan.value.completedQuantity || 0
  }
  return 1
})

const approveForm = reactive({
  status: 'APPROVED',
  remark: ''
})

const taskDialogVisible = ref(false)
const loadingTasks = ref(false)
const planTaskList = ref([])
const progressDialogVisible = ref(false)
const currentTask = ref({})
const newCompletedQuantity = ref(0)

const calculatedProgress = computed(() => {
  if (!currentTask.value.planQuantity || currentTask.value.planQuantity <= 0) return 0
  return Math.round((newCompletedQuantity.value / currentTask.value.planQuantity) * 100)
})

const router = useRouter()

const sizeOptions = [
  { label: 'XS', value: 'XS' },
  { label: 'S', value: 'S' },
  { label: 'M', value: 'M' },
  { label: 'L', value: 'L' },
  { label: 'XL', value: 'XL' },
  { label: 'XXL', value: 'XXL' },
  { label: 'XXXL', value: 'XXXL' }
]

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

onMounted(() => {
  fetchPlanList()
  fetchProductDefinitions()
})

async function fetchPlanList() {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.pageSize,
      keyword: searchForm.keyword,
      status: searchForm.status
    }
    const res = await getPlanList(params)
    const data = res.data || res
    planList.value = data.list || []
    pagination.total = data.total || 0
  } catch (error) {
    ElMessage.error('获取计划列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchPlanList()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.status = ''
  pagination.page = 1
  fetchPlanList()
}

async function fetchProductDefinitions() {
  try {
    const res = await getProductDefinitionList({ size: 1000, status: '启用' })
    const data = res.data || res
    productDefinitionList.value = data.list || []
  } catch (error) {
    console.error('获取产品定义列表失败', error)
  }
}

function handleProductChange(productId) {
  const product = productDefinitionList.value.find(p => p.id === productId)
  if (product) {
    planForm.productName = product.productName
    planForm.unit = '件'
  }
}

function getCurrentProduct() {
  if (!planForm.productDefinitionId) return null
  return productDefinitionList.value.find(p => p.id === planForm.productDefinitionId)
}

function generateUniqueBatchNo() {
  const now = new Date()
  const dateStr = now.getFullYear().toString() +
    String(now.getMonth() + 1).padStart(2, '0') +
    String(now.getDate()).padStart(2, '0')
  const randomNum = Math.floor(1000 + Math.random() * 9000)
  const batchNo = `PC-${dateStr}-${randomNum}`
  const isDuplicate = planList.value.some(p => p.batchNo === batchNo)
  if (isDuplicate) {
    return generateUniqueBatchNo()
  }
  return batchNo
}

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

async function handleSubmit() {
  const form = planFormRef.value
  if (!form) return
  await form.validate(async (valid) => {
    if (!valid) return

    if (dialogType.value === 'edit' && isEditApproved.value) {
      const qtyDiff = planForm.quantity - originalQuantity.value
      if (qtyDiff !== 0) {
        const actionText = qtyDiff > 0 ? '扣减' : '返还'
        const diffText = Math.abs(qtyDiff)
        try {
          await ElMessageBox.confirm(
            `计划数量将从 ${originalQuantity.value} 修改为 ${planForm.quantity}（${qtyDiff > 0 ? '增加' : '减少'} ${diffText} 单位）<br/>` +
            `系统将自动${actionText}相应数量的原材料库存。<br/>` +
            `<span style="color: #E6A23C">请确认是否继续？</span>`,
            '数量变更确认',
            { confirmButtonText: '确认保存', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: true }
          )
        } catch (error) {
          return
        }
      }
    }

    submitLoading.value = true
    try {
      if (dialogType.value === 'add') {
        await createPlan(planForm)
        ElMessage.success('新增计划成功')
      } else {
        const submitData = isEditApproved.value ? {
          quantity: planForm.quantity,
          unit: planForm.unit,
          startDate: planForm.startDate,
          endDate: planForm.endDate,
          description: planForm.description,
          color: planForm.color,
          size: planForm.size
        } : { ...planForm }
        await updatePlan(currentPlan.value.id, submitData)
        ElMessage.success('编辑计划成功')
      }
      dialogVisible.value = false
      fetchPlanList()
    } catch (error) {
      ElMessage.error(error.message || '操作失败')
    } finally {
      submitLoading.value = false
    }
  })
}

function handleApprove(row) {
  currentPlan.value = row
  approveForm.status = 'APPROVED'
  approveForm.remark = ''
  approveDialogVisible.value = true
}

async function handleApproveSubmit() {
  submitLoading.value = true
  try {
    await approvePlan(currentPlan.value.id, { status: approveForm.status })
    ElMessage.success(approveForm.status === 'APPROVED' ? '审批通过' : '已拒绝')
    approveDialogVisible.value = false
    fetchPlanList()
  } catch (error) {
    ElMessage.error(error.message || '审批失败')
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除计划"${row.batchNo}"吗？此操作不可恢复。`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deletePlan(row.id)
    ElMessage.success('删除成功')
    fetchPlanList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

async function handleStartProduction(row) {
  try {
    await ElMessageBox.confirm(
      `确定开始执行计划【${row.batchNo}】吗？<br/>系统将自动创建1个生产任务。`,
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
      `确定完成计划【${row.batchNo}】吗？<br/>已完成数量：${row.completedQuantity}/${row.quantity}`,
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
  router.push('/production/task')
}

function handleAssignTaskInDialog(row) {
  ElMessage.info(`请在任务管理页面分配任务：【${row.taskName}】`)
  goToTaskPage()
}

function handleUpdateProgressInDialog(row) {
  currentTask.value = row
  newCompletedQuantity.value = row.completedQuantity || 0
  progressDialogVisible.value = true
}

async function handleSubmitProgress() {
  submitLoading.value = true
  try {
    await updateTaskProgress(currentTask.value.id, { progress: calculatedProgress.value })
    ElMessage.success('进度更新成功')
    progressDialogVisible.value = false
    handleViewTasks(currentPlan.value)
  } catch (error) {
    ElMessage.error(error.message || '更新失败')
  } finally {
    submitLoading.value = false
  }
}

function statusTagType(status) {
  const map = {
    PENDING: 'warning',
    APPROVED: 'success',
    IN_PROGRESS: '',
    COMPLETED: 'success',
    CANCELLED: 'info'
  }
  return map[status] || ''
}

function statusText(status) {
  const map = {
    PENDING: '待审批',
    APPROVED: '已审批',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  }
  return map[status] || status
}

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

function disabledStartDate(time) {
  return time.getTime() < Date.now() - 86400000
}

function disabledEndDate(time) {
  if (planForm.startDate) {
    const startDate = new Date(planForm.startDate)
    startDate.setHours(0, 0, 0, 0)
    return time.getTime() < startDate.getTime()
  }
  return time.getTime() < Date.now() - 86400000
}
</script>

<style scoped>
.plan-container {
  padding: 20px;
}

.search-card {
  margin-bottom: 16px;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.product-info-card {
  margin-bottom: 16px;
  background-color: #f5f7fa;
}

.info-title {
  font-weight: 600;
  color: #409eff;
}

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
.quantity-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.progress-text {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  font-size: 13px;
  color: #606266;
  padding-left: 4px;
}
.progress-pct {
  font-weight: 600;
  color: #409eff;
  min-width: 50px;
  text-align: right;
}
</style>
