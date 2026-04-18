<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Loader2 } from 'lucide-vue-next'
import { useRoute } from 'vue-router'
import { activateEmail, activateEmailByToken } from '@/api/auth'
import { getUserDisplayName, type UserProfile } from '@/utils/request'
import { USER_BRAND_MARK } from '@/utils/brand'

const route = useRoute()

const form = ref({
  token: '',
})

const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const activatedUser = ref<UserProfile | null>(null)

const activatedUserRows = computed(() => {
  if (!activatedUser.value) {
    return []
  }

  return [
    { label: '用户 ID', value: activatedUser.value.id ?? '-' },
    { label: '显示名称', value: getUserDisplayName(activatedUser.value) },
    { label: '登录名', value: activatedUser.value.loginName || '-' },
    { label: '邮箱地址', value: activatedUser.value.email || '-' },
  ]
})

const normalizeQueryToken = (value: unknown): string => {
  if (Array.isArray(value)) {
    return normalizeQueryToken(value[0])
  }

  return typeof value === 'string' ? value.trim() : ''
}

const previewUrl = import.meta.env.VITE_EMAIL_PREVIEW_PATH || '/api/user/auth/email-preview/latest'

const openEmailPreview = () => {
  window.open(previewUrl, '_blank', 'noopener,noreferrer')
}

const handleActivate = async () => {
  if (loading.value) {
    return
  }

  if (!form.value.token.trim()) {
    errorMessage.value = '请输入激活 token。'
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''
    successMessage.value = ''

    const user = await activateEmail({ token: form.value.token.trim() })
    activatedUser.value = user
    successMessage.value = '邮箱已激活，现在可以返回登录页继续使用。'
  } catch (error: any) {
    errorMessage.value = error.message || '邮箱激活失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

/**
 * 激活页同时支持两种入口：
 * 1. 用户手动粘贴 token 后提交；
 * 2. 直接点击邮件里的 query token 链接自动激活。
 * 自动激活只在首次进入页面时触发，避免路由细碎变化导致重复请求。
 */
const autoActivateByQuery = async (token: string) => {
  try {
    loading.value = true
    errorMessage.value = ''
    successMessage.value = ''

    const user = await activateEmailByToken(token)
    activatedUser.value = user
    successMessage.value = '邮箱已激活，现在可以返回登录页继续使用。'
  } catch (error: any) {
    errorMessage.value = error.message || '邮箱激活失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  const token = normalizeQueryToken(route.query.token)
  if (token) {
    form.value.token = token
    autoActivateByQuery(token)
  }
})
</script>

<template>
  <div class="auth-shell">
    <div class="auth-card">
      <div class="auth-brand"><div class="auth-logo">{{ USER_BRAND_MARK }}</div><h1 class="auth-title">激活用户工作台账户</h1><p class="auth-desc">粘贴激活令牌或直接打开邮件链接，完成用户工作台账户激活。</p></div>
      <div v-if="errorMessage" class="auth-message auth-message-danger"><svg class="mt-0.5 h-4 w-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" /></svg><span>{{ errorMessage }}</span></div>
      <div v-if="successMessage" class="auth-message auth-message-success"><svg class="mt-0.5 h-4 w-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" /></svg><span>{{ successMessage }}</span></div>
      <form v-if="!activatedUserRows.length" class="space-y-5" @submit.prevent="handleActivate">
        <div><label class="form-label">激活令牌</label><textarea v-model="form.token" class="input-standard min-h-[120px] resize-none" placeholder="请粘贴激活邮件中的令牌" :disabled="loading" /></div>
        <button class="btn-primary w-full" type="submit" :disabled="loading"><Loader2 v-if="loading" class="h-4 w-4 animate-spin" /><span>{{ loading ? '激活中...' : '立即激活' }}</span></button>
      </form>
      <div v-if="activatedUserRows.length" class="rounded-2xl border border-gray-200/80 bg-gray-50/80 p-5 shadow-sm"><div class="flex items-center gap-3 border-b border-gray-200/70 pb-4"><div class="flex h-9 w-9 items-center justify-center rounded-full bg-emerald-100 text-emerald-700"><svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" /></svg></div><div><p class="text-[15px] font-semibold text-gray-900">激活成功</p><p class="text-[12px] text-gray-500">你的账户现在可以正常使用。</p></div></div><div class="mt-4 grid gap-4 sm:grid-cols-2"><div v-for="row in activatedUserRows" :key="row.label" class="meta-item"><p class="meta-label">{{ row.label }}</p><p class="meta-value truncate" :title="String(row.value)">{{ row.value }}</p></div></div></div>
      <div class="auth-divider"><div class="auth-links"><span class="text-gray-400">需要其他帮助？</span><div class="auth-link-group"><router-link class="auth-link" to="/login">返回登录</router-link><span class="h-1 w-1 rounded-full bg-gray-300"></span><router-link class="auth-link" to="/register/email">继续注册</router-link><span class="h-1 w-1 rounded-full bg-gray-300"></span><button class="auth-link cursor-pointer border-0 bg-transparent p-0" type="button" @click="openEmailPreview">邮件预览</button></div></div></div>
    </div>
  </div>
</template>
