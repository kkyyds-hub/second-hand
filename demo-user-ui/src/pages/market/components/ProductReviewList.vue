<script setup lang="ts">
import { computed } from 'vue'
import { ChevronLeft, ChevronRight, Loader2, MessageSquare, Star } from 'lucide-vue-next'
import type { PagePayload, ReviewItem } from '@/api/market'

const props = defineProps<{
  page: PagePayload<ReviewItem>
  loading: boolean
  errorMessage: string
}>()

const emit = defineEmits<{
  retry: []
  'change-page': [page: number]
}>()

const totalPages = computed(() => Math.max(1, Math.ceil(props.page.total / props.page.pageSize)))
const score = (value: number) => Math.max(0, Math.min(5, value))
</script>

<template>
  <section class="section-panel">
    <div class="section-header">
      <div>
        <h2 class="section-heading">商品评价</h2>
        <p class="section-subtitle">共 {{ page.total }} 条评价</p>
      </div>
    </div>
    <div class="section-body">
      <div v-if="errorMessage" class="notice-banner notice-banner-danger">
        <div class="flex-1">
          <p>{{ errorMessage }}</p>
          <button class="btn-default mt-3" type="button" :disabled="loading" @click="emit('retry')">重新加载</button>
        </div>
      </div>
      <div v-else-if="loading && page.list.length === 0" class="empty-state min-h-[220px]">
        <Loader2 class="empty-state-icon animate-spin text-blue-500" />
        <p class="empty-state-title">正在加载评价</p>
      </div>
      <div v-else-if="page.list.length === 0" class="empty-state min-h-[220px]">
        <MessageSquare class="empty-state-icon" />
        <p class="empty-state-title">暂无商品评价</p>
        <p class="empty-state-text">成为第一位分享使用感受的人。</p>
      </div>
      <div v-else class="space-y-4">
        <article v-for="item in page.list" :key="item.id ?? `${item.userName}-${item.createdAt}-${item.content}`" class="rounded-lg border border-gray-200 bg-white p-4">
          <div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <p class="text-[13px] font-semibold text-gray-900">{{ item.userName || '匿名用户' }}</p>
                <span class="inline-flex items-center gap-0.5 text-amber-500" :aria-label="`${score(item.score)} 星评价`">
                  <Star v-for="star in 5" :key="star" class="h-3.5 w-3.5" :class="star <= score(item.score) ? 'fill-current' : 'text-gray-200'" aria-hidden="true" />
                </span>
              </div>
              <p class="mt-2 whitespace-pre-line break-words text-[13px] leading-6 text-gray-700">{{ item.content || '评价者暂未留下文字内容。' }}</p>
            </div>
            <p v-if="item.createdAt" class="shrink-0 text-[12px] text-gray-400">{{ item.createdAt }}</p>
          </div>
        </article>
        <div v-if="totalPages > 1" class="pagination-bar">
          <span class="inline-meta">第 {{ page.page }} / {{ totalPages }} 页</span>
          <div class="flex gap-2">
            <button class="btn-default !h-9 px-3.5" type="button" :disabled="page.page <= 1 || loading" @click="emit('change-page', page.page - 1)">
              <ChevronLeft class="h-4 w-4" />
              上一页
            </button>
            <button class="btn-default !h-9 px-3.5" type="button" :disabled="page.page >= totalPages || loading" @click="emit('change-page', page.page + 1)">
              下一页
              <ChevronRight class="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
