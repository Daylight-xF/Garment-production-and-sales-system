<template>
  <div class="user-manage-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="用户名">
          <el-input
            v-model="searchForm.username"
            placeholder="请输入用户名"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="searchForm.role" placeholder="全部" clearable style="width: 140px">
            <el-option label="未激活" value="inactive" />
            <el-option label="管理员" value="admin" />
            <el-option label="生产管理" value="production_manager" />
            <el-option label="仓库操作" value="warehouse_staff" />
            <el-option label="销售" value="sales_staff" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
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
          <span>用户列表</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>新增用户
          </el-button>
        </div>
      </template>

      <el-table :data="userList" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="username" label="用户名" min-width="100" align="center"/>
        <el-table-column prop="realName" label="真实姓名" min-width="100" align="center"/>
        <el-table-column prop="phone" label="手机号" min-width="120" align="center"/>
        <el-table-column prop="email" label="邮箱" min-width="160" align="center"/>
        <el-table-column prop="roles" label="角色" min-width="120" align="center">
          <template #default="{ row }">
            <el-tag
              v-for="role in row.roles"
              :key="role"
              size="small"
              style="margin-right: 4px"
            >
              {{ roleNameMap[role] || role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="160" align="center">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="warning" link size="small" @click="handleAssignRole(row)">分配角色</el-button>
            <el-button
              :type="row.status === 1 ? 'danger' : 'success'"
              link
              size="small"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
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
          @size-change="fetchUserList"
          @current-change="fetchUserList"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogType === 'add' ? '新增用户' : '编辑用户'"
      width="500px"
      destroy-on-close
    >
      <el-form
        ref="userFormRef"
        :model="userForm"
        :rules="userFormRules"
        label-width="80px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="userForm.username"
            placeholder="请输入用户名"
            :disabled="dialogType === 'edit'"
          />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="userForm.realName" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item v-if="dialogType === 'add'" label="密码" prop="password">
          <el-input
            v-model="userForm.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>
        <el-form-item v-if="dialogType === 'edit'" label="新密码" prop="newPassword">
          <el-input
            v-model="userForm.newPassword"
            :type="showNewPassword ? 'text' : 'password'"
            placeholder="留空则不修改密码"
            :suffix-icon="showNewPassword ? View : Hide"
            @click:icon="showNewPassword = !showNewPassword"
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
          <div class="password-hint">如需修改密码请填写，留空保持原密码不变</div>
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="userForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" placeholder="请输入邮箱" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="roleDialogVisible"
      title="分配角色"
      width="400px"
      destroy-on-close
    >
      <el-radio-group v-model="selectedRoleId">
        <el-radio
          v-for="role in roleList"
          :key="role.id"
          :value="role.id"
          style="margin-bottom: 12px; margin-right: 0; width: 100%"
        >
          {{ role.name }}
        </el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleRoleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock, View, Hide, Plus } from '@element-plus/icons-vue'
import { getErrorMessage } from '../../utils/errorMessage'
import {
  getUserList,
  createUser,
  updateUser,
  deleteUser,
  assignRoles,
  updateUserStatus,
  getRoleList
} from '../../api/user'

const loading = ref(false)
const submitLoading = ref(false)
const userList = ref([])
const roleList = ref([])
const dialogVisible = ref(false)
const roleDialogVisible = ref(false)
const dialogType = ref('add')
const currentUserId = ref(null)
const selectedRoleId = ref('')
const userFormRef = ref(null)
const showNewPassword = ref(false)

const roleNameMap = {
  admin: '系统管理员',
  production_manager: '生产管理人员',
  warehouse_staff: '仓库操作人员',
  sales_staff: '销售人员',
  inactive: '未激活'
}

const searchForm = reactive({
  username: '',
  role: '',
  status: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

const userForm = reactive({
  username: '',
  realName: '',
  password: '',
  newPassword: '',
  phone: '',
  email: ''
})

const validateNewPassword = (rule, value, callback) => {
  if (value && value.length > 0) {
    if (value.length < 6 || value.length > 20) {
      callback(new Error('密码长度在6到20个字符'))
    } else {
      callback()
    }
  } else {
    callback()
  }
}

const userFormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  newPassword: [{ validator: validateNewPassword, trigger: 'blur' }],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ]
}

function formatTime(time) {
  if (!time) return '-'
  const date = new Date(time)
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const min = String(date.getMinutes()).padStart(2, '0')
  const s = String(date.getSeconds()).padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${min}:${s}`
}

onMounted(() => {
  fetchUserList()
  fetchRoleList()
})

async function fetchUserList() {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      pageSize: pagination.pageSize,
      keyword: searchForm.username
    }
    if (searchForm.role) {
      params.role = searchForm.role
    }
    if (searchForm.status !== '') {
      params.status = searchForm.status
    }
    const res = await getUserList(params)
    const data = res.data || res
    userList.value = data.list || data.records || []
    pagination.total = data.total || 0
  } catch (error) {
    ElMessage.error(error.message || '获取用户列表失败')
  } finally {
    loading.value = false
  }
}

async function fetchRoleList() {
  try {
    const res = await getRoleList()
    const data = res.data || res
    roleList.value = data.list || data || []
  } catch (error) {
    ElMessage.error('获取角色列表失败')
  }
}

function handleSearch() {
  pagination.page = 1
  fetchUserList()
}

function handleReset() {
  searchForm.username = ''
  searchForm.role = ''
  searchForm.status = ''
  pagination.page = 1
  fetchUserList()
}

function handleAdd() {
  dialogType.value = 'add'
  showNewPassword.value = false
  Object.assign(userForm, {
    username: '',
    realName: '',
    password: '',
    newPassword: '',
    phone: '',
    email: ''
  })
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogType.value = 'edit'
  currentUserId.value = row.id
  showNewPassword.value = false
  Object.assign(userForm, {
    username: row.username,
    realName: row.realName,
    password: '',
    newPassword: '',
    phone: row.phone,
    email: row.email
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  const form = userFormRef.value
  if (!form) return
  await form.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (dialogType.value === 'add') {
        await createUser(userForm)
        ElMessage.success('新增用户成功')
      } else {
        const { password, newPassword, ...updateData } = userForm
        if (newPassword && newPassword.length > 0) {
          updateData.password = newPassword
        }
        await updateUser(currentUserId.value, updateData)
        ElMessage.success('编辑用户成功')
      }
      dialogVisible.value = false
      fetchUserList()
    } catch (error) {
      ElMessage.error(getErrorMessage(error, '操作失败'))
    } finally {
      submitLoading.value = false
    }
  })
}

function handleAssignRole(row) {
  currentUserId.value = row.id
  selectedRoleId.value = row.roles && row.roles.length > 0 ? row.roles[0] : ''
  roleDialogVisible.value = true
}

async function handleRoleSubmit() {
  if (!selectedRoleId.value) {
    ElMessage.warning('请选择一个角色')
    return
  }
  submitLoading.value = true
  try {
    await assignRoles(currentUserId.value, { roleIds: [selectedRoleId.value] })
    ElMessage.success('分配角色成功')
    roleDialogVisible.value = false
    fetchUserList()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '分配角色失败'))
  } finally {
    submitLoading.value = false
  }
}

async function handleToggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  const actionText = newStatus === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定${actionText}用户"${row.username}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await updateUserStatus(row.id, { status: newStatus })
    ElMessage.success(`${actionText}成功`)
    fetchUserList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(getErrorMessage(error, '操作失败'))
    }
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除用户"${row.username}"吗？此操作不可恢复。`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    fetchUserList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(getErrorMessage(error, '删除失败'))
    }
  }
}
</script>

<style scoped>
.user-manage-container {
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

.password-hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 4px;
}
</style>
