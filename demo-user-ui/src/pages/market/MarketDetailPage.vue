<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight, Loader2, MessageSquareMore, ShieldAlert, User } from 'lucide-vue-next'
import {
  createEmptyReviewPage,
  getMarketProductDetail,
  getMarketProductReviews,
  reportMarketProduct,
  type MarketProductDetail,
} from '@/api/market'
import { createReview } from '@/api/review'
import { favoriteProduct, getFavoriteStatus, unfavoriteProduct } from '@/api/favorite'
import { addCartItem } from '@/api/cart'
import { useCartStore } from '@/stores/cart'
import FavoriteToggleButton from '@/pages/market/components/FavoriteToggleButton.vue'
import ProductImageGallery from '@/pages/market/components/ProductImageGallery.vue'
import ProductReviewList from '@/pages/market/components/ProductReviewList.vue'
import MarketplaceProductCard, { type CommerceProductCardData } from '@/pages/market/components/MarketplaceProductCard.vue'
import { useRoute, useRouter } from 'vue-router'
import { readCurrentUser } from '@/utils/request'
import {
  createEmptyShopProfile,
  createEmptyShopProductPage,
  getSellerShop,
  getSellerOtherProducts,
  type SellerShopProductPage,
  type SellerShopProfile,
} from '@/api/sellerShop'

const route = useRoute()
const router = useRouter()
const detailLoading = ref(false)
const reviewLoading = ref(false)
const favoriteLoading = ref(false)
const detailError = ref('')
const reviewError = ref('')
const favoriteSuccessMessage = ref('')
const favoriteErrorMessage = ref('')
const favoriteStatusSyncError = ref('')
const detail = ref<MarketProductDetail | null>(null)
const reviewPage = ref(createEmptyReviewPage())
const favoriteStatus = ref(false)
const reviewFormOpen = ref(false)
const reportFormOpen = ref(false)

const reviewPagination = reactive({ page: 1, pageSize: 10 })
const reviewSubmitting = ref(false)
const reviewSubmitError = ref('')
const reviewSubmitSuccessMessage = ref('')
const reviewCreateForm = reactive({ orderId: '', rating: 5, content: '', isAnonymous: false })

const reportSubmitting = ref(false)
const reportError = ref('')
const reportSuccessMessage = ref('')
const reportForm = reactive({ reportType: 'misleading_desc', description: '', evidenceUrlsText: '' })

const cartStore = useCartStore()
const cartAdding = ref(false)
const cartMessage = ref('')
const cartErrorMessage = ref('')
const cartErrorRef = ref<HTMLElement | null>(null)

// Seller shop state
const sellerProfile = ref<SellerShopProfile>(createEmptyShopProfile())
const sellerProfileLoading = ref(false)
const sellerProfileError = ref('')
const otherProductsPage = ref<SellerShopProductPage>(createEmptyShopProductPage())
const otherProductsLoading = ref(false)
const otherProductsError = ref('')

function toCommerceProduct(item: SellerShopProductPage['list'][number]): CommerceProductCardData {
  return {
    id: item.productId,
    title: item.title,
    coverUrl: item.coverUrl,
    price: item.price,
    categoryName: item.categoryName,
  }
}

/**
 * 加入购物车按钮可用性：
 * 已登录 + 商品已加载 + 非本人商品 + 当前无加入请求。
 * 商品详情接口只返回在售商品，因此无需再判断 on_sale；
 * 已售/下架/审核中/已删除的商品不会出现在该详情页。
 */
const canAddToCart = computed(() => {
  return Boolean(
    productId.value !== null &&
      detail.value &&
      !isOwnProduct.value &&
      !cartAdding.value,
  )
})

const reportTypeOptions = [
  { value: 'misleading_desc', label: '描述与实物不符' },
  { value: 'counterfeit', label: '疑似假冒伪劣' },
  { value: 'prohibited_item', label: '疑似违禁商品' },
  { value: 'other', label: '其他违规问题' },
]

let requestSequence = 0

