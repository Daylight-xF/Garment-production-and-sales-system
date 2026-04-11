<template>
  <div class="statistics-container" v-loading="loading">
    <el-row :gutter="20" class="overview-row">
      <el-col :span="6">
        <el-card class="overview-card" shadow="hover">
          <div class="card-content">
            <div class="card-info">
              <div class="card-label">计划完成率</div>
              <div class="card-value blue">{{ productionOverview.planCompletionRate || 0 }}%</div>
            </div>
            <el-icon class="card-icon blue"><DocumentChecked /></el-icon>
          </div>
          <div class="card-footer">已完成 {{ productionOverview.completedPlans || 0 }} / {{ productionOverview.totalPlans || 0 }} 个计划</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="overview-card" shadow="hover">
          <div class="card-content">
            <div class="card-info">
              <div class="card-label">销售总额</div>
              <div class="card-value green">¥{{ formatNumber(salesOverview.totalAmount) }}</div>
            </div>
            <el-icon class="card-icon green"><TrendCharts /></el-icon>
          </div>
          <div class="card-footer">本月 ¥{{ formatNumber(salesOverview.monthlyAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="overview-card" shadow="hover">
          <div class="card-content">
            <div class="card-info">
              <div class="card-label">库存总量</div>
              <div class="card-value orange">{{ (inventoryOverview.rawMaterialTotalQuantity || 0) + (inventoryOverview.finishedProductTotalQuantity || 0) }}</div>
            </div>
            <el-icon class="card-icon orange"><Box /></el-icon>
          </div>
          <div class="card-footer">原材料 {{ inventoryOverview.rawMaterialTotalQuantity || 0 }} / 成品 {{ inventoryOverview.finishedProductTotalQuantity || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="overview-card" shadow="hover">
          <div class="card-content">
            <div class="card-info">
              <div class="card-label">库存预警</div>
              <div class="card-value red">{{ inventoryOverview.alertCount || 0 }}</div>
            </div>
            <el-icon class="card-icon red"><Warning /></el-icon>
          </div>
          <div class="card-footer">待处理 {{ alertStats.pendingCount || 0 }} / 已处理 {{ alertStats.handledCount || 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="chart-row">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><span class="chart-title">月度销售趋势</span></template>
          <div ref="salesTrendChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><span class="chart-title">计划状态分布</span></template>
          <div ref="planStatusChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="chart-row">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><span class="chart-title">热销产品排行</span></template>
          <div ref="topProductsChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="chart-title-with-tabs">
              <span>库存分类分布</span>
              <el-radio-group v-model="inventoryTab" size="small" @change="renderInventoryDistribution">
                <el-radio-button label="raw">原材料</el-radio-button>
                <el-radio-button label="finished">成品</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="inventoryDistChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="chart-row">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header><span class="chart-title">产品生产进度</span></template>
          <el-table :data="productProgress" stripe style="width: 100%">
            <el-table-column prop="productName" label="产品名称" min-width="150" />
            <el-table-column prop="plannedQuantity" label="计划数量" width="120" align="center" />
            <el-table-column prop="completedQuantity" label="已完成数量" width="120" align="center" />
            <el-table-column label="完成进度" width="300">
              <template #default="{ row }">
                <el-progress
                  :percentage="row.progress || 0"
                  :color="getProgressColor(row.progress || 0)"
                  :stroke-width="18"
                  :text-inside="true"
                />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { DocumentChecked, TrendCharts, Box, Warning } from '@element-plus/icons-vue'
import {
  getProductionOverview,
  getPlanStatusDistribution,
  getProductProgress,
  getSalesOverview,
  getMonthlySalesTrend,
  getTopProducts,
  getInventoryOverview,
  getRawMaterialDistribution,
  getFinishedProductDistribution,
  getAlertStats
} from '../api/statistics'

const loading = ref(false)
const inventoryTab = ref('raw')

const productionOverview = ref({})
const salesOverview = ref({})
const inventoryOverview = ref({})
const alertStats = ref({})
const productProgress = ref([])
const monthlySalesData = ref([])
const topProductsData = ref([])
const planStatusData = ref([])
const rawMaterialDist = ref([])
const finishedProductDist = ref([])

const salesTrendChartRef = ref(null)
const planStatusChartRef = ref(null)
const topProductsChartRef = ref(null)
const inventoryDistChartRef = ref(null)

let salesTrendChart = null
let planStatusChart = null
let topProductsChart = null
let inventoryDistChart = null

const statusMap = {
  PENDING: '待审批',
  APPROVED: '已审批',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

const statusColors = {
  PENDING: '#E6A23C',
  APPROVED: '#67C23A',
  IN_PROGRESS: '#409EFF',
  COMPLETED: '#67C23A',
  CANCELLED: '#909399'
}

function formatNumber(num) {
  if (!num && num !== 0) return '0'
  return Number(num).toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
}

function getProgressColor(progress) {
  if (progress >= 80) return '#67C23A'
  if (progress >= 50) return '#409EFF'
  if (progress >= 30) return '#E6A23C'
  return '#F56C6C'
}

function initCharts() {
  if (salesTrendChartRef.value) {
    salesTrendChart = echarts.init(salesTrendChartRef.value)
  }
  if (planStatusChartRef.value) {
    planStatusChart = echarts.init(planStatusChartRef.value)
  }
  if (topProductsChartRef.value) {
    topProductsChart = echarts.init(topProductsChartRef.value)
  }
  if (inventoryDistChartRef.value) {
    inventoryDistChart = echarts.init(inventoryDistChartRef.value)
  }
}

function renderSalesTrend() {
  if (!salesTrendChart) return
  const months = monthlySalesData.value.map(item => item.month)
  const amounts = monthlySalesData.value.map(item => item.amount || 0)
  const orders = monthlySalesData.value.map(item => item.orderCount || 0)
  salesTrendChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { data: ['销售额', '订单数'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: months, axisLabel: { rotate: 30 } },
    yAxis: [
      { type: 'value', name: '销售额(元)', position: 'left' },
      { type: 'value', name: '订单数', position: 'right' }
    ],
    series: [
      {
        name: '销售额',
        type: 'line',
        smooth: true,
        data: amounts,
        itemStyle: { color: '#409EFF' },
        areaStyle: { color: 'rgba(64,158,255,0.15)' }
      },
      {
        name: '订单数',
        type: 'bar',
        yAxisIndex: 1,
        data: orders,
        itemStyle: { color: '#67C23A' }
      }
    ]
  })
}

function renderPlanStatus() {
  if (!planStatusChart) return
  const data = planStatusData.value.map(item => ({
    name: statusMap[item.status] || item.status,
    value: item.count,
    itemStyle: { color: statusColors[item.status] || '#909399' }
  }))
  planStatusChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', left: 'left' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['60%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}: {c}' },
      data
    }]
  })
}

function renderTopProducts() {
  if (!topProductsChart) return
  const names = topProductsData.value.map(item => item.productName)
  const quantities = topProductsData.value.map(item => item.quantity || 0)
  topProductsChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', name: '销量' },
    yAxis: { type: 'category', data: names.reverse(), axisLabel: { width: 80, overflow: 'truncate' } },
    series: [{
      type: 'bar',
      data: quantities.reverse(),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#409EFF' },
          { offset: 1, color: '#67C23A' }
        ]),
        borderRadius: [0, 4, 4, 0]
      },
      barWidth: 20
    }]
  })
}

