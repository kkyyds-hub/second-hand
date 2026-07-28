<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { AlertOctagon, CalendarDays, Clock, Loader2, RefreshCw, ShieldAlert } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { fetchDashboardData, type CoreMetric, type DisputeItem, type ReviewItem, type RiskAlert } from '@/api/dashboard'
import { fetchHomeStatisticsBundle, type HomeStatisticsAvailability } from '@/api/adminExtra'

const router = useRouter()
const getCurrentLocalDate = () => new Date().toLocaleDateString('en-CA')
const loading = ref(false)
const hasLoaded = ref(false)
const statisticsDate = ref(getCurrentLocalDate())
const lastUpdated = ref('暂未同步')
const dashboardError = ref('')
const dashboardErrorLevel = ref<'warning' | 'danger'>('warning')
const overviewSynced = ref(false)
const coreMetrics = ref<CoreMetric[]>([])
const reviewQueue = ref<ReviewItem[]>([])
const disputeQueue = ref<DisputeItem[]>([])
const riskAlerts = ref<RiskAlert[]>([])
const statsAvailability = ref<HomeStatisticsAvailability>({ dau: false, orderGmv: false, productPublish: false })
const homeStats = ref({ dau: 0, orderCount: 0, gmv: 0, publishTotal: 0 })

const isValidStatisticsDate = (value: string) => /^\d{4}-\d{2}-\d{2}$/.test(value) && value <= getCurrentLocalDate() && !Number.isNaN(new Date(`${value}T00:00:00`).valueOf())
const sourceState = computed(() => {
  const statsReady = Object.values(statsAvailability.value).some(Boolean)
  if (overviewSynced.value && statsReady) return '已同步'
  if (overviewSynced.value || statsReady) return '部分同步'
  return '暂未同步'
})
const formatMetricValue = (value: number) => value.toLocaleString('zh-CN')
const formatCurrencyValue = (value: number) => Math.abs(value) >= 10000 ? `¥${(value / 10000).toFixed(2)}万` : `¥${value.toLocaleString('zh-CN')}`
const statsOverviewItems = computed(() => [
  { label: '日活用户', value: statsAvailability.value.dau ? formatMetricValue(homeStats.value.dau) : '—', state: statsAvailability.value.dau ? '' : '暂未同步' },
  { label: '支付订单', value: statsAvailability.value.orderGmv ? formatMetricValue(homeStats.value.orderCount) : '—', state: statsAvailability.value.orderGmv ? '' : '暂未同步' },
  { label: '今日 GMV', value: statsAvailability.value.orderGmv ? formatCurrencyValue(homeStats.value.gmv) : '—', state: statsAvailability.value.orderGmv ? '' : '暂未同步' },
  { label: '新增发布', value: statsAvailability.value.productPublish ? formatMetricValue(homeStats.value.publishTotal) : '—', state: statsAvailability.value.productPublish ? '' : '暂未同步' },
])
const hasReviewQueue = computed(() => overviewSynced.value && reviewQueue.value.length > 0)
const hasDisputeQueue = computed(() => overviewSynced.value && disputeQueue.value.length > 0)
const hasRiskAlerts = computed(() => overviewSynced.value && riskAlerts.value.length > 0)
const dashboardErrorTitle = computed(() => dashboardErrorLevel.value === 'warning' ? '看板部分同步' : '看板暂未同步')

const refreshDashboardData = async () => {
  if (!isValidStatisticsDate(statisticsDate.value)) { dashboardErrorLevel.value = 'danger'; dashboardError.value = '请选择今天或更早的有效日期。'; return }
  loading.value = true
  dashboardError.value = ''
  const failures: string[] = []
  const [overviewResult, statsResult] = await Promise.allSettled([fetchDashboardData(statisticsDate.value), fetchHomeStatisticsBundle(statisticsDate.value)])
  let hasSuccess = false
  if (overviewResult.status === 'fulfilled') {
    const overview = overviewResult.value
    coreMetrics.value = Array.isArray(overview.coreMetrics) ? overview.coreMetrics : []
    reviewQueue.value = Array.isArray(overview.reviewQueue) ? overview.reviewQueue : []
    disputeQueue.value = Array.isArray(overview.disputeQueue) ? overview.disputeQueue : []
    riskAlerts.value = Array.isArray(overview.riskAlerts) ? overview.riskAlerts : []
    overviewSynced.value = true
    hasSuccess = true
  } else failures.push('经营概览与工作队列')
  if (statsResult.status === 'fulfilled') {
    const bundle = statsResult.value
    statsAvailability.value = bundle.availability
    if (bundle.hasAnySuccess) { homeStats.value = { dau: Number(bundle.snapshot.dau ?? 0), orderCount: Number(bundle.snapshot.orderGmv?.orderCount ?? 0), gmv: Number(bundle.snapshot.orderGmv?.gmv ?? 0), publishTotal: Number(bundle.snapshot.productPublish?.total ?? 0) }; hasSuccess = true }
    if (bundle.failureSummary) failures.push(bundle.failureSummary)
  } else failures.push('统计快照')
  if (hasSuccess) lastUpdated.value = new Date().toLocaleString('zh-CN', { hour12: false })
  if (failures.length) { dashboardErrorLevel.value = hasSuccess ? 'warning' : 'danger'; dashboardError.value = hasSuccess ? `部分数据暂未同步：${[...new Set(failures)].join('、')}。已保留本次同步成功的内容。` : `暂未同步：${[...new Set(failures)].join('、')}。请稍后重新加载。` }
  hasLoaded.value = true
  loading.value = false
}
onMounted(refreshDashboardData)
</script>

