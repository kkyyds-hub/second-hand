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
  payBuyerOrder,
  simulateBuyerOrderMockPay,
  type BuyerMockPayScenario,
  type BuyerMockPayResult,
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
const mockPayScenario = ref<BuyerMockPayScenario>('SUCCESS')
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

const mockPayScenarioOptions: Array<{ value: BuyerMockPayScenario; label: string }> = [
  { value: 'SUCCESS', label: 'SUCCESS (simulate successful callback)' },
  { value: 'FAIL', label: 'FAIL (simulate failed callback)' },
  { value: 'REPEAT', label: 'REPEAT (simulate duplicate callback)' },
]

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

const canTriggerPayment = computed(() => {
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
    return `Current status is "${statusMeta.value.label}". Only pending orders can pay/mock-pay.`
  }

  return 'Pending order detected. Pay/mock-pay is available.'
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

function readErrorMessage(error: unknown, fallback = 'Request failed, please retry later.') {
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

function readMockPaySummary(result: BuyerMockPayResult) {
  const beforeLabel = getBuyerOrderStatusMeta(result.beforeStatus).label
  const afterLabel = getBuyerOrderStatusMeta(result.afterStatus).label
  const finalResult = result.finalResult || 'result returned'
  return `mock(${result.scenario}) done: ${finalResult}, ${beforeLabel} -> ${afterLabel}.`
}

function readAfterSaleSuccessMessage(result: CreateAfterSaleResult) {
  if (result.afterSaleId !== null) {
    return `After-sale apply submitted. afterSaleId: ${result.afterSaleId}.`
  }

  return 'After-sale apply submitted.'
}

async function submitPay() {
  if (!canTriggerPayment.value || detail.value.orderId === null) {
    return
  }

  try {
    actionSubmitting.value = true
    actionErrorMessage.value = ''
    actionSuccessMessage.value = ''

    const message = await payBuyerOrder(detail.value.orderId)
    actionSuccessMessage.value = message || 'Pay request submitted.'
    await loadDetail()
  } catch (error: unknown) {
    actionErrorMessage.value = readErrorMessage(error, 'Order action failed. Please retry later.')
  } finally {
    actionSubmitting.value = false
  }
}

async function submitMockPay() {
  if (!canTriggerPayment.value || detail.value.orderId === null) {
    return
  }

  try {
    actionSubmitting.value = true
    actionErrorMessage.value = ''
    actionSuccessMessage.value = ''

    /**
     * mock-pay is only for demo/testing.
     * It reuses the same callback chain, but does not mean real PSP integration is done.
     */
    const result = await simulateBuyerOrderMockPay(detail.value.orderId, mockPayScenario.value)
    actionSuccessMessage.value = readMockPaySummary(result)
    await loadDetail()
  } catch (error: unknown) {
    actionErrorMessage.value = readErrorMessage(error, 'Order action failed. Please retry later.')
  } finally {
    actionSubmitting.value = false
  }
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
    actionErrorMessage.value = readErrorMessage(error, 'Order action failed. Please retry later.')
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
    actionErrorMessage.value = readErrorMessage(error, 'Order action failed. Please retry later.')
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
    afterSaleErrorMessage.value = readErrorMessage(error, 'After-sale apply failed. Please retry later.')
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
    disputeErrorMessage.value = readErrorMessage(error, 'Dispute submit failed. Please retry later.')
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
    errorMessage.value = readErrorMessage(error, 'Order detail load failed. Please retry later.')
  } finally {
    loading.value = false
    hasLoadedOnce.value = true
  }
}

const packageHint = 'This page now includes pay/cancel/confirm-receipt/after-sale-apply/dispute-initiate. Final acceptance is out of scope.'

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

    <section class="notice-banner notice-banner-warning">
      <span class="notice-dot bg-orange-500"></span>
      <span>{{ packageHint }}</span>
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
            <h2 class="section-heading">Order actions</h2>
            <p class="section-subtitle">Actions are enabled by status: pay, cancel, confirm receipt, after-sale apply, dispute initiate.</p>
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
              <h3 class="text-[14px] font-semibold text-gray-900">Pay actions</h3>
              <div class="flex flex-wrap items-end gap-3">
                <button class="btn-primary" type="button" :disabled="!canTriggerPayment" @click="submitPay">
                  <Loader2 v-if="actionSubmitting" class="h-4 w-4 animate-spin" />
                  <span>{{ actionSubmitting ? 'Submitting...' : 'Pay now (pay)' }}</span>
                </button>

                <div class="w-full max-w-[320px]">
                  <label class="form-label" for="buyer-mock-pay-scenario">Mock scenario</label>
                  <select
                    id="buyer-mock-pay-scenario"
                    v-model="mockPayScenario"
                    class="input-standard"
                    :disabled="!canTriggerPayment"
                    @change="clearActionMessages"
                  >
                    <option v-for="option in mockPayScenarioOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
                  </select>
                </div>

                <button class="btn-default" type="button" :disabled="!canTriggerPayment" @click="submitMockPay">
                  Run mock pay
                </button>
              </div>
              <p class="form-helper">{{ paymentGuardText }}</p>
            </section>

            <section class="rounded-2xl border border-gray-100 bg-gray-50/80 p-4 space-y-3">
              <h3 class="text-[14px] font-semibold text-gray-900">Cancel order</h3>
              <div class="w-full">
                <label class="form-label" for="buyer-cancel-reason">Cancel reason (optional)</label>
                <input
                  id="buyer-cancel-reason"
                  v-model="cancelReason"
                  class="input-standard"
                  type="text"
                  maxlength="100"
                  placeholder="Optional short reason (<=100 chars)"
                  :disabled="!canTriggerCancel"
                  @input="clearActionMessages"
                />
              </div>
              <div class="flex flex-wrap items-center gap-3">
                <button class="btn-default" type="button" :disabled="!canTriggerCancel" @click="submitCancelOrder">
                  <Loader2 v-if="actionSubmitting" class="h-4 w-4 animate-spin" />
                  <span>{{ actionSubmitting ? 'Submitting...' : 'Cancel order' }}</span>
                </button>
              </div>
              <p class="form-helper">{{ cancelGuardText }}</p>
            </section>
          </div>

          <section class="rounded-2xl border border-gray-100 bg-gray-50/80 p-4 space-y-3">
            <h3 class="text-[14px] font-semibold text-gray-900">Confirm receipt</h3>
            <div class="flex flex-wrap items-center gap-3">
              <button class="btn-primary" type="button" :disabled="!canTriggerConfirmReceipt" @click="submitConfirmReceipt">
                <Loader2 v-if="actionSubmitting" class="h-4 w-4 animate-spin" />
                <span>{{ actionSubmitting ? 'Submitting...' : 'Confirm receipt' }}</span>
              </button>
            </div>
            <p class="form-helper">{{ confirmReceiptGuardText }}</p>
          </section>

          <section class="rounded-2xl border border-gray-100 bg-gray-50/80 p-4 space-y-3">
            <h3 class="text-[14px] font-semibold text-gray-900">After-sale apply</h3>
            <div v-if="afterSaleErrorMessage" class="notice-banner notice-banner-danger">
              <span class="notice-dot bg-red-500"></span>
              <span>{{ afterSaleErrorMessage }}</span>
            </div>
            <div v-if="afterSaleSuccessMessage" class="notice-banner notice-banner-success">
              <span class="notice-dot bg-emerald-500"></span>
              <span>{{ afterSaleSuccessMessage }}</span>
            </div>

            <div class="w-full">
              <label class="form-label" for="buyer-after-sale-reason">After-sale reason</label>
              <textarea
                id="buyer-after-sale-reason"
                v-model="afterSaleForm.reason"
                class="input-standard min-h-[112px]"
                maxlength="200"
                placeholder="Input reason (2~200 chars)"
                :disabled="afterSaleSubmitting"
                @input="clearAfterSaleMessages"
              ></textarea>
            </div>

            <div class="w-full">
              <label class="form-label" for="buyer-after-sale-evidence-images">Evidence image URLs (optional)</label>
              <textarea
                id="buyer-after-sale-evidence-images"
                v-model="afterSaleForm.evidenceImageText"
                class="input-standard min-h-[96px]"
                placeholder="Split by newline/comma, up to 3 URLs"
                :disabled="afterSaleSubmitting"
                @input="clearAfterSaleMessages"
              ></textarea>
              <p class="form-helper">The page input is normalized in API layer and mapped to <code>evidenceImages</code>.</p>
            </div>

            <div class="flex flex-wrap items-center gap-3">
              <button class="btn-primary" type="button" :disabled="!canTriggerAfterSaleApply" @click="submitAfterSaleApply">
                <Loader2 v-if="afterSaleSubmitting" class="h-4 w-4 animate-spin" />
                <span>{{ afterSaleSubmitting ? 'Submitting...' : 'Submit apply' }}</span>
              </button>
              <span v-if="createdAfterSaleId !== null" class="chip chip-neutral font-numeric">afterSaleId {{ createdAfterSaleId }}</span>
            </div>
            <p class="form-helper">{{ afterSaleApplyGuardText }}</p>
          </section>

          <section class="rounded-2xl border border-gray-100 bg-gray-50/80 p-4 space-y-3">
            <h3 class="text-[14px] font-semibold text-gray-900">Dispute initiate (platform intervention)</h3>
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
                <label class="form-label" for="buyer-dispute-after-sale-id">afterSaleId</label>
                <input
                  id="buyer-dispute-after-sale-id"
                  v-model="disputeForm.afterSaleId"
                  class="input-standard"
                  type="text"
                  inputmode="numeric"
                  placeholder="e.g. 50001"
                  :disabled="disputeSubmitting"
                  @input="clearDisputeMessages"
                />
              </div>
              <div>
                <label class="form-label" for="buyer-dispute-content">Dispute content</label>
                <textarea
                  id="buyer-dispute-content"
                  v-model="disputeForm.content"
                  class="input-standard min-h-[112px]"
                  maxlength="500"
                  placeholder="Input content (2~500 chars)"
                  :disabled="disputeSubmitting"
                  @input="clearDisputeMessages"
                ></textarea>
              </div>
            </div>

            <div class="flex flex-wrap items-center gap-3">
              <button class="btn-default" type="button" :disabled="!canTriggerDisputeInitiate" @click="submitDisputeInitiate">
                <Loader2 v-if="disputeSubmitting" class="h-4 w-4 animate-spin" />
                <span>{{ disputeSubmitting ? 'Submitting...' : 'Submit dispute' }}</span>
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
