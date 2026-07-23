<script setup lang="ts">
import { ref, watch } from 'vue'
import { ImageOff } from 'lucide-vue-next'
import type { MarketProductSummary } from '@/api/market'
import FavoriteToggleButton from '@/pages/market/components/FavoriteToggleButton.vue'

const props = withDefaults(
  defineProps<{
    product: MarketProductSummary
    favorited?: boolean
    favoriteLoading?: boolean
    showFavorite?: boolean
  }>(),
  {
    favorited: false,
    favoriteLoading: false,
    showFavorite: false,
  },
)

const emit = defineEmits<{
  'toggle-favorite': []
}>()

const imageFailed = ref(false)

watch(
  () => props.product.coverUrl,
  () => {
    imageFailed.value = false
  },
)
</script>

<template>
  <article class="product-card marketplace-product-card">
    <div class="product-card-media">
      <router-link v-if="product.id !== null" class="product-card-image-link" :to="`/market/${product.id}`" :aria-label="`查看 ${product.title}`">
        <img v-if="product.coverUrl && !imageFailed" :src="product.coverUrl" :alt="product.title" class="product-card-image" @error="imageFailed = true" />
        <span v-else class="product-card-image-placeholder">
          <ImageOff class="h-7 w-7" aria-hidden="true" />
          <span>暂无商品图片</span>
        </span>
      </router-link>
      <div v-else class="product-card-image-placeholder">
        <ImageOff class="h-7 w-7" aria-hidden="true" />
        <span>暂无商品图片</span>
      </div>
      <div v-if="showFavorite" class="product-card-favorite">
        <FavoriteToggleButton :active="favorited" :loading="favoriteLoading" :disabled="product.id === null" @toggle="emit('toggle-favorite')" />
      </div>
    </div>

    <div class="product-card-body">
      <router-link v-if="product.id !== null" class="product-card-title-link" :to="`/market/${product.id}`">
        <h3 class="product-card-title" :title="product.title">{{ product.title }}</h3>
      </router-link>
      <h3 v-else class="product-card-title" :title="product.title">{{ product.title }}</h3>
      <p class="product-card-price font-numeric">¥ {{ product.price.toFixed(2) }}</p>
      <div v-if="product.categoryName" class="product-card-meta">
        <span>{{ product.categoryName }}</span>
      </div>
      <p v-if="product.shortDescription" class="product-card-summary">{{ product.shortDescription }}</p>
      <router-link v-if="product.id !== null" class="product-card-detail-link" :to="`/market/${product.id}`">查看详情</router-link>
    </div>
  </article>
</template>

<style scoped>
.product-card-image-link {
  display: block;
  height: 100%;
}
</style>
