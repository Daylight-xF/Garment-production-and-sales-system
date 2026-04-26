<template>
  <div class="register-container">
    <div class="register-bg-pattern"></div>
    <div class="register-card">
      <div class="register-left">
        <div class="brand-section">
          <img src="../../public/cat.svg" alt="logo" class="brand-logo" />
          <h1 class="brand-title">服装生产销售管理系统</h1>
          <p class="brand-desc">Clothing Production & Sales Management System</p>
        </div>
        <div class="feature-list">
          <div class="feature-item">
            <span class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="8.5" cy="7" r="4"/><path d="M20 8v6m3-3h-6"/></svg>
            </span>
            <span>快速创建账号</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            </span>
            <span>安全可靠保障</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg>
            </span>
            <span>高效协同工作</span>
          </div>
        </div>
      </div>

      <div class="register-right">
        <div class="register-header">
          <h2 class="register-title">创建账号</h2>
          <p class="register-subtitle">填写以下信息完成注册</p>
        </div>
        <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" label-width="0" class="register-form">
          <el-form-item prop="username">
            <el-input
              v-model="registerForm.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              size="large"
              clearable
            />
          </el-form-item>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item prop="password">
                <el-input
                  v-model="registerForm.password"
                  type="password"
                  placeholder="请输入密码"
                  :prefix-icon="Lock"
                  size="large"
                  show-password
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="confirmPassword">
                <el-input
                  v-model="registerForm.confirmPassword"
                  type="password"
                  placeholder="请确认密码"
                  :prefix-icon="Lock"
                  size="large"
                  show-password
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item prop="realName">
                <el-input
                  v-model="registerForm.realName"
                  placeholder="请输入真实姓名"
                  :prefix-icon="UserFilled"
                  size="large"
                  clearable
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="phone">
                <el-input
                  v-model="registerForm.phone"
                  placeholder="请输入手机号"
                  :prefix-icon="Phone"
                  size="large"
                  clearable
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item prop="email">
            <el-input
              v-model="registerForm.email"
              placeholder="请输入邮箱"
              :prefix-icon="Message"
              size="large"
              clearable
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              size="large"
              :loading="loading"
              class="register-btn"
              @click="handleRegister"
            >
              注 册
            </el-button>
          </el-form-item>
          <div class="login-link">
            已有账号？<router-link to="/login">立即登录</router-link>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, UserFilled, Phone, Message } from '@element-plus/icons-vue'
import { register } from '../api/user'
import { getErrorMessage } from '../utils/errorMessage'

const router = useRouter()

const registerFormRef = ref(null)
const loading = ref(false)

const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  phone: '',
  email: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在3到20个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在6到20个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ]
}

const handleRegister = async () => {
  const form = registerFormRef.value
  if (!form) return
  await form.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await register({
        username: registerForm.username,
        password: registerForm.password,
        realName: registerForm.realName,
        phone: registerForm.phone,
        email: registerForm.email
      })
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } catch (error) {
      ElMessage.error(getErrorMessage(error, '注册失败，请稍后重试'))
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #1a2a3a 0%, #304156 50%, #263445 100%);
  position: relative;
  overflow: hidden;
}

.register-bg-pattern {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(circle at 80% 80%, rgba(64, 158, 255, 0.08) 0%, transparent 50%),
    radial-gradient(circle at 20% 20%, rgba(64, 158, 255, 0.06) 0%, transparent 50%);
  pointer-events: none;
}

.register-bg-pattern::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background:
    repeating-linear-gradient(
      -45deg,
      transparent,
      transparent 60px,
      rgba(255, 255, 255, 0.015) 60px,
      rgba(255, 255, 255, 0.015) 120px
    );
  animation: bgShift 30s ease-in-out infinite;
}

@keyframes bgShift {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(-40px, 40px); }
}

.register-card {
  width: 920px;
  min-height: 600px;
  background: #fff;
  border-radius: 16px;
  box-shadow:
    0 25px 60px rgba(0, 0, 0, 0.3),
    0 0 0 1px rgba(255, 255, 255, 0.05);
  display: flex;
  overflow: hidden;
  animation: cardEnter 0.7s cubic-bezier(0.22, 1, 0.36, 1);
}

