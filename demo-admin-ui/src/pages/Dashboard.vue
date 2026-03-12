<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ShieldAlert, ArrowUpRight, ArrowDownRight, AlertTriangle, MessageSquareWarning, Clock, Gavel, Zap, Flag, Loader2 } from 'lucide-vue-next'
import { fetchDashboardData, type CoreMetric, type ReviewItem, type DisputeItem, type RiskAlert } from '@/api/dashboard'

// 这是页面的默认 mock 指标数据。
// 作用：在后端接口还没准备好，或者接口请求失败时，页面至少还能正常展示。
const coreMetrics = ref<CoreMetric[]>([
  { title: '今日成交额 (GMV)', value: '￥ 542.45万', trend: '+15.2%', isUp: true, subtext: '较昨日同时段增加 15.2%' },
  { title: '新增付款订单', value: '4,105', trend: '+8.4%', isUp: true, subtext: '客单价稳定在 ￥ 1,321' },
  { title: '待审异常商品', value: '182', trend: '+12.1%', isUp: false, subtext: '超时未审积压: 14款' },
  { title: '售后争议 & 举报', value: '47', trend: '-5.0%', isUp: true, subtext: '平台强介入违规单: 21单' }
])

// 这是首页“待审核商品队列”的默认 mock 数据。
// 等接口联调成功后，会被真实接口返回值覆盖。
const reviewQueue = ref<ReviewItem[]>([
  { id: '审核-8902', item: 'Apple iPhone 15 Pro Max 256GB 钛金属', user: '数码回收_老王', type: '高价值', price: '￥ 6,950', time: '10分钟前', risk: '正常' },
  { id: '审核-8903', item: '全新未拆封 劳力士绿水鬼', user: 'WatchMaster', type: '品牌防伪', price: '￥ 125,000', time: '15分钟前', risk: '高风险' },
  { id: '审核-8904', item: 'Nike Air Force 1 联名款 42码', user: 'Sneaker搬砖人', type: '图片异常', price: '￥ 8,500', time: '22分钟前', risk: '中风险' },
  { id: '审核-8905', item: 'Sony A7M4 微单套机 95新', user: '光影流年', type: '常规审核', price: '￥ 13,200', time: '1小时前', risk: '正常' },
])

// 这是首页“纠纷处理队列”的默认 mock 数据。
// 因为当前后端还没有现成的 Dashboard 聚合接口，这部分先继续走前端兜底。
const disputeQueue = ref<DisputeItem[]>([
  { id: '纠纷-102', reason: '商品描述严重不符 (瑕疵未告知)', target: '已签收', user: '买家投诉卖家', level: '紧急' },
  { id: '纠纷-103', reason: '疑似售假 / 鉴定未通过', target: '交易阻断', user: '查验中心拦截', level: '紧急' },
  { id: '纠纷-104', reason: '发货超时 / 虚假发货', target: '退款申请中', user: '买家发起', level: '中风险' },
])

// 这是右侧风控预警的默认 mock 数据。
const riskAlerts = ref<RiskAlert[]>([
  { id: '风控-1', type: '同设备多账号批量发布', target: '设备指纹: FA98...21C', count: '14 个账号关联' },
  { id: '风控-2', type: '价格严重偏离市场均价', target: 'M2芯片 MacBook Air 挂牌 800元', count: '拦截违规展现' },
  { id: '风控-3', type: '支付异常预警', target: '同一黑卡高频拉起支付', count: '风控组已介入' },
])

// loading 用来控制页面顶部“数据同步中”提示，以及局部加载态展示。
const loading = ref(false)

onMounted(async () => {
  try {
    // 组件一挂载，就先进入加载状态。
    loading.value = true

    // 调用 API 层的聚合函数，尝试拿真实 Dashboard 数据。
    const res = await fetchDashboardData()

    // 如果真实接口成功返回，就用真实数据覆盖掉本地 mock 数据。
    if (res) {
      // 指标卡只有在真实数据数组不为空时才覆盖，避免失败时把原有 mock 清空。
      if (res.coreMetrics?.length) coreMetrics.value = res.coreMetrics
      // 审核队列同理。
      if (res.reviewQueue?.length) reviewQueue.value = res.reviewQueue
      // 纠纷队列目前后端还没聚合好，所以只有在真有数据时才替换。
      if (res.disputeQueue?.length) disputeQueue.value = res.disputeQueue
      // 风控预警如果接口拿到了，就覆盖右侧 mock 数据。
      if (res.riskAlerts?.length) riskAlerts.value = res.riskAlerts
    }
  } catch (error) {
    // 如果接口失败，不让页面崩掉，而是继续显示 mock 数据。
    console.warn('Dashboard API is not fully aligned yet. Showing local mock data.', error)
  } finally {
    // 不管成功还是失败，最后都要结束加载状态。
    loading.value = false
  }
})
</script>

