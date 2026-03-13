<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import {
  AlertOctagon,
  AlertTriangle,
  FileText,
  Info,
  Loader2,
  Search,
  ShieldAlert,
} from 'lucide-vue-next'
import { getAuditOverview, submitAuditAction, type AuditStats, type AuditTicketItem } from '@/api/audit'

/**
 * 页面基础状态。
 * 当前调整只做样式精修，不改变现有联调链路。
 */
const loading = ref(false)
const isDetailModalOpen = ref(false)
const isProcessModalOpen = ref(false)
const currentTicket = ref<AuditTicketItem | null>(null)
const processTargetTicket = ref<AuditTicketItem | null>(null)
const summary = ref<AuditStats>({
  pendingDisputes: 0,
  urgentReports: 0,
  platformIntervention: 0,
  todayNewClues: 0,
})

/**
 * 筛选条件。
 */
const searchQuery = ref('')
const selectedType = ref('ALL')
const selectedStatus = ref('ALL')
const selectedRisk = ref('ALL')

/**
 * 列表与处理状态。
 */
const tickets = ref<AuditTicketItem[]>([])
const processSubmitting = ref(false)
const processDecision = ref<'approve' | 'reject'>('approve')
const processReportAction = ref<'dismiss' | 'force_off_shelf'>('dismiss')
const processRemark = ref('')
const processError = ref('')

/**
 * 拉取总览数据。
 */
