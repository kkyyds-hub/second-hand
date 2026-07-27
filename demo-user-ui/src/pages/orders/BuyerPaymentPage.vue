<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft, Loader2, PackageSearch } from 'lucide-vue-next'
import {
  createEmptyBuyerOrderDetail,
  getBuyerOrderDetail,
  getOrderStatusMeta,
  payBuyerOrder,
  type BuyerOrderDetail,
} from '@/api/orders'
import { readCurrentUser } from '@/utils/request'

const route = useRoute()
const router = useRouter()

const detail = ref<BuyerOrderDetail>(createEmptyBuyerOrderDetail())
const loading = ref(false)
const hasLoadedOnce = ref(false)
const loadErrorMessage = ref('')

const paying = ref(false)
const rechecking = ref(false)
const payErrorMessage = ref('')
const paySuccessMessage = ref('')
const payStatusPendingSync = ref(false)
const productImageFailed = ref(false)

const loadErrorRef = ref<HTMLElement | null>(null)
const payErrorRef = ref<HTMLElement | null>(null)

let active = true
let requestSequence = 0
let payInFlight = false

function readOrderIdParam(value: unknown) {
  /**
   * 支付页只接受正安全整数订单 ID：
   * - 拒绝 0 / 负数 / 非数字；
   * - 拒绝超出 Number.isSafeInteger 的超大 ID；
   * - 无效 ID 不会触发任何详情或支付请求。
   */
  if (typeof value === 'number' && Number.isSafeInteger(value) && value > 0) {
    return value
  }

  if (typeof value === 'string') {
    const normalized = value.trim()
    if (/^\d+$/.test(normalized)) {
      const parsed = Number(normalized)
      if (Number.isSafeInteger(parsed) && parsed > 0) {
        return parsed
      }
    }
  }

  return null
}

function readErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message.trim() ? error.message : fallback
}

const orderId = computed(() => {
  const rawId = route.params.orderId
  return readOrderIdParam(Array.isArray(rawId) ? rawId[0] : rawId)
})

const invalidOrderId = computed(() => orderId.value === null)
const currentUser = computed(() => readCurrentUser())
const statusMeta = computed(() => getOrderStatusMeta(detail.value.status))

const productImage = computed(() => detail.value.productImages[0] || detail.value.productThumbnail)

const isCurrentBuyer = computed(() => {
  const buyerId = detail.value.buyerId
  const currentUserId = currentUser.value?.id

  return typeof buyerId === 'number'
    && Number.isSafeInteger(buyerId)
    && buyerId > 0
    && typeof currentUserId === 'number'
    && Number.isSafeInteger(currentUserId)
    && currentUserId > 0
    && buyerId === currentUserId
})

const canPay = computed(() => Boolean(
  orderId.value !== null
    && hasLoadedOnce.value
    && !loading.value
    && !paying.value
    && !rechecking.value
    /**
     * P1-D2-A-F1：支付请求成功但订单状态尚未确认期间，
     * 必须保持“确认支付”锁定，只能通过“重新检查”解除，
     * 避免用户在结果未知时重复发起支付。
     */
    && !payStatusPendingSync.value
    && isCurrentBuyer.value
    && detail.value.status === 'pending',
))

const statusBlockMessage = computed(() => {
  if (!hasLoadedOnce.value || loading.value) {
    return ''
  }

  if (detail.value.status === 'pending') {
    return ''
  }

  if (detail.value.status === 'paid') {
    return '该订单已经完成支付，无需重复操作。'
  }

  if (detail.value.status === 'cancelled') {
    return '该订单已取消，无法继续支付。'
  }

  return `当前订单状态为"${statusMeta.value.label}"，无法继续支付。`
})

function readStatusChipClass() {
  if (statusMeta.value.tone === 'accent') {
    return 'chip chip-accent'
  }

  if (statusMeta.value.tone === 'success') {
    return 'chip chip-success'
  }

  if (statusMeta.value.tone === 'warning') {
    return 'chip chip-warning'
  }

  return 'chip chip-neutral'
}

function isRequestCurrent(sequence: number, expectedOrderId: number) {
  return active
    && sequence === requestSequence
    && route.name === 'BuyerOrderPayment'
    && orderId.value === expectedOrderId
}

function isPayCurrent(sequence: number, expectedOrderId: number, expectedPath: string) {
  return isRequestCurrent(sequence, expectedOrderId) && route.fullPath === expectedPath
}

