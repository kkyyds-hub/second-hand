<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ImageOff, Plus, Trash2 } from 'lucide-vue-next'

const props = withDefaults(defineProps<{
  modelValue: string[]
  disabled?: boolean
  error?: string
}>(), {
  disabled: false,
  error: '',
})

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
  blur: []
  change: []
}>()

const failedImageIndexes = ref(new Set<number>())
const images = computed(() => props.modelValue)
const primaryImageIndex = computed(() => images.value.findIndex((url) => Boolean(url.trim())))
const localImageUpdates = ref<string[]>([])

function imageSignature(value: string[]) { return JSON.stringify(value) }
function emitImages(next: string[]) {
  localImageUpdates.value = [...localImageUpdates.value, imageSignature(next)]
  emit('update:modelValue', next)
  emit('change')
}

watch(
  () => props.modelValue,
  (next) => {
    const signature = imageSignature(next)
    const localUpdateIndex = localImageUpdates.value.indexOf(signature)
    if (localUpdateIndex >= 0) {
      localImageUpdates.value = localImageUpdates.value.slice(localUpdateIndex + 1)
      return
    }
    localImageUpdates.value = []
    failedImageIndexes.value = new Set()
  },
)

function updateImage(index: number, value: string) {
  const next = [...images.value]
  next[index] = value
  failedImageIndexes.value = new Set([...failedImageIndexes.value].filter((itemIndex) => itemIndex !== index))
  emitImages(next)
}

function addImage() {
  emitImages([...images.value, ''])
}

function removeImage(index: number) {
  failedImageIndexes.value = new Set([...failedImageIndexes.value]
    .filter((itemIndex) => itemIndex !== index)
    .map((itemIndex) => itemIndex > index ? itemIndex - 1 : itemIndex))
  emitImages(images.value.filter((_, itemIndex) => itemIndex !== index))
}

function markImageFailed(index: number) {
  failedImageIndexes.value = new Set([...failedImageIndexes.value, index])
}
</script>

<template>
  <section class="section-panel">
    <div class="section-header section-header-plain">
      <div>
        <h2 class="section-heading">商品图片</h2>
        <p class="section-subtitle">添加商品图片链接，第一张有效图片将作为主图。</p>
      </div>
      <button class="btn-default !h-9 px-3" type="button" :disabled="disabled" @click="addImage">
        <Plus class="h-4 w-4" aria-hidden="true" />
        <span>添加图片链接</span>
      </button>
    </div>
    <div class="section-body pt-0">
      <div v-if="images.length" class="space-y-4">
        <div v-for="(url, index) in images" :key="index" class="grid gap-3 border-b border-gray-100 pb-4 last:border-0 last:pb-0 sm:grid-cols-[minmax(0,1fr)_104px]">
          <div class="min-w-0">
            <div class="mb-2 flex flex-wrap items-center gap-2">
              <p class="text-[13px] font-medium text-gray-800">第 {{ index + 1 }} 张图片</p>
              <span v-if="index === primaryImageIndex" class="chip chip-accent">主图</span>
            </div>
            <label class="sr-only" :for="`seller-product-image-${index}`">第 {{ index + 1 }} 张图片链接</label>
            <div class="flex min-w-0 gap-2">
              <input
                :id="`seller-product-image-${index}`"
                :value="url"
                class="input-standard min-w-0 flex-1"
                type="text"
                inputmode="url"
                placeholder="https://example.com/product.jpg"
                :disabled="disabled"
                @input="updateImage(index, ($event.target as HTMLInputElement).value)"
                @blur="emit('blur')"
              />
              <button class="btn-danger !h-10 !w-10 shrink-0 px-0" type="button" :disabled="disabled" :aria-label="`删除第 ${index + 1} 张图片`" @click="removeImage(index)">
                <Trash2 class="h-4 w-4" aria-hidden="true" />
              </button>
            </div>
          </div>
          <div class="flex aspect-square w-full items-center justify-center overflow-hidden rounded-lg border border-gray-200 bg-gray-50 sm:w-[104px]">
            <img v-if="url.trim() && !failedImageIndexes.has(index)" :src="url.trim()" :alt="`第 ${index + 1} 张商品图片预览`" class="h-full w-full object-contain" @error="markImageFailed(index)" />
            <div v-else class="flex flex-col items-center gap-1 px-2 text-center text-[11px] leading-4 text-gray-400">
              <ImageOff class="h-5 w-5" aria-hidden="true" />
              <span>{{ url.trim() ? '图片无法加载' : '等待图片链接' }}</span>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="flex min-h-28 items-center justify-center rounded-lg border border-dashed border-gray-200 bg-gray-50 px-4 text-center text-[13px] text-gray-500">
        暂未添加商品图片
      </div>
      <p v-if="error" class="form-helper !text-red-500" role="alert">{{ error }}</p>
    </div>
  </section>
</template>