function readProductId(value: unknown) {
  if (typeof value === 'number' && Number.isInteger(value) && value > 0) {
    return value
  }
  if (typeof value === 'string' && /^\d+$/.test(value.trim())) {
    const parsed = Number(value.trim())
    return parsed > 0 ? parsed : null
  }
  return null
}

function readPositiveInt(value: unknown) {
  if (typeof value === 'number' && Number.isInteger(value) && value > 0) {
    return value
  }
  if (typeof value === 'string' && /^\d+$/.test(value.trim())) {
    const parsed = Number(value.trim())
    return parsed > 0 ? parsed : null
  }
  return null
}

function readEvidenceUrls(value: string) {
  return value.split(/[\n,]/g).map((item) => item.trim()).filter(Boolean)
}

const productId = computed(() => readProductId(route.params.productId))
const invalidProductId = computed(() => productId.value === null)
const currentUser = computed(() => readCurrentUser())
const isOwnProduct = computed(() => {
  return detail.value?.ownerId !== null && currentUser.value?.id !== null && detail.value?.ownerId === currentUser.value?.id
})
const canSubmitReview = computed(() => {
  return Boolean(
    productId.value !== null &&
      !reviewSubmitting.value &&
      readPositiveInt(reviewCreateForm.orderId) !== null &&
      reviewCreateForm.rating >= 1 &&
      reviewCreateForm.rating <= 5 &&
      reviewCreateForm.content.trim().length >= 2,
  )
})
const canSubmitReport = computed(() => {
  return Boolean(
    productId.value !== null &&
      !reportSubmitting.value &&
      reportForm.reportType.trim() &&
      reportForm.description.trim().length >= 5,
  )
})

function resetRouteState() {
  detail.value = null
  detailError.value = ''
  detailLoading.value = false
  reviewError.value = ''
  reviewLoading.value = false
  reviewPage.value = createEmptyReviewPage()
  reviewPagination.page = 1
  favoriteStatus.value = false
  favoriteSuccessMessage.value = ''
  favoriteErrorMessage.value = ''
  favoriteStatusSyncError.value = ''
  favoriteLoading.value = false
  reviewFormOpen.value = false
  reportFormOpen.value = false
  reviewSubmitError.value = ''
  reviewSubmitSuccessMessage.value = ''
  reviewSubmitting.value = false
  reviewCreateForm.orderId = ''
  reviewCreateForm.rating = 5
  reviewCreateForm.content = ''
  reviewCreateForm.isAnonymous = false
  reportError.value = ''
  reportSuccessMessage.value = ''
  reportSubmitting.value = false
  reportForm.reportType = 'misleading_desc'
  reportForm.description = ''
  reportForm.evidenceUrlsText = ''
  cartAdding.value = false
  cartMessage.value = ''
  cartErrorMessage.value = ''
  sellerProfile.value = createEmptyShopProfile()
  sellerProfileLoading.value = false
  sellerProfileError.value = ''
  otherProductsPage.value = createEmptyShopProductPage()
  otherProductsLoading.value = false
  otherProductsError.value = ''
}

async function loadDetail(id: number, sequence: number) {
  detailLoading.value = true
  detailError.value = ''
  try {
    const result = await getMarketProductDetail(id)
    if (sequence !== requestSequence) return
    detail.value = result
  } catch {
    if (sequence !== requestSequence) return
    detailError.value = '商品详情暂时无法加载，请稍后重试。'
  } finally {
    if (sequence === requestSequence) detailLoading.value = false
  }
}

async function loadReviews(id: number, sequence: number) {
  reviewLoading.value = true
  reviewError.value = ''
  try {
    const result = await getMarketProductReviews(id, reviewPagination)
    if (sequence !== requestSequence) return
    reviewPage.value = result
  } catch {
    if (sequence !== requestSequence) return
    reviewError.value = '商品评价暂时无法加载，请稍后重试。'
  } finally {
    if (sequence === requestSequence) reviewLoading.value = false
  }
}

