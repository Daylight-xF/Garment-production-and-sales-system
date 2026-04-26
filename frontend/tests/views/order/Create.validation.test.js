import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../../../src/views/order/Create.vue', import.meta.url), 'utf8')

assert.match(source, /const invalidItem = touchedItems\.find\(item => !item\.selectedProductKey \|\| !item\.color \|\| !item\.size\)/)
assert.doesNotMatch(source, /const invalidItem = touchedItems\.find\(item => !item\.productId \|\| !item\.color \|\| !item\.size\)/)

console.log('order create validation tests passed')
