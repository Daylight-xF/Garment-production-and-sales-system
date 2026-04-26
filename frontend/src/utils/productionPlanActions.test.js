import assert from 'node:assert/strict'
import { canCompletePlan, canEditPlan } from './productionPlanActions.js'

assert.equal(canEditPlan({
  status: 'IN_PROGRESS',
  quantity: 3,
  completedQuantity: 3
}), true)

assert.equal(canEditPlan({ status: 'COMPLETED' }), false)
assert.equal(canEditPlan({ status: 'CANCELLED' }), false)

assert.equal(canCompletePlan({
  status: 'IN_PROGRESS',
  quantity: 3,
  completedQuantity: 3
}), true)

assert.equal(canCompletePlan({
  status: 'APPROVED',
  quantity: 3,
  completedQuantity: 3
}), false)

assert.equal(canCompletePlan({
  status: 'IN_PROGRESS',
  quantity: 4,
  completedQuantity: 3
}), false)