function renderInventoryDistribution() {
  if (!inventoryDistChart) return
  const data = inventoryTab.value === 'raw' ? rawMaterialDist.value : finishedProductDist.value
  const chartData = data.map(item => ({
    name: item.category,
    value: item.quantity || 0
  }))
  inventoryDistChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', left: 'left' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['60%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}: {c}' },
      data: chartData
    }]
  })
}

function handleResize() {
  salesTrendChart?.resize()
  planStatusChart?.resize()
  topProductsChart?.resize()
  inventoryDistChart?.resize()
}

async function loadData() {
  loading.value = true
  try {
    const [
      prodRes,
      planStatusRes,
      progressRes,
      salesRes,
      monthlyRes,
      topRes,
      invRes,
      rawDistRes,
      finishedDistRes,
      alertRes
    ] = await Promise.allSettled([
      getProductionOverview(),
      getPlanStatusDistribution(),
      getProductProgress(),
      getSalesOverview(),
      getMonthlySalesTrend(12),
      getTopProducts(10),
      getInventoryOverview(),
      getRawMaterialDistribution(),
      getFinishedProductDistribution(),
      getAlertStats()
    ])

    if (prodRes.status === 'fulfilled' && prodRes.value?.code === 200) {
      productionOverview.value = prodRes.value.data || {}
    }
    if (planStatusRes.status === 'fulfilled' && planStatusRes.value?.code === 200) {
      planStatusData.value = planStatusRes.value.data || []
    }
    if (progressRes.status === 'fulfilled' && progressRes.value?.code === 200) {
      productProgress.value = progressRes.value.data || []
    }
    if (salesRes.status === 'fulfilled' && salesRes.value?.code === 200) {
      salesOverview.value = salesRes.value.data || {}
    }
    if (monthlyRes.status === 'fulfilled' && monthlyRes.value?.code === 200) {
      monthlySalesData.value = monthlyRes.value.data || []
    }
    if (topRes.status === 'fulfilled' && topRes.value?.code === 200) {
      topProductsData.value = topRes.value.data || []
    }
    if (invRes.status === 'fulfilled' && invRes.value?.code === 200) {
      inventoryOverview.value = invRes.value.data || {}
    }
    if (rawDistRes.status === 'fulfilled' && rawDistRes.value?.code === 200) {
      rawMaterialDist.value = rawDistRes.value.data || []
    }
    if (finishedDistRes.status === 'fulfilled' && finishedDistRes.value?.code === 200) {
      finishedProductDist.value = finishedDistRes.value.data || []
    }
    if (alertRes.status === 'fulfilled' && alertRes.value?.code === 200) {
      alertStats.value = alertRes.value.data || {}
    }

    await nextTick()
    renderSalesTrend()
    renderPlanStatus()
    renderTopProducts()
    renderInventoryDistribution()
  } catch (error) {
    console.error('加载统计数据失败:', error)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await nextTick()
  initCharts()
  loadData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  salesTrendChart?.dispose()
  planStatusChart?.dispose()
  topProductsChart?.dispose()
  inventoryDistChart?.dispose()
})
</script>

<style scoped>
.statistics-container {
  padding: 20px;
}

.overview-row {
  margin-bottom: 20px;
}

.overview-card {
  height: 100%;
}

.card-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.card-value {
  font-size: 28px;
  font-weight: 700;
}

.card-value.blue { color: #409EFF; }
.card-value.green { color: #67C23A; }
.card-value.orange { color: #E6A23C; }
.card-value.red { color: #F56C6C; }

.card-icon {
  font-size: 48px;
  opacity: 0.2;
}

.card-icon.blue { color: #409EFF; }
.card-icon.green { color: #67C23A; }
.card-icon.orange { color: #E6A23C; }
.card-icon.red { color: #F56C6C; }

.card-footer {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #EBEEF5;
  font-size: 13px;
  color: #909399;
}

.chart-row {
  margin-bottom: 20px;
}

.chart-title {
  font-size: 16px;
  font-weight: 600;
}

.chart-title-with-tabs {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-container {
  height: 350px;
  width: 100%;
}
</style>
