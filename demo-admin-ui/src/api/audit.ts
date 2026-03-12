import request from '@/utils/request'
import { isMockEnabled } from '@/mock/config'
import { mockGetAuditOverview, mockProcessAuditTicket } from '@/mock/audit'

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
 * 纠纷工单处理参数（售后裁决）。
 */
export interface AfterSaleArbitratePayload {
  approved: boolean
  remark?: string
}

/**
 * 举报工单处理参数。
 */
export interface ReportResolvePayload {
  action: 'dismiss' | 'force_off_shelf'
  remark?: string
}

/**
 * 页面级统一处理表单。
 */
export interface AuditActionPayload {
  approved?: boolean
  action?: ReportResolvePayload['action']
  remark?: string
}

/**
 * 获取纠纷与违规页总览数据。
 */
export function getAuditOverview(params: AuditOverviewParams) {
  if (isMockEnabled()) {
    return mockGetAuditOverview(params)
  }

  return request.get<any, AuditOverviewResponse>('/admin/audit/overview', {
    params,
  })
}

/**
 * 页面统一处理入口：
 * - mock 模式：继续推进本地状态
 * - real 模式：
 *   - DISPUTE -> /admin/after-sales/{afterSaleId}/arbitrate
 *   - REPORT  -> /admin/products/reports/{ticketNo}/resolve
 */
export function submitAuditAction(ticket: AuditTicketItem, payload: AuditActionPayload) {
  if (isMockEnabled()) {
    return mockProcessAuditTicket(ticket.id)
  }

  if (ticket.type === 'DISPUTE') {
    if (!ticket.sourceId) {
      return Promise.reject(new Error('当前纠纷工单缺少 sourceId，无法发起平台裁决'))
    }

    if (typeof payload.approved !== 'boolean') {
      return Promise.reject(new Error('请选择裁决结果'))
    }

    const body: AfterSaleArbitratePayload = {
      approved: payload.approved,
      remark: payload.remark?.trim() || undefined,
    }

    return request.put<any, void>(`/admin/after-sales/${ticket.sourceId}/arbitrate`, body)
  }

  if (ticket.type === 'REPORT') {
    if (!ticket.id) {
      return Promise.reject(new Error('当前举报工单缺少 ticketNo，无法提交处理结果'))
    }

    if (!payload.action) {
      return Promise.reject(new Error('请选择举报处理动作'))
    }

    const body: ReportResolvePayload = {
      action: payload.action,
      remark: payload.remark?.trim() || undefined,
    }

    return request.put<any, void>(`/admin/products/reports/${encodeURIComponent(ticket.id)}/resolve`, body)
  }

  return Promise.reject(new Error(`工单 ${ticket.id} 暂未开放统一处理动作`))
}
