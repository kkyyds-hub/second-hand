<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ChevronLeft, ChevronRight, Heart, Loader2, PackageOpen, Search, SlidersHorizontal } from 'lucide-vue-next'
import {
  createEmptyProductPage,
  getMarketProductList,
  type MarketProductListQuery,
  type MarketProductSummary,
} from '@/api/market'
import { favoriteProduct, getFavoriteStatus, unfavoriteProduct } from '@/api/favorite'
import FavoriteToggleButton from '@/pages/market/components/FavoriteToggleButton.vue'

const loading = ref(false)
const hasLoadedOnce = ref(false)
const errorMessage = ref('')
const actionMessage = ref('')
const submittingFavoriteId = ref<number | null>(null)
const pageData = ref(createEmptyProductPage())

interface MarketFilterForm {
  keyword: string
  minPrice: string
  maxPrice: string
  pageSize: number
}

const filters = reactive<MarketFilterForm>({
  keyword: '',
  minPrice: '',
  maxPrice: '',
  pageSize: 12,
})

const pagination = reactive({
  page: 1,
})

const favoriteMap = ref<Record<number, boolean>>({})

const list = computed<MarketProductSummary[]>(() => pageData.value.list)
const totalPages = computed(() => Math.max(1, Math.ceil(pageData.value.total / pageData.value.pageSize)))
const hasEmptyState = computed(() => !loading.value && hasLoadedOnce.value && !errorMessage.value && list.value.length === 0)
const hasPrevPage = computed(() => pagination.page > 1)
const hasNextPage = computed(() => pagination.page < totalPages.value)

function readErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return '市场列表加载失败，请稍后重试。'
}

function sanitizePriceInput(value: string) {
  const normalized = value.trim()
  if (!normalized) {
    return ''
  }
  const parsed = Number(normalized)
  if (!Number.isFinite(parsed) || parsed < 0) {
    return ''
  }
  return parsed
}

function buildListQuery(): MarketProductListQuery {
  return {
    keyword: filters.keyword,
    minPrice: sanitizePriceInput(filters.minPrice),
    maxPrice: sanitizePriceInput(filters.maxPrice),
    page: pagination.page,
    pageSize: filters.pageSize,
  }
}

async function syncFavoriteStatus(listData: MarketProductSummary[]) {
  const entries = await Promise.all(
    listData
      .filter((item) => item.id !== null)
      .map(async (item) => {
        const id = Number(item.id)
        try {
          const favorited = await getFavoriteStatus(id)
          return [id, favorited] as const
        } catch {
          return [id, false] as const
        }
      }),
  )

  const snapshot: Record<number, boolean> = {}
  for (const [id, favorited] of entries) {
    snapshot[id] = favorited
  }
  favoriteMap.value = snapshot
}

async function loadList() {
  if (loading.value) {
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''
    actionMessage.value = ''

    const payload = await getMarketProductList(buildListQuery())
    pageData.value = payload
    await syncFavoriteStatus(payload.list)
  } catch (error: unknown) {
    errorMessage.value = readErrorMessage(error)
  } finally {
    loading.value = false
    hasLoadedOnce.value = true
  }
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
      favoriteMap.value = {
        ...favoriteMap.value,
        [productId]: false,
      }
      actionMessage.value = `已取消收藏：${item.title}`
    } else {
      await favoriteProduct(productId)
      favoriteMap.value = {
        ...favoriteMap.value,
        [productId]: true,
      }
      actionMessage.value = `已收藏：${item.title}`
    }
  } catch (error: unknown) {
    actionMessage.value = readErrorMessage(error)
  } finally {
    submittingFavoriteId.value = null
  }
}

function submitFilters() {
  pagination.page = 1
  loadList()
}

function resetFilters() {
  filters.keyword = ''
  filters.minPrice = ''
  filters.maxPrice = ''
  pagination.page = 1
  loadList()
}

function changePage(nextPage: number) {
  if (nextPage < 1 || nextPage > totalPages.value || nextPage === pagination.page) {
    return
  }

  pagination.page = nextPage
  loadList()
}

onMounted(() => {
  loadList()
})
</script>

