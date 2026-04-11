import request from '../utils/request'

export function getPlanList(params) {
  return request({
    url: '/production/plans',
    method: 'get',
    params
  })
}

export function getPlanDetail(id) {
  return request({
    url: `/production/plans/${id}`,
    method: 'get'
  })
}

export function createPlan(data) {
  return request({
    url: '/production/plans',
    method: 'post',
    data
  })
}

export function updatePlan(id, data) {
  return request({
    url: `/production/plans/${id}`,
    method: 'put',
    data
  })
}

export function deletePlan(id) {
  return request({
    url: `/production/plans/${id}`,
    method: 'delete'
  })
}

export function approvePlan(id, data) {
  return request({
    url: `/production/plans/${id}/approve`,
    method: 'put',
    data
  })
}

export function getTaskList(params) {
  return request({
    url: '/production/tasks',
    method: 'get',
    params
  })
}

export function getTaskDetail(id) {
  return request({
    url: `/production/tasks/${id}`,
    method: 'get'
  })
}

export function createTask(data) {
  return request({
    url: '/production/tasks',
    method: 'post',
    data
  })
}

export function updateTask(id, data) {
  return request({
    url: `/production/tasks/${id}`,
    method: 'put',
    data
  })
}

export function assignTask(id, data) {
  return request({
    url: `/production/tasks/${id}/assign`,
    method: 'put',
    data
  })
}

export function updateTaskProgress(id, data) {
  return request({
    url: `/production/tasks/${id}/progress`,
    method: 'put',
    data
  })
}
