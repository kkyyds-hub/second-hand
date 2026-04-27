<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Loader2, RefreshCw, ShieldCheck } from 'lucide-vue-next'
import {
  createEmptyCreditLogPage,
  createEmptyCreditOverview,
  getMyCreditLogs,
  getMyCreditOverview,
  type UserCreditLogItem,
} from '@/api/credit'

const overview = ref(createEmptyCreditOverview())
const logs = ref(createEmptyCreditLogPage())
const loading = ref(false)
const errorMessage = ref('')
const currentPage = ref(1)
const pageSize = 10

const totalPages = computed(() => Math.max(1, Math.ceil(logs.value.total / logs.value.pageSize)))
const hasLogs = computed(() => logs.value.list.length > 0)
const canGoPrev = computed(() => currentPage.value > 1 && !loading.value)
const canGoNext = computed(() => currentPage.value < totalPages.value && !loading.value)
const scoreText = computed(() => (overview.value.creditScore === null ? '-' : String(overview.value.creditScore)))

function formatDelta(item: UserCreditLogItem) {
  const prefix = item.delta > 0 ? '+' : ''
  return `${prefix}${item.delta}`
}

function getDeltaTone(item: UserCreditLogItem) {
  if (item.delta > 0) {
    return 'text-emerald-700'
  }

  if (item.delta < 0) {
    return 'text-red-600'
  }

  return 'text-gray-700'
}

function formatScore(value: number | null) {
  return value === null ? '-' : String(value)
}

async function loadCredit(page = currentPage.value) {
  if (loading.value) {
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''
    currentPage.value = page
    const [nextOverview, nextLogs] = await Promise.all([
      getMyCreditOverview(),
      getMyCreditLogs({ page, pageSize }),
    ])
    overview.value = nextOverview
    logs.value = nextLogs
    currentPage.value = nextLogs.page
  } catch (error: unknown) {
    errorMessage.value = error instanceof Error && error.message.trim() ? error.message : '信用数据加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function goPrev() {
  if (canGoPrev.value) {
    loadCredit(currentPage.value - 1)
  }
}

function goNext() {
  if (canGoNext.value) {
    loadCredit(currentPage.value + 1)
  }
}

onMounted(() => {
  loadCredit(1)
})
</script>

<template>
  <div class="page-body">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">资产中心</p>
          <h1 class="page-title">信用概览与流水</h1>
          <p class="page-desc">集中展示当前信用分、信用等级与信用分变更流水；本页只做用户端信用资产视图，不承接后台调分或风控规则配置。</p>
        </div>
        <div class="page-actions">
          <router-link class="btn-default" to="/assets/wallet">钱包</router-link>
          <router-link class="btn-default" to="/assets/points">积分</router-link>
          <button class="btn-default" type="button" :disabled="loading" @click="loadCredit(currentPage)">
            <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
            <RefreshCw v-else class="h-4 w-4" />
            <span>{{ loading ? '刷新中' : '刷新信用' }}</span>
          </button>
        </div>
      </div>
    </section>

    <section v-if="errorMessage" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <div>
        <p class="font-semibold">信用数据加载失败</p>
        <p class="mt-1 text-[12px] leading-5">{{ errorMessage }}</p>
      </div>
    </section>

    <section class="grid gap-6 lg:grid-cols-[0.8fr_1.2fr]">
      <section class="section-panel">
        <div class="section-header">
          <div>
            <h2 class="section-heading">信用概览</h2>
            <p class="section-subtitle">接口：GET /user/credit</p>
          </div>
          <ShieldCheck class="h-5 w-5 text-gray-400" />
        </div>
        <div class="section-body">
          <p class="text-[13px] text-gray-500">当前信用分</p>
          <p class="mt-3 font-numeric text-[34px] font-bold tracking-tight text-gray-900">{{ scoreText }}</p>
          <div class="mt-5 grid gap-3">
            <div class="detail-row">
              <span class="detail-label">信用等级</span>
              <span class="detail-value">{{ overview.creditLevelText }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">更新时间</span>
              <span class="detail-value">{{ overview.creditUpdatedAt || '待确认' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">用户 ID</span>
              <span class="detail-value">{{ overview.userId ?? '-' }}</span>
            </div>
          </div>
          <p class="mt-5 rounded-2xl border border-gray-200 bg-gray-50 p-4 text-[12px] leading-6 text-gray-600">
            信用等级文案由前端按已知 dbValue 做展示映射；若后续风控规则变更，应先回填 Day07 文档再调整页面。
          </p>
        </div>
      </section>

      <section class="section-panel">
        <div class="section-header">
          <div>
            <h2 class="section-heading">信用流水</h2>
            <p class="section-subtitle">接口：GET /user/credit/logs?page={{ currentPage }}&pageSize={{ pageSize }}</p>
          </div>
          <span class="chip chip-neutral">共 {{ logs.total }} 条</span>
        </div>
        <div class="section-body">
          <div v-if="loading" class="empty-state">
            <Loader2 class="empty-state-icon animate-spin" />
            <p class="empty-state-title">正在加载信用流水</p>
          </div>
          <div v-else-if="!hasLogs" class="empty-state">
            <ShieldCheck class="empty-state-icon" />
            <p class="empty-state-title">暂无信用流水</p>
            <p class="empty-state-text">信用分发生调整后会在这里展示原因、分值变化与关联业务。</p>
          </div>
          <div v-else class="space-y-3">
            <article v-for="item in logs.list" :key="item.id ?? `${item.reasonType}-${item.refId}-${item.createTime}`" class="list-card-item">
              <div class="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                <div>
                  <p class="text-[14px] font-semibold text-gray-900">{{ item.reasonText }}</p>
                  <p class="mt-1 text-[12px] text-gray-500">{{ item.reasonNote || '无补充说明' }}</p>
                  <p class="mt-2 text-[11px] text-gray-400">关联 ID：{{ item.refId ?? '-' }} · {{ item.createTime || '时间待确认' }}</p>
                </div>
                <div class="text-left md:text-right">
                  <p class="font-numeric text-[18px] font-bold" :class="getDeltaTone(item)">{{ formatDelta(item) }}</p>
                  <p class="mt-1 text-[12px] text-gray-500">{{ formatScore(item.scoreBefore) }} → {{ formatScore(item.scoreAfter) }}</p>
                </div>
              </div>
            </article>
          </div>
          <div class="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-gray-100 pt-4">
            <p class="text-[12px] text-gray-500">第 {{ currentPage }} / {{ totalPages }} 页</p>
            <div class="flex gap-2">
              <button class="btn-default" type="button" :disabled="!canGoPrev" @click="goPrev">上一页</button>
              <button class="btn-default" type="button" :disabled="!canGoNext" @click="goNext">下一页</button>
            </div>
          </div>
        </div>
      </section>
    </section>
  </div>
</template>
