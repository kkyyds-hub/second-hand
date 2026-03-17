<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  AlertOctagon,
  ArrowDownRight,
  ArrowUpRight,
  Clock,
  Info,
  Loader2,
  MessageSquareWarning,
  ShieldAlert,
} from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { fetchDashboardData, type CoreMetric, type DisputeItem, type ReviewItem, type RiskAlert } from '@/api/dashboard'
import { fetchHomeStatisticsBundle, type HomeStatisticsAvailability } from '@/api/adminExtra'

const router = useRouter()

/**
 * 首页所有统计都按“本地日期”请求，避免各个 API 调用点自行拼装日期字符串。
 */
const getCurrentLocalDate = () => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/**
 * 默认数据承担首屏骨架和失败兜底两个职责：
 * - 接口未返回前，页面依然保持完整结构
 * - 某一路接口失败时，review 仍能看到卡片和队列布局，而不是整页空白
 */
const defaultCoreMetrics: [CoreMetric, CoreMetric, CoreMetric, CoreMetric] = [
  { title: '今日成交额(GMV)', value: '¥542.45万', trend: '+15.2%', isUp: true, subtext: '较昨日同时段增加 15.2%' },
  { title: '新增付款订单', value: '4,105', trend: '+8.4%', isUp: true, subtext: '客单价稳定在 ¥1,321' },
  { title: '待审异常商品', value: '182', trend: '+12.1%', isUp: false, subtext: '超时未审积压 14 单' },
  { title: '售后争议 & 举报', value: '47', trend: '-5.0%', isUp: true, subtext: '平台强介入违规单 21 单' },
]

const coreMetrics = ref<CoreMetric[]>([...defaultCoreMetrics])

const reviewQueue = ref<ReviewItem[]>([
  { id: '审核-8902', item: 'Apple iPhone 15 Pro Max 256GB 钛金属', sellerName: '数码回收_老王', type: '高价值', price: '¥6,950', time: '10分钟前', risk: '正常' },
  { id: '审核-8903', item: '全新未拆封 劳力士绿水鬼', sellerName: 'WatchMaster', type: '品牌防伪', price: '¥125,000', time: '15分钟前', risk: '高风险' },
  { id: '审核-8904', item: 'Nike Air Force 1 联名款 42码', sellerName: 'Sneaker搬砖人', type: '图片异常', price: '¥8,500', time: '22分钟前', risk: '中风险' },
  { id: '审核-8905', item: 'Sony A7M4 微单套机 95新', sellerName: '光影流年', type: '常规审核', price: '¥13,200', time: '1小时前', risk: '正常' },
])

const disputeQueue = ref<DisputeItem[]>([
  { id: '纠纷-102', reason: '商品描述严重不符（瑕疵未告知）', target: '已签收', user: '买家投诉卖家', level: '紧急' },
  { id: '纠纷-103', reason: '疑似售假 / 鉴定未通过', target: '交易阻断', user: '查验中心拦截', level: '紧急' },
  { id: '纠纷-104', reason: '发货超时 / 虚假发货', target: '退款申请中', user: '买家发起', level: '中风险' },
])

const riskAlerts = ref<RiskAlert[]>([
  { id: '风控-1', type: '同设备多账号批量发布', target: '设备指纹: FA98...21C', count: '14个账号关联' },
  { id: '风控-2', type: '价格严重偏离市场均价', target: 'M2 芯片 MacBook Air 挂牌 800元', count: '已拦截违规展示' },
  { id: '风控-3', type: '支付异常预警', target: '同一卡段高频拉起支付', count: '风控组已介入' },
])

/**
 * 首页运行时数据拆成两路：
 * - 概览接口：核心卡片、审核/纠纷/风控队列
 * - 统计接口：DAU、订单、GMV、发布量
 * 因为允许“部分成功”，所以需要 availability + errorLevel 一起描述页面现状。
 */
const loading = ref(false)
const statisticsDate = ref(getCurrentLocalDate())
const lastUpdated = ref('未同步')
const dashboardError = ref('')
const dashboardErrorLevel = ref<'warning' | 'danger'>('warning')

const defaultStatsAvailability: HomeStatisticsAvailability = {
  dau: false,
  orderGmv: false,
  productPublish: false,
}

