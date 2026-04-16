# 个人信息功能实现计划

> **For agentic workers:** REQUIRED SUBSKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为服装生产销售系统开发完整的个人信息管理功能，包括基本信息编辑、密码修改和角色权限查看。

**Architecture:** 采用纯前端实现方案，创建独立的 Profile.vue 页面组件，使用 Element Plus Tabs 组件实现三标签页布局，复用现有的后端 API 接口（getCurrentUser 和 updateUser），通过 Pinia store 管理用户状态，确保当前登录用户只能修改自己的信息。

**Tech Stack:** Vue 3 (Composition API) + Element Plus + Pinia + Vue Router 4 + Axios

---

## 文件结构总览

```
frontend/src/
├── views/
│   └── system/
│       └── Profile.vue              ← 新建：个人信息页面主组件（3个Tab）
├── router/
│   └── index.js                     ← 修改：添加 /profile 路由配置
└── layout/
    └── MainLayout.vue               ← 修改：个人信息按钮跳转到 /profile
```

**涉及文件说明：**
- **Profile.vue（新建）：** 核心页面组件，包含基本信息、修改密码、角色权限三个Tab页的所有逻辑和UI
- **router/index.js（修改）：** 在 MainLayout 的 children 中添加 profile 路由
- **MainLayout.vue（修改）：** 将"个人信息"点击事件从提示信息改为路由跳转

---

## Task 1: 创建 Profile.vue 基础结构和路由集成

**Files:**
- Create: `frontend/src/views/system/Profile.vue`
- Modify: `frontend/src/router/index.js:125` (在 system/product-definition 路由之后添加)
- Modify: `frontend/src/layout/MainLayout.vue:207-209` (修改 handleCommand 函数)

### Step 1: 创建 Profile.vue 基础框架

```vue
<template>
  <div class="profile-container">
    <el-card class="profile-card">
      <template #header>
        <div class="card-header">
          <span class="title">个人信息</span>
        </div>
      </template>

      <el-tabs v-model="activeTab" type="border-card">
        <!-- Tab1: 基本信息 -->
        <el-tab-pane label="基本信息" name="basic">
          <BasicInfo />
        </el-tab-pane>

        <!-- Tab2: 修改密码 -->
        <el-tab-pane label="修改密码" name="password">
          <ChangePassword />
        </el-tab-pane>

        <!-- Tab3: 角色权限 -->
        <el-tab-pane label="角色权限" name="roles">
          <RolePermissions />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useUserStore } from '../../store/user'
import BasicInfo from './components/BasicInfo.vue'
import ChangePassword from './components/ChangePassword.vue'
import RolePermissions from './components/RolePermissions.vue'

const userStore = useUserStore()
const activeTab = ref('basic')

onMounted(async () => {
  try {
    await userStore.getUserInfoAction()
  } catch (error) {
    console.error('获取用户信息失败:', error)
  }
})
</script>

<style scoped>
.profile-container {
  padding: 20px;
}

.profile-card {
  max-width: 1000px;
  margin: 0 auto;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-header .title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

:deep(.el-tabs__content) {
  padding: 20px;
}
</style>
```

**说明：** 这是主容器组件，负责整体布局和用户数据加载。实际功能将在子组件中实现。

---

### Step 2: 添加路由配置

在 `frontend/src/router/index.js` 文件中，找到第125行（system/product-definition 路由之后），添加以下代码：

```javascript
{
  path: 'profile',
  name: 'Profile',
  component: () => import('../views/system/Profile.vue'),
  meta: {
    title: '个人信息',
    requiresAuth: true
  }
}
```

**插入位置：** 在 `system/product-definition` 路由配置块（第120-125行）之后，children 数组的闭合括号之前。

---

### Step 3: 修改 MainLayout 跳转逻辑

在 `frontend/src/layout/MainLayout.vue` 文件中，找到第207-209行的 `handleCommand` 函数，将：

```javascript
} else if (command === 'profile') {
  ElMessage.info('个人信息功能开发中')
}
```

修改为：

```javascript
} else if (command === 'profile') {
  router.push('/profile')
}
```

**验证：** 点击右上角"个人信息"应跳转到 `/profile` 路由。

---

## Task 2: 实现基本信息 Tab 页（BasicInfo）

**Files:**
- Create: `frontend/src/views/system/components/BasicInfo.vue`

### Step 1: 创建 BasicInfo.vue 组件基础结构

