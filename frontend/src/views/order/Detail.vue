<template>
  <div class="order-detail-container">
    <div class="page-header">
      <div class="header-left">
        <el-button class="back-btn" @click="goBack" round>
          <el-icon><ArrowLeft /></el-icon>
          返回列表
        </el-button>
        <div class="header-title-group">
          <h2 class="page-title">订单详情</h2>
          <span v-if="order" class="order-no-subtitle">{{ order.orderNo }}</span>
        </div>
      </div>
      <div v-if="order" class="header-right">
        <div class="status-badge" :class="'status-' + order.status.toLowerCase()">
          <span class="status-dot"></span>
          {{ statusLabel(order.status) }}
        </div>
      </div>
    </div>

    <div v-if="order" class="detail-body">
      <section class="section-card info-section">
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>基本信息</span>
        </div>
        <div class="info-grid">
          <div class="info-item">
            <span class="info-label">订单编号</span>
            <span class="info-value mono">{{ order.orderNo }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">客户名称</span>
            <span class="info-value highlight">{{ order.customerName }}</span>
          </div>
          <div class="info-item amount-item">
            <span class="info-label">总金额</span>
            <span class="info-value amount">¥{{ order.totalAmount?.toFixed(2) }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">状态</span>
            <span class="info-value"><el-tag :type="statusTagType(order.status)" effect="light" round>{{ statusLabel(order.status) }}</el-tag></span>
          </div>
          <div class="info-item">
            <span class="info-label">创建人</span>
            <span class="info-value">{{ order.createByName || '-' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">创建时间</span>
            <span class="info-value time">{{ formatDate(order.createTime) }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">审批人</span>
            <span class="info-value">{{ order.approveByName || '-' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">审批时间</span>
            <span class="info-value time">{{ formatDate(order.approveTime) || '-' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">审批意见</span>
            <span class="info-value remark">{{ order.approveRemark || '-' }}</span>
          </div>
          <div class="info-item full-width" v-if="order.remark">
            <span class="info-label">备注</span>
            <span class="info-value remark-text">{{ order.remark }}</span>
          </div>
        </div>

        <div v-if="showActions" class="action-bar">
          <el-button
            v-if="order.status === 'PENDING_APPROVAL' && userStore.hasPermission('ORDER_APPROVE')"
            type="warning"
            round
            @click="approveDialogVisible = true"
          ><el-icon><Select /></el-icon>审核订单</el-button>
          <el-button
            v-if="order.status === 'APPROVED' && userStore.hasPermission('ORDER_UPDATE')"
            type="primary"
            round
            @click="handleShip"
          ><el-icon><Van /></el-icon>确认发货</el-button>
          <el-button
            v-if="order.status === 'SHIPPED' && userStore.hasPermission('ORDER_UPDATE')"
            type="success"
            round
            @click="handleComplete"
          ><el-icon><CircleCheck /></el-icon>完成订单</el-button>
          <el-button
            v-if="(order.status === 'PENDING_APPROVAL' || order.status === 'APPROVED') && userStore.hasPermission('ORDER_CANCEL')"
            type="danger"
            plain
            round
            @click="handleCancel"
          ><el-icon><Close /></el-icon>取消订单</el-button>
        </div>
      </section>

      <section class="section-card items-section">
        <div class="section-header">
          <el-icon><ShoppingBag /></el-icon>
          <span>订单明细</span>
          <span class="item-count-badge" v-if="order.items && order.items.length">
            {{ order.items.length }} 款 · 共 {{ totalQty }} 件
          </span>
        </div>
        <div v-if="order.items && order.items.length > 0" class="items-table-wrapper">
          <table class="items-table">
            <thead>
              <tr>
                <th class="th-left" style="width: 32%">产品名称</th>
                <th class="th-center" style="width: 12%">颜色</th>
                <th class="th-center" style="width: 10%">尺码</th>
                <th class="th-center" style="width: 14%">数量</th>
                <th class="th-right" style="width: 16%">单价</th>
                <th class="th-right" style="width: 16%">金额</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, idx) in order.items" :key="idx">
                <td class="product-cell">
                  <span class="product-name">{{ item.productName }}{{ item.productCode ? '-' + item.productCode : '' }}</span>
                </td>
                <td class="center-cell">
                  <span v-if="item.color" class="color-chip">{{ item.color }}</span>
                  <span v-else class="empty-val">-</span>
                </td>
                <td class="center-cell">
                  <span v-if="item.size" class="size-chip">{{ item.size }}</span>
                  <span v-else class="empty-val">-</span>
                </td>
                <td class="center-cell qty-cell">
                  <span class="qty-num">{{ item.quantity }}</span>
                  <span class="unit-suffix">{{ item.unit || '件' }}</span>
                </td>
                <td class="right-cell price-cell">¥{{ item.unitPrice?.toFixed(2) }}</td>
                <td class="right-cell amount-cell">¥{{ item.amount?.toFixed(2) }}</td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <td colspan="4" class="total-label-cell">合计</td>
                <td class="right-cell"></td>
                <td class="right-cell total-amount-cell">¥{{ order.totalAmount?.toFixed(2) }}</td>
              </tr>
            </tfoot>
          </table>
        </div>
        <el-empty v-else description="暂无商品明细" :image-size="80" />
      </section>

      <section class="section-card timeline-section">
        <div class="section-header">
          <el-icon><Clock /></el-icon>
          <span>操作日志</span>
        </div>
        <div v-if="order.logs && order.logs.length > 0" class="timeline-wrapper">
          <div v-for="(log, idx) in order.logs" :key="log.id || idx" class="timeline-item">
            <div class="timeline-node" :class="'node-' + (idx === order.logs.length - 1 ? 'latest' : 'past')">
              <div class="node-inner"></div>
            </div>
            <div class="timeline-line" v-if="idx < order.logs.length - 1"></div>
            <div class="timeline-content">
              <div class="log-header">
                <span class="log-action" :class="'action-' + log.action?.toLowerCase()">{{ actionLabel(log.action) }}</span>
                <span class="log-time">{{ formatDate(log.createTime) }}</span>
              </div>
              <div class="log-body">
                <span class="log-operator">{{ log.operatorName }}</span>
                <span v-if="log.remark" class="log-remark">{{ log.remark }}</span>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="empty-timeline">
          <el-empty description="暂无操作日志" :image-size="60" />
        </div>
      </section>
    </div>

    <div v-else class="skeleton-area">
      <el-skeleton :rows="10" animated />
    </div>

    <el-dialog v-model="approveDialogVisible" title="订单审核" width="480px" class="approve-dialog" :close-on-click-modal="false">
      <div class="dialog-content">
        <div class="approve-info">
          <el-icon class="warn-icon"><WarningFilled /></el-icon>
          <span>请确认是否通过该订单的审核</span>
        </div>
        <el-form :model="approveForm" label-position="top">
          <el-form-item label="审核结果">
            <el-radio-group v-model="approveForm.approved" size="large">
              <el-radio-button :label="true">
                <el-icon><CircleCheck /></el-icon> 通过
              </el-radio-button>
              <el-radio-button :label="false">
                <el-icon><CircleClose /></el-icon> 拒绝
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="审核意见">
            <el-input v-model="approveForm.remark" type="textarea" :rows="3" placeholder="请输入审核意见（选填）" maxlength="200" show-word-limit />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="approveDialogVisible = false" round>取消</el-button>
          <el-button type="primary" @click="submitApprove" round>{{ approveForm.approved ? '确认通过' : '确认拒绝' }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Document, ShoppingBag, Clock, Select, Van, CircleCheck, Close, WarningFilled, CircleClose as CircleCloseIcon } from '@element-plus/icons-vue'
import { getOrderDetail, approveOrder, rejectOrder, cancelOrder, shipOrder, completeOrder } from '../../api/order'
import { useUserStore } from '../../store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const order = ref(null)
const approveDialogVisible = ref(false)
const approveForm = ref({ approved: true, remark: '' })

const statusMap = {
  PENDING_APPROVAL: { label: '待审核', type: 'warning' },
  APPROVED: { label: '已审核', type: 'success' },
  IN_PRODUCTION: { label: '生产中', type: 'primary' },
  SHIPPED: { label: '已发货', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' }
}

const actionMap = {
  CREATE: '创建了订单',
  APPROVE: '审核通过了订单',
  REJECT: '拒绝了订单',
  SHIP: '将订单发货',
  COMPLETE: '完成了订单',
  CANCEL: '取消了订单'
}

function statusLabel(status) {
  return statusMap[status]?.label || status
}

function statusTagType(status) {
  return statusMap[status]?.type || 'info'
}

function actionLabel(action) {
  return actionMap[action] || action
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const totalQty = computed(() => {
  if (!order.value?.items) return 0
  return order.value.items.reduce((sum, item) => sum + (item.quantity || 0), 0)
})

const showActions = computed(() => {
  if (!order.value) return false
  const s = order.value.status
  return s === 'PENDING_APPROVAL' || s === 'APPROVED' || s === 'SHIPPED'
})

async function loadDetail() {
  try {
    const res = await getOrderDetail(route.params.id)
    order.value = res.data || res
  } catch (error) {
    ElMessage.error('获取订单详情失败')
    console.error(error)
  }
}

async function submitApprove() {
  try {
    if (approveForm.value.approved) {
      await approveOrder(order.value.id, {
        approved: true,
        remark: approveForm.value.remark
      })
      ElMessage.success('审核通过')
    } else {
      await rejectOrder(order.value.id, {
        approved: false,
        remark: approveForm.value.remark
      })
      ElMessage.success('已拒绝')
    }
    approveDialogVisible.value = false
    loadDetail()
  } catch (error) {
    console.error(error)
  }
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm('确定取消该订单吗？此操作不可恢复。', '提示', {
      confirmButtonText: '确定取消',
      cancelButtonText: '再想想',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    })
    await cancelOrder(order.value.id)
    ElMessage.success('订单已取消')
    loadDetail()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.response?.data?.message || '操作失败')
    }
  }
}

async function handleShip() {
  try {
    await ElMessageBox.confirm('确定发货吗？', '提示', {
      confirmButtonText: '确定发货',
      cancelButtonText: '取消',
      type: 'success'
    })
    await shipOrder(order.value.id)
    ElMessage.success('已发货')
    loadDetail()
  } catch {
  }
}

async function handleComplete() {
  try {
    await ElMessageBox.confirm('确定完成该订单吗？', '提示', {
      confirmButtonText: '确定完成',
      cancelButtonText: '取消',
      type: 'success'
    })
    await completeOrder(order.value.id)
    ElMessage.success('订单已完成')
    loadDetail()
  } catch {
  }
}

function goBack() {
  router.push('/order/list')
}

onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
.order-detail-container {
  padding: 24px;
  max-width: 1100px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 18px 22px;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  border-radius: 14px;
  color: #fff;
  box-shadow: 0 8px 32px rgba(15, 52, 96, 0.3);
  position: relative;
  overflow: hidden;
}
.page-header::before {
  content: '';
  position: absolute;
  top: -40px;
  right: -40px;
  width: 140px;
  height: 140px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.04);
}
.page-header::after {
  content: '';
  position: absolute;
  bottom: -30px;
  left: 25%;
  width: 90px;
  height: 90px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.03);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  z-index: 1;
}

.back-btn {
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: #fff;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 550;
  letter-spacing: 0.5px;
  border-radius: 20px;
  transition: all 0.25s ease;
}
.back-btn:hover {
  background: rgba(255, 255, 255, 0.25);
  border-color: rgba(255, 255, 255, 0.4);
  transform: translateX(-2px);
}

.header-title-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.order-no-subtitle {
  font-size: 12px;
  opacity: 0.55;
  letter-spacing: 0.5px;
  font-family: 'SF Mono', 'Cascadia Code', 'Consolas', monospace;
}

.header-right {
  z-index: 1;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 16px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.3px;
  animation: badgeIn 0.4s ease-out;
}
@keyframes badgeIn {
  from { opacity: 0; transform: scale(0.9); }
  to { opacity: 1; transform: scale(1); }
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  animation: pulse 2s infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}

.status-pending_approval {
  background: linear-gradient(135deg, #fdf6ec, #faecd8);
  color: #e6a23c;
  box-shadow: 0 2px 8px rgba(230, 162, 60, 0.2);
}
.status-pending_approval .status-dot { background: #e6a23c; }

.status-approved {
  background: linear-gradient(135deg, #e8f8ee, #d4edda);
  color: #67c23a;
  box-shadow: 0 2px 8px rgba(103, 194, 58, 0.2);
}
.status-approved .status-dot { background: #67c23a; }

.status-in_production {
  background: linear-gradient(135deg, #ecf5ff, #d9ecff);
  color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);
}
.status-in_production .status-dot { background: #409eff; }

.status-shipped {
  background: linear-gradient(135deg, #ecf5ff, #d9ecff);
  color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);
}
.status-shipped .status-dot { background: #409eff; }

.status-completed {
  background: linear-gradient(135deg, #e8f8ee, #d4edda);
  color: #67c23a;
  box-shadow: 0 2px 8px rgba(103, 194, 58, 0.2);
}
.status-completed .status-dot { background: #67c23a; }

.status-cancelled {
  background: linear-gradient(135deg, #f4f4f5, #ebeef5);
  color: #909399;
  box-shadow: 0 2px 8px rgba(144, 147, 153, 0.15);
}
.status-cancelled .status-dot { background: #909399; animation: none; }

.detail-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.section-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #ebeef5;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.3s ease;
}
.section-card:hover {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.07);
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 22px;
  border-bottom: 1px solid #f0f2f5;
  font-size: 15px;
  font-weight: 650;
  color: #303133;
  letter-spacing: 0.3px;
  background: linear-gradient(to right, #fafbfc, #fff);
}
.section-header .el-icon {
  color: #409eff;
  font-size: 17px;
}

.item-count-badge {
  margin-left: auto;
  font-size: 12px;
  font-weight: 500;
  color: #909399;
  background: #f4f4f5;
  padding: 3px 11px;
  border-radius: 10px;
}

.info-section {
  padding: 22px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px 28px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.info-item.full-width {
  grid-column: 1 / -1;
}

.info-label {
  font-size: 12px;
  color: #909399;
  font-weight: 550;
  text-transform: uppercase;
  letter-spacing: 0.8px;
}

.info-value {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}

.info-value.mono {
  font-family: 'SF Mono', 'Cascadia Code', 'Consolas', monospace;
  font-size: 13px;
  letter-spacing: 0.3px;
  color: #606266;
}

.info-value.highlight {
  color: #1a1a2e;
  font-weight: 650;
}

.info-value.amount {
  font-size: 19px;
  font-weight: 800;
  color: #e6a23c;
  letter-spacing: -0.3px;
}

.info-value.time {
  font-size: 13px;
  color: #606266;
  font-variant-numeric: tabular-nums;
}

.info-value.remark {
  color: #606266;
  font-size: 13px;
  line-height: 1.55;
}

.info-value.remark-text {
  background: #fafbfc;
  padding: 10px 14px;
  border-radius: 8px;
  border-left: 3px solid #dcdfe6;
  line-height: 1.6;
  font-size: 13px;
  color: #606266;
}

.action-bar {
  display: flex;
  gap: 10px;
  margin-top: 22px;
  padding-top: 20px;
  border-top: 1px dashed #ebeef5;
  flex-wrap: wrap;
}
.action-bar .el-icon {
  margin-right: 4px;
}

.items-section {
  padding: 0;
}

.items-table-wrapper {
  overflow-x: auto;
  padding: 4px 22px 18px;
}

.items-table {
  width: 100%;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
}

.items-table thead th {
  padding: 11px 14px;
  background: #f8f9fb;
  color: #606266;
  font-weight: 650;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.6px;
  border-bottom: 2px solid #e4e7ed;
  white-space: nowrap;
}
.items-table .th-left { text-align: left; }
.items-table .th-center { text-align: center; }
.items-table .th-right { text-align: right; }
.items-table thead th:first-child { border-radius: 8px 0 0 0; }
.items-table thead th:last-child { border-radius: 0 8px 0 0; }

.items-table tbody tr {
  transition: background 0.2s ease;
}
.items-table tbody tr:hover {
  background: #fafbfc;
}

.items-table tbody td {
  padding: 13px 14px;
  border-bottom: 1px solid #f0f2f5;
  color: #303133;
  vertical-align: middle;
}
.items-table tbody tr:last-child td {
  border-bottom: none;
}

.product-cell .product-name {
  font-weight: 600;
  color: #1a1a2e;
}

.center-cell {
  text-align: center;
}

.right-cell {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.color-chip {
  display: inline-block;
  padding: 2px 10px;
  background: linear-gradient(135deg, #f0f5ff, #e6f0ff);
  color: #3370ff;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 550;
  border: 1px solid #d6e4ff;
}

.size-chip {
  display: inline-block;
  padding: 2px 10px;
  background: linear-gradient(135deg, #fff7e6, #fff0cc);
  color: #b26a00;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 550;
  border: 1px solid #ffe0a0;
}

.empty-val {
  color: #c0c4cc;
}

.qty-cell {
  font-weight: 600;
}
.qty-num {
  font-size: 15px;
  color: #303133;
  font-weight: 700;
}
.unit-suffix {
  font-size: 11px;
  color: #909399;
  margin-left: 2px;
}

.price-cell {
  color: #606266;
}

.amount-cell {
  color: #e6a23c;
  font-weight: 650;
}

.items-table tfoot td {
  padding: 13px 14px;
  background: #fafbfc;
  border-top: 2px solid #e4e7ed;
  font-weight: 650;
}
.total-label-cell {
  text-align: right;
  color: #606266;
  font-size: 13px;
}
.total-amount-cell {
  color: #e6a23c !important;
  font-size: 17px;
  font-weight: 800;
}

.timeline-section {
  padding: 22px;
}

.timeline-wrapper {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding-left: 8px;
}

.timeline-item {
  display: flex;
  gap: 16px;
  position: relative;
  padding-bottom: 28px;
}

.timeline-node {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: flex-start;
  padding-top: 4px;
  flex-shrink: 0;
}

.node-inner {
  width: 13px;
  height: 13px;
  border-radius: 50%;
  border: 2.5px solid;
  background: #fff;
  transition: all 0.3s ease;
}

.node-past .node-inner {
  border-color: #c0c4cc;
  background: #c0c4cc;
}

.node-latest .node-inner {
  border-color: #409eff;
  background: #409eff;
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.15);
  animation: nodeGlow 2s infinite;
}
@keyframes nodeGlow {
  0%, 100% { box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.15); }
  50% { box-shadow: 0 0 0 7px rgba(64, 158, 255, 0.08); }
}

.timeline-line {
  position: absolute;
  left: 5.85px;
  top: 21px;
  width: 2px;
  height: calc(100% - 12px);
  background: linear-gradient(to bottom, #e4e7ed, #ebeef5);
  border-radius: 1px;
}

.timeline-content {
  flex: 1;
  min-width: 0;
  padding-top: 1px;
}

.log-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 5px;
}

.log-action {
  font-size: 13px;
  font-weight: 650;
  color: #303133;
}
.log-action.action-create { color: #409eff; }
.log-action.action-approve { color: #67c23a; }
.log-action.action-reject { color: #f56c6c; }
.log-action.action-ship { color: #e6a23c; }
.log-action.action-complete { color: #67c23a; }
.log-action.action-cancel { color: #909399; }

.log-time {
  font-size: 12px;
  color: #c0c4cc;
  font-variant-numeric: tabular-nums;
}

.log-body {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
}

.log-operator {
  font-weight: 600;
  color: #303133;
}

.log-remark {
  display: block;
  margin-top: 3px;
  color: #909399;
  font-size: 12px;
  padding: 5px 10px;
  background: #fafbfc;
  border-radius: 6px;
  border-left: 2.5px solid #dcdfe6;
}

.timeline-item:last-child {
  padding-bottom: 0;
}

.empty-timeline {
  padding: 20px 0;
}

.skeleton-area {
  background: #fff;
  border-radius: 12px;
  padding: 30px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.approve-dialog :deep(.el-dialog__header) {
  padding: 18px 22px 14px;
  border-bottom: 1px solid #f0f2f5;
}
.approve-dialog :deep(.el-dialog__title) {
  font-weight: 700;
  font-size: 16px;
  color: #303133;
}
.approve-dialog :deep(.el-dialog__body) {
  padding: 22px;
}

.dialog-content {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.approve-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: #fdf6ec;
  border-radius: 10px;
  color: #e6a23c;
  font-size: 14px;
  font-weight: 550;
}
.warn-icon {
  font-size: 20px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
