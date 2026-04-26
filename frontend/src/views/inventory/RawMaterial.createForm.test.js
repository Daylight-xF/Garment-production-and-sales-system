import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(__dirname, 'RawMaterial.vue'), 'utf8')

const quantityItem = source.match(/<el-form-item v-if="dialogType === 'add'" label="库存数量"[\s\S]*?<\/el-form-item>/)?.[0] || ''
const locationItem = source.match(/<el-form-item label="存放位置"[\s\S]*?<\/el-form-item>/)?.[0] || ''

assert.ok(quantityItem, 'raw material create quantity field should exist')
assert.ok(locationItem, 'raw material location field should exist')
assert.doesNotMatch(quantityItem, /:required="dialogType === 'add'"/)
assert.doesNotMatch(locationItem, /:required="dialogType === 'add'"/)
assert.match(quantityItem, /:min="0"/)
assert.doesNotMatch(source, /dialogType\.value === 'add' && \(!Number\.isFinite\(Number\(value\)\) \|\| Number\(value\) <= 0\)/)
assert.doesNotMatch(source, /dialogType\.value === 'add' && !String\(value \|\| ''\)\.trim\(\)/)

console.log('RawMaterial create form tests passed')
