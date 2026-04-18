<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft, Loader2, PencilLine, Trash2 } from 'lucide-vue-next'
import {
  createEmptyUserProductDetail,
  deleteUserProduct,
  getUserProductDetail,
  getUserProductStatusActions,
  getUserProductStatusMeta,
  runUserProductStatusAction,
  type UserProductStatusActionMeta,
  type UserProductDetail,
} from '@/api/userProducts'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const errorMessage = ref('')
const detail = ref<UserProductDetail>(createEmptyUserProductDetail())
const hasLoadedOnce = ref(false)
const runningAction = ref<'idle' | 'status' | 'delete'>('idle')
const actionStatus = ref<'idle' | 'success' | 'error'>('idle')
const actionMessage = ref('')

const productId = computed(() => {
  const raw = route.params.productId
  if (typeof raw === 'string' && /^\d+$/.test(raw)) {
    return Number(raw)
  }

  return null
})

const hasCreatedNotice = computed(() => route.query.created === '1')
const hasEditedNotice = computed(() => route.query.edited === '1')
const hasQueryNotice = computed(() => hasCreatedNotice.value || hasEditedNotice.value)

const queryNoticeText = computed(() => {
  if (hasCreatedNotice.value) {
    return '商品创建成功，当前为最新详情。'
  }

  return '商品编辑成功，当前为最新详情。'
})

const hasActionNotice = computed(() => actionStatus.value !== 'idle')
const statusMeta = computed(() => getUserProductStatusMeta(detail.value.status))
const statusActions = computed(() => getUserProductStatusActions(detail.value.status))
const hasImages = computed(() => detail.value.imageUrls.length > 0)
const canEdit = computed(() => productId.value !== null && detail.value.status !== 'sold' && runningAction.value === 'idle')

function readErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return '我的商品详情加载失败，请稍后重试。'
}

function readDeleteErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return '商品删除失败，请稍后重试。'
}

function readStatusActionErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return '状态操作失败，请稍后重试。'
}

function readStatusActionButtonClass(tone: UserProductStatusActionMeta['tone']) {
  if (tone === 'accent') {
    return 'btn-primary !h-9 px-3'
  }

  return 'btn-default !h-9 px-3'
}

function dismissQueryNotice() {
  if (!hasQueryNotice.value) {
    return
  }

  const nextQuery = { ...route.query }
  delete nextQuery.created
  delete nextQuery.edited
  router.replace({ query: nextQuery })
}

function dismissActionNotice() {
  if (runningAction.value === 'idle' && hasActionNotice.value) {
    actionStatus.value = 'idle'
    actionMessage.value = ''
  }
}

async function loadDetail() {
  if (loading.value || productId.value === null) {
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''
    detail.value = await getUserProductDetail(productId.value)
  } catch (error: unknown) {
    errorMessage.value = readErrorMessage(error)
  } finally {
    loading.value = false
    hasLoadedOnce.value = true
  }
}

async function handleDelete() {
  if (productId.value === null || runningAction.value !== 'idle') {
    return
  }

  const confirmed = window.confirm(`确认删除商品「${detail.value.title || '未命名商品'}」吗？该操作不可恢复。`)
  if (!confirmed) {
    return
  }

  try {
    runningAction.value = 'delete'
    actionStatus.value = 'idle'
    actionMessage.value = ''

    await deleteUserProduct(productId.value)

    await router.replace({
      name: 'SellerProductList',
      query: { deleted: '1' },
    })
  } catch (error: unknown) {
    actionStatus.value = 'error'
    actionMessage.value = readDeleteErrorMessage(error)
  } finally {
    runningAction.value = 'idle'
  }
}

