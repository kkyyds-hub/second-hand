<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Loader2, RefreshCcw, ShoppingCart, Trash2 } from 'lucide-vue-next'
import {
  batchDeleteCartItems,
  checkoutCart,
  deleteCartItem,
  getCartItems,
  type CartCheckoutResult,
  type CartItem,
} from '@/api/cart'
import { getBuyerOrderList } from '@/api/orders'
import { getMyAddressList, type UserAddressItem } from '@/api/address'
import { useCartStore } from '@/stores/cart'
import { readCurrentUser } from '@/utils/request'
import CommerceAddressOption from '@/components/commerce/CommerceAddressOption.vue'
import cartEmptyImage from '@/assets/commerce/cart-empty.webp'

const router = useRouter()
const cartStore = useCartStore()

/** 单次结算最多选择件数。 */
const CHECKOUT_MAX_SELECT = 20

const items = ref<CartItem[]>([])
const loading = ref(false)
const loadError = ref('')

const addresses = ref<UserAddressItem[]>([])
const addressLoading = ref(false)
const addressError = ref('')
const selectedAddressId = ref<number | null>(null)

const selectedIds = ref<number[]>([])
const confirmDeleteId = ref<number | null>(null)
const singleDeleteSubmitting = ref(false)
const batchDeleteSubmitting = ref(false)

const checkoutSubmitting = ref(false)
const checkoutError = ref('')
const checkoutUnknown = ref(false)
const checkoutResult = ref<CartCheckoutResult | null>(null)
const selectLimitNotice = ref('')
const cartEmptyImageFailed = ref(false)
const cartImageFailedIds = ref<Set<string>>(new Set())

/**
 * 结算结果需重新核对锁。
 *
 * 触发条件：
 * 1) checkout 请求网络/超时类失败（后端可能已成功但前端不知道）；
 * 2) 后端响应计数不满足一致性恒等式。
 *
 * 锁定期间禁止再次提交 checkout、选择、删除、切换地址；
 * 只能通过“重新检查”成功同步后解除，不得定时自动解锁，不得自动重试结算。
 */
const checkoutReconcileRequired = ref(false)
const reconciling = ref(false)

/** 错误/状态区引用，出现后调用 focus()。 */
const reconcileRegionRef = ref<HTMLElement | null>(null)
const checkoutErrorRef = ref<HTMLElement | null>(null)
const loadErrorRef = ref<HTMLElement | null>(null)
const selectAllCheckboxRef = ref<HTMLInputElement | null>(null)

const currentUserId = computed(() => readCurrentUser()?.id ?? null)

const availableItems = computed(() => items.value.filter((item) => item.available && item.cartItemId !== null))
const unavailableItems = computed(() => items.value.filter((item) => !item.available))
const availableSellerGroups = computed(() => {
  const groups = new Map<string, { sellerId: number | null; sellerNickname: string; items: CartItem[] }>()
  for (const item of availableItems.value) {
    const sellerId = typeof item.sellerId === 'number' && Number.isSafeInteger(item.sellerId) && item.sellerId > 0
      ? item.sellerId
      : null
    const key = sellerId === null ? `unknown-${item.sellerNickname || 'seller'}` : `seller-${sellerId}`
    const current = groups.get(key)
    if (current) {
      current.items.push(item)
    } else {
      groups.set(key, { sellerId, sellerNickname: item.sellerNickname || '未知卖家', items: [item] })
    }
  }
  return [...groups.values()]
})

const selectedItems = computed(() => {
  const set = new Set(selectedIds.value)
  return items.value.filter((item) => item.cartItemId !== null && set.has(item.cartItemId) && item.available)
})
const selectedCount = computed(() => selectedItems.value.length)
const selectedTotalAmount = computed(() =>
  selectedItems.value.reduce((sum, item) => sum + (Number.isFinite(item.price) ? item.price : 0), 0),
)

/**
 * 全选状态机：none / partial / capped-all / all。
 *
 * - none：未选择任何可结算项；
 * - partial：选择了部分；
 * - capped-all：可结算项超过上限且已选满上限（前 20 件）；
 * - all：可结算项不超过上限且全部选中。
 */
