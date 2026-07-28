<script setup lang="ts">
import { computed, ref } from 'vue'
import { Loader2, LockKeyhole, ShieldCheck, X } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import AdminBrand from '@/components/admin/AdminBrand.vue'
import AdminOverlay from '@/components/admin/AdminOverlay.vue'
import { login } from '@/api/auth'
import { sanitizeAdminRedirect } from '@/utils/adminRoute'
import { saveAdminToken, saveAdminUser } from '@/utils/request'

const router = useRouter()
const route = useRoute()
const loginId = ref('')
const password = ref('')
const remember = ref(false)
const loading = ref(false)
const errorMessage = ref('')
const showLoginHelp = ref(false)
const redirectPath = computed(() => sanitizeAdminRedirect(route.query.redirect as string | undefined))

const handleLogin = async () => {
  if (loading.value) return
  if (!loginId.value.trim() || !password.value) {
    errorMessage.value = '请输入账号和密码。'
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''
    const result = await login({ loginId: loginId.value.trim(), password: password.value })
    if (!result?.token) {
      errorMessage.value = '登录失败，未获取到有效凭证。'
      return
    }
    saveAdminToken(result.token, remember.value)
    saveAdminUser(result.user, remember.value)
    await router.replace(redirectPath.value)
  } catch (error: unknown) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败，请检查账号密码或网络状态。'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="admin-login-page">
    <section class="admin-login-shell" aria-labelledby="login-title">
      <div class="admin-login-visual" aria-label="运营管理工作台抽象视觉">
        <div class="admin-login-visual-grid" aria-hidden="true"></div>
        <div class="admin-login-visual-tag">二手交易运营</div>
        <div class="admin-login-visual-copy">
          <AdminBrand inverted />
          <p>围绕审核、订单与风险处理建立清晰的运营工作流。</p>
        </div>
        <div class="admin-login-visual-board" aria-hidden="true"><span></span><span></span><span></span><i></i><i></i></div>
      </div>

      <div class="admin-login-form-wrap">
        <div class="admin-login-form">
          <AdminBrand class="admin-login-mobile-brand" />
          <p class="admin-eyebrow">运营管理</p>
          <h1 id="login-title">登录拾光集运营后台</h1>
          <p class="admin-login-intro">使用已分配的管理账号进入工作台。</p>

          <div v-if="errorMessage" class="admin-form-alert" role="alert" aria-live="assertive"><ShieldCheck aria-hidden="true" />{{ errorMessage }}</div>

          <form class="admin-login-form-fields" @submit.prevent="handleLogin">
            <div>
              <label for="admin-login-id">账号</label>
              <input id="admin-login-id" v-model="loginId" class="input-standard" type="text" autocomplete="username" placeholder="手机号或邮箱" :disabled="loading" autofocus />
            </div>
            <div>
              <div class="admin-field-header"><label for="admin-login-password">密码</label><button type="button" class="admin-text-button" :disabled="loading" @click="showLoginHelp = true">无法登录？</button></div>
              <input id="admin-login-password" v-model="password" class="input-standard" type="password" autocomplete="current-password" placeholder="请输入密码" :disabled="loading" />
            </div>
            <label class="admin-checkbox-row"><input v-model="remember" class="checkbox-standard" type="checkbox" :disabled="loading" /><span>保持登录状态</span></label>
            <button class="btn-primary admin-login-submit" type="submit" :disabled="loading"><Loader2 v-if="loading" class="btn-loading-icon" aria-hidden="true" />{{ loading ? '登录中...' : '登录' }}</button>
          </form>
          <p class="admin-login-note"><LockKeyhole aria-hidden="true" />未勾选时，登录状态仅在当前浏览器会话中保留。</p>
        </div>
      </div>
    </section>

    <AdminOverlay :open="showLoginHelp" title-id="login-help-title" description-id="login-help-description" @close="showLoginHelp = false">
      <div class="admin-dialog-header"><div><p class="admin-eyebrow">访问协助</p><h2 id="login-help-title">无法登录？</h2></div><button class="admin-icon-button" type="button" aria-label="关闭登录帮助" @click="showLoginHelp = false"><X aria-hidden="true" /></button></div>
      <p id="login-help-description" class="admin-dialog-description">请联系平台主管理员核验账号并协助恢复访问。</p>
      <div class="admin-dialog-footer"><button class="btn-primary" type="button" @click="showLoginHelp = false">我知道了</button></div>
    </AdminOverlay>
  </main>
</template>
