import request from '@/utils/request'
import { type MarketProductSummary, type PagePayload } from '@/api/market'

export interface FavoriteListQuery {
  page?: number
  pageSize?: number
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

function normalizePrice(...values: unknown[]) {
  for (const value of values) {
    const normalized = normalizeNumber(value)
    if (normalized !== null && normalized >= 0) {
      return normalized
    }
  }

  return 0
}

function normalizeQuery(query?: FavoriteListQuery) {
  return {
    page: readPositiveInt(DEFAULT_PAGE, query?.page),
    pageSize: readPositiveInt(DEFAULT_PAGE_SIZE, query?.pageSize),
  }
}

function normalizeProductId(productId: number | string) {
  const normalized = readPositiveInt(-1, productId)
  if (normalized <= 0) {
    throw new Error('商品 ID 无效，无法继续收藏操作。')
  }
  return normalized
}

function normalizeFavoriteProduct(payload: unknown): MarketProductSummary {
  const source = isRecord(payload) ? payload : {}
  const nestedProduct = isRecord(source.product) ? source.product : {}
  const id = readNonNegativeInt(-1, source.productId, source.id, nestedProduct.id, nestedProduct.productId)

  return {
    id: id >= 0 ? id : null,
    title:
      readFirstText(source.productTitle, source.title, source.name, nestedProduct.title, nestedProduct.productTitle, nestedProduct.name) || '未命名商品',
    coverUrl: readFirstText(source.coverUrl, source.cover, source.mainImage, nestedProduct.coverUrl, nestedProduct.cover, nestedProduct.mainImage),
    price: normalizePrice(source.price, source.salePrice, nestedProduct.price, nestedProduct.salePrice),
    categoryName: readFirstText(source.categoryName, source.category, nestedProduct.categoryName, nestedProduct.category),
    sellerName: readFirstText(source.sellerName, source.shopName, nestedProduct.sellerName, nestedProduct.shopName),
    stock: readNonNegativeInt(0, source.stock, source.inventory, nestedProduct.stock, nestedProduct.inventory),
    soldCount: readNonNegativeInt(0, source.soldCount, source.sales, nestedProduct.soldCount, nestedProduct.sales),
    shortDescription: readFirstText(source.shortDescription, source.summary, source.description, nestedProduct.shortDescription, nestedProduct.summary),
  }
}

function normalizeFavoritePage(payload: unknown, query: { page: number; pageSize: number }): PagePayload<MarketProductSummary> {
  const source = isRecord(payload) ? payload : {}
  const nestedSource = isRecord(source.data) ? source.data : {}

  /**
   * 收藏列表可能直接返回 FavoriteItemDTO，也可能嵌套 product 字段；
   * 统一在 API 层摊平为市场商品摘要，页面无需关心 DTO 形状差异。
   */
  const list = readFirstArray(
    source.list,
    source.records,
    source.rows,
    source.items,
    nestedSource.list,
    nestedSource.records,
    nestedSource.rows,
    nestedSource.items,
  ).map((item) => normalizeFavoriteProduct(item))

  return {
    list,
    total: readNonNegativeInt(
      list.length,
      source.total,
      source.totalCount,
      nestedSource.total,
      nestedSource.totalCount,
    ),
    page: readPositiveInt(query.page, source.page, source.current, source.pageNum, nestedSource.page, nestedSource.current),
    pageSize: readPositiveInt(query.pageSize, source.pageSize, source.size, nestedSource.pageSize, nestedSource.size),
  }
}

export function createEmptyFavoritePage(): PagePayload<MarketProductSummary> {
  return {
    list: [],
    total: 0,
    page: DEFAULT_PAGE,
    pageSize: DEFAULT_PAGE_SIZE,
  }
}

export async function getFavoriteStatus(productId: number | string) {
  const payload = await request.get<any, unknown>(`/user/favorites/${normalizeProductId(productId)}/status`)
  return readBoolean(payload)
}

export async function favoriteProduct(productId: number | string) {
  await request.post<any, unknown>(`/user/favorites/${normalizeProductId(productId)}`)
}

export async function unfavoriteProduct(productId: number | string) {
  await request.delete<any, unknown>(`/user/favorites/${normalizeProductId(productId)}`)
}

export async function getFavoriteList(query?: FavoriteListQuery) {
  const normalizedQuery = normalizeQuery(query)
  const payload = await request.get<any, unknown>('/user/favorites', { params: normalizedQuery })
  return normalizeFavoritePage(payload, normalizedQuery)
}

