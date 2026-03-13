<script setup lang="ts">
import { onMounted, ref } from 'vue'
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
  getProductReviewList,
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
 * 仅保留现有业务逻辑，样式优化不改变审核流程本身。
 */
const loading = ref(false)
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
 * 拉取审核列表。
 */
const fetchData = async () => {
  try {
    loading.value = true
    const res = await getProductReviewList({
      keyword: searchQuery.value || undefined,
      status: selectedStatus.value === 'ALL' ? undefined : (selectedStatus.value as ProductReviewItem['status']),
    })
    products.value = res.items || []
    totalCount.value = res.total || 0
  } catch (error) {
    console.warn('Product review list request failed.', error)
    products.value = []
    totalCount.value = 0
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchData()
})

/**
 * 审核状态样式映射。
 */
const getStatusBadgeClass = (status: string) => {
  switch (status) {
    case 'APPROVED':
      return 'bg-green-50 text-green-700 border-green-200'
    case 'REJECTED':
      return 'bg-red-50 text-red-700 border-red-200'
    case 'PENDING':
      return 'bg-blue-50 text-blue-700 border-blue-200'
    default:
      return 'bg-gray-50 text-gray-700 border-gray-200'
  }
}

/**
 * 审核状态中文映射。
 */
const getStatusText = (status: string) => {
  switch (status) {
    case 'APPROVED':
      return '已通过'
    case 'REJECTED':
      return '已驳回'
    case 'PENDING':
      return '待审核'
    default:
      return '未知'
  }
}

/**
 * 风险等级样式映射。
 */
const getRiskBadgeClass = (risk: string) => {
  switch (risk) {
    case 'HIGH':
      return 'text-red-700 bg-red-50 border-red-200'
    case 'MEDIUM':
      return 'text-orange-700 bg-orange-50 border-orange-200'
    case 'LOW':
      return 'text-green-700 bg-green-50 border-green-200'
    default:
      return 'text-gray-600 bg-gray-50 border-gray-200'
  }
}

/**
 * 风险等级中文映射。
 */
