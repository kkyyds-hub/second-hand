import type { AuditOverviewParams, AuditOverviewResponse, AuditStats, AuditTicketItem } from '@/api/audit'
import { cloneData, mockDelay, readLocalJson, writeLocalJson } from './config'

/**
 * 审核中心 mock：
 * - 用本地存储保留工单状态推进
 * - 让筛选、统计卡、处理动作都能在离线场景下连续验证
 */
const AUDIT_STORAGE_KEY = 'demo_admin_mock_audit_tickets_v1'

// seed 覆盖纠纷 / 举报 / 风险线索三种工单，并带上不同风险与状态。
const auditSeed: AuditTicketItem[] = [
  {
    id: 'AUD-10201',
    type: 'DISPUTE',
    title: '商品描述与实物严重不符',
    target: '订单#T20260312001',
    riskLevel: 'HIGH',
    status: 'PENDING',
    createTime: '2026-03-12T10:40:00',
    description: '买家反馈商品存在明显划痕，卖家描述未提及。',
  },
  {
    id: 'AUD-10202',
    type: 'REPORT',
    title: '疑似售卖假冒奢侈品',
    target: '商品#P88903',
    riskLevel: 'HIGH',
    status: 'PROCESSING',
    createTime: '2026-03-12T09:15:22',
    description: '平台收到多次举报，需人工复核鉴定记录。',
  },
  {
    id: 'AUD-10203',
    type: 'RISK',
    title: '同设备多账号异常发布',
    target: '设备指纹 FA98...21C',
    riskLevel: 'MEDIUM',
    status: 'PENDING',
    createTime: '2026-03-11T18:05:00',
  },
  {
    id: 'AUD-10190',
    type: 'DISPUTE',
    title: '发货超时引发退款争议',
    target: '订单#T20260311078',
    riskLevel: 'LOW',
    status: 'CLOSED',
    createTime: '2026-03-11T11:22:00',
  },
]

function loadTickets() {
  return readLocalJson<AuditTicketItem[]>(AUDIT_STORAGE_KEY, cloneData(auditSeed))
}

function saveTickets(list: AuditTicketItem[]) {
  writeLocalJson(AUDIT_STORAGE_KEY, list)
}

/**
 * 筛选逻辑和真实页保持一致：先按 type/status/risk 精确过滤，再做关键字匹配。
 */
function withFilter(list: AuditTicketItem[], params: AuditOverviewParams) {
  const keyword = (params.keyword || '').trim().toLowerCase()

  return list.filter((ticket) => {
    if (params.type && ticket.type !== params.type) return false
    if (params.status && ticket.status !== params.status) return false
    if (params.riskLevel && ticket.riskLevel !== params.riskLevel) return false

    if (keyword) {
      const text = `${ticket.id} ${ticket.title} ${ticket.target}`.toLowerCase()
      if (!text.includes(keyword)) return false
    }

    return true
  })
}

function calcStats(list: AuditTicketItem[]): AuditStats {
  const todayPrefix = new Date().toISOString().slice(0, 10)
  // 顶部统计卡基于全部工单计算，而不是基于当前筛选结果计算。
  return {
    pendingDisputes: list.filter((it) => it.type === 'DISPUTE' && it.status !== 'CLOSED').length,
    urgentReports: list.filter((it) => it.type === 'REPORT' && it.riskLevel === 'HIGH' && it.status !== 'CLOSED').length,
    platformIntervention: list.filter((it) => it.type === 'DISPUTE' && it.riskLevel === 'HIGH').length,
    todayNewClues: list.filter((it) => it.createTime.startsWith(todayPrefix)).length,
  }
}

export async function mockGetAuditOverview(params: AuditOverviewParams): Promise<AuditOverviewResponse> {
  await mockDelay()
  const all = loadTickets()
  const filtered = withFilter(all, params).sort((a, b) => b.createTime.localeCompare(a.createTime))

  return {
    stats: calcStats(all),
    tickets: cloneData(filtered),
  }
}

export async function mockProcessAuditTicket(ticketId: string): Promise<void> {
  await mockDelay(260)
  const list = loadTickets()
  const item = list.find((it) => it.id === ticketId)
  if (!item) {
    throw new Error('工单不存在')
  }

  if (item.status === 'PENDING') {
    item.status = 'PROCESSING'
  } else if (item.status === 'PROCESSING') {
    item.status = 'CLOSED'
  }

  // mock 只模拟状态推进，足够验证页面上的“处理后刷新列表与角标”交互。
  saveTickets(list)
}
