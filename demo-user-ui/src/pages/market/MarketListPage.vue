<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Box, ChevronLeft, ChevronRight, Heart, Loader2, Search, SlidersHorizontal } from 'lucide-vue-next'
import { useRoute, useRouter, type LocationQuery } from 'vue-router'
import { createEmptyProductPage, getMarketProductList, type MarketProductListQuery, type MarketProductSummary } from '@/api/market'
import { favoriteProduct, getFavoriteStatus, unfavoriteProduct } from '@/api/favorite'
import MarketplaceProductCard from '@/pages/market/components/MarketplaceProductCard.vue'

const DEFAULT_PAGE = 1
const DEFAULT_PAGE_SIZE = 12
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const hasLoadedOnce = ref(false)
const errorMessage = ref('')
const actionMessage = ref('')
const validationMessage = ref('')
const submittingFavoriteId = ref<number | null>(null)
const pageData = ref(createEmptyProductPage())
const favoriteMap = ref<Record<number, boolean>>({})
let requestSequence = 0

interface MarketFilterDraft {
  keyword: string
  minPrice: string | number
  maxPrice: string | number
  pageSize: number
}

interface AppliedMarketFilters {
  keyword: string
  minPrice: number | null
  maxPrice: number | null
  page: number
  pageSize: number
  hasValidPriceRange: boolean
}

const draft = reactive<MarketFilterDraft>({
  keyword: '',
  minPrice: '',
  maxPrice: '',
  pageSize: DEFAULT_PAGE_SIZE,
})
const appliedFilters = ref<AppliedMarketFilters>(parseRouteQuery(route.query))

const list = computed<MarketProductSummary[]>(() => pageData.value.list)
const totalPages = computed(() => Math.max(1, Math.ceil(pageData.value.total / appliedFilters.value.pageSize)))
const hasEmptyState = computed(() => !loading.value && hasLoadedOnce.value && !errorMessage.value && list.value.length === 0)
const hasPrevPage = computed(() => appliedFilters.value.page > 1)
const hasNextPage = computed(() => appliedFilters.value.page < totalPages.value)

function readQueryText(value: LocationQuery[string] | undefined) {
  const rawValue = Array.isArray(value) ? value[0] : value
  return typeof rawValue === 'string' ? rawValue.trim() : ''
}

function readPositiveInteger(value: string, fallback: number) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback
}

function readNonNegativePrice(value: string) {
  if (!value) {
    return null
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : null
}

function parseRouteQuery(query: LocationQuery): AppliedMarketFilters {
  const keyword = readQueryText(query.keyword)
  const minPrice = readNonNegativePrice(readQueryText(query.minPrice))
  const maxPrice = readNonNegativePrice(readQueryText(query.maxPrice))

  return {
    keyword,
    minPrice,
    maxPrice,
    page: readPositiveInteger(readQueryText(query.page), DEFAULT_PAGE),
    pageSize: readPositiveInteger(readQueryText(query.pageSize), DEFAULT_PAGE_SIZE),
    hasValidPriceRange: minPrice === null || maxPrice === null || minPrice <= maxPrice,
  }
}

function syncDraft(filters: AppliedMarketFilters) {
  draft.keyword = filters.keyword
  draft.minPrice = filters.minPrice === null ? '' : String(filters.minPrice)
  draft.maxPrice = filters.maxPrice === null ? '' : String(filters.maxPrice)
  draft.pageSize = filters.pageSize
}

function buildListQuery(filters: AppliedMarketFilters): MarketProductListQuery {
  return {
    keyword: filters.keyword || undefined,
    minPrice: filters.minPrice,
    maxPrice: filters.maxPrice,
    page: filters.page,
    pageSize: filters.pageSize,
  }
}

function buildRouteQuery(filters: Omit<AppliedMarketFilters, 'hasValidPriceRange'>) {
  const query: Record<string, string> = {}
  if (filters.keyword) query.keyword = filters.keyword
  if (filters.minPrice !== null) query.minPrice = String(filters.minPrice)
  if (filters.maxPrice !== null) query.maxPrice = String(filters.maxPrice)
  if (filters.page > DEFAULT_PAGE) query.page = String(filters.page)
  if (filters.pageSize !== DEFAULT_PAGE_SIZE) query.pageSize = String(filters.pageSize)
  return query
}

function readErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return '市场列表加载失败，请稍后重试。'
}

async function syncFavoriteStatus(listData: MarketProductSummary[], sequence: number) {
  const entries = await Promise.all(
    listData
      .filter((item) => item.id !== null)
      .map(async (item) => {
        const id = Number(item.id)
        try {
          return [id, await getFavoriteStatus(id)] as const
        } catch {
          return [id, false] as const
        }
      }),
  )

  if (sequence !== requestSequence) {
    return
  }

  favoriteMap.value = Object.fromEntries(entries)
}