async function loadFavoriteStatus(id: number, sequence: number) {
  favoriteLoading.value = true
  favoriteStatusSyncError.value = ''
  try {
    const result = await getFavoriteStatus(id)
    if (sequence !== requestSequence) return
    favoriteStatus.value = result
  } catch {
    if (sequence !== requestSequence) return
    favoriteStatus.value = false
    favoriteStatusSyncError.value = '收藏状态暂时无法读取，稍后可重新尝试收藏操作。'
  } finally {
    if (sequence === requestSequence) favoriteLoading.value = false
  }
}

async function loadProduct() {
  const id = productId.value
  const sequence = ++requestSequence
  resetRouteState()
  if (id === null) return
  await Promise.all([loadDetail(id, sequence), loadFavoriteStatus(id, sequence)])
  if (sequence === requestSequence) await loadReviews(id, sequence)
  // Load seller info and other products after product detail
  if (sequence === requestSequence && detail.value?.ownerId != null) {
    await Promise.all([
      loadSellerProfile(detail.value.ownerId, sequence),
      loadOtherProducts(detail.value.ownerId, id, sequence),
    ])
  }
}

async function reloadDetail() {
  await loadProduct()
}

async function loadSellerProfile(ownerId: number, sequence: number) {
  sellerProfileLoading.value = true
  sellerProfileError.value = ''
  try {
    const result = await getSellerShop(ownerId)
    if (sequence !== requestSequence) return
    sellerProfile.value = result
  } catch {
    if (sequence !== requestSequence) return
    sellerProfileError.value = '卖家信息暂时无法加载'
  } finally {
    if (sequence === requestSequence) sellerProfileLoading.value = false
  }
}

async function loadOtherProducts(ownerId: number, excludeId: number, sequence: number) {
  otherProductsLoading.value = true
  otherProductsError.value = ''
  try {
    const result = await getSellerOtherProducts(ownerId, excludeId, 6)
    if (sequence !== requestSequence) return
    otherProductsPage.value = result
  } catch {
    if (sequence !== requestSequence) return
    otherProductsError.value = ''
  } finally {
    if (sequence === requestSequence) otherProductsLoading.value = false
  }
}

function startCheckout() {
  if (productId.value === null || !detail.value || isOwnProduct.value) {
    return
  }
  void router.push(`/checkout/${productId.value}`)
}

/**
 * 加入购物车（幂等）。
 *
 * 1) 请求期间按钮 disabled 并显示“加入中...”，防快速双击 / Enter 双触发；
 * 2) 成功后刷新角标并给出明确反馈，不跳离商品详情；
 * 3) 失败展示后端真实错误，按钮恢复，不自动重试，错误区域获得焦点。
 */
async function addToCart() {
  const id = productId.value
  if (id === null || !detail.value || isOwnProduct.value || cartAdding.value) {
    return
  }

  cartAdding.value = true
  cartMessage.value = ''
  cartErrorMessage.value = ''
  try {
    await addCartItem({ productId: id })
    cartMessage.value = '已加入购物车'
    cartStore.refreshCount(currentUser.value?.id ?? null).catch(() => {})
  } catch (error) {
    cartErrorMessage.value = error instanceof Error && error.message ? error.message : '加入购物车失败，请稍后重试'
    await nextTick()
    cartErrorRef.value?.focus()
  } finally {
    cartAdding.value = false
  }
}

async function toggleFavorite() {
  const id = productId.value
  if (id === null || favoriteLoading.value) return

  const sequence = requestSequence
  const currentState = favoriteStatus.value
  favoriteLoading.value = true
  favoriteSuccessMessage.value = ''
  favoriteErrorMessage.value = ''
  try {
    if (currentState) {
      await unfavoriteProduct(id)
      if (sequence !== requestSequence) return
      favoriteStatus.value = false
      favoriteStatusSyncError.value = ''
      favoriteSuccessMessage.value = '已取消收藏该商品。'
    } else {
      await favoriteProduct(id)
      if (sequence !== requestSequence) return
      favoriteStatus.value = true
      favoriteStatusSyncError.value = ''
      favoriteSuccessMessage.value = '已收藏该商品。'
    }
  } catch {
    if (sequence !== requestSequence) return
    favoriteErrorMessage.value = currentState ? '取消收藏失败，请稍后重试。' : '收藏失败，请稍后重试。'
  } finally {
    if (sequence === requestSequence) favoriteLoading.value = false
  }
}

