<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ChevronLeft, ChevronRight, Loader2, PackageSearch } from 'lucide-vue-next'
import {
  createEmptySellerOrderPage,
  getSellerOrderList,
  getSellerOrderStatusMeta,
  type SellerOrderSummary,
} from '@/api/orders'
import { getUserDisplayName, isSellerUser, readCurrentUser } from '@/utils/request'

const currentUser = readCurrentUser()
const sellerEnabled = computed(() => isSellerUser(currentUser))
const sellerName = computed(() => getUserDisplayName(currentUser))

const loading = ref(false)
const hasLoadedOnce = ref(false)
const errorMessage = ref('')
const pageData = ref(createEmptySellerOrderPage())

const filters = reactive({
  status: '',
  pageSize: 10,
})

const pagination = reactive({
  page: 1,
})

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'pending', label: '待支付' },
  { value: 'paid', label: '待发货' },
  { value: 'shipped', label: '已发货' },
  { value: 'completed', label: '已完成' },
  { value: 'cancelled', label: '已取消' },
]

const list = computed<SellerOrderSummary[]>(() => pageData.value.list)
const totalPages = computed(() => Math.max(1, Math.ceil(pageData.value.total / pageData.value.pageSize)))
const hasPrevPage = computed(() => pagination.page > 1)
const hasNextPage = computed(() => pagination.page < totalPages.value)
const hasEmptyState = computed(() => !loading.value && hasLoadedOnce.value && !errorMessage.value && list.value.length === 0)

function readErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return '卖家订单列表加载失败，请稍后重试。'
}

function readStatusChipClass(status: string) {
  const meta = getSellerOrderStatusMeta(status)

  if (meta.tone === 'accent') {
    return 'chip chip-accent'
  }

  if (meta.tone === 'success') {
    return 'chip chip-success'
  }

  if (meta.tone === 'warning') {
    return 'chip chip-warning'
  }

  return 'chip chip-neutral'
}

async function loadList() {
  if (loading.value || !sellerEnabled.value) {
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''

    const payload = await getSellerOrderList({
      status: filters.status || undefined,
      page: pagination.page,
      pageSize: filters.pageSize,
    })

    pageData.value = payload
    pagination.page = payload.page
  } catch (error: unknown) {
    errorMessage.value = readErrorMessage(error)
  } finally {
    loading.value = false
    hasLoadedOnce.value = true
  }
}

function submitFilters() {
  pagination.page = 1
  loadList()
}

function resetFilters() {
  filters.status = ''
  filters.pageSize = 10
  pagination.page = 1
  loadList()
}

function changePage(nextPage: number) {
  if (nextPage < 1 || nextPage > totalPages.value || nextPage === pagination.page) {
    return
  }

  pagination.page = nextPage
  loadList()
}

onMounted(() => {
  /**
   * 卖家订单页沿用 Day04 workbench 的本地 seller 快照口径：
   * - 本地 session 若已明确不是 seller，页面先停在前端提示；
   * - 真正的权限裁定继续以 `/user/orders/sell` 后端校验为准；
   * - 本线程只做代码落地，不在这里越权裁定 runtime 身份状态。
   */
  if (sellerEnabled.value) {
    loadList()
  } else {
    hasLoadedOnce.value = true
  }
})
</script>

