<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { AlertTriangle, Clock, Loader2, PlayCircle, RefreshCw, Wrench } from 'lucide-vue-next'
import {
  fetchOpsRuntimeBundle,
  getOpsRuntimeFailedSources,
  publishOutboxOnce,
  runRefundOnce,
  runShipReminderOnce,
  runShipTimeoutOnce,
  type BundleNotice,
  type OpsRuntimeAvailability,
} from '@/api/adminExtra'

/**
 * 运维中心把“概览快照”和“手动执行反馈”分开管理：
 * - runtime* 负责页面首次加载和刷新
 * - opsAction* 负责用户主动触发任务时的按钮状态与结果提示
 */
const runtimeLoading = ref(false)
const runtimeNotice = ref<BundleNotice | null>(null)
const lastUpdated = ref('')
const opsActionLoading = ref('')
type OpsActionFeedbackTone = 'info' | 'danger'

interface OpsActionFeedback {
  tone: OpsActionFeedbackTone
  title: string
  message: string
}

const opsActionFeedback = ref<OpsActionFeedback | null>(null)

/**
 * availability 用来区分两种完全不同的情况：
 * - 数值确实为 0
 * - 对应接口本次没有同步成功
 * 所以模板里看到 “—” 时，优先排查 source 是否可用，而不是把它当成 0。
 */
const defaultRuntimeAvailability: OpsRuntimeAvailability = {
  adminOrders: false,
  outboxMetrics: false,
  shipTimeoutTasks: false,
  refundTasks: false,
  shipReminderTasks: false,
  violationStatistics: false,
}
const runtimeAvailability = ref<OpsRuntimeAvailability>({ ...defaultRuntimeAvailability })

const runtimeSnapshot = ref({
  orderTotal: 0,
  outboxNew: 0,
  outboxFail: 0,
  outboxSent: 0,
  shipTimeoutTotal: 0,
  refundTotal: 0,
  shipReminderTotal: 0,
  topViolationType: '--',
  topViolationCount: 0,
})

type OpsActionKey = 'outbox' | 'ship-timeout' | 'refund' | 'ship-reminder'

/**
 * 页面层统一提取动作失败文案：
 * - API 继续只负责抛错
 * - 页面只关心怎么稳定地给运维同学展示结果
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

const formatCount = (value: number) => value.toLocaleString('zh-CN')
const isRuntimeSourceAvailable = (source: keyof OpsRuntimeAvailability) => runtimeAvailability.value[source]
const formatRuntimeMetric = (value: number, source: keyof OpsRuntimeAvailability) =>
  isRuntimeSourceAvailable(source) ? formatCount(value) : '—'

const runtimeUnavailableSources = computed(() => getOpsRuntimeFailedSources(runtimeAvailability.value))

const hasAnyRuntimeSuccess = computed(() => Object.values(runtimeAvailability.value).some(Boolean))

const pendingWorkTotal = computed(
  () =>
    runtimeSnapshot.value.outboxNew +
    runtimeSnapshot.value.outboxFail +
    runtimeSnapshot.value.shipTimeoutTotal +
    runtimeSnapshot.value.refundTotal +
    runtimeSnapshot.value.shipReminderTotal,
)

const runtimeStatusText = computed(() => {
  if (runtimeLoading.value && !lastUpdated.value) return '正在同步运行数据'
  if (runtimeUnavailableSources.value.length > 0) {
    return hasAnyRuntimeSuccess.value
      ? `运行概览待补齐 ${formatCount(runtimeUnavailableSources.value.length)} 项`
      : '运行概览待刷新'
  }
  if (runtimeSnapshot.value.outboxFail > 0) return `失败消息 ${formatCount(runtimeSnapshot.value.outboxFail)} 条待处理`
  if (pendingWorkTotal.value > 0) return `当前待处理 ${formatCount(pendingWorkTotal.value)} 项`
  return '当前运行平稳'
})

const priorityFocus = computed(() => {
  if (runtimeUnavailableSources.value.length > 0) {
    return hasAnyRuntimeSuccess.value ? '优先补齐运行概览' : '等待运行概览首次同步'
  }
  if (runtimeSnapshot.value.outboxFail > 0) return '优先处理失败消息'
  if (runtimeSnapshot.value.shipTimeoutTotal > 0) return '优先处理发货超时'
  if (runtimeSnapshot.value.refundTotal > 0) return '关注退款任务积压'
  if (runtimeSnapshot.value.shipReminderTotal > 0) return '关注发货提醒队列'
  return '当前无积压'
})

const runtimeSyncBadgeText = computed(() => {
  if (runtimeLoading.value) return '同步中'
  if (runtimeUnavailableSources.value.length > 0) return hasAnyRuntimeSuccess.value ? '部分同步' : '待刷新'
  return '已刷新'
})

const runtimeErrorTone = computed(() =>
  runtimeNotice.value?.tone === 'danger' ? 'state-banner-danger' : 'state-banner-warning',
)

const runtimeErrorTitle = computed(() => runtimeNotice.value?.title || '运行概览提示')
const runtimeErrorMessage = computed(() => runtimeNotice.value?.message || '')

const topViolationText = computed(() =>
  isRuntimeSourceAvailable('violationStatistics') ? runtimeSnapshot.value.topViolationType : '待刷新',
)

const topViolationCountText = computed(() =>
  isRuntimeSourceAvailable('violationStatistics') ? formatCount(runtimeSnapshot.value.topViolationCount) : '—',
)

/**
 * 所有概览卡片都由 snapshot + availability 派生，模板只负责渲染。
 * review 想调整状态色、提示文案或优先级时，优先修改这一层映射。
 */
