<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ImageOff } from 'lucide-vue-next'

const props = defineProps<{
  productTitle: string
  imageUrls: string[]
}>()

const selectedIndex = ref(0)
const failedImages = ref<Set<string>>(new Set())

const availableImages = computed(() =>
  props.imageUrls.filter((url) => url && !failedImages.value.has(url)),
)
const selectedImage = computed(() => availableImages.value[selectedIndex.value] ?? '')

watch(
  () => props.imageUrls,
  () => {
    selectedIndex.value = 0
    failedImages.value = new Set()
  },
  { deep: true },
)

watch(availableImages, (images) => {
  if (selectedIndex.value >= images.length) {
    selectedIndex.value = 0
  }
})

function selectImage(index: number) {
  selectedIndex.value = index
}

function handleImageError(url: string) {
  failedImages.value = new Set([...failedImages.value, url])
}
</script>

<template>
  <div class="product-gallery">
    <div class="product-gallery-stage">
      <img
        v-if="selectedImage"
        :src="selectedImage"
        :alt="`${productTitle} 商品图片 ${selectedIndex + 1}`"
        class="product-gallery-main-image"
        @error="handleImageError(selectedImage)"
      />
      <div v-else class="product-gallery-placeholder">
        <ImageOff class="h-9 w-9" aria-hidden="true" />
        <span>暂无商品图片</span>
      </div>
    </div>

    <div v-if="availableImages.length > 1" class="product-gallery-thumbnails" aria-label="商品图片列表">
      <button
        v-for="(imageUrl, index) in availableImages"
        :key="`${imageUrl}-${index}`"
        type="button"
        class="product-gallery-thumbnail"
        :class="{ 'product-gallery-thumbnail-active': selectedIndex === index }"
        :aria-label="`查看第 ${index + 1} 张商品图片`"
        :aria-pressed="selectedIndex === index"
        @click="selectImage(index)"
      >
        <img :src="imageUrl" :alt="`${productTitle} 缩略图 ${index + 1}`" @error="handleImageError(imageUrl)" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.product-gallery {
  display: grid;
  gap: 0.875rem;
  min-width: 0;
}

.product-gallery-stage {
  aspect-ratio: 1 / 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1px solid var(--commerce-border);
  border-radius: var(--commerce-radius-md);
  background: #f4eee7;
}

.product-gallery-main-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
  transition: opacity var(--commerce-duration) var(--commerce-ease);
}

.product-gallery-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  color: var(--commerce-muted);
  font-size: 0.875rem;
}

.product-gallery-thumbnails {
  display: flex;
  gap: 0.625rem;
  overflow-x: auto;
  padding-bottom: 0.25rem;
  scrollbar-width: thin;
  scrollbar-color: #d6c9bd transparent;
}

.product-gallery-thumbnails::-webkit-scrollbar {
  height: 5px;
}

.product-gallery-thumbnails::-webkit-scrollbar-thumb {
  background: #d6c9bd;
  border-radius: 999px;
}

.product-gallery-thumbnail {
  flex: 0 0 4.5rem;
  width: 4.5rem;
  height: 4.5rem;
  overflow: hidden;
  border: 2px solid transparent;
  border-radius: var(--commerce-radius-sm);
  background: white;
  padding: 0.1875rem;
  cursor: pointer;
  transition: border-color var(--commerce-duration) var(--commerce-ease), transform var(--commerce-duration) var(--commerce-ease);
}

.product-gallery-thumbnail:hover {
  border-color: #dfc8b7;
  transform: translateY(-1px);
}

.product-gallery-thumbnail-active {
  border-color: var(--commerce-brand);
  box-shadow: 0 0 0 2px var(--commerce-brand-soft);
}

.product-gallery-thumbnail:focus-visible {
  outline: 2px solid var(--commerce-brand);
  outline-offset: 2px;
}

.product-gallery-thumbnail img {
  width: 100%;
  height: 100%;
  border-radius: 0.25rem;
  object-fit: cover;
}
</style>
