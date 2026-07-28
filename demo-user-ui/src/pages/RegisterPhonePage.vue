<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { Loader2 } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import CommerceAuthShell from '@/components/commerce/CommerceAuthShell.vue'
import { registerByPhone, sendSmsCode } from '@/api/auth'

const router = useRouter()
const form = ref({ mobile: '', smsCode: '', secret: '', nickname: '' })
const sendingCode = ref(false)
const submitting = ref(false)
const message = ref('')
const errorMessage = ref('')
const countdown = ref(0)
let countdownTimerId: number | null = null
let redirectTimerId: number | null = null

const getErrorMessage = (error: unknown, fallback: string) => error instanceof Error && error.message ? error.message : fallback
const stopCountdown = () => { if (countdownTimerId !== null) { window.clearInterval(countdownTimerId); countdownTimerId = null } }
const startCountdown = () => {
  stopCountdown()
  countdown.value = 60
  countdownTimerId = window.setInterval(() => { countdown.value -= 1; if (countdown.value <= 0) { countdown.value = 0; stopCountdown() } }, 1000)
}
const scheduleRedirectToLogin = () => {
  if (redirectTimerId !== null) window.clearTimeout(redirectTimerId)
  redirectTimerId = window.setTimeout(() => { router.push('/login') }, 900)
}
const handleSendCode = async () => {
  if (sendingCode.value || countdown.value > 0 || submitting.value) return
  if (!/^1\d{10}$/.test(form.value.mobile.trim())) { errorMessage.value = '请输入有效的 11 位手机号。'; return }
  try {
    sendingCode.value = true; errorMessage.value = ''; message.value = ''
    await sendSmsCode({ mobile: form.value.mobile.trim() })
    message.value = '验证码已发送，请留意短信。'
    startCountdown()
  } catch (error: unknown) { errorMessage.value = getErrorMessage(error, '验证码发送失败，请稍后重试。') } finally { sendingCode.value = false }
}
const handleRegister = async () => {
  if (submitting.value) return
  if (!form.value.mobile.trim() || !form.value.smsCode.trim() || !form.value.secret.trim() || !form.value.nickname.trim()) { errorMessage.value = '请完整填写手机号、验证码、昵称和密码。'; return }
  try {
    submitting.value = true; errorMessage.value = ''; message.value = ''
    await registerByPhone({ mobile: form.value.mobile.trim(), smsCode: form.value.smsCode.trim(), secret: form.value.secret.trim(), nickname: form.value.nickname.trim() })
    message.value = '注册成功，即将返回登录页。'
    scheduleRedirectToLogin()
  } catch (error: unknown) { errorMessage.value = getErrorMessage(error, '注册失败，请稍后重试。') } finally { submitting.value = false }
}
onUnmounted(() => { stopCountdown(); if (redirectTimerId !== null) window.clearTimeout(redirectTimerId) })
</script>

<template>
  <CommerceAuthShell title="手机号注册" description="使用手机号创建拾光集账户。">
    <p v-if="errorMessage" class="commerce-auth-message commerce-auth-message-danger" role="alert">{{ errorMessage }}</p>
    <p v-if="message" class="commerce-auth-message commerce-auth-message-success" role="status">{{ message }}</p>
    <form class="commerce-auth-fields" @submit.prevent="handleRegister">
      <div><label class="form-label" for="register-mobile">手机号</label><input id="register-mobile" v-model="form.mobile" class="input-standard" type="tel" inputmode="numeric" placeholder="请输入 11 位手机号" autocomplete="tel" :disabled="submitting" /></div>
      <div><label class="form-label" for="register-sms-code">短信验证码</label><div class="inline-form-row"><input id="register-sms-code" v-model="form.smsCode" class="input-standard min-w-0 flex-1" type="text" inputmode="numeric" placeholder="输入短信验证码" autocomplete="one-time-code" :disabled="submitting" /><button class="btn-default inline-form-action" type="button" :disabled="sendingCode || countdown > 0 || submitting" @click="handleSendCode">{{ countdown > 0 ? `${countdown}s 后重试` : sendingCode ? '发送中...' : '发送验证码' }}</button></div></div>
      <div><label class="form-label" for="register-nickname">昵称</label><input id="register-nickname" v-model="form.nickname" class="input-standard" type="text" placeholder="请输入昵称" autocomplete="nickname" :disabled="submitting" /></div>
      <div><label class="form-label" for="register-secret">登录密码</label><input id="register-secret" v-model="form.secret" class="input-standard" type="password" placeholder="请至少设置 6 位密码" autocomplete="new-password" :disabled="submitting" /></div>
      <button class="btn-primary w-full" type="submit" :disabled="submitting"><Loader2 v-if="submitting" class="h-4 w-4 animate-spin" aria-hidden="true" /><span>{{ submitting ? '提交中...' : '完成注册' }}</span></button>
    </form>
    <template #footer><p>已有账号？</p><div class="commerce-auth-link-group"><router-link to="/login">返回登录</router-link><router-link to="/register/email">改用邮箱注册</router-link></div></template>
  </CommerceAuthShell>
</template>