function resetRouteState() {
  detail.value = createEmptyBuyerOrderDetail()
  hasLoadedOnce.value = false
  loadErrorMessage.value = ''
  payErrorMessage.value = ''
  paySuccessMessage.value = ''
  payStatusPendingSync.value = false
  productImageFailed.value = false
}

async function focusLoadError(sequence: number, expectedOrderId: number) {
  await nextTick()
  if (isRequestCurrent(sequence, expectedOrderId)) {
    loadErrorRef.value?.focus()
  }
}

async function focusPayError(sequence: number, expectedOrderId: number, expectedPath: string) {
  await nextTick()
  if (isPayCurrent(sequence, expectedOrderId, expectedPath)) {
    payErrorRef.value?.focus()
  }
}

async function loadDetail() {
  if (!active || route.name !== 'BuyerOrderPayment') {
    return
  }

  const sequence = ++requestSequence
  const id = orderId.value
  resetRouteState()

  if (id === null) {
    return
  }

  loading.value = true
  try {
    const result = await getBuyerOrderDetail(id)
    if (!isRequestCurrent(sequence, id)) {
      return
    }
    detail.value = result
  } catch (error: unknown) {
    if (!isRequestCurrent(sequence, id)) {
      return
    }
    loadErrorMessage.value = readErrorMessage(error, '订单详情加载失败，请稍后重试。')
    await focusLoadError(sequence, id)
  } finally {
    if (isRequestCurrent(sequence, id)) {
      loading.value = false
      hasLoadedOnce.value = true
    }
  }
}

function retryLoad() {
  void loadDetail()
}

async function submitPay() {
  const id = orderId.value
  /**
   * `payInFlight` 是同步锁：
   * 双击或连续 Enter 只会放行第一次调用，后续调用在首个 await 之前就被拒绝。
   */
  if (id === null || !canPay.value || payInFlight) {
    return
  }

  const sequence = requestSequence
  const submittingPath = route.fullPath

  payInFlight = true
  paying.value = true
  payErrorMessage.value = ''
  paySuccessMessage.value = ''
  payStatusPendingSync.value = false

  try {
    await payBuyerOrder(id)
    if (!isPayCurrent(sequence, id, submittingPath)) {
      return
    }

    /**
     * 支付接口返回成功不等于订单已流转。
     * 必须重新读取订单详情，只有 status === paid 才判定支付成功。
     */
    try {
      const result = await getBuyerOrderDetail(id)
      if (!isPayCurrent(sequence, id, submittingPath)) {
        return
      }
      detail.value = result

      if (result.status === 'paid') {
        paySuccessMessage.value = '支付成功，正在返回订单详情。'
        await router.replace(`/orders/buyer/${id}`)
      } else {
        payStatusPendingSync.value = true
      }
    } catch {
      if (!isPayCurrent(sequence, id, submittingPath)) {
        return
      }
      payStatusPendingSync.value = true
    }
  } catch (error: unknown) {
    if (!isPayCurrent(sequence, id, submittingPath)) {
      return
    }
    payErrorMessage.value = readErrorMessage(error, '支付失败，请稍后重试。')
    await focusPayError(sequence, id, submittingPath)
  } finally {
    payInFlight = false
    paying.value = false
  }
}

async function recheckPayStatus() {
  const id = orderId.value
  if (id === null || rechecking.value || paying.value || payInFlight || loading.value) {
    return
  }

  const sequence = requestSequence
  const expectedPath = route.fullPath

  rechecking.value = true
  try {
    const result = await getBuyerOrderDetail(id)
    if (!isPayCurrent(sequence, id, expectedPath)) {
      return
    }
    detail.value = result

    if (result.status === 'paid') {
      payStatusPendingSync.value = false
      paySuccessMessage.value = '支付成功，正在返回订单详情。'
      await router.replace(`/orders/buyer/${id}`)
    } else {
      payStatusPendingSync.value = true
    }
  } catch {
    if (!isPayCurrent(sequence, id, expectedPath)) {
      return
    }
    payStatusPendingSync.value = true
  } finally {
    rechecking.value = false
  }
}

watch(orderId, () => {
  void loadDetail()
}, { immediate: true })

onBeforeUnmount(() => {
  active = false
  requestSequence += 1
})
</script>

