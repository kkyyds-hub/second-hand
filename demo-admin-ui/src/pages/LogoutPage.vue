<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Loader2, LogOut, ShieldAlert } from 'lucide-vue-next'
import { clearAdminToken } from '@/utils/request'

const router = useRouter()
const submitting = ref(false)

/**
 * 取消退出时直接回到首页，避免误操作打断当前 review 动线。
 */
const goBack = () => {
  router.push('/')
}

/**
 * 退出流程保持为“进入 loading -> 清 token -> 跳登录页”的顺序，
 * 这样状态切换更清晰，也能防止连续点击导致重复提交。
 */
const confirmLogout = async () => {
  try {
    submitting.value = true
    // 人为补一个极短过渡，让按钮 loading 状态能被用户感知到。
    await new Promise((resolve) => window.setTimeout(resolve, 280))
    clearAdminToken()
    // 用 replace 避免浏览器返回后又回到已经失效的后台页面。
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
        <!-- 这里强调公共设备风险，属于退出页最核心的安全提示。 -->
        <div class="state-banner state-banner-warning">
          <div class="state-banner-main">
            <span class="state-banner-icon border-orange-200">
              <ShieldAlert class="w-4 h-4 text-amber-600" />
            </span>
            <div>
              <p class="state-banner-title">退出前请确认设备环境</p>
              <p class="state-banner-text text-amber-800/90">若当前为公共设备，退出后建议关闭浏览器或清理浏览记录，避免账号信息残留。</p>
            </div>
          </div>
        </div>

        <div class="rounded-xl border border-gray-200 bg-gray-50/70 p-4 text-sm text-gray-600">
          <p class="text-[12px] text-gray-500">退出后效果</p>
          <p class="mt-1 font-medium text-gray-800">结束当前登录状态，并返回登录页重新验证身份。</p>
        </div>
      </div>

      <div class="px-6 py-4 border-t border-gray-200 bg-gray-50/60 flex justify-end gap-3">
        <button class="btn-default" @click="goBack" :disabled="submitting">取消</button>
        <!-- 提交按钮与 loading 态绑定，防止退出请求期间再次触发。 -->
        <button
          class="btn-danger btn-loading"
          @click="confirmLogout"
          :disabled="submitting"
        >
          <Loader2 v-if="submitting" class="btn-loading-icon" />
          {{ submitting ? '退出中...' : '确认退出登录' }}
        </button>
      </div>
    </div>
  </div>
</template>
