<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  getUserDisplayName,
  getUserPrimaryContact,
  hasUserProfileSnapshot,
  isSellerUser,
  readCurrentUser,
  type UserProfile,
} from '@/utils/request'

const currentUser = ref<UserProfile | null>(readCurrentUser())

function formatAccountStatus(status: string | null | undefined) {
  const normalized = (status || '').trim().toLowerCase()

  if (!normalized) {
    return '待确认'
  }

  if (['active', 'enabled', 'normal'].includes(normalized)) {
    return '正常'
  }

  if (['pending', 'reviewing'].includes(normalized)) {
    return '待确认'
  }

  if (['disabled', 'inactive', 'blocked', 'banned'].includes(normalized)) {
    return '已停用'
  }

  return status || '待确认'
}

const hasSessionProfile = computed(() => hasUserProfileSnapshot(currentUser.value))
const displayName = computed(() => getUserDisplayName(currentUser.value))
const primaryContact = computed(() => getUserPrimaryContact(currentUser.value))
const roleTags = computed(() => [isSellerUser(currentUser.value) ? '卖家账号' : '普通账号', formatAccountStatus(currentUser.value?.status)])
const avatarInitial = computed(() => displayName.value.slice(0, 1).toUpperCase() || '客')
const sessionRoleText = computed(() => (isSellerUser(currentUser.value) ? '卖家账号' : '普通账号'))

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
    { label: '邮箱', value: currentUser.value?.email || '-' },
    { label: '所在地区', value: currentUser.value?.region || '-' },
    { label: '最近登录 IP', value: currentUser.value?.lastLoginIp || '-' },
  ]
})

const accountRows = computed(() => {
  return [
    { label: '信用分', value: currentUser.value?.creditScore ?? '-' },
    { label: '商品数量', value: currentUser.value?.productCount ?? '-' },
    { label: '账户状态', value: formatAccountStatus(currentUser.value?.status) },
    { label: '个人简介', value: currentUser.value?.bio || '暂未填写' },
  ]
})
</script>

