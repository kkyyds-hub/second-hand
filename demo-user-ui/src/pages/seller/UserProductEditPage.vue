<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Loader2 } from 'lucide-vue-next'
import { getUserProductDetail, updateUserProduct } from '@/api/userProducts'
import SellerProductForm from '@/pages/seller/components/SellerProductForm.vue'
import {
  collectUserProductValidationErrors,
  createEmptyUserProductFormModel,
  normalizeUpdateUserProductInput,
  toUserProductFormModel,
  userProductUpdateFingerprint,
  type UserProductFormField,
  type UserProductFormModel,
} from '@/pages/seller/user-product-form'
import { isSellerUser, readCurrentUser } from '@/utils/request'

const route = useRoute()
const router = useRouter()
const sellerEnabled = computed(() => isSellerUser(readCurrentUser()))
const productForm = reactive<UserProductFormModel>(createEmptyUserProductFormModel())
const loading = ref(false)
const submitting = ref(false)
const loadError = ref('')
const submitMessage = ref('')
const categoryLabel = ref('')
const productStatus = ref('')
const originalFingerprint = ref('')
const submitAttempted = ref(false)
const touched = reactive<Partial<Record<UserProductFormField, boolean>>>({})
let requestSequence = 0
let active = true

const productId = computed(() => {
  const value = route.params.productId
  const text = typeof value === 'string' ? value.trim() : ''
  if (!/^\d+$/.test(text)) return null
  const id = Number(text)
  return Number.isSafeInteger(id) && id > 0 ? id : null
})
const validationErrors = computed(() => collectUserProductValidationErrors(productForm, { includeCategory: false }))
const visibleErrors = computed(() => Object.fromEntries(Object.entries(validationErrors.value).filter(([field]) => submitAttempted.value || touched[field as UserProductFormField])) as Partial<Record<UserProductFormField, string>>)
const canEdit = computed(() => ['on_sale', 'under_review', 'off_shelf'].includes(productStatus.value))

function clearForm() {
  Object.assign(productForm, createEmptyUserProductFormModel())
  categoryLabel.value = ''
  productStatus.value = ''
  originalFingerprint.value = ''
  submitAttempted.value = false
  Object.keys(touched).forEach((field) => delete touched[field as UserProductFormField])
}
function readError(error: unknown, fallback: string) { return error instanceof Error && error.message.trim() ? error.message : fallback }
function markTouched(field: UserProductFormField) { touched[field] = true }
function clearSubmitMessage() { if (!submitting.value) submitMessage.value = '' }
function focusFirstError() {
  const ids: Record<UserProductFormField, string> = { title: 'seller-product-title', price: 'seller-product-price', category: 'seller-product-category', description: 'seller-product-description', imageUrls: 'seller-product-image-0' }
  const field = Object.keys(validationErrors.value)[0] as UserProductFormField | undefined
  if (field) document.getElementById(ids[field])?.focus()
}

async function loadProduct() {
  const sequence = ++requestSequence
  const id = productId.value
  clearForm()
  submitMessage.value = ''
  loadError.value = ''
  if (!id) { loading.value = false; loadError.value = '商品编号无效，请从商品管理重新进入。'; return }
  if (!sellerEnabled.value) return
  loading.value = true
  try {
    const detail = await getUserProductDetail(id)
    if (!active || sequence !== requestSequence || productId.value !== id) return
    Object.assign(productForm, toUserProductFormModel(detail))
    categoryLabel.value = detail.category
    productStatus.value = detail.status
    originalFingerprint.value = userProductUpdateFingerprint(productForm)
  } catch (error: unknown) {
    if (active && sequence === requestSequence) loadError.value = readError(error, '商品详情加载失败，请稍后重试。')
  } finally {
    if (active && sequence === requestSequence) loading.value = false
  }
}

