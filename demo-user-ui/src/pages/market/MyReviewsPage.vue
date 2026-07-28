<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ChevronLeft, ChevronRight, Loader2, MessageSquare, RefreshCw, Star } from 'lucide-vue-next'
import { createEmptyMyReviewPage, getMyReviews } from '@/api/review'

const loading = ref(false)
const hasLoadedOnce = ref(false)
const errorMessage = ref('')
const pageData = ref(createEmptyMyReviewPage())
const pagination = reactive({
  page: 1,
  pageSize: 10,
})

const totalPages = computed(() => Math.max(1, Math.ceil(pageData.value.total / pageData.value.pageSize)))
const hasPrevPage = computed(() => pagination.page > 1)
const hasNextPage = computed(() => pagination.page < totalPages.value)
const hasEmptyState = computed(() => !loading.value && hasLoadedOnce.value && !errorMessage.value && pageData.value.list.length === 0)

function validId(value: number | null | undefined) {
  return typeof value === 'number' && value > 0
}

function ratingStars(rating: number) {
  return Array.from({ length: 5 }, (_, index) => index < Math.max(0, Math.min(5, Math.round(rating))))
}

function readErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return '我的评价加载失败，请稍后重试。'
}

async function loadMyReviews() {
  if (loading.value) {
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''

    pageData.value = await getMyReviews({
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
  } catch (error: unknown) {
    errorMessage.value = readErrorMessage(error)
  } finally {
    loading.value = false
    hasLoadedOnce.value = true
  }
}

function changePage(nextPage: number) {
  if (nextPage < 1 || nextPage > totalPages.value || nextPage === pagination.page) {
    return
  }

  pagination.page = nextPage
  loadMyReviews()
}

onMounted(() => {
  loadMyReviews()
})
</script>

<template>
  <div class="page-body">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">评价</p>
          <h1 class="page-title">我的评价记录</h1>
          <p class="page-desc">在这里回顾你对已购商品留下的真实评价。</p>
        </div>
        <div class="page-actions">
          <router-link class="btn-default" to="/market">
            <ChevronLeft class="h-4 w-4" />
            <span>返回市场</span>
          </router-link>
          <button class="btn-default" type="button" :disabled="loading" @click="loadMyReviews">
            <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
            <RefreshCw v-else class="h-4 w-4" />
            <span>{{ loading ? '刷新中' : '刷新列表' }}</span>
          </button>
        </div>
      </div>
    </section>
    <section v-if="errorMessage" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <div class="flex-1">
        <p class="font-semibold">加载失败</p>
        <p class="mt-1 text-[12px] leading-5">{{ errorMessage }}</p>
        <button class="btn-default mt-3" type="button" :disabled="loading" @click="loadMyReviews">重试请求</button>
      </div>
    </section>
    <section class="section-panel">
      <div class="section-header">
        <div>
          <h2 class="section-heading">历史评价</h2>
          <p class="section-subtitle">共 {{ pageData.total }} 条记录，当前第 {{ pageData.page }} / {{ totalPages }} 页</p>
        </div>
        <div class="section-actions">
          <span class="chip chip-muted font-numeric">共 {{ pageData.total }} 条</span>
        </div>
      </div>
      <div class="section-body">
        <div v-if="loading && !hasLoadedOnce" class="empty-state min-h-[320px]">
          <Loader2 class="empty-state-icon animate-spin text-blue-500" />
          <p class="empty-state-title">正在加载评价记录</p>
        </div>
        <div v-else-if="hasEmptyState" class="empty-state min-h-[320px]">
          <MessageSquare class="empty-state-icon" />
          <p class="empty-state-title">暂无评价记录</p>
          <p class="empty-state-text">完成订单后可以在商品或订单页面留下评价。</p>
          <router-link class="btn-primary mt-5" to="/orders/buyer">查看我的订单</router-link>
        </div>
        <div v-else class="grid gap-4">
          <article v-for="item in pageData.list" :key="item.id ?? `${item.orderId}-${item.createdAt}`" class="soft-panel p-5">
            <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
              <div class="min-w-0 flex items-start gap-3">
                <div class="icon-badge shrink-0">
                  <MessageSquare class="h-4 w-4" />
                </div>
                <div class="min-w-0">
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="review-stars" :aria-label="`${item.rating} 星`">
                      <Star v-for="(active, index) in ratingStars(item.rating)" :key="index" class="h-4 w-4" :class="active ? 'fill-orange-400 text-orange-400' : 'text-gray-300'" />
                    </span>
                    <router-link v-if="validId(item.productId)" class="max-w-[320px] truncate text-[15px] font-semibold text-gray-900 hover:text-orange-600" :to="`/market/${item.productId}`" :title="item.productTitle || '商品信息待补充'">
                      {{ item.productTitle || '商品信息待补充' }}
                    </router-link>
                    <p v-else class="max-w-[320px] truncate text-[15px] font-semibold text-gray-900">{{ item.productTitle || '商品信息待补充' }}</p>
                  </div>
                  <div class="mt-2 flex flex-wrap items-center gap-2 text-[11px] text-gray-500">
                    <span class="chip chip-neutral">{{ item.isAnonymous ? '匿名评价' : '公开评价' }}</span>
                    <router-link v-if="validId(item.orderId)" class="text-[12px] font-medium text-orange-700 hover:text-orange-800" :to="`/orders/buyer/${item.orderId}`">查看关联订单</router-link>
                  </div>
                </div>
              </div>
              <p class="text-[12px] text-gray-400 font-numeric">{{ item.createdAt || '时间未知' }}</p>
            </div>
            <p class="mt-4 border-t border-gray-100 pt-4 text-[13px] leading-6 text-gray-700">{{ item.content || '未留下详细评价。' }}</p>
          </article>
        </div>
        <div class="pagination-bar">
          <div class="inline-meta">
            <span class="chip chip-neutral font-numeric">第 {{ pagination.page }} / {{ totalPages }} 页</span>
            <span>显示 {{ pageData.total === 0 ? 0 : (pagination.page - 1) * pagination.pageSize + 1 }} 到 {{ Math.min(pagination.page * pagination.pageSize, pageData.total) }} 条记录</span>
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
    </section>
  </div>
</template>
