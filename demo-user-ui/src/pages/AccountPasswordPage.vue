<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { changeMyPassword } from '@/api/security'

const form = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const submitting = ref(false)
const submitStatus = ref<'idle' | 'success' | 'error'>('idle')
const submitMessage = ref('')

const hasRequiredFields = computed(() => {
  return Boolean(form.currentPassword.trim() && form.newPassword.trim() && form.confirmPassword.trim())
})

const passwordsMatch = computed(() => form.newPassword === form.confirmPassword)
const canSubmit = computed(() => !submitting.value && hasRequiredFields.value && passwordsMatch.value)

function clearStatus() {
  if (submitting.value || submitStatus.value === 'idle') {
    return
  }

  submitStatus.value = 'idle'
  submitMessage.value = ''
}

async function submitForm() {
  if (submitting.value) {
    return
  }

  if (!hasRequiredFields.value) {
    submitStatus.value = 'error'
    submitMessage.value = '请完整填写所有密码字段。'
    return
  }

  if (!passwordsMatch.value) {
    submitStatus.value = 'error'
    submitMessage.value = '两次输入的新密码不一致。'
    return
  }

  try {
    submitting.value = true
    submitStatus.value = 'idle'
    submitMessage.value = ''

    const message = await changeMyPassword({
      currentPassword: form.currentPassword,
      newPassword: form.newPassword,
      confirmPassword: form.confirmPassword,
    })

    form.currentPassword = ''
    form.newPassword = ''
    form.confirmPassword = ''
    submitStatus.value = 'success'
    submitMessage.value = message || '密码修改成功。'
  } catch (error: unknown) {
    submitStatus.value = 'error'
    submitMessage.value = error instanceof Error && error.message.trim() ? error.message : '密码修改失败，请稍后重试。'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page-body page-body-narrow">
    <section class="page-header">
      <div class="page-header-main"><p class="page-kicker">安全</p><h1 class="page-title">修改登录密码</h1><p class="page-desc">安全页回归同一套表单节奏：标题、说明、主表单、提示卡和提交反馈统一排布。</p></div>
      <div class="page-actions"><span class="chip chip-neutral">安全设置</span><router-link class="btn-default" to="/account">返回账户中心</router-link></div>
    </section>
    <div class="max-w-3xl">
      <section class="section-panel">
        <div class="section-header section-header-plain"><div><h2 class="section-heading">密码更新表单</h2><p class="section-subtitle">统一以中性灰白容器承载输入区与安全提示，减少蓝色块状占比。</p></div></div>
        <form class="section-body pt-0 space-y-5" @submit.prevent="submitForm">
          <div><label class="form-label" for="current-password">当前密码 <span class="text-red-500">*</span></label><input id="current-password" v-model="form.currentPassword" class="input-standard" type="password" autocomplete="current-password" placeholder="请输入当前密码" :disabled="submitting" @input="clearStatus" /></div>
          <div><label class="form-label" for="new-password">新密码 <span class="text-red-500">*</span></label><input id="new-password" v-model="form.newPassword" class="input-standard" type="password" autocomplete="new-password" placeholder="请输入新密码" :disabled="submitting" @input="clearStatus" /></div>
          <div><label class="form-label" for="confirm-password">确认新密码 <span class="text-red-500">*</span></label><input id="confirm-password" v-model="form.confirmPassword" class="input-standard" type="password" autocomplete="new-password" placeholder="请再次输入新密码" :disabled="submitting" @input="clearStatus" /></div>
          <div class="meta-item"><p class="meta-label">安全提示</p><p class="meta-value">密码更新已接入真实安全接口，页面层只保留必要的输入校验、状态反馈与说明信息。</p></div>
          <section v-if="submitStatus === 'success'" class="notice-banner notice-banner-success"><span class="notice-dot bg-emerald-500"></span><span>{{ submitMessage }}</span></section>
          <section v-else-if="submitStatus === 'error'" class="notice-banner notice-banner-warning"><span class="notice-dot bg-orange-500"></span><span>{{ submitMessage }}</span></section>
          <div class="flex flex-wrap items-center gap-3 pt-2"><button class="btn-primary" type="submit" :disabled="!canSubmit">{{ submitting ? '提交中...' : '提交修改' }}</button><p v-if="!hasRequiredFields" class="text-[12px] text-gray-500">提交前需要完整填写三个密码字段。</p><p v-else-if="!passwordsMatch" class="text-[12px] text-orange-600">两次输入的新密码必须保持一致。</p></div>
        </form>
      </section>
    </div>
  </div>
</template>
