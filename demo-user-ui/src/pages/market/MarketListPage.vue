<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Box, ChevronLeft, ChevronRight, Heart, Loader2, Search, SlidersHorizontal, X } from 'lucide-vue-next'
import { useRoute, useRouter, type LocationQuery } from 'vue-router'
import { createEmptyProductPage, getMarketProductList, type MarketProductListQuery, type MarketProductSummary } from '@/api/market'
import { favoriteProduct, getFavoriteStatus, unfavoriteProduct } from '@/api/favorite'
import MarketplaceProductCard from '@/pages/market/components/MarketplaceProductCard.vue'
import marketEmptyImage from '@/assets/commerce/market-empty.webp'

const DEFAULT_PAGE = 1
const DEFAULT_PAGE_SIZE = 12
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const hasLoadedOnce = ref(false)
const errorMessage = ref('')
const actionSuccessMessage = ref('')
const actionErrorMessage = ref('')
const validationMessage = ref('')
const routeValidationMessage = ref('')
const submittingFavoriteId = ref<number | null>(null)
const pageData = ref(createEmptyProductPage())
const favoriteMap = ref<Record<number, boolean>>({})
const emptyImageFailed = ref(false)
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
}

interface ParsedMarketRoute {
  filters: AppliedMarketFilters
  validationMessage: string
}

const draft = reactive<MarketFilterDraft>({
  keyword: '',
  minPrice: '',
  maxPrice: '',
  pageSize: DEFAULT_PAGE_SIZE,
})
const appliedFilters = ref<AppliedMarketFilters>(parseRouteQuery(route.query).filters)

const list = computed<MarketProductSummary[]>(() => pageData.value.list)
const totalPages = computed(() => Math.max(1, Math.ceil(pageData.value.total / appliedFilters.value.pageSize)))
const hasEmptyState = computed(() => !loading.value && hasLoadedOnce.value && !errorMessage.value && list.value.length === 0)
const hasPrevPage = computed(() => appliedFilters.value.page > 1)
const hasNextPage = computed(() => appliedFilters.value.page < totalPages.value)

/** Applied filter tags derived from current route query */
const appliedFilterTags = computed(() => {
  const tags: { label: string; clearKey: string }[] = []
  const f = appliedFilters.value
  if (f.keyword) {
    tags.push({ label: `关键词：${f.keyword}`, clearKey: 'keyword' })
  }
  if (f.minPrice !== null && f.maxPrice !== null) {
    tags.push({ label: `价格：¥${f.minPrice}–¥${f.maxPrice}`, clearKey: 'price' })
  } else if (f.minPrice !== null) {
    tags.push({ label: `最低价：¥${f.minPrice}`, clearKey: 'minPrice' })
  } else if (f.maxPrice !== null) {
    tags.push({ label: `最高价：¥${f.maxPrice}`, clearKey: 'maxPrice' })
  }
  return tags
})

const hasActiveFilters = computed(() => appliedFilterTags.value.length > 0)

function readQueryText(value: LocationQuery[string] | undefined) {
  const rawValue = Array.isArray(value) ? value[0] : value
  return typeof rawValue === 'string' ? rawValue.trim() : ''
}

function readNonNegativePrice(value: string) {
  if (!value) {
    return null
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : null
}

function readRoutePrice(value: string, label: string, issues: string[]) {
  if (!value) {
    return null
  }

  const parsed = readNonNegativePrice(value)
  if (parsed === null) {
    issues.push(`${label}必须是不小于 0 的数字，已恢复为不限。`)
  }
  return parsed
}

function readRouteInteger(value: string, fallback: number, label: string, max: number | null, issues: string[]) {
  if (!value) {
    return fallback
  }

  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed < 1 || (max !== null && parsed > max)) {
    const range = max === null ? '正整数' : `1 到 ${max}`
    issues.push(`${label}必须为${range}，已恢复为 ${fallback}。`)
    return fallback
  }
  return parsed
}

