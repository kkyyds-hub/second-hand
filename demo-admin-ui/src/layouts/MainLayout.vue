<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LayoutDashboard, Users, ShoppingBag, ShieldAlert, Settings, Bell, ChevronDown, Menu, Search, HelpCircle, LogOut } from 'lucide-vue-next'
import { clearAdminToken } from '@/utils/request'

const route = useRoute()
const router = useRouter()
const isCollapsed = ref(false)
const userMenuOpen = ref(false)

const menu = [
  { name: '工作台', path: '/', icon: LayoutDashboard },
  { name: '用户与商家', path: '/users', icon: Users },
  { name: '商品审核', path: '/products', icon: ShoppingBag },
  { name: '纠纷与违规', path: '/audit', icon: ShieldAlert },
  { name: '系统设置', path: '/settings', icon: Settings },
]

const isActive = (path: string) => route.path === path

const goLogoutPage = () => {
  userMenuOpen.value = false
  router.push('/logout')
}

const quickLogout = () => {
  userMenuOpen.value = false
  clearAdminToken()
  router.replace('/login')
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 flex text-gray-800 font-sans selection:bg-blue-200 overflow-hidden" @click="userMenuOpen = false">
    <!-- Sidebar (Standard Dark Enterprise Theme) -->
    <aside 
      class="bg-slate-900 border-r border-slate-800 flex flex-col items-center py-4 relative z-20 shrink-0 transition-all duration-300 ease-in-out shadow-lg"
      :class="isCollapsed ? 'w-16' : 'w-56'"
    >
      <!-- Logo Area -->
      <div 
        class="text-xl font-bold flex items-center mb-6 cursor-pointer group px-4 w-full h-10 text-white"
        :class="isCollapsed ? 'justify-center' : 'justify-start space-x-2'"
      >
        <div class="flex items-center justify-center shrink-0 w-8 h-8 bg-blue-600 rounded">
          <span class="text-white text-sm font-black tracking-tighter">KK</span>
        </div>
        <span 
          class="tracking-widest whitespace-nowrap overflow-hidden transition-all duration-300 font-semibold"
          :class="isCollapsed ? 'opacity-0 w-0' : 'opacity-100 w-auto'"
        >运营后台</span>
      </div>

      <!-- Navigation -->
      <nav class="flex-1 w-full px-2 space-y-1 overflow-x-hidden">
        <router-link
          v-for="item in menu"
          :key="item.path"
          :to="item.path"
          class="flex items-center px-3 py-2.5 rounded transition-all duration-200 relative group overflow-hidden"
          :class="[
            isActive(item.path) 
              ? 'bg-blue-600 text-white font-medium' 
              : 'text-slate-400 hover:text-white hover:bg-slate-800',
            isCollapsed ? 'justify-center' : 'justify-start space-x-3'
          ]"
          :title="isCollapsed ? item.name : ''"
        >
          <component :is="item.icon" class="w-4 h-4 shrink-0" />
          <span 
            class="text-sm whitespace-nowrap transition-all duration-300"
            :class="isCollapsed ? 'opacity-0 w-0 hidden' : 'opacity-100 w-auto'"
          >{{ item.name }}</span>
        </router-link>
      </nav>
    </aside>

    <!-- Main Content -->
    <div class="flex-1 flex flex-col h-screen overflow-hidden relative min-w-0 bg-gray-50">
      <!-- Top Header (Standard Enterprise White Header) -->
      <header class="h-14 bg-white z-10 flex items-center justify-between px-4 sm:px-6 border-b border-gray-200 shrink-0 shadow-sm relative">
        <div class="flex items-center space-x-4">
          <button @click="isCollapsed = !isCollapsed" class="text-gray-500 hover:text-gray-700 focus:outline-none">
            <Menu class="w-5 h-5" />
          </button>
          
          <!-- Search Bar & Quick Status -->
          <div class="hidden md:flex items-center bg-gray-100/80 rounded px-3 py-1.5 w-72 border border-transparent focus-within:border-blue-400 focus-within:bg-white transition-all focus-within:ring-2 focus-within:ring-blue-100">
            <Search class="w-4 h-4 text-gray-400 mr-2 shrink-0" />
            <input type="text" placeholder="全局搜索订单 / 商品 / 用户..." class="bg-transparent border-none outline-none text-sm w-full text-gray-700 placeholder-gray-400">
          </div>
          
          <div class="hidden lg:flex items-center space-x-5 text-xs font-semibold text-gray-500 ml-4 px-4 border-l border-gray-200">
            <div class="flex items-center gap-1.5 cursor-pointer hover:text-gray-800 transition-colors">
              <span class="w-1.5 h-1.5 rounded-full bg-orange-500"></span> 待审商品 <span class="font-numeric">182</span>
            </div>
            <div class="flex items-center gap-1.5 cursor-pointer hover:text-gray-800 transition-colors">
              <span class="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse"></span> 紧急纠纷 <span class="font-numeric">12</span>
            </div>
            <div class="flex items-center gap-1.5 cursor-pointer hover:text-gray-800 transition-colors">
              <span class="w-1.5 h-1.5 rounded-full bg-yellow-500"></span> 风控预警 <span class="font-numeric">3</span>
            </div>
          </div>
        </div>
        
        <div class="flex items-center space-x-4">
          <button class="text-gray-500 hover:text-gray-700 transition-colors">
            <HelpCircle class="w-5 h-5" />
          </button>
          <button class="relative text-gray-500 hover:text-gray-700 transition-colors">
            <Bell class="w-5 h-5" />
            <span class="absolute 0 top-0.5 right-0.5 w-2 h-2 bg-red-500 rounded-full border-2 border-white"></span>
          </button>
          
          <!-- User Dropdown -->
          <div class="relative" @click.stop>
            <button
              class="flex items-center space-x-2 cursor-pointer hover:bg-gray-50 px-2 py-1 rounded transition-colors border border-transparent hover:border-gray-200"
              @click="userMenuOpen = !userMenuOpen"
            >
              <img src="https://i.pravatar.cc/150?u=admin" alt="avatar" class="w-7 h-7 rounded-full bg-gray-200" />
              <span class="text-sm text-gray-700 font-medium">管理员</span>
              <ChevronDown class="w-4 h-4 text-gray-500" />
            </button>

            <transition name="fade">
              <div
                v-if="userMenuOpen"
                class="absolute right-0 mt-2 w-44 rounded border border-gray-200 bg-white shadow-lg py-1 z-30"
              >
                <button
                  class="w-full text-left px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors"
                  @click="goLogoutPage"
                >
                  退出登录页
                </button>
                <button
                  class="w-full text-left px-3 py-2 text-sm text-red-600 hover:bg-red-50 transition-colors flex items-center gap-2"
                  @click="quickLogout"
                >
                  <LogOut class="w-3.5 h-3.5" />
                  立即退出
                </button>
              </div>
            </transition>
          </div>
        </div>
      </header>

      <!-- Page view -->
      <main class="flex-1 overflow-y-auto p-4 sm:p-6 w-full relative">
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
