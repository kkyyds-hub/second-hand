<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { AlertTriangle, Clock, PlayCircle, RefreshCw, Wrench } from 'lucide-vue-next'
import {
  fetchAdminOrders,
  fetchOutboxMetrics,
  fetchRefundTasks,
  fetchShipReminderTasks,
  fetchShipTimeoutTasks,
  fetchViolationStatistics,
  publishOutboxOnce,
  runRefundOnce,
  runShipReminderOnce,
  runShipTimeoutOnce,
} from '@/api/adminExtra'

const runtimeLoading = ref(false)
const runtimeError = ref('')
const lastUpdated = ref('')
const opsActionLoading = ref('')
const opsActionMessage = ref('')

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

const refreshRuntimeData = async () => {
  try {
    runtimeLoading.value = true
    runtimeError.value = ''
    const [orders, outbox, shipTimeout, refund, shipReminder, violationStats] = await Promise.all([
      fetchAdminOrders(1, 1),
      fetchOutboxMetrics(),
      fetchShipTimeoutTasks(1, 1),
      fetchRefundTasks(1, 1),
      fetchShipReminderTasks(1, 1),
      fetchViolationStatistics(),
    ])

    const firstViolation = (violationStats.violationTypeDistribution || [])[0]

    runtimeSnapshot.value = {
      orderTotal: Number(orders.total || 0),
      outboxNew: Number(outbox.new || 0),
      outboxFail: Number(outbox.fail || 0),
      outboxSent: Number(outbox.sent || 0),
      shipTimeoutTotal: Number(shipTimeout.total || 0),
      refundTotal: Number(refund.total || 0),
      shipReminderTotal: Number(shipReminder.total || 0),
      topViolationType: firstViolation?.violationTypeDesc || firstViolation?.violationType || '--',
      topViolationCount: Number(firstViolation?.count || 0),
    }
    lastUpdated.value = new Date().toLocaleString()
  } catch (error: any) {
    runtimeError.value = error?.message || '运行态数据拉取失败'
  } finally {
    runtimeLoading.value = false
  }
}

