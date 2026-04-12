import request from '../utils/request'

export function getProductDefinitionList(params) {
  return request({
    url: '/product-definition',
    method: 'get',
    params
  })
}

export function getProductDefinitionDetail(id) {
  return request({
    url: `/product-definition/${id}`,
    method: 'get'
  })
}

export function createProductDefinition(data) {
  return request({
    url: '/product-definition',
    method: 'post',
    data
  })
}

export function updateProductDefinition(id, data) {
  return request({
    url: `/product-definition/${id}`,
    method: 'put',
    data
  })
}

export function deleteProductDefinition(id) {
  return request({
    url: `/product-definition/${id}`,
    method: 'delete'
  })
}
