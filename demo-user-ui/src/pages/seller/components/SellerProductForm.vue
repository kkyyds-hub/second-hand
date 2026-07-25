<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ImageOff } from 'lucide-vue-next'
import SellerProductImageFields from '@/pages/seller/components/SellerProductImageFields.vue'
import type { UserProductFormField, UserProductFormModel } from '@/pages/seller/user-product-form'

const props = withDefaults(defineProps<{
  model: UserProductFormModel
  mode: 'create' | 'edit'
  categoryLabel?: string
  disabled?: boolean
  errors?: Partial<Record<UserProductFormField, string>>
}>(), {
  categoryLabel: '',
  disabled: false,
  errors: () => ({}),
})

const emit = defineEmits<{
  blur: [field: UserProductFormField]
  change: [field: UserProductFormField]
}>()

const previewImage = computed(() => props.model.imageUrls.map((url) => url.trim()).find(Boolean) || '')
const previewImageFailed = ref(false)
const previewTitle = computed(() => props.model.title.trim() || '商品标题')
const previewCategory = computed(() => props.model.category.trim() || props.categoryLabel || '未填写分类')
const previewPrice = computed(() => {
  const price = Number(props.model.price.trim())
  return Number.isFinite(price) && price > 0 ? `¥${price.toFixed(2)}` : '价格待填写'
})

watch(previewImage, () => { previewImageFailed.value = false })
</script>

<template>
  <div class="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_300px]">
    <div class="space-y-6">
      <section class="section-panel">
        <div class="section-header section-header-plain"><div><h2 class="section-heading">基础信息</h2></div></div>
        <div class="section-body grid gap-5 pt-0 md:grid-cols-2">
          <div class="md:col-span-2">
            <label class="form-label" for="seller-product-title">商品标题 *</label>
            <input id="seller-product-title" v-model="model.title" class="input-standard" type="text" maxlength="120" placeholder="例如：九成新无线耳机" :disabled="disabled" @input="emit('change', 'title')" @blur="emit('blur', 'title')" />
            <p class="form-helper">{{ model.title.length }}/120</p>
            <p v-if="errors.title" class="form-helper !text-red-500" role="alert">{{ errors.title }}</p>
          </div>
          <div>
            <label class="form-label" for="seller-product-price">商品价格（元）*</label>
            <input id="seller-product-price" v-model="model.price" class="input-standard" type="text" inputmode="decimal" placeholder="例如 99.99" :disabled="disabled" @input="emit('change', 'price')" @blur="emit('blur', 'price')" />
            <p v-if="errors.price" class="form-helper !text-red-500" role="alert">{{ errors.price }}</p>
          </div>
          <div v-if="mode === 'create'">
            <label class="form-label" for="seller-product-category">商品分类（可选）</label>
            <input id="seller-product-category" v-model="model.category" class="input-standard" type="text" maxlength="60" placeholder="例如 数码配件" :disabled="disabled" @input="emit('change', 'category')" @blur="emit('blur', 'category')" />
            <p class="form-helper">{{ model.category.length }}/60</p>
            <p v-if="errors.category" class="form-helper !text-red-500" role="alert">{{ errors.category }}</p>
          </div>
          <div v-else class="rounded-lg border border-gray-200 bg-gray-50 px-4 py-3">
            <p class="text-[12px] font-medium text-gray-500">商品分类</p>
            <p class="mt-1 break-words text-[14px] font-medium text-gray-900">{{ categoryLabel || '未填写分类' }}</p>
            <p class="mt-1 text-[12px] leading-5 text-gray-500">分类发布后暂不支持修改</p>
          </div>
        </div>
      </section>

      <section class="section-panel">
        <div class="section-header section-header-plain"><div><h2 class="section-heading">商品描述</h2></div></div>
        <div class="section-body pt-0">
          <label class="form-label" for="seller-product-description">描述（可选）</label>
          <textarea id="seller-product-description" v-model="model.description" class="input-standard min-h-[156px] resize-y" rows="6" maxlength="2000" placeholder="补充商品的使用情况、配件和注意事项" :disabled="disabled" @input="emit('change', 'description')" @blur="emit('blur', 'description')" />
          <p class="form-helper">{{ model.description.length }}/2000</p>
          <p v-if="errors.description" class="form-helper !text-red-500" role="alert">{{ errors.description }}</p>
        </div>
      </section>

      <SellerProductImageFields v-model="model.imageUrls" :disabled="disabled" :error="errors.imageUrls" @blur="emit('blur', 'imageUrls')" @change="emit('change', 'imageUrls')" />
    </div>

    <aside class="section-panel lg:sticky lg:top-6">
      <div class="section-header section-header-plain"><div><h2 class="section-heading">实时商品预览</h2><p class="section-subtitle">预览仅使用当前填写的信息。</p></div></div>
      <div class="section-body space-y-4 pt-0">
        <div class="flex aspect-square items-center justify-center overflow-hidden rounded-lg border border-gray-200 bg-gray-50">
          <img v-if="previewImage && !previewImageFailed" :src="previewImage" :alt="previewTitle" class="h-full w-full object-contain" @error="previewImageFailed = true" />
          <div v-else class="flex flex-col items-center gap-2 text-[13px] text-gray-400"><ImageOff class="h-7 w-7" aria-hidden="true" /><span>{{ previewImage ? '图片无法加载' : '暂无商品图片' }}</span></div>
        </div>
        <div class="space-y-2">
          <p class="break-words text-[17px] font-semibold text-gray-900">{{ previewTitle }}</p>
          <p class="font-numeric text-[22px] font-bold text-gray-900">{{ previewPrice }}</p>
          <p class="break-words text-[13px] text-gray-500">{{ previewCategory }}</p>
          <p class="text-[13px] leading-6 text-gray-600">提交后进入审核中</p>
        </div>
      </div>
    </aside>
  </div>
</template>
