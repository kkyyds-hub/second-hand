<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ArrowLeft, Loader2, MapPin, PackageSearch, Plus, RefreshCw } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import { getMyAddressList, type UserAddressItem } from '@/api/address'
import { getMarketProductDetail, type MarketProductDetail } from '@/api/market'
import { createBuyerOrder } from '@/api/orders'
import { readCurrentUser } from '@/utils/request'
import CommerceAddressOption from '@/components/commerce/CommerceAddressOption.vue'

const route = useRoute()
const router = useRouter()
const product = ref<MarketProductDetail | null>(null)
const addresses = ref<UserAddressItem[]>([])
const selectedAddressId = ref<number | null>(null)
const productLoading = ref(false)
const addressLoading = ref(false)
const productError = ref('')
const addressError = ref('')
const submitError = ref('')
const submitting = ref(false)
const productImageFailed = ref(false)
const productErrorRef = ref<HTMLElement | null>(null)
const addressErrorRef = ref<HTMLElement | null>(null)
const submitErrorRef = ref<HTMLElement | null>(null)
let active = true
let requestSequence = 0
let submitRequestInFlight = false

function readProductId(value: unknown) {
  if (typeof value === 'number' && Number.isInteger(value) && value > 0) return value
  if (typeof value === 'string' && /^\d+$/.test(value.trim())) {
    const parsed = Number(value.trim())
    return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
  }
  return null
}

function readErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message.trim() ? error.message : fallback
}

const productId = computed(() => readProductId(route.params.productId))
const invalidProductId = computed(() => productId.value === null)
const currentUser = computed(() => readCurrentUser())
const isOwnProduct = computed(() => {
  const ownerId = product.value?.ownerId
  const currentUserId = currentUser.value?.id

  return typeof ownerId === 'number'
    && Number.isSafeInteger(ownerId)
    && ownerId > 0
    && typeof currentUserId === 'number'
    && Number.isSafeInteger(currentUserId)
    && currentUserId > 0
    && ownerId === currentUserId
})
const selectedAddress = computed(() => addresses.value.find((item) => item.id === selectedAddressId.value) ?? null)
const hasAddress = computed(() => addresses.value.some((item) => item.id !== null && item.id > 0))
const canSubmit = computed(() => Boolean(
  productId.value !== null && product.value && selectedAddress.value?.id && !isOwnProduct.value && !productLoading.value && !addressLoading.value && !submitting.value,
))
const checkoutRedirect = computed(() => productId.value === null ? '/account/addresses/new' : `/account/addresses/new?redirect=/checkout/${productId.value}`)
const shouldReturnToMarket = computed(() => /已被购买|不可购买|非在售|下架|已售/.test(submitError.value))

function isRequestCurrent(sequence: number, expectedProductId: number) {
  return active
    && sequence === requestSequence
    && route.name === 'BuyerCheckout'
    && productId.value === expectedProductId
}

function isSubmissionCurrent(sequence: number, expectedProductId: number, expectedPath: string) {
  return isRequestCurrent(sequence, expectedProductId) && route.fullPath === expectedPath
}

async function focusProductError(sequence: number, expectedProductId: number) {
  await nextTick()
  if (isRequestCurrent(sequence, expectedProductId)) productErrorRef.value?.focus()
}

async function focusAddressError(sequence: number, expectedProductId: number) {
  await nextTick()
  if (isRequestCurrent(sequence, expectedProductId)) addressErrorRef.value?.focus()
}

async function focusSubmitError(sequence: number, expectedProductId: number, expectedPath: string) {
  await nextTick()
  if (isSubmissionCurrent(sequence, expectedProductId, expectedPath)) submitErrorRef.value?.focus()
}

function clearRouteState() {
  product.value = null
  addresses.value = []
  selectedAddressId.value = null
  productError.value = ''
  addressError.value = ''
  submitError.value = ''
  productImageFailed.value = false
}

function selectDefaultAddress(items: UserAddressItem[]) {
  const selected = items.find((item) => item.isDefault && item.id !== null && item.id > 0)
    ?? items.find((item) => item.id !== null && item.id > 0)
  selectedAddressId.value = selected?.id ?? null
}

async function loadProduct(id: number, sequence: number) {
  if (!isRequestCurrent(sequence, id)) return
  productLoading.value = true
  productError.value = ''
  try {
    const result = await getMarketProductDetail(id)
    if (isRequestCurrent(sequence, id)) product.value = result
  } catch (error: unknown) {
    if (!isRequestCurrent(sequence, id)) return
    productError.value = readErrorMessage(error, '商品详情暂时无法加载，请稍后重试。')
    productLoading.value = false
    await focusProductError(sequence, id)
  } finally {
    if (isRequestCurrent(sequence, id)) productLoading.value = false
  }
}

