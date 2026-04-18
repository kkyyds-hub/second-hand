<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Loader2 } from 'lucide-vue-next'
import { registerByPhone, sendSmsCode } from '@/api/auth'
import { USER_BRAND_MARK } from '@/utils/brand'

const router = useRouter()

const form = ref({
  mobile: '',
  smsCode: '',
  secret: '',
  nickname: '',
})

const sendingCode = ref(false)
const submitting = ref(false)
const message = ref('')
const errorMessage = ref('')
const countdown = ref(0)

let countdownTimerId: number | null = null
let redirectTimerId: number | null = null

const stopCountdown = () => {
  if (countdownTimerId !== null) {
    window.clearInterval(countdownTimerId)
    countdownTimerId = null
  }
}

/**
 * 发送验证码成功后立即进入倒计时，阻止用户在短信未到达前反复点击。
 * 这类提交节流属于页面交互态，应留在页面层，而不是下沉到 API 层。
 */
const startCountdown = () => {
  stopCountdown()
  countdown.value = 60

  countdownTimerId = window.setInterval(() => {
    countdown.value -= 1

    if (countdown.value <= 0) {
      countdown.value = 0
      stopCountdown()
    }
  }, 1000)
}

const scheduleRedirectToLogin = () => {
  if (redirectTimerId !== null) {
    window.clearTimeout(redirectTimerId)
  }

  redirectTimerId = window.setTimeout(() => {
    router.push('/login')
  }, 900)
}

const handleSendCode = async () => {
  if (sendingCode.value || countdown.value > 0 || submitting.value) {
    return
  }

  if (!/^1\d{10}$/.test(form.value.mobile.trim())) {
    errorMessage.value = '请输入有效的 11 位手机号。'
    return
  }

  try {
    sendingCode.value = true
    errorMessage.value = ''
    message.value = ''

    await sendSmsCode({ mobile: form.value.mobile.trim() })
    message.value = '验证码已发送，请留意短信。'
    startCountdown()
  } catch (error: any) {
    errorMessage.value = error.message || '验证码发送失败，请稍后重试。'
  } finally {
    sendingCode.value = false
  }
}

const handleRegister = async () => {
  if (submitting.value) {
    return
  }

  if (!form.value.mobile.trim() || !form.value.smsCode.trim() || !form.value.secret.trim() || !form.value.nickname.trim()) {
    errorMessage.value = '请完整填写手机号、验证码、昵称和密码。'
    return
  }

  try {
    submitting.value = true
    errorMessage.value = ''
    message.value = ''

    await registerByPhone({
      mobile: form.value.mobile.trim(),
      smsCode: form.value.smsCode.trim(),
      secret: form.value.secret.trim(),
      nickname: form.value.nickname.trim(),
    })

    message.value = '注册成功，正在跳转到登录页...'
    scheduleRedirectToLogin()
  } catch (error: any) {
    errorMessage.value = error.message || '注册失败，请稍后重试。'
  } finally {
    submitting.value = false
  }
}

onUnmounted(() => {
  stopCountdown()

  /**
   * 注册成功后的延迟跳转也需要在卸载时清理，避免用户手动离页后仍被旧定时器带回登录页。
   */
  if (redirectTimerId !== null) {
    window.clearTimeout(redirectTimerId)
  }
})
</script>

<template>
  <div class="auth-shell">
    <div class="auth-card">
      <div class="auth-brand"><div class="auth-logo">{{ USER_BRAND_MARK }}</div><h1 class="auth-title">注册用户工作台</h1><p class="auth-desc">使用手机号创建用户工作台账户，表单与提示保持同一套中性语法。</p></div>
      <div v-if="errorMessage" class="auth-message auth-message-danger"><svg class="mt-0.5 h-4 w-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" /></svg><span>{{ errorMessage }}</span></div>
      <div v-if="message" class="auth-message auth-message-success"><svg class="mt-0.5 h-4 w-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg><span>{{ message }}</span></div>
      <form class="space-y-5" @submit.prevent="handleRegister">
        <div><label class="form-label">手机号</label><input v-model="form.mobile" class="input-standard" type="text" inputmode="numeric" placeholder="请输入 11 位手机号" autocomplete="tel" :disabled="submitting" /></div>
        <div><label class="form-label">短信验证码</label><div class="inline-form-row"><input v-model="form.smsCode" class="input-standard flex-1" type="text" placeholder="输入短信验证码" :disabled="submitting" /><button class="btn-default inline-form-action" type="button" :disabled="sendingCode || countdown > 0 || submitting" @click="handleSendCode">{{ countdown > 0 ? `${countdown}s 后重试` : sendingCode ? '发送中...' : '发送验证码' }}</button></div></div>
        <div><label class="form-label">昵称</label><input v-model="form.nickname" class="input-standard" type="text" placeholder="请输入昵称" autocomplete="nickname" :disabled="submitting" /></div>
        <div><label class="form-label">登录密码</label><input v-model="form.secret" class="input-standard" type="password" placeholder="请至少设置 6 位密码" autocomplete="new-password" :disabled="submitting" /></div>
        <button class="btn-primary w-full" type="submit" :disabled="submitting"><Loader2 v-if="submitting" class="h-4 w-4 animate-spin" /><span>{{ submitting ? '提交中...' : '完成注册' }}</span></button>
      </form>
      <div class="auth-divider"><div class="auth-links"><span class="text-gray-400">已有账号？</span><div class="auth-link-group"><router-link class="auth-link" to="/login">返回登录</router-link><span class="h-1 w-1 rounded-full bg-gray-300"></span><router-link class="auth-link" to="/register/email">改用邮箱注册</router-link></div></div></div>
    </div>
  </div>
</template>
