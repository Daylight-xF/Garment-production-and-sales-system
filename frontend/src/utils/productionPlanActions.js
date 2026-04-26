export function canEditPlan(plan) {
  return plan?.status !== 'COMPLETED' && plan?.status !== 'CANCELLED'
}

export function canCompletePlan(plan) {
  if (plan?.status !== 'IN_PROGRESS') {
    return false
  }
  const completedQuantity = plan.completedQuantity || 0
  const quantity = plan.quantity || 0
  return quantity > 0 && completedQuantity >= quantity
}