async function loadFromRoute(filters: AppliedMarketFilters) {
  const sequence = ++requestSequence
  if (!filters.hasValidPriceRange) {
    loading.value = false
    hasLoadedOnce.value = true
    errorMessage.value = '最低价不能高于最高价。请调整价格区间后重新筛选。'
    pageData.value = createEmptyProductPage()
    favoriteMap.value = {}
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''
    actionMessage.value = ''
    const payload = await getMarketProductList(buildListQuery(filters))
    if (sequence !== requestSequence) {
      return
    }
    pageData.value = payload
    await syncFavoriteStatus(payload.list, sequence)
  } catch (error: unknown) {
    if (sequence === requestSequence) {
      errorMessage.value = readErrorMessage(error)
    }
  } finally {
    if (sequence === requestSequence) {
      loading.value = false
      hasLoadedOnce.value = true
    }
  }
}

function validateDraft() {
  const minPriceInput = String(draft.minPrice).trim()
  const maxPriceInput = String(draft.maxPrice).trim()
  const minPrice = readNonNegativePrice(minPriceInput)
  const maxPrice = readNonNegativePrice(maxPriceInput)
  if (minPriceInput && minPrice === null) {
    validationMessage.value = '最低价请输入不小于 0 的数字。'
    return null
  }
  if (maxPriceInput && maxPrice === null) {
    validationMessage.value = '最高价请输入不小于 0 的数字。'
    return null
  }
  if (minPrice !== null && maxPrice !== null && minPrice > maxPrice) {
    validationMessage.value = '最低价不能高于最高价。'
    return null
  }

  validationMessage.value = ''
  return {
    keyword: draft.keyword.trim(),
    minPrice,
    maxPrice,
    pageSize: draft.pageSize,
  }
}

function submitFilters() {
  const next = validateDraft()
  if (!next) {
    return
  }
  router.push({ path: '/market', query: buildRouteQuery({ ...next, page: DEFAULT_PAGE }) })
}

function resetFilters() {
  validationMessage.value = ''
  router.push({ path: '/market' })
}

function changePage(nextPage: number) {
  if (nextPage < 1 || nextPage > totalPages.value || nextPage === appliedFilters.value.page) {
    return
  }
  const filters = appliedFilters.value
  router.push({
    path: '/market',
    query: buildRouteQuery({
      keyword: filters.keyword,
      minPrice: filters.minPrice,
      maxPrice: filters.maxPrice,
      page: nextPage,
      pageSize: filters.pageSize,
    }),
  })
}

function retryLoad() {
  loadFromRoute(appliedFilters.value)
}

async function handleToggleFavorite(item: MarketProductSummary) {
  if (item.id === null || submittingFavoriteId.value !== null) {
    return
  }

  const productId = item.id
  const currentState = Boolean(favoriteMap.value[productId])
  try {
    submittingFavoriteId.value = productId
    actionMessage.value = ''
    if (currentState) {
      await unfavoriteProduct(productId)
      favoriteMap.value = { ...favoriteMap.value, [productId]: false }
      actionMessage.value = `已取消收藏：${item.title}`
    } else {
      await favoriteProduct(productId)
      favoriteMap.value = { ...favoriteMap.value, [productId]: true }
      actionMessage.value = `已收藏：${item.title}`
    }
  } catch (error: unknown) {
    actionMessage.value = readErrorMessage(error)
  } finally {
    submittingFavoriteId.value = null
  }
}

watch(
  () => route.fullPath,
  () => {
    const filters = parseRouteQuery(route.query)
    appliedFilters.value = filters
    syncDraft(filters)
    validationMessage.value = ''
    loadFromRoute(filters)
  },
  { immediate: true },
)
</script>

