<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft, ChevronRight, Loader2, PackageSearch, Plus } from 'lucide-vue-next'
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

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const hasLoadedOnce = ref(false)
const errorMessage = ref('')
const pageData = ref(createEmptyUserProductPage())
const runningActionKey = ref('')
const actionStatus = ref<'idle' | 'success' | 'error'>('idle')
const actionMessage = ref('')

const filters = reactive({
  status: '',
  pageSize: 10,
})

const pagination = reactive({
  page: 1,
})

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'under_review', label: '审核中' },
  { value: 'on_sale', label: '在售' },
  { value: 'off_shelf', label: '已下架' },
  { value: 'sold', label: '已售出' },
  { value: 'rejected', label: '审核驳回' },
]

const list = computed<UserProductSummary[]>(() => pageData.value.list)
const totalPages = computed(() => Math.max(1, Math.ceil(pageData.value.total / pageData.value.pageSize)))
const hasEmptyState = computed(() => !loading.value && hasLoadedOnce.value && !errorMessage.value && list.value.length === 0)
const hasPrevPage = computed(() => pagination.page > 1)
const hasNextPage = computed(() => pagination.page < totalPages.value)

const hasCreatedNotice = computed(() => route.query.created === '1')
const hasDeletedNotice = computed(() => route.query.deleted === '1')
const hasQueryNotice = computed(() => hasCreatedNotice.value || hasDeletedNotice.value)

const queryNoticeText = computed(() => {
  if (hasCreatedNotice.value) {
    return '商品创建成功，列表已刷新。'
  }

  return '商品删除成功，列表已刷新。'
})

const hasActionNotice = computed(() => actionStatus.value !== 'idle')

function dismissQueryNotice() {
  if (!hasQueryNotice.value) {
    return
  }

  const nextQuery = { ...route.query }
  delete nextQuery.created
  delete nextQuery.deleted
  router.replace({ query: nextQuery })
}

function dismissActionNotice() {
  if (hasActionNotice.value && !runningActionKey.value) {
    actionStatus.value = 'idle'
    actionMessage.value = ''
  }
}

function readErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return '我的商品列表加载失败，请稍后重试。'
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

function buildActionKey(productId: number, action: UserProductStatusAction | 'delete') {
  return `${productId}:${action}`
}

function isRunningAction(productId: number | null, action: UserProductStatusAction | 'delete') {
  return productId !== null && runningActionKey.value === buildActionKey(productId, action)
}

function readStatusActions(status: string): UserProductStatusActionMeta[] {
  return getUserProductStatusActions(status)
}

function readFirstStatusActionDescription(status: string) {
  const actions = readStatusActions(status)
  const first = actions[0]
  return first ? first.description : ''
}

function readStatusChipClass(status: string) {
  const meta = getUserProductStatusMeta(status)

  if (meta.tone === 'accent') {
    return 'chip chip-accent'
  }

  if (meta.tone === 'success') {
    return 'chip chip-success'
  }

  if (meta.tone === 'warning') {
    return 'chip chip-warning'
  }

  return 'chip chip-neutral'
}

function readStatusLabel(status: string) {
  return getUserProductStatusMeta(status).label
}

function readStatusActionButtonClass(tone: UserProductStatusActionMeta['tone']) {
  if (tone === 'accent') {
    return 'btn-primary !h-9 px-3'
  }

  if (tone === 'warning') {
    return 'btn-default !h-9 px-3'
  }

  return 'btn-default !h-9 px-3'
}

async function loadList(options?: { throwOnError?: boolean }) {
  if (loading.value) {
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''

    const nextPageData = await getUserProductList({
      status: filters.status || undefined,
      page: pagination.page,
      pageSize: filters.pageSize,
    })

    pageData.value = nextPageData
    pagination.page = nextPageData.page
  } catch (error: unknown) {
    errorMessage.value = readErrorMessage(error)
    if (options?.throwOnError) {
      throw error
    }
  } finally {
    loading.value = false
    hasLoadedOnce.value = true
  }
}

function reloadList() {
  return loadList()
}

function submitFilters() {
  pagination.page = 1
  loadList()
}

function resetFilters() {
  filters.status = ''
  filters.pageSize = 10
  pagination.page = 1
  loadList()
}

function changePage(nextPage: number) {
  if (nextPage < 1 || nextPage > totalPages.value || nextPage === pagination.page) {
    return
  }

  pagination.page = nextPage
  loadList()
}

