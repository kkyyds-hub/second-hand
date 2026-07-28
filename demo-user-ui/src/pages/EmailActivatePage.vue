<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Loader2 } from 'lucide-vue-next'
import { useRoute } from 'vue-router'
import CommerceAuthShell from '@/components/commerce/CommerceAuthShell.vue'
import { activateEmail, activateEmailByToken } from '@/api/auth'
import { getUserDisplayName, type UserProfile } from '@/utils/request'

const route = useRoute()
const form = ref({ token: '' })
const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const activatedUser = ref<UserProfile | null>(null)
const activatedUserRows = computed(() => !activatedUser.value ? [] : [
  { label: '显示名称', value: getUserDisplayName(activatedUser.value) },
  { label: '登录名', value: activatedUser.value.loginName || '-' },
  { label: '邮箱地址', value: activatedUser.value.email || '-' },
])
const showEmailPreview = import.meta.env.DEV
const previewUrl = import.meta.env.DEV ? (import.meta.env.VITE_EMAIL_PREVIEW_PATH || '/api/user/auth/email-preview/latest') : ''
const getErrorMessage = (error: unknown, fallback: string) => error instanceof Error && error.message ? error.message : fallback
const normalizeQueryToken = (value: unknown): string => Array.isArray(value) ? normalizeQueryToken(value[0]) : typeof value === 'string' ? value.trim() : ''
const openEmailPreview = () => { window.open(previewUrl, '_blank', 'noopener,noreferrer') }

const activate = async (value: string, automatic = false) => {
  try {
    loading.value = true; errorMessage.value = ''; successMessage.value = ''
    const user = automatic ? await activateEmailByToken(value) : await activateEmail({ token: value })
    activatedUser.value = user
    successMessage.value = '邮箱已激活，现在可以返回登录页继续使用。'
  } catch (error: unknown) { errorMessage.value = getErrorMessage(error, '邮箱激活失败，请稍后重试。') } finally { loading.value = false }
}
const handleActivate = async () => {
  if (loading.value) return
  if (!form.value.token.trim()) { errorMessage.value = '请输入激活码。'; return }
  await activate(form.value.token.trim())
}
onMounted(() => {
  const token = normalizeQueryToken(route.query.token)
  if (token) { form.value.token = token; void activate(token, true) }
})
</script>

<template>
  <CommerceAuthShell title="激活邮箱账户" description="打开邮件中的激活链接，或在这里填写激活码。">
    <p v-if="errorMessage" class="commerce-auth-message commerce-auth-message-danger" role="alert">{{ errorMessage }}</p>
    <p v-if="successMessage" class="commerce-auth-message commerce-auth-message-success" role="status">{{ successMessage }}</p>
    <form v-if="!activatedUserRows.length" class="commerce-auth-fields" @submit.prevent="handleActivate">
      <div><label class="form-label" for="email-activation-code">激活码</label><textarea id="email-activation-code" v-model="form.token" class="input-standard commerce-auth-textarea" placeholder="请粘贴邮件中的激活码" :disabled="loading" /></div>
      <button class="btn-primary w-full" type="submit" :disabled="loading"><Loader2 v-if="loading" class="h-4 w-4 animate-spin" aria-hidden="true" /><span>{{ loading ? '激活中...' : '立即激活' }}</span></button>
    </form>
    <section v-else class="commerce-activation-result" aria-label="激活结果">
      <div v-for="row in activatedUserRows" :key="row.label"><p>{{ row.label }}</p><strong :title="String(row.value)">{{ row.value }}</strong></div>
      <router-link class="btn-primary w-full" to="/login">返回登录</router-link>
    </section>
    <template #footer><p>需要其他帮助？</p><div class="commerce-auth-link-group"><router-link to="/login">返回登录</router-link><router-link to="/register/email">继续注册</router-link><button v-if="showEmailPreview" type="button" @click="openEmailPreview">开发邮件预览</button></div></template>
  </CommerceAuthShell>
</template>
