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
const roleText = computed(() => (isSellerUser(currentUser) ? '当前账号已具备卖家身份。' : '当前账号可能尚未开通卖家身份。'))
const summaryStatusText = computed(() => {
  if (loading.value) {
    return '正在同步最新卖家摘要...'
  }

  /**
   * Day01 联调仍可能停在接口异常或鉴权失败。
   * 这里优先展示失败态，避免首轮请求失败后仍沿用“已加载”文案误导联调判断。
   */
  if (errorMessage.value) {
    return '卖家摘要加载失败，请检查当前登录态或接口状态后重试。'
  }

  if (hasLoadedOnce.value) {
    return '卖家摘要已加载，可手动刷新。'
  }

  return '页面初始化中...'
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

    /**
     * 页面层只维护 loading / error / retry。
     * 字段默认值、数字归一化和别名兼容已经下沉到 `src/api/seller.ts`。
     */
    summary.value = await getSellerSummary()
  } catch (error: any) {
    errorMessage.value = error.message || '卖家摘要加载失败，请稍后重试。'
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
  <div class="space-y-6">
    <section class="card p-6 md:p-8">
      <p class="muted-kicker">首页摘要</p>
      <h1 class="section-title mt-3">你好，{{ greetingName }}</h1>
      <p class="section-desc">
        这里展示当前登录账号的基础卖家摘要，便于你在进入更完整的业务模块前先确认会话状态与关键统计。
      </p>

      <div class="mt-5 grid gap-3 md:grid-cols-3">
        <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
          <p class="text-xs text-slate-400">当前会话</p>
          <p class="mt-2 text-sm font-medium text-slate-900">{{ roleText }}</p>
        </div>
        <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
          <p class="text-xs text-slate-400">联系方式</p>
          <p class="mt-2 text-sm font-medium text-slate-900">{{ primaryContact }}</p>
        </div>
        <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
          <p class="text-xs text-slate-400">摘要状态</p>
          <p class="mt-2 text-sm font-medium text-slate-900">{{ summaryStatusText }}</p>
        </div>
      </div>
    </section>

    <section v-if="errorMessage" class="rounded-2xl border border-orange-200 bg-orange-50 px-5 py-4 text-sm text-orange-700">
      {{ errorMessage }}
    </section>

    <section class="card p-6">
      <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <p class="muted-kicker">Seller summary</p>
          <h2 class="section-title mt-2">卖家经营概览</h2>
          <p class="section-desc mt-1">
            当前首页只冻结摘要级数据，不等同于完整的市场首页、卖家工作台或订单域页面。
          </p>
        </div>

        <button class="btn-default gap-2" type="button" :disabled="loading" @click="loadSummary">
          <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
          <RefreshCw v-else class="h-4 w-4" />
          <span>{{ loading ? '刷新中...' : '刷新摘要' }}</span>
        </button>
      </div>

      <div class="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <article v-for="metric in primaryMetrics" :key="metric.label" class="metric-card">
          <p class="text-sm text-slate-500">{{ metric.label }}</p>
          <p class="mt-4 text-3xl font-semibold text-slate-900">{{ metric.value }}</p>
        </article>
      </div>
    </section>

    <section class="card p-6">
      <p class="muted-kicker">More details</p>
      <h2 class="section-title mt-2">补充统计</h2>
      <p class="section-desc mt-1">这些字段与首页摘要共用同一接口，便于在 Day01 就确认基础数据口径。</p>

      <div class="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        <article v-for="metric in secondaryMetrics" :key="metric.label" class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
          <p class="text-sm text-slate-500">{{ metric.label }}</p>
          <p class="mt-3 text-2xl font-semibold text-slate-900">{{ metric.value }}</p>
        </article>
      </div>
    </section>
  </div>
</template>
