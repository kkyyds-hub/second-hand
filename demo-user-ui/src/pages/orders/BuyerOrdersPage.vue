<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ChevronLeft, ChevronRight, Loader2, PackageSearch } from 'lucide-vue-next'
import {
  createBuyerOrder,
  createEmptyBuyerOrderPage,
  getBuyerOrderList,
  getBuyerOrderStatusMeta,
  type BuyerOrderSummary,
  type CreateBuyerOrderResult,
} from '@/api/orders'

const loading = ref(false)
const hasLoadedOnce = ref(false)
const errorMessage = ref('')
const pageData = ref(createEmptyBuyerOrderPage())

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

const list = computed<BuyerOrderSummary[]>(() => pageData.value.list)
const totalPages = computed(() => Math.max(1, Math.ceil(pageData.value.total / pageData.value.pageSize)))
const hasPrevPage = computed(() => pagination.page > 1)
const hasNextPage = computed(() => pagination.page < totalPages.value)
const hasEmptyState = computed(() => !loading.value && hasLoadedOnce.value && !errorMessage.value && list.value.length === 0)

const createSubmitting = ref(false)
const createErrorMessage = ref('')
const createSuccessMessage = ref('')
const createdOrderId = ref<number | null>(null)

const createForm = reactive({
  productId: '',
  shippingAddress: '',
})

const canSubmitCreate = computed(() => {
  const productId = readPositiveProductId(createForm.productId)
  const shippingAddress = readShippingAddress(createForm.shippingAddress)
  return !createSubmitting.value && productId !== null && shippingAddress.length >= 5 && shippingAddress.length <= 200
})

function readPositiveProductId(value: unknown) {
  if (typeof value === 'number' && Number.isInteger(value) && value > 0) {
    return value
  }

  if (typeof value === 'string') {
    const normalized = value.trim()
    if (/^\d+$/.test(normalized)) {
      const parsed = Number(normalized)
      if (parsed > 0) {
        return parsed
      }
    }
  }

  return null
}

function readShippingAddress(value: string) {
  return value.trim()
}

function readErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return '买家订单列表加载失败，请稍后重试。'
}

