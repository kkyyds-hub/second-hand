import { createRouter, createWebHistory } from 'vue-router'
import UserLayout from '@/layouts/UserLayout.vue'
import { buildLoginRedirectPath, readUserToken } from '@/utils/request'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'UserLogin',
      component: () => import('@/pages/LoginPage.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/register/phone',
      name: 'RegisterPhone',
      component: () => import('@/pages/RegisterPhonePage.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/register/email',
      name: 'RegisterEmail',
      component: () => import('@/pages/RegisterEmailPage.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/activate/email',
      name: 'ActivateEmail',
      component: () => import('@/pages/EmailActivatePage.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/',
      component: UserLayout,
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          name: 'UserHome',
          component: () => import('@/pages/HomePage.vue'),
        },
        {
          path: 'account',
          name: 'AccountCenter',
          component: () => import('@/pages/AccountCenterPage.vue'),
        },
        {
          path: 'logout',
          name: 'UserLogout',
          component: () => import('@/pages/LogoutPage.vue'),
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: () => (readUserToken() ? '/' : '/login'),
    },
  ],
})

/**
 * `to.meta` 会合并父子路由的 meta。
 * 这样 layout 级别的 `requiresAuth` 和页面级别的 `guestOnly` 都能在同一个守卫里统一处理。
 */
router.beforeEach((to) => {
  const token = readUserToken()
  const isGuestOnly = Boolean(to.meta.guestOnly)
  const requiresAuth = Boolean(to.meta.requiresAuth)

  if (isGuestOnly && token) {
    return '/'
  }

  /**
   * 登录回跳只允许站内路径，真正的路径净化逻辑下沉到 request.ts。
   * 这样路由守卫与 401 清理都复用同一套 redirect 规则。
   */
  if (requiresAuth && !token) {
    return buildLoginRedirectPath(to.fullPath)
  }

  return true
})

export default router