type SelectAllState = 'none' | 'partial' | 'capped-all' | 'all'
const selectAllState = computed<SelectAllState>(() => {
  const availableCount = availableItems.value.length
  const selected = selectedCount.value
  if (availableCount === 0 || selected === 0) return 'none'
  if (availableCount > CHECKOUT_MAX_SELECT) {
    return selected >= CHECKOUT_MAX_SELECT ? 'capped-all' : 'partial'
  }
  return selected === availableCount ? 'all' : 'partial'
})
const selectAllChecked = computed(() => selectAllState.value === 'all' || selectAllState.value === 'capped-all')
const selectAllIndeterminate = computed(() => selectAllState.value === 'partial')
const selectAllLabel = computed(() => {
  if (availableItems.value.length > CHECKOUT_MAX_SELECT) {
    return selectAllState.value === 'capped-all'
      ? `取消选择（已选 ${CHECKOUT_MAX_SELECT} 件）`
      : `选择前 ${CHECKOUT_MAX_SELECT} 件（共 ${availableItems.value.length} 件可结算）`
  }
  return `全选可结算（${availableItems.value.length}）`
})

const canCheckout = computed(() => {
  return (
    selectedCount.value > 0 &&
    selectedAddressId.value !== null &&
    !checkoutSubmitting.value &&
    !checkoutReconcileRequired.value &&
    !loading.value
  )
})

/** 结果未知锁定时，禁用页面上所有会改变状态的操作。 */
const interactionLocked = computed(() => checkoutSubmitting.value || checkoutReconcileRequired.value)

const selectedAddress = computed(() => addresses.value.find((a) => a.id === selectedAddressId.value) ?? null)

/** 同步全选框的 indeterminate DOM 属性（无法通过 attribute 绑定）。 */
watch([selectAllIndeterminate, selectAllChecked], () => {
  nextTick(() => {
    if (selectAllCheckboxRef.value) {
      selectAllCheckboxRef.value.indeterminate = selectAllIndeterminate.value
    }
  })
})

function formatPrice(value: number) {
  return (Number.isFinite(value) ? value : 0).toFixed(2)
}

function cartImageKey(item: CartItem) {
  return String(item.cartItemId ?? item.productId ?? item.title)
}

function markCartImageFailed(item: CartItem) {
  const next = new Set(cartImageFailedIds.value)
  next.add(cartImageKey(item))
  cartImageFailedIds.value = next
}

function canLinkSeller(sellerId: number | null) {
  return typeof sellerId === 'number' && Number.isSafeInteger(sellerId) && sellerId > 0
}

function canLinkProduct(item: CartItem) {
  return typeof item.productId === 'number'
    && Number.isSafeInteger(item.productId)
    && item.productId > 0
    && item.productStatus !== 'deleted'
}

/** 判断是否为网络/超时类错误（结果未知）。 */
function isNetworkLikeError(message: string) {
  return /timeout|network|网络/i.test(message)
}

function pruneSelection() {
  const valid = new Set(availableItems.value.map((item) => item.cartItemId))
  selectedIds.value = selectedIds.value.filter((id) => valid.has(id))
}

async function loadCart() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await getCartItems()
    items.value = result
    pruneSelection()
  } catch (error) {
    items.value = []
    selectedIds.value = []
    loadError.value = error instanceof Error && error.message ? error.message : '购物车加载失败'
    await nextTick()
    loadErrorRef.value?.focus()
  } finally {
    loading.value = false
  }
}

async function loadAddresses() {
  addressLoading.value = true
  addressError.value = ''
  try {
    const result = await getMyAddressList({ page: 1, pageSize: 100 })
    addresses.value = result.list
    const valid = result.list.filter((a) => a.id !== null && a.id > 0)
    const preferred = valid.find((a) => a.isDefault) ?? valid[0] ?? null
    selectedAddressId.value = preferred?.id ?? null
  } catch (error) {
    addresses.value = []
    selectedAddressId.value = null
    addressError.value = error instanceof Error && error.message ? error.message : '地址加载失败'
  } finally {
    addressLoading.value = false
  }
}

async function reloadAll() {
  checkoutResult.value = null
  checkoutError.value = ''
  // 注意：手动刷新不清除结果未知锁（checkoutReconcileRequired），
  // 该锁只能通过“重新检查”成功同步后解除，避免在订单状态未核对前误解锁。
  await Promise.all([loadCart(), loadAddresses()])
  cartStore.refreshCount(currentUserId.value).catch(() => {})
}