function clearActionNoticeWhenIdle() {
  if (!runningActionKey.value && hasActionNotice.value) {
    actionStatus.value = 'idle'
    actionMessage.value = ''
  }
}

async function handleDelete(item: UserProductSummary) {
  if (item.id === null || runningActionKey.value || loading.value) {
    return
  }

  const confirmed = window.confirm(`确认删除商品「${item.title}」吗？该操作不可恢复。`)
  if (!confirmed) {
    return
  }

  try {
    runningActionKey.value = buildActionKey(item.id, 'delete')
    actionStatus.value = 'idle'
    actionMessage.value = ''

    const responseMessage = await deleteUserProduct(item.id)
    await loadList({ throwOnError: true })

    actionStatus.value = 'success'
    actionMessage.value = responseMessage || '删除成功'
  } catch (error: unknown) {
    actionStatus.value = 'error'
    actionMessage.value = readDeleteErrorMessage(error)
  } finally {
    runningActionKey.value = ''
  }
}

async function handleStatusAction(item: UserProductSummary, actionMeta: UserProductStatusActionMeta) {
  if (item.id === null || runningActionKey.value || loading.value) {
    return
  }

  try {
    runningActionKey.value = buildActionKey(item.id, actionMeta.action)
    actionStatus.value = 'idle'
    actionMessage.value = ''

    const responseMessage = await runUserProductStatusAction(item.id, actionMeta.action)
    await loadList({ throwOnError: true })

    actionStatus.value = 'success'
    actionMessage.value = responseMessage
  } catch (error: unknown) {
    actionStatus.value = 'error'
    actionMessage.value = readStatusActionErrorMessage(error)
  } finally {
    runningActionKey.value = ''
  }
}

onMounted(() => {
  loadList()
})
</script>

