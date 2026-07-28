<script setup lang="ts">
import { ref } from 'vue'
import authSideImage from '@/assets/commerce/auth-side.webp'
import { USER_APP_TITLE, USER_BRAND_MARK } from '@/utils/brand'

defineProps<{
  title: string
  description: string
}>()

const imageFailed = ref(false)
</script>

<template>
  <main class="commerce-auth-shell">
    <section class="commerce-auth-frame" aria-label="账户入口">
      <aside class="commerce-auth-visual" :class="{ 'commerce-auth-visual-fallback': imageFailed }">
        <img
          v-if="!imageFailed"
          class="commerce-auth-image"
          :src="authSideImage"
          alt="陈列着可继续使用的生活物件的展示架"
          @error="imageFailed = true"
        />
        <div class="commerce-auth-visual-content">
          <span class="commerce-auth-brand-mark" aria-hidden="true">{{ USER_BRAND_MARK }}</span>
          <p class="commerce-auth-brand-name">{{ USER_APP_TITLE }}</p>
          <h2>让闲置继续被需要</h2>
          <p>在这里发现、分享和延续每一件生活好物的价值。</p>
        </div>
      </aside>

      <section class="commerce-auth-panel" aria-labelledby="commerce-auth-title">
        <div class="commerce-auth-form">
          <header class="commerce-auth-heading">
            <p class="commerce-auth-kicker">{{ USER_APP_TITLE }}</p>
            <h1 id="commerce-auth-title">{{ title }}</h1>
            <p>{{ description }}</p>
          </header>
          <slot />
          <footer class="commerce-auth-footer"><slot name="footer" /></footer>
        </div>
      </section>
    </section>
  </main>
</template>
