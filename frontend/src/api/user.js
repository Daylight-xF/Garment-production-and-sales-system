import request from '../utils/request'

export function login(data) {
  return request({
    url: '/auth/login',
    method: 'post',
    data
  })
}

export function register(data) {
  return request({
    url: '/auth/register',
    method: 'post',
    data
  })
}

export function getUserList(params) {
  return request({
    url: '/users',
    method: 'get',
    params
  })
}

export function getAssignableUsers() {
  return request({
    url: '/users/assignable',
    method: 'get'
  })
}

export function getUserDetail(id) {
  return request({
    url: `/users/${id}`,
    method: 'get'
  })
}

export function createUser(data) {
  return request({
    url: '/users',
    method: 'post',
    data
  })
}

export function updateUser(id, data) {
  return request({
    url: `/users/${id}`,
    method: 'put',
    data
  })
}

export function deleteUser(id) {
  return request({
    url: `/users/${id}`,
    method: 'delete'
  })
}

export function assignRoles(id, data) {
  return request({
    url: `/users/${id}/roles`,
    method: 'put',
    data
  })
}

export function updateUserStatus(id, data) {
  return request({
    url: `/users/${id}/status`,
    method: 'put',
    data
  })
}

export function getCurrentUser() {
  return request({
    url: '/users/info',
    method: 'get'
  })
}

export function getRoleList() {
  return request({
    url: '/roles',
    method: 'get'
  })
}

export function changePassword(data) {
  return request({
    url: '/users/password',
    method: 'put',
    data
  })
}
