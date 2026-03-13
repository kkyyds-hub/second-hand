import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import { readAdminToken } from '@/utils/request'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/pages/Login.vue')
    },
    {
      path: '/',
      component: MainLayout,
      children: [
        {
          path: '',
          name: 'Dashboard',
          component: () => import('@/pages/Dashboard.vue')
        },
        {
          path: 'users',
          name: 'UserList',
          component: () => import('@/pages/users/UserList.vue')
        },
        {
          path: 'products',
          name: 'ProductReview',
          component: () => import('@/pages/products/ProductReview.vue')
        },
        {
          path: 'settings',
          name: 'SystemSettings',
          component: () => import('@/pages/settings/SystemSettings.vue')
        },
        {
          path: 'ops-center',
          name: 'OpsCenter',
          component: () => import('@/pages/ops/OpsCenter.vue')
        },
        {
          path: 'audit',
          name: 'AuditCenter',
          component: () => import('@/pages/audit/AuditCenter.vue')
        },
        {
          path: 'logout',
          name: 'LogoutPage',
          component: () => import('@/pages/LogoutPage.vue')
        }
      ]
    }
  ]
})

// 路由守卫：实现最小可用的登录拦截
router.beforeEach((to, _from, next) => {
  // 获取本地 token
  const token = readAdminToken()
  
  // 如果访问的是登录页，且已经有 token，直接跳首页
  if (to.path === '/login' && token) {
    next('/')
    return
  }
  
  // 如果访问的页面不是登录页，且没有 token，强制跳回登录页
  if (to.path !== '/login' && !token) {
    next('/login')
    return
  }
  
  // 其他情况正常放行
  next()
})

export default router
