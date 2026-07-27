<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  ChevronLeft,
  ChevronRight,
  Loader2,
  Package,
  PackagePlus,
  RefreshCw,
  ShieldAlert,
  ShoppingBag,
  Store,
  User,
} from 'lucide-vue-next'
import {
  createEmptyShopProfile,
  createEmptyShopProductPage,
  getSellerShop,
  getSellerShopProducts,
  type SellerShopProductPage,
  type SellerShopProfile,
} from '@/api/sellerShop'
import MarketplaceProductCard, { type CommerceProductCardData } from '@/pages/market/components/MarketplaceProductCard.vue'

const route = useRoute()

let shopRequestSequence = 0
let productRequestSequence = 0

const profileLoading = ref(false)
const productsLoading = ref(false)
const profileError = ref('')
const productsError = ref('')
const profile = ref<SellerShopProfile>(createEmptyShopProfile())
const productsPage = ref<SellerShopProductPage>(createEmptyShopProductPage())
const activeTab = ref<'on_sale' | 'sold'>('on_sale')
const currentPage = ref(1)
const pageSize = 12

function readSellerId(value: unknown) {
  if (typeof value === 'number' && Number.isInteger(value) && value > 0) return value
  if (typeof value === 'string' && /^\d+$/.test(value.trim())) {
    const parsed = Number(value.trim())
    return parsed > 0 ? parsed : null
  }
  return null
}

const sellerId = computed(() => readSellerId(route.params.sellerId))
const invalidSellerId = computed(() => sellerId.value === null)
const totalPages = computed(() => Math.max(1, Math.ceil(productsPage.value.total / productsPage.value.pageSize)))
const hasPrevPage = computed(() => currentPage.value > 1)
const hasNextPage = computed(() => currentPage.value < totalPages.value)
const tabOnSaleLabel = computed(() => `正在出售 ${profile.value.onSaleCount}`)
const tabSoldLabel = computed(() => `已经售出 ${profile.value.soldCount}`)
const isRetrying = ref(false)

function toCommerceProduct(item: SellerShopProductPage['list'][number]): CommerceProductCardData {
  return {
    id: item.productId,
    title: item.title,
    coverUrl: item.coverUrl,
    price: item.price,
    categoryName: item.categoryName,
  }
}

const displayStats = computed(() => {
  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return '—'
    const m = /^\d{4}-\d{2}-\d{2}/.exec(dateStr)
    return m ? m[0] : '—'
  }

  return [
    { label: '信用分', value: profile.value.creditScore != null ? String(profile.value.creditScore) : '—' },
    { label: '加入平台', value: formatDate(profile.value.registeredAt) },
    { label: '已完成交易', value: String(profile.value.completedOrderCount) },
    { label: '正在出售', value: String(profile.value.onSaleCount) },
    { label: '已经售出', value: String(profile.value.soldCount) },
  ]
})

async function loadProfile(sequence: number) {
  const id = sellerId.value
  if (id === null) return
  profileLoading.value = true
  profileError.value = ''
  try {
    const result = await getSellerShop(id)
    if (sequence !== shopRequestSequence) return
    profile.value = result
    document.title = `${result.shopName}`
  } catch (error) {
    if (sequence !== shopRequestSequence) return
    profileError.value = error instanceof Error && error.message ? error.message : '小店资料暂时无法加载，请稍后重试。'
  } finally {
    if (sequence === shopRequestSequence) profileLoading.value = false
  }
}

async function loadProducts(sequence: number) {
  const id = sellerId.value
  if (id === null) return
  productsLoading.value = true
  productsError.value = ''
  try {
    const result = await getSellerShopProducts(id, {
      status: activeTab.value,
      page: currentPage.value,
      pageSize,
    })
    if (sequence !== productRequestSequence) return
    productsPage.value = result
  } catch {
    if (sequence !== productRequestSequence) return
    productsError.value = '商品列表暂时无法加载，请稍后重试。'
  } finally {
    if (sequence === productRequestSequence) productsLoading.value = false
  }
}

