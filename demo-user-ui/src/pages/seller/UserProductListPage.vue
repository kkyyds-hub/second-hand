<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft, ChevronRight, Loader2, Package, PackagePlus, PackageSearch } from 'lucide-vue-next'
import SellerProductActionDialog from '@/pages/seller/components/SellerProductActionDialog.vue'
import {
  createEmptyUserProductPage,
  deleteUserProduct,
  getUserProductList,
  getUserProductStatusActions,
  getUserProductStatusMeta,
  runUserProductStatusAction,
  type UserProductStatusAction,
  type UserProductStatusActionMeta,
  type UserProductSummary,
} from '@/api/userProducts'

type AppliedListState = {
  status: string
  page: number
  pageSize: number
}

type PendingAction =
  | { kind: 'delete'; item: UserProductSummary }
  | { kind: 'status'; item: UserProductSummary; actionMeta: UserProductStatusActionMeta }

type ListLoadOutcome = 'loaded' | 'redirected' | 'stale' | 'failed'

const route = useRoute()
const router = useRouter()
const validStatuses = new Set(['under_review', 'on_sale', 'off_shelf', 'sold'])
const validPageSizes = new Set([10, 20, 50])
const statusOptions = [
  { value: '', label: '全部' },
  { value: 'on_sale', label: '在售' },
  { value: 'under_review', label: '审核中' },
  { value: 'off_shelf', label: '已下架' },
  { value: 'sold', label: '已售出' },
]

const loading = ref(false)
const hasLoadedOnce = ref(false)
const listError = ref('')
const pageData = ref(createEmptyUserProductPage())
const requestSequence = ref(0)
const isViewActive = ref(true)
const explicitRouteReloadKey = ref('')
const runningActionKey = ref('')
const pendingAction = ref<PendingAction | null>(null)
const feedback = ref<{ tone: 'success' | 'warning' | 'error'; message: string } | null>(null)
const routeNotice = ref('')
const filterDraft = reactive({ status: '', pageSize: 10 })

const appliedState = computed(() => readAppliedState(route.query))
const list = computed<UserProductSummary[]>(() => pageData.value.list)
const totalPages = computed(() => Math.max(1, Math.ceil(pageData.value.total / appliedState.value.pageSize)))
const hasEmptyState = computed(() => !loading.value && hasLoadedOnce.value && !listError.value && list.value.length === 0)
const hasPrevPage = computed(() => appliedState.value.page > 1)
const hasNextPage = computed(() => appliedState.value.page < totalPages.value)
const isMutating = computed(() => Boolean(runningActionKey.value))
const dialogTitle = computed(() => {
  if (!pendingAction.value) return ''
  if (pendingAction.value.kind === 'delete') return '删除商品'
  return pendingAction.value.actionMeta.label === '下架' ? '下架商品' : pendingAction.value.actionMeta.label
})
const dialogDescription = computed(() => {
  if (!pendingAction.value) return ''
  const { item } = pendingAction.value
  if (pendingAction.value.kind === 'delete') return `确认删除商品「${item.title}」吗？该操作不可恢复。`
  return `确认对商品「${item.title}」执行“${pendingAction.value.actionMeta.label}”吗？`
})
const dialogConfirmLabel = computed(() => pendingAction.value?.kind === 'delete' ? '确认删除' : pendingAction.value?.actionMeta.label || '确认')

function readQueryValue(value: unknown) {
  return Array.isArray(value) ? value[0] : value
}

function readPositiveInteger(value: unknown, fallback: number) {
  const normalized = typeof value === 'string' ? value.trim() : String(value ?? '').trim()
  if (!/^\d+$/.test(normalized)) return fallback
  const parsed = Number(normalized)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : fallback
}

function readAppliedState(query: Record<string, unknown>): AppliedListState {
  const rawStatus = String(readQueryValue(query.status) ?? '').trim()
  const status = validStatuses.has(rawStatus) ? rawStatus : ''
  const page = readPositiveInteger(readQueryValue(query.page), 1)
  const pageSizeCandidate = readPositiveInteger(readQueryValue(query.pageSize), 10)
  const pageSize = validPageSizes.has(pageSizeCandidate) ? pageSizeCandidate : 10
  return { status, page, pageSize }
}

