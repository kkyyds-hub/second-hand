<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { AlertOctagon, Clock, Loader2, ShieldAlert } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { fetchDashboardData, type CoreMetric, type DisputeItem, type ReviewItem, type RiskAlert } from '@/api/dashboard'
import { fetchHomeStatisticsBundle, type HomeStatisticsAvailability } from '@/api/adminExtra'

const router = useRouter()
const getCurrentLocalDate = () => new Date().toLocaleDateString('en-CA')

const loading = ref(false)
const statisticsDate = ref(getCurrentLocalDate())
const lastUpdated = ref('暂未同步')
const dashboardError = ref('')
const dashboardErrorLevel = ref<'warning' | 'danger'>('warning')
const overviewSynced = ref(false)
const coreMetrics = ref<CoreMetric[]>([])
const reviewQueue = ref<ReviewItem[]>([])
const disputeQueue = ref<DisputeItem[]>([])
const riskAlerts = ref<RiskAlert[]>([])
const disputeQueueSource = ref<'overview' | 'audit-overview'>('overview')
const statsAvailability = ref<HomeStatisticsAvailability>({ dau: false, orderGmv: false, productPublish: false })
const homeStats = ref({ dau: 0, orderCount: 0, gmv: 0, publishTotal: 0 })

const buildDashboardErrorMessage = (failedSources: string[], hasSuccess: boolean) =>
  hasSuccess
    ? `部分数据暂未同步：${failedSources.join('、')}。保留本次会话已同步内容。`
    : `暂未同步：${failedSources.join('、')}。请稍后重试。`

const dashboardErrorTone = computed(() => dashboardErrorLevel.value === 'warning' ? 'state-banner-warning' : 'state-banner-danger')
const dashboardErrorTitle = computed(() => dashboardErrorLevel.value === 'warning' ? '看板部分同步' : '看板暂未同步')
const sourceState = computed(() => {
  const statsReady = Object.values(statsAvailability.value).some(Boolean)
  if (overviewSynced.value && statsReady) return '已同步'
  if (overviewSynced.value || statsReady) return '部分同步'
  return '暂未同步'
})
const hasReviewQueue = computed(() => overviewSynced.value && reviewQueue.value.length > 0)
const hasDisputeQueue = computed(() => overviewSynced.value && disputeQueue.value.length > 0)
const hasRiskAlerts = computed(() => overviewSynced.value && riskAlerts.value.length > 0)
const disputeQueueTitle = computed(() => disputeQueueSource.value === 'audit-overview' ? '优先跟进事项' : '优先处理纠纷')
const formatMetricValue = (value: number) => value.toLocaleString('zh-CN')
const formatCurrencyValue = (value: number) => Math.abs(value) >= 10000 ? `¥${(value / 10000).toFixed(2)}万` : `¥${value.toLocaleString('zh-CN')}`
const statsOverviewItems = computed(() => [
  { label: '日活用户', value: statsAvailability.value.dau ? formatMetricValue(homeStats.value.dau) : '—' },
  { label: '支付订单', value: statsAvailability.value.orderGmv ? formatMetricValue(homeStats.value.orderCount) : '—' },
  { label: '今日 GMV', value: statsAvailability.value.orderGmv ? formatCurrencyValue(homeStats.value.gmv) : '—' },
  { label: '新增发布', value: statsAvailability.value.productPublish ? formatMetricValue(homeStats.value.publishTotal) : '—' },
])

const refreshDashboardData = async () => {
  loading.value = true
  dashboardError.value = ''
  const failures: string[] = []
  const [overviewResult, statsResult] = await Promise.allSettled([
    fetchDashboardData(statisticsDate.value),
    fetchHomeStatisticsBundle(statisticsDate.value),
  ])
  let hasSuccess = false

  if (overviewResult.status === 'fulfilled') {
    const overview = overviewResult.value
    coreMetrics.value = Array.isArray(overview.coreMetrics) ? overview.coreMetrics : []
    reviewQueue.value = Array.isArray(overview.reviewQueue) ? overview.reviewQueue : []
    disputeQueue.value = Array.isArray(overview.disputeQueue) ? overview.disputeQueue : []
    riskAlerts.value = Array.isArray(overview.riskAlerts) ? overview.riskAlerts : []
    disputeQueueSource.value = overview.disputeQueueSource === 'audit-overview' ? 'audit-overview' : 'overview'
    overviewSynced.value = true
    hasSuccess = true
  } else {
    failures.push('经营概览与工作队列')
  }

  if (statsResult.status === 'fulfilled') {
    const bundle = statsResult.value
    statsAvailability.value = bundle.availability
    if (bundle.hasAnySuccess) {
      homeStats.value = {
        dau: Number(bundle.snapshot.dau ?? 0),
        orderCount: Number(bundle.snapshot.orderGmv?.orderCount ?? 0),
        gmv: Number(bundle.snapshot.orderGmv?.gmv ?? 0),
        publishTotal: Number(bundle.snapshot.productPublish?.total ?? 0),
      }
      hasSuccess = true
    }
    if (bundle.failureSummary) failures.push(bundle.failureSummary)
    if (!bundle.hasAnySuccess) failures.push('统计快照')
  } else {
    failures.push('统计快照')
  }

  if (hasSuccess) lastUpdated.value = new Date().toLocaleString('zh-CN', { hour12: false })
  if (failures.length) {
    dashboardErrorLevel.value = hasSuccess ? 'warning' : 'danger'
    dashboardError.value = buildDashboardErrorMessage([...new Set(failures)], hasSuccess)
  }
  loading.value = false
}

