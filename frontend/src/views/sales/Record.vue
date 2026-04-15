<template>
  <div class="sales-record-container">
    <div class="page-header">
      <el-tag type="primary" effect="plain" round>订单自动归档</el-tag>
    </div>

    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="客户、订单号、商品名称"
            clearable
            class="keyword-input"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="完成日期">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            class="date-range"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span>销售记录列表</span>
          <span class="table-total">共 {{ pagination.total }} 条</span>
        </div>
      </template>

      <el-table :data="recordList" v-loading="loading" border stripe class="record-table">
        <el-table-column prop="customerName" label="客户名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="orderNo" label="订单编号" min-width="170" show-overflow-tooltip />
        <el-table-column label="商品概览" min-width="220">
          <template #default="{ row }">
            <div class="product-summary">
              <div class="product-name">{{ getProductPreview(row) }}</div>
              <div class="product-meta">
                <span>{{ row.productCount || 0 }} 款商品</span>
                <span>共 {{ row.totalQuantity || 0 }} 件</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="总金额" width="130" align="right">
          <template #default="{ row }">
            <span class="amount-text">{{ formatAmount(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="下单日期" width="170" align="center">
          <template #default="{ row }">
            {{ formatDateTime(row.orderDate) || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="发货日期" width="170" align="center">
          <template #default="{ row }">
            {{ formatDateTime(row.shipDate) || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="完成日期" width="170" align="center">
          <template #default="{ row }">
            {{ formatDateTime(row.completeDate) || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createByName" label="创建人" width="110" align="center" />
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">查看</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <div class="table-empty">
            <el-empty description="暂无销售记录" :image-size="80">
              <template #description>
                <span class="empty-desc">订单完成后会自动生成销售记录</span>
              </template>
            </el-empty>
          </div>
        </template>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchRecordList"
          @current-change="fetchRecordList"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="detailVisible"
      title="销售记录明细"
      width="880px"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <template v-if="detailRecord">
          <div class="detail-block detail-summary">
            <div class="summary-row">
              <div class="summary-item wide">
                <span class="label">客户名称</span>
                <span class="value strong">{{ detailRecord.customerName || '--' }}</span>
              </div>
              <div class="summary-item">
                <span class="label">订单编号</span>
                <span class="value">{{ detailRecord.orderNo || '--' }}</span>
              </div>
              <div class="summary-item">
                <span class="label">创建人</span>
                <span class="value">{{ detailRecord.createByName || '--' }}</span>
              </div>
            </div>
            <div class="summary-row">
              <div class="summary-item">
                <span class="label">商品款数</span>
                <span class="value">{{ detailRecord.productCount || 0 }} 款</span>
              </div>
              <div class="summary-item">
                <span class="label">商品总数</span>
                <span class="value">{{ detailRecord.totalQuantity || 0 }}</span>
              </div>
              <div class="summary-item">
                <span class="label">订单金额</span>
                <span class="value amount-text">{{ formatAmount(detailRecord.totalAmount) }}</span>
              </div>
            </div>
          </div>

          <div class="detail-block">
            <div class="block-title">订单时间</div>
            <div class="summary-row">
              <div class="summary-item">
                <span class="label">下单日期</span>
                <span class="value">{{ formatDateTime(detailRecord.orderDate) || '--' }}</span>
              </div>
              <div class="summary-item">
                <span class="label">发货日期</span>
                <span class="value">{{ formatDateTime(detailRecord.shipDate) || '--' }}</span>
              </div>
              <div class="summary-item">
                <span class="label">完成日期</span>
                <span class="value">{{ formatDateTime(detailRecord.completeDate) || '--' }}</span>
              </div>
            </div>
          </div>

          <div class="detail-block">
            <div class="block-title">商品明细</div>
            <el-table :data="detailItems" border stripe>
              <el-table-column type="index" label="#" width="60" align="center" />
              <el-table-column prop="productName" label="商品名称" min-width="180" show-overflow-tooltip />
              <el-table-column prop="productCode" label="商品编码" width="140" />
              <el-table-column prop="color" label="颜色" width="120" align="center">
                <template #default="{ row }">
                  {{ row.color || '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="size" label="尺码" width="120" align="center">
                <template #default="{ row }">
                  {{ row.size || '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="quantity" label="数量" width="90" align="center" />
              <el-table-column label="单价" width="110" align="right">
                <template #default="{ row }">
                  {{ formatAmount(row.unitPrice) }}
                </template>
              </el-table-column>
              <el-table-column label="小计" width="120" align="right">
                <template #default="{ row }">
                  <span class="amount-text">{{ formatAmount(row.amount) }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div v-if="detailRecord.remark" class="detail-block">
            <div class="block-title">备注</div>
            <div class="remark-text">{{ detailRecord.remark }}</div>
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSalesRecordDetail, getSalesRecordList } from '../../api/sales'

const loading = ref(false)
const detailLoading = ref(false)
const recordList = ref([])
const detailVisible = ref(false)
const detailRecord = ref(null)

const searchForm = reactive({
  keyword: '',
  dateRange: null
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

const detailItems = computed(() => detailRecord.value?.items || [])

onMounted(() => {
  fetchRecordList()
})

async function fetchRecordList() {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.pageSize,
      keyword: searchForm.keyword || undefined
    }
    if (searchForm.dateRange?.length === 2) {
      params.startDate = searchForm.dateRange[0]
      params.endDate = searchForm.dateRange[1]
    }
    const res = await getSalesRecordList(params)
    const data = res.data || res
    recordList.value = data.list || []
    pagination.total = data.total || 0
  } catch (error) {
    ElMessage.error('获取销售记录失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchRecordList()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.dateRange = null
  pagination.page = 1
  fetchRecordList()
}

async function handleView(row) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getSalesRecordDetail(row.id)
    detailRecord.value = res.data || res
  } catch (error) {
    detailVisible.value = false
    ElMessage.error('获取销售记录详情失败')
  } finally {
    detailLoading.value = false
  }
}

function getProductPreview(row) {
  const names = (row.items || [])
    .map(item => item.productName)
    .filter(Boolean)
  if (!names.length) {
    return row.productName || '--'
  }
  if (names.length === 1) {
    return names[0]
  }
  return `${names[0]} 等 ${names.length} 项`
}

function formatAmount(value) {
  const amount = Number(value || 0)
  return `¥${amount.toFixed(2)}`
}

function formatDateTime(value) {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  const pad = num => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
</script>

<style scoped>
.sales-record-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.search-card {
  margin-bottom: 16px;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.keyword-input {
  width: 260px;
}

.date-range {
  width: 280px;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.table-total {
  font-size: 13px;
  color: #909399;
}

.record-table :deep(.el-table__cell) {
  padding: 12px 0;
}

.product-summary {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.product-name {
  color: #303133;
  line-height: 1.5;
}

.product-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #909399;
}

.amount-text {
  color: #e6a23c;
  font-weight: 600;
}

.table-empty {
  padding: 24px 0 12px;
}

.empty-desc {
  font-size: 12px;
  color: #909399;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-block {
  margin-bottom: 16px;
}

.detail-summary {
  padding: 16px;
  background: #f8fafc;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.block-title {
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.summary-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.summary-row:last-child {
  margin-bottom: 0;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}

.summary-item.wide {
  grid-column: span 1;
}

.label {
  font-size: 12px;
  color: #909399;
}

.value {
  font-size: 14px;
  color: #303133;
  word-break: break-all;
}

.value.strong {
  font-size: 16px;
  font-weight: 600;
}

.remark-text {
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafafa;
  color: #606266;
  line-height: 1.8;
  white-space: pre-wrap;
}

@media (max-width: 900px) {
  .page-header,
  .table-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .summary-row {
    grid-template-columns: 1fr;
  }

  .keyword-input,
  .date-range {
    width: 100%;
  }
}
</style>
