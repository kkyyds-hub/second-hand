<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  AlertTriangle,
  CheckCircle,
  Clock,
  Filter,
  Loader2,
  Search,
  ShieldAlert,
  XCircle,
} from 'lucide-vue-next'
import {
  approveProductReview,
  getProductReviewRiskText,
  getProductReviewList,
  getProductReviewStatusText,
  rejectProductReview,
  type ProductReviewItem,
} from '@/api/product'
import {
  fetchProductViolations,
  forceOffShelfProduct,
  type ProductViolationItem,
} from '@/api/adminExtra'

/**
 * 页面状态。
 * Day08 这一轮继续只做前端消费边界收口，不改审核联调链路。
 */
const loading = ref(false)
const listError = ref('')
const searchQuery = ref('')
const selectedStatus = ref('PENDING')
const processingId = ref('')
const isRejectModalOpen = ref(false)
const rejectTarget = ref<ProductReviewItem | null>(null)
const rejectReason = ref('')
const rejectError = ref('')
const isDetailModalOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailTarget = ref<ProductReviewItem | null>(null)
const productViolations = ref<ProductViolationItem[]>([])
const forceReason = ref('')
const forceOffShelfLoading = ref(false)

/**
 * 规则说明弹窗状态。
 */
const isRuleModalOpen = ref(false)

/**
 * 列表数据。
 */
const products = ref<ProductReviewItem[]>([])
const totalCount = ref(0)

/**
 * 页面层统一收口错误文案：
 * - 不改接口层契约，只决定页面最终怎么提示。
 * - FrontDay08 后续若继续治理其他页面，可复用同类 helper。
 */
const resolvePageErrorMessage = (error: unknown, fallback: string) => {
  if (error && typeof error === 'object' && 'message' in error) {
    const message = error.message
    if (typeof message === 'string' && message.trim()) {
      return message.trim()
    }
  }

  return fallback
}

const showListErrorBanner = computed(() => !!listError.value && products.value.length > 0)
const showListErrorEmptyState = computed(() => !!listError.value && !loading.value && products.value.length === 0)
const showListEmptyState = computed(() => !listError.value && !loading.value && products.value.length === 0)

/**
 * 拉取审核列表。
 */
