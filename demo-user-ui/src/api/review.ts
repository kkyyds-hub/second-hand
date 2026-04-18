import request from '@/utils/request'
import type { PagePayload } from '@/api/market'

export interface ReviewCreatePayload {
  orderId: number | string
  rating: number | string
  content: string
  isAnonymous: boolean
}

export interface MyReviewQuery {
  page?: number
  pageSize?: number
}

export interface MyReviewItem {
  id: number | null
  orderId: number | null
  productId: number | null
  rating: number
  content: string
  isAnonymous: boolean
  createdAt: string
  buyerDisplayName: string
  buyerAvatar: string
  productTitle: string
  productCover: string
}

type UnknownRecord = Record<string, unknown>

const DEFAULT_PAGE = 1
const DEFAULT_PAGE_SIZE = 10

function isRecord(value: unknown): value is UnknownRecord {
  return value !== null && typeof value === "object" && !Array.isArray(value)
}

function readFirstArray(...values: unknown[]) {
  for (const value of values) {
    if (Array.isArray(value)) {
      return value
    }
  }

  return []
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

function readBoolean(value: unknown) {
  if (typeof value === 'boolean') {
    return value
  }

  const normalized = normalizeNumber(value)
  if (normalized !== null) {
    return normalized === 1
  }

  if (typeof value === 'string') {
    const text = value.trim().toLowerCase()
    return ['true', 'yes', 'y'].includes(text)
  }

  return false
}

function normalizeQuery(query?: MyReviewQuery) {
  return {
    page: readPositiveInt(DEFAULT_PAGE, query?.page),
    pageSize: readPositiveInt(DEFAULT_PAGE_SIZE, query?.pageSize),
  }
}

function normalizeMyReviewItem(payload: unknown): MyReviewItem {
  const source = isRecord(payload) ? payload : {}

  return {
    id: readNonNegativeInt(-1, source.id, source.reviewId) >= 0 ? readNonNegativeInt(-1, source.id, source.reviewId) : null,
    orderId: readPositiveInt(-1, source.orderId) > 0 ? readPositiveInt(-1, source.orderId) : null,
    productId: readPositiveInt(-1, source.productId) > 0 ? readPositiveInt(-1, source.productId) : null,
    rating: readPositiveInt(0, source.rating, source.score),
    content: readFirstText(source.content, source.reviewContent, source.comment),
    isAnonymous: readBoolean(source.isAnonymous),
    createdAt: readFirstText(source.createdAt, source.createTime, source.reviewTime),
    buyerDisplayName: readFirstText(source.buyerDisplayName, source.userName, source.userNickname),
    buyerAvatar: readFirstText(source.buyerAvatar, source.avatar, source.avatarUrl),
    productTitle: readFirstText(source.productTitle, source.title),
    productCover: readFirstText(source.productCover, source.coverUrl, source.cover),
  }
}

function normalizeMyReviewPage(payload: unknown, page: number, pageSize: number): PagePayload<MyReviewItem> {
  const source = isRecord(payload) ? payload : {}
  const nestedSource = isRecord(source.data) ? source.data : {}

  const list = readFirstArray(
    source.list,
    source.records,
    source.rows,
    source.items,
    nestedSource.list,
    nestedSource.records,
    nestedSource.rows,
    nestedSource.items,
  ).map((item) => normalizeMyReviewItem(item))

  return {
    list,
    total: readNonNegativeInt(
      list.length,
      source.total,
      source.totalCount,
      source.count,
      nestedSource.total,
      nestedSource.totalCount,
      nestedSource.count,
    ),
    page: readPositiveInt(page, source.page, source.current, source.pageNum, nestedSource.page, nestedSource.current),
    pageSize: readPositiveInt(pageSize, source.pageSize, source.size, source.limit, nestedSource.pageSize, nestedSource.size),
  }
}

function normalizeCreateReviewPayload(payload: ReviewCreatePayload) {
  /**
   * 评论提交受订单完成态等后端规则约束，页面层只保留交互和前置提示；
   * 具体 DTO 字段映射统一在 API 层，避免多个页面重复拼 `orderId/rating/isAnonymous`。
   */
  return {
    orderId: readPositiveInt(-1, payload.orderId),
    rating: readPositiveInt(0, payload.rating),
    content: readFirstText(payload.content),
    isAnonymous: Boolean(payload.isAnonymous),
  }
}

export function createEmptyMyReviewPage(): PagePayload<MyReviewItem> {
  return {
    list: [],
    total: 0,
    page: DEFAULT_PAGE,
    pageSize: DEFAULT_PAGE_SIZE,
  }
}

export async function createReview(payload: ReviewCreatePayload) {
  const requestPayload = normalizeCreateReviewPayload(payload)
  const reviewId = await request.post<any, unknown>('/user/reviews', requestPayload)

  const normalizedId = normalizeNumber(reviewId)
  return normalizedId !== null && normalizedId > 0 ? Math.trunc(normalizedId) : null
}

export async function getMyReviews(query?: MyReviewQuery) {
  const normalizedQuery = normalizeQuery(query)
  const payload = await request.get<any, unknown>('/user/reviews/mine', { params: normalizedQuery })
  return normalizeMyReviewPage(payload, normalizedQuery.page, normalizedQuery.pageSize)
}
