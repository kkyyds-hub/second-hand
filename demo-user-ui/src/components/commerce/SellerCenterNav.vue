<script setup lang="ts">
import { computed } from 'vue'
import { readCurrentUser } from '@/utils/request'

defineProps<{
  current: 'workbench' | 'products' | 'create' | 'edit' | 'detail'
}>()

const user = readCurrentUser()
const shopId = computed(() => typeof user?.id === 'number' && user.id > 0 ? user.id : null)

const items = [
  { key: 'workbench', label: '工作台', to: '/seller' },
  { key: 'products', label: '我的商品', to: '/seller/products' },
  { key: 'orders', label: '卖家订单', to: '/orders/seller' },
  { key: 'after-sales', label: '售后处理', to: '/orders/seller/after-sales/decision' },
] as const
</script>

<template>
  <nav class="seller-center-nav" aria-label="卖家中心导航">
    <router-link
      v-for="item in items"
      :key="item.key"
      class="seller-center-nav-item"
      :class="{ 'seller-center-nav-item-active': current === item.key || (item.key === 'products' && ['create', 'edit', 'detail'].includes(current)) }"
      :to="item.to"
      :aria-current="current === item.key ? 'page' : undefined"
    >{{ item.label }}</router-link>
    <router-link v-if="shopId" class="seller-center-nav-item" :to="`/shop/${shopId}`">公开小店</router-link>
  </nav>
</template>
