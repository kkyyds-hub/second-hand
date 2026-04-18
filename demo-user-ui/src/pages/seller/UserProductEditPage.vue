<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Loader2 } from 'lucide-vue-next'
import { getUserProductDetail, updateUserProduct } from '@/api/userProducts'
import {
  collectUserProductValidationErrors,
  createEmptyUserProductFormModel,
  normalizeUpdateUserProductInput,
  toUserProductFormModel,
  type UserProductFormField,
  type UserProductFormModel,
} from '@/pages/seller/user-product-form'

const route = useRoute()
const router = useRouter()

const loadingDetail = ref(true)
const loadErrorMessage = ref('')
const submitting = ref(false)
const submitStatus = ref<'idle' | 'success' | 'error'>('idle')
const submitMessage = ref('')
const categoryReadonly = ref('')
const productForm = reactive<UserProductFormModel>(createEmptyUserProductFormModel())

function readRouteProductId(value: unknown) {
  if (typeof value === 'number' && Number.isInteger(value) && value > 0) {
    return value
  }

  if (typeof value === 'string') {
    const normalized = value.trim()
    if (/^\d+$/.test(normalized)) {
      const parsed = Number(normalized)
      if (Number.isInteger(parsed) && parsed > 0) {
        return parsed
      }
    }
  }

  return null
}

const productId = computed(() => readRouteProductId(route.params.productId))

const validationErrors = computed<Partial<Record<UserProductFormField, string>>>(() => {
  return collectUserProductValidationErrors(productForm, { requireCategory: false })
})

const hasValidationErrors = computed(() => Object.keys(validationErrors.value).length > 0)
const canSubmit = computed(() => !loadingDetail.value && !submitting.value && !hasValidationErrors.value && !loadErrorMessage.value)

function clearSubmitStatus() {
  if (submitting.value || submitStatus.value === 'idle') {
    return
  }

  submitStatus.value = 'idle'
  submitMessage.value = ''
}

function readLoadErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return '商品详情加载失败，请稍后重试。'
}

function readSubmitErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return '商品编辑失败，请稍后重试。'
}

function fillProductFormFromDetail() {
  Object.assign(productForm, createEmptyUserProductFormModel())
  categoryReadonly.value = ''
}

async function loadProductDetail() {
  const currentProductId = productId.value
  if (!currentProductId) {
    loadingDetail.value = false
    loadErrorMessage.value = '商品 ID 无效，无法进入编辑页。'
    return
  }

  try {
    loadingDetail.value = true
    loadErrorMessage.value = ''
    fillProductFormFromDetail()
    clearSubmitStatus()

    const detail = await getUserProductDetail(currentProductId)
    Object.assign(productForm, toUserProductFormModel(detail))
    categoryReadonly.value = detail.category || '-'
  } catch (error: unknown) {
    loadErrorMessage.value = readLoadErrorMessage(error)
  } finally {
    loadingDetail.value = false
  }
}

async function submitEditForm() {
  if (submitting.value || loadingDetail.value) {
    return
  }

  const currentProductId = productId.value
  if (!currentProductId) {
    submitStatus.value = 'error'
    submitMessage.value = '商品 ID 无效，无法提交编辑。'
    return
  }

  if (hasValidationErrors.value) {
    submitStatus.value = 'error'
    submitMessage.value = '请先修正表单校验错误后再提交。'
    return
  }

  try {
    submitting.value = true
    submitStatus.value = 'idle'
    submitMessage.value = ''

    await updateUserProduct(currentProductId, normalizeUpdateUserProductInput(productForm))

    submitStatus.value = 'success'
    submitMessage.value = '商品编辑成功，正在返回详情页...'
    await router.replace({
      name: 'SellerProductDetail',
      params: { productId: currentProductId },
      query: { edited: '1' },
    })
  } catch (error: unknown) {
    submitStatus.value = 'error'
    submitMessage.value = readSubmitErrorMessage(error)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadProductDetail()
})
</script>

