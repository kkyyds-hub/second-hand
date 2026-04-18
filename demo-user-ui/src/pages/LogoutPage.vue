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
  <div class="page-body page-body-narrow">
    <section class="section-panel">
      <div class="section-body">
        <div class="empty-state min-h-[260px] border-0 bg-transparent px-0 py-0">
          <Loader2 class="empty-state-icon animate-spin text-blue-500" />
          <p class="page-kicker">退出</p>
          <h1 class="empty-state-title mt-1">正在安全退出</h1>
          <p class="empty-state-text">系统正在清理本地登录会话，并将你带回登录页。</p>
        </div>
      </div>
    </section>
  </div>
</template>
