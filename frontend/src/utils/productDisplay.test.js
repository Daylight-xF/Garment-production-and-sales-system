import assert from 'node:assert/strict'

let module = {}

try {
  module = await import('./productDisplay.js')
} catch {
  module = {}
}

assert.equal(typeof module.formatProductDisplayName, 'function')
assert.equal(
  module.formatProductDisplayName({ name: 'T恤', productCode: 'P001' }),
  'T恤-P001'
)
assert.equal(
  module.formatProductDisplayName({ name: 'T恤', productCode: '' }),
  'T恤'
)
