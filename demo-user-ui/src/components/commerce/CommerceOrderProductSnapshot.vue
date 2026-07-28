<script setup lang="ts">
import { ref, watch } from 'vue'
import { PackageSearch } from 'lucide-vue-next'

const props = defineProps<{
  productId: number | null
  productTitle: string
  productThumbnail: string
  dealPrice: number
  quantity: number
  totalAmount: number
  counterpartLabel: string
  counterpartName: string
  createTime: string
}>()

const imageFailed = ref(false)

watch(() => props.productThumbnail, () => {
  imageFailed.value = false
})
</script>

<template>
  <article class="order-product-snapshot">
    <router-link v-if="productId !== null" class="order-product-image" :to="`/market/${productId}`" :aria-label="`查看商品 ${productTitle}`">
      <img v-if="productThumbnail && !imageFailed" :src="productThumbnail" :alt="productTitle || '商品图片'" @error="imageFailed = true" />
      <PackageSearch v-else class="h-8 w-8" aria-hidden="true" />
    </router-link>
    <div v-else class="order-product-image" aria-hidden="true">
      <img v-if="productThumbnail && !imageFailed" :src="productThumbnail" :alt="productTitle || '商品图片'" @error="imageFailed = true" />
      <PackageSearch v-else class="h-8 w-8" />
    </div>
    <div class="min-w-0 flex-1">
      <router-link v-if="productId !== null" :to="`/market/${productId}`" class="order-product-title">{{ productTitle || '商品信息待补充' }}</router-link>
      <p v-else class="order-product-title">{{ productTitle || '商品信息待补充' }}</p>
      <div class="order-product-meta">
        <span>{{ counterpartLabel }}：{{ counterpartName || '-' }}</span>
        <span>下单时间：{{ createTime || '-' }}</span>
      </div>
      <div class="order-product-amounts">
        <span>¥ {{ dealPrice.toFixed(2) }} × {{ quantity }}</span>
        <strong>合计 ¥ {{ totalAmount.toFixed(2) }}</strong>
      </div>
    </div>
  </article>
</template>

<style scoped>
.order-product-snapshot { display: flex; gap: 1rem; min-width: 0; }
.order-product-image { display: grid; width: 6.5rem; height: 6.5rem; flex: 0 0 auto; place-items: center; overflow: hidden; border: 1px solid #e7e5e4; border-radius: 0.5rem; color: #a8a29e; background: #fafaf9; }
.order-product-image img { width: 100%; height: 100%; object-fit: cover; }
.order-product-title { display: -webkit-box; overflow: hidden; color: #1c1917; font-size: 0.9375rem; font-weight: 600; line-height: 1.5rem; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.order-product-title:hover { color: #c2410c; }
.order-product-meta { display: flex; flex-wrap: wrap; gap: 0.25rem 1rem; margin-top: 0.5rem; color: #78716c; font-size: 0.75rem; line-height: 1.25rem; }
.order-product-amounts { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: 0.5rem 1rem; margin-top: 0.75rem; color: #78716c; font-size: 0.8125rem; }
.order-product-amounts strong { color: #1c1917; font-size: 1rem; }
@media (max-width: 390px) { .order-product-image { width: 5.25rem; height: 5.25rem; } }
</style>
