<template>
  <div class="order-list">
    <el-card>
      <div class="search-bar">
        <el-input
          v-model="searchOrderNo"
          placeholder="订单编号"
          clearable
          style="width: 200px"
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="searchCustomerName"
          placeholder="客户名称"
          clearable
          style="width: 200px"
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        />
        <el-select
          v-model="searchStatus"
          placeholder="订单状态"
          clearable
          style="width: 160px"
          @change="handleSearch"
        >
          <el-option label="待审核" value="PENDING_APPROVAL" />
          <el-option label="已审核" value="APPROVED" />
          <el-option label="生产中" value="IN_PRODUCTION" />
          <el-option label="已发货" value="SHIPPED" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="resetSearch">重置</el-button>
        <el-button type="success" @click="goCreate" v-if="userStore.hasPermission('ORDER_CREATE')">新增订单</el-button>
      </div>

      <el-table :data="orderList" border stripe style="width: 100%">
        <el-table-column prop="orderNo" label="订单编号" width="160" />
        <el-table-column prop="customerName" label="客户名称" width="140" />
        <el-table-column prop="totalAmount" label="总金额" width="120">
          <template #default="{ row }">
            ¥{{ row.totalAmount?.toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createByName" label="创建人" width="120" />
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="goDetail(row.id)">查看</el-button>
            <el-button
              v-if="row.status === 'PENDING_APPROVAL' && userStore.hasPermission('ORDER_APPROVE')"
              size="small"
              type="warning"
              @click="handleApprove(row)"
            >审核</el-button>
            <el-button
              v-if="row.status === 'APPROVED' && userStore.hasPermission('ORDER_UPDATE')"
              size="small"
              type="primary"
              @click="handleShip(row.id)"
            >发货</el-button>
            <el-button
              v-if="row.status === 'SHIPPED' && userStore.hasPermission('ORDER_UPDATE')"
              size="small"
              type="success"
              @click="handleComplete(row.id)"
            >完成</el-button>
            <el-button
              v-if="(row.status === 'PENDING_APPROVAL' || row.status === 'APPROVED') && userStore.hasPermission('ORDER_CANCEL')"
              size="small"
              type="danger"
              @click="handleCancel(row.id)"
            >取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderList, approveOrder, rejectOrder, cancelOrder, shipOrder, completeOrder } from '../../api/order'
import { useUserStore } from '../../store/user'

const router = useRouter()
const userStore = useUserStore()

const orderList = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const searchOrderNo = ref('')
const searchCustomerName = ref('')
const searchStatus = ref('')

const approveDialogVisible = ref(false)
const approveForm = ref({ approved: true, remark: '', orderId: '' })

const statusMap = {
  PENDING_APPROVAL: { label: '待审核', type: 'warning' },
  APPROVED: { label: '已审核', type: 'success' },
  IN_PRODUCTION: { label: '生产中', type: 'primary' },
  SHIPPED: { label: '已发货', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' }
}

function statusLabel(status) {
  return statusMap[status]?.label || status
}

function statusTagType(status) {
  return statusMap[status]?.type || 'info'
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function loadData() {
  const res = await getOrderList({
    page: page.value,
    size: size.value,
    orderNo: searchOrderNo.value,
    customerName: searchCustomerName.value,
    status: searchStatus.value
  })
  const data = res.data || res
  orderList.value = data.list || []
  total.value = data.total || 0
}

function handleSearch() {
  page.value = 1
  loadData()
}

function resetSearch() {
  searchOrderNo.value = ''
  searchCustomerName.value = ''
  searchStatus.value = ''
  page.value = 1
  loadData()
}

function goCreate() {
  router.push('/order/create')
}

function goDetail(id) {
  router.push(`/order/detail/${id}`)
}

function handleApprove(row) {
  approveForm.value = { approved: true, remark: '', orderId: row.id }
  approveDialogVisible.value = true
}

async function submitApprove() {
  try {
    if (approveForm.value.approved) {
      await approveOrder(approveForm.value.orderId, {
        approved: true,
        remark: approveForm.value.remark
      })
      ElMessage.success('审核通过')
    } else {
      await rejectOrder(approveForm.value.orderId, {
        approved: false,
        remark: approveForm.value.remark
      })
      ElMessage.success('已拒绝')
    }
    approveDialogVisible.value = false
    loadData()
  } catch (error) {
    console.error(error)
  }
}

async function handleCancel(id) {
  try {
    await ElMessageBox.confirm('确定取消该订单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await cancelOrder(id)
    ElMessage.success('订单已取消')
    loadData()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.response?.data?.message || '发货失败')
    }
  }
}

async function handleShip(id) {
  try {
    await ElMessageBox.confirm('确定发货吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await shipOrder(id)
    ElMessage.success('已发货')
    loadData()
  } catch {
    // cancelled
  }
}

async function handleComplete(id) {
  try {
    await ElMessageBox.confirm('确定完成该订单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await completeOrder(id)
    ElMessage.success('订单已完成')
    loadData()
  } catch {
    // cancelled
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.order-list {
  padding: 20px;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
