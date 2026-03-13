<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  AlertOctagon,
  ArrowDownRight,
  ArrowUpRight,
  Clock,
  Flag,
  Gavel,
  Info,
  Loader2,
  MessageSquareWarning,
  ShieldAlert,
  Zap,
} from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { fetchDashboardData, type CoreMetric, type DisputeItem, type ReviewItem, type RiskAlert } from '@/api/dashboard'
import { fetchHomeStatistics } from '@/api/adminExtra'

const router = useRouter()

const defaultCoreMetrics: [CoreMetric, CoreMetric, CoreMetric, CoreMetric] = [
  { title: '今日成交额(GMV)', value: '¥542.45万', trend: '+15.2%', isUp: true, subtext: '较昨日同时段增加 15.2%' },
  { title: '新增付款订单', value: '4,105', trend: '+8.4%', isUp: true, subtext: '客单价稳定在 ¥ 1,321' },
  { title: '待审异常商品', value: '182', trend: '+12.1%', isUp: false, subtext: '超时未审积压: 14次' },
  { title: '售后争议 & 举报', value: '47', trend: '-5.0%', isUp: true, subtext: '平台强介入违规单: 21单' },
]

const coreMetrics = ref<CoreMetric[]>([...defaultCoreMetrics])

const reviewQueue = ref<ReviewItem[]>([
  { id: '审核-8902', item: 'Apple iPhone 15 Pro Max 256GB 钛金属', user: '数码回收_老王', type: '高价值', price: '¥6,950', time: '10分钟前', risk: '正常' },
  { id: '审核-8903', item: '全新未拆封 劳力士绿水鬼', user: 'WatchMaster', type: '品牌防伪', price: '¥125,000', time: '15分钟前', risk: '高风险' },
  { id: '审核-8904', item: 'Nike Air Force 1 联名款 42码', user: 'Sneaker搬砖人', type: '图片异常', price: '¥8,500', time: '22分钟前', risk: '中风险' },
  { id: '审核-8905', item: 'Sony A7M4 微单套机 95新', user: '光影流年', type: '常规审核', price: '¥13,200', time: '1小时前', risk: '正常' },
])

const disputeQueue = ref<DisputeItem[]>([
  { id: '纠纷-102', reason: '商品描述严重不符（瑕疵未告知）', target: '已签收', user: '买家投诉卖家', level: '紧急' },
  { id: '纠纷-103', reason: '疑似售假 / 鉴定未通过', target: '交易阻断', user: '查验中心拦截', level: '紧急' },
  { id: '纠纷-104', reason: '发货超时 / 虚假发货', target: '退款申请中', user: '买家发起', level: '中风险' },
])

const riskAlerts = ref<RiskAlert[]>([
  { id: '风控-1', type: '同设备多账号批量发布', target: '设备指纹: FA98...21C', count: '14个账号关联' },
  { id: '风控-2', type: '价格严重偏离市场均价', target: 'M2 芯片 MacBook Air 挂牌 800元', count: '已拦截违规展示' },
  { id: '风控-3', type: '支付异常预警', target: '同一卡段高频拉起支付', count: '风控组已介入' },
])

const loading = ref(false)
const statisticsDate = ref(new Date().toISOString().slice(0, 10))
const homeStats = ref({
  dau: 0,
  orderCount: 0,
  gmv: 0,
  publishTotal: 0,
})

const normalizeTitle = (title: string) => title.toLowerCase().replace(/\s+/g, '')

const findMetricByKeywords = (keywords: string[]) => {
  return coreMetrics.value.find((metric) => {
    const title = normalizeTitle(metric?.title || '')
    return keywords.some((keyword) => title.includes(keyword))
  })
}

const gmvMetric = computed(() => {
  return findMetricByKeywords(['gmv', '成交额']) || defaultCoreMetrics[0]
})

const paidOrderMetric = computed(() => {
  const direct =
    findMetricByKeywords(['付款订单', '新增付款', '支付订单']) ||
    coreMetrics.value.find((metric) => {
      const title = normalizeTitle(metric?.title || '')
      return title.includes('订单') && !title.includes('成交') && !title.includes('争议') && !title.includes('举报')
    })
  return direct || defaultCoreMetrics[1]
})

