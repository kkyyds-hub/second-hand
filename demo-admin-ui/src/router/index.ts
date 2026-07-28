import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import { buildAdminDocumentTitle } from '@/utils/adminBrand'
import { ADMIN_HOME_PATH, createAdminLoginPath, sanitizeAdminRedirect } from '@/utils/adminRoute'
import { readAdminToken } from '@/utils/request'

declare module 'vue-router' {
  interface RouteMeta {
    title: string
    requiresAuth: boolean
    guestOnly: boolean
  }
}

const protectedRoutes: RouteRecordRaw[] = [
  { path: '', name: 'Dashboard', component: () => import('@/pages/Dashboard.vue'), meta: { title: '运营工作台', requiresAuth: true, guestOnly: false } },
  { path: 'users', name: 'UserList', component: () => import('@/pages/users/UserList.vue'), meta: { title: '用户与商家', requiresAuth: true, guestOnly: false } },
  { path: 'products', name: 'ProductReview', component: () => import('@/pages/products/ProductReview.vue'), meta: { title: '商品审核', requiresAuth: true, guestOnly: false } },
  { path: 'orders', name: 'AdminOrderList', component: () => import('@/pages/orders/OrderList.vue'), meta: { title: '订单管理', requiresAuth: true, guestOnly: false } },
  { path: 'audit', name: 'AuditCenter', component: () => import('@/pages/audit/AuditCenter.vue'), meta: { title: '纠纷与违规', requiresAuth: true, guestOnly: false } },
  { path: 'ops-center', name: 'OpsCenter', component: () => import('@/pages/ops/OpsCenter.vue'), meta: { title: '运维中心', requiresAuth: true, guestOnly: false } },
  { path: 'settings', name: 'SystemSettings', component: () => import('@/pages/settings/SystemSettings.vue'), meta: { title: '系统设置', requiresAuth: true, guestOnly: false } },
  { path: 'logout', name: 'LogoutPage', component: () => import('@/pages/LogoutPage.vue'), meta: { title: '退出登录', requiresAuth: true, guestOnly: false } },
]

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: (_to, _from, savedPosition) => savedPosition || { top: 0 },
  routes: [
    { path: '/login', name: 'Login', component: () => import('@/pages/Login.vue'), meta: { title: '登录', requiresAuth: false, guestOnly: true } },
    { path: '/', component: MainLayout, children: protectedRoutes },
    { path: '/:pathMatch(.*)*', component: MainLayout, children: [{ path: '', name: 'NotFound', component: () => import('@/pages/NotFoundPage.vue'), meta: { title: '页面未找到', requiresAuth: true, guestOnly: false } }], meta: { title: '页面未找到', requiresAuth: true, guestOnly: false } },
  ],
})

router.beforeEach((to) => {
  const token = readAdminToken()
  if (to.meta.requiresAuth && !token) return createAdminLoginPath(to.fullPath)
  if (to.meta.guestOnly && token) return sanitizeAdminRedirect(to.query.redirect as string | undefined) || ADMIN_HOME_PATH
  return true
})

const applyDocumentTitle = (title: unknown) => {
  document.title = buildAdminDocumentTitle(typeof title === 'string' ? title : '运营工作台')
}

router.afterEach((to) => applyDocumentTitle(to.meta.title))
void router.isReady().then(() => applyDocumentTitle(router.currentRoute.value.meta.title))

export default router