function toggleSelect(cartItemId: number | null, available: boolean) {
  if (cartItemId === null || !available || interactionLocked.value) return
  const index = selectedIds.value.indexOf(cartItemId)
  if (index >= 0) {
    selectedIds.value.splice(index, 1)
    selectLimitNotice.value = ''
    return
  }
  if (selectedIds.value.length >= CHECKOUT_MAX_SELECT) {
    selectLimitNotice.value = `单次最多结算 ${CHECKOUT_MAX_SELECT} 件商品`
    return
  }
  selectedIds.value.push(cartItemId)
  selectLimitNotice.value = ''
}

function toggleSelectAll() {
  if (interactionLocked.value) return
  // 已处于 all 或 capped-all：一次操作清空全部选择。
  if (selectAllChecked.value) {
    selectedIds.value = []
    selectLimitNotice.value = ''
    return
  }
  const list = availableItems.value
  const capped = list.slice(0, CHECKOUT_MAX_SELECT)
  selectedIds.value = capped.map((item) => item.cartItemId as number)
  selectLimitNotice.value =
    list.length > CHECKOUT_MAX_SELECT ? `单次最多结算 ${CHECKOUT_MAX_SELECT} 件商品，已为你选中前 ${CHECKOUT_MAX_SELECT} 件` : ''
}

async function removeSingle(cartItemId: number | null) {
  if (cartItemId === null || singleDeleteSubmitting.value || interactionLocked.value) return
  singleDeleteSubmitting.value = true
  try {
    await deleteCartItem(cartItemId)
    items.value = items.value.filter((item) => item.cartItemId !== cartItemId)
    selectedIds.value = selectedIds.value.filter((id) => id !== cartItemId)
    confirmDeleteId.value = null
    cartStore.refreshCount(currentUserId.value).catch(() => {})
  } catch (error) {
    checkoutError.value = error instanceof Error && error.message ? error.message : '删除失败'
  } finally {
    singleDeleteSubmitting.value = false
  }
}

async function runBatchDelete(ids: number[]) {
  if (ids.length === 0 || batchDeleteSubmitting.value || interactionLocked.value) return
  batchDeleteSubmitting.value = true
  try {
    await batchDeleteCartItems(ids)
    const removed = new Set(ids)
    items.value = items.value.filter((item) => item.cartItemId === null || !removed.has(item.cartItemId))
    selectedIds.value = selectedIds.value.filter((id) => !removed.has(id))
    confirmDeleteId.value = null
    cartStore.refreshCount(currentUserId.value).catch(() => {})
  } catch (error) {
    checkoutError.value = error instanceof Error && error.message ? error.message : '批量删除失败'
  } finally {
    batchDeleteSubmitting.value = false
  }
}

function deleteSelected() {
  void runBatchDelete([...selectedIds.value])
}

function clearUnavailable() {
  const ids = unavailableItems.value.map((item) => item.cartItemId).filter((id): id is number => id !== null)
  void runBatchDelete(ids)
}

/**
 * 校验后端结算响应是否满足计数一致性恒等式：
 * requestedCount = successCount + failureCount
 * successCount = orders.length
 * failureCount = failures.length
 *
 * 不一致时不得显示“全部完成”，必须进入结果需重新检查状态。
 */
function isConsistentResult(result: CartCheckoutResult) {
  return (
    result.successCount === result.orders.length &&
    result.failureCount === result.failures.length &&
    result.requestedCount === result.successCount + result.failureCount
  )
}

/** 进入“结果需重新检查”锁定状态，并聚焦状态区。 */
async function enterReconcileLock() {
  checkoutReconcileRequired.value = true
  checkoutResult.value = null
  await nextTick()
  reconcileRegionRef.value?.focus()
}