```vue
<template>
  <div class="basic-info">
    <el-row :gutter="40">
      <!-- 左侧：头像区域 -->
      <el-col :xs="24" :sm="8" :md="6">
        <div class="avatar-section">
          <el-avatar :size="120" :icon="UserFilled" class="user-avatar" />
          <h3 class="username">{{ userStore.username }}</h3>
        </div>
      </el-col>

      <!-- 右侧：表单区域 -->
      <el-col :xs="24" :sm="16" :md="18">
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="100px"
          class="info-form"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              disabled
              placeholder="系统自动生成"
            />
          </el-form-item>

          <el-form-item label="真实姓名" prop="realName">
            <el-input
              v-model="form.realName"
              placeholder="请输入真实姓名"
              clearable
            />
          </el-form-item>

          <el-form-item label="手机号" prop="phone">
            <el-input
              v-model="form.phone"
              placeholder="请输入手机号"
              clearable
              maxlength="11"
            />
          </el-form-item>

          <el-form-item label="邮箱" prop="email">
            <el-input
              v-model="form.email"
              placeholder="请输入邮箱地址"
              clearable
            />
          </el-form-item>

          <el-form-item label="创建时间">
            <el-input
              :value="formatDate(userStore.userInfo.createTime)"
              disabled
            />
          </el-form-item>

          <el-form-item label="更新时间">
            <el-input
              :value="formatDate(userStore.userInfo.updateTime)"
              disabled
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" @click="handleSave" :loading="loading">
              保存修改
            </el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UserFilled } from '@element-plus/icons-vue'
import { useUserStore } from '../../../store/user'
import { updateUser } from '../../../api/user'

const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  realName: '',
  phone: '',
  email: ''
})

// 备份原始数据用于重置
const originalData = reactive({ ...form })

const rules = {
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
    { min: 2, max: 20, message: '长度在2到20个字符', trigger: 'blur' }
  ],
  phone: [
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '请输入正确的手机号码',
      trigger: 'blur'
    }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ]
}

function formatDate(date) {
  if (!date) return '-'
  const d = new Date(date)
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

function initFormData() {
  const info = userStore.userInfo
  form.username = info.username || ''
  form.realName = info.realName || ''
  form.phone = info.phone || ''
  form.email = info.email || ''

  // 备份原始数据
  Object.assign(originalData, { ...form })
}

watch(() => userStore.userInfo, () => {
  initFormData()
}, { deep: true })

onMounted(() => {
  initFormData()
})

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  try {
    loading.value = true
    const userId = userStore.userInfo.id
    await updateUser(userId, {
      realName: form.realName,
      phone: form.phone,
      email: form.email
    })

    ElMessage.success('个人信息保存成功')
    await userStore.getUserInfoAction()
  } catch (error) {
    const msg = error.response?.data?.message || '保存失败'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

function handleReset() {
  ElMessageBox.confirm('确定要重置吗？未保存的修改将丢失', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    Object.assign(form, { ...originalData })
    ElMessage.info('表单已重置')
  }).catch(() => {})
}
</script>

<style scoped>
.basic-info {
  padding: 20px 0;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 8px;
}

.user-avatar {
  background-color: #409eff;
  margin-bottom: 15px;
  font-size: 48px;
}

.avatar-section .username {
  margin: 0;
  font-size: 16px;
  color: #303133;
  font-weight: 500;
}

.info-form {
  padding: 20px;
}

:deep(.el-input.is-disabled .el-input__inner) {
  background-color: #f5f7fa;
  color: #909399;
}
</style>
```

**关键特性：**
- 左右分栏布局（响应式：小屏幕上下堆叠）
- 表单验证规则（真实姓名必填、手机号格式、邮箱格式）
- 保存时调用 updateUser API 并刷新 store
- 重置功能带确认弹窗
- 时间格式化显示

---

## Task 3: 实现修改密码 Tab 页（ChangePassword）

**Files:**
- Create: `frontend/src/views/system/components/ChangePassword.vue`

### Step 1: 创建 ChangePassword.vue 组件