const fetchData = async () => {
  try {
    loading.value = true
    listError.value = ''
    const res = await getProductReviewList({
      keyword: searchQuery.value || undefined,
      status: selectedStatus.value === 'ALL' ? undefined : (selectedStatus.value as ProductReviewItem['status']),
    })
    products.value = res.items || []
    totalCount.value = res.total || 0
  } catch (error) {
    console.warn('Product review list request failed.', error)
    // 失败时保留当前已展示数据，避免把“请求失败”误展示成“真实空列表”。
    listError.value = resolvePageErrorMessage(error, '审核队列加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchData()
})

/**
 * 审核页的中文标签已经回收到 product API 模块；
 * 页面这里只保留状态 / 风险对应的视觉样式，避免把颜色规则反向塞回 API 层。
 */
const getStatusBadgeClass = (status: string) => {
  switch (status) {
    case 'APPROVED':
      return 'status-chip-success'
    case 'REJECTED':
      return 'status-chip-danger'
    case 'PENDING':
      return 'status-chip-info'
    default:
      return 'status-chip-muted'
  }
}

/**
 * 风险等级样式映射。
 */
const getRiskBadgeClass = (risk: string) => {
  switch (risk) {
    case 'HIGH':
      return 'status-chip-danger'
    case 'MEDIUM':
      return 'status-chip-warning'
    case 'LOW':
      return 'status-chip-success'
    default:
      return 'status-chip-muted'
  }
}

/**
 * 审核通过。
 */
const handleApprove = async (id: string) => {
  try {
    processingId.value = id
    await approveProductReview(id)
    await fetchData()
  } catch (error) {
    console.warn('Approve product failed.', error)
  } finally {
    processingId.value = ''
  }
}

/**
 * 打开驳回弹窗。
 */
const openRejectModal = (item: ProductReviewItem) => {
  rejectTarget.value = item
  rejectReason.value = ''
  rejectError.value = ''
  isRejectModalOpen.value = true
}

/**
 * 确认驳回。
 */
const confirmReject = async () => {
  if (!rejectTarget.value) return

  const reason = rejectReason.value.trim()
  if (!reason) {
    rejectError.value = '请输入驳回原因'
    return
  }

  try {
    processingId.value = rejectTarget.value.id
    rejectError.value = ''
    await rejectProductReview(rejectTarget.value.id, reason)
    isRejectModalOpen.value = false
    await fetchData()
  } catch (error) {
    console.warn('Reject product failed.', error)
    rejectError.value = '驳回失败，请稍后重试'
  } finally {
    processingId.value = ''
  }
}

/**
 * 详情弹窗当前把“列表摘要”和“违规明细”分开获取：
 * - 列表行已经能覆盖商品标题、分类、状态、风险等级
 * - 进入详情后再按需补抓违规记录，避免列表首屏背太重
 */
const openDetailModal = async (item: ProductReviewItem) => {
  detailTarget.value = item
  productViolations.value = []
  detailError.value = ''
  forceReason.value = ''
  isDetailModalOpen.value = true
  detailLoading.value = true

  try {
    const res = await fetchProductViolations(item.id, 1, 10)
    productViolations.value = res.list || []
  } catch (error: any) {
    detailError.value = error?.message || '拉取违规记录失败'
  } finally {
    detailLoading.value = false
  }
}

/**
 * 强制下架放在详情弹窗里执行，而不直接挂在列表行上：
 * 这样运营能先看到违规记录，再决定是否走人工强制处置。
 */
const handleForceOffShelf = async () => {
  if (!detailTarget.value) return
  const reasonText = forceReason.value.trim()
  if (!reasonText) {
    detailError.value = '请填写强制下架原因'
    return
  }

  try {
    forceOffShelfLoading.value = true
    detailError.value = ''
    await forceOffShelfProduct(detailTarget.value.id, {
      reasonCode: 'manual_review',
      reasonText,
    })
    await fetchData()
    isDetailModalOpen.value = false
  } catch (error: any) {
    detailError.value = error?.message || '强制下架失败'
  } finally {
    forceOffShelfLoading.value = false
  }
}
</script>

<template>
  <div class="space-y-6 max-w-[1600px] mx-auto">
    <section class="card relative overflow-hidden rounded-2xl p-6">
      <div class="absolute inset-y-0 right-0 w-72 bg-gradient-to-l from-orange-50/70 to-transparent pointer-events-none"></div>

      <div class="relative z-10 section-header mb-0">
        <div>
          <div class="section-title-row flex-wrap">
            <h1 class="text-2xl font-bold tracking-tight text-gray-900">商品审核</h1>
            <span class="status-chip status-chip-warning">审核队列</span>
            <span class="status-chip status-chip-muted">
              当前积压
              <span class="font-numeric">{{ totalCount }}</span>
            </span>
          </div>
          <p class="section-desc max-w-3xl">
            面向平台新发布商品的合规性、真实性与价格合理性审核工作台，优先处理高风险品类与异常价格商品。
          </p>

          <div class="mt-3 flex flex-wrap items-center gap-3 text-[12px] text-gray-500">
            <span class="status-chip status-chip-success">当前审核链路正常</span>
            <span class="flex items-center gap-1.5">
              <Clock class="h-3.5 w-3.5" />
              平均审核时效 12 分钟
            </span>
            <span class="flex items-center gap-1.5">
              <ShieldAlert class="h-3.5 w-3.5" />
              重点关注高价数码、奢品与潮鞋
            </span>
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-3">
          <div class="state-banner state-banner-warning px-4 py-3 shadow-none">
            <div class="state-banner-main">
              <span class="state-banner-icon border-orange-200">
                <AlertTriangle class="h-4 w-4 text-orange-600" />
              </span>
              <div>
                <p class="state-banner-title">今日优先项</p>
                <p class="state-banner-text text-orange-700/90">高风险商品优先人工复核，避免异常价格商品进入交易环节。</p>
              </div>
            </div>
          </div>
          <button @click="isRuleModalOpen = true" class="btn-default gap-2">
            审核规则说明
          </button>
        </div>
      </div>
    </section>

    <div class="grid grid-cols-1 items-start gap-6 xl:grid-cols-[minmax(0,1fr)_300px]">
      <div class="space-y-4">
        <section class="filter-bar">
          <div class="filter-bar-group">
            <div class="filter-search">
              <Search class="filter-search-icon" />
              <input
                v-model="searchQuery"
                type="text"
                placeholder="搜索商品名称 / 商品ID / 卖家"
                class="input-standard w-full !pl-9"
              />
            </div>

            <select v-model="selectedStatus" class="input-standard min-w-[156px] bg-white">
              <option value="ALL">全部状态</option>
              <option value="PENDING">待审核</option>
              <option value="APPROVED">已通过</option>
              <option value="REJECTED">已驳回</option>
            </select>

            <button @click="fetchData" class="btn-primary btn-loading px-5" :disabled="loading">
              <Loader2 v-if="loading" class="btn-loading-icon" />
              {{ loading ? '刷新中...' : '查询列表' }}
            </button>
          </div>

          <div class="filter-actions">
            <div class="flex items-center gap-2 rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 text-[12px] text-gray-500">
              <Filter class="h-4 w-4 text-gray-400" />
              支持按关键字与审核状态快速收敛待审队列
            </div>
          </div>
        </section>

        <section class="table-shell relative min-h-[460px] shadow-sm">
          <div class="card-header border-b border-gray-100 bg-gray-50/70 px-5 py-4">
            <div>
              <p class="card-kicker">审核工作区</p>
              <h2 class="card-title">待处理审核队列</h2>
              <p class="section-meta mt-2">按提交时间与风险等级排序，供运营人员逐条处理。</p>
            </div>
            <div class="status-chip status-chip-muted">
              共 <span class="font-numeric">{{ totalCount }}</span> 条记录
            </div>
          </div>

          <div v-if="loading" class="absolute inset-0 z-10 flex items-center justify-center bg-white/82 backdrop-blur-[1px]">
            <div class="state-banner state-banner-info shadow-lg">
              <div class="state-banner-main">
                <span class="state-banner-icon border-blue-200">
                  <Loader2 class="h-4.5 w-4.5 animate-spin text-blue-500" />
                </span>
                <div>
                  <p class="state-banner-title">正在刷新审核队列</p>
                  <p class="state-banner-text text-blue-700/90">请稍候，系统正在同步最新的待审商品记录。</p>
                </div>
              </div>
            </div>
          </div>

          <div v-if="showListErrorBanner" class="border-b border-red-100 bg-red-50/40 px-5 py-4">
            <div class="state-banner state-banner-danger shadow-none">
              <div class="state-banner-body">
                <div class="state-banner-main">
                  <span class="state-banner-icon border-red-200">
                    <AlertTriangle class="h-4 w-4 text-red-500" />
                  </span>
                  <div>
                    <p class="state-banner-title">审核队列刷新失败</p>
                    <p class="state-banner-text text-red-700/90">{{ listError }}</p>
                  </div>
                </div>
                <button class="btn-default shrink-0 px-3 py-1.5 text-[12px]" :disabled="loading" @click="fetchData">
                  重新加载
                </button>
              </div>
            </div>
          </div>

          <div class="overflow-x-auto">
            <table class="table-base">
              <thead>
                <tr class="table-head-row">
                  <th class="table-head-cell min-w-[300px]">商品信息</th>
                  <th class="table-head-cell">卖家</th>
                  <th class="table-head-cell">标价</th>
                  <th class="table-head-cell">风险评估</th>
                  <th class="table-head-cell">提交时间</th>
                  <th class="table-head-cell">审核状态</th>
                  <th class="table-head-cell text-right">处理动作</th>
                </tr>
              </thead>

              <tbody class="table-body">
                <tr
                  v-for="item in products"
                  :key="item.id"
                  class="table-row group align-top"
                >
                  <td class="table-cell">
                    <div class="flex flex-col gap-2">
                      <div class="flex items-start gap-2">
                        <span
                          v-if="item.riskLevel === 'HIGH'"
                          class="mt-1 inline-flex h-2 w-2 shrink-0 rounded-full bg-red-500"
                        ></span>
                        <span class="max-w-[340px] font-medium leading-6 text-gray-900" :title="item.title">
                          {{ item.title }}
                        </span>
                      </div>

                      <div class="flex flex-wrap items-center gap-2 text-[11px] text-gray-500">
                        <span class="status-chip status-chip-muted font-numeric">ID: {{ item.id }}</span>
                        <span class="status-chip status-chip-neutral">{{ item.category }}</span>
                        <span v-if="item.status === 'PENDING'" class="status-chip status-chip-info">待人工复核</span>
                      </div>
                    </div>
                  </td>

                  <td class="table-cell">
                    <div class="space-y-1">
                      <div class="font-medium text-gray-800">{{ item.seller }}</div>
                      <div class="text-[11px] text-gray-500">卖家主体</div>
                    </div>
                  </td>

                  <td class="table-cell">
                    <div class="space-y-1">
                      <div class="font-numeric font-semibold text-gray-900">{{ item.price }}</div>
                      <div class="text-[11px] text-gray-500">当前挂牌价</div>
                    </div>
                  </td>

                  <td class="table-cell">
                    <span class="status-chip" :class="getRiskBadgeClass(item.riskLevel)">
                      <AlertTriangle v-if="item.riskLevel === 'HIGH'" class="h-3 w-3" />
                      {{ getProductReviewRiskText(item.riskLevel) }}
                    </span>
                  </td>

                  <td class="table-cell">
                    <div class="space-y-1">
                      <div class="font-numeric text-gray-700">{{ item.submitTime }}</div>
                      <div class="text-[11px] text-gray-500">进入审核池</div>
                    </div>
                  </td>

                  <td class="table-cell">
                    <span class="status-chip" :class="getStatusBadgeClass(item.status)">
                      {{ getProductReviewStatusText(item.status) }}
                    </span>
                  </td>

                  <td class="table-cell text-right">
                    <div class="flex flex-wrap items-center justify-end gap-2 opacity-100 transition-opacity md:opacity-0 md:group-hover:opacity-100 md:group-focus-within:opacity-100">
                      <button class="btn-default px-3 py-1.5 text-xs" @click="openDetailModal(item)">
                        查看详情
                      </button>
                      <template v-if="item.status === 'PENDING'">
                        <button
                          @click="handleApprove(item.id)"
                          class="inline-flex items-center gap-1 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-1.5 text-xs font-medium text-emerald-700 transition-colors hover:border-emerald-300 hover:bg-emerald-100 disabled:cursor-not-allowed disabled:opacity-60"
                          :disabled="processingId === item.id"
                        >
                          <Loader2 v-if="processingId === item.id" class="h-3.5 w-3.5 animate-spin" />
                          <CheckCircle v-else class="h-3.5 w-3.5" />
                          {{ processingId === item.id ? '处理中...' : '通过' }}
                        </button>

                        <button
                          @click="openRejectModal(item)"
                          class="inline-flex items-center gap-1 rounded-lg border border-red-200 bg-red-50 px-3 py-1.5 text-xs font-medium text-red-700 transition-colors hover:border-red-300 hover:bg-red-100 disabled:cursor-not-allowed disabled:opacity-60"
                          :disabled="processingId === item.id"
                        >
                          <XCircle class="h-3.5 w-3.5" />
                          驳回
                        </button>
                      </template>
                    </div>
                  </td>
                </tr>

                <tr v-if="showListErrorEmptyState">
                  <td colspan="7" class="px-5 py-10">
                    <div class="empty-state empty-state-danger">
                      <AlertTriangle class="empty-state-icon text-red-400" />
                      <p class="empty-state-title">审核队列暂未加载成功</p>
                      <p class="empty-state-text">{{ listError }}</p>
                      <button class="btn-default mt-4" :disabled="loading" @click="fetchData">重新加载</button>
                    </div>
                  </td>
                </tr>

                <tr v-else-if="showListEmptyState">
                  <td colspan="7" class="px-5 py-10">
                    <div class="empty-state empty-state-neutral">
                      <Search class="empty-state-icon" />
                      <p class="empty-state-title">当前没有符合条件的待审商品</p>
                      <p class="empty-state-text">可以尝试调整关键字或审核状态，重新查看审核队列。</p>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="flex items-center justify-between border-t border-gray-100 bg-gray-50/50 px-5 py-3">
            <div class="text-[12px] text-gray-500">
              当前展示 <span class="font-numeric font-medium text-gray-700">{{ products.length }}</span> / {{ totalCount }} 条记录
            </div>

            <div class="flex items-center gap-2">
              <button class="btn-default h-8 min-w-8 px-2.5 py-0 text-xs" disabled>
                &lt;
              </button>
              <button class="btn-primary h-8 min-w-8 px-2.5 py-0 text-xs">
                1
              </button>
              <button class="btn-default h-8 min-w-8 px-2.5 py-0 text-xs" disabled>
                &gt;
              </button>
            </div>
          </div>
        </section>
      </div>

      <aside class="space-y-4">
        <section class="card p-5">
          <div class="card-header">
            <div>
              <p class="card-kicker">审核重点</p>
              <h3 class="card-title">高风险场景提示</h3>
            </div>
            <span class="status-chip status-chip-warning">优先处理</span>
          </div>

          <div class="mt-4 space-y-3">
            <div class="state-banner state-banner-danger shadow-none">
              <div class="state-banner-main">
                <span class="state-banner-icon border-red-200">
                  <AlertTriangle class="h-4 w-4 text-red-500" />
                </span>
                <div>
                  <p class="state-banner-title">品牌防伪</p>
                  <p class="state-banner-text text-red-700/90">重点关注奢侈品、潮鞋、数码 3C 类目，必要时要求补充购买凭证或细节图。</p>
                </div>
              </div>
            </div>

            <div class="state-banner state-banner-warning shadow-none">
              <div class="state-banner-main">
                <span class="state-banner-icon border-orange-200">
                  <Clock class="h-4 w-4 text-orange-500" />
                </span>
                <div>
                  <p class="state-banner-title">价格异常</p>
                  <p class="state-banner-text text-orange-700/90">标价明显低于市场均价的商品，需要重点排查引流诈骗或虚假发货风险。</p>
                </div>
              </div>
            </div>

            <div class="state-banner state-banner-info shadow-none">
              <div class="state-banner-main">
                <span class="state-banner-icon border-blue-200">
                  <ShieldAlert class="h-4 w-4 text-blue-500" />
                </span>
                <div>
                  <p class="state-banner-title">违禁品排查</p>
                  <p class="state-banner-text text-blue-700/90">严禁发布虚拟账号、医疗器械、管制刀具等平台禁售商品。</p>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section class="card bg-gray-50/70 p-5 shadow-none">
          <div class="card-header">
            <div>
              <p class="card-kicker">审核建议</p>
              <h3 class="card-title">处理节奏说明</h3>
            </div>
          </div>

          <div class="mt-4 space-y-3 text-[12px] leading-relaxed text-gray-600">
            <p class="flex items-start gap-2">
              <span class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-gray-400"></span>
              <span>优先处理高风险商品与等待时间较长的记录，避免审核队列积压。</span>
            </p>
            <p class="flex items-start gap-2">
              <span class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-gray-400"></span>
              <span>驳回时请填写明确原因，便于卖家修正信息并保留后续排查依据。</span>
            </p>
            <p class="flex items-start gap-2">
              <span class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-gray-400"></span>
              <span>遇到重大违规或涉嫌违法线索时，需按平台流程及时升级处理。</span>
            </p>
          </div>
        </section>
      </aside>
    </div>

    <Teleport to="body">
      <div v-if="isDetailModalOpen" class="modal-backdrop" @click="isDetailModalOpen = false">
        <div class="modal-panel max-w-2xl flex max-h-[85vh] flex-col" @click.stop>
          <div class="modal-header bg-gray-50/50">
              <div>
                <div class="flex flex-wrap items-center gap-2">
                  <h2 class="modal-title">商品详情</h2>
                  <span v-if="detailTarget" class="status-chip status-chip-muted font-numeric">ID: {{ detailTarget.id }}</span>
                </div>
                <p class="form-helper">查看商品审核结果、近期违规记录与下架状态。</p>
              </div>
            <button @click="isDetailModalOpen = false" class="modal-close">
              <span class="text-xl leading-none">&times;</span>
            </button>
          </div>

          <div class="modal-body overflow-y-auto custom-scrollbar">
            <div v-if="detailLoading" class="state-banner state-banner-info">
              <div class="state-banner-main">
                <span class="state-banner-icon border-blue-200">
                  <Loader2 class="h-4.5 w-4.5 animate-spin text-blue-500" />
                </span>
                <div>
                  <p class="state-banner-title">正在加载商品详情</p>
                  <p class="state-banner-text text-blue-700/90">正在整理该商品的近期违规记录，请稍候。</p>
                </div>
              </div>
            </div>

            <div v-else class="space-y-4">
              <div class="rounded-xl border border-gray-200 bg-gray-50/70 p-4">
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p class="text-sm font-semibold text-gray-900">{{ detailTarget?.title }}</p>
                    <p class="form-helper mt-1">当前状态以最新审核结果为准。</p>
                  </div>
                  <span class="status-chip" :class="detailTarget ? getStatusBadgeClass(detailTarget.status) : 'status-chip-muted'">
                    {{ detailTarget ? getProductReviewStatusText(detailTarget.status) : '--' }}
                  </span>
                </div>
              </div>

              <section class="space-y-3">
                <div class="section-title-row">
                  <h3 class="section-title">近期违规记录</h3>
                </div>
                <div class="rounded-xl border border-gray-200 p-3">
                  <div v-if="productViolations.length > 0" class="space-y-2 text-[12px]">
                    <div v-for="v in productViolations" :key="v.id" class="rounded-lg border border-gray-100 bg-gray-50/60 p-3">
                      <div class="flex flex-wrap items-center justify-between gap-3">
                        <span class="status-chip status-chip-muted">{{ v.violationType || 'UNKNOWN' }}</span>
                        <span class="font-numeric text-gray-500">{{ v.createTime || '--' }}</span>
                      </div>
                      <p class="mt-2 text-gray-500">{{ v.violationDesc || '--' }}</p>
                    </div>
                  </div>
                  <div v-else class="empty-state empty-state-neutral py-6">
                    <Search class="empty-state-icon" />
                    <p class="empty-state-title">近期没有违规记录</p>
                    <p class="empty-state-text">当前商品暂无历史违规记录，可结合商品信息继续判断。</p>
                  </div>
                </div>
              </section>

              <section class="space-y-3 border-t border-gray-100 pt-4">
                <div class="flex flex-wrap items-center gap-2">
                  <h3 class="section-title">强制下架</h3>
                  <span class="status-chip status-chip-danger">谨慎操作</span>
                </div>

                <div v-if="detailTarget?.status === 'REJECTED'" class="state-banner state-banner-warning">
                  <div class="state-banner-main">
                    <span class="state-banner-icon border-orange-200">
                      <AlertTriangle class="h-4 w-4 text-orange-500" />
                    </span>
                    <div>
                      <p class="state-banner-title">当前商品已下架</p>
                      <p class="state-banner-text text-orange-700/90">该商品已完成下架处理，无需重复操作，请先核对历史处理记录。</p>
                    </div>
                  </div>
                </div>

                <div>
                  <label for="product_force_reason" class="form-label">下架原因 <span class="text-red-500">*</span></label>
                  <textarea
                    id="product_force_reason"
                    v-model="forceReason"
                    class="input-standard min-h-[100px] w-full resize-none py-2"
                    placeholder="填写强制下架原因"
                    maxlength="200"
                    :disabled="forceOffShelfLoading || detailTarget?.status === 'REJECTED'"
                  ></textarea>
                  <div class="mt-2 flex items-center justify-between">
                    <span class="text-[12px] text-gray-400"></span>
                    <span class="font-numeric text-[12px] text-gray-400">{{ forceReason.length }}/200</span>
                  </div>
                </div>

                <div v-if="detailError" class="state-banner state-banner-danger">
                  <div class="state-banner-main">
                    <span class="state-banner-icon border-red-200">
                      <AlertTriangle class="h-4 w-4 text-red-500" />
                    </span>
                    <div>
                      <p class="state-banner-title">处理未完成</p>
                      <p class="state-banner-text text-red-700/90">{{ detailError }}</p>
                    </div>
                  </div>
                </div>
              </section>
            </div>
          </div>

          <div class="modal-footer mt-auto">
            <button @click="isDetailModalOpen = false" class="btn-default" :disabled="forceOffShelfLoading">关闭</button>
            <button
              class="btn-danger btn-loading"
              :disabled="forceOffShelfLoading || !forceReason.trim() || detailTarget?.status === 'REJECTED'"
              @click="handleForceOffShelf"
            >
              <Loader2 v-if="forceOffShelfLoading" class="btn-loading-icon" />
              {{ forceOffShelfLoading ? '提交中...' : '确认强制下架' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="isRejectModalOpen" class="modal-backdrop" @click="!processingId && (isRejectModalOpen = false)">
        <div class="modal-panel max-w-md flex flex-col" @click.stop>
          <div class="modal-header bg-gray-50/50">
            <div>
              <h2 class="modal-title">驳回商品审核</h2>
              <p class="form-helper">请填写清晰、明确的驳回原因，便于卖家及时调整商品信息。</p>
            </div>
            <button
              @click="isRejectModalOpen = false"
              class="modal-close"
              :disabled="!!processingId"
            >
              <span class="text-xl leading-none">&times;</span>
            </button>
          </div>

          <div class="modal-body">
            <div class="rounded-xl border border-gray-200 bg-gray-50/70 p-4">
              <p class="text-[12px] text-gray-500">处理商品</p>
              <p class="mt-1 text-sm font-semibold text-gray-900">{{ rejectTarget?.title }}</p>
              <p class="mt-1 font-numeric text-[12px] text-gray-400">ID: {{ rejectTarget?.id }}</p>
            </div>

            <div>
              <label for="product_reject_reason" class="form-label">驳回原因 <span class="text-red-500">*</span></label>
              <textarea
                id="product_reject_reason"
                v-model="rejectReason"
                class="input-standard min-h-[110px] w-full resize-none py-2"
                placeholder="请输入驳回原因，例如图片不清晰、类目错误、信息缺失或违规风险"
                maxlength="200"
                :disabled="!!processingId"
              ></textarea>
              <div class="mt-2 flex items-center justify-between">
                <span v-if="rejectError" class="flex items-center gap-1 text-[12px] text-red-500">
                  <AlertTriangle class="h-3 w-3" />
                  {{ rejectError }}
                </span>
                <span v-else class="text-[12px] text-gray-400"></span>
                <span class="font-numeric text-[12px] text-gray-400">{{ rejectReason.length }}/200</span>
              </div>
            </div>
          </div>

          <div class="modal-footer mt-auto">
            <button @click="isRejectModalOpen = false" class="btn-default" :disabled="!!processingId">取消</button>
            <button
              @click="confirmReject"
              class="btn-danger btn-loading"
              :disabled="!!processingId || !rejectReason.trim()"
            >
              <Loader2 v-if="!!processingId" class="btn-loading-icon" />
              {{ processingId ? '提交中...' : '确认驳回' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 审核规则说明弹窗 -->
    <Teleport to="body">
      <div v-if="isRuleModalOpen" class="modal-backdrop" @click="isRuleModalOpen = false">
        <div class="modal-panel max-w-2xl flex max-h-[85vh] flex-col" @click.stop>
          <div class="modal-header bg-gray-50/50">
            <div>
              <h2 class="modal-title flex items-center gap-2">
                <ShieldAlert class="h-4.5 w-4.5 text-blue-600" />
                商品审核参考要点
              </h2>
              <p class="form-helper">帮助统一高风险商品、异常价格与违规线索的审核判断标准。</p>
            </div>
            <button @click="isRuleModalOpen = false" class="modal-close">
              <span class="text-xl leading-none">&times;</span>
            </button>
          </div>

          <div class="modal-body space-y-6 overflow-y-auto custom-scrollbar">
            <!-- 审核关注重点 -->
            <section>
              <h3 class="text-[14px] font-semibold text-gray-900 mb-3 flex items-center gap-2">
                <span class="w-1.5 h-4 bg-blue-500 rounded-full"></span>
                审核关注重点
              </h3>
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div class="bg-gray-50 rounded-lg p-3 border border-gray-100">
                  <p class="text-[13px] font-medium text-gray-800 mb-1">合规性审查</p>
                  <p class="text-[12px] text-gray-500 leading-relaxed">严禁发布国家法律法规禁止的商品，如管制刀具、违禁药品、涉黄涉暴内容等。</p>
                </div>
                <div class="bg-gray-50 rounded-lg p-3 border border-gray-100">
                  <p class="text-[13px] font-medium text-gray-800 mb-1">真实性核验</p>
                  <p class="text-[12px] text-gray-500 leading-relaxed">重点核查商品图片是否为实拍，描述是否夸大，防范虚假宣传和货不对板。</p>
                </div>
                <div class="bg-gray-50 rounded-lg p-3 border border-gray-100">
                  <p class="text-[13px] font-medium text-gray-800 mb-1">价格合理性</p>
                  <p class="text-[12px] text-gray-500 leading-relaxed">警惕标价异常偏低（引流/诈骗）或异常偏高（洗钱/违规交易）的商品。</p>
                </div>
                <div class="bg-gray-50 rounded-lg p-3 border border-gray-100">
                  <p class="text-[13px] font-medium text-gray-800 mb-1">品牌侵权</p>
                  <p class="text-[12px] text-gray-500 leading-relaxed">对知名品牌、奢侈品需严格审核授权资质或购买凭证，打击山寨假冒。</p>
                </div>
              </div>
            </section>

            <!-- 风险等级处理原则 -->
            <section>
              <h3 class="text-[14px] font-semibold text-gray-900 mb-3 flex items-center gap-2">
                <span class="w-1.5 h-4 bg-orange-500 rounded-full"></span>
                风险等级处理原则
              </h3>
              <div class="space-y-3">
                <div class="flex items-start gap-3 p-3 rounded-lg border border-red-100 bg-red-50/30">
                  <span class="badge bg-red-100 text-red-700 border-red-200 shrink-0 mt-0.5">高风险</span>
                  <div>
                    <p class="text-[13px] font-medium text-gray-900">优先复核，从严把控</p>
                    <p class="text-[12px] text-gray-600 mt-1 leading-relaxed">涉及高客单价（如数码、奢品）、易违规类目或历史违规卖家的商品。需仔细比对图片细节，必要时要求卖家补充凭证。发现违规直接驳回并记录。</p>
                  </div>
                </div>
                <div class="flex items-start gap-3 p-3 rounded-lg border border-orange-100 bg-orange-50/30">
                  <span class="badge bg-orange-100 text-orange-700 border-orange-200 shrink-0 mt-0.5">中风险</span>
                  <div>
                    <p class="text-[13px] font-medium text-gray-900">常规审核，关注细节</p>
                    <p class="text-[12px] text-gray-600 mt-1 leading-relaxed">普通类目但存在轻微信息缺失或描述模糊的商品。需核实核心信息是否完整，若影响交易安全则予以驳回要求修改。</p>
                  </div>
                </div>
                <div class="flex items-start gap-3 p-3 rounded-lg border border-green-100 bg-green-50/30">
                  <span class="badge bg-green-100 text-green-700 border-green-200 shrink-0 mt-0.5">低风险</span>
                  <div>
                    <p class="text-[13px] font-medium text-gray-900">快速放行，提升效率</p>
                    <p class="text-[12px] text-gray-600 mt-1 leading-relaxed">信誉良好卖家发布的常规低价商品，信息完整清晰。确认无明显违规后可快速通过，保障上架时效。</p>
                  </div>
                </div>
              </div>
            </section>

            <!-- 驳回与边界说明 -->
            <section class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div class="border border-gray-200 rounded-lg p-4">
                <h3 class="text-[13px] font-semibold text-gray-900 mb-2 flex items-center gap-1.5">
                  <XCircle class="w-4 h-4 text-red-500" />
                  驳回填写要求
                </h3>
                <ul class="space-y-2 text-[12px] text-gray-600 list-disc pl-4 marker:text-gray-400">
                  <li>必须明确指出具体违规点或缺失信息。</li>
                  <li>语言需客观、专业，避免情绪化表达。</li>
                  <li>若需卖家补充材料，请清晰列出所需凭证类型。</li>
                  <li>驳回原因将直接展示给卖家，请确保准确无误。</li>
                </ul>
              </div>
              <div class="border border-gray-200 rounded-lg p-4 bg-gray-50/50">
                <h3 class="text-[13px] font-semibold text-gray-900 mb-2 flex items-center gap-1.5">
                  <AlertTriangle class="w-4 h-4 text-gray-500" />
                  协同处理提示
                </h3>
                <ul class="space-y-2 text-[12px] text-gray-600 list-disc pl-4 marker:text-gray-400">
                  <li>本页面主要处理商品上架前的初审与复核。</li>
                  <li>如涉及已上架商品的投诉或纠纷，请前往“纠纷与违规”页面继续处理。</li>
                  <li>如发现重大违规或涉嫌违法线索，请及时同步风控团队跟进。</li>
                </ul>
              </div>
            </section>
          </div>

          <div class="modal-footer mt-auto">
            <button @click="isRuleModalOpen = false" class="btn-primary px-6">我知道了</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
