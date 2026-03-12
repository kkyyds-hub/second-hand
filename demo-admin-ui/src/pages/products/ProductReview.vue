<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Search, Filter, Loader2, CheckCircle, XCircle, AlertTriangle } from 'lucide-vue-next'
import {
  approveProductReview,
  getProductReviewList,
  rejectProductReview,
  type ProductReviewItem,
} from '@/api/product'

/**
 * 页面状态
 */
const loading = ref(false)
const searchQuery = ref('')
const selectedStatus = ref('PENDING')
const processingId = ref('')
const isRejectModalOpen = ref(false)
const rejectTarget = ref<ProductReviewItem | null>(null)
const rejectReason = ref('')
const rejectError = ref('')

/**
 * 列表数据
 */
const products = ref<ProductReviewItem[]>([])
const totalCount = ref(0)

/**
 * 获取数据
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
 * 状态标签样式映射
 */
const getStatusBadgeClass = (status: string) => {
  switch (status) {
    case 'APPROVED': return 'bg-green-50 text-green-700 border-green-200'
    case 'REJECTED': return 'bg-red-50 text-red-700 border-red-200'
    case 'PENDING': return 'bg-blue-50 text-blue-700 border-blue-200'
    default: return 'bg-gray-50 text-gray-700 border-gray-200'
  }
}

/**
 * 状态中文映射
 */
const getStatusText = (status: string) => {
  switch (status) {
    case 'APPROVED': return '已通过'
    case 'REJECTED': return '已驳回'
    case 'PENDING': return '待审核'
    default: return '未知'
  }
}

/**
 * 风险等级标签样式映射
 */
const getRiskBadgeClass = (risk: string) => {
  switch (risk) {
    case 'HIGH': return 'text-red-600 bg-red-50 border-red-200'
    case 'MEDIUM': return 'text-orange-600 bg-orange-50 border-orange-200'
    case 'LOW': return 'text-green-600 bg-green-50 border-green-200'
    default: return 'text-gray-600 bg-gray-50 border-gray-200'
  }
}

/**
 * 风险等级中文映射
 */
const getRiskText = (risk: string) => {
  switch (risk) {
    case 'HIGH': return '高风险'
    case 'MEDIUM': return '中风险'
    case 'LOW': return '低风险'
    default: return '未知'
  }
}