<template>
  <div class="page-body page-body-narrow">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">账户</p>
          <h1 class="page-title">账户中心</h1>
          <p class="page-desc">统一展示账户概览、会话快照与设置入口，让总览页与其他用户端页面保持同一视觉语言。</p>
        </div>
        <div class="page-actions">
          <span v-for="tag in roleTags" :key="tag" class="chip chip-neutral">{{ tag }}</span>
        </div>
      </div>
    </section>

    <section v-if="!hasSessionProfile" class="notice-banner notice-banner-warning">
      <span class="notice-dot bg-orange-500"></span>
      <span>当前没有可用的本地账户快照，请重新登录后继续管理账户信息。</span>
    </section>

    <section class="grid gap-6 lg:grid-cols-[1.2fr_0.85fr]">
      <section class="section-panel">
        <div class="section-body">
          <div class="flex flex-col gap-5 sm:flex-row sm:items-center">
            <img
              v-if="currentUser?.avatar"
              :src="currentUser.avatar"
              :alt="`${displayName} avatar`"
              class="h-[72px] w-[72px] rounded-full border border-gray-200/80 object-cover shadow-sm"
            />
            <div
              v-else
              class="flex h-[72px] w-[72px] items-center justify-center rounded-full border border-gray-200/80 bg-gray-100 text-2xl font-bold text-gray-600 shadow-sm"
            >
              {{ avatarInitial }}
            </div>
            <div class="min-w-0 flex-1">
              <p class="text-[20px] font-semibold text-gray-900">{{ displayName }}</p>
              <p class="mt-1 text-[13px] text-gray-500">{{ primaryContact }}</p>
              <div class="mt-3 flex flex-wrap gap-2">
                <span v-for="tag in roleTags" :key="tag" class="chip chip-neutral">{{ tag }}</span>
              </div>
            </div>
          </div>

          <div class="meta-item mt-6">
            <p class="meta-label">总览说明</p>
            <p class="meta-value">总览页继续保持轻量，只展示状态与入口，不把更细的账户操作重新堆回这个页面。</p>
          </div>
        </div>
      </section>

      <section class="section-panel-muted">
        <div class="section-header section-header-plain">
          <div>
            <h2 class="section-heading">会话快照</h2>
            <p class="section-subtitle">保留对当前登录快照来源和作用范围的简洁说明。</p>
          </div>
        </div>
        <div class="section-body pt-0">
          <div class="detail-grid">
            <div class="detail-row">
              <span class="detail-label">角色</span>
              <span class="detail-value">{{ sessionRoleText }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">来源</span>
              <span class="detail-value">本地快照</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">作用范围</span>
              <span class="detail-value">当前总览与入口卡</span>
            </div>
          </div>
        </div>
      </section>
    </section>

    <section class="section-panel">
      <div class="section-header">
        <div>
          <h2 class="section-heading">设置与操作</h2>
          <p class="section-subtitle">入口卡片统一为一套中性风格，按钮尺寸与描述层级保持一致。</p>
        </div>
      </div>
      <div class="section-body">
        <div class="link-grid">
          <div class="link-card">
            <div class="flex-1">
              <h3 class="link-card-title">资料编辑</h3>
              <p class="link-card-desc">继续沿用昵称与个人简介编辑链路。</p>
            </div>
            <router-link class="btn-default mt-4 w-full" to="/account/profile">进入页面</router-link>
          </div>
          <div class="link-card">
            <div class="flex-1">
              <h3 class="link-card-title">头像上传</h3>
              <p class="link-card-desc">单独页面处理头像选择、预览与上传。</p>
            </div>
            <router-link class="btn-default mt-4 w-full" to="/account/avatar">进入页面</router-link>
          </div>
          <div class="link-card">
            <div class="flex-1">
              <h3 class="link-card-title">密码管理</h3>
              <p class="link-card-desc">保留独立的安全切片修改流程。</p>
            </div>
            <router-link class="btn-default mt-4 w-full" to="/account/security/password">进入页面</router-link>
          </div>
          <div class="link-card">
            <div class="flex-1">
              <h3 class="link-card-title">邮箱绑定</h3>
              <p class="link-card-desc">单独处理邮箱绑定与解绑，不与其他安全表单混排。</p>
            </div>
            <router-link class="btn-default mt-4 w-full" to="/account/security/email">进入页面</router-link>
          </div>
          <div class="link-card">
            <div class="flex-1">
              <h3 class="link-card-title">手机绑定</h3>
              <p class="link-card-desc">统一移动端重要联络链路的绑定与解绑。</p>
            </div>
            <router-link class="btn-default mt-4 w-full" to="/account/security/phone">进入页面</router-link>
          </div>
          <div class="link-card">
            <div class="flex-1">
              <h3 class="link-card-title">地址簿</h3>
              <p class="link-card-desc">查看地址列表、新增与编辑收货地址。</p>
            </div>
            <router-link class="btn-default mt-4 w-full" to="/account/addresses">进入页面</router-link>
          </div>
        </div>
      </div>
    </section>

    <section class="grid gap-6 lg:grid-cols-3">
      <section class="section-panel">
        <div class="section-header section-header-plain">
          <div>
            <h2 class="section-heading">身份信息</h2>
          </div>
        </div>
        <div class="section-body pt-0">
          <div class="detail-grid">
            <div v-for="row in identityRows" :key="row.label" class="detail-row">
              <span class="detail-label">{{ row.label }}</span>
              <span class="detail-value">{{ row.value }}</span>
            </div>
          </div>
        </div>
      </section>

      <section class="section-panel">
        <div class="section-header section-header-plain">
          <div>
            <h2 class="section-heading">联系方式</h2>
          </div>
        </div>
        <div class="section-body pt-0">
          <div class="detail-grid">
            <div v-for="row in contactRows" :key="row.label" class="detail-row">
              <span class="detail-label">{{ row.label }}</span>
              <span class="detail-value">{{ row.value }}</span>
            </div>
          </div>
        </div>
      </section>

      <section class="section-panel">
        <div class="section-header section-header-plain">
          <div>
            <h2 class="section-heading">账户状态</h2>
          </div>
        </div>
        <div class="section-body pt-0">
          <div class="detail-grid">
            <div v-for="row in accountRows" :key="row.label" class="detail-row !items-start !justify-start !gap-1.5">
              <span class="detail-label">{{ row.label }}</span>
              <span class="detail-value">{{ row.value }}</span>
            </div>
          </div>
        </div>
      </section>
    </section>
  </div>
</template>

