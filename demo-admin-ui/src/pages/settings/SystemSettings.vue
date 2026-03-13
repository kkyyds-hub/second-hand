<script setup lang="ts">
import {
  Activity,
  Bell,
  CheckCircle2,
  Clock,
  CreditCard,
  Info,
  Settings,
  ShieldAlert,
  ShoppingBag,
  Users,
} from 'lucide-vue-next'

const settingModules = [
  {
    id: 'account',
    title: '账号与权限',
    description: '管理后台运营账号、角色分配和操作权限边界。',
    icon: Users,
    colorClass: 'text-blue-600 bg-blue-50 border-blue-100',
    status: '已接入',
    statusType: 'success',
  },
  {
    id: 'notification',
    title: '通知与消息',
    description: '配置站内信模板、消息接收人及告警通知策略。',
    icon: Bell,
    colorClass: 'text-orange-600 bg-orange-50 border-orange-100',
    status: '已接入',
    statusType: 'success',
  },
  {
    id: 'risk',
    title: '风控策略',
    description: '维护高危行为规则、拦截阈值和处罚策略。',
    icon: ShieldAlert,
    colorClass: 'text-red-600 bg-red-50 border-red-100',
    status: '已启用',
    statusType: 'success',
  },
  {
    id: 'product',
    title: '商品审核规则',
    description: '定义不同品类的审核标准与违规处理流程。',
    icon: ShoppingBag,
    colorClass: 'text-indigo-600 bg-indigo-50 border-indigo-100',
    status: '待完善',
    statusType: 'warning',
  },
  {
    id: 'trade',
    title: '交易与订单配置',
    description: '设置订单超时、售后时效和资金侧基础参数。',
    icon: CreditCard,
    colorClass: 'text-emerald-600 bg-emerald-50 border-emerald-100',
    status: '建设中',
    statusType: 'pending',
  },
  {
    id: 'system',
    title: '系统日志与审计',
    description: '查看关键操作、审计日志和服务运行信息。',
    icon: Activity,
    colorClass: 'text-slate-600 bg-slate-50 border-slate-100',
    status: '内部配置',
    statusType: 'info',
  },
]

import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const showTradeConfigModal = ref(false)

const handleCardClick = (id: string) => {
  if (id === 'trade') {
    showTradeConfigModal.value = true
  } else if (id === 'account') {
    router.push('/users')
  } else if (id === 'risk') {
    router.push('/audit')
  } else if (id === 'product') {
    router.push('/products')
  }
}
</script>

