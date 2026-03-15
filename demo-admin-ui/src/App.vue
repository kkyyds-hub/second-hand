<script setup lang="ts">
/**
 * 根组件只负责承接路由页面与切换动效。
 * review 时如果发现“页面切换闪烁 / 过渡不生效”，优先从这里排查。
 */
</script>

<template>
  <!-- 所有业务页都通过路由出口渲染，根组件本身不承载具体页面逻辑。 -->
  <router-view v-slot="{ Component }">
    <!-- out-in 能保证旧页先离场再挂载新页，减少布局跳动。 -->
    <transition name="fade" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>

<style>
/* 根级切换动画尽量保持简单，避免和页面内部动画相互干扰。 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