<template>
  <div class="space-y-6 max-w-[1600px] mx-auto">
    
    <!-- Page Header (Operational Standard) -->
    <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 bg-white p-5 rounded-sm border border-gray-200">
      <div>
        <h1 class="text-xl font-bold text-gray-900">平台运营总览与调度</h1>
        <p class="text-sm text-gray-500 mt-1">C2C交易大盘及安全风控核心工作台。 <span class="text-blue-600 font-medium">当前有 28 项紧急待办</span></p>
      </div>
      <div class="flex items-center space-x-3">
        <div class="text-xs text-gray-500 mr-2 flex items-center gap-1.5" v-if="loading">
          <Loader2 class="w-3.5 h-3.5 animate-spin"/> 数据同步中...
        </div>
        <div class="text-xs text-gray-500 mr-2 flex items-center gap-1.5" v-else>
          <Clock class="w-3.5 h-3.5"/> 数据更新: 刚刚
        </div>
        <select class="input-standard bg-gray-50 min-w-[120px] font-medium text-gray-700">
          <option>今日实时</option>
          <option>昨日全天</option>
          <option>近7天趋势</option>
        </select>
        <button class="btn-default bg-white hidden sm:flex">导出看板</button>
      </div>
    </div>

    <!-- Emergency Priorities Top Bar -->
    <div class="bg-red-50 border border-red-200 p-4 rounded-sm flex flex-col sm:flex-row items-start sm:items-center justify-between shadow-sm gap-4">
      <div class="flex items-start sm:items-center space-x-3">
        <AlertTriangle class="w-5 h-5 text-red-600 animate-pulse shrink-0 mt-0.5 sm:mt-0" />
        <div class="flex flex-col sm:flex-row sm:items-center gap-1 sm:gap-4">
          <span class="font-bold text-red-900 border-b pb-2 mb-1 sm:border-none sm:pb-0 sm:mb-0 sm:border-r border-red-200 sm:pr-4">今日待处理重点 (SLA 预警)</span>
          <div class="flex flex-wrap items-center gap-4 text-sm">
            <p class="text-red-800">紧急纠纷待查: <span class="font-bold font-numeric text-red-600">12 单</span></p>
            <p class="text-red-800">超时未审高危物: <span class="font-bold font-numeric text-red-600">14 个</span></p>
            <p class="text-red-800">1小时内新增风控告警: <span class="font-bold font-numeric text-red-600">3 条</span></p>
          </div>
        </div>
      </div>
      <button class="btn-primary bg-red-600 hover:bg-red-700 font-medium text-white px-4 border border-red-700/50 shrink-0">一键分派处理</button>
    </div>

    <!-- Core Metrics (Grid) -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <div 
        v-for="stat in coreMetrics" 
        :key="stat.title"
        class="card p-5 flex flex-col hover:border-blue-200 transition-colors"
      >
        <p class="text-sm text-gray-600 font-medium mb-3">{{ stat.title }}</p>
        <div class="flex items-end justify-between mt-auto mb-2">
          <p class="text-3xl font-bold text-gray-900 font-numeric tracking-tight">
            {{ stat.value }}
          </p>
          <div class="flex items-center space-x-1 text-xs font-semibold px-2 py-0.5 rounded bg-gray-50" :class="stat.isUp ? 'text-red-600' : 'text-green-600'">
            <component :is="stat.isUp ? ArrowUpRight : ArrowDownRight" class="w-3 h-3" />
            <span class="font-numeric">{{ stat.trend }}</span>
          </div>
        </div>
        <div class="text-[11px] text-gray-500 font-numeric border-t border-gray-100 pt-2">{{ stat.subtext }}</div>
      </div>
    </div>

    <!-- Main Workspace -->
    <div class="grid grid-cols-1 xl:grid-cols-3 gap-6">
      
      <!-- Left Column: Primary Workflows (2/3 width) -->
      <div class="xl:col-span-2 space-y-6">
        
        <!-- Product Review Queue Table -->
        <div class="card p-0 overflow-hidden flex flex-col">
           <div class="flex items-center justify-between p-4 px-5 border-b border-gray-200 bg-white">
            <h2 class="text-base font-bold text-gray-800 flex items-center gap-2">
              待审核商品队列 
              <span class="bg-blue-100 text-blue-700 text-[11px] px-2 py-0.5 rounded font-bold font-numeric">182</span>
            </h2>
            <button class="text-sm text-blue-600 hover:text-blue-800 font-medium flex gap-1 items-center">
              进入审核后台 <ArrowUpRight class="w-4 h-4"/>
            </button>
          </div>
          
          <div class="overflow-x-auto">
            <table class="w-full text-left border-collapse whitespace-nowrap">
              <thead>
                <tr class="bg-gray-50 border-b border-gray-200 text-xs text-gray-500 uppercase tracking-wider">
                  <th class="py-3 px-5 font-semibold">排队任务</th>
                  <th class="py-3 px-5 font-semibold">发布信息</th>
                  <th class="py-3 px-5 font-semibold">标价</th>
                  <th class="py-3 px-5 font-semibold">审核分类</th>
                  <th class="py-3 px-5 font-semibold text-right">人工操作</th>
                </tr>
              </thead>
              <tbody class="text-sm text-gray-700 divide-y divide-gray-100">
                <tr v-for="item in reviewQueue" :key="item.id" class="hover:bg-blue-50/50 transition-colors">
                  <td class="py-3 px-5">
                    <div class="font-numeric text-gray-500 text-xs">{{ item.id }}</div>
                    <div class="text-gray-400 text-[11px]">{{ item.time }}提交</div>
                  </td>
                  <td class="py-3 px-5">
                    <div class="font-medium text-gray-900 truncate max-w-[200px]" :title="item.item">{{ item.item }}</div>
                    <div class="text-xs text-gray-500 flex items-center gap-1"><span class="bg-gray-100 text-gray-600 px-1 rounded text-[10px]">卖家</span> {{ item.user }}</div>
                  </td>
                  <td class="py-3 px-5 font-numeric font-bold text-gray-800">{{ item.price }}</td>
                  <td class="py-3 px-5">
                     <span class="px-2 py-0.5 rounded text-xs border font-medium" :class="{
                       'border-red-200 text-red-700 bg-red-50': item.risk === '高风险',
                       'border-orange-200 text-orange-700 bg-orange-50': item.risk === '中风险',
                       'border-green-200 text-green-700 bg-green-50': item.risk === '正常'
                     }">
                       {{ item.risk }}
                     </span>
                  </td>
                  <td class="py-3 px-5 text-right w-24">
                    <button class="bg-white border border-gray-300 text-gray-700 hover:text-blue-600 hover:border-blue-400 font-medium px-3 py-1 rounded text-xs transition-colors shadow-sm">
                      审阅
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Dispute Handling Queue -->
        <div class="card p-0 overflow-hidden">
           <div class="flex items-center justify-between p-4 px-5 border-b border-gray-200 bg-white">
            <h2 class="text-base font-bold text-gray-800 flex items-center gap-2">
              平台介入与纠纷 (SLA即将超标)
              <span class="bg-red-100 text-red-700 text-[11px] px-2 py-0.5 rounded font-bold font-numeric">12单紧急</span>
            </h2>
            <button class="text-sm text-blue-600 hover:text-blue-800 font-medium">分配仲裁客服</button>
          </div>
          <div class="divide-y divide-gray-100">
            <div v-for="dsp in disputeQueue" :key="dsp.id" class="p-4 px-5 flex flex-col sm:flex-row sm:items-center justify-between hover:bg-gray-50 transition-colors gap-4">
              <div class="flex flex-col">
                <div class="flex items-center gap-2 mb-1">
                  <span class="bg-red-100 text-red-700 px-1.5 py-0.5 rounded text-[10px] uppercase font-bold text-numeric">{{ dsp.id }}</span>
                  <span class="font-semibold text-gray-900 text-sm">{{ dsp.reason }}</span>
                </div>
                <div class="text-xs text-gray-500 flex items-center gap-2">
                   <MessageSquareWarning class="w-3.5 h-3.5 text-gray-400" />
                   <span>{{ dsp.user }}</span>
                   <span class="px-1.5 bg-gray-100 rounded text-gray-600">{{ dsp.target }}</span>
                </div>
              </div>
              <button class="btn-primary text-xs w-full sm:w-auto shrink-0 bg-blue-600 shadow-sm border border-transparent">立即处理</button>
            </div>
          </div>
        </div>

      </div>

      <!-- Right Column: Secondary & Alerts (1/3 width) -->
      <div class="space-y-6">
        
        <!-- Risk Intelligence Alerts -->
        <div class="card overflow-hidden">
          <div class="bg-orange-50 border-b border-orange-100 p-4 flex items-center gap-2">
            <ShieldAlert class="w-5 h-5 text-orange-600" />
            <h2 class="text-base font-bold text-orange-900">大盘风控引擎预警</h2>
          </div>
          <div class="p-4 space-y-4">
            <div v-for="alert in riskAlerts" :key="alert.id" class="flex gap-3 items-start border-l-2 border-orange-400 pl-3">
              <div class="pt-0.5">
                <p class="text-sm font-semibold text-gray-900">{{ alert.type }}</p>
                <div class="flex flex-col text-xs text-gray-500 mt-1 gap-1">
                  <span class="text-gray-700 bg-gray-100 px-1 py-0.5 w-fit rounded font-numeric">{{ alert.target }}</span>
                  <span class="text-orange-600 font-medium">{{ alert.count }}</span>
                </div>
              </div>
            </div>
          </div>
          <div class="bg-gray-50 p-3 border-t border-gray-100 flex justify-center">
            <button class="text-blue-600 text-sm font-medium hover:underline">查看完整安全日志</button>
          </div>
        </div>

        <!-- Quick Op Actions -->
        <div class="card p-5">
           <h2 class="text-base font-bold text-gray-800 mb-4">高频运营直达</h2>
           <div class="grid grid-cols-2 gap-3">
             <button class="flex items-center gap-3 p-3 bg-white border border-gray-200 rounded hover:bg-blue-50 hover:border-blue-200 transition-all text-left shadow-sm">
               <div class="bg-blue-100 p-2 rounded text-blue-600"><Gavel class="w-4 h-4"/></div>
               <span class="text-sm text-gray-700 font-medium">违规处罚库</span>
             </button>
             <button class="flex items-center gap-3 p-3 bg-white border border-gray-200 rounded hover:bg-blue-50 hover:border-blue-200 transition-all text-left shadow-sm">
               <div class="bg-purple-100 p-2 rounded text-purple-600"><Flag class="w-4 h-4"/></div>
               <span class="text-sm text-gray-700 font-medium">类目佣金配置</span>
             </button>
             <button class="flex items-center gap-3 p-3 bg-white border border-gray-200 rounded hover:bg-blue-50 hover:border-blue-200 transition-all text-left shadow-sm">
               <div class="bg-green-100 p-2 rounded text-green-600"><ShieldAlert class="w-4 h-4"/></div>
               <span class="text-sm text-gray-700 font-medium">黑名单管控</span>
             </button>
             <button class="flex items-center gap-3 p-3 bg-white border border-gray-200 rounded hover:bg-blue-50 hover:border-blue-200 transition-all text-left shadow-sm">
               <div class="bg-orange-100 p-2 rounded text-orange-600"><Zap class="w-4 h-4"/></div>
               <span class="text-sm text-gray-700 font-medium">发红包/券</span>
             </button>
           </div>
        </div>

        <!-- System Note -->
        <div class="bg-gray-100 rounded border border-gray-200 p-4 text-sm text-gray-600 flex gap-3 shadow-inner">
           <AlertTriangle class="w-5 h-5 text-gray-400 shrink-0" />
           <p class="leading-relaxed">因【苹果秋季发布会】临近，平台数码3C品类预计迎来挂牌高峰。请审核部门注意人力调度，风控组严查假冒首发机型诈骗。</p>
        </div>

      </div>
    </div>
  </div>
</template>
