<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loader2 } from 'lucide-vue-next'
import { loginWithPassword } from '@/api/auth'
import { normalizeUserRedirectPath, saveUserSession } from '@/utils/request'

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
  <div class="page-shell flex items-center px-4 py-10">
    <div class="auth-card">
      <p class="muted-kicker">登录</p>
      <h1 class="section-title mt-3">登录到用户端工作台</h1>
      <p class="section-desc">
        支持使用用户名、手机号或邮箱配合密码登录。登录成功后会优先返回你刚刚访问的受保护页面。
      </p>

      <div v-if="errorMessage" class="mt-6 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">
        {{ errorMessage }}
      </div>

      <form class="mt-6 space-y-5" @submit.prevent="handleLogin">
        <div>
          <label class="form-label">登录账号</label>
          <input
            v-model="form.loginId"
            class="input-standard"
            type="text"
            placeholder="请输入用户名、手机号或邮箱"
            autocomplete="username"
            :disabled="loading"
          />
        </div>

        <div>
          <label class="form-label">登录密码</label>
          <input
            v-model="form.secret"
            class="input-standard"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
            :disabled="loading"
          />
        </div>

        <button class="btn-primary w-full gap-2" type="submit" :disabled="loading">
          <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
          <span>{{ loading ? '登录中...' : '立即登录' }}</span>
        </button>
      </form>

      <div class="mt-6 grid gap-3 md:grid-cols-3">
        <router-link class="btn-default" to="/register/phone">手机注册</router-link>
        <router-link class="btn-default" to="/register/email">邮箱注册</router-link>
        <router-link class="btn-default" to="/activate/email">邮箱激活</router-link>
      </div>

      <div class="mt-8 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
        <p class="font-medium text-slate-900">登录说明</p>
        <ul class="mt-3 list-disc space-y-2 pl-5 text-slate-600">
          <li>如果你刚完成注册，可直接从下方入口继续激活邮箱或改用其他注册方式。</li>
          <li>登录后系统会保留当前会话，后续访问受保护页面无需重复输入账号密码。</li>
          <li>若登录状态失效，系统会自动清理本地会话并跳回登录页。</li>
        </ul>
      </div>
    </div>
  </div>
</template>
