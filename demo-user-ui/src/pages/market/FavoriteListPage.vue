<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ChevronLeft, ChevronRight, Heart, HeartOff, Loader2, RefreshCw } from 'lucide-vue-next'
import { createEmptyFavoritePage, getFavoriteList, unfavoriteProduct } from '@/api/favorite'
import { type MarketProductSummary } from '@/api/market'
import FavoriteToggleButton from '@/pages/market/components/FavoriteToggleButton.vue'

const loading = ref(false)
const hasLoadedOnce = ref(false)
const errorMessage = ref('')
const actionMessage = ref('')
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
    actionMessage.value = ''

    await unfavoriteProduct(item.id)
    actionMessage.value = `已从收藏夹移除：${item.title}`
    await loadFavoriteList()
  } catch (error: unknown) {
    actionMessage.value = readErrorMessage(error)
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
          <p class="page-desc">收藏页延续市场域的统一卡片样式，弱化蓝色铺底，让视觉更克制、更精致。</p>
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
    <section v-if="actionMessage" class="notice-banner notice-banner-success">
      <span class="notice-dot bg-emerald-500"></span>
      <span>{{ actionMessage }}</span>
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
          <HeartOff class="empty-state-icon" />
          <p class="empty-state-title">收藏夹还是空的</p>
          <p class="empty-state-text">去市场页挑选感兴趣的商品，稍后会统一回流到这里。</p>
          <router-link class="btn-primary mt-5" to="/market">
            <Heart class="h-4 w-4" />
            <span>去市场看看</span>
          </router-link>
        </div>
        <div v-else class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          <article v-for="item in list" :key="item.id ?? item.title" class="group product-card">
            <div class="product-card-media">
              <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.title" class="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105" />
              <div v-else class="flex h-full items-center justify-center text-[12px] text-gray-400">暂无图片</div>
              <div class="absolute right-3 top-3">
                <FavoriteToggleButton
                  :active="true"
                  :loading="item.id !== null && togglingProductId === item.id"
                  :disabled="item.id === null"
                  @toggle="item.id !== null && toggleFavorite(item)"
                  class="!rounded-full !border-gray-200/80 !bg-white/95 shadow-sm shadow-gray-200/30"
                />
              </div>
            </div>
            <div class="product-card-body">
              <h3 class="product-card-title">{{ item.title }}</h3>
              <div class="mt-3 flex flex-wrap items-center gap-2">
                <span v-if="item.categoryName" class="chip chip-accent">{{ item.categoryName }}</span>
                <span v-if="item.sellerName" class="chip chip-neutral max-w-[120px] truncate">{{ item.sellerName }}</span>
              </div>
              <p class="product-card-summary">{{ item.shortDescription || '暂无商品描述' }}</p>
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
