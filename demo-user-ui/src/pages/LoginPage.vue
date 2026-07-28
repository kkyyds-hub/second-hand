<script setup lang="ts">
import { computed, ref } from 'vue'
import { Loader2 } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import CommerceAuthShell from '@/components/commerce/CommerceAuthShell.vue'
import { loginWithPassword } from '@/api/auth'
import { normalizeUserRedirectPath, saveUserSession } from '@/utils/request'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const form = ref({ loginId: '', secret: '' })

const redirectPath = computed(() => normalizeUserRedirectPath(route.query.redirect))
const getErrorMessage = (error: unknown, fallback: string) => error instanceof Error && error.message ? error.message : fallback

const handleLogin = async () => {
  if (loading.value) return

  if (!form.value.loginId.trim() || !form.value.secret.trim()) {
    errorMessage.value = '请填写登录账号和密码。'
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''
    const result = await loginWithPassword({ loginId: form.value.loginId.trim(), secret: form.value.secret.trim() })
    saveUserSession(result.token, result.user)
    await router.replace(redirectPath.value)
  } catch (error: unknown) {
    errorMessage.value = getErrorMessage(error, '登录失败，请稍后重试。')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <CommerceAuthShell title="欢迎回到拾光集" description="登录后继续浏览、收藏和管理你的二手交易。">
    <p v-if="errorMessage" class="commerce-auth-message commerce-auth-message-danger" role="alert">{{ errorMessage }}</p>
    <form class="commerce-auth-fields" @submit.prevent="handleLogin">
      <div><label class="form-label" for="login-id">登录账号</label><input id="login-id" v-model="form.loginId" class="input-standard" type="text" placeholder="请输入用户名、手机号或邮箱" autocomplete="username" :disabled="loading" /></div>
      <div><label class="form-label" for="login-secret">登录密码</label><input id="login-secret" v-model="form.secret" class="input-standard" type="password" placeholder="请输入密码" autocomplete="current-password" :disabled="loading" /></div>
      <button class="btn-primary w-full" type="submit" :disabled="loading"><Loader2 v-if="loading" class="h-4 w-4 animate-spin" aria-hidden="true" /><span>{{ loading ? '登录中...' : '登录' }}</span></button>
    </form>
    <template #footer>
      <p>还没有账户？</p>
      <div class="commerce-auth-link-group"><router-link to="/register/phone">手机注册</router-link><router-link to="/register/email">邮箱注册</router-link><router-link to="/activate/email">邮箱激活</router-link></div>
    </template>
  </CommerceAuthShell>
</template>
