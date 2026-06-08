import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(__dirname, '../../../src/views/system/UserManage.vue'), 'utf8')

assert.match(source, /getErrorMessage/)
assert.doesNotMatch(source, /error\.response\?\.data\?\.message/)
assert.match(source, /admin账户为系统内置账户，无法删除/)
assert.match(source, /row\.username === 'admin'/)
assert.match(source, /useUserStore/)
assert.match(source, /ADMIN_OPERATE_TIP/)
assert.match(source, /canOperateBuiltInAdmin/)
assert.match(source, /userStore\.username === 'admin'/)
assert.match(source, /canOperateBuiltInAdmin\(row\) \? '' : ADMIN_OPERATE_TIP/)
assert.match(source, /handleProtectedAdminAction\(row, handleEdit\)/)
assert.match(source, /ADMIN_ROLE_STATUS_TIP/)
assert.match(source, /canChangeBuiltInAdminRoleOrStatus/)
assert.match(source, /canChangeBuiltInAdminRoleOrStatus\(row\) \? '' : ADMIN_ROLE_STATUS_TIP/)
assert.match(source, /handleProtectedRoleStatusAction\(row, handleAssignRole\)/)
assert.match(source, /handleProtectedRoleStatusAction\(row, handleToggleStatus\)/)

console.log('UserManage error message test passed')