<template>
  <div class="page-body">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">卖家履约</p>
          <h1 class="page-title">卖家订单列表</h1>
          <p class="page-desc">
            {{ sellerName }}，这里承接 Day06 Package-1 的 seller fulfillment 主链第一站：列表筛选、详情进入、物流查看与发货动作入口。
          </p>
        </div>
        <div class="page-actions">
          <span class="chip" :class="sellerEnabled ? 'chip-accent' : 'chip-warning'">
            {{ sellerEnabled ? '卖家态已启用' : '等待卖家身份' }}
          </span>
        </div>
      </div>
    </section>

    <section v-if="!sellerEnabled" class="notice-banner notice-banner-warning">
      <span class="notice-dot bg-orange-500"></span>
      <span>当前账号的本地 session 快照未显示卖家身份，因此本页先停在提示态；运行态是否可访问仍由后端卖家校验决定。</span>
    </section>

    <template v-else>
      <section class="toolbar">
        <form class="w-full" @submit.prevent="submitFilters">
          <div class="flex flex-col gap-4 md:flex-row md:items-end">
            <div class="toolbar-field max-w-[280px]">
              <label class="form-label" for="seller-order-status">订单状态</label>
              <select id="seller-order-status" v-model="filters.status" class="input-standard" :disabled="loading">
                <option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </div>
            <div class="w-full md:w-36">
              <label class="form-label" for="seller-order-page-size">每页条数</label>
              <select id="seller-order-page-size" v-model.number="filters.pageSize" class="input-standard" :disabled="loading">
                <option :value="10">10</option>
                <option :value="20">20</option>
                <option :value="50">50</option>
              </select>
            </div>
            <div class="toolbar-group md:pl-2">
              <button class="btn-primary" type="submit" :disabled="loading">
                <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
                <span>{{ loading ? '加载中...' : '应用筛选' }}</span>
              </button>
              <button class="btn-default" type="button" :disabled="loading" @click="resetFilters">重置</button>
            </div>
          </div>
        </form>
      </section>

      <section v-if="errorMessage" class="notice-banner notice-banner-danger">
        <span class="notice-dot bg-red-500"></span>
        <div class="flex-1">
          <p class="font-semibold">卖家订单列表加载失败</p>
          <p class="mt-1 text-[12px] leading-5">{{ errorMessage }}</p>
          <button class="btn-default mt-3" type="button" :disabled="loading" @click="loadList">重新加载</button>
        </div>
      </section>

      <section class="section-panel">
        <div class="section-header">
          <div>
            <h2 class="section-heading">卖家订单</h2>
            <p class="section-subtitle">共 {{ pageData.total }} 条，当前第 {{ pageData.page }} / {{ totalPages }} 页。</p>
          </div>
          <div class="section-actions">
            <span class="chip chip-muted font-numeric">总数 {{ pageData.total }}</span>
          </div>
        </div>
        <div class="section-body">
          <div v-if="loading && !hasLoadedOnce" class="empty-state min-h-[320px]">
            <Loader2 class="empty-state-icon animate-spin text-blue-500" />
            <p class="empty-state-title">正在同步卖家订单列表</p>
          </div>

          <div v-else-if="hasEmptyState" class="empty-state min-h-[320px]">
            <PackageSearch class="empty-state-icon" />
            <p class="empty-state-title">当前条件下暂无卖家订单</p>
            <p class="empty-state-text">可以切换状态筛选或稍后重新加载，再继续进入详情执行发货与物流查看。</p>
            <button class="btn-default mt-4" type="button" :disabled="loading" @click="loadList">重新加载</button>
          </div>

          <div v-else class="space-y-4">
            <article v-for="item in list" :key="item.orderId ?? item.orderNo" class="list-card-item">
              <div class="flex flex-col gap-4">
                <div class="min-w-0 flex-1">
                  <div class="flex flex-wrap items-center gap-2">
                    <h3 class="text-[16px] font-semibold text-gray-900">订单号：{{ item.orderNo || '-' }}</h3>
                    <span :class="readStatusChipClass(item.status)">{{ item.statusLabel }}</span>
                  </div>
                  <p class="mt-2 text-[13px] leading-6 text-gray-600">{{ item.productTitle || '商品标题待确认' }}</p>
                  <div class="inline-meta mt-3 font-numeric">
                    <span>买家 {{ item.buyerNickname || '-' }}</span>
                    <span class="inline-meta-dot"></span>
                    <span>单价 ¥ {{ item.dealPrice.toFixed(2) }}</span>
                    <span class="inline-meta-dot"></span>
                    <span>数量 {{ item.quantity }}</span>
                  </div>
                  <div class="inline-meta mt-2">
                    <span>下单时间：{{ item.createTime || '-' }}</span>
                    <span class="inline-meta-dot"></span>
                    <span>物流：{{ item.shippingCompany || '待补充' }}</span>
                    <span v-if="item.trackingNo" class="inline-meta-dot"></span>
                    <span v-if="item.trackingNo">运单号：{{ item.trackingNo }}</span>
                  </div>
                </div>

                <div class="flex flex-wrap items-center gap-2 border-t border-gray-100 pt-4">
                  <router-link v-if="item.orderId !== null" class="btn-default !h-9 px-3" :to="`/orders/seller/${item.orderId}`">
                    查看详情
                  </router-link>
                </div>
              </div>
            </article>

            <div class="pagination-bar">
              <div class="inline-meta">
                <span class="chip chip-neutral font-numeric">第 {{ pagination.page }} / {{ totalPages }} 页</span>
              </div>
              <div class="flex gap-2">
                <button class="btn-default !h-9 px-3.5" type="button" :disabled="!hasPrevPage || loading" @click="changePage(pagination.page - 1)">
                  <ChevronLeft class="h-4 w-4" />
                  <span>上一页</span>
                </button>
                <button class="btn-default !h-9 px-3.5" type="button" :disabled="!hasNextPage || loading" @click="changePage(pagination.page + 1)">
                  <span>下一页</span>
                  <ChevronRight class="h-4 w-4" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>