function buildQuery(state: AppliedListState) {
  const query: Record<string, string> = {}
  if (state.status) query.status = state.status
  if (state.page > 1) query.page = String(state.page)
  if (state.pageSize !== 10) query.pageSize = String(state.pageSize)
  return query
}

function stateKey(state: AppliedListState) {
  return `${state.status}:${state.page}:${state.pageSize}`
}

function isCanonicalQuery(query: Record<string, unknown>, state: AppliedListState) {
  const canonicalQuery = buildQuery(state)
  const queryEntries = Object.entries(query)
  const canonicalEntries = Object.entries(canonicalQuery)
  return queryEntries.length === canonicalEntries.length && canonicalEntries.every(([key, value]) => readQueryValue(query[key]) === value)
}

function readStatusChipClass(status: string) {
  const meta = getUserProductStatusMeta(status)
  if (meta.tone === 'accent') return 'chip chip-accent'
  if (meta.tone === 'success') return 'chip chip-success'
  if (meta.tone === 'warning') return 'chip chip-warning'
  return 'chip chip-neutral'
}

function readStatusLabel(status: string) {
  return getUserProductStatusMeta(status).label
}

function readStatusActions(status: string) {
  return getUserProductStatusActions(status)
}

function readStatusActionButtonClass(tone: UserProductStatusActionMeta['tone']) {
  return tone === 'accent' ? 'btn-primary !h-9 px-3' : 'btn-default !h-9 px-3'
}

function buildActionKey(productId: number, action: UserProductStatusAction | 'delete') {
  return `${productId}:${action}`
}

function isRunningAction(productId: number | null, action: UserProductStatusAction | 'delete') {
  return productId !== null && runningActionKey.value === buildActionKey(productId, action)
}

function actionSuccessMessage(action: UserProductStatusAction | 'delete') {
  switch (action) {
    case 'delete': return '商品已删除。'
    case 'off_shelf': return '商品已下架。'
    case 'withdraw': return '已撤回商品审核。'
    case 'resubmit':
    case 'on_shelf': return '商品已重新提交审核。'
  }
}

function actionFailureMessage(action: UserProductStatusAction | 'delete') {
  switch (action) {
    case 'delete': return '商品删除失败，请稍后重试。'
    case 'off_shelf': return '商品下架失败，请稍后重试。'
    case 'withdraw': return '撤回审核失败，请稍后重试。'
    case 'resubmit':
    case 'on_shelf': return '重新提交审核失败，请稍后重试。'
  }
}

function actionRemovesItemFromState(action: PendingAction, state: AppliedListState) {
  if (action.kind === 'delete') return true
  if (!state.status) return false

  const targetStatusByAction: Partial<Record<UserProductStatusAction, string>> = {
    off_shelf: 'off_shelf',
    withdraw: 'off_shelf',
    resubmit: 'under_review',
    on_shelf: 'under_review',
  }
  const targetStatus = targetStatusByAction[action.actionMeta.action]
  return Boolean(targetStatus && targetStatus !== state.status)
}

async function loadList(state: AppliedListState): Promise<ListLoadOutcome> {
  const sequence = ++requestSequence.value
  loading.value = true
  listError.value = ''

  try {
    const nextPageData = await getUserProductList(state)
    if (!isViewActive.value || sequence !== requestSequence.value) return 'stale'

    const lastPage = Math.max(1, Math.ceil(nextPageData.total / state.pageSize))
    if (state.page > lastPage) {
      await router.replace({ query: buildQuery({ ...state, page: lastPage }) })
      return 'redirected'
    }

    if (!isViewActive.value || sequence !== requestSequence.value || stateKey(state) !== stateKey(appliedState.value)) return 'stale'
    pageData.value = nextPageData
    return 'loaded'
  } catch {
    if (!isViewActive.value || sequence !== requestSequence.value) return 'stale'
    listError.value = '商品列表暂时无法加载，请稍后重试。'
    return 'failed'
  } finally {
    if (isViewActive.value && sequence === requestSequence.value) {
      loading.value = false
      hasLoadedOnce.value = true
    }
  }
}