const overviewCards = computed(() => [
  {
    key: 'ship-timeout',
    label: '发货超时任务',
    value: formatRuntimeMetric(runtimeSnapshot.value.shipTimeoutTotal, 'shipTimeoutTasks'),
    hint: !isRuntimeSourceAvailable('shipTimeoutTasks')
      ? '运行概览待刷新'
      : runtimeSnapshot.value.shipTimeoutTotal > 0
        ? '建议优先处理积压订单'
        : '当前暂无积压',
    statusLabel: !isRuntimeSourceAvailable('shipTimeoutTasks')
      ? '待刷新'
      : runtimeSnapshot.value.shipTimeoutTotal > 0
        ? '待处理'
        : '稳定',
    statusClass:
      !isRuntimeSourceAvailable('shipTimeoutTasks')
        ? 'status-chip-neutral'
        : runtimeSnapshot.value.shipTimeoutTotal > 0
        ? 'status-chip-warning'
        : 'status-chip-success',
    panelClass:
      runtimeSnapshot.value.shipTimeoutTotal > 0 && isRuntimeSourceAvailable('shipTimeoutTasks')
        ? 'from-orange-50/80 via-white to-white'
        : 'from-gray-50 to-white',
  },
  {
    key: 'refund',
    label: '退款任务',
    value: formatRuntimeMetric(runtimeSnapshot.value.refundTotal, 'refundTasks'),
    hint: !isRuntimeSourceAvailable('refundTasks')
      ? '运行概览待刷新'
      : runtimeSnapshot.value.refundTotal > 0
        ? '建议尽快跟进售后队列'
        : '当前暂无退款积压',
    statusLabel: !isRuntimeSourceAvailable('refundTasks')
      ? '待刷新'
      : runtimeSnapshot.value.refundTotal > 0
        ? '待处理'
        : '稳定',
    statusClass:
      !isRuntimeSourceAvailable('refundTasks')
        ? 'status-chip-neutral'
        : runtimeSnapshot.value.refundTotal > 0
        ? 'status-chip-danger'
        : 'status-chip-success',
    panelClass:
      runtimeSnapshot.value.refundTotal > 0 && isRuntimeSourceAvailable('refundTasks')
        ? 'from-red-50/80 via-white to-white'
        : 'from-gray-50 to-white',
  },
  {
    key: 'ship-reminder',
    label: '发货提醒任务',
    value: formatRuntimeMetric(runtimeSnapshot.value.shipReminderTotal, 'shipReminderTasks'),
    hint: !isRuntimeSourceAvailable('shipReminderTasks')
      ? '运行概览待刷新'
      : runtimeSnapshot.value.shipReminderTotal > 0
        ? '提醒队列待继续触达'
        : '当前提醒队列空闲',
    statusLabel: !isRuntimeSourceAvailable('shipReminderTasks')
      ? '待刷新'
      : runtimeSnapshot.value.shipReminderTotal > 0
        ? '待处理'
        : '稳定',
    statusClass:
      !isRuntimeSourceAvailable('shipReminderTasks')
        ? 'status-chip-neutral'
        : runtimeSnapshot.value.shipReminderTotal > 0
        ? 'status-chip-success'
        : 'status-chip-neutral',
    panelClass:
      runtimeSnapshot.value.shipReminderTotal > 0 && isRuntimeSourceAvailable('shipReminderTasks')
        ? 'from-emerald-50/80 via-white to-white'
        : 'from-gray-50 to-white',
  },
  {
    key: 'outbox-sent',
    label: '消息已发送',
    value: formatRuntimeMetric(runtimeSnapshot.value.outboxSent, 'outboxMetrics'),
    hint: !isRuntimeSourceAvailable('outboxMetrics')
      ? '运行概览待刷新'
      : runtimeSnapshot.value.outboxSent > 0
        ? '消息链路持续输出中'
        : '等待新的发送结果',
    statusLabel: !isRuntimeSourceAvailable('outboxMetrics')
      ? '待刷新'
      : runtimeSnapshot.value.outboxSent > 0
        ? '已输出'
        : '空闲',
    statusClass:
      !isRuntimeSourceAvailable('outboxMetrics')
        ? 'status-chip-neutral'
        : runtimeSnapshot.value.outboxSent > 0
        ? 'status-chip-info'
        : 'status-chip-neutral',
    panelClass:
      runtimeSnapshot.value.outboxSent > 0 && isRuntimeSourceAvailable('outboxMetrics')
        ? 'from-blue-50/80 via-white to-white'
        : 'from-gray-50 to-white',
  },
])

