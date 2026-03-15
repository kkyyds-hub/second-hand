<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
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
  X,
} from 'lucide-vue-next'

type SettingStatusType = 'success' | 'warning' | 'pending' | 'info'
type EntryMode = 'route' | 'modal' | 'static'

const router = useRouter()
const showTradeConfigModal = ref(false)

/**
 * 系统设置页本质是“配置模块目录”：
 * 每个模块同时描述展示文案、状态色和进入方式。
 * review 时先看这里，就能知道为什么有的跳路由、有的开弹窗、有的只做静态概览。
 */
const settingModules = [
  {
    id: 'account',
    title: '账号与权限',
    category: '权限配置',
    description: '管理后台运营账号、角色分配和操作权限边界。',
    summary: '账号角色、菜单权限和操作边界已纳入统一配置体系，可继续在用户模块内查看细项。',
    icon: Users,
    colorClass: 'text-blue-600 bg-blue-50 border-blue-100',
    status: '已接入',
    statusType: 'success' as SettingStatusType,
    entryMode: 'route' as EntryMode,
    scopeTag: '支持进入',
  },
  {
    id: 'notification',
    title: '通知与消息',
    category: '消息策略',
    description: '配置站内信模板、消息接收人及告警通知策略。',
    summary: '通知模板与接收策略已纳入统一管理，支持以概览视图查看整体配置状态。',
    icon: Bell,
    colorClass: 'text-orange-600 bg-orange-50 border-orange-100',
    status: '已接入',
    statusType: 'success' as SettingStatusType,
    entryMode: 'static' as EntryMode,
    scopeTag: '状态概览',
  },
  {
    id: 'risk',
    title: '风控策略',
    category: '风险规则',
    description: '维护高危行为规则、拦截阈值和处罚策略。',
    summary: '风险拦截阈值与处罚策略已经启用，可继续进入审核运营模块查看关联处理链路。',
    icon: ShieldAlert,
    colorClass: 'text-red-600 bg-red-50 border-red-100',
    status: '已启用',
    statusType: 'success' as SettingStatusType,
    entryMode: 'route' as EntryMode,
    scopeTag: '支持进入',
  },
  {
    id: 'product',
    title: '商品审核规则',
    category: '审核规则',
    description: '定义不同品类的审核标准与违规处理流程。',
    summary: '商品审核规则已经形成主结构，当前重点关注品类细则与违规处置口径的一致性。',
    icon: ShoppingBag,
    colorClass: 'text-indigo-600 bg-indigo-50 border-indigo-100',
    status: '重点关注',
    statusType: 'warning' as SettingStatusType,
    entryMode: 'route' as EntryMode,
    scopeTag: '重点关注',
  },
  {
    id: 'trade',
    title: '交易与订单配置',
    category: '交易参数',
    description: '设置订单超时、售后时效和资金侧基础参数。',
    summary: '集中查看订单时效、售后时效与资金参数的管理口径，便于变更前统一确认。',
    icon: CreditCard,
    colorClass: 'text-emerald-600 bg-emerald-50 border-emerald-100',
    status: '参数总览',
    statusType: 'pending' as SettingStatusType,
    entryMode: 'modal' as EntryMode,
    scopeTag: '关键参数',
  },
  {
    id: 'system',
    title: '系统日志与审计',
    category: '审计信息',
    description: '查看关键操作、审计日志和服务运行信息。',
    summary: '集中查看系统日志与审计信息，便于确认关键配置与重要操作状态。',
    icon: Activity,
    colorClass: 'text-slate-600 bg-slate-50 border-slate-100',
    status: '审计概览',
    statusType: 'info' as SettingStatusType,
    entryMode: 'static' as EntryMode,
    scopeTag: '审计概览',
  },
]

/**
 * 下面几个 helper 专门把“状态语义”翻译成 UI 样式和 CTA 文案，
 * 避免模板里堆条件分支，review 时也更容易核对一套状态是否一致。
 */
const formatCount = (value: number) => `${value} 个`

const getStatusBadgeClass = (statusType: SettingStatusType) => {
  if (statusType === 'success') return 'status-chip-success'
  if (statusType === 'warning') return 'status-chip-warning'
  if (statusType === 'pending') return 'status-chip-neutral'
  return 'status-chip-info'
}

