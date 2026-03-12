<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { AlertTriangle, Loader2, Search, ShieldAlert, FileText, AlertOctagon, Info } from 'lucide-vue-next'
import { getAuditOverview, submitAuditAction, type AuditStats, type AuditTicketItem } from '@/api/audit'

/**
 * 纠纷与违规工单类型定义
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
 * 筛选条件
 */
const searchQuery = ref('')
const selectedType = ref('ALL')
const selectedStatus = ref('ALL')
const selectedRisk = ref('ALL')

/**
 * 列表数据（带前端过滤）
 */
const tickets = ref<AuditTicketItem[]>([])
const processSubmitting = ref(false)
const processDecision = ref<'approve' | 'reject'>('approve')
const processReportAction = ref<'dismiss' | 'force_off_shelf'>('dismiss')
const processRemark = ref('')
const processError = ref('')

/**
 * 查询真实总览数据。
 * 当前页面已经切到后端聚合接口，不再依赖本地 mock 数组。
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
 * 监听筛选条件变化
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
 * 查看详情
 */
const openDetail = (ticket: AuditTicketItem) => {
  currentTicket.value = ticket
  isDetailModalOpen.value = true
}

/**
 * 打开处理弹窗
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
 * 提交处理动作
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
 * 时间格式化为更稳定的表格展示值。
 */
const formatDateTime = (value?: string) => {
  if (!value) return '--'
  return value.replace('T', ' ').slice(0, 19)
}

/**
 * 样式辅助函数
 */
const getTypeLabel = (type: string) => {
  switch (type) {
    case 'DISPUTE': return '交易纠纷'
    case 'REPORT': return '违规举报'
    case 'RISK': return '风控线索'
    default: return '未知'
  }
}

const getTypeBadgeClass = (type: string) => {
  switch (type) {
    case 'DISPUTE': return 'bg-blue-50 text-blue-700 border-blue-200'
    case 'REPORT': return 'bg-orange-50 text-orange-700 border-orange-200'
    case 'RISK': return 'bg-purple-50 text-purple-700 border-purple-200'
    default: return 'bg-gray-50 text-gray-700 border-gray-200'
  }
}

const getRiskLabel = (level: string) => {
  switch (level) {
    case 'HIGH': return '高风险'
    case 'MEDIUM': return '中风险'
    case 'LOW': return '低风险'
    default: return '未知'
  }
}

const getRiskBadgeClass = (level: string) => {
  switch (level) {
    case 'HIGH': return 'bg-red-50 text-red-700 border-red-200 font-semibold'
    case 'MEDIUM': return 'bg-yellow-50 text-yellow-700 border-yellow-200'
    case 'LOW': return 'bg-green-50 text-green-700 border-green-200'
    default: return 'bg-gray-50 text-gray-700 border-gray-200'
  }
}

const getStatusLabel = (status: string) => {
  switch (status) {
    case 'PENDING': return '待处理'
    case 'PROCESSING': return '处理中'
    case 'CLOSED': return '已关闭'
    default: return '未知'
  }
}

const getStatusBadgeClass = (status: string) => {
  switch (status) {
    case 'PENDING': return 'bg-red-50 text-red-700 border-red-200'
    case 'PROCESSING': return 'bg-blue-50 text-blue-700 border-blue-200'
    case 'CLOSED': return 'bg-gray-50 text-gray-700 border-gray-200'
    default: return 'bg-gray-50 text-gray-700 border-gray-200'
  }
}

const canProcessTicket = (ticket: AuditTicketItem) => (
  ticket.status !== 'CLOSED' && (ticket.type === 'DISPUTE' || ticket.type === 'REPORT')
)

const getProcessTitle = (ticket?: AuditTicketItem | null) => {
  if (!ticket) return '处理工单'
  if (ticket.type === 'DISPUTE') return '处理交易纠纷'
  if (ticket.type === 'REPORT') return '处理违规举报'
  return '处理工单'
}

