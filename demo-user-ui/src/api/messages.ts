import request from '@/utils/request'

export interface OrderMessageListQuery {
  page?: number
  pageSize?: number
}

export interface OrderMessage {
  id: string
  orderId: number | null
  fromUserId: number | null
  toUserId: number | null
  content: string
  read: boolean
  createTime: string
}

export interface SendOrderMessageInput {
  toUserId: number | string
  content: string
}

export interface PagePayload<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

type UnknownRecord = Record<string, unknown>

const DEFAULT_PAGE = 1
const DEFAULT_PAGE_SIZE = 20

function isBrowser() {
  return typeof window !== 'undefined'
}

function isRecord(value: unknown): value is UnknownRecord {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function readFirstText(...values: unknown[]) {
  for (const value of values) {
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

function readBoolean(value: unknown) {
  if (typeof value === 'boolean') {
    return value
  }

  if (typeof value === 'number') {
    return value !== 0
  }

  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase()
    if (['true', '1', 'yes', 'y'].includes(normalized)) {
      return true
    }

    if (['false', '0', 'no', 'n'].includes(normalized)) {
      return false
    }
  }

  return false
}

function readFirstArray(...values: unknown[]) {
  for (const value of values) {
    if (Array.isArray(value)) {
      return value
    }
  }

  return []
}

function normalizeOrderMessage(payload: unknown): OrderMessage {
  const source = isRecord(payload) ? payload : {}

  return {
    id: readFirstText(source.id, source._id),
    orderId: readPositiveId(source.orderId),
    fromUserId: readPositiveId(source.fromUserId),
    toUserId: readPositiveId(source.toUserId),
    content: readFirstText(source.content),
    read: readBoolean(source.read),
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

function normalizeOrderId(orderId: number | string, errorMessage = '订单 ID 无效，无法读取会话。') {
  const normalized = readPositiveInt(-1, orderId)
  if (normalized <= 0) {
    throw new Error(errorMessage)
  }

  return normalized
}

function normalizeListQuery(query?: OrderMessageListQuery) {
  const page = readPositiveInt(DEFAULT_PAGE, query?.page)
  const pageSize = readPositiveInt(DEFAULT_PAGE_SIZE, query?.pageSize)

  return {
    params: { page, pageSize },
    page,
    pageSize,
  }
}

function normalizeActionMessage(payload: unknown, fallback: string) {
  if (typeof payload === 'string') {
    const normalized = payload.trim()
    if (normalized) {
      return normalized
    }
  }

  const source = isRecord(payload) ? payload : {}
  return readFirstText(source.message, source.msg, source.result) || fallback
}

function buildClientMessageId() {
  if (isBrowser() && typeof window.crypto?.randomUUID === 'function') {
    return window.crypto.randomUUID()
  }

  return `user-ui-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
}

export function createEmptyOrderMessagePage(): PagePayload<OrderMessage> {
  return {
    list: [],
    total: 0,
    page: DEFAULT_PAGE,
    pageSize: DEFAULT_PAGE_SIZE,
  }
}

export function readSendOrderMessageValidationError(input: Partial<SendOrderMessageInput>) {
  const toUserId = readPositiveId(input.toUserId)
  const content = readFirstText(input.content)

  if (toUserId === null) {
    return '接收方用户 ID 无效，当前无法发送消息。'
  }

  if (content.length < 1 || content.length > 500) {
    return '消息内容长度需在 1~500 个字符之间。'
  }

  return ''
}

export async function getOrderMessageList(orderId: number | string, query?: OrderMessageListQuery) {
  const normalizedOrderId = normalizeOrderId(orderId)
  const normalizedQuery = normalizeListQuery(query)
  const payload = await request.get<any, unknown>(`/user/messages/orders/${normalizedOrderId}`, {
    params: normalizedQuery.params,
  })

  return normalizePagePayload(payload, normalizedQuery.page, normalizedQuery.pageSize, normalizeOrderMessage)
}

export async function sendOrderMessage(orderId: number | string, input: SendOrderMessageInput) {
  const validationError = readSendOrderMessageValidationError(input)
  if (validationError) {
    throw new Error(validationError)
  }

  /**
   * 后端以 clientMsgId 做幂等去重。
   * 前端在 API 层统一生成它，页面层只维护“发给谁 / 发什么”这两个事实。
   */
  const payload = await request.post<any, unknown>(`/user/messages/orders/${normalizeOrderId(orderId, '订单 ID 无效，无法发送消息。')}`, {
    toUserId: readPositiveId(input.toUserId),
    clientMsgId: buildClientMessageId(),
    content: readFirstText(input.content),
  })

  return normalizeOrderMessage(payload)
}

export async function markOrderMessagesAsRead(orderId: number | string) {
  const payload = await request.put<any, unknown>(`/user/messages/orders/${normalizeOrderId(orderId, '订单 ID 无效，无法标记已读。')}/read`)
  return normalizeActionMessage(payload, '订单会话已标记为已读。')
}
