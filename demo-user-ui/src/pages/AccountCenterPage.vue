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
const roleTags = computed(() => [isSellerUser(currentUser.value) ? '卖家' : '普通用户', formatAccountStatus(currentUser.value?.status)])
const avatarInitial = computed(() => displayName.value.slice(0, 1).toUpperCase() || '客')
const avatarFailed = ref(false)
const validUserId = computed(() => typeof currentUser.value?.id === 'number' && currentUser.value.id > 0)

const profileRows = computed(() => {
  return [
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
  ]
})

const accountRows = computed(() => {
  return [
    { label: '信用分', value: currentUser.value?.creditScore ?? '-' },
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
          <p class="page-desc">管理个人资料、账户联系方式和常用购物服务。</p>
        </div>
        <div class="page-actions">
          <span v-for="tag in roleTags" :key="tag" class="chip chip-neutral">{{ tag }}</span>
        </div>
      </div>
    </section>

    <section v-if="!hasSessionProfile" class="notice-banner notice-banner-warning">
      <span class="notice-dot bg-orange-500"></span>
      <span>账户信息暂时无法读取，请重新登录后再试。</span>
    </section>

      <section class="section-panel">
        <div class="section-body">
          <div class="flex flex-col gap-5 sm:flex-row sm:items-center">
            <img
              v-if="currentUser?.avatar && !avatarFailed"
              :src="currentUser.avatar"
              :alt="`${displayName}的头像`"
              class="h-[72px] w-[72px] rounded-full border border-gray-200/80 object-cover shadow-sm"
              @error="avatarFailed = true"
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

        </div>
    </section>

    <section class="section-panel">
      <div class="section-header">
        <div>
          <h2 class="section-heading">常用功能</h2>
          <p class="section-subtitle">个人服务和账户设置都在这里快速进入。</p>
        </div>
      </div>
      <div class="section-body">
        <div class="link-grid">
          <div class="link-card">
            <div class="flex-1">
              <h3 class="link-card-title">资料编辑</h3>
              <p class="link-card-desc">完善昵称和个人简介。</p>
            </div>
            <router-link class="btn-default mt-4 w-full" to="/account/profile">进入页面</router-link>
          </div>
          <div class="link-card">
            <div class="flex-1">
              <h3 class="link-card-title">头像上传</h3>
              <p class="link-card-desc">更新你的个人头像。</p>
            </div>
            <router-link class="btn-default mt-4 w-full" to="/account/avatar">进入页面</router-link>
          </div>
          <div class="link-card">
            <div class="flex-1">
              <h3 class="link-card-title">密码设置</h3>
              <p class="link-card-desc">修改登录密码。</p>
            </div>
            <router-link class="btn-default mt-4 w-full" to="/account/security/password">进入页面</router-link>
          </div>
          <div class="link-card">
            <div class="flex-1">
              <h3 class="link-card-title">邮箱绑定</h3>
              <p class="link-card-desc">绑定或更换邮箱。</p>
            </div>
            <router-link class="btn-default mt-4 w-full" to="/account/security/email">进入页面</router-link>
          </div>
          <div class="link-card">
            <div class="flex-1">
              <h3 class="link-card-title">手机绑定</h3>
              <p class="link-card-desc">绑定或更换手机号。</p>
            </div>
            <router-link class="btn-default mt-4 w-full" to="/account/security/phone">进入页面</router-link>
          </div>
          <div class="link-card">
            <div class="flex-1">
              <h3 class="link-card-title">收货地址</h3>
              <p class="link-card-desc">管理常用收货地址。</p>
            </div>
            <router-link class="btn-default mt-4 w-full" to="/account/addresses">进入页面</router-link>
          </div>
          <div class="link-card">
            <div class="flex-1">
              <h3 class="link-card-title">资产中心</h3>
              <p class="link-card-desc">查看钱包、积分和信用。</p>
            </div>
            <router-link class="btn-default mt-4 w-full" to="/assets/wallet">进入页面</router-link>
          </div>
          <div class="link-card"><div class="flex-1"><h3 class="link-card-title">我的买家订单</h3><p class="link-card-desc">查看购买订单。</p></div><router-link class="btn-default mt-4 w-full" to="/orders/buyer">查看订单</router-link></div>
          <div class="link-card"><div class="flex-1"><h3 class="link-card-title">我的收藏</h3><p class="link-card-desc">回顾收藏的商品。</p></div><router-link class="btn-default mt-4 w-full" to="/favorites">查看收藏</router-link></div>
          <div class="link-card"><div class="flex-1"><h3 class="link-card-title">购物车</h3><p class="link-card-desc">管理待购买商品。</p></div><router-link class="btn-default mt-4 w-full" to="/cart">打开购物车</router-link></div>
          <div class="link-card"><div class="flex-1"><h3 class="link-card-title">我的评价</h3><p class="link-card-desc">回顾已提交的评价。</p></div><router-link class="btn-default mt-4 w-full" to="/reviews/mine">查看评价</router-link></div>
          <template v-if="isSellerUser(currentUser)">
            <div class="link-card"><div class="flex-1"><h3 class="link-card-title">卖家工作台</h3></div><router-link class="btn-default mt-4 w-full" to="/seller">进入工作台</router-link></div>
            <div class="link-card"><div class="flex-1"><h3 class="link-card-title">我的商品</h3></div><router-link class="btn-default mt-4 w-full" to="/seller/products">管理商品</router-link></div>
            <div class="link-card"><div class="flex-1"><h3 class="link-card-title">我的卖家订单</h3></div><router-link class="btn-default mt-4 w-full" to="/orders/seller">查看订单</router-link></div>
            <div v-if="validUserId" class="link-card"><div class="flex-1"><h3 class="link-card-title">我的公开小店</h3></div><router-link class="btn-default mt-4 w-full" :to="`/shop/${currentUser!.id}`">查看小店</router-link></div>
          </template>
        </div>
      </div>
    </section>

    <section class="grid gap-6 lg:grid-cols-3">
      <section class="section-panel">
        <div class="section-header section-header-plain">
          <div>
            <h2 class="section-heading">基础资料</h2>
          </div>
        </div>
        <div class="section-body pt-0">
          <div class="detail-grid">
            <div v-for="row in profileRows" :key="row.label" class="detail-row">
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
