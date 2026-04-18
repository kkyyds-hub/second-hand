<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { bindMyPhone, unbindMyPhone } from '@/api/profile'
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
  mobile: currentUser.value?.mobile || '',
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
const currentMobile = computed(() => (currentUser.value?.mobile || '').trim())
const hasBoundMobile = computed(() => Boolean(currentMobile.value))

const normalizedBindMobile = computed(() => bindForm.mobile.trim())
const normalizedBindCode = computed(() => bindForm.verifyCode.trim())
const normalizedUnbindCode = computed(() => unbindForm.verifyCode.trim())

const canSubmitBind = computed(() => {
  return !binding.value && Boolean(normalizedBindMobile.value && normalizedBindCode.value)
})

const canSubmitUnbind = computed(() => {
  return hasBoundMobile.value && !unbinding.value && Boolean(normalizedUnbindCode.value)
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

  if (!normalizedBindMobile.value || !normalizedBindCode.value) {
    bindStatus.value = 'error'
    bindMessage.value = '请完整填写手机号和验证码后再提交。'
    return
  }

  try {
    binding.value = true
    bindStatus.value = 'idle'
    bindMessage.value = ''

    const nextUser = await bindMyPhone(
      {
        mobile: bindForm.mobile,
        verifyCode: bindForm.verifyCode,
      },
      currentUser.value,
    )

    saveCurrentUser(nextUser)
    currentUser.value = nextUser

    bindForm.mobile = nextUser.mobile || normalizedBindMobile.value
    bindForm.verifyCode = ''

    bindStatus.value = 'success'
    bindMessage.value = '手机绑定更新成功。'
  } catch (error: unknown) {
    bindStatus.value = 'error'
    bindMessage.value = readErrorMessage(error, '手机绑定失败，请稍后重试。')
  } finally {
    binding.value = false
  }
}

async function submitUnbindForm() {
  if (unbinding.value) {
    return
  }

  if (!hasBoundMobile.value) {
    unbindStatus.value = 'error'
    unbindMessage.value = '当前没有可解绑的手机号。'
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

    const nextUser = await unbindMyPhone(
      {
        verifyCode: unbindForm.verifyCode,
        currentPassword: unbindForm.currentPassword,
      },
      currentUser.value,
    )

    saveCurrentUser(nextUser)
    currentUser.value = nextUser

    bindForm.mobile = ''
    unbindForm.verifyCode = ''
    unbindForm.currentPassword = ''

    unbindStatus.value = 'success'
    unbindMessage.value = '手机解绑成功。'
  } catch (error: unknown) {
    unbindStatus.value = 'error'
    unbindMessage.value = readErrorMessage(error, '手机解绑失败，请稍后重试。')
  } finally {
    unbinding.value = false
  }
}
</script>

<template>
  <div class="page-body page-body-narrow">
    <section class="page-header">
      <div class="page-header-main"><p class="page-kicker">安全</p><h1 class="page-title">手机绑定管理</h1><p class="page-desc">与邮箱页保持同一布局语法，统一安全页的视觉语言和表单节奏。</p></div>
      <div class="page-actions"><span class="chip chip-neutral">安全设置</span><router-link class="btn-default" to="/account">返回账户中心</router-link></div>
    </section>
    <section v-if="!hasSessionProfile" class="notice-banner notice-banner-warning"><span class="notice-dot bg-orange-500"></span><span>当前没有本地账户快照，请重新登录后再调整手机绑定状态。</span></section>
    <section class="grid gap-6 lg:grid-cols-[0.95fr_1.05fr]">
      <div class="section-panel-muted">
        <div class="section-header section-header-plain"><div><h2 class="section-heading">当前绑定快照</h2><p class="section-subtitle">左侧统一展示账户、手机状态和本页边界说明。</p></div></div>
        <div class="section-body pt-0"><div class="detail-grid"><div class="detail-row"><span class="detail-label">账户显示名</span><span class="detail-value">{{ displayName }}</span></div><div class="detail-row"><span class="detail-label">已绑定手机</span><span class="detail-value font-numeric">{{ currentMobile || '暂未绑定' }}</span></div><div class="meta-item"><p class="meta-label">页面边界</p><p class="meta-value">这里只处理手机绑定与解绑，避免与邮箱表单交错排列造成视觉割裂。</p></div></div></div>
      </div>
      <div class="space-y-6">
        <section class="section-panel">
          <div class="section-header section-header-plain"><div><h2 class="section-heading">绑定或更新手机</h2><p class="section-subtitle">以统一表单壳层承载输入、反馈与提交动作。</p></div></div>
          <form class="section-body pt-0 space-y-4" @submit.prevent="submitBindForm">
            <div><label class="form-label" for="bind-phone-value">新手机号</label><input id="bind-phone-value" v-model="bindForm.mobile" class="input-standard font-numeric" type="tel" autocomplete="tel" placeholder="请输入手机号" :disabled="binding" @input="clearBindStatus" /></div>
            <div><label class="form-label" for="bind-phone-code">验证码</label><input id="bind-phone-code" v-model="bindForm.verifyCode" class="input-standard font-numeric" type="text" autocomplete="one-time-code" placeholder="输入收到的验证码" :disabled="binding" @input="clearBindStatus" /></div>
            <section v-if="bindStatus === 'success'" class="notice-banner notice-banner-success"><span class="notice-dot bg-emerald-500"></span><span>{{ bindMessage }}</span></section>
            <section v-else-if="bindStatus === 'error'" class="notice-banner notice-banner-warning"><span class="notice-dot bg-orange-500"></span><span>{{ bindMessage }}</span></section>
            <div class="pt-2"><button class="btn-primary" type="submit" :disabled="!canSubmitBind">{{ binding ? '提交中...' : '提交绑定' }}</button></div>
          </form>
        </section>
        <section class="section-panel">
          <div class="section-header section-header-plain"><div><h2 class="section-heading">解除绑定</h2><p class="section-subtitle">继续保持克制的风险提示和统一按钮样式。</p></div></div>
          <form class="section-body pt-0 space-y-4" @submit.prevent="submitUnbindForm">
            <div><label class="form-label" for="unbind-phone-code">原手机验证码</label><input id="unbind-phone-code" v-model="unbindForm.verifyCode" class="input-standard font-numeric" type="text" autocomplete="one-time-code" placeholder="输入当前绑定手机号收到的验证码" :disabled="unbinding || !hasBoundMobile" @input="clearUnbindStatus" /></div>
            <div><label class="form-label" for="unbind-phone-password">当前账户密码（可选）</label><input id="unbind-phone-password" v-model="unbindForm.currentPassword" class="input-standard" type="password" autocomplete="current-password" placeholder="如后端要求二次校验可填写" :disabled="unbinding || !hasBoundMobile" @input="clearUnbindStatus" /></div>
            <p v-if="!hasBoundMobile" class="text-[12px] text-gray-500">当前没有可解除绑定的手机号。</p>
            <section v-if="unbindStatus === 'success'" class="notice-banner notice-banner-success"><span class="notice-dot bg-emerald-500"></span><span>{{ unbindMessage }}</span></section>
            <section v-else-if="unbindStatus === 'error'" class="notice-banner notice-banner-warning"><span class="notice-dot bg-orange-500"></span><span>{{ unbindMessage }}</span></section>
            <div class="pt-2"><button class="btn-danger" type="submit" :disabled="!canSubmitUnbind">{{ unbinding ? '提交中...' : '提交解绑' }}</button></div>
          </form>
        </section>
      </div>
    </section>
  </div>
</template>
