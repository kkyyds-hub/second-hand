import request from '@/utils/request'

export interface SellerShopProfile {
  sellerId: number | null
  shopName: string
  nickname: string
  avatarUrl: string | null
  bio: string | null
  creditScore: number | null
  registeredAt: string | null
  onSaleCount: number
  soldCount: number
  completedOrderCount: number
  isCurrentUser: boolean
}

export interface SellerShopProduct {
  productId: number | null
  title: string
  coverUrl: string
  price: number
  categoryName: string
  status: string
  createTime: string
  soldTime: string | null
}

export interface SellerShopProductPage {
  list: SellerShopProduct[]
  total: number
  page: number
  pageSize: number
}

export interface SellerShopProductQuery {
  status?: string
  page?: number
  pageSize?: number
  excludeProductId?: number | null
}

type UnknownRecord = Record<string, unknown>

const DEFAULT_PAGE = 1
const DEFAULT_PAGE_SIZE = 12
const MAX_PAGE_SIZE = 24
const ALLOWED_STATUSES = new Set(['on_sale', 'sold'])

function isRecord(value: unknown): value is UnknownRecord {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function readFirstText(...values: unknown[]) {
  for (const value of values) {
    if (typeof value !== 'string') continue
    const normalized = value.trim()
    if (normalized) return normalized
  }
  return ''
}

function readFirstNullableText(...values: unknown[]) {
  const text = readFirstText(...values)
  return text || null
}

function normalizeNumber(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string') {
    const normalized = value.trim()
    if (!normalized) return null
    const parsed = Number(normalized)
    if (Number.isFinite(parsed)) return parsed
  }
  return null
}

function readPositiveInt(fallback: number, ...values: unknown[]) {
  for (const value of values) {
    const normalized = normalizeNumber(value)
    if (normalized !== null && normalized > 0) return Math.trunc(normalized)
  }
  return fallback
}

function readNonNegativeInt(fallback: number, ...values: unknown[]) {
  for (const value of values) {
    const normalized = normalizeNumber(value)
    if (normalized !== null && normalized >= 0) return Math.trunc(normalized)
  }
  return fallback
}

function normalizePrice(...values: unknown[]) {
  for (const value of values) {
    const normalized = normalizeNumber(value)
    if (normalized !== null && normalized >= 0) return normalized
  }
  return 0
}

function normalizeSellerId(sellerId: number | string) {
  const normalized = readPositiveInt(-1, sellerId)
  if (normalized <= 0) throw new Error('卖家 ID 无效。')
  return normalized
}

function normalizeProductId(productId: number | null | undefined) {
  if (productId == null) return null
  const normalized = readPositiveInt(-1, productId)
  if (normalized <= 0) return null
  return normalized
}

function normalizeStatus(status: string | undefined) {
  if (!status) return 'on_sale'
  const trimmed = status.trim()
  return ALLOWED_STATUSES.has(trimmed) ? trimmed : 'on_sale'
}

function normalizeQuery(query?: SellerShopProductQuery) {
  const page = readPositiveInt(DEFAULT_PAGE, query?.page)
  let pageSize = readPositiveInt(DEFAULT_PAGE_SIZE, query?.pageSize)
  if (pageSize > MAX_PAGE_SIZE) pageSize = MAX_PAGE_SIZE
  const status = normalizeStatus(query?.status)
  const excludeProductId = normalizeProductId(query?.excludeProductId)

  const params: Record<string, string | number> = {
    page,
    pageSize,
    status,
  }
  if (excludeProductId !== null) {
    params.excludeProductId = excludeProductId
  }
  return { params, page, pageSize }
}

