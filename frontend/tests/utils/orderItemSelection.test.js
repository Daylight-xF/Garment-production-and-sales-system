import assert from 'node:assert/strict'

import {
  buildProductOptions,
  getAvailableProductOptions,
  getAvailableColors,
  getAvailableSizes,
  findMatchedFinishedProduct,
  getSelectionCostPrice
} from '../../src/utils/orderItemSelection.js'

const inventory = [
  { id: '1', name: 'TShirt', productCode: 'n1', color: 'Red', size: 'M', costPrice: 20 },
  { id: '2', name: 'TShirt', productCode: 'n1', color: 'Red', size: 'L', costPrice: 22 },
  { id: '3', name: 'TShirt', productCode: 'n1', color: 'White', size: 'M', costPrice: 21 },
  { id: '4', name: 'Hoodie', productCode: 'w1', color: 'Black', size: 'XL', costPrice: 40 }
]

assert.deepEqual(buildProductOptions(inventory), [
  { key: 'TShirt||n1', label: 'TShirt-n1', productName: 'TShirt', productCode: 'n1' },
  { key: 'Hoodie||w1', label: 'Hoodie-w1', productName: 'Hoodie', productCode: 'w1' }
])

assert.deepEqual(
  getAvailableProductOptions(inventory, { selectedProductKey: '', color: 'Red', size: 'M' }),
  [{ key: 'TShirt||n1', label: 'TShirt-n1', productName: 'TShirt', productCode: 'n1' }]
)

assert.deepEqual(
  getAvailableColors(inventory, { selectedProductKey: 'TShirt||n1', color: '', size: '' }),
  ['Red', 'White']
)

assert.deepEqual(
  getAvailableSizes(inventory, { selectedProductKey: 'TShirt||n1', color: 'Red', size: '' }),
  ['L', 'M']
)

assert.equal(
  findMatchedFinishedProduct(inventory, { selectedProductKey: 'TShirt||n1', color: 'White', size: 'M' }).id,
  '3'
)

assert.equal(
  findMatchedFinishedProduct(inventory, { selectedProductKey: 'TShirt||n1', color: 'Red', size: '' }),
  null
)

assert.equal(
  findMatchedFinishedProduct(inventory, { selectedProductKey: 'TShirt||n1', color: 'White', size: '' }),
  null
)

assert.equal(
  findMatchedFinishedProduct(inventory, { selectedProductKey: '', color: 'White', size: 'M' }),
  null
)

const multiBatchInventory = [
  { id: 'b1', name: 'Dress', productCode: 'n2', color: 'Pink', size: 'M', costPrice: 6 },
  { id: 'b2', name: 'Dress', productCode: 'n2', color: 'Pink', size: 'M', costPrice: 6 },
  { id: 'b3', name: 'Dress', productCode: 'n2', color: 'Pink', size: 'L', costPrice: 8 }
]

assert.equal(
  findMatchedFinishedProduct(multiBatchInventory, { selectedProductKey: 'Dress||n2', color: 'Pink', size: 'M' }),
  null
)

assert.equal(
  getSelectionCostPrice(multiBatchInventory, { selectedProductKey: 'Dress||n2', color: 'Pink', size: 'M' }),
  6
)

assert.equal(
  getSelectionCostPrice([
    ...multiBatchInventory,
    { id: 'b4', name: 'Dress', productCode: 'n2', color: 'Pink', size: 'M', costPrice: 7 }
  ], { selectedProductKey: 'Dress||n2', color: 'Pink', size: 'M' }),
  null
)
