<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { bindMyEmail, unbindMyEmail } from '@/api/profile'
import {
  getUserDisplayName,
  hasUserProfileSnapshot,
  readCurrentUser,
  saveCurrentUser,
  type UserProfile,
} from '@/utils/request'

type ActionStatus = 'idle' | 'success' | 'error'

const currentUser = ref<UserProfile | null>(readCurrentUser())

const bindForm = reactive({
  email: currentUser.value?.email || '',
  verifyCode: '',
})

const unbindForm = reactive({
  verifyCode: '',
  currentPassword: '',
})

const binding = ref(false)
const bindStatus = ref<ActionStatus>('idle')
const bindMessage = ref('')

const unbinding = ref(false)
const unbindStatus = ref<ActionStatus>('idle')
const unbindMessage = ref('')

const hasSessionProfile = computed(() => hasUserProfileSnapshot(currentUser.value))
const displayName = computed(() => getUserDisplayName(currentUser.value))
const currentEmail = computed(() => (currentUser.value?.email || '').trim())
const hasBoundEmail = computed(() => Boolean(currentEmail.value))

const normalizedBindEmail = computed(() => bindForm.email.trim())
const normalizedBindCode = computed(() => bindForm.verifyCode.trim())
const normalizedUnbindCode = computed(() => unbindForm.verifyCode.trim())

const canSubmitBind = computed(() => {
  return !binding.value && Boolean(normalizedBindEmail.value && normalizedBindCode.value)
})

const canSubmitUnbind = computed(() => {
  return hasBoundEmail.value && !unbinding.value && Boolean(normalizedUnbindCode.value)
})

function readErrorMessage(error: unknown, fallbackMessage: string) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return fallbackMessage
}

function clearBindStatus() {
  if (binding.value || bindStatus.value === 'idle') {
    return
  }

  bindStatus.value = 'idle'
  bindMessage.value = ''
}

function clearUnbindStatus() {
  if (unbinding.value || unbindStatus.value === 'idle') {
    return
  }

  unbindStatus.value = 'idle'
  unbindMessage.value = ''
}

async function submitBindForm() {
  if (binding.value) {
    return
  }

  if (!normalizedBindEmail.value || !normalizedBindCode.value) {
    bindStatus.value = 'error'
    bindMessage.value = '请完整填写邮箱和验证码后再提交。'
    return
  }

  try {
    binding.value = true
    bindStatus.value = 'idle'
    bindMessage.value = ''

    const nextUser = await bindMyEmail(
      {
        email: bindForm.email,
        verifyCode: bindForm.verifyCode,
      },
      currentUser.value,
    )

    saveCurrentUser(nextUser)
    currentUser.value = nextUser

    bindForm.email = nextUser.email || normalizedBindEmail.value
    bindForm.verifyCode = ''

    bindStatus.value = 'success'
    bindMessage.value = '邮箱绑定更新成功。'
  } catch (error: unknown) {
    bindStatus.value = 'error'
    bindMessage.value = readErrorMessage(error, '邮箱绑定失败，请稍后重试。')
  } finally {
    binding.value = false
  }
}

async function submitUnbindForm() {
  if (unbinding.value) {
    return
  }

  if (!hasBoundEmail.value) {
    unbindStatus.value = 'error'
    unbindMessage.value = '当前没有可解绑的邮箱。'
    return
  }

  if (!normalizedUnbindCode.value) {
    unbindStatus.value = 'error'
    unbindMessage.value = '验证码不能为空。'
    return
  }

  try {
    unbinding.value = true
    unbindStatus.value = 'idle'
    unbindMessage.value = ''

    const nextUser = await unbindMyEmail(
      {
        verifyCode: unbindForm.verifyCode,
        currentPassword: unbindForm.currentPassword,
      },
      currentUser.value,
    )

    saveCurrentUser(nextUser)
    currentUser.value = nextUser

    bindForm.email = ''
    unbindForm.verifyCode = ''
    unbindForm.currentPassword = ''

    unbindStatus.value = 'success'
    unbindMessage.value = '邮箱解绑成功。'
  } catch (error: unknown) {
    unbindStatus.value = 'error'
    unbindMessage.value = readErrorMessage(error, '邮箱解绑失败，请稍后重试。')
  } finally {
    unbinding.value = false
  }
}
</script>

