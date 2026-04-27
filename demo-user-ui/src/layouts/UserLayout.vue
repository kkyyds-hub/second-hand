<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { House, LogOut, Menu, PackageSearch, Store, Truck, UserRound, Wallet, X } from 'lucide-vue-next'
import {
  clearUserSession,
  getUserDisplayName,
  getUserPrimaryContact,
  isSellerUser,
  readCurrentUser,
  type UserProfile,
} from '@/utils/request'
import { USER_APP_TITLE, USER_BRAND_MARK, USER_BRAND_SUBTITLE, USER_SESSION_STATUS_TEXT } from '@/utils/brand'

const router = useRouter()
const route = useRoute()
const menuOpen = ref(false)
const currentUser = ref<UserProfile | null>(readCurrentUser())

const appTitle = import.meta.env.VITE_APP_TITLE?.trim() || USER_APP_TITLE

const syncCurrentUser = () => {
  currentUser.value = readCurrentUser()
}

const displayName = computed(() => getUserDisplayName(currentUser.value))
const primaryContact = computed(() => getUserPrimaryContact(currentUser.value))
const roleText = computed(() => (isSellerUser(currentUser.value) ? '卖家账号' : '普通账号'))

const navItems = computed(() => {
  const items = [
    { to: '/', label: '首页摘要', icon: House },
    { to: '/account', label: '账户中心', icon: UserRound },
    { to: '/assets', label: '资产中心', icon: Wallet },
    { to: '/orders/buyer', label: '我的订单', icon: PackageSearch },
  ]

  if (isSellerUser(currentUser.value)) {
    /**
     * 卖家导航保持“工作台 + 卖家订单”两级入口：
     * - 工作台继续承接 Day04 的经营与商品管理；
     * - 卖家订单承接 Day06 的履约主链，避免 buyer/seller 订单混成一套入口。
     */
    items.push({ to: '/seller', label: '卖家工作台', icon: Store })
    items.push({ to: '/orders/seller', label: '卖家订单', icon: Truck })
  }

  return items
})

function isNavItemActive(targetPath: string) {
  return route.path === targetPath || route.path.startsWith(`${targetPath}/`)
}

const quickLogout = async () => {
  clearUserSession()
  menuOpen.value = false
  await router.replace('/login')
}

const closeMenu = () => {
  menuOpen.value = false
}

const handleStorageSync = (event: StorageEvent) => {
  if (event.key === null || ['user_profile', 'user_token', 'authentication'].includes(event.key)) {
    syncCurrentUser()
  }
}

watch(
  () => route.fullPath,
  () => {
    syncCurrentUser()
    closeMenu()
  },
)

onMounted(() => {
  syncCurrentUser()
  window.addEventListener('storage', handleStorageSync)
})

onUnmounted(() => {
  window.removeEventListener('storage', handleStorageSync)
})
</script>

