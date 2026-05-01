<template>
  <el-container class="main-layout">
    <el-aside
      :width="sidebarWidth"
      class="sidebar"
      :class="{ 'mobile-open': mobileSidebarOpen }"
    >
      <div class="logo-container">
        <img src="../../public/cat.svg" alt="logo" class="logo-img" />
        <span v-show="!isCollapse || isMobile" class="logo-text">服装生产销售管理系统</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isMobile ? false : isCollapse"
        :collapse-transition="false"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <template #title>首页</template>
        </el-menu-item>

        <el-sub-menu
          v-if="hasMenuPermission('production')"
          index="production"
        >
          <template #title>
            <el-icon><Goods /></el-icon>
            <span>生产管理</span>
          </template>
          <el-menu-item index="/production/plan">生产计划</el-menu-item>
          <el-menu-item index="/production/task">生产任务</el-menu-item>
          <el-menu-item index="/production/progress">生产进度</el-menu-item>
        </el-sub-menu>

        <el-sub-menu
          v-if="hasMenuPermission('inventory')"
          index="inventory"
        >
          <template #title>
            <el-icon><Box /></el-icon>
            <span>库存管理</span>
          </template>
          <el-menu-item
            v-if="hasInventoryItemPermission('rawMaterial')"
            index="/inventory/raw-material"
          >原材料库存</el-menu-item>
          <el-menu-item
              v-if="hasInventoryItemPermission('pendingStockIn')"
              index="/inventory/pending-stock-in"
          >待入库</el-menu-item>
          <el-menu-item
            v-if="hasInventoryItemPermission('finishedProduct')"
            index="/inventory/finished-product"
          >成品库存</el-menu-item>
          <el-menu-item
            v-if="hasInventoryItemPermission('alert')"
            index="/inventory/alert"
          >库存预警</el-menu-item>
        </el-sub-menu>

        <el-sub-menu
          v-if="hasMenuPermission('order')"
          index="order"
        >
          <template #title>
            <el-icon><Document /></el-icon>
            <span>订单管理</span>
          </template>
          <el-menu-item index="/order/create">创建订单</el-menu-item>
          <el-menu-item index="/order/list">订单列表</el-menu-item>
        </el-sub-menu>

        <el-sub-menu
          v-if="hasMenuPermission('sales')"
          index="sales"
        >
          <template #title>
            <el-icon><TrendCharts /></el-icon>
            <span>销售管理</span>
          </template>
          <el-menu-item index="/sales/customer">客户管理</el-menu-item>
          <el-menu-item index="/sales/record">销售记录</el-menu-item>
          <el-menu-item index="/sales/report">销售报表</el-menu-item>
        </el-sub-menu>

        <el-sub-menu
          v-if="hasMenuPermission('system')"
          index="system"
        >
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/system/user">用户管理</el-menu-item>
          <el-menu-item index="/system/product-definition">产品定义</el-menu-item>
        </el-sub-menu>

        <el-menu-item
            v-if="hasMenuPermission('statistics')"
            index="/statistics"
        >
          <el-icon><DataAnalysis /></el-icon>
          <template #title>数据统计</template>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <div
      v-show="isMobile && mobileSidebarOpen"
      class="mobile-sidebar-mask"
      @click="closeMobileSidebar"
    ></div>

    <el-container class="main-container">
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentRoute.meta?.title && currentRoute.path !== '/dashboard'">
              {{ currentRoute.meta.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-info">
              <el-icon><UserFilled /></el-icon>
              <span class="username">{{ userStore.realName || userStore.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapse = ref(false)
const isMobile = ref(false)
const mobileSidebarOpen = ref(false)
const MOBILE_BREAKPOINT = 768

const activeMenu = computed(() => route.path)
const currentRoute = computed(() => route)
const sidebarWidth = computed(() => (isMobile.value ? '220px' : (isCollapse.value ? '64px' : '220px')))

const menuRoleMap = {
  production: ['admin', 'production_manager'],
  inventory: ['admin', 'warehouse_staff', 'sales_staff'],
  order: ['admin', 'sales_staff'],
  sales: ['admin', 'sales_staff'],
  statistics: ['admin', 'production_manager', 'warehouse_staff'],
  system: ['admin']
}

const inventoryItemRoleMap = {
  rawMaterial: ['admin', 'warehouse_staff'],
  finishedProduct: ['admin', 'warehouse_staff', 'sales_staff'],
  pendingStockIn: ['admin', 'warehouse_staff'],
  alert: ['admin', 'warehouse_staff']
}

function hasMenuPermission(menuKey) {
  const allowedRoles = menuRoleMap[menuKey]
  if (!allowedRoles) return true
  return userStore.roles.some((role) => allowedRoles.includes(role))
}

function hasInventoryItemPermission(itemKey) {
  const allowedRoles = inventoryItemRoleMap[itemKey]
  if (!allowedRoles) return false
  return userStore.roles.some((role) => allowedRoles.includes(role))
}

function toggleCollapse() {
  if (isMobile.value) {
    mobileSidebarOpen.value = !mobileSidebarOpen.value
    return
  }
  isCollapse.value = !isCollapse.value
}

function closeMobileSidebar() {
  mobileSidebarOpen.value = false
}

function syncMobileState() {
  isMobile.value = window.innerWidth <= MOBILE_BREAKPOINT
  if (!isMobile.value) {
    mobileSidebarOpen.value = false
  }
}

async function handleCommand(command) {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      userStore.logout()
      router.push('/login')
      ElMessage.success('已退出登录')
    } catch {
      // cancelled
    }
  } else if (command === 'profile') {
    router.push('/profile')
  }
}

watch(() => route.path, () => {
  closeMobileSidebar()
})

onMounted(() => {
  syncMobileState()
  window.addEventListener('resize', syncMobileState)
})

onUnmounted(() => {
  window.removeEventListener('resize', syncMobileState)
})
</script>

<style scoped>
.main-layout {
  height: 100vh;
  width: 100%;
  overflow: hidden;
}

.sidebar {
  background-color: #304156;
  overflow-y: auto;
  overflow-x: hidden;
  transition: width 0.3s;
}

.sidebar::-webkit-scrollbar {
  width: 0;
}

.logo-container {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 16px;
  background-color: #263445;
  overflow: hidden;
}

.logo-img {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
}

.logo-text {
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  margin-left: 10px;
  white-space: nowrap;
}

.el-menu {
  border-right: none;
}

.main-container {
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  padding: 0 20px;
  height: 60px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #333;
  flex-shrink: 0;
}

.collapse-btn:hover {
  color: #409eff;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #333;
  font-size: 14px;
}

.user-info:hover {
  color: #409eff;
}

.username {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.main-content {
  background: #f0f2f5;
  overflow-y: auto;
  overflow-x: hidden;
  min-width: 0;
}

.mobile-sidebar-mask {
  display: none;
}

@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 1001;
    transform: translateX(-100%);
    transition: transform 0.25s ease;
    box-shadow: 8px 0 24px rgba(0, 0, 0, 0.16);
  }

  .sidebar.mobile-open {
    transform: translateX(0);
  }

  .mobile-sidebar-mask {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 1000;
    background: rgba(15, 23, 42, 0.45);
  }

  .header {
    height: 56px;
    padding: 0 12px;
  }

  .header-left {
    gap: 10px;
  }

  .header :deep(.el-breadcrumb) {
    min-width: 0;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .username {
    max-width: 88px;
  }

  .main-content {
    padding: 12px;
  }
}
</style>
