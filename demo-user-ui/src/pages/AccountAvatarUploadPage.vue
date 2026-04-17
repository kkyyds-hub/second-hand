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

  return 'Avatar upload failed. Please try again.'
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
    uploadMessage.value = 'Only JPG and PNG avatar files are supported.'
    return
  }

  updateSelectedFile(file)
}

const hasSessionProfile = computed(() => hasUserProfileSnapshot(currentUser.value))
const displayName = computed(() => getUserDisplayName(currentUser.value))
const avatarInitial = computed(() => displayName.value.slice(0, 1).toUpperCase() || 'U')
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
    uploadMessage.value = 'No local account snapshot is available for avatar upload.'
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
    uploadMessage.value = 'Avatar updated.'
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
  <div class="space-y-6">
    <section class="card p-6 md:p-8">
      <p class="muted-kicker">Avatar upload</p>
      <div class="mt-3 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 class="section-title">Upload Avatar</h1>
          <p class="section-desc mt-2">
            This page owns only the Day02 avatar slice: fetch upload config, upload the file, then let the API module write the avatar back into the profile.
          </p>
        </div>
        <div class="flex flex-wrap gap-3">
          <router-link class="btn-default" to="/account">Back to account center</router-link>
          <router-link class="btn-default" to="/account/profile">Open profile edit</router-link>
        </div>
      </div>
    </section>

    <section v-if="!hasSessionProfile" class="rounded-2xl border border-orange-200 bg-orange-50 px-5 py-4 text-sm text-orange-700">
      No local account snapshot is available. Please sign in again before uploading an avatar.
    </section>

    <section class="grid gap-6 lg:grid-cols-[0.95fr_1.05fr]">
      <section class="card p-6">
        <p class="muted-kicker">Preview</p>
        <h2 class="section-title mt-2">Avatar preview</h2>

        <div class="mt-6 flex flex-col items-center rounded-[28px] border border-slate-200 bg-slate-50 px-6 py-8 text-center">
          <img
            v-if="previewAvatarUrl"
            :src="previewAvatarUrl"
            :alt="`${displayName} avatar preview`"
            class="h-28 w-28 rounded-full border border-slate-200 object-cover shadow-sm"
          />
          <div
            v-else
            class="flex h-28 w-28 items-center justify-center rounded-full bg-slate-900 text-4xl font-semibold text-white"
          >
            {{ avatarInitial }}
          </div>

          <p class="mt-5 text-lg font-semibold text-slate-900">{{ displayName }}</p>
          <p class="mt-2 text-sm text-slate-500">
            {{ selectedFile ? 'Preview is showing the selected local file.' : 'Preview is showing the current session avatar.' }}
          </p>
        </div>

        <div class="mt-6 grid gap-4">
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">Selected file</p>
            <p class="mt-2 text-base font-medium text-slate-900">{{ selectedFile?.name || 'No file selected' }}</p>
          </div>
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">File size</p>
            <p class="mt-2 text-base font-medium text-slate-900">{{ formatFileSize(selectedFile) }}</p>
          </div>
        </div>
      </section>

      <section class="card p-6">
        <form class="space-y-5" @submit.prevent="submitAvatar">
          <div>
            <label class="form-label" for="avatar-file">Avatar file</label>
            <input
              id="avatar-file"
              ref="fileInputRef"
              class="input-standard"
              type="file"
              accept=".jpg,.jpeg,.png,image/jpeg,image/png"
              :disabled="uploadingAvatar"
              @change="handleFileChange"
            />
            <p class="form-helper">Only JPG and PNG are accepted. Upload payload shaping stays inside `src/api/profile.ts`.</p>
          </div>

          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4 text-sm text-slate-600">
            <p class="font-medium text-slate-900">Flow boundary</p>
            <p class="mt-2 leading-6">
              The page only handles file choice, preview, and submit state. The `upload-config -> avatar/upload -> profile write-back`
              chain stays inside the API module so upload fields do not leak into page code.
            </p>
          </div>

          <section
            v-if="uploadStatus === 'success'"
            class="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700"
          >
            {{ uploadMessage }}
          </section>
          <section
            v-else-if="uploadStatus === 'error'"
            class="rounded-2xl border border-orange-200 bg-orange-50 px-4 py-3 text-sm text-orange-700"
          >
            {{ uploadMessage }}
          </section>

          <div class="flex flex-wrap items-center gap-3">
            <button class="btn-primary" type="submit" :disabled="!canSubmitAvatar">
              {{ uploadingAvatar ? 'Uploading...' : 'Upload avatar' }}
            </button>
            <button class="btn-default" type="button" :disabled="uploadingAvatar" @click="clearSelectedFile">
              Clear selection
            </button>
            <p v-if="!selectedFile" class="text-xs text-slate-500">Choose one avatar file before submitting.</p>
          </div>
        </form>
      </section>
    </section>
  </div>
</template>
