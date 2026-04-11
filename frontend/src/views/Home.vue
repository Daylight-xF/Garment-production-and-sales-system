<template>
  <div class="home-container">
    <el-container>
      <el-header class="home-header">
        <h2 class="system-title">服装生产销售管理系统</h2>
        <div class="header-right">
          <span class="welcome-text">欢迎，{{ userStore.username }}</span>
          <el-button type="danger" size="small" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="home-main">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-card shadow="hover" class="stat-card">
              <template #header>今日订单</template>
              <div class="stat-number">128</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="stat-card">
              <template #header>生产中</template>
              <div class="stat-number">56</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="stat-card">
              <template #header>库存总量</template>
              <div class="stat-number">3,420</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="stat-card">
              <template #header>本月销售额</template>
              <div class="stat-number">¥128,500</div>
            </el-card>
          </el-col>
        </el-row>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '../store/user'

const router = useRouter()
const userStore = useUserStore()

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    userStore.logout()
    router.push('/login')
  } catch {
    // cancelled
  }
}
</script>

<style scoped>
.home-container {
  min-height: 100vh;
  background: #f0f2f5;
}

.home-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  padding: 0 24px;
}

.system-title {
  font-size: 18px;
  color: #333;
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.welcome-text {
  color: #666;
  font-size: 14px;
}

.home-main {
  padding: 24px;
}

.stat-card {
  text-align: center;
}

.stat-number {
  font-size: 28px;
  font-weight: bold;
  color: #409eff;
}
</style>
