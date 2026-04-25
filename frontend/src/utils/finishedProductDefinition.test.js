import assert from 'node:assert/strict'

import {
  applyProductDefinitionToFinishedProductForm,
  buildFinishedProductPayload,
  formatFinishedProductDefinitionLabel,
  getFinishedProductFormDisplayName
} from './finishedProductDefinition.js'

assert.equal(
  formatFinishedProductDefinitionLabel({ productName: '卫衣', productCode: 'W001' }),
  '卫衣-W001'
)

assert.equal(
  getFinishedProductFormDisplayName({ name: 'Casual Pants', productCode: 'N001' }),
  'Casual Pants-N001'
)

const result = applyProductDefinitionToFinishedProductForm(
  {
    batchNo: 'FP-001',
    name: '',
    productCode: '',
    category: '',
    unit: '件',
    location: 'A-01'
  },
  {
    id: 'def-1',
    productName: '卫衣',
    productCode: 'W001',
    category: '上装'
  }
)

assert.deepEqual(result, {
  batchNo: 'FP-001',
  productDefinitionId: 'def-1',
  name: '卫衣',
  productCode: 'W001',
  category: '上装',
  unit: '件',
  location: 'A-01'
})

const payload = buildFinishedProductPayload({
  productDefinitionId: 'def-1',
  batchNo: 'FP-001',
  name: '卫衣',
  productCode: 'W001',
  category: '上装',
  color: '黑色',
  size: 'L',
  unit: '件',
  quantity: 10,
  alertThreshold: 2,
  location: 'A-01',
  description: 'test'
})

assert.equal(payload.productCode, 'W001')
assert.equal(payload.name, '卫衣')
assert.equal(payload.category, '上装')
assert.equal(payload.location, 'A-01')
assert.ok(!('productDefinitionId' in payload))

console.log('finishedProductDefinition tests passed')
