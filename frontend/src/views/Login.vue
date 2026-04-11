<template>
  <div class="login-container">
    <div class="login-bg-pattern"></div>
    <div class="login-card">
      <div class="login-left">
        <div class="brand-section">
          <img src="../../public/cat.svg" alt="logo" class="brand-logo" />
          <h1 class="brand-title">服装生产销售管理系统</h1>
          <p class="brand-desc">Clothing Production & Sales Management System</p>
        </div>
        <div class="feature-list">
          <div class="feature-item">
            <span class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"/></svg>
            </span>
            <span>全流程生产管理</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"/></svg>
            </span>
            <span>智能库存预警</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"/></svg>
            </span>
            <span>实时数据统计</span>
          </div>
        </div>
      </div>

      <div class="login-right">
        <div class="login-header">
          <h2 class="login-title">欢迎登录</h2>
          <p class="login-subtitle">请输入您的账号信息</p>
        </div>
        <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" label-width="0" class="login-form">
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              size="large"
              clearable
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              size="large"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              size="large"
              :loading="loading"
              class="login-btn"
              @click="handleLogin"
            >
              登 录
            </el-button>
          </el-form-item>
          <div class="register-link">
            还没有账号？<router-link to="/register">立即注册</router-link>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loginFormRef = ref(null)
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  const form = loginFormRef.value
  if (!form) return
  await form.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.login({
        username: loginForm.username,
        password: loginForm.password
      })
      ElMessage.success('登录成功')
      const redirect = route.query.redirect || '/'
      router.push(redirect)
    } catch (error) {
      const errorMsg = error.message || error.response?.data?.message || ''
      if (errorMsg.includes('未激活')) {
        ElMessage.error('账户未激活，请联系管理员！')
      } else if (errorMsg.includes('用户名或密码')) {
        ElMessage.error('用户名或密码不对')
      } else {
        ElMessage.error(errorMsg || '登录失败，请检查用户名和密码')
      }
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #1a2a3a 0%, #304156 50%, #263445 100%);
  position: relative;
  overflow: hidden;
}

.login-bg-pattern {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(circle at 20% 80%, rgba(64, 158, 255, 0.08) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(64, 158, 255, 0.06) 0%, transparent 50%);
  pointer-events: none;
}

.login-bg-pattern::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background:
    repeating-linear-gradient(
      45deg,
      transparent,
      transparent 60px,
      rgba(255, 255, 255, 0.015) 60px,
      rgba(255, 255, 255, 0.015) 120px
    );
  animation: bgShift 30s ease-in-out infinite;
}

@keyframes bgShift {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(40px, 40px); }
}

.login-card {
  width: 900px;
  min-height: 520px;
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

.login-left {
  width: 380px;
  background: linear-gradient(160deg, #304156 0%, #1a2a3a 100%);
  padding: 48px 36px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.login-left::before {
  content: '';
  position: absolute;
  top: -60px;
  right: -60px;
  width: 200px;
  height: 200px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(64, 158, 255, 0.15) 0%, transparent 70%);
  pointer-events: none;
}

.login-left::after {
  content: '';
  position: absolute;
  bottom: -40px;
  left: -40px;
  width: 150px;
  height: 150px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(64, 158, 255, 0.1) 0%, transparent 70%);
  pointer-events: none;
}

.brand-section {
  position: relative;
  z-index: 1;
  text-align: center;
  margin-bottom: 40px;
}

.brand-logo {
  width: 64px;
  height: 64px;
  margin-bottom: 16px;
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
  font-size: 20px;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: 1px;
}

.brand-desc {
  margin: 0;
  color: rgba(191, 203, 217, 0.7);
  font-size: 11px;
  letter-spacing: 2px;
  text-transform: uppercase;
}

.feature-list {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  color: rgba(191, 203, 217, 0.85);
  font-size: 13px;
  padding: 10px 14px;
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
    transform: translateX(-20px);
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
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: rgba(64, 158, 255, 0.15);
  flex-shrink: 0;
}

.feature-icon svg {
  width: 16px;
  height: 16px;
  color: #409eff;
}

.login-right {
  flex: 1;
  padding: 48px 44px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.login-header {
  margin-bottom: 32px;
  animation: headerFadeIn 0.5s ease backwards;
  animation-delay: 0.2s;
}

@keyframes headerFadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.login-title {
  margin: 0 0 8px;
  color: #1a2a3a;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.5px;
}

.login-subtitle {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.login-form {
  animation: formFadeIn 0.5s ease backwards;
  animation-delay: 0.35s;
}

@keyframes formFadeIn {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #dcdfe6 inset;
  transition: all 0.3s ease;
  padding: 4px 12px;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #c0c4cc inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #409eff inset, 0 0 0 3px rgba(64, 158, 255, 0.1);
}

.login-form :deep(.el-input__inner) {
  font-size: 14px;
}

.login-btn {
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

.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.45);
}

.login-btn:active {
  transform: translateY(0);
}

.register-link {
  text-align: center;
  margin-top: 18px;
  color: #909399;
  font-size: 13px;
}

.register-link a {
  color: #409eff;
  font-weight: 500;
  text-decoration: none;
  transition: all 0.2s;
  margin-left: 2px;
}

.register-link a:hover {
  color: #337ecc;
  text-decoration: underline;
  text-underline-offset: 3px;
}

@media (max-width: 768px) {
  .login-card {
    width: 92%;
    max-width: 420px;
    flex-direction: column;
    min-height: auto;
  }

  .login-left {
    display: none;
  }

  .login-right {
    padding: 36px 28px;
  }
}
</style>
