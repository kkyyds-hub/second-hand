import request from '@/utils/request'

/**
 * 购物车项（列表/加入返回）。
 *
 * 商品标题、封面、价格、卖家、状态均来自后端实时联表结果，
 * 前端不缓存陈旧副本。
 */
export interface CartItem {
  cartItemId: number | null
  productId: number | null
  title: string
  coverUrl: string
  price: number
  sellerId: number | null
  sellerNickname: string
  productStatus: string
  available: boolean
  unavailableReason: string
  createTime: string
}

/**
 * 购物车数量统计。
 */
export interface CartCount {
  total: number
  available: number
}

/**
 * 批量结算成功项（每项对应一个独立订单）。
 */
export interface CartCheckoutOrder {
  cartItemId: number | null
  productId: number | null
  orderId: number | null
  orderNo: string
  status: string
  totalAmount: number
}

/**
 * 批量结算失败项。
 */
export interface CartCheckoutFailure {
  cartItemId: number | null
  productId: number | null
  reason: string
}

/**
 * 批量结算结果（部分成功模型）。
 *
 * 注意：HTTP 200 不代表全部成功，必须读取 successCount / failureCount 与 orders / failures。
 */
export interface CartCheckoutResult {
  requestedCount: number
  successCount: number
  failureCount: number
  orders: CartCheckoutOrder[]
  failures: CartCheckoutFailure[]
}

export interface AddCartItemInput {
  productId: number
}

export interface CartCheckoutInput {
  cartItemIds: number[]
  addressId: number
}

type UnknownRecord = Record<string, unknown>

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

function readFirstNumber(...values: unknown[]) {
  for (const value of values) {
    const normalized = normalizeNumber(value)
    if (normalized !== null) {
      return normalized
    }
  }
  return null
}

/**
 * 读取正整数 ID；非法（null/0/负数/非数字）返回 null。
 */
function readPositiveId(...values: unknown[]) {
  const normalized = readFirstNumber(...values)
  if (normalized === null || normalized <= 0) {
    return null
  }
  return Math.trunc(normalized)
}

/**
 * 读取非负整数。
 */
function readNonNegativeInt(...values: unknown[]) {
  const normalized = readFirstNumber(...values)
  if (normalized === null || normalized < 0) {
    return 0
  }
  return Math.trunc(normalized)
}

/**
 * 读取非负金额。
 */
function normalizePrice(...values: unknown[]) {
  const normalized = readFirstNumber(...values)
  if (normalized === null || normalized < 0) {
    return 0
  }
  return normalized
}

/**
 * 读取明确布尔值；未知一律视为 false（不得把未知状态当成可购买）。
 */
function readBoolean(value: unknown) {
  if (typeof value === 'boolean') {
    return value
  }
  const normalized = normalizeNumber(value)
  if (normalized !== null) {
    return normalized === 1
  }
  if (typeof value === 'string') {
    return value.trim().toLowerCase() === 'true'
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

function normalizeCartItem(payload: unknown): CartItem {
  const source = isRecord(payload) ? payload : {}
  return {
    cartItemId: readPositiveId(source.cartItemId, source.id),
    productId: readPositiveId(source.productId),
    title: readFirstText(source.title, source.productTitle, source.name) || '未命名商品',
    coverUrl: readFirstText(source.coverUrl, source.cover, source.mainImage),
    price: normalizePrice(source.price, source.salePrice),
    sellerId: readPositiveId(source.sellerId, source.ownerId),
    sellerNickname: readFirstText(source.sellerNickname, source.sellerName, source.shopName),
    productStatus: readFirstText(source.productStatus, source.status),
    available: readBoolean(source.available),
    unavailableReason: readFirstText(source.unavailableReason, source.reason),
    createTime: readFirstText(source.createTime, source.createdAt, source.favoritedAt),
  }
}

function normalizeCartCount(payload: unknown): CartCount {
  const source = isRecord(payload) ? payload : {}
  return {
    total: readNonNegativeInt(source.total, source.totalCount),
    available: readNonNegativeInt(source.available, source.availableCount),
  }
}

function normalizeCheckoutOrder(payload: unknown): CartCheckoutOrder {
  const source = isRecord(payload) ? payload : {}
  return {
    cartItemId: readPositiveId(source.cartItemId),
    productId: readPositiveId(source.productId),
    orderId: readPositiveId(source.orderId, source.id),
    orderNo: readFirstText(source.orderNo, source.orderSn),
    status: readFirstText(source.status, source.orderStatus),
    totalAmount: normalizePrice(source.totalAmount, source.amount, source.price),
  }
}

function normalizeCheckoutFailure(payload: unknown): CartCheckoutFailure {
  const source = isRecord(payload) ? payload : {}
  return {
    cartItemId: readPositiveId(source.cartItemId),
    productId: readPositiveId(source.productId),
    reason: readFirstText(source.reason, source.message, source.failReason) || '结算失败',
  }
}

function normalizeCheckoutResult(payload: unknown): CartCheckoutResult {
  const source = isRecord(payload) ? payload : {}
  const orders = readFirstArray(source.orders, source.successList).map(normalizeCheckoutOrder)
  const failures = readFirstArray(source.failures, source.failureList).map(normalizeCheckoutFailure)
  return {
    requestedCount: readNonNegativeInt(source.requestedCount),
    successCount: readNonNegativeInt(source.successCount, orders.length),
    failureCount: readNonNegativeInt(source.failureCount, failures.length),
    orders,
    failures,
  }
}

export function createEmptyCartCount(): CartCount {
  return { total: 0, available: 0 }
}

/**
 * 查询当前用户全部购物车项（按加入时间倒序）。
 */
export async function getCartItems(): Promise<CartItem[]> {
  const payload = await request.get<any, unknown>('/user/cart/items')
  return readFirstArray(payload).map(normalizeCartItem)
}

/**
 * 查询当前用户购物车数量统计。
 */
export async function getCartCount(): Promise<CartCount> {
  const payload = await request.get<any, unknown>('/user/cart/count')
  return normalizeCartCount(payload)
}

/**
 * 加入购物车（幂等）。返回完整购物车项。
 */
export async function addCartItem(input: AddCartItemInput): Promise<CartItem> {
  const payload = await request.post<any, unknown>('/user/cart/items', { productId: input.productId })
  return normalizeCartItem(payload)
}

/**
 * 删除单个购物车项（幂等）。
 */
export async function deleteCartItem(cartItemId: number): Promise<void> {
  await request.delete<any, unknown>(`/user/cart/items/${cartItemId}`)
}

/**
 * 批量删除购物车项，返回实际删除数量。
 */
export async function batchDeleteCartItems(cartItemIds: number[]): Promise<number> {
  const payload = await request.post<any, unknown>('/user/cart/items/batch-delete', { cartItemIds })
  return readNonNegativeInt(payload)
}

/**
 * 批量结算（部分成功模型）。
 */
export async function checkoutCart(input: CartCheckoutInput): Promise<CartCheckoutResult> {
  const payload = await request.post<any, unknown>('/user/cart/checkout', {
    cartItemIds: input.cartItemIds,
    addressId: input.addressId,
  })
  return normalizeCheckoutResult(payload)
}
