<template>
  <div class="dashboard-container" v-loading="loading">
    <div class="welcome-section">
      <h2>欢迎回来，{{ userStore.realName || userStore.username }}</h2>
      <p class="welcome-desc">以下是系统概览信息</p>
    </div>

    <el-row :gutter="20" class="stat-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background-color: #409eff">
            <el-icon :size="28"><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ salesData.totalOrders || 0 }}</div>
            <div class="stat-label">总订单数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background-color: #67c23a">
            <el-icon :size="28"><Goods /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ productionData.inProgressPlans || 0 }}</div>
            <div class="stat-label">生产中</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background-color: #e6a23c">
            <el-icon :size="28"><Box /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ (inventoryData.rawMaterialTotalQuantity || 0) + (inventoryData.finishedProductTotalQuantity || 0) }}</div>
            <div class="stat-label">库存总量</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background-color: #f56c6c">
            <el-icon :size="28"><TrendCharts /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">¥{{ formatNumber(salesData.monthlyAmount) }}</div>
            <div class="stat-label">本月销售额</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useUserStore } from '../store/user'
import { getProductionOverview, getSalesOverview, getInventoryOverview } from '../api/statistics'

const userStore = useUserStore()
const loading = ref(false)

const productionData = ref({})
const salesData = ref({})
const inventoryData = ref({})

function formatNumber(num) {
  if (!num && num !== 0) return '0'
  return Number(num).toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
}

async function loadDashboardData() {
  loading.value = true
  try {
    const [prodRes, salesRes, invRes] = await Promise.allSettled([
      getProductionOverview(),
      getSalesOverview(),
      getInventoryOverview()
    ])

    if (prodRes.status === 'fulfilled' && prodRes.value?.code === 200) {
      productionData.value = prodRes.value.data || {}
    }
    if (salesRes.status === 'fulfilled' && salesRes.value?.code === 200) {
      salesData.value = salesRes.value.data || {}
    }
    if (invRes.status === 'fulfilled' && invRes.value?.code === 200) {
      inventoryData.value = invRes.value.data || {}
    }
  } catch (error) {
    console.error('加载仪表盘数据失败:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDashboardData()
})
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
}

.welcome-section {
  margin-bottom: 24px;
}

.welcome-section h2 {
  margin: 0 0 8px;
  font-size: 22px;
  color: #333;
}

.welcome-desc {
  margin: 0;
  color: #999;
  font-size: 14px;
}

.stat-row {
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
}

.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  padding: 20px;
  width: 100%;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.stat-info {
  margin-left: 16px;
}

.stat-number {
  font-size: 24px;
  font-weight: 700;
  color: #333;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #999;
  margin-top: 4px;
}
</style>
