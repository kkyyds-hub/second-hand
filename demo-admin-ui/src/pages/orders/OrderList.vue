<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  Eye,
  Flag,
  Loader2,
  RefreshCw,
  Search,
  X,
} from 'lucide-vue-next'
import {
  createAdminOrderFlag,
  getAdminOrderDetail,
  getAdminOrders,
  getAdminOrderStatusLabel,
  getOrderFlagLabel,
  type AdminOrderDetail,
  type AdminOrderItem,
  type OrderFlagType,
} from '@/api/order'

const router = useRouter()
const loading = ref(false)
const listError = ref('')
const orders = ref<AdminOrderItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const status = ref('')
const startTime = ref('')
const endTime = ref('')
const sortField = ref<'createTime' | 'payTime'>('createTime')
const sortOrder = ref<'asc' | 'desc'>('desc')
const filterError = ref('')
const selectedOrder = ref<AdminOrderDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')
const isDrawerOpen = ref(false)
const isFlagModalOpen = ref(false)
const flagType = ref<OrderFlagType>('MANUAL_REVIEW')
const flagRemark = ref('')
const flagError = ref('')
const flagNotice = ref('')
const flagSubmitting = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const showErrorBanner = computed(() => Boolean(listError.value && orders.value.length))
const showEmptyError = computed(() => Boolean(listError.value && !orders.value.length && !loading.value))
const showEmpty = computed(() => Boolean(!listError.value && !orders.value.length && !loading.value))
const currentPagePaid = computed(() => orders.value.filter((item) => item.status === 'paid').length)
const currentPagePending = computed(() => orders.value.filter((item) => item.status === 'pending').length)
const currentPageCancelled = computed(() => orders.value.filter((item) => item.status === 'cancelled').length)
const currentPageFlags = computed(() => orders.value.filter((item) => item.flagCount > 0).length)

const resolveError = (error: unknown, fallback: string) => {
  if (error && typeof error === 'object' && 'message' in error && typeof error.message === 'string') {
    return error.message.trim() || fallback
  }
  return fallback
}

const formatDate = (value?: string | null) => value ? value.replace('T', ' ').slice(0, 19) : '—'
const formatMoney = (value?: number | string | null) => `¥${Number(value || 0).toFixed(2)}`
const maskMobile = (value?: string | null) => {
  if (!value) return '—'
  return value.length >= 7 ? `${value.slice(0, 3)}****${value.slice(-4)}` : value
}
const personLabel = (nickname: string | undefined, userId: number | undefined, deleted?: boolean) =>
  `${deleted ? '已注销用户' : nickname?.trim() || '未知用户'} #${userId ?? '—'}`
const maskSensitiveAddress = (value?: string | null) => {
  if (!value) return '—'
  return value.replace(/(?<!\d)(1[3-9]\d)\d{4}(\d{4})(?!\d)/g, '$1****$2')
}

const statusClass = (value: string) => ({
  pending: 'border-amber-200 bg-amber-50 text-amber-700',
  paid: 'border-blue-200 bg-blue-50 text-blue-700',
  shipped: 'border-cyan-200 bg-cyan-50 text-cyan-700',
  completed: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  cancelled: 'border-gray-200 bg-gray-50 text-gray-700',
}[value] || 'border-rose-200 bg-rose-50 text-rose-700')

const validateDates = () => {
  filterError.value = ''
  if (startTime.value && endTime.value && new Date(startTime.value) > new Date(endTime.value)) {
    filterError.value = '开始时间不能晚于结束时间'
    return false
  }
  return true
}

