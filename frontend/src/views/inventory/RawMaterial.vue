<template>
  <div class="raw-material-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="名称">
          <el-input
            v-model="searchForm.name"
            placeholder="请输入原材料名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="searchForm.category" placeholder="全部" clearable style="width: 140px">
            <el-option label="面料" value="面料" />
            <el-option label="里料" value="里料" />
            <el-option label="辅料" value="辅料" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span>原材料列表</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>新增原材料
          </el-button>
        </div>
      </template>

      <el-table :data="materialList" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="name" label="名称" min-width="100" />
        <el-table-column prop="category" label="类别" width="80" />
        <el-table-column prop="specification" label="规格" min-width="100" />
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="quantity" label="库存数量" width="100" align="center">
          <template #default="{ row }">
            <span
              class="qty-badge"
              :class="{
                'qty-low': row.quantity <= row.alertThreshold,
                'qty-normal': row.quantity > row.alertThreshold,
                'qty-zero': row.quantity === 0
              }"
            >
              {{ row.quantity }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="alertThreshold" label="预警阈值" width="90" align="center" />
        <el-table-column label="存放位置" min-width="200">
          <template #default="{ row }">
            <div v-if="row.locations && row.locations.length > 0" class="location-container">
              <div
                v-for="(loc, index) in row.locations"
                :key="index"
                class="location-chip"
                :style="{ '--chip-index': index }"
              >
                <span class="loc-icon">📦</span>
                <span class="loc-name">{{ loc.location }}</span>
                <span class="loc-qty">
                  <em>{{ loc.quantity }}</em>件
                </span>
              </div>
            </div>
            <span v-else class="loc-empty">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="supplier" label="供应商" min-width="100" />
        <el-table-column prop="price" label="单价" width="80" align="right">
          <template #default="{ row }">
            {{ row.price != null ? row.price.toFixed(2) : '' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="handleStockIn(row)">入库</el-button>
            <el-button type="warning" link size="small" @click="handleStockOut(row)">出库</el-button>
            <el-button type="info" link size="small" @click="handleSetThreshold(row)">阈值</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="addDialogVisible"
      :title="dialogType === 'add' ? '新增原材料' : '编辑原材料'"
      width="560px"
      destroy-on-close
    >
      <el-form
        ref="materialFormRef"
        :model="materialForm"
        :rules="materialFormRules"
        label-width="80px"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="materialForm.name" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="类别" prop="category">
          <el-select v-model="materialForm.category" placeholder="请选择类别" style="width: 100%">
            <el-option label="面料" value="面料" />
            <el-option label="里料" value="里料" />
            <el-option label="辅料" value="辅料" />
          </el-select>
        </el-form-item>
        <el-form-item label="规格" prop="specification">
          <el-input v-model="materialForm.specification" placeholder="请输入规格" />
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="materialForm.unit" placeholder="请输入单位" />
        </el-form-item>
        <el-form-item v-if="dialogType === 'add'" label="库存数量" prop="quantity">
          <el-input-number v-model="materialForm.quantity" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="dialogType === 'add'" label="预警阈值" prop="alertThreshold">
          <el-input-number v-model="materialForm.alertThreshold" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="存放位置">
          <el-input
            v-if="dialogType === 'add'"
            v-model="materialForm.location"
            placeholder="请输入初始存放位置，如：A区-01-03"
          />
          <template v-else>
            <div v-if="materialForm.locations && materialForm.locations.length > 0" class="location-container">
              <div
                v-for="(loc, index) in materialForm.locations"
                :key="index"
                class="location-chip"
                :style="{ '--chip-index': index }"
              >
                <span class="loc-icon">📦</span>
                <span class="loc-name">{{ loc.location }}</span>
                <span class="loc-qty">
                  <em>{{ loc.quantity }}</em>件
                </span>
                <el-icon
                  class="loc-move-btn"
                  title="移动库存到其他位置"
                  @click.stop="openMovePanel(loc)"
                ><Switch /></el-icon>
              </div>
            </div>
            <span v-else style="color: #909399; font-size: 13px;">暂无入库位置记录，请通过入库操作添加</span>

            <transition name="slide-fade">
              <div v-if="movePanelVisible" class="move-panel">
                <div class="move-panel-header">
                  <span class="move-panel-title">
                    <el-icon><Rank /></el-icon> 跨库移动
                  </span>
                  <el-icon class="move-panel-close" @click="closeMovePanel"><Close /></el-icon>
                </div>
                <div class="move-panel-body">
                  <div class="move-row">
                    <label>源位置</label>
                    <span class="move-source">{{ moveForm.sourceLocation }}
                      <small>(可用: {{ moveSourceMaxQty }})</small>
                    </span>
                  </div>
                  <div class="move-row">
                    <label>目标位置</label>
                    <el-select
                      v-model="moveForm.targetLocation"
                      placeholder="选择目标库位"
                      size="small"
                      style="width: 200px"
                    >
                      <el-option
                        v-for="(loc, idx) in availableTargetLocations"
                        :key="idx"
                        :label="loc.location"
                        :value="loc.location"
                      >
                        {{ loc.location }} <span style="color:#909399;font-size:11px">(当前:{{ loc.quantity }})</span>
                      </el-option>
                    </el-select>
                    <el-input
                      v-model="moveForm.newLocation"
                      placeholder="或输入新位置"
                      size="small"
                      style="width: 140px; margin-left: 4px;"
                      clearable
                    />
                  </div>
                  <div class="move-row">
                    <label>移动数量</label>
                    <el-input-number
                      v-model="moveForm.quantity"
                      :min="1"
                      :max="moveSourceMaxQty"
                      size="small"
                      controls-position="right"
                    />
                    <span class="move-hint">最多可移动 {{ moveSourceMaxQty }} 件</span>
                  </div>
                </div>
                <div class="move-panel-footer">
                  <el-button size="small" @click="closeMovePanel">取消</el-button>
                  <el-button type="primary" size="small" :loading="moveLoading" @click="handleMoveSubmit">确认移动</el-button>
                </div>
              </div>
            </transition>
          </template>
        </el-form-item>
        <el-form-item label="供应商" prop="supplier">
          <el-input v-model="materialForm.supplier" placeholder="请输入供应商" />
        </el-form-item>
        <el-form-item label="单价" prop="price">
          <el-input-number v-model="materialForm.price" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="materialForm.description" type="textarea" :rows="2" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="stockDialogVisible"
      :title="stockType === 'IN' ? '入库操作' : '出库操作'"
      width="440px"
      destroy-on-close
    >
      <el-form
        ref="stockFormRef"
        :model="stockForm"
        :rules="stockFormRules"
        label-width="80px"
      >
        <el-form-item label="物品">
          <el-input :model-value="currentItem.name" disabled />
        </el-form-item>
        <el-form-item label="当前库存">
          <el-input :model-value="String(currentItem.quantity)" disabled />
        </el-form-item>
        <el-form-item v-if="stockType === 'OUT' && currentItem.locations && currentItem.locations.length > 0" label="出库位置" prop="stockLocation">
          <el-select
              v-model="stockForm.stockLocation"
              placeholder="请选择出库位置"
              style="width: 100%"
          >
            <el-option
                v-for="loc in currentItem.locations"
                :key="loc.location"
                :label="`${loc.location} (库存: ${loc.quantity})`"
                :value="loc.location"
            >
              <span style="font-weight: 600">{{ loc.location }}</span>
              <span style="color: #909399; font-size: 12px; margin-left: 8px; float: right">剩余 {{ loc.quantity }} 件</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="stockType === 'IN'" label="存放位置" prop="stockLocation">
          <el-input v-model="stockForm.stockLocation" placeholder="请输入存放位置，如：A区-01-03" />
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="stockForm.quantity" :min="1" :max="stockMaxQuantity" style="width: 100%" />
        </el-form-item>
        <el-form-item label="原因" prop="reason">
          <el-input v-model="stockForm.reason" type="textarea" :rows="2" placeholder="请输入原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stockDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleStockSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="thresholdDialogVisible"
      title="设置预警阈值"
      width="400px"
      destroy-on-close
    >
      <el-form
        ref="thresholdFormRef"
        :model="thresholdForm"
        :rules="thresholdFormRules"
        label-width="80px"
      >
        <el-form-item label="物品">
          <el-input :model-value="currentItem.name" disabled />
        </el-form-item>
        <el-form-item label="预警阈值" prop="alertThreshold">
          <el-input-number v-model="thresholdForm.alertThreshold" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="thresholdDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleThresholdSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Switch, Rank, Close } from '@element-plus/icons-vue'
import {
  getRawMaterialList,
  createRawMaterial,
  updateRawMaterial,
  deleteRawMaterial,
  setRawMaterialThreshold,
  stockIn,
  stockOut,
  moveRawMaterialLocation
} from '../../api/inventory'

const loading = ref(false)
const submitLoading = ref(false)
const materialList = ref([])
const addDialogVisible = ref(false)
const stockDialogVisible = ref(false)
const thresholdDialogVisible = ref(false)
const dialogType = ref('add')
const stockType = ref('IN')
const currentItemId = ref(null)
const materialFormRef = ref(null)
const stockFormRef = ref(null)
const thresholdFormRef = ref(null)

const currentItem = reactive({
  name: '',
  quantity: 0,
  locations: []
})

const searchForm = reactive({
  name: '',
  category: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const materialForm = reactive({
  name: '',
  category: '',
  specification: '',
  unit: '',
  quantity: 0,
  alertThreshold: 0,
  location: '',
  locations: [],
  supplier: '',
  price: 0,
  description: ''
})

const stockForm = reactive({
  quantity: 1,
  stockLocation: '',
  reason: ''
})

const stockMaxQuantity = computed(() => {
  if (stockType.value !== 'OUT' || !stockForm.stockLocation || !currentItem.locations) {
    return currentItem.quantity || 999999
  }
  const selected = currentItem.locations.find(l => l.location === stockForm.stockLocation)
  return selected ? selected.quantity : (currentItem.quantity || 999999)
})

watch(() => stockForm.stockLocation, (newLoc) => {
  if (stockType.value === 'OUT' && newLoc) {
    const selected = currentItem.locations.find(l => l.location === newLoc)
    if (selected && stockForm.quantity > selected.quantity) {
      stockForm.quantity = selected.quantity
    }
  }
})

const thresholdForm = reactive({
  alertThreshold: 0
})

const movePanelVisible = ref(false)
const moveLoading = ref(false)
const moveForm = reactive({
  sourceLocation: '',
  targetLocation: '',
  newLocation: '',
  quantity: 1
})

const moveSourceMaxQty = computed(() => {
  if (!moveForm.sourceLocation || !materialForm.locations) return 999999
  const src = materialForm.locations.find(l => l.location === moveForm.sourceLocation)
  return src ? src.quantity : 999999
})

const availableTargetLocations = computed(() => {
  if (!materialForm.locations) return []
  return materialForm.locations.filter(l => l.location !== moveForm.sourceLocation)
})

const materialFormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

const stockFormRules = {
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  stockLocation: [{ required: true, message: '请输入存放位置', trigger: 'blur' }],
  reason: [{ required: true, message: '请输入原因', trigger: 'blur' }]
}

const thresholdFormRules = {
  alertThreshold: [{ required: true, message: '请输入预警阈值', trigger: 'blur' }]
}

onMounted(() => {
  fetchList()
})

async function fetchList() {
  loading.value = true
  try {
    const params = {
      name: searchForm.name,
      category: searchForm.category,
      page: pagination.page,
      size: pagination.size
    }
    const res = await getRawMaterialList(params)
    const data = res.data || res
    materialList.value = data.list || []
    pagination.total = data.total || 0
  } catch (error) {
    ElMessage.error('获取原材料列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchList()
}

function handleReset() {
  searchForm.name = ''
  searchForm.category = ''
  pagination.page = 1
  fetchList()
}

function formatDateTime(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

function handleAdd() {
  dialogType.value = 'add'
  Object.assign(materialForm, {
    name: '',
    category: '',
    specification: '',
    unit: '',
    quantity: 0,
    alertThreshold: 0,
    location: '',
    locations: [],
    supplier: '',
    price: 0,
    description: ''
  })
  addDialogVisible.value = true
}

function handleEdit(row) {
  dialogType.value = 'edit'
  currentItemId.value = row.id
  Object.assign(materialForm, {
    name: row.name,
    category: row.category,
    specification: row.specification,
    unit: row.unit,
    location: row.locations && row.locations.length > 0 ? row.locations[0].location : '',
    locations: row.locations || [],
    supplier: row.supplier,
    price: row.price,
    description: row.description
  })
  addDialogVisible.value = true
}

async function handleSubmit() {
  const form = materialFormRef.value
  if (!form) return
  await form.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (dialogType.value === 'add') {
        await createRawMaterial(materialForm)
        ElMessage.success('新增原材料成功')
      } else {
        await updateRawMaterial(currentItemId.value, materialForm)
        ElMessage.success('编辑原材料成功')
      }
      addDialogVisible.value = false
      fetchList()
    } catch (error) {
      ElMessage.error(error.response?.data?.message || '操作失败')
    } finally {
      submitLoading.value = false
    }
  })
}

function handleStockIn(row) {
  stockType.value = 'IN'
  currentItemId.value = row.id
  currentItem.name = row.name
  currentItem.quantity = row.quantity
  currentItem.locations = []
  stockForm.quantity = 1
  stockForm.stockLocation = ''
  stockForm.reason = ''
  stockDialogVisible.value = true
}

function handleStockOut(row) {
  stockType.value = 'OUT'
  currentItemId.value = row.id
  currentItem.name = row.name
  currentItem.quantity = row.quantity
  currentItem.locations = row.locations || []
  stockForm.quantity = 1
  stockForm.stockLocation = ''
  stockForm.reason = ''
  stockDialogVisible.value = true
}

async function handleStockSubmit() {
  const form = stockFormRef.value
  if (!form) return
  await form.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      const data = {
        itemType: 'RAW_MATERIAL',
        itemId: currentItemId.value,
        quantity: stockForm.quantity,
        reason: stockForm.stockLocation
          ? `${stockForm.reason} 位置:${stockForm.stockLocation}`
          : stockForm.reason
      }
      if (stockType.value === 'IN') {
        await stockIn(data)
        ElMessage.success('入库成功')
      } else {
        await stockOut(data)
        ElMessage.success('出库成功')
      }
      stockDialogVisible.value = false
      fetchList()
    } catch (error) {
      ElMessage.error(error.response?.data?.message || '操作失败')
    } finally {
      submitLoading.value = false
    }
  })
}

function handleSetThreshold(row) {
  currentItemId.value = row.id
  currentItem.name = row.name
  thresholdForm.alertThreshold = row.alertThreshold || 0
  thresholdDialogVisible.value = true
}

async function handleThresholdSubmit() {
  const form = thresholdFormRef.value
  if (!form) return
  await form.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      await setRawMaterialThreshold(currentItemId.value, { alertThreshold: thresholdForm.alertThreshold })
      ElMessage.success('设置预警阈值成功')
      thresholdDialogVisible.value = false
      fetchList()
    } catch (error) {
      ElMessage.error(error.response?.data?.message || '操作失败')
    } finally {
      submitLoading.value = false
    }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除原材料"${row.name}"吗？此操作不可恢复。`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteRawMaterial(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '删除失败')
    }
  }
}

function openMovePanel(loc) {
  moveForm.sourceLocation = loc.location
  moveForm.targetLocation = ''
  moveForm.newLocation = ''
  moveForm.quantity = Math.min(1, loc.quantity)
  movePanelVisible.value = true
}

function closeMovePanel() {
  movePanelVisible.value = false
}

async function handleMoveSubmit() {
  const targetLoc = moveForm.targetLocation || moveForm.newLocation
  if (!targetLoc || !targetLoc.trim()) {
    ElMessage.warning('请选择或输入目标位置')
    return
  }
  if (targetLoc === moveForm.sourceLocation) {
    ElMessage.warning('源位置和目标位置不能相同')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认将 ${moveForm.quantity} 件从「${moveForm.sourceLocation}」移动到「${targetLoc}」吗？`,
      '跨库移动确认',
      { confirmButtonText: '确认移动', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return
  }

  moveLoading.value = true
  try {
    await moveRawMaterialLocation(currentItemId.value, {
      sourceLocation: moveForm.sourceLocation,
      targetLocation: targetLoc,
      quantity: moveForm.quantity
    })
    ElMessage.success(`成功将 ${moveForm.quantity} 件从「${moveForm.sourceLocation}」移动到「${targetLoc}」`)

    const srcIdx = materialForm.locations.findIndex(l => l.location === moveForm.sourceLocation)
    if (srcIdx !== -1) {
      materialForm.locations[srcIdx].quantity -= moveForm.quantity
      if (materialForm.locations[srcIdx].quantity <= 0) {
        materialForm.locations.splice(srcIdx, 1)
      }
    }

    const targetIdx = materialForm.locations.findIndex(l => l.location === targetLoc)
    if (targetIdx !== -1) {
      materialForm.locations[targetIdx].quantity += moveForm.quantity
    } else {
      materialForm.locations.push({ location: targetLoc, quantity: moveForm.quantity })
    }

    closeMovePanel()
    fetchList()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '移动失败')
  } finally {
    moveLoading.value = false
  }
}
</script>

