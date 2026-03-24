<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { House, LogOut, Menu, UserRound, X } from 'lucide-vue-next'
import {
  clearUserSession,
  getUserDisplayName,
  getUserPrimaryContact,
  isSellerUser,
  readCurrentUser,
  type UserProfile,
} from '@/utils/request'

const router = useRouter()
const route = useRoute()
const menuOpen = ref(false)
const currentUser = ref<UserProfile | null>(readCurrentUser())

const appTitle = import.meta.env.VITE_APP_TITLE || '用户端工作台'

/**
 * Day01 的壳层导航只保留首页摘要和账户中心。
 * 这样可以先冻结鉴权壳和登录后骨架，不把 Day02+ 的业务菜单提前塞进来。
 */
const navItems = [
  { to: '/', label: '首页摘要', icon: House },
  { to: '/account', label: '账户中心', icon: UserRound },
]

const syncCurrentUser = () => {
  currentUser.value = readCurrentUser()
}

const displayName = computed(() => getUserDisplayName(currentUser.value))
const primaryContact = computed(() => getUserPrimaryContact(currentUser.value))
const roleText = computed(() => (isSellerUser(currentUser.value) ? '卖家 / 普通用户' : '普通用户'))

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

/**
 * 当前用户信息来自本地 session 快照。
 * Day01 还没有独立 profile 刷新接口，所以壳层通过路由切换和 storage 事件同步，覆盖登录、退出和多标签页变化。
 */
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
  <div class="min-h-screen bg-slate-50 lg:flex">
    <aside class="hidden lg:flex lg:w-72 lg:flex-col lg:border-r lg:border-slate-200 lg:bg-white">
      <div class="border-b border-slate-200 px-6 py-6">
        <p class="muted-kicker">demo-user-ui</p>
        <h1 class="mt-2 text-xl font-semibold text-slate-900">{{ appTitle }}</h1>
        <p class="mt-2 text-sm text-slate-500">登录后可查看首页摘要、账户快照，并随时安全退出当前会话。</p>
      </div>

      <nav class="flex-1 space-y-2 px-4 py-6">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="flex items-center gap-3 rounded-2xl px-4 py-3 text-sm font-medium text-slate-600 transition hover:bg-slate-100 hover:text-slate-900"
          active-class="bg-slate-900 text-white hover:bg-slate-900 hover:text-white"
        >
          <component :is="item.icon" class="h-4 w-4" />
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="border-t border-slate-200 px-6 py-5">
        <div class="rounded-2xl bg-slate-50 p-4">
          <p class="text-sm font-semibold text-slate-900">{{ displayName }}</p>
          <p class="mt-1 text-xs text-slate-500">{{ roleText }}</p>
          <p class="mt-3 text-xs text-slate-500">{{ primaryContact }}</p>
          <button class="btn-default mt-4 w-full gap-2" type="button" @click="quickLogout">
            <LogOut class="h-4 w-4" />
            <span>退出登录</span>
          </button>
        </div>
      </div>
    </aside>

    <div class="flex min-h-screen flex-1 flex-col">
      <header class="border-b border-slate-200 bg-white px-4 py-4 lg:hidden">
        <div class="flex items-center justify-between gap-3">
          <div>
            <p class="muted-kicker">demo-user-ui</p>
            <p class="text-base font-semibold text-slate-900">{{ appTitle }}</p>
            <p class="mt-1 text-xs text-slate-500">{{ displayName }}</p>
          </div>
          <button
            type="button"
            class="inline-flex h-10 w-10 items-center justify-center rounded-2xl border border-slate-200 bg-white text-slate-700"
            @click="menuOpen = !menuOpen"
          >
            <Menu v-if="!menuOpen" class="h-5 w-5" />
            <X v-else class="h-5 w-5" />
          </button>
        </div>

        <div v-if="menuOpen" class="mt-4 space-y-2 rounded-2xl border border-slate-200 bg-slate-50 p-3">
          <div class="rounded-2xl bg-white px-4 py-3 text-sm text-slate-600">
            <p class="font-medium text-slate-900">{{ displayName }}</p>
            <p class="mt-1 text-xs text-slate-500">{{ roleText }}</p>
            <p class="mt-2 text-xs text-slate-500">{{ primaryContact }}</p>
          </div>

          <router-link
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            class="flex items-center gap-3 rounded-2xl px-4 py-3 text-sm font-medium text-slate-700 transition hover:bg-white"
            active-class="bg-white text-slate-900"
            @click="closeMenu"
          >
            <component :is="item.icon" class="h-4 w-4" />
            <span>{{ item.label }}</span>
          </router-link>
          <button class="btn-default mt-2 w-full gap-2" type="button" @click="quickLogout">
            <LogOut class="h-4 w-4" />
            <span>退出登录</span>
          </button>
        </div>
      </header>

      <main class="flex-1 px-4 py-6 md:px-6 lg:px-10 lg:py-8">
        <router-view />
      </main>
    </div>
  </div>
</template>
