import request from '../utils/request'

export function getRawMaterialList(params) {
  return request({
    url: '/inventory/raw-materials',
    method: 'get',
    params
  })
}

export function getRawMaterialDetail(id) {
  return request({
    url: `/inventory/raw-materials/${id}`,
    method: 'get'
  })
}

export function createRawMaterial(data) {
  return request({
    url: '/inventory/raw-materials',
    method: 'post',
    data
  })
}

export function updateRawMaterial(id, data) {
  return request({
    url: `/inventory/raw-materials/${id}`,
    method: 'put',
    data
  })
}

export function deleteRawMaterial(id) {
  return request({
    url: `/inventory/raw-materials/${id}`,
    method: 'delete'
  })
}

export function setRawMaterialThreshold(id, data) {
  return request({
    url: `/inventory/raw-materials/${id}/threshold`,
    method: 'put',
    data
  })
}

export function getFinishedProductList(params) {
  return request({
    url: '/inventory/finished-products',
    method: 'get',
    params
  })
}

export function getFinishedProductDetail(id) {
  return request({
    url: `/inventory/finished-products/${id}`,
    method: 'get'
  })
}

export function createFinishedProduct(data) {
  return request({
    url: '/inventory/finished-products',
    method: 'post',
    data
  })
}

export function updateFinishedProduct(id, data) {
  return request({
    url: `/inventory/finished-products/${id}`,
    method: 'put',
    data
  })
}

export function deleteFinishedProduct(id) {
  return request({
    url: `/inventory/finished-products/${id}`,
    method: 'delete'
  })
}

export function setFinishedProductThreshold(id, data) {
  return request({
    url: `/inventory/finished-products/${id}/threshold`,
    method: 'put',
    data
  })
}

export function stockIn(data) {
  return request({
    url: '/inventory/stock-in',
    method: 'post',
    data
  })
}

export function stockOut(data) {
  return request({
    url: '/inventory/stock-out',
    method: 'post',
    data
  })
}

export function getInventoryRecords(params) {
  return request({
    url: '/inventory/records',
    method: 'get',
    params
  })
}

export function getAlertList(params) {
  return request({
    url: '/inventory/alerts',
    method: 'get',
    params
  })
}

export function handleAlert(id, data) {
  return request({
    url: `/inventory/alerts/${id}/handle`,
    method: 'put',
    data
  })
}
