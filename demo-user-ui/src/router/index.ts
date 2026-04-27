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
          path: 'assets',
          redirect: '/assets/wallet',
          /**
           * Day07 Package-1：资产中心总入口，当前默认进入钱包页。
           */
          meta: { requiresAuth: true },
        },
        {
          path: 'assets/wallet',
          name: 'AssetWallet',
          component: () => import('@/pages/assets/WalletPage.vue'),
          /**
           * Day07 Package-1：钱包余额 / 流水 / 提现申请最小闭环。
           */
          meta: { requiresAuth: true },
        },
        {
          path: 'assets/points',
          name: 'AssetPoints',
          component: () => import('@/pages/assets/PointsPage.vue'),
          /**
           * Day07 Package-1：积分总额 / 积分流水最小闭环。
           */
          meta: { requiresAuth: true },
        },
        {
          path: 'assets/credit',
          name: 'AssetCredit',
          component: () => import('@/pages/assets/CreditPage.vue'),
          /**
           * Day07 Package-1：信用概览 / 信用流水最小闭环。
           */
          meta: { requiresAuth: true },
        },
        {
          path: 'seller',
          name: 'SellerWorkbench',
          component: () => import('@/pages/seller/SellerWorkbenchPage.vue'),
          /**
           * Day04 第一包入口：卖家工作台只读入口。
           * 写操作链路（创建/编辑/删除/状态流转）留到下一包。
           */
          meta: { requiresAuth: true },
        },
        {
          path: 'seller/products',
          name: 'SellerProductList',
          component: () => import('@/pages/seller/UserProductListPage.vue'),
          /**
           * Day04 第二包升级：我的商品列表承接 create/edit/delete/状态流转入口。
           */
          meta: { requiresAuth: true },
        },
        {
          path: 'seller/products/new',
          name: 'SellerProductCreate',
          component: () => import('@/pages/seller/UserProductCreatePage.vue'),
          /**
           * 固定放在 `:productId` 动态路由前面，避免 `/seller/products/new` 被错误解析为详情页参数。
           */
          meta: { requiresAuth: true },
        },
        {
          path: 'seller/products/:productId/edit',
          name: 'SellerProductEdit',
          component: () => import('@/pages/seller/UserProductEditPage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'seller/products/:productId',
          name: 'SellerProductDetail',
          component: () => import('@/pages/seller/UserProductDetailPage.vue'),
          /**
           * Day04 第二包升级：详情页提供编辑、删除与状态写操作入口。
           */
          meta: { requiresAuth: true },
        },
        {
          path: 'account/security/password',
          name: 'AccountPassword',
          component: () => import('@/pages/AccountPasswordPage.vue'),
          /**
           * This preserves the existing Day02 security slice as its own independent page.
           */
          meta: { requiresAuth: true },
        },
        {
          path: 'account/security/email',
          name: 'AccountEmailBinding',
          component: () => import('@/pages/AccountEmailBindingPage.vue'),
          /**
           * Day02 security is split by slice. This route is dedicated to email bind/unbind only.
           */
          meta: { requiresAuth: true },
        },
        {
          path: 'account/security/phone',
          name: 'AccountPhoneBinding',
          component: () => import('@/pages/AccountPhoneBindingPage.vue'),
          /**
           * Day02 security is split by slice. This route is dedicated to phone bind/unbind only.
           */
          meta: { requiresAuth: true },
        },
        {
          path: 'account/profile',
          name: 'AccountProfileEdit',
          component: () => import('@/pages/AccountProfileEditPage.vue'),
          /**
           * 资料编辑从账户中心拆到独立页面，避免继续把 Day02 能力堆回总览页。
           */
          meta: { requiresAuth: true },
        },
        {
          path: 'account/avatar',
          name: 'AccountAvatarUpload',
          component: () => import('@/pages/AccountAvatarUploadPage.vue'),
          /**
           * 头像上传属于 Day02 两步链路切片，必须保留独立入口和登录保护。
           */
          meta: { requiresAuth: true },
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
          path: 'market',
          name: 'MarketList',
          component: () => import('@/pages/market/MarketListPage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'market/:productId',
          name: 'MarketDetail',
          component: () => import('@/pages/market/MarketDetailPage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'favorites',
          name: 'FavoriteList',
          component: () => import('@/pages/market/FavoriteListPage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'reviews/mine',
          name: 'MyReviews',
          component: () => import('@/pages/market/MyReviewsPage.vue'),
          /**
           * Day03 第二包“我的评价入口”保留独立路由，供后续 verify 线程做最小链路验证。
           */
          meta: { requiresAuth: true },
        },
        {
          path: 'orders/buyer',
          name: 'BuyerOrders',
          component: () => import('@/pages/orders/BuyerOrdersPage.vue'),
          /**
           * Day05 Package-1：买家订单列表只读入口。
           * 支付/取消/确认收货/售后写链路留到后续包分段接入。
           */
          meta: { requiresAuth: true },
        },
        {
          path: 'orders/buyer/:orderId',
          name: 'BuyerOrderDetail',
          component: () => import('@/pages/orders/BuyerOrderDetailPage.vue'),
          /**
           * Day05 Package-1：详情页保持只读，先稳定接入 `GET /user/orders/{orderId}`。
           */
          meta: { requiresAuth: true },
        },
        {
          path: 'orders/seller',
          name: 'SellerOrders',
          component: () => import('@/pages/orders/SellerOrdersPage.vue'),
          /**
           * Day06 Package-1：卖家订单列表作为 seller fulfillment 主链入口。
           * 先把列表、详情、物流查看、发货串在一起，不把第一包过早拆成多个孤立页面。
           */
          meta: { requiresAuth: true },
        },
        {
          path: 'orders/seller/after-sales/decision',
          name: 'SellerAfterSaleDecision',
          component: () => import('@/pages/orders/SellerAfterSaleDecisionPage.vue'),
          /**
           * Day06 Package-3：卖家售后处理独立页面。
           * 由于当前用户端没有售后查询接口，也没有稳定 afterSaleId 来源，
           * 页面只承接“手动输入 / URL query 预填 afterSaleId + seller decision 提交”。
           */
          meta: { requiresAuth: true },
        },
        {
          path: 'orders/seller/:orderId',
          name: 'SellerOrderDetail',
          component: () => import('@/pages/orders/SellerOrderDetailPage.vue'),
          /**
           * Day06 Package-1：详情页承接物流查看与发货表单，
           * 继续保持“一个线程先打通最核心 seller fulfill 链路”的边界。
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
