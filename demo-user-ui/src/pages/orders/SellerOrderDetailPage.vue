<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Loader2, PackageSearch, RefreshCw, Truck } from 'lucide-vue-next'
import {
  createEmptySellerOrderDetail,
  createEmptySellerOrderLogistics,
  getSellerOrderDetail,
  getSellerOrderLogistics,
  getSellerOrderStatusMeta,
  readShipSellerOrderValidationError,
  shipSellerOrder,
  type SellerOrderDetail,
  type SellerOrderLogistics,
} from '@/api/orders'
import OrderMessagePanel from '@/pages/orders/components/OrderMessagePanel.vue'
import { getUserDisplayName, isSellerUser, readCurrentUser } from '@/utils/request'

const route = useRoute()
const currentUser = readCurrentUser()
const sellerEnabled = computed(() => isSellerUser(currentUser))
const sellerName = computed(() => getUserDisplayName(currentUser))

const loading = ref(false)
const hasLoadedOnce = ref(false)
const errorMessage = ref('')
const detail = ref<SellerOrderDetail>(createEmptySellerOrderDetail())

const logisticsLoading = ref(false)
const logisticsErrorMessage = ref('')
const logistics = ref<SellerOrderLogistics>(createEmptySellerOrderLogistics())

const shipSubmitting = ref(false)
const shipErrorMessage = ref('')
const shipSuccessMessage = ref('')
const shipForm = reactive({
  shippingCompany: '',
  trackingNo: '',
  remark: '',
})

const orderId = computed(() => {
  const rawId = route.params.orderId
  if (Array.isArray(rawId)) {
    return rawId[0] || ''
  }

  return typeof rawId === 'string' ? rawId : ''
})

const statusMeta = computed(() => getSellerOrderStatusMeta(detail.value.status))
const productImages = computed(() => {
  if (detail.value.productImages.length > 0) {
    return detail.value.productImages
  }

  if (detail.value.productThumbnail) {
    return [detail.value.productThumbnail]
  }

  return []
})

const shipValidationError = computed(() => readShipSellerOrderValidationError(shipForm))
const canEditShipFields = computed(() => {
  return Boolean(
    sellerEnabled.value
      && detail.value.orderId !== null
      && detail.value.status === 'paid'
      && !loading.value
      && !shipSubmitting.value,
  )
})

/**
 * 后端 `ship` 接口对已发货 / 已完成订单保留幂等成功口径，
 * 但页面主按钮仍只在 `paid` 状态开放：
 * 1) 让“正常履约发货入口”和“并发补偿/重复点击返回”明确分层；
 * 2) review 时能一眼看出 seller 主链只承认 `paid -> shipped`；
 * 3) 真正的兜底幂等继续留给后端，不让页面承担状态裁定职责。
 */
const canSubmitShip = computed(() => canEditShipFields.value && !shipValidationError.value)

const logisticsEmptyText = computed(() => {
  if (detail.value.status === 'pending') {
    return '买家尚未支付，物流轨迹尚未开始。'
  }

  if (detail.value.status === 'paid') {
    return '订单待发货，当前只有订单快照，暂无物流轨迹。'
  }

  if (detail.value.status === 'cancelled') {
    return '订单已取消，物流轨迹不再更新。'
  }

  return '当前暂无物流轨迹，可稍后刷新再看。'
})

const shipGuardText = computed(() => {
  if (!sellerEnabled.value) {
    return '当前账号的本地 session 快照未显示卖家身份，前端先不开放发货主链。'
  }

  if (loading.value) {
    return '订单详情加载中，暂不能提交发货。'
  }

  if (shipSubmitting.value) {
    return '正在提交发货信息，请稍候。'
  }

  if (detail.value.orderId === null) {
    return '订单信息缺失，暂不能提交发货。'
  }

  if (detail.value.status === 'pending') {
    return '买家完成支付后，卖家才能执行发货。'
  }

  if (detail.value.status === 'cancelled') {
    return '已取消订单不能继续发货。'
  }

  if (detail.value.status === 'shipped' || detail.value.status === 'completed') {
    return '订单已进入发货后阶段；接口仍具备幂等保护，但主按钮不再作为常规入口开放。'
  }

  if (shipValidationError.value) {
    return shipValidationError.value
  }

  return '仅已支付订单可发货，提交后页面会同步刷新订单详情与物流快照。'
})

function readErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return fallback
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

