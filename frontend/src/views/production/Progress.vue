<template>
  <div class="progress-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="批次号">
          <el-input
            v-model="searchForm.keyword"
            placeholder="请输入批次号"
            clearable
            @keyup.enter="fetchData"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-loading="loading" class="plan-cards">
      <el-empty v-if="planList.length === 0 && !loading" description="暂无进行中的生产计划" />
      <el-card
        v-for="plan in filteredPlans"
        :key="plan.id"
        class="plan-card"
        shadow="hover"
      >
        <template #header>
          <div class="plan-card-header">
            <span class="plan-name">{{ plan.batchNo }}</span>
            <el-tag :type="statusTagType(plan.status)" size="small">
              {{ statusText(plan.status) }}
            </el-tag>
          </div>
        </template>

        <div class="plan-info">
          <div class="plan-info-row">
            <span class="label">产品名称：</span>
            <span>{{ plan.productName }}{{ plan.productCode ? '-' + plan.productCode : '' }}</span>
          </div>
          <div class="plan-info-row">
            <span class="label">颜色：</span>
            <span>{{ plan.color || '-' }}</span>
            <span class="label" style="margin-left: 20px;">尺码：</span>
            <el-tag size="small">{{ plan.size || '-' }}</el-tag>
          </div>
          <div class="plan-info-row">
            <span class="label">总体进度：</span>
            <span>{{ plan.completedQuantity || 0 }} / {{ plan.quantity || 0 }} {{ plan.unit || '' }}</span>
          </div>
          <div class="plan-progress">
            <el-progress
              :percentage="plan.quantity ? Math.round((plan.completedQuantity || 0) / plan.quantity * 100) : 0"
              :stroke-width="18"
              :text-inside="true"
            />
          </div>
        </div>

        <el-divider content-position="left">任务列表</el-divider>

        <el-table :data="getTasksByPlanId(plan.id)" size="small" border>
          <el-table-column prop="taskName" label="任务名称" min-width="120" />
          <el-table-column prop="color" label="颜色" width="90" align="center">
            <template #default="{ row }">
              {{ row.color || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="size" label="尺码" width="70" align="center">
            <template #default="{ row }">
              <el-tag size="small">{{ row.size || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="assigneeName" label="分配人" width="100" align="center">
            <template #default="{ row }">
              {{ row.assigneeName || '未分配' }}
            </template>
          </el-table-column>
          <el-table-column prop="progress" label="进度" width="160" align="center">
            <template #default="{ row }">
              <el-progress
                :percentage="row.progress || 0"
                :status="row.progress === 100 ? 'success' : ''"
                :stroke-width="12"
                :text-inside="true"
              />
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="taskStatusTagType(row.status)" size="small">
                {{ taskStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getPlanList, getTaskList } from '../../api/production'

const loading = ref(false)
const planList = ref([])
const taskList = ref([])

const searchForm = reactive({
  keyword: ''
})

const filteredPlans = computed(() => {
  if (!searchForm.keyword) return planList.value
  return planList.value.filter(p =>
    p.batchNo && p.batchNo.includes(searchForm.keyword)
  )
})

onMounted(() => {
  fetchData()
})

async function fetchData() {
  loading.value = true
  try {
    const [planRes, taskRes] = await Promise.all([
      getPlanList({ page: 1, size: 100, status: 'IN_PROGRESS' }),
      getTaskList({ page: 1, size: 1000 })
    ])
    const planData = planRes.data || planRes
    const taskData = taskRes.data || taskRes
    planList.value = planData.list || []
    taskList.value = taskData.list || []
  } catch (error) {
    ElMessage.error('获取进度数据失败')
  } finally {
    loading.value = false
  }
}

function handleReset() {
  searchForm.keyword = ''
}

function getTasksByPlanId(planId) {
  return taskList.value.filter(t => t.planId === planId)
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
    PENDING: '待处理',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成'
  }
  return map[status] || status
}
</script>

<style scoped>
.progress-container {
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

.plan-cards {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.plan-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.plan-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.plan-info {
  margin-bottom: 8px;
}

.plan-info-row {
  margin-bottom: 8px;
  font-size: 14px;
  color: #606266;
}

.plan-info-row .label {
  color: #909399;
}

.plan-progress {
  margin-top: 8px;
}
</style>
