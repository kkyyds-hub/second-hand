import request from '@/utils/request'

/**
 * 纠纷与违规页查询参数。
 */
export interface AuditOverviewParams {
  keyword?: string
  type?: string
  status?: string
  riskLevel?: string
}

/**
 * 单条工单记录。
 */
export interface AuditTicketItem {
  id: string
  type: 'DISPUTE' | 'REPORT' | 'RISK'
  title: string
  target: string
  riskLevel: 'HIGH' | 'MEDIUM' | 'LOW'
  status: 'PENDING' | 'PROCESSING' | 'CLOSED'
  createTime: string
  description?: string
  sourceId?: number
  sourceStatus?: string
}

/**
 * 页面顶部统计卡。
 */
export interface AuditStats {
  pendingDisputes: number
  urgentReports: number
  platformIntervention: number
  todayNewClues: number
}

/**
 * 纠纷与违规页总览返回结构。
 */
export interface AuditOverviewResponse {
  stats: AuditStats
  tickets: AuditTicketItem[]
}

/**
 * 获取纠纷与违规页总览数据。
 */
export function getAuditOverview(params: AuditOverviewParams) {
  return request.get<any, AuditOverviewResponse>('/admin/audit/overview', {
    params,
  })
}