// 这里只把已有运行态数据重新组织成页面展示卡片，不改变接口或字段口径。
const opsActionCards = computed(() => [
  {
    key: 'outbox' as const,
    title: '消息补发',
    subtitle: 'Outbox 发布一次',
    description: '手动触发一次消息发送，用于处理通知补发与消息积压。',
    pendingLabel: '待发送消息',
    pendingValue: runtimeSnapshot.value.outboxNew,
    pendingValueText: formatRuntimeMetric(runtimeSnapshot.value.outboxNew, 'outboxMetrics'),
    metaText:
      !isRuntimeSourceAvailable('outboxMetrics')
        ? '运行概览待刷新，执行前请先确认当前消息状态'
        : runtimeSnapshot.value.outboxFail > 0
        ? `失败消息 ${formatCount(runtimeSnapshot.value.outboxFail)} 条，建议优先处理`
        : '当前消息链路稳定，可按需执行补发',
    accentClass: 'border-blue-200 bg-blue-50/80 text-blue-700',
    statusLabel: !isRuntimeSourceAvailable('outboxMetrics')
      ? '待刷新'
      : runtimeSnapshot.value.outboxFail > 0
        ? '优先关注'
        : runtimeSnapshot.value.outboxNew > 0
          ? '可执行'
          : '稳定',
    statusClass:
      !isRuntimeSourceAvailable('outboxMetrics')
        ? 'status-chip-neutral'
        : runtimeSnapshot.value.outboxFail > 0
        ? 'status-chip-danger'
        : runtimeSnapshot.value.outboxNew > 0
          ? 'status-chip-info'
          : 'status-chip-neutral',
    buttonTone:
      isRuntimeSourceAvailable('outboxMetrics') &&
      (runtimeSnapshot.value.outboxFail > 0 || runtimeSnapshot.value.outboxNew > 0)
        ? 'primary'
        : 'default',
    buttonLabel:
      isRuntimeSourceAvailable('outboxMetrics') && runtimeSnapshot.value.outboxFail > 0 ? '立即补发' : '手动执行',
  },
  {
    key: 'ship-timeout' as const,
    title: '超时发货处理',
    subtitle: '发货超时任务',
    description: '补跑发货超时扫描，优先处理积压时效单。',
    pendingLabel: '待处理订单',
    pendingValue: runtimeSnapshot.value.shipTimeoutTotal,
    pendingValueText: formatRuntimeMetric(runtimeSnapshot.value.shipTimeoutTotal, 'shipTimeoutTasks'),
    metaText: !isRuntimeSourceAvailable('shipTimeoutTasks')
      ? '运行概览待刷新，执行前请先确认当前任务状态'
      : runtimeSnapshot.value.shipTimeoutTotal > 0
        ? '建议在低峰期安排清理'
        : '当前暂无超时订单积压',
    accentClass: 'border-orange-200 bg-orange-50/80 text-orange-700',
    statusLabel: !isRuntimeSourceAvailable('shipTimeoutTasks')
      ? '待刷新'
      : runtimeSnapshot.value.shipTimeoutTotal > 0
        ? '待处理'
        : '稳定',
    statusClass:
      !isRuntimeSourceAvailable('shipTimeoutTasks')
        ? 'status-chip-neutral'
        : runtimeSnapshot.value.shipTimeoutTotal > 0
        ? 'status-chip-warning'
        : 'status-chip-neutral',
    buttonTone:
      isRuntimeSourceAvailable('shipTimeoutTasks') && runtimeSnapshot.value.shipTimeoutTotal > 0
        ? 'primary'
        : 'default',
    buttonLabel:
      isRuntimeSourceAvailable('shipTimeoutTasks') && runtimeSnapshot.value.shipTimeoutTotal > 0
        ? '立即处理'
        : '手动执行',
  },
  {
    key: 'refund' as const,
    title: '退款任务处理',
    subtitle: '退款任务',
    description: '触发退款侧任务批次，适合回归退款链路处理。',
    pendingLabel: '待处理工单',
    pendingValue: runtimeSnapshot.value.refundTotal,
    pendingValueText: formatRuntimeMetric(runtimeSnapshot.value.refundTotal, 'refundTasks'),
    metaText: !isRuntimeSourceAvailable('refundTasks')
      ? '运行概览待刷新，执行前请先确认当前任务状态'
      : runtimeSnapshot.value.refundTotal > 0
        ? '建议优先跟进售后积压'
        : '当前退款队列空闲',
    accentClass: 'border-red-200 bg-red-50/80 text-red-700',
    statusLabel: !isRuntimeSourceAvailable('refundTasks')
      ? '待刷新'
      : runtimeSnapshot.value.refundTotal > 0
        ? '待处理'
        : '稳定',
    statusClass:
      !isRuntimeSourceAvailable('refundTasks')
        ? 'status-chip-neutral'
        : runtimeSnapshot.value.refundTotal > 0
        ? 'status-chip-danger'
        : 'status-chip-neutral',
    buttonTone:
      isRuntimeSourceAvailable('refundTasks') && runtimeSnapshot.value.refundTotal > 0 ? 'primary' : 'default',
    buttonLabel:
      isRuntimeSourceAvailable('refundTasks') && runtimeSnapshot.value.refundTotal > 0
        ? '立即处理'
        : '手动执行',
  },
  {
    key: 'ship-reminder' as const,
    title: '提醒队列补跑',
    subtitle: '发货提醒任务',
    description: '补跑提醒任务，检查即将超时订单的通知触达。',
    pendingLabel: '待提醒订单',
    pendingValue: runtimeSnapshot.value.shipReminderTotal,
    pendingValueText: formatRuntimeMetric(runtimeSnapshot.value.shipReminderTotal, 'shipReminderTasks'),
    metaText: !isRuntimeSourceAvailable('shipReminderTasks')
      ? '运行概览待刷新，执行前请先确认当前任务状态'
      : runtimeSnapshot.value.shipReminderTotal > 0
        ? '可继续推进发货提醒触达'
        : '当前提醒队列空闲',
    accentClass: 'border-emerald-200 bg-emerald-50/80 text-emerald-700',
    statusLabel: !isRuntimeSourceAvailable('shipReminderTasks')
      ? '待刷新'
      : runtimeSnapshot.value.shipReminderTotal > 0
        ? '待处理'
        : '稳定',
    statusClass:
      !isRuntimeSourceAvailable('shipReminderTasks')
        ? 'status-chip-neutral'
        : runtimeSnapshot.value.shipReminderTotal > 0
        ? 'status-chip-success'
        : 'status-chip-neutral',
    buttonTone:
      isRuntimeSourceAvailable('shipReminderTasks') && runtimeSnapshot.value.shipReminderTotal > 0
        ? 'primary'
        : 'default',
    buttonLabel:
      isRuntimeSourceAvailable('shipReminderTasks') && runtimeSnapshot.value.shipReminderTotal > 0
        ? '立即处理'
        : '手动执行',
  },
])

