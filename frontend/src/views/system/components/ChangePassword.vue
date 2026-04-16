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
        <div class="password-hint">6-20位</div>
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
import { changePassword } from '../../../api/user'

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
    await changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword
    })

    ElMessage.success('密码修改成功，即将跳转到登录页')

    setTimeout(() => {
      userStore.logout()
      router.push('/login')
    }, 2000)
  } catch (error) {
    const msg = error.message || '密码修改失败'
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
