<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { updateMyProfile } from '@/api/profile'
import {
  getUserDisplayName,
  getUserPrimaryContact,
  hasUserProfileSnapshot,
  isSellerUser,
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
const primaryContact = computed(() => getUserPrimaryContact(currentUser.value))
const roleTags = computed(() => [isSellerUser(currentUser.value) ? '卖家身份' : '普通用户', currentUser.value?.status || '状态待确认'])
const avatarInitial = computed(() => displayName.value.slice(0, 1).toUpperCase() || 'U')
const sessionRoleText = computed(() => (isSellerUser(currentUser.value) ? '卖家 / 普通用户' : '普通用户'))

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

const identityRows = computed(() => {
  return [
    { label: '用户 ID', value: currentUser.value?.id ?? '-' },
    { label: '登录名', value: currentUser.value?.loginName || '-' },
    { label: '昵称', value: currentUser.value?.nickname || '-' },
    { label: '注册时间', value: currentUser.value?.registerTime || '-' },
  ]
})

const contactRows = computed(() => {
  return [
    { label: '手机号', value: currentUser.value?.mobile || '-' },
    { label: '邮箱地址', value: currentUser.value?.email || '-' },
    { label: '地区', value: currentUser.value?.region || '-' },
    { label: '最后登录 IP', value: currentUser.value?.lastLoginIp || '-' },
  ]
})

const accountRows = computed(() => {
  return [
    { label: '信用分', value: currentUser.value?.creditScore ?? '-' },
    { label: '商品数', value: currentUser.value?.productCount ?? '-' },
    { label: '账户状态', value: currentUser.value?.status || '-' },
    { label: '个人简介', value: currentUser.value?.bio || '暂无简介' },
  ]
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
    profileSaveMessage.value = '昵称不能为空。'
    return
  }

  if (!hasProfileChanges.value) {
    profileSaveStatus.value = 'error'
    profileSaveMessage.value = '暂无需要保存的资料变更。'
    return
  }

  try {
    savingProfile.value = true
    profileSaveStatus.value = 'idle'
    profileSaveMessage.value = ''

    /**
     * 页面层只维护提交态；字段兼容与返回体合并统一收敛在 `src/api/profile.ts`。
     */
    const nextUser = await updateMyProfile(
      {
        nickname: profileForm.nickname,
        bio: profileForm.bio,
      },
      currentUser.value,
    )

    /**
     * Day02 第一刀约定资料编辑成功后立即回写本地 session。
     * 这样账户中心和壳层都能继续复用既有 `readCurrentUser()` 读取路径。
     */
    saveCurrentUser(nextUser)
    currentUser.value = nextUser
    syncFormWithCurrentUser()

    profileSaveStatus.value = 'success'
    profileSaveMessage.value = '资料已保存。'
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
      <p class="muted-kicker">Account center</p>
      <h1 class="section-title mt-3">账户中心</h1>
      <p class="section-desc">
        当前页面展示本地会话快照，并支持最小资料编辑（昵称 / 简介）以便快速完成账户信息补全。
      </p>
    </section>

    <section v-if="!hasSessionProfile" class="rounded-2xl border border-orange-200 bg-orange-50 px-5 py-4 text-sm text-orange-700">
      当前没有可展示的本地账户快照，请先登录后再查看账户中心。
    </section>

    <section class="card p-6">
      <p class="muted-kicker">Profile edit</p>
      <h2 class="section-title mt-2">资料编辑</h2>
      <p class="section-desc mt-1">可修改昵称和个人简介，保存后会同步更新当前会话快照。</p>

      <form class="mt-6 space-y-5" @submit.prevent="saveProfile">
        <div>
          <label class="form-label" for="profile-nickname">昵称</label>
          <input
            id="profile-nickname"
            v-model="profileForm.nickname"
            class="input-standard"
            type="text"
            maxlength="30"
            placeholder="请输入昵称"
            :disabled="savingProfile"
            @input="clearProfileStatus"
          />
          <p class="form-helper">昵称不能为空，最多 30 个字符。</p>
        </div>

        <div>
          <label class="form-label" for="profile-bio">简介</label>
          <textarea
            id="profile-bio"
            v-model="profileForm.bio"
            class="input-standard min-h-[120px] resize-y"
            rows="4"
            maxlength="200"
            placeholder="介绍一下自己（可留空）"
            :disabled="savingProfile"
            @input="clearProfileStatus"
          />
          <p class="form-helper">可留空，最多 200 个字符。</p>
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
            {{ savingProfile ? '保存中...' : '保存资料' }}
          </button>
          <p v-if="!normalizedNickname" class="text-xs text-orange-600">昵称不能为空。</p>
          <p v-else-if="!hasProfileChanges" class="text-xs text-slate-500">暂无需要保存的资料变更。</p>
        </div>
      </form>
    </section>

    <section class="card p-6">
      <p class="muted-kicker">Address management</p>
      <h2 class="section-title mt-2">收货地址</h2>
      <p class="section-desc mt-1">可进入地址列表查看当前记录并新增地址；编辑、删除能力将在后续页面开放。</p>
      <div class="mt-5">
        <router-link class="btn-default" to="/account/addresses">进入地址列表</router-link>
      </div>
    </section>

    <section class="grid gap-6 lg:grid-cols-[1.2fr_1fr]">
      <div class="card p-6">
        <div class="flex flex-col gap-4 sm:flex-row sm:items-center">
          <div class="flex h-16 w-16 items-center justify-center rounded-3xl bg-slate-900 text-2xl font-semibold text-white">
            {{ avatarInitial }}
          </div>
          <div class="flex-1">
            <p class="text-lg font-semibold text-slate-900">{{ displayName }}</p>
            <p class="mt-1 text-sm text-slate-500">{{ primaryContact }}</p>
            <div class="mt-3 flex flex-wrap gap-2">
              <span
                v-for="tag in roleTags"
                :key="tag"
                class="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-medium text-slate-600"
              >
                {{ tag }}
              </span>
            </div>
          </div>
        </div>

        <div class="mt-6 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4 text-sm text-slate-600">
          <p class="font-medium text-slate-900">账户信息说明</p>
          <p class="mt-2 leading-6">
            当前会话摘要仍来自本地 session 快照；目前已支持昵称/简介编辑，其他账户能力会按模块逐步补齐。
          </p>
        </div>
      </div>

      <div class="card p-6">
        <p class="muted-kicker">Session snapshot</p>
        <h2 class="section-title mt-2">当前会话摘要</h2>
        <div class="mt-6 space-y-4 text-sm text-slate-600">
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">登录身份</p>
            <p class="mt-2 font-medium text-slate-900">{{ sessionRoleText }}</p>
          </div>
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">资料来源</p>
            <p class="mt-2 font-medium text-slate-900">登录成功后写入的本地 session 快照</p>
          </div>
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">当前限制</p>
            <p class="mt-2 font-medium text-slate-900">当前只开放昵称与简介编辑，其他账户能力会继续补齐</p>
          </div>
        </div>
      </div>
    </section>

    <section class="grid gap-6 xl:grid-cols-3">
      <section class="card p-6">
        <p class="muted-kicker">Identity</p>
        <h2 class="section-title mt-2">基础身份</h2>
        <div class="mt-6 grid gap-4">
          <div
            v-for="row in identityRows"
            :key="row.label"
            class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4"
          >
            <p class="text-xs text-slate-400">{{ row.label }}</p>
            <p class="mt-2 text-base font-medium text-slate-900">{{ row.value }}</p>
          </div>
        </div>
      </section>

      <section class="card p-6">
        <p class="muted-kicker">Contact</p>
        <h2 class="section-title mt-2">联系方式</h2>
        <div class="mt-6 grid gap-4">
          <div
            v-for="row in contactRows"
            :key="row.label"
            class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4"
          >
            <p class="text-xs text-slate-400">{{ row.label }}</p>
            <p class="mt-2 text-base font-medium text-slate-900">{{ row.value }}</p>
          </div>
        </div>
      </section>

      <section class="card p-6">
        <p class="muted-kicker">Status</p>
        <h2 class="section-title mt-2">平台状态</h2>
        <div class="mt-6 grid gap-4">
          <div
            v-for="row in accountRows"
            :key="row.label"
            class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4"
          >
            <p class="text-xs text-slate-400">{{ row.label }}</p>
            <p class="mt-2 text-base font-medium text-slate-900">{{ row.value }}</p>
          </div>
        </div>
      </section>
    </section>
  </div>
</template>