```vue
<template>
  <div class="change-password">
    <el-alert
      title="密码修改成功后需要重新登录"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 24px;"
    />

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      class="password-form"
      style="max-width: 500px;"
    >
      <el-form-item label="旧密码" prop="oldPassword">
        <el-input
          v-model="form.oldPassword"
          type="password"
          placeholder="请输入旧密码"
          show-password
        />
      </el-form-item>

      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          type="password"
          placeholder="请输入新密码"
          show-password
          @input="calculateStrength"
        />
        <div v-if="form.newPassword" class="strength-indicator">
          <div class="strength-bar">
            <div
              class="strength-fill"
              :class="strengthLevel"
              :style="{ width: strengthPercent + '%' }"
            ></div>
          </div>
          <span class="strength-text" :class="strengthLevel">
            {{ strengthText }}
          </span>
        </div>
        <div class="password-hint">6-20位，需包含字母和数字</div>
      </el-form-item>

      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          placeholder="请确认新密码"
          show-password
        />
      </el-form-item>

      <el-form-item>
        <el-button
          type="primary"
          @click="handleChangePassword"
          :loading="loading"
        >
          修改密码
        </el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../../store/user'
import { updateUser } from '../../../api/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== form.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const validateNewPassword = (rule, value, callback) => {
  if (value && value.length >= 6) {
    const hasLetter = /[a-zA-Z]/.test(value)
    const hasNumber = /\d/.test(value)
    if (!hasLetter || !hasNumber) {
      callback(new Error('密码必须包含字母和数字'))
      return
    }
  }
  callback()
}

const rules = {
  oldPassword: [
    { required: true, message: '请输入旧密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度在6到20个字符', trigger: 'blur' },
    { validator: validateNewPassword, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

// 密码强度计算
const strengthLevel = ref('')
const strengthPercent = computed(() => {
  const pwd = form.newPassword
  if (!pwd) return 0

  let score = 0
  if (pwd.length >= 8) score++
  if (/[a-z]/.test(pwd)) score++
  if (/[A-Z]/.test(pwd)) score++
  if (/\d/.test(pwd)) score++
  if (/[^a-zA-Z0-9]/.test(pwd)) score++

  return Math.min(score * 20, 100)
})

const strengthText = computed(() => {
  const level = strengthLevel.value
  if (level === 'weak') return '弱'
  if (level === 'medium') return '中'
  if (level === 'strong') return '强'
  return ''
})

function calculateStrength() {
  const pwd = form.newPassword
  if (!pwd) {
    strengthLevel.value = ''
    return
  }

  let score = 0
  if (pwd.length >= 8) score++
  if (/[a-z]/.test(pwd)) score++
  if (/[A-Z]/.test(pwd)) score++
  if (/\d/.test(pwd)) score++
  if (/[^a-zA-Z0-9]/.test(pwd)) score++

  if (score <= 2) strengthLevel.value = 'weak'
  else if (score <= 3) strengthLevel.value = 'medium'
  else strengthLevel.value = 'strong'
}

async function handleChangePassword() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  try {
    await ElMessageBox.confirm(
      '确定要修改密码吗？修改后需要重新登录',
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  try {
    loading.value = true
    const userId = userStore.userInfo.id
    await updateUser(userId, {
      password: form.newPassword
    })

    ElMessage.success('密码修改成功，即将跳转到登录页')

    setTimeout(() => {
      userStore.logout()
      router.push('/login')
    }, 2000)
  } catch (error) {
    const msg = error.response?.data?.message || '密码修改失败'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

function handleReset() {
  form.oldPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  strengthLevel.value = ''
  ElMessage.info('表单已重置')
}
</script>

<style scoped>
.change-password {
  padding: 20px 0;
}

.password-form {
  padding: 20px;
  background: #fafafa;
  border-radius: 8px;
}

.strength-indicator {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}

.strength-bar {
  flex: 1;
  height: 6px;
  background: #e4e7ed;
  border-radius: 3px;
  overflow: hidden;
}

.strength-fill {
  height: 100%;
  transition: all 0.3s ease;
  border-radius: 3px;
}

.strength-fill.weak {
  background: #f56c6c;
}

.strength-fill.medium {
  background: #e6a23c;
}

.strength-fill.strong {
  background: #67c23a;
}

.strength-text {
  font-size: 12px;
  font-weight: 500;
  min-width: 20px;
}

.strength-text.weak {
  color: #f56c6c;
}

.strength-text.medium {
  color: #e6a23c;
}

.strength-text.strong {
  color: #67c23a;
}

.password-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
```

**关键特性：**
- 安全警告提示（顶部 Alert）
- 密码可见性切换（show-password 属性）
- 实时密码强度指示器（弱/中/强 三级）
- 二次确认弹窗（防止误操作）
- 修改成功后自动退出登录并跳转
- 完整的自定义验证规则（密码复杂度、一致性检查）

---

## Task 4: 实现角色权限 Tab 页（RolePermissions）

**Files:**
- Create: `frontend/src/views/system/components/RolePermissions.vue`

### Step 1: 创建 RolePermissions.vue 组件

