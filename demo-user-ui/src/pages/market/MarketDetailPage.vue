<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronLeft, ChevronRight, Loader2, MessageSquare, MessageSquareMore, Package, ShieldAlert } from 'lucide-vue-next'
import {
  createEmptyReviewPage,
  getMarketProductDetail,
  getMarketProductReviews,
  reportMarketProduct,
  type MarketProductDetail,
} from '@/api/market'
import { createReview } from '@/api/review'
import { favoriteProduct, getFavoriteStatus, unfavoriteProduct } from '@/api/favorite'
import FavoriteToggleButton from '@/pages/market/components/FavoriteToggleButton.vue'

const route = useRoute()
const detailLoading = ref(false)
const reviewLoading = ref(false)
const favoriteLoading = ref(false)
const detailError = ref('')
const reviewError = ref('')
const actionMessage = ref('')

const detail = ref<MarketProductDetail | null>(null)
const reviewPage = ref(createEmptyReviewPage())
const reviewPagination = reactive({
  page: 1,
  pageSize: 10,
})
const favoriteStatus = ref(false)

const reportSubmitting = ref(false)
const reportError = ref('')
const reportSuccessMessage = ref('')
const reportForm = reactive({
  reportType: 'misleading_desc',
  description: '',
  evidenceUrlsText: '',
})

const reviewSubmitting = ref(false)
const reviewSubmitError = ref('')
const reviewSubmitSuccessMessage = ref('')
const reviewCreateForm = reactive({
  orderId: '',
  rating: 5,
  content: '',
  isAnonymous: false,
})

const reportTypeOptions = [
  { value: 'misleading_desc', label: '描述与实物不符' },
  { value: 'counterfeit', label: '疑似假冒伪劣' },
  { value: 'prohibited_item', label: '疑似违禁商品' },
  { value: 'other', label: '其他违规问题' },
]