function parseRouteQuery(query: LocationQuery): ParsedMarketRoute {
  const keyword = readQueryText(query.keyword)
  const issues: string[] = []
  let minPrice = readRoutePrice(readQueryText(query.minPrice), '最低价', issues)
  let maxPrice = readRoutePrice(readQueryText(query.maxPrice), '最高价', issues)

  if (minPrice !== null && maxPrice !== null && minPrice > maxPrice) {
    issues.push('最低价不能高于最高价，已清除价格区间。')
    minPrice = null
    maxPrice = null
  }

  return {
    filters: {
      keyword,
      minPrice,
      maxPrice,
      page: readRouteInteger(readQueryText(query.page), DEFAULT_PAGE, '页码', null, issues),
      pageSize: readRouteInteger(readQueryText(query.pageSize), DEFAULT_PAGE_SIZE, '每页数量', 100, issues),
    },
    validationMessage: issues.join(' '),
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

function buildRouteQuery(filters: AppliedMarketFilters) {
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
  try {
    loading.value = true
    errorMessage.value = ''
    const payload = await getMarketProductList(buildListQuery(filters))
    if (sequence !== requestSequence) {
      return
    }

    const lastPage = payload.total === 0 ? DEFAULT_PAGE : Math.max(DEFAULT_PAGE, Math.ceil(payload.total / filters.pageSize))
    const canonicalPage = Math.min(filters.page, lastPage)
    if (canonicalPage !== filters.page) {
      router.replace({
        path: '/market',
        query: buildRouteQuery({ ...filters, page: canonicalPage }),
      })
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

/** Clear a single filter tag via route update */
function clearFilterTag(clearKey: string) {
  const f = { ...appliedFilters.value }
  if (clearKey === 'keyword') {
    f.keyword = ''
  } else if (clearKey === 'price') {
    f.minPrice = null
    f.maxPrice = null
  } else if (clearKey === 'minPrice') {
    f.minPrice = null
  } else if (clearKey === 'maxPrice') {
    f.maxPrice = null
  }
  f.page = DEFAULT_PAGE
  router.push({ path: '/market', query: buildRouteQuery(f) })
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
    actionSuccessMessage.value = ''
    actionErrorMessage.value = ''
    if (currentState) {
      await unfavoriteProduct(productId)
      favoriteMap.value = { ...favoriteMap.value, [productId]: false }
      actionSuccessMessage.value = `已取消收藏：${item.title}`
    } else {
      await favoriteProduct(productId)
      favoriteMap.value = { ...favoriteMap.value, [productId]: true }
      actionSuccessMessage.value = `已收藏：${item.title}`
    }
  } catch (error: unknown) {
    actionErrorMessage.value = currentState ? '取消收藏失败，请稍后重试。' : '收藏失败，请稍后重试。'
  } finally {
    submittingFavoriteId.value = null
  }
}

watch(
  () => route.fullPath,
  () => {
    const parsedRoute = parseRouteQuery(route.query)
    actionSuccessMessage.value = ''
    actionErrorMessage.value = ''

    if (parsedRoute.validationMessage) {
      routeValidationMessage.value = parsedRoute.validationMessage
      router.replace({ path: '/market', query: buildRouteQuery(parsedRoute.filters) })
      return
    }

    appliedFilters.value = parsedRoute.filters
    syncDraft(parsedRoute.filters)
    validationMessage.value = routeValidationMessage.value
    routeValidationMessage.value = ''
    loadFromRoute(parsedRoute.filters)
  },
  { immediate: true },
)
</script>

<template>
  <div class="page-body market-page">
    <!-- Page header -->
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

    <!-- Filter panel -->
    <section class="market-filter-panel" aria-label="商品筛选">
      <form class="market-filter-form" @submit.prevent="submitFilters">
        <div class="market-keyword-field">
          <label class="form-label" for="market-keyword">搜索商品</label>
          <div class="relative">
            <Search class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" aria-hidden="true" />
            <input id="market-keyword" v-model="draft.keyword" class="input-standard !pl-10" type="search" maxlength="40" placeholder="商品名称或描述" @keydown.enter.prevent="submitFilters" />
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

      <!-- Applied filter tags -->
      <div v-if="hasActiveFilters" class="market-applied-tags" aria-label="已应用的筛选条件">
        <span class="market-applied-tags-label">当前条件</span>
        <button
          v-for="tag in appliedFilterTags"
          :key="tag.clearKey"
          class="market-filter-tag"
          type="button"
          :aria-label="`清除筛选：${tag.label}`"
          @click="clearFilterTag(tag.clearKey)"
        >
          <span>{{ tag.label }}</span>
          <X class="h-3 w-3" aria-hidden="true" />
        </button>
        <button class="market-filter-tag-clear-all" type="button" @click="resetFilters">清除全部</button>
      </div>
    </section>

    <!-- Error banner -->
    <section v-if="errorMessage" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <div class="flex-1">
        <p class="font-semibold">商品没有加载成功</p>
        <p class="mt-1 text-[12px] leading-5">{{ errorMessage }}</p>
        <button class="btn-default mt-3" type="button" :disabled="loading" @click="retryLoad">重新加载</button>
      </div>
    </section>
    <section v-if="actionSuccessMessage" class="notice-banner notice-banner-success" role="status">
      <span class="notice-dot bg-emerald-500"></span>
      <span>{{ actionSuccessMessage }}</span>
    </section>
    <section v-if="actionErrorMessage" class="notice-banner notice-banner-danger" role="alert">
      <span class="notice-dot bg-red-500"></span>
      <span>{{ actionErrorMessage }}</span>
    </section>

    <!-- Results section -->
    <section class="market-results" aria-labelledby="market-results-heading">
      <div class="market-results-header">
        <div>
          <h2 id="market-results-heading" class="section-heading">全部商品</h2>
          <p class="section-subtitle">
            共 {{ pageData.total }} 件商品
            <template v-if="hasLoadedOnce && !errorMessage">
              · 第 {{ appliedFilters.page }} / {{ totalPages }} 页 · 每页 {{ appliedFilters.pageSize }} 件
            </template>
          </p>
        </div>
        <span class="chip chip-muted font-numeric">第 {{ appliedFilters.page }} / {{ totalPages }} 页</span>
      </div>

      <!-- Loading skeleton -->
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

      <!-- Empty state with illustration -->
      <div v-else-if="hasEmptyState" class="market-empty-illustration">
        <div class="market-empty-image-wrap">
          <img
            v-if="!emptyImageFailed"
            :src="marketEmptyImage"
            alt="空收纳箱和展示架，表示当前条件下没有商品"
            class="market-empty-image"
            @error="emptyImageFailed = true"
          />
          <Box v-else class="market-empty-fallback-icon" aria-hidden="true" />
        </div>
        <p class="market-empty-title">当前条件下没有商品</p>
        <p class="market-empty-desc">可以放宽关键词或价格区间，再试一次。</p>
        <div class="market-empty-actions">
          <button class="btn-primary" type="button" @click="resetFilters">重置筛选</button>
          <router-link class="btn-default" to="/market">返回全部商品</router-link>
        </div>
      </div>

      <!-- Product grid -->
      <div v-else-if="!errorMessage" class="market-product-grid">
        <MarketplaceProductCard
          v-for="product in list"
          :key="product.id ?? product.title"
          :product="product"
          :favorited="product.id !== null ? Boolean(favoriteMap[product.id]) : false"
          :favorite-loading="product.id !== null && submittingFavoriteId === product.id"
          :show-favorite="true"
          variant="standard"
          @toggle-favorite="handleToggleFavorite(product)"
        />
      </div>

      <!-- Pagination -->
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