const statsAvailability = ref<HomeStatisticsAvailability>({ ...defaultStatsAvailability })
const homeStats = ref({
  dau: 0,
  orderCount: 0,
  gmv: 0,
  publishTotal: 0,
})

/**
 * 页面层只负责把错误翻译成最终展示文案：
 * - 不改 API 契约
 * - 不改字段映射
 * - 只统一“当前首页应该如何解释这次失败”
 */
const resolvePageErrorMessage = (error: unknown, fallback: string) => {
  if (error && typeof error === 'object' && 'message' in error) {
    const message = error.message
    if (typeof message === 'string' && message.trim()) {
      return message.trim()
    }
  }

  return fallback
}

const dashboardErrorTone = computed(() =>
  dashboardErrorLevel.value === 'warning' ? 'state-banner-warning' : 'state-banner-danger',
)

const dashboardErrorTitle = computed(() =>
  dashboardErrorLevel.value === 'warning' ? '看板部分数据暂未同步' : '看板暂未同步成功',
)

const buildDashboardErrorMessage = (failedSummaries: string[], hasAnySuccess: boolean) => {
  if (!failedSummaries.length) return ''

  return hasAnySuccess
    ? `部分数据暂未同步：${failedSummaries.join('、')}。页面先展示已同步内容。`
    : `看板暂未同步成功：${failedSummaries.join('、')}。当前先保留既有卡片与兜底视图，请稍后重试。`
}

/**
 * 指标卡片文案会跟着产品话术调整，页面不直接依赖固定下标，
 * 而是用关键词把“GMV / 订单 / 争议”映射回对应卡片，降低文案改动带来的脆弱性。
 */
const normalizeTitle = (title: string) => title.toLowerCase().replace(/\s+/g, '')

const findMetricByKeywords = (keywords: string[]) => {
  return coreMetrics.value.find((metric) => {
    const title = normalizeTitle(metric?.title || '')
    return keywords.some((keyword) => title.includes(keyword))
  })
}

const gmvMetric = computed(() => {
  return findMetricByKeywords(['gmv', '成交额']) || defaultCoreMetrics[0]
})

const paidOrderMetric = computed(() => {
  const direct =
    findMetricByKeywords(['付款订单', '新增付款', '支付订单']) ||
    coreMetrics.value.find((metric) => {
      const title = normalizeTitle(metric?.title || '')
      return title.includes('订单') && !title.includes('成交') && !title.includes('争议') && !title.includes('举报')
    })

  return direct || defaultCoreMetrics[1]
})

const disputeMetric = computed(() => {
  return findMetricByKeywords(['争议', '举报', '售后', '纠纷']) || defaultCoreMetrics[3]
})

const hasReviewQueue = computed(() => reviewQueue.value.length > 0)
const hasDisputeQueue = computed(() => disputeQueue.value.length > 0)
const hasRiskAlerts = computed(() => riskAlerts.value.length > 0)
const disputeQueueSource = ref<'overview' | 'audit-overview'>('overview')

const disputeQueueTitle = computed(() =>
  disputeQueueSource.value === 'audit-overview' ? '优先跟进事项' : '优先处理纠纷',
)

const disputeQueueDesc = computed(() =>
  disputeQueueSource.value === 'audit-overview'
    ? '优先展示需要尽快跟进的纠纷、举报与风险事项'
    : '临近处理时限的纠纷事项，请优先跟进',
)

const disputeQueueEmptyTitle = computed(() =>
  disputeQueueSource.value === 'audit-overview'
    ? '当前暂无需要优先跟进的事项'
    : '当前暂无需要优先处理的纠纷',
)

const disputeQueueEmptyText = computed(() =>
  disputeQueueSource.value === 'audit-overview'
    ? '稍后可在这里查看最新的纠纷、举报与风险跟进事项。'
    : '纠纷队列清空后，这里会继续展示新进入处理窗口的事项。',
)

const formatMetricValue = (value: string | number) => {
  if (typeof value === 'number') {
    return value.toLocaleString('zh-CN')
  }

  return value
}

const formatCurrencyValue = (value: number) => {
  if (!Number.isFinite(value)) return '—'
  if (Math.abs(value) >= 10000) {
    return `¥${(value / 10000).toFixed(2)}万`
  }

  return `¥${value.toLocaleString('zh-CN')}`
}

