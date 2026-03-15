import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import { readAdminToken } from '@/utils/request'

/**
 * 路由分层约定：
 * - /login 独立渲染，避免登录页复用后台壳子
 * - 其余业务页统一挂在 MainLayout 下，共享侧边栏和顶栏
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/pages/Login.vue'),
    },
    {
      path: '/',
      component: MainLayout,
      children: [
        {
          path: '',
          name: 'Dashboard',
          component: () => import('@/pages/Dashboard.vue'),
        },
        {
          path: 'users',
          name: 'UserList',
          component: () => import('@/pages/users/UserList.vue'),
        },
        {
          path: 'products',
          name: 'ProductReview',
          component: () => import('@/pages/products/ProductReview.vue'),
        },
        {
          path: 'settings',
          name: 'SystemSettings',
          component: () => import('@/pages/settings/SystemSettings.vue'),
        },
        {
          path: 'ops-center',
          name: 'OpsCenter',
          component: () => import('@/pages/ops/OpsCenter.vue'),
        },
        {
          path: 'audit',
          name: 'AuditCenter',
          component: () => import('@/pages/audit/AuditCenter.vue'),
        },
        {
          path: 'logout',
          name: 'LogoutPage',
          component: () => import('@/pages/LogoutPage.vue'),
        },
      ],
    },
  ],
})

/**
 * 这里刻意只保留“最小登录态守卫”，不在路由层做复杂角色判断：
 * - 已登录访问登录页时回首页
 * - 未登录访问业务页时回登录页
 * review 跳转问题时，先确认 token 是否存在即可。
 */
router.beforeEach((to, _from, next) => {
  const token = readAdminToken()

  if (to.path === '/login' && token) {
    next('/')
    return
  }

  if (to.path !== '/login' && !token) {
    next('/login')
    return
  }

  next()
})

export default router
