<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  BadgeCheck,
  ClipboardList,
  Loader2,
  PackageCheck,
  PackagePlus,
  RefreshCw,
  ShoppingBag,
  Store,
} from 'lucide-vue-next'
import { createEmptySellerSummary, getSellerSummary, type SellerSummary } from '@/api/seller'
import { getUserDisplayName, isSellerUser, readCurrentUser } from '@/utils/request'
import SellerCenterNav from '@/components/commerce/SellerCenterNav.vue'

const currentUser = readCurrentUser()
const loading = ref(false)
const hasLoadedOnce = ref(false)
const errorMessage = ref('')
const summary = ref<SellerSummary>(createEmptySellerSummary())

const sellerEnabled = computed(() => isSellerUser(currentUser))
const greetingName = computed(() => getUserDisplayName(currentUser))
const shopId = computed(() => typeof currentUser?.id === 'number' && currentUser.id > 0 ? currentUser.id : null)
const primaryMetrics = computed(() => [
  { label: '在售商品', value: summary.value.onSaleProducts, icon: ShoppingBag, tone: 'text-orange-700' },
  { label: '审核中商品', value: summary.value.underReviewProducts, icon: ClipboardList, tone: 'text-orange-700' },
  { label: '待发货订单', value: summary.value.paidOrders, icon: PackageCheck, tone: 'text-orange-700' },
  { label: '已完成订单', value: summary.value.completedOrders, icon: BadgeCheck, tone: 'text-emerald-700' },
])
const secondaryMetrics = computed(() => [
  { label: '已下架', value: summary.value.offShelfProducts },
  { label: '已售出', value: summary.value.soldProducts },
  { label: '待支付', value: summary.value.pendingOrders },
  { label: '已发货', value: summary.value.shippedOrders },
  { label: '已取消', value: summary.value.cancelledOrders },
])
const pendingItems = computed(() => [
  summary.value.underReviewProducts > 0
    ? {
        title: `${summary.value.underReviewProducts} 件商品正在审核`,
        description: '查看审核中的商品状态与备注。',
        to: '/seller/products?status=under_review',
      }
    : null,
  summary.value.paidOrders > 0
    ? {
        title: `${summary.value.paidOrders} 个订单等待发货`,
        description: '买家已完成支付，请及时处理物流信息。',
        to: '/orders/seller',
      }
    : null,
].filter((item): item is { title: string; description: string; to: string } => item !== null))

async function loadSummary() {
  if (loading.value || !sellerEnabled.value) return

  loading.value = true
  errorMessage.value = ''
  try {
    summary.value = await getSellerSummary()
  } catch {
    errorMessage.value = '卖家数据暂时无法加载，请稍后重试。'
  } finally {
    loading.value = false
    hasLoadedOnce.value = true
  }
}

onMounted(() => {
  if (sellerEnabled.value) {
    void loadSummary()
  }
})
</script>