const opsActionFeedbackTone = computed(() =>
  opsActionFeedback.value?.tone === 'danger'
    ? 'state-banner-danger'
    : 'state-banner-info',
)

/**
 * 页面只消费 API 层聚合好的 runtime bundle：
 * API 已经负责把多接口结果压成 snapshot / availability / failedSources，
 * 页面这里只负责决定“部分成功”时如何展示。
 */
const refreshRuntimeData = async () => {
  try {
    runtimeLoading.value = true
    runtimeNotice.value = null
    const runtimeBundle = await fetchOpsRuntimeBundle()

    runtimeAvailability.value = runtimeBundle.availability
    runtimeSnapshot.value = runtimeBundle.snapshot

    if (runtimeBundle.hasAnySuccess) {
      lastUpdated.value = new Date().toLocaleString('zh-CN', { hour12: false })
    }

    if (runtimeBundle.notice) {
      runtimeNotice.value = runtimeBundle.notice
    }
  } catch (error: any) {
    runtimeAvailability.value = { ...defaultRuntimeAvailability }
    runtimeNotice.value = {
      tone: 'danger',
      title: '运行概览拉取失败',
      message: resolvePageErrorMessage(error, '运行概览拉取失败'),
    }
  } finally {
    runtimeLoading.value = false
  }
}

