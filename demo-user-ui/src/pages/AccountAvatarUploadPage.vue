<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { uploadMyAvatar } from '@/api/profile'
import {
  getUserDisplayName,
  hasUserProfileSnapshot,
  readCurrentUser,
  saveCurrentUser,
  type UserProfile,
} from '@/utils/request'

const currentUser = ref<UserProfile | null>(readCurrentUser())
const selectedFile = ref<File | null>(null)
const selectedPreviewUrl = ref('')
const uploadingAvatar = ref(false)
const uploadStatus = ref<'idle' | 'success' | 'error'>('idle')
const uploadMessage = ref('')
const fileInputRef = ref<HTMLInputElement | null>(null)

function revokeSelectedPreviewUrl() {
  if (!selectedPreviewUrl.value) {
    return
  }

  URL.revokeObjectURL(selectedPreviewUrl.value)
  selectedPreviewUrl.value = ''
}

function clearUploadStatus() {
  if (uploadingAvatar.value || uploadStatus.value === 'idle') {
    return
  }

  uploadStatus.value = 'idle'
  uploadMessage.value = ''
}

function clearSelectedFile() {
  selectedFile.value = null
  revokeSelectedPreviewUrl()

  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

function readErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return '头像上传失败，请稍后重试。'
}

function isSupportedAvatarFile(file: File) {
  return ['image/jpeg', 'image/png'].includes(file.type)
}

function formatFileSize(file: File | null) {
  if (!file) {
    return '-'
  }

  if (file.size < 1024) {
    return `${file.size} B`
  }

  const sizeInKb = file.size / 1024
  if (sizeInKb < 1024) {
    return `${sizeInKb.toFixed(1)} KB`
  }

  return `${(sizeInKb / 1024).toFixed(2)} MB`
}

function updateSelectedFile(file: File | null) {
  clearUploadStatus()
  revokeSelectedPreviewUrl()
  selectedFile.value = file

  if (!file) {
    return
  }

  selectedPreviewUrl.value = URL.createObjectURL(file)
}

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement | null
  const file = input?.files?.[0] || null

  if (!file) {
    updateSelectedFile(null)
    return
  }

  if (!isSupportedAvatarFile(file)) {
    updateSelectedFile(null)
    uploadStatus.value = 'error'
    uploadMessage.value = '仅支持 JPG / PNG 图片格式。'
    return
  }

  updateSelectedFile(file)
}

const hasSessionProfile = computed(() => hasUserProfileSnapshot(currentUser.value))
const displayName = computed(() => getUserDisplayName(currentUser.value))
const avatarInitial = computed(() => displayName.value.slice(0, 1).toUpperCase() || '客')
const previewAvatarUrl = computed(() => selectedPreviewUrl.value || currentUser.value?.avatar || '')
const canSubmitAvatar = computed(() => {
  return !uploadingAvatar.value && Boolean(selectedFile.value) && hasSessionProfile.value
})

const submitAvatar = async () => {
  if (uploadingAvatar.value || !selectedFile.value) {
    return
  }

  if (!hasSessionProfile.value) {
    uploadStatus.value = 'error'
    uploadMessage.value = '当前没有可用的本地账户快照，暂时无法上传头像。'
    return
  }

  try {
    uploadingAvatar.value = true
    uploadStatus.value = 'idle'
    uploadMessage.value = ''

    const nextUser = await uploadMyAvatar(selectedFile.value, currentUser.value)
    saveCurrentUser(nextUser)
    currentUser.value = nextUser
    clearSelectedFile()

    uploadStatus.value = 'success'
    uploadMessage.value = '头像更新成功。'
  } catch (error: unknown) {
    uploadStatus.value = 'error'
    uploadMessage.value = readErrorMessage(error)
  } finally {
    uploadingAvatar.value = false
  }
}

onBeforeUnmount(() => {
  revokeSelectedPreviewUrl()
})
</script>