@keyframes cardEnter {
  from {
    opacity: 0;
    transform: translateY(30px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.register-left {
  width: 340px;
  background: linear-gradient(160deg, #304156 0%, #1a2a3a 100%);
  padding: 48px 32px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.register-left::before {
  content: '';
  position: absolute;
  top: -50px;
  right: -50px;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(64, 158, 255, 0.13) 0%, transparent 70%);
  pointer-events: none;
}

.register-left::after {
  content: '';
  position: absolute;
  bottom: -30px;
  left: -30px;
  width: 130px;
  height: 130px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(64, 158, 255, 0.09) 0%, transparent 70%);
  pointer-events: none;
}

.brand-section {
  position: relative;
  z-index: 1;
  text-align: center;
  margin-bottom: 36px;
}

.brand-logo {
  width: 56px;
  height: 56px;
  margin-bottom: 14px;
  filter: brightness(0) invert(1);
  animation: logoFloat 4s ease-in-out infinite;
}

@keyframes logoFloat {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

.brand-title {
  margin: 0 0 10px;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: 1px;
}

.brand-desc {
  margin: 0;
  color: rgba(191, 203, 217, 0.65);
  font-size: 10px;
  letter-spacing: 2px;
  text-transform: uppercase;
}

.feature-list {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 11px;
  color: rgba(191, 203, 217, 0.82);
  font-size: 13px;
  padding: 10px 13px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  transition: all 0.3s ease;
  animation: featureSlideIn 0.6s cubic-bezier(0.22, 1, 0.36, 1) backwards;
}

.feature-item:nth-child(1) { animation-delay: 0.3s; }
.feature-item:nth-child(2) { animation-delay: 0.4s; }
.feature-item:nth-child(3) { animation-delay: 0.5s; }

@keyframes featureSlideIn {
  from {
    opacity: 0;
    transform: translateX(-18px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.feature-item:hover {
  background: rgba(64, 158, 255, 0.08);
  border-color: rgba(64, 158, 255, 0.2);
  color: #fff;
  transform: translateX(4px);
}

.feature-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 7px;
  background: rgba(64, 158, 255, 0.14);
  flex-shrink: 0;
}

.feature-icon svg {
  width: 15px;
  height: 15px;
  color: #409eff;
}

.register-right {
  flex: 1;
  padding: 40px 42px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow-y: auto;
  max-height: 100vh;
}

.register-header {
  margin-bottom: 28px;
  animation: headerFadeIn 0.5s ease backwards;
  animation-delay: 0.2s;
}

@keyframes headerFadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.register-title {
  margin: 0 0 8px;
  color: #1a2a3a;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.5px;
}

.register-subtitle {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.register-form {
  animation: formFadeIn 0.5s ease backwards;
  animation-delay: 0.35s;
}

@keyframes formFadeIn {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

.register-form :deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #dcdfe6 inset;
  transition: all 0.3s ease;
  padding: 4px 12px;
}

.register-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #c0c4cc inset;
}

.register-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #409eff inset, 0 0 0 3px rgba(64, 158, 255, 0.1);
}

.register-form :deep(.el-input__inner) {
  font-size: 14px;
}

.register-btn {
  width: 100%;
  height: 44px;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 4px;
  background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
  border: none;
  transition: all 0.3s ease;
  box-shadow: 0 4px 14px rgba(64, 158, 255, 0.35);
}

.register-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.45);
}

.register-btn:active {
  transform: translateY(0);
}

.login-link {
  text-align: center;
  margin-top: 16px;
  color: #909399;
  font-size: 13px;
}

.login-link a {
  color: #409eff;
  font-weight: 500;
  text-decoration: none;
  transition: all 0.2s;
  margin-left: 2px;
}

.login-link a:hover {
  color: #337ecc;
  text-decoration: underline;
  text-underline-offset: 3px;
}

@media (max-width: 900px) {
  .register-card {
    width: 94%;
    max-width: 480px;
    flex-direction: column;
    min-height: auto;
  }

  .register-left {
    display: none;
  }

  .register-right {
    padding: 36px 28px;
    max-height: none;
  }

  .register-form :deep(.el-row) {
    flex-direction: column;
  }

  .register-form :deep(.el-col) {
    max-width: 100%;
    width: 100%;
  }
}
</style>