const getRiskText = (risk: string) => {
  switch (risk) {
    case 'HIGH':
      return '高风险'
    case 'MEDIUM':
      return '中风险'
    case 'LOW':
      return '低风险'
    default:
      return '未知'
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
    <!-- 页头区：强化“审核工作台”的场景感，但不新增业务能力。 -->
    <section class="bg-white border border-gray-200/80 rounded-xl p-6 shadow-sm relative overflow-hidden">
      <div class="absolute inset-y-0 right-0 w-72 bg-gradient-to-l from-orange-50/70 to-transparent pointer-events-none"></div>

      <div class="relative z-10 flex flex-col xl:flex-row xl:items-end xl:justify-between gap-5">
        <div class="space-y-3">
          <div class="flex flex-wrap items-center gap-2.5">
            <h1 class="text-2xl font-bold tracking-tight text-gray-900">商品审核</h1>
            <span class="badge bg-orange-50 text-orange-700 border-orange-200">审核队列</span>
            <span class="badge bg-gray-50 text-gray-600 border-gray-200">当前积压 {{ totalCount }}</span>
          </div>

          <p class="text-[14px] leading-relaxed text-gray-500 max-w-3xl">
            面向平台新发布商品的合规性、真实性与价格合理性审核工作台，优先处理高风险品类与异常价格商品。
          </p>

          <div class="flex flex-wrap items-center gap-4 text-[12px] text-gray-500">
            <span class="flex items-center gap-1.5">
              <span class="w-1.5 h-1.5 rounded-full bg-green-500"></span>
              当前审核链路正常
            </span>
            <span class="w-px h-3 bg-gray-300"></span>
            <span class="flex items-center gap-1.5">
              <Clock class="w-3.5 h-3.5" />
              平均审核时效 12 分钟
            </span>
            <span class="w-px h-3 bg-gray-300"></span>
            <span class="flex items-center gap-1.5">
              <ShieldAlert class="w-3.5 h-3.5" />
              重点关注高价数码、奢品与潮鞋
            </span>
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-3">
          <div class="rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 text-[12px] text-gray-600">
            今日优先项：高风险商品人工复核
          </div>
          <button @click="isRuleModalOpen = true" class="btn-default gap-2">审核规则说明</button>
        </div>
      </div>
    </section>

    <div class="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_300px] gap-6 items-start">
      <div class="space-y-4">
        <!-- 工具栏：保持现有筛选逻辑，只做更精致的审核工具条表现。 -->
        <section class="bg-white border border-gray-200/80 rounded-xl p-4 shadow-sm">
          <div class="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
            <div class="flex flex-wrap items-center gap-3">
              <div class="relative w-full sm:w-72">
                <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <input
                  v-model="searchQuery"
                  type="text"
                  placeholder="搜索商品名称 / 商品ID / 卖家"
                  class="input-standard !pl-9 w-full"
                />
              </div>

              <select v-model="selectedStatus" class="input-standard min-w-[144px] bg-white">
                <option value="ALL">全部状态</option>
                <option value="PENDING">待审核</option>
                <option value="APPROVED">已通过</option>
                <option value="REJECTED">已驳回</option>
              </select>

              <button @click="fetchData" class="btn-primary px-5">
                查询列表
              </button>
            </div>

            <div class="flex items-center gap-2 text-[12px] text-gray-500">
              <button class="btn-default gap-2 text-gray-600">
                <Filter class="w-4 h-4" />
                更多筛选
              </button>
            </div>
          </div>
        </section>

        <!-- 主列表：强调“待处理队列”的工作感。 -->
        <section class="bg-white border border-gray-200/80 rounded-xl overflow-hidden shadow-sm relative min-h-[460px]">
          <div class="flex items-center justify-between px-5 py-4 border-b border-gray-100 bg-gray-50/50">
            <div>
              <h2 class="text-[15px] font-semibold text-gray-900">待处理审核队列</h2>
              <p class="text-[12px] text-gray-500 mt-1">按提交时间与风险等级排序，供运营人员逐条处理</p>
            </div>
            <div class="text-[12px] text-gray-500">
              共 <span class="font-medium text-gray-800 font-numeric">{{ totalCount }}</span> 条记录
            </div>
          </div>

          <div v-if="loading" class="absolute inset-0 z-10 flex items-center justify-center bg-white/70 backdrop-blur-[1px]">
            <div class="flex items-center gap-2 rounded-lg border border-gray-200 bg-white px-4 py-2 text-[13px] text-gray-600 shadow-sm">
              <Loader2 class="w-4 h-4 animate-spin" />
              数据加载中...
            </div>
          </div>

          <div class="overflow-x-auto">
            <table class="w-full text-left border-collapse whitespace-nowrap">
              <thead>
                <tr class="bg-gray-50/70 border-b border-gray-100 text-[12px] font-medium text-gray-500">
                  <th class="py-3 px-5 min-w-[280px]">商品信息</th>
                  <th class="py-3 px-4">卖家</th>
                  <th class="py-3 px-4">标价</th>
                  <th class="py-3 px-4">风险评估</th>
                  <th class="py-3 px-4">提交时间</th>
                  <th class="py-3 px-4">审核状态</th>
                  <th class="py-3 px-5 text-right">处理动作</th>
                </tr>
              </thead>

              <tbody class="text-[13px] text-gray-700 divide-y divide-gray-100">
                <tr
                  v-for="item in products"
                  :key="item.id"
                  class="group hover:bg-gray-50/60 transition-colors align-top"
                >
                  <td class="py-4 px-5">
                    <div class="flex flex-col gap-2">
                      <div class="flex items-start gap-2">
                        <span
                          v-if="item.riskLevel === 'HIGH'"
                          class="mt-1 inline-flex h-2 w-2 rounded-full bg-red-500 shrink-0"
                        ></span>
                        <span class="font-medium leading-6 text-gray-900 max-w-[340px]" :title="item.title">
                          {{ item.title }}
                        </span>
                      </div>

                      <div class="flex flex-wrap items-center gap-2 text-[11px] text-gray-500">
                        <span class="px-1.5 py-0.5 rounded border border-gray-200 bg-gray-50 font-numeric">
                          ID: {{ item.id }}
                        </span>
                        <span class="px-1.5 py-0.5 rounded border border-gray-200 bg-white">
                          {{ item.category }}
                        </span>
                        <span v-if="item.status === 'PENDING'" class="px-1.5 py-0.5 rounded border border-blue-100 bg-blue-50 text-blue-700">
                          待人工复核
                        </span>
                      </div>
                    </div>
                  </td>

                  <td class="py-4 px-4">
                    <div class="space-y-1">
                      <div class="text-gray-800 font-medium">{{ item.seller }}</div>
                      <div class="text-[11px] text-gray-500">卖家主体</div>
                    </div>
                  </td>

                  <td class="py-4 px-4">
                    <div class="space-y-1">
                      <div class="font-numeric font-semibold text-gray-900">{{ item.price }}</div>
                      <div class="text-[11px] text-gray-500">当前挂牌价</div>
                    </div>
                  </td>

                  <td class="py-4 px-4">
                    <span class="badge inline-flex items-center gap-1.5" :class="getRiskBadgeClass(item.riskLevel)">
                      <AlertTriangle v-if="item.riskLevel === 'HIGH'" class="w-3 h-3" />
                      {{ getRiskText(item.riskLevel) }}
                    </span>
                  </td>

                  <td class="py-4 px-4">
                    <div class="space-y-1">
                      <div class="font-numeric text-gray-700">{{ item.submitTime }}</div>
                      <div class="text-[11px] text-gray-500">进入审核池</div>
                    </div>
                  </td>

                  <td class="py-4 px-4">
                    <span class="badge" :class="getStatusBadgeClass(item.status)">
                      {{ getStatusText(item.status) }}
                    </span>
                  </td>

                  <td class="py-4 px-5 text-right">
                    <div class="flex items-center justify-end gap-3 opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity">
                      <button class="text-[12px] font-medium text-gray-500 hover:text-gray-800" @click="openDetailModal(item)">
                        查看详情
                      </button>
                      <template v-if="item.status === 'PENDING'">
                        <button
                          @click="handleApprove(item.id)"
                          class="inline-flex items-center gap-1 text-[12px] font-medium text-green-700 hover:text-green-800 disabled:opacity-60 disabled:cursor-not-allowed"
                          :disabled="processingId === item.id"
                        >
                          <CheckCircle class="w-3.5 h-3.5" />
                          通过
                        </button>

                        <button
                          @click="openRejectModal(item)"
                          class="inline-flex items-center gap-1 text-[12px] font-medium text-red-700 hover:text-red-800 disabled:opacity-60 disabled:cursor-not-allowed"
                          :disabled="processingId === item.id"
                        >
                          <XCircle class="w-3.5 h-3.5" />
                          驳回
                        </button>
                      </template>
                    </div>
                  </td>
                </tr>

                <tr v-if="products.length === 0 && !loading">
                  <td colspan="7" class="py-16 text-center">
                    <div class="flex flex-col items-center justify-center text-gray-400">
                      <Search class="w-8 h-8 mb-3 opacity-25" />
                      <p class="text-[13px]">没有找到符合条件的商品</p>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="border-t border-gray-100 px-5 py-3 flex items-center justify-between bg-gray-50/30">
            <div class="text-[12px] text-gray-500">
              共 <span class="font-medium font-numeric text-gray-700">{{ totalCount }}</span> 条记录
            </div>

            <div class="flex items-center gap-1">
              <button class="px-2.5 py-1 min-w-[30px] border border-gray-200 rounded bg-white text-gray-600 text-[12px] disabled:opacity-50" disabled>
                &lt;
              </button>
              <button class="px-2.5 py-1 min-w-[30px] border border-blue-600 bg-blue-600 text-white font-medium rounded text-[12px]">
                1
              </button>
              <button class="px-2.5 py-1 min-w-[30px] border border-gray-200 rounded bg-white text-gray-600 text-[12px] disabled:opacity-50" disabled>
                &gt;
              </button>
            </div>
          </div>
        </section>
      </div>

      <!-- 右侧辅助区：只增强审核语境，不增加新业务逻辑。 -->
      <aside class="space-y-4">
        <section class="bg-white border border-gray-200/80 rounded-xl p-5 shadow-sm">
          <h3 class="text-[14px] font-semibold text-gray-900 flex items-center gap-2 mb-4">
            <AlertTriangle class="w-4 h-4 text-orange-500" />
            审核重点提示
          </h3>

          <div class="space-y-3">
            <div class="rounded-lg border border-red-100 bg-red-50/60 p-3">
              <p class="text-[12px] font-medium text-red-800 mb-1">品牌防伪</p>
              <p class="text-[12px] leading-relaxed text-red-700/80">
                重点关注奢侈品、潮鞋、数码 3C 类目，必要时要求补充购买凭证或细节图。
              </p>
            </div>

            <div class="rounded-lg border border-orange-100 bg-orange-50/60 p-3">
              <p class="text-[12px] font-medium text-orange-800 mb-1">价格异常</p>
              <p class="text-[12px] leading-relaxed text-orange-700/80">
                标价明显低于市场均价的商品，需要重点排查引流诈骗或虚假发货风险。
              </p>
            </div>

            <div class="rounded-lg border border-blue-100 bg-blue-50/60 p-3">
              <p class="text-[12px] font-medium text-blue-800 mb-1">违禁品排查</p>
              <p class="text-[12px] leading-relaxed text-blue-700/80">
                严禁发布虚拟账号、医疗器械、管制刀具等平台禁售商品。
              </p>
            </div>
          </div>
        </section>

        <section class="bg-gray-50/60 border border-gray-200/80 rounded-xl p-5">
          <h3 class="text-[14px] font-semibold text-gray-900 mb-4">审核工作说明</h3>

          <div class="space-y-3 text-[12px] text-gray-600 leading-relaxed">
            <p class="flex items-start gap-2">
              <span class="w-1.5 h-1.5 rounded-full bg-gray-400 mt-1.5 shrink-0"></span>
              <span>优先处理高风险商品与等待时间较长的记录，避免审核队列积压。</span>
            </p>
            <p class="flex items-start gap-2">
              <span class="w-1.5 h-1.5 rounded-full bg-gray-400 mt-1.5 shrink-0"></span>
              <span>驳回时请填写明确原因，便于卖家修正信息并保留后续排查依据。</span>
            </p>
            <p class="flex items-start gap-2">
              <span class="w-1.5 h-1.5 rounded-full bg-gray-400 mt-1.5 shrink-0"></span>
              <span>当前页面仅做审核工作台展示，相关规则说明以平台内部规范为准。</span>
            </p>
          </div>
        </section>
      </aside>
    </div>

    <Teleport to="body">
      <div v-if="isDetailModalOpen" class="fixed inset-0 bg-gray-900/40 backdrop-blur-sm z-50 flex items-center justify-center p-4">
        <div class="bg-white rounded-xl shadow-2xl w-full max-w-2xl border border-gray-200/80 overflow-hidden flex flex-col max-h-[85vh]" @click.stop>
          <div class="flex justify-between items-center px-6 py-4 border-b border-gray-100 bg-gray-50/60">
            <h2 class="text-[15px] font-semibold text-gray-900">
              商品详情联调
              <span v-if="detailTarget" class="ml-2 text-gray-500 font-normal">ID: {{ detailTarget.id }}</span>
            </h2>
            <button @click="isDetailModalOpen = false" class="text-gray-400 hover:text-gray-600 transition-colors p-1 rounded-md hover:bg-gray-100">
              <span class="text-xl leading-none">&times;</span>
            </button>
          </div>

          <div class="p-6 space-y-4 overflow-y-auto">
            <div v-if="detailLoading" class="text-[13px] text-gray-500 flex items-center gap-2">
              <Loader2 class="w-4 h-4 animate-spin" /> 正在加载违规记录...
            </div>
            <div v-else>
              <div class="rounded-lg border border-gray-200 bg-gray-50/60 p-4">
                <p class="text-[13px] font-medium text-gray-900">{{ detailTarget?.title }}</p>
                <p class="text-[12px] text-gray-500 mt-1">状态: {{ detailTarget ? getStatusText(detailTarget.status) : '--' }}</p>
              </div>

              <div class="mt-4">
                <h3 class="text-[13px] font-semibold text-gray-900 mb-2">违规记录（/admin/products/{id}/violations）</h3>
                <div class="rounded-lg border border-gray-200 p-3 text-[12px] space-y-2">
                  <div v-for="v in productViolations" :key="v.id" class="rounded border border-gray-100 bg-gray-50/40 p-2.5">
                    <div class="flex justify-between gap-3">
                      <span class="font-medium text-gray-800">{{ v.violationType || 'UNKNOWN' }}</span>
                      <span class="font-numeric text-gray-500">{{ v.createTime || '--' }}</span>
                    </div>
                    <p class="text-gray-500 mt-1">{{ v.violationDesc || '--' }}</p>
                  </div>
                  <p v-if="productViolations.length === 0" class="text-center text-gray-400 py-1">暂无违规记录</p>
                </div>
              </div>

              <div class="mt-4 border-t border-gray-100 pt-4">
                <h3 class="text-[13px] font-semibold text-gray-900 mb-2">强制下架</h3>
                <p v-if="detailTarget?.status === 'REJECTED'" class="text-[12px] text-orange-600 mb-2">
                  当前商品已是下架状态，禁止重复强制下架。
                </p>
                <textarea
                  v-model="forceReason"
                  class="input-standard w-full min-h-[90px] resize-none py-2"
                  placeholder="填写强制下架原因（必填）"
                  maxlength="200"
                ></textarea>
                <div class="mt-3 flex justify-end">
                  <button
                    class="btn-primary bg-red-600 hover:bg-red-700 border-red-700/50 flex items-center gap-2"
                    :disabled="forceOffShelfLoading || !forceReason.trim() || detailTarget?.status === 'REJECTED'"
                    @click="handleForceOffShelf"
                  >
                    <Loader2 v-if="forceOffShelfLoading" class="w-4 h-4 animate-spin" />
                    {{ forceOffShelfLoading ? '提交中...' : '确认强制下架' }}
                  </button>
                </div>
              </div>

              <p v-if="detailError" class="text-[12px] text-red-600 mt-3">{{ detailError }}</p>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="isRejectModalOpen" class="fixed inset-0 bg-gray-900/40 backdrop-blur-sm z-50 flex items-center justify-center">
        <div class="bg-white rounded-xl shadow-2xl w-full max-w-md border border-gray-200/80 overflow-hidden flex flex-col" @click.stop>
          <div class="flex justify-between items-center px-6 py-4 border-b border-gray-100">
            <h2 class="text-[15px] font-semibold text-gray-900">驳回商品审核</h2>
            <button
              @click="isRejectModalOpen = false"
              class="text-gray-400 hover:text-gray-600 transition-colors p-1 rounded-md hover:bg-gray-100"
              :disabled="!!processingId"
            >
              <span class="text-xl leading-none">&times;</span>
            </button>
          </div>

          <div class="p-6 space-y-5">
            <div class="bg-gray-50 p-3 rounded-lg border border-gray-100 flex flex-col gap-1">
              <span class="text-[12px] text-gray-500">当前商品</span>
              <span class="text-[13px] font-medium text-gray-900">{{ rejectTarget?.title }}</span>
              <span class="text-[12px] text-gray-400 font-numeric">ID: {{ rejectTarget?.id }}</span>
            </div>

            <div>
              <label for="product_reject_reason" class="block text-[13px] font-medium text-gray-700 mb-1.5">
                驳回原因 <span class="text-red-500">*</span>
              </label>
              <textarea
                id="product_reject_reason"
                v-model="rejectReason"
                class="input-standard w-full min-h-[100px] resize-none py-2"
                placeholder="请输入驳回原因，例如图片不清晰、类目错误、涉嫌违禁或信息缺失"
                maxlength="200"
              ></textarea>
              <div class="flex justify-between items-center mt-1.5">
                <span v-if="rejectError" class="text-[12px] text-red-500 flex items-center gap-1">
                  <AlertTriangle class="w-3 h-3" />
                  {{ rejectError }}
                </span>
                <span v-else class="text-[12px] text-gray-400"></span>
                <span class="text-[12px] text-gray-400 font-numeric">{{ rejectReason.length }}/200</span>
              </div>
            </div>
          </div>

          <div class="px-6 py-4 border-t border-gray-100 flex justify-end space-x-3 bg-gray-50/50 mt-auto">
            <button @click="isRejectModalOpen = false" class="btn-default" :disabled="!!processingId">取消</button>
            <button
              @click="confirmReject"
              class="btn-primary bg-red-600 hover:bg-red-700 border-red-700/50 flex items-center gap-2"
              :disabled="!!processingId || !rejectReason.trim()"
            >
              <Loader2 v-if="!!processingId" class="w-4 h-4 animate-spin" />
              {{ processingId ? '提交中...' : '确认驳回' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 审核规则说明弹窗 -->
    <Teleport to="body">
      <div v-if="isRuleModalOpen" class="fixed inset-0 bg-gray-900/40 backdrop-blur-sm z-50 flex items-center justify-center p-4" @click="isRuleModalOpen = false">
        <div class="bg-white rounded-xl shadow-2xl w-full max-w-2xl border border-gray-200/80 overflow-hidden flex flex-col max-h-[85vh]" @click.stop>
          <div class="flex justify-between items-center px-6 py-4 border-b border-gray-100 bg-gray-50/60">
            <h2 class="text-[16px] font-bold text-gray-900 flex items-center gap-2">
              <ShieldAlert class="w-4.5 h-4.5 text-blue-600" />
              商品审核规则说明
            </h2>
            <button @click="isRuleModalOpen = false" class="text-gray-400 hover:text-gray-600 transition-colors p-1 rounded-md hover:bg-gray-100">
              <span class="text-xl leading-none">&times;</span>
            </button>
          </div>

          <div class="p-6 space-y-6 overflow-y-auto custom-scrollbar">
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
                  处理边界说明
                </h3>
                <ul class="space-y-2 text-[12px] text-gray-600 list-disc pl-4 marker:text-gray-400">
                  <li>本页面仅处理商品上架前的初审与复核。</li>
                  <li>已上架商品的举报与纠纷，请前往“纠纷与违规”工作台处理。</li>
                  <li>对于重大违规或涉嫌违法线索，请及时上报风控团队。</li>
                </ul>
              </div>
            </section>
          </div>

          <div class="px-6 py-4 border-t border-gray-100 flex justify-end bg-gray-50/60 mt-auto">
            <button @click="isRuleModalOpen = false" class="btn-primary px-6">我知道了</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
