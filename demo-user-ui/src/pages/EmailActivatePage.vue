<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Loader2 } from 'lucide-vue-next'
import { useRoute } from 'vue-router'
import { activateEmail, activateEmailByToken } from '@/api/auth'
import { getUserDisplayName, type UserProfile } from '@/utils/request'

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
  <div class="page-shell px-4 py-10">
    <div class="auth-card">
      <p class="muted-kicker">邮箱激活</p>
      <h1 class="section-title mt-3">完成邮箱激活</h1>
      <p class="section-desc">
        你可以直接打开邮件中的激活链接，也可以把邮件里的 token 粘贴到下方手动完成激活。
      </p>

      <div v-if="errorMessage" class="mt-6 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">
        {{ errorMessage }}
      </div>
      <div v-if="successMessage" class="mt-6 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
        {{ successMessage }}
      </div>

      <form class="mt-6 space-y-5" @submit.prevent="handleActivate">
        <div>
          <label class="form-label">激活 token</label>
          <textarea
            v-model="form.token"
            class="input-standard min-h-28 resize-y"
            placeholder="请粘贴激活邮件中的 token"
            :disabled="loading"
          />
        </div>

        <button class="btn-primary w-full gap-2" type="submit" :disabled="loading">
          <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
          <span>{{ loading ? '激活中...' : '立即激活' }}</span>
        </button>
      </form>

      <div v-if="activatedUserRows.length" class="mt-6 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
        <p class="font-medium text-slate-900">激活结果</p>
        <div class="mt-3 grid gap-3 md:grid-cols-2">
          <div v-for="row in activatedUserRows" :key="row.label">
            <p class="text-xs text-slate-400">{{ row.label }}</p>
            <p class="mt-1 text-sm font-medium text-slate-900">{{ row.value }}</p>
          </div>
        </div>
      </div>

      <div class="mt-6 grid gap-3 md:grid-cols-3">
        <router-link class="btn-default" to="/login">返回登录</router-link>
        <router-link class="btn-default" to="/register/email">继续邮箱注册</router-link>
        <button class="btn-default" type="button" @click="openEmailPreview">打开邮件预览</button>
      </div>
    </div>
  </div>
</template>
