<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  open: boolean
  title: string
  description: string
  confirming?: boolean
}>(), { confirming: false })

const emit = defineEmits<{ cancel: []; confirm: [] }>()
const dialog = ref<HTMLElement | null>(null)
const cancelButton = ref<HTMLButtonElement | null>(null)
const previousActiveElement = ref<HTMLElement | null>(null)
let focusGuardActive = false

const focusDialogTarget = () => {
  if (!props.open) return
  if (props.confirming) dialog.value?.focus()
  else cancelButton.value?.focus()
}
const handleFocusIn = (event: FocusEvent) => {
  if (!props.open || !dialog.value) return
  if (event.target instanceof Node && dialog.value.contains(event.target)) return
  focusDialogTarget()
}
const addFocusGuard = () => {
  if (focusGuardActive) return
  document.addEventListener('focusin', handleFocusIn)
  focusGuardActive = true
}
const removeFocusGuard = () => {
  if (!focusGuardActive) return
  document.removeEventListener('focusin', handleFocusIn)
  focusGuardActive = false
}
const restoreFocus = async () => {
  const activeElement = previousActiveElement.value
  await nextTick()
  if (activeElement?.isConnected) activeElement.focus()
  if (previousActiveElement.value === activeElement) previousActiveElement.value = null
}
const close = () => { if (!props.confirming) emit('cancel') }
const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') { close(); return }
  if (event.key !== 'Tab') return
  if (props.confirming) { event.preventDefault(); dialog.value?.focus(); return }
  const focusable = Array.from(dialog.value?.querySelectorAll<HTMLElement>('button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])') ?? [])
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (!first || !last) { event.preventDefault(); dialog.value?.focus(); return }
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}

watch(() => props.open, async (open) => {
  if (open) {
    previousActiveElement.value = document.activeElement instanceof HTMLElement ? document.activeElement : null
    await nextTick()
    if (!props.open) return
    addFocusGuard()
    focusDialogTarget()
    return
  }
  removeFocusGuard()
  await restoreFocus()
})
watch(() => props.confirming, async () => { if (props.open) { await nextTick(); focusDialogTarget() } })
onBeforeUnmount(() => { removeFocusGuard(); void restoreFocus() })
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="commerce-dialog-backdrop" role="presentation" @click.self="close">
      <section ref="dialog" class="commerce-dialog" role="dialog" aria-modal="true" aria-labelledby="commerce-confirm-dialog-title" aria-describedby="commerce-confirm-dialog-description" :aria-busy="confirming" tabindex="-1" @keydown="handleKeydown">
        <h2 id="commerce-confirm-dialog-title" class="commerce-dialog-title">{{ title }}</h2>
        <p id="commerce-confirm-dialog-description" class="commerce-dialog-desc">{{ description }}</p>
        <div class="commerce-dialog-actions">
          <button ref="cancelButton" class="btn-default" type="button" :disabled="confirming" @click="close">取消</button>
          <button class="btn-danger" type="button" :disabled="confirming" @click="emit('confirm')">{{ confirming ? '删除中...' : '确认删除' }}</button>
        </div>
      </section>
    </div>
  </Teleport>
</template>
