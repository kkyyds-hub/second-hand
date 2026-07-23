<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BookOpenCheck,
  ChevronDown,
  CircleUserRound,
  Heart,
  House,
  Landmark,
  LogOut,
  MapPin,
  Menu,
  PackagePlus,
  PackageSearch,
  ShieldCheck,
  Store,
  UserRound,
  X,
} from 'lucide-vue-next'
import {
  clearUserSession,
  getUserDisplayName,
  getUserPrimaryContact,
  isSellerUser,
  readCurrentUser,
  type UserProfile,
} from '@/utils/request'
import { USER_APP_TITLE, USER_BRAND_MARK, USER_BRAND_SUBTITLE } from '@/utils/brand'

type NavigationItem = {
  to: string
  label: string
  icon: typeof House
  active: (path: string) => boolean
}

const router = useRouter()
const route = useRoute()
const profileMenuOpen = ref(false)
const mobileMenuOpen = ref(false)
const profileMenuRef = ref<HTMLElement | null>(null)
const currentUser = ref<UserProfile | null>(readCurrentUser())

const appTitle = USER_APP_TITLE
const isSeller = computed(() => isSellerUser(currentUser.value))
const displayName = computed(() => getUserDisplayName(currentUser.value))
const primaryContact = computed(() => getUserPrimaryContact(currentUser.value))
const userInitial = computed(() => displayName.value.trim().charAt(0).toUpperCase() || '我')

const isExact = (target: string) => (path: string) => path === target
const isRouteGroup = (target: string) => (path: string) => path === target || path.startsWith(`${target}/`)

const desktopNavigation = computed<NavigationItem[]>(() => {
  const items: NavigationItem[] = [
    { to: '/', label: '首页', icon: House, active: isExact('/') },
    { to: '/market', label: '逛市场', icon: Store, active: isRouteGroup('/market') },
    { to: '/favorites', label: '收藏', icon: Heart, active: isRouteGroup('/favorites') },
    { to: '/orders/buyer', label: '我的订单', icon: PackageSearch, active: (path) => isRouteGroup('/orders/buyer')(path) || isRouteGroup('/orders/seller')(path) },
  ]

  if (isSeller.value) {
    items.push({ to: '/seller', label: '卖家中心', icon: Store, active: isRouteGroup('/seller') })
  }

  return items
})

const mobileNavigation = computed<NavigationItem[]>(() => {
  const items: NavigationItem[] = [
    { to: '/', label: '首页', icon: House, active: isExact('/') },
    { to: '/market', label: '市场', icon: Store, active: isRouteGroup('/market') },
    isSeller.value
      ? { to: '/seller/products/new', label: '发布', icon: PackagePlus, active: isRouteGroup('/seller/products') }
      : { to: '/favorites', label: '收藏', icon: Heart, active: isRouteGroup('/favorites') },
    { to: '/orders/buyer', label: '订单', icon: PackageSearch, active: (path) => isRouteGroup('/orders/buyer')(path) || isRouteGroup('/orders/seller')(path) },
    { to: '/account', label: '我的', icon: UserRound, active: (path) => isRouteGroup('/account')(path) || isRouteGroup('/assets')(path) },
  ]

  return items
})

const accountMenuItems = computed<NavigationItem[]>(() => {
  const items: NavigationItem[] = [
    { to: '/account', label: '个人中心', icon: CircleUserRound, active: isExact('/account') },
    { to: '/account/addresses', label: '地址管理', icon: MapPin, active: isRouteGroup('/account/addresses') },
    { to: '/account/security/password', label: '账户安全', icon: ShieldCheck, active: isRouteGroup('/account/security') },
    { to: '/assets/wallet', label: '资产中心', icon: Landmark, active: isRouteGroup('/assets') },
  ]

  if (isSeller.value) {
    items.push(
      { to: '/seller', label: '卖家中心', icon: Store, active: isExact('/seller') },
      { to: '/seller/products', label: '我的商品', icon: PackagePlus, active: isRouteGroup('/seller/products') },
      { to: '/orders/seller', label: '卖家订单', icon: BookOpenCheck, active: isRouteGroup('/orders/seller') },
    )
  }

  return items
})

const syncCurrentUser = () => {
  currentUser.value = readCurrentUser()
}

const closeMenus = () => {
  profileMenuOpen.value = false
  mobileMenuOpen.value = false
}

const quickLogout = async () => {
  clearUserSession()
  closeMenus()
  await router.replace('/login')
}

const handleStorageSync = (event: StorageEvent) => {
  if (event.key === null || ['user_profile', 'user_token', 'authentication'].includes(event.key)) {
    syncCurrentUser()
  }
}

const handleDocumentPointerDown = (event: PointerEvent) => {
  if (profileMenuOpen.value && profileMenuRef.value && !profileMenuRef.value.contains(event.target as Node)) {
    profileMenuOpen.value = false
  }
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    closeMenus()
  }
}

watch(
  () => route.fullPath,
  () => {
    syncCurrentUser()
    closeMenus()
  },
)

