<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft, Loader2, PencilLine, Trash2 } from 'lucide-vue-next'
import ProductImageGallery from '@/pages/market/components/ProductImageGallery.vue'
import SellerProductActionDialog from '@/pages/seller/components/SellerProductActionDialog.vue'
import {
  createEmptyUserProductDetail,
  deleteUserProduct,
  getUserProductDetail,
  getUserProductStatusActions,
  getUserProductStatusMeta,
  runUserProductStatusAction,
  type UserProductDetail,
  type UserProductStatusActionMeta,
} from '@/api/userProducts'
import { isSellerUser, readCurrentUser } from '@/utils/request'

type PendingAction = { kind: 'status'; meta: UserProductStatusActionMeta } | { kind: 'delete' }
type Feedback = { tone: 'success' | 'warning' | 'error'; message: string }

const route = useRoute()
const router = useRouter()
const sellerEnabled = computed(() => isSellerUser(readCurrentUser()))
const detail = ref<UserProductDetail>(createEmptyUserProductDetail())
const loading = ref(false)
const hasLoadedOnce = ref(false)
const loadError = ref('')
const pendingAction = ref<PendingAction | null>(null)
const runningAction = ref(false)
const feedback = ref<Feedback | null>(null)
const navigationNotice = ref('')
let requestSequence = 0
let active = true
let noticeKey = ''

const productId = computed(() => {
  const text = typeof route.params.productId === 'string' ? route.params.productId.trim() : ''
  if (!/^\d+$/.test(text)) return null
  const id = Number(text)
  return Number.isSafeInteger(id) && id > 0 ? id : null
})
const statusMeta = computed(() => getUserProductStatusMeta(detail.value.status))
const statusActions = computed(() => getUserProductStatusActions(detail.value.status))
const canEdit = computed(() => ['on_sale', 'under_review', 'off_shelf'].includes(detail.value.status))
const canDelete = computed(() => ['under_review', 'off_shelf'].includes(detail.value.status))
const dialogTitle = computed(() => pendingAction.value?.kind === 'delete' ? '删除商品' : pendingAction.value?.meta.label || '')
const dialogDescription = computed(() => pendingAction.value?.kind === 'delete'
  ? `确认删除商品「${detail.value.title || '未命名商品'}」吗？该操作不可恢复。`
  : `确认对商品「${detail.value.title || '未命名商品'}」执行“${pendingAction.value?.meta.label || ''}”吗？`)
const dialogConfirmLabel = computed(() => pendingAction.value?.kind === 'delete' ? '确认删除' : pendingAction.value?.meta.label || '')

function readError(error: unknown, fallback: string) { return error instanceof Error && error.message.trim() ? error.message : fallback }
function statusChipClass() {
  if (statusMeta.value.tone === 'accent') return 'chip chip-accent'
  if (statusMeta.value.tone === 'success') return 'chip chip-success'
  if (statusMeta.value.tone === 'warning') return 'chip chip-warning'
  return 'chip chip-neutral'
}
function actionButtonClass(meta: UserProductStatusActionMeta) { return meta.tone === 'accent' ? 'btn-primary !h-9 px-3' : 'btn-default !h-9 px-3' }
function successMessage(action: UserProductStatusActionMeta['action']) {
  if (action === 'off_shelf') return '商品已下架。'
  if (action === 'withdraw') return '已撤回商品审核。'
  return '商品已重新提交审核。'
}
function failureMessage(action: UserProductStatusActionMeta['action']) {
  if (action === 'off_shelf') return '商品下架失败，请稍后重试。'
  if (action === 'withdraw') return '撤回审核失败，请稍后重试。'
  return '重新提交审核失败，请稍后重试。'
}

async function loadDetail(options?: { preserveDetail?: boolean; suppressError?: boolean }) {
  const sequence = ++requestSequence
  const id = productId.value
  if (!options?.preserveDetail) detail.value = createEmptyUserProductDetail()
  loadError.value = ''
  if (!id) { loading.value = false; hasLoadedOnce.value = true; loadError.value = '商品编号无效，请从商品管理重新进入。'; return false }
  if (!sellerEnabled.value) return false
  loading.value = true
  try {
    const nextDetail = await getUserProductDetail(id)
    if (!active || sequence !== requestSequence || productId.value !== id) return false
    detail.value = nextDetail
    return true
  } catch (error: unknown) {
    if (active && sequence === requestSequence && !options?.suppressError) {
      loadError.value = readError(error, '商品详情加载失败，请稍后重试。')
    }
    return false
  } finally {
    if (active && sequence === requestSequence) { loading.value = false; hasLoadedOnce.value = true }
  }
}

