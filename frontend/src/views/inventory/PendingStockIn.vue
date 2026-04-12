<template>
  <div class="pending-stock-in-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="批次号">
          <el-input
            v-model="searchForm.batchNo"
            placeholder="请输入批次号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input
            v-model="searchForm.productName"
            placeholder="请输入产品名称"
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
          <span>待入库列表</span>
        </div>
      </template>

      <el-table :data="planList" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="batchNo" label="批次号" min-width="160" />
        <el-table-column label="产品名称" min-width="150">
          <template #default="{ row }">
            {{ row.productName }}{{ row.productCode ? '-' + row.productCode : '' }}
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="计划数量" width="100" align="center" />
        <el-table-column prop="completedQuantity" label="完成数量" width="100" align="center">
          <template #default="{ row }">
            <span :style="{ color: row.completedQuantity >= row.quantity ? '#67C23A' : '#E6A23C' }">
              {{ row.completedQuantity }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="startDate" label="开始日期" width="120" align="center">
          <template #default="{ row }">
            {{ formatDate(row.startDate) }}
          </template>
        </el-table-column>
        <el-table-column prop="endDate" label="结束日期" width="120" align="center">
          <template #default="{ row }">
            {{ formatDate(row.endDate) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleStockIn(row)">确认入库</el-button>
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
      v-model="stockInDialogVisible"
      title="确认入库"
      width="520px"
      destroy-on-close
    >
      <el-form
        ref="stockInFormRef"
        :model="stockInForm"
        :rules="stockInFormRules"
        label-width="90px"
      >
        <el-form-item label="批次号">
          <el-input :model-value="currentPlan.batchNo" disabled />
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input :model-value="`${currentPlan.productName}${currentPlan.productCode ? '-' + currentPlan.productCode : ''}`" disabled />
        </el-form-item>
        <el-form-item label="计划数量">
          <el-input :model-value="`${currentPlan.quantity} ${currentPlan.unit}`" disabled />
        </el-form-item>
        <el-form-item label="完成数量">
          <el-input :model-value="`${currentPlan.completedQuantity} ${currentPlan.unit}`" disabled />
        </el-form-item>
        <el-divider content-position="left">入库信息</el-divider>
        <el-form-item label="入库数量" prop="quantity">
          <el-input-number
            v-model="stockInForm.quantity"
            :min="1"
            :max="currentPlan.completedQuantity"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="存放位置" prop="location">
          <el-input v-model="stockInForm.location" placeholder="请输入存放位置，如：A区-01-03" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="stockInForm.remark"
            type="textarea"
            :rows="2"
            placeholder="请输入备注信息（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stockInDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmitStockIn">确认入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getPlanList } from '../../api/production'
import { stockIn } from '../../api/inventory'

const loading = ref(false)
const submitLoading = ref(false)
const planList = ref([])
const stockInDialogVisible = ref(false)
const stockInFormRef = ref(null)

const currentPlan = reactive({
  id: '',
  batchNo: '',
  productName: '',
  productCode: '',
  quantity: 0,
  completedQuantity: 0,
  unit: ''
})

const searchForm = reactive({
  batchNo: '',
  productName: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const stockInForm = reactive({
  quantity: 0,
  location: '',
  remark: ''
})

const stockInFormRules = {
  quantity: [
    { required: true, message: '请输入入库数量', trigger: 'blur' }
  ],
  location: [
    { required: true, message: '请输入存放位置', trigger: 'blur' }
  ]
}

onMounted(() => {
  fetchList()
})

async function fetchList() {
  loading.value = true
  try {
    const params = {
      keyword: searchForm.batchNo || searchForm.productName,
      status: 'COMPLETED',
      page: pagination.page,
      size: pagination.size
    }
    const res = await getPlanList(params)
    const data = res.data || res
    planList.value = data.list || []
    pagination.total = data.total || 0
  } catch (error) {
    ElMessage.error('获取待入库列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchList()
}

function handleReset() {
  searchForm.batchNo = ''
  searchForm.productName = ''
  pagination.page = 1
  fetchList()
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function handleStockIn(row) {
  currentPlan.id = row.id
  currentPlan.batchNo = row.batchNo
  currentPlan.productName = row.productName
  currentPlan.productCode = row.productCode
  currentPlan.quantity = row.quantity
  currentPlan.completedQuantity = row.completedQuantity
  currentPlan.unit = row.unit

  stockInForm.quantity = row.completedQuantity
  stockInForm.location = ''
  stockInForm.remark = ''

  stockInDialogVisible.value = true
}

async function handleSubmitStockIn() {
  const form = stockInFormRef.value
  if (!form) return

  await form.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      const data = {
        itemType: 'FINISHED_PRODUCT',
        itemId: currentPlan.id,
        quantity: stockInForm.quantity,
        reason: `生产批次${currentPlan.batchNo}入库 | 位置:${stockInForm.location} | ${stockInForm.remark}`
      }

      await stockIn(data)

      ElMessage.success(`批次 [${currentPlan.batchNo}] 入库成功，数量：${stockInForm.quantity} ${currentPlan.unit}`)
      stockInDialogVisible.value = false
      fetchList()
    } catch (error) {
      ElMessage.error(error.response?.data?.message || '入库失败')
    } finally {
      submitLoading.value = false
    }
  })
}
</script>

<style scoped>
.pending-stock-in-container {
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
