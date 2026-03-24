<script setup lang="ts">
import { ref } from 'vue'
import { Loader2 } from 'lucide-vue-next'
import { registerByEmail } from '@/api/auth'

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
  <div class="page-shell px-4 py-10">
    <div class="auth-card">
      <p class="muted-kicker">邮箱注册</p>
      <h1 class="section-title mt-3">使用邮箱创建账号</h1>
      <p class="section-desc">注册成功后请查收激活邮件。若当前环境提供邮件预览，也可直接打开辅助页查看最新邮件。</p>

      <div v-if="errorMessage" class="mt-6 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">
        {{ errorMessage }}
      </div>
      <div v-if="message" class="mt-6 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
        {{ message }}
      </div>

      <form class="mt-6 space-y-5" @submit.prevent="handleRegister">
        <div>
          <label class="form-label">邮箱地址</label>
          <input
            v-model="form.email"
            class="input-standard"
            type="email"
            placeholder="请输入邮箱地址"
            autocomplete="email"
            :disabled="submitting"
          />
        </div>

        <div>
          <label class="form-label">邮件验证码（如有）</label>
          <input
            v-model="form.emailCode"
            class="input-standard"
            type="text"
            placeholder="如果后端要求验证码，可在这里填写"
            :disabled="submitting"
          />
          <p class="form-helper">当前字段保持可选，用于兼容不同环境下的邮箱注册口径。</p>
        </div>

        <div>
          <label class="form-label">昵称</label>
          <input
            v-model="form.nickname"
            class="input-standard"
            type="text"
            placeholder="请输入你的昵称"
            autocomplete="nickname"
            :disabled="submitting"
          />
        </div>

        <div>
          <label class="form-label">登录密码</label>
          <input
            v-model="form.secret"
            class="input-standard"
            type="password"
            placeholder="请至少设置 6 位密码"
            autocomplete="new-password"
            :disabled="submitting"
          />
        </div>

        <button class="btn-primary w-full gap-2" type="submit" :disabled="submitting">
          <Loader2 v-if="submitting" class="h-4 w-4 animate-spin" />
          <span>{{ submitting ? '提交中...' : '提交邮箱注册' }}</span>
        </button>
      </form>

      <div class="mt-6 grid gap-3 md:grid-cols-3">
        <router-link class="btn-default" to="/login">返回登录</router-link>
        <router-link class="btn-default" to="/activate/email">前往激活</router-link>
        <button class="btn-default" type="button" @click="openEmailPreview">打开邮件预览</button>
      </div>
    </div>
  </div>
</template>
