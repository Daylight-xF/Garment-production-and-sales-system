import request from '../utils/request'

export function getProductionOverview() {
  return request({ url: '/statistics/production/overview', method: 'get' })
}

export function getPlanStatusDistribution() {
  return request({ url: '/statistics/production/plan-status-distribution', method: 'get' })
}

export function getProductProgress() {
  return request({ url: '/statistics/production/product-progress', method: 'get' })
}

export function getSalesOverview() {
  return request({ url: '/statistics/sales/overview', method: 'get' })
}

export function getMonthlySalesTrend(months = 12) {
  return request({ url: '/statistics/sales/monthly-trend', method: 'get', params: { months } })
}

export function getTopProducts(limit = 10) {
  return request({ url: '/statistics/sales/top-products', method: 'get', params: { limit } })
}

export function getInventoryOverview() {
  return request({ url: '/statistics/inventory/overview', method: 'get' })
}

export function getRawMaterialDistribution() {
  return request({ url: '/statistics/inventory/raw-material-distribution', method: 'get' })
}

export function getFinishedProductDistribution() {
  return request({ url: '/statistics/inventory/finished-product-distribution', method: 'get' })
}

export function getAlertStats() {
  return request({ url: '/statistics/inventory/alert-stats', method: 'get' })
}
