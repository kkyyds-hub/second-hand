<script setup lang="ts">
import { computed } from 'vue'
import {
  getUserDisplayName,
  getUserPrimaryContact,
  hasUserProfileSnapshot,
  isSellerUser,
  readCurrentUser,
} from '@/utils/request'

/**
 * Day01 的账户中心只读取登录成功后落在本地的 session 快照。
 * 这里刻意不引入新的 profile 查询接口，避免把 Day02 的资料刷新能力提前做进来。
 */
const currentUser = readCurrentUser()

const hasSessionProfile = computed(() => hasUserProfileSnapshot(currentUser))
const displayName = computed(() => getUserDisplayName(currentUser))
const primaryContact = computed(() => getUserPrimaryContact(currentUser))
const roleTags = computed(() => [isSellerUser(currentUser) ? '卖家身份' : '普通用户', currentUser?.status || '状态待确认'])
const avatarInitial = computed(() => displayName.value.slice(0, 1).toUpperCase())

const identityRows = computed(() => {
  return [
    { label: '用户 ID', value: currentUser?.id ?? '-' },
    { label: '登录名', value: currentUser?.loginName || '-' },
    { label: '昵称', value: currentUser?.nickname || '-' },
    { label: '注册时间', value: currentUser?.registerTime || '-' },
  ]
})

const contactRows = computed(() => {
  return [
    { label: '手机号', value: currentUser?.mobile || '-' },
    { label: '邮箱地址', value: currentUser?.email || '-' },
    { label: '地区', value: currentUser?.region || '-' },
    { label: '最后登录 IP', value: currentUser?.lastLoginIp || '-' },
  ]
})

const accountRows = computed(() => {
  return [
    { label: '信用分', value: currentUser?.creditScore ?? '-' },
    { label: '商品数', value: currentUser?.productCount ?? '-' },
    { label: '账户状态', value: currentUser?.status || '-' },
    { label: '个人简介', value: currentUser?.bio || '暂无简介' },
  ]
})
</script>

<template>
  <div class="space-y-6">
    <section class="card p-6 md:p-8">
      <p class="muted-kicker">Account center</p>
      <h1 class="section-title mt-3">账户中心</h1>
      <p class="section-desc">
        当前页面展示的是本次登录会话保存下来的基础资料快照，便于先确认账户身份、联系方式和平台状态。
      </p>
    </section>

    <section v-if="!hasSessionProfile" class="rounded-2xl border border-orange-200 bg-orange-50 px-5 py-4 text-sm text-orange-700">
      当前没有可展示的本地账户快照，请先登录后再查看账户中心。
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
          <p class="font-medium text-slate-900">Day01 页面边界</p>
          <p class="mt-2 leading-6">
            这里先冻结“查看当前登录会话快照”的能力；资料编辑、密码安全、地址管理等更完整的账户功能将在后续阶段补齐。
          </p>
        </div>
      </div>

      <div class="card p-6">
        <p class="muted-kicker">Session snapshot</p>
        <h2 class="section-title mt-2">当前会话摘要</h2>
        <div class="mt-6 space-y-4 text-sm text-slate-600">
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">登录身份</p>
            <p class="mt-2 font-medium text-slate-900">{{ isSellerUser(currentUser) ? '卖家 / 普通用户' : '普通用户' }}</p>
          </div>
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">资料来源</p>
            <p class="mt-2 font-medium text-slate-900">登录成功后写入的本地 session 快照</p>
          </div>
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">当前限制</p>
            <p class="mt-2 font-medium text-slate-900">Day01 暂不调用独立 profile 刷新或编辑接口</p>
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
