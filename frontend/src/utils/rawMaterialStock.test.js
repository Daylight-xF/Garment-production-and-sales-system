import assert from 'node:assert/strict'

let module = {}

try {
  module = await import('./rawMaterialStock.js')
} catch {
  module = {}
}

assert.equal(typeof module.getRawMaterialStockMaxQuantity, 'function')

assert.equal(
  module.getRawMaterialStockMaxQuantity({
    stockType: 'IN',
    stockLocation: '',
    currentItem: { quantity: 100, locations: [{ location: 'A-01', quantity: 40 }] }
  }),
  999999
)

assert.equal(
  module.getRawMaterialStockMaxQuantity({
    stockType: 'OUT',
    stockLocation: '',
    currentItem: { quantity: 100, locations: [{ location: 'A-01', quantity: 40 }] }
  }),
  100
)

assert.equal(
  module.getRawMaterialStockMaxQuantity({
    stockType: 'OUT',
    stockLocation: 'A-01',
    currentItem: { quantity: 100, locations: [{ location: 'A-01', quantity: 40 }] }
  }),
  40
)
