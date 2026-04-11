<template>
  <div class="order-create">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>创建订单</span>
          <el-button @click="goBack">返回</el-button>
        </div>
      </template>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="客户" prop="customerId">
          <el-select
            v-model="form.customerId"
            placeholder="请选择客户"
            filterable
            style="width: 400px"
            @change="onCustomerChange"
          >
            <el-option
              v-for="c in customerList"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="产品明细" prop="items">
          <el-table :data="form.items" border style="width: 800px">
            <el-table-column label="产品" min-width="180">
              <template #default="{ row, $index }">
                <el-select
                  v-model="row.productId"
                  placeholder="选择产品"
                  filterable
                  @change="(val) => onProductChange($index, val)"
                >
                  <el-option
                    v-for="p in productList"
                    :key="p.id"
                    :label="p.name"
                    :value="p.id"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="规格" width="150">
              <template #default="{ row }">
                <el-input v-model="row.specification" placeholder="规格" />
              </template>
            </el-table-column>
            <el-table-column label="数量" width="140">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="1" :max="99999" @change="calcAmount(row)" />
              </template>
            </el-table-column>
            <el-table-column label="单价" width="140">
              <template #default="{ row }">
                <el-input-number v-model="row.unitPrice" :min="0" :precision="2" @change="calcAmount(row)" />
              </template>
            </el-table-column>
            <el-table-column label="金额" width="120">
              <template #default="{ row }">
                ¥{{ row.amount?.toFixed(2) || '0.00' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ $index }">
                <el-button type="danger" size="small" @click="removeItem($index)" :disabled="form.items.length <= 1">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button type="primary" style="margin-top: 10px" @click="addItem">添加产品</el-button>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" style="width: 400px" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="submitForm" :loading="submitting">提交订单</el-button>
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
import request from '../../utils/request'

const router = useRouter()
const formRef = ref(null)
const submitting = ref(false)

const customerList = ref([])
const productList = ref([])

const form = ref({
  customerId: '',
  customerName: '',
  items: [
    { productId: '', productName: '', specification: '', quantity: 1, unitPrice: 0, amount: 0 }
  ],
  remark: ''
})

const rules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }]
}

function onCustomerChange(val) {
  const customer = customerList.value.find(c => c.id === val)
  form.value.customerName = customer ? customer.name : ''
}

function onProductChange(index, val) {
  const product = productList.value.find(p => p.id === val)
  if (product) {
    form.value.items[index].productName = product.name
    form.value.items[index].unitPrice = product.price || 0
    calcAmount(form.value.items[index])
  }
}

function calcAmount(row) {
  row.amount = (row.unitPrice || 0) * (row.quantity || 0)
}

function addItem() {
  form.value.items.push({ productId: '', productName: '', specification: '', quantity: 1, unitPrice: 0, amount: 0 })
}

function removeItem(index) {
  form.value.items.splice(index, 1)
}

async function submitForm() {
  try {
    await formRef.value.validate()

    const validItems = form.value.items.filter(item => item.productId)
    if (validItems.length === 0) {
      ElMessage.warning('请至少添加一个产品')
      return
    }

    submitting.value = true
    await createOrder({
      customerId: form.value.customerId,
      customerName: form.value.customerName,
      items: validItems.map(item => ({
        productId: item.productId,
        productName: item.productName,
        specification: item.specification,
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
</style>
