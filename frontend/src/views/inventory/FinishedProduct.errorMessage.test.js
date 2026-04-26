import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('./FinishedProduct.vue', import.meta.url), 'utf8')

assert.match(source, /getErrorMessage/)
assert.match(source, /ElMessage\.error\(getErrorMessage\(error, '操作失败'\)\)/)
assert.doesNotMatch(source, /ElMessage\.error\(error\.response\?\.data\?\.message \|\| '操作失败'\)/)

console.log('FinishedProduct error message tests passed')
