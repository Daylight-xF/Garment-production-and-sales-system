import request from '../utils/request'

export function getSalesRecordList(params) {
  return request({
    url: '/sales/records',
    method: 'get',
    params
  })
}

export function getSalesRecordDetail(id) {
  return request({
    url: `/sales/records/${id}`,
    method: 'get'
  })
}

export function createSalesRecord(data) {
  return request({
    url: '/sales/records',
    method: 'post',
    data
  })
}

export function updateSalesRecord(id, data) {
  return request({
    url: `/sales/records/${id}`,
    method: 'put',
    data
  })
}

export function deleteSalesRecord(id) {
  return request({
    url: `/sales/records/${id}`,
    method: 'delete'
  })
}

export function getSalesOverview() {
  return request({
    url: '/sales/report/overview',
    method: 'get'
  })
}

export function getSalesTrend(params) {
  return request({
    url: '/sales/report/trend',
    method: 'get',
    params
  })
}

export function getProductRanking(params) {
  return request({
    url: '/sales/report/product-ranking',
    method: 'get',
    params
  })
}

export function getCategoryDistribution(params) {
  return request({
    url: '/sales/report/category-distribution',
    method: 'get',
    params
  })
}

export function getCustomerList(params) {
  return request({
    url: '/sales/customers',
    method: 'get',
    params
  })
}

export function getCustomerDetail(id) {
  return request({
    url: `/sales/customers/${id}`,
    method: 'get'
  })
}

export function createCustomer(data) {
  return request({
    url: '/sales/customers',
    method: 'post',
    data
  })
}

export function updateCustomer(id, data) {
  return request({
    url: `/sales/customers/${id}`,
    method: 'put',
    data
  })
}

export function deleteCustomer(id) {
  return request({
    url: `/sales/customers/${id}`,
    method: 'delete'
  })
}