async function submitEditForm() {
  if (submitting.value || loading.value || !canEdit.value) return
  const submittingProductId = productId.value
  if (!submittingProductId) return
  submitAttempted.value = true
  if (Object.keys(validationErrors.value).length) { focusFirstError(); return }
  if (userProductUpdateFingerprint(productForm) === originalFingerprint.value) { submitMessage.value = '商品信息尚未修改'; return }
  const submittingRequestSequence = requestSequence
  const isSubmittingProductCurrent = () => active
    && productId.value === submittingProductId
    && requestSequence === submittingRequestSequence
    && route.name === 'SellerProductEdit'
  try {
    submitting.value = true
    submitMessage.value = ''
    await updateUserProduct(submittingProductId, normalizeUpdateUserProductInput(productForm))
    if (!isSubmittingProductCurrent()) return
    await router.replace({ name: 'SellerProductDetail', params: { productId: submittingProductId }, query: { edited: '1' } })
  } catch (error: unknown) {
    if (isSubmittingProductCurrent()) submitMessage.value = readError(error, '保存失败，请稍后重试。')
  } finally {
    if (active) submitting.value = false
  }
}

watch(productId, () => { void loadProduct() }, { immediate: true })
onBeforeUnmount(() => { active = false; requestSequence += 1 })
</script>

<template>
  <main class="page-body">
    <section v-if="!sellerEnabled" class="section-panel"><div class="section-body space-y-4"><h1 class="page-title">当前账号尚未开通卖家功能</h1><p class="page-desc">开通卖家功能后，才能发布和管理闲置商品。</p><div class="flex flex-wrap gap-3"><router-link class="btn-primary" to="/market">返回市场</router-link><router-link class="btn-default" to="/">返回首页</router-link></div></div></section>
    <template v-else>
      <section class="page-header"><div class="page-header-main"><p class="page-kicker">卖家中心</p><h1 class="page-title">编辑商品</h1><p class="page-desc">保存修改后，商品将重新进入审核流程。</p></div><router-link class="btn-default" :to="productId ? `/seller/products/${productId}` : '/seller/products'"><ArrowLeft class="h-4 w-4" /><span>返回商品详情</span></router-link></section>
      <section v-if="loadError" class="notice-banner notice-banner-danger" role="alert"><span class="notice-dot bg-red-500"></span><div class="flex-1"><p>{{ loadError }}</p><div class="mt-3 flex gap-2"><button class="btn-default" type="button" :disabled="loading" @click="loadProduct">重新加载</button><router-link class="btn-default" to="/seller/products">返回商品管理</router-link></div></div></section>
      <section v-else-if="loading" class="section-panel"><div class="section-body flex min-h-[300px] items-center justify-center"><Loader2 class="h-5 w-5 animate-spin text-gray-400" /><span class="ml-3 text-[13px] text-gray-500">正在加载商品信息...</span></div></section>
      <section v-else-if="productStatus === 'sold'" class="section-panel"><div class="section-body space-y-4"><h2 class="section-heading">该商品已售出，不能继续编辑。</h2><div class="flex flex-wrap gap-3"><router-link class="btn-primary" :to="`/seller/products/${productId}`">返回详情</router-link><router-link class="btn-default" to="/seller/products">返回商品管理</router-link></div></div></section>
      <section v-else-if="!canEdit" class="section-panel"><div class="section-body space-y-4"><h2 class="section-heading">当前商品暂不能编辑。</h2><div class="flex flex-wrap gap-3"><router-link class="btn-primary" :to="productId ? `/seller/products/${productId}` : '/seller/products'">返回详情</router-link><router-link class="btn-default" to="/seller/products">返回商品管理</router-link></div></div></section>
      <form v-else class="space-y-6" @submit.prevent="submitEditForm"><SellerProductForm :model="productForm" mode="edit" :category-label="categoryLabel" :disabled="submitting" :errors="visibleErrors" @blur="markTouched" @change="clearSubmitMessage" /><section v-if="submitMessage" class="notice-banner" :class="submitMessage === '商品信息尚未修改' ? 'notice-banner-warning' : 'notice-banner-danger'" role="alert"><span class="notice-dot" :class="submitMessage === '商品信息尚未修改' ? 'bg-orange-500' : 'bg-red-500'"></span><span>{{ submitMessage }}</span></section><div class="flex flex-wrap gap-3"><button class="btn-primary" type="submit" :disabled="submitting"><Loader2 v-if="submitting" class="h-4 w-4 animate-spin" /><span>{{ submitting ? '保存中...' : '保存并重新提交审核' }}</span></button><router-link class="btn-default" :to="`/seller/products/${productId}`">取消</router-link></div></form>
    </template>
  </main>
</template>
