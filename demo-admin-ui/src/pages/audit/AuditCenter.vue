<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  AlertOctagon,
  AlertTriangle,
  FileText,
  Info,
  Loader2,
  Search,
  ShieldAlert,
} from 'lucide-vue-next'
import {
  getAuditOverview,
  getAuditProcessMeta,
  getAuditRiskLabel,
  getAuditStatusLabel,
  getAuditTypeLabel,
  submitAuditAction,
  type AuditStats,
  type AuditTicketItem,
} from '@/api/audit'

/**
 * 页面基础状态。
 * Day08 这一轮继续只做前端消费边界收口，不改后端联调链路。
 */
const loading = ref(false)
const listError = ref('')
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
 * 页面层统一提取最终展示错误文案：
 * - 接口层继续只负责抛错，不在这里改协议。
 * - 页面只关心“最终给运营同学看什么文案”。
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

const showAuditListErrorBanner = computed(() => !!listError.value && tickets.value.length > 0)
const showAuditListErrorEmptyState = computed(() => !!listError.value && !loading.value && tickets.value.length === 0)
const showAuditListEmptyState = computed(() => !listError.value && !loading.value && tickets.value.length === 0)

/**
 * 拉取总览数据。
 */
const fetchData = async () => {
  try {
    loading.value = true
    listError.value = ''
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
    // 失败时保留当前已展示内容，避免把“请求失败”误渲染成“真实为 0”。
    listError.value = resolvePageErrorMessage(error, '工单队列加载失败，请稍后重试')
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

/**
 * 每次打开处理弹窗都重置表单状态，避免上一个工单的选择串到下一个工单。
 */
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
 * 详情弹窗和处理弹窗都直接消费 API 模块返回的 process meta，
 * 页面只保留模态框状态、按钮 loading 与表单输入，不再自己维护一套 blocked reason / title 文案。
 */
const currentTicketProcessMeta = computed(() => getAuditProcessMeta(currentTicket.value))
const processTargetMeta = computed(() => getAuditProcessMeta(processTargetTicket.value))

/**
 * 提交处理动作。
 */
const handleProcess = async () => {
  if (!processTargetTicket.value) return

  try {
    processSubmitting.value = true
    processError.value = ''

    // 页面层只负责把统一表单翻译成 API 层要求的不同 payload。
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
    processError.value = resolvePageErrorMessage(error, '提交失败，请稍后重试')
  } finally {
    processSubmitting.value = false
  }
}

/**
 * 时间格式仍留在页面层，因为这里只影响当前表格与弹窗展示。
 */
const formatDateTime = (value?: string) => {
  if (!value) return '--'
  return value.replace('T', ' ').slice(0, 19)
}

/**
 * badge 颜色仍留在页面层，因为这是纯视觉规则：
 * - API 模块负责中文标签和处理可用性
 * - 页面只负责把不同语义映射成当前设计稿的颜色
 */
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
            集中处理平台纠纷工单、违规举报与高风险线索，优先识别高风险工单、缩短处置响应时间，并为后续运营复盘保留清晰的处理依据。
          </p>

          <div class="flex flex-wrap items-center gap-4 text-[12px] text-gray-500">
            <span class="flex items-center gap-1.5">
              <span class="h-1.5 w-1.5 rounded-full bg-green-500"></span>
              当前处理节奏稳定
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
            信息汇总：平台纠纷与风险概览
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
        <section class="filter-bar">
          <div class="filter-bar-group flex-1">
            <div class="filter-search">
              <Search class="filter-search-icon" />
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

          <div class="filter-note">
            当前列表优先显示近期需重点跟进的工单
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

          <div v-if="showAuditListErrorBanner" class="border-b border-red-100 bg-red-50/40 px-5 py-4">
            <div class="state-banner state-banner-danger shadow-none">
              <div class="state-banner-body">
                <div class="state-banner-main">
                  <span class="state-banner-icon border-red-200">
                    <AlertTriangle class="h-4 w-4 text-red-500" />
                  </span>
                  <div>
                    <p class="state-banner-title">工单队列刷新失败</p>
                    <p class="state-banner-text text-red-700/90">{{ listError }}</p>
                  </div>
                </div>
                <button class="btn-default shrink-0 px-3 py-1.5 text-[12px]" :disabled="loading" @click="fetchData">
                  重新加载
                </button>
              </div>
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
                          处理中
                        </span>
                      </div>
                    </div>
                  </td>

                  <td class="px-4 py-4">
                    <span class="badge" :class="getTypeBadgeClass(ticket.type)">{{ getAuditTypeLabel(ticket.type) }}</span>
                  </td>

                  <td class="px-4 py-4">
                    <div class="space-y-1">
                      <p class="font-medium text-gray-800">{{ ticket.target }}</p>
                      <p class="text-[11px] text-gray-500">当前关联主体</p>
                    </div>
                  </td>

                  <td class="px-4 py-4">
                    <span class="badge" :class="getRiskBadgeClass(ticket.riskLevel)">{{ getAuditRiskLabel(ticket.riskLevel) }}</span>
                  </td>

                  <td class="px-4 py-4">
                    <span class="badge" :class="getStatusBadgeClass(ticket.status)">{{ getAuditStatusLabel(ticket.status) }}</span>
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
                        v-if="getAuditProcessMeta(ticket).canProcess"
                        @click="openProcessModal(ticket)"
                        class="text-[12px] font-medium text-orange-700 hover:text-orange-800"
                      >
                        立即处理
                      </button>
                      <span
                        v-else-if="getAuditProcessMeta(ticket).blockedReason"
                        class="text-[12px] text-gray-400"
                        :title="getAuditProcessMeta(ticket).blockedReason"
                      >
                        {{ getAuditProcessMeta(ticket).blockedLabel }}
                      </span>
                    </div>
                  </td>
                </tr>

                <tr v-if="showAuditListErrorEmptyState">
                  <td colspan="7" class="px-5 py-12">
                    <div class="empty-state empty-state-danger py-8">
                      <AlertTriangle class="empty-state-icon text-red-400" />
                      <p class="empty-state-title">工单队列暂未加载成功</p>
                      <p class="empty-state-text">{{ listError }}</p>
                      <button class="btn-default mt-4" :disabled="loading" @click="fetchData">重新加载</button>
                    </div>
                  </td>
                </tr>

                <tr v-else-if="showAuditListEmptyState">
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
              <span>当前列表聚焦可优先推进的高风险工单，便于值守人员快速跟进重点事项。</span>
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
                {{ getAuditTypeLabel(currentTicket.type) }}
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
                <span class="badge" :class="getStatusBadgeClass(currentTicket.status)">{{ getAuditStatusLabel(currentTicket.status) }}</span>
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
            <button v-if="currentTicket && currentTicketProcessMeta.canProcess" @click="openProcessModal(currentTicket)" class="btn-primary">
              立即处理
            </button>
            <span
              v-else-if="currentTicket && currentTicketProcessMeta.blockedReason"
              class="inline-flex items-center rounded-md border border-gray-200 bg-white px-3 py-2 text-[12px] text-gray-400"
              :title="currentTicketProcessMeta.blockedReason"
            >
              {{ currentTicketProcessMeta.blockedReason }}
            </span>
          </div>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="isProcessModalOpen" class="modal-backdrop">
        <div class="modal-panel max-w-lg flex flex-col" @click.stop>
          <div class="modal-header"
               :class="processTargetTicket?.type === 'REPORT' ? 'bg-orange-50/30' : 'bg-blue-50/30'">
            <div>
              <h2 class="modal-title flex items-center gap-2">
                <ShieldAlert v-if="processTargetTicket?.type === 'REPORT'" class="h-4.5 w-4.5 text-orange-500" />
                <AlertOctagon v-else class="h-4.5 w-4.5 text-blue-500" />
                {{ processTargetMeta.title }}
              </h2>
              <p class="form-helper">提交后会同步刷新当前工单状态，请在确认处置结果后再执行。</p>
            </div>
            <button
              @click="closeProcessModal"
              class="modal-close"
              :disabled="processSubmitting"
            >
              <span class="text-xl leading-none">&times;</span>
            </button>
          </div>

          <div v-if="processTargetTicket" class="modal-body">
            <div class="flex flex-col gap-1.5 rounded-lg border border-gray-200 bg-gray-50 p-4">
              <span class="text-[12px] font-medium text-gray-500">当前处理工单</span>
              <span class="text-[14px] font-bold text-gray-900">{{ processTargetTicket.title }}</span>
              <div class="mt-0.5 flex items-center gap-2 text-[12px] text-gray-500">
                <span class="status-chip status-chip-neutral font-numeric leading-none">ID: {{ processTargetTicket.id }}</span>
                <span>关联主体: {{ processTargetTicket.target }}</span>
              </div>
            </div>

            <div class="state-banner state-banner-info">
              <div class="state-banner-main">
                <span class="state-banner-icon border-blue-200">
                  <Info class="h-4.5 w-4.5 text-blue-500" />
                </span>
                <div>
                  <p class="state-banner-title">处理说明</p>
                  <p class="state-banner-text text-blue-700/90">
                    {{ processTargetMeta.description }}
                  </p>
                </div>
              </div>
            </div>

            <div v-if="processTargetTicket.type === 'DISPUTE'" class="space-y-3">
              <label class="form-label">平台裁决结果 <span class="text-red-500">*</span></label>
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
              <label class="form-label">举报处理动作 <span class="text-red-500">*</span></label>
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
              <label for="audit_process_remark" class="form-label">处理备注 <span class="text-gray-400 font-normal ml-1">(选填，建议保留处置依据)</span></label>
              <textarea
                id="audit_process_remark"
                v-model="processRemark"
                class="input-standard min-h-[100px] w-full resize-none py-2.5"
                :class="processError ? '!border-red-300 !bg-red-50/40 focus:!border-red-400 focus:!ring-red-100' : ''"
                placeholder="请输入处理说明，便于后续运营复盘和审计追踪"
                maxlength="200"
                :disabled="processSubmitting"
              ></textarea>
              <div class="mt-2 flex items-center justify-between gap-3">
                <span class="text-[12px] text-gray-400">建议写清裁决依据、补充证据或联动动作，便于后续复盘。</span>
                <span class="font-numeric text-[12px] text-gray-400">{{ processRemark.length }}/200</span>
              </div>
              <div v-if="processError" class="mt-3 state-banner state-banner-danger shadow-none">
                <div class="state-banner-main">
                  <span class="state-banner-icon border-red-200">
                    <AlertTriangle class="h-4 w-4 text-red-500" />
                  </span>
                  <div>
                    <p class="state-banner-title">提交未完成</p>
                    <p class="state-banner-text text-red-700/90">{{ processError }}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="modal-footer mt-auto">
            <button @click="closeProcessModal" class="btn-default px-4 py-2" :disabled="processSubmitting">取消</button>
            <button 
              @click="handleProcess" 
              :class="`${processTargetTicket?.type === 'REPORT' && processReportAction === 'force_off_shelf' ? 'btn-danger' : 'btn-primary'} btn-loading px-4 py-2`"
              :disabled="processSubmitting"
            >
              <Loader2 v-if="processSubmitting" class="btn-loading-icon" />
              {{ processSubmitting ? '提交中...' : processTargetMeta.confirmText }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
