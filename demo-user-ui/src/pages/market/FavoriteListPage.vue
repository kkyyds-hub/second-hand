<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ChevronLeft, ChevronRight, Heart, HeartOff, Loader2, RefreshCw } from 'lucide-vue-next'
import { createEmptyFavoritePage, getFavoriteList, unfavoriteProduct } from '@/api/favorite'
import { type MarketProductSummary } from '@/api/market'
import favoritesEmptyImage from '@/assets/commerce/favorites-empty.webp'
import MarketplaceProductCard from '@/pages/market/components/MarketplaceProductCard.vue'

const loading = ref(false)
const hasLoadedOnce = ref(false)
const errorMessage = ref('')
const actionFeedback = ref<{ tone: 'success' | 'error'; message: string } | null>(null)
const togglingProductId = ref<number | null>(null)
const pageData = ref(createEmptyFavoritePage())

const pagination = reactive({
  page: 1,
  pageSize: 12,
})

const list = computed<MarketProductSummary[]>(() => pageData.value.list)
const totalPages = computed(() => Math.max(1, Math.ceil(pageData.value.total / pageData.value.pageSize)))
const hasEmptyState = computed(() => !loading.value && hasLoadedOnce.value && !errorMessage.value && list.value.length === 0)
const hasPrevPage = computed(() => pagination.page > 1)
const hasNextPage = computed(() => pagination.page < totalPages.value)

function readErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return '收藏列表加载失败，请稍后重试。'
}

async function loadFavoriteList() {
  if (loading.value) {
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''

    pageData.value = await getFavoriteList({
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
  } catch (error: unknown) {
    errorMessage.value = readErrorMessage(error)
  } finally {
    loading.value = false
    hasLoadedOnce.value = true
  }
}

async function toggleFavorite(item: MarketProductSummary) {
  if (item.id === null || togglingProductId.value !== null) {
    return
  }

  try {
    togglingProductId.value = item.id
    actionFeedback.value = null

    await unfavoriteProduct(item.id)
    actionFeedback.value = { tone: 'success', message: `已从收藏夹移除：${item.title}` }
    if (list.value.length === 1 && pagination.page > 1) {
      pagination.page -= 1
    }
    await loadFavoriteList()
  } catch (error: unknown) {
    actionFeedback.value = { tone: 'error', message: readErrorMessage(error) }
  } finally {
    togglingProductId.value = null
  }
}

function changePage(nextPage: number) {
  if (nextPage < 1 || nextPage > totalPages.value || nextPage === pagination.page) {
    return
  }
  pagination.page = nextPage
  loadFavoriteList()
}

onMounted(() => {
  loadFavoriteList()
})
</script>

<template>
  <div class="page-body">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">收藏</p>
          <h1 class="page-title">我的收藏</h1>
          <p class="page-desc">把喜欢的商品留在这里，方便随时回来查看。</p>
        </div>
        <div class="page-actions">
          <router-link class="btn-default" to="/market">
            <ChevronLeft class="h-4 w-4" />
            <span>返回市场</span>
          </router-link>
          <button class="btn-default" type="button" :disabled="loading" @click="loadFavoriteList">
            <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
            <RefreshCw v-else class="h-4 w-4" />
            <span>{{ loading ? '刷新中' : '刷新收藏' }}</span>
          </button>
        </div>
      </div>
    </section>

    <section v-if="errorMessage" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <div class="flex-1">
        <p class="font-semibold">加载失败</p>
        <p class="mt-1 text-[12px] leading-5">{{ errorMessage }}</p>
        <button class="btn-default mt-3" type="button" :disabled="loading" @click="loadFavoriteList">重试</button>
      </div>
    </section>
    <section v-if="actionFeedback" class="notice-banner" :class="actionFeedback.tone === 'success' ? 'notice-banner-success' : 'notice-banner-danger'">
      <span class="notice-dot" :class="actionFeedback.tone === 'success' ? 'bg-emerald-500' : 'bg-red-500'"></span>
      <span>{{ actionFeedback.message }}</span>
    </section>
    <section class="section-panel">
      <div class="section-header">
        <div>
          <h2 class="section-heading">收藏列表</h2>
          <p class="section-subtitle">共 {{ pageData.total }} 件收藏，当前第 {{ pageData.page }} / {{ totalPages }} 页</p>
        </div>
        <div class="section-actions">
          <span class="chip chip-muted font-numeric">共 {{ pageData.total }} 件</span>
        </div>
      </div>
      <div class="section-body">
        <div v-if="loading && !hasLoadedOnce" class="empty-state min-h-[360px]">
          <Loader2 class="empty-state-icon animate-spin text-blue-500" />
          <p class="empty-state-title">正在加载收藏内容</p>
        </div>
        <div v-else-if="hasEmptyState" class="empty-state min-h-[360px]">
          <img :src="favoritesEmptyImage" alt="空的收藏夹插画" class="favorites-empty-image" @error="($event.currentTarget as HTMLImageElement).style.display = 'none'" />
          <HeartOff class="empty-state-icon favorites-empty-fallback" />
          <p class="empty-state-title">收藏夹还是空的</p>
          <p class="empty-state-text">去市场页挑选感兴趣的商品，稍后会统一回流到这里。</p>
          <router-link class="btn-primary mt-5" to="/market">
            <Heart class="h-4 w-4" />
            <span>去市场看看</span>
          </router-link>
        </div>
        <div v-else class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          <MarketplaceProductCard
            v-for="item in list"
            :key="item.id ?? item.title"
            :product="item"
            variant="standard"
            show-favorite
            :favorited="true"
            :favorite-loading="item.id !== null && togglingProductId === item.id"
            @toggle-favorite="toggleFavorite(item)"
          />
        </div>
        <div class="pagination-bar">
          <div class="inline-meta">
            <span class="chip chip-neutral font-numeric">第 {{ pagination.page }} / {{ totalPages }} 页</span>
            <span>显示 {{ pageData.total === 0 ? 0 : (pagination.page - 1) * pagination.pageSize + 1 }} 到 {{ Math.min(pagination.page * pagination.pageSize, pageData.total) }} 条</span>
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
