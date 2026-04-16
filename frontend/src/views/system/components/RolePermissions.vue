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

      <div class="tip-box">
        <el-icon><InfoFilled /></el-icon>
        <span>如需调整角色权限，请联系系统管理员</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { User, InfoFilled } from '@element-plus/icons-vue'
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
</style>