onMounted(refreshDashboardData)
</script>

<template>
  <div class="mx-auto max-w-[1600px] space-y-6 pb-8">
    <header class="flex flex-col gap-4 border-b border-gray-200 pb-5 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <div class="flex flex-wrap items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900">运营中枢</h1>
          <span class="status-chip status-chip-muted">实时快照</span>
          <span class="status-chip" :class="sourceState === '已同步' ? 'status-chip-success' : sourceState === '部分同步' ? 'status-chip-warning' : 'status-chip-neutral'">{{ sourceState }}</span>
        </div>
        <p class="mt-2 text-sm text-gray-500">数据以当前页面请求结果为准，不展示模拟经营数据或趋势。</p>
      </div>
      <div class="flex items-center gap-3 text-xs text-gray-500">
        <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
        <Clock v-else class="h-4 w-4" />
        <span>{{ loading ? '同步中...' : `最近同步：${lastUpdated}` }}</span>
        <button class="btn-default px-3 py-1.5 text-xs" :disabled="loading" @click="refreshDashboardData">刷新</button>
      </div>
    </header>

    <section v-if="dashboardError" class="state-banner" :class="dashboardErrorTone">
      <div class="state-banner-body">
        <div class="state-banner-main">
          <span class="state-banner-icon" :class="dashboardErrorLevel === 'warning' ? 'border-orange-200' : 'border-red-200'"><AlertOctagon class="h-4 w-4" /></span>
          <div><p class="state-banner-title">{{ dashboardErrorTitle }}</p><p class="state-banner-text">{{ dashboardError }}</p></div>
        </div>
        <button class="btn-default shrink-0 px-3 py-1.5 text-xs" :disabled="loading" @click="refreshDashboardData">重新加载</button>
      </div>
    </section>

    <section class="grid grid-cols-2 gap-3 lg:grid-cols-4">
      <article v-for="item in statsOverviewItems" :key="item.label" class="border border-gray-200 bg-white p-4">
        <p class="text-xs text-gray-500">{{ item.label }}</p><p class="mt-2 text-2xl font-semibold text-gray-900">{{ item.value }}</p>
      </article>
    </section>

    <section class="grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
      <div class="space-y-5">
        <article class="border border-gray-200 bg-white">
          <div class="flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 px-5 py-4"><div><p class="card-kicker">审核工作区</p><h2 class="card-title">待处理审核队列</h2></div><button class="btn-default text-xs" @click="router.push('/products')">前往商品审核</button></div>
          <div v-if="!overviewSynced" class="empty-state"><p class="empty-state-title">审核队列暂未同步</p><p class="empty-state-text">接口成功后显示当前待处理记录。</p></div>
          <div v-else-if="!hasReviewQueue" class="empty-state"><p class="empty-state-title">当前没有待处理审核记录</p></div>
          <div v-else class="divide-y divide-gray-100"><div v-for="item in reviewQueue" :key="item.id" class="flex flex-col gap-2 p-4 sm:flex-row sm:items-center sm:justify-between"><div><p class="font-medium text-gray-900">{{ item.item }}</p><p class="mt-1 text-xs text-gray-500">{{ item.sellerName }} · {{ item.type }} · {{ item.time }}</p></div><span class="status-chip status-chip-muted">{{ item.risk }}</span></div></div>
        </article>
        <article class="border border-gray-200 bg-white">
          <div class="flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 px-5 py-4"><div><p class="card-kicker">纠纷与违规</p><h2 class="card-title">{{ disputeQueueTitle }}</h2></div><button class="btn-default text-xs" @click="router.push('/audit')">前往纠纷与违规</button></div>
          <div v-if="!overviewSynced" class="empty-state"><p class="empty-state-title">纠纷队列暂未同步</p></div>
          <div v-else-if="!hasDisputeQueue" class="empty-state"><p class="empty-state-title">当前没有需要优先跟进的事项</p></div>
          <div v-else class="divide-y divide-gray-100"><div v-for="item in disputeQueue" :key="item.id" class="p-4"><div class="flex items-start justify-between gap-3"><div><p class="font-medium text-gray-900">{{ item.reason }}</p><p class="mt-1 text-xs text-gray-500">{{ item.target }} · {{ item.user }}</p></div><span class="status-chip status-chip-warning">{{ item.level }}</span></div></div></div>
        </article>
      </div>
      <aside class="border border-gray-200 bg-white">
        <div class="border-b border-gray-100 p-5"><p class="card-kicker">风控线索</p><h2 class="card-title">当前风险记录</h2></div>
        <div v-if="!overviewSynced" class="empty-state"><ShieldAlert class="empty-state-icon" /><p class="empty-state-title">风险记录暂未同步</p></div>
        <div v-else-if="!hasRiskAlerts" class="empty-state"><p class="empty-state-title">当前没有风险记录</p></div>
        <div v-else class="divide-y divide-gray-100"><div v-for="item in riskAlerts" :key="item.id" class="p-4"><p class="font-medium text-gray-900">{{ item.type }}</p><p class="mt-1 text-xs text-gray-500">{{ item.target }}</p><p class="mt-2 text-xs text-gray-600">{{ item.count }}</p></div></div>
      </aside>
    </section>
  </div>
</template>