async function submitCheckout() {
  if (!canCheckout.value || checkoutSubmitting.value || checkoutReconcileRequired.value) return
  const cartItemIds = [...selectedIds.value]
  const addressId = selectedAddressId.value
  if (cartItemIds.length === 0 || addressId === null) return

  checkoutSubmitting.value = true
  checkoutError.value = ''
  checkoutUnknown.value = false
  checkoutReconcileRequired.value = false
  checkoutResult.value = null
  try {
    const result = await checkoutCart({ cartItemIds, addressId })

    // 响应一致性校验：不满足恒等式则视为结果不可信，进入重新检查锁。
    if (!isConsistentResult(result)) {
      await enterReconcileLock()
      await loadCart()
      cartStore.refreshCount(currentUserId.value).catch(() => {})
      return
    }

    checkoutResult.value = result
    const successIds = new Set(result.orders.map((o) => o.cartItemId).filter((id): id is number => id !== null))
    items.value = items.value.filter((item) => item.cartItemId === null || !successIds.has(item.cartItemId))
    selectedIds.value = selectedIds.value.filter((id) => !successIds.has(id))
    cartStore.refreshCount(currentUserId.value).catch(() => {})
    // 重新拉取以反映最新可用性（成交商品会变为已售）。
    await loadCart()
  } catch (error) {
    const message = error instanceof Error && error.message ? error.message : '结算失败'
    if (isNetworkLikeError(message)) {
      // 结果未知：后端可能已成功创建订单，进入重新检查锁，禁止再次提交。
      await enterReconcileLock()
    } else {
      checkoutError.value = message
      await nextTick()
      checkoutErrorRef.value?.focus()
    }
    await loadCart()
    cartStore.refreshCount(currentUserId.value).catch(() => {})
  } finally {
    checkoutSubmitting.value = false
  }
}

/**
 * 重新检查：在不发送 checkout POST 的前提下同步真实状态。
 *
 * 1) 重新 GET 购物车；
 * 2) GET 买家订单列表（读取已创建订单，不新增）；
 * 3) 更新角标；
 * 4) 移除已创建订单或已失效商品的选中项；
 * 5) 同步成功后才解除结果未知锁；失败则继续保持锁定。
 */
async function reconcile() {
  if (reconciling.value) return
  reconciling.value = true
  try {
    // 读取买家订单（仅 GET，绝不 POST checkout）。失败不阻断购物车同步，但记录。
    let ordersOk = true
    try {
      await getBuyerOrderList({ page: 1, pageSize: 20 })
    } catch {
      ordersOk = false
    }

    // 重新拉取购物车并剔除已不存在/已失效的选中项。
    const cart = await getCartItems()
    items.value = cart
    pruneSelection()
    cartStore.refreshCount(currentUserId.value).catch(() => {})

    if (!ordersOk) {
      // 订单读取失败：状态仍不可信，保持锁定。
      return
    }

    // 同步成功：解除锁定。
    checkoutReconcileRequired.value = false
    checkoutUnknown.value = false
  } catch {
    // 同步失败：继续保持锁定，不得自动解锁。
  } finally {
    reconciling.value = false
  }
}

function goToOrders() {
  void router.push('/orders/buyer')
}

onMounted(() => {
  void reloadAll()
})
</script>

