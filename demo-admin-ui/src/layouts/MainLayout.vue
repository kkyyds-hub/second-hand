<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronDown, LayoutDashboard, LogOut, Menu, ReceiptText, Settings, ShieldAlert, ShoppingBag, Users, Wrench, X } from 'lucide-vue-next'
import AdminBrand from '@/components/admin/AdminBrand.vue'
import AdminOverlay from '@/components/admin/AdminOverlay.vue'
import { readAdminUser } from '@/utils/request'

const route = useRoute()
const router = useRouter()
const isCollapsed = ref(false)
const drawerOpen = ref(false)
const userMenuOpen = ref(false)
const userMenuButton = ref<HTMLButtonElement | null>(null)
const firstUserMenuItem = ref<HTMLButtonElement | null>(null)
const adminUser = readAdminUser()
const adminName = computed(() => adminUser?.nickname || adminUser?.name || adminUser?.username || '管理员')
const adminContext = computed(() => adminUser?.username || '当前登录账号')
const pageTitle = computed(() => route.meta.title || '运营工作台')
const adminInitial = computed(() => adminName.value.trim().charAt(0).toUpperCase() || '管')

const menuGroups = [
  { label: '总览', items: [{ name: '工作台', path: '/', icon: LayoutDashboard }] },
  { label: '业务', items: [{ name: '用户与商家', path: '/users', icon: Users }, { name: '商品审核', path: '/products', icon: ShoppingBag }, { name: '订单管理', path: '/orders', icon: ReceiptText }] },
  { label: '风控', items: [{ name: '纠纷与违规', path: '/audit', icon: ShieldAlert }] },
  { label: '系统', items: [{ name: '运维中心', path: '/ops-center', icon: Wrench }, { name: '系统设置', path: '/settings', icon: Settings }] },
]

const isActive = (path: string) => route.path === path
const closeUserMenu = (restoreFocus = false) => {
  const wasOpen = userMenuOpen.value
  userMenuOpen.value = false
  if (restoreFocus && wasOpen) nextTick(() => userMenuButton.value?.focus())
}
const openUserMenu = async () => {
  userMenuOpen.value = !userMenuOpen.value
  if (userMenuOpen.value) { await nextTick(); firstUserMenuItem.value?.focus() }
}
const goLogoutPage = () => { closeUserMenu(); router.push('/logout') }
const handleUserMenuKeydown = (event: KeyboardEvent) => { if (event.key === 'Escape') { event.preventDefault(); closeUserMenu(true) } }

watch(() => route.fullPath, () => { drawerOpen.value = false; closeUserMenu() })
const handleDocumentClick = (event: MouseEvent) => {
  const target = event.target as Node
  if (userMenuOpen.value && !userMenuButton.value?.parentElement?.contains(target)) closeUserMenu()
}
onMounted(() => document.addEventListener('click', handleDocumentClick))
onBeforeUnmount(() => document.removeEventListener('click', handleDocumentClick))
</script>

<template>
  <div class="admin-shell">
    <a class="admin-skip-link" href="#admin-main-content">跳到主要内容</a>
    <aside class="admin-sidebar" :class="{ 'admin-sidebar-collapsed': isCollapsed }">
      <router-link class="admin-sidebar-brand" to="/" :aria-label="isCollapsed ? '返回运营工作台' : undefined"><AdminBrand :compact="isCollapsed" /></router-link>
      <nav class="admin-navigation" aria-label="管理端主导航">
        <section v-for="group in menuGroups" :key="group.label" class="admin-nav-group"><p v-if="!isCollapsed" class="admin-nav-group-label">{{ group.label }}</p><router-link v-for="item in group.items" :key="item.path" :to="item.path" class="admin-nav-link" :class="{ 'admin-nav-link-active': isActive(item.path) }" :aria-current="isActive(item.path) ? 'page' : undefined" :title="isCollapsed ? item.name : undefined"><component :is="item.icon" aria-hidden="true" /><span v-if="!isCollapsed">{{ item.name }}</span></router-link></section>
      </nav>
      <button class="admin-sidebar-toggle admin-icon-button" type="button" :aria-label="isCollapsed ? '展开侧边导航' : '折叠侧边导航'" :title="isCollapsed ? '展开侧边导航' : '折叠侧边导航'" @click="isCollapsed = !isCollapsed"><Menu aria-hidden="true" /></button>
    </aside>

    <div class="admin-shell-content">
      <header class="admin-topbar">
        <button class="admin-mobile-menu admin-icon-button" type="button" aria-label="打开导航菜单" aria-controls="admin-mobile-navigation" :aria-expanded="drawerOpen" @click="drawerOpen = true"><Menu aria-hidden="true" /></button>
        <div class="admin-page-heading"><p class="admin-eyebrow">运营管理</p><h1>{{ pageTitle }}</h1></div>
        <div class="admin-account-area">
          <button ref="userMenuButton" class="admin-account-button" type="button" aria-controls="admin-user-menu" :aria-expanded="userMenuOpen" @click.stop="openUserMenu" @keydown="handleUserMenuKeydown"><span class="admin-avatar" aria-hidden="true">{{ adminInitial }}</span><span class="admin-account-copy"><strong>{{ adminName }}</strong><small>{{ adminContext }}</small></span><ChevronDown aria-hidden="true" /></button>
          <div v-if="userMenuOpen" id="admin-user-menu" class="admin-user-menu" role="menu" aria-label="管理员菜单" @keydown="handleUserMenuKeydown"><button ref="firstUserMenuItem" type="button" role="menuitem" @click="goLogoutPage"><LogOut aria-hidden="true" /> 退出登录</button></div>
        </div>
      </header>
      <main id="admin-main-content" class="admin-main" tabindex="-1"><router-view /></main>
    </div>

    <AdminOverlay :open="drawerOpen" title-id="admin-mobile-navigation-title" variant="drawer" @close="drawerOpen = false">
      <div id="admin-mobile-navigation" class="admin-drawer" aria-label="移动端导航">
        <div class="admin-drawer-header"><div><AdminBrand /><p id="admin-mobile-navigation-title">导航菜单</p></div><button class="admin-icon-button" type="button" aria-label="关闭导航菜单" @click="drawerOpen = false"><X aria-hidden="true" /></button></div>
        <nav class="admin-drawer-nav" aria-label="移动端管理导航"><section v-for="group in menuGroups" :key="group.label"><p>{{ group.label }}</p><router-link v-for="item in group.items" :key="item.path" :to="item.path" :aria-current="isActive(item.path) ? 'page' : undefined" @click="drawerOpen = false"><component :is="item.icon" aria-hidden="true" />{{ item.name }}</router-link></section></nav>
      </div>
    </AdminOverlay>
  </div>
</template>
