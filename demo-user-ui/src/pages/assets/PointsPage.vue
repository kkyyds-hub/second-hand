<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Coins, Loader2, RefreshCw } from 'lucide-vue-next'
import { createEmptyPointsLedgerPage, getPointsLedger, getPointsTotal, type PointsLedgerItem } from '@/api/points'

const totalPoints = ref(0)
const ledger = ref(createEmptyPointsLedgerPage())
const loading = ref(false)
const errorMessage = ref('')
const currentPage = ref(1)
const pageSize = 10

const totalPages = computed(() => Math.max(1, Math.ceil(ledger.value.total / ledger.value.pageSize)))
const hasLedger = computed(() => ledger.value.list.length > 0)
const canGoPrev = computed(() => currentPage.value > 1 && !loading.value)
const canGoNext = computed(() => currentPage.value < totalPages.value && !loading.value)

function formatPoints(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value)
}

function formatSignedPoints(item: PointsLedgerItem) {
  const prefix = item.points > 0 ? '+' : ''
  return `${prefix}${formatPoints(item.points)}`
}

function getPointsTone(item: PointsLedgerItem) {
  if (item.points > 0) {
    return 'text-emerald-700'
  }

  if (item.points < 0) {
    return 'text-red-600'
  }

  return 'text-gray-700'
}

function formatBizType(type: string) {
  const normalized = type.trim().toUpperCase()
  const typeMap: Record<string, string> = {
    ORDER_COMPLETED: '订单完成',
    ADMIN_ADJUST: '后台调整',
    ACTIVITY_REWARD: '活动奖励',
  }

  return typeMap[normalized] || type || '积分流水'
}

async function loadPoints(page = currentPage.value) {
  if (loading.value) {
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''
    currentPage.value = page
    const [nextTotal, nextLedger] = await Promise.all([
      getPointsTotal(),
      getPointsLedger({ page, pageSize }),
    ])
    totalPoints.value = nextTotal
    ledger.value = nextLedger
    currentPage.value = nextLedger.page
  } catch (error: unknown) {
    errorMessage.value = error instanceof Error && error.message.trim() ? error.message : '积分数据加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function goPrev() {
  if (canGoPrev.value) {
    loadPoints(currentPage.value - 1)
  }
}

function goNext() {
  if (canGoNext.value) {
    loadPoints(currentPage.value + 1)
  }
}

onMounted(() => {
  loadPoints(1)
})
</script>

<template>
  <div class="page-body">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">资产中心</p>
          <h1 class="page-title">积分总额与流水</h1>
          <p class="page-desc">独立展示当前积分总额与积分流水，避免把积分资产混入口袋余额或订单履约页面。</p>
        </div>
        <div class="page-actions">
          <router-link class="btn-default" to="/assets/wallet">钱包</router-link>
          <router-link class="btn-default" to="/assets/credit">信用</router-link>
          <button class="btn-default" type="button" :disabled="loading" @click="loadPoints(currentPage)">
            <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
            <RefreshCw v-else class="h-4 w-4" />
            <span>{{ loading ? '刷新中' : '刷新积分' }}</span>
          </button>
        </div>
      </div>
    </section>

    <section v-if="errorMessage" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <div>
        <p class="font-semibold">积分数据加载失败</p>
        <p class="mt-1 text-[12px] leading-5">{{ errorMessage }}</p>
      </div>
    </section>

    <section class="grid gap-6 lg:grid-cols-[0.8fr_1.2fr]">
      <section class="section-panel">
        <div class="section-header">
          <div>
            <h2 class="section-heading">积分总额</h2>
            <p class="section-subtitle">接口：GET /user/points/total</p>
          </div>
          <Coins class="h-5 w-5 text-gray-400" />
        </div>
        <div class="section-body">
          <p class="text-[13px] text-gray-500">当前可用积分</p>
          <p class="mt-3 font-numeric text-[34px] font-bold tracking-tight text-gray-900">{{ formatPoints(totalPoints) }}</p>
          <p class="mt-5 rounded-2xl border border-gray-200 bg-gray-50 p-4 text-[12px] leading-6 text-gray-600">
            积分口径独立于钱包余额；本页只承接积分资产展示，不代表订单完成、退款或履约链路重新验收。
          </p>
        </div>
      </section>

      <section class="section-panel">
        <div class="section-header">
          <div>
            <h2 class="section-heading">积分流水</h2>
            <p class="section-subtitle">接口：GET /user/points/ledger?page={{ currentPage }}&pageSize={{ pageSize }}</p>
          </div>
          <span class="chip chip-neutral">共 {{ ledger.total }} 条</span>
        </div>
        <div class="section-body">
          <div v-if="loading" class="empty-state">
            <Loader2 class="empty-state-icon animate-spin" />
            <p class="empty-state-title">正在加载积分流水</p>
          </div>
          <div v-else-if="!hasLedger" class="empty-state">
            <Coins class="empty-state-icon" />
            <p class="empty-state-title">暂无积分流水</p>
            <p class="empty-state-text">完成订单或触发积分规则后会在这里展示积分变化。</p>
          </div>
          <div v-else class="space-y-3">
            <article v-for="item in ledger.list" :key="item.id ?? `${item.bizType}-${item.bizId}-${item.createTime}`" class="list-card-item">
              <div class="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                <div>
                  <p class="text-[14px] font-semibold text-gray-900">{{ formatBizType(item.bizType) }}</p>
                  <p class="mt-2 text-[11px] text-gray-400">业务 ID：{{ item.bizId ?? '-' }} · {{ item.createTime || '时间待确认' }}</p>
                </div>
                <p class="font-numeric text-[18px] font-bold" :class="getPointsTone(item)">{{ formatSignedPoints(item) }}</p>
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