function openStatusAction(meta: UserProductStatusActionMeta) { if (!runningAction.value) pendingAction.value = { kind: 'status', meta } }
function openDeleteAction() { if (!runningAction.value) pendingAction.value = { kind: 'delete' } }
function closeDialog() { if (!runningAction.value) pendingAction.value = null }

async function confirmAction() {
  const action = pendingAction.value
  const id = productId.value
  if (!action || !id || runningAction.value) return
  try {
    runningAction.value = true
    feedback.value = null
    if (action.kind === 'delete') {
      await deleteUserProduct(id)
      if (active) await router.replace({ name: 'SellerProductList', query: { deleted: '1' } })
      return
    }
    await runUserProductStatusAction(id, action.meta.action)
    pendingAction.value = null
    const refreshed = await loadDetail({ preserveDetail: true, suppressError: true })
    if (!active) return
    feedback.value = refreshed
      ? { tone: 'success', message: successMessage(action.meta.action) }
      : { tone: 'warning', message: `${successMessage(action.meta.action).slice(0, -1)}，但最新详情加载失败，请重新加载。` }
  } catch (error: unknown) {
    if (active) feedback.value = { tone: 'error', message: action.kind === 'delete' ? readError(error, '商品删除失败，请稍后重试。') : failureMessage(action.meta.action) }
  } finally {
    if (active) runningAction.value = false
  }
}

watch(productId, () => { void loadDetail() }, { immediate: true })
watch(
  () => ({ created: route.query.created, edited: route.query.edited, path: route.path }),
  (value) => {
    const nextNotice = value.created === '1' ? '商品已发布并提交审核。' : value.edited === '1' ? '商品修改已保存并重新提交审核。' : ''
    const key = `${value.path}:${nextNotice}`
    if (!nextNotice || key === noticeKey) return
    noticeKey = key
    navigationNotice.value = nextNotice
    const query = { ...route.query }
    delete query.created
    delete query.edited
    void router.replace({ path: route.path, query })
  },
  { immediate: true },
)
onBeforeUnmount(() => { active = false; requestSequence += 1 })
</script>