<template>
  <div class="page-body page-body-narrow">
    <section class="page-header">
      <div class="page-header-main"><p class="page-kicker">安全</p><h1 class="page-title">邮箱绑定管理</h1><p class="page-desc">绑定、解绑和当前状态说明统一放入同一视觉体系下，避免同一域内再拆成两套样式。</p></div>
      <div class="page-actions"><span class="chip chip-neutral">安全设置</span><router-link class="btn-default" to="/account">返回账户中心</router-link></div>
    </section>
    <section v-if="!hasSessionProfile" class="notice-banner notice-banner-warning"><span class="notice-dot bg-orange-500"></span><span>当前没有本地账户快照，请重新登录后再调整邮箱绑定状态。</span></section>
    <section class="grid gap-6 lg:grid-cols-[0.95fr_1.05fr]">
      <div class="section-panel-muted">
        <div class="section-header section-header-plain"><div><h2 class="section-heading">当前绑定快照</h2><p class="section-subtitle">左侧统一展示当前账户、邮箱状态和页面边界说明。</p></div></div>
        <div class="section-body pt-0"><div class="detail-grid"><div class="detail-row"><span class="detail-label">账户显示名</span><span class="detail-value">{{ displayName }}</span></div><div class="detail-row"><span class="detail-label">已绑定邮箱</span><span class="detail-value font-numeric">{{ currentEmail || '暂未绑定' }}</span></div><div class="meta-item"><p class="meta-label">页面边界</p><p class="meta-value">这里只处理邮箱绑定与解绑，不再混入手机等其他安全表单。</p></div></div></div>
      </div>
      <div class="space-y-6">
        <section class="section-panel">
          <div class="section-header section-header-plain"><div><h2 class="section-heading">绑定或更新邮箱</h2><p class="section-subtitle">表单和反馈区域统一使用一套留白和圆角规则。</p></div></div>
          <form class="section-body pt-0 space-y-4" @submit.prevent="submitBindForm">
            <div><label class="form-label" for="bind-email-value">新邮箱地址</label><input id="bind-email-value" v-model="bindForm.email" class="input-standard" type="email" autocomplete="email" placeholder="请输入有效邮箱" :disabled="binding" @input="clearBindStatus" /></div>
            <div><label class="form-label" for="bind-email-code">验证码</label><input id="bind-email-code" v-model="bindForm.verifyCode" class="input-standard font-numeric" type="text" autocomplete="one-time-code" placeholder="输入收到的验证码" :disabled="binding" @input="clearBindStatus" /></div>
            <section v-if="bindStatus === 'success'" class="notice-banner notice-banner-success"><span class="notice-dot bg-emerald-500"></span><span>{{ bindMessage }}</span></section>
            <section v-else-if="bindStatus === 'error'" class="notice-banner notice-banner-warning"><span class="notice-dot bg-orange-500"></span><span>{{ bindMessage }}</span></section>
            <div class="pt-2"><button class="btn-primary" type="submit" :disabled="!canSubmitBind">{{ binding ? '提交中...' : '提交绑定' }}</button></div>
          </form>
        </section>
        <section class="section-panel">
          <div class="section-header section-header-plain"><div><h2 class="section-heading">解除绑定</h2><p class="section-subtitle">弱化危险视觉噪声，但保留必要的风险提示。</p></div></div>
          <form class="section-body pt-0 space-y-4" @submit.prevent="submitUnbindForm">
            <div><label class="form-label" for="unbind-email-code">原邮箱验证码</label><input id="unbind-email-code" v-model="unbindForm.verifyCode" class="input-standard font-numeric" type="text" autocomplete="one-time-code" placeholder="输入当前绑定邮箱收到的验证码" :disabled="unbinding || !hasBoundEmail" @input="clearUnbindStatus" /></div>
            <div><label class="form-label" for="unbind-email-password">当前账户密码（可选）</label><input id="unbind-email-password" v-model="unbindForm.currentPassword" class="input-standard" type="password" autocomplete="current-password" placeholder="如后端要求二次校验可填写" :disabled="unbinding || !hasBoundEmail" @input="clearUnbindStatus" /></div>
            <p v-if="!hasBoundEmail" class="text-[12px] text-gray-500">当前没有可解除绑定的邮箱。</p>
            <section v-if="unbindStatus === 'success'" class="notice-banner notice-banner-success"><span class="notice-dot bg-emerald-500"></span><span>{{ unbindMessage }}</span></section>
            <section v-else-if="unbindStatus === 'error'" class="notice-banner notice-banner-warning"><span class="notice-dot bg-orange-500"></span><span>{{ unbindMessage }}</span></section>
            <div class="pt-2"><button class="btn-danger" type="submit" :disabled="!canSubmitUnbind">{{ unbinding ? '提交中...' : '提交解绑' }}</button></div>
          </form>
        </section>
      </div>
    </section>
  </div>
</template>
