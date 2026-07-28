<script setup lang="ts">
defineProps<{
  current: 'wallet' | 'points' | 'credit'
}>()

const items = [
  { key: 'wallet', label: '钱包', to: '/assets/wallet' },
  { key: 'points', label: '积分', to: '/assets/points' },
  { key: 'credit', label: '信用', to: '/assets/credit' },
] as const
</script>

<template>
  <nav class="asset-center-nav" aria-label="资产中心导航">
    <router-link
      v-for="item in items"
      :key="item.key"
      :to="item.to"
      class="asset-center-nav-item"
      :class="{ 'asset-center-nav-item-active': current === item.key }"
      :aria-current="current === item.key ? 'page' : undefined"
    >
      {{ item.label }}
    </router-link>
  </nav>
</template>