async function loadAddresses(id: number, sequence: number) {
  if (!isRequestCurrent(sequence, id)) return
  addressLoading.value = true
  addressError.value = ''
  try {
    const result = await getMyAddressList({ page: 1, pageSize: 100 })
    if (!isRequestCurrent(sequence, id)) return
    addresses.value = result.list
    selectDefaultAddress(result.list)
  } catch (error: unknown) {
    if (!isRequestCurrent(sequence, id)) return
    addressError.value = readErrorMessage(error, '地址列表暂时无法加载，请稍后重试。')
    addressLoading.value = false
    await focusAddressError(sequence, id)
  } finally {
    if (isRequestCurrent(sequence, id)) addressLoading.value = false
  }
}

async function loadCheckout() {
  if (!active || route.name !== 'BuyerCheckout') return
  const sequence = ++requestSequence
  clearRouteState()
  const id = productId.value
  if (id === null) return
  await Promise.all([loadProduct(id, sequence), loadAddresses(id, sequence)])
}

function retryLoad() {
  void loadCheckout()
}

async function refreshAddresses() {
  const id = productId.value
  if (id === null) return
  await loadAddresses(id, requestSequence)
}

async function submitOrder() {
  const id = productId.value
  const addressId = selectedAddressId.value
  if (id === null || addressId === null || isOwnProduct.value || submitRequestInFlight || submitting.value) return

  const sequence = requestSequence
  const submittingPath = route.fullPath
  const submittingProductId = id
  if (!isSubmissionCurrent(sequence, submittingProductId, submittingPath)) return

  submitRequestInFlight = true
  submitting.value = true
  submitError.value = ''
  try {
    const result = await createBuyerOrder({ productId: id, addressId })
    if (!isSubmissionCurrent(sequence, submittingProductId, submittingPath)) return
    if (result.orderId === null) {
      submitError.value = '订单已创建，但未返回有效订单编号，请前往买家订单列表查看。'
      await focusSubmitError(sequence, submittingProductId, submittingPath)
      return
    }
    await router.replace(`/orders/buyer/${result.orderId}`)
  } catch (error: unknown) {
    if (!isSubmissionCurrent(sequence, submittingProductId, submittingPath)) return
    const message = readErrorMessage(error, '创建订单失败，请稍后重试。')
    submitError.value = message
    await focusSubmitError(sequence, submittingProductId, submittingPath)
    if (/地址/.test(message) && isSubmissionCurrent(sequence, submittingProductId, submittingPath)) {
      await refreshAddresses()
    }
  } finally {
    submitRequestInFlight = false
    if (isSubmissionCurrent(sequence, submittingProductId, submittingPath)) submitting.value = false
  }
}

watch(productId, () => {
  void loadCheckout()
}, { immediate: true })

onBeforeUnmount(() => {
  active = false
  requestSequence += 1
})
</script>