const getProcessDescription = (ticket?: AuditTicketItem | null) => {
  if (!ticket) return '提交处理结果后，页面列表将自动刷新。'
  if (ticket.type === 'DISPUTE') return '平台裁决会写入售后处理结果，并同步更新工单状态。'
  if (ticket.type === 'REPORT') return '举报处理会写入工单结果，必要时联动商品强制下架。'
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
  <div class="space-y-4 max-w-[1600px] mx-auto">
    <!-- 顶部标题区 -->
    <div class="flex justify-between items-center pb-2">
      <div>
        <h1 class="text-xl font-bold text-gray-900">纠纷与违规</h1>
        <p class="text-sm text-gray-500 mt-1">集中处理平台纠纷工单、违规举报与高风险交易线索</p>
      </div>
    </div>

    <!-- 指标卡区 -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="card p-4 flex items-center space-x-4 border-l-4 border-l-red-500">
        <div class="p-3 bg-red-50 rounded-full text-red-600">
          <AlertOctagon class="w-6 h-6" />
        </div>
        <div>
          <p class="text-sm text-gray-500 font-medium">待处理纠纷</p>
          <p class="text-2xl font-bold text-gray-900 font-numeric mt-1">{{ summary.pendingDisputes }}</p>
        </div>
      </div>
      
      <div class="card p-4 flex items-center space-x-4 border-l-4 border-l-orange-500">
        <div class="p-3 bg-orange-50 rounded-full text-orange-600">
          <AlertTriangle class="w-6 h-6" />
        </div>
        <div>
          <p class="text-sm text-gray-500 font-medium">紧急举报</p>
          <p class="text-2xl font-bold text-gray-900 font-numeric mt-1">{{ summary.urgentReports }}</p>
        </div>
      </div>

      <div class="card p-4 flex items-center space-x-4 border-l-4 border-l-blue-500">
        <div class="p-3 bg-blue-50 rounded-full text-blue-600">
          <ShieldAlert class="w-6 h-6" />
        </div>
        <div>
          <p class="text-sm text-gray-500 font-medium">平台强介入</p>
          <p class="text-2xl font-bold text-gray-900 font-numeric mt-1">{{ summary.platformIntervention }}</p>
        </div>
      </div>

      <div class="card p-4 flex items-center space-x-4 border-l-4 border-l-purple-500">
        <div class="p-3 bg-purple-50 rounded-full text-purple-600">
          <FileText class="w-6 h-6" />
        </div>
        <div>
          <p class="text-sm text-gray-500 font-medium">今日新增线索</p>
          <p class="text-2xl font-bold text-gray-900 font-numeric mt-1">{{ summary.todayNewClues }}</p>
        </div>
      </div>
    </div>

    <div class="flex flex-col lg:flex-row gap-4 items-start">
      <!-- 左侧主内容区 -->
      <div class="w-full lg:w-3/4 space-y-4">
        <!-- 筛选区 -->
        <div class="card p-4 flex flex-col sm:flex-row gap-4 items-start sm:items-center justify-between">
          <div class="flex flex-wrap gap-3 items-center w-full">
            <div class="relative w-full sm:w-64">
              <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              <input
                v-model="searchQuery"
                type="text"
                placeholder="搜索工单编号 / 标题 / 对象"
                class="input-standard !pl-9 w-full"
              />
            </div>

            <select v-model="selectedType" class="input-standard min-w-[120px]">
              <option value="ALL">全部类型</option>
              <option value="DISPUTE">交易纠纷</option>
              <option value="REPORT">违规举报</option>
              <option value="RISK">风控线索</option>
            </select>

            <select v-model="selectedStatus" class="input-standard min-w-[120px]">
              <option value="ALL">全部状态</option>
              <option value="PENDING">待处理</option>
              <option value="PROCESSING">处理中</option>
              <option value="CLOSED">已关闭</option>
            </select>

            <select v-model="selectedRisk" class="input-standard min-w-[120px]">
              <option value="ALL">全部风险等级</option>
              <option value="HIGH">高风险</option>
              <option value="MEDIUM">中风险</option>
              <option value="LOW">低风险</option>
            </select>
          </div>
        </div>

        <!-- 主列表区 -->
        <div class="card overflow-hidden relative min-h-[400px]">
          <div v-if="loading" class="absolute inset-0 z-10 flex items-center justify-center bg-white/60 backdrop-blur-[1px]">
            <div class="flex items-center gap-2 rounded border border-gray-200 bg-white px-4 py-2 text-sm text-gray-600 shadow-sm">
              <Loader2 class="w-4 h-4 animate-spin" /> 数据加载中...
            </div>
          </div>

          <div class="overflow-x-auto">
            <table class="w-full text-left border-collapse whitespace-nowrap">
              <thead>
                <tr class="bg-gray-50 border-b border-gray-200 text-sm font-semibold text-gray-700">
                  <th class="py-3 px-4">工单编号</th>
                  <th class="py-3 px-4">类型</th>
                  <th class="py-3 px-4 min-w-[200px]">标题/原因</th>
                  <th class="py-3 px-4">关联对象</th>
                  <th class="py-3 px-4">风险等级</th>
                  <th class="py-3 px-4">当前状态</th>
                  <th class="py-3 px-4">提交时间</th>
                  <th class="py-3 px-4 text-right">操作</th>
                </tr>
              </thead>
              <tbody class="text-sm text-gray-700 divide-y divide-gray-100">
                <tr v-for="ticket in tickets" :key="ticket.id" class="hover:bg-blue-50/50 transition-colors group">
                  <td class="py-3 px-4 font-numeric text-gray-500">{{ ticket.id }}</td>
                  
                  <td class="py-3 px-4">
                    <span class="px-2 py-1 rounded-sm border text-xs" :class="getTypeBadgeClass(ticket.type)">
                      {{ getTypeLabel(ticket.type) }}
                    </span>
                  </td>

                  <td class="py-3 px-4">
                    <span class="font-medium text-gray-900 truncate max-w-[200px] block" :title="ticket.title">
                      {{ ticket.title }}
                    </span>
                  </td>

                  <td class="py-3 px-4 text-gray-600">{{ ticket.target }}</td>

                  <td class="py-3 px-4">
                    <span class="px-2 py-1 rounded-sm border text-xs" :class="getRiskBadgeClass(ticket.riskLevel)">
                      {{ getRiskLabel(ticket.riskLevel) }}
                    </span>
                  </td>

                  <td class="py-3 px-4">
                    <span class="px-2 py-1 rounded-sm border text-xs font-medium" :class="getStatusBadgeClass(ticket.status)">
                      {{ getStatusLabel(ticket.status) }}
                    </span>
                  </td>

                  <td class="py-3 px-4 font-numeric text-gray-500">{{ formatDateTime(ticket.createTime) }}</td>

                  <td class="py-3 px-4 text-right">
                    <div class="flex items-center justify-end space-x-3">
                      <button @click="openDetail(ticket)" class="text-blue-600 hover:text-blue-800 font-medium text-xs">
                        查看详情
                      </button>
                      <button
                        v-if="canProcessTicket(ticket)"
                        @click="openProcessModal(ticket)"
                        class="text-orange-600 hover:text-orange-800 font-medium text-xs"
                      >
                        立即处理
                      </button>
                    </div>
                  </td>
                </tr>

                <tr v-if="tickets.length === 0 && !loading">
                  <td colspan="8" class="py-16 text-center">
                    <div class="flex flex-col items-center justify-center text-gray-400">
                      <ShieldAlert class="w-12 h-12 mb-3 text-gray-300" />
                      <p>没有找到符合条件的工单记录</p>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          
          <!-- 简易分页占位 -->
          <div class="border-t border-gray-200 px-4 py-3 flex items-center justify-between bg-gray-50/80">
            <div class="text-sm text-gray-500">
              共 <span class="font-semibold font-numeric text-gray-700">{{ tickets.length }}</span> 条记录
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧说明区 -->
      <div class="w-full lg:w-1/4 space-y-4">
        <div class="card p-5 bg-blue-50/50 border-blue-100">
          <h3 class="font-bold text-blue-900 flex items-center gap-2 mb-3">
            <Info class="w-4 h-4" /> 处理规范与SLA
          </h3>
          <div class="space-y-3 text-sm text-blue-800/80">
            <div class="bg-white p-3 rounded border border-blue-100 shadow-sm">
              <p class="font-semibold text-blue-900 mb-1">高风险工单 (SLA: 2小时)</p>
              <p class="text-xs">涉及违禁品、严重欺诈、高频异常交易等，需立即介入并采取限制措施。</p>
            </div>
            <div class="bg-white p-3 rounded border border-blue-100 shadow-sm">
              <p class="font-semibold text-blue-900 mb-1">中风险工单 (SLA: 24小时)</p>
              <p class="text-xs">普通交易纠纷、轻度违规举报，需在24小时内完成初步审核与双方沟通。</p>
            </div>
            <div class="bg-white p-3 rounded border border-blue-100 shadow-sm">
              <p class="font-semibold text-blue-900 mb-1">平台介入条件</p>
              <p class="text-xs">买卖双方协商超过72小时未果，或单笔交易金额大于5000元且存在争议。</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 详情弹窗 -->
    <Teleport to="body">
      <div v-if="isDetailModalOpen" class="fixed inset-0 bg-gray-900/40 z-50 flex items-center justify-center">
        <div class="bg-white rounded-md shadow-xl w-full max-w-2xl border border-gray-200 overflow-hidden flex flex-col" @click.stop>
          <div class="flex justify-between items-center px-6 py-4 border-b border-gray-200">
            <h2 class="text-base font-bold text-gray-800 flex items-center gap-2">
              工单详情
              <span class="px-2 py-0.5 rounded-sm border text-xs font-normal" :class="currentTicket ? getTypeBadgeClass(currentTicket.type) : ''">
                {{ currentTicket ? getTypeLabel(currentTicket.type) : '' }}
              </span>
            </h2>
            <button @click="isDetailModalOpen = false" class="text-gray-400 hover:text-gray-600">
              <span class="text-xl leading-none">&times;</span>
            </button>
          </div>

          <div class="p-6 space-y-6 overflow-y-auto max-h-[60vh]" v-if="currentTicket">
            <!-- 基础信息 -->
            <div class="grid grid-cols-2 gap-4 bg-gray-50 p-4 rounded border border-gray-100">
              <div>
                <p class="text-xs text-gray-500 mb-1">工单编号</p>
                <p class="text-sm font-medium font-numeric">{{ currentTicket.id }}</p>
              </div>
              <div>
                <p class="text-xs text-gray-500 mb-1">提交时间</p>
                <p class="text-sm font-medium font-numeric">{{ formatDateTime(currentTicket.createTime) }}</p>
              </div>
              <div>
                <p class="text-xs text-gray-500 mb-1">关联对象</p>
                <p class="text-sm font-medium text-blue-600 cursor-pointer hover:underline">{{ currentTicket.target }}</p>
              </div>
              <div>
                <p class="text-xs text-gray-500 mb-1">当前状态</p>
                <span class="px-2 py-0.5 rounded-sm border text-xs font-medium inline-block" :class="getStatusBadgeClass(currentTicket.status)">
                  {{ getStatusLabel(currentTicket.status) }}
                </span>
              </div>
            </div>

            <!-- 详细描述 -->
            <div>
              <h3 class="text-sm font-bold text-gray-900 mb-2 border-l-2 border-blue-500 pl-2">标题 / 原因</h3>
              <p class="text-sm text-gray-800 font-medium">{{ currentTicket.title }}</p>
            </div>

            <div>
              <h3 class="text-sm font-bold text-gray-900 mb-2 border-l-2 border-blue-500 pl-2">详细描述</h3>
              <div class="bg-gray-50 p-3 rounded border border-gray-200 text-sm text-gray-700 leading-relaxed min-h-[80px]">
                {{ currentTicket.description || '暂无详细描述' }}
              </div>
            </div>
            
            <!-- 风险提示 -->
            <div v-if="currentTicket.riskLevel === 'HIGH'" class="bg-red-50 p-3 rounded border border-red-200 flex items-start gap-2">
              <AlertTriangle class="w-5 h-5 text-red-500 shrink-0 mt-0.5" />
              <div>
                <p class="text-sm font-bold text-red-800">高风险预警</p>
                <p class="text-xs text-red-700 mt-1">该工单涉及高风险事项，请优先处理并严格按照平台安全规范执行操作。</p>
              </div>
            </div>
          </div>

          <div class="px-6 py-4 border-t border-gray-200 flex justify-end space-x-3 bg-gray-50/50 mt-auto">
            <button @click="isDetailModalOpen = false" class="btn-default">关闭</button>
            <button v-if="currentTicket && canProcessTicket(currentTicket)" @click="openProcessModal(currentTicket)" class="btn-primary">
              立即处理
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="isProcessModalOpen" class="fixed inset-0 bg-gray-900/40 z-50 flex items-center justify-center">
        <div class="bg-white rounded-md shadow-xl w-full max-w-lg border border-gray-200 overflow-hidden flex flex-col" @click.stop>
          <div class="flex justify-between items-center px-6 py-4 border-b border-gray-200">
            <h2 class="text-base font-bold text-gray-800">{{ getProcessTitle(processTargetTicket) }}</h2>
            <button @click="closeProcessModal" class="text-gray-400 hover:text-gray-600" :disabled="processSubmitting">
              <span class="text-xl leading-none">&times;</span>
            </button>
          </div>

          <div class="p-6 space-y-5" v-if="processTargetTicket">
            <div class="bg-gray-50 p-3 rounded-sm border border-gray-200 flex flex-col gap-1">
              <span class="text-xs text-gray-500">当前工单</span>
              <span class="text-sm font-medium text-gray-900">{{ processTargetTicket.title }}</span>
              <div class="flex items-center gap-2 text-xs text-gray-400">
                <span class="font-numeric">{{ processTargetTicket.id }}</span>
                <span>{{ processTargetTicket.target }}</span>
              </div>
            </div>

            <div class="bg-blue-50 p-3 rounded-sm border border-blue-100">
              <p class="text-sm text-blue-800 leading-relaxed">
                {{ getProcessDescription(processTargetTicket) }}
              </p>
            </div>

            <div v-if="processTargetTicket.type === 'DISPUTE'" class="space-y-3">
              <label class="block text-sm font-medium text-gray-700">平台裁决结果 <span class="text-red-500">*</span></label>
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <button
                  type="button"
                  class="border rounded px-4 py-3 text-left transition-colors"
                  :class="processDecision === 'approve'
                    ? 'border-blue-500 bg-blue-50 text-blue-700'
                    : 'border-gray-200 bg-white text-gray-700 hover:border-blue-200 hover:bg-blue-50/40'"
                  @click="processDecision = 'approve'"
                >
                  <div class="text-sm font-semibold">支持售后申请</div>
                  <div class="text-xs mt-1 text-current/80">同意退款或平台支持用户申诉请求</div>
                </button>
                <button
                  type="button"
                  class="border rounded px-4 py-3 text-left transition-colors"
                  :class="processDecision === 'reject'
                    ? 'border-orange-500 bg-orange-50 text-orange-700'
                    : 'border-gray-200 bg-white text-gray-700 hover:border-orange-200 hover:bg-orange-50/40'"
                  @click="processDecision = 'reject'"
                >
                  <div class="text-sm font-semibold">驳回售后申请</div>
                  <div class="text-xs mt-1 text-current/80">不支持当前诉求，维持现有售后结果</div>
                </button>
              </div>
            </div>

            <div v-if="processTargetTicket.type === 'REPORT'" class="space-y-3">
              <label class="block text-sm font-medium text-gray-700">举报处理动作 <span class="text-red-500">*</span></label>
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <button
                  type="button"
                  class="border rounded px-4 py-3 text-left transition-colors"
                  :class="processReportAction === 'dismiss'
                    ? 'border-gray-500 bg-gray-50 text-gray-700'
                    : 'border-gray-200 bg-white text-gray-700 hover:border-gray-300 hover:bg-gray-50/70'"
                  @click="processReportAction = 'dismiss'"
                >
                  <div class="text-sm font-semibold">举报不成立</div>
                  <div class="text-xs mt-1 text-current/80">关闭当前举报工单，不联动商品处置</div>
                </button>
                <button
                  type="button"
                  class="border rounded px-4 py-3 text-left transition-colors"
                  :class="processReportAction === 'force_off_shelf'
                    ? 'border-red-500 bg-red-50 text-red-700'
                    : 'border-gray-200 bg-white text-gray-700 hover:border-red-200 hover:bg-red-50/40'"
                  @click="processReportAction = 'force_off_shelf'"
                >
                  <div class="text-sm font-semibold">强制下架商品</div>
                  <div class="text-xs mt-1 text-current/80">举报成立，联动商品下架并记录处置结果</div>
                </button>
              </div>
            </div>

            <div>
              <label for="audit_process_remark" class="block text-sm font-medium text-gray-700 mb-1.5">处理备注</label>
              <textarea
                id="audit_process_remark"
                v-model="processRemark"
                class="input-standard w-full min-h-[110px] resize-none py-2"
                placeholder="请输入处理说明，便于后续运营复盘和审计追踪"
                maxlength="200"
                :disabled="processSubmitting"
              ></textarea>
              <div class="flex justify-between items-center mt-1">
                <span v-if="processError" class="text-xs text-red-500 flex items-center gap-1">
                  <AlertTriangle class="w-3 h-3" /> {{ processError }}
                </span>
                <span v-else class="text-xs text-gray-400"></span>
                <span class="text-xs text-gray-400 font-numeric">{{ processRemark.length }}/200</span>
              </div>
            </div>
          </div>

          <div class="px-6 py-4 border-t border-gray-200 flex justify-end space-x-3 bg-gray-50/50 mt-auto">
            <button @click="closeProcessModal" class="btn-default" :disabled="processSubmitting">取消</button>
            <button @click="handleProcess" class="btn-primary flex items-center gap-2" :disabled="processSubmitting">
              <Loader2 v-if="processSubmitting" class="w-4 h-4 animate-spin" />
              {{ processSubmitting ? '提交中...' : getProcessConfirmText(processTargetTicket) }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
