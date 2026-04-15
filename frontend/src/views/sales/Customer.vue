<template>
  <div class="customer-manage-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="客户名称">
          <el-input
            v-model="searchForm.keyword"
            placeholder="请输入客户名称/电话"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="客户等级">
          <el-select v-model="searchForm.level" placeholder="全部" clearable style="width: 120px">
            <el-option label="VIP" value="VIP" />
            <el-option label="普通" value="NORMAL" />
            <el-option label="新客户" value="NEW" />
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
          <span>客户列表</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>新增客户
          </el-button>
        </div>
      </template>

      <el-table :data="customerList" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="name" label="客户名称" min-width="80" align="center" />
        <el-table-column prop="phone" label="联系电话" width="180" align="center"/>
        <el-table-column prop="email" label="邮箱" min-width="70" align="center"/>
        <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip align="center"/>
        <el-table-column prop="level" label="等级" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              :type="levelTagType(row.level)"
              size="small"
            >
              {{ levelText(row.level) }}
            </el-tag>
          </template>
        </el-table-column>
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
          @size-change="fetchCustomerList"
          @current-change="fetchCustomerList"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogType === 'add' ? '新增客户' : '编辑客户'"
      width="520px"
      destroy-on-close
      >
        <el-form
        ref="customerFormRef"
        :model="customerForm"
        :rules="customerFormRules"
        label-width="90px"
      >
        <el-form-item label="客户名称" prop="name">
          <el-input v-model="customerForm.name" placeholder="请输入客户名称" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="customerForm.phone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="customerForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="地址" prop="address">
          <el-input v-model="customerForm.address" placeholder="请输入地址" />
        </el-form-item>
        <el-form-item label="客户等级" prop="level">
          <el-select v-model="customerForm.level" placeholder="请选择等级" style="width: 100%">
            <el-option label="VIP" value="VIP" />
            <el-option label="普通" value="NORMAL" />
            <el-option label="新客户" value="NEW" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="customerForm.remark"
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
  getCustomerList,
  createCustomer,
  updateCustomer,
  deleteCustomer
} from '../../api/sales'

const loading = ref(false)
const submitLoading = ref(false)
const customerList = ref([])
const dialogVisible = ref(false)
const dialogType = ref('add')
const currentId = ref(null)
const customerFormRef = ref(null)

const searchForm = reactive({
  keyword: '',
  level: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

const customerForm = reactive({
  name: '',
  phone: '',
  email: '',
  address: '',
  level: 'NEW',
  remark: ''
})

const customerFormRules = {
  name: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
  level: [{ required: true, message: '请选择客户等级', trigger: 'change' }]
}

onMounted(() => {
  fetchCustomerList()
})

async function fetchCustomerList() {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.pageSize,
      keyword: searchForm.keyword || undefined,
      level: searchForm.level || undefined
    }
    const res = await getCustomerList(params)
    const data = res.data || res
    customerList.value = data.list || []
    pagination.total = data.total || 0
  } catch (error) {
    ElMessage.error('获取客户列表失败')
  } finally {
    loading.value = false
  }
}

function levelTagType(level) {
  switch (level) {
    case 'VIP': return 'warning'
    case 'NORMAL': return 'success'
    case 'NEW': return 'info'
    default: return 'info'
  }
}

function levelText(level) {
  switch (level) {
    case 'VIP': return 'VIP'
    case 'NORMAL': return '普通'
    case 'NEW': return '新客户'
    default: return level
  }
}

function handleSearch() {
  pagination.page = 1
  fetchCustomerList()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.level = ''
  pagination.page = 1
  fetchCustomerList()
}

function handleAdd() {
  dialogType.value = 'add'
  Object.assign(customerForm, {
    name: '',
    phone: '',
    email: '',
    address: '',
    level: 'NEW',
    remark: ''
  })
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogType.value = 'edit'
  currentId.value = row.id
  Object.assign(customerForm, {
    name: row.name,
    phone: row.phone || '',
    email: row.email || '',
    address: row.address || '',
    level: row.level || 'NEW',
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  const form = customerFormRef.value
  if (!form) return
  await form.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      const data = {
        name: customerForm.name,
        phone: customerForm.phone,
        email: customerForm.email,
        address: customerForm.address,
        level: customerForm.level,
        remark: customerForm.remark
      }
      if (dialogType.value === 'add') {
        await createCustomer(data)
        ElMessage.success('新增客户成功')
      } else {
        await updateCustomer(currentId.value, data)
        ElMessage.success('编辑客户成功')
      }
      dialogVisible.value = false
      fetchCustomerList()
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
      `确定删除客户"${row.name}"吗？此操作不可恢复。`,
      '警告',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await deleteCustomer(row.id)
    ElMessage.success('删除成功')
    fetchCustomerList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '删除失败')
    }
  }
}
</script>

<style scoped>
.customer-manage-container {
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