async function replaceAndReloadList(targetState: AppliedListState): Promise<ListLoadOutcome> {
  const targetKey = stateKey(targetState)
  explicitRouteReloadKey.value = targetKey

  try {
    await router.replace({ query: buildQuery(targetState) })
    if (!isViewActive.value || stateKey(appliedState.value) !== targetKey) return 'stale'

    return await loadList(targetState)
  } catch {
    if (isViewActive.value) {
      listError.value = '商品列表暂时无法加载，请稍后重试。'
    }
    return 'failed'
  } finally {
    if (explicitRouteReloadKey.value === targetKey) {
      explicitRouteReloadKey.value = ''
    }
  }
}

function applyFilters() {
  const nextState = {
    status: filterDraft.status,
    page: 1,
    pageSize: filterDraft.pageSize,
  }
  void router.push({ query: buildQuery(nextState) })
}

function resetFilters() {
  void router.push({ path: '/seller/products' })
}

function changePage(page: number) {
  if (page < 1 || page > totalPages.value || page === appliedState.value.page || isMutating.value) return
  void router.push({ query: buildQuery({ ...appliedState.value, page }) })
}

function reloadList() {
  void loadList(appliedState.value)
}

function openDeleteDialog(item: UserProductSummary) {
  if (item.id !== null && !isMutating.value) {
    pendingAction.value = { kind: 'delete', item }
  }
}

function openStatusActionDialog(item: UserProductSummary, actionMeta: UserProductStatusActionMeta) {
  if (item.id !== null && !isMutating.value) {
    pendingAction.value = { kind: 'status', item, actionMeta }
  }
}

function closeActionDialog() {
  if (!isMutating.value) pendingAction.value = null
}

async function confirmAction() {
  const action = pendingAction.value
  if (!action || action.item.id === null || isMutating.value) return

  const actionName = action.kind === 'delete' ? 'delete' : action.actionMeta.action
  const successMessage = actionSuccessMessage(actionName)
  const mutationStartState = { ...appliedState.value }
  const mutationStartStateKey = stateKey(mutationStartState)
  const mutationStartPageItemCount = list.value.length

  runningActionKey.value = buildActionKey(action.item.id, actionName)
  feedback.value = null

  try {
    if (action.kind === 'delete') {
      await deleteUserProduct(action.item.id)
    } else {
      await runUserProductStatusAction(action.item.id, action.actionMeta.action)
    }

    if (!isViewActive.value || stateKey(appliedState.value) !== mutationStartStateKey) return

    const shouldShrinkPage = actionRemovesItemFromState(action, mutationStartState)
      && mutationStartPageItemCount === 1
      && mutationStartState.page > 1
    const refreshState = shouldShrinkPage
      ? { ...mutationStartState, page: mutationStartState.page - 1 }
      : mutationStartState
    const outcome = shouldShrinkPage
      ? await replaceAndReloadList(refreshState)
      : await loadList(refreshState)

    if (!isViewActive.value) return
    if (outcome === 'loaded') {
      feedback.value = { tone: 'success', message: successMessage }
    } else if (outcome === 'failed') {
      feedback.value = { tone: 'warning', message: `${successMessage.slice(0, -1)}，但列表刷新失败，请手动重新加载。` }
    }
  } catch {
    if (isViewActive.value) feedback.value = { tone: 'error', message: actionFailureMessage(actionName) }
  } finally {
    if (isViewActive.value) {
      runningActionKey.value = ''
      pendingAction.value = null
    }
  }
}

watch(
  () => route.fullPath,
  async () => {
    const hasCreatedNotice = readQueryValue(route.query.created) === '1'
    const hasDeletedNotice = readQueryValue(route.query.deleted) === '1'
    if (hasCreatedNotice) {
      routeNotice.value = '商品已创建。'
    } else if (hasDeletedNotice) {
      routeNotice.value = '商品已删除。'
    }

    const state = readAppliedState(route.query)
    if (!isCanonicalQuery(route.query, state)) {
      await router.replace({ query: buildQuery(state) })
      return
    }

    filterDraft.status = state.status
    filterDraft.pageSize = state.pageSize
    if (stateKey(state) === explicitRouteReloadKey.value) return
    await loadList(state)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  isViewActive.value = false
  requestSequence.value += 1
})
</script>

