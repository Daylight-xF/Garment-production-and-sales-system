import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../../../src/views/inventory/FinishedProduct.vue', import.meta.url), 'utf8')

assert.match(
  source,
  /onMounted\(\(\) => \{\s*if \(canCreateOrEditProduct\.value\) \{\s*fetchProductDefinitions\(\)\s*\}\s*fetchFinishedProductCategories\(\)\s*fetchList\(\)\s*\}/s
)

console.log('FinishedProduct permission tests passed')
