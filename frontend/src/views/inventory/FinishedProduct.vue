<template>
  <div class="finished-product-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="名称">
          <el-input
            v-model="searchForm.name"
            placeholder="请输入成品名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="searchForm.category" placeholder="全部" clearable style="width: 140px">
            <el-option label="上衣" value="上衣" />
            <el-option label="裤子" value="裤子" />
            <el-option label="裙子" value="裙子" />
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
          <span>成品列表</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>新增成品
          </el-button>
        </div>
      </template>

      <el-table :data="productList" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="name" label="名称" min-width="100" />
        <el-table-column prop="category" label="类别" width="80" />
        <el-table-column prop="specification" label="规格" min-width="100" />
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="quantity" label="库存数量" width="90" align="center">
          <template #default="{ row }">
            <span :style="{ color: row.quantity <= row.alertThreshold ? '#F56C6C' : '' }">
              {{ row.quantity }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="alertThreshold" label="预警阈值" width="90" align="center" />
        <el-table-column prop="location" label="存放位置" min-width="100" />
        <el-table-column prop="price" label="销售单价" width="90" align="right">
          <template #default="{ row }">
            {{ row.price != null ? row.price.toFixed(2) : '' }}
          </template>
        </el-table-column>
        <el-table-column prop="costPrice" label="成本价" width="80" align="right">
          <template #default="{ row }">
            {{ row.costPrice != null ? row.costPrice.toFixed(2) : '' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="handleStockIn(row)">入库</el-button>
            <el-button type="warning" link size="small" @click="handleStockOut(row)">出库</el-button>
            <el-button type="info" link size="small" @click="handleSetThreshold(row)">阈值</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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
      v-model="addDialogVisible"
      :title="dialogType === 'add' ? '新增成品' : '编辑成品'"
      width="560px"
      destroy-on-close
    >
      <el-form
        ref="productFormRef"
        :model="productForm"
        :rules="productFormRules"
        label-width="80px"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="productForm.name" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="类别" prop="category">
          <el-select v-model="productForm.category" placeholder="请选择类别" style="width: 100%">
            <el-option label="上衣" value="上衣" />
            <el-option label="裤子" value="裤子" />
            <el-option label="裙子" value="裙子" />
          </el-select>
        </el-form-item>
        <el-form-item label="规格" prop="specification">
          <el-input v-model="productForm.specification" placeholder="请输入规格(尺码/颜色)" />
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="productForm.unit" placeholder="请输入单位" />
        </el-form-item>
        <el-form-item v-if="dialogType === 'add'" label="库存数量" prop="quantity">
          <el-input-number v-model="productForm.quantity" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="dialogType === 'add'" label="预警阈值" prop="alertThreshold">
          <el-input-number v-model="productForm.alertThreshold" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="存放位置" prop="location">
          <el-input v-model="productForm.location" placeholder="请输入存放位置" />
        </el-form-item>
        <el-form-item label="销售单价" prop="price">
          <el-input-number v-model="productForm.price" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="成本价" prop="costPrice">
          <el-input-number v-model="productForm.costPrice" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="productForm.description" type="textarea" :rows="2" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="stockDialogVisible"
      :title="stockType === 'IN' ? '入库操作' : '出库操作'"
      width="440px"
      destroy-on-close
    >
      <el-form
        ref="stockFormRef"
        :model="stockForm"
        :rules="stockFormRules"
        label-width="80px"
      >
        <el-form-item label="物品">
          <el-input :model-value="currentItem.name" disabled />
        </el-form-item>
        <el-form-item label="当前库存">
          <el-input :model-value="String(currentItem.quantity)" disabled />
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="stockForm.quantity" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="原因" prop="reason">
          <el-input v-model="stockForm.reason" type="textarea" :rows="2" placeholder="请输入原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stockDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleStockSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="thresholdDialogVisible"
      title="设置预警阈值"
      width="400px"
      destroy-on-close
    >
      <el-form
        ref="thresholdFormRef"
        :model="thresholdForm"
        :rules="thresholdFormRules"
        label-width="80px"
      >
        <el-form-item label="物品">
          <el-input :model-value="currentItem.name" disabled />
        </el-form-item>
        <el-form-item label="预警阈值" prop="alertThreshold">
          <el-input-number v-model="thresholdForm.alertThreshold" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="thresholdDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleThresholdSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getFinishedProductList,
  createFinishedProduct,
  updateFinishedProduct,
  deleteFinishedProduct,
  setFinishedProductThreshold,
  stockIn,
  stockOut
} from '../../api/inventory'

const loading = ref(false)
const submitLoading = ref(false)
const productList = ref([])
const addDialogVisible = ref(false)
const stockDialogVisible = ref(false)
const thresholdDialogVisible = ref(false)
const dialogType = ref('add')
const stockType = ref('IN')
const currentItemId = ref(null)
const productFormRef = ref(null)
const stockFormRef = ref(null)
const thresholdFormRef = ref(null)

const currentItem = reactive({
  name: '',
  quantity: 0
})

const searchForm = reactive({
  name: '',
  category: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const productForm = reactive({
  name: '',
  category: '',
  specification: '',
  unit: '',
  quantity: 0,
  alertThreshold: 0,
  location: '',
  price: 0,
  costPrice: 0,
  description: ''
})

const stockForm = reactive({
  quantity: 1,
  reason: ''
})

const thresholdForm = reactive({
  alertThreshold: 0
})

const productFormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

const stockFormRules = {
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  reason: [{ required: true, message: '请输入原因', trigger: 'blur' }]
}

const thresholdFormRules = {
  alertThreshold: [{ required: true, message: '请输入预警阈值', trigger: 'blur' }]
}

onMounted(() => {
  fetchList()
})

async function fetchList() {
  loading.value = true
  try {
    const params = {
      name: searchForm.name,
      category: searchForm.category,
      page: pagination.page,
      size: pagination.size
    }
    const res = await getFinishedProductList(params)
    const data = res.data || res
    productList.value = data.list || []
    pagination.total = data.total || 0
  } catch (error) {
    ElMessage.error('获取成品列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchList()
}

function handleReset() {
  searchForm.name = ''
  searchForm.category = ''
  pagination.page = 1
  fetchList()
}

function handleAdd() {
  dialogType.value = 'add'
  Object.assign(productForm, {
    name: '',
    category: '',
    specification: '',
    unit: '',
    quantity: 0,
    alertThreshold: 0,
    location: '',
    price: 0,
    costPrice: 0,
    description: ''
  })
  addDialogVisible.value = true
}

function handleEdit(row) {
  dialogType.value = 'edit'
  currentItemId.value = row.id
  Object.assign(productForm, {
    name: row.name,
    category: row.category,
    specification: row.specification,
    unit: row.unit,
    location: row.location,
    price: row.price,
    costPrice: row.costPrice,
    description: row.description
  })
  addDialogVisible.value = true
}

async function handleSubmit() {
  const form = productFormRef.value
  if (!form) return
  await form.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (dialogType.value === 'add') {
        await createFinishedProduct(productForm)
        ElMessage.success('新增成品成功')
      } else {
        await updateFinishedProduct(currentItemId.value, productForm)
        ElMessage.success('编辑成品成功')
      }
      addDialogVisible.value = false
      fetchList()
    } catch (error) {
      ElMessage.error(error.response?.data?.message || '操作失败')
    } finally {
      submitLoading.value = false
    }
  })
}

function handleStockIn(row) {
  stockType.value = 'IN'
  currentItemId.value = row.id
  currentItem.name = row.name
  currentItem.quantity = row.quantity
  stockForm.quantity = 1
  stockForm.reason = ''
  stockDialogVisible.value = true
}

function handleStockOut(row) {
  stockType.value = 'OUT'
  currentItemId.value = row.id
  currentItem.name = row.name
  currentItem.quantity = row.quantity
  stockForm.quantity = 1
  stockForm.reason = ''
  stockDialogVisible.value = true
}

async function handleStockSubmit() {
  const form = stockFormRef.value
  if (!form) return
  await form.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      const data = {
        itemType: 'FINISHED_PRODUCT',
        itemId: currentItemId.value,
        quantity: stockForm.quantity,
        reason: stockForm.reason
      }
      if (stockType.value === 'IN') {
        await stockIn(data)
        ElMessage.success('入库成功')
      } else {
        await stockOut(data)
        ElMessage.success('出库成功')
      }
      stockDialogVisible.value = false
      fetchList()
    } catch (error) {
      ElMessage.error(error.response?.data?.message || '操作失败')
    } finally {
      submitLoading.value = false
    }
  })
}

function handleSetThreshold(row) {
  currentItemId.value = row.id
  currentItem.name = row.name
  thresholdForm.alertThreshold = row.alertThreshold || 0
  thresholdDialogVisible.value = true
}

async function handleThresholdSubmit() {
  const form = thresholdFormRef.value
  if (!form) return
  await form.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      await setFinishedProductThreshold(currentItemId.value, { alertThreshold: thresholdForm.alertThreshold })
      ElMessage.success('设置预警阈值成功')
      thresholdDialogVisible.value = false
      fetchList()
    } catch (error) {
      ElMessage.error(error.response?.data?.message || '操作失败')
    } finally {
      submitLoading.value = false
    }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除成品"${row.name}"吗？此操作不可恢复。`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteFinishedProduct(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '删除失败')
    }
  }
}
</script>

<style scoped>
.finished-product-container {
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
