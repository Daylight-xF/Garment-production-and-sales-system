import request from '../utils/request'

export function getOrderList(params) {
  return request({
    url: '/orders',
    method: 'get',
    params
  })
}

export function getOrderDetail(id) {
  return request({
    url: `/orders/${id}`,
    method: 'get'
  })
}

export function createOrder(data) {
  return request({
    url: '/orders',
    method: 'post',
    data
  })
}

export function updateOrder(id, data) {
  return request({
    url: `/orders/${id}`,
    method: 'put',
    data
  })
}

export function cancelOrder(id) {
  return request({
    url: `/orders/${id}/cancel`,
    method: 'put'
  })
}

export function approveOrder(id, data) {
  return request({
    url: `/orders/${id}/approve`,
    method: 'put',
    data
  })
}

export function rejectOrder(id, data) {
  return request({
    url: `/orders/${id}/reject`,
    method: 'put',
    data
  })
}

export function getOrderLogs(id) {
  return request({
    url: `/orders/${id}/logs`,
    method: 'get'
  })
}

export function shipOrder(id) {
  return request({
    url: `/orders/${id}/ship`,
    method: 'put'
  })
}

export function completeOrder(id) {
  return request({
    url: `/orders/${id}/complete`,
    method: 'put'
  })
}
