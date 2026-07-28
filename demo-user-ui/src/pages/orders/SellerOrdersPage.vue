<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ChevronLeft, ChevronRight, Loader2, PackageSearch } from 'lucide-vue-next'
import { createEmptySellerOrderPage, getSellerOrderList, type SellerOrderSummary } from '@/api/orders'
import { isSellerUser, readCurrentUser } from '@/utils/request'
import CommerceOrderCard from '@/components/commerce/CommerceOrderCard.vue'
import ordersEmptyImage from '@/assets/commerce/orders-empty.webp'

const sellerEnabled = computed(() => isSellerUser(readCurrentUser()))
const loading = ref(false); const hasLoadedOnce = ref(false); const errorMessage = ref(''); const emptyImageFailed = ref(false)
const pageData = ref(createEmptySellerOrderPage()); const filters = reactive({ status: '', pageSize: 10 }); const pagination = reactive({ page: 1 })
const statusOptions = [{ value: '', label: '全部' }, { value: 'pending', label: '待支付' }, { value: 'paid', label: '待发货' }, { value: 'shipped', label: '已发货' }, { value: 'completed', label: '已完成' }, { value: 'cancelled', label: '已取消' }]
const list = computed<SellerOrderSummary[]>(() => pageData.value.list); const totalPages = computed(() => Math.max(1, Math.ceil(pageData.value.total / pageData.value.pageSize)))
const hasEmptyState = computed(() => !loading.value && hasLoadedOnce.value && !errorMessage.value && list.value.length === 0)
const currentFilterLabel = computed(() => statusOptions.find((item) => item.value === filters.status)?.label || '全部')
function isValidOrderId(value: number | null) { return typeof value === 'number' && Number.isSafeInteger(value) && value > 0 }
function readErrorMessage(error: unknown) { return error instanceof Error && error.message.trim() ? error.message : '卖家订单列表加载失败，请稍后重试。' }
async function loadList() { if (loading.value || !sellerEnabled.value) return; try { loading.value = true; errorMessage.value = ''; const payload = await getSellerOrderList({ status: filters.status || undefined, page: pagination.page, pageSize: filters.pageSize }); pageData.value = payload; pagination.page = payload.page } catch (error: unknown) { errorMessage.value = readErrorMessage(error) } finally { loading.value = false; hasLoadedOnce.value = true } }
function changeStatus(status: string) { if (filters.status !== status) { filters.status = status; pagination.page = 1; void loadList() } }
function changePage(nextPage: number) { if (nextPage >= 1 && nextPage <= totalPages.value && nextPage !== pagination.page) { pagination.page = nextPage; void loadList() } }
function readOrderActions(item: SellerOrderSummary) {
  const detailPath = isValidOrderId(item.orderId) ? `/orders/seller/${item.orderId}` : null
  if (!detailPath) return {}
  if (item.status === 'pending') return { statusText: '等待买家支付', secondaryLabel: '查看详情', secondaryPath: detailPath }
  if (item.status === 'paid') return { primaryLabel: '去发货', primaryPath: detailPath }
  if (item.status === 'shipped') return { primaryLabel: '查看物流', primaryPath: detailPath }
  return { secondaryLabel: '查看详情', secondaryPath: detailPath }
}
onMounted(() => { if (sellerEnabled.value) void loadList(); else hasLoadedOnce.value = true })
</script>

