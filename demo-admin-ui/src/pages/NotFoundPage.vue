<script setup lang="ts">
import { ArrowLeft, LayoutDashboard, SearchX } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <section class="admin-not-found" aria-labelledby="not-found-title">
    <SearchX class="admin-not-found-icon" aria-hidden="true" />
    <p class="admin-eyebrow">404</p><h1 id="not-found-title">页面未找到</h1><p>该管理端页面不存在，或链接已失效。</p>
    <div class="admin-not-found-actions"><button class="btn-default" type="button" @click="router.back()"><ArrowLeft aria-hidden="true" /> 返回上一页</button><button class="btn-primary" type="button" @click="router.push('/')"><LayoutDashboard aria-hidden="true" /> 返回工作台</button></div>
  </section>
</template>