<template>
  <div class="space-y-8 max-w-[1600px] mx-auto pb-8">
    <div class="bg-white border border-gray-200/80 rounded-xl p-6 shadow-sm relative overflow-hidden">
      <div class="absolute top-0 right-0 w-64 h-full bg-gradient-to-l from-gray-50 to-transparent pointer-events-none"></div>
      <div class="relative z-10 flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <div class="flex items-center gap-3 mb-2">
            <h1 class="text-2xl font-bold text-gray-900 tracking-tight">系统设置</h1>
            <span class="px-2 py-0.5 bg-gray-100 text-gray-600 text-[12px] font-medium rounded border border-gray-200">
              配置中心
            </span>
          </div>
          <p class="text-[14px] text-gray-500">统一管理账号权限、通知策略、风控规则和系统基础配置。</p>
        </div>

        <div class="flex items-center gap-2 text-[12px] text-gray-500 bg-gray-50 px-3 py-1.5 rounded-md border border-gray-100">
          <Info class="w-3.5 h-3.5 text-gray-400" />
          <span>运维动作已迁移到独立菜单“运维中心”</span>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
      <div
        v-for="mod in settingModules"
        :key="mod.id"
        @click="handleCardClick(mod.id)"
        class="bg-white border border-gray-200/80 rounded-xl p-5 flex flex-col hover:border-gray-300 hover:shadow-md transition-all group relative overflow-hidden"
        :class="{ 'cursor-pointer': mod.id === 'trade' || mod.id === 'product' || mod.id === 'account' || mod.id === 'risk' }"
      >
        <div class="flex items-start justify-between mb-4">
          <div class="flex items-center gap-3">
            <div class="p-2.5 rounded-lg border" :class="mod.colorClass">
              <component :is="mod.icon" class="w-5 h-5" />
            </div>
            <h2 class="text-[15px] font-bold text-gray-900">{{ mod.title }}</h2>
          </div>

          <span
            class="text-[11px] px-2 py-0.5 rounded font-medium border"
            :class="{
              'bg-green-50 text-green-700 border-green-200': mod.statusType === 'success',
              'bg-orange-50 text-orange-700 border-orange-200': mod.statusType === 'warning',
              'bg-gray-50 text-gray-600 border-gray-200': mod.statusType === 'pending',
              'bg-blue-50 text-blue-700 border-blue-200': mod.statusType === 'info',
            }"
          >
            {{ mod.status }}
          </span>
        </div>

        <p class="text-[13px] text-gray-500 leading-relaxed flex-1 mb-5">
          {{ mod.description }}
        </p>

        <div class="pt-4 border-t border-gray-100 flex justify-between items-center">
          <span class="text-[12px] text-gray-400 flex items-center gap-1.5">
            <Settings class="w-3.5 h-3.5" />
            {{ mod.statusType === 'success' ? '配置已生效' : '模块规划中' }}
          </span>

          <span
            class="text-[13px] font-medium transition-colors"
            :class="mod.statusType === 'success' ? 'text-gray-600 group-hover:text-gray-900' : 'text-gray-400'"
          >
            {{ mod.statusType === 'success' ? '查看模块 ->' : '即将开放' }}
          </span>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6 mt-4">
      <div class="lg:col-span-2 bg-gray-50/50 border border-gray-200/80 rounded-xl p-6">
        <h3 class="text-[15px] font-bold text-gray-900 mb-4 flex items-center gap-2">
          <Info class="w-4.5 h-4.5 text-gray-500" />
          配置中心使用说明
        </h3>

        <div class="space-y-3 text-[13px] text-gray-600 leading-relaxed">
          <p class="flex items-start gap-2">
            <span class="w-1.5 h-1.5 rounded-full bg-gray-400 mt-1.5 shrink-0"></span>
            <span><strong>全局影响：</strong>配置变更会影响运营策略和风控逻辑，请在变更前完成复核。</span>
          </p>
          <p class="flex items-start gap-2">
            <span class="w-1.5 h-1.5 rounded-full bg-gray-400 mt-1.5 shrink-0"></span>
            <span><strong>变更留痕：</strong>涉及资金、处罚、权限的配置建议走双人复核并保留审计记录。</span>
          </p>
          <p class="flex items-start gap-2">
            <span class="w-1.5 h-1.5 rounded-full bg-gray-400 mt-1.5 shrink-0"></span>
            <span><strong>职责拆分：</strong>系统设置负责“规则与参数”，任务执行请到“运维中心”。</span>
          </p>
        </div>
      </div>

      <div class="bg-white border border-gray-200/80 rounded-xl p-6 shadow-sm">
        <h3 class="text-[15px] font-bold text-gray-900 mb-4">系统状态摘要</h3>

        <div class="space-y-4">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2 text-[13px] text-gray-600">
              <CheckCircle2 class="w-4 h-4 text-green-500" />
              <span>权限体系</span>
            </div>
            <span class="text-[12px] font-medium text-green-700 bg-green-50 px-2 py-0.5 rounded border border-green-100">正常</span>
          </div>

          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2 text-[13px] text-gray-600">
              <CheckCircle2 class="w-4 h-4 text-green-500" />
              <span>通知链路</span>
            </div>
            <span class="text-[12px] font-medium text-green-700 bg-green-50 px-2 py-0.5 rounded border border-green-100">正常</span>
          </div>

          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2 text-[13px] text-gray-600">
              <CheckCircle2 class="w-4 h-4 text-green-500" />
              <span>风控规则</span>
            </div>
            <span class="text-[12px] font-medium text-green-700 bg-green-50 px-2 py-0.5 rounded border border-green-100">已启用</span>
          </div>

          <div class="flex items-center justify-between pt-3 border-t border-gray-100">
            <div class="flex items-center gap-2 text-[13px] text-gray-600">
              <Clock class="w-4 h-4 text-orange-400" />
              <span>高级配置</span>
            </div>
            <span class="text-[12px] font-medium text-orange-700 bg-orange-50 px-2 py-0.5 rounded border border-orange-100">部分建设中</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 交易与订单配置建设中弹窗 -->
    <div v-if="showTradeConfigModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-gray-900/40 backdrop-blur-sm transition-opacity" @click="showTradeConfigModal = false">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-sm overflow-hidden border border-gray-200/80" @click.stop>
        <div class="p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-[16px] font-bold text-gray-900">交易与订单配置建设中</h3>
            <button @click="showTradeConfigModal = false" class="text-gray-400 hover:text-gray-600 transition-colors">
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
            </button>
          </div>
          <div class="text-[13px] text-gray-600 space-y-3 leading-relaxed">
            <p>当前模块暂未开放，后续会补齐订单超时、售后仲裁期限、支付参数等配置能力。</p>
          </div>
        </div>
        <div class="px-6 py-4 bg-gray-50/80 border-t border-gray-100 flex justify-end">
          <button @click="showTradeConfigModal = false" class="px-4 py-2 bg-gray-900 text-white text-[13px] font-medium rounded-lg hover:bg-gray-800 transition-colors shadow-sm">
            我知道了
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