<template>
  <main class="page-body">
    <section v-if="!sellerEnabled" class="section-panel"><div class="section-body space-y-4"><h1 class="page-title">当前账号尚未开通卖家功能</h1><p class="page-desc">开通卖家功能后，才能发布和管理闲置商品。</p><div class="flex flex-wrap gap-3"><router-link class="btn-primary" to="/market">返回市场</router-link><router-link class="btn-default" to="/">返回首页</router-link></div></div></section>
    <template v-else>
      <section class="page-header"><div class="page-header-main"><p class="page-kicker">卖家中心</p><h1 class="page-title">我的商品详情</h1><p class="page-desc">查看商品审核状态和管理操作。</p></div><div class="flex flex-wrap gap-2"><router-link class="btn-default" to="/seller/products"><ChevronLeft class="h-4 w-4" /><span>返回商品管理</span></router-link><router-link v-if="canEdit && productId" class="btn-default" :to="`/seller/products/${productId}/edit`"><PencilLine class="h-4 w-4" /><span>编辑商品</span></router-link><button v-if="canDelete" class="btn-danger" type="button" :disabled="runningAction" @click="openDeleteAction"><Trash2 class="h-4 w-4" /><span>删除商品</span></button></div></section>
      <section v-if="navigationNotice" class="notice-banner notice-banner-success" role="status"><span class="notice-dot bg-emerald-500"></span><span>{{ navigationNotice }}</span></section>
      <section v-if="feedback" class="notice-banner" :class="feedback.tone === 'success' ? 'notice-banner-success' : feedback.tone === 'warning' ? 'notice-banner-warning' : 'notice-banner-danger'" :role="feedback.tone === 'error' ? 'alert' : 'status'"><span class="notice-dot" :class="feedback.tone === 'success' ? 'bg-emerald-500' : feedback.tone === 'warning' ? 'bg-orange-500' : 'bg-red-500'"></span><div class="flex flex-1 flex-wrap items-center justify-between gap-3"><span>{{ feedback.message }}</span><button v-if="feedback.tone === 'warning'" class="btn-default !h-8 px-3" type="button" :disabled="loading" @click="loadDetail">重新加载</button></div></section>
      <section v-if="loadError" class="notice-banner notice-banner-danger" role="alert"><span class="notice-dot bg-red-500"></span><div class="flex-1"><p>{{ loadError }}</p><button class="btn-default mt-3" type="button" :disabled="loading" @click="loadDetail">重新加载</button></div></section>
      <section v-else-if="loading || !hasLoadedOnce" class="section-panel"><div class="section-body grid min-h-[420px] gap-6 lg:grid-cols-[minmax(0,420px)_minmax(0,1fr)]"><div class="animate-pulse rounded-lg bg-gray-100"></div><div class="space-y-4"><div class="h-6 w-24 animate-pulse rounded bg-gray-100"></div><div class="h-10 w-3/4 animate-pulse rounded bg-gray-100"></div><div class="h-8 w-36 animate-pulse rounded bg-gray-100"></div></div></div></section>
      <template v-else>
        <section class="section-panel"><div class="section-body"><div class="grid gap-8 lg:grid-cols-[minmax(0,420px)_minmax(0,1fr)]"><ProductImageGallery :product-title="detail.title" :image-urls="detail.imageUrls" /><div class="min-w-0 space-y-5"><div class="space-y-3 border-b border-gray-100 pb-5"><div class="flex flex-wrap items-center gap-2"><span :class="statusChipClass()">{{ statusMeta.label }}</span><span v-if="detail.category" class="chip chip-neutral break-all">{{ detail.category }}</span></div><h2 class="break-words text-[26px] font-bold text-gray-900">{{ detail.title || '未命名商品' }}</h2><p class="font-numeric text-[30px] font-bold text-gray-900">¥{{ detail.price.toFixed(2) }}</p></div><div v-if="statusActions.length" class="flex flex-wrap gap-2"><button v-for="meta in statusActions" :key="meta.action" :class="actionButtonClass(meta)" type="button" :disabled="runningAction" @click="openStatusAction(meta)"><Loader2 v-if="runningAction" class="h-4 w-4 animate-spin" /><span>{{ meta.label }}</span></button></div><p v-else-if="detail.status === 'on_sale'" class="text-[13px] text-gray-500">在售商品需先下架后才能删除</p><div class="detail-grid"><div class="detail-row"><span class="detail-label">商品编号</span><span class="detail-value font-numeric">{{ detail.id ?? '-' }}</span></div><div class="detail-row"><span class="detail-label">创建时间</span><span class="detail-value font-numeric">{{ detail.createTime || '-' }}</span></div><div class="detail-row"><span class="detail-label">更新时间</span><span class="detail-value font-numeric">{{ detail.updateTime || '-' }}</span></div><div class="detail-row"><span class="detail-label">最近提交审核时间</span><span class="detail-value font-numeric">{{ detail.submitTime || '-' }}</span></div></div></div></div></div></section>
        <section v-if="detail.reviewRemark || detail.reason" class="section-panel-muted"><div class="section-body"><h2 class="section-heading">状态备注</h2><p class="mt-2 whitespace-pre-wrap break-words text-[13px] leading-6 text-orange-800">{{ detail.reviewRemark || detail.reason }}</p></div></section>
        <section class="section-panel"><div class="section-header section-header-plain"><div><h2 class="section-heading">商品描述</h2></div></div><div class="section-body pt-0"><p class="whitespace-pre-wrap break-words text-[14px] leading-7 text-gray-700">{{ detail.description || '卖家暂未填写商品描述。' }}</p></div></section>
      </template>
      <SellerProductActionDialog :open="Boolean(pendingAction)" :title="dialogTitle" :description="dialogDescription" :confirm-label="dialogConfirmLabel" :loading="runningAction" :destructive="pendingAction?.kind === 'delete'" @close="closeDialog" @confirm="confirmAction" />
    </template>
  </main>
</template>