/**
 * 审核操作
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

const openRejectModal = (item: ProductReviewItem) => {
  rejectTarget.value = item
  rejectReason.value = ''
  rejectError.value = ''
  isRejectModalOpen.value = true
}

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
</script>

<template>
  <div class="space-y-4 max-w-[1600px] mx-auto">
    <!-- 页面标题区 -->
    <div class="flex justify-between items-center pb-2">
      <div>
        <h1 class="text-xl font-bold text-gray-900">商品审核</h1>
        <p class="text-sm text-gray-500 mt-1">对平台新发布的商品进行合规性、真实性及价格审核</p>
      </div>
      <div class="flex space-x-3">
        <button class="btn-default gap-2">审核规则说明</button>
      </div>
    </div>

    <!-- 顶部筛选区 -->
    <div class="card p-4 flex flex-col lg:flex-row gap-4 items-start lg:items-center justify-between">
      <div class="flex flex-wrap gap-3 items-center w-full lg:w-auto">
        <div class="relative w-full sm:w-72">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索商品名称 / ID / 卖家"
            class="input-standard !pl-9 w-full"
          />
        </div>

        <select v-model="selectedStatus" class="input-standard min-w-[140px]">
          <option value="ALL">全部状态</option>
          <option value="PENDING">待审核</option>
          <option value="APPROVED">已通过</option>
          <option value="REJECTED">已驳回</option>
        </select>
        
        <button @click="fetchData" class="btn-primary px-4">查询</button>
      </div>

      <div class="flex space-x-2 shrink-0">
        <button class="btn-default gap-2 text-gray-600">
          <Filter class="w-4 h-4" /> 更多筛选
        </button>
      </div>
    </div>

    <!-- 表格区域 -->
    <div class="card overflow-hidden relative min-h-[400px]">
      <!-- Loading 遮罩 -->
      <div v-if="loading" class="absolute inset-0 z-10 flex items-center justify-center bg-white/60 backdrop-blur-[1px]">
        <div class="flex items-center gap-2 rounded border border-gray-200 bg-white px-4 py-2 text-sm text-gray-600 shadow-sm">
          <Loader2 class="w-4 h-4 animate-spin" /> 数据加载中...
        </div>
      </div>

      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse whitespace-nowrap">
          <thead>
            <tr class="bg-gray-50 border-b border-gray-200 text-sm font-semibold text-gray-700">
              <th class="py-3 px-4 min-w-[250px]">商品信息</th>
              <th class="py-3 px-4">卖家</th>
              <th class="py-3 px-4">标价</th>
              <th class="py-3 px-4">风控评估</th>
              <th class="py-3 px-4">提交时间</th>
              <th class="py-3 px-4">审核状态</th>
              <th class="py-3 px-4 text-right">操作</th>
            </tr>
          </thead>
          <tbody class="text-sm text-gray-700 divide-y divide-gray-100">
            <tr v-for="item in products" :key="item.id" class="hover:bg-blue-50/50 transition-colors group">
              <td class="py-3 px-4">
                <div class="flex flex-col">
                  <span class="font-medium text-gray-900 truncate max-w-[300px]" :title="item.title">{{ item.title }}</span>
                  <div class="flex items-center text-xs text-gray-500 mt-0.5 space-x-2">
                    <span class="font-numeric text-gray-400">{{ item.id }}</span>
                    <span class="px-1.5 py-0.5 bg-gray-100 rounded text-[10px]">{{ item.category }}</span>
                  </div>
                </div>
              </td>

              <td class="py-3 px-4">
                <span class="text-gray-700">{{ item.seller }}</span>
              </td>

              <td class="py-3 px-4 font-numeric font-medium text-gray-900">{{ item.price }}</td>
              
              <td class="py-3 px-4">
                <span class="px-2 py-0.5 rounded text-xs border font-medium flex items-center w-fit gap-1" :class="getRiskBadgeClass(item.riskLevel)">
                  <AlertTriangle v-if="item.riskLevel === 'HIGH'" class="w-3 h-3" />
                  {{ getRiskText(item.riskLevel) }}
                </span>
              </td>

              <td class="py-3 px-4 font-numeric text-gray-500">{{ item.submitTime }}</td>

              <td class="py-3 px-4">
                <span class="px-2 py-1 rounded-sm border text-xs font-medium" :class="getStatusBadgeClass(item.status)">
                  {{ getStatusText(item.status) }}
                </span>
              </td>

              <td class="py-3 px-4 text-right">
                <div class="flex items-center justify-end space-x-3">
                  <template v-if="item.status === 'PENDING'">
                    <button
                      @click="handleApprove(item.id)"
                      class="text-green-600 hover:text-green-800 font-medium text-xs flex items-center gap-1 disabled:opacity-60 disabled:cursor-not-allowed"
                      :disabled="processingId === item.id"
                    >
                      <CheckCircle class="w-3.5 h-3.5" /> 通过
                    </button>
                    <button
                      @click="openRejectModal(item)"
                      class="text-red-600 hover:text-red-800 font-medium text-xs flex items-center gap-1 disabled:opacity-60 disabled:cursor-not-allowed"
                      :disabled="processingId === item.id"
                    >
                      <XCircle class="w-3.5 h-3.5" /> 驳回
                    </button>
                  </template>
                  <template v-else>
                    <button class="text-blue-600 hover:text-blue-800 font-medium text-xs">
                      查看详情
                    </button>
                  </template>
                </div>
              </td>
            </tr>

            <!-- 空状态 -->
            <tr v-if="products.length === 0 && !loading">
              <td colspan="7" class="py-16 text-center">
                <div class="flex flex-col items-center justify-center text-gray-400">
                  <Search class="w-8 h-8 mb-2 opacity-20" />
                  <p>没有找到符合条件的商品</p>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      
      <!-- 简易分页区 -->
      <div class="border-t border-gray-200 px-4 py-3 flex items-center justify-between bg-gray-50/80">
        <div class="text-sm text-gray-500">
          共 <span class="font-semibold font-numeric text-gray-700">{{ totalCount }}</span> 条记录
        </div>
        <div class="flex space-x-1">
          <button class="px-2.5 py-1 min-w-[32px] border border-gray-300 rounded bg-white text-gray-600 text-sm disabled:opacity-50" disabled><</button>
          <button class="px-2.5 py-1 min-w-[32px] border border-blue-600 bg-blue-50 text-blue-600 font-medium rounded text-sm">1</button>
          <button class="px-2.5 py-1 min-w-[32px] border border-gray-300 rounded bg-white text-gray-600 text-sm disabled:opacity-50" disabled>></button>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <div v-if="isRejectModalOpen" class="fixed inset-0 bg-gray-900/40 z-50 flex items-center justify-center">
        <div class="bg-white rounded-md shadow-xl w-full max-w-md border border-gray-200 overflow-hidden flex flex-col" @click.stop>
          <div class="flex justify-between items-center px-6 py-4 border-b border-gray-200">
            <h2 class="text-base font-bold text-gray-800">驳回商品审核</h2>
            <button
              @click="isRejectModalOpen = false"
              class="text-gray-400 hover:text-gray-600"
              :disabled="!!processingId"
            >
              <span class="text-xl leading-none">&times;</span>
            </button>
          </div>

          <div class="p-6 space-y-5">
            <div class="bg-gray-50 p-3 rounded-sm border border-gray-200 flex flex-col gap-1">
              <span class="text-xs text-gray-500">当前商品</span>
              <span class="text-sm font-medium text-gray-900">{{ rejectTarget?.title }}</span>
              <span class="text-xs text-gray-400 font-numeric">ID: {{ rejectTarget?.id }}</span>
            </div>

            <div>
              <label for="product_reject_reason" class="block text-sm font-medium text-gray-700 mb-1.5">
                驳回原因 <span class="text-red-500">*</span>
              </label>
              <textarea
                id="product_reject_reason"
                v-model="rejectReason"
                class="input-standard w-full min-h-[100px] resize-none py-2"
                placeholder="请输入驳回原因，例如图片不清晰、类目错误、涉嫌违禁或信息缺失"
                maxlength="200"
              ></textarea>
              <div class="flex justify-between items-center mt-1">
                <span v-if="rejectError" class="text-xs text-red-500 flex items-center gap-1">
                  <AlertTriangle class="w-3 h-3" /> {{ rejectError }}
                </span>
                <span v-else class="text-xs text-gray-400"></span>
                <span class="text-xs text-gray-400 font-numeric">{{ rejectReason.length }}/200</span>
              </div>
            </div>
          </div>

          <div class="px-6 py-4 border-t border-gray-200 flex justify-end space-x-3 bg-gray-50/50 mt-auto">
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
  </div>
</template>
