<script setup lang="ts">
defineProps<{
  open: boolean
  title: string
  description: string
  confirming?: boolean
}>()

const emit = defineEmits<{
  cancel: []
  confirm: []
}>()
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="commerce-dialog-backdrop" role="presentation" @click.self="emit('cancel')">
      <section class="commerce-dialog" role="dialog" aria-modal="true" :aria-label="title">
        <h2 class="commerce-dialog-title">{{ title }}</h2>
        <p class="commerce-dialog-desc">{{ description }}</p>
        <div class="commerce-dialog-actions">
          <button class="btn-default" type="button" :disabled="confirming" @click="emit('cancel')">取消</button>
          <button class="btn-danger" type="button" :disabled="confirming" @click="emit('confirm')">
            {{ confirming ? '删除中...' : '确认删除' }}
          </button>
        </div>
      </section>
    </div>
  </Teleport>
</template>