<template>
  <div class="page-shell flex min-h-screen text-gray-800">
    <aside class="hidden lg:flex lg:w-[272px] lg:flex-col lg:border-r lg:border-gray-200/80 lg:bg-white/90 lg:backdrop-blur">
      <div class="flex h-[72px] items-center gap-3 border-b border-gray-200/70 px-6">
        <div class="brand-mark h-10 w-10 text-[15px]">{{ USER_BRAND_MARK }}</div>
        <div class="min-w-0">
          <p class="truncate text-[15px] font-semibold text-gray-900">{{ appTitle }}</p>
          <p class="mt-0.5 text-[11px] text-gray-500">{{ USER_BRAND_SUBTITLE }}</p>
        </div>
      </div>

      <nav class="custom-scrollbar flex-1 overflow-y-auto px-4 py-6">
        <div class="space-y-2">
          <router-link
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            class="nav-link"
            :class="isNavItemActive(item.to) ? 'nav-link-active' : 'nav-link-idle'"
          >
            <component
              :is="item.icon"
              class="h-[18px] w-[18px] shrink-0"
              :class="isNavItemActive(item.to) ? 'text-white' : 'text-gray-400'"
            />
            <span>{{ item.label }}</span>
          </router-link>
        </div>
      </nav>

      <div class="border-t border-gray-200/70 px-5 py-5">
        <div class="panel-muted p-4">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-white text-sm font-bold text-gray-700 shadow-sm shadow-gray-200/40">
              {{ displayName.charAt(0).toUpperCase() }}
            </div>
            <div class="min-w-0 flex-1">
              <p class="truncate text-[13px] font-semibold text-gray-900">{{ displayName }}</p>
              <p class="truncate text-[11px] text-gray-500">{{ primaryContact }}</p>
            </div>
          </div>
          <div class="mt-4 flex items-center justify-between rounded-xl border border-gray-200/80 bg-white/90 px-3 py-2.5 text-[12px] text-gray-500">
            <span class="font-medium text-gray-700">{{ roleText }}</span>
            <span class="chip chip-accent">{{ USER_SESSION_STATUS_TEXT }}</span>
          </div>
          <button class="btn-default mt-4 w-full" @click="quickLogout">
            <LogOut class="h-4 w-4" />
            <span>退出登录</span>
          </button>
        </div>
      </div>
    </aside>

    <div class="flex min-w-0 flex-1 flex-col">
      <header class="sticky top-0 z-30 hidden h-[72px] items-center justify-between border-b border-gray-200/70 bg-stone-50/90 px-6 backdrop-blur lg:flex">
        <div class="flex items-center gap-3">
          <div class="brand-mark h-10 w-10 text-[14px]">{{ USER_BRAND_MARK }}</div>
          <div>
            <p class="page-kicker mb-1">工作台</p>
            <h1 class="text-[18px] font-semibold text-gray-900">用户工作台</h1>
          </div>
        </div>
        <div class="flex items-center gap-3">
          <div class="rounded-2xl border border-gray-200/80 bg-white px-4 py-2.5 text-right shadow-sm shadow-gray-200/25">
            <div class="flex items-center justify-end gap-2">
              <span class="chip chip-muted">{{ roleText }}</span>
              <p class="text-[12px] font-medium text-gray-900">{{ displayName }}</p>
            </div>
            <p class="text-[11px] text-gray-500">{{ primaryContact }}</p>
          </div>
          <router-link class="btn-default" to="/account">
            <UserRound class="h-4 w-4" />
            <span>账户中心</span>
          </router-link>
        </div>
      </header>

      <header class="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-gray-200/70 bg-stone-50/90 px-4 backdrop-blur lg:hidden">
        <div class="flex items-center gap-3">
          <div class="brand-mark h-9 w-9 text-[14px]">{{ USER_BRAND_MARK }}</div>
          <div>
            <p class="text-[14px] font-semibold text-gray-900">{{ appTitle }}</p>
            <p class="text-[11px] text-gray-500">{{ displayName }}</p>
          </div>
        </div>
        <button
          @click="menuOpen = !menuOpen"
          class="rounded-xl border border-gray-200 bg-white p-2 text-gray-600 transition-colors hover:bg-gray-50 hover:text-gray-900"
        >
          <Menu v-if="!menuOpen" class="h-[18px] w-[18px]" />
          <X v-else class="h-[18px] w-[18px]" />
        </button>
      </header>

      <transition name="fade">
        <div v-if="menuOpen" class="border-b border-gray-200/70 bg-white px-4 py-4 shadow-sm lg:hidden">
          <div class="rounded-2xl border border-gray-200/80 bg-gray-50/80 p-4">
            <div class="flex items-center gap-3 border-b border-gray-200/70 pb-4">
              <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-white text-sm font-bold text-gray-700 shadow-sm shadow-gray-200/40">
                {{ displayName.charAt(0).toUpperCase() }}
              </div>
              <div class="min-w-0 flex-1">
                <p class="truncate text-[13px] font-semibold text-gray-900">{{ displayName }}</p>
                <p class="truncate text-[11px] text-gray-500">{{ primaryContact }}</p>
              </div>
            </div>
            <nav class="mt-4 space-y-2">
              <router-link
                v-for="item in navItems"
                :key="item.to"
                :to="item.to"
                class="nav-link"
                :class="isNavItemActive(item.to) ? 'nav-link-active' : 'nav-link-idle'"
                @click="closeMenu"
              >
                <component
                  :is="item.icon"
                  class="h-[18px] w-[18px] shrink-0"
                  :class="isNavItemActive(item.to) ? 'text-white' : 'text-gray-400'"
                />
                <span>{{ item.label }}</span>
              </router-link>
            </nav>
            <button class="btn-default mt-4 w-full" @click="quickLogout">
              <LogOut class="h-4 w-4" />
              <span>退出登录</span>
            </button>
          </div>
        </div>
      </transition>

      <main class="flex-1 overflow-y-auto px-4 py-5 md:px-6 lg:px-8 lg:py-6">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