async function changeReviewPage(nextPage: number) {
  if (productId.value === null || nextPage < 1 || nextPage === reviewPagination.page) return
  reviewPagination.page = nextPage
  await loadReviews(productId.value, requestSequence)
}

function reloadReviews() {
  if (productId.value !== null) {
    void loadReviews(productId.value, requestSequence)
  }
}

async function submitReview() {
  const id = productId.value
  const orderId = readPositiveInt(reviewCreateForm.orderId)
  if (id === null || reviewSubmitting.value) return
  if (orderId === null) {
    reviewSubmitError.value = '请填写有效的关联订单 ID。'
    return
  }
  if (reviewCreateForm.content.trim().length < 2) {
    reviewSubmitError.value = '评价内容至少需要 2 个字。'
    return
  }

  reviewSubmitting.value = true
  const sequence = requestSequence
  reviewSubmitError.value = ''
  reviewSubmitSuccessMessage.value = ''
  try {
    await createReview({
      orderId,
      rating: reviewCreateForm.rating,
      content: reviewCreateForm.content,
      isAnonymous: reviewCreateForm.isAnonymous,
    })
    if (sequence !== requestSequence) return
    reviewSubmitSuccessMessage.value = '评价已提交。'
    reviewCreateForm.orderId = ''
    reviewCreateForm.content = ''
    await loadReviews(id, sequence)
  } catch {
    if (sequence !== requestSequence) return
    reviewSubmitError.value = '评价提交失败，请确认订单已完成且属于当前账号。'
  } finally {
    if (sequence === requestSequence) reviewSubmitting.value = false
  }
}

async function submitReport() {
  const id = productId.value
  if (id === null || reportSubmitting.value) return
  if (reportForm.description.trim().length < 5) {
    reportError.value = '请至少填写 5 个字的举报说明。'
    return
  }

  reportSubmitting.value = true
  const sequence = requestSequence
  reportError.value = ''
  reportSuccessMessage.value = ''
  try {
    const result = await reportMarketProduct(id, {
      reportType: reportForm.reportType,
      description: reportForm.description,
      evidenceUrls: readEvidenceUrls(reportForm.evidenceUrlsText),
    })
    if (sequence !== requestSequence) return
    reportSuccessMessage.value = `举报已提交，工单号：${result.ticketNo}`
    reportForm.description = ''
    reportForm.evidenceUrlsText = ''
  } catch {
    if (sequence !== requestSequence) return
    reportError.value = '举报提交失败，请稍后重试。'
  } finally {
    if (sequence === requestSequence) reportSubmitting.value = false
  }
}

watch(productId, () => {
  void loadProduct()
}, { immediate: true })
</script>