```vue
<template>
  <div class="role-permissions">
    <!-- 角色展示 -->
    <div class="section">
      <h3 class="section-title">
        <el-icon><User /></el-icon>
        当前角色
      </h3>
      <div class="role-tags">
        <el-tag
          v-for="role in userStore.roles"
          :key="role"
          :type="getRoleType(role)"
          size="large"
          effect="dark"
          class="role-tag"
        >
          {{ getRoleName(role) }}
        </el-tag>
        <el-tag v-if="userStore.roles.length === 0" type="info" size="large">
          暂无角色
        </el-tag>
      </div>
    </div>

    <!-- 权限列表 -->
    <div class="section">
      <h3 class="section-title">
        <el-icon><Key /></el-icon>
        权限列表
      </h3>

      <el-table
        :data="permissionTableData"
        border
        stripe
        style="width: 100%"
        empty-text="暂无权限数据"
      >
        <el-table-column prop="module" label="模块" width="150" />
        <el-table-column prop="name" label="权限名称" width="200" />
        <el-table-column prop="code" label="权限编码" />
      </el-table>

      <div class="tip-box">
        <el-icon><InfoFilled /></el-icon>
        <span>如需调整角色权限，请联系系统管理员</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { User, Key, InfoFilled } from '@element-plus/icons-vue'
import { useUserStore } from '../../../store/user'

const userStore = useUserStore()

// 角色名称映射
const roleNames = {
  admin: '管理员',
  production_manager: '生产经理',
  warehouse_staff: '仓库人员',
  sales_staff: '销售人员',
  inactive: '未激活'
}

// 角色颜色映射
const roleTypes = {
  admin: 'danger',
  production_manager: 'warning',
  warehouse_staff: 'success',
  sales_staff: '',
  inactive: 'info'
}

function getRoleName(role) {
  return roleNames[role] || role
}

function getRoleType(role) {
  return roleTypes[role] || ''
}

// 将权限列表转换为表格数据（按模块分组）
const permissionTableData = computed(() => {
  const permissions = userStore.permissions || []

  // 权限编码到模块和名称的映射
  const permissionMap = {
    // 用户管理
    USER_READ: { module: '用户管理', name: '查看用户' },
    USER_CREATE: { module: '用户管理', name: '创建用户' },
    USER_UPDATE: { module: '用户管理', name: '更新用户' },
    USER_DELETE: { module: '用户管理', name: '删除用户' },
    ROLE_ASSIGN: { module: '用户管理', name: '分配角色' },

    // 生产管理
    PRODUCTION_PLAN_READ: { module: '生产管理', name: '查看生产计划' },
    PRODUCTION_PLAN_CREATE: { module: '生产管理', name: '创建生产计划' },
    PRODUCTION_PLAN_UPDATE: { module: '生产管理', name: '更新生产计划' },
    PRODUCTION_TASK_READ: { module: '生产管理', name: '查看任务' },
    PRODUCTION_TASK_CREATE: { module: '生产管理', name: '创建任务' },
    PRODUCTION_TASK_UPDATE: { module: '生产管理', name: '更新任务' },

    // 库存管理
    INVENTORY_READ: { module: '库存管理', name: '查看库存' },
    INVENTORY_UPDATE: { module: '库存管理', name: '更新库存' },
    INVENTORY_ALERT: { module: '库存管理', name: '库存预警' },

    // 订单管理
    ORDER_READ: { module: '订单管理', name: '查看订单' },
    ORDER_CREATE: { module: '订单管理', name: '创建订单' },
    ORDER_UPDATE: { module: '订单管理', name: '更新订单' },
    ORDER_APPROVE: { module: '订单管理', name: '审批订单' },

    // 销售管理
    SALES_READ: { module: '销售管理', name: '查看销售记录' },
    SALES_CREATE: { module: '销售管理', name: '创建销售记录' },
    CUSTOMER_MANAGE: { module: '销售管理', name: '客户管理' },

    // 数据统计
    STATISTICS_VIEW: { module: '数据统计', name: '查看统计报表' }
  }

  return permissions.map(code => ({
    code,
    ...(permissionMap[code] || { module: '其他', name: code })
  })).sort((a, b) => a.module.localeCompare(b.module))
})
</script>

<style scoped>
.role-permissions {
  padding: 20px 0;
}

.section {
  margin-bottom: 30px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 2px solid #409eff;
}

.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 8px;
}

.role-tag {
  font-size: 14px;
  padding: 8px 16px;
}

.tip-box {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding: 12px 16px;
  background: #ecf5ff;
  border: 1px solid #d9ecff;
  border-radius: 4px;
  color: #409eff;
  font-size: 14px;
}

.tip-box .el-icon {
  font-size: 16px;
}

:deep(.el-table) {
  border-radius: 8px;
  overflow: hidden;
}
</style>
```

