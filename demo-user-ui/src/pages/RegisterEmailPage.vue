<script setup lang="ts">
import { ref } from 'vue'
import { Loader2 } from 'lucide-vue-next'
import CommerceAuthShell from '@/components/commerce/CommerceAuthShell.vue'
import { registerByEmail } from '@/api/auth'

const form = ref({ email: '', emailCode: '', secret: '', nickname: '' })
const submitting = ref(false)
const errorMessage = ref('')
const message = ref('')
const showEmailPreview = import.meta.env.DEV
const previewUrl = import.meta.env.DEV ? (import.meta.env.VITE_EMAIL_PREVIEW_PATH || '/api/user/auth/email-preview/latest') : ''
const getErrorMessage = (error: unknown, fallback: string) => error instanceof Error && error.message ? error.message : fallback
const openEmailPreview = () => { window.open(previewUrl, '_blank', 'noopener,noreferrer') }

const handleRegister = async () => {
  if (submitting.value) return
  if (!form.value.email.trim() || !form.value.secret.trim() || !form.value.nickname.trim()) { errorMessage.value = '请填写邮箱、昵称和密码。'; return }
  try {
    submitting.value = true; errorMessage.value = ''; message.value = ''
    await registerByEmail({ email: form.value.email.trim(), emailCode: form.value.emailCode.trim() || undefined, secret: form.value.secret.trim(), nickname: form.value.nickname.trim() })
    message.value = '注册成功，请前往邮箱完成激活。'
  } catch (error: unknown) { errorMessage.value = getErrorMessage(error, '注册失败，请稍后重试。') } finally { submitting.value = false }
}
</script>

<template>
  <CommerceAuthShell title="邮箱注册" description="使用邮箱创建拾光集账户，注册后需要完成邮箱激活。">
    <p v-if="errorMessage" class="commerce-auth-message commerce-auth-message-danger" role="alert">{{ errorMessage }}</p>
    <p v-if="message" class="commerce-auth-message commerce-auth-message-success" role="status">{{ message }}</p>
    <form class="commerce-auth-fields" @submit.prevent="handleRegister">
      <div><label class="form-label" for="register-email">邮箱地址</label><input id="register-email" v-model="form.email" class="input-standard" type="email" placeholder="请输入邮箱地址" autocomplete="email" :disabled="submitting" /></div>
      <div><label class="form-label" for="register-email-code">邮箱验证码（选填）</label><input id="register-email-code" v-model="form.emailCode" class="input-standard" type="text" placeholder="收到验证码时填写" autocomplete="one-time-code" :disabled="submitting" /><p class="form-helper">收到验证码时填写，未收到时可以留空继续注册。</p></div>
      <div><label class="form-label" for="register-email-nickname">昵称</label><input id="register-email-nickname" v-model="form.nickname" class="input-standard" type="text" placeholder="请输入昵称" autocomplete="nickname" :disabled="submitting" /></div>
      <div><label class="form-label" for="register-email-secret">登录密码</label><input id="register-email-secret" v-model="form.secret" class="input-standard" type="password" placeholder="请至少设置 6 位密码" autocomplete="new-password" :disabled="submitting" /></div>
      <button class="btn-primary w-full" type="submit" :disabled="submitting"><Loader2 v-if="submitting" class="h-4 w-4 animate-spin" aria-hidden="true" /><span>{{ submitting ? '提交中...' : '提交注册' }}</span></button>
    </form>
    <template #footer><p>已有账号？</p><div class="commerce-auth-link-group"><router-link to="/login">返回登录</router-link><router-link to="/activate/email">前往激活</router-link><button v-if="showEmailPreview" type="button" @click="openEmailPreview">开发邮件预览</button></div></template>
  </CommerceAuthShell>
</template>
