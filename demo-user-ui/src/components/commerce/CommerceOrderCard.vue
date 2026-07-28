<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { PackageSearch } from 'lucide-vue-next'

const props = defineProps<{
  orderId: number | null
  orderNo: string
  status: string
  statusLabel: string
  productId: number | null
  productTitle: string
  productThumbnail: string
  counterpartLabel: string
  counterpartName: string
  dealPrice: number
  quantity: number
  createTime: string
  shippingCompany: string
  trackingNo: string
  primaryLabel?: string
  primaryPath?: string | null
  secondaryLabel?: string
  secondaryPath?: string | null
  statusText?: string
}>()

const imageFailed = ref(false)
const totalAmount = computed(() => props.dealPrice * props.quantity)
const hasPrimaryAction = computed(() => Boolean(
  props.primaryLabel
    && props.primaryPath
    && props.primaryPath !== props.secondaryPath,
))
const hasSecondaryAction = computed(() => Boolean(props.secondaryLabel && props.secondaryPath))
const hasActions = computed(() => Boolean(props.statusText || hasPrimaryAction.value || hasSecondaryAction.value))

watch(() => props.productThumbnail, () => {
  imageFailed.value = false
})

function statusClass() {
  if (props.status === 'completed') return 'chip chip-success'
  if (props.status === 'pending' || props.status === 'cancelled') return 'chip chip-warning'
  if (props.status === 'paid' || props.status === 'shipped') return 'chip chip-accent'
  return 'chip chip-neutral'
}
</script>

<template>
  <article class="commerce-order-card">
    <header class="commerce-order-card-head">
      <div class="min-w-0">
        <span class="commerce-order-number">订单号：{{ orderNo || '-' }}</span>
        <span class="commerce-order-counterpart">{{ counterpartLabel }}：{{ counterpartName || '-' }}</span>
      </div>
      <span :class="statusClass()">{{ statusLabel || '状态待确认' }}</span>
    </header>
    <div class="commerce-order-card-body">
      <router-link v-if="productId !== null" class="commerce-order-card-image" :to="`/market/${productId}`" :aria-label="`查看商品 ${productTitle}`">
        <img v-if="productThumbnail && !imageFailed" :src="productThumbnail" :alt="productTitle || '商品图片'" @error="imageFailed = true" />
        <PackageSearch v-else class="h-7 w-7" aria-hidden="true" />
      </router-link>
      <div v-else class="commerce-order-card-image" aria-hidden="true">
        <img v-if="productThumbnail && !imageFailed" :src="productThumbnail" :alt="productTitle || '商品图片'" @error="imageFailed = true" />
        <PackageSearch v-else class="h-7 w-7" />
      </div>
      <div class="min-w-0 flex-1">
        <router-link v-if="productId !== null" :to="`/market/${productId}`" class="commerce-order-card-title">{{ productTitle || '商品信息待补充' }}</router-link>
        <p v-else class="commerce-order-card-title">{{ productTitle || '商品信息待补充' }}</p>
        <p class="commerce-order-card-meta">下单时间：{{ createTime || '-' }}</p>
        <p v-if="shippingCompany || trackingNo" class="commerce-order-card-meta break-all">物流：{{ shippingCompany || '-' }}<template v-if="trackingNo"> · {{ trackingNo }}</template></p>
      </div>
      <div class="commerce-order-card-amount">
        <span>¥ {{ dealPrice.toFixed(2) }} × {{ quantity }}</span>
        <strong>¥ {{ totalAmount.toFixed(2) }}</strong>
      </div>
    </div>
    <footer v-if="hasActions" class="commerce-order-card-actions">
      <span v-if="statusText" class="text-[13px] text-stone-500">{{ statusText }}</span>
      <router-link v-if="hasPrimaryAction" class="btn-primary !h-9 px-3" :to="primaryPath!">{{ primaryLabel }}</router-link>
      <router-link v-if="hasSecondaryAction" class="btn-default !h-9 px-3" :to="secondaryPath!">{{ secondaryLabel }}</router-link>
    </footer>
  </article>
</template>

<style scoped>
.commerce-order-card { overflow: hidden; border: 1px solid #e7e5e4; border-radius: 0.5rem; background: #fff; box-shadow: 0 1px 2px rgba(41, 37, 36, 0.04); }
.commerce-order-card-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 0.75rem; padding: 0.75rem 1rem; border-bottom: 1px solid #f1f0ef; background: #fffcf8; }
.commerce-order-number { display: inline-block; overflow-wrap: anywhere; color: #44403c; font-size: 0.8125rem; font-weight: 600; }
.commerce-order-counterpart { display: inline-block; margin-left: 0.75rem; color: #78716c; font-size: 0.75rem; }
.commerce-order-card-body { display: grid; grid-template-columns: 5.5rem minmax(0, 1fr) auto; gap: 0.875rem; align-items: start; padding: 1rem; }
.commerce-order-card-image { display: grid; width: 5.5rem; height: 5.5rem; place-items: center; overflow: hidden; border-radius: 0.375rem; color: #a8a29e; background: #f5f5f4; }
.commerce-order-card-image img { width: 100%; height: 100%; object-fit: cover; }
.commerce-order-card-title { display: -webkit-box; overflow: hidden; color: #1c1917; font-size: 0.9375rem; font-weight: 600; line-height: 1.45rem; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.commerce-order-card-title:hover { color: #c2410c; }
.commerce-order-card-meta { margin-top: 0.4rem; color: #78716c; font-size: 0.75rem; line-height: 1.25rem; }
.commerce-order-card-amount { display: grid; justify-items: end; gap: 0.4rem; color: #78716c; font-size: 0.75rem; white-space: nowrap; }
.commerce-order-card-amount strong { color: #1c1917; font-size: 1rem; }
.commerce-order-card-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 0.5rem; padding: 0.75rem 1rem; border-top: 1px solid #f1f0ef; }
@media (max-width: 560px) { .commerce-order-card-head { align-items: stretch; flex-direction: column; } .commerce-order-counterpart { display: block; margin: 0.25rem 0 0; } .commerce-order-card-body { grid-template-columns: 4.5rem minmax(0, 1fr); } .commerce-order-card-image { width: 4.5rem; height: 4.5rem; } .commerce-order-card-amount { grid-column: 2; justify-items: start; grid-template-columns: auto auto; align-items: baseline; } .commerce-order-card-actions { justify-content: flex-start; } }
</style>