async function handleStatusAction(actionMeta: UserProductStatusActionMeta) {
  if (productId.value === null || runningAction.value !== 'idle') {
    return
  }

  try {
    runningAction.value = 'status'
    actionStatus.value = 'idle'
    actionMessage.value = ''

    const responseMessage = await runUserProductStatusAction(productId.value, actionMeta.action)
    await loadDetail()

    actionStatus.value = 'success'
    actionMessage.value = responseMessage
  } catch (error: unknown) {
    actionStatus.value = 'error'
    actionMessage.value = readStatusActionErrorMessage(error)
  } finally {
    runningAction.value = 'idle'
  }
}

onMounted(() => {
  loadDetail()
})

watch(
  () => productId.value,
  () => {
    detail.value = createEmptyUserProductDetail()
    hasLoadedOnce.value = false
    loadDetail()
  },
)
</script>

<template>
  <div class="page-body">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">卖家</p>
          <h1 class="page-title">商品详情</h1>
          <p class="page-desc">Day04 第二包详情页：补齐编辑、删除与状态流转动作，并显式保留 on-shelf 兼容提审语义。</p>
        </div>
        <div class="page-actions">
          <router-link class="btn-default" to="/seller/products">
            <ChevronLeft class="h-4 w-4" />
            <span>返回商品列表</span>
          </router-link>
          <router-link v-if="productId !== null" class="btn-default" :class="{ 'pointer-events-none opacity-50': !canEdit }" :to="`/seller/products/${productId}/edit`">
            <PencilLine class="h-4 w-4" />
            <span>{{ canEdit ? '编辑商品' : '已售商品不可编辑' }}</span>
          </router-link>
          <button class="btn-danger" type="button" :disabled="runningAction !== 'idle' || productId === null" @click="handleDelete">
            <Loader2 v-if="runningAction === 'delete'" class="h-4 w-4 animate-spin" />
            <Trash2 v-else class="h-4 w-4" />
            <span>{{ runningAction === 'delete' ? '删除中' : '删除商品' }}</span>
          </button>
        </div>
      </div>
    </section>

    <section v-if="hasQueryNotice" class="notice-banner notice-banner-success">
      <span class="notice-dot bg-emerald-500"></span>
      <span class="flex-1">{{ queryNoticeText }}</span>
      <button class="text-[12px] font-medium" type="button" @click="dismissQueryNotice">关闭</button>
    </section>

    <section
      v-if="hasActionNotice"
      class="notice-banner"
      :class="actionStatus === 'success' ? 'notice-banner-success' : 'notice-banner-danger'"
    >
      <span class="notice-dot" :class="actionStatus === 'success' ? 'bg-emerald-500' : 'bg-red-500'"></span>
      <span class="flex-1">{{ actionMessage }}</span>
      <button class="text-[12px] font-medium" type="button" :disabled="runningAction !== 'idle'" @click="dismissActionNotice">关闭</button>
    </section>

    <section v-if="productId === null" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <span>商品 ID 无效，请从商品列表重新进入详情页。</span>
    </section>

    <section v-else-if="errorMessage" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <div class="flex-1">
        <p class="font-semibold">详情加载失败</p>
        <p class="mt-1 text-[12px] leading-5">{{ errorMessage }}</p>
        <button class="btn-default mt-3" type="button" :disabled="loading" @click="loadDetail">重新加载</button>
      </div>
    </section>

    <section v-else-if="loading || !hasLoadedOnce" class="section-panel">
      <div class="section-body">
        <div class="empty-state min-h-[340px]">
          <Loader2 class="empty-state-icon animate-spin text-blue-500" />
          <p class="empty-state-title">正在加载商品详情</p>
        </div>
      </div>
    </section>

    <template v-else>
      <section class="section-panel">
        <div class="section-header">
          <div>
            <h2 class="section-heading">状态操作</h2>
            <p class="section-subtitle">当前状态：{{ statusMeta.label }}。on-shelf 为兼容提审入口，不代表直接在售。</p>
          </div>
        </div>
        <div class="section-body pt-2">
          <div v-if="statusActions.length === 0" class="notice-banner notice-banner-info">
            <span class="notice-dot bg-blue-500"></span>
            <span>当前状态没有可执行的状态流转操作。</span>
          </div>
          <div v-else class="flex flex-wrap items-center gap-2">
            <button
              v-for="actionMeta in statusActions"
              :key="actionMeta.action"
              :class="readStatusActionButtonClass(actionMeta.tone)"
              type="button"
              :disabled="runningAction !== 'idle'"
              @click="handleStatusAction(actionMeta)"
            >
              <Loader2 v-if="runningAction === 'status'" class="h-3.5 w-3.5 animate-spin" />
              <span>{{ actionMeta.label }}</span>
            </button>
          </div>
        </div>
      </section>

      <section class="section-panel">
        <div class="section-body">
          <div class="grid gap-8 lg:grid-cols-[420px_minmax(0,1fr)]">
            <div class="soft-panel overflow-hidden">
              <img v-if="hasImages" :src="detail.imageUrls[0]" :alt="detail.title" class="h-full max-h-[420px] w-full object-cover" />
              <div v-else class="flex h-[320px] items-center justify-center text-[12px] text-gray-400">暂无商品主图</div>
            </div>
            <div class="flex flex-col gap-5">
              <div class="border-b border-gray-100 pb-4">
                <div class="flex flex-wrap items-center gap-2">
                  <h2 class="text-[24px] font-bold tracking-tight text-gray-900">{{ detail.title || '未命名商品' }}</h2>
                  <span
                    :class="
                      statusMeta.tone === 'accent'
                        ? 'chip chip-accent'
                        : statusMeta.tone === 'success'
                          ? 'chip chip-success'
                          : statusMeta.tone === 'warning'
                            ? 'chip chip-warning'
                            : 'chip chip-neutral'
                    "
                  >
                    {{ statusMeta.label }}
                  </span>
                  <span v-if="detail.category" class="chip chip-neutral">{{ detail.category }}</span>
                </div>
                <p class="mt-4 font-numeric text-[30px] font-bold text-gray-900">¥{{ detail.price.toFixed(2) }}</p>
              </div>

              <div class="detail-grid">
                <div class="detail-row">
                  <span class="detail-label">商品 ID</span>
                  <span class="detail-value font-numeric">{{ detail.id ?? '-' }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">浏览量</span>
                  <span class="detail-value font-numeric">{{ detail.viewCount }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">创建时间</span>
                  <span class="detail-value font-numeric">{{ detail.createTime || '-' }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">更新时间</span>
                  <span class="detail-value font-numeric">{{ detail.updateTime || '-' }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">最近提交审核时间</span>
                  <span class="detail-value font-numeric">{{ detail.submitTime || '-' }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="section-panel">
        <div class="section-header">
          <div>
            <h2 class="section-heading">商品描述</h2>
          </div>
        </div>
        <div class="section-body">
          <p class="text-[14px] leading-7 text-gray-700">{{ detail.description || '卖家暂未填写商品描述。' }}</p>
        </div>
      </section>

      <section v-if="detail.reviewRemark || detail.reason" class="section-panel-muted">
        <div class="section-header section-header-plain">
          <div>
            <h2 class="section-heading">状态备注</h2>
          </div>
        </div>
        <div class="section-body pt-2">
          <p class="text-[13px] leading-6 text-orange-700">{{ detail.reviewRemark || detail.reason }}</p>
        </div>
      </section>

      <section v-if="hasImages" class="section-panel">
        <div class="section-header">
          <div>
            <h2 class="section-heading">商品图集</h2>
          </div>
        </div>
        <div class="section-body">
          <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            <figure v-for="(image, index) in detail.imageUrls" :key="`${image}-${index}`" class="overflow-hidden rounded-2xl border border-gray-200/80 bg-white">
              <img :src="image" :alt="`商品图片 ${index + 1}`" class="aspect-square w-full object-cover" />
            </figure>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>
