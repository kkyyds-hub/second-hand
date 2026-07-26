<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronLeft, Loader2, PackageSearch } from 'lucide-vue-next'
import {
  createBuyerAfterSale,
  initiateBuyerAfterSaleDispute,
  type CreateAfterSaleResult,
} from '@/api/afterSales'
import {
  cancelBuyerOrder,
  confirmBuyerOrderReceipt,
  createEmptyBuyerOrderDetail,
  getBuyerOrderDetail,
  getBuyerOrderStatusMeta,
  type BuyerOrderDetail,
} from '@/api/orders'
import OrderMessagePanel from '@/pages/orders/components/OrderMessagePanel.vue'

const route = useRoute()

const loading = ref(false)
const hasLoadedOnce = ref(false)
const errorMessage = ref('')
const detail = ref<BuyerOrderDetail>(createEmptyBuyerOrderDetail())

const actionSubmitting = ref(false)
const actionErrorMessage = ref('')
const actionSuccessMessage = ref('')
const cancelReason = ref('')

const afterSaleSubmitting = ref(false)
const afterSaleErrorMessage = ref('')
const afterSaleSuccessMessage = ref('')
const createdAfterSaleId = ref<number | null>(null)
const afterSaleForm = reactive({
  reason: '',
  evidenceImageText: '',
})

const disputeSubmitting = ref(false)
const disputeErrorMessage = ref('')
const disputeSuccessMessage = ref('')
const disputeForm = reactive({
  afterSaleId: '',
  content: '',
})

const orderId = computed(() => {
  const rawId = route.params.orderId
  if (Array.isArray(rawId)) {
    return rawId[0] || ''
  }

  return typeof rawId === 'string' ? rawId : ''
})

const statusMeta = computed(() => getBuyerOrderStatusMeta(detail.value.status))

const productImages = computed(() => {
  if (detail.value.productImages.length > 0) {
    return detail.value.productImages
  }

  if (detail.value.productThumbnail) {
    return [detail.value.productThumbnail]
  }

  return []
})

const canEnterPaymentPage = computed(() => {
  /**
   * P1-D2-A：详情页只保留“去支付”入口，支付执行下沉到正式支付页。
   * 仅在订单有效、状态为 pending、且页面没有其他提交任务时显示入口。
   */
  return Boolean(
    detail.value.orderId !== null
      && detail.value.status === 'pending'
      && !loading.value
      && !actionSubmitting.value
      && !afterSaleSubmitting.value
      && !disputeSubmitting.value,
  )
})

const canTriggerCancel = computed(() => {
  return Boolean(
    detail.value.orderId !== null
      && detail.value.status === 'pending'
      && !loading.value
      && !actionSubmitting.value
      && !afterSaleSubmitting.value
      && !disputeSubmitting.value,
  )
})

const canTriggerConfirmReceipt = computed(() => {
  return Boolean(
    detail.value.orderId !== null
      && detail.value.status === 'shipped'
      && !loading.value
      && !actionSubmitting.value
      && !afterSaleSubmitting.value
      && !disputeSubmitting.value,
  )
})

const canTriggerAfterSaleApply = computed(() => {
  if (
    detail.value.orderId === null
    || detail.value.status !== 'completed'
    || loading.value
    || actionSubmitting.value
    || afterSaleSubmitting.value
    || disputeSubmitting.value
  ) {
    return false
  }

  const hoursSinceComplete = readHoursSinceCompleteTime(detail.value.completeTime)
  if (hoursSinceComplete === null || hoursSinceComplete < 0 || hoursSinceComplete > 24 * 7) {
    return false
  }

  const reason = normalizeAfterSaleReason(afterSaleForm.reason)
  if (reason.length < 2 || reason.length > 200) {
    return false
  }

  const evidenceImages = normalizeEvidenceImagesFromText(afterSaleForm.evidenceImageText)
  return evidenceImages.length <= 3
})