<template>
  <div class="cart-page space-y-6">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-[24px] font-bold text-gray-950 sm:text-[28px]">购物车</h1>
        <p class="mt-1 text-[13px] text-gray-500">
          共 {{ items.length }} 件，其中 {{ availableItems.length }} 件可结算
        </p>
      </div>
      <button class="btn-default" type="button" :disabled="loading" @click="reloadAll">
        <RefreshCcw class="h-4 w-4" :class="loading ? 'animate-spin' : ''" aria-hidden="true" />
        刷新
      </button>
    </header>

    <!-- 加载中 -->
    <div v-if="loading" class="section-panel section-body" role="status" aria-live="polite">
      <p class="flex items-center gap-2 text-[14px] text-gray-600">
        <Loader2 class="h-4 w-4 animate-spin" aria-hidden="true" />
        正在加载购物车...
      </p>
    </div>

    <!-- 加载失败 -->
    <div
      v-else-if="loadError"
      ref="loadErrorRef"
      class="section-panel section-body"
      role="alert"
      aria-live="assertive"
      tabindex="-1"
    >
      <p class="text-[15px] font-semibold text-gray-900">购物车加载失败</p>
      <p class="mt-1 text-[13px] text-red-700">{{ loadError }}</p>
      <button class="btn-primary mt-4" type="button" @click="loadCart">重新加载</button>
    </div>

    <!-- 空购物车 -->
    <div v-else-if="items.length === 0" class="empty-state section-panel px-5 py-8 sm:px-8">
      <img
        v-if="!cartEmptyImageFailed"
        :src="cartEmptyImage"
        alt="空购物车"
        class="cart-empty-image"
        @error="cartEmptyImageFailed = true"
      />
      <span v-else class="empty-state-icon"><ShoppingCart class="h-8 w-8" aria-hidden="true" /></span>
      <p class="empty-state-title">购物车还是空的</p>
      <p class="empty-state-text">去挑几件心仪的闲置好物吧。</p>
      <router-link class="btn-primary mt-4 inline-flex" to="/market">去逛市场</router-link>
    </div>

    <!-- 购物车列表 + 结算 -->
    <template v-else>
      <div class="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
        <!-- 左侧：列表 -->
        <section class="min-w-0 space-y-4">
          <div class="section-panel section-body flex flex-wrap items-center gap-3">
            <label class="flex cursor-pointer items-center gap-2 text-[14px] font-medium text-gray-800">
              <input
                ref="selectAllCheckboxRef"
                type="checkbox"
                class="checkbox-standard"
                :checked="selectAllChecked"
                :disabled="availableItems.length === 0 || interactionLocked"
                :aria-label="selectAllLabel"
                @change="toggleSelectAll"
              />
              {{ selectAllLabel }}
            </label>
            <div class="ml-auto flex flex-wrap gap-2">
              <button
                class="btn-default"
                type="button"
                :disabled="selectedIds.length === 0 || batchDeleteSubmitting || interactionLocked"
                @click="deleteSelected"
              >
                <Trash2 class="h-4 w-4" aria-hidden="true" />
                删除选中（{{ selectedIds.length }}）
              </button>
              <button
                class="btn-default"
                type="button"
                :disabled="unavailableItems.length === 0 || batchDeleteSubmitting || interactionLocked"
                @click="clearUnavailable"
              >
                清理失效商品（{{ unavailableItems.length }}）
              </button>
            </div>
          </div>

          <p v-if="selectLimitNotice" class="notice-banner notice-banner-warning" role="status">{{ selectLimitNotice }}</p>

          <div v-for="group in availableSellerGroups" :key="group.sellerId ?? group.sellerNickname" class="section-panel overflow-hidden">
            <div class="commerce-seller-group-header">
              <div class="min-w-0">
                <router-link v-if="canLinkSeller(group.sellerId)" :to="`/shop/${group.sellerId}`" class="commerce-seller-link">
                  {{ group.sellerNickname }}
                </router-link>
                <p v-else class="truncate text-[14px] font-semibold text-gray-800">{{ group.sellerNickname }}</p>
                <p class="mt-0.5 text-[12px] text-gray-500">{{ group.items.length }} 件待结算商品</p>
              </div>
              <span class="chip chip-success">可结算</span>
            </div>
            <ul class="divide-y divide-stone-100">
              <li v-for="item in group.items" :key="item.cartItemId ?? item.productId ?? item.title" class="relative">
              <div class="flex gap-3 p-4 sm:gap-4">
                <input
                  type="checkbox"
                  class="checkbox-standard mt-1 shrink-0"
                  :checked="item.cartItemId !== null && selectedIds.includes(item.cartItemId)"
                  :disabled="!item.available || interactionLocked"
                  :aria-label="`选择商品 ${item.title}`"
                  @change="toggleSelect(item.cartItemId, item.available)"
                />

                <div class="h-20 w-20 shrink-0 overflow-hidden rounded-md border border-gray-200 bg-gray-50 sm:h-24 sm:w-24">
                  <img v-if="item.coverUrl && !cartImageFailedIds.has(cartImageKey(item))" :src="item.coverUrl" :alt="item.title" class="h-full w-full object-cover" loading="lazy" @error="markCartImageFailed(item)" />
                  <div v-else class="flex h-full w-full items-center justify-center text-gray-300">
                    <ShoppingCart class="h-6 w-6" aria-hidden="true" />
                  </div>
                </div>

                <div class="min-w-0 flex-1">
                  <div class="flex flex-wrap items-start justify-between gap-2">
                    <div class="min-w-0">
                      <router-link
                        v-if="canLinkProduct(item)"
                        :to="`/market/${item.productId}`"
                        class="block truncate text-[15px] font-semibold text-gray-900 hover:text-blue-700"
                      >{{ item.title }}</router-link>
                      <p v-else class="truncate text-[15px] font-semibold text-gray-400">{{ item.title }}</p>
                      <p class="mt-0.5 text-[12px] text-gray-500">加入于 {{ item.createTime || '时间待确认' }}</p>
                    </div>
                    <p class="font-numeric shrink-0 text-[16px] font-bold text-gray-950">¥ {{ formatPrice(item.price) }}</p>
                  </div>

                  <div class="mt-2 flex flex-wrap items-center gap-2 text-[12px]">
                    <span class="chip" :class="item.available ? 'chip-success' : 'chip-warning'">
                      {{ item.available ? '可结算' : item.unavailableReason || '不可结算' }}
                    </span>
                    <span class="chip chip-muted">数量 1</span>
                    <router-link
                      v-if="canLinkProduct(item)"
                      :to="`/market/${item.productId}`"
                      class="text-blue-700 hover:underline"
                    >查看商品</router-link>
                    <span v-else class="text-gray-400">商品已删除</span>
                  </div>

                  <div class="mt-3 flex items-center gap-2">
                    <template v-if="confirmDeleteId === item.cartItemId">
                      <button
                        class="btn-danger"
                        type="button"
                        :disabled="singleDeleteSubmitting || interactionLocked"
                        @click="removeSingle(item.cartItemId)"
                      >确认删除</button>
                      <button class="btn-default" type="button" :disabled="singleDeleteSubmitting" @click="confirmDeleteId = null">取消</button>
                    </template>
                    <button
                      v-else
                      class="btn-default"
                      type="button"
                      :disabled="interactionLocked"
                      :aria-label="`删除商品 ${item.title}`"
                      @click="confirmDeleteId = item.cartItemId"
                    >
                      <Trash2 class="h-4 w-4" aria-hidden="true" />
                      删除
                    </button>
                  </div>
                </div>
              </div>

              </li>
            </ul>
          </div>

          <section v-if="unavailableItems.length > 0" class="section-panel overflow-hidden">
            <div class="commerce-seller-group-header bg-stone-50">
              <div><h2 class="text-[14px] font-semibold text-gray-800">失效商品</h2><p class="mt-0.5 text-[12px] text-gray-500">不可结算，但仍可删除</p></div>
              <span class="chip chip-warning">{{ unavailableItems.length }} 件</span>
            </div>
            <ul class="divide-y divide-stone-100">
              <li v-for="item in unavailableItems" :key="item.cartItemId ?? item.productId ?? item.title" class="relative opacity-80">
                <div class="flex gap-3 p-4 sm:gap-4">
                  <input type="checkbox" class="checkbox-standard mt-1 shrink-0" :disabled="true" :aria-label="`商品 ${item.title} 不可结算`" />
                  <div class="h-20 w-20 shrink-0 overflow-hidden rounded-md border border-gray-200 bg-gray-50 sm:h-24 sm:w-24">
                    <img v-if="item.coverUrl && !cartImageFailedIds.has(cartImageKey(item))" :src="item.coverUrl" :alt="item.title" class="h-full w-full object-cover" loading="lazy" @error="markCartImageFailed(item)" />
                    <div v-else class="flex h-full w-full items-center justify-center text-gray-300"><ShoppingCart class="h-6 w-6" aria-hidden="true" /></div>
                  </div>
                  <div class="min-w-0 flex-1">
                    <div class="flex flex-wrap items-start justify-between gap-2"><p class="break-words text-[15px] font-semibold text-gray-700">{{ item.title }}</p><p class="font-numeric shrink-0 text-[16px] font-bold text-gray-700">¥ {{ formatPrice(item.price) }}</p></div>
                    <div class="mt-2 flex flex-wrap items-center gap-2 text-[12px]"><span class="chip chip-warning">{{ item.unavailableReason || '不可结算' }}</span><span class="text-gray-400">加入于 {{ item.createTime || '时间待确认' }}</span></div>
                    <div class="mt-3 flex items-center gap-2"><template v-if="confirmDeleteId === item.cartItemId"><button class="btn-danger" type="button" :disabled="singleDeleteSubmitting || interactionLocked" @click="removeSingle(item.cartItemId)">确认删除</button><button class="btn-default" type="button" :disabled="singleDeleteSubmitting" @click="confirmDeleteId = null">取消</button></template><button v-else class="btn-default" type="button" :disabled="interactionLocked" :aria-label="`删除商品 ${item.title}`" @click="confirmDeleteId = item.cartItemId"><Trash2 class="h-4 w-4" aria-hidden="true" />删除</button></div>
                  </div>
                </div>
              </li>
            </ul>
          </section>
        </section>

        <!-- 右侧 / 底部：地址 + 结算摘要 -->
        <aside class="space-y-4 lg:sticky lg:top-24 lg:self-start">
          <!-- 收货地址 -->
          <section class="section-panel">
            <div class="section-header-plain section-body border-b border-gray-100">
              <h2 class="section-heading">收货地址</h2>
            </div>
            <div class="section-body">
              <p v-if="addressLoading" class="text-[13px] text-gray-500" role="status">正在加载地址...</p>
              <div v-else-if="addressError" role="alert">
                <p class="text-[13px] text-red-700">{{ addressError }}</p>
                <button class="btn-default mt-2" type="button" @click="loadAddresses">重新加载地址</button>
              </div>
              <div v-else-if="addresses.length === 0">
                <p class="text-[13px] text-gray-500">暂无收货地址</p>
                <router-link class="btn-default mt-2 inline-flex" to="/account/addresses/new?redirect=/cart">新增收货地址</router-link>
              </div>
              <fieldset v-else :disabled="interactionLocked">
                <legend class="sr-only">选择收货地址</legend>
                <div class="space-y-2">
                  <CommerceAddressOption
                    v-for="address in addresses"
                    :key="address.id ?? address.fullAddress"
                    :model-value="selectedAddressId"
                    name="cart-address"
                    :address="address"
                    :disabled="interactionLocked"
                    @update:model-value="selectedAddressId = $event"
                  />
                </div>
              </fieldset>
            </div>
          </section>

          <!-- 结算摘要 -->
          <section class="section-panel">
            <div class="section-body space-y-3">
              <h2 class="section-heading">结算摘要</h2>
              <p class="text-[14px] text-gray-700">已选择 <strong class="font-semibold">{{ selectedCount }}</strong> 件</p>
              <p class="text-[14px] text-gray-700">
                商品合计
                <span class="font-numeric ml-1 text-[20px] font-bold text-gray-950">¥ {{ formatPrice(selectedTotalAmount) }}</span>
              </p>
              <p class="text-[13px] text-gray-500">将分别生成 {{ selectedCount }} 个订单</p>
              <p class="rounded-md bg-gray-50 p-2 text-[12px] leading-5 text-gray-500">
                二手商品按件分别创建订单，不会合并为一个跨卖家订单。
              </p>

              <button
                class="btn-primary hidden w-full lg:inline-flex"
                type="button"
                :disabled="!canCheckout"
                @click="submitCheckout"
              >
                <Loader2 v-if="checkoutSubmitting" class="h-4 w-4 animate-spin" aria-hidden="true" />
                {{ checkoutSubmitting ? '正在创建订单...' : `提交结算（${selectedCount}）` }}
              </button>
              <p v-if="selectedCount > 0 && selectedAddress === null" class="text-[12px] text-amber-700">请先选择收货地址。</p>
            </div>
          </section>
        </aside>
      </div>

      <!-- 结算结果 -->
      <section class="space-y-4" aria-live="polite">
        <div
          v-if="checkoutReconcileRequired"
          ref="reconcileRegionRef"
          class="notice-banner notice-banner-warning"
          role="alert"
          aria-live="assertive"
          tabindex="-1"
        >
          <p class="font-semibold">结算结果尚未确认</p>
          <p class="mt-1">网络波动或响应异常导致结果未知，已暂时锁定结算。请不要重复提交，先核对订单并重新检查购物车状态。</p>
          <div class="mt-2 flex flex-wrap gap-2">
            <button class="btn-default" type="button" @click="goToOrders">去我的订单核对</button>
            <button
              class="btn-primary"
              type="button"
              :disabled="reconciling"
              :aria-busy="reconciling"
              @click="reconcile"
            >
              <Loader2 v-if="reconciling" class="h-4 w-4 animate-spin" aria-hidden="true" />
              {{ reconciling ? '正在重新检查...' : '重新检查' }}
            </button>
          </div>
        </div>

        <div
          v-else-if="checkoutError"
          ref="checkoutErrorRef"
          class="notice-banner notice-banner-danger"
          role="alert"
          aria-live="assertive"
          tabindex="-1"
        >{{ checkoutError }}</div>

        <div v-else-if="checkoutResult" class="section-panel section-body space-y-4">
          <p class="text-[16px] font-bold text-gray-950">
            {{ checkoutResult.successCount }} 件成功，{{ checkoutResult.failureCount }} 件失败
          </p>

          <div v-if="checkoutResult.orders.length > 0">
            <h3 class="section-heading">成功创建的订单</h3>
            <ul class="mt-2 space-y-2">
              <li v-for="order in checkoutResult.orders" :key="order.orderId ?? order.orderNo" class="flex flex-wrap items-center justify-between gap-2 rounded-md border border-gray-200 p-3">
                <div class="min-w-0">
                  <p class="text-[13px] font-semibold text-gray-900">订单号 {{ order.orderNo }}</p>
                  <p class="text-[12px] text-gray-500">
                    商品 {{ order.productId ?? '—' }} · 状态 {{ order.status || 'pending' }}
                  </p>
                </div>
                <div class="flex items-center gap-3">
                  <span class="font-numeric text-[15px] font-bold text-gray-950">¥ {{ formatPrice(order.totalAmount) }}</span>
                  <router-link v-if="order.orderId !== null" class="btn-default" :to="`/orders/buyer/${order.orderId}`">查看订单</router-link>
                  <router-link v-if="order.orderId !== null" class="btn-primary" :to="`/orders/buyer/${order.orderId}/pay`">去支付</router-link>
                </div>
              </li>
            </ul>
          </div>

          <div v-if="checkoutResult.failures.length > 0">
            <h3 class="section-heading">未能结算的商品</h3>
            <ul class="mt-2 space-y-2">
              <li v-for="failure in checkoutResult.failures" :key="`${failure.cartItemId}-${failure.productId}`" class="rounded-md border border-amber-200 bg-amber-50 p-3">
                <p class="text-[13px] font-semibold text-gray-900">商品 {{ failure.productId ?? '—' }}</p>
                <p class="mt-0.5 text-[12px] text-amber-800">{{ failure.reason }}</p>
              </li>
            </ul>
            <p class="mt-2 text-[12px] text-gray-500">失败商品仍保留在购物车中，可重新查询状态后再次结算。</p>
          </div>
        </div>
      </section>

      <div class="commerce-mobile-action-bar lg:hidden" aria-label="购物车结算操作">
        <div class="min-w-0"><p class="text-[12px] text-gray-500">已选 {{ selectedCount }} 件</p><p class="font-numeric truncate text-[18px] font-bold text-gray-950">¥ {{ formatPrice(selectedTotalAmount) }}</p></div>
        <button class="btn-primary shrink-0" type="button" :disabled="!canCheckout" @click="submitCheckout"><Loader2 v-if="checkoutSubmitting" class="h-4 w-4 animate-spin" aria-hidden="true" />{{ checkoutReconcileRequired ? '结果待核对' : checkoutSubmitting ? '正在提交' : '提交结算' }}</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.cart-page {
  padding-bottom: 6.75rem;
}

.cart-empty-image { max-width: min(30rem, 100%); margin-bottom: 1rem; border-radius: 0.5rem; }
.commerce-seller-group-header { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: 0.875rem 1rem; border-bottom: 1px solid #f3eee8; }
.commerce-seller-link { display: inline-block; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--commerce-brand-strong); font-size: 0.875rem; font-weight: 600; }
.commerce-mobile-action-bar { position: fixed; z-index: 30; right: 0.75rem; bottom: calc(4.75rem + env(safe-area-inset-bottom)); left: 0.75rem; display: flex; align-items: center; justify-content: space-between; gap: 0.75rem; border: 1px solid var(--commerce-border); border-radius: 0.5rem; background: rgba(255, 255, 255, 0.97); padding: 0.75rem 0.875rem; box-shadow: var(--commerce-shadow-overlay); }

@media (min-width: 1024px) { .cart-page { padding-bottom: 1rem; } }
</style>
