<script setup lang="ts">
withDefaults(defineProps<{ compact?: boolean; inverted?: boolean }>(), { compact: false, inverted: false })
</script>

<template>
  <div class="admin-brand" :class="{ 'admin-brand-compact': compact, 'admin-brand-inverted': inverted }">
    <span class="admin-brand-mark" aria-hidden="true"><i></i><i></i></span>
    <span v-if="!compact" class="admin-brand-copy"><strong>拾光集</strong><small>运营管理</small></span>
  </div>
</template>
