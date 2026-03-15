import request from '@/utils/request'
import { isMockEnabled } from '@/mock/config'
import { mockGetAuditOverview, mockProcessAuditTicket } from '@/mock/audit'

/**
 * 审核中心 API 收口文件：
 * - 页面统一消费工单总览 + 工单处理入口
 * - 不同工单类型对应的真实后端路由差异由这里兜底
 */
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
 * sourceId/sourceStatus 主要给纠纷类工单做真实后端联调时使用。
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
 * 这三组 helper 把后端枚举翻译成页面稳定可复用的中文文案。
 * Day08 之后，页面不再各自 switch `DISPUTE / REPORT / RISK` 这些协议值，
 * 如果后端新增枚举或改口径，只需要回到 audit API 模块统一调整。
 */
export function getAuditTypeLabel(type?: AuditTicketItem['type'] | string) {
  switch (type) {
    case 'DISPUTE':
      return '交易纠纷'
    case 'REPORT':
      return '违规举报'
    case 'RISK':
      return '风控线索'
    default:
      return '未知'
  }
}

export function getAuditRiskLabel(level?: AuditTicketItem['riskLevel'] | string) {
  switch (level) {
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

export function getAuditStatusLabel(status?: AuditTicketItem['status'] | string) {
  switch (status) {
    case 'PENDING':
      return '待处理'
    case 'PROCESSING':
      return '处理中'
    case 'CLOSED':
      return '已关闭'
    default:
      return '未知'
  }
}

export interface AuditProcessMeta {
  canProcess: boolean
  blockedReason: string
  blockedLabel: string
  title: string
  description: string
  confirmText: string
}

/**
 * 统一收口“当前工单能不能处理”以及对应弹窗 copy。
 *
 * 这里显式依赖两个真实后端字段：
 * - DISPUTE 依赖 `sourceId`，因为 real 模式会调用 `/admin/after-sales/{sourceId}/arbitrate`
 * - REPORT 依赖 `ticket.id`，因为 real 模式会调用 `/admin/products/reports/{ticketNo}/resolve`
 *
 * 这样页面只消费结构化结果，不再自己分散维护 blocked reason / confirm text / title。
 */
export function getAuditProcessMeta(ticket?: AuditTicketItem | null): AuditProcessMeta {
  if (!ticket) {
    return {
      canProcess: false,
      blockedReason: '',
      blockedLabel: '暂缓处理',
      title: '处理工单',
      description: '提交处理结果后，页面列表将自动刷新。',
      confirmText: '确认提交',
    }
  }

  const baseMeta =
    ticket.type === 'DISPUTE'
      ? {
          title: '处理交易纠纷',
          description: '平台裁决会同步写入售后处理结果，并更新当前工单状态。',
          confirmText: '确认提交裁决',
        }
      : ticket.type === 'REPORT'
      ? {
          title: '处理违规举报',
          description: '举报处理会写入工单结论，必要时联动商品强制下架。',
          confirmText: '确认提交处理',
        }
      : {
          title: '处理工单',
          description: '提交处理结果后，页面列表将自动刷新。',
          confirmText: '确认提交',
        }

  if (ticket.status === 'CLOSED') {
    return {
      ...baseMeta,
      canProcess: false,
      blockedReason: '当前工单已完成处理',
      blockedLabel: '已完成',
    }
  }

  if (ticket.type === 'DISPUTE' && !ticket.sourceId) {
    return {
      ...baseMeta,
      canProcess: false,
      blockedReason: '当前纠纷关键信息待补充，请稍后再处理',
      blockedLabel: '待补充',
    }
  }

  if (ticket.type === 'REPORT' && !ticket.id) {
    return {
      ...baseMeta,
      canProcess: false,
      blockedReason: '当前举报信息待补充，请稍后再处理',
      blockedLabel: '待补充',
    }
  }

  if (ticket.type === 'RISK') {
    return {
      ...baseMeta,
      canProcess: false,
      blockedReason: '该风险线索仍在持续观察，暂不需要人工处置',
      blockedLabel: '继续观察',
    }
  }

  return {
    ...baseMeta,
    canProcess: true,
    blockedReason: '',
    blockedLabel: '',
  }
}

/**
 * 获取纠纷与违规页总览数据。
 * 无论 mock 还是真实接口，页面都拿同一份 AuditOverviewResponse。
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
 *   - RISK    -> 当前仅展示，不开放统一处理提交
 */
export function submitAuditAction(ticket: AuditTicketItem, payload: AuditActionPayload) {
  if (isMockEnabled()) {
    return mockProcessAuditTicket(ticket.id)
  }

  if (ticket.type === 'DISPUTE') {
    // 纠纷工单必须拿到真实 after-sale 主键，才能提交平台裁决。
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
    // 举报工单按 ticketNo 提交处理动作，和纠纷裁决走的是完全不同的后端链路。
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
