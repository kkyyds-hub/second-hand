<script setup lang="ts">
/**
 * 根组件只承载路由视图和基础转场。
 * 具体的鉴权壳、页面守卫和业务状态都在各自模块里处理。
 */
</script>

<template>
  <router-view v-slot="{ Component }">
    <transition name="fade" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>

<style>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.24s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
