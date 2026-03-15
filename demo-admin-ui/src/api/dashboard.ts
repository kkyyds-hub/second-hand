import request from '../utils/request'
import { isMockEnabled } from '@/mock/config'
import { mockFetchDashboardData } from '@/mock/dashboard'
import { getAuditOverview, type AuditTicketItem } from '@/api/audit'

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
  disputeQueueSource?: 'overview' | 'audit-overview'
}

const buildAuditTicketMeta = (ticket: AuditTicketItem) => {
  const typeLabelMap: Record<AuditTicketItem['type'], string> = {
    DISPUTE: '交易纠纷',
    REPORT: '违规举报',
    RISK: '风控线索',
  }

  const statusLabelMap: Record<AuditTicketItem['status'], string> = {
    PENDING: '待处理',
    PROCESSING: '处理中',
    CLOSED: '已关闭',
  }

  const typeLabel = typeLabelMap[ticket.type] || '审计工单'
  const statusLabel = statusLabelMap[ticket.status] || '处理中'
  return `${typeLabel} · ${statusLabel}`
}

const shortenText = (value?: string, maxLength = 28) => {
  const text = value?.trim()
  if (!text) return ''
  return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text
}

const mapAuditTicketToDisputeItem = (ticket: AuditTicketItem): DisputeItem => ({
  id: ticket.id,
  reason: ticket.title?.trim() || '高优工单待处理',
  target: ticket.target?.trim() || '待确认处理对象',
  user: shortenText(ticket.description, 24) || buildAuditTicketMeta(ticket),
  level: ticket.riskLevel === 'HIGH' ? '紧急' : '中风险',
})

const buildFallbackDisputeQueue = (tickets?: AuditTicketItem[]) => {
  if (!Array.isArray(tickets) || !tickets.length) {
    return []
  }

  return tickets
    .filter((ticket) => ticket.status !== 'CLOSED')
    .filter((ticket) => ticket.riskLevel === 'HIGH' || ticket.type === 'DISPUTE' || ticket.type === 'REPORT')
    .slice(0, 3)
    .map(mapAuditTicketToDisputeItem)
}

/**
 * 查询首页总览数据。
 *
 * 这里不再像之前一样分别调用 4 个接口再在前端拼装，
 * 而是改成直接请求后端聚合好的总览接口。
 */
export async function fetchDashboardData(date?: string): Promise<DashboardData> {
  if (isMockEnabled()) {
    return mockFetchDashboardData()
  }

  const overview = await (request({
    url: '/admin/dashboard/overview',
    method: 'get',
    params: date ? { date } : undefined,
  }) as Promise<DashboardData>)

  if (Array.isArray(overview?.disputeQueue) && overview.disputeQueue.length > 0) {
    return {
      ...overview,
      disputeQueueSource: 'overview',
    }
  }

  try {
    const auditOverview = await getAuditOverview({ riskLevel: 'HIGH' })
    const fallbackDisputeQueue = buildFallbackDisputeQueue(auditOverview?.tickets)

    if (fallbackDisputeQueue.length > 0) {
      return {
        ...overview,
        disputeQueue: fallbackDisputeQueue,
        disputeQueueSource: 'audit-overview',
      }
    }
  } catch (error) {
    console.warn('Dashboard dispute queue audit fallback failed.', error)
  }

  return {
    ...overview,
    disputeQueueSource: 'overview',
  }
}
