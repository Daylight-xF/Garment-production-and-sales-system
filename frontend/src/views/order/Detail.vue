<template>
  <div class="order-detail">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>订单详情</span>
          <el-button @click="goBack">返回</el-button>
        </div>
      </template>

      <div v-if="order" class="detail-content">
        <el-descriptions title="基本信息" :column="3" border>
          <el-descriptions-item label="订单编号">{{ order.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="客户名称">{{ order.customerName }}</el-descriptions-item>
          <el-descriptions-item label="总金额">¥{{ order.totalAmount?.toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(order.status)">{{ statusLabel(order.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建人">{{ order.createByName }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDate(order.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="审批人">{{ order.approveByName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批时间">{{ formatDate(order.approveTime) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批意见">{{ order.approveRemark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ order.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="action-buttons" v-if="showActions">
          <el-button
            v-if="order.status === 'PENDING_APPROVAL' && userStore.hasPermission('ORDER_APPROVE')"
            type="warning"
            @click="approveDialogVisible = true"
          >审核</el-button>
          <el-button
            v-if="order.status === 'APPROVED' && userStore.hasPermission('ORDER_UPDATE')"
            type="primary"
            @click="handleShip"
          >发货</el-button>
          <el-button
            v-if="order.status === 'SHIPPED' && userStore.hasPermission('ORDER_UPDATE')"
            type="success"
            @click="handleComplete"
          >完成</el-button>
          <el-button
            v-if="(order.status === 'PENDING_APPROVAL' || order.status === 'APPROVED') && userStore.hasPermission('ORDER_CANCEL')"
            type="danger"
            @click="handleCancel"
          >取消订单</el-button>
        </div>

        <h3 style="margin-top: 24px">订单明细</h3>
        <el-table :data="order.items || []" border stripe>
          <el-table-column label="产品名称" min-width="150">
            <template #default="{ row }">
              {{ row.productName }}{{ row.productCode ? '-' + row.productCode : '' }}
            </template>
          </el-table-column>
          <el-table-column prop="color" label="颜色" width="110" align="center">
            <template #default="{ row }">
              {{ row.color || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="size" label="尺码" width="110" align="center">
            <template #default="{ row }">
              {{ row.size || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="100" />
          <el-table-column prop="unitPrice" label="单价" width="120">
            <template #default="{ row }">¥{{ row.unitPrice?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="amount" label="金额" width="120">
            <template #default="{ row }">¥{{ row.amount?.toFixed(2) }}</template>
          </el-table-column>
        </el-table>

        <h3 style="margin-top: 24px">操作日志</h3>
        <el-timeline v-if="order.logs && order.logs.length > 0">
          <el-timeline-item
            v-for="log in order.logs"
            :key="log.id"
            :timestamp="formatDate(log.createTime)"
            placement="top"
          >
            <el-card shadow="never">
              <p><strong>{{ log.operatorName }}</strong> {{ actionLabel(log.action) }}</p>
              <p v-if="log.remark" style="color: #909399; font-size: 13px">{{ log.remark }}</p>
            </el-card>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无操作日志" />
      </div>

      <el-skeleton v-else :rows="8" animated />
    </el-card>

    <el-dialog v-model="approveDialogVisible" title="订单审核" width="500px">
      <el-form :model="approveForm" label-width="80px">
        <el-form-item label="审核结果">
          <el-radio-group v-model="approveForm.approved">
            <el-radio :label="true">通过</el-radio>
            <el-radio :label="false">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="approveForm.remark" type="textarea" :rows="3" placeholder="请输入审核意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitApprove">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
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
    await ElMessageBox.confirm('确定取消该订单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await cancelOrder(order.value.id)
    ElMessage.success('订单已取消')
    loadDetail()
  } catch {
    // cancelled
  }
}

async function handleShip() {
  try {
    await ElMessageBox.confirm('确定发货吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await shipOrder(order.value.id)
    ElMessage.success('已发货')
    loadDetail()
  } catch {
    // cancelled
  }
}

async function handleComplete() {
  try {
    await ElMessageBox.confirm('确定完成该订单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await completeOrder(order.value.id)
    ElMessage.success('订单已完成')
    loadDetail()
  } catch {
    // cancelled
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
.order-detail {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.detail-content {
  max-width: 1200px;
}

.action-buttons {
  margin-top: 20px;
  display: flex;
  gap: 12px;
}
</style>
