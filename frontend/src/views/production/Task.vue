<template>
  <div class="task-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="待处理" value="PENDING" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
        </el-form-item>
        <el-form-item label="分配人">
          <el-input
            v-model="searchForm.assignee"
            placeholder="请输入分配人"
            clearable
            @keyup.enter="handleSearch"
          />
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
          <span>生产任务列表</span>
        </div>
      </template>

      <el-table :data="taskList" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="taskName" label="任务名称" min-width="120" />
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
        <el-table-column prop="assigneeName" label="分配人" width="100" align="center">
          <template #default="{ row }">
            {{ row.assigneeName || '未分配' }}
          </template>
        </el-table-column>
        <el-table-column prop="progress" label="进度" width="180" align="center">
          <template #default="{ row }">
            <el-progress
              :percentage="row.progress || 0"
              :status="row.progress === 100 ? 'success' : ''"
              :stroke-width="14"
              :text-inside="true"
            />
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="taskStatusTagType(row.status)" size="small">
              {{ taskStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始日期" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.startDate) }}
          </template>
        </el-table-column>
        <el-table-column prop="endDate" label="结束日期" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.endDate) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'COMPLETED'" type="primary" link size="small" @click="handleAssign(row)">分配</el-button>
            <el-button v-if="row.status !== 'COMPLETED' && row.assignee" type="success" link size="small" @click="handleProgress(row)">更新进度</el-button>
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
          @size-change="fetchTaskList"
          @current-change="fetchTaskList"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="assignDialogVisible"
      title="分配任务"
      width="450px"
      destroy-on-close
    >
      <el-form label-width="80px">
        <el-form-item label="任务名称">
          <span>{{ currentTask.taskName }}</span>
        </el-form-item>
        <el-form-item label="选择用户">
          <el-select v-model="assignForm.assignee" placeholder="请选择用户" filterable style="width: 100%">
            <el-option
              v-for="user in userList"
              :key="user.id"
              :label="user.realName || user.username"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleAssignSubmit">确定</el-button>
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
        <el-button type="primary" :loading="submitLoading" @click="handleProgressSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getTaskList, assignTask, updateTaskProgress } from '../../api/production'
import { getAssignableUsers } from '../../api/user'

const loading = ref(false)
const submitLoading = ref(false)
const taskList = ref([])
const userList = ref([])
const assignDialogVisible = ref(false)
const progressDialogVisible = ref(false)
const currentTask = ref({})

const searchForm = reactive({
  status: '',
  assignee: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

const assignForm = reactive({
  assignee: ''
})

const newCompletedQuantity = ref(0)

const calculatedProgress = computed(() => {
  if (!currentTask.value.planQuantity || currentTask.value.planQuantity <= 0) return 0
  return Math.round((newCompletedQuantity.value / currentTask.value.planQuantity) * 100)
})

onMounted(() => {
  fetchTaskList()
  fetchUserList()
})

async function fetchTaskList() {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.pageSize,
      status: searchForm.status,
      assignee: searchForm.assignee
    }
    const res = await getTaskList(params)
    const data = res.data || res
    taskList.value = data.list || []
    pagination.total = data.total || 0
  } catch (error) {
    ElMessage.error('获取任务列表失败')
  } finally {
    loading.value = false
  }
}

async function fetchUserList() {
  try {
    const res = await getAssignableUsers()
    const data = res.data || res
    userList.value = data || []
  } catch (error) {
    console.error('获取用户列表失败')
  }
}

function handleSearch() {
  pagination.page = 1
  fetchTaskList()
}

function handleReset() {
  searchForm.status = ''
  searchForm.assignee = ''
  pagination.page = 1
  fetchTaskList()
}

function handleAssign(row) {
  currentTask.value = row
  assignForm.assignee = row.assignee || ''
  assignDialogVisible.value = true
}

async function handleAssignSubmit() {
  if (!assignForm.assignee) {
    ElMessage.warning('请选择用户')
    return
  }
  submitLoading.value = true
  try {
    await assignTask(currentTask.value.id, { assignee: assignForm.assignee })
    ElMessage.success('分配成功')
    assignDialogVisible.value = false
    fetchTaskList()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '分配失败')
  } finally {
    submitLoading.value = false
  }
}

function handleProgress(row) {
  currentTask.value = row
  newCompletedQuantity.value = row.completedQuantity || 0
  progressDialogVisible.value = true
}

async function handleProgressSubmit() {
  submitLoading.value = true
  try {
    await updateTaskProgress(currentTask.value.id, { progress: calculatedProgress.value })
    ElMessage.success('进度更新成功')
    progressDialogVisible.value = false
    fetchTaskList()
  } catch (error) {
    ElMessage.error(error.message || '更新进度失败')
  } finally {
    submitLoading.value = false
  }
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
    PENDING: '待处理',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成'
  }
  return map[status] || status
}

function formatDateTime(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${day} ${h}:${min}`
}
</script>

<style scoped>
.task-container {
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