const fetchData = async () => {
  try {
    loading.value = true
    const res = await getAuditOverview({
      keyword: searchQuery.value || undefined,
      type: selectedType.value === 'ALL' ? undefined : selectedType.value,
      status: selectedStatus.value === 'ALL' ? undefined : selectedStatus.value,
      riskLevel: selectedRisk.value === 'ALL' ? undefined : selectedRisk.value,
    })

    summary.value = res?.stats || {
      pendingDisputes: 0,
      urgentReports: 0,
      platformIntervention: 0,
      todayNewClues: 0,
    }
    tickets.value = res?.tickets || []
  } catch (error) {
    console.warn('Audit overview request failed.', error)
    summary.value = {
      pendingDisputes: 0,
      urgentReports: 0,
      platformIntervention: 0,
      todayNewClues: 0,
    }
    tickets.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 监听筛选变化，避免高频请求。
 */
let fetchTimer: ReturnType<typeof setTimeout> | undefined
watch([searchQuery, selectedType, selectedStatus, selectedRisk], () => {
  window.clearTimeout(fetchTimer)
  fetchTimer = window.setTimeout(() => {
    fetchData()
  }, 300)
})

onMounted(() => {
  fetchData()
})

/**
 * 详情与处理弹窗。
 */
const openDetail = (ticket: AuditTicketItem) => {
  currentTicket.value = ticket
  isDetailModalOpen.value = true
}

const openProcessModal = (ticket: AuditTicketItem) => {
  processTargetTicket.value = ticket
  processDecision.value = 'approve'
  processReportAction.value = 'dismiss'
  processRemark.value = ''
  processError.value = ''
  isProcessModalOpen.value = true
}

const closeProcessModal = () => {
  if (processSubmitting.value) return
  isProcessModalOpen.value = false
}

/**
 * 提交处理动作。
 */
const handleProcess = async () => {
  if (!processTargetTicket.value) return

  try {
    processSubmitting.value = true
    processError.value = ''

    const ticket = processTargetTicket.value

    await submitAuditAction(ticket, {
      approved: ticket.type === 'DISPUTE' ? processDecision.value === 'approve' : undefined,
      action: ticket.type === 'REPORT' ? processReportAction.value : undefined,
      remark: processRemark.value,
    })

    isProcessModalOpen.value = false
    if (currentTicket.value?.id === ticket.id) {
      isDetailModalOpen.value = false
    }
    await fetchData()
  } catch (error: any) {
    console.warn('Process ticket failed.', error)
    processError.value = error?.message || '提交失败，请稍后重试'
  } finally {
    processSubmitting.value = false
  }
}

/**
 * 时间与标签辅助函数。
 */
const formatDateTime = (value?: string) => {
  if (!value) return '--'
  return value.replace('T', ' ').slice(0, 19)
}

const getTypeLabel = (type: string) => {
  switch (type) {
    case 'DISPUTE':
      return '交易纠纷'
    case 'REPORT':
      return '违规举报'
    case 'RISK':
      return '风控线索'
    default:
      return '未知'
  }
}

const getTypeBadgeClass = (type: string) => {
  switch (type) {
    case 'DISPUTE':
      return 'bg-blue-50 text-blue-700 border-blue-200'
    case 'REPORT':
      return 'bg-orange-50 text-orange-700 border-orange-200'
    case 'RISK':
      return 'bg-slate-100 text-slate-700 border-slate-200'
    default:
      return 'bg-gray-50 text-gray-700 border-gray-200'
  }
}

const getRiskLabel = (level: string) => {
  switch (level) {
    case 'HIGH':
      return '高风险'
    case 'MEDIUM':
      return '中风险'
    case 'LOW':
      return '低风险'
    default:
      return '未知'
  }
}

const getRiskBadgeClass = (level: string) => {
  switch (level) {
    case 'HIGH':
      return 'bg-red-50 text-red-700 border-red-200'
    case 'MEDIUM':
      return 'bg-amber-50 text-amber-700 border-amber-200'
    case 'LOW':
      return 'bg-emerald-50 text-emerald-700 border-emerald-200'
    default:
      return 'bg-gray-50 text-gray-700 border-gray-200'
  }
}

const getStatusLabel = (status: string) => {
  switch (status) {
    case 'PENDING':
      return '待处理'
    case 'PROCESSING':
      return '处理中'
    case 'CLOSED':
      return '已关闭'
    default:
      return '未知'
  }
}

const getStatusBadgeClass = (status: string) => {
  switch (status) {
    case 'PENDING':
      return 'bg-rose-50 text-rose-700 border-rose-200'
    case 'PROCESSING':
      return 'bg-blue-50 text-blue-700 border-blue-200'
    case 'CLOSED':
      return 'bg-gray-50 text-gray-700 border-gray-200'
    default:
      return 'bg-gray-50 text-gray-700 border-gray-200'
  }
}

const canProcessTicket = (ticket: AuditTicketItem) => {
  if (ticket.status === 'CLOSED') return false

  if (ticket.type === 'DISPUTE') {
    return Boolean(ticket.sourceId)
  }

  if (ticket.type === 'REPORT') {
    return Boolean(ticket.id)
  }

  return false
}

const getProcessBlockedReason = (ticket: AuditTicketItem) => {
  if (ticket.status === 'CLOSED') return '当前工单已关闭'
  if (ticket.type === 'DISPUTE' && !ticket.sourceId) return '缺少 sourceId，暂不可处理'
  if (ticket.type === 'REPORT' && !ticket.id) return '缺少 ticketNo，暂不可处理'
  if (ticket.type === 'RISK') return '风险线索暂不支持处理动作'
  return ''
}

const getProcessTitle = (ticket?: AuditTicketItem | null) => {
  if (!ticket) return '处理工单'
  if (ticket.type === 'DISPUTE') return '处理交易纠纷'
  if (ticket.type === 'REPORT') return '处理违规举报'
  return '处理工单'
}

const getProcessDescription = (ticket?: AuditTicketItem | null) => {
  if (!ticket) return '提交处理结果后，页面列表将自动刷新。'
  if (ticket.type === 'DISPUTE') return '平台裁决会同步写入售后处理结果，并更新当前工单状态。'
  if (ticket.type === 'REPORT') return '举报处理会写入工单结论，必要时联动商品强制下架。'
  return '提交处理结果后，页面列表将自动刷新。'
}

const getProcessConfirmText = (ticket?: AuditTicketItem | null) => {
  if (!ticket) return '确认提交'
  if (ticket.type === 'DISPUTE') return '确认提交裁决'
  if (ticket.type === 'REPORT') return '确认提交处理'
  return '确认提交'
}
</script>

<template>
  <div class="mx-auto max-w-[1600px] space-y-6">
    <section class="relative overflow-hidden rounded-xl border border-gray-200/80 bg-white p-6 shadow-sm">
      <div class="pointer-events-none absolute inset-y-0 right-0 w-80 bg-gradient-to-l from-red-50/70 via-orange-50/40 to-transparent"></div>

      <div class="relative z-10 flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
        <div class="space-y-3">
          <div class="flex flex-wrap items-center gap-2.5">
            <h1 class="text-2xl font-bold tracking-tight text-gray-900">纠纷与违规</h1>
            <span class="badge border-blue-200 bg-blue-50 text-blue-700">平台介入工作台</span>
            <span class="badge border-gray-200 bg-gray-50 text-gray-600">聚焦售后争议与违规处置</span>
          </div>

          <p class="max-w-3xl text-[14px] leading-relaxed text-gray-500">
            集中处理平台纠纷工单、违规举报与高风险线索，优先识别高风险工单、缩短处置链路，并为后续运营复盘保留清晰的处理依据。
          </p>

          <div class="flex flex-wrap items-center gap-4 text-[12px] text-gray-500">
            <span class="flex items-center gap-1.5">
              <span class="h-1.5 w-1.5 rounded-full bg-green-500"></span>
              核心链路正常
            </span>
            <span class="h-3 w-px bg-gray-300"></span>
            <span>当前焦点：高风险举报与平台强介入纠纷</span>
            <span class="h-3 w-px bg-gray-300"></span>
            <span>处置节奏：高风险优先，普通工单顺序推进</span>
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-3">
          <div class="rounded-lg border border-rose-100 bg-rose-50 px-3 py-2 text-[12px] text-rose-700">
            今日重点：优先闭环高风险工单，避免处理超时
          </div>
          <div class="rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 text-[12px] text-gray-600">
            数据来源：后台聚合总览
          </div>
        </div>
      </div>
    </section>

    <section class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
      <article class="rounded-xl border border-rose-200/70 bg-gradient-to-br from-rose-50 to-white p-5 shadow-sm">
        <div class="mb-4 flex items-center justify-between">
          <span class="inline-flex rounded-lg bg-rose-100 p-2 text-rose-700">
            <AlertOctagon class="h-5 w-5" />
          </span>
          <span class="text-[11px] font-medium text-rose-600">待优先介入</span>
        </div>
        <p class="text-[13px] text-gray-500">待处理纠纷</p>
        <p class="mt-2 font-numeric text-3xl font-bold text-gray-900">{{ summary.pendingDisputes }}</p>
        <p class="mt-3 text-[12px] leading-5 text-gray-500">平台需要继续推进双方协商、裁决或结果确认的争议工单。</p>
      </article>

      <article class="rounded-xl border border-orange-200/70 bg-gradient-to-br from-orange-50 to-white p-5 shadow-sm">
        <div class="mb-4 flex items-center justify-between">
          <span class="inline-flex rounded-lg bg-orange-100 p-2 text-orange-700">
            <AlertTriangle class="h-5 w-5" />
          </span>
          <span class="text-[11px] font-medium text-orange-600">需尽快判断</span>
        </div>
        <p class="text-[13px] text-gray-500">紧急举报</p>
        <p class="mt-2 font-numeric text-3xl font-bold text-gray-900">{{ summary.urgentReports }}</p>
        <p class="mt-3 text-[12px] leading-5 text-gray-500">涉及违规售卖、异常描述或潜在欺诈的举报，需要优先判定是否下架处置。</p>
      </article>

      <article class="rounded-xl border border-blue-200/70 bg-gradient-to-br from-blue-50 to-white p-5 shadow-sm">
        <div class="mb-4 flex items-center justify-between">
          <span class="inline-flex rounded-lg bg-blue-100 p-2 text-blue-700">
            <ShieldAlert class="h-5 w-5" />
          </span>
          <span class="text-[11px] font-medium text-blue-600">平台裁决中</span>
        </div>
        <p class="text-[13px] text-gray-500">平台强介入</p>
        <p class="mt-2 font-numeric text-3xl font-bold text-gray-900">{{ summary.platformIntervention }}</p>
        <p class="mt-3 text-[12px] leading-5 text-gray-500">当前由平台直接推动结论输出的工单量，反映人工介入压力。</p>
      </article>

      <article class="rounded-xl border border-slate-200/70 bg-gradient-to-br from-slate-50 to-white p-5 shadow-sm">
        <div class="mb-4 flex items-center justify-between">
          <span class="inline-flex rounded-lg bg-slate-100 p-2 text-slate-700">
            <FileText class="h-5 w-5" />
          </span>
          <span class="text-[11px] font-medium text-slate-600">新增线索</span>
        </div>
        <p class="text-[13px] text-gray-500">今日新增线索</p>
        <p class="mt-2 font-numeric text-3xl font-bold text-gray-900">{{ summary.todayNewClues }}</p>
        <p class="mt-3 text-[12px] leading-5 text-gray-500">当天进入风险视野的新工单与举报，为后续排查和分流提供入口。</p>
      </article>
    </section>

    <div class="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
      <div class="space-y-4">
        <section class="rounded-xl border border-gray-200/80 bg-white p-4 shadow-sm">
          <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div class="flex flex-1 flex-wrap items-center gap-3">
              <div class="relative w-full sm:w-72">
                <Search class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
                <input
                  v-model="searchQuery"
                  type="text"
                  placeholder="搜索工单编号 / 标题 / 关联对象"
                  class="input-standard w-full !pl-9"
                />
              </div>

              <select v-model="selectedType" class="input-standard min-w-[138px] bg-white">
                <option value="ALL">全部类型</option>
                <option value="DISPUTE">交易纠纷</option>
                <option value="REPORT">违规举报</option>
                <option value="RISK">风控线索</option>
              </select>

              <select v-model="selectedStatus" class="input-standard min-w-[128px] bg-white">
                <option value="ALL">全部状态</option>
                <option value="PENDING">待处理</option>
                <option value="PROCESSING">处理中</option>
                <option value="CLOSED">已关闭</option>
              </select>

              <select v-model="selectedRisk" class="input-standard min-w-[128px] bg-white">
                <option value="ALL">全部风险</option>
                <option value="HIGH">高风险</option>
                <option value="MEDIUM">中风险</option>
                <option value="LOW">低风险</option>
              </select>
            </div>

            <div class="text-[12px] text-gray-500">
              当前列表优先显示最近进入平台处理链路的工单
            </div>
          </div>
        </section>

        <section class="relative min-h-[480px] overflow-hidden rounded-xl border border-gray-200/80 bg-white shadow-sm">
          <div class="flex items-center justify-between border-b border-gray-100 bg-gray-50/60 px-5 py-4">
            <div>
              <h2 class="text-[15px] font-semibold text-gray-900">待处理工单队列</h2>
              <p class="mt-1 text-[12px] text-gray-500">按风险等级、工单类型与进入时间展示，便于运营逐条判断与推进。</p>
            </div>
            <div class="text-[12px] text-gray-500">
              当前共 <span class="font-numeric font-semibold text-gray-800">{{ tickets.length }}</span> 条记录
            </div>
          </div>

          <div v-if="loading" class="absolute inset-0 z-10 flex items-center justify-center bg-white/72 backdrop-blur-[1px]">
            <div class="flex items-center gap-2 rounded-lg border border-gray-200 bg-white px-4 py-2 text-[13px] text-gray-600 shadow-sm">
              <Loader2 class="h-4 w-4 animate-spin" />
              数据加载中...
            </div>
          </div>

          <div class="overflow-x-auto custom-scrollbar">
            <table class="w-full min-w-[1020px] border-collapse text-left whitespace-nowrap">
              <thead>
                <tr class="border-b border-gray-100 bg-gray-50/70 text-[12px] font-medium text-gray-500">
                  <th class="px-5 py-3">工单任务</th>
                  <th class="px-4 py-3">类型</th>
                  <th class="px-4 py-3">关联对象</th>
                  <th class="px-4 py-3">风险等级</th>
                  <th class="px-4 py-3">处理状态</th>
                  <th class="px-4 py-3">提交时间</th>
                  <th class="px-5 py-3 text-right">操作</th>
                </tr>
              </thead>

              <tbody class="divide-y divide-gray-100 text-[13px] text-gray-700">
                <tr
                  v-for="ticket in tickets"
                  :key="ticket.id"
                  class="group align-top transition-colors hover:bg-gray-50/70"
                >
                  <td class="px-5 py-4">
                    <div class="space-y-2">
                      <div class="flex items-start gap-2">
                        <span
                          :class="ticket.riskLevel === 'HIGH' ? 'bg-red-500' : ticket.riskLevel === 'MEDIUM' ? 'bg-amber-500' : 'bg-emerald-500'"
                          class="mt-1.5 inline-flex h-2 w-2 shrink-0 rounded-full"
                        ></span>
                        <div class="min-w-0">
                          <p class="truncate font-medium leading-6 text-gray-900" :title="ticket.title">{{ ticket.title }}</p>
                          <p class="mt-1 line-clamp-2 max-w-[380px] text-[12px] leading-5 text-gray-500">
                            {{ ticket.description || '当前工单暂无补充描述，可进入详情继续查看处理线索。' }}
                          </p>
                        </div>
                      </div>

                      <div class="flex flex-wrap items-center gap-2 text-[11px] text-gray-500">
                        <span class="rounded border border-gray-200 bg-gray-50 px-1.5 py-0.5 font-numeric">编号 {{ ticket.id }}</span>
                        <span
                          v-if="ticket.status === 'PENDING'"
                          class="rounded border border-rose-100 bg-rose-50 px-1.5 py-0.5 text-rose-700"
                        >
                          待人工处理
                        </span>
                        <span
                          v-if="ticket.status === 'PROCESSING'"
                          class="rounded border border-blue-100 bg-blue-50 px-1.5 py-0.5 text-blue-700"
                        >
                          已进入推进链路
                        </span>
                      </div>
                    </div>
                  </td>

                  <td class="px-4 py-4">
                    <span class="badge" :class="getTypeBadgeClass(ticket.type)">{{ getTypeLabel(ticket.type) }}</span>
                  </td>

                  <td class="px-4 py-4">
                    <div class="space-y-1">
                      <p class="font-medium text-gray-800">{{ ticket.target }}</p>
                      <p class="text-[11px] text-gray-500">当前关联主体</p>
                    </div>
                  </td>

                  <td class="px-4 py-4">
                    <span class="badge" :class="getRiskBadgeClass(ticket.riskLevel)">{{ getRiskLabel(ticket.riskLevel) }}</span>
                  </td>

                  <td class="px-4 py-4">
                    <span class="badge" :class="getStatusBadgeClass(ticket.status)">{{ getStatusLabel(ticket.status) }}</span>
                  </td>

                  <td class="px-4 py-4">
                    <div class="space-y-1">
                      <p class="font-numeric text-gray-700">{{ formatDateTime(ticket.createTime) }}</p>
                      <p class="text-[11px] text-gray-500">进入当前队列时间</p>
                    </div>
                  </td>

                  <td class="px-5 py-4 text-right">
                    <div class="flex items-center justify-end gap-3 opacity-0 transition-opacity group-hover:opacity-100">
                      <button @click="openDetail(ticket)" class="text-[12px] font-medium text-blue-700 hover:text-blue-800">
                        查看详情
                      </button>
                      <button
                        v-if="canProcessTicket(ticket)"
                        @click="openProcessModal(ticket)"
                        class="text-[12px] font-medium text-orange-700 hover:text-orange-800"
                      >
                        立即处理
                      </button>
                      <span
                        v-else-if="ticket.type === 'DISPUTE' || ticket.type === 'REPORT'"
                        class="text-[12px] text-gray-400"
                        :title="getProcessBlockedReason(ticket)"
                      >
                        不可处理
                      </span>
                    </div>
                  </td>
                </tr>

                <tr v-if="tickets.length === 0 && !loading">
                  <td colspan="7" class="px-5 py-16 text-center">
                    <div class="flex flex-col items-center justify-center text-gray-400">
                      <ShieldAlert class="mb-3 h-9 w-9 opacity-30" />
                      <p class="text-[13px]">当前没有符合条件的工单记录</p>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="flex items-center justify-between border-t border-gray-100 bg-gray-50/40 px-5 py-3">
            <div class="text-[12px] text-gray-500">
              当前结果共 <span class="font-numeric font-medium text-gray-700">{{ tickets.length }}</span> 条
            </div>
            <div class="text-[12px] text-gray-400">列表按当前筛选条件自动刷新</div>
          </div>
        </section>
      </div>

      <aside class="space-y-4">
        <section class="rounded-xl border border-gray-200/80 bg-white p-5 shadow-sm">
          <h3 class="mb-4 flex items-center gap-2 text-[14px] font-semibold text-gray-900">
            <Info class="h-4 w-4 text-blue-600" />
            处理规范与节奏
          </h3>

          <div class="space-y-3">
            <div class="rounded-lg border border-red-100 bg-red-50/70 p-3">
              <p class="text-[12px] font-medium text-red-800">高风险工单优先</p>
              <p class="mt-1 text-[12px] leading-5 text-red-700/85">涉及违禁售卖、资金争议或高频异常交易的工单，优先判断是否立即升级处理。</p>
            </div>

            <div class="rounded-lg border border-orange-100 bg-orange-50/70 p-3">
              <p class="text-[12px] font-medium text-orange-800">举报需形成闭环</p>
              <p class="mt-1 text-[12px] leading-5 text-orange-700/85">对举报工单给出明确结论，必要时同步商品下架，避免列表长期堆积未闭环记录。</p>
            </div>

            <div class="rounded-lg border border-blue-100 bg-blue-50/70 p-3">
              <p class="text-[12px] font-medium text-blue-800">裁决要保留备注</p>
              <p class="mt-1 text-[12px] leading-5 text-blue-700/85">处理说明将用于后续复盘和审计追踪，建议备注结论依据与关键判断点。</p>
            </div>
          </div>
        </section>

        <section class="rounded-xl border border-gray-200/80 bg-gray-50/70 p-5">
          <h3 class="mb-4 text-[14px] font-semibold text-gray-900">当前处理视角</h3>

          <div class="space-y-3 text-[12px] leading-relaxed text-gray-600">
            <p class="flex items-start gap-2">
              <span class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-gray-400"></span>
              <span>纠纷工单更关注平台裁决结论与售后处理一致性。</span>
            </p>
            <p class="flex items-start gap-2">
              <span class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-gray-400"></span>
              <span>举报工单更关注违规事实判断、商品处置动作与备注留痕。</span>
            </p>
            <p class="flex items-start gap-2">
              <span class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-gray-400"></span>
              <span>页面已接真实聚合数据，当前展示的是可直接进入处理链路的工单视图。</span>
            </p>
          </div>
        </section>
      </aside>
    </div>

    <Teleport to="body">
      <div v-if="isDetailModalOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-gray-900/40 px-4">
        <div class="flex max-h-[78vh] w-full max-w-2xl flex-col overflow-hidden rounded-xl border border-gray-200/80 bg-white shadow-2xl" @click.stop>
          <div class="flex items-center justify-between border-b border-gray-100 px-6 py-4">
            <h2 class="flex items-center gap-2 text-[15px] font-semibold text-gray-900">
              工单详情
              <span v-if="currentTicket" class="badge" :class="getTypeBadgeClass(currentTicket.type)">
                {{ getTypeLabel(currentTicket.type) }}
              </span>
            </h2>
            <button @click="isDetailModalOpen = false" class="rounded-md p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600">
              <span class="text-xl leading-none">&times;</span>
            </button>
          </div>

          <div v-if="currentTicket" class="custom-scrollbar space-y-6 overflow-y-auto p-6">
            <div class="grid grid-cols-1 gap-4 rounded-lg border border-gray-100 bg-gray-50 p-4 sm:grid-cols-2">
              <div>
                <p class="mb-1 text-[12px] text-gray-500">工单编号</p>
                <p class="font-numeric text-[13px] font-medium text-gray-900">{{ currentTicket.id }}</p>
              </div>
              <div>
                <p class="mb-1 text-[12px] text-gray-500">提交时间</p>
                <p class="font-numeric text-[13px] font-medium text-gray-900">{{ formatDateTime(currentTicket.createTime) }}</p>
              </div>
              <div>
                <p class="mb-1 text-[12px] text-gray-500">关联对象</p>
                <p class="text-[13px] font-medium text-gray-900">{{ currentTicket.target }}</p>
              </div>
              <div>
                <p class="mb-1 text-[12px] text-gray-500">当前状态</p>
                <span class="badge" :class="getStatusBadgeClass(currentTicket.status)">{{ getStatusLabel(currentTicket.status) }}</span>
              </div>
            </div>

            <div>
              <h3 class="mb-2 border-l-2 border-blue-500 pl-2 text-[13px] font-semibold text-gray-900">标题 / 原因</h3>
              <p class="text-[14px] font-medium leading-6 text-gray-900">{{ currentTicket.title }}</p>
            </div>

            <div>
              <h3 class="mb-2 border-l-2 border-blue-500 pl-2 text-[13px] font-semibold text-gray-900">详细描述</h3>
              <div class="min-h-[88px] rounded-lg border border-gray-200 bg-gray-50 p-3 text-[13px] leading-6 text-gray-700">
                {{ currentTicket.description || '暂无详细描述' }}
              </div>
            </div>

            <div v-if="currentTicket.riskLevel === 'HIGH'" class="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-3">
              <AlertTriangle class="mt-0.5 h-5 w-5 shrink-0 text-red-500" />
              <div>
                <p class="text-[13px] font-semibold text-red-800">高风险预警</p>
                <p class="mt-1 text-[12px] leading-5 text-red-700/85">该工单涉及较高风险事项，建议优先处理并保留清晰的处置说明。</p>
              </div>
            </div>
          </div>

          <div class="mt-auto flex justify-end gap-3 border-t border-gray-100 bg-gray-50/50 px-6 py-4">
            <button @click="isDetailModalOpen = false" class="btn-default">关闭</button>
            <button v-if="currentTicket && canProcessTicket(currentTicket)" @click="openProcessModal(currentTicket)" class="btn-primary">
              立即处理
            </button>
            <span
              v-else-if="currentTicket && (currentTicket.type === 'DISPUTE' || currentTicket.type === 'REPORT')"
              class="inline-flex items-center rounded-md border border-gray-200 bg-white px-3 py-2 text-[12px] text-gray-400"
              :title="getProcessBlockedReason(currentTicket)"
            >
              {{ getProcessBlockedReason(currentTicket) }}
            </span>
          </div>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="isProcessModalOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-gray-900/40 px-4">
        <div class="flex w-full max-w-lg flex-col overflow-hidden rounded-xl border border-gray-200/80 bg-white shadow-2xl" @click.stop>
          <div class="flex items-center justify-between border-b border-gray-100 px-6 py-5"
               :class="processTargetTicket?.type === 'REPORT' ? 'bg-orange-50/30' : 'bg-blue-50/30'">
            <h2 class="flex items-center gap-2 text-[16px] font-bold text-gray-900">
              <ShieldAlert v-if="processTargetTicket?.type === 'REPORT'" class="h-4.5 w-4.5 text-orange-500" />
              <AlertOctagon v-else class="h-4.5 w-4.5 text-blue-500" />
              {{ getProcessTitle(processTargetTicket) }}
            </h2>
            <button
              @click="closeProcessModal"
              class="rounded-md p-1.5 text-gray-400 transition-colors hover:bg-gray-200 hover:text-gray-700"
              :disabled="processSubmitting"
            >
              <span class="text-xl leading-none">&times;</span>
            </button>
          </div>

          <div v-if="processTargetTicket" class="space-y-5 p-6">
            <div class="flex flex-col gap-1.5 rounded-lg border border-gray-200 bg-gray-50 p-4">
              <span class="text-[12px] font-medium text-gray-500">当前处理工单</span>
              <span class="text-[14px] font-bold text-gray-900">{{ processTargetTicket.title }}</span>
              <div class="mt-0.5 flex items-center gap-2 text-[12px] text-gray-500">
                <span class="rounded border border-gray-200 bg-white px-1.5 py-0.5 font-numeric leading-none">ID: {{ processTargetTicket.id }}</span>
                <span>关联主体: {{ processTargetTicket.target }}</span>
              </div>
            </div>

            <div class="flex items-start gap-2.5 rounded-lg border border-blue-100 bg-blue-50/50 p-3.5">
              <Info class="mt-0.5 h-4.5 w-4.5 shrink-0 text-blue-500" />
              <p class="text-[13px] leading-relaxed text-blue-800/80">
                {{ getProcessDescription(processTargetTicket) }}
              </p>
            </div>

            <div v-if="processTargetTicket.type === 'DISPUTE'" class="space-y-3">
              <label class="block text-[13px] font-medium text-gray-700">平台裁决结果 <span class="text-red-500">*</span></label>
              <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <button
                  type="button"
                  class="relative flex flex-col rounded-xl border p-4 text-left transition-all"
                  :class="processDecision === 'approve'
                    ? 'border-blue-500 bg-blue-50/50 shadow-sm ring-1 ring-blue-500'
                    : 'border-gray-200 bg-white hover:border-blue-300 hover:bg-gray-50'"
                  @click="processDecision = 'approve'"
                >
                  <div class="flex items-center justify-between w-full mb-1.5">
                    <span class="text-[14px] font-bold" :class="processDecision === 'approve' ? 'text-blue-700' : 'text-gray-900'">支持售后申请</span>
                    <div class="h-4 w-4 rounded-full border flex items-center justify-center" :class="processDecision === 'approve' ? 'border-blue-500 bg-blue-500' : 'border-gray-300'">
                      <div v-if="processDecision === 'approve'" class="h-1.5 w-1.5 rounded-full bg-white"></div>
                    </div>
                  </div>
                  <div class="text-[12px] leading-relaxed" :class="processDecision === 'approve' ? 'text-blue-700/80' : 'text-gray-500'">同意退款或支持用户当前售后诉求。</div>
                </button>
                <button
                  type="button"
                  class="relative flex flex-col rounded-xl border p-4 text-left transition-all"
                  :class="processDecision === 'reject'
                    ? 'border-orange-500 bg-orange-50/50 shadow-sm ring-1 ring-orange-500'
                    : 'border-gray-200 bg-white hover:border-orange-300 hover:bg-gray-50'"
                  @click="processDecision = 'reject'"
                >
                  <div class="flex items-center justify-between w-full mb-1.5">
                    <span class="text-[14px] font-bold" :class="processDecision === 'reject' ? 'text-orange-700' : 'text-gray-900'">驳回售后申请</span>
                    <div class="h-4 w-4 rounded-full border flex items-center justify-center" :class="processDecision === 'reject' ? 'border-orange-500 bg-orange-500' : 'border-gray-300'">
                      <div v-if="processDecision === 'reject'" class="h-1.5 w-1.5 rounded-full bg-white"></div>
                    </div>
                  </div>
                  <div class="text-[12px] leading-relaxed" :class="processDecision === 'reject' ? 'text-orange-700/80' : 'text-gray-500'">维持现有结果，不支持本次售后诉求。</div>
                </button>
              </div>
            </div>

            <div v-if="processTargetTicket.type === 'REPORT'" class="space-y-3">
              <label class="block text-[13px] font-medium text-gray-700">举报处理动作 <span class="text-red-500">*</span></label>
              <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <button
                  type="button"
                  class="relative flex flex-col rounded-xl border p-4 text-left transition-all"
                  :class="processReportAction === 'dismiss'
                    ? 'border-gray-500 bg-gray-100/50 shadow-sm ring-1 ring-gray-500'
                    : 'border-gray-200 bg-white hover:border-gray-300 hover:bg-gray-50'"
                  @click="processReportAction = 'dismiss'"
                >
                  <div class="flex items-center justify-between w-full mb-1.5">
                    <span class="text-[14px] font-bold" :class="processReportAction === 'dismiss' ? 'text-gray-800' : 'text-gray-900'">举报不成立</span>
                    <div class="h-4 w-4 rounded-full border flex items-center justify-center" :class="processReportAction === 'dismiss' ? 'border-gray-600 bg-gray-600' : 'border-gray-300'">
                      <div v-if="processReportAction === 'dismiss'" class="h-1.5 w-1.5 rounded-full bg-white"></div>
                    </div>
                  </div>
                  <div class="text-[12px] leading-relaxed" :class="processReportAction === 'dismiss' ? 'text-gray-600' : 'text-gray-500'">关闭当前举报工单，不联动商品处置。</div>
                </button>
                <button
                  type="button"
                  class="relative flex flex-col rounded-xl border p-4 text-left transition-all"
                  :class="processReportAction === 'force_off_shelf'
                    ? 'border-red-500 bg-red-50/50 shadow-sm ring-1 ring-red-500'
                    : 'border-gray-200 bg-white hover:border-red-300 hover:bg-gray-50'"
                  @click="processReportAction = 'force_off_shelf'"
                >
                  <div class="flex items-center justify-between w-full mb-1.5">
                    <span class="text-[14px] font-bold" :class="processReportAction === 'force_off_shelf' ? 'text-red-700' : 'text-gray-900'">强制下架商品</span>
                    <div class="h-4 w-4 rounded-full border flex items-center justify-center" :class="processReportAction === 'force_off_shelf' ? 'border-red-500 bg-red-500' : 'border-gray-300'">
                      <div v-if="processReportAction === 'force_off_shelf'" class="h-1.5 w-1.5 rounded-full bg-white"></div>
                    </div>
                  </div>
                  <div class="text-[12px] leading-relaxed" :class="processReportAction === 'force_off_shelf' ? 'text-red-700/80' : 'text-gray-500'">举报成立，联动商品下架并记录处置结果。</div>
                </button>
              </div>
            </div>

            <div>
              <label for="audit_process_remark" class="mb-2 block text-[13px] font-medium text-gray-700">处理备注 <span class="text-gray-400 font-normal ml-1">(选填，建议保留处置依据)</span></label>
              <textarea
                id="audit_process_remark"
                v-model="processRemark"
                class="input-standard min-h-[100px] w-full resize-none py-2.5"
                placeholder="请输入处理说明，便于后续运营复盘和审计追踪"
                maxlength="200"
                :disabled="processSubmitting"
              ></textarea>
              <div class="mt-2 flex items-center justify-between">
                <span v-if="processError" class="flex items-center gap-1.5 text-[12px] font-medium text-red-500">
                  <AlertTriangle class="h-3.5 w-3.5" />
                  {{ processError }}
                </span>
                <span v-else class="text-[12px] text-gray-400"></span>
                <span class="font-numeric text-[12px] text-gray-400">{{ processRemark.length }}/200</span>
              </div>
            </div>
          </div>

          <div class="mt-auto flex justify-end gap-3 border-t border-gray-100 bg-gray-50/80 px-6 py-4">
            <button @click="closeProcessModal" class="btn-default px-4 py-2" :disabled="processSubmitting">取消</button>
            <button 
              @click="handleProcess" 
              class="btn-primary gap-2 px-4 py-2" 
              :class="processTargetTicket?.type === 'REPORT' && processReportAction === 'force_off_shelf' ? 'bg-red-600 hover:bg-red-700 border-red-700/50' : ''"
              :disabled="processSubmitting"
            >
              <Loader2 v-if="processSubmitting" class="h-4 w-4 animate-spin" />
              {{ processSubmitting ? '提交中...' : getProcessConfirmText(processTargetTicket) }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