<template>
  <div class="page-body page-body-narrow">
    <section class="page-header">
      <div class="page-header-main"><p class="page-kicker">头像</p><h1 class="page-title">头像上传</h1><p class="page-desc">预览区与上传区统一为左右双栏，卡片、按钮、输入框和说明块保持相同语法。</p></div>
      <div class="page-actions"><span class="chip chip-neutral">头像设置</span><router-link class="btn-default" to="/account">返回账户中心</router-link><router-link class="btn-default" to="/account/profile">资料编辑</router-link></div>
    </section>
    <section v-if="!hasSessionProfile" class="notice-banner notice-banner-warning"><span class="notice-dot bg-orange-500"></span><span>当前没有可用的本地账户快照，请重新登录后再上传头像。</span></section>
    <section class="grid gap-6 lg:grid-cols-[0.95fr_1.05fr]">
      <div class="section-panel-muted">
        <div class="section-header section-header-plain"><div><h2 class="section-heading">头像预览</h2><p class="section-subtitle">预览区统一承载当前生效头像和本地文件预览，不额外引入花哨装饰。</p></div></div>
        <div class="section-body pt-0 space-y-4">
          <div class="flex flex-col items-center rounded-2xl border border-gray-200/70 bg-white px-6 py-8 text-center">
            <img v-if="previewAvatarUrl" :src="previewAvatarUrl" :alt="`${displayName} avatar preview`" class="h-28 w-28 rounded-full border border-gray-200 object-cover shadow-sm bg-white" />
            <div v-else class="flex h-28 w-28 items-center justify-center rounded-full border border-gray-200/80 bg-gray-100 text-4xl font-bold text-gray-600 shadow-sm">{{ avatarInitial }}</div>
            <p class="mt-5 text-[18px] font-semibold text-gray-900">{{ displayName }}</p>
            <p class="mt-2 text-[13px] text-gray-500">{{ selectedFile ? '当前展示本地预览文件' : '当前展示已生效头像' }}</p>
          </div>
          <div class="detail-grid"><div class="detail-row"><span class="detail-label">当前选择文件</span><span class="detail-value truncate">{{ selectedFile?.name || '未选择文件' }}</span></div><div class="detail-row"><span class="detail-label">文件大小</span><span class="detail-value font-numeric">{{ formatFileSize(selectedFile) }}</span></div></div>
        </div>
      </div>
      <div class="section-panel">
        <div class="section-header section-header-plain"><div><h2 class="section-heading">上传表单</h2><p class="section-subtitle">文件选择、说明、状态反馈和操作按钮统一使用相同的间距与边框体系。</p></div></div>
        <form class="section-body pt-0 space-y-5" @submit.prevent="submitAvatar">
          <div><label class="form-label" for="avatar-file">选择头像图片</label><input id="avatar-file" ref="fileInputRef" class="input-standard bg-gray-50 file:mr-4 file:rounded-xl file:border-0 file:bg-white file:px-4 file:py-2 file:text-[13px] file:font-medium file:text-gray-700 file:shadow-sm hover:file:bg-gray-50" type="file" accept=".jpg,.jpeg,.png,image/jpeg,image/png" :disabled="uploadingAvatar" @change="handleFileChange" /><p class="form-helper">仅支持 JPG / PNG 格式，上传与资料回写逻辑仍由底层 API 模块处理。</p></div>
          <div class="meta-item"><p class="meta-label">页面边界</p><p class="meta-value">本页只负责文件选择、预览和提交，让上传链路与资料编辑页保持边界清晰。</p></div>
          <section v-if="uploadStatus === 'success'" class="notice-banner notice-banner-success"><span class="notice-dot bg-emerald-500"></span><span>{{ uploadMessage }}</span></section>
          <section v-else-if="uploadStatus === 'error'" class="notice-banner notice-banner-warning"><span class="notice-dot bg-orange-500"></span><span>{{ uploadMessage }}</span></section>
          <div class="flex flex-wrap items-center gap-3 pt-2"><button class="btn-primary" type="submit" :disabled="!canSubmitAvatar">{{ uploadingAvatar ? '上传中...' : '开始上传' }}</button><button class="btn-default" type="button" :disabled="uploadingAvatar" @click="clearSelectedFile">清除选择</button><p v-if="!selectedFile" class="text-[12px] text-gray-500">请先在上方选择需要上传的图片。</p></div>
        </form>
      </div>
    </section>
  </div>
</template>
