import request from '@/utils/request'

export interface PagePayload<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

export interface WalletBalance {
  balance: string
  balanceNumber: number | null
}

export interface WalletTransaction {
  id: number | null
  userId: number | null
  bizType: string
  bizId: number | null
  amount: string
  amountNumber: number | null
  balanceAfter: string
  balanceAfterNumber: number | null
  remark: string
  createTime: string
}

export interface WalletTransactionQuery {
  page?: number
  pageSize?: number
}

export interface WalletWithdrawInput {
  amount: number | string
  bankCardNo: string
}

export interface WalletWithdrawResult {
  requestId: number | null
  message: string
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

function normalizeMoney(value: unknown) {
  const text = readFirstText(value)
  if (text) {
    return text
  }

  const numberValue = normalizeNumber(value)
  return numberValue === null ? '0.00' : numberValue.toFixed(2)
}

function normalizePageQuery(query?: WalletTransactionQuery) {
  const page = readPositiveInt(DEFAULT_PAGE, query?.page)
  const pageSize = readPositiveInt(DEFAULT_PAGE_SIZE, query?.pageSize)

  return {
    params: { page, pageSize },
    page,
    pageSize,
  }
}

function normalizeWalletBalance(payload: unknown): WalletBalance {
  const balance = normalizeMoney(payload)

  return {
    balance,
    balanceNumber: normalizeNumber(balance),
  }
}

function normalizeWalletTransaction(payload: unknown): WalletTransaction {
  const source = isRecord(payload) ? payload : {}
  const amount = normalizeMoney(source.amount)
  const balanceAfter = normalizeMoney(source.balanceAfter)

  return {
    id: readPositiveId(source.id),
    userId: readPositiveId(source.userId),
    bizType: readFirstText(source.bizType, source.type),
    bizId: readPositiveId(source.bizId, source.orderId, source.refId),
    amount,
    amountNumber: normalizeNumber(amount),
    balanceAfter,
    balanceAfterNumber: normalizeNumber(balanceAfter),
    remark: readFirstText(source.remark, source.note, source.description),
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

function normalizeWithdrawInput(input: WalletWithdrawInput) {
  const amount = normalizeNumber(input.amount)
  const bankCardNo = readFirstText(input.bankCardNo)

  if (amount === null || amount < 0.01) {
    throw new Error('提现金额最小为 0.01。')
  }

  if (bankCardNo.length < 4 || bankCardNo.length > 32) {
    throw new Error('银行卡号长度需在 4~32 个字符之间。')
  }

  return {
    amount,
    bankCardNo,
  }
}

function normalizeWithdrawResult(payload: unknown): WalletWithdrawResult {
  if (typeof payload === 'number' && Number.isFinite(payload)) {
    return {
      requestId: payload > 0 ? Math.trunc(payload) : null,
      message: '提现申请已提交。',
    }
  }

  const source = isRecord(payload) ? payload : {}
  return {
    requestId: readPositiveId(payload, source.requestId, source.id),
    message: readFirstText(source.message, source.msg, source.result) || '提现申请已提交。',
  }
}

export function createEmptyWalletTransactionPage(): PagePayload<WalletTransaction> {
  return {
    list: [],
    total: 0,
    page: DEFAULT_PAGE,
    pageSize: DEFAULT_PAGE_SIZE,
  }
}

export async function getWalletBalance() {
  const payload = await request.get<any, unknown>('/user/wallet/balance')
  return normalizeWalletBalance(payload)
}

export async function getWalletTransactions(query?: WalletTransactionQuery) {
  const normalized = normalizePageQuery(query)
  const payload = await request.get<any, unknown>('/user/wallet/transactions', { params: normalized.params })
  return normalizePagePayload(payload, normalized.page, normalized.pageSize, normalizeWalletTransaction)
}

export async function applyWalletWithdraw(input: WalletWithdrawInput) {
  const payload = await request.post<any, unknown>('/user/wallet/withdraw', normalizeWithdrawInput(input))
  return normalizeWithdrawResult(payload)
}