<template>
  <div class="page-body">
    <section class="page-hero"><div class="page-hero-content"><div class="page-header-main"><p class="page-kicker">卖家履约</p><h1 class="page-title">卖家订单</h1><p class="page-desc">集中查看买家订单、物流摘要和待处理的发货事项。</p></div></div></section>
    <section v-if="!sellerEnabled" class="notice-banner notice-banner-warning"><span class="notice-dot bg-orange-500"></span><span>当前账号暂未启用卖家功能，无法查看卖家订单。</span></section>
    <template v-else>
      <section class="section-panel overflow-hidden"><div class="section-body border-b border-stone-100 pb-3"><div class="order-filter-tabs" role="tablist" aria-label="卖家订单状态"><button v-for="option in statusOptions" :key="option.value" class="order-filter-tab" :class="{ 'order-filter-tab-active': filters.status === option.value }" type="button" :disabled="loading" @click="changeStatus(option.value)">{{ option.label }}</button></div></div><div class="section-body flex flex-wrap items-center justify-between gap-3 pt-3"><p class="text-[13px] text-stone-600">{{ currentFilterLabel }}订单共 {{ pageData.total }} 条</p><label class="inline-flex items-center gap-2 text-[13px] text-stone-600">每页 <select v-model.number="filters.pageSize" class="input-standard !h-9 !w-20" :disabled="loading" @change="pagination.page = 1; loadList()"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select> 条</label></div></section>
      <section v-if="errorMessage" class="notice-banner notice-banner-danger"><span class="notice-dot bg-red-500"></span><div class="flex-1"><p class="font-semibold">卖家订单加载失败</p><p class="mt-1 text-[12px] leading-5">{{ errorMessage }}</p><button class="btn-default mt-3" type="button" :disabled="loading" @click="loadList">重新加载</button></div></section>
      <section class="mt-6 space-y-4"><div v-if="loading && !hasLoadedOnce" class="empty-state section-panel min-h-[320px]"><Loader2 class="empty-state-icon animate-spin text-orange-600" /><p class="empty-state-title">正在加载卖家订单</p></div><div v-else-if="hasEmptyState" class="empty-state section-panel min-h-[360px]"><img v-if="!emptyImageFailed" :src="ordersEmptyImage" alt="暂无订单" class="orders-empty-image" @error="emptyImageFailed = true" /><PackageSearch v-else class="empty-state-icon" /><p class="empty-state-title">{{ filters.status ? '当前状态下暂无订单' : '还没有卖家订单' }}</p><p class="empty-state-text">{{ filters.status ? '可以查看全部订单，继续处理其他履约事项。' : '产生交易后，订单会在这里显示。' }}</p><button v-if="filters.status" class="btn-default mt-4" type="button" @click="changeStatus('')">查看全部订单</button></div><template v-else><CommerceOrderCard v-for="item in list" :key="item.orderId ?? item.orderNo" :order-id="item.orderId" :order-no="item.orderNo" :status="item.status" :status-label="item.statusLabel" :product-id="item.productId" :product-title="item.productTitle" :product-thumbnail="item.productThumbnail" counterpart-label="买家" :counterpart-name="item.buyerNickname" :deal-price="item.dealPrice" :quantity="item.quantity" :create-time="item.createTime" :shipping-company="item.shippingCompany" :tracking-no="item.trackingNo" v-bind="readOrderActions(item)" /><div class="pagination-bar section-panel"><span class="chip chip-neutral font-numeric">第 {{ pagination.page }} / {{ totalPages }} 页</span><div class="flex gap-2"><button class="btn-default !h-9 px-3" type="button" :disabled="pagination.page <= 1 || loading" @click="changePage(pagination.page - 1)"><ChevronLeft class="h-4 w-4" />上一页</button><button class="btn-default !h-9 px-3" type="button" :disabled="pagination.page >= totalPages || loading" @click="changePage(pagination.page + 1)">下一页<ChevronRight class="h-4 w-4" /></button></div></div></template></section>
    </template>
  </div>
</template>

<style scoped>
.order-filter-tabs { display: flex; gap: 0.5rem; overflow-x: auto; padding-bottom: 0.25rem; }.order-filter-tab { flex: 0 0 auto; min-height: 2.25rem; padding: 0 0.875rem; border-bottom: 2px solid transparent; color: #78716c; font-size: 0.875rem; }.order-filter-tab:hover, .order-filter-tab-active { border-color: #ea580c; color: #c2410c; font-weight: 600; }.orders-empty-image { width: min(31rem, 100%); margin-bottom: 0.75rem; border-radius: 0.5rem; object-fit: contain; }
</style>