onMounted(() => {
  syncCurrentUser()
  window.addEventListener('storage', handleStorageSync)
  document.addEventListener('pointerdown', handleDocumentPointerDown)
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('storage', handleStorageSync)
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div class="page-shell text-gray-800">
    <header class="user-site-header">
      <div class="site-container flex h-[68px] items-center justify-between gap-4 px-4 sm:px-6 lg:px-8">
        <router-link class="flex shrink-0 items-center gap-2.5" to="/" aria-label="返回首页">
          <span class="brand-mark h-9 w-9 text-[14px]">{{ USER_BRAND_MARK }}</span>
          <span class="hidden min-w-0 sm:block">
            <span class="block text-[15px] font-semibold text-gray-950">{{ appTitle }}</span>
            <span class="block text-[11px] text-gray-500">{{ USER_BRAND_SUBTITLE }}</span>
          </span>
        </router-link>

        <nav class="hidden items-center gap-1 lg:flex" aria-label="主导航">
          <router-link
            v-for="item in desktopNavigation"
            :key="item.to"
            :to="item.to"
            class="site-nav-link"
            :class="item.active(route.path) ? 'site-nav-link-active' : ''"
          >
            {{ item.label }}
          </router-link>
        </nav>

        <div class="hidden items-center gap-3 lg:flex">
          <router-link v-if="isSeller" class="btn-primary" to="/seller/products/new">
            <PackagePlus class="h-4 w-4" aria-hidden="true" />
            <span>发布闲置</span>
          </router-link>

          <div ref="profileMenuRef" class="relative">
            <button
              class="profile-trigger"
              type="button"
              aria-haspopup="menu"
              :aria-expanded="profileMenuOpen"
              @click="profileMenuOpen = !profileMenuOpen; mobileMenuOpen = false"
            >
              <span class="profile-avatar">{{ userInitial }}</span>
              <span class="max-w-28 truncate text-left text-[13px] font-medium text-gray-800">{{ displayName }}</span>
              <ChevronDown class="h-4 w-4 text-gray-400" aria-hidden="true" />
            </button>

            <transition name="fade">
              <nav v-if="profileMenuOpen" class="account-popover" aria-label="个人菜单">
                <div class="border-b border-gray-100 px-4 py-3">
                  <p class="truncate text-[13px] font-semibold text-gray-900">{{ displayName }}</p>
                  <p class="mt-0.5 truncate text-[11px] text-gray-500">{{ primaryContact }}</p>
                </div>
                <div class="p-2">
                  <router-link
                    v-for="item in accountMenuItems"
                    :key="item.to"
                    :to="item.to"
                    class="account-menu-link"
                    :class="item.active(route.path) ? 'account-menu-link-active' : ''"
                    @click="closeMenus"
                  >
                    <component :is="item.icon" class="h-4 w-4" aria-hidden="true" />
                    <span>{{ item.label }}</span>
                  </router-link>
                </div>
                <div class="border-t border-gray-100 p-2">
                  <button class="account-menu-link w-full" type="button" @click="quickLogout">
                    <LogOut class="h-4 w-4" aria-hidden="true" />
                    <span>退出登录</span>
                  </button>
                </div>
              </nav>
            </transition>
          </div>
        </div>

        <button
          class="icon-button lg:hidden"
          type="button"
          :aria-label="mobileMenuOpen ? '关闭菜单' : '打开菜单'"
          :aria-expanded="mobileMenuOpen"
          @click="mobileMenuOpen = !mobileMenuOpen; profileMenuOpen = false"
        >
          <X v-if="mobileMenuOpen" class="h-5 w-5" aria-hidden="true" />
          <Menu v-else class="h-5 w-5" aria-hidden="true" />
        </button>
      </div>

      <transition name="fade">
        <div v-if="mobileMenuOpen" class="border-t border-gray-200 bg-white lg:hidden">
          <div class="site-container px-4 py-3 sm:px-6 lg:px-8">
            <div class="flex items-center gap-3 border-b border-gray-100 pb-3">
              <span class="profile-avatar">{{ userInitial }}</span>
              <div class="min-w-0">
                <p class="truncate text-[13px] font-semibold text-gray-900">{{ displayName }}</p>
                <p class="truncate text-[11px] text-gray-500">{{ primaryContact }}</p>
              </div>
            </div>
            <nav class="grid grid-cols-2 gap-1 py-3" aria-label="账户导航">
              <router-link
                v-for="item in accountMenuItems"
                :key="item.to"
                :to="item.to"
                class="account-menu-link"
                :class="item.active(route.path) ? 'account-menu-link-active' : ''"
                @click="closeMenus"
              >
                <component :is="item.icon" class="h-4 w-4" aria-hidden="true" />
                <span>{{ item.label }}</span>
              </router-link>
            </nav>
            <button class="btn-default w-full" type="button" @click="quickLogout">
              <LogOut class="h-4 w-4" aria-hidden="true" />
              <span>退出登录</span>
            </button>
          </div>
        </div>
      </transition>
    </header>

    <main class="min-h-[calc(100vh-68px)] px-4 py-5 pb-[calc(5.75rem+env(safe-area-inset-bottom))] sm:px-6 lg:px-8 lg:py-7 lg:pb-9">
      <div class="site-container">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>

    <nav class="mobile-bottom-nav lg:hidden" aria-label="移动端主导航">
      <router-link
        v-for="item in mobileNavigation"
        :key="item.to"
        :to="item.to"
        class="mobile-bottom-link"
        :class="item.active(route.path) ? 'mobile-bottom-link-active' : ''"
        @click="closeMenus"
      >
        <component :is="item.icon" class="h-5 w-5" aria-hidden="true" />
        <span>{{ item.label }}</span>
      </router-link>
    </nav>
  </div>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.16s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
