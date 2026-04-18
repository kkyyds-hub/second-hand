<script setup lang="ts">
import { ref } from 'vue'
import { Loader2 } from 'lucide-vue-next'
import { registerByEmail } from '@/api/auth'
import { USER_BRAND_MARK } from '@/utils/brand'

const form = ref({
  email: '',
  emailCode: '',
  secret: '',
  nickname: '',
})

const submitting = ref(false)
const errorMessage = ref('')
const message = ref('')

const previewUrl = import.meta.env.VITE_EMAIL_PREVIEW_PATH || '/api/user/auth/email-preview/latest'

const openEmailPreview = () => {
  window.open(previewUrl, '_blank', 'noopener,noreferrer')
}

const handleRegister = async () => {
  if (submitting.value) {
    return
  }

  if (!form.value.email.trim() || !form.value.secret.trim() || !form.value.nickname.trim()) {
    errorMessage.value = '请填写邮箱、昵称和密码。'
    return
  }

  try {
    submitting.value = true
    errorMessage.value = ''
    message.value = ''

    await registerByEmail({
      email: form.value.email.trim(),
      emailCode: form.value.emailCode.trim() || undefined,
      secret: form.value.secret.trim(),
      nickname: form.value.nickname.trim(),
    })

    message.value = '注册成功，请前往邮箱完成激活。'
  } catch (error: any) {
    errorMessage.value = error.message || '注册失败，请稍后重试。'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-shell">
    <div class="auth-card">
      <div class="auth-brand"><div class="auth-logo">{{ USER_BRAND_MARK }}</div><h1 class="auth-title">注册用户工作台</h1><p class="auth-desc">使用邮箱创建用户工作台账户，保持统一的灰白中性表单节奏。</p></div>
      <div v-if="errorMessage" class="auth-message auth-message-danger"><svg class="mt-0.5 h-4 w-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" /></svg><span>{{ errorMessage }}</span></div>
      <div v-if="message" class="auth-message auth-message-success"><svg class="mt-0.5 h-4 w-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg><span>{{ message }}</span></div>
      <form class="space-y-5" @submit.prevent="handleRegister">
        <div><label class="form-label">邮箱地址</label><input v-model="form.email" class="input-standard" type="email" placeholder="请输入邮箱地址" autocomplete="email" :disabled="submitting" /></div>
        <div><label class="form-label">邮箱验证码 <span class="text-gray-400 font-normal">（如有）</span></label><input v-model="form.emailCode" class="input-standard" type="text" placeholder="如后端要求，可在这里填写验证码" :disabled="submitting" /></div>
        <div><label class="form-label">昵称</label><input v-model="form.nickname" class="input-standard" type="text" placeholder="请输入昵称" autocomplete="nickname" :disabled="submitting" /></div>
        <div><label class="form-label">登录密码</label><input v-model="form.secret" class="input-standard" type="password" placeholder="请至少设置 6 位密码" autocomplete="new-password" :disabled="submitting" /></div>
        <button class="btn-primary w-full" type="submit" :disabled="submitting"><Loader2 v-if="submitting" class="h-4 w-4 animate-spin" /><span>{{ submitting ? '提交中...' : '提交注册' }}</span></button>
      </form>
      <div class="auth-divider"><div class="auth-links"><span class="text-gray-400">已有账号？</span><div class="auth-link-group"><router-link class="auth-link" to="/login">返回登录</router-link><span class="h-1 w-1 rounded-full bg-gray-300"></span><router-link class="auth-link" to="/activate/email">前往激活</router-link><span class="h-1 w-1 rounded-full bg-gray-300"></span><button class="auth-link cursor-pointer border-0 bg-transparent p-0" type="button" @click="openEmailPreview">邮件预览</button></div></div></div>
    </div>
  </div>
</template>