/**
 * 一键运维动作统一走同一个入口，保证：
 * - 同时只执行一个动作，按钮 loading 不会打架
 * - 成功/失败反馈文案结构一致
 * - 执行完成后立刻回刷最新概览
 */
const runOpsAction = async (action: OpsActionKey) => {
  if (!window.confirm('该操作会触发真实运维任务，确认继续？')) return

  try {
    opsActionLoading.value = action
    opsActionFeedback.value = null

    if (action === 'outbox') {
      const res = await publishOutboxOnce(50)
      opsActionFeedback.value = {
        tone: 'info',
        title: '执行结果',
        message: `消息补发已完成：成功发送 ${res.sent ?? 0} 条，发送失败 ${res.failed ?? 0} 条`,
      }
    } else if (action === 'ship-timeout') {
      const res = await runShipTimeoutOnce(50)
      opsActionFeedback.value = {
        tone: 'info',
        title: '执行结果',
        message: `发货超时处理已完成：本次共处理 ${res.success ?? 0} 条订单`,
      }
    } else if (action === 'refund') {
      const res = await runRefundOnce(50)
      opsActionFeedback.value = {
        tone: 'info',
        title: '执行结果',
        message: `退款处理已完成：本次共处理 ${res.success ?? 0} 条任务`,
      }
    } else {
      const res = await runShipReminderOnce(50)
      opsActionFeedback.value = {
        tone: 'info',
        title: '执行结果',
        message: `发货提醒已完成：本次共处理 ${res.success ?? 0} 条提醒`,
      }
    }

    await refreshRuntimeData()
  } catch (error: any) {
    opsActionFeedback.value = {
      tone: 'danger',
      title: '执行失败',
      message: resolvePageErrorMessage(error, '运维动作执行失败'),
    }
  } finally {
    opsActionLoading.value = ''
  }
}

