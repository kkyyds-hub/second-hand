<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Loader2 } from 'lucide-vue-next'
import { clearUserSession } from '@/utils/request'

const router = useRouter()

onMounted(async () => {
  clearUserSession()
  await router.replace('/login')
})
</script>

<template>
  <div class="flex min-h-[40vh] items-center justify-center">
    <div class="card w-full max-w-md p-8 text-center">
      <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100 text-slate-700">
        <Loader2 class="h-6 w-6 animate-spin" />
      </div>
      <p class="muted-kicker mt-4">Logout</p>
      <h1 class="section-title mt-3">正在安全退出</h1>
      <p class="section-desc">系统正在清理本地登录会话，并将你带回登录页。</p>
    </div>
  </div>
</template>