const statsOverviewItems = computed(() => [
  {
    label: '日活用户',
    value: statsAvailability.value.dau ? formatMetricValue(homeStats.value.dau) : '—',
  },
  {
    label: '支付订单',
    value: statsAvailability.value.orderGmv ? formatMetricValue(homeStats.value.orderCount) : '—',
  },
  {
    label: '今日 GMV',
    value: statsAvailability.value.orderGmv ? formatCurrencyValue(homeStats.value.gmv) : '—',
  },
  {
    label: '新增发布',
    value: statsAvailability.value.productPublish ? formatMetricValue(homeStats.value.publishTotal) : '—',
  },
])

const paidOrderDisplayValue = computed(() => {
  const metricValue = Number(homeStats.value.orderCount ?? 0)
  return statsAvailability.value.orderGmv
    ? metricValue.toLocaleString('zh-CN')
    : formatMetricValue(paidOrderMetric.value.value || 0)
})

/**
 * 这些轻量趋势图只用于增强首页观感，不依赖真实接口。
 * 直接在页面内生成 SVG path，可以避免为了示意图额外引入图表库。
 */
const mockTrendData = {
  gmv: [40, 45, 42, 50, 55, 52, 60, 65, 62, 70, 75, 80, 85, 82, 90, 95, 100],
  paidOrder: [20, 22, 21, 25, 24, 28, 27, 30, 32, 31, 35, 34, 38, 40, 42],
  dispute: [15, 14, 16, 13, 12, 14, 11, 10, 12, 9, 8, 10, 7, 6, 5],
}

const buildSparklinePaths = (values: number[], width: number, height: number) => {
  if (!values.length) {
    return { linePath: '', areaPath: '' }
  }

  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1
  const stepX = values.length > 1 ? width / (values.length - 1) : width
  const points = values.map((value, index) => {
    const x = Number((index * stepX).toFixed(2))
    const y = Number((height - ((value - min) / range) * (height - 6) - 3).toFixed(2))
    return { x, y }
  })

  const linePath = points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ')
  const areaPath = `${linePath} L ${width} ${height} L 0 ${height} Z`

  return { linePath, areaPath }
}

const buildBarChartDefs = (values: number[], height: number) => {
  if (!values.length) {
    return []
  }

  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1

  return values.map((value) => {
    const normalized = (value - min) / range
    return Math.max(28, Math.round(28 + normalized * (height - 28)))
  })
}

const sparklineDefs = {
  gmv: buildSparklinePaths(mockTrendData.gmv, 200, 60),
  paidOrder: buildSparklinePaths(mockTrendData.paidOrder, 100, 30),
  dispute: buildSparklinePaths(mockTrendData.dispute, 100, 30),
}

const barChartDefs = {
  paidOrder: buildBarChartDefs(mockTrendData.paidOrder, 100),
  dispute: buildBarChartDefs(mockTrendData.dispute, 100),
}

/**
 * 刷新逻辑故意用 allSettled 合并两路数据源：
 * - 任意一路成功都更新对应区域
 * - 失败时保留上一份可读数据，并附带失败来源提示
 * 这样 review 时更容易判断“哪块数据断了”。
 */
