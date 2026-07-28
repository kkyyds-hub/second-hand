<script setup lang="ts">
import { computed } from 'vue'
import { Compass, PackageSearch, SearchX } from 'lucide-vue-next'
import { readUserToken } from '@/utils/request'
import { USER_APP_TITLE, USER_BRAND_MARK } from '@/utils/brand'

const isAuthenticated = computed(() => Boolean(readUserToken()))
</script>

<template>
  <main class="commerce-not-found" aria-labelledby="not-found-title">
    <section class="commerce-not-found-card">
      <span class="brand-mark h-11 w-11 text-[16px]" aria-hidden="true">{{ USER_BRAND_MARK }}</span>
      <p class="commerce-not-found-code" aria-hidden="true">404</p>
      <h1 id="not-found-title">页面没有找到</h1>
      <p>当前地址可能已失效，或页面已被移动。</p>
      <div class="commerce-not-found-actions">
        <router-link class="btn-primary" :to="isAuthenticated ? '/' : '/login'">
          <Compass class="h-4 w-4" aria-hidden="true" />
          <span>{{ isAuthenticated ? `返回${USER_APP_TITLE}首页` : '返回登录' }}</span>
        </router-link>
        <router-link v-if="isAuthenticated" class="btn-default" to="/market">
          <PackageSearch class="h-4 w-4" aria-hidden="true" />
          <span>去逛市场</span>
        </router-link>
        <router-link v-else class="btn-default" to="/register/phone">
          <SearchX class="h-4 w-4" aria-hidden="true" />
          <span>手机注册</span>
        </router-link>
      </div>
    </section>
  </main>
</template>
