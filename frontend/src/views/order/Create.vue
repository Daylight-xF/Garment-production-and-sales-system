<template>
  <div class="order-create">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>创建订单</span>
          <el-button @click="goBack">返回</el-button>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="客户" prop="customerId">
          <el-select v-model="form.customerId" placeholder="请选择客户" filterable style="width: 400px"
            @change="onCustomerChange">
            <el-option v-for="customer in customerList" :key="customer.id" :label="customer.name"
              :value="customer.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="产品明细" prop="items">
          <el-table :data="form.items" border style="width: 1360px">
            <el-table-column label="产品" min-width="190">
              <template #default="{ row, $index }">
                <el-select v-model="row.selectedProductKey" placeholder="选择产品" filterable clearable
                  @change="(value) => onProductKeyChange($index, value)">
                  <el-option v-for="option in getProductOptions(row)" :key="option.key" :label="option.label"
                    :value="option.key" />
                </el-select>
              </template>
            </el-table-column>

            <el-table-column label="颜色" width="150">
              <template #default="{ row, $index }">
                <el-select v-model="row.color" placeholder="颜色" clearable
                  @change="(value) => onColorChange($index, value)">
                  <el-option v-for="color in getColorOptions(row)" :key="color" :label="color" :value="color" />
                </el-select>
              </template>
            </el-table-column>

            <el-table-column label="尺码" width="150">
              <template #default="{ row, $index }">
                <el-select v-model="row.size" placeholder="尺码" clearable
                  @change="(value) => onSizeChange($index, value)">
                  <el-option v-for="size in getSizeOptions(row)" :key="size" :label="size" :value="size" />
                </el-select>
              </template>
            </el-table-column>

            <el-table-column label="数量" width="180">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="1" :max="99999" @change="calcAmount(row)" />
              </template>
            </el-table-column>

            <el-table-column label="单价" width="180">
              <template #default="{ row }">
                <el-input-number v-model="row.unitPrice" :min="0" :precision="2" @change="calcAmount(row)" />
              </template>
            </el-table-column>

            <el-table-column label="单件成本" width="120" align="center">
              <template #default="{ row }">
                <span class="cost-pill cost-pill--table" :class="{ 'cost-empty': row.costPrice == null }">
                  {{ formatCurrency(row.costPrice) }}
                </span>
              </template>
            </el-table-column>

            <el-table-column label="金额" width="175" align="center">
              <template #default="{ row }">
                <span class="amount-card amount-card--table" :class="{ 'amount-card--empty': !row.amount }">
                  <span class="amount-card__label">合计</span>
                  <span class="amount-card__value">{{ formatCurrency(row.amount) }}</span>
                </span>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="150">
              <template #default="{ $index }">
                <div class="row-actions">
                  <el-button type="warning" size="small" plain @click="clearItem($index)">
                    清空
                  </el-button>
                  <el-button type="danger" size="small" @click="removeItem($index)" :disabled="form.items.length <= 1">
                    删除
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <el-button type="primary" style="margin-top: 40px" @click="addItem">添加产品</el-button>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" style="width: 400px" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submitForm">提交订单</el-button>
          <el-button @click="goBack">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createOrder } from '../../api/order'
import { getErrorMessage } from '../../utils/errorMessage'
import {
  getAvailableColors,
  getAvailableProductOptions,
  getAvailableSizes,
  findMatchedFinishedProduct,
  getSelectionCostPrice,
  parseProductKey
} from '../../utils/orderItemSelection'
import request from '../../utils/request'

const router = useRouter()
const formRef = ref(null)
const submitting = ref(false)

const customerList = ref([])
const productList = ref([])

function createEmptyItem() {
  return {
    productId: '',
    productCode: '',
    productName: '',
    selectedProductKey: '',
    color: '',
    size: '',
    quantity: 1,
    unitPrice: 0,
    costPrice: null,
    amount: 0
  }
}

const form = ref({
  customerId: '',
  customerName: '',
  items: [createEmptyItem()],
  remark: ''
})

const rules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }]
}

function formatCurrency(value) {
  if (value == null) {
    return '-'
  }
  return `¥${Number(value).toFixed(2)}`
}

function onCustomerChange(value) {
  const customer = customerList.value.find(item => item.id === value)
  form.value.customerName = customer ? customer.name : ''
}

function getProductOptions(row) {
  return getAvailableProductOptions(productList.value, row)
}

function getColorOptions(row) {
  return getAvailableColors(productList.value, row)
}

function getSizeOptions(row) {
  return getAvailableSizes(productList.value, row)
}

function calcAmount(row) {
  row.amount = (row.unitPrice || 0) * (row.quantity || 0)
}

