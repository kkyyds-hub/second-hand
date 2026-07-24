<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { Loader2 } from 'lucide-vue-next'

const props = withDefaults(defineProps<{
  open: boolean
  title: string
  description: string
  confirmLabel: string
  loading?: boolean
  destructive?: boolean
}>(), {
  loading: false,
  destructive: false,
})

const dialog = ref<HTMLElement | null>(null)

const emit = defineEmits<{
  close: []
  confirm: []
}>()

watch(() => props.open, async (open) => {
  if (open) {
    await nextTick()
    dialog.value?.focus()
  }
})

function close() {
  if (!props.loading) {
    emit('close')
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    close()
  }
}
</script>

<template>
  <div
    v-if="open"
    class="fixed inset-0 z-50 flex items-end bg-gray-950/35 p-4 sm:items-center sm:justify-center"
    @click.self="close"
  >
    <section
      ref="dialog"
      class="w-full max-w-md rounded-xl bg-white p-5 shadow-xl shadow-gray-950/20 sm:p-6"
      role="dialog"
      aria-modal="true"
      aria-labelledby="seller-product-action-dialog-title"
      tabindex="-1"
      @keydown="handleKeydown"
    >
      <h2 id="seller-product-action-dialog-title" class="text-[18px] font-semibold text-gray-950">{{ title }}</h2>
      <p class="mt-3 text-[14px] leading-6 text-gray-600">{{ description }}</p>
      <div class="mt-6 flex flex-wrap justify-end gap-3">
        <button class="btn-default" type="button" :disabled="loading" @click="close">取消</button>
        <button :class="destructive ? 'btn-danger' : 'btn-primary'" type="button" :disabled="loading" @click="emit('confirm')">
          <Loader2 v-if="loading" class="h-4 w-4 animate-spin" aria-hidden="true" />
          <span>{{ loading ? '处理中...' : confirmLabel }}</span>
        </button>
      </div>
    </section>
  </div>
</template>
