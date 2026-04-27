import request from '@/utils/request'

export interface PagePayload<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

export interface PointsLedgerItem {
  id: number | null
  userId: number | null
  bizType: string
  bizId: number | null
  points: number
  createTime: string
}

export interface PointsLedgerQuery {
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

function normalizePageQuery(query?: PointsLedgerQuery) {
  const page = readPositiveInt(DEFAULT_PAGE, query?.page)
  const pageSize = readPositiveInt(DEFAULT_PAGE_SIZE, query?.pageSize)

  return {
    params: { page, pageSize },
    page,
    pageSize,
  }
}

function normalizePointsTotal(payload: unknown) {
  return readNonNegativeInt(0, payload)
}

function normalizePointsLedgerItem(payload: unknown): PointsLedgerItem {
  const source = isRecord(payload) ? payload : {}

  return {
    id: readPositiveId(source.id),
    userId: readPositiveId(source.userId),
    bizType: readFirstText(source.bizType, source.type),
    bizId: readPositiveId(source.bizId, source.orderId, source.refId),
    points: Math.trunc(normalizeNumber(source.points) ?? 0),
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

export function createEmptyPointsLedgerPage(): PagePayload<PointsLedgerItem> {
  return {
    list: [],
    total: 0,
    page: DEFAULT_PAGE,
    pageSize: DEFAULT_PAGE_SIZE,
  }
}

export async function getPointsTotal() {
  const payload = await request.get<any, unknown>('/user/points/total')
  return normalizePointsTotal(payload)
}

export async function getPointsLedger(query?: PointsLedgerQuery) {
  const normalized = normalizePageQuery(query)
  const payload = await request.get<any, unknown>('/user/points/ledger', { params: normalized.params })
  return normalizePagePayload(payload, normalized.page, normalized.pageSize, normalizePointsLedgerItem)
}
