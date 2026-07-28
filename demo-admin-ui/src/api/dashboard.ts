import request from '../utils/request'

/**
 * Dashboard 页面顶部指标卡的数据结构。
 */
export interface CoreMetric {
  title: string
  value: string | number
  trend: string
  isUp: boolean
  subtext: string
}

/**
 * Dashboard 页面“待审核商品队列”单条记录结构。
 */
export interface ReviewItem {
  id: string
  item: string
  sellerName: string
  type: string
  price: string
  time: string
  risk: string
}

interface ReviewItemPayload extends Partial<ReviewItem> {
  user?: string
}

/**
 * Dashboard 页面“平台介入纠纷队列”单条记录结构。
 */
export interface DisputeItem {
  id: string
  reason: string
  target: string
  user: string
  level: string
}

/**
 * Dashboard 页面右侧“风控预警”单条记录结构。
 */
export interface RiskAlert {
  id: string
  type: string
  target: string
  count: string
}

/**
 * 这是首页总览接口返回给前端的整体结构。
 *
 * 现在后端已经补了一个聚合接口：
 * GET /admin/dashboard/overview
 *
 * 所以前端不需要再自己拼多个接口，直接拿这个总览对象即可。
 */
export interface DashboardData {
  coreMetrics: CoreMetric[]
  reviewQueue: ReviewItem[]
  disputeQueue: DisputeItem[]
  riskAlerts: RiskAlert[]
}

interface DashboardDataPayload extends Omit<DashboardData, 'reviewQueue'> {
  reviewQueue?: ReviewItemPayload[]
}

/**
 * 将后端旧版推测值转为中性兜底文案，避免滚动发布窗口期间出现：
 * - "正常"（旧版在缺失 product/price 时返回）
 *
 * 注意：不映射"刚刚"，因为真实创建不到 1 分钟的商品会合法返回"刚刚"。
 * 时间缺失的"刚刚"应由后端修复（null → 时间未知），不由前端粗暴替换。
 */
const normalizeLegacySpeculativeValue = (field: string, value: string): string => {
  if (field === 'risk' && value === '正常') {
    return '风险未提供'
  }
  return value
}

const normalizeReviewQueue = (reviewQueue?: ReviewItemPayload[]): ReviewItem[] => {
  if (!Array.isArray(reviewQueue)) {
    return []
  }

  return reviewQueue.map((item, index) => ({
    id: item.id?.trim() || `审核队列-${index + 1}`,
    item: item.item?.trim() || '未命名商品',
    // 2026-03-16 起后端 reviewQueue 专门拆出了 sellerName 字段。
    // 这里继续兼容旧 user 字段，避免前后端部署窗口不一致时首页卖家列突然变空。
    sellerName: item.sellerName?.trim() || item.user?.trim() || '未知卖家',
    type: item.type?.trim() || '未分类',
    price: item.price?.trim() || '—',
    time: item.time?.trim() || '时间未知',
    risk: normalizeLegacySpeculativeValue('risk', item.risk?.trim() || '风险未提供'),
  }))
}

const normalizeDashboardData = (overview: DashboardDataPayload): DashboardData => ({
  ...overview,
  reviewQueue: normalizeReviewQueue(overview.reviewQueue),
})

/**
 * 查询首页总览数据。
 *
 * 这里不再像之前一样分别调用 4 个接口再在前端拼装，
 * 而是改成直接请求后端聚合好的总览接口。
 */
export async function fetchDashboardData(date?: string): Promise<DashboardData> {
  const overview = normalizeDashboardData(await (request({
    url: '/admin/dashboard/overview',
    method: 'get',
    params: date ? { date } : undefined,
  }) as Promise<DashboardDataPayload>))

  return overview
}
