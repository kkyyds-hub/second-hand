<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LayoutDashboard, Users, ShoppingBag, ShieldAlert, Settings, Bell, ChevronDown, Menu, Search, HelpCircle, LogOut, Wrench } from 'lucide-vue-next'
import { clearAdminToken } from '@/utils/request'

const route = useRoute()
const router = useRouter()
const isCollapsed = ref(false)
const userMenuOpen = ref(false)

const menuGroups = [
  {
    label: '总览',
    items: [
      { name: '工作台', path: '/', icon: LayoutDashboard },
    ]
  },
  {
    label: '业务',
    items: [
      { name: '用户与商家', path: '/users', icon: Users },
      { name: '商品审核', path: '/products', icon: ShoppingBag },
    ]
  },
  {
    label: '风控',
    items: [
      { name: '纠纷与违规', path: '/audit', icon: ShieldAlert },
    ]
  },
  {
    label: '系统',
    items: [
      { name: '运维中心', path: '/ops-center', icon: Wrench },
      { name: '系统设置', path: '/settings', icon: Settings },
    ]
  }
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
  <div class="min-h-screen bg-[#fafafa] flex text-gray-800 font-sans selection:bg-gray-200 overflow-hidden" @click="userMenuOpen = false">
    <!-- Sidebar (Modern Light/Minimal Theme) -->
    <aside 
      class="bg-white border-r border-gray-200/80 flex flex-col items-center py-5 relative z-20 shrink-0 transition-all duration-300 ease-in-out"
      :class="isCollapsed ? 'w-16' : 'w-60'"
    >
      <!-- Logo Area -->
      <div class="flex flex-col w-full mb-8">
        <div 
          class="flex items-center cursor-pointer group px-5 w-full h-12 mt-2"
          :class="isCollapsed ? 'justify-center' : 'justify-start space-x-3'"
        >
          <!-- Brand Logo: KK + Circular/Flow Concept -->
          <div class="relative flex items-center justify-center shrink-0 w-9 h-9 bg-gradient-to-br from-gray-900 to-gray-800 rounded-xl shadow-md overflow-hidden group-hover:shadow-lg transition-shadow">
            <!-- Subtle background flow pattern -->
            <svg class="absolute inset-0 w-full h-full opacity-20" viewBox="0 0 36 36" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M18 4C25.732 4 32 10.268 32 18C32 25.732 25.732 32 18 32C10.268 32 4 25.732 4 18" stroke="white" stroke-width="1.5" stroke-linecap="round" stroke-dasharray="4 4"/>
              <path d="M18 4L22 8M18 4L14 8" stroke="white" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M18 32L14 28M18 32L22 28" stroke="white" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <!-- KK Text -->
            <span class="relative z-10 text-white text-[15px] font-black tracking-tighter italic pr-0.5">KK</span>
          </div>
          
          <div 
            class="flex flex-col overflow-hidden transition-all duration-300"
            :class="isCollapsed ? 'opacity-0 w-0' : 'opacity-100 w-auto'"
          >
            <span class="tracking-wide whitespace-nowrap font-bold text-[16px] text-gray-900 leading-tight">运营中枢</span>
            <span class="text-[11px] text-gray-500 font-medium mt-0.5 flex items-center gap-1">
              <span class="w-1 h-1 rounded-full bg-blue-500"></span>
              Management
            </span>
          </div>
        </div>
      </div>

      <!-- Navigation -->
      <nav class="flex-1 w-full px-4 space-y-6 overflow-x-hidden overflow-y-auto custom-scrollbar pb-4">
        <div v-for="group in menuGroups" :key="group.label" class="flex flex-col">
          <div 
            class="px-2 mb-2 text-[11px] font-bold text-gray-400/80 uppercase tracking-widest transition-all duration-300"
            :class="isCollapsed ? 'opacity-0 h-0 overflow-hidden' : 'opacity-100'"
          >
            {{ group.label }}
          </div>
          <div class="space-y-1">
            <router-link
              v-for="item in group.items"
              :key="item.path"
              :to="item.path"
              class="flex items-center px-3 py-2.5 rounded-lg transition-all duration-200 relative group overflow-hidden"
              :class="[
                isActive(item.path) 
                  ? 'bg-gray-900 text-white font-medium shadow-md shadow-gray-900/10' 
                  : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100/80',
                isCollapsed ? 'justify-center' : 'justify-start space-x-3'
              ]"
              :title="isCollapsed ? item.name : ''"
            >
              <component :is="item.icon" class="w-[18px] h-[18px] shrink-0 transition-colors" :class="isActive(item.path) ? 'text-white' : 'text-gray-400 group-hover:text-gray-600'" />
              <span 
                class="text-[13px] whitespace-nowrap transition-all duration-300"
                :class="isCollapsed ? 'opacity-0 w-0 hidden' : 'opacity-100 w-auto'"
              >{{ item.name }}</span>
            </router-link>
          </div>
        </div>
      </nav>

      <!-- Workspace Meta Footer (Lightweight Status Area) -->
      <div 
        class="w-full px-5 py-4 border-t border-gray-200/60 transition-all duration-300"
        :class="isCollapsed ? 'items-center flex flex-col' : ''"
      >
        <div v-if="!isCollapsed" class="flex flex-col space-y-2.5">
          <!-- Line 1: Environment -->
          <div class="flex items-center gap-2 text-[11px] text-gray-500">
            <span class="w-1.5 h-1.5 rounded-full bg-green-500 shrink-0"></span>
            <span class="font-medium tracking-wide">Production · Real API</span>
          </div>
          
          <!-- Line 2: Workspace Context -->
          <div class="flex items-center gap-2 text-[11px] text-gray-500">
            <span class="px-1.5 py-0.5 bg-gray-100 text-gray-500 rounded border border-gray-200/80 leading-none shrink-0">工作区</span>
            <span class="truncate">运营管理后台</span>
          </div>

          <!-- Line 3: Status -->
          <div class="flex items-center gap-2 text-[11px] text-gray-500">
            <span class="px-1.5 py-0.5 bg-gray-100 text-gray-500 rounded border border-gray-200/80 leading-none shrink-0">状态</span>
            <span class="truncate">核心链路正常</span>
          </div>
        </div>
        
        <!-- Collapsed State: Minimal Visual Hint -->
        <div v-else class="flex flex-col items-center justify-center h-full">
          <div class="w-2 h-2 rounded-full bg-green-500" title="Production · 核心链路正常"></div>
        </div>
      </div>
    </aside>

    <!-- Main Content -->
    <div class="flex-1 flex flex-col h-screen overflow-hidden relative min-w-0 bg-[#fafafa]">
      <!-- Top Header (Minimalist) -->
      <header class="h-14 bg-[#fafafa]/80 backdrop-blur-md z-50 flex items-center justify-between px-6 border-b border-gray-200/60 shrink-0 sticky top-0">
        <div class="flex items-center space-x-4">
          <button @click="isCollapsed = !isCollapsed" class="text-gray-400 hover:text-gray-700 focus:outline-none transition-colors p-1 -ml-1 rounded-md hover:bg-gray-100">
            <Menu class="w-[18px] h-[18px]" />
          </button>
          
          <!-- Search Bar & Quick Status -->
          <div class="hidden md:flex items-center bg-white border border-gray-200/80 rounded-lg px-3 py-1.5 w-80 focus-within:border-gray-400 focus-within:ring-2 focus-within:ring-gray-900/5 transition-all shadow-sm">
            <Search class="w-4 h-4 text-gray-400 mr-2 shrink-0" />
            <input type="text" placeholder="搜索..." class="bg-transparent border-none outline-none text-[13px] w-full text-gray-700 placeholder-gray-400">
            <div class="flex items-center gap-1 ml-2">
              <kbd class="hidden sm:inline-block px-1.5 py-0.5 text-[10px] font-sans font-medium text-gray-400 bg-gray-50 border border-gray-200 rounded">⌘</kbd>
              <kbd class="hidden sm:inline-block px-1.5 py-0.5 text-[10px] font-sans font-medium text-gray-400 bg-gray-50 border border-gray-200 rounded">K</kbd>
            </div>
          </div>
          
          <div class="hidden lg:flex items-center space-x-5 text-[12px] font-medium text-gray-500 ml-4 px-4 border-l border-gray-200/80">
            <div class="flex items-center gap-1.5 cursor-pointer hover:text-gray-800 transition-colors">
              <span class="w-1.5 h-1.5 rounded-full bg-orange-400"></span> 待审 <span class="font-numeric text-gray-700">182</span>
            </div>
            <div class="flex items-center gap-1.5 cursor-pointer hover:text-gray-800 transition-colors">
              <span class="w-1.5 h-1.5 rounded-full bg-red-500"></span> 纠纷 <span class="font-numeric text-gray-700">12</span>
            </div>
          </div>
        </div>
        
        <div class="flex items-center space-x-3">
          <button class="text-gray-400 hover:text-gray-700 transition-colors p-1.5 rounded-md hover:bg-gray-100">
            <HelpCircle class="w-[18px] h-[18px]" />
          </button>
          <button class="relative text-gray-400 hover:text-gray-700 transition-colors p-1.5 rounded-md hover:bg-gray-100">
            <Bell class="w-[18px] h-[18px]" />
            <span class="absolute top-1.5 right-1.5 w-1.5 h-1.5 bg-red-500 rounded-full border border-white"></span>
          </button>
          
          <div class="w-px h-4 bg-gray-200 mx-1"></div>
          
          <!-- User Dropdown -->
          <div class="relative" @click.stop>
            <button
              class="flex items-center space-x-2 cursor-pointer hover:bg-gray-100 px-2 py-1 rounded-lg transition-colors"
              @click="userMenuOpen = !userMenuOpen"
            >
              <img src="https://i.pravatar.cc/150?u=admin" alt="avatar" class="w-6 h-6 rounded-full bg-gray-200 border border-gray-200" />
              <span class="text-[13px] text-gray-700 font-medium hidden sm:block">Admin</span>
              <ChevronDown class="w-3.5 h-3.5 text-gray-400 hidden sm:block" />
            </button>

            <transition name="fade">
              <div
                v-if="userMenuOpen"
                class="absolute right-0 mt-2 w-48 rounded-xl border border-gray-200/80 bg-white shadow-[0_4px_20px_-4px_rgba(0,0,0,0.1)] py-1.5 z-[60]"
              >
                <div class="px-3 py-2 border-b border-gray-100 mb-1">
                  <p class="text-[13px] font-medium text-gray-900">Admin User</p>
                  <p class="text-[11px] text-gray-500 truncate">admin@kkplatform.com</p>
                </div>
                <button
                  class="w-full text-left px-3 py-1.5 text-[13px] text-gray-600 hover:bg-gray-50 hover:text-gray-900 transition-colors"
                  @click="goLogoutPage"
                >
                  退出登录页
                </button>
                <button
                  class="w-full text-left px-3 py-1.5 text-[13px] text-red-600 hover:bg-red-50 transition-colors flex items-center gap-2"
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
      <main class="flex-1 overflow-y-auto p-6 lg:p-8 w-full relative">
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