<style scoped>
.raw-material-container {
  padding: 20px;
}

.search-card {
  margin-bottom: 16px;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.qty-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 42px;
  height: 26px;
  padding: 0 10px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.5px;
  line-height: 1;
  transition: all 0.25s ease;
}
.qty-normal {
  color: #0d7377;
  background: linear-gradient(135deg, #e8f7f6 0%, #d4f4ed 100%);
  box-shadow: 0 2px 6px rgba(13, 115, 119, 0.12), inset 0 -2px 0 rgba(13, 115, 119, 0.08);
}
.qty-normal:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(13, 115, 119, 0.18);
}
.qty-low {
  color: #c0392b;
  background: linear-gradient(135deg, #fef3f2 0%, #fdeaea 100%);
  box-shadow: 0 2px 6px rgba(192, 57, 43, 0.15), inset 0 -2px 0 rgba(192, 57, 43, 0.08);
  animation: pulse-warning 2s ease-in-out infinite;
}
.qty-low:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(192, 57, 43, 0.22);
}
.qty-zero {
  color: #95a5a6;
  background: linear-gradient(135deg, #f4f6f7 0%, #eaeded 100%);
  box-shadow: 0 2px 6px rgba(149, 165, 166, 0.1);
}

@keyframes pulse-warning {
  0%, 100% { box-shadow: 0 2px 6px rgba(192, 57, 43, 0.15); }
  50% { box-shadow: 0 2px 14px rgba(192, 57, 43, 0.35); }
}

.location-container {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.location-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px 4px 8px;
  border-radius: 8px;
  font-size: 12px;
  white-space: nowrap;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
  background: linear-gradient(
    135deg,
    hsl(calc(210 + var(--chip-index) * 22), 45%, 96%) 0%,
    hsl(calc(200 + var(--chip-index) * 22), 40%, 91%) 100%
  );
  border: 1px solid hsl(calc(210 + var(--chip-index) * 22), 30%, 85%);
  box-shadow:
    0 1px 3px hsla(calc(210 + var(--chip-index) * 22), 30%, 50%, 0.06),
    0 1px 0 hsla(calc(210 + var(--chip-index) * 22), 30%, 95%, 0.9) inset;
}
.location-chip:hover {
  transform: translateY(-1.5px) scale(1.03);
  box-shadow:
    0 4px 10px hsla(calc(210 + var(--chip-index) * 22), 30%, 45%, 0.14),
    0 1px 0 hsla(calc(210 + var(--chip-index) * 22), 30%, 95%, 0.9) inset;
  border-color: hsl(calc(210 + var(--chip-index) * 22), 35%, 72%);
}
.loc-icon {
  font-size: 15px;
  flex-shrink: 0;
  filter: grayscale(0.15);
}
.loc-name {
  font-weight: 600;
  color: #2c3e50;
  letter-spacing: 0.3px;
}
.loc-qty {
  color: #5d6d7e;
  font-size: 11px;
  padding-left: 5px;
  border-left: 1.5px solid rgba(93, 109, 126, 0.2);
}
.loc-qty em {
  font-style: normal;
  font-weight: 700;
  color: #2980b9;
  font-size: 12px;
}
.loc-empty {
  color: #c0c4cc;
  font-style: italic;
  font-size: 13px;
}

.loc-move-btn {
  cursor: pointer;
  margin-left: 4px;
  padding: 3px;
  color: #409eff;
  border-radius: 4px;
  transition: all 0.2s;
  font-size: 16px;
  background: rgba(64, 158, 255, 0.06);
}
.loc-move-btn:hover {
  color: #fff;
  background: #409eff;
  transform: scale(1.15);
}

.move-panel {
  margin-top: 10px;
  padding: 14px 16px;
  background: linear-gradient(135deg, #f8faff 0%, #f0f5ff 100%);
  border: 1px solid #d4e3fc;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.06);
}
.move-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.move-panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
  display: flex;
  align-items: center;
  gap: 4px;
}
.move-panel-close {
  cursor: pointer;
  color: #909399;
  font-size: 14px;
  transition: color 0.2s;
}
.move-panel-close:hover {
  color: #f56c6c;
}
.move-panel-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.move-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.move-row label {
  width: 56px;
  font-size: 12px;
  color: #606266;
  flex-shrink: 0;
}
.move-source {
  font-weight: 600;
  color: #303133;
  font-size: 13px;
}
.move-source small {
  color: #909399;
  font-weight: 400;
  margin-left: 4px;
}
.move-hint {
  font-size: 11px;
  color: #909399;
  margin-left: 8px;
}
.move-panel-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #dcdfe6;
}

.slide-fade-enter-active {
  transition: all 0.25s ease-out;
}
.slide-fade-leave-active {
  transition: all 0.15s ease-in;
}
.slide-fade-enter-from {
  opacity: 0;
  transform: translateY(-8px);
}
.slide-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