<template>
  <main class="page-body space-y-6">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">订单支付</p>
          <h1 class="page-title">确认支付</h1>
          <p class="page-desc">核对订单信息后完成支付，支付结果以后端返回为准。</p>
        </div>
        <div class="page-actions">
          <router-link v-if="!invalidOrderId" class="btn-default" :to="`/orders/buyer/${orderId}`">
            <ChevronLeft class="h-4 w-4" />
            <span>返回订单详情</span>
          </router-link>
          <router-link class="btn-default" to="/orders/buyer">
            <ChevronLeft class="h-4 w-4" />
            <span>返回买家订单</span>
          </router-link>
        </div>
      </div>
    </section>

    <section v-if="invalidOrderId" class="section-panel">
      <div class="empty-state min-h-[300px]">
        <p class="empty-state-title">订单地址无效</p>
        <p class="empty-state-text">无法识别订单编号，请返回买家订单列表后重新进入。</p>
        <router-link class="btn-primary mt-5" to="/orders/buyer">返回买家订单</router-link>
      </div>
    </section>

    <template v-else>
      <section v-if="loadErrorMessage" class="section-panel">
        <div ref="loadErrorRef" class="empty-state min-h-[300px]" role="alert" aria-live="assertive" tabindex="-1">
          <p class="empty-state-title">订单详情加载失败</p>
          <p class="empty-state-text">{{ loadErrorMessage }}</p>
          <div class="mt-5 flex flex-wrap justify-center gap-3">
            <button class="btn-primary" type="button" :disabled="loading" @click="retryLoad">重新加载</button>
            <router-link class="btn-default" :to="`/orders/buyer/${orderId}`">返回订单详情</router-link>
          </div>
        </div>
      </section>

      <section v-else-if="loading && !hasLoadedOnce" class="section-panel">
        <div class="section-body flex min-h-[280px] items-center justify-center gap-3 text-gray-500" role="status" aria-live="polite">
          <Loader2 class="h-5 w-5 animate-spin" />
          <span>正在加载订单详情</span>
        </div>
      </section>

      <template v-else>
        <section v-if="!isCurrentBuyer" class="section-panel">
          <div class="empty-state min-h-[300px]">
            <p class="empty-state-title">无法支付该订单</p>
            <p class="empty-state-text">当前账号不是该订单的买家，不能执行支付。订单归属以后端校验为准。</p>
            <div class="mt-5 flex flex-wrap justify-center gap-3">
              <router-link class="btn-default" :to="`/orders/buyer/${orderId}`">返回订单详情</router-link>
              <router-link class="btn-primary" to="/orders/buyer">返回买家订单</router-link>
            </div>
          </div>
        </section>

        <template v-else>
          <div class="grid gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
          <section class="section-panel">
            <div class="section-header">
              <div>
                <h2 class="section-heading">订单摘要</h2>
                <p class="section-subtitle">订单号：{{ detail.orderNo || '-' }}</p>
              </div>
              <span :class="readStatusChipClass()">{{ statusMeta.label }}</span>
            </div>
            <div class="section-body">
              <div class="grid gap-6 lg:grid-cols-[240px_minmax(0,1fr)]">
                <div>
                  <img
                    v-if="productImage && !productImageFailed"
                    :src="productImage"
                    :alt="detail.productTitle || '订单商品'"
                    class="h-[200px] w-full rounded-md border border-gray-200/80 object-cover"
                    @error="productImageFailed = true"
                  />
                  <div v-else class="empty-state min-h-[200px] rounded-md border border-dashed border-gray-200 bg-gray-50">
                    <PackageSearch class="empty-state-icon" />
                    <p class="empty-state-title">暂无商品图片</p>
                  </div>
                </div>
                <div class="min-w-0 space-y-4">
                  <h3 class="break-words text-[18px] font-semibold text-gray-900">{{ detail.productTitle || '商品标题待确认' }}</h3>
                  <div class="meta-grid">
                    <div class="meta-item">
                      <p class="meta-label">成交单价</p>
                      <p class="meta-value font-numeric">¥ {{ detail.dealPrice.toFixed(2) }}</p>
                    </div>
                    <div class="meta-item">
                      <p class="meta-label">数量</p>
                      <p class="meta-value font-numeric">{{ detail.quantity }}</p>
                    </div>
                    <div class="meta-item">
                      <p class="meta-label">订单总额</p>
                      <p class="meta-value font-numeric">¥ {{ detail.totalAmount.toFixed(2) }}</p>
                    </div>
                    <div class="meta-item">
                      <p class="meta-label">卖家昵称</p>
                      <p class="meta-value break-words">{{ detail.sellerNickname || '-' }}</p>
                    </div>
                  </div>
                  <div>
                    <p class="meta-label">收货地址快照</p>
                    <p class="mt-1 break-words text-[13px] leading-6 text-gray-600">{{ detail.shippingAddress || '暂无地址快照' }}</p>
                  </div>
                </div>
              </div>
            </div>
          </section>

          <aside class="lg:sticky lg:top-24 lg:self-start"><section class="section-panel">
            <div class="section-header">
              <div>
                <h2 class="section-heading">支付订单</h2>
                <p class="section-subtitle">确认订单信息无误后提交支付。</p>
              </div>
            </div>
            <div class="section-body space-y-4">
              <div v-if="statusBlockMessage" class="notice-banner notice-banner-warning" role="status">
                <span class="notice-dot bg-orange-500"></span>
                <div class="flex-1">
                  <p class="font-semibold">当前状态不可支付</p>
                  <p class="mt-1 text-[12px] leading-5">{{ statusBlockMessage }}</p>
                  <div class="mt-3 flex flex-wrap gap-3">
                    <router-link class="btn-default" :to="`/orders/buyer/${orderId}`">返回订单详情</router-link>
                    <router-link class="btn-default" to="/orders/buyer">返回买家订单</router-link>
                  </div>
                </div>
              </div>

              <template v-else>
                <div class="notice-banner notice-banner-warning" role="status">
                  <span class="notice-dot bg-orange-500"></span>
                  <div class="flex-1">
                    <p class="font-semibold">演示支付</p>
                    <p class="mt-1 text-[12px] leading-5">当前环境使用模拟支付通道，仅用于演示订单状态流转，不会产生真实扣款。</p>
                  </div>
                </div>

                <div
                  v-if="payErrorMessage"
                  ref="payErrorRef"
                  class="notice-banner notice-banner-danger"
                  role="alert"
                  aria-live="assertive"
                  tabindex="-1"
                >
                  <span class="notice-dot bg-red-500"></span>
                  <span class="min-w-0 break-words">{{ payErrorMessage }}</span>
                </div>

                <div v-if="payStatusPendingSync" class="notice-banner notice-banner-warning" role="status" aria-live="polite">
                  <span class="notice-dot bg-orange-500"></span>
                  <div class="flex-1">
                    <p class="font-semibold">支付状态尚未同步</p>
                    <p class="mt-1 text-[12px] leading-5">支付请求已提交，但订单状态暂未更新。请稍后重新检查，不要重复发起支付。</p>
                    <button class="btn-default mt-3" type="button" :disabled="rechecking || paying" @click="recheckPayStatus">
                      <Loader2 v-if="rechecking" class="h-4 w-4 animate-spin" />
                      <span>{{ rechecking ? '正在检查' : '重新检查' }}</span>
                    </button>
                  </div>
                </div>

                <div v-if="paySuccessMessage" class="notice-banner notice-banner-success" role="status" aria-live="polite">
                  <span class="notice-dot bg-emerald-500"></span>
                  <span>{{ paySuccessMessage }}</span>
                </div>

                <div class="rounded-md border border-stone-200 bg-stone-50 p-4">
                  <p class="text-[12px] font-medium text-gray-500">应付金额</p>
                  <p class="font-numeric mt-1 text-[28px] font-bold text-gray-950">¥ {{ detail.totalAmount.toFixed(2) }}</p>
                </div>
                <div class="flex flex-wrap items-center gap-3">
                  <button
                    class="btn-primary"
                    type="button"
                    :disabled="!canPay"
                    :aria-busy="paying ? 'true' : 'false'"
                    @click="submitPay"
                  >
                    <Loader2 v-if="paying" class="h-4 w-4 animate-spin" />
                    <span>{{ paying ? '正在支付' : '确认支付' }}</span>
                  </button>
                  <span class="text-[12px] text-gray-500">状态以订单详情重新读取结果为准</span>
                </div>
              </template>
            </div>
          </section></aside>
          </div>
        </template>
      </template>
    </template>
  </main>
</template>
