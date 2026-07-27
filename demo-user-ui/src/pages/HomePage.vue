<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowRight, Box, CirclePlus, RefreshCw, ShoppingBag } from 'lucide-vue-next'
import homeHeroImage from '@/assets/commerce/home-hero.webp'
import { createEmptyProductPage, getMarketProductList } from '@/api/market'
import MarketplaceProductCard from '@/pages/market/components/MarketplaceProductCard.vue'
import { isSellerUser, readCurrentUser } from '@/utils/request'

const currentUser = readCurrentUser()
const sellerEnabled = computed(() => isSellerUser(currentUser))
const loading = ref(false)
const hasLoadedOnce = ref(false)
const errorMessage = ref('')
const heroImageFailed = ref(false)
const pageData = ref(createEmptyProductPage())
let isActive = true

function readErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return '商品加载失败，请稍后重试。'
}

async function loadProducts() {
  if (loading.value) {
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''
    const payload = await getMarketProductList({ page: 1, pageSize: 9 })
    if (isActive) {
      pageData.value = payload
    }
  } catch (error: unknown) {
    if (isActive) {
      errorMessage.value = readErrorMessage(error)
    }
  } finally {
    if (isActive) {
      loading.value = false
      hasLoadedOnce.value = true
    }
  }
}

onMounted(loadProducts)

onBeforeUnmount(() => {
  isActive = false
})
</script>

<template>
  <div class="page-body home-page">
    <section class="home-hero">
      <img
        v-if="!heroImageFailed"
        :src="homeHeroImage"
        alt="相机、耳机和生活好物组成的闲置市集场景"
        class="home-hero-image"
        @error="heroImageFailed = true"
      />
      <div v-else class="home-hero-art" aria-hidden="true"></div>
      <div class="home-hero-content">
        <div class="home-hero-copy">
          <p class="page-kicker">二手市场</p>
          <h1 class="home-hero-title">让闲置，遇见真正需要它的人</h1>
          <p class="home-hero-desc">把仍有价值的好物，交给下一个懂得使用它的人。</p>
        </div>
        <div class="home-hero-actions">
          <router-link class="btn-default" to="/market">
            <ShoppingBag class="h-4 w-4" />
            <span>浏览市场</span>
          </router-link>
          <router-link v-if="sellerEnabled" class="btn-primary" to="/seller/products/new">
            <CirclePlus class="h-4 w-4" />
            <span>发布闲置</span>
          </router-link>
        </div>
      </div>
    </section>

    <section class="home-products-section" aria-labelledby="home-products-heading">
      <div class="home-section-heading">
        <div>
          <p class="page-kicker">最近上架</p>
          <h2 id="home-products-heading" class="section-heading">最新闲置</h2>
          <p class="section-subtitle">看看刚刚进入市场的实用好物。</p>
        </div>
        <router-link class="home-text-link" to="/market">
          <span>查看更多</span>
          <ArrowRight class="h-4 w-4" />
        </router-link>
      </div>

      <div v-if="loading && !hasLoadedOnce" class="home-product-grid" aria-label="商品加载中">
        <div v-for="item in 9" :key="item" class="product-card product-card-skeleton" aria-hidden="true">
          <div class="product-card-skeleton-media"></div>
          <div class="product-card-body gap-3">
            <div class="skeleton-line w-3/4"></div>
            <div class="skeleton-line w-1/2"></div>
            <div class="skeleton-line w-2/5"></div>
          </div>
        </div>
      </div>

      <section v-else-if="errorMessage" class="empty-state home-state">
        <Box class="empty-state-icon" />
        <p class="empty-state-title">商品暂时没有加载出来</p>
        <p class="empty-state-text">{{ errorMessage }}</p>
        <button class="btn-default mt-5" type="button" :disabled="loading" @click="loadProducts">
          <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" />
          <span>重新加载</span>
        </button>
      </section>

      <section v-else-if="hasLoadedOnce && pageData.list.length === 0" class="empty-state home-state">
        <Box class="empty-state-icon" />
        <p class="empty-state-title">暂时没有在售商品</p>
        <p class="empty-state-text">可以稍后刷新，或先浏览市场看看。</p>
        <div class="mt-5 flex flex-wrap justify-center gap-3">
          <button class="btn-default" type="button" @click="loadProducts">
            <RefreshCw class="h-4 w-4" />
            <span>刷新商品</span>
          </button>
          <router-link class="btn-primary" to="/market">浏览市场</router-link>
        </div>
      </section>

      <div v-else class="home-product-grid">
        <MarketplaceProductCard v-for="product in pageData.list" :key="product.id ?? product.title" :product="product" variant="standard" />
      </div>
    </section>

    <section v-if="sellerEnabled" class="home-seller-callout">
      <div>
        <p class="page-kicker">卖家服务</p>
        <h2 class="section-heading">让闲置继续被使用</h2>
        <p class="section-subtitle">卖家可在个人中心管理商品，并发布自己的闲置。</p>
      </div>
      <router-link class="btn-primary" to="/seller/products/new">
        <CirclePlus class="h-4 w-4" />
        <span>发布闲置</span>
      </router-link>
    </section>
  </div>
</template>
