import assert from 'node:assert/strict'

let module = {}

try {
  module = await import('../../src/utils/productCost.js')
} catch {
  module = {}
}

assert.equal(typeof module.getMaterialCurrentPrice, 'function')
assert.equal(typeof module.calculateMaterialCost, 'function')
assert.equal(typeof module.calculateProductUnitCost, 'function')

const rawMaterialList = [
  { id: 'm1', price: 8 },
  { id: 'm2', price: 0.5 }
]

assert.equal(
  module.getMaterialCurrentPrice({ materialId: 'm1', materialPrice: 6 }, rawMaterialList),
  8
)

assert.equal(
  module.calculateMaterialCost({ materialId: 'm1', quantity: 2.5 }, rawMaterialList),
  20
)

assert.equal(
  module.calculateProductUnitCost([
    { materialId: 'm1', quantity: 2.5 },
    { materialId: 'm2', quantity: 4 }
  ], rawMaterialList),
  22
)
