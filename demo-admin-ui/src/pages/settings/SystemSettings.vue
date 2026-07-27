<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Activity, BookOpen, ReceiptText, Settings, ShieldAlert, ShoppingBag, Users } from 'lucide-vue-next'

const router = useRouter()

const businessEntries = [
  {
    title: '账号与用户治理',
    description: '查看用户资料、信用信息及账号治理操作。',
    path: '/users',
    icon: Users,
    iconClass: 'border-blue-200 bg-blue-50 text-blue-700',
  },
  {
    title: '商品审核',
    description: '处理当前商品审核队列及相应操作。',
    path: '/products',
    icon: ShoppingBag,
    iconClass: 'border-indigo-200 bg-indigo-50 text-indigo-700',
  },
  {
    title: '风控和纠纷',
    description: '查看和处理当前纠纷、举报及风险工单。',
    path: '/audit',
    icon: ShieldAlert,
    iconClass: 'border-red-200 bg-red-50 text-red-700',
  },
  {
    title: '运行任务',
    description: '查看当前运行指标，并按权限执行一次性任务。',
    path: '/ops-center',
    icon: Activity,
    iconClass: 'border-orange-200 bg-orange-50 text-orange-700',
  },
  {
    title: '订单治理',
    description: '查看订单记录、订单详情和异常订单标记。',
    path: '/orders',
    icon: ReceiptText,
    iconClass: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  },
]

const readOnlyBoundaries = [
  {
    title: '统一在线参数修改',
    status: '未接入',
    description: '当前没有统一的在线参数修改接口；交易、售后等参数由 application 配置维护。',
  },
  {
    title: '通知中心与接收策略',
    status: '未接入',
    description: '当前没有通知模板、接收人或消息策略的管理入口。',
  },
  {
    title: '角色与权限配置',
    status: '只读说明',
    description: '本轮不提供完整 RBAC 或角色授权编辑；请使用已有业务入口完成对应操作。',
  },
]
</script>

<template>
  <div class="mx-auto max-w-[1200px] space-y-6 pb-8">
    <header class="border border-gray-200 bg-white p-6">
      <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <div class="flex flex-wrap items-center gap-3">
            <h1 class="text-2xl font-bold text-gray-900">系统能力与配置入口</h1>
            <span class="status-chip status-chip-muted">只读说明</span>
          </div>
          <p class="mt-3 max-w-3xl text-sm leading-6 text-gray-600">
            当前页面用于查看已有业务入口和配置边界。尚未提供统一在线参数修改接口。
          </p>
        </div>
        <span class="flex shrink-0 items-center gap-2 text-xs text-gray-500">
          <Settings class="h-4 w-4" />
          不提供在线保存操作
        </span>
      </div>
    </header>

    <section class="border border-gray-200 bg-white p-6">
      <div class="section-header">
        <div>
          <h2 class="section-title">已有业务入口</h2>
          <p class="section-desc">跳转后以目标页面的当前请求结果和可用操作为准。</p>
        </div>
        <span class="status-chip status-chip-info">已有入口</span>
      </div>

      <div class="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
        <button
          v-for="entry in businessEntries"
          :key="entry.path"
          type="button"
          class="group flex min-h-32 flex-col justify-between border border-gray-200 bg-white p-4 text-left transition-colors hover:border-gray-400 hover:bg-gray-50"
          @click="router.push(entry.path)"
        >
          <div class="flex items-start gap-3">
            <span class="rounded border p-2" :class="entry.iconClass">
              <component :is="entry.icon" class="h-4 w-4" />
            </span>
            <div>
              <h3 class="text-sm font-semibold text-gray-900">{{ entry.title }}</h3>
              <p class="mt-2 text-xs leading-5 text-gray-500">{{ entry.description }}</p>
            </div>
          </div>
          <span class="mt-4 text-xs font-medium text-gray-600 group-hover:text-gray-900">前往入口</span>
        </button>
      </div>
    </section>

    <section class="border border-gray-200 bg-white p-6">
      <div class="section-header">
        <div>
          <h2 class="section-title">当前配置边界</h2>
          <p class="section-desc">以下内容仅用于说明能力范围，不代表存在可操作的线上配置。 </p>
        </div>
        <span class="status-chip status-chip-muted">只读说明</span>
      </div>

      <div class="divide-y divide-gray-100 border-y border-gray-100">
        <article v-for="item in readOnlyBoundaries" :key="item.title" class="flex flex-col gap-3 py-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h3 class="text-sm font-medium text-gray-900">{{ item.title }}</h3>
            <p class="mt-1 max-w-3xl text-xs leading-5 text-gray-500">{{ item.description }}</p>
          </div>
          <span class="status-chip shrink-0" :class="item.status === '未接入' ? 'status-chip-neutral' : 'status-chip-muted'">{{ item.status }}</span>
        </article>
      </div>
    </section>

    <section class="state-banner state-banner-info">
      <div class="state-banner-main">
        <span class="state-banner-icon border-blue-200"><BookOpen class="h-4 w-4 text-blue-600" /></span>
        <div>
          <p class="state-banner-title">操作说明</p>
          <p class="state-banner-text text-blue-700/90">需要处理订单、审核、纠纷或运行任务时，请从上方已有入口进入对应业务页面。</p>
        </div>
      </div>
    </section>
  </div>
</template>