function readStatusChipClass(status: string) {
  const meta = getBuyerOrderStatusMeta(status)

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

function clearCreateMessages() {
  if (createSubmitting.value) {
    return
  }

  createErrorMessage.value = ''
  createSuccessMessage.value = ''
  createdOrderId.value = null
}

function readCreateValidationError() {
  if (readPositiveProductId(createForm.productId) === null) {
    return '请输入有效的商品 ID。'
  }

  const shippingAddress = readShippingAddress(createForm.shippingAddress)
  if (shippingAddress.length < 5 || shippingAddress.length > 200) {
    return '收货地址长度需在 5~200 个字符之间。'
  }

  return ''
}

function readCreateSuccessMessage(result: CreateBuyerOrderResult) {
  const orderNo = result.orderNo || '-'
  const statusLabel = result.statusLabel || '待确认'
  return `下单成功，订单号 ${orderNo}，当前状态：${statusLabel}。`
}

async function submitCreateOrder() {
  if (createSubmitting.value) {
    return
  }

  const validationError = readCreateValidationError()
  if (validationError) {
    createErrorMessage.value = validationError
    createSuccessMessage.value = ''
    createdOrderId.value = null
    return
  }

  const productId = readPositiveProductId(createForm.productId)
  if (productId === null) {
    createErrorMessage.value = '请输入有效的商品 ID。'
    return
  }

  try {
    createSubmitting.value = true
    createErrorMessage.value = ''
    createSuccessMessage.value = ''
    createdOrderId.value = null

    const result = await createBuyerOrder({
      productId,
      shippingAddress: readShippingAddress(createForm.shippingAddress),
    })

    createSuccessMessage.value = readCreateSuccessMessage(result)
    createdOrderId.value = result.orderId

    /**
     * create 成功后主动刷新列表：
     * - 让用户能在同页看到新订单读路径；
     * - 只做“代码层状态同步”，不在本线程宣称 runtime 验证结果。
     */
    pagination.page = 1
    await loadList()
  } catch (error: unknown) {
    createErrorMessage.value = readErrorMessage(error)
    createSuccessMessage.value = ''
    createdOrderId.value = null
  } finally {
    createSubmitting.value = false
  }
}

async function loadList() {
  if (loading.value) {
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''

    const payload = await getBuyerOrderList({
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
  loadList()
})
</script>

<template>
  <div class="page-body">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">订单</p>
          <h1 class="page-title">我的买家订单</h1>
          <p class="page-desc">Day05 Package-2 先补买家下单 create，再在详情页接支付 pay / mock-pay 演示辅助。</p>
        </div>
      </div>
    </section>

    <section class="section-panel">
      <div class="section-header">
        <div>
          <h2 class="section-heading">快捷下单（create）</h2>
          <p class="section-subtitle">当前包只接入 `POST /user/orders` 最小写链路，数量固定 1，由后端状态机判定可下单性。</p>
        </div>
      </div>
      <form class="section-body space-y-4" @submit.prevent="submitCreateOrder">
        <div v-if="createErrorMessage" class="notice-banner notice-banner-danger">
          <span class="notice-dot bg-red-500"></span>
          <span>{{ createErrorMessage }}</span>
        </div>
        <div v-if="createSuccessMessage" class="notice-banner notice-banner-success">
          <span class="notice-dot bg-emerald-500"></span>
          <span>{{ createSuccessMessage }}</span>
        </div>

        <div class="grid gap-4 md:grid-cols-[220px_minmax(0,1fr)]">
          <div>
            <label class="form-label" for="buyer-create-product-id">商品 ID</label>
            <input
              id="buyer-create-product-id"
              v-model="createForm.productId"
              class="input-standard"
              type="text"
              inputmode="numeric"
              placeholder="例如 10001"
              :disabled="createSubmitting"
              @input="clearCreateMessages"
            />
          </div>
          <div>
            <label class="form-label" for="buyer-create-shipping-address">收货地址</label>
            <input
              id="buyer-create-shipping-address"
              v-model="createForm.shippingAddress"
              class="input-standard"
              type="text"
              maxlength="200"
              placeholder="请填写完整收货地址（5~200 字）"
              :disabled="createSubmitting"
              @input="clearCreateMessages"
            />
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-3">
          <button class="btn-primary" type="submit" :disabled="!canSubmitCreate">
            <Loader2 v-if="createSubmitting" class="h-4 w-4 animate-spin" />
            <span>{{ createSubmitting ? '下单中...' : '提交下单' }}</span>
          </button>
          <router-link v-if="createdOrderId !== null" class="btn-default" :to="`/orders/buyer/${createdOrderId}`">查看新订单</router-link>
        </div>
      </form>
    </section>

    <section class="toolbar">
      <form class="w-full" @submit.prevent="submitFilters">
        <div class="flex flex-col gap-4 md:flex-row md:items-end">
          <div class="toolbar-field max-w-[280px]">
            <label class="form-label" for="buyer-order-status">订单状态</label>
            <select id="buyer-order-status" v-model="filters.status" class="input-standard" :disabled="loading">
              <option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
          </div>
          <div class="w-full md:w-36">
            <label class="form-label" for="buyer-order-page-size">每页条数</label>
            <select id="buyer-order-page-size" v-model.number="filters.pageSize" class="input-standard" :disabled="loading">
              <option :value="10">10</option>
              <option :value="20">20</option>
              <option :value="50">50</option>
            </select>
          </div>
          <div class="toolbar-group md:pl-2">
            <button class="btn-primary" type="submit" :disabled="loading">
              <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
              <span>{{ loading ? '加载中' : '应用筛选' }}</span>
            </button>
            <button class="btn-default" type="button" :disabled="loading" @click="resetFilters">重置</button>
          </div>
        </div>
      </form>
    </section>

    <section v-if="errorMessage" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <div class="flex-1">
        <p class="font-semibold">订单列表加载失败</p>
        <p class="mt-1 text-[12px] leading-5">{{ errorMessage }}</p>
        <button class="btn-default mt-3" type="button" :disabled="loading" @click="loadList">重新加载</button>
      </div>
    </section>

    <section class="section-panel">
      <div class="section-header">
        <div>
          <h2 class="section-heading">买家订单列表</h2>
          <p class="section-subtitle">共 {{ pageData.total }} 条，当前第 {{ pageData.page }} / {{ totalPages }} 页。</p>
        </div>
        <div class="section-actions">
          <span class="chip chip-muted font-numeric">总数 {{ pageData.total }}</span>
        </div>
      </div>
      <div class="section-body">
        <div v-if="loading && !hasLoadedOnce" class="empty-state min-h-[320px]">
          <Loader2 class="empty-state-icon animate-spin text-blue-500" />
          <p class="empty-state-title">正在加载订单列表</p>
        </div>

        <div v-else-if="hasEmptyState" class="empty-state min-h-[320px]">
          <PackageSearch class="empty-state-icon" />
          <p class="empty-state-title">当前条件下暂无订单</p>
          <p class="empty-state-text">可以切换状态筛选后重试。</p>
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
                  <span>单价 ¥{{ item.dealPrice.toFixed(2) }}</span>
                  <span class="inline-meta-dot"></span>
                  <span>数量 {{ item.quantity }}</span>
                  <span class="inline-meta-dot"></span>
                  <span>卖家 {{ item.sellerNickname || '-' }}</span>
                </div>
                <p class="mt-2 text-[12px] text-gray-500">下单时间：{{ item.createTime || '-' }}</p>
              </div>

              <div class="flex flex-wrap items-center gap-2 border-t border-gray-100 pt-4">
                <router-link v-if="item.orderId !== null" class="btn-default !h-9 px-3" :to="`/orders/buyer/${item.orderId}`">
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
  </div>
</template>