function syncShipFormFromDetail() {
  shipForm.shippingCompany = detail.value.shippingCompany
  shipForm.trackingNo = detail.value.trackingNo
  shipForm.remark = detail.value.shippingRemark
}

function clearShipMessages() {
  if (shipSubmitting.value) {
    return
  }

  shipErrorMessage.value = ''
  shipSuccessMessage.value = ''
}

async function loadLogistics(targetOrderId: number | string) {
  try {
    logisticsLoading.value = true
    logisticsErrorMessage.value = ''
    logistics.value = await getSellerOrderLogistics(targetOrderId)
  } catch (error: unknown) {
    logisticsErrorMessage.value = readErrorMessage(error, '物流信息加载失败，请稍后重试。')
    logistics.value = createEmptySellerOrderLogistics()
  } finally {
    logisticsLoading.value = false
  }
}

async function loadPage() {
  if (loading.value || !sellerEnabled.value) {
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''

    detail.value = await getSellerOrderDetail(orderId.value)
    syncShipFormFromDetail()

    if (detail.value.orderId !== null) {
      await loadLogistics(detail.value.orderId)
    }
  } catch (error: unknown) {
    errorMessage.value = readErrorMessage(error, '卖家订单详情加载失败，请稍后重试。')
    detail.value = createEmptySellerOrderDetail()
    logistics.value = createEmptySellerOrderLogistics()
  } finally {
    loading.value = false
    hasLoadedOnce.value = true
  }
}

async function refreshLogistics() {
  if (detail.value.orderId === null || logisticsLoading.value) {
    return
  }

  await loadLogistics(detail.value.orderId)
}

async function submitShip() {
  if (shipSubmitting.value || detail.value.orderId === null) {
    return
  }

  const validationError = readShipSellerOrderValidationError(shipForm)
  if (validationError) {
    shipErrorMessage.value = validationError
    shipSuccessMessage.value = ''
    return
  }

  try {
    shipSubmitting.value = true
    shipErrorMessage.value = ''
    shipSuccessMessage.value = ''

    shipSuccessMessage.value = await shipSellerOrder(detail.value.orderId, shipForm)

    /**
     * 发货成功后要同时刷新 detail + logistics：
     * - 详情页状态从 `paid` 进入 `shipped`；
     * - 物流快照补齐 company / trackingNo / shipTime；
     * - 当前页先把履约事实同步出来，避免用户回列表才看到新状态。
     */
    await loadPage()
  } catch (error: unknown) {
    shipErrorMessage.value = readErrorMessage(error, '发货失败，请稍后重试。')
    shipSuccessMessage.value = ''
  } finally {
    shipSubmitting.value = false
  }
}

