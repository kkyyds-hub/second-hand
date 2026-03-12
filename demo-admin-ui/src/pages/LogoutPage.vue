<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Loader2, LogOut, ShieldAlert } from 'lucide-vue-next'
import { clearAdminToken } from '@/utils/request'

const router = useRouter()
const submitting = ref(false)

const goBack = () => {
  router.push('/')
}

const confirmLogout = async () => {
  try {
    submitting.value = true
    await new Promise((resolve) => window.setTimeout(resolve, 280))
    clearAdminToken()
    router.replace('/login')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="max-w-[980px] mx-auto">
    <div class="mb-4">
      <h1 class="text-xl font-bold text-gray-900">退出登录</h1>
      <p class="text-sm text-gray-500 mt-1">安全退出当前管理端账号，返回登录页重新鉴权</p>
    </div>

    <div class="card p-0 overflow-hidden">
      <div class="px-6 py-5 border-b border-gray-200 bg-white flex items-center gap-3">
        <div class="w-9 h-9 rounded bg-red-50 text-red-600 flex items-center justify-center">
          <LogOut class="w-4.5 h-4.5" />
        </div>
        <div>
          <h2 class="text-base font-bold text-gray-800">确认退出当前账号？</h2>
          <p class="text-xs text-gray-500 mt-1">退出后将清理本地登录凭证，需重新登录才能访问后台页面。</p>
        </div>
      </div>

      <div class="p-6 space-y-4">
        <div class="bg-amber-50 border border-amber-200 rounded p-3 flex items-start gap-2">
          <ShieldAlert class="w-4 h-4 text-amber-600 mt-0.5 shrink-0" />
          <p class="text-sm text-amber-800 leading-relaxed">
            如果你正在联调多个账号，建议先退出当前账号，再用其他账号重新登录，避免权限状态混淆。
          </p>
        </div>

        <div class="bg-gray-50 border border-gray-200 rounded p-3 text-sm text-gray-600">
          当前操作：<span class="font-medium text-gray-800">清理 token 并跳转登录页</span>
        </div>
      </div>

      <div class="px-6 py-4 border-t border-gray-200 bg-gray-50/60 flex justify-end gap-3">
        <button class="btn-default" @click="goBack" :disabled="submitting">取消</button>
        <button
          class="btn-primary bg-red-600 hover:bg-red-700 border-red-700/50 flex items-center gap-2 disabled:opacity-70"
          @click="confirmLogout"
          :disabled="submitting"
        >
          <Loader2 v-if="submitting" class="w-4 h-4 animate-spin" />
          {{ submitting ? '退出中...' : '确认退出登录' }}
        </button>
      </div>
    </div>
  </div>
</template>