function normalizeShopProfile(payload: unknown): SellerShopProfile {
  const source = isRecord(payload) ? payload : {}
  const sellerId = readNonNegativeInt(-1, source.sellerId)
  return {
    sellerId: sellerId >= 0 ? sellerId : null,
    shopName: readFirstText(source.shopName) || '卖家小店',
    nickname: readFirstText(source.nickname) || '卖家',
    avatarUrl: readFirstNullableText(source.avatarUrl),
    bio: readFirstNullableText(source.bio),
    creditScore: normalizeNumber(source.creditScore) as number | null,
    registeredAt: readFirstNullableText(source.registeredAt),
    onSaleCount: readNonNegativeInt(0, source.onSaleCount),
    soldCount: readNonNegativeInt(0, source.soldCount),
    completedOrderCount: readNonNegativeInt(0, source.completedOrderCount),
    isCurrentUser: Boolean(source.isCurrentUser),
  }
}

function normalizeShopProduct(payload: unknown): SellerShopProduct {
  const source = isRecord(payload) ? payload : {}
  const productId = readNonNegativeInt(-1, source.productId)
  return {
    productId: productId >= 0 ? productId : null,
    title: readFirstText(source.title) || '未命名商品',
    coverUrl: readFirstText(source.coverUrl),
    price: normalizePrice(source.price),
    categoryName: readFirstText(source.categoryName),
    status: readFirstText(source.status) || 'on_sale',
    createTime: readFirstText(source.createTime),
    soldTime: readFirstNullableText(source.soldTime),
  }
}

function normalizePagePayload(
  payload: unknown,
  page: number,
  pageSize: number,
  mapper: (item: unknown) => SellerShopProduct,
): SellerShopProductPage {
  const source = isRecord(payload) ? payload : {}
  const nestedSource = isRecord(source.data) ? source.data : {}

  function readFirstArray(...values: unknown[]) {
    for (const value of values) {
      if (Array.isArray(value)) return value
    }
    return []
  }

  const rawList = readFirstArray(
    source.list, source.records, source.rows, source.items,
    nestedSource.list, nestedSource.records, nestedSource.rows, nestedSource.items,
    Array.isArray(payload) ? payload : undefined,
  )

  return {
    list: rawList.map(mapper),
    total: readNonNegativeInt(
      rawList.length,
      source.total, source.totalCount, source.count,
      nestedSource.total, nestedSource.totalCount, nestedSource.count,
    ),
    page: readPositiveInt(page, source.page, source.current, source.pageNum, nestedSource.page, nestedSource.current),
    pageSize: readPositiveInt(pageSize, source.pageSize, source.size, source.limit, nestedSource.pageSize, nestedSource.size),
  }
}

export function createEmptyShopProfile(): SellerShopProfile {
  return {
    sellerId: null,
    shopName: '',
    nickname: '',
    avatarUrl: null,
    bio: null,
    creditScore: null,
    registeredAt: null,
    onSaleCount: 0,
    soldCount: 0,
    completedOrderCount: 0,
    isCurrentUser: false,
  }
}

export function createEmptyShopProductPage(): SellerShopProductPage {
  return { list: [], total: 0, page: DEFAULT_PAGE, pageSize: DEFAULT_PAGE_SIZE }
}

/**
 * 查询卖家小店概览信息。
 */
export async function getSellerShop(sellerId: number | string) {
  const payload = await request.get<any, unknown>(`/user/shops/${normalizeSellerId(sellerId)}`)
  return normalizeShopProfile(payload)
}

/**
 * 分页查询卖家小店公开商品。
 */
export async function getSellerShopProducts(
  sellerId: number | string,
  query?: SellerShopProductQuery,
): Promise<SellerShopProductPage> {
  const normalized = normalizeQuery(query)
  const payload = await request.get<any, unknown>(
    `/user/shops/${normalizeSellerId(sellerId)}/products`,
    { params: normalized.params },
  )
  return normalizePagePayload(payload, normalized.page, normalized.pageSize, normalizeShopProduct)
}

/**
 * 查询该卖家的其他在售商品（用于商品详情页）。
 */
export async function getSellerOtherProducts(
  sellerId: number | string,
  excludeProductId: number,
  limit: number = 4,
): Promise<SellerShopProductPage> {
  const effectiveLimit = Math.min(Math.max(limit, 1), 6)
  return getSellerShopProducts(sellerId, {
    status: 'on_sale',
    page: 1,
    pageSize: effectiveLimit,
    excludeProductId,
  })
}