<template>
  <div class="page-body">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">市场</p>
          <h1 class="page-title">发现好物</h1>
          <p class="page-desc">让市场页回到与管理端同系列的中性卡片和节奏，只保留少量蓝色强调筛选与状态。</p>
        </div>
        <div class="page-actions">
          <router-link class="btn-default" to="/favorites">
            <Heart class="h-4 w-4" />
            <span>我的收藏</span>
          </router-link>
        </div>
      </div>
    </section>

    <section class="toolbar">
      <form class="w-full space-y-4 md:space-y-0" @submit.prevent="submitFilters">
        <div class="flex flex-col gap-4 md:flex-row md:items-end">
          <div class="toolbar-field">
            <label class="form-label" for="market-keyword">商品关键词</label>
            <div class="relative">
              <Search class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
              <input id="market-keyword" v-model="filters.keyword" class="input-standard !pl-10" type="text" maxlength="40" placeholder="搜索商品名称、品牌或描述..." :disabled="loading" />
            </div>
          </div>
          <div class="w-full md:w-40">
            <label class="form-label" for="market-min-price">最低价</label>
            <input id="market-min-price" v-model="filters.minPrice" class="input-standard" type="number" min="0" step="0.01" placeholder="0.00" :disabled="loading" />
          </div>
          <div class="w-full md:w-40">
            <label class="form-label" for="market-max-price">最高价</label>
            <input id="market-max-price" v-model="filters.maxPrice" class="input-standard" type="number" min="0" step="0.01" placeholder="不限" :disabled="loading" />
          </div>
          <div class="toolbar-group md:pl-2">
            <button class="btn-primary" type="submit" :disabled="loading">
              <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
              <SlidersHorizontal v-else class="h-4 w-4" />
              <span>筛选</span>
            </button>
            <button class="btn-default" type="button" :disabled="loading" @click="resetFilters">重置</button>
          </div>
        </div>
      </form>
    </section>

    <section v-if="errorMessage" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <div class="flex-1">
        <p class="font-semibold">列表刷新失败</p>
        <p class="mt-1 text-[12px] leading-5">{{ errorMessage }}</p>
        <button class="btn-default mt-3" type="button" :disabled="loading" @click="loadList">重新加载</button>
      </div>
    </section>
    <section v-if="actionMessage" class="notice-banner notice-banner-success">
      <span class="notice-dot bg-emerald-500"></span>
      <span>{{ actionMessage }}</span>
    </section>

    <section class="section-panel">
      <div class="section-header">
        <div>
          <h2 class="section-heading">全部商品</h2>
          <p class="section-subtitle">共 {{ pageData.total }} 件商品，当前第 {{ pageData.page }} / {{ totalPages }} 页</p>
        </div>
        <div class="section-actions">
          <span class="chip chip-muted font-numeric">共 {{ pageData.total }} 件</span>
        </div>
      </div>
      <div class="section-body">
        <div v-if="loading && !hasLoadedOnce" class="empty-state min-h-[360px]">
          <Loader2 class="empty-state-icon animate-spin text-blue-500" />
          <p class="empty-state-title">正在加载商品列表</p>
          <p class="empty-state-text">请稍候，系统正在同步最新市场数据。</p>
        </div>
        <div v-else-if="hasEmptyState" class="empty-state min-h-[360px]">
          <PackageOpen class="empty-state-icon" />
          <p class="empty-state-title">当前条件下暂无商品</p>
          <p class="empty-state-text">可以尝试放宽关键字或价格区间，查看更多市场结果。</p>
        </div>
        <div v-else class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          <article v-for="item in list" :key="item.id ?? item.title" class="group product-card">
            <div class="product-card-media">
              <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.title" class="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105" />
              <div v-else class="flex h-full items-center justify-center text-[12px] text-gray-400">暂无商品主图</div>
              <div class="absolute right-3 top-3">
                <FavoriteToggleButton
                  :active="item.id !== null ? Boolean(favoriteMap[item.id]) : false"
                  :loading="item.id !== null && submittingFavoriteId === item.id"
                  :disabled="item.id === null"
                  @toggle="item.id !== null && handleToggleFavorite(item)"
                  class="!rounded-full !border-gray-200/80 !bg-white/95 shadow-sm shadow-gray-200/30"
                />
              </div>
            </div>
            <div class="product-card-body">
              <h3 class="product-card-title" :title="item.title">{{ item.title }}</h3>
              <div class="mt-3 flex flex-wrap items-center gap-2">
                <span v-if="item.categoryName" class="chip chip-accent">{{ item.categoryName }}</span>
                <span v-if="item.sellerName" class="chip chip-neutral max-w-[120px] truncate">{{ item.sellerName }}</span>
              </div>
              <p class="product-card-summary">{{ item.shortDescription || '卖家暂未补充商品摘要。' }}</p>
              <div class="product-card-footer">
                <div class="space-y-1.5">
                  <p class="font-numeric text-[22px] font-bold tracking-tight text-gray-900">¥ {{ item.price.toFixed(2) }}</p>
                  <div class="inline-meta font-numeric">
                    <span>库存 {{ item.stock }}</span>
                    <span class="inline-meta-dot"></span>
                    <span>已售 {{ item.soldCount }}</span>
                  </div>
                </div>
                <router-link v-if="item.id !== null" class="btn-default !h-9 px-3" :to="`/market/${item.id}`">
                  <span>查看详情</span>
                  <ChevronRight class="h-4 w-4" />
                </router-link>
              </div>
            </div>
          </article>
        </div>
        <div class="pagination-bar">
          <div class="inline-meta">
            <span class="chip chip-neutral font-numeric">第 {{ pagination.page }} / {{ totalPages }} 页</span>
            <span>显示 {{ pageData.total === 0 ? 0 : (pagination.page - 1) * filters.pageSize + 1 }} 到 {{ Math.min(pagination.page * filters.pageSize, pageData.total) }} 条</span>
          </div>
          <div class="flex gap-2">
            <button class="btn-default !h-9 px-3.5" type="button" :disabled="!hasPrevPage || loading" @click="changePage(pagination.page - 1)">
              <ChevronLeft class="h-4 w-4" />
              <span>上一页</span>
            </button>
            <button class="btn-default !h-9 px-3.5" type="button" :disabled="!hasNextPage || loading" @click="changePage(pagination.page + 1)">
              <span>下一页</span>
              <ChevronRight class="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

