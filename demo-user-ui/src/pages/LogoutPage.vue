<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Loader2 } from 'lucide-vue-next'
import { clearUserSession } from '@/utils/request'

const router = useRouter()
onMounted(async () => { clearUserSession(); await router.replace('/login') })
</script>

<template>
  <main class="commerce-logout" aria-live="polite">
    <Loader2 class="h-8 w-8 animate-spin text-blue-500" aria-hidden="true" />
    <h1>正在退出登录</h1>
    <p>正在清理登录状态并返回登录页。</p>
  </main>
</template>
