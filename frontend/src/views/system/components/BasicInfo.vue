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
    const msg = error.message || '保存失败'
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