<template>
  <div class="page-body market-page">
    <section class="market-page-header">
      <div>
        <p class="page-kicker">市场</p>
        <h1 class="page-title">发现闲置好物</h1>
        <p class="page-desc">按关键词和价格找到更合适的商品。</p>
      </div>
      <router-link class="btn-default" to="/favorites">
        <Heart class="h-4 w-4" />
        <span>我的收藏</span>
      </router-link>
    </section>

    <section class="market-filter-panel" aria-label="商品筛选">
      <form class="market-filter-form" @submit.prevent="submitFilters">
        <div class="market-keyword-field">
          <label class="form-label" for="market-keyword">搜索商品</label>
          <div class="relative">
            <Search class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" aria-hidden="true" />
            <input id="market-keyword" v-model="draft.keyword" class="input-standard !pl-10" type="search" maxlength="40" placeholder="商品名称、品牌或描述" @keydown.enter.prevent="submitFilters" />
          </div>
        </div>
        <div class="market-price-fields">
          <div>
            <label class="form-label" for="market-min-price">最低价</label>
            <input id="market-min-price" v-model="draft.minPrice" class="input-standard" type="number" min="0" step="0.01" inputmode="decimal" placeholder="不限" @keydown.enter.prevent="submitFilters" />
          </div>
          <div>
            <label class="form-label" for="market-max-price">最高价</label>
            <input id="market-max-price" v-model="draft.maxPrice" class="input-standard" type="number" min="0" step="0.01" inputmode="decimal" placeholder="不限" @keydown.enter.prevent="submitFilters" />
          </div>
        </div>
        <div class="market-filter-actions">
          <button class="btn-primary" type="submit" :disabled="loading">
            <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
            <SlidersHorizontal v-else class="h-4 w-4" />
            <span>筛选</span>
          </button>
          <button class="btn-default" type="button" :disabled="loading" @click="resetFilters">重置</button>
        </div>
      </form>
      <p v-if="validationMessage" class="market-validation-message" role="alert">{{ validationMessage }}</p>
    </section>

    <section v-if="errorMessage" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <div class="flex-1">
        <p class="font-semibold">商品没有加载成功</p>
        <p class="mt-1 text-[12px] leading-5">{{ errorMessage }}</p>
        <button class="btn-default mt-3" type="button" :disabled="loading" @click="retryLoad">重新加载</button>
      </div>
    </section>
    <section v-if="actionMessage" class="notice-banner notice-banner-success" role="status">
      <span class="notice-dot bg-emerald-500"></span>
      <span>{{ actionMessage }}</span>
    </section>

    <section class="market-results" aria-labelledby="market-results-heading">
      <div class="market-results-header">
        <div>
          <h2 id="market-results-heading" class="section-heading">全部商品</h2>
          <p class="section-subtitle">共 {{ pageData.total }} 件商品</p>
        </div>
        <span class="chip chip-muted font-numeric">第 {{ appliedFilters.page }} / {{ totalPages }} 页</span>
      </div>

      <div v-if="loading && !hasLoadedOnce" class="market-product-grid" aria-label="商品加载中">
        <div v-for="item in appliedFilters.pageSize" :key="item" class="product-card product-card-skeleton" aria-hidden="true">
          <div class="product-card-skeleton-media"></div>
          <div class="product-card-body gap-3">
            <div class="skeleton-line w-3/4"></div>
            <div class="skeleton-line w-2/5"></div>
            <div class="skeleton-line w-1/2"></div>
          </div>
        </div>
      </div>
      <div v-else-if="hasEmptyState" class="empty-state market-empty-state">
        <Box class="empty-state-icon" />
        <p class="empty-state-title">当前条件下没有商品</p>
        <p class="empty-state-text">可以放宽关键词或价格区间，再试一次。</p>
        <button class="btn-default mt-5" type="button" @click="resetFilters">重置筛选</button>
      </div>
      <div v-else-if="!errorMessage" class="market-product-grid">
        <MarketplaceProductCard
          v-for="product in list"
          :key="product.id ?? product.title"
          :product="product"
          :favorited="product.id !== null ? Boolean(favoriteMap[product.id]) : false"
          :favorite-loading="product.id !== null && submittingFavoriteId === product.id"
          :show-favorite="true"
          @toggle-favorite="handleToggleFavorite(product)"
        />
      </div>

      <div v-if="!errorMessage && !hasEmptyState && hasLoadedOnce" class="pagination-bar">
        <p class="inline-meta">显示 {{ pageData.total === 0 ? 0 : (appliedFilters.page - 1) * appliedFilters.pageSize + 1 }} 到 {{ Math.min(appliedFilters.page * appliedFilters.pageSize, pageData.total) }} 条</p>
        <div class="flex gap-2">
          <button class="btn-default !h-9 px-3.5" type="button" :disabled="!hasPrevPage || loading" aria-label="上一页" @click="changePage(appliedFilters.page - 1)">
            <ChevronLeft class="h-4 w-4" />
            <span>上一页</span>
          </button>
          <button class="btn-default !h-9 px-3.5" type="button" :disabled="!hasNextPage || loading" aria-label="下一页" @click="changePage(appliedFilters.page + 1)">
            <span>下一页</span>
            <ChevronRight class="h-4 w-4" />
          </button>
        </div>
      </div>
    </section>
  </div>
</template>