const runOpsAction = async (action: 'outbox' | 'ship-timeout' | 'refund' | 'ship-reminder') => {
  if (!window.confirm('该操作会触发真实运维任务，确认继续？')) return

  try {
    opsActionLoading.value = action
    opsActionMessage.value = ''

    if (action === 'outbox') {
      const res = await publishOutboxOnce(50)
      opsActionMessage.value = `Outbox发布完成：sent=${res.sent ?? 0}，failed=${res.failed ?? 0}`
    } else if (action === 'ship-timeout') {
      const res = await runShipTimeoutOnce(50)
      opsActionMessage.value = `发货超时任务执行完成：success=${res.success ?? 0}`
    } else if (action === 'refund') {
      const res = await runRefundOnce(50)
      opsActionMessage.value = `退款任务执行完成：success=${res.success ?? 0}`
    } else {
      const res = await runShipReminderOnce(50)
      opsActionMessage.value = `发货提醒任务执行完成：success=${res.success ?? 0}`
    }

    await refreshRuntimeData()
  } catch (error: any) {
    opsActionMessage.value = error?.message || '运维动作执行失败'
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
          <div class="flex items-center gap-3 mb-2">
            <h1 class="text-2xl font-bold text-gray-900 tracking-tight">运维中心</h1>
            <span class="px-2 py-0.5 bg-gray-100 text-gray-600 text-[12px] font-medium rounded border border-gray-200">任务调度</span>
          </div>
          <p class="text-[14px] text-gray-500">统一查看运行指标并执行一次性运维动作。</p>
        </div>

        <div class="flex items-center gap-2">
          <span class="text-[12px] text-gray-500 bg-gray-50 border border-gray-200 rounded px-2 py-1">
            <Clock class="w-3.5 h-3.5 inline-block mr-1" />
            {{ lastUpdated ? `最近刷新 ${lastUpdated}` : '未刷新' }}
          </span>
          <button @click="refreshRuntimeData" class="btn-default text-[12px] px-3 py-1.5" :disabled="runtimeLoading">
            <RefreshCw class="w-3.5 h-3.5 mr-1.5" :class="runtimeLoading ? 'animate-spin' : ''" />
            {{ runtimeLoading ? '刷新中...' : '刷新数据' }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="runtimeError" class="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-[13px]">
      <AlertTriangle class="w-4 h-4 inline-block mr-1.5" />
      {{ runtimeError }}
    </div>

    <div class="bg-white border border-gray-200/80 rounded-xl p-6 shadow-sm">
      <h2 class="text-[15px] font-bold text-gray-900 mb-4">运行概览</h2>
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <div class="rounded-lg border border-gray-200 bg-gray-50/50 p-3">
          <p class="text-[12px] text-gray-500">订单总量</p>
          <p class="text-[18px] font-semibold font-numeric text-gray-900">{{ runtimeSnapshot.orderTotal }}</p>
        </div>
        <div class="rounded-lg border border-gray-200 bg-gray-50/50 p-3">
          <p class="text-[12px] text-gray-500">Outbox NEW/FAIL</p>
          <p class="text-[18px] font-semibold font-numeric text-gray-900">{{ runtimeSnapshot.outboxNew }} / {{ runtimeSnapshot.outboxFail }}</p>
        </div>
        <div class="rounded-lg border border-gray-200 bg-gray-50/50 p-3">
          <p class="text-[12px] text-gray-500">发货超时任务</p>
          <p class="text-[18px] font-semibold font-numeric text-gray-900">{{ runtimeSnapshot.shipTimeoutTotal }}</p>
        </div>
        <div class="rounded-lg border border-gray-200 bg-gray-50/50 p-3">
          <p class="text-[12px] text-gray-500">退款任务</p>
          <p class="text-[18px] font-semibold font-numeric text-gray-900">{{ runtimeSnapshot.refundTotal }}</p>
        </div>
        <div class="rounded-lg border border-gray-200 bg-gray-50/50 p-3">
          <p class="text-[12px] text-gray-500">发货提醒任务</p>
          <p class="text-[18px] font-semibold font-numeric text-gray-900">{{ runtimeSnapshot.shipReminderTotal }}</p>
        </div>
        <div class="rounded-lg border border-gray-200 bg-gray-50/50 p-3">
          <p class="text-[12px] text-gray-500">Outbox SENT</p>
          <p class="text-[18px] font-semibold font-numeric text-gray-900">{{ runtimeSnapshot.outboxSent }}</p>
        </div>
        <div class="rounded-lg border border-gray-200 bg-gray-50/50 p-3 lg:col-span-2">
          <p class="text-[12px] text-gray-500">违规类型 Top1</p>
          <p class="text-[16px] font-semibold text-gray-900 mt-0.5">
            {{ runtimeSnapshot.topViolationType }}
            <span class="font-numeric text-gray-600 ml-1">({{ runtimeSnapshot.topViolationCount }})</span>
          </p>
        </div>
      </div>
    </div>

    <div class="bg-white border border-gray-200/80 rounded-xl p-6 shadow-sm">
      <h2 class="text-[15px] font-bold text-gray-900 mb-4">一次性运维动作</h2>
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        <button class="btn-default justify-start px-4 py-2" :disabled="!!opsActionLoading" @click="runOpsAction('outbox')">
          <PlayCircle class="w-4 h-4 mr-2" />
          {{ opsActionLoading === 'outbox' ? '执行中...' : '执行 Outbox 发布一次' }}
        </button>
        <button class="btn-default justify-start px-4 py-2" :disabled="!!opsActionLoading" @click="runOpsAction('ship-timeout')">
          <PlayCircle class="w-4 h-4 mr-2" />
          {{ opsActionLoading === 'ship-timeout' ? '执行中...' : '执行发货超时任务一次' }}
        </button>
        <button class="btn-default justify-start px-4 py-2" :disabled="!!opsActionLoading" @click="runOpsAction('refund')">
          <PlayCircle class="w-4 h-4 mr-2" />
          {{ opsActionLoading === 'refund' ? '执行中...' : '执行退款任务一次' }}
        </button>
        <button class="btn-default justify-start px-4 py-2" :disabled="!!opsActionLoading" @click="runOpsAction('ship-reminder')">
          <PlayCircle class="w-4 h-4 mr-2" />
          {{ opsActionLoading === 'ship-reminder' ? '执行中...' : '执行发货提醒任务一次' }}
        </button>
      </div>
      <p v-if="opsActionMessage" class="text-[12px] text-gray-600 mt-3">{{ opsActionMessage }}</p>
    </div>

    <div class="bg-gray-50/60 border border-gray-200 rounded-xl p-5 text-[13px] text-gray-600">
      <p class="flex items-start gap-2">
        <Wrench class="w-4 h-4 mt-0.5 text-gray-500 shrink-0" />
        运维动作会触发真实任务，请在低峰期并使用有权限账号执行。
      </p>
    </div>
  </div>
</template>
