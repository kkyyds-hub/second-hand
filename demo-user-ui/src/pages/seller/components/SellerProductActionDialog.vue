<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
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
const cancelButton = ref<HTMLButtonElement | null>(null)
const previousActiveElement = ref<HTMLElement | null>(null)
let isDocumentFocusGuardActive = false

const emit = defineEmits<{
  close: []
  confirm: []
}>()

function focusPreferredDialogTarget() {
  if (!props.open) return

  if (props.loading) {
    dialog.value?.focus()
    return
  }

  cancelButton.value?.focus()
}

function handleDocumentFocusIn(event: FocusEvent) {
  if (!props.open || !dialog.value) return

  const target = event.target
  if (target instanceof Node && dialog.value.contains(target)) return

  focusPreferredDialogTarget()
}

function addDocumentFocusGuard() {
  if (typeof document === 'undefined' || isDocumentFocusGuardActive) return

  document.addEventListener('focusin', handleDocumentFocusIn)
  isDocumentFocusGuardActive = true
}

function removeDocumentFocusGuard() {
  if (typeof document === 'undefined' || !isDocumentFocusGuardActive) return

  document.removeEventListener('focusin', handleDocumentFocusIn)
  isDocumentFocusGuardActive = false
}

watch(() => props.open, async (open) => {
  if (open) {
    previousActiveElement.value = typeof document !== 'undefined' && document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null
    await nextTick()
    if (!props.open) return

    addDocumentFocusGuard()
    focusPreferredDialogTarget()
    return
  }

  removeDocumentFocusGuard()
  const activeElement = previousActiveElement.value
  await nextTick()
  if (activeElement?.isConnected) {
    activeElement.focus()
  }
  if (previousActiveElement.value === activeElement) {
    previousActiveElement.value = null
  }
})

watch(() => props.loading, async (loading) => {
  if (!props.open) return

  await nextTick()
  if (!props.open) return

  if (loading) {
    dialog.value?.focus()
    return
  }

  cancelButton.value?.focus()
})

function close() {
  if (!props.loading) {
    emit('close')
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    close()
    return
  }

  if (event.key !== 'Tab') return

  if (props.loading) {
    event.preventDefault()
    dialog.value?.focus()
    return
  }

  const focusableElements = Array.from(
    dialog.value?.querySelectorAll<HTMLElement>('button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])') ?? [],
  ).filter((element) => !element.hasAttribute('disabled'))
  const firstElement = focusableElements[0]
  const lastElement = focusableElements[focusableElements.length - 1]
  if (!firstElement || !lastElement) {
    event.preventDefault()
    dialog.value?.focus()
    return
  }

  if (event.shiftKey && document.activeElement === firstElement) {
    event.preventDefault()
    lastElement.focus()
  } else if (!event.shiftKey && document.activeElement === lastElement) {
    event.preventDefault()
    firstElement.focus()
  }
}

onBeforeUnmount(() => {
  removeDocumentFocusGuard()
  const activeElement = previousActiveElement.value
  previousActiveElement.value = null
  if (activeElement?.isConnected) {
    activeElement.focus()
  }
})
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
      aria-describedby="seller-product-action-dialog-description"
      :aria-busy="loading"
      tabindex="-1"
      @keydown="handleKeydown"
    >
      <h2 id="seller-product-action-dialog-title" class="text-[18px] font-semibold text-gray-950">{{ title }}</h2>
      <p id="seller-product-action-dialog-description" class="mt-3 text-[14px] leading-6 text-gray-600">{{ description }}</p>
      <div class="mt-6 flex flex-wrap justify-end gap-3">
        <button ref="cancelButton" class="btn-default" type="button" :disabled="loading" @click="close">取消</button>
        <button :class="destructive ? 'btn-danger' : 'btn-primary'" type="button" :disabled="loading" @click="emit('confirm')">
          <Loader2 v-if="loading" class="h-4 w-4 animate-spin" aria-hidden="true" />
          <span>{{ loading ? '处理中...' : confirmLabel }}</span>
        </button>
      </div>
    </section>
  </div>
</template>
