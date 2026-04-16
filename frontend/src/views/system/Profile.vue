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
