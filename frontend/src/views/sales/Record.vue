<template>
  <div class="sales-record-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="客户名称">
          <el-input
            v-model="searchForm.keyword"
            placeholder="请输入客户/产品名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="销售日期">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 260px"
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
          <span>销售记录列表</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>新增销售记录
          </el-button>
        </div>
      </template>

      <el-table :data="recordList" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="customerName" label="客户名称" min-width="120" />
        <el-table-column prop="productName" label="产品名称" min-width="140" />
        <el-table-column prop="quantity" label="数量" width="80" align="center" />
        <el-table-column prop="unitPrice" label="单价" width="100" align="right">
          <template #default="{ row }">
            {{ row.unitPrice?.toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="120" align="right">
          <template #default="{ row }">
            <span style="color: #e6a23c; font-weight: 600">{{ row.amount?.toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="saleDate" label="销售日期" min-width="110" align="center">
          <template #default="{ row }">
            {{ formatDate(row.saleDate) }}
          </template>
        </el-table-column>
        <el-table-column prop="createByName" label="创建人" width="100" align="center" />
        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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
          @size-change="fetchRecordList"
          @current-change="fetchRecordList"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogType === 'add' ? '新增销售记录' : '编辑销售记录'"
      width="560px"
      destroy-on-close
    >
      <el-form
        ref="recordFormRef"
        :model="recordForm"
        :rules="recordFormRules"
        label-width="90px"
      >
        <el-form-item label="客户" prop="customerId">
          <el-select
            v-model="recordForm.customerId"
            placeholder="请选择客户"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="customer in customerOptions"
              :key="customer.id"
              :label="customer.name"
              :value="customer.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="产品名称" prop="productName">
          <el-input v-model="recordForm.productName" placeholder="请输入产品名称" />
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number
            v-model="recordForm.quantity"
            :min="1"
            :max="99999"
            style="width: 100%"
            @change="calcAmount"
          />
        </el-form-item>
        <el-form-item label="单价" prop="unitPrice">
          <el-input-number
            v-model="recordForm.unitPrice"
            :min="0"
            :precision="2"
            :step="10"
            style="width: 100%"
            @change="calcAmount"
          />
        </el-form-item>
        <el-form-item label="金额">
          <el-input
            :model-value="recordForm.amount?.toFixed(2)"
            disabled
            placeholder="自动计算"
          />
        </el-form-item>
        <el-form-item label="销售日期" prop="saleDate">
          <el-date-picker
            v-model="recordForm.saleDate"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="recordForm.remark"
            type="textarea"
            :rows="2"
            placeholder="请输入备注"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getSalesRecordList,
  createSalesRecord,
  updateSalesRecord,
  deleteSalesRecord,
  getCustomerList
} from '../../api/sales'

const loading = ref(false)
const submitLoading = ref(false)
const recordList = ref([])
const customerOptions = ref([])
const dialogVisible = ref(false)
const dialogType = ref('add')
const currentId = ref(null)
const recordFormRef = ref(null)

const searchForm = reactive({
  keyword: '',
  dateRange: null
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

const recordForm = reactive({
  customerId: '',
  productId: '',
  productName: '',
  quantity: 1,
  unitPrice: 0,
  amount: 0,
  saleDate: '',
  remark: ''
})

const recordFormRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  productName: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  unitPrice: [{ required: true, message: '请输入单价', trigger: 'blur' }],
  saleDate: [{ required: true, message: '请选择销售日期', trigger: 'change' }]
}

onMounted(() => {
  fetchRecordList()
  fetchCustomerOptions()
})

async function fetchRecordList() {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.pageSize,
      keyword: searchForm.keyword || undefined
    }
    if (searchForm.dateRange && searchForm.dateRange.length === 2) {
      params.startDate = searchForm.dateRange[0]
      params.endDate = searchForm.dateRange[1]
    }
    const res = await getSalesRecordList(params)
    const data = res.data || res
    recordList.value = data.list || []
    pagination.total = data.total || 0
  } catch (error) {
    ElMessage.error('获取销售记录失败')
  } finally {
    loading.value = false
  }
}

async function fetchCustomerOptions() {
  try {
    const res = await getCustomerList({ page: 1, size: 1000 })
    const data = res.data || res
    customerOptions.value = data.list || []
  } catch (error) {
    console.error('获取客户列表失败')
  }
}

function calcAmount() {
  recordForm.amount = (recordForm.quantity || 0) * (recordForm.unitPrice || 0)
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function handleSearch() {
  pagination.page = 1
  fetchRecordList()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.dateRange = null
  pagination.page = 1
  fetchRecordList()
}

function handleAdd() {
  dialogType.value = 'add'
  Object.assign(recordForm, {
    customerId: '',
    productId: '',
    productName: '',
    quantity: 1,
    unitPrice: 0,
    amount: 0,
    saleDate: '',
    remark: ''
  })
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogType.value = 'edit'
  currentId.value = row.id
  Object.assign(recordForm, {
    customerId: row.customerId,
    productId: row.productId,
    productName: row.productName,
    quantity: row.quantity,
    unitPrice: row.unitPrice,
    amount: row.amount,
    saleDate: formatDate(row.saleDate),
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  const form = recordFormRef.value
  if (!form) return
  await form.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      const data = {
        customerId: recordForm.customerId,
        productId: recordForm.productId,
        productName: recordForm.productName,
        quantity: recordForm.quantity,
        unitPrice: recordForm.unitPrice,
        saleDate: recordForm.saleDate,
        remark: recordForm.remark
      }
      if (dialogType.value === 'add') {
        await createSalesRecord(data)
        ElMessage.success('新增销售记录成功')
      } else {
        await updateSalesRecord(currentId.value, data)
        ElMessage.success('编辑销售记录成功')
      }
      dialogVisible.value = false
      fetchRecordList()
    } catch (error) {
      ElMessage.error(error.response?.data?.message || '操作失败')
    } finally {
      submitLoading.value = false
    }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除该条销售记录吗？此操作不可恢复。`,
      '警告',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await deleteSalesRecord(row.id)
    ElMessage.success('删除成功')
    fetchRecordList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '删除失败')
    }
  }
}
</script>

<style scoped>
.sales-record-container {
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
