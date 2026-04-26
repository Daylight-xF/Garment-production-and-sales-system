import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(__dirname, 'UserManage.vue'), 'utf8')

assert.match(source, /getErrorMessage/)
assert.doesNotMatch(source, /error\.response\?\.data\?\.message/)

console.log('UserManage error message tests passed')
