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
  user: string
  type: string
  price: string
  time: string
  risk: string
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

/**
 * 查询首页总览数据。
 *
 * 这里不再像之前一样分别调用 4 个接口再在前端拼装，
 * 而是改成直接请求后端聚合好的总览接口。
 */
export function fetchDashboardData(date?: string): Promise<DashboardData> {
  return request({
    url: '/admin/dashboard/overview',
    method: 'get',
    params: date ? { date } : undefined,
  }) as Promise<DashboardData>
}