function syncMatchedItem(row) {
  const matched = findMatchedFinishedProduct(productList.value, row)
  const selectionCostPrice = getSelectionCostPrice(productList.value, row)
  const selectedMeta = parseProductKey(row.selectedProductKey)

  row.productName = selectedMeta.productName || ''
  row.productCode = selectedMeta.productCode || ''

  if (!matched) {
    row.productId = ''
    row.costPrice = selectionCostPrice
    calcAmount(row)
    return
  }

  row.productId = matched.id
  row.productName = matched.name || selectedMeta.productName || ''
  row.productCode = matched.productCode || selectedMeta.productCode || ''
  row.color = matched.color || ''
  row.size = matched.size || ''
  row.costPrice = matched.costPrice ?? selectionCostPrice ?? null
  row.unitPrice = matched.price ?? row.unitPrice ?? 0
  calcAmount(row)
}

function applySelectionConstraints(row) {
  const productOptions = getAvailableProductOptions(productList.value, row)
  if (row.selectedProductKey && !productOptions.some(option => option.key === row.selectedProductKey)) {
    row.selectedProductKey = ''
  }

  const colorOptions = getAvailableColors(productList.value, row)
  if (row.color && !colorOptions.includes(row.color)) {
    row.color = ''
  }

  const sizeOptions = getAvailableSizes(productList.value, row)
  if (row.size && !sizeOptions.includes(row.size)) {
    row.size = ''
  }

  syncMatchedItem(row)
}

function onProductKeyChange(index, value) {
  const row = form.value.items[index]
  row.selectedProductKey = value || ''
  applySelectionConstraints(row)
}

function onColorChange(index, value) {
  const row = form.value.items[index]
  row.color = value || ''
  applySelectionConstraints(row)
}

function onSizeChange(index, value) {
  const row = form.value.items[index]
  row.size = value || ''
  applySelectionConstraints(row)
}

function addItem() {
  form.value.items.push(createEmptyItem())
}

function clearItem(index) {
  form.value.items.splice(index, 1, createEmptyItem())
}

function removeItem(index) {
  form.value.items.splice(index, 1)
}

function isTouchedItem(item) {
  return Boolean(item.selectedProductKey || item.color || item.size || item.productId)
}

async function submitForm() {
  try {
    await formRef.value.validate()

    const touchedItems = form.value.items.filter(isTouchedItem)
    if (touchedItems.length === 0) {
      ElMessage.warning('请至少添加一个产品')
      return
    }

    const invalidItem = touchedItems.find(item => !item.selectedProductKey || !item.color || !item.size)
    if (invalidItem) {
      ElMessage.warning('请为每个订单项完整选择产品、颜色和尺码')
      return
    }

    submitting.value = true
    await createOrder({
      customerId: form.value.customerId,
      customerName: form.value.customerName,
      items: touchedItems.map(item => ({
        productId: item.productId,
        productCode: item.productCode,
        productName: item.productName,
        color: item.color,
        size: item.size,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
        amount: item.amount
      })),
      remark: form.value.remark
    })
    ElMessage.success('订单创建成功')
    router.push('/order/list')
  } catch (error) {
    if (error !== false) {
      ElMessage.error(getErrorMessage(error, '订单创建失败'))
      console.error(error)
    }
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.push('/order/list')
}

async function loadCustomers() {
  try {
    const res = await request({ url: '/sales/customers', method: 'get', params: { page: 1, size: 1000 } })
    const data = res.data || res
    customerList.value = data.list || data || []
  } catch {
    customerList.value = []
  }
}

async function loadProducts() {
  try {
    const res = await request({ url: '/inventory/finished-products', method: 'get', params: { page: 1, size: 1000 } })
    const data = res.data || res
    productList.value = data.list || data || []
  } catch {
    productList.value = []
  }
}

onMounted(() => {
  loadCustomers()
  loadProducts()
})
</script>

<style scoped>
.order-create {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.row-actions {
  display: flex;
  gap: 8px;
}

.cost-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 82px;
  padding: 6px 10px;
  border-radius: 999px;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.cost-pill--table {
  color: #b26a00;
  background: linear-gradient(135deg, #fff7e6, #ffe7ba);
  border: 1px solid #ffd591;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.65);
}

.cost-empty {
  color: #909399;
  background: linear-gradient(135deg, #f4f4f5, #ebeef5);
  border-color: #dcdfe6;
}

.amount-card {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 92px;
  padding: 6px 10px 7px;
  border-radius: 14px;
  border: 1px solid transparent;
  gap: 2px;
}

.amount-card--table {
  background: linear-gradient(180deg, #fff9ef, #ffe7c6);
  border-color: #ffb86b;
  box-shadow: 0 8px 18px rgba(255, 170, 74, 0.16);
}

.amount-card__label {
  font-size: 11px;
  line-height: 1;
  font-weight: 700;
  color: #c27713;
  letter-spacing: 0.08em;
}

.amount-card__value {
  font-size: 18px;
  line-height: 1.1;
  font-weight: 800;
  color: #a74e00;
}

.amount-card--empty {
  background: linear-gradient(135deg, #f4f4f5, #ebeef5);
  border-color: #dcdfe6;
  box-shadow: none;
}

.amount-card--empty .amount-card__label,
.amount-card--empty .amount-card__value {
  color: #909399;
}
</style>