onMounted(() => {
  if (sellerEnabled.value) {
    loadPage()
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
          <h1 class="page-title">卖家订单详情</h1>
          <p class="page-desc">
            {{ sellerName }}，这里把订单详情、物流查看与卖家发货保持在同一页，避免把 Day06 第一条主链过早拆碎。
          </p>
        </div>
        <div class="page-actions">
          <router-link class="btn-default" to="/orders/seller">返回卖家订单</router-link>
          <button class="btn-default" type="button" :disabled="loading || !sellerEnabled" @click="loadPage">
            <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
            <RefreshCw v-else class="h-4 w-4" />
            <span>{{ loading ? '刷新中' : '刷新详情' }}</span>
          </button>
        </div>
      </div>
    </section>

    <section v-if="!sellerEnabled" class="notice-banner notice-banner-warning">
      <span class="notice-dot bg-orange-500"></span>
      <span>当前账号的本地 session 快照未显示卖家身份，因此本页先停在提示态；运行态权限仍以 `/user/orders/{id}` 与 `/ship` 接口校验为准。</span>
    </section>

    <section v-else-if="errorMessage" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <div class="flex-1">
        <p class="font-semibold">卖家订单详情加载失败</p>
        <p class="mt-1 text-[12px] leading-5">{{ errorMessage }}</p>
        <button class="btn-default mt-3" type="button" :disabled="loading" @click="loadPage">重新加载</button>
      </div>
    </section>

    <section v-if="loading && !hasLoadedOnce && sellerEnabled" class="section-panel">
      <div class="section-body">
        <div class="empty-state min-h-[360px]">
          <Loader2 class="empty-state-icon animate-spin text-blue-500" />
          <p class="empty-state-title">正在加载卖家订单详情</p>
        </div>
      </div>
    </section>

    <template v-else-if="sellerEnabled && detail.orderId !== null">
      <section class="section-panel">
        <div class="section-header">
          <div>
            <div class="flex flex-wrap items-center gap-2">
              <h2 class="section-heading">订单概览</h2>
              <span :class="readStatusChipClass(detail.status)">{{ statusMeta.label }}</span>
            </div>
            <p class="section-subtitle">订单号 {{ detail.orderNo || '-' }}，用于卖家查看订单快照并承接发货动作。</p>
          </div>
        </div>
        <div class="section-body">
          <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
            <article class="metric-card">
              <p class="metric-label">买家</p>
              <p class="metric-value !mt-2 !text-[18px]">{{ detail.buyerNickname || '-' }}</p>
            </article>
            <article class="metric-card">
              <p class="metric-label">订单总额</p>
              <p class="metric-value !mt-2 !text-[18px] font-numeric">¥ {{ detail.totalAmount.toFixed(2) }}</p>
            </article>
            <article class="metric-card">
              <p class="metric-label">支付时间</p>
              <p class="metric-value !mt-2 !text-[15px]">{{ detail.payTime || '-' }}</p>
            </article>
            <article class="metric-card">
              <p class="metric-label">发货时间</p>
              <p class="metric-value !mt-2 !text-[15px]">{{ detail.shipTime || '-' }}</p>
            </article>
          </div>
        </div>
      </section>

      <section class="section-panel">
        <div class="section-header">
          <div>
            <h2 class="section-heading">卖家发货</h2>
            <p class="section-subtitle">前端 guard 只认 `paid -> shipped` 主链，其余状态保持只读提示。</p>
          </div>
        </div>
        <div class="section-body space-y-4">
          <div v-if="shipErrorMessage" class="notice-banner notice-banner-danger">
            <span class="notice-dot bg-red-500"></span>
            <span>{{ shipErrorMessage }}</span>
          </div>
          <div v-if="shipSuccessMessage" class="notice-banner notice-banner-success">
            <span class="notice-dot bg-emerald-500"></span>
            <span>{{ shipSuccessMessage }}</span>
          </div>

          <div class="grid gap-4 lg:grid-cols-[240px_240px_minmax(0,1fr)]">
            <div>
              <label class="form-label" for="seller-ship-company">物流公司</label>
              <input
                id="seller-ship-company"
                v-model="shipForm.shippingCompany"
                class="input-standard"
                type="text"
                maxlength="50"
                placeholder="例如 SF Express"
                :disabled="!canEditShipFields"
                @input="clearShipMessages"
              />
            </div>
            <div>
              <label class="form-label" for="seller-ship-tracking-no">运单号</label>
              <input
                id="seller-ship-tracking-no"
                v-model="shipForm.trackingNo"
                class="input-standard"
                type="text"
                maxlength="50"
                placeholder="6~50 位字母/数字/中划线"
                :disabled="!canEditShipFields"
                @input="clearShipMessages"
              />
            </div>
            <div>
              <label class="form-label" for="seller-ship-remark">发货备注</label>
              <input
                id="seller-ship-remark"
                v-model="shipForm.remark"
                class="input-standard"
                type="text"
                maxlength="200"
                placeholder="可选，最多 200 个字符"
                :disabled="!canEditShipFields"
                @input="clearShipMessages"
              />
            </div>
          </div>

          <div class="flex flex-wrap items-center gap-3">
            <button class="btn-primary" type="button" :disabled="!canSubmitShip" @click="submitShip">
              <Loader2 v-if="shipSubmitting" class="h-4 w-4 animate-spin" />
              <Truck v-else class="h-4 w-4" />
              <span>{{ shipSubmitting ? '提交中...' : '提交发货' }}</span>
            </button>
            <span class="chip chip-muted">当前状态：{{ statusMeta.label }}</span>
          </div>
          <p class="form-helper">{{ shipGuardText }}</p>
        </div>
      </section>

      <section class="section-panel">
        <div class="section-header">
          <div>
            <h2 class="section-heading">商品信息</h2>
            <p class="section-subtitle">展示订单内商品快照，避免卖家发货时回到其它页面重复确认。</p>
          </div>
        </div>
        <div class="section-body">
          <div class="grid gap-6 lg:grid-cols-[320px_1fr]">
            <div>
              <img
                v-if="productImages.length > 0"
                :src="productImages[0]"
                :alt="detail.productTitle || 'seller order product'"
                class="h-[240px] w-full rounded-2xl border border-gray-200/80 object-cover"
              />
              <div v-else class="empty-state min-h-[240px] rounded-2xl border border-dashed border-gray-200 bg-gray-50">
                <PackageSearch class="empty-state-icon" />
                <p class="empty-state-title">暂无商品图片</p>
              </div>
              <div v-if="productImages.length > 1" class="mt-3 grid grid-cols-4 gap-2">
                <img
                  v-for="image in productImages.slice(1, 5)"
                  :key="image"
                  :src="image"
                  alt="product thumbnail"
                  class="h-16 w-full rounded-lg border border-gray-200 object-cover"
                />
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
                  <p class="meta-label">下单时间</p>
                  <p class="meta-value">{{ detail.createTime || '-' }}</p>
                </div>
                <div class="meta-item">
                  <p class="meta-label">卖家备注快照</p>
                  <p class="meta-value">{{ detail.shippingRemark || '-' }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="grid gap-6 lg:grid-cols-2">
        <section class="section-panel">
          <div class="section-header">
            <div>
              <h2 class="section-heading">物流查看</h2>
              <p class="section-subtitle">快照字段和动态轨迹拆开显示，便于 review 时区分“订单落库信息”和“provider 查询结果”。</p>
            </div>
            <div class="section-actions">
              <button class="btn-default !h-9 px-3" type="button" :disabled="logisticsLoading" @click="refreshLogistics">
                <Loader2 v-if="logisticsLoading" class="h-4 w-4 animate-spin" />
                <RefreshCw v-else class="h-4 w-4" />
                <span>{{ logisticsLoading ? '刷新中' : '刷新物流' }}</span>
              </button>
            </div>
          </div>
          <div class="section-body space-y-4">
            <div v-if="logisticsErrorMessage" class="notice-banner notice-banner-warning">
              <span class="notice-dot bg-orange-500"></span>
              <span>{{ logisticsErrorMessage }}</span>
            </div>

            <div class="detail-grid">
              <div class="detail-row">
                <span class="detail-label">物流公司</span>
                <span class="detail-value">{{ logistics.shippingCompany || detail.shippingCompany || '-' }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">运单号</span>
                <span class="detail-value">{{ logistics.trackingNo || detail.trackingNo || '-' }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">物流提供方</span>
                <span class="detail-value">{{ logistics.provider || '-' }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">最近同步</span>
                <span class="detail-value">{{ logistics.lastSyncTime || '-' }}</span>
              </div>
            </div>

            <div v-if="logistics.trace.length > 0" class="space-y-3">
              <article
                v-for="item in logistics.trace"
                :key="`${item.time}-${item.location}-${item.status}`"
                class="rounded-2xl border border-gray-100 bg-gray-50/80 px-4 py-3"
              >
                <div class="inline-meta font-numeric">
                  <span>{{ item.time || '-' }}</span>
                  <span class="inline-meta-dot"></span>
                  <span>{{ item.location || '-' }}</span>
                </div>
                <p class="mt-2 text-[13px] font-medium text-gray-800">{{ item.status || '-' }}</p>
              </article>
            </div>
            <div v-else class="empty-state min-h-[220px]">
              <PackageSearch class="empty-state-icon" />
              <p class="empty-state-title">暂无物流轨迹</p>
              <p class="empty-state-text">{{ logisticsEmptyText }}</p>
            </div>
          </div>
        </section>

        <section class="section-panel">
          <div class="section-header section-header-plain">
            <div>
              <h2 class="section-heading">地址与角色快照</h2>
            </div>
          </div>
          <div class="section-body pt-0">
            <div class="detail-grid">
              <div class="detail-row">
                <span class="detail-label">买家昵称</span>
                <span class="detail-value">{{ detail.buyerNickname || '-' }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">卖家昵称</span>
                <span class="detail-value">{{ detail.sellerNickname || '-' }}</span>
              </div>
              <div class="detail-row !items-start !justify-start !gap-1.5">
                <span class="detail-label">收货地址快照</span>
                <span class="detail-value">{{ detail.shippingAddress || '暂无地址快照' }}</span>
              </div>
              <div class="detail-row !items-start !justify-start !gap-1.5">
                <span class="detail-label">发货备注快照</span>
                <span class="detail-value">{{ detail.shippingRemark || '-' }}</span>
              </div>
            </div>
          </div>
        </section>
      </section>
      <OrderMessagePanel
        :order-id="detail.orderId"
        :current-user-id="detail.sellerId"
        :counterpart-user-id="detail.buyerId"
        :counterpart-label="detail.buyerNickname || '买家'"
      />
    </template>
  </div>
</template>
