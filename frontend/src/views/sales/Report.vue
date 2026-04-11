<template>
  <div class="sales-report-container">
    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm" class="filter-form">
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="filterForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 300px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchAllData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="16" class="overview-row">
      <el-col :span="6">
        <el-card class="overview-card" shadow="hover">
          <div class="overview-item">
            <div class="overview-label">总销售额</div>
            <div class="overview-value amount">¥{{ overview.totalAmount?.toFixed(2) || '0.00' }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="overview-card" shadow="hover">
          <div class="overview-item">
            <div class="overview-label">总订单数</div>
            <div class="overview-value orders">{{ overview.totalOrders || 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="overview-card" shadow="hover">
          <div class="overview-item">
            <div class="overview-label">平均客单价</div>
            <div class="overview-value avg">¥{{ overview.avgOrderAmount?.toFixed(2) || '0.00' }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="overview-card" shadow="hover">
          <div class="overview-item">
            <div class="overview-label">客户数</div>
            <div class="overview-value customers">{{ overview.customerCount || 0 }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="chart-row">
      <el-col :span="24">
        <el-card class="chart-card">
          <template #header>
            <span>销售趋势</span>
          </template>
          <div ref="trendChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="chart-row">
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header>
            <span>产品销量排行 (Top 10)</span>
          </template>
          <div ref="rankingChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header>
            <span>类别销售分布</span>
          </template>
          <div ref="categoryChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import {
  getSalesOverview,
  getSalesTrend,
  getProductRanking,
  getCategoryDistribution
} from '../../api/sales'

const filterForm = reactive({
  dateRange: null
})

const overview = ref({})
const trendChartRef = ref(null)
const rankingChartRef = ref(null)
const categoryChartRef = ref(null)

let trendChart = null
let rankingChart = null
let categoryChart = null

onMounted(() => {
  fetchAllData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (trendChart) {
    trendChart.dispose()
    trendChart = null
  }
  if (rankingChart) {
    rankingChart.dispose()
    rankingChart = null
  }
  if (categoryChart) {
    categoryChart.dispose()
    categoryChart = null
  }
})

function handleResize() {
  trendChart?.resize()
  rankingChart?.resize()
  categoryChart?.resize()
}

function getParams() {
  const params = {}
  if (filterForm.dateRange && filterForm.dateRange.length === 2) {
    params.startDate = filterForm.dateRange[0]
    params.endDate = filterForm.dateRange[1]
  }
  return params
}

async function fetchAllData() {
  await fetchOverview()
  await fetchTrend()
  await fetchRanking()
  await fetchCategory()
}

async function fetchOverview() {
  try {
    const res = await getSalesOverview()
    overview.value = res.data || res
  } catch (error) {
    ElMessage.error('获取销售概览失败')
  }
}

async function fetchTrend() {
  try {
    const res = await getSalesTrend(getParams())
    const data = res.data || res || []
    await nextTick()
    renderTrendChart(data)
  } catch (error) {
    ElMessage.error('获取销售趋势失败')
  }
}

async function fetchRanking() {
  try {
    const params = { ...getParams(), limit: 10 }
    const res = await getProductRanking(params)
    const data = res.data || res || []
    await nextTick()
    renderRankingChart(data)
  } catch (error) {
    ElMessage.error('获取产品排行失败')
  }
}

async function fetchCategory() {
  try {
    const res = await getCategoryDistribution(getParams())
    const data = res.data || res || []
    await nextTick()
    renderCategoryChart(data)
  } catch (error) {
    ElMessage.error('获取类别分布失败')
  }
}

function renderTrendChart(data) {
  if (!trendChartRef.value) return
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }
  const dates = data.map(item => item.date)
  const amounts = data.map(item => item.amount)
  trendChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: '{b}<br/>销售额: ¥{c}'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        formatter: '¥{value}'
      }
    },
    series: [
      {
        name: '销售额',
        type: 'line',
        data: amounts,
        smooth: true,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
          ])
        },
        lineStyle: {
          color: '#409eff',
          width: 2
        },
        itemStyle: {
          color: '#409eff'
        }
      }
    ]
  })
}

function renderRankingChart(data) {
  if (!rankingChartRef.value) return
  if (!rankingChart) {
    rankingChart = echarts.init(rankingChartRef.value)
  }
  const names = data.map(item => item.productName)
  const quantities = data.map(item => item.quantity)
  rankingChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'value'
    },
    yAxis: {
      type: 'category',
      data: names.reverse(),
      inverse: false
    },
    series: [
      {
        name: '销量',
        type: 'bar',
        data: quantities.reverse(),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#409eff' },
            { offset: 1, color: '#67c23a' }
          ]),
          borderRadius: [0, 4, 4, 0]
        },
        barWidth: '60%'
      }
    ]
  })
}

function renderCategoryChart(data) {
  if (!categoryChartRef.value) return
  if (!categoryChart) {
    categoryChart = echarts.init(categoryChartRef.value)
  }
  const pieData = data.map(item => ({
    name: item.category,
    value: item.amount
  }))
  categoryChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{b}: ¥{c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        name: '类别销售',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['55%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 6,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: true,
          formatter: '{b}: {d}%'
        },
        data: pieData
      }
    ]
  })
}

function handleReset() {
  filterForm.dateRange = null
  fetchAllData()
}
</script>

<style scoped>
.sales-report-container {
  padding: 20px;
}

.filter-card {
  margin-bottom: 16px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.overview-row {
  margin-bottom: 16px;
}

.overview-card {
  text-align: center;
}

.overview-item {
  padding: 10px 0;
}

.overview-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.overview-value {
  font-size: 28px;
  font-weight: 700;
}

.overview-value.amount {
  color: #e6a23c;
}

.overview-value.orders {
  color: #409eff;
}

.overview-value.avg {
  color: #67c23a;
}

.overview-value.customers {
  color: #f56c6c;
}

.chart-row {
  margin-bottom: 16px;
}

.chart-card {
  height: 100%;
}

.chart-container {
  height: 350px;
  width: 100%;
}
</style>
