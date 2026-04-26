import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../../../src/views/order/Create.vue', import.meta.url), 'utf8')

assert.match(source, /import \{ getErrorMessage \} from '..\/..\/utils\/errorMessage'/)
assert.match(source, /ElMessage\.error\(getErrorMessage\(error, '订单创建失败'\)\)/)

console.log('order create error message tests passed')
