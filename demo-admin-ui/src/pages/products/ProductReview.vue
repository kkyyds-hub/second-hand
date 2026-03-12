<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Search, Filter, Loader2, CheckCircle, XCircle, AlertTriangle } from 'lucide-vue-next'

/**
 * 商品审核列表项的类型定义
 */
interface ProductReviewItem {
  id: string
  title: string
  category: string
  seller: string
  price: string
  submitTime: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
}

/**
 * 页面状态
 */
const loading = ref(false)
const searchQuery = ref('')
const selectedStatus = ref('PENDING')

/**
 * 模拟的商品审核数据
 */
const mockProducts = ref<ProductReviewItem[]>([])

/**
 * 模拟获取数据的方法
 */
const fetchData = async () => {
  loading.value = true
  // 模拟网络延迟
  await new Promise(resolve => setTimeout(resolve, 800))
  
  mockProducts.value = [
    {
      id: 'PRD-8902',
      title: 'Apple iPhone 15 Pro Max 256GB 钛金属',
      category: '数码3C',
      seller: '数码回收_老王',
      price: '￥ 6,950',
      submitTime: '2026-03-12 10:30:00',
      status: 'PENDING',
      riskLevel: 'LOW'
    },
    {
      id: 'PRD-8903',
      title: '全新未拆封 劳力士绿水鬼',
      category: '奢侈品',
      seller: 'WatchMaster',
      price: '￥ 125,000',
      submitTime: '2026-03-12 11:15:22',
      status: 'PENDING',
      riskLevel: 'HIGH'
    },
    {
      id: 'PRD-8904',
      title: 'Nike Air Force 1 联名款 42码',
      category: '潮鞋',
      seller: 'Sneaker搬砖人',
      price: '￥ 8,500',
      submitTime: '2026-03-12 14:20:10',
      status: 'PENDING',
      riskLevel: 'MEDIUM'
    },
    {
      id: 'PRD-8890',
      title: 'Sony A7M4 微单套机 95新',
      category: '数码3C',
      seller: '光影流年',
      price: '￥ 13,200',
      submitTime: '2026-03-11 16:45:00',
      status: 'APPROVED',
      riskLevel: 'LOW'
    },
    {
      id: 'PRD-8885',
      title: '高仿 LV 经典老花包',
      category: '箱包',
      seller: '时尚买手',
      price: '￥ 500',
      submitTime: '2026-03-11 09:10:00',
      status: 'REJECTED',
      riskLevel: 'HIGH'
    }
  ]
  
  loading.value = false
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
 * 模拟操作方法
 */
const handleApprove = (id: string) => {
  console.log('Approve product:', id)
  // 实际项目中这里会调用接口并刷新列表
}

const handleReject = (id: string) => {
  console.log('Reject product:', id)
  // 实际项目中这里会调用接口并刷新列表
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
            <tr v-for="item in mockProducts" :key="item.id" class="hover:bg-blue-50/50 transition-colors group">
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
                    <button @click="handleApprove(item.id)" class="text-green-600 hover:text-green-800 font-medium text-xs flex items-center gap-1">
                      <CheckCircle class="w-3.5 h-3.5" /> 通过
                    </button>
                    <button @click="handleReject(item.id)" class="text-red-600 hover:text-red-800 font-medium text-xs flex items-center gap-1">
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
            <tr v-if="mockProducts.length === 0 && !loading">
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
          共 <span class="font-semibold font-numeric text-gray-700">{{ mockProducts.length }}</span> 条记录
        </div>
        <div class="flex space-x-1">
          <button class="px-2.5 py-1 min-w-[32px] border border-gray-300 rounded bg-white text-gray-600 text-sm disabled:opacity-50" disabled><</button>
          <button class="px-2.5 py-1 min-w-[32px] border border-blue-600 bg-blue-50 text-blue-600 font-medium rounded text-sm">1</button>
          <button class="px-2.5 py-1 min-w-[32px] border border-gray-300 rounded bg-white text-gray-600 text-sm disabled:opacity-50" disabled>></button>
        </div>
      </div>
    </div>
  </div>
</template>
