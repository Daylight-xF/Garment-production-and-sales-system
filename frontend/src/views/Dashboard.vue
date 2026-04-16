<template>
  <div class="dashboard-container" v-loading="loading">
    <div class="welcome-section">
      <div class="welcome-content">
        <h2>欢迎回来，{{ userStore.realName || userStore.username }}</h2>
        <p class="welcome-desc">以下是系统概览信息</p>
      </div>
      <div class="welcome-time">
        <div class="current-time">{{ currentTime }}</div>
        <div class="current-date">{{ currentDate }}</div>
      </div>
    </div>

    <el-row :gutter="20" class="stat-row">
      <el-col :span="6" v-if="canViewSales">
        <el-card shadow="hover" class="stat-card stat-card-blue">
          <div class="stat-icon" style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%)">
            <el-icon :size="28"><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ salesData.totalOrders || 0 }}</div>
            <div class="stat-label">总订单数</div>
          </div>
          <div class="stat-trend up" v-if="salesData.orderGrowth > 0">
            <el-icon><Top /></el-icon>
            {{ salesData.orderGrowth }}%
          </div>
        </el-card>
      </el-col>
      <el-col :span="canViewSales && !canViewProduction && !canViewInventory ? 12 : (canViewSales ? 6 : 0)" v-if="!canViewProduction && !canViewInventory && canViewSales">
        <el-card shadow="hover" class="stat-card stat-card-red">
          <div class="stat-icon" style="background: linear-gradient(135deg, #fa709a 0%, #fee140 100%)">
            <el-icon :size="28"><TrendCharts /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">¥{{ formatNumber(salesData.monthlyAmount) }}</div>
            <div class="stat-label">本月销售额</div>
          </div>
          <div class="stat-trend up" v-if="salesData.salesGrowth > 0">
            <el-icon><Top /></el-icon>
            {{ salesData.salesGrowth }}%
          </div>
        </el-card>
      </el-col>
      <el-col :span="6" v-if="canViewProduction">
        <el-card shadow="hover" class="stat-card stat-card-green">
          <div class="stat-icon" style="background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%)">
            <el-icon :size="28"><Goods /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ productionData.inProgressPlans || 0 }}</div>
            <div class="stat-label">生产中</div>
          </div>
          <div class="stat-trend neutral">
            进行中
          </div>
        </el-card>
      </el-col>
      <el-col :span="6" v-if="canViewInventory">
        <el-card shadow="hover" class="stat-card stat-card-orange">
          <div class="stat-icon" style="background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%)">
            <el-icon :size="28"><Box /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ (inventoryData.rawMaterialTotalQuantity || 0) + (inventoryData.finishedProductTotalQuantity || 0) }}</div>
            <div class="stat-label">库存总量</div>
          </div>
          <div class="stat-trend down" v-if="alertStats.pendingCount > 0">
            <el-icon><Warning /></el-icon>
            {{ alertStats.pendingCount }}预警
          </div>
        </el-card>
      </el-col>
      <el-col :span="6" v-if="canViewSales && (canViewProduction || canViewInventory)">
        <el-card shadow="hover" class="stat-card stat-card-red">
          <div class="stat-icon" style="background: linear-gradient(135deg, #fa709a 0%, #fee140 100%)">
            <el-icon :size="28"><TrendCharts /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">¥{{ formatNumber(salesData.monthlyAmount) }}</div>
            <div class="stat-label">本月销售额</div>
          </div>
          <div class="stat-trend up" v-if="salesData.salesGrowth > 0">
            <el-icon><Top /></el-icon>
            {{ salesData.salesGrowth }}%
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useUserStore } from '../store/user'
import {
  getProductionOverview,
  getSalesOverview,
  getInventoryOverview,
  getAlertStats
} from '../api/statistics'
import { getOrderList } from '../api/order'

const userStore = useUserStore()
const loading = ref(false)

const productionData = ref({})
const salesData = ref({})
const inventoryData = ref({})
const alertStats = ref({})
const recentOrders = ref([])
const recentAlerts = ref([])

let timeInterval = null
const currentTime = ref('')
const currentDate = ref('')

const canViewProduction = computed(() => userStore.hasPermission('STATS_PRODUCTION'))
const canViewSales = computed(() => {
  return userStore.hasPermission('STATS_SALES') && !userStore.hasRole('production_manager')
})
const canViewInventory = computed(() => userStore.hasPermission('STATS_INVENTORY'))

const todoList = ref([
  { text: '处理待审核订单', time: '今天', type: 'urgent' },
  { text: '确认生产计划', time: '明天', type: 'normal' },
  { text: '更新客户信息', time: '本周', type: 'normal' },
  { text: '完成月度报表', time: '本月', type: 'done' }
])

