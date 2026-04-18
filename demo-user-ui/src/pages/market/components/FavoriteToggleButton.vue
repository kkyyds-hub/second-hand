<script setup lang="ts">
import { computed } from 'vue'
import { Heart, Loader2 } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    active?: boolean
    loading?: boolean
    disabled?: boolean
  }>(),
  {
    active: false,
    loading: false,
    disabled: false,
  },
)

const emit = defineEmits<{
  toggle: []
}>()

const canClick = computed(() => !props.loading && !props.disabled)
const label = computed(() => {
  if (props.loading) {
    return props.active ? '取消中' : '收藏中'
  }

  return props.active ? '已收藏' : '收藏'
})

function handleClick() {
  if (!canClick.value) {
    return
  }
  emit('toggle')
}
</script>

<template>
  <button
    class="inline-flex h-9 items-center justify-center gap-2 rounded-xl border px-3.5 text-[12px] font-medium shadow-sm shadow-gray-200/20 transition-all duration-200"
    :class="active && !disabled ? 'border-blue-200 bg-blue-50/90 text-blue-700 hover:border-blue-300 hover:bg-blue-100/80' : 'border-gray-200 bg-white/95 text-gray-600 hover:border-gray-300 hover:bg-gray-50 hover:text-gray-900 disabled:border-gray-200 disabled:bg-gray-100 disabled:text-gray-400'"
    type="button"
    :disabled="!canClick"
    @click="handleClick"
    :title="label"
  >
    <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
    <Heart v-else class="h-4 w-4 transition-colors" :class="active && !disabled ? 'fill-blue-500 text-blue-500' : 'text-gray-400'" />
    <span class="min-w-[36px] text-center">{{ label }}</span>
  </button>
</template>
