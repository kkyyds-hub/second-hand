import request from '@/utils/request'

export interface PagePayload<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

export interface UserCreditOverview {
  userId: number | null
  creditScore: number | null
  creditLevel: string
  creditLevelText: string
  creditUpdatedAt: string
}

export interface UserCreditLogItem {
  id: number | null
  userId: number | null
  delta: number
  reasonType: string
  reasonText: string
  refId: number | null
  scoreBefore: number | null
  scoreAfter: number | null
  reasonNote: string
  createTime: string
}

export interface UserCreditLogQuery {
  page?: number
  pageSize?: number
}

type UnknownRecord = Record<string, unknown>

const DEFAULT_PAGE = 1
const DEFAULT_PAGE_SIZE = 10

function isRecord(value: unknown): value is UnknownRecord {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function readFirstText(...values: unknown[]) {
  for (const value of values) {
    if (typeof value === 'number' && Number.isFinite(value)) {
      return String(value)
    }

    if (typeof value !== 'string') {
      continue
    }

    const normalized = value.trim()
    if (normalized) {
      return normalized
    }
  }

  return ''
}

function normalizeNumber(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }

  if (typeof value === 'string') {
    const normalized = value.trim()
    if (!normalized) {
      return null
    }

    const parsed = Number(normalized)
    if (Number.isFinite(parsed)) {
      return parsed
    }
  }

  return null
}

function readPositiveInt(fallback: number, ...values: unknown[]) {
  for (const value of values) {
    const normalized = normalizeNumber(value)
    if (normalized !== null && normalized > 0) {
      return Math.trunc(normalized)
    }
  }

  return fallback
}

function readNonNegativeInt(fallback: number, ...values: unknown[]) {
  for (const value of values) {
    const normalized = normalizeNumber(value)
    if (normalized !== null && normalized >= 0) {
      return Math.trunc(normalized)
    }
  }

  return fallback
}

function readIntOrNull(...values: unknown[]) {
  for (const value of values) {
    const normalized = normalizeNumber(value)
    if (normalized !== null) {
      return Math.trunc(normalized)
    }
  }

  return null
}

function readPositiveId(...values: unknown[]) {
  const id = readPositiveInt(-1, ...values)
  return id > 0 ? id : null
}

function readFirstArray(...values: unknown[]) {
  for (const value of values) {
    if (Array.isArray(value)) {
      return value
    }
  }

  return []
}

function normalizePageQuery(query?: UserCreditLogQuery) {
  const page = readPositiveInt(DEFAULT_PAGE, query?.page)
  const pageSize = readPositiveInt(DEFAULT_PAGE_SIZE, query?.pageSize)

  return {
    params: { page, pageSize },
    page,
    pageSize,
  }
}

function formatCreditLevel(level: string) {
  const normalized = level.trim().toLowerCase()
  const levelMap: Record<string, string> = {
    lv1: '较低信用',
    lv2: '基础信用',
    lv3: '良好信用',
    lv4: '优秀信用',
    lv5: '卓越信用',
  }

  return levelMap[normalized] || level || '待确认'
}

function formatReasonType(reasonType: string) {
  const normalized = reasonType.trim().toUpperCase()
  const reasonMap: Record<string, string> = {
    ORDER_COMPLETED: '订单完成',
    AFTER_SALE: '售后处理',
    DISPUTE: '纠纷处理',
    ADMIN_ADJUST: '后台调整',
  }

  return reasonMap[normalized] || reasonType || '信用变动'
}

function normalizeCreditOverview(payload: unknown): UserCreditOverview {
  const source = isRecord(payload) ? payload : {}
  const creditLevel = readFirstText(source.creditLevel, source.level)

  return {
    userId: readPositiveId(source.userId, source.id),
    creditScore: readIntOrNull(source.creditScore, source.score),
    creditLevel,
    creditLevelText: formatCreditLevel(creditLevel),
    creditUpdatedAt: readFirstText(source.creditUpdatedAt, source.updatedAt, source.updateTime),
  }
}

function normalizeCreditLogItem(payload: unknown): UserCreditLogItem {
  const source = isRecord(payload) ? payload : {}
  const reasonType = readFirstText(source.reasonType, source.type)

  return {
    id: readPositiveId(source.id),
    userId: readPositiveId(source.userId),
    delta: Math.trunc(normalizeNumber(source.delta) ?? 0),
    reasonType,
    reasonText: formatReasonType(reasonType),
    refId: readPositiveId(source.refId, source.bizId, source.orderId),
    scoreBefore: readIntOrNull(source.scoreBefore),
    scoreAfter: readIntOrNull(source.scoreAfter),
    reasonNote: readFirstText(source.reasonNote, source.note, source.remark),
    createTime: readFirstText(source.createTime, source.createdAt),
  }
}

function normalizePagePayload<T>(payload: unknown, page: number, pageSize: number, mapper: (item: unknown) => T): PagePayload<T> {
  const source = isRecord(payload) ? payload : {}
  const rawList = readFirstArray(source.list, source.records, source.rows, source.items, Array.isArray(payload) ? payload : undefined)

  return {
    list: rawList.map((item) => mapper(item)),
    total: readNonNegativeInt(rawList.length, source.total, source.totalCount, source.count),
    page: readPositiveInt(page, source.page, source.current, source.pageNum),
    pageSize: readPositiveInt(pageSize, source.pageSize, source.size, source.limit),
  }
}

export function createEmptyCreditOverview(): UserCreditOverview {
  return {
    userId: null,
    creditScore: null,
    creditLevel: '',
    creditLevelText: '待确认',
    creditUpdatedAt: '',
  }
}

export function createEmptyCreditLogPage(): PagePayload<UserCreditLogItem> {
  return {
    list: [],
    total: 0,
    page: DEFAULT_PAGE,
    pageSize: DEFAULT_PAGE_SIZE,
  }
}

export async function getMyCreditOverview() {
  const payload = await request.get<any, unknown>('/user/credit')
  return normalizeCreditOverview(payload)
}

export async function getMyCreditLogs(query?: UserCreditLogQuery) {
  const normalized = normalizePageQuery(query)
  const payload = await request.get<any, unknown>('/user/credit/logs', { params: normalized.params })
  return normalizePagePayload(payload, normalized.page, normalized.pageSize, normalizeCreditLogItem)
}