<template>
  <div class="admin-dashboard">
    <header class="admin-dashboard-header"><div><p class="admin-eyebrow">运营工作台</p><h1>平台运营概览</h1><p>当前数据来自平台业务接口，部分来源不可用时会单独标记。</p></div><form class="admin-dashboard-actions" @submit.prevent="refreshDashboardData"><label for="dashboard-date"><CalendarDays aria-hidden="true" />统计日期</label><input id="dashboard-date" v-model="statisticsDate" class="input-standard" type="date" :max="getCurrentLocalDate()" :disabled="loading" /><button class="btn-default" type="submit" :disabled="loading"><Loader2 v-if="loading" class="btn-loading-icon" aria-hidden="true" /><RefreshCw v-else aria-hidden="true" /> 查询</button></form></header>
    <div class="admin-dashboard-sync" aria-live="polite"><Clock aria-hidden="true" /><span>{{ loading ? '同步中...' : `最近同步：${lastUpdated}` }}</span><span class="status-chip" :class="sourceState === '已同步' ? 'status-chip-success' : sourceState === '部分同步' ? 'status-chip-warning' : 'status-chip-neutral'">{{ sourceState }}</span></div>
    <section v-if="dashboardError" class="state-banner" :class="dashboardErrorLevel === 'warning' ? 'state-banner-warning' : 'state-banner-danger'" role="status"><div class="state-banner-main"><span class="state-banner-icon"><AlertOctagon aria-hidden="true" /></span><div><p class="state-banner-title">{{ dashboardErrorTitle }}</p><p class="state-banner-text">{{ dashboardError }}</p></div></div><button class="btn-default" type="button" :disabled="loading" @click="refreshDashboardData">重新加载</button></section>
    <section v-if="!hasLoaded && loading" class="admin-metric-grid" aria-label="加载中的业务指标"><div v-for="index in 4" :key="index" class="admin-skeleton-card"><span></span><i></i></div></section>
    <section v-else class="admin-metric-grid" aria-label="业务指标"><article v-for="item in statsOverviewItems" :key="item.label" class="admin-metric-card"><p>{{ item.label }}</p><strong>{{ item.value }}</strong><small v-if="item.state">{{ item.state }}</small></article></section>
    <section v-if="coreMetrics.length" class="admin-core-metrics" aria-labelledby="core-metrics-title"><div class="admin-section-heading"><div><p class="admin-eyebrow">业务概览</p><h2 id="core-metrics-title">当前运营信息</h2></div></div><div><article v-for="metric in coreMetrics" :key="`${metric.title}-${metric.value}`"><p>{{ metric.title }}</p><strong>{{ metric.value }}</strong><small v-if="metric.subtext">{{ metric.subtext }}</small></article></div></section>
    <section class="admin-dashboard-work"><div class="admin-dashboard-queues"><article class="admin-queue-card"><div class="admin-section-heading"><div><p class="admin-eyebrow">审核工作区</p><h2>待处理审核队列</h2></div><button class="btn-default" type="button" @click="router.push('/products')">前往商品审核</button></div><div v-if="!overviewSynced" class="empty-state"><p class="empty-state-title">审核队列暂未同步</p><p class="empty-state-text">接口成功后显示当前待处理记录。</p></div><div v-else-if="!hasReviewQueue" class="empty-state"><p class="empty-state-title">当前没有待处理审核记录</p></div><ul v-else class="admin-queue-list"><li v-for="item in reviewQueue" :key="item.id"><div><strong>{{ item.item }}</strong><p>{{ item.sellerName }} · {{ item.type }} · {{ item.time }}</p></div><span class="status-chip status-chip-muted">{{ item.risk }}</span></li></ul></article>
      <article class="admin-queue-card"><div class="admin-section-heading"><div><p class="admin-eyebrow">纠纷与违规</p><h2>优先处理纠纷</h2></div><button class="btn-default" type="button" @click="router.push('/audit')">前往纠纷与违规</button></div><div v-if="!overviewSynced" class="empty-state"><p class="empty-state-title">纠纷队列暂未同步</p></div><div v-else-if="!hasDisputeQueue" class="empty-state"><p class="empty-state-title">当前没有需要优先跟进的事项</p></div><ul v-else class="admin-queue-list"><li v-for="item in disputeQueue" :key="item.id"><div><strong>{{ item.reason }}</strong><p>{{ item.target }} · {{ item.user }}</p></div><span class="status-chip status-chip-warning">{{ item.level }}</span></li></ul></article></div>
      <aside class="admin-risk-panel"><div class="admin-section-heading"><div><p class="admin-eyebrow">风控线索</p><h2>当前风险记录</h2></div><ShieldAlert aria-hidden="true" /></div><div v-if="!overviewSynced" class="empty-state"><p class="empty-state-title">风险记录暂未同步</p></div><div v-else-if="!hasRiskAlerts" class="empty-state"><p class="empty-state-title">当前没有风险记录</p></div><ul v-else class="admin-risk-list"><li v-for="item in riskAlerts" :key="item.id"><strong>{{ item.type }}</strong><p>{{ item.target }}</p><small>{{ item.count }}</small></li></ul></aside>
    </section>
  </div>
</template>
