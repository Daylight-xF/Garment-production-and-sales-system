<template>
  <div class="alert-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="待处理" value="PENDING" />
            <el-option label="已处理" value="HANDLED" />
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
          <span>库存预警列表</span>
        </div>
      </template>

      <el-table :data="alertList" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="itemType" label="物品类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.itemType === 'RAW_MATERIAL' ? '' : 'success'" size="small">
              {{ row.itemType === 'RAW_MATERIAL' ? '原材料' : '成品' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="itemName" label="物品名称" min-width="120" />
        <el-table-column prop="currentQuantity" label="当前数量" width="100" align="center">
          <template #default="{ row }">
            <span style="color: #F56C6C; font-weight: bold">{{ row.currentQuantity }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="threshold" label="预警阈值" width="100" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PENDING' ? 'danger' : 'success'" size="small">
              {{ row.status === 'PENDING' ? '待处理' : '已处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="预警时间" min-width="60" align="center">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="handleTime" label="处理时间" min-width="60" align="center">
          <template #default="{ row }">
            {{ formatDateTime(row.handleTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              type="warning"
              link
              size="small"
              @click="handleAlert(row)"
            >
              处理预警
            </el-button>
            <el-button type="primary" link size="small" @click="handleViewRecords(row)">
              出入库记录
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="handleDialogVisible"
      title="处理预警"
      width="440px"
      destroy-on-close
    >
      <el-form label-width="80px">
        <el-form-item label="物品类型">
          <el-input :model-value="currentAlert.itemType === 'RAW_MATERIAL' ? '原材料' : '成品'" disabled />
        </el-form-item>
        <el-form-item label="物品名称">
          <el-input :model-value="currentAlert.itemName" disabled />
        </el-form-item>
        <el-form-item label="当前数量">
          <el-input :model-value="String(currentAlert.currentQuantity)" disabled />
        </el-form-item>
        <el-form-item label="预警阈值">
          <el-input :model-value="String(currentAlert.threshold)" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitHandleAlert">确认处理</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="recordDialogVisible"
      title="出入库记录"
      width="900px"
      destroy-on-close
    >
      <el-table :data="recordList" v-loading="recordLoading" border stripe style="width: 100%" :max-height="600">
        <el-table-column prop="inventoryType" label="类型" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.inventoryType === 'IN' ? 'success' : 'danger'" size="small">
              {{ row.inventoryType === 'IN' ? '入库' : '出库' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="itemName" label="物品名称" min-width="120" align="center"/>
        <el-table-column prop="quantity" label="数量" width="70" align="center" />
        <el-table-column prop="operatorName" label="操作人" width="90" align="center"/>
        <el-table-column prop="reason" label="原因" min-width="150" align="center"/>
        <el-table-column prop="createTime" label="时间" min-width="100">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="recordPagination.page"
          v-model:page-size="recordPagination.size"
          :page-sizes="[5, 10, 20]"
          :total="recordPagination.total"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchRecords"
          @current-change="fetchRecords"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getAlertList, handleAlert as handleAlertApi, getInventoryRecords } from '../../api/inventory'

function formatDateTime(val) {
  if (!val) return ''
  const d = new Date(val)
  if (isNaN(d.getTime())) return val
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const loading = ref(false)
const submitLoading = ref(false)
const recordLoading = ref(false)
const alertList = ref([])
const recordList = ref([])
const handleDialogVisible = ref(false)
const recordDialogVisible = ref(false)

const currentAlert = reactive({
  id: '',
  itemType: '',
  itemName: '',
  currentQuantity: 0,
  threshold: 0
})

const searchForm = reactive({
  status: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const recordPagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const currentRecordFilter = reactive({
  itemType: '',
  itemId: ''
})

onMounted(() => {
  fetchList()
})

async function fetchList() {
  loading.value = true
  try {
    const params = {
      status: searchForm.status,
      page: pagination.page,
      size: pagination.size
    }
    const res = await getAlertList(params)
    const data = res.data || res
    alertList.value = data.list || []
    pagination.total = data.total || 0
  } catch (error) {
    ElMessage.error('获取预警列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchList()
}

function handleReset() {
  searchForm.status = ''
  pagination.page = 1
  fetchList()
}

function handleAlert(row) {
  currentAlert.id = row.id
  currentAlert.itemType = row.itemType
  currentAlert.itemName = row.itemName
  currentAlert.currentQuantity = row.currentQuantity
  currentAlert.threshold = row.threshold
  handleDialogVisible.value = true
}

async function submitHandleAlert() {
  submitLoading.value = true
  try {
    await handleAlertApi(currentAlert.id, { handleBy: '' })
    ElMessage.success('预警处理成功')
    handleDialogVisible.value = false
    fetchList()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '处理失败')
  } finally {
    submitLoading.value = false
  }
}

function handleViewRecords(row) {
  currentRecordFilter.itemType = row.itemType
  currentRecordFilter.itemId = row.itemId
  recordPagination.page = 1
  recordDialogVisible.value = true
  fetchRecords()
}

async function fetchRecords() {
  recordLoading.value = true
  try {
    const params = {
      itemType: currentRecordFilter.itemType,
      itemId: currentRecordFilter.itemId,
      page: recordPagination.page,
      size: recordPagination.size
    }
    const res = await getInventoryRecords(params)
    const data = res.data || res
    recordList.value = data.list || []
    recordPagination.total = data.total || 0
  } catch (error) {
    ElMessage.error('获取出入库记录失败')
  } finally {
    recordLoading.value = false
  }
}
</script>

<style scoped>
.alert-container {
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
</style>
