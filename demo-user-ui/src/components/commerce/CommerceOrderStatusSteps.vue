<script setup lang="ts">
import { computed } from 'vue'
import { CircleAlert, CircleCheck, Clock3 } from 'lucide-vue-next'

const props = defineProps<{
  status: string
  statusLabel?: string
}>()

const steps = [
  { key: 'pending', label: '待支付' },
  { key: 'paid', label: '待发货' },
  { key: 'shipped', label: '已发货' },
  { key: 'completed', label: '已完成' },
]

const activeIndex = computed(() => {
  const index = steps.findIndex((step) => step.key === props.status)
  return index
})

const isCancelled = computed(() => props.status === 'cancelled')
const isUnknown = computed(() => activeIndex.value === -1 && !isCancelled.value)
</script>

<template>
  <div class="order-status-steps" :class="{ 'order-status-steps-special': isCancelled || isUnknown }">
    <template v-if="isCancelled || isUnknown">
      <CircleAlert class="h-5 w-5 shrink-0" aria-hidden="true" />
      <span>{{ isCancelled ? '订单已取消' : (statusLabel || '当前状态暂无法识别') }}</span>
    </template>
    <template v-else>
      <div v-for="(step, index) in steps" :key="step.key" class="order-status-step">
        <span class="order-status-dot" :class="{ 'order-status-dot-active': index <= activeIndex }">
          <CircleCheck v-if="index < activeIndex" class="h-4 w-4" aria-hidden="true" />
          <Clock3 v-else class="h-4 w-4" aria-hidden="true" />
        </span>
        <span class="order-status-label" :class="{ 'order-status-label-active': index <= activeIndex }">{{ step.label }}</span>
        <span v-if="index < steps.length - 1" class="order-status-line" :class="{ 'order-status-line-active': index < activeIndex }"></span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.order-status-steps { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 0; align-items: start; width: 100%; }
.order-status-step { position: relative; display: grid; justify-items: center; gap: 0.45rem; min-width: 0; color: #a8a29e; font-size: 0.75rem; text-align: center; }
.order-status-dot { position: relative; z-index: 1; display: grid; width: 1.5rem; height: 1.5rem; place-items: center; border: 1px solid #d6d3d1; border-radius: 999px; background: #fff; }
.order-status-dot-active { border-color: #ea580c; color: #ea580c; background: #fff7ed; }
.order-status-label { overflow: hidden; max-width: 100%; text-overflow: ellipsis; white-space: nowrap; }
.order-status-label-active { color: #44403c; font-weight: 600; }
.order-status-line { position: absolute; z-index: 0; top: 0.75rem; right: calc(50% + 0.75rem); left: calc(-50% + 0.75rem); height: 1px; background: #e7e5e4; }
.order-status-step:first-child .order-status-line { display: none; }
.order-status-line-active { background: #fb923c; }
.order-status-steps-special { display: flex; align-items: center; gap: 0.5rem; min-height: 2rem; padding: 0.5rem 0.75rem; border: 1px solid #fed7aa; border-radius: 0.5rem; color: #9a3412; background: #fff7ed; font-size: 0.875rem; font-weight: 600; }
@media (max-width: 480px) { .order-status-steps { overflow-x: auto; min-width: 18rem; } .order-status-step { font-size: 0.6875rem; } }
</style>
