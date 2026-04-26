import assert from 'node:assert/strict'
import { getErrorMessage } from './errorMessage.js'

assert.equal(
  getErrorMessage(new Error('订单发货失败，以下商品库存不足：连衣裙-n2/撒啊/L（需 10，现有 6）'), '发货失败'),
  '订单发货失败，以下商品库存不足：连衣裙-n2/撒啊/L（需 10，现有 6）'
)

assert.equal(
  getErrorMessage({ response: { data: { message: '库存不足' } } }, '发货失败'),
  '库存不足'
)

assert.equal(getErrorMessage({}, '发货失败'), '发货失败')

console.log('errorMessage tests passed')