function onSellerIdChange() {
  // Increment BOTH sequences so old shop AND product responses are fully discarded
  const shopSeq = ++shopRequestSequence
  ++productRequestSequence

  // Reset tab, page, and clear all old data
  activeTab.value = 'on_sale'
  currentPage.value = 1
  profile.value = createEmptyShopProfile()
  productsPage.value = createEmptyShopProductPage()
  profileError.value = ''
  productsError.value = ''
  profileLoading.value = false
  productsLoading.value = false

  if (sellerId.value === null) return

  // Load profile first, then default products
  loadProfile(shopSeq).then(() => {
    if (shopSeq === shopRequestSequence) {
      void loadProducts(++productRequestSequence)
    }
  })
}

function switchTab(tab: 'on_sale' | 'sold') {
  if (activeTab.value === tab) return
  activeTab.value = tab
  currentPage.value = 1
  void loadProducts(++productRequestSequence)
}

function changePage(page: number) {
  if (page < 1 || page > totalPages.value || page === currentPage.value) return
  currentPage.value = page
  void loadProducts(++productRequestSequence)
}

function reloadProducts() {
  void loadProducts(++productRequestSequence)
}

function retryLoadAll() {
  isRetrying.value = true
  const shopSeq = ++shopRequestSequence
  ++productRequestSequence

  activeTab.value = 'on_sale'
  currentPage.value = 1
  profile.value = createEmptyShopProfile()
  productsPage.value = createEmptyShopProductPage()
  profileError.value = ''
  productsError.value = ''

  if (sellerId.value === null) {
    isRetrying.value = false
    return
  }

  loadProfile(shopSeq).then(() => {
    isRetrying.value = false
    if (shopSeq === shopRequestSequence) {
      void loadProducts(++productRequestSequence)
    }
  })
}

watch(sellerId, () => { onSellerIdChange() }, { immediate: true })

onBeforeUnmount(() => { ++shopRequestSequence; ++productRequestSequence })
</script>