const getCardSurfaceClass = (statusType: SettingStatusType) => {
  if (statusType === 'success') return 'from-white via-white to-emerald-50/50'
  if (statusType === 'warning') return 'from-white via-white to-orange-50/55'
  if (statusType === 'pending') return 'from-white via-white to-gray-50/80'
  return 'from-white via-white to-blue-50/45'
}

const getFooterText = (entryMode: EntryMode, statusType: SettingStatusType) => {
  if (entryMode === 'route') return '支持查看模块详情'
  if (entryMode === 'modal') return '可查看关键参数口径'
  return statusType === 'success' ? '当前展示核心状态概览' : '当前展示管理概览'
}

const getActionText = (entryMode: EntryMode, statusType: SettingStatusType) => {
  if (entryMode === 'route') return '进入模块'
  if (entryMode === 'modal') return '查看参数'
  return statusType === 'success' ? '状态概览' : '管理概览'
}

const getActionClass = (entryMode: EntryMode, statusType: SettingStatusType) => {
  if (entryMode === 'route' || entryMode === 'modal') return 'text-gray-700 group-hover:text-gray-900'
  return statusType === 'success' ? 'text-gray-500' : 'text-gray-400'
}

const headerScopeTags = ['规则配置', '参数管理', '审计摘要']

/**
 * moduleCards 是模板真正消费的数据层，
 * 会把原始模块配置补齐 clickable、badgeClass、footerText 等派生字段。
 */
const moduleCards = computed(() =>
  settingModules.map((mod) => {
    const clickable = mod.entryMode !== 'static'

    return {
      ...mod,
      clickable,
      badgeClass: getStatusBadgeClass(mod.statusType),
      surfaceClass: getCardSurfaceClass(mod.statusType),
      footerText: getFooterText(mod.entryMode, mod.statusType),
      actionText: getActionText(mod.entryMode, mod.statusType),
      actionClass: getActionClass(mod.entryMode, mod.statusType),
      entryTag: clickable ? '支持查看详情' : '概览查看',
    }
  }),
)

const statusOverviewCards = computed(() => [
  {
    label: '稳定项',
    value: formatCount(settingModules.filter((item) => item.statusType === 'success').length),
    hint: '核心配置已生效',
    className: 'border-emerald-200 bg-emerald-50/70',
  },
  {
    label: '重点关注',
    value: formatCount(settingModules.filter((item) => item.statusType === 'warning' || item.statusType === 'pending').length),
    hint: '持续核对关键配置',
    className: 'border-orange-200 bg-orange-50/70',
  },
])

const systemStatusItems = [
  {
    label: '权限体系',
    description: '账号角色与操作边界状态正常。',
    status: '正常',
    icon: CheckCircle2,
    iconClass: 'border-emerald-200 bg-emerald-50 text-emerald-700',
    badgeClass: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  },
  {
    label: '通知链路',
    description: '模板与通知接收策略保持可用。',
    status: '正常',
    icon: CheckCircle2,
    iconClass: 'border-emerald-200 bg-emerald-50 text-emerald-700',
    badgeClass: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  },
  {
    label: '风控规则',
    description: '风控阈值与拦截策略当前已启用。',
    status: '已启用',
    icon: ShieldAlert,
    iconClass: 'border-red-200 bg-red-50 text-red-700',
    badgeClass: 'border-red-200 bg-red-50 text-red-700',
  },
  {
    label: '高级配置',
    description: '交易参数与扩展配置需结合业务策略持续校准。',
    status: '重点关注',
    icon: Clock,
    iconClass: 'border-orange-200 bg-orange-50 text-orange-700',
    badgeClass: 'border-orange-200 bg-orange-50 text-orange-700',
  },
]

