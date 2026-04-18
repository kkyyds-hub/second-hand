<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loader2 } from 'lucide-vue-next'
import { loginWithPassword } from '@/api/auth'
import { normalizeUserRedirectPath, saveUserSession } from '@/utils/request'
import { USER_BRAND_MARK } from '@/utils/brand'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const errorMessage = ref('')

const form = ref({
  loginId: '',
  secret: '',
})

/**
 * redirect 只允许站内路径，避免把受保护页回跳能力变成开放重定向入口。
 * 真正的净化规则与 401 回跳共用同一个 helper，减少前后行为漂移。
 */
const redirectPath = computed(() => normalizeUserRedirectPath(route.query.redirect))

const handleLogin = async () => {
  if (loading.value) {
    return
  }

  if (!form.value.loginId.trim() || !form.value.secret.trim()) {
    errorMessage.value = '请填写登录账号和密码。'
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''

    const result = await loginWithPassword({
      loginId: form.value.loginId.trim(),
      secret: form.value.secret.trim(),
    })

    saveUserSession(result.token, result.user)
    await router.replace(redirectPath.value)
  } catch (error: any) {
    errorMessage.value = error.message || '登录失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-shell">
    <div class="auth-card">
      <div class="auth-brand"><div class="auth-logo">{{ USER_BRAND_MARK }}</div><h1 class="auth-title">登录用户工作台</h1><p class="auth-desc">使用你的账户进入统一的用户工作台，继续浏览市场与账户服务。</p></div>
      <div v-if="errorMessage" class="auth-message auth-message-danger"><svg class="mt-0.5 h-4 w-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" /></svg><span>{{ errorMessage }}</span></div>
      <form class="space-y-5" @submit.prevent="handleLogin">
        <div><label class="form-label">登录账号</label><input v-model="form.loginId" class="input-standard" type="text" placeholder="请输入用户名、手机号或邮箱" autocomplete="username" :disabled="loading" /></div>
        <div><label class="form-label">登录密码</label><input v-model="form.secret" class="input-standard" type="password" placeholder="请输入密码" autocomplete="current-password" :disabled="loading" /></div>
        <button class="btn-primary w-full" type="submit" :disabled="loading"><Loader2 v-if="loading" class="h-4 w-4 animate-spin" /><span>{{ loading ? '登录中...' : '登录' }}</span></button>
      </form>
      <div class="auth-divider"><div class="auth-links"><span class="text-gray-400">没有账号？</span><div class="auth-link-group"><router-link class="auth-link" to="/register/phone">手机注册</router-link><span class="h-1 w-1 rounded-full bg-gray-300"></span><router-link class="auth-link" to="/register/email">邮箱注册</router-link><span class="h-1 w-1 rounded-full bg-gray-300"></span><router-link class="auth-link" to="/activate/email">邮箱激活</router-link></div></div></div>
      <div class="auth-note"><p class="font-medium text-gray-900">登录说明</p><ul class="mt-2 list-disc space-y-1.5 pl-4 text-gray-500"><li>首次使用可从上方入口进入注册或激活流程。</li><li>登录后系统会保留当前会话状态。</li><li>登录态失效时页面会自动回到登录页。</li></ul></div>
    </div>
  </div>
</template>
