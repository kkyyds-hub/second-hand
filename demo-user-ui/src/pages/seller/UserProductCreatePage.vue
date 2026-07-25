<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Loader2 } from 'lucide-vue-next'
import { createUserProduct } from '@/api/userProducts'
import SellerProductForm from '@/pages/seller/components/SellerProductForm.vue'
import {
  collectUserProductValidationErrors,
  createEmptyUserProductFormModel,
  findFirstOverlongUserProductImageIndex,
  normalizeCreateUserProductInput,
  type UserProductFormField,
  type UserProductFormModel,
} from '@/pages/seller/user-product-form'
import { isSellerUser, readCurrentUser } from '@/utils/request'

const route = useRoute()
const router = useRouter()
const sellerEnabled = computed(() => isSellerUser(readCurrentUser()))
const productForm = reactive<UserProductFormModel>(createEmptyUserProductFormModel())
const submitting = ref(false)
const submitAttempted = ref(false)
const touched = reactive<Partial<Record<UserProductFormField, boolean>>>({})
const submitMessage = ref('')
let active = true
let createSequence = 0
let createRequestInFlight = false

const validationErrors = computed(() => collectUserProductValidationErrors(productForm))
const visibleErrors = computed(() => Object.fromEntries(Object.entries(validationErrors.value).filter(([field]) => submitAttempted.value || touched[field as UserProductFormField])) as Partial<Record<UserProductFormField, string>>)

function markTouched(field: UserProductFormField) { touched[field] = true }
function clearSubmitMessage() { if (!submitting.value) submitMessage.value = '' }
function focusFirstError() {
  const field = Object.keys(validationErrors.value)[0] as UserProductFormField | undefined
  if (!field) return
  if (field === 'imageUrls') {
    const imageIndex = findFirstOverlongUserProductImageIndex(productForm)
    document.getElementById(imageIndex >= 0 ? `seller-product-image-${imageIndex}` : 'seller-product-image-0')?.focus()
    return
  }
  const ids: Record<Exclude<UserProductFormField, 'imageUrls'>, string> = {
    title: 'seller-product-title',
    price: 'seller-product-price',
    category: 'seller-product-category',
    description: 'seller-product-description',
  }
  document.getElementById(ids[field])?.focus()
}
function readError(error: unknown) { return error instanceof Error && error.message.trim() ? error.message : '发布失败，请稍后重试。' }

async function submitCreateForm() {
  if (createRequestInFlight || submitting.value) return
  submitAttempted.value = true
  if (Object.keys(validationErrors.value).length) { focusFirstError(); return }
  createRequestInFlight = true
  submitting.value = true
  const submittingSequence = ++createSequence
  const submittingRoutePath = route.path
  const isCreateRequestCurrent = () => active
    && createSequence === submittingSequence
    && route.name === 'SellerProductCreate'
    && route.path === submittingRoutePath
  try {
    submitMessage.value = ''
    const payload = normalizeCreateUserProductInput(productForm)
    const created = await createUserProduct(payload)
    if (!isCreateRequestCurrent()) return
    if (created.id !== null) {
      await router.replace({ name: 'SellerProductDetail', params: { productId: created.id }, query: { created: '1' } })
      return
    }
    await router.replace({ name: 'SellerProductList', query: { created: '1' } })
  } catch (error: unknown) {
    if (isCreateRequestCurrent()) submitMessage.value = readError(error)
  } finally {
    createRequestInFlight = false
    if (active && createSequence === submittingSequence) submitting.value = false
  }
}

onBeforeUnmount(() => {
  active = false
  createSequence += 1
})
</script>

<template>
  <main class="page-body">
    <section v-if="!sellerEnabled" class="section-panel">
      <div class="section-body space-y-4"><h1 class="page-title">当前账号尚未开通卖家功能</h1><p class="page-desc">开通卖家功能后，才能发布和管理闲置商品。</p><div class="flex flex-wrap gap-3"><router-link class="btn-primary" to="/market">返回市场</router-link><router-link class="btn-default" to="/">返回首页</router-link></div></div>
    </section>
    <template v-else>
      <section class="page-header"><div class="page-header-main"><p class="page-kicker">卖家中心</p><h1 class="page-title">发布闲置</h1><p class="page-desc">补充商品信息并提交审核，审核通过后即可在市场展示。</p></div><router-link class="btn-default" to="/seller/products"><ArrowLeft class="h-4 w-4" /><span>返回商品管理</span></router-link></section>
      <form class="space-y-6" @submit.prevent="submitCreateForm">
        <SellerProductForm :model="productForm" mode="create" :disabled="submitting" :errors="visibleErrors" @blur="markTouched" @change="clearSubmitMessage" />
        <section class="section-panel-muted"><div class="section-body"><h2 class="section-heading">发布说明</h2><p class="mt-2 text-[13px] leading-6 text-gray-600">商品提交后会进入审核中，审核通过后才会在市场展示。</p></div></section>
        <section v-if="submitMessage" class="notice-banner notice-banner-danger" role="alert"><span class="notice-dot bg-red-500"></span><span>{{ submitMessage }}</span></section>
        <div class="flex flex-wrap items-center gap-3"><button class="btn-primary" type="submit" :disabled="submitting"><Loader2 v-if="submitting" class="h-4 w-4 animate-spin" /><span>{{ submitting ? '提交中...' : '发布并提交审核' }}</span></button><router-link class="btn-default" to="/seller/products">取消</router-link></div>
      </form>
    </template>
  </main>
</template>