<template>
  <div class="page-body page-body-narrow">
    <section class="page-header">
      <div class="page-header-main">
        <p class="page-kicker">卖家</p>
        <h1 class="page-title">编辑商品</h1>
        <p class="page-desc">Day04 第二包 edit 子流：编辑提交后统一进入审核中，分类字段当前按后端契约保持只读展示。</p>
      </div>
      <div class="page-actions">
        <router-link class="btn-default" :to="productId ? `/seller/products/${productId}` : '/seller/products'">
          <ArrowLeft class="h-4 w-4" />
          <span>返回商品详情</span>
        </router-link>
      </div>
    </section>

    <section v-if="loadErrorMessage" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <div class="flex-1">
        <p>{{ loadErrorMessage }}</p>
        <div class="mt-3 flex flex-wrap items-center gap-2">
          <button class="btn-default" type="button" :disabled="loadingDetail" @click="loadProductDetail">重试加载</button>
          <router-link class="btn-default" to="/seller/products">返回列表</router-link>
        </div>
      </div>
    </section>

    <section v-else-if="loadingDetail" class="section-panel">
      <div class="section-body flex min-h-[280px] items-center justify-center">
        <div class="flex items-center gap-3 text-gray-500">
          <Loader2 class="h-5 w-5 animate-spin" />
          <p class="text-[13px]">正在加载商品详情...</p>
        </div>
      </div>
    </section>

    <form v-else class="space-y-6" @submit.prevent="submitEditForm">
      <section class="section-panel">
        <div class="section-header section-header-plain">
          <div>
            <h2 class="section-heading">基础信息</h2>
          </div>
        </div>
        <div class="section-body pt-0 grid gap-5 md:grid-cols-2">
          <div class="md:col-span-2">
            <label class="form-label" for="product-edit-title">商品标题 *</label>
            <input
              id="product-edit-title"
              v-model="productForm.title"
              class="input-standard"
              type="text"
              maxlength="120"
              placeholder="请输入商品标题"
              :disabled="submitting"
              @input="clearSubmitStatus"
            />
            <p v-if="validationErrors.title" class="form-helper !text-red-500">{{ validationErrors.title }}</p>
          </div>

          <div>
            <label class="form-label" for="product-edit-price">商品价格（元）*</label>
            <input
              id="product-edit-price"
              v-model="productForm.price"
              class="input-standard"
              type="text"
              inputmode="decimal"
              placeholder="例如 99.99"
              :disabled="submitting"
              @input="clearSubmitStatus"
            />
            <p v-if="validationErrors.price" class="form-helper !text-red-500">{{ validationErrors.price }}</p>
          </div>

          <div>
            <label class="form-label" for="product-edit-category-readonly">商品分类（只读）</label>
            <input id="product-edit-category-readonly" class="input-standard" type="text" :value="categoryReadonly" disabled />
            <p class="form-helper">当前更新契约不接收 category，若需修改分类请升级后端合同后再开放编辑。</p>
          </div>
        </div>
      </section>

      <section class="section-panel">
        <div class="section-header section-header-plain">
          <div>
            <h2 class="section-heading">商品描述</h2>
          </div>
        </div>
        <div class="section-body pt-0">
          <label class="form-label" for="product-edit-description">描述（可选）</label>
          <textarea
            id="product-edit-description"
            v-model="productForm.description"
            class="input-standard min-h-[140px] resize-y"
            rows="5"
            maxlength="2000"
            placeholder="请输入商品描述"
            :disabled="submitting"
            @input="clearSubmitStatus"
          />
          <p v-if="validationErrors.description" class="form-helper !text-red-500">{{ validationErrors.description }}</p>
        </div>
      </section>

      <section class="section-panel">
        <div class="section-header section-header-plain">
          <div>
            <h2 class="section-heading">商品图片</h2>
            <p class="section-subtitle">支持“每行一个 URL”或“逗号分隔 URL”。</p>
          </div>
        </div>
        <div class="section-body pt-0">
          <label class="form-label" for="product-edit-images">图片 URL（可选）</label>
          <textarea
            id="product-edit-images"
            v-model="productForm.imageUrlsText"
            class="input-standard min-h-[160px] resize-y font-mono text-[12px]"
            rows="6"
            placeholder="https://example.com/image-1.jpg\nhttps://example.com/image-2.jpg"
            :disabled="submitting"
            @input="clearSubmitStatus"
          />
          <p v-if="validationErrors.imageUrlsText" class="form-helper !text-red-500">{{ validationErrors.imageUrlsText }}</p>
        </div>
      </section>

      <section class="space-y-4">
        <div v-if="submitStatus === 'success'" class="notice-banner notice-banner-success">
          <span class="notice-dot bg-emerald-500"></span>
          <span>{{ submitMessage }}</span>
        </div>
        <div v-else-if="submitStatus === 'error'" class="notice-banner notice-banner-danger">
          <span class="notice-dot bg-red-500"></span>
          <span>{{ submitMessage }}</span>
        </div>

        <div class="flex flex-wrap items-center gap-3">
          <button class="btn-primary" type="submit" :disabled="!canSubmit">
            <Loader2 v-if="submitting" class="h-4 w-4 animate-spin" />
            <span>{{ submitting ? '提交中...' : '提交编辑' }}</span>
          </button>
          <router-link class="btn-default" :to="productId ? `/seller/products/${productId}` : '/seller/products'">取消返回</router-link>
        </div>
      </section>
    </form>
  </div>
</template>