onMounted(() => {
  refreshRuntimeData()
})
</script>

<template>
  <div class="space-y-8 max-w-[1600px] mx-auto pb-8">
    <div class="bg-white border border-gray-200/80 rounded-xl p-6 shadow-sm relative overflow-hidden">
      <div class="absolute top-0 right-0 w-64 h-full bg-gradient-to-l from-gray-50 to-transparent pointer-events-none"></div>
      <div class="relative z-10 flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <div class="mb-2 flex flex-wrap items-center gap-3">
            <h1 class="text-2xl font-bold text-gray-900 tracking-tight">运维中心</h1>
            <span class="status-chip status-chip-muted">任务调度</span>
            <span
              class="status-chip"
              :class="pendingWorkTotal ? 'status-chip-warning' : 'status-chip-success'"
            >
              {{ priorityFocus }}
            </span>
          </div>
          <p class="text-[14px] text-gray-500">统一查看运行指标并执行一次性运维动作。</p>
        </div>

        <div class="flex flex-wrap items-center gap-2">
          <span class="status-chip status-chip-muted">
            <Clock class="w-3.5 h-3.5 inline-block mr-1" />
            {{ lastUpdated ? `最近刷新 ${lastUpdated}` : '未刷新' }}
          </span>
          <span
            class="status-chip"
            :class="
              runtimeLoading
                ? 'status-chip-info'
                : runtimeUnavailableSources.length
                  ? 'status-chip-warning'
                  : pendingWorkTotal
                    ? 'status-chip-warning'
                    : 'status-chip-success'
            "
          >
            {{ runtimeStatusText }}
          </span>
          <button @click="refreshRuntimeData" class="btn-default btn-loading text-[12px] px-3 py-1.5" :disabled="runtimeLoading">
            <RefreshCw v-if="!runtimeLoading" class="w-3.5 h-3.5" />
            <Loader2 v-else class="btn-loading-icon h-3.5 w-3.5" />
            {{ runtimeLoading ? '刷新中...' : '刷新数据' }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="runtimeNotice" class="state-banner" :class="runtimeErrorTone">
      <div class="state-banner-body">
        <div class="state-banner-main">
          <span
            class="state-banner-icon"
            :class="hasAnyRuntimeSuccess ? 'border-orange-200' : 'border-red-200'"
          >
            <AlertTriangle
              class="w-4 h-4"
              :class="hasAnyRuntimeSuccess ? 'text-orange-600' : 'text-red-600'"
            />
          </span>
          <div>
            <p class="state-banner-title">{{ runtimeErrorTitle }}</p>
            <p
              class="state-banner-text"
              :class="hasAnyRuntimeSuccess ? 'text-orange-700/90' : 'text-red-700/90'"
            >
              {{ runtimeErrorMessage }}
            </p>
          </div>
        </div>
        <button class="btn-default shrink-0 text-[12px] px-3 py-1.5" @click="refreshRuntimeData" :disabled="runtimeLoading">
          重新刷新
        </button>
      </div>
    </div>

    <div class="bg-white border border-gray-200/80 rounded-xl p-6 shadow-sm">
      <div class="section-header">
        <div>
          <h2 class="section-title">运行概览</h2>
          <p class="section-desc">聚焦订单、Outbox 与任务积压，先看清当前运行情况。</p>
        </div>
        <div class="section-meta">
          {{ runtimeLoading ? '运行概览刷新中...' : runtimeStatusText }}
        </div>
      </div>

      <div class="grid grid-cols-1 gap-4 xl:grid-cols-5">
        <div class="xl:col-span-2 rounded-2xl bg-gray-900 p-5 text-white relative overflow-hidden">
          <div class="absolute inset-y-0 right-0 w-32 bg-gradient-to-l from-white/5 to-transparent pointer-events-none"></div>
          <div class="relative z-10 flex h-full flex-col justify-between gap-5">
            <div class="flex items-start justify-between gap-3">
              <div>
                <p class="text-[12px] uppercase tracking-[0.14em] text-gray-400">核心负载</p>
                <h3 class="mt-3 text-[15px] font-semibold">订单与消息队列</h3>
              </div>
              <span class="status-chip border-white/10 bg-white/10 text-gray-200">
                {{ runtimeSyncBadgeText }}
              </span>
            </div>

            <div>
              <p class="text-[12px] text-gray-400">订单总量</p>
              <p class="mt-2 text-4xl font-bold tracking-tight font-numeric">
                {{ formatRuntimeMetric(runtimeSnapshot.orderTotal, 'adminOrders') }}
              </p>
            </div>

            <div class="grid grid-cols-2 gap-3">
              <div class="rounded-xl border border-white/10 bg-white/5 p-4">
                <p class="text-[12px] text-gray-400">Outbox 待发送</p>
                <p class="mt-2 text-[22px] font-semibold font-numeric text-white">
                  {{ formatRuntimeMetric(runtimeSnapshot.outboxNew, 'outboxMetrics') }}
                </p>
              </div>
              <div class="rounded-xl border border-white/10 bg-white/5 p-4">
                <p class="text-[12px] text-gray-400">Outbox 失败</p>
                <p class="mt-2 text-[22px] font-semibold font-numeric text-white">
                  {{ formatRuntimeMetric(runtimeSnapshot.outboxFail, 'outboxMetrics') }}
                </p>
              </div>
            </div>

            <div class="rounded-xl border border-white/10 bg-white/5 px-4 py-3">
              <p class="text-[12px] text-gray-400">当前焦点</p>
              <p class="mt-2 text-[14px] font-medium text-white/90">{{ priorityFocus }}</p>
            </div>
          </div>
        </div>

        <div class="xl:col-span-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div
            v-for="card in overviewCards"
            :key="card.key"
            class="rounded-2xl border border-gray-200 bg-gradient-to-br p-4"
            :class="card.panelClass"
          >
            <div class="card-header">
              <p class="card-kicker">{{ card.label }}</p>
              <span class="status-chip" :class="card.statusClass">
                {{ card.statusLabel }}
              </span>
            </div>
            <p class="mt-4 text-[26px] font-semibold font-numeric text-gray-900">{{ card.value }}</p>
            <p class="mt-2 text-[12px] leading-5 text-gray-500">{{ card.hint }}</p>
          </div>
        </div>

        <div class="xl:col-span-5 rounded-2xl border border-gray-200 bg-gray-50/70 px-4 py-4">
          <div class="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div>
              <div class="flex flex-wrap items-center gap-2">
                <p class="card-kicker">违规类型 Top1</p>
                <span class="status-chip status-chip-neutral">风险关注</span>
              </div>
              <p class="mt-1 text-[15px] font-semibold text-gray-900">
                {{ topViolationText }}
                <span class="ml-1 font-numeric text-gray-500">({{ topViolationCountText }})</span>
              </p>
            </div>
            <p class="section-meta">最近刷新：{{ lastUpdated || '未刷新' }}</p>
          </div>
        </div>
      </div>
    </div>

    <div class="bg-white border border-gray-200/80 rounded-xl p-6 shadow-sm">
      <div class="section-header">
        <div>
          <h2 class="section-title">一次性运维动作</h2>
          <p class="section-desc">把高频手动动作整理成操作面板，减少临时排查时的决策成本。</p>
        </div>
        <div class="section-meta">
          {{
            opsActionLoading
              ? '当前有任务执行中...'
              : runtimeUnavailableSources.length
                ? `运行概览待补齐 ${formatCount(runtimeUnavailableSources.length)} 项`
                : pendingWorkTotal
                  ? `当前待处理 ${formatCount(pendingWorkTotal)} 项`
                  : '当前暂无需要手动处理的任务'
          }}
        </div>
      </div>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <div
          v-for="card in opsActionCards"
          :key="card.key"
          class="rounded-2xl border border-gray-200 bg-gradient-to-br from-white to-gray-50/80 p-4 shadow-sm"
        >
          <div class="card-header">
            <div class="rounded-lg border p-2.5" :class="card.accentClass">
              <PlayCircle class="w-4 h-4" />
            </div>
            <span
              class="status-chip"
              :class="opsActionLoading === card.key ? 'status-chip-dark' : 'status-chip-neutral'"
            >
              {{ opsActionLoading === card.key ? '执行中' : '人工触发' }}
            </span>
          </div>

          <p class="mt-4 text-[12px] text-gray-500">{{ card.subtitle }}</p>
          <h3 class="mt-4 text-[14px] font-semibold text-gray-900">{{ card.title }}</h3>
          <p class="mt-2 min-h-[40px] text-[12px] leading-5 text-gray-500">{{ card.description }}</p>

          <div class="mt-4 rounded-xl border border-gray-100 bg-gray-50/80 p-4">
            <div class="flex items-end justify-between gap-3">
              <div>
                <p class="text-[12px] text-gray-500">{{ card.pendingLabel }}</p>
                <p class="mt-2 text-[30px] font-semibold font-numeric text-gray-900">{{ card.pendingValueText }}</p>
              </div>
              <span class="status-chip" :class="card.statusClass">
                {{ card.statusLabel }}
              </span>
            </div>
            <p class="mt-2 text-[12px] leading-5 text-gray-500">{{ card.metaText }}</p>
          </div>

          <button
            :class="`${opsActionLoading === card.key || card.buttonTone === 'primary' ? 'btn-primary' : 'btn-default'} btn-loading mt-5 w-full justify-center`"
            :disabled="!!opsActionLoading"
            @click="runOpsAction(card.key)"
          >
            <PlayCircle v-if="opsActionLoading !== card.key" class="w-4 h-4" />
            <Loader2 v-else class="btn-loading-icon" />
            {{ opsActionLoading === card.key ? '执行中...' : card.buttonLabel }}
          </button>
        </div>
      </div>

      <div
        v-if="opsActionFeedback"
        class="state-banner mt-4"
        :class="opsActionFeedbackTone"
      >
        <div class="state-banner-main">
          <span
            class="state-banner-icon"
            :class="opsActionFeedback?.tone === 'danger' ? 'border-red-200' : 'border-blue-200'"
          >
            <AlertTriangle
              v-if="opsActionFeedback?.tone === 'danger'"
              class="h-4 w-4 text-red-600"
            />
            <PlayCircle
              v-else
              class="h-4 w-4 text-blue-600"
            />
          </span>
          <div>
            <p class="state-banner-title">{{ opsActionFeedback?.title }}</p>
            <p class="state-banner-text">{{ opsActionFeedback?.message }}</p>
          </div>
        </div>
      </div>
    </div>

    <div class="state-banner state-banner-warning">
      <div class="state-banner-body">
        <div class="state-banner-main">
          <span class="state-banner-icon border-orange-200">
            <Wrench class="w-4 h-4 text-orange-600" />
          </span>
          <div>
            <p class="state-banner-title">执行前提醒</p>
            <p class="state-banner-text text-orange-700/90">运维动作会触发真实任务，请在低峰期并使用有权限账号执行。</p>
          </div>
        </div>
        <div class="flex flex-wrap gap-2">
          <span class="status-chip status-chip-neutral">低峰期执行</span>
          <span class="status-chip status-chip-neutral">使用权限账号</span>
        </div>
      </div>
    </div>
  </div>
</template>
