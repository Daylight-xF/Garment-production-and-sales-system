import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, getCurrentUser } from '../api/user'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || '{}'))
  const roles = ref(userInfo.value.roles || [])
  const permissions = ref(userInfo.value.permissions || [])

  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => userInfo.value.username || '')
  const realName = computed(() => userInfo.value.realName || '')
  const isAdmin = computed(() => roles.value.includes('admin'))

  function setToken(newToken) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUserInfo(info) {
    userInfo.value = info
    roles.value = info.roles || []
    permissions.value = info.permissions || []
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  async function login(loginData) {
    const res = await loginApi(loginData)
    const data = res.data || res
    setToken(data.token)
    await getUserInfoAction()
    return data
  }

  async function getUserInfoAction() {
    try {
      const res = await getCurrentUser()
      const data = res.data || res
      setUserInfo(data)
      return data
    } catch (error) {
      console.error('获取用户信息失败', error)
      throw error
    }
  }

  function logout() {
    token.value = ''
    userInfo.value = {}
    roles.value = []
    permissions.value = []
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  function hasRole(role) {
    return roles.value.includes(role)
  }

  function hasPermission(permission) {
    return permissions.value.includes(permission)
  }

  return {
    token,
    userInfo,
    roles,
    permissions,
    isLoggedIn,
    username,
    realName,
    isAdmin,
    setToken,
    setUserInfo,
    login,
    getUserInfoAction,
    logout,
    hasRole,
    hasPermission
  }
})