const disputeMetric = computed(() => {
  return findMetricByKeywords(['争议', '举报', '售后', '纠纷']) || defaultCoreMetrics[3]
})

// 局部前端临时趋势占位数据，后续应替换为后端历史序列
const mockTrendData = {
  gmv: [40, 45, 42, 50, 55, 52, 60, 65, 62, 70, 75, 80, 85, 82, 90, 95, 100],
  paidOrder: [20, 22, 21, 25, 24, 28, 27, 30, 32, 31, 35, 34, 38, 40, 42],
  dispute: [15, 14, 16, 13, 12, 14, 11, 10, 12, 9, 8, 10, 7, 6, 5],
}

const buildSparklinePaths = (values: number[], width: number, height: number) => {
  if (!values.length) {
    return { linePath: '', areaPath: '' }
  }

  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1
  const stepX = values.length > 1 ? width / (values.length - 1) : width
  const points = values.map((value, index) => {
    const x = Number((index * stepX).toFixed(2))
    const y = Number((height - ((value - min) / range) * (height - 6) - 3).toFixed(2))
    return { x, y }
  })

  const linePath = points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ')
  const areaPath = `${linePath} L ${width} ${height} L 0 ${height} Z`

  return { linePath, areaPath }
}

const buildBarChartDefs = (values: number[], height: number) => {
  if (!values.length) {
    return []
  }

  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1

  return values.map((value) => {
    const normalized = (value - min) / range
    return Math.max(28, Math.round(28 + normalized * (height - 28)))
  })
}

const sparklineDefs = {
  gmv: buildSparklinePaths(mockTrendData.gmv, 200, 60),
  paidOrder: buildSparklinePaths(mockTrendData.paidOrder, 100, 30),
  dispute: buildSparklinePaths(mockTrendData.dispute, 100, 30),
}

const barChartDefs = {
  paidOrder: buildBarChartDefs(mockTrendData.paidOrder, 100),
  dispute: buildBarChartDefs(mockTrendData.dispute, 100),
}

