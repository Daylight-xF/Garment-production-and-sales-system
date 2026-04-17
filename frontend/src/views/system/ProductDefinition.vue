<template>
  <div class="product-definition-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="产品编号">
          <el-input
            v-model="searchForm.productCode"
            placeholder="请输入产品编号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input
            v-model="searchForm.name"
            placeholder="请输入产品名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="产品分类">
          <el-select v-model="searchForm.category" placeholder="全部" clearable style="width: 140px">
            <el-option label="上装" value="上装" />
            <el-option label="下装" value="下装" />
            <el-option label="外套" value="外套" />
            <el-option label="其他" value="其他" />
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
          <span>产品定义列表</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>添加产品
          </el-button>
        </div>
      </template>

      <el-table :data="productList" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="productCode" label="产品编号" width="120" align="center" />
        <el-table-column prop="productName" label="产品名称" min-width="120" align="center" />
        <el-table-column prop="category" label="产品分类" width="100" align="center" />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '启用' ? 'success' : 'danger'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="原材料数量" width="100" align="center">
          <template #default="{ row }">
            {{ row.materials ? row.materials.length : 0 }}
          </template>
        </el-table-column>
        <el-table-column label="单件成本" width="120" align="center">
          <template #default="{ row }">
            <span class="cost-pill cost-pill--table">
              {{ formatCurrency(row.unitCost) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="产品描述" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="170" align="center">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
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
      v-model="dialogVisible"
      :title="dialogType === 'add' ? '添加产品' : '编辑产品'"
      width="980px"
      destroy-on-close
    >
      <el-form
        ref="productFormRef"
        :model="productForm"
        :rules="productFormRules"
        label-width="100px"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="产品编号" prop="productCode">
              <el-input
                v-model="productForm.productCode"
                placeholder="请输入产品编号"
                :disabled="dialogType === 'edit'"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产品名称" prop="productName">
              <el-input v-model="productForm.productName" placeholder="请输入产品名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="产品分类" prop="category">
              <el-select v-model="productForm.category" placeholder="请选择分类" style="width: 100%">
                <el-option label="上装" value="上装" />
                <el-option label="下装" value="下装" />
                <el-option label="套装" value="套装" />
                <el-option label="配饰" value="配饰" />
                <el-option label="其他" value="其他" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="productForm.status" placeholder="请选择状态" style="width: 100%">
                <el-option label="启用" value="启用" />
                <el-option label="停用" value="停用" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="产品描述">
          <el-input
            v-model="productForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入产品描述"
          />
        </el-form-item>

        <el-divider content-position="left">原材料配方</el-divider>

        <el-table :data="productForm.materials" border style="width: 100%; margin-bottom: 16px;">
          <el-table-column label="原材料" min-width="200">
            <template #default="{ row, $index }">
              <el-select
                v-model="row.materialId"
                filterable
                placeholder="请选择原材料"
                style="width: 100%"
                @change="(val) => handleMaterialChange(val, $index)"
              >
                <el-option
                  v-for="item in rawMaterialList"
                  :key="item.id"
                  :label="`${item.name} (${item.quantity || 0})`"
                  :value="item.id"
                  :disabled="isMaterialSelected(item.id, $index)"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="100" align="center">
            <template #default="{ row }">
              {{ row.materialCategory || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="用量" width="150" align="center">
            <template #default="{ row }">
              <el-input-number
                v-model="row.quantity"
                :min="0.01"
                :precision="2"
                :step="1"
                controls-position="right"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="单价" width="120" align="center">
            <template #default="{ row }">
              <span class="cost-chip cost-chip--price">
                {{ formatCurrency(getMaterialCurrentPriceValue(row)) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="单件成本" width="120" align="center">
            <template #default="{ row }">
              <span class="cost-chip cost-chip--cost">
                {{ formatCurrency(getMaterialCostValue(row)) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="单位" width="80" align="center">
            <template #default="{ row }">
              {{ row.unit || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ $index }">
              <el-button type="danger" link size="small" @click="removeMaterial($index)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="add-material-btn">
          <el-button type="primary" plain @click="addMaterialRow">
            + 添加原材料
          </el-button>
        </div>

        <div class="cost-summary">
          <div class="cost-summary__meta">
            <span class="cost-summary__eyebrow">COST OVERVIEW</span>
            <span class="cost-summary__label">总单件成本</span>
          </div>
          <div class="cost-summary__amount">
            <span class="cost-summary__currency">￥</span>
            <span class="cost-summary__value">{{ (productUnitCost || 0).toFixed(2) }}</span>
          </div>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getProductDefinitionList,
  getProductDefinitionDetail,
  createProductDefinition,
  updateProductDefinition,
  deleteProductDefinition
} from '../../api/productDefinition'
import { getRawMaterialList } from '../../api/inventory'
import {
  getMaterialCurrentPrice,
  calculateMaterialCost,
  calculateProductUnitCost
} from '../../utils/productCost'

const loading = ref(false)
const submitLoading = ref(false)
const productList = ref([])
const dialogVisible = ref(false)
const dialogType = ref('add')
const currentProductId = ref(null)
const productFormRef = ref(null)
const rawMaterialList = ref([])

const searchForm = reactive({
  productCode: '',
  name: '',
  category: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const productForm = reactive({
  productCode: '',
  productName: '',
  category: '',
  status: '启用',
  description: '',
  materials: []
})

const productUnitCost = computed(() => calculateProductUnitCost(productForm.materials, rawMaterialList.value))

const productFormRules = {
  productCode: [{ required: true, message: '请输入产品编号', trigger: 'blur' }],
  productName: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择产品分类', trigger: 'change' }]
}

onMounted(() => {
  fetchList()
  fetchRawMaterials()
})

async function fetchList() {
  loading.value = true
  try {
    const params = {
      name: searchForm.name,
      productCode: searchForm.productCode,
      category: searchForm.category,
      page: pagination.page,
      size: pagination.size
    }
    const res = await getProductDefinitionList(params)
    const data = res.data || res
    productList.value = data.list || []
    pagination.total = data.total || 0
  } catch (error) {
    ElMessage.error('获取产品列表失败')
  } finally {
    loading.value = false
  }
}

async function fetchRawMaterials() {
  try {
    const res = await getRawMaterialList({ size: 1000 })
    const data = res.data || res
    rawMaterialList.value = data.list || []
  } catch (error) {
    console.error('获取原材料列表失败', error)
  }
}

function handleSearch() {
  pagination.page = 1
  fetchList()
}

function handleReset() {
  searchForm.productCode = ''
  searchForm.name = ''
  searchForm.category = ''
  pagination.page = 1
  fetchList()
}

async function handleAdd() {
  await fetchRawMaterials()
  dialogType.value = 'add'
  currentProductId.value = null
  Object.assign(productForm, {
    productCode: '',
    productName: '',
    category: '',
    status: '启用',
    description: '',
    materials: []
  })
  dialogVisible.value = true
}

async function handleEdit(row) {
  await fetchRawMaterials()
  dialogType.value = 'edit'
  currentProductId.value = row.id

  const res = await getProductDefinitionDetail(row.id)
  const detail = res.data || res
  const materials = (detail.materials || []).map(material => ({
    materialId: material.materialId,
    materialName: material.materialName,
    materialCategory: material.materialCategory,
    quantity: material.quantity,
    unit: material.unit,
    materialPrice: material.materialPrice || 0,
    materialCost: material.materialCost || 0
  }))

  Object.assign(productForm, {
    productCode: detail.productCode,
    productName: detail.productName,
    category: detail.category,
    status: detail.status,
    description: detail.description,
    materials: materials.length > 0 ? materials : [createEmptyMaterial()]
  })
  dialogVisible.value = true
}

function createEmptyMaterial() {
  return {
    materialId: '',
    materialName: '',
    materialCategory: '',
    quantity: 1.0,
    unit: '',
    materialPrice: 0,
    materialCost: 0
  }
}

function addMaterialRow() {
  productForm.materials.push(createEmptyMaterial())
}

function removeMaterial(index) {
  productForm.materials.splice(index, 1)
}

function handleMaterialChange(materialId, index) {
  const material = rawMaterialList.value.find(item => item.id === materialId)
  if (material) {
    productForm.materials[index].materialName = material.name
    productForm.materials[index].materialCategory = material.category
    productForm.materials[index].unit = material.unit
    productForm.materials[index].materialPrice = material.price || 0
    productForm.materials[index].quantity = 1.0
  }
}

function isMaterialSelected(materialId, currentIndex) {
  return productForm.materials.some((material, index) =>
    material.materialId === materialId && index !== currentIndex
  )
}

function getMaterialCurrentPriceValue(material) {
  return getMaterialCurrentPrice(material, rawMaterialList.value)
}

function getMaterialCostValue(material) {
  return calculateMaterialCost(material, rawMaterialList.value)
}

function formatCurrency(value) {
  return `￥${(value || 0).toFixed(2)}`
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

async function handleSubmit() {
  const form = productFormRef.value
  if (!form) return

  await form.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      const validMaterials = productForm.materials.filter(material => material.materialId)

      if (validMaterials.length === 0) {
        ElMessage.warning('请至少配置一种原材料')
        return
      }

      const submitData = {
        ...productForm,
        materials: validMaterials.map(material => ({
          materialId: material.materialId,
          quantity: material.quantity
        }))
      }

      if (dialogType.value === 'add') {
        await createProductDefinition(submitData)
        ElMessage.success('添加产品成功')
      } else {
        await updateProductDefinition(currentProductId.value, submitData)
        ElMessage.success('编辑产品成功')
      }

      dialogVisible.value = false
      fetchList()
    } catch (error) {
      ElMessage.error(error.message || '操作失败')
    } finally {
      submitLoading.value = false
    }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除产品“${row.productName}”吗？此操作不可恢复。`,
      '警告',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await deleteProductDefinition(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}
</script>

<style scoped>
.product-definition-container {
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

.add-material-btn {
  display: flex;
  justify-content: center;
  margin-top: 8px;
}

.cost-summary {
  margin-top: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  border: 1px solid #d9e6f5;
  border-radius: 14px;
  background:
    linear-gradient(135deg, rgba(64, 158, 255, 0.08), rgba(230, 162, 60, 0.14)),
    #f8fbff;
  box-shadow: 0 10px 24px rgba(48, 65, 86, 0.08);
}

.cost-summary__meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.cost-summary__eyebrow {
  color: #7f8ea3;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.cost-summary__label {
  color: #304156;
  font-size: 14px;
  font-weight: 600;
}

.cost-summary__amount {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.cost-summary__currency {
  color: #d48806;
  font-size: 16px;
  font-weight: 700;
}

.cost-summary__value {
  font-size: 28px;
  line-height: 1;
  font-weight: 800;
  color: #d48806;
  text-shadow: 0 1px 0 rgba(255, 255, 255, 0.7);
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

.cost-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 88px;
  padding: 7px 10px;
  border-radius: 12px;
  font-weight: 700;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.cost-chip:hover {
  transform: translateY(-1px);
}

.cost-chip--price {
  color: #2f6db5;
  background: linear-gradient(135deg, #eef6ff, #dcecff);
  border: 1px solid #bfd8ff;
  box-shadow: 0 6px 14px rgba(64, 158, 255, 0.12);
}

.cost-chip--cost {
  color: #b26a00;
  background: linear-gradient(135deg, #fff7e6, #ffe7ba);
  border: 1px solid #ffd591;
  box-shadow: 0 6px 14px rgba(230, 162, 60, 0.14);
}
</style>