const todoCount = computed(() => {
  return todoList.value.filter(item => item.type !== 'done').length
})

function updateTime() {
  const now = new Date()
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  const options = { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }
  currentDate.value = now.toLocaleDateString('zh-CN', options)
}

function formatNumber(num) {
  if (!num && num !== 0) return '0'
  return Number(num).toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
}

function getStatusType(status) {
  const map = {
    PENDING_APPROVAL: 'warning',
    APPROVED: '',
    IN_PRODUCTION: '',
    SHIPPED: 'success',
    COMPLETED: 'success',
    CANCELLED: 'info'
  }
  return map[status] || ''
}

function getStatusLabel(status) {
  const map = {
    PENDING_APPROVAL: '待审核',
    APPROVED: '已审核',
    IN_PRODUCTION: '生产中',
    SHIPPED: '已发货',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  }
  return map[status] || status
}

async function loadDashboardData() {
  loading.value = true
  try {
    const requests = []

    if (canViewProduction.value) {
      requests.push(
        getProductionOverview()
          .then(res => ({ type: 'production', res }))
          .catch(err => {
            console.warn('加载生产概览失败:', err.message)
            return null
          })
      )
    }
    if (canViewSales.value) {
      requests.push(
        getSalesOverview()
          .then(res => ({ type: 'sales', res }))
          .catch(err => {
            console.warn('加载销售概览失败:', err.message)
            return null
          })
      )
      requests.push(
        getOrderList({ page: 1, size: 5 })
          .then(res => ({ type: 'orders', res }))
          .catch(err => {
            console.warn('加载订单列表失败:', err.message)
            return null
          })
      )
    }
    if (canViewInventory.value) {
      requests.push(
        getInventoryOverview()
          .then(res => ({ type: 'inventory', res }))
          .catch(err => {
            console.warn('加载库存概览失败:', err.message)
            return null
          })
      )
      requests.push(
        getAlertStats()
          .then(res => ({ type: 'alerts', res }))
          .catch(err => {
            console.warn('加载预警统计失败:', err.message)
            return null
          })
      )
    }

    if (requests.length > 0) {
      const results = await Promise.allSettled(requests)

      results.forEach(result => {
        if (result.status === 'fulfilled' && result.value) {
          const { type, res } = result.value
          if (res?.code === 200 && res.data) {
            if (type === 'production') productionData.value = res.data
            else if (type === 'sales') salesData.value = res.data
            else if (type === 'inventory') inventoryData.value = res.data
            else if (type === 'orders') recentOrders.value = res.data?.records || []
            else if (type === 'alerts') {
              alertStats.value = res.data
              recentAlerts.value = res.data?.recentAlerts || []
            }
          }
        }
      })
    }
  } catch (error) {
    console.error('加载仪表盘数据失败:', error)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  updateTime()
  timeInterval = setInterval(updateTime, 1000)
  await loadDashboardData()
})

onUnmounted(() => {
  if (timeInterval) {
    clearInterval(timeInterval)
  }
})
</script>

<style scoped>
.dashboard-container {
  padding: 24px;
  background: #f5f7fa;
  min-height: calc(100vh - 84px);
}

.welcome-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28px;
  padding: 28px 32px;
  background: linear-gradient(135deg, #1e3a5f 0%, #2d5a87 50%, #1e6b4f 100%);
  border-radius: 16px;
  color: #fff;
  box-shadow: 0 10px 30px rgba(30, 58, 95, 0.25);
}

.welcome-content h2 {
  margin: 0 0 8px;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.welcome-desc {
  margin: 0;
  font-size: 15px;
  opacity: 0.9;
}

.welcome-time {
  text-align: right;
}

.current-time {
  font-size: 36px;
  font-weight: 700;
  font-family: 'Courier New', monospace;
  letter-spacing: 2px;
}

.current-date {
  font-size: 14px;
  opacity: 0.9;
  margin-top: 4px;
}

.stat-row {
  margin-bottom: 20px;
}

.stat-card {
  margin-bottom: 16px;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.15);
}

.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  padding: 24px;
  width: 100%;
  position: relative;
  z-index: 1;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.stat-info {
  margin-left: 18px;
  flex: 1;
}

.stat-number {
  font-size: 28px;
  font-weight: 800;
  color: #1a1a1a;
  line-height: 1.2;
  letter-spacing: -0.5px;
}

.stat-label {
  font-size: 13px;
  color: #888;
  margin-top: 6px;
  font-weight: 500;
}

.stat-trend {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 20px;
}

.stat-trend.up {
  color: #67c23a;
  background: rgba(103, 194, 58, 0.1);
}

.stat-trend.down {
  color: #f56c6c;
  background: rgba(245, 108, 108, 0.1);
}

.stat-trend.neutral {
  color: #909399;
  background: rgba(144, 147, 153, 0.1);
}