**关键特性：**
- 只读展示（所有数据不可编辑）
- 角色 Tag 标签展示（不同角色不同颜色）
- 权限表格化展示（按模块分组排序）
- 底部联系管理员提示（info 样式）
- 权限编码友好名称映射

---

## Task 5: 集成测试与优化

**Files:**
- Verify: 所有上述文件的集成工作正常

### Step 1: 功能完整性验证清单

运行前端开发服务器并逐一验证：

- [ ] **路由访问：** 访问 `/profile` 能正常加载页面
- [ ] **菜单跳转：** 点击右上角"个人信息"能正确跳转
- [ ] **Tab切换：** 三个标签页能正常切换，无报错
- [ ] **数据显示：** 基本信息 Tab 正确显示当前用户信息
- [ ] **信息编辑：** 修改真实姓名/手机号/邮箱能保存成功
- [ ] **表单验证：**
  - 真实姓名为空时提示必填
  - 手机号格式错误时提示
  - 邮箱格式错误时提示
- [ ] **重置功能：** 重置按钮能恢复原始数据（带确认弹窗）
- [ ] **密码修改完整流程：**
  - 输入旧密码、新密码、确认密码
  - 密码强度指示器正常工作
  - 提交前二次确认弹窗
  - 修改成功后自动退出并跳转登录页
- [ ] **角色权限展示：** 正确显示当前用户的角色和权限列表
- [ ] **错误处理：**
  - 断网状态下有友好提示
  - 服务器错误时有错误消息
- [ ] **响应式布局：** 缩小浏览器窗口时布局自适应

### Step 2: UI细节优化

根据实际效果进行微调：

- [ ] 调整卡片间距和内边距
- [ ] 确保字体大小与系统其他页面一致
- [ ] 检查颜色对比度符合可访问性标准
- [ ] 测试不同分辨率下的显示效果（1920x1080, 1366x768）
- [ ] 验证 Element Plus 组件样式未被意外覆盖

### Step 3: 性能优化（可选）

- [ ] 添加页面加载骨架屏（Skeleton）
- [ ] 图片懒加载（如果后续添加头像功能）
- [ ] 防抖处理频繁的表单输入验证

---

## 实现注意事项

### 1. 安全性要点

✅ **必须遵守：**
- 使用 `userStore.userInfo.id` 作为用户ID，不允许手动输入或从URL获取
- 密码字段使用 `type="password"` 避免明文显示
- 修改密码后立即清除登录状态
- 敏感操作（重置、改密）必须有二次确认

⚠️ **注意：**
- 当前方案依赖前端限制，生产环境建议后端增加专用API
- 不在前端存储任何密码明文（仅在提交时发送）

### 2. 用户体验要点

✅ **必须做到：**
- 加载状态有明确的视觉反馈（loading动画）
- 操作结果有清晰的成功/失败提示
- 表单验证错误要精确到具体字段
- 必填项用红色星号标记

💡 **建议优化：**
- 手机号和邮箱脱敏显示（138****1234）
- 自动保存草稿（localStorage）
- 页面离开前检测未保存修改

### 3. 代码规范

✅ **遵循项目现有风格：**
- 使用 Composition API (`<script setup>`)
- 样式使用 `<style scoped>` 避免污染
- API调用统一使用封装好的 request 工具
- 状态管理统一使用 Pinia store

📝 **命名约定：**
- 组件名：PascalCase（如 `BasicInfo.vue`）
- 变量/函数：camelCase（如 `handleSave`）
- 常量：UPPER_SNAKE_CASE（如 `ROLE_NAMES`）
- CSS类：kebab-case（如 `basic-info`）

---

## 预期成果

完成本计划后，系统将拥有完整的个人信息管理功能：

1. ✅ 用户可以查看和编辑个人基本信息（姓名、手机、邮箱）
2. ✅ 用户可以安全地修改登录密码（带强度检测和二次确认）
3. ✅ 用户可以查看自己的角色和权限列表（只读）
4. ✅ UI美观且符合项目主题风格
5. ✅ 具备完善的表单验证和错误处理机制
6. ✅ 响应式设计适配多种屏幕尺寸

---

## 后续扩展方向（不在本次实现范围内）

如果将来需要增强功能，可以考虑：

1. **头像上传：** 扩展User模型 + 文件上传接口
2. **专用API端点：** `PUT /api/users/profile` 和 `PUT /api/users/password`
3. **操作日志：** 记录每次个人信息变更历史
4. **多因素认证：** 修改密码时需要短信/邮箱验证码

---

**计划版本：** v1.0
**创建日期：** 2026-04-16
**基于设计文档：** [2026-04-16-profile-design.md](../specs/2026-04-16-profile-design.md)
**预计工作量：** 4-5小时
