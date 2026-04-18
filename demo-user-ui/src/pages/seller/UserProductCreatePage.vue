<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Loader2 } from 'lucide-vue-next'
import { createUserProduct } from '@/api/userProducts'
import {
  collectUserProductValidationErrors,
  createEmptyUserProductFormModel,
  normalizeCreateUserProductInput,
  type UserProductFormField,
  type UserProductFormModel,
} from '@/pages/seller/user-product-form'

const router = useRouter()
const submitting = ref(false)
const submitStatus = ref<'idle' | 'success' | 'error'>('idle')
const submitMessage = ref('')

const productForm = reactive<UserProductFormModel>(createEmptyUserProductFormModel())

const validationErrors = computed<Partial<Record<UserProductFormField, string>>>(() => {
  return collectUserProductValidationErrors(productForm, { requireCategory: false })
})

const hasValidationErrors = computed(() => Object.keys(validationErrors.value).length > 0)
const canSubmit = computed(() => !submitting.value && !hasValidationErrors.value)

function clearSubmitStatus() {
  if (submitting.value || submitStatus.value === 'idle') {
    return
  }

  submitStatus.value = 'idle'
  submitMessage.value = ''
}

function readSubmitErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return '商品创建失败，请稍后重试。'
}

async function submitCreateForm() {
  if (submitting.value) {
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

    const created = await createUserProduct(normalizeCreateUserProductInput(productForm))

    submitStatus.value = 'success'
    submitMessage.value = '商品创建成功，正在跳转到详情页...'

    if (created.id !== null) {
      await router.replace({
        name: 'SellerProductDetail',
        params: { productId: created.id },
        query: { created: '1' },
      })
      return
    }

    await router.replace({
      name: 'SellerProductList',
      query: { created: '1' },
    })
  } catch (error: unknown) {
    submitStatus.value = 'error'
    submitMessage.value = readSubmitErrorMessage(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page-body page-body-narrow">
    <section class="page-header">
      <div class="page-header-main">
        <p class="page-kicker">卖家</p>
        <h1 class="page-title">创建商品</h1>
        <p class="page-desc">Day04 第二包 create 子流：创建成功后默认进入审核中状态，后续状态流转在列表/详情页继续处理。</p>
      </div>
      <div class="page-actions">
        <router-link class="btn-default" to="/seller/products">
          <ArrowLeft class="h-4 w-4" />
          <span>返回商品列表</span>
        </router-link>
      </div>
    </section>

    <form class="space-y-6" @submit.prevent="submitCreateForm">
      <section class="section-panel">
        <div class="section-header section-header-plain">
          <div>
            <h2 class="section-heading">基础信息</h2>
          </div>
        </div>
        <div class="section-body pt-0 grid gap-5 md:grid-cols-2">
          <div class="md:col-span-2">
            <label class="form-label" for="product-create-title">商品标题 *</label>
            <input
              id="product-create-title"
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
            <label class="form-label" for="product-create-price">商品价格（元）*</label>
            <input
              id="product-create-price"
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
            <label class="form-label" for="product-create-category">商品分类（可选）</label>
            <input
              id="product-create-category"
              v-model="productForm.category"
              class="input-standard"
              type="text"
              maxlength="60"
              placeholder="例如 数码配件"
              :disabled="submitting"
              @input="clearSubmitStatus"
            />
            <p v-if="validationErrors.category" class="form-helper !text-red-500">{{ validationErrors.category }}</p>
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
          <label class="form-label" for="product-create-description">描述（可选）</label>
          <textarea
            id="product-create-description"
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
          <label class="form-label" for="product-create-images">图片 URL（可选）</label>
          <textarea
            id="product-create-images"
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
            <span>{{ submitting ? '提交中...' : '提交创建' }}</span>
          </button>
          <router-link class="btn-default" to="/seller/products">取消返回</router-link>
        </div>
      </section>
    </form>
  </div>
</template>