<template>
  <div class="page-body">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">卖家</p>
          <h1 class="page-title">我的商品管理</h1>
          <p class="page-desc">Day04 第二包：列表页统一承接 create/edit/delete 与状态流转，详情页承接字段确认与二次操作。</p>
        </div>
        <div class="page-actions">
          <router-link class="btn-default" to="/seller">
            <ChevronLeft class="h-4 w-4" />
            <span>返回工作台</span>
          </router-link>
          <router-link class="btn-primary" to="/seller/products/new">
            <Plus class="h-4 w-4" />
            <span>创建商品</span>
          </router-link>
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
      <button class="text-[12px] font-medium" type="button" :disabled="Boolean(runningActionKey)" @click="dismissActionNotice">关闭</button>
    </section>

    <section class="toolbar">
      <form class="w-full" @submit.prevent="submitFilters">
        <div class="flex flex-col gap-4 md:flex-row md:items-end">
          <div class="toolbar-field max-w-[280px]">
            <label class="form-label" for="status-filter">商品状态</label>
            <select id="status-filter" v-model="filters.status" class="input-standard" :disabled="loading">
              <option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
          </div>
          <div class="w-full md:w-36">
            <label class="form-label" for="page-size-filter">每页条数</label>
            <select id="page-size-filter" v-model.number="filters.pageSize" class="input-standard" :disabled="loading">
              <option :value="10">10</option>
              <option :value="20">20</option>
              <option :value="50">50</option>
            </select>
          </div>
          <div class="toolbar-group md:pl-2">
            <button class="btn-primary" type="submit" :disabled="loading">
              <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
              <span>{{ loading ? '筛选中' : '应用筛选' }}</span>
            </button>
            <button class="btn-default" type="button" :disabled="loading" @click="resetFilters">重置</button>
            <button class="btn-default" type="button" :disabled="loading" @click="clearActionNoticeWhenIdle">清空操作提示</button>
          </div>
        </div>
      </form>
    </section>

    <section v-if="errorMessage" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <div class="flex-1">
        <p class="font-semibold">列表加载失败</p>
        <p class="mt-1 text-[12px] leading-5">{{ errorMessage }}</p>
        <button class="btn-default mt-3" type="button" :disabled="loading" @click="reloadList">重新加载</button>
      </div>
    </section>

    <section class="section-panel">
      <div class="section-header">
        <div>
          <h2 class="section-heading">商品列表</h2>
          <p class="section-subtitle">共 {{ pageData.total }} 条，当前第 {{ pageData.page }} / {{ totalPages }} 页。</p>
        </div>
        <div class="section-actions">
          <span class="chip chip-muted font-numeric">总数 {{ pageData.total }}</span>
        </div>
      </div>
      <div class="section-body">
        <div v-if="loading && !hasLoadedOnce" class="empty-state min-h-[320px]">
          <Loader2 class="empty-state-icon animate-spin text-blue-500" />
          <p class="empty-state-title">正在加载我的商品列表</p>
        </div>
        <div v-else-if="hasEmptyState" class="empty-state min-h-[320px]">
          <PackageSearch class="empty-state-icon" />
          <p class="empty-state-title">当前条件下暂无商品</p>
          <p class="empty-state-text">你可以切换状态筛选，或直接创建一个新商品。</p>
          <div class="mt-4 flex flex-wrap items-center justify-center gap-3">
            <button class="btn-default" type="button" :disabled="loading" @click="reloadList">重新加载</button>
            <router-link class="btn-primary" to="/seller/products/new">
              <Plus class="h-4 w-4" />
              <span>创建商品</span>
            </router-link>
          </div>
        </div>
        <div v-else class="space-y-4">
          <article v-for="item in list" :key="item.id ?? item.title" class="list-card-item">
            <div class="flex flex-col gap-4">
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <h3 class="text-[16px] font-semibold text-gray-900">{{ item.title }}</h3>
                  <span :class="readStatusChipClass(item.status)">{{ readStatusLabel(item.status) }}</span>
                  <span v-if="item.category" class="chip chip-neutral">{{ item.category }}</span>
                </div>
                <p class="mt-2 text-[13px] leading-6 text-gray-600">{{ item.description || '卖家暂未补充商品描述。' }}</p>
                <div class="inline-meta mt-3 font-numeric">
                  <span>价格 ¥{{ item.price.toFixed(2) }}</span>
                  <span class="inline-meta-dot"></span>
                  <span>浏览 {{ item.viewCount }}</span>
                  <span class="inline-meta-dot"></span>
                  <span>更新时间 {{ item.updateTime || '-' }}</span>
                </div>
                <p v-if="item.reason" class="mt-2 text-[12px] text-orange-700">状态备注：{{ item.reason }}</p>
              </div>

              <div class="flex flex-wrap items-center gap-2 border-t border-gray-100 pt-4">
                <router-link v-if="item.id !== null" class="btn-default !h-9 px-3" :to="`/seller/products/${item.id}`">
                  详情
                </router-link>

                <router-link v-if="item.id !== null" class="btn-default !h-9 px-3" :to="`/seller/products/${item.id}/edit`">
                  编辑
                </router-link>

                <template v-for="actionMeta in readStatusActions(item.status)" :key="`${item.id}-${actionMeta.action}`">
                  <button
                    v-if="item.id !== null"
                    :class="readStatusActionButtonClass(actionMeta.tone)"
                    type="button"
                    :disabled="loading || Boolean(runningActionKey)"
                    @click="handleStatusAction(item, actionMeta)"
                  >
                    <Loader2 v-if="isRunningAction(item.id, actionMeta.action)" class="h-3.5 w-3.5 animate-spin" />
                    <span>{{ actionMeta.label }}</span>
                  </button>
                </template>

                <button
                  v-if="item.id !== null"
                  class="btn-danger !h-9 px-3"
                  type="button"
                  :disabled="loading || Boolean(runningActionKey)"
                  @click="handleDelete(item)"
                >
                  <Loader2 v-if="isRunningAction(item.id, 'delete')" class="h-3.5 w-3.5 animate-spin" />
                  <span>删除</span>
                </button>
              </div>

              <p v-if="readStatusActions(item.status).length > 0" class="form-helper">
                {{ readFirstStatusActionDescription(item.status) }}
              </p>
            </div>
          </article>

          <div class="pagination-bar">
            <div class="inline-meta">
              <span class="chip chip-neutral font-numeric">第 {{ pagination.page }} / {{ totalPages }} 页</span>
            </div>
            <div class="flex gap-2">
              <button class="btn-default !h-9 px-3.5" type="button" :disabled="!hasPrevPage || loading" @click="changePage(pagination.page - 1)">
                <ChevronLeft class="h-4 w-4" />
                <span>上一页</span>
              </button>
              <button class="btn-default !h-9 px-3.5" type="button" :disabled="!hasNextPage || loading" @click="changePage(pagination.page + 1)">
                <span>下一页</span>
                <ChevronRight class="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
