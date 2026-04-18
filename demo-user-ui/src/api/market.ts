import request from '@/utils/request'

export interface MarketProductListQuery {
  keyword?: string
  categoryId?: number | string | null
  minPrice?: number | string | null
  maxPrice?: number | string | null
  page?: number
  pageSize?: number
}

export interface MarketReviewQuery {
  page?: number
  pageSize?: number
}

export interface MarketProductSummary {
  id: number | null
  title: string
  coverUrl: string
  price: number
  categoryName: string
  sellerName: string
  stock: number
  soldCount: number
  shortDescription: string
}

export interface MarketProductDetail {
  id: number | null
  title: string
  coverUrl: string
  price: number
  categoryName: string
  sellerName: string
  stock: number
  soldCount: number
  description: string
  images: string[]
}

export interface ReviewItem {
  id: number | null
  score: number
  content: string
  userName: string
  createdAt: string
}

export interface ProductReportPayload {
  reportType: string
  description: string
  evidenceUrls?: string[]
}

export interface ProductReportResult {
  ticketNo: string
}

export interface PagePayload<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

type UnknownRecord = Record<string, unknown>

const DEFAULT_PAGE = 1
const DEFAULT_PAGE_SIZE = 10

function isRecord(value: unknown): value is UnknownRecord {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
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

function normalizePrice(...values: unknown[]) {
  for (const value of values) {
    const normalized = normalizeNumber(value)
    if (normalized !== null && normalized >= 0) {
      return normalized
    }
  }

  return 0
}

function normalizePageQuery(query?: { page?: number; pageSize?: number }) {
  return {
    page: readPositiveInt(DEFAULT_PAGE, query?.page),
    pageSize: readPositiveInt(DEFAULT_PAGE_SIZE, query?.pageSize),
  }
}

function normalizeListQuery(query?: MarketProductListQuery) {
  const normalizedPage = normalizePageQuery(query)
  const params: Record<string, string | number> = {
    page: normalizedPage.page,
    pageSize: normalizedPage.pageSize,
  }

  const keyword = readFirstText(query?.keyword)
  if (keyword) {
    params.keyword = keyword
  }

  const categoryId = normalizeNumber(query?.categoryId)
  if (categoryId !== null && categoryId > 0) {
    params.categoryId = Math.trunc(categoryId)
  }

  const minPrice = normalizeNumber(query?.minPrice)
  if (minPrice !== null && minPrice >= 0) {
    params.minPrice = minPrice
  }

  const maxPrice = normalizeNumber(query?.maxPrice)
  if (maxPrice !== null && maxPrice >= 0) {
    params.maxPrice = maxPrice
  }

  return { params, page: normalizedPage.page, pageSize: normalizedPage.pageSize }
}

function normalizeProductId(productId: number | string) {
  const normalized = readPositiveInt(-1, productId)
  if (normalized <= 0) {
    throw new Error('商品 ID 无效，无法继续读取市场数据。')
  }
  return normalized
}

function normalizeMarketProductSummary(payload: unknown): MarketProductSummary {
  const source = isRecord(payload) ? payload : {}
  const id = readNonNegativeInt(-1, source.id, source.productId)

  return {
    id: id >= 0 ? id : null,
    title: readFirstText(source.title, source.productTitle, source.name) || '未命名商品',
    coverUrl: readFirstText(source.coverUrl, source.cover, source.mainImage, source.imageUrl),
    price: normalizePrice(source.price, source.salePrice, source.currentPrice),
    categoryName: readFirstText(source.categoryName, source.category),
    sellerName: readFirstText(source.sellerName, source.shopName, source.storeName),
    stock: readNonNegativeInt(0, source.stock, source.inventory),
    soldCount: readNonNegativeInt(0, source.soldCount, source.sales, source.saleCount),
    shortDescription: readFirstText(source.shortDescription, source.summary, source.description),
  }
}

function normalizeMarketProductDetail(payload: unknown): MarketProductDetail {
  const source = isRecord(payload) ? payload : {}
  const summary = normalizeMarketProductSummary(source)
  const images = readFirstArray(source.images, source.imageList, source.gallery, source.pictures).map((item) => readFirstText(item)).filter(Boolean)

  return {
    ...summary,
    description: readFirstText(source.description, source.detail, source.content),
    images,
  }
}

function normalizeReviewItem(payload: unknown): ReviewItem {
  const source = isRecord(payload) ? payload : {}
  const id = readNonNegativeInt(-1, source.id, source.reviewId)

  return {
    id: id >= 0 ? id : null,
    score: readNonNegativeInt(0, source.score, source.rating),
    content: readFirstText(source.content, source.comment, source.reviewContent),
    userName: readFirstText(source.userName, source.nickName, source.nickname, source.userNickname) || '匿名用户',
    createdAt: readFirstText(source.createdAt, source.createTime, source.reviewTime),
  }
}

function normalizeProductReportResult(payload: unknown): ProductReportResult {
  const source = isRecord(payload) ? payload : {}
  const ticketNo = readFirstText(source.ticketNo, source.reportNo, source.ticket, source.id)

  return {
    ticketNo: ticketNo || '已提交（工单号待后端返回）',
  }
}

function normalizeEvidenceUrls(values: unknown) {
  if (!Array.isArray(values)) {
    return []
  }

  return values.map((item) => readFirstText(item)).filter(Boolean)
}

function normalizePagePayload<T>(payload: unknown, page: number, pageSize: number, mapper: (item: unknown) => T): PagePayload<T> {
  const source = isRecord(payload) ? payload : {}
  const nestedSource = isRecord(source.data) ? source.data : {}

  /**
   * Day03 页面统一消费固定分页结构，避免列表页/详情页分别维护历史分页别名兼容。
   */
  const rawList = readFirstArray(
    source.list,
    source.records,
    source.rows,
    source.items,
    nestedSource.list,
    nestedSource.records,
    nestedSource.rows,
    nestedSource.items,
    Array.isArray(payload) ? payload : undefined,
  )

  const list = rawList.map((item) => mapper(item))

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

export function createEmptyProductPage(): PagePayload<MarketProductSummary> {
  return {
    list: [],
    total: 0,
    page: DEFAULT_PAGE,
    pageSize: DEFAULT_PAGE_SIZE,
  }
}

export function createEmptyReviewPage(): PagePayload<ReviewItem> {
  return {
    list: [],
    total: 0,
    page: DEFAULT_PAGE,
    pageSize: DEFAULT_PAGE_SIZE,
  }
}

export async function getMarketProductList(query?: MarketProductListQuery) {
  const normalized = normalizeListQuery(query)
  const payload = await request.get<any, unknown>('/user/market/products', { params: normalized.params })
  return normalizePagePayload(payload, normalized.page, normalized.pageSize, normalizeMarketProductSummary)
}

export async function getMarketProductDetail(productId: number | string) {
  const payload = await request.get<any, unknown>(`/user/market/products/${normalizeProductId(productId)}`)
  return normalizeMarketProductDetail(payload)
}

export async function getMarketProductReviews(productId: number | string, query?: MarketReviewQuery) {
  const normalizedProductId = normalizeProductId(productId)
  const normalizedPage = normalizePageQuery(query)
  const payload = await request.get<any, unknown>(`/user/market/products/${normalizedProductId}/reviews`, {
    params: normalizedPage,
  })
  return normalizePagePayload(payload, normalizedPage.page, normalizedPage.pageSize, normalizeReviewItem)
}

export async function reportMarketProduct(productId: number | string, payload: ProductReportPayload) {
  const normalizedProductId = normalizeProductId(productId)

  /**
   * 举报入参在 API 层收口：页面只维护表单态，不负责拼接 DTO 细节。
   * 这样后续若后端字段改名，变更只会集中在一个模块里。
   */
  const requestPayload = {
    reportType: readFirstText(payload.reportType),
    description: readFirstText(payload.description),
    evidenceUrls: normalizeEvidenceUrls(payload.evidenceUrls),
  }

  const responsePayload = await request.post<any, unknown>(`/user/market/products/${normalizedProductId}/report`, requestPayload)
  return normalizeProductReportResult(responsePayload)
}
