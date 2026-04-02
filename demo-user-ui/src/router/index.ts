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
          path: 'account/addresses',
          name: 'AccountAddressList',
          component: () => import('@/pages/AddressListPage.vue'),
          /**
           * 该页面虽然挂在受保护的 layout 下，仍显式标注 requiresAuth，
           * 便于后续维护者在路由表里直接识别“地址页必须登录后访问”。
           */
          meta: { requiresAuth: true },
        },
        {
          path: 'account/addresses/new',
          name: 'AccountAddressCreate',
          component: () => import('@/pages/AddressCreatePage.vue'),
          /**
           * 地址新增页属于 Day02 地址管理，必须保持登录保护，
           * 避免未登录用户绕过列表页直接访问新增表单。
           */
          meta: { requiresAuth: true },
        },
        {
          path: 'account/addresses/:addressId/edit',
          name: 'AccountAddressEdit',
          component: () => import('@/pages/AddressEditPage.vue'),
          /**
           * 地址编辑页属于 Day02 edit-only 切片，必须保持登录保护；
           * 同时显式挂路由参数，保证从地址列表进入时能定位目标地址。
           */
          meta: { requiresAuth: true },
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
