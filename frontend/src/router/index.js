import { createRouter, createWebHistory } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import MainLayout from '../layout/MainLayout.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue'),
    meta: { title: '注册', requiresAuth: false }
  },
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '首页', roles: ['admin', 'production_manager', 'warehouse_staff', 'sales_staff'] }
      },
      {
        path: 'production/plan',
        name: 'ProductionPlan',
        component: () => import('../views/production/Plan.vue'),
        meta: { title: '生产计划', roles: ['admin', 'production_manager'] }
      },
      {
        path: 'production/task',
        name: 'ProductionTask',
        component: () => import('../views/production/Task.vue'),
        meta: { title: '生产任务', roles: ['admin', 'production_manager'] }
      },
      {
        path: 'production/progress',
        name: 'ProductionProgress',
        component: () => import('../views/production/Progress.vue'),
        meta: { title: '生产进度', roles: ['admin', 'production_manager'] }
      },
      {
        path: 'inventory/raw-material',
        name: 'RawMaterial',
        component: () => import('../views/inventory/RawMaterial.vue'),
        meta: { title: '原材料库存', roles: ['admin', 'warehouse_staff'] }
      },
      {
        path: 'inventory/finished-product',
        name: 'FinishedProduct',
        component: () => import('../views/inventory/FinishedProduct.vue'),
        meta: { title: '成品库存', roles: ['admin', 'warehouse_staff', 'sales_staff'] }
      },
      {
        path: 'inventory/alert',
        name: 'InventoryAlert',
        component: () => import('../views/inventory/Alert.vue'),
        meta: { title: '库存预警', roles: ['admin', 'warehouse_staff'] }
      },
      {
        path: 'inventory/pending-stock-in',
        name: 'PendingStockIn',
        component: () => import('../views/inventory/PendingStockIn.vue'),
        meta: { title: '待入库', roles: ['admin', 'warehouse_staff'] }
      },
      {
        path: 'order/list',
        name: 'OrderList',
        component: () => import('../views/order/List.vue'),
        meta: { title: '订单列表', roles: ['admin', 'sales_staff'] }
      },
      {
        path: 'order/create',
        name: 'OrderCreate',
        component: () => import('../views/order/Create.vue'),
        meta: { title: '创建订单', roles: ['admin', 'sales_staff'] }
      },
      {
        path: 'order/detail/:id',
        name: 'OrderDetail',
        component: () => import('../views/order/Detail.vue'),
        meta: { title: '订单详情', roles: ['admin', 'sales_staff'] }
      },
      {
        path: 'sales/record',
        name: 'SalesRecord',
        component: () => import('../views/sales/Record.vue'),
        meta: { title: '销售记录', roles: ['admin', 'sales_staff'] }
      },
      {
        path: 'sales/report',
        name: 'SalesReport',
        component: () => import('../views/sales/Report.vue'),
        meta: { title: '销售报表', roles: ['admin', 'sales_staff'] }
      },
      {
        path: 'sales/customer',
        name: 'CustomerManage',
        component: () => import('../views/sales/Customer.vue'),
        meta: { title: '客户管理', roles: ['admin', 'sales_staff'] }
      },
      {
        path: 'statistics',
        name: 'Statistics',
        component: () => import('../views/Statistics.vue'),
        meta: { title: '数据统计', roles: ['admin', 'production_manager'] }
      },
      {
        path: 'system/user',
        name: 'UserManage',
        component: () => import('../views/system/UserManage.vue'),
        meta: { title: '用户管理', roles: ['admin'] }
      },
      {
        path: 'system/product-definition',
        name: 'ProductDefinition',
        component: () => import('../views/system/ProductDefinition.vue'),
        meta: { title: '产品定义', roles: ['admin'] }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const whiteList = ['/login', '/register']

router.beforeEach((to, from) => {
  document.title = to.meta.title || '服装生产销售管理系统'

  const token = localStorage.getItem('token')
  const userInfoStr = localStorage.getItem('userInfo')

  if (token) {
    if (to.path === '/login' || to.path === '/register') {
      return { path: '/' }
    }
    
    let user = null
    let roles = []
    
    if (userInfoStr) {
      try {
        user = JSON.parse(userInfoStr)
        roles = user.roles || []
      } catch (e) {
        console.error('解析用户信息失败，清除缓存')
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        return { path: '/login' }
      }
    }
    
    if (!user || roles.length === 0) {
      console.error('用户信息无效，清除缓存')
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      return { path: '/login' }
    }

    if (roles.includes('inactive')) {
      ElMessageBox.alert(
        '该用户还未激活，请联系管理员！',
        '提示',
        {
          confirmButtonText: '确定',
          type: 'warning',
          callback: () => {
            localStorage.removeItem('token')
            localStorage.removeItem('userInfo')
            window.location.href = '/login'
          }
        }
      )
      return { path: '/login' }
    }
    
    if (to.meta.roles && to.meta.roles.length > 0) {
      const hasRole = to.meta.roles.some((role) => roles.includes(role))
      if (!hasRole && to.path !== '/dashboard') {
        return { path: '/dashboard' }
      }
    }
    
    return true
  } else {
    if (whiteList.includes(to.path)) {
      return true
    } else {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
})

export default router
