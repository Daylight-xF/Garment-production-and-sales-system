import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../../../src/views/order/Create.vue', import.meta.url), 'utf8')

assert.match(source, /getSelectionCostPrice/)
assert.match(source, /const selectionCostPrice = getSelectionCostPrice\(productList\.value, row\)/)
assert.match(source, /row\.costPrice = selectionCostPrice/)
assert.match(source, /row\.costPrice = matched\.costPrice \?\? selectionCostPrice \?\? null/)

console.log('order create cost price test passed')
