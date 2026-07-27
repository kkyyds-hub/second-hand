<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ImageOff } from 'lucide-vue-next'
import FavoriteToggleButton from '@/pages/market/components/FavoriteToggleButton.vue'

export type CommerceProductCardData = {
  id: number | null
  title: string
  coverUrl: string
  price: number
  categoryName: string
  shortDescription?: string
}

export type ProductCardVariant = 'standard' | 'compact' | 'sold'

const props = withDefaults(
  defineProps<{
    product: CommerceProductCardData
    favorited?: boolean
    favoriteLoading?: boolean
    showFavorite?: boolean
    variant?: ProductCardVariant
  }>(),
  {
    favorited: false,
    favoriteLoading: false,
    showFavorite: false,
    variant: 'standard',
  },
)

const emit = defineEmits<{
  'toggle-favorite': []
}>()

const imageFailed = ref(false)
const isNavigable = computed(() => typeof props.product.id === 'number' && props.product.id > 0 && props.variant !== 'sold')

watch(
  () => props.product.coverUrl,
  () => {
    imageFailed.value = false
  },
)
</script>

<template>
  <article
    class="product-card marketplace-product-card"
    :class="[`product-card-${variant}`, { 'product-card-interactive': isNavigable }]"
  >
    <div class="product-card-media">
      <router-link v-if="isNavigable" class="product-card-image-link" :to="`/market/${product.id}`" :aria-label="`查看 ${product.title}`">
        <img v-if="product.coverUrl && !imageFailed" :src="product.coverUrl" :alt="product.title" class="product-card-image" loading="lazy" @error="imageFailed = true" />
        <span v-else class="product-card-image-placeholder">
          <ImageOff class="h-7 w-7" aria-hidden="true" />
          <span>暂无商品图片</span>
        </span>
      </router-link>
      <div v-else>
        <img v-if="product.coverUrl && !imageFailed" :src="product.coverUrl" :alt="product.title" class="product-card-image" loading="lazy" @error="imageFailed = true" />
        <span v-else class="product-card-image-placeholder">
          <ImageOff class="h-7 w-7" aria-hidden="true" />
          <span>暂无商品图片</span>
        </span>
      </div>
      <span v-if="variant === 'sold'" class="product-card-status">已售出</span>
      <div v-if="showFavorite && variant !== 'sold'" class="product-card-favorite">
        <FavoriteToggleButton :active="favorited" :loading="favoriteLoading" :disabled="product.id === null" @toggle="emit('toggle-favorite')" />
      </div>
    </div>

    <div class="product-card-body">
      <router-link v-if="isNavigable" class="product-card-title-link" :to="`/market/${product.id}`">
        <h3 class="product-card-title" :title="product.title">{{ product.title }}</h3>
      </router-link>
      <h3 v-else class="product-card-title" :title="product.title">{{ product.title }}</h3>
      <p class="product-card-price font-numeric">¥ {{ product.price.toFixed(2) }}</p>
      <div v-if="product.categoryName" class="product-card-meta">
        <span>{{ product.categoryName }}</span>
      </div>
      <p v-if="variant === 'standard' && product.shortDescription" class="product-card-summary">{{ product.shortDescription }}</p>
      <router-link v-if="isNavigable && variant === 'standard'" class="product-card-detail-link" :to="`/market/${product.id}`">查看详情</router-link>
    </div>
  </article>
</template>

<style scoped>
.product-card-image-link {
  display: block;
  height: 100%;
}

.product-card-media > div {
  height: 100%;
}
</style>
