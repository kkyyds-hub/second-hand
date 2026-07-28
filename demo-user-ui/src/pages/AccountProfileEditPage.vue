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

  return '资料保存失败，请稍后重试。'
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
    profileSaveMessage.value = '用户昵称为必填项。'
    return
  }

  if (!hasProfileChanges.value) {
    profileSaveStatus.value = 'error'
    profileSaveMessage.value = '当前没有新的资料变更。'
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
    profileSaveMessage.value = '资料保存成功。'
  } catch (error: unknown) {
    profileSaveStatus.value = 'error'
    profileSaveMessage.value = readErrorMessage(error)
  } finally {
    savingProfile.value = false
  }
}
</script>

<template>
  <div class="page-body page-body-narrow">
    <section class="page-header">
      <div class="page-header-main"><p class="page-kicker">资料</p><h1 class="page-title">编辑个人资料</h1><p class="page-desc">完善昵称和个人简介，让其他用户更容易了解你。</p></div>
      <div class="page-actions"><span class="chip chip-neutral">资料设置</span><router-link class="btn-default" to="/account">返回账户中心</router-link></div>
    </section>

    <section v-if="!hasSessionProfile" class="notice-banner notice-banner-warning"><span class="notice-dot bg-orange-500"></span><span>账户信息暂时无法读取，请重新登录后再试。</span></section>

    <section class="grid gap-6 lg:grid-cols-[1.1fr_0.85fr]">
      <div class="section-panel">
        <div class="section-header section-header-plain"><div><h2 class="section-heading">基础资料</h2><p class="section-subtitle">修改后会同步更新你的个人资料。</p></div></div>
        <form class="section-body pt-0 space-y-5" @submit.prevent="saveProfile">
          <div><label class="form-label" for="profile-nickname">用户昵称</label><input id="profile-nickname" v-model="profileForm.nickname" class="input-standard" type="text" maxlength="20" placeholder="请输入昵称" :disabled="savingProfile" @input="clearProfileStatus" /><p class="form-helper">必填，最多 20 个字符。{{ profileForm.nickname.length }} / 20</p></div>
          <div><label class="form-label" for="profile-bio">个人简介（可选）</label><textarea id="profile-bio" v-model="profileForm.bio" class="input-standard min-h-[120px] resize-y" rows="4" maxlength="150" placeholder="向大家简单介绍一下自己..." :disabled="savingProfile" @input="clearProfileStatus" /><p class="form-helper">最多 150 个字符。{{ profileForm.bio.length }} / 150</p></div>
          <section v-if="profileSaveStatus === 'success'" class="notice-banner notice-banner-success"><span class="notice-dot bg-emerald-500"></span><span>{{ profileSaveMessage }}</span></section>
          <section v-else-if="profileSaveStatus === 'error'" class="notice-banner notice-banner-warning"><span class="notice-dot bg-orange-500"></span><span>{{ profileSaveMessage }}</span></section>
          <div class="flex flex-wrap items-center gap-3 pt-2"><button class="btn-primary" type="submit" :disabled="!canSubmitProfile">{{ savingProfile ? '保存中...' : '保存资料' }}</button><p v-if="!normalizedNickname" class="text-[12px] text-orange-600">用户昵称不能为空。</p><p v-else-if="!hasProfileChanges" class="text-[12px] text-gray-500">当前资料还没有发生变更。</p></div>
        </form>
      </div>
      <div class="section-panel-muted">
        <div class="section-header section-header-plain"><div><h2 class="section-heading">当前资料</h2></div></div>
        <div class="section-body pt-0"><div class="detail-grid"><div class="detail-row !items-start !justify-start !gap-1.5"><span class="detail-label">当前显示名称</span><span class="detail-value">{{ displayName }}</span></div><div class="detail-row !items-start !justify-start !gap-1.5"><span class="detail-label">当前简介</span><span class="detail-value">{{ currentUser?.bio || '暂无简介' }}</span></div><div class="meta-item mt-1"><p class="meta-label">头像管理</p><router-link class="btn-default mt-4 w-full" to="/account/avatar">前往头像设置</router-link></div></div></div>
      </div>
    </section>
  </div>
</template>
