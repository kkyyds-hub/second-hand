<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Loader2, RefreshCw } from 'lucide-vue-next'
import { createEmptySellerSummary, getSellerSummary, type SellerSummary } from '@/api/seller'
import { getUserDisplayName, getUserPrimaryContact, isSellerUser, readCurrentUser } from '@/utils/request'

const currentUser = readCurrentUser()
const loading = ref(false)
const errorMessage = ref('')
const hasLoadedOnce = ref(false)
const summary = ref<SellerSummary>(createEmptySellerSummary())

const greetingName = computed(() => getUserDisplayName(currentUser))
const primaryContact = computed(() => getUserPrimaryContact(currentUser))
const sellerEnabled = computed(() => isSellerUser(currentUser))
const roleText = computed(() => (sellerEnabled.value ? '当前账号已具备卖家身份。' : '当前账号可能尚未开通卖家身份。'))

const summaryStatusText = computed(() => {
  if (loading.value) {
    return '正在同步最新卖家摘要...'
  }

  if (errorMessage.value) {
    return '卖家摘要加载失败，请重试'
  }

  if (hasLoadedOnce.value) {
    return '卖家摘要已加载'
  }

  return '初始化中...'
})

const primaryMetrics = computed(() => {
  return [
    { label: '全部商品', value: summary.value.totalProducts },
    { label: '审核中商品', value: summary.value.underReviewProducts },
    { label: '在售商品', value: summary.value.onSaleProducts },
    { label: '全部订单', value: summary.value.totalOrders },
    { label: '待处理订单', value: summary.value.pendingOrders },
    { label: '已完成订单', value: summary.value.completedOrders },
  ]
})

const secondaryMetrics = computed(() => {
  return [
    { label: '已下架商品', value: summary.value.offShelfProducts },
    { label: '已售出商品', value: summary.value.soldProducts },
    { label: '已支付订单', value: summary.value.paidOrders },
    { label: '已发货订单', value: summary.value.shippedOrders },
    { label: '已取消订单', value: summary.value.cancelledOrders },
  ]
})

const loadSummary = async () => {
  if (loading.value) {
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''
    summary.value = await getSellerSummary()
  } catch (error: unknown) {
    if (error instanceof Error && error.message.trim()) {
      errorMessage.value = error.message
    } else {
      errorMessage.value = '卖家摘要加载失败，请稍后重试。'
    }
  } finally {
    loading.value = false
    hasLoadedOnce.value = true
  }
}

onMounted(() => {
  loadSummary()
})
</script>

<template>
  <div class="page-body">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">概览</p>
          <h1 class="page-title">你好，{{ greetingName }}</h1>
          <p class="page-desc">确认会话状态与关键业务摘要，开启今天的工作。</p>
        </div>
        <div class="page-actions">
          <span class="chip chip-muted">{{ summaryStatusText }}</span>
          <button class="btn-default" type="button" :disabled="loading" @click="loadSummary">
            <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
            <RefreshCw v-else class="h-4 w-4" />
            <span>{{ loading ? '刷新中' : '刷新摘要' }}</span>
          </button>
        </div>
      </div>
    </section>

    <section v-if="errorMessage" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <div>
        <p class="font-semibold">数据加载失败</p>
        <p class="mt-1 text-[12px] leading-5">{{ errorMessage }}</p>
      </div>
    </section>

    <section class="section-panel">
      <div class="section-header">
        <div>
          <h2 class="section-heading">会话状态</h2>
          <p class="section-subtitle">统一查看当前身份、联系方式与摘要同步状态。</p>
        </div>
        <span class="chip chip-neutral">{{ summaryStatusText }}</span>
      </div>
      <div class="section-body">
        <div class="meta-grid">
          <div class="meta-item">
            <p class="meta-label">当前身份</p>
            <p class="meta-value">{{ roleText }}</p>
          </div>
          <div class="meta-item">
            <p class="meta-label">联系方式</p>
            <p class="meta-value font-numeric">{{ primaryContact }}</p>
          </div>
        </div>
      </div>
    </section>

    <section class="section-panel">
      <div class="section-header">
        <div>
          <h2 class="section-heading">卖家经营概览</h2>
          <p class="section-subtitle">核心指标使用统一卡片语法，避免页面各自定义一套统计样式。</p>
        </div>
      </div>
      <div class="section-body">
        <div class="metric-grid">
          <article v-for="metric in primaryMetrics" :key="metric.label" class="metric-card">
            <p class="metric-label">{{ metric.label }}</p>
            <p class="metric-value font-numeric">{{ metric.value }}</p>
          </article>
        </div>
      </div>
    </section>

    <section class="section-panel-muted">
      <div class="section-header section-header-plain">
        <div>
          <h2 class="section-heading">补充统计</h2>
          <p class="section-subtitle">次级指标统一使用浅底信息卡，降低视觉噪声。</p>
        </div>
      </div>
      <div class="section-body pt-0">
        <div class="submetric-grid">
          <article v-for="metric in secondaryMetrics" :key="metric.label" class="submetric-card">
            <p class="text-[12px] text-gray-500">{{ metric.label }}</p>
            <p class="mt-2 text-[18px] font-semibold text-gray-900 font-numeric">{{ metric.value }}</p>
          </article>
        </div>
      </div>
    </section>

    <section class="section-panel">
      <div class="section-header">
        <div>
          <h2 class="section-heading">快捷入口</h2>
          <p class="section-subtitle">延续同系列产品的中性卡片风格，只在细节上保留少量强调色。</p>
        </div>
      </div>
      <div class="section-body">
        <div class="link-grid">
          <router-link v-if="sellerEnabled" class="link-card" to="/seller">
            <div class="flex items-center justify-between gap-3">
              <p class="link-card-title">卖家工作台</p>
              <span class="chip chip-accent">Day04</span>
            </div>
            <p class="link-card-desc">进入卖家工作台，并继续到 userProducts 列表/详情只读链路。</p>
          </router-link>
          <router-link class="link-card" to="/market">
            <div class="flex items-center justify-between gap-3">
              <p class="link-card-title">浏览市场</p>
              <span class="chip chip-accent">市场</span>
            </div>
            <p class="link-card-desc">浏览商品、进入详情、查看评价并执行收藏与举报操作。</p>
          </router-link>
          <router-link class="link-card" to="/favorites">
            <div class="flex items-center justify-between gap-3">
              <p class="link-card-title">我的收藏夹</p>
              <span class="chip chip-neutral">收藏</span>
            </div>
            <p class="link-card-desc">集中查看已收藏商品列表，并执行取消收藏操作。</p>
          </router-link>
          <router-link class="link-card" to="/orders/buyer">
            <div class="flex items-center justify-between gap-3">
              <p class="link-card-title">我的买家订单</p>
              <span class="chip chip-accent">Day05</span>
            </div>
            <p class="link-card-desc">查看买家订单列表并进入订单详情只读页面。</p>
          </router-link>
          <router-link class="link-card" to="/reviews/mine">
            <div class="flex items-center justify-between gap-3">
              <p class="link-card-title">我的评价记录</p>
              <span class="chip chip-neutral">评价</span>
            </div>
            <p class="link-card-desc">查看我提交过的评价记录，便于后续校验与回顾。</p>
          </router-link>
        </div>
      </div>
    </section>
  </div>
</template>