<template>
  <main class="page-body market-detail-page">
    <nav class="flex items-center gap-2 text-[13px] text-gray-500" aria-label="面包屑">
      <router-link class="inline-flex items-center gap-1 font-medium text-gray-600 hover:text-blue-700" to="/market">
        <ChevronLeft class="h-4 w-4" />
        返回市场
      </router-link>
      <span aria-hidden="true">/</span>
      <span>商品详情</span>
    </nav>

    <section v-if="invalidProductId" class="section-panel">
      <div class="empty-state min-h-[340px]">
        <p class="empty-state-title">商品地址无效</p>
        <p class="empty-state-text">请从市场列表重新选择需要查看的商品。</p>
        <router-link class="btn-primary mt-5" to="/market">返回市场</router-link>
      </div>
    </section>

    <template v-else>
      <section v-if="detailError" class="section-panel">
        <div class="empty-state min-h-[340px]">
          <p class="empty-state-title">商品详情加载失败</p>
          <p class="empty-state-text">{{ detailError }}</p>
          <div class="mt-5 flex flex-wrap justify-center gap-3">
            <button class="btn-primary" type="button" :disabled="detailLoading" @click="reloadDetail">重新加载</button>
            <router-link class="btn-default" to="/market">返回市场</router-link>
          </div>
        </div>
      </section>

      <section v-else-if="detailLoading || !detail" class="market-detail-skeleton" aria-label="正在加载商品详情">
        <div class="animate-pulse rounded-xl bg-gray-200"></div>
        <div class="space-y-5 rounded-xl border border-gray-200 bg-white p-6">
          <div class="h-5 w-20 rounded bg-gray-200"></div>
          <div class="h-9 w-4/5 rounded bg-gray-200"></div>
          <div class="h-10 w-36 rounded bg-gray-200"></div>
          <div class="h-20 rounded bg-gray-100"></div>
        </div>
      </section>

      <template v-else>
        <section class="market-detail-hero">
          <ProductImageGallery :product-title="detail.title" :image-urls="detail.imageUrls" />
          <div class="min-w-0 py-1">
            <div class="flex flex-wrap items-start justify-between gap-4">
              <div class="min-w-0">
                <span class="chip chip-accent">{{ detail.categoryName || '未分类' }}</span>
                <h1 class="mt-4 break-words text-[28px] font-bold leading-tight text-gray-950 sm:text-[34px]">{{ detail.title }}</h1>
              </div>
              <FavoriteToggleButton :active="favoriteStatus" :loading="favoriteLoading" @toggle="toggleFavorite" />
            </div>
            <p class="mt-6 font-numeric text-[36px] font-bold leading-none text-gray-950 sm:text-[42px]">¥ {{ detail.price.toFixed(2) }}</p>
            <p v-if="detail.createTime" class="mt-3 text-[13px] text-gray-500">发布于 {{ detail.createTime }}</p>
            <div v-if="favoriteSuccessMessage" class="notice-banner notice-banner-success mt-5">{{ favoriteSuccessMessage }}</div>
            <div v-if="favoriteErrorMessage" class="notice-banner notice-banner-danger mt-5">{{ favoriteErrorMessage }}</div>
            <div v-if="favoriteStatusSyncError" class="notice-banner notice-banner-warning mt-5">{{ favoriteStatusSyncError }}</div>
            <div class="mt-6 flex flex-wrap items-center gap-3">
              <button class="btn-primary" type="button" :disabled="isOwnProduct" @click="startCheckout">立即购买</button>
              <button
                class="btn-default"
                type="button"
                :disabled="!canAddToCart"
                :aria-busy="cartAdding"
                @click="addToCart"
              >
                <Loader2 v-if="cartAdding" class="h-4 w-4 animate-spin" aria-hidden="true" />
                {{ cartAdding ? '加入中...' : '加入购物车' }}
              </button>
              <p v-if="isOwnProduct" class="text-[13px] text-amber-700">不能购买自己发布的商品</p>
            </div>
            <div
              v-if="cartMessage"
              class="notice-banner notice-banner-success mt-4"
              role="status"
              aria-live="polite"
            >{{ cartMessage }}</div>
            <div
              v-if="cartErrorMessage"
              ref="cartErrorRef"
              class="notice-banner notice-banner-danger mt-4"
              role="alert"
              aria-live="assertive"
              tabindex="-1"
            >{{ cartErrorMessage }}</div>
            <div class="mt-8 border-t border-gray-100 pt-6">
              <h2 class="text-[15px] font-semibold text-gray-900">商品描述</h2>
              <p class="mt-3 whitespace-pre-line break-words text-[14px] leading-7 text-gray-700">{{ detail.description || '该商品暂未提供详细描述。' }}</p>
            </div>

            <!-- Seller card -->
            <div class="mt-6 border-t border-gray-100 pt-6">
              <template v-if="sellerProfileLoading">
                <div class="flex items-center gap-3 animate-pulse">
                  <div class="h-10 w-10 rounded-full bg-gray-200"></div>
                  <div class="space-y-1.5">
                    <div class="h-4 w-24 rounded bg-gray-200"></div>
                    <div class="h-3 w-32 rounded bg-gray-100"></div>
                  </div>
                </div>
              </template>
              <template v-else-if="sellerProfileError">
                <p class="text-[13px] text-gray-500">卖家信息暂时无法加载</p>
              </template>
              <template v-else-if="sellerProfile.sellerId != null">
                <router-link
                  :to="`/shop/${sellerProfile.sellerId}`"
                  class="flex items-center gap-3 group rounded-lg p-2 -m-2 hover:bg-gray-50 transition-colors"
                >
                  <div v-if="sellerProfile.avatarUrl" class="h-10 w-10 rounded-full overflow-hidden bg-gray-50 border border-gray-100 shrink-0">
                    <img :src="sellerProfile.avatarUrl" alt="" class="h-full w-full object-cover" />
                  </div>
                  <div v-else class="h-10 w-10 rounded-full bg-blue-50 flex items-center justify-center shrink-0">
                    <User class="h-5 w-5 text-blue-500" aria-hidden="true" />
                  </div>
                  <div class="min-w-0 flex-1">
                    <p class="text-[14px] font-semibold text-gray-900 group-hover:text-blue-700 transition-colors">
                      {{ sellerProfile.shopName }}
                    </p>
                    <p class="text-[12px] text-gray-500">
                      {{ sellerProfile.nickname }}
                      <span v-if="sellerProfile.creditScore != null" class="ml-2">信用 {{ sellerProfile.creditScore }}</span>
                      <span class="ml-2">已成交 {{ sellerProfile.completedOrderCount }} 单</span>
                      <span class="ml-2">在售 {{ sellerProfile.onSaleCount }} 件</span>
                    </p>
                  </div>
                  <span class="text-[13px] font-medium text-blue-600 group-hover:text-blue-800 shrink-0 flex items-center gap-1">
                    查看小店
                    <ChevronRight class="h-3.5 w-3.5" aria-hidden="true" />
                  </span>
                </router-link>
                <p v-if="isOwnProduct && sellerProfile.isCurrentUser" class="mt-2 text-[12px] text-blue-700">这是你的商品</p>
              </template>
            </div>
          </div>
        </section>

        <!-- Other products from this seller -->
        <section v-if="otherProductsPage.list.length && !otherProductsLoading" class="section-panel mt-6">
          <div class="section-header">
            <div>
              <h2 class="section-heading">该卖家的其他商品</h2>
              <p class="section-subtitle">共 {{ otherProductsPage.total }} 件在售</p>
            </div>
          </div>
          <div class="section-body">
            <div class="market-product-grid market-product-grid-compact">
              <MarketplaceProductCard
                v-for="(item, idx) in otherProductsPage.list"
                :key="item.productId ?? `other-${idx}`"
                :product="toCommerceProduct(item)"
                variant="compact"
              />
            </div>
          </div>
        </section>

        <ProductReviewList
          :page="reviewPage"
          :loading="reviewLoading"
          :error-message="reviewError"
          @retry="reloadReviews"
          @change-page="changeReviewPage"
        />

        <section class="section-panel">
          <div class="section-header">
            <div>
              <h2 class="section-heading">写评价</h2>
              <p class="section-subtitle">仅已完成且属于当前账号的订单可以评价。</p>
            </div>
            <button class="btn-default" type="button" :aria-expanded="reviewFormOpen" @click="reviewFormOpen = !reviewFormOpen">
              <MessageSquareMore class="h-4 w-4" />
              {{ reviewFormOpen ? '收起表单' : '写评价' }}
            </button>
          </div>
          <form v-if="reviewFormOpen" class="section-body space-y-4" @submit.prevent="submitReview">
            <div v-if="reviewSubmitError" class="notice-banner notice-banner-danger">{{ reviewSubmitError }}</div>
            <div v-if="reviewSubmitSuccessMessage" class="notice-banner notice-banner-success">{{ reviewSubmitSuccessMessage }}</div>
            <div class="grid gap-4 sm:grid-cols-2">
              <div>
                <label class="form-label" for="review-order-id">关联订单 ID</label>
                <input id="review-order-id" v-model="reviewCreateForm.orderId" class="input-standard" type="text" inputmode="numeric" placeholder="请输入关联订单 ID" :disabled="reviewSubmitting" />
              </div>
              <div>
                <label class="form-label" for="review-rating">评分</label>
                <select id="review-rating" v-model.number="reviewCreateForm.rating" class="input-standard" :disabled="reviewSubmitting">
                  <option v-for="rating in [5, 4, 3, 2, 1]" :key="rating" :value="rating">{{ rating }} 星</option>
                </select>
              </div>
            </div>
            <div>
              <label class="form-label" for="review-content">评价内容</label>
              <textarea id="review-content" v-model="reviewCreateForm.content" class="input-standard min-h-[108px] resize-y" maxlength="500" placeholder="分享商品的实际体验" :disabled="reviewSubmitting" />
            </div>
            <div class="flex flex-wrap items-center justify-between gap-3">
              <label class="inline-flex items-center gap-2 text-[13px] text-gray-600">
                <input v-model="reviewCreateForm.isAnonymous" class="checkbox-standard" type="checkbox" :disabled="reviewSubmitting" />
                匿名评价
              </label>
              <button class="btn-primary" type="submit" :disabled="!canSubmitReview">
                <Loader2 v-if="reviewSubmitting" class="h-4 w-4 animate-spin" />
                提交评价
              </button>
            </div>
          </form>
        </section>

        <section class="section-panel">
          <div class="section-header">
            <div>
              <h2 class="section-heading">举报该商品</h2>
              <p class="section-subtitle">发现违规信息时，可提交说明供平台处理。</p>
            </div>
            <button class="btn-danger" type="button" :aria-expanded="reportFormOpen" @click="reportFormOpen = !reportFormOpen">
              <ShieldAlert class="h-4 w-4" />
              {{ reportFormOpen ? '收起举报' : '举报该商品' }}
            </button>
          </div>
          <form v-if="reportFormOpen" class="section-body space-y-4" @submit.prevent="submitReport">
            <div v-if="reportError" class="notice-banner notice-banner-danger">{{ reportError }}</div>
            <div v-if="reportSuccessMessage" class="notice-banner notice-banner-success">{{ reportSuccessMessage }}</div>
            <div>
              <label class="form-label" for="report-type">举报类型</label>
              <select id="report-type" v-model="reportForm.reportType" class="input-standard" :disabled="reportSubmitting">
                <option v-for="item in reportTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </div>
            <div>
              <label class="form-label" for="report-description">举报说明</label>
              <textarea id="report-description" v-model="reportForm.description" class="input-standard min-h-[108px] resize-y" maxlength="500" placeholder="请至少填写 5 个字的具体说明" :disabled="reportSubmitting" />
            </div>
            <div>
              <label class="form-label" for="report-evidence">证据链接</label>
              <input id="report-evidence" v-model="reportForm.evidenceUrlsText" class="input-standard" type="text" placeholder="多个链接请用逗号或换行分隔" :disabled="reportSubmitting" />
            </div>
            <div class="flex justify-end">
              <button class="btn-danger" type="submit" :disabled="!canSubmitReport">
                <Loader2 v-if="reportSubmitting" class="h-4 w-4 animate-spin" />
                提交举报
              </button>
            </div>
          </form>
        </section>
      </template>
    </template>
  </main>
</template>

<style scoped>
.market-detail-page {
  padding-top: 1.5rem;
  padding-inline: 1rem;
}

.market-detail-hero,
.market-detail-skeleton {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 2rem;
}

.market-detail-hero {
  padding: 1.25rem;
  border: 1px solid rgb(229 231 235);
  border-radius: 0.75rem;
  background: white;
}

.market-detail-skeleton > div:first-child {
  aspect-ratio: 1 / 1;
}

@media (min-width: 1024px) {
  .market-detail-hero,
  .market-detail-skeleton {
    grid-template-columns: minmax(0, 45fr) minmax(0, 55fr);
  }

  .market-detail-hero {
    padding: 2rem;
  }
}
</style>