<template>
  <main class="page-body">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">卖家中心</p>
          <h1 class="page-title">我的商品</h1>
          <p class="page-desc">查看商品状态，处理需要关注的商品，并进入详情或编辑页面继续管理。</p>
        </div>
        <div class="page-actions">
          <router-link class="btn-default" to="/seller">返回工作台</router-link>
          <router-link class="btn-primary" to="/seller/products/new">
            <PackagePlus class="h-4 w-4" aria-hidden="true" />
            <span>发布闲置</span>
          </router-link>
        </div>
      </div>
    </section>

    <section
      v-if="feedback"
      class="notice-banner"
      :class="feedback.tone === 'success' ? 'notice-banner-success' : feedback.tone === 'warning' ? 'notice-banner-warning' : 'notice-banner-danger'"
    >
      <span class="notice-dot" :class="feedback.tone === 'success' ? 'bg-emerald-500' : feedback.tone === 'warning' ? 'bg-orange-500' : 'bg-red-500'"></span>
      <span class="flex-1">{{ feedback.message }}</span>
      <button class="text-[12px] font-medium" type="button" :disabled="isMutating" @click="feedback = null">关闭</button>
    </section>

    <section v-if="routeNotice" class="notice-banner notice-banner-success">
      <span class="notice-dot bg-emerald-500"></span>
      <span class="flex-1">{{ routeNotice }}</span>
      <button class="text-[12px] font-medium" type="button" @click="routeNotice = ''">关闭</button>
    </section>

    <section class="toolbar">
      <form class="w-full" @submit.prevent="applyFilters">
        <div class="flex flex-col gap-4 md:flex-row md:items-end">
          <div class="toolbar-field max-w-[280px]">
            <label class="form-label" for="status-filter">商品状态</label>
            <select id="status-filter" v-model="filterDraft.status" class="input-standard" :disabled="isMutating">
              <option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
          </div>
          <div class="w-full md:w-36">
            <label class="form-label" for="page-size-filter">每页条数</label>
            <select id="page-size-filter" v-model.number="filterDraft.pageSize" class="input-standard" :disabled="isMutating">
              <option :value="10">10</option>
              <option :value="20">20</option>
              <option :value="50">50</option>
            </select>
          </div>
          <div class="toolbar-group md:pl-2">
            <button class="btn-primary" type="submit" :disabled="isMutating">
              <Loader2 v-if="loading" class="h-4 w-4 animate-spin" aria-hidden="true" />
              <span>{{ loading ? '加载中' : '应用筛选' }}</span>
            </button>
            <button class="btn-default" type="button" :disabled="isMutating" @click="resetFilters">重置</button>
          </div>
        </div>
      </form>
    </section>

    <section v-if="listError" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <div class="flex-1">
        <p class="font-semibold">商品列表暂时无法加载</p>
        <p class="mt-1 text-[12px] leading-5">请稍后重试，已有商品信息会继续保留。</p>
        <button class="btn-default mt-3" type="button" :disabled="loading || isMutating" @click="reloadList">重新加载</button>
      </div>
    </section>

    <section class="section-panel">
      <div class="section-header">
        <div>
          <h2 class="section-heading">商品列表</h2>
          <p class="section-subtitle">共 {{ pageData.total }} 件商品，第 {{ appliedState.page }} / {{ totalPages }} 页。</p>
        </div>
      </div>
      <div class="section-body">
        <div v-if="loading && !hasLoadedOnce" class="space-y-3" aria-label="正在加载商品列表">
          <div v-for="index in 3" :key="index" class="h-36 animate-pulse rounded-lg bg-gray-100"></div>
        </div>
        <div v-else-if="hasEmptyState" class="empty-state min-h-[320px]">
          <PackageSearch class="empty-state-icon" aria-hidden="true" />
          <p class="empty-state-title">当前条件下暂无商品</p>
          <p class="empty-state-text">可以切换状态筛选，或发布一件新的闲置商品。</p>
          <router-link class="btn-primary mt-5" to="/seller/products/new">发布闲置</router-link>
        </div>
        <div v-else class="space-y-3">
          <article v-for="item in list" :key="item.id ?? item.title" class="rounded-lg border border-gray-200/80 bg-white p-4 sm:p-5">
            <div class="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
              <div class="flex min-w-0 gap-4">
                <div class="icon-badge mt-0.5 shrink-0" aria-hidden="true">
                  <Package class="h-5 w-5" />
                </div>
                <div class="min-w-0">
                  <div class="flex flex-wrap items-center gap-2">
                    <h3 class="break-words text-[16px] font-semibold text-gray-950">{{ item.title }}</h3>
                    <span :class="readStatusChipClass(item.status)">{{ readStatusLabel(item.status) }}</span>
                  </div>
                  <p class="mt-2 text-[13px] text-gray-600">{{ item.category || '未分类' }}<span class="mx-2 text-gray-300">·</span>更新时间 {{ item.updateTime || '-' }}</p>
                  <div class="mt-3 flex flex-wrap items-baseline gap-x-4 gap-y-2">
                    <p class="font-numeric text-[20px] font-bold text-gray-950">¥ {{ item.price.toFixed(2) }}</p>
                    <p class="text-[13px] text-gray-500">浏览 {{ item.viewCount }}</p>
                  </div>
                  <p v-if="item.reason" class="mt-3 text-[13px] leading-5 text-orange-700">状态备注：{{ item.reason }}</p>
                </div>
              </div>
              <div class="flex shrink-0 flex-wrap gap-2 border-t border-gray-100 pt-4 lg:max-w-[360px] lg:justify-end lg:border-t-0 lg:pt-0">
                <router-link v-if="item.id !== null" class="btn-default !h-9 px-3" :to="`/seller/products/${item.id}`">查看详情</router-link>
                <router-link v-if="item.id !== null && item.status !== 'sold'" class="btn-default !h-9 px-3" :to="`/seller/products/${item.id}/edit`">编辑</router-link>
                <button
                  v-for="actionMeta in readStatusActions(item.status)"
                  :key="`${item.id}-${actionMeta.action}`"
                  :class="readStatusActionButtonClass(actionMeta.tone)"
                  type="button"
                  :disabled="item.id === null || isMutating"
                  @click="openStatusActionDialog(item, actionMeta)"
                >
                  <Loader2 v-if="isRunningAction(item.id, actionMeta.action)" class="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
                  <span>{{ actionMeta.label }}</span>
                </button>
                <button v-if="item.id !== null" class="btn-danger !h-9 px-3" type="button" :disabled="isMutating" @click="openDeleteDialog(item)">
                  <Loader2 v-if="isRunningAction(item.id, 'delete')" class="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
                  <span>删除</span>
                </button>
              </div>
            </div>
          </article>

          <div v-if="list.length" class="pagination-bar">
            <span class="chip chip-neutral font-numeric">第 {{ appliedState.page }} / {{ totalPages }} 页</span>
            <div class="flex flex-wrap gap-2">
              <button class="btn-default !h-9 px-3.5" type="button" :disabled="!hasPrevPage || isMutating" @click="changePage(appliedState.page - 1)">
                <ChevronLeft class="h-4 w-4" aria-hidden="true" />
                <span>上一页</span>
              </button>
              <button class="btn-default !h-9 px-3.5" type="button" :disabled="!hasNextPage || isMutating" @click="changePage(appliedState.page + 1)">
                <span>下一页</span>
                <ChevronRight class="h-4 w-4" aria-hidden="true" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <SellerProductActionDialog
      :open="Boolean(pendingAction)"
      :title="dialogTitle"
      :description="dialogDescription"
      :confirm-label="dialogConfirmLabel"
      :loading="isMutating"
      :destructive="pendingAction?.kind === 'delete'"
      @close="closeActionDialog"
      @confirm="confirmAction"
    />
  </main>
</template>