onMounted(async () => {
  try {
    loading.value = true
    const [res, stats] = await Promise.all([
      fetchDashboardData(),
      fetchHomeStatistics(statisticsDate.value),
    ])

    if (res?.coreMetrics?.length) coreMetrics.value = res.coreMetrics
    if (res?.reviewQueue?.length) reviewQueue.value = res.reviewQueue
    if (res?.disputeQueue?.length) disputeQueue.value = res.disputeQueue
    if (res?.riskAlerts?.length) riskAlerts.value = res.riskAlerts

    homeStats.value = {
      dau: Number(stats?.dau || 0),
      orderCount: Number(stats?.orderGmv?.orderCount || 0),
      gmv: Number(stats?.orderGmv?.gmv || 0),
      publishTotal: Number(stats?.productPublish?.total || 0),
    }
  } catch (error) {
    console.warn('Dashboard API is not fully aligned yet. Showing local mock data.', error)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="space-y-6 max-w-[1600px] mx-auto">
    <div class="bg-white border border-gray-200/80 rounded-xl p-6 shadow-sm mb-6 relative overflow-hidden">
      <div class="absolute top-0 right-0 w-64 h-full bg-gradient-to-l from-blue-50/50 to-transparent pointer-events-none"></div>
      <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 relative z-10">
        <div>
          <div class="flex items-center gap-3 mb-2">
            <h1 class="text-2xl font-bold text-gray-900 tracking-tight">运营中枢</h1>
            <span class="px-2 py-0.5 bg-gray-100 text-gray-600 text-[12px] font-medium rounded border border-gray-200">全局概览</span>
          </div>
          <p class="text-[14px] text-gray-500">
            当前工作焦点：<span class="text-gray-900 font-medium">大促前夕风控排查与高危纠纷清零</span>
          </p>
        </div>
        <div class="flex items-center gap-4">
          <div class="flex flex-col items-end">
            <div class="text-[12px] text-gray-500 flex items-center gap-1.5 mb-1" v-if="loading">
              <Loader2 class="w-3.5 h-3.5 animate-spin" /> 数据同步中...
            </div>
            <div class="text-[12px] text-gray-500 flex items-center gap-1.5 mb-1" v-else>
              <Clock class="w-3.5 h-3.5" /> 数据更新: 刚刚
            </div>
            <span class="flex items-center gap-1.5 text-[12px] text-gray-600 bg-gray-50 px-2 py-1 rounded border border-gray-100">
              <span class="w-1.5 h-1.5 rounded-full bg-green-500"></span> 核心链路正常
            </span>
          </div>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-4 gap-4 mb-6">
      <div class="lg:col-span-2 space-y-4">
        <div class="bg-gray-900 rounded-xl p-6 text-white relative overflow-hidden shadow-md group">
          <div class="absolute -right-10 -top-10 w-40 h-40 bg-white/5 rounded-full blur-2xl"></div>
          
          <!-- GMV Sparkline (Temporary Frontend Mock) -->
          <div class="absolute bottom-0 left-0 w-full h-24 opacity-40 pointer-events-none">
            <svg viewBox="0 0 200 60" preserveAspectRatio="none" class="w-full h-full">
              <defs>
                <linearGradient id="gmvGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#4ade80" stop-opacity="0.4" />
                  <stop offset="100%" stop-color="#4ade80" stop-opacity="0" />
                </linearGradient>
              </defs>
              <path :d="sparklineDefs.gmv.areaPath" fill="url(#gmvGradient)" />
              <path :d="sparklineDefs.gmv.linePath" fill="none" stroke="#4ade80" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </div>

          <div class="relative z-10 flex flex-col h-full justify-between">
            <div class="flex justify-between items-start mb-4">
              <p class="text-[14px] text-gray-300 font-medium">今日成交额(GMV)</p>
              <div class="flex items-center space-x-1 text-[12px] font-medium px-2 py-1 rounded bg-white/10 text-green-400 border border-white/5">
                <ArrowUpRight class="w-3.5 h-3.5" />
                <span class="font-numeric">{{ gmvMetric.trend || '+0.0%' }}</span>
              </div>
            </div>
            <div>
              <p class="text-4xl font-bold font-numeric tracking-tight mb-2">{{ gmvMetric.value }}</p>
              <p class="text-[13px] text-gray-400">{{ gmvMetric.subtext || '暂无趋势数据' }}</p>
            </div>
          </div>
        </div>

        <div class="bg-white border border-gray-200/80 rounded-xl p-4 shadow-sm">
          <div class="flex items-center justify-between flex-wrap gap-3">
            <h2 class="text-[14px] font-semibold text-gray-900">首页统计接口</h2>
            <span class="text-[12px] text-gray-500 font-numeric">date={{ statisticsDate }}</span>
          </div>
          <div class="mt-3 grid grid-cols-2 gap-3">
            <div class="rounded-lg border border-gray-200 p-3 bg-gray-50/40">
              <p class="text-[12px] text-gray-500">DAU</p>
              <p class="text-[18px] font-semibold text-gray-900 font-numeric">{{ homeStats.dau }}</p>
            </div>
            <div class="rounded-lg border border-gray-200 p-3 bg-gray-50/40">
              <p class="text-[12px] text-gray-500">订单数</p>
              <p class="text-[18px] font-semibold text-gray-900 font-numeric">{{ homeStats.orderCount }}</p>
            </div>
            <div class="rounded-lg border border-gray-200 p-3 bg-gray-50/40">
              <p class="text-[12px] text-gray-500">GMV</p>
              <p class="text-[18px] font-semibold text-gray-900 font-numeric">{{ homeStats.gmv }}</p>
            </div>
            <div class="rounded-lg border border-gray-200 p-3 bg-gray-50/40">
              <p class="text-[12px] text-gray-500">发布量</p>
              <p class="text-[18px] font-semibold text-gray-900 font-numeric">{{ homeStats.publishTotal }}</p>
            </div>
          </div>
        </div>
      </div>

      <div class="bg-white border border-gray-200/80 rounded-xl p-5 flex flex-col justify-start gap-6 shadow-sm relative overflow-hidden group min-h-[240px] lg:min-h-[260px] lg:self-start">
        <div class="flex justify-between items-start mb-2 relative z-10">
          <p class="text-[13px] text-gray-600 font-medium">{{ paidOrderMetric.title }}</p>
          <div class="flex items-center space-x-1 text-[11px] font-medium px-1.5 py-0.5 rounded bg-green-50 text-green-600 border border-green-100">
            <ArrowUpRight class="w-3 h-3" />
            <span class="font-numeric">{{ paidOrderMetric.trend || '+0.0%' }}</span>
          </div>
        </div>
        <div class="relative z-10">
          <p class="text-2xl font-semibold text-gray-900 font-numeric tracking-tight mb-1">{{ paidOrderMetric.value || 0 }}</p>
          <p class="text-[12px] text-gray-500">{{ paidOrderMetric.subtext || '暂无补充数据' }}</p>
        </div>
        
        <div class="mt-auto relative z-10 rounded-lg bg-gradient-to-t from-green-50 to-white/40 border border-green-100/70 px-3 py-3">
          <div class="flex items-end gap-1.5 h-14">
            <div
              v-for="(height, index) in barChartDefs.paidOrder"
              :key="`paid-bar-${index}`"
              class="flex-1 rounded-t-sm bg-gradient-to-t from-green-500/70 to-green-300/45"
              :style="{ height: `${height}%` }"
            ></div>
          </div>
        </div>
      </div>

      <div class="bg-white border border-gray-200/80 rounded-xl p-5 flex flex-col justify-start gap-6 shadow-sm relative overflow-hidden group min-h-[240px] lg:min-h-[260px] lg:self-start">
        <div class="flex justify-between items-start mb-2 relative z-10">
          <p class="text-[13px] text-gray-600 font-medium">{{ disputeMetric.title }}</p>
          <div class="flex items-center space-x-1 text-[11px] font-medium px-1.5 py-0.5 rounded bg-red-50 text-red-600 border border-red-100">
            <ArrowDownRight class="w-3 h-3" />
            <span class="font-numeric">{{ disputeMetric.trend || '-0.0%' }}</span>
          </div>
        </div>
        <div class="relative z-10">
          <p class="text-2xl font-semibold text-gray-900 font-numeric tracking-tight mb-1">{{ disputeMetric.value || 0 }}</p>
          <p class="text-[12px] text-gray-500">{{ disputeMetric.subtext || '暂无补充数据' }}</p>
        </div>
        
        <div class="mt-auto relative z-10 rounded-lg bg-gradient-to-t from-red-50 to-white/40 border border-red-100/70 px-3 py-3">
          <div class="flex items-end gap-1.5 h-14">
            <div
              v-for="(height, index) in barChartDefs.dispute"
              :key="`dispute-bar-${index}`"
              class="flex-1 rounded-t-sm bg-gradient-to-t from-red-500/70 to-red-300/45"
              :style="{ height: `${height}%` }"
            ></div>
          </div>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 xl:grid-cols-3 gap-6">
      <div class="xl:col-span-2 space-y-6">
        <div class="bg-white border border-red-200/60 rounded-xl overflow-hidden shadow-sm">
          <div class="bg-red-50/30 border-b border-red-100/60 p-5 flex items-center justify-between">
            <div>
              <h2 class="text-[16px] font-bold text-gray-900 flex items-center gap-2">
                <AlertOctagon class="w-4.5 h-4.5 text-red-500" />
                高优纠纷处理
              </h2>
              <p class="text-[13px] text-gray-500 mt-1.5">SLA 预警工单，需要立即介入裁决</p>
            </div>
            <button @click="router.push('/audit')" class="text-[13px] text-red-600 hover:text-red-700 font-medium transition-colors bg-white border border-red-200 px-4 py-2 rounded-md shadow-sm">
              进入处理中心
            </button>
          </div>
          <div class="divide-y divide-gray-100/80">
            <div v-for="dsp in disputeQueue" :key="dsp.id" class="p-5 flex flex-col sm:flex-row sm:items-center justify-between hover:bg-gray-50/80 transition-colors gap-4 group">
              <div class="flex flex-col">
                <div class="flex items-center gap-3 mb-2">
                  <span class="bg-gray-100 border border-gray-200 text-gray-600 px-2 py-0.5 rounded text-[11px] uppercase font-medium text-numeric leading-none">{{ dsp.id }}</span>
                  <span class="font-medium text-gray-900 text-[14px]">{{ dsp.reason }}</span>
                  <span v-if="dsp.level === '紧急'" class="text-[11px] text-red-600 bg-red-50 border border-red-100 px-2 py-0.5 rounded leading-none">SLA即将超时</span>
                </div>
                <div class="text-[13px] text-gray-500 flex items-center gap-2.5">
                  <MessageSquareWarning class="w-4 h-4 text-gray-400" />
                  <span class="hover:text-gray-700 cursor-pointer">{{ dsp.user }}</span>
                  <span class="w-px h-3.5 bg-gray-300"></span>
                  <span class="text-gray-500">{{ dsp.target }}</span>
                </div>
              </div>
              <button @click="router.push('/audit')" class="bg-gray-900 text-white hover:bg-gray-800 font-medium px-5 py-2 rounded-md text-[13px] transition-colors shadow-sm w-full sm:w-auto shrink-0 opacity-0 group-hover:opacity-100 focus:opacity-100">
                立即处理
              </button>
            </div>
          </div>
        </div>

        <div class="bg-white border border-gray-200/80 rounded-xl overflow-hidden shadow-sm">
          <div class="flex items-center justify-between p-5 border-b border-gray-100">
            <div>
              <h2 class="text-[16px] font-bold text-gray-900">待审核商品队列</h2>
              <p class="text-[13px] text-gray-500 mt-1.5">按风险等级与提交时间排序</p>
            </div>
            <button @click="router.push('/products')" class="text-[13px] text-gray-600 hover:text-gray-900 font-medium transition-colors">查看全部 →</button>
          </div>

          <div class="overflow-x-auto">
            <table class="w-full text-left border-collapse whitespace-nowrap">
              <thead>
                <tr class="bg-gray-50/50 border-b border-gray-100 text-[13px] text-gray-500">
                  <th class="py-3.5 px-5 font-medium">排队任务</th>
                  <th class="py-3.5 px-5 font-medium">发布信息</th>
                  <th class="py-3.5 px-5 font-medium">标价</th>
                  <th class="py-3.5 px-5 font-medium">审核分类</th>
                  <th class="py-3.5 px-5 font-medium text-right">操作</th>
                </tr>
              </thead>
              <tbody class="text-[13px] text-gray-700 divide-y divide-gray-100/80">
                <tr v-for="item in reviewQueue" :key="item.id" class="hover:bg-gray-50/80 transition-colors group">
                  <td class="py-4 px-5">
                    <div class="font-numeric text-gray-900 font-medium flex items-center gap-2">
                      {{ item.id }}
                      <span v-if="item.risk === '高风险'" class="w-2 h-2 rounded-full bg-red-500"></span>
                    </div>
                    <div class="text-gray-400 text-[12px] mt-1.5 flex items-center gap-1.5"><Clock class="w-3.5 h-3.5" />{{ item.time }}提交</div>
                  </td>
                  <td class="py-4 px-5">
                    <div class="font-medium text-gray-900 truncate max-w-[220px]" :title="item.item">{{ item.item }}</div>
                    <div class="text-[12px] text-gray-500 flex items-center gap-2 mt-1.5">
                      <span class="bg-gray-100 border border-gray-200 text-gray-600 px-1.5 py-0.5 rounded text-[11px] leading-none">卖家</span>
                      <span class="hover:text-gray-800 cursor-pointer">{{ item.user }}</span>
                    </div>
                  </td>
                  <td class="py-4 px-5 font-numeric font-medium text-gray-900">{{ item.price }}</td>
                  <td class="py-4 px-5">
                    <span class="badge" :class="{
                      'border-red-200 text-red-700 bg-red-50': item.risk === '高风险',
                      'border-orange-200 text-orange-700 bg-orange-50': item.risk === '中风险',
                      'border-gray-200 text-gray-600 bg-gray-50': item.risk === '正常'
                    }">
                      {{ item.risk }}
                    </span>
                  </td>
                  <td class="py-4 px-5 text-right w-28">
                    <button @click="router.push('/products')" class="bg-white border border-gray-200 text-gray-700 hover:text-gray-900 hover:border-gray-300 font-medium px-4 py-1.5 rounded-md text-[13px] transition-colors shadow-sm opacity-0 group-hover:opacity-100 focus:opacity-100">
                      审阅
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div class="space-y-6">
        <div class="bg-blue-50/50 border border-blue-100 rounded-xl p-5">
          <h3 class="text-[15px] font-bold text-blue-900 mb-3.5 flex items-center gap-2">
            <Info class="w-4.5 h-4.5 text-blue-600" />
            今日运营建议
          </h3>
          <div class="space-y-3.5 text-[13px] text-blue-800/80 leading-relaxed">
            <p>1. <strong>大促预热期：</strong>预计下午 14:00-16:00 迎来商品发布高峰，请提前调度审核人力。</p>
            <p>2. <strong>重点品类监控：</strong>数码 3C 类目近期出现多起“低价引流”违规，请在审核时重点关注价格偏离度。</p>
            <p>3. <strong>SLA 考核：</strong>本周纠纷处理平均时长略有上升，请优先处理即将超时工单。</p>
          </div>
        </div>

        <div class="bg-white border border-gray-200/80 rounded-xl overflow-hidden shadow-sm">
          <div class="bg-gray-50/50 border-b border-gray-100 p-5">
            <div class="flex items-center gap-2">
              <ShieldAlert class="w-4.5 h-4.5 text-gray-500" />
              <h2 class="text-[15px] font-bold text-gray-900">风控引擎实时线索</h2>
            </div>
          </div>
          <div class="p-5 space-y-5">
            <div v-for="alert in riskAlerts" :key="alert.id" class="flex gap-3.5 items-start relative before:absolute before:left-0 before:top-2 before:bottom-0 before:w-0.5 before:bg-gray-200 pl-3.5">
              <div class="absolute left-[-3px] top-2 w-2 h-2 rounded-full bg-white border-2 border-gray-300"></div>
              <div>
                <p class="text-[14px] font-medium text-gray-900 leading-tight">{{ alert.type }}</p>
                <p class="text-[13px] text-gray-500 mt-2">{{ alert.target }}</p>
              </div>
            </div>
          </div>
        </div>

        <div class="bg-white border border-gray-200/80 rounded-xl p-5 shadow-sm">
          <h2 class="text-[15px] font-bold text-gray-900 mb-4">常用工具箱</h2>
          <div class="grid grid-cols-2 gap-3.5">
            <button class="flex flex-col items-center justify-center gap-2.5 p-4 bg-gray-50/50 border border-gray-100 rounded-lg hover:border-gray-300 hover:bg-gray-50 transition-all text-center group">
              <div class="p-2.5 bg-white rounded-md shadow-sm text-gray-500 group-hover:text-gray-700 transition-colors"><Gavel class="w-4.5 h-4.5" /></div>
              <span class="text-[13px] text-gray-600 font-medium">违规处罚库</span>
            </button>
            <button class="flex flex-col items-center justify-center gap-2.5 p-4 bg-gray-50/50 border border-gray-100 rounded-lg hover:border-gray-300 hover:bg-gray-50 transition-all text-center group">
              <div class="p-2.5 bg-white rounded-md shadow-sm text-gray-500 group-hover:text-gray-700 transition-colors"><Flag class="w-4.5 h-4.5" /></div>
              <span class="text-[13px] text-gray-600 font-medium">类目佣金配置</span>
            </button>
            <button class="flex flex-col items-center justify-center gap-2.5 p-4 bg-gray-50/50 border border-gray-100 rounded-lg hover:border-gray-300 hover:bg-gray-50 transition-all text-center group">
              <div class="p-2.5 bg-white rounded-md shadow-sm text-gray-500 group-hover:text-gray-700 transition-colors"><ShieldAlert class="w-4.5 h-4.5" /></div>
              <span class="text-[13px] text-gray-600 font-medium">黑名单管控</span>
            </button>
            <button class="flex flex-col items-center justify-center gap-2.5 p-4 bg-gray-50/50 border border-gray-100 rounded-lg hover:border-gray-300 hover:bg-gray-50 transition-all text-center group">
              <div class="p-2.5 bg-white rounded-md shadow-sm text-gray-500 group-hover:text-gray-700 transition-colors"><Zap class="w-4.5 h-4.5" /></div>
              <span class="text-[13px] text-gray-600 font-medium">活动红包配置</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