<template>
  <main class="page-body">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">卖家中心</p>
          <h1 class="page-title">卖家工作台</h1>
          <p class="page-desc">管理你的闲置商品与交易进度</p>
        </div>
        <div v-if="sellerEnabled" class="page-actions">
          <router-link class="btn-primary" to="/seller/products/new">
            <PackagePlus class="h-4 w-4" aria-hidden="true" />
            <span>发布闲置</span>
          </router-link>
          <router-link class="btn-default" to="/seller/products">管理商品</router-link>
          <router-link v-if="shopId" class="btn-default" :to="`/shop/${shopId}`">
            <Store class="h-4 w-4" aria-hidden="true" />
            查看我的小店
          </router-link>
          <router-link class="btn-default" to="/orders/seller">查看卖家订单</router-link>
        </div>
      </div>
    </section>
    <SellerCenterNav v-if="sellerEnabled" current="workbench" />

    <section v-if="!sellerEnabled" class="empty-state min-h-[320px]">
      <ShoppingBag class="empty-state-icon" aria-hidden="true" />
      <h2 class="empty-state-title">当前账号暂未启用卖家功能</h2>
      <p class="empty-state-text">卖家功能开通后，可以发布闲置商品并管理交易。</p>
      <div class="mt-5 flex flex-wrap justify-center gap-3">
        <router-link class="btn-primary" to="/market">返回市场</router-link>
        <router-link class="btn-default" to="/">返回首页</router-link>
      </div>
    </section>

    <template v-else>
      <section v-if="errorMessage" class="notice-banner notice-banner-danger">
        <span class="notice-dot bg-red-500"></span>
        <div class="flex-1">
          <p class="font-semibold">卖家数据暂时无法加载</p>
          <p class="mt-1 text-[12px] leading-5">你仍可以继续管理商品或查看订单。</p>
          <button class="btn-default mt-3" type="button" :disabled="loading" @click="loadSummary">
            <RefreshCw class="h-4 w-4" aria-hidden="true" />
            <span>重试</span>
          </button>
        </div>
      </section>

      <section class="section-panel">
        <div class="section-header">
          <div>
            <h2 class="section-heading">经营概览</h2>
            <p class="section-subtitle">你好，{{ greetingName }}。以下数据来自你的商品与订单摘要。</p>
          </div>
          <button class="btn-default" type="button" :disabled="loading" @click="loadSummary">
            <Loader2 v-if="loading" class="h-4 w-4 animate-spin" aria-hidden="true" />
            <RefreshCw v-else class="h-4 w-4" aria-hidden="true" />
            <span>{{ loading ? '刷新中' : '刷新数据' }}</span>
          </button>
        </div>
        <div class="section-body">
          <div v-if="loading && !hasLoadedOnce" class="grid gap-3 sm:grid-cols-2 xl:grid-cols-5" aria-label="正在加载经营概览">
            <div v-for="index in 5" :key="index" class="h-[126px] animate-pulse rounded-lg bg-gray-100"></div>
          </div>
          <div v-else-if="!errorMessage" class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <article v-for="metric in primaryMetrics" :key="metric.label" class="rounded-lg border border-gray-200/80 bg-white p-4">
              <component :is="metric.icon" class="h-5 w-5" :class="metric.tone" aria-hidden="true" />
              <p class="mt-5 text-[12px] font-medium text-gray-500">{{ metric.label }}</p>
              <p class="mt-1 text-[28px] font-bold text-gray-950 font-numeric">{{ metric.value }}</p>
            </article>
          </div>
        </div>
      </section>

      <section v-if="!loading && !errorMessage" class="section-panel-muted">
        <div class="section-body">
          <div class="flex flex-wrap gap-x-6 gap-y-3">
            <p v-for="metric in secondaryMetrics" :key="metric.label" class="text-[13px] text-gray-600">
              <span>{{ metric.label }}</span>
              <strong class="ml-2 font-numeric text-gray-950">{{ metric.value }}</strong>
            </p>
          </div>
        </div>
      </section>

      <section v-if="!loading && !errorMessage" class="section-panel">
        <div class="section-header">
          <div>
            <h2 class="section-heading">待处理事项</h2>
            <p class="section-subtitle">优先处理需要你关注的商品和订单。</p>
          </div>
        </div>
        <div class="section-body">
          <div v-if="pendingItems.length" class="grid gap-3 md:grid-cols-2">
            <router-link v-for="item in pendingItems" :key="item.title" class="soft-panel soft-panel-hover flex items-center justify-between gap-4 p-4" :to="item.to">
              <div>
                <p class="text-[14px] font-semibold text-gray-900">{{ item.title }}</p>
                <p class="mt-1 text-[12px] leading-5 text-gray-500">{{ item.description }}</p>
              </div>
              <span class="text-[13px] font-medium text-orange-700">查看</span>
            </router-link>
          </div>
          <p v-else class="text-[14px] text-gray-600">当前没有需要立即处理的事项。</p>
        </div>
      </section>

      <section class="section-panel">
        <div class="section-header">
          <div>
            <h2 class="section-heading">常用入口</h2>
            <p class="section-subtitle">快速回到日常商品和订单管理。</p>
          </div>
        </div>
        <div class="section-body">
          <div class="link-grid !grid-cols-1 md:!grid-cols-2">
            <router-link class="link-card" to="/seller/products">
              <p class="link-card-title">管理我的商品</p>
              <p class="link-card-desc">查看商品状态，编辑信息并处理明确的状态操作。</p>
            </router-link>
            <router-link v-if="shopId" class="link-card" :to="`/shop/${shopId}`">
              <p class="link-card-title">查看我的小店</p>
              <p class="link-card-desc">预览买家看到的公开小店页面与在售商品。</p>
            </router-link>
            <router-link class="link-card" to="/orders/seller">
              <p class="link-card-title">查看卖家订单</p>
              <p class="link-card-desc">查看交易进度并处理待完成的订单事项。</p>
            </router-link>
          </div>
        </div>
      </section>
    </template>
  </main>
</template>