const fetchOrders = async (resetPage = false) => {
  if (!validateDates()) return
  if (resetPage) page.value = 1
  try {
    loading.value = true
    listError.value = ''
    const result = await getAdminOrders({
      page: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      status: status.value || undefined,
      startTime: startTime.value ? `${startTime.value.replace('T', ' ')}:00` : undefined,
      endTime: endTime.value ? `${endTime.value.replace('T', ' ')}:00` : undefined,
      sortField: sortField.value,
      sortOrder: sortOrder.value,
    })
    orders.value = result.list || []
    total.value = Number(result.total || 0)
    page.value = Number(result.page || page.value)
  } catch (error) {
    listError.value = resolveError(error, '订单列表加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const clearFilters = () => {
  keyword.value = ''
  status.value = ''
  startTime.value = ''
  endTime.value = ''
  sortField.value = 'createTime'
  sortOrder.value = 'desc'
  filterError.value = ''
  fetchOrders(true)
}

const openDetail = async (order: Pick<AdminOrderItem, 'orderId'>) => {
  isDrawerOpen.value = true
  selectedOrder.value = null
  detailError.value = ''
  flagNotice.value = ''
  detailLoading.value = true
  try {
    selectedOrder.value = await getAdminOrderDetail(order.orderId)
  } catch (error) {
    detailError.value = resolveError(error, '订单详情加载失败，请稍后重试')
  } finally {
    detailLoading.value = false
  }
}

const closeDrawer = () => {
  if (flagSubmitting.value) return
  isDrawerOpen.value = false
  isFlagModalOpen.value = false
  selectedOrder.value = null
  detailError.value = ''
  flagNotice.value = ''
}

const openFlagForOrder = async (order: Pick<AdminOrderItem, 'orderId'>) => {
  await openDetail(order)
  if (selectedOrder.value) openFlagModal()
}

const openFlagModal = () => {
  if (!selectedOrder.value) return
  flagType.value = 'MANUAL_REVIEW'
  flagRemark.value = ''
  flagError.value = ''
  isFlagModalOpen.value = true
}

const submitFlag = async () => {
  if (!selectedOrder.value || flagSubmitting.value) return
  const remark = flagRemark.value.trim()
  if (remark.length > 200) {
    flagError.value = '备注不能超过 200 个字符'
    return
  }
  try {
    flagSubmitting.value = true
    flagError.value = ''
    flagNotice.value = await createAdminOrderFlag(selectedOrder.value.orderId, { type: flagType.value, remark: remark || undefined })
    selectedOrder.value = await getAdminOrderDetail(selectedOrder.value.orderId)
    isFlagModalOpen.value = false
    await fetchOrders()
  } catch (error) {
    flagError.value = resolveError(error, '标记提交失败，请稍后重试')
  } finally {
    flagSubmitting.value = false
  }
}

const goToAudit = () => {
  if (selectedOrder.value?.afterSaleId) {
    router.push({ path: '/audit', query: { orderId: String(selectedOrder.value.orderId) } })
  }
}

onMounted(() => fetchOrders())
</script>

<template>
  <div class="mx-auto max-w-[1600px] space-y-6 pb-8">
    <section class="rounded-xl border border-gray-200/80 bg-white p-6 shadow-sm">
      <div class="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
        <div>
          <div class="flex flex-wrap items-center gap-3">
            <h1 class="text-2xl font-bold text-gray-900">订单管理</h1>
            <span class="rounded-md border border-blue-200 bg-blue-50 px-2 py-0.5 text-[12px] font-medium text-blue-700">全平台交易</span>
          </div>
          <p class="mt-2 text-sm text-gray-500">查看订单进度、交易参与方、售后关联与风险标记；订单状态只能在既有业务流程中变更。</p>
        </div>
        <button class="btn-default inline-flex items-center gap-2" :disabled="loading" @click="fetchOrders()">
          <RefreshCw :class="['h-4 w-4', loading ? 'animate-spin' : '']" />
          刷新
        </button>
      </div>
    </section>

    <section class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-5">
      <article v-for="item in [
        { label: '当前查询订单', value: total },
        { label: '当前页待支付', value: currentPagePending },
        { label: '当前页待发货', value: currentPagePaid },
        { label: '当前页已取消', value: currentPageCancelled },
        { label: '当前页异常标记', value: currentPageFlags },
      ]" :key="item.label" class="rounded-xl border border-gray-200/80 bg-white p-4 shadow-sm">
        <p class="text-[13px] text-gray-500">{{ item.label }}</p>
        <p class="mt-2 font-numeric text-2xl font-bold text-gray-900">{{ item.value }}</p>
      </article>
    </section>

    <section class="filter-bar">
      <div class="filter-bar-group flex-1">
        <div class="filter-search">
          <Search class="filter-search-icon" />
          <input v-model="keyword" class="input-standard w-full !pl-9" placeholder="订单号、买卖双方或商品" @keyup.enter="fetchOrders(true)" />
        </div>
        <select v-model="status" class="input-standard min-w-[132px] bg-white">
          <option value="">全部状态</option><option value="pending">待支付</option><option value="paid">待发货</option><option value="shipped">待收货</option><option value="completed">已完成</option><option value="cancelled">已取消</option>
        </select>
        <input v-model="startTime" class="input-standard" type="datetime-local" aria-label="开始时间" />
        <input v-model="endTime" class="input-standard" type="datetime-local" aria-label="结束时间" />
        <select v-model="sortField" class="input-standard min-w-[130px] bg-white"><option value="createTime">按创建时间</option><option value="payTime">按支付时间</option></select>
        <select v-model="sortOrder" class="input-standard min-w-[104px] bg-white"><option value="desc">降序</option><option value="asc">升序</option></select>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <button class="btn-primary" :disabled="loading" @click="fetchOrders(true)">查询</button>
        <button class="btn-default" :disabled="loading" @click="clearFilters">清空</button>
      </div>
      <p v-if="filterError" class="basis-full text-sm text-red-600" role="alert" aria-live="assertive" tabindex="-1">{{ filterError }}</p>
    </section>

    <section class="table-shell relative min-h-[440px]">
      <div v-if="loading" class="absolute inset-0 z-10 flex items-center justify-center bg-white/80" role="status" aria-live="polite"><Loader2 class="h-6 w-6 animate-spin text-gray-600" /><span class="ml-2 text-sm text-gray-600">订单列表加载中...</span></div>
      <div v-if="showErrorBanner" class="m-4 flex items-center justify-between gap-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700" role="alert" aria-live="assertive" tabindex="-1"><span>{{ listError }}</span><button class="btn-default shrink-0" @click="fetchOrders()">重新加载</button></div>
      <div class="overflow-x-auto">
        <table class="table-base min-w-[1120px]">
          <thead class="table-head-row"><tr><th class="table-head-cell">订单 / 商品</th><th class="table-head-cell">买家</th><th class="table-head-cell">卖家</th><th class="table-head-cell">金额</th><th class="table-head-cell">状态</th><th class="table-head-cell">支付 / 创建</th><th class="table-head-cell">风险标记</th><th class="table-head-cell">操作</th></tr></thead>
          <tbody class="table-body">
            <tr v-for="order in orders" :key="order.orderId" class="table-row">
              <td class="table-cell"><p class="font-mono text-xs text-gray-900">{{ order.orderNo }}</p><p class="mt-1 max-w-[220px] truncate text-xs text-gray-500">{{ order.productTitle || `商品 #${order.productId || '—'}` }}</p></td>
              <td class="table-cell"><p>{{ personLabel(order.buyerNickname, order.buyerId, order.buyerDeleted) }}</p><p class="mt-1 text-xs text-gray-500">{{ order.buyerDeleted ? '—' : maskMobile(order.buyerMobile) }}</p></td>
              <td class="table-cell"><p>{{ personLabel(order.sellerNickname, order.sellerId, order.sellerDeleted) }}</p><p class="mt-1 text-xs text-gray-500">{{ order.sellerDeleted ? '—' : maskMobile(order.sellerMobile) }}</p></td>
              <td class="table-cell font-medium text-gray-900">{{ formatMoney(order.totalAmount) }}</td>
              <td class="table-cell"><span :class="['rounded-md border px-2 py-1 text-xs font-medium', statusClass(order.status)]">{{ getAdminOrderStatusLabel(order.status) }}</span></td>
              <td class="table-cell text-xs text-gray-500"><p>{{ formatDate(order.payTime) }}</p><p class="mt-1">{{ formatDate(order.createTime) }}</p></td>
              <td class="table-cell"><span v-if="order.flagCount" class="rounded-md border border-rose-200 bg-rose-50 px-2 py-1 text-xs font-medium text-rose-700">已标记 {{ order.flagCount }} 项</span><span v-else class="text-xs text-gray-500">无标记</span></td>
              <td class="table-cell"><div class="flex flex-wrap gap-2"><button class="btn-default inline-flex items-center gap-1 text-xs" @click="openDetail(order)"><Eye class="h-3.5 w-3.5" />详情</button><button class="btn-default inline-flex items-center gap-1 text-xs" @click="openFlagForOrder(order)"><Flag class="h-3.5 w-3.5" />标记</button></div></td>
            </tr>
            <tr v-if="showEmpty || showEmptyError"><td colspan="8" class="p-12 text-center"><AlertTriangle v-if="showEmptyError" class="mx-auto h-8 w-8 text-red-400" /><ClipboardList v-else class="mx-auto h-8 w-8 text-gray-300" /><p class="mt-3 font-medium text-gray-800">{{ showEmptyError ? '订单列表暂未加载成功' : '当前条件下没有订单' }}</p><p class="mt-1 text-sm text-gray-500">{{ showEmptyError ? listError : '调整筛选条件后再次查询。' }}</p><button v-if="showEmptyError" class="btn-default mt-4" @click="fetchOrders()">重新加载</button></td></tr>
          </tbody>
        </table>
      </div>
      <div class="flex flex-wrap items-center justify-between gap-3 border-t border-gray-100 px-5 py-4 text-sm text-gray-500"><span>共 {{ total }} 条，第 {{ page }} / {{ totalPages }} 页</span><div class="flex items-center gap-2"><select v-model.number="pageSize" class="input-standard py-1.5 text-xs" @change="fetchOrders(true)"><option :value="10">10 / 页</option><option :value="20">20 / 页</option><option :value="50">50 / 页</option></select><button class="btn-default p-2" :disabled="page <= 1 || loading" aria-label="上一页" @click="page -= 1; fetchOrders()"><ChevronLeft class="h-4 w-4" /></button><button class="btn-default p-2" :disabled="page >= totalPages || loading" aria-label="下一页" @click="page += 1; fetchOrders()"><ChevronRight class="h-4 w-4" /></button></div></div>
    </section>

    <div v-if="isDrawerOpen" class="fixed inset-0 z-50 bg-gray-900/35" @click.self="closeDrawer">
      <aside class="ml-auto flex h-full w-full max-w-2xl flex-col bg-white shadow-2xl" role="dialog" aria-modal="true" aria-label="订单详情">
        <header class="flex items-start justify-between border-b border-gray-100 px-6 py-5"><div><h2 class="text-lg font-bold text-gray-900">订单详情</h2><p class="mt-1 text-sm text-gray-500">{{ selectedOrder?.orderNo || '正在读取订单信息' }}</p></div><button class="modal-close" aria-label="关闭详情" @click="closeDrawer"><X class="h-5 w-5" /></button></header>
        <div class="custom-scrollbar flex-1 overflow-y-auto p-6">
          <div v-if="detailLoading" class="flex justify-center py-16" role="status" aria-live="polite"><Loader2 class="h-6 w-6 animate-spin text-gray-500" /></div>
          <div v-else-if="detailError" class="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert" aria-live="assertive" tabindex="-1"><p>{{ detailError }}</p><button class="btn-default mt-3" @click="selectedOrder && openDetail(selectedOrder)">重新加载</button></div>
          <div v-else-if="selectedOrder" class="space-y-6">
            <section class="grid grid-cols-2 gap-4 rounded-lg border border-gray-200 p-4 text-sm"><div><p class="text-gray-500">订单状态</p><p class="mt-1 font-medium">{{ getAdminOrderStatusLabel(selectedOrder.status) }}</p></div><div><p class="text-gray-500">支付金额</p><p class="mt-1 font-medium">{{ formatMoney(selectedOrder.totalAmount) }}</p></div><div><p class="text-gray-500">创建时间</p><p class="mt-1">{{ formatDate(selectedOrder.createTime) }}</p></div><div><p class="text-gray-500">支付时间</p><p class="mt-1">{{ formatDate(selectedOrder.payTime) }}</p></div><div><p class="text-gray-500">发货 / 完成</p><p class="mt-1">{{ formatDate(selectedOrder.shipTime) }} / {{ formatDate(selectedOrder.completeTime) }}</p></div><div><p class="text-gray-500">取消时间</p><p class="mt-1">{{ formatDate(selectedOrder.cancelTime) }}</p></div></section>
            <section><h3 class="text-sm font-semibold text-gray-900">商品信息</h3><div class="mt-3 space-y-3"><article v-for="item in selectedOrder.items" :key="item.productId" class="flex gap-3 rounded-lg border border-gray-200 p-3"><img v-if="item.productThumbnail" :src="item.productThumbnail" class="h-12 w-12 rounded object-cover" alt="" /><div class="min-w-0 flex-1"><p class="truncate font-medium text-gray-900">{{ item.productTitle }}</p><p class="mt-1 text-xs text-gray-500">商品 #{{ item.productId }} · {{ item.productStatus }} · {{ formatMoney(item.price) }} × {{ item.quantity }}</p></div></article></div></section>
            <section class="grid grid-cols-1 gap-3 md:grid-cols-2"><div class="rounded-lg border border-gray-200 p-4"><h3 class="text-sm font-semibold text-gray-900">买家</h3><p class="mt-2">{{ personLabel(selectedOrder.buyerNickname, selectedOrder.buyerId, selectedOrder.buyerDeleted) }}</p><p class="mt-1 text-sm text-gray-500">{{ selectedOrder.buyerDeleted ? '—' : maskMobile(selectedOrder.buyerMobile) }}</p></div><div class="rounded-lg border border-gray-200 p-4"><h3 class="text-sm font-semibold text-gray-900">卖家</h3><p class="mt-2">{{ personLabel(selectedOrder.sellerNickname, selectedOrder.sellerId, selectedOrder.sellerDeleted) }}</p><p class="mt-1 text-sm text-gray-500">{{ selectedOrder.sellerDeleted ? '—' : maskMobile(selectedOrder.sellerMobile) }}</p></div></section>
            <section class="rounded-lg border border-gray-200 p-4"><h3 class="text-sm font-semibold text-gray-900">收货与物流</h3><dl class="mt-3 space-y-2 text-sm"><div><dt class="text-gray-500">收货地址</dt><dd class="mt-1 break-words text-gray-900">{{ maskSensitiveAddress(selectedOrder.shippingAddress) }}</dd></div><div class="grid grid-cols-2 gap-3"><div><dt class="text-gray-500">物流公司</dt><dd class="mt-1">{{ selectedOrder.shippingCompany || '—' }}</dd></div><div><dt class="text-gray-500">物流单号</dt><dd class="mt-1">{{ selectedOrder.trackingNo || '—' }}</dd></div></div><div><dt class="text-gray-500">发货备注</dt><dd class="mt-1">{{ selectedOrder.shippingRemark || '—' }}</dd></div></dl></section>
            <section class="rounded-lg border border-gray-200 p-4"><div class="flex items-center justify-between gap-3"><h3 class="text-sm font-semibold text-gray-900">售后关联</h3><button v-if="selectedOrder.afterSaleId" class="btn-default text-xs" @click="goToAudit">前往纠纷与违规</button></div><p v-if="selectedOrder.afterSaleId" class="mt-3 text-sm">售后 #{{ selectedOrder.afterSaleId }} · {{ selectedOrder.afterSaleStatus }}</p><p v-if="selectedOrder.afterSaleId" class="mt-1 text-sm text-gray-500">{{ selectedOrder.afterSaleReason || '—' }}</p><p v-if="!selectedOrder.afterSaleId" class="mt-3 text-sm text-gray-500">暂无关联售后记录。</p></section>
            <section><div class="flex items-center justify-between"><h3 class="text-sm font-semibold text-gray-900">异常标记</h3><button class="btn-primary inline-flex items-center gap-2 text-xs" @click="openFlagModal"><Flag class="h-3.5 w-3.5" />添加标记</button></div><p v-if="flagNotice" class="mt-3 rounded-lg border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-800" role="status" aria-live="polite">{{ flagNotice }}</p><div v-if="selectedOrder.flags.length" class="mt-3 space-y-2"><article v-for="flag in selectedOrder.flags" :key="flag.id" class="rounded-lg border border-rose-100 bg-rose-50/50 p-3 text-sm"><p class="font-medium text-rose-800">{{ getOrderFlagLabel(flag.type) }}</p><p class="mt-1 text-gray-700">{{ flag.remark || '无备注' }}</p><p class="mt-1 text-xs text-gray-500">{{ flag.createdByNickname || `管理员 #${flag.createdBy || '—'}` }} · {{ formatDate(flag.createTime) }}</p></article></div><p v-else class="mt-3 text-sm text-gray-500">暂无异常标记。</p></section>
          </div>
        </div>
      </aside>
    </div>

    <div v-if="isFlagModalOpen" class="modal-backdrop" @click.self="!flagSubmitting && (isFlagModalOpen = false)"><section class="modal-panel max-w-lg" role="dialog" aria-modal="true" aria-label="添加异常标记"><header class="modal-header"><div><h2 class="modal-title">添加异常标记</h2><p class="mt-1 text-sm text-gray-500">{{ selectedOrder?.orderNo }}</p></div><button class="modal-close" :disabled="flagSubmitting" aria-label="关闭标记弹窗" @click="isFlagModalOpen = false"><X class="h-5 w-5" /></button></header><div class="modal-body"><label class="block text-sm font-medium text-gray-700">标记类型<select v-model="flagType" class="input-standard mt-2 w-full"><option value="PAYMENT_RISK">支付异常</option><option value="PRICE_ANOMALY">金额异常</option><option value="DELIVERY_RISK">发货风险</option><option value="AFTERSALE_RISK">售后风险</option><option value="ACCOUNT_RISK">账号风险</option><option value="MANUAL_REVIEW">人工复核</option></select></label><label class="mt-4 block text-sm font-medium text-gray-700">备注（可选）<textarea v-model="flagRemark" class="input-standard mt-2 min-h-24 w-full" maxlength="200" placeholder="记录本次标记的客观原因" /></label><p class="mt-1 text-right text-xs text-gray-500">{{ flagRemark.length }} / 200</p><p v-if="flagError" class="mt-3 text-sm text-red-600" role="alert" aria-live="assertive">{{ flagError }}</p></div><footer class="modal-footer"><button class="btn-default" :disabled="flagSubmitting" @click="isFlagModalOpen = false">取消</button><button class="btn-primary inline-flex items-center gap-2" :disabled="flagSubmitting" @click="submitFlag"><Loader2 v-if="flagSubmitting" class="h-4 w-4 animate-spin" />{{ flagSubmitting ? '提交中...' : '提交标记' }}</button></footer></section></div>
  </div>
</template>
