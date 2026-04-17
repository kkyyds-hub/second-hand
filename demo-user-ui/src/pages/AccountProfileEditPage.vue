<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { updateMyProfile } from '@/api/profile'
import {
  getUserDisplayName,
  hasUserProfileSnapshot,
  readCurrentUser,
  saveCurrentUser,
  type UserProfile,
} from '@/utils/request'

const currentUser = ref<UserProfile | null>(readCurrentUser())
const savingProfile = ref(false)
const profileSaveStatus = ref<'idle' | 'success' | 'error'>('idle')
const profileSaveMessage = ref('')

function readEditableNickname(user: UserProfile | null) {
  return (user?.nickname || user?.loginName || '').trim()
}

function readEditableBio(user: UserProfile | null) {
  return user?.bio || ''
}

function readErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return 'Profile save failed. Please try again.'
}

const profileForm = reactive({
  nickname: readEditableNickname(currentUser.value),
  bio: readEditableBio(currentUser.value),
})

const hasSessionProfile = computed(() => hasUserProfileSnapshot(currentUser.value))
const displayName = computed(() => getUserDisplayName(currentUser.value))
const normalizedNickname = computed(() => profileForm.nickname.trim())
const normalizedBio = computed(() => profileForm.bio.trim())
const hasProfileChanges = computed(() => {
  return (
    normalizedNickname.value !== readEditableNickname(currentUser.value) ||
    normalizedBio.value !== readEditableBio(currentUser.value).trim()
  )
})
const canSubmitProfile = computed(() => {
  return !savingProfile.value && Boolean(normalizedNickname.value) && hasProfileChanges.value
})

const clearProfileStatus = () => {
  if (savingProfile.value || profileSaveStatus.value === 'idle') {
    return
  }

  profileSaveStatus.value = 'idle'
  profileSaveMessage.value = ''
}

const syncFormWithCurrentUser = () => {
  profileForm.nickname = readEditableNickname(currentUser.value)
  profileForm.bio = readEditableBio(currentUser.value)
}

const saveProfile = async () => {
  if (savingProfile.value) {
    return
  }

  if (!normalizedNickname.value) {
    profileSaveStatus.value = 'error'
    profileSaveMessage.value = 'Nickname is required.'
    return
  }

  if (!hasProfileChanges.value) {
    profileSaveStatus.value = 'error'
    profileSaveMessage.value = 'No profile changes to save.'
    return
  }

  try {
    savingProfile.value = true
    profileSaveStatus.value = 'idle'
    profileSaveMessage.value = ''

    const nextUser = await updateMyProfile(
      {
        nickname: profileForm.nickname,
        bio: profileForm.bio,
      },
      currentUser.value,
    )

    saveCurrentUser(nextUser)
    currentUser.value = nextUser
    syncFormWithCurrentUser()

    profileSaveStatus.value = 'success'
    profileSaveMessage.value = 'Profile saved.'
  } catch (error: unknown) {
    profileSaveStatus.value = 'error'
    profileSaveMessage.value = readErrorMessage(error)
  } finally {
    savingProfile.value = false
  }
}
</script>

<template>
  <div class="space-y-6">
    <section class="card p-6 md:p-8">
      <p class="muted-kicker">Profile edit</p>
      <div class="mt-3 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 class="section-title">Edit Profile</h1>
          <p class="section-desc mt-2">
            This page keeps the existing nickname and bio edit flow, while moving it out of the account overview page.
          </p>
        </div>
        <router-link class="btn-default" to="/account">Back to account center</router-link>
      </div>
    </section>

    <section v-if="!hasSessionProfile" class="rounded-2xl border border-orange-200 bg-orange-50 px-5 py-4 text-sm text-orange-700">
      No local account snapshot is available. Please sign in again before editing your profile.
    </section>

    <section class="grid gap-6 lg:grid-cols-[1.15fr_0.85fr]">
      <section class="card p-6">
        <form class="space-y-5" @submit.prevent="saveProfile">
          <div>
            <label class="form-label" for="profile-nickname">Nickname</label>
            <input
              id="profile-nickname"
              v-model="profileForm.nickname"
              class="input-standard"
              type="text"
              maxlength="20"
              placeholder="Enter a nickname"
              :disabled="savingProfile"
              @input="clearProfileStatus"
            />
            <p class="form-helper">Required, up to 20 characters.</p>
          </div>

          <div>
            <label class="form-label" for="profile-bio">Bio</label>
            <textarea
              id="profile-bio"
              v-model="profileForm.bio"
              class="input-standard min-h-[120px] resize-y"
              rows="4"
              maxlength="150"
              placeholder="Tell people a little about yourself"
              :disabled="savingProfile"
              @input="clearProfileStatus"
            />
            <p class="form-helper">Optional, up to 150 characters.</p>
          </div>

          <section
            v-if="profileSaveStatus === 'success'"
            class="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700"
          >
            {{ profileSaveMessage }}
          </section>
          <section
            v-else-if="profileSaveStatus === 'error'"
            class="rounded-2xl border border-orange-200 bg-orange-50 px-4 py-3 text-sm text-orange-700"
          >
            {{ profileSaveMessage }}
          </section>

          <div class="flex flex-wrap items-center gap-3">
            <button class="btn-primary" type="submit" :disabled="!canSubmitProfile">
              {{ savingProfile ? 'Saving...' : 'Save profile' }}
            </button>
            <p v-if="!normalizedNickname" class="text-xs text-orange-600">Nickname is required.</p>
            <p v-else-if="!hasProfileChanges" class="text-xs text-slate-500">No profile changes yet.</p>
          </div>
        </form>
      </section>

      <section class="card p-6">
        <p class="muted-kicker">Current snapshot</p>
        <h2 class="section-title mt-2">Current profile summary</h2>
        <div class="mt-6 space-y-4 text-sm text-slate-600">
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">Display name</p>
            <p class="mt-2 text-base font-medium text-slate-900">{{ displayName }}</p>
          </div>
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">Current bio</p>
            <p class="mt-2 text-base font-medium text-slate-900">{{ currentUser?.bio || 'No bio yet' }}</p>
          </div>
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">Avatar maintenance</p>
            <p class="mt-2 text-base font-medium text-slate-900">Avatar upload now lives on its own page.</p>
            <router-link class="btn-default mt-4 inline-flex" to="/account/avatar">Open avatar upload</router-link>
          </div>
        </div>
      </section>
    </section>
  </div>
</template>