/**
 * 入口动作按模块类型分流：
 * - route: 进入已有业务页面
 * - modal: 在当前页打开参数说明
 * - static: 只展示概览，不响应点击
 */
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
  <div class="mx-auto max-w-[1600px] space-y-8 pb-8">
    <div class="relative overflow-hidden rounded-xl border border-gray-200/80 bg-white p-6 shadow-sm">
      <div class="pointer-events-none absolute right-0 top-0 h-full w-72 bg-gradient-to-l from-gray-50 to-transparent"></div>
      <div class="relative z-10 space-y-4">
        <div>
          <div class="mb-2 flex flex-wrap items-center gap-3">
            <h1 class="text-2xl font-bold tracking-tight text-gray-900">系统设置</h1>
            <span class="rounded border border-gray-200 bg-gray-100 px-2 py-0.5 text-[12px] font-medium text-gray-600">
              配置中心
            </span>
          </div>
          <p class="text-[14px] text-gray-500">统一管理账号权限、通知策略、风控规则和系统基础配置。</p>
        </div>

        <div class="flex flex-wrap gap-2">
          <span
            v-for="item in headerScopeTags"
            :key="item"
            class="status-chip status-chip-muted"
          >
            {{ item }}
          </span>
        </div>

        <div class="state-banner state-banner-info max-w-[920px]">
          <div class="state-banner-main">
            <span class="state-banner-icon border-blue-200">
              <Info class="h-4 w-4 text-blue-600" />
            </span>
            <div>
              <p class="state-banner-title">配置协同提示</p>
              <p class="state-banner-text text-blue-700/90">系统设置聚焦规则、参数与审计信息查看；执行类任务请前往“运维中心”。</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-3">
      <div
        v-for="mod in moduleCards"
        :key="mod.id"
        class="group relative overflow-hidden rounded-2xl border border-gray-200/80 bg-gradient-to-br p-5 shadow-sm transition-all"
        :class="[mod.surfaceClass, mod.clickable ? 'cursor-pointer hover:-translate-y-0.5 hover:border-gray-300 hover:shadow-md' : '']"
        @click="handleCardClick(mod.id)"
      >
        <div class="pointer-events-none absolute -right-6 -top-6 h-24 w-24 rounded-full bg-white/70 blur-2xl"></div>

        <div class="relative z-10 flex h-full flex-col">
          <div class="card-header mb-4">
            <div class="flex items-start gap-3">
              <div class="rounded-xl border p-3" :class="mod.colorClass">
                <component :is="mod.icon" class="h-5 w-5" />
              </div>
              <div>
                <p class="card-kicker">{{ mod.category }}</p>
                <h2 class="mt-1 text-[16px] font-bold text-gray-900">{{ mod.title }}</h2>
              </div>
            </div>

            <span class="status-chip" :class="mod.badgeClass">
              {{ mod.status }}
            </span>
          </div>

          <p class="min-h-[48px] text-[13px] leading-6 text-gray-600">
            {{ mod.description }}
          </p>

          <div class="mt-4 rounded-xl border border-white/70 bg-white/80 p-4 backdrop-blur-sm">
            <p class="text-[12px] text-gray-500">当前状态</p>
            <p class="mt-2 text-[13px] font-medium leading-6 text-gray-900">
              {{ mod.summary }}
            </p>

            <div class="mt-3 flex flex-wrap gap-2">
              <span class="status-chip status-chip-neutral">
                {{ mod.scopeTag }}
              </span>
              <span class="status-chip status-chip-neutral">
                {{ mod.entryTag }}
              </span>
            </div>
          </div>

          <div class="mt-5 flex items-center justify-between border-t border-gray-100 pt-4">
            <span class="flex items-center gap-1.5 text-[12px] text-gray-500">
              <Settings class="h-3.5 w-3.5" />
              {{ mod.footerText }}
            </span>

            <span class="text-[13px] font-medium transition-colors" :class="mod.actionClass">
              {{ mod.actionText }}<span v-if="mod.clickable"> →</span>
            </span>
          </div>
        </div>
      </div>
    </div>

    <div class="mt-4 grid grid-cols-1 gap-6 lg:grid-cols-3">
      <div class="lg:col-span-2 rounded-2xl border border-gray-200/80 bg-gradient-to-br from-gray-50 via-white to-white p-6 shadow-sm">
        <div class="section-header">
          <div>
            <div class="section-title-row">
              <Info class="h-4.5 w-4.5 text-gray-500" />
              <h3 class="section-title">配置中心使用说明</h3>
            </div>
            <p class="section-desc">统一查看配置边界、变更要求与职责分工。</p>
          </div>
        </div>

        <div class="space-y-3">
          <div class="rounded-xl border border-gray-100 bg-white/90 px-4 py-3 text-[13px] leading-6 text-gray-600">
            <strong class="text-gray-900">全局影响：</strong>配置变更会影响运营策略和风控逻辑，请在变更前完成复核。
          </div>
          <div class="rounded-xl border border-gray-100 bg-white/90 px-4 py-3 text-[13px] leading-6 text-gray-600">
            <strong class="text-gray-900">变更留痕：</strong>涉及资金、处罚、权限的配置建议走双人复核并保留审计记录。
          </div>
          <div class="rounded-xl border border-gray-100 bg-white/90 px-4 py-3 text-[13px] leading-6 text-gray-600">
            <strong class="text-gray-900">职责拆分：</strong>系统设置负责“规则与参数”，任务执行请到“运维中心”。
          </div>
        </div>
      </div>

      <div class="rounded-2xl border border-gray-200/80 bg-white p-6 shadow-sm">
        <div class="section-header">
          <div>
            <h3 class="section-title">系统状态摘要</h3>
            <p class="section-desc">从配置生效视角快速确认当前系统状态。</p>
          </div>
          <span class="status-chip status-chip-muted">摘要视图</span>
        </div>

        <div class="grid grid-cols-2 gap-3">
          <div
            v-for="item in statusOverviewCards"
            :key="item.label"
            class="rounded-xl border p-4"
            :class="item.className"
          >
            <p class="text-[12px] text-gray-500">{{ item.label }}</p>
            <p class="mt-2 text-[22px] font-semibold font-numeric text-gray-900">{{ item.value }}</p>
            <p class="mt-1 text-[12px] text-gray-500">{{ item.hint }}</p>
          </div>
        </div>

        <div class="mt-5 space-y-3">
          <div
            v-for="item in systemStatusItems"
            :key="item.label"
            class="rounded-xl border border-gray-100 bg-gray-50/70 p-4"
          >
            <div class="flex items-start justify-between gap-3">
              <div class="flex items-start gap-3">
                <span class="rounded-xl border p-2" :class="item.iconClass">
                  <component :is="item.icon" class="h-4 w-4" />
                </span>
                <div>
                  <p class="text-[13px] font-medium text-gray-900">{{ item.label }}</p>
                  <p class="mt-1 text-[12px] leading-5 text-gray-500">{{ item.description }}</p>
                </div>
              </div>

              <span class="status-chip shrink-0" :class="item.badgeClass">
                {{ item.status }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div
      v-if="showTradeConfigModal"
      class="modal-backdrop"
      @click="showTradeConfigModal = false"
    >
      <div class="modal-panel max-w-md" role="dialog" aria-modal="true" @click.stop>
        <div class="modal-header">
          <div class="flex items-start gap-3">
            <div class="rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-emerald-700">
              <CreditCard class="h-5 w-5" />
            </div>
            <div>
              <div class="flex flex-wrap items-center gap-2">
                <h3 class="modal-title">交易与订单配置</h3>
                <span class="status-chip status-chip-neutral">参数概览</span>
              </div>
              <p class="mt-1 text-sm text-gray-500">集中查看交易与订单的关键管理口径，便于变更前统一确认。</p>
            </div>
          </div>

          <button @click="showTradeConfigModal = false" class="modal-close" aria-label="关闭弹窗">
            <X class="h-4.5 w-4.5" />
          </button>
        </div>

        <div class="modal-body">
          <p>本模块用于汇总订单时效、售后规则与支付管理要点，帮助运营在调整前先完成口径确认。</p>

          <div class="space-y-3 rounded-xl border border-gray-100 bg-gray-50/80 p-4">
            <div>
              <p class="text-xs text-gray-500">管理重点</p>
              <p class="mt-1 text-sm text-gray-900">本页聚焦交易时效、售后规则与支付参数的管理口径，不在此直接执行交易处理动作。</p>
            </div>
            <div>
              <p class="text-xs text-gray-500">重点关注</p>
              <p class="mt-1 text-sm text-gray-900">订单超时、售后时效、支付参数等关键规则需要结合业务策略统一确认。</p>
            </div>
          </div>
        </div>

        <div class="modal-footer">
          <button
            @click="showTradeConfigModal = false"
            class="btn-primary"
          >
            我知道了
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