.content-row {
  margin-bottom: 20px;
}

.info-card {
  height: auto !important;
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.card-title .el-icon {
  color: #667eea;
}

.view-all {
  color: #667eea;
  font-size: 13px;
  font-weight: 500;
  text-decoration: none;
  transition: all 0.2s;
}

.view-all:hover {
  color: #764ba2;
  transform: translateX(4px);
  display: inline-block;
}

.notice-list {
  padding: 8px 0;
}

.notice-item {
  display: flex;
  gap: 16px;
  padding: 18px 0;
  border-bottom: 1px solid #f5f5f5;
  transition: all 0.2s;
}

.notice-item:last-child {
  border-bottom: none;
}

.notice-item:hover {
  background: #fafafa;
  margin: 0 -16px;
  padding-left: 16px;
  padding-right: 16px;
  border-radius: 8px;
}

.notice-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-top: 6px;
  flex-shrink: 0;
}

.notice-dot.blue {
  background: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.2);
}

.notice-dot.green {
  background: #67c23a;
  box-shadow: 0 0 0 3px rgba(103, 194, 58, 0.2);
}

.notice-dot.orange {
  background: #e6a23c;
  box-shadow: 0 0 0 3px rgba(230, 162, 60, 0.2);
}

.notice-content {
  flex: 1;
}

.notice-content h4 {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.notice-content p {
  margin: 0 0 8px;
  font-size: 13px;
  color: #666;
  line-height: 1.6;
}

.notice-time {
  font-size: 12px;
  color: #999;
}

.todo-list {
  padding: 8px 0;
}

.todo-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid #f5f5f5;
  transition: all 0.2s;
}

.todo-item:last-child {
  border-bottom: none;
}

.todo-item:hover {
  background: #fafafa;
  margin: 0 -12px;
  padding-left: 12px;
  padding-right: 12px;
  border-radius: 8px;
}

.todo-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  font-size: 16px;
}

.todo-icon.urgent {
  background: linear-gradient(135deg, #f56c6c 0%, #e74c3c 100%);
}

.todo-icon.normal {
  background: linear-gradient(135deg, #e6a23c 0%, #f39c12 100%);
}

.todo-icon.done {
  background: linear-gradient(135deg, #67c23a 0%, #52c41a 100%);
}

.todo-info {
  flex: 1;
}

.todo-text {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
}

.todo-time {
  font-size: 12px;
  color: #999;
}

.alert-summary {
  text-align: center;
  padding: 20px 0;
}

.alert-count {
  margin-bottom: 20px;
}

.count-number {
  display: block;
  font-size: 48px;
  font-weight: 800;
  color: #f56c6c;
  line-height: 1;
  margin-bottom: 8px;
}

.count-label {
  font-size: 14px;
  color: #888;
  font-weight: 500;
}

.alert-items {
  text-align: left;
}

.alert-mini {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f5f5f5;
}

.alert-mini:last-child {
  border-bottom: none;
}

.alert-name {
  font-size: 13px;
  color: #333;
  font-weight: 500;
}

.quick-actions-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  padding: 16px 0;
}

.action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 18px 12px;
  border-radius: 12px;
  background: #fafafa;
  text-decoration: none;
  color: #333;
  transition: all 0.3s ease;
  font-size: 13px;
  font-weight: 500;
}

.action-btn:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.1);
  background: #fff;
}

.action-icon-wrap {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.recent-list {
  padding: 8px 0;
}

.recent-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid #f5f5f5;
  transition: all 0.2s;
}

.recent-item:last-child {
  border-bottom: none;
}

.recent-item:hover {
  background: #fafafa;
  margin: 0 -12px;
  padding-left: 12px;
  padding-right: 12px;
  border-radius: 8px;
}

.recent-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.recent-no {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  font-weight: 600;
  color: #333;
}

.recent-customer {
  font-size: 12px;
  color: #888;
}

.recent-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.recent-amount {
  font-size: 15px;
  font-weight: 700;
  color: #667eea;
}

.no-data-mini {
  text-align: center;
  padding: 40px 20px;
  color: #ccc;
  font-size: 14px;
}

.tips-list {
  padding: 8px 0;
}

.tip-item {
  display: flex;
  gap: 16px;
  padding: 18px 0;
  border-bottom: 1px solid #f5f5f5;
  transition: all 0.2s;
}

.tip-item:last-child {
  border-bottom: none;
}

.tip-item:hover {
  background: #fafafa;
  margin: 0 -16px;
  padding-left: 16px;
  padding-right: 16px;
  border-radius: 8px;
}

.tip-number {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
  flex-shrink: 0;
}

.tip-content h4 {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.tip-content p {
  margin: 0;
  font-size: 13px;
  color: #888;
  line-height: 1.5;
}
</style>