const totalReviewPages = computed(() => Math.max(1, Math.ceil(reviewPage.value.total / reviewPage.value.pageSize)))
const hasPrevReviewPage = computed(() => reviewPagination.page > 1)
const hasNextReviewPage = computed(() => reviewPagination.page < totalReviewPages.value)
const canSubmitReport = computed(() => {
  return Boolean(
    productId.value !== null &&
      !reportSubmitting.value &&
      reportForm.reportType.trim() &&
      reportForm.description.trim().length >= 5,
  )
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

function readProductId(value: unknown) {
  if (typeof value === 'number' && Number.isInteger(value) && value > 0) {
    return value
  }

  if (typeof value === 'string') {
    const normalized = value.trim()
    if (/^\d+$/.test(normalized)) {
      return Number(normalized)
    }
  }

  return null
}

const productId = computed(() => readProductId(route.params.productId))

function readErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return fallback
}

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

function readEvidenceUrls(value: string) {
  return value
    .split(/[\n,]/g)
    .map((item) => item.trim())
    .filter(Boolean)
}

async function loadDetail() {
  if (detailLoading.value || productId.value === null) {
    return
  }

  try {
    detailLoading.value = true
    detailError.value = ''

    /**
     * 详情 DTO 的字段兼容和默认值处理全部留在 `src/api/market.ts`，
     * 页面仅消费稳定的展示模型，避免把 contract 兼容逻辑散落在模板中。
     */
    detail.value = await getMarketProductDetail(productId.value)
  } catch (error: unknown) {
    detailError.value = readErrorMessage(error, '商品详情加载失败，请稍后重试。')
  } finally {
    detailLoading.value = false
  }
}

async function loadReviews() {
  if (reviewLoading.value || productId.value === null) {
    return
  }

  try {
    reviewLoading.value = true
    reviewError.value = ''
    reviewPage.value = await getMarketProductReviews(productId.value, {
      page: reviewPagination.page,
      pageSize: reviewPagination.pageSize,
    })
  } catch (error: unknown) {
    reviewError.value = readErrorMessage(error, '评论列表加载失败，请稍后重试。')
  } finally {
    reviewLoading.value = false
  }
}

async function loadFavoriteStatus() {
  if (favoriteLoading.value || productId.value === null) {
    return
  }

  try {
    favoriteLoading.value = true
    favoriteStatus.value = await getFavoriteStatus(productId.value)
  } catch {
    favoriteStatus.value = false
  } finally {
    favoriteLoading.value = false
  }
}

async function toggleFavorite() {
  if (productId.value === null || favoriteLoading.value) {
    return
  }

  try {
    favoriteLoading.value = true
    actionMessage.value = ''

    if (favoriteStatus.value) {
      await unfavoriteProduct(productId.value)
      favoriteStatus.value = false
      actionMessage.value = '已取消收藏该商品。'
    } else {
      await favoriteProduct(productId.value)
      favoriteStatus.value = true
      actionMessage.value = '已收藏该商品。'
    }
  } catch (error: unknown) {
    actionMessage.value = readErrorMessage(error, '收藏状态更新失败，请稍后重试。')
  } finally {
    favoriteLoading.value = false
  }
}

async function submitReport() {
  if (productId.value === null || reportSubmitting.value) {
    return
  }

  if (!reportForm.description.trim() || reportForm.description.trim().length < 5) {
    reportError.value = '请至少填写 5 个字的举报说明。'
    return
  }

  try {
    reportSubmitting.value = true
    reportError.value = ''
    reportSuccessMessage.value = ''

    const result = await reportMarketProduct(productId.value, {
      reportType: reportForm.reportType,
      description: reportForm.description,
      evidenceUrls: readEvidenceUrls(reportForm.evidenceUrlsText),
    })

    reportSuccessMessage.value = `举报已提交，工单号：${result.ticketNo}`
    reportForm.description = ''
    reportForm.evidenceUrlsText = ''
  } catch (error: unknown) {
    reportError.value = readErrorMessage(error, '举报提交失败，请稍后重试。')
  } finally {
    reportSubmitting.value = false
  }
}

async function submitReview() {
  if (productId.value === null || reviewSubmitting.value) {
    return
  }

  const orderId = readPositiveInt(reviewCreateForm.orderId)
  if (orderId === null) {
    reviewSubmitError.value = '请填写有效的订单 ID。'
    return
  }

  if (!reviewCreateForm.content.trim() || reviewCreateForm.content.trim().length < 2) {
    reviewSubmitError.value = '评价内容至少需要 2 个字。'
    return
  }

  try {
    reviewSubmitting.value = true
    reviewSubmitError.value = ''
    reviewSubmitSuccessMessage.value = ''

    /**
     * 评论提交的订单完成前置由后端判定，前端只做最小格式校验：
     * 1) 提供 orderId/rating/content/isAnonymous 四个必填字段；
     * 2) 失败态明确展示给用户，避免把“入口可见”误当成“一定可提交”。
     */
    const reviewId = await createReview({
      orderId,
      rating: reviewCreateForm.rating,
      content: reviewCreateForm.content,
      isAnonymous: reviewCreateForm.isAnonymous,
    })

    reviewSubmitSuccessMessage.value = reviewId ? `评价已提交，编号：${reviewId}` : '评价已提交。'
    reviewCreateForm.orderId = ''
    reviewCreateForm.content = ''
    await loadReviews()
  } catch (error: unknown) {
    reviewSubmitError.value = readErrorMessage(error, '评价提交失败，请检查订单状态后重试。')
  } finally {
    reviewSubmitting.value = false
  }
}

function changeReviewPage(nextPage: number) {
  if (nextPage < 1 || nextPage > totalReviewPages.value || nextPage === reviewPagination.page) {
    return
  }

  reviewPagination.page = nextPage
  loadReviews()
}

async function bootstrap() {
  actionMessage.value = ''
  reportError.value = ''
  reportSuccessMessage.value = ''
  reviewSubmitError.value = ''
  reviewSubmitSuccessMessage.value = ''
  await Promise.all([loadDetail(), loadFavoriteStatus()])
  await loadReviews()
}

onMounted(() => {
  bootstrap()
})

watch(
  () => productId.value,
  () => {
    reviewPagination.page = 1
    detail.value = null
    reviewPage.value = createEmptyReviewPage()
    reportForm.description = ''
    reportForm.evidenceUrlsText = ''
    reviewCreateForm.orderId = ''
    reviewCreateForm.content = ''
    bootstrap()
  },
)
</script>

<template>
  <div class="page-body">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">??</p>
          <h1 class="page-title">????</h1>
          <p class="page-desc">???????????????????????????????</p>
        </div>
        <div class="page-actions">
          <router-link class="btn-default" to="/market">
            <ChevronLeft class="h-4 w-4" />
            <span>????</span>
          </router-link>
          <router-link class="btn-default" to="/reviews/mine">
            <MessageSquareMore class="h-4 w-4" />
            <span>????</span>
          </router-link>
        </div>
      </div>
    </section>

    <section v-if="actionMessage" class="notice-banner notice-banner-success">
      <span class="notice-dot bg-emerald-500"></span>
      <span>{{ actionMessage }}</span>
    </section>
    <section v-if="detailError" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <div class="flex-1">
        <p class="font-semibold">????????</p>
        <p class="mt-1 text-[12px] leading-5">{{ detailError }}</p>
        <button class="btn-default mt-3" type="button" :disabled="detailLoading" @click="loadDetail">????</button>
      </div>
    </section>
    <section v-else-if="detailLoading || !detail" class="section-panel">
      <div class="section-body">
        <div class="empty-state min-h-[380px]">
          <Loader2 class="empty-state-icon animate-spin text-blue-500" />
          <p class="empty-state-title">????????</p>
          <p class="empty-state-text">????????????????????</p>
        </div>
      </div>
    </section>
    <section v-else class="section-panel">
      <div class="section-body">
        <div class="grid gap-8 lg:grid-cols-[430px_minmax(0,1fr)]">
          <div class="soft-panel overflow-hidden">
            <img v-if="detail.coverUrl" :src="detail.coverUrl" :alt="detail.title" class="h-full max-h-[440px] w-full object-cover" />
            <div v-else class="flex h-[360px] items-center justify-center text-sm text-gray-400">??????</div>
          </div>
          <div class="flex flex-col gap-5">
            <div class="flex flex-col gap-4 border-b border-gray-100 pb-5 md:flex-row md:items-start md:justify-between">
              <div class="min-w-0">
                <h2 class="text-[28px] font-bold leading-tight text-gray-900">{{ detail.title }}</h2>
                <div class="mt-4 flex flex-wrap items-center gap-2">
                  <span class="chip chip-accent">{{ detail.categoryName || '???' }}</span>
                  <span class="chip chip-neutral">{{ detail.sellerName || '????' }}</span>
                </div>
              </div>
              <FavoriteToggleButton :active="favoriteStatus" :loading="favoriteLoading" @toggle="toggleFavorite" class="!rounded-2xl !px-3.5 !shadow-none" />
            </div>

            <div class="soft-panel p-5">
              <div class="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
                <div>
                  <p class="meta-label">????</p>
                  <p class="mt-2 font-numeric text-[32px] font-bold tracking-tight text-gray-900">? {{ detail.price.toFixed(2) }}</p>
                </div>
                <div class="detail-grid sm:grid-cols-2 lg:min-w-[320px]">
                  <div class="detail-row">
                    <span class="detail-label">??</span>
                    <span class="detail-value font-numeric">{{ detail.stock }}</span>
                  </div>
                  <div class="detail-row">
                    <span class="detail-label">??</span>
                    <span class="detail-value font-numeric">{{ detail.soldCount }}</span>
                  </div>
                  <div class="detail-row">
                    <span class="detail-label">????</span>
                    <span class="detail-value">{{ detail.categoryName || '???' }}</span>
                  </div>
                  <div class="detail-row">
                    <span class="detail-label">??</span>
                    <span class="detail-value">{{ detail.sellerName || '????' }}</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="soft-panel p-5">
              <div class="flex items-start gap-3">
                <div class="icon-badge shrink-0">
                  <Package class="h-4 w-4" />
                </div>
                <div class="min-w-0">
                  <p class="text-[13px] font-semibold text-gray-900">????</p>
                  <p class="mt-2 text-[14px] leading-7 text-gray-700">{{ detail.description || '?????????????' }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <div class="grid gap-6 lg:grid-cols-[1fr_380px]">
      <section class="section-panel">
        <div class="section-header">
          <div>
            <h2 class="section-heading">????</h2>
            <p class="section-subtitle">? {{ reviewPage.total }} ???</p>
          </div>
          <div class="section-actions">
            <span class="chip chip-muted font-numeric">? {{ reviewPage.total }} ?</span>
          </div>
        </div>
        <div class="section-body">
          <div v-if="reviewError" class="notice-banner notice-banner-danger">
            <span class="notice-dot bg-red-500"></span>
            <div class="flex-1">
              <p>{{ reviewError }}</p>
              <button class="btn-default mt-3" type="button" :disabled="reviewLoading" @click="loadReviews">????</button>
            </div>
          </div>
          <div v-else-if="reviewLoading && reviewPage.list.length === 0" class="empty-state min-h-[240px]">
            <Loader2 class="empty-state-icon animate-spin text-blue-500" />
            <p class="empty-state-title">??????</p>
          </div>
          <div v-else-if="reviewPage.list.length === 0" class="empty-state min-h-[240px]">
            <MessageSquare class="empty-state-icon" />
            <p class="empty-state-title">??????</p>
            <p class="empty-state-text">???????????????</p>
          </div>
          <div v-else class="space-y-4">
            <article v-for="item in reviewPage.list" :key="item.id ?? `${item.userName}-${item.createdAt}`" class="soft-panel p-4">
              <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                <div class="min-w-0 flex items-start gap-3">
                  <div class="icon-badge shrink-0 text-[12px] font-bold text-gray-700">
                    {{ item.userName ? item.userName.charAt(0).toUpperCase() : '?' }}
                  </div>
                  <div class="min-w-0">
                    <div class="flex flex-wrap items-center gap-2">
                      <p class="text-[13px] font-semibold text-gray-900">{{ item.userName }}</p>
                      <span class="chip chip-accent font-numeric">{{ item.score }} / 5 ?</span>
                    </div>
                    <p class="mt-1 text-[12px] leading-6 text-gray-700">{{ item.content || '?????????????' }}</p>
                  </div>
                </div>
                <p class="text-[12px] text-gray-400 font-numeric">{{ item.createdAt || '????' }}</p>
              </div>
            </article>
            <div class="pagination-bar">
              <div class="inline-meta">
                <span class="chip chip-neutral font-numeric">? {{ reviewPagination.page }} / {{ totalReviewPages }} ?</span>
              </div>
              <div class="flex gap-2">
                <button class="btn-default !h-9 px-3.5" type="button" :disabled="!hasPrevReviewPage || reviewLoading" @click="changeReviewPage(reviewPagination.page - 1)">
                  <ChevronLeft class="h-4 w-4" />
                  <span>???</span>
                </button>
                <button class="btn-default !h-9 px-3.5" type="button" :disabled="!hasNextReviewPage || reviewLoading" @click="changeReviewPage(reviewPagination.page + 1)">
                  <span>???</span>
                  <ChevronRight class="h-4 w-4" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div class="space-y-6">
        <section class="section-panel">
          <div class="section-header section-header-plain">
            <div class="flex items-start gap-3">
              <div class="icon-badge">
                <MessageSquareMore class="h-4 w-4" />
              </div>
              <div>
                <h2 class="section-heading">????</h2>
                <p class="section-subtitle">?????????????????????</p>
              </div>
            </div>
          </div>
          <form class="section-body space-y-4 pt-4" @submit.prevent="submitReview">
            <div v-if="reviewSubmitError" class="notice-banner notice-banner-danger">
              <span class="notice-dot bg-red-500"></span>
              <span>{{ reviewSubmitError }}</span>
            </div>
            <div v-if="reviewSubmitSuccessMessage" class="notice-banner notice-banner-success">
              <span class="notice-dot bg-emerald-500"></span>
              <span>{{ reviewSubmitSuccessMessage }}</span>
            </div>
            <div class="grid gap-4 sm:grid-cols-2">
              <div>
                <label class="form-label" for="review-order-id">????</label>
                <input id="review-order-id" v-model="reviewCreateForm.orderId" class="input-standard" type="text" placeholder="?? 10001" :disabled="reviewSubmitting" />
              </div>
              <div>
                <label class="form-label" for="review-rating">????</label>
                <select id="review-rating" v-model.number="reviewCreateForm.rating" class="input-standard" :disabled="reviewSubmitting">
                  <option :value="5">5 ? - ????</option>
                  <option :value="4">4 ? - ??</option>
                  <option :value="3">3 ? - ??</option>
                  <option :value="2">2 ? - ??</option>
                  <option :value="1">1 ? - ??</option>
                </select>
              </div>
            </div>
            <div>
              <label class="form-label" for="review-content">????</label>
              <textarea id="review-content" v-model="reviewCreateForm.content" class="input-standard min-h-[96px] resize-y" maxlength="500" placeholder="????????..." :disabled="reviewSubmitting" />
            </div>
            <div class="flex flex-wrap items-center justify-between gap-3">
              <label class="inline-flex items-center gap-2 text-[12px] text-gray-600">
                <input v-model="reviewCreateForm.isAnonymous" class="checkbox-standard" type="checkbox" :disabled="reviewSubmitting" />
                <span>????</span>
              </label>
              <button class="btn-primary" type="submit" :disabled="!canSubmitReview">
                <Loader2 v-if="reviewSubmitting" class="h-4 w-4 animate-spin" />
                <span>????</span>
              </button>
            </div>
          </form>
        </section>

        <section class="section-panel">
          <div class="section-header section-header-plain">
            <div class="flex items-start gap-3">
              <div class="icon-badge">
                <ShieldAlert class="h-4 w-4" />
              </div>
              <div>
                <h2 class="section-heading">????</h2>
                <p class="section-subtitle">????????????????????????</p>
              </div>
            </div>
          </div>
          <form class="section-body space-y-4 pt-4" @submit.prevent="submitReport">
            <div v-if="reportError" class="notice-banner notice-banner-danger">
              <span class="notice-dot bg-red-500"></span>
              <span>{{ reportError }}</span>
            </div>
            <div v-if="reportSuccessMessage" class="notice-banner notice-banner-success">
              <span class="notice-dot bg-emerald-500"></span>
              <span>{{ reportSuccessMessage }}</span>
            </div>
            <div>
              <label class="form-label" for="report-type">????</label>
              <select id="report-type" v-model="reportForm.reportType" class="input-standard" :disabled="reportSubmitting">
                <option v-for="item in reportTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </div>
            <div>
              <label class="form-label" for="report-description">????</label>
              <textarea id="report-description" v-model="reportForm.description" class="input-standard min-h-[96px] resize-y" maxlength="500" placeholder="???????????..." :disabled="reportSubmitting" />
            </div>
            <div>
              <label class="form-label" for="report-evidence">????????</label>
              <input id="report-evidence" v-model="reportForm.evidenceUrlsText" class="input-standard" type="text" placeholder="?????????" :disabled="reportSubmitting" />
            </div>
            <div class="flex justify-end">
              <button class="btn-danger" type="submit" :disabled="!canSubmitReport">
                <Loader2 v-if="reportSubmitting" class="h-4 w-4 animate-spin" />
                <span>????</span>
              </button>
            </div>
          </form>
        </section>
      </div>
    </div>
  </div>
</template>