<template>
  <main class="page-body page-body-narrow space-y-6">
    <nav class="flex items-center gap-2 text-[13px] text-gray-500" aria-label="面包屑">
      <router-link class="inline-flex items-center gap-1 font-medium text-gray-600 hover:text-blue-700" to="/market">
        <ArrowLeft class="h-4 w-4" />
        返回市场
      </router-link>
      <span aria-hidden="true">/</span>
      <span>确认订单</span>
    </nav>

    <section v-if="invalidProductId" class="section-panel">
      <div class="empty-state min-h-[300px]">
        <p class="empty-state-title">商品地址无效</p>
        <p class="empty-state-text">请返回市场后重新选择商品。</p>
        <router-link class="btn-primary mt-5" to="/market">返回市场</router-link>
      </div>
    </section>

    <template v-else>
      <section v-if="productError" class="section-panel">
        <div ref="productErrorRef" class="empty-state min-h-[300px]" role="alert" aria-live="assertive" tabindex="-1">
          <p class="empty-state-title">商品详情加载失败</p>
          <p class="empty-state-text">{{ productError }}</p>
          <div class="mt-5 flex flex-wrap justify-center gap-3">
            <button class="btn-primary" type="button" :disabled="productLoading" @click="retryLoad">重新加载</button>
            <router-link class="btn-default" to="/market">返回市场</router-link>
          </div>
        </div>
      </section>

      <template v-else>
        <header class="page-header">
          <div class="page-header-main"><p class="page-kicker">确认订单</p><h1 class="page-title">核对商品与收货信息</h1><p class="page-desc">本次仅创建当前商品的独立订单，金额以商品实际展示为准。</p></div>
        </header>

        <div class="grid gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
          <div class="min-w-0 space-y-6">
            <section class="section-panel">
              <div class="section-header"><div><h2 class="section-heading">商品确认</h2><p class="section-subtitle">数量固定为 1 件</p></div><router-link v-if="productId" class="btn-default" :to="`/market/${productId}`">返回商品详情</router-link></div>
              <div v-if="productLoading || !product" class="section-body flex min-h-40 items-center justify-center gap-3 text-gray-500" role="status" aria-live="polite"><Loader2 class="h-5 w-5 animate-spin" />正在加载商品</div>
              <div v-else class="section-body"><div class="flex flex-col gap-4 sm:flex-row"><div class="flex h-28 w-28 shrink-0 items-center justify-center overflow-hidden rounded-md border border-gray-200 bg-gray-50"><img v-if="product.coverUrl && !productImageFailed" class="h-full w-full object-cover" :src="product.coverUrl" :alt="product.title" @error="productImageFailed = true" /><PackageSearch v-else class="h-7 w-7 text-gray-300" aria-label="商品图片不可用" /></div><div class="min-w-0 flex-1"><p class="break-words text-[17px] font-semibold text-gray-900">{{ product.title }}</p><p class="mt-2 text-[13px] text-gray-500">{{ product.categoryName || '未分类' }}</p><div class="mt-5 flex flex-wrap items-end justify-between gap-3"><span class="font-numeric text-[22px] font-bold text-gray-950">¥ {{ product.price.toFixed(2) }}</span><span class="text-[13px] text-gray-600">数量 1</span></div></div></div></div>
            </section>

            <section v-if="isOwnProduct" class="notice-banner notice-banner-warning" role="alert" aria-live="assertive"><div><p class="font-semibold">不能购买自己发布的商品</p><div class="mt-3 flex flex-wrap gap-3"><router-link class="btn-default" :to="`/market/${productId}`">返回商品详情</router-link><router-link class="btn-default" to="/market">返回市场</router-link></div></div></section>

            <section class="section-panel">
              <div class="section-header"><div><h2 class="section-heading">收货地址</h2><p class="section-subtitle">订单将保存当前选择的地址快照。</p></div><button class="icon-button" type="button" title="刷新地址" :disabled="addressLoading || submitting" @click="refreshAddresses"><RefreshCw class="h-4 w-4" /></button></div>
              <div v-if="addressLoading" class="section-body flex min-h-32 items-center gap-3 text-gray-500" role="status" aria-live="polite"><Loader2 class="h-5 w-5 animate-spin" />正在加载地址</div>
              <div v-else-if="addressError" ref="addressErrorRef" class="section-body" role="alert" aria-live="assertive" tabindex="-1"><div class="notice-banner notice-banner-danger"><span>{{ addressError }}</span></div><button class="btn-default mt-4" type="button" :disabled="submitting" @click="refreshAddresses">重新加载地址</button></div>
              <div v-else-if="!hasAddress" class="section-body"><div class="empty-state min-h-44"><MapPin class="empty-state-icon" /><p class="empty-state-title">暂无收货地址</p><p class="empty-state-text">添加地址后即可确认订单。</p><router-link class="btn-primary mt-4" :to="checkoutRedirect"><Plus class="h-4 w-4" />新增收货地址</router-link></div></div>
              <fieldset v-else class="section-body space-y-3" :disabled="submitting"><legend class="sr-only">选择收货地址</legend><CommerceAddressOption v-for="address in addresses" :key="address.id ?? address.fullAddress" :address="address" :model-value="selectedAddressId" name="checkout-address" :disabled="submitting" @update:model-value="selectedAddressId = $event" /></fieldset>
            </section>
          </div>

          <aside v-if="hasAddress || isOwnProduct" class="hidden lg:block lg:self-start lg:sticky lg:top-24"><section class="section-panel"><div class="section-body space-y-4"><h2 class="section-heading">订单摘要</h2><div class="flex items-center justify-between text-[13px] text-gray-600"><span>商品金额</span><span v-if="product" class="font-numeric text-gray-900">¥ {{ product.price.toFixed(2) }}</span></div><div class="flex items-center justify-between text-[13px] text-gray-600"><span>数量</span><span>1</span></div><div class="border-t border-stone-100 pt-4"><p class="text-[13px] text-gray-500">订单总额</p><p v-if="product" class="font-numeric mt-1 text-[26px] font-bold text-gray-950">¥ {{ product.price.toFixed(2) }}</p></div><p class="rounded-md bg-stone-50 p-3 text-[12px] leading-5 text-gray-500">提交后将创建订单并进入订单详情。</p><button class="btn-primary w-full" type="button" :disabled="!canSubmit" :aria-busy="submitting" @click="submitOrder"><Loader2 v-if="submitting" class="h-4 w-4 animate-spin" /><span>{{ submitting ? '正在创建订单' : '确认订单' }}</span></button></div></section></aside>
        </div>

        <section v-if="submitError" ref="submitErrorRef" class="notice-banner notice-banner-danger" role="alert" aria-live="assertive" tabindex="-1"><div><p>{{ submitError }}</p><router-link v-if="shouldReturnToMarket" class="btn-default mt-3" to="/market">返回市场</router-link><router-link v-else-if="submitError.includes('未返回有效订单编号')" class="btn-default mt-3" to="/orders/buyer">查看买家订单</router-link></div></section>

        <div v-if="hasAddress || isOwnProduct" class="commerce-mobile-action-bar lg:hidden" aria-label="确认订单操作"><div v-if="product" class="min-w-0"><p class="text-[12px] text-gray-500">订单总额</p><p class="font-numeric text-[18px] font-bold text-gray-950">¥ {{ product.price.toFixed(2) }}</p></div><button class="btn-primary shrink-0" type="button" :disabled="!canSubmit" :aria-busy="submitting" @click="submitOrder"><Loader2 v-if="submitting" class="h-4 w-4 animate-spin" />{{ submitting ? '正在创建' : '确认订单' }}</button></div>
      </template>
    </template>
  </main>
</template>