const refreshDashboardData = async () => {
  try {
    loading.value = true
    dashboardError.value = ''
    const failedSummaries: string[] = []

    const [overviewResult, statsResult] = await Promise.allSettled([
      fetchDashboardData(statisticsDate.value),
      fetchHomeStatisticsBundle(statisticsDate.value),
    ])

    let hasSuccessfulRuntimePayload = false

    if (overviewResult.status === 'fulfilled') {
      const res = overviewResult.value

      if (res?.coreMetrics?.length) {
        coreMetrics.value = res.coreMetrics
      }

      if (Array.isArray(res?.reviewQueue)) {
        reviewQueue.value = res.reviewQueue
      }

      if (Array.isArray(res?.disputeQueue)) {
        disputeQueue.value = res.disputeQueue
      }

      disputeQueueSource.value = res?.disputeQueueSource === 'audit-overview' ? 'audit-overview' : 'overview'

      if (Array.isArray(res?.riskAlerts)) {
        riskAlerts.value = res.riskAlerts
      }

      hasSuccessfulRuntimePayload = true
    } else {
      console.warn('Dashboard overview load failed, keeping current cards and queues.', overviewResult.reason)
      failedSummaries.push('经营概览与工作队列')
    }

    if (statsResult.status === 'fulfilled') {
      const statsBundle = statsResult.value

      if (statsBundle.hasAnySuccess) {
        statsAvailability.value = statsBundle.availability
        homeStats.value = {
          dau: Number(statsBundle.snapshot.dau ?? 0),
          orderCount: Number(statsBundle.snapshot.orderGmv?.orderCount ?? 0),
          gmv: Number(statsBundle.snapshot.orderGmv?.gmv ?? 0),
          publishTotal: Number(statsBundle.snapshot.productPublish?.total ?? 0),
        }
        hasSuccessfulRuntimePayload = true
      }

      if (statsBundle.failureSummary) {
        failedSummaries.push(statsBundle.failureSummary)
      }

      if (!statsBundle.hasAnySuccess) {
        console.warn('Dashboard statistics load failed, keeping current metric view.')
      }
    } else {
      console.warn('Dashboard statistics load failed, keeping metric fallbacks.', statsResult.reason)
      failedSummaries.push('统计快照')
    }

    if (hasSuccessfulRuntimePayload) {
      lastUpdated.value = new Date().toLocaleString('zh-CN', {
        hour12: false,
      })
    }

    if (failedSummaries.length > 0) {
      dashboardErrorLevel.value = hasSuccessfulRuntimePayload ? 'warning' : 'danger'
      dashboardError.value = buildDashboardErrorMessage(failedSummaries, hasSuccessfulRuntimePayload)
    }
  } catch (error) {
    console.warn('Dashboard data load failed, keeping current view.', error)
    dashboardErrorLevel.value = 'danger'
    dashboardError.value = resolvePageErrorMessage(error, '看板加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refreshDashboardData()
})
</script>

<template>
  <div class="mx-auto max-w-[1600px] space-y-6 pb-8">
    <div class="relative overflow-hidden rounded-2xl border border-gray-200/80 bg-white p-6 shadow-sm">
      <div class="pointer-events-none absolute right-0 top-0 h-full w-64 bg-gradient-to-l from-blue-50/50 to-transparent"></div>

      <div class="relative z-10 flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <h1 class="text-2xl font-bold tracking-tight text-gray-900">运营中枢</h1>
          <p class="mt-3 text-[14px] text-gray-500">
            当前工作焦点：<span class="font-medium text-gray-900">大促前夕风控排查与高危纠纷清零</span>
          </p>
        </div>

        <div class="flex items-center gap-2 text-[12px] text-gray-500">
          <Loader2 v-if="loading" class="h-3.5 w-3.5 animate-spin" />
          <Clock v-else class="h-3.5 w-3.5" />
          <span>{{ loading ? '看板更新中...' : `最近更新：${lastUpdated}` }}</span>
        </div>
      </div>
    </div>

    <div v-if="dashboardError" class="state-banner" :class="dashboardErrorTone">
      <div class="state-banner-body">
        <div class="state-banner-main">
          <span
            class="state-banner-icon"
            :class="dashboardErrorLevel === 'warning' ? 'border-orange-200' : 'border-red-200'"
          >
            <AlertOctagon
              class="h-4 w-4"
              :class="dashboardErrorLevel === 'warning' ? 'text-orange-600' : 'text-red-600'"
            />
          </span>
          <div>
            <p class="state-banner-title">{{ dashboardErrorTitle }}</p>
            <p
              class="state-banner-text"
              :class="dashboardErrorLevel === 'warning' ? 'text-orange-700/90' : 'text-red-700/90'"
            >
              {{ dashboardError }}
            </p>
          </div>
        </div>
        <button class="btn-default shrink-0 px-3 py-1.5 text-[12px]" :disabled="loading" @click="refreshDashboardData">
          重新加载
        </button>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-4 lg:grid-cols-4">
      <div class="space-y-4 lg:col-span-2">
        <div class="relative min-h-[240px] overflow-hidden rounded-2xl bg-gray-900 p-6 text-white shadow-md">
          <div class="absolute -right-10 -top-10 h-40 w-40 rounded-full bg-white/5 blur-2xl"></div>

          <div class="pointer-events-none absolute bottom-0 left-0 h-32 w-full opacity-40">
            <svg viewBox="0 0 200 60" preserveAspectRatio="none" class="h-full w-full">
              <defs>
                <linearGradient id="gmvGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#4ade80" stop-opacity="0.4" />
                  <stop offset="100%" stop-color="#4ade80" stop-opacity="0" />
                </linearGradient>
              </defs>
              <path :d="sparklineDefs.gmv.areaPath" fill="url(#gmvGradient)" />
              <path :d="sparklineDefs.gmv.linePath" fill="none" stroke="#4ade80" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </div>

          <div class="relative z-10 flex h-full flex-col">
            <div class="flex items-start justify-between gap-4">
              <div>
                <p class="text-[14px] font-medium text-gray-300">{{ gmvMetric.title }}</p>
                <p class="mt-3 text-4xl font-bold tracking-tight font-numeric">{{ gmvMetric.value }}</p>
                <p class="mt-2 text-[13px] text-gray-400">{{ gmvMetric.subtext }}</p>
              </div>

              <div
                class="flex items-center gap-1 rounded border px-2 py-1 text-[12px] font-medium"
                :class="gmvMetric.isUp ? 'border-emerald-400/20 bg-emerald-400/10 text-emerald-300' : 'border-red-400/20 bg-red-400/10 text-red-300'"
              >
                <ArrowUpRight v-if="gmvMetric.isUp" class="h-3.5 w-3.5" />
                <ArrowDownRight v-else class="h-3.5 w-3.5" />
                <span class="font-numeric">{{ gmvMetric.trend || '+0.0%' }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="rounded-2xl border border-gray-200/80 bg-white p-4 shadow-sm">
          <div class="section-header">
            <div>
              <h2 class="section-title">核心经营概览</h2>
              <p class="section-meta">看板日期 {{ statisticsDate }}</p>
            </div>
          </div>

          <div class="mt-4 grid grid-cols-2 gap-3">
            <div
              v-for="item in statsOverviewItems"
              :key="item.label"
              class="rounded-xl border border-gray-200 bg-gray-50/60 p-3"
            >
              <p class="text-[12px] text-gray-500">{{ item.label }}</p>
              <p class="mt-1 text-[20px] font-semibold text-gray-900 font-numeric">{{ item.value }}</p>
            </div>
          </div>
        </div>
      </div>

      <div class="flex min-h-[340px] flex-col rounded-2xl border border-gray-200/80 bg-white p-6 shadow-sm xl:min-h-[360px]">
        <div class="card-header">
          <div>
            <p class="card-kicker">{{ paidOrderMetric.title }}</p>
            <p class="mt-3 text-3xl font-bold tracking-tight text-gray-900 font-numeric">{{ paidOrderDisplayValue }}</p>
            <p class="mt-2 text-xs text-gray-500">{{ paidOrderMetric.subtext || '暂无更多说明' }}</p>
          </div>

          <div class="status-chip status-chip-success">
            <ArrowUpRight class="h-3 w-3" />
            <span class="font-numeric">{{ paidOrderMetric.trend || '+0.0%' }}</span>
          </div>
        </div>

        <div class="mt-6 flex-1">
          <div class="flex h-full min-h-[220px] items-end rounded-xl border border-emerald-100/70 bg-gradient-to-t from-emerald-50 to-white/40 px-4 py-5">
            <div
              v-for="(height, index) in barChartDefs.paidOrder"
              :key="`paid-bar-${index}`"
              class="flex-1 rounded-t-md bg-gradient-to-t from-emerald-500/70 to-emerald-300/45"
              :style="{ height: `${height}%` }"
            ></div>
          </div>
        </div>
      </div>

      <div class="flex min-h-[340px] flex-col rounded-2xl border border-gray-200/80 bg-white p-6 shadow-sm xl:min-h-[360px]">
        <div class="card-header">
          <div>
            <p class="card-kicker">{{ disputeMetric.title }}</p>
            <p class="mt-3 text-3xl font-bold tracking-tight text-gray-900 font-numeric">{{ disputeMetric.value || 0 }}</p>
            <p class="mt-2 text-xs text-gray-500">{{ disputeMetric.subtext || '暂无更多说明' }}</p>
          </div>

          <div class="status-chip status-chip-danger">
            <ArrowDownRight class="h-3 w-3" />
            <span class="font-numeric">{{ disputeMetric.trend || '-0.0%' }}</span>
          </div>
        </div>

        <div class="mt-6 flex-1">
          <div class="flex h-full min-h-[220px] items-end rounded-xl border border-red-100/70 bg-gradient-to-t from-red-50 to-white/40 px-4 py-5">
            <div
              v-for="(height, index) in barChartDefs.dispute"
              :key="`dispute-bar-${index}`"
              class="flex-1 rounded-t-md bg-gradient-to-t from-red-500/70 to-red-300/45"
              :style="{ height: `${height}%` }"
            ></div>
          </div>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-6 xl:grid-cols-3">
      <div class="space-y-6 xl:col-span-2">
        <div class="overflow-hidden rounded-2xl border border-red-200/60 bg-white shadow-sm">
          <div class="border-b border-red-100/60 bg-red-50/40 p-5">
            <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
              <div>
                <div class="section-title-row">
                  <AlertOctagon class="h-4.5 w-4.5 text-red-500" />
                  <h2 class="section-title">{{ disputeQueueTitle }}</h2>
                </div>
                <p class="section-desc">{{ disputeQueueDesc }}</p>
              </div>

              <button @click="router.push('/audit')" class="btn-primary">进入处理中心</button>
            </div>
          </div>

          <div v-if="!hasDisputeQueue" class="p-6">
            <div class="empty-state empty-state-danger">
              <AlertOctagon class="empty-state-icon text-red-400" />
              <p class="empty-state-title">{{ disputeQueueEmptyTitle }}</p>
              <p class="empty-state-text">{{ disputeQueueEmptyText }}</p>
            </div>
          </div>

          <div v-else class="divide-y divide-gray-100/80">
            <div
              v-for="dsp in disputeQueue"
              :key="dsp.id"
              class="group flex flex-col justify-between gap-4 p-5 transition-colors hover:bg-gray-50/80 sm:flex-row sm:items-center"
            >
              <div class="flex flex-col">
                <div class="mb-2 flex flex-wrap items-center gap-3">
                  <span class="status-chip status-chip-muted font-numeric uppercase leading-none">{{ dsp.id }}</span>
                  <span class="text-[14px] font-medium text-gray-900">{{ dsp.reason }}</span>
                  <span v-if="dsp.level === '紧急'" class="status-chip status-chip-danger">临近处理时限</span>
                </div>
                <div class="flex items-center gap-2.5 text-[13px] text-gray-500">
                  <MessageSquareWarning class="h-4 w-4 text-gray-400" />
                  <span class="cursor-pointer hover:text-gray-700">{{ dsp.user }}</span>
                  <span class="h-3.5 w-px bg-gray-300"></span>
                  <span class="text-gray-500">{{ dsp.target }}</span>
                </div>
              </div>

              <button
                @click="router.push('/audit')"
                class="btn-primary w-full transition-opacity sm:w-auto sm:opacity-0 sm:group-hover:opacity-100 sm:focus:opacity-100"
              >
                立即处理
              </button>
            </div>
          </div>
        </div>

        <div class="overflow-hidden rounded-2xl border border-gray-200/80 bg-white shadow-sm">
          <div class="border-b border-gray-100 p-5">
            <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
              <div>
                <h2 class="section-title">待审核商品</h2>
                <p class="section-desc">按风险等级与提交时间排序</p>
              </div>

              <button @click="router.push('/products')" class="btn-default">查看全部</button>
            </div>
          </div>

          <div v-if="!hasReviewQueue" class="p-6">
            <div class="empty-state empty-state-neutral">
              <Clock class="empty-state-icon" />
              <p class="empty-state-title">当前没有待审核商品</p>
              <p class="empty-state-text">新的审核任务会在这里同步展示。</p>
            </div>
          </div>

          <div v-else class="overflow-x-auto">
            <table class="table-base">
              <thead>
                <tr class="table-head-row">
                  <th class="table-head-cell">排队任务</th>
                  <th class="table-head-cell">发布信息</th>
                  <th class="table-head-cell">标价</th>
                  <th class="table-head-cell">审核分类</th>
                  <th class="table-head-cell text-right">操作</th>
                </tr>
              </thead>
              <tbody class="table-body">
                <tr v-for="item in reviewQueue" :key="item.id" class="table-row group">
                  <td class="table-cell">
                    <div class="flex items-center gap-2 font-medium text-gray-900 font-numeric">
                      {{ item.id }}
                      <span v-if="item.risk === '高风险'" class="h-2 w-2 rounded-full bg-red-500"></span>
                    </div>
                    <div class="mt-1.5 flex items-center gap-1.5 text-[12px] text-gray-400">
                      <Clock class="h-3.5 w-3.5" />
                      {{ item.time }}提交
                    </div>
                  </td>
                  <td class="table-cell">
                    <div class="max-w-[220px] truncate font-medium text-gray-900" :title="item.item">{{ item.item }}</div>
                    <div class="mt-1.5 flex items-center gap-2 text-[12px] text-gray-500">
                      <span class="status-chip status-chip-muted">卖家</span>
                      <span class="cursor-pointer hover:text-gray-800">{{ item.sellerName }}</span>
                    </div>
                  </td>
                  <td class="table-cell font-medium text-gray-900 font-numeric">{{ item.price }}</td>
                  <td class="table-cell">
                    <span
                      class="status-chip"
                      :class="{
                        'status-chip-danger': item.risk === '高风险',
                        'status-chip-warning': item.risk === '中风险',
                        'status-chip-muted': item.risk === '正常',
                      }"
                    >
                      {{ item.risk }}
                    </span>
                  </td>
                  <td class="table-cell w-28 text-right">
                    <button
                      @click="router.push('/products')"
                      class="btn-default w-full transition-opacity sm:w-auto sm:opacity-0 sm:group-hover:opacity-100 sm:focus:opacity-100"
                    >
                      审阅
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div class="space-y-6">
        <div class="state-banner state-banner-info">
          <div class="section-title-row mb-3.5">
            <Info class="h-4.5 w-4.5 text-blue-600" />
            <h3 class="text-sm font-bold text-blue-900">今日运营建议</h3>
          </div>

          <div class="space-y-3.5 text-[13px] leading-relaxed text-blue-800/80">
            <p>1. <strong>大促预热期：</strong>预计下午 14:00-16:00 迎来商品发布高峰，请提前调度审核人力。</p>
            <p>2. <strong>重点品类监控：</strong>数码 3C 类目近期出现多起“低价引流”违规，请在审核时重点关注价格偏离度。</p>
            <p>3. <strong>处理时效：</strong>本周纠纷处理平均时长略有上升，请优先处理临近时限的工单。</p>
          </div>
        </div>

        <div class="overflow-hidden rounded-2xl border border-gray-200/80 bg-white shadow-sm">
          <div class="border-b border-gray-100 bg-gray-50/60 p-5">
            <div class="section-title-row">
              <ShieldAlert class="h-4.5 w-4.5 text-gray-500" />
              <h2 class="section-title">实时风控关注</h2>
            </div>
          </div>

          <div v-if="!hasRiskAlerts" class="p-6">
            <div class="empty-state empty-state-neutral">
              <ShieldAlert class="empty-state-icon" />
              <p class="empty-state-title">当前暂无风控线索</p>
              <p class="empty-state-text">出现新的风险提醒后会展示在这里。</p>
            </div>
          </div>

          <div v-else class="space-y-5 p-5">
            <div
              v-for="alert in riskAlerts"
              :key="alert.id"
              class="relative flex items-start gap-3.5 pl-3.5 before:absolute before:bottom-0 before:left-0 before:top-2 before:w-0.5 before:bg-gray-200"
            >
              <div class="absolute left-[-3px] top-2 h-2 w-2 rounded-full border-2 border-gray-300 bg-white"></div>
              <div class="w-full">
                <div class="flex items-start justify-between gap-3">
                  <p class="text-[14px] font-medium leading-tight text-gray-900">{{ alert.type }}</p>
                  <span class="status-chip status-chip-muted">{{ alert.count }}</span>
                </div>
                <p class="mt-2 text-[13px] text-gray-500">{{ alert.target }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
