<script setup lang="ts">
import { nextTick, onBeforeUnmount, watch } from 'vue'

const props = withDefaults(defineProps<{
  open: boolean
  titleId: string
  descriptionId?: string
  variant?: 'dialog' | 'drawer'
}>(), { descriptionId: undefined, variant: 'dialog' })
const emit = defineEmits<{ close: [] }>()
let previousActiveElement: HTMLElement | null = null

const getPanel = () => document.querySelector<HTMLElement>('[data-admin-overlay-panel]')
const getFocusableElements = () => {
  const panel = getPanel()
  if (!panel) return []
  return Array.from(panel.querySelectorAll<HTMLElement>('a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])')).filter((element) => !element.hasAttribute('hidden'))
}
const close = () => emit('close')
const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') { event.preventDefault(); close(); return }
  if (event.key !== 'Tab') return
  const focusable = getFocusableElements()
  const panel = getPanel()
  if (!panel || !focusable.length) { event.preventDefault(); panel?.focus(); return }
  const first = focusable[0]!
  const last = focusable[focusable.length - 1]!
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}
const restoreFocus = () => {
  document.body.style.overflow = ''
  if (previousActiveElement?.isConnected) previousActiveElement.focus()
  previousActiveElement = null
}
watch(() => props.open, async (isOpen) => {
  if (!isOpen) { restoreFocus(); return }
  previousActiveElement = document.activeElement instanceof HTMLElement ? document.activeElement : null
  document.body.style.overflow = 'hidden'
  await nextTick()
  const focusable = getFocusableElements()
  ;(focusable[0] || getPanel())?.focus()
})
onBeforeUnmount(restoreFocus)
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="admin-overlay" :class="`admin-overlay-${variant}`" @mousedown.self="close">
      <section data-admin-overlay-panel class="admin-overlay-panel" :class="`admin-overlay-panel-${variant}`" role="dialog" aria-modal="true" :aria-labelledby="titleId" :aria-describedby="descriptionId" tabindex="-1" @keydown="handleKeydown"><slot /></section>
    </div>
  </Teleport>
</template>
