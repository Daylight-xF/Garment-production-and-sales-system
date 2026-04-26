import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../../../src/views/inventory/FinishedProduct.vue', import.meta.url), 'utf8')

assert.match(
  source,
  /<el-input\s+v-model="productForm\.batchNo"\s+placeholder="请输入批次号"\s+:disabled="dialogType === 'edit'"\s*\/>/
)

console.log('FinishedProduct edit form tests passed')
