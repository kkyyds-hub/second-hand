<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Loader2, RefreshCw } from 'lucide-vue-next'
import { createEmptySellerSummary, getSellerSummary, type SellerSummary } from '@/api/seller'
import { getUserDisplayName, getUserPrimaryContact, isSellerUser, readCurrentUser } from '@/utils/request'

const currentUser = readCurrentUser()
const loading = ref(false)
const hasLoadedOnce = ref(false)
const errorMessage = ref('')
const summary = ref<SellerSummary>(createEmptySellerSummary())

const greetingName = computed(() => getUserDisplayName(currentUser))
const primaryContact = computed(() => getUserPrimaryContact(currentUser))
const sellerEnabled = computed(() => isSellerUser(currentUser))

const summaryStatusText = computed(() => {
  if (!sellerEnabled.value) {
    return '当前账号未开通卖家身份'
  }

  if (loading.value) {
    return '正在同步卖家摘要...'
  }

  if (errorMessage.value) {
    return '卖家摘要加载失败'
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
  if (loading.value || !sellerEnabled.value) {
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
  if (sellerEnabled.value) {
    loadSummary()
  } else {
    hasLoadedOnce.value = true
  }
})
</script>

<template>
  <div class="page-body">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">卖家</p>
          <h1 class="page-title">卖家工作台</h1>
          <p class="page-desc">你好，{{ greetingName }}（{{ primaryContact }}）。这里聚合卖家摘要，并提供 Day04 第二包的 userProducts 写操作入口。</p>
        </div>
        <div class="page-actions">
          <span class="chip chip-muted">{{ summaryStatusText }}</span>
          <button class="btn-default" type="button" :disabled="loading || !sellerEnabled" @click="loadSummary">
            <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
            <RefreshCw v-else class="h-4 w-4" />
            <span>{{ loading ? '刷新中' : '刷新摘要' }}</span>
          </button>
        </div>
      </div>
    </section>

    <section v-if="!sellerEnabled" class="notice-banner notice-banner-warning">
      <span class="notice-dot bg-orange-500"></span>
      <span>当前账号尚未开通卖家身份。Day04 第一包已提供页面入口，但运行态是否可访问依赖后端卖家身份校验。</span>
    </section>

    <section v-if="errorMessage" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <div>
        <p class="font-semibold">摘要加载失败</p>
        <p class="mt-1 text-[12px] leading-5">{{ errorMessage }}</p>
      </div>
    </section>

    <section class="section-panel">
      <div class="section-header">
        <div>
          <h2 class="section-heading">经营概览</h2>
          <p class="section-subtitle">复用 seller summary 指标，不引入 Day04 写操作按钮。</p>
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
          <p class="section-subtitle">同一套中性卡片风格承载次级指标。</p>
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
          <h2 class="section-heading">Day04 第二包入口</h2>
          <p class="section-subtitle">本包补齐 userProducts 创建、编辑、删除与状态流转入口，并保留列表/详情读取链路。</p>
        </div>
      </div>
      <div class="section-body">
        <div class="link-grid !grid-cols-1 md:!grid-cols-3">
          <router-link class="link-card" to="/seller/products">
            <div class="flex items-center justify-between gap-3">
              <p class="link-card-title">我的商品列表</p>
              <span class="chip chip-accent">userProducts</span>
            </div>
            <p class="link-card-desc">查看卖家商品列表，进入详情页并执行编辑、删除、状态操作。</p>
          </router-link>
          <router-link class="link-card" to="/seller/products/new">
            <div class="flex items-center justify-between gap-3">
              <p class="link-card-title">创建商品</p>
              <span class="chip chip-success">create</span>
            </div>
            <p class="link-card-desc">进入创建表单，提交后进入商品详情页继续后续管理。</p>
          </router-link>
          <div class="link-card border-dashed">
            <div class="flex items-center justify-between gap-3">
              <p class="link-card-title">状态流转提示</p>
              <span class="chip chip-neutral">off-shelf / resubmit / on-shelf / withdraw</span>
            </div>
            <p class="link-card-desc">on-shelf 保留兼容提审语义，不代表直接进入在售状态。</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