<template>
  <main class="page-body seller-shop-page">
    <!-- Breadcrumb -->
    <nav class="flex items-center gap-2 text-[13px] text-gray-500" aria-label="面包屑">
      <router-link class="inline-flex items-center gap-1 font-medium text-gray-600 hover:text-blue-700" to="/market">
        <ChevronLeft class="h-4 w-4" />
        返回市场
      </router-link>
      <span aria-hidden="true">/</span>
      <span>卖家小店</span>
    </nav>

    <!-- Invalid sellerId -->
    <section v-if="invalidSellerId" class="empty-state min-h-[340px]">
      <Store class="empty-state-icon" aria-hidden="true" />
      <p class="empty-state-title">小店地址无效</p>
      <p class="empty-state-text">请从市场或商品详情重新进入小店。</p>
      <router-link class="btn-primary mt-5" to="/market">返回市场</router-link>
    </section>

    <!-- Error state with retry -->
    <section v-else-if="profileError" class="empty-state min-h-[340px]">
      <ShieldAlert class="empty-state-icon text-red-400" aria-hidden="true" />
      <p class="empty-state-title">{{ profileError }}</p>
      <p class="empty-state-text">请检查网络后重试，或先浏览其他内容。</p>
      <div class="mt-5 flex flex-wrap justify-center gap-3">
        <button class="btn-primary" type="button" :disabled="isRetrying" @click="retryLoadAll">
          <Loader2 v-if="isRetrying" class="h-4 w-4 animate-spin" aria-hidden="true" />
          {{ isRetrying ? '加载中...' : '重新加载' }}
        </button>
        <router-link class="btn-default" to="/market">返回市场</router-link>
      </div>
    </section>

    <!-- Loading skeleton -->
    <template v-else-if="profileLoading || !profile.sellerId">
      <section aria-label="正在加载小店信息">
        <div class="rounded-xl border border-gray-200 bg-white p-6 animate-pulse">
          <div class="flex flex-col items-center gap-4 sm:flex-row sm:items-start">
            <div class="h-20 w-20 rounded-full bg-gray-200 shrink-0"></div>
            <div class="flex-1 space-y-3 text-center sm:text-left">
              <div class="h-6 w-48 rounded bg-gray-200 mx-auto sm:mx-0"></div>
              <div class="h-4 w-32 rounded bg-gray-200 mx-auto sm:mx-0"></div>
              <div class="flex flex-wrap justify-center gap-3 sm:justify-start">
                <div class="h-4 w-20 rounded bg-gray-100"></div>
                <div class="h-4 w-24 rounded bg-gray-100"></div>
                <div class="h-4 w-28 rounded bg-gray-100"></div>
              </div>
            </div>
          </div>
        </div>
      </section>
      <section aria-label="正在加载商品列表" class="mt-6">
        <div class="h-10 w-64 rounded bg-gray-100 mb-4"></div>
        <div class="grid gap-4 grid-cols-2 md:grid-cols-3 xl:grid-cols-4">
          <div v-for="i in 4" :key="i" class="h-64 rounded-lg bg-gray-100 animate-pulse"></div>
        </div>
      </section>
    </template>

    <!-- Shop content -->
    <template v-else>
      <!-- Shop header -->
      <section class="rounded-xl border border-gray-200 bg-white p-5 sm:p-6">
        <div class="flex flex-col items-center gap-5 sm:flex-row sm:items-start">
          <!-- Avatar -->
          <div class="shrink-0">
            <div
              v-if="profile.avatarUrl"
              class="h-20 w-20 rounded-full border-2 border-gray-100 overflow-hidden bg-gray-50"
            >
              <img :src="profile.avatarUrl" alt="" class="h-full w-full object-cover" />
            </div>
            <div
              v-else
              class="h-20 w-20 rounded-full border-2 border-gray-100 flex items-center justify-center bg-blue-50"
            >
              <User class="h-9 w-9 text-blue-500" aria-hidden="true" />
            </div>
          </div>

          <!-- Info -->
          <div class="flex-1 min-w-0 text-center sm:text-left">
            <h1 class="text-[22px] font-bold text-gray-950 break-words">{{ profile.shopName }}</h1>
            <p class="mt-1 text-[14px] text-gray-500">卖家昵称：{{ profile.nickname }}</p>
            <p v-if="profile.bio" class="mt-2 text-[13px] text-gray-600 break-words max-w-lg">{{ profile.bio }}</p>
            <p v-else class="mt-2 text-[13px] text-gray-400">卖家暂未填写介绍</p>

            <!-- Stats -->
            <div class="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-[13px] text-gray-600">
              <span v-for="stat in displayStats" :key="stat.label" class="inline-flex items-center gap-1">
                <span class="font-medium text-gray-500">{{ stat.label }}</span>
                <span class="font-semibold text-gray-950">{{ stat.value }}</span>
              </span>
            </div>
          </div>
        </div>
      </section>

      <!-- Self-view banner -->
      <section v-if="profile.isCurrentUser" class="mt-4 rounded-lg border border-blue-200 bg-blue-50 p-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <p class="text-[14px] font-medium text-blue-800">
            <Store class="inline h-4 w-4 mr-1" aria-hidden="true" />
            这是你的小店
          </p>
          <div class="flex flex-wrap gap-2">
            <router-link class="btn-default !h-9 px-3 text-[13px]" to="/seller/products">管理商品</router-link>
            <router-link class="btn-primary !h-9 px-3 text-[13px]" to="/seller/products/new">
              <PackagePlus class="h-4 w-4" aria-hidden="true" />
              发布商品
            </router-link>
          </div>
        </div>
      </section>

      <!-- Product tabs -->
      <section class="mt-6">
        <div class="flex items-center justify-between flex-wrap gap-3">
          <div class="flex gap-1 rounded-lg bg-gray-100 p-1" role="tablist">
            <button
              class="shop-tab"
              :class="{ 'shop-tab-active': activeTab === 'on_sale' }"
              role="tab"
              :aria-selected="activeTab === 'on_sale'"
              @click="switchTab('on_sale')"
            >
              <ShoppingBag class="h-4 w-4" aria-hidden="true" />
              {{ tabOnSaleLabel }}
            </button>
            <button
              class="shop-tab"
              :class="{ 'shop-tab-active': activeTab === 'sold' }"
              role="tab"
              :aria-selected="activeTab === 'sold'"
              @click="switchTab('sold')"
            >
              <Package class="h-4 w-4" aria-hidden="true" />
              {{ tabSoldLabel }}
            </button>
          </div>
        </div>
      </section>

      <!-- Products error -->
      <section v-if="productsError" class="mt-4 notice-banner notice-banner-danger">
        <span class="notice-dot bg-red-500"></span>
        <div class="flex-1">
          <p class="font-semibold">{{ productsError }}</p>
          <button class="btn-default mt-3" type="button" :disabled="productsLoading" @click="reloadProducts">
            <RefreshCw class="h-4 w-4" aria-hidden="true" />
            重新加载
          </button>
        </div>
      </section>

      <!-- Products loading -->
      <section v-else-if="productsLoading" class="mt-4" aria-label="正在加载商品列表">
        <div class="grid gap-4 grid-cols-2 md:grid-cols-3 xl:grid-cols-4">
          <div v-for="i in 4" :key="i" class="h-64 rounded-lg bg-gray-100 animate-pulse"></div>
        </div>
      </section>

      <!-- Empty state -->
      <section v-else-if="productsPage.list.length === 0" class="mt-4 empty-state min-h-[260px]">
        <Package class="empty-state-icon" aria-hidden="true" />
        <p class="empty-state-title">
          {{ activeTab === 'on_sale' ? '这家小店暂时没有正在出售的商品' : '这家小店暂时没有已售出的商品' }}
        </p>
        <p class="empty-state-text">可以稍后再来看看，或前往市场浏览更多商品。</p>
        <router-link class="btn-primary mt-5" to="/market">前往市场</router-link>
      </section>

      <!-- Product grid -->
      <section v-else class="mt-4">
        <div class="market-product-grid">
          <MarketplaceProductCard
            v-for="(item, idx) in productsPage.list"
            :key="item.productId ?? `prod-${idx}`"
            :product="toCommerceProduct(item)"
            :variant="activeTab === 'sold' ? 'sold' : 'standard'"
          />
        </div>

        <!-- Pagination -->
        <div v-if="totalPages > 1" class="pagination-bar mt-6">
          <span class="chip chip-neutral font-numeric">第 {{ currentPage }} / {{ totalPages }} 页</span>
          <div class="flex flex-wrap gap-2">
            <button
              class="btn-default !h-9 px-3.5"
              type="button"
              :disabled="!hasPrevPage"
              @click="changePage(currentPage - 1)"
            >
              <ChevronLeft class="h-4 w-4" aria-hidden="true" />
              <span>上一页</span>
            </button>
            <button
              class="btn-default !h-9 px-3.5"
              type="button"
              :disabled="!hasNextPage"
              @click="changePage(currentPage + 1)"
            >
              <span>下一页</span>
              <ChevronRight class="h-4 w-4" aria-hidden="true" />
            </button>
          </div>
        </div>
      </section>
    </template>
  </main>
</template>

<style scoped>
.seller-shop-page {
  padding-top: 1.5rem;
  padding-inline: 1rem;
}

/* Tab buttons */
.shop-tab {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.5rem 1rem;
  border-radius: 0.5rem;
  font-size: 13px;
  font-weight: 500;
  color: rgb(107 114 128);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: all 0.15s;
}
.shop-tab:hover {
  color: rgb(55 65 81);
}
.shop-tab-active {
  background: white;
  color: rgb(17 24 39);
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

</style>