const canTriggerDisputeInitiate = computed(() => {
  if (loading.value || actionSubmitting.value || afterSaleSubmitting.value || disputeSubmitting.value) {
    return false
  }

  const afterSaleId = readPositiveInt(disputeForm.afterSaleId)
  if (afterSaleId === null) {
    return false
  }

  const content = normalizeDisputeContent(disputeForm.content)
  return content.length >= 2 && content.length <= 500
})

function readPositiveInt(value: unknown) {
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

function parseDateTime(value: string) {
  const normalized = value.trim()
  if (!normalized) {
    return null
  }

  const isoCandidate = normalized.replace(' ', 'T')
  const parsed = new Date(isoCandidate)
  if (!Number.isNaN(parsed.getTime())) {
    return parsed
  }

  const fallback = new Date(normalized.replace(/-/g, '/'))
  if (!Number.isNaN(fallback.getTime())) {
    return fallback
  }

  return null
}

function readHoursSinceCompleteTime(completeTime: string) {
  const parsed = parseDateTime(completeTime)
  if (!parsed) {
    return null
  }

  return (Date.now() - parsed.getTime()) / (60 * 60 * 1000)
}

function normalizeAfterSaleReason(value: string) {
  return value.trim()
}

function normalizeEvidenceImagesFromText(value: string) {
  const normalized = value.trim()
  if (!normalized) {
    return []
  }

  const deduped = new Set(
    normalized
      .split(/[\n,;]/g)
      .map((item) => item.trim())
      .filter(Boolean),
  )

  return Array.from(deduped)
}

function normalizeDisputeContent(value: string) {
  return value.trim()
}

function readActionGuardPrefix() {
  if (detail.value.orderId === null) {
    return 'Order ID is missing. Action unavailable.'
  }

  if (loading.value) {
    return 'Order detail is loading. Please wait.'
  }

  if (actionSubmitting.value || afterSaleSubmitting.value || disputeSubmitting.value) {
    return 'A request is in progress. Please wait.'
  }

  return ''
}

const paymentGuardText = computed(() => {
  const guardPrefix = readActionGuardPrefix()
  if (guardPrefix) {
    return guardPrefix
  }

  if (detail.value.status !== 'pending') {
    return `Current status is "${statusMeta.value.label}". Only pending orders show the payment entry.`
  }

  return 'Pending order detected. Use "去支付" to enter the formal payment page.'
})

const cancelGuardText = computed(() => {
  const guardPrefix = readActionGuardPrefix()
  if (guardPrefix) {
    return guardPrefix
  }

  if (detail.value.status !== 'pending') {
    return `Current status is "${statusMeta.value.label}". Only pending orders can be canceled.`
  }

  return 'Pending order detected. Cancel is available.'
})

const confirmReceiptGuardText = computed(() => {
  const guardPrefix = readActionGuardPrefix()
  if (guardPrefix) {
    return guardPrefix
  }

  if (detail.value.status !== 'shipped') {
    return `Current status is "${statusMeta.value.label}". Only shipped orders can confirm receipt.`
  }

  return 'Shipped order detected. Confirm receipt is available.'
})

const afterSaleApplyGuardText = computed(() => {
  if (detail.value.orderId === null) {
    return 'Order ID is missing. After-sale apply unavailable.'
  }

  if (loading.value) {
    return 'Order detail is loading. Please wait.'
  }

  if (actionSubmitting.value || afterSaleSubmitting.value || disputeSubmitting.value) {
    return 'A request is in progress. Please wait.'
  }

  if (detail.value.status !== 'completed') {
    return `Current status is "${statusMeta.value.label}". Only completed orders can apply after-sale.`
  }

  const hoursSinceComplete = readHoursSinceCompleteTime(detail.value.completeTime)
  if (hoursSinceComplete === null) {
    return 'Complete time is missing/invalid, cannot pre-check the 7-day window on frontend.'
  }

  if (hoursSinceComplete < 0) {
    return 'Complete time is abnormal. Cannot pre-check after-sale window.'
  }

  if (hoursSinceComplete > 24 * 7) {
    return 'More than 7 days since completion. Apply is expected to fail.'
  }

  return 'Preconditions pass on frontend (completed + within 7 days). Final decision is backend-owned.'
})

const disputeGuardText = computed(() => {
  if (loading.value) {
    return 'Order detail is loading. Please wait.'
  }

  if (actionSubmitting.value || afterSaleSubmitting.value || disputeSubmitting.value) {
    return 'A request is in progress. Please wait.'
  }

  if (readPositiveInt(disputeForm.afterSaleId) === null) {
    return 'Please provide a valid afterSaleId before dispute submission.'
  }

  return 'Dispute endpoint only accepts SELLER_REJECTED after-sale records. This page has no after-sale detail query, so backend decides final eligibility.'
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

function readErrorMessage(error: unknown, fallback = '请求失败，请稍后重试。') {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return fallback
}

function clearActionMessages() {
  if (actionSubmitting.value) {
    return
  }

  actionErrorMessage.value = ''
  actionSuccessMessage.value = ''
}

function clearAfterSaleMessages() {
  if (afterSaleSubmitting.value) {
    return
  }

  afterSaleErrorMessage.value = ''
  afterSaleSuccessMessage.value = ''
}

function clearDisputeMessages() {
  if (disputeSubmitting.value) {
    return
  }

  disputeErrorMessage.value = ''
  disputeSuccessMessage.value = ''
}

function normalizeCancelReason(value: string) {
  return value.trim()
}

function readAfterSaleSuccessMessage(result: CreateAfterSaleResult) {
  if (result.afterSaleId !== null) {
    return `After-sale apply submitted. afterSaleId: ${result.afterSaleId}.`
  }

  return 'After-sale apply submitted.'
}

async function submitCancelOrder() {
  if (!canTriggerCancel.value || detail.value.orderId === null) {
    return
  }

  const normalizedReason = normalizeCancelReason(cancelReason.value)
  if (normalizedReason.length > 100) {
    actionErrorMessage.value = 'Cancel reason must be <= 100 chars.'
    actionSuccessMessage.value = ''
    return
  }

  try {
    actionSubmitting.value = true
    actionErrorMessage.value = ''
    actionSuccessMessage.value = ''

    const message = await cancelBuyerOrder(detail.value.orderId, {
      reason: normalizedReason || undefined,
    })
    actionSuccessMessage.value = message || 'Cancel request submitted.'
    await loadDetail()
  } catch (error: unknown) {
    actionErrorMessage.value = readErrorMessage(error, '订单操作失败，请稍后重试。')
  } finally {
    actionSubmitting.value = false
  }
}

async function submitConfirmReceipt() {
  if (!canTriggerConfirmReceipt.value || detail.value.orderId === null) {
    return
  }

  try {
    actionSubmitting.value = true
    actionErrorMessage.value = ''
    actionSuccessMessage.value = ''

    const message = await confirmBuyerOrderReceipt(detail.value.orderId)
    actionSuccessMessage.value = message || 'Confirm-receipt request submitted.'
    await loadDetail()
  } catch (error: unknown) {
    actionErrorMessage.value = readErrorMessage(error, '订单操作失败，请稍后重试。')
  } finally {
    actionSubmitting.value = false
  }
}

async function submitAfterSaleApply() {
  if (afterSaleSubmitting.value || detail.value.orderId === null) {
    return
  }

  if (detail.value.status !== 'completed') {
    afterSaleErrorMessage.value = `Current status is "${statusMeta.value.label}". Only completed orders can apply after-sale.`
    afterSaleSuccessMessage.value = ''
    return
  }

  const hoursSinceComplete = readHoursSinceCompleteTime(detail.value.completeTime)
  if (hoursSinceComplete === null) {
    afterSaleErrorMessage.value = 'Complete time is missing/invalid. Cannot submit apply.'
    afterSaleSuccessMessage.value = ''
    return
  }

  if (hoursSinceComplete < 0 || hoursSinceComplete > 24 * 7) {
    afterSaleErrorMessage.value = 'Order is outside 7-day window or has abnormal complete time.'
    afterSaleSuccessMessage.value = ''
    return
  }

  const reason = normalizeAfterSaleReason(afterSaleForm.reason)
  if (reason.length < 2 || reason.length > 200) {
    afterSaleErrorMessage.value = 'Reason length must be 2~200 chars.'
    afterSaleSuccessMessage.value = ''
    return
  }

  const evidenceImages = normalizeEvidenceImagesFromText(afterSaleForm.evidenceImageText)
  if (evidenceImages.length > 3) {
    afterSaleErrorMessage.value = 'At most 3 evidence images are allowed.'
    afterSaleSuccessMessage.value = ''
    return
  }

  try {
    afterSaleSubmitting.value = true
    afterSaleErrorMessage.value = ''
    afterSaleSuccessMessage.value = ''
    createdAfterSaleId.value = null

    const result = await createBuyerAfterSale({
      orderId: detail.value.orderId,
      reason,
      evidenceImages,
    })

    afterSaleSuccessMessage.value = readAfterSaleSuccessMessage(result)
    createdAfterSaleId.value = result.afterSaleId

    /**
     * Dispute requires afterSaleId.
     * If apply returns afterSaleId, autofill it to reduce manual copy mistakes.
     */
    if (result.afterSaleId !== null) {
      disputeForm.afterSaleId = String(result.afterSaleId)
      clearDisputeMessages()
    }
  } catch (error: unknown) {
    afterSaleErrorMessage.value = readErrorMessage(error, '售后申请失败，请稍后重试。')
    afterSaleSuccessMessage.value = ''
    createdAfterSaleId.value = null
  } finally {
    afterSaleSubmitting.value = false
  }
}

async function submitDisputeInitiate() {
  if (disputeSubmitting.value) {
    return
  }

  const afterSaleId = readPositiveInt(disputeForm.afterSaleId)
  if (afterSaleId === null) {
    disputeErrorMessage.value = 'Please input a valid afterSaleId.'
    disputeSuccessMessage.value = ''
    return
  }

  const content = normalizeDisputeContent(disputeForm.content)
  if (content.length < 2 || content.length > 500) {
    disputeErrorMessage.value = 'Dispute content length must be 2~500 chars.'
    disputeSuccessMessage.value = ''
    return
  }

  try {
    disputeSubmitting.value = true
    disputeErrorMessage.value = ''
    disputeSuccessMessage.value = ''

    const message = await initiateBuyerAfterSaleDispute(afterSaleId, { content })
    disputeSuccessMessage.value = message || 'Dispute request submitted.'
  } catch (error: unknown) {
    disputeErrorMessage.value = readErrorMessage(error, '争议提交失败，请稍后重试。')
    disputeSuccessMessage.value = ''
  } finally {
    disputeSubmitting.value = false
  }
}

async function loadDetail() {
  if (loading.value) {
    return
  }

  if (!orderId.value) {
    errorMessage.value = 'Order param is missing. Cannot load detail.'
    hasLoadedOnce.value = true
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''

    detail.value = await getBuyerOrderDetail(orderId.value)
  } catch (error: unknown) {
    errorMessage.value = readErrorMessage(error, '订单详情加载失败，请稍后重试。')
  } finally {
    loading.value = false
    hasLoadedOnce.value = true
  }
}

onMounted(() => {
  loadDetail()
})
</script>

<template>
  <div class="page-body">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">订单详情</p>
          <h1 class="page-title">买家订单详情</h1>
          <p class="page-desc">查看订单状态、支付记录、物流信息并执行当前可用动作。</p>
        </div>
        <div class="page-actions">
          <router-link class="btn-default" to="/orders/buyer">
            <ChevronLeft class="h-4 w-4" />
            <span>返回订单列表</span>
          </router-link>
        </div>
      </div>
    </section>

    <section v-if="errorMessage" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <div class="flex-1">
        <p class="font-semibold">订单详情加载失败</p>
        <p class="mt-1 text-[12px] leading-5">{{ errorMessage }}</p>
        <button class="btn-default mt-3" type="button" :disabled="loading" @click="loadDetail">重新加载</button>
      </div>
    </section>

    <section v-if="loading && !hasLoadedOnce" class="section-panel">
      <div class="section-body">
        <div class="empty-state min-h-[280px]">
          <Loader2 class="empty-state-icon animate-spin text-blue-500" />
          <p class="empty-state-title">正在加载订单详情</p>
        </div>
      </div>
    </section>

    <template v-else>
      <section class="section-panel">
        <div class="section-header">
          <div>
            <h2 class="section-heading">订单状态</h2>
            <p class="section-subtitle">订单号：{{ detail.orderNo || '-' }}</p>
          </div>
          <span :class="readStatusChipClass()">{{ statusMeta.label }}</span>
        </div>
        <div class="section-body">
          <div class="detail-grid">
            <div class="detail-row">
              <span class="detail-label">买家昵称</span>
              <span class="detail-value">{{ detail.buyerNickname || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">卖家昵称</span>
              <span class="detail-value">{{ detail.sellerNickname || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">下单时间</span>
              <span class="detail-value">{{ detail.createTime || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">支付时间</span>
              <span class="detail-value">{{ detail.payTime || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">发货时间</span>
              <span class="detail-value">{{ detail.shipTime || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">完成时间</span>
              <span class="detail-value">{{ detail.completeTime || '-' }}</span>
            </div>
          </div>
        </div>
      </section>

      <section class="section-panel">
        <div class="section-header">
          <div>
            <h2 class="section-heading">订单操作</h2>
            <p class="section-subtitle">根据订单状态启用：支付、取消、确认收货、售后申请、争议发起。</p>
          </div>
        </div>
        <div class="section-body space-y-4">
          <div v-if="actionErrorMessage" class="notice-banner notice-banner-danger">
            <span class="notice-dot bg-red-500"></span>
            <span>{{ actionErrorMessage }}</span>
          </div>
          <div v-if="actionSuccessMessage" class="notice-banner notice-banner-success">
            <span class="notice-dot bg-emerald-500"></span>
            <span>{{ actionSuccessMessage }}</span>
          </div>

          <div class="grid gap-4 lg:grid-cols-2">
            <section class="rounded-2xl border border-gray-100 bg-gray-50/80 p-4 space-y-3">
              <h3 class="text-[14px] font-semibold text-gray-900">订单支付</h3>
              <div class="flex flex-wrap items-end gap-3">
                <router-link
                  v-if="canEnterPaymentPage"
                  class="btn-primary"
                  :to="`/orders/buyer/${detail.orderId}/pay`"
                >
                  去支付
                </router-link>
                <span v-else class="chip chip-neutral">暂无可用支付入口</span>
              </div>
              <p class="form-helper">{{ paymentGuardText }}</p>
            </section>

            <section class="rounded-2xl border border-gray-100 bg-gray-50/80 p-4 space-y-3">
              <h3 class="text-[14px] font-semibold text-gray-900">取消订单</h3>
              <div class="w-full">
                <label class="form-label" for="buyer-cancel-reason">取消原因（选填）</label>
                <input
                  id="buyer-cancel-reason"
                  v-model="cancelReason"
                  class="input-standard"
                  type="text"
                  maxlength="100"
                  placeholder="选填，不超过 100 字"
                  :disabled="!canTriggerCancel"
                  @input="clearActionMessages"
                />
              </div>
              <div class="flex flex-wrap items-center gap-3">
                <button class="btn-default" type="button" :disabled="!canTriggerCancel" @click="submitCancelOrder">
                  <Loader2 v-if="actionSubmitting" class="h-4 w-4 animate-spin" />
                  <span>{{ actionSubmitting ? '提交中...' : '取消订单' }}</span>
                </button>
              </div>
              <p class="form-helper">{{ cancelGuardText }}</p>
            </section>
          </div>

          <section class="rounded-2xl border border-gray-100 bg-gray-50/80 p-4 space-y-3">
            <h3 class="text-[14px] font-semibold text-gray-900">确认收货</h3>
            <div class="flex flex-wrap items-center gap-3">
              <button class="btn-primary" type="button" :disabled="!canTriggerConfirmReceipt" @click="submitConfirmReceipt">
                <Loader2 v-if="actionSubmitting" class="h-4 w-4 animate-spin" />
                <span>{{ actionSubmitting ? '提交中...' : '确认收货' }}</span>
              </button>
            </div>
            <p class="form-helper">{{ confirmReceiptGuardText }}</p>
          </section>

          <section class="rounded-2xl border border-gray-100 bg-gray-50/80 p-4 space-y-3">
            <h3 class="text-[14px] font-semibold text-gray-900">申请售后</h3>
            <div v-if="afterSaleErrorMessage" class="notice-banner notice-banner-danger">
              <span class="notice-dot bg-red-500"></span>
              <span>{{ afterSaleErrorMessage }}</span>
            </div>
            <div v-if="afterSaleSuccessMessage" class="notice-banner notice-banner-success">
              <span class="notice-dot bg-emerald-500"></span>
              <span>{{ afterSaleSuccessMessage }}</span>
            </div>

            <div class="w-full">
              <label class="form-label" for="buyer-after-sale-reason">售后原因</label>
              <textarea
                id="buyer-after-sale-reason"
                v-model="afterSaleForm.reason"
                class="input-standard min-h-[112px]"
                maxlength="200"
                placeholder="请输入原因（2~200 字）"
                :disabled="afterSaleSubmitting"
                @input="clearAfterSaleMessages"
              ></textarea>
            </div>

            <div class="w-full">
              <label class="form-label" for="buyer-after-sale-evidence-images">凭证图片链接（选填）</label>
              <textarea
                id="buyer-after-sale-evidence-images"
                v-model="afterSaleForm.evidenceImageText"
                class="input-standard min-h-[96px]"
                placeholder="每行或逗号分隔，最多 3 个链接"
                :disabled="afterSaleSubmitting"
                @input="clearAfterSaleMessages"
              ></textarea>
              <p class="form-helper">输入内容将在提交时自动规范化。</p>
            </div>

            <div class="flex flex-wrap items-center gap-3">
              <button class="btn-primary" type="button" :disabled="!canTriggerAfterSaleApply" @click="submitAfterSaleApply">
                <Loader2 v-if="afterSaleSubmitting" class="h-4 w-4 animate-spin" />
                <span>{{ afterSaleSubmitting ? '提交中...' : '提交申请' }}</span>
              </button>
              <span v-if="createdAfterSaleId !== null" class="chip chip-neutral font-numeric">afterSaleId {{ createdAfterSaleId }}</span>
            </div>
            <p class="form-helper">{{ afterSaleApplyGuardText }}</p>
          </section>

          <section class="rounded-2xl border border-gray-100 bg-gray-50/80 p-4 space-y-3">
            <h3 class="text-[14px] font-semibold text-gray-900">发起争议（平台介入）</h3>
            <div v-if="disputeErrorMessage" class="notice-banner notice-banner-danger">
              <span class="notice-dot bg-red-500"></span>
              <span>{{ disputeErrorMessage }}</span>
            </div>
            <div v-if="disputeSuccessMessage" class="notice-banner notice-banner-success">
              <span class="notice-dot bg-emerald-500"></span>
              <span>{{ disputeSuccessMessage }}</span>
            </div>

            <div class="grid gap-4 md:grid-cols-[240px_minmax(0,1fr)]">
              <div>
                <label class="form-label" for="buyer-dispute-after-sale-id">售后单号</label>
                <input
                  id="buyer-dispute-after-sale-id"
                  v-model="disputeForm.afterSaleId"
                  class="input-standard"
                  type="text"
                  inputmode="numeric"
                  placeholder="例如 50001"
                  :disabled="disputeSubmitting"
                  @input="clearDisputeMessages"
                />
              </div>
              <div>
                <label class="form-label" for="buyer-dispute-content">争议内容</label>
                <textarea
                  id="buyer-dispute-content"
                  v-model="disputeForm.content"
                  class="input-standard min-h-[112px]"
                  maxlength="500"
                  placeholder="请输入内容（2~500 字）"
                  :disabled="disputeSubmitting"
                  @input="clearDisputeMessages"
                ></textarea>
              </div>
            </div>

            <div class="flex flex-wrap items-center gap-3">
              <button class="btn-default" type="button" :disabled="!canTriggerDisputeInitiate" @click="submitDisputeInitiate">
                <Loader2 v-if="disputeSubmitting" class="h-4 w-4 animate-spin" />
                <span>{{ disputeSubmitting ? '提交中...' : '提交争议' }}</span>
              </button>
            </div>
            <p class="form-helper">{{ disputeGuardText }}</p>
          </section>
        </div>
      </section>

      <section class="section-panel">
        <div class="section-header">
          <div>
            <h2 class="section-heading">商品信息</h2>
            <p class="section-subtitle">展示订单快照商品信息，供支付前再次确认。</p>
          </div>
        </div>
        <div class="section-body">
          <div class="grid gap-6 lg:grid-cols-[320px_1fr]">
            <div>
              <img
                v-if="productImages.length > 0"
                :src="productImages[0]"
                :alt="detail.productTitle || 'order product'"
                class="h-[240px] w-full rounded-2xl border border-gray-200/80 object-cover"
              />
              <div v-else class="empty-state min-h-[240px] rounded-2xl border border-dashed border-gray-200 bg-gray-50">
                <PackageSearch class="empty-state-icon" />
                <p class="empty-state-title">暂无商品图片</p>
              </div>
              <div v-if="productImages.length > 1" class="mt-3 grid grid-cols-4 gap-2">
                <img v-for="image in productImages.slice(1, 5)" :key="image" :src="image" alt="product thumbnail" class="h-16 w-full rounded-lg border border-gray-200 object-cover" />
              </div>
            </div>

            <div class="space-y-4">
              <h3 class="text-[18px] font-semibold text-gray-900">{{ detail.productTitle || '商品标题待确认' }}</h3>
              <div class="inline-meta font-numeric">
                <span>订单 ID {{ detail.orderId ?? '-' }}</span>
                <span class="inline-meta-dot"></span>
                <span>商品 ID {{ detail.productId ?? '-' }}</span>
              </div>
              <div class="meta-grid">
                <div class="meta-item">
                  <p class="meta-label">成交单价</p>
                  <p class="meta-value font-numeric">¥ {{ detail.dealPrice.toFixed(2) }}</p>
                </div>
                <div class="meta-item">
                  <p class="meta-label">购买数量</p>
                  <p class="meta-value font-numeric">{{ detail.quantity }}</p>
                </div>
                <div class="meta-item">
                  <p class="meta-label">订单总额</p>
                  <p class="meta-value font-numeric">¥ {{ detail.totalAmount.toFixed(2) }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="grid gap-6 lg:grid-cols-2">
        <section class="section-panel">
          <div class="section-header section-header-plain">
            <div>
              <h2 class="section-heading">物流信息</h2>
            </div>
          </div>
          <div class="section-body pt-0">
            <div class="detail-grid">
              <div class="detail-row">
                <span class="detail-label">物流公司</span>
                <span class="detail-value">{{ detail.shippingCompany || '-' }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">运单号</span>
                <span class="detail-value">{{ detail.trackingNo || '-' }}</span>
              </div>
              <div class="detail-row !items-start !justify-start !gap-1.5">
                <span class="detail-label">发货备注</span>
                <span class="detail-value">{{ detail.shippingRemark || '-' }}</span>
              </div>
            </div>
          </div>
        </section>

        <section class="section-panel">
          <div class="section-header section-header-plain">
            <div>
              <h2 class="section-heading">收货地址快照</h2>
            </div>
          </div>
          <div class="section-body pt-0">
            <p class="text-[13px] leading-6 text-gray-600">{{ detail.shippingAddress || '暂无地址快照' }}</p>
          </div>
        </section>
      </section>

      <OrderMessagePanel
        :order-id="detail.orderId"
        :current-user-id="detail.buyerId"
        :counterpart-user-id="detail.sellerId"
        :counterpart-label="detail.sellerNickname || '卖家'"
      />
    </template>
  </div>
</template>
