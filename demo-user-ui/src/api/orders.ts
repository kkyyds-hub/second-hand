import request from '@/utils/request'

export interface BuyerOrderListQuery {
  status?: string
  page?: number
  pageSize?: number
}

export interface SellerOrderListQuery extends BuyerOrderListQuery {}

export interface BuyerOrderSummary {
  orderId: number | null
  orderNo: string
  status: string
  statusLabel: string
  productId: number | null
  productTitle: string
  productThumbnail: string
  sellerNickname: string
  dealPrice: number
  quantity: number
  createTime: string
  payTime: string
  shipTime: string
  completeTime: string
  shippingCompany: string
  trackingNo: string
}

export interface SellerOrderSummary {
  orderId: number | null
  orderNo: string
  status: string
  statusLabel: string
  productId: number | null
  productTitle: string
  productThumbnail: string
  buyerNickname: string
  dealPrice: number
  quantity: number
  createTime: string
  payTime: string
  shipTime: string
  completeTime: string
  shippingCompany: string
  trackingNo: string
}

export interface BuyerOrderDetail extends BuyerOrderSummary {
  totalAmount: number
  shippingAddress: string
  buyerNickname: string
  sellerId: number | null
  buyerId: number | null
  productImages: string[]
  shippingRemark: string
  sellerNickname: string
}

export type SellerOrderDetail = BuyerOrderDetail

export interface SellerOrderLogisticsTraceItem {
  time: string
  location: string
  status: string
}

export interface SellerOrderLogistics {
  orderId: number | null
  status: string
  statusLabel: string
  shippingCompany: string
  trackingNo: string
  shipTime: string
  provider: string
  lastSyncTime: string
  trace: SellerOrderLogisticsTraceItem[]
}

export interface CreateBuyerOrderInput {
  productId: number | string
  shippingAddress: string
}

export interface CreateBuyerOrderResult {
  orderId: number | null
  orderNo: string
  status: string
  statusLabel: string
  totalAmount: number
  createTime: string
}

export interface CancelBuyerOrderInput {
  reason?: string
}

export interface ShipSellerOrderInput {
  shippingCompany: string
  trackingNo: string
  remark?: string
}

export type BuyerMockPayScenario = 'SUCCESS' | 'FAIL' | 'REPEAT'

export interface BuyerMockPayResult {
  orderId: number | null
  orderNo: string
  scenario: BuyerMockPayScenario
  channel: string
  beforeStatus: string
  afterStatus: string
  callbackCount: number
  firstStatus: string
  firstTradeNo: string
  firstResult: string
  secondStatus: string
  secondTradeNo: string
  secondResult: string
  finalResult: string
}

export interface OrderStatusMeta {
  label: string
  tone: 'neutral' | 'accent' | 'success' | 'warning'
}

export type BuyerOrderStatusMeta = OrderStatusMeta
export type SellerOrderStatusMeta = OrderStatusMeta

export interface PagePayload<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

type UnknownRecord = Record<string, unknown>

const DEFAULT_PAGE = 1
const DEFAULT_PAGE_SIZE = 10
const SHIPPING_COMPANY_PATTERN = /^[\p{L}0-9 _.-]{2,50}$/u
const TRACKING_NO_PATTERN = /^[A-Za-z0-9-]{6,50}$/

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

function normalizePrice(...values: unknown[]) {
  for (const value of values) {
    const normalized = normalizeNumber(value)
    if (normalized !== null && normalized >= 0) {
      return normalized
    }
  }

  return 0
}

function normalizeOrderStatus(value: unknown) {
  const normalized = readFirstText(value).toLowerCase()

  if (['pending', 'paid', 'shipped', 'completed', 'cancelled'].includes(normalized)) {
    return normalized
  }

  return normalized || 'unknown'
}

function normalizeMockPayScenario(value: unknown): BuyerMockPayScenario {
  const normalized = readFirstText(value).toUpperCase()

  if (normalized === 'FAIL' || normalized === 'REPEAT') {
    return normalized
  }

  return 'SUCCESS'
}

function normalizeListQuery(query?: BuyerOrderListQuery | SellerOrderListQuery) {
  const page = readPositiveInt(DEFAULT_PAGE, query?.page)
  const pageSize = readPositiveInt(DEFAULT_PAGE_SIZE, query?.pageSize)
  const params: Record<string, string | number> = { page, pageSize }

  const status = normalizeOrderStatus(query?.status)
  if (status !== 'unknown') {
    params.status = status
  }

  return { params, page, pageSize }
}

function readFirstArray(...values: unknown[]) {
  for (const value of values) {
    if (Array.isArray(value)) {
      return value
    }
  }

  return []
}

function splitCsv(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function normalizeImages(...values: unknown[]) {
  /**
   * 订单详情的商品图片仍然统一在 API 层兜底：
   * - 兼容数组；
   * - 兼容逗号拼接字符串；
   * - 页面层只消费 `string[]`，不再继续猜字段形态。
   */
  const list = values.flatMap((value) => {
    if (Array.isArray(value)) {
      return value
    }

    if (typeof value === 'string') {
      return splitCsv(value)
    }

    return []
  })

  return list.map((item) => readFirstText(item)).filter(Boolean)
}

function normalizeBuyerOrderSummary(payload: unknown): BuyerOrderSummary {
  const source = isRecord(payload) ? payload : {}
  const status = normalizeOrderStatus(source.status)

  return {
    orderId: readPositiveId(source.orderId, source.id),
    orderNo: readFirstText(source.orderNo, source.orderSn),
    status,
    statusLabel: getOrderStatusMeta(status).label,
    productId: readPositiveId(source.productId),
    productTitle: readFirstText(source.productTitle, source.productName) || '未命名商品',
    productThumbnail: readFirstText(source.productThumbnail, source.productCover, source.coverUrl),
    sellerNickname: readFirstText(source.sellerNickname, source.sellerName) || '卖家',
    dealPrice: normalizePrice(source.dealPrice, source.unitPrice, source.price),
    quantity: readPositiveInt(1, source.quantity, source.count),
    createTime: readFirstText(source.createTime, source.createdAt),
    payTime: readFirstText(source.payTime),
    shipTime: readFirstText(source.shipTime),
    completeTime: readFirstText(source.completeTime),
    shippingCompany: readFirstText(source.shippingCompany, source.logisticsCompany),
    trackingNo: readFirstText(source.trackingNo, source.logisticsNo),
  }
}

function normalizeSellerOrderSummary(payload: unknown): SellerOrderSummary {
  const source = isRecord(payload) ? payload : {}
  const status = normalizeOrderStatus(source.status)

  return {
    orderId: readPositiveId(source.orderId, source.id),
    orderNo: readFirstText(source.orderNo, source.orderSn),
    status,
    statusLabel: getOrderStatusMeta(status).label,
    productId: readPositiveId(source.productId),
    productTitle: readFirstText(source.productTitle, source.productName) || '未命名商品',
    productThumbnail: readFirstText(source.productThumbnail, source.productCover, source.coverUrl),
    buyerNickname: readFirstText(source.buyerNickname, source.buyerName) || '买家',
    dealPrice: normalizePrice(source.dealPrice, source.unitPrice, source.price),
    quantity: readPositiveInt(1, source.quantity, source.count),
    createTime: readFirstText(source.createTime, source.createdAt),
    payTime: readFirstText(source.payTime),
    shipTime: readFirstText(source.shipTime),
    completeTime: readFirstText(source.completeTime),
    shippingCompany: readFirstText(source.shippingCompany, source.logisticsCompany),
    trackingNo: readFirstText(source.trackingNo, source.logisticsNo),
  }
}

function normalizeOrderDetail(payload: unknown): BuyerOrderDetail {
  const source = isRecord(payload) ? payload : {}
  const summary = normalizeBuyerOrderSummary(source)

  return {
    ...summary,
    totalAmount: normalizePrice(source.totalAmount, source.totalPrice, source.amount),
    shippingAddress: readFirstText(source.shippingAddress, source.addressSnapshot, source.address),
    buyerNickname: readFirstText(source.buyerNickname, source.buyerName) || '我',
    sellerId: readPositiveId(source.sellerId),
    buyerId: readPositiveId(source.buyerId),
    productImages: normalizeImages(source.productImages, source.images, source.imageUrls),
    shippingRemark: readFirstText(source.shippingRemark, source.remark),
    sellerNickname: readFirstText(source.sellerNickname, source.sellerName) || '卖家',
  }
}

function normalizeCreateOrderInput(input: CreateBuyerOrderInput) {
  const productId = normalizeOrderId(input.productId, '商品 ID 无效，无法提交下单。')
  const shippingAddress = readFirstText(input.shippingAddress)

  if (shippingAddress.length < 5 || shippingAddress.length > 200) {
    throw new Error('收货地址长度需在 5~200 个字符之间。')
  }

  /**
   * 后端合同当前明确 quantity 固定为 1：
   * 1) 避免前端误传多件导致 400；
   * 2) 页面层只维护“是否下单”这一最小写链路状态，不引入数量编辑歧义。
   */
  return {
    productId,
    shippingAddress,
    quantity: 1,
  }
}

function normalizeCreateOrderResult(payload: unknown): CreateBuyerOrderResult {
  const source = isRecord(payload) ? payload : {}
  const status = normalizeOrderStatus(source.status)

  return {
    orderId: readPositiveId(source.orderId, source.id),
    orderNo: readFirstText(source.orderNo, source.orderSn),
    status,
    statusLabel: getOrderStatusMeta(status).label,
    totalAmount: normalizePrice(source.totalAmount, source.totalPrice, source.amount),
    createTime: readFirstText(source.createTime, source.createdAt),
  }
}

function normalizeCancelOrderInput(input?: CancelBuyerOrderInput) {
  const reason = readFirstText(input?.reason)

  /**
   * cancel reason 是可选输入：
   * - 为空时不传 body，保持和后端 `required = false` 一致；
   * - 非空时在 API 层统一做长度兜底，避免页面层重复校验。
   */
  if (!reason) {
    return undefined
  }

  if (reason.length > 100) {
    throw new Error('取消原因长度不能超过 100 个字符。')
  }

  return { reason }
}

function normalizeShipOrderInput(input: ShipSellerOrderInput) {
  const shippingCompany = readFirstText(input.shippingCompany)
  const trackingNo = readFirstText(input.trackingNo)
  const remark = readFirstText(input.remark)

  const validationError = readShipSellerOrderValidationError({ shippingCompany, trackingNo, remark })
  if (validationError) {
    throw new Error(validationError)
  }

  return {
    shippingCompany,
    trackingNo,
    ...(remark ? { remark } : {}),
  }
}

export function readShipSellerOrderValidationError(input: ShipSellerOrderInput) {
  const shippingCompany = readFirstText(input.shippingCompany)
  const trackingNo = readFirstText(input.trackingNo)
  const remark = readFirstText(input.remark)

  /**
   * Day06 的发货表单 guard 下沉到 API 层：
   * - shippingCompany 对齐后端 2~50 与基础字符集约束；
   * - trackingNo 对齐后端 6~50 与字母/数字/横杠约束；
   * - remark 仅做可选字段与长度兜底。
   * 页面层只负责禁用按钮与展示提示，不再散落重复正则。
   */
  if (!SHIPPING_COMPANY_PATTERN.test(shippingCompany)) {
    return '物流公司需为 2~50 个字符，且仅支持中英文、数字、空格、下划线、点和中划线。'
  }

  if (!TRACKING_NO_PATTERN.test(trackingNo)) {
    return '运单号需为 6~50 个字符，且仅支持字母、数字和中划线。'
  }

  if (remark.length > 200) {
    return '发货备注长度不能超过 200 个字符。'
  }

  return ''
}

function normalizeActionMessage(payload: unknown, fallback: string) {
  if (typeof payload === 'string') {
    const normalized = payload.trim()
    if (normalized) {
      return normalized
    }
  }

  const source = isRecord(payload) ? payload : {}
  const message = readFirstText(source.message, source.msg, source.result)
  return message || fallback
}

function normalizeMockPayResult(payload: unknown, fallbackScenario: BuyerMockPayScenario): BuyerMockPayResult {
  const source = isRecord(payload) ? payload : {}

  return {
    orderId: readPositiveId(source.orderId, source.id),
    orderNo: readFirstText(source.orderNo, source.orderSn),
    scenario: normalizeMockPayScenario(source.scenario || fallbackScenario),
    channel: readFirstText(source.channel) || 'mock',
    beforeStatus: normalizeOrderStatus(source.beforeStatus),
    afterStatus: normalizeOrderStatus(source.afterStatus),
    callbackCount: readPositiveInt(1, source.callbackCount),
    firstStatus: normalizeOrderStatus(source.firstStatus),
    firstTradeNo: readFirstText(source.firstTradeNo),
    firstResult: readFirstText(source.firstResult),
    secondStatus: normalizeOrderStatus(source.secondStatus),
    secondTradeNo: readFirstText(source.secondTradeNo),
    secondResult: readFirstText(source.secondResult),
    finalResult: readFirstText(source.finalResult),
  }
}

function normalizeLogisticsTraceItem(payload: unknown): SellerOrderLogisticsTraceItem {
  const source = isRecord(payload) ? payload : {}

  return {
    time: readFirstText(source.time, source.eventTime),
    location: readFirstText(source.location, source.city, source.address),
    status: readFirstText(source.status, source.content, source.description),
  }
}

function normalizeSellerOrderLogistics(payload: unknown): SellerOrderLogistics {
  const source = isRecord(payload) ? payload : {}
  const status = normalizeOrderStatus(source.status)

  return {
    orderId: readPositiveId(source.orderId, source.id),
    status,
    statusLabel: getOrderStatusMeta(status).label,
    shippingCompany: readFirstText(source.shippingCompany, source.logisticsCompany),
    trackingNo: readFirstText(source.trackingNo, source.logisticsNo),
    shipTime: readFirstText(source.shipTime),
    provider: readFirstText(source.provider) || '物流服务',
    lastSyncTime: readFirstText(source.lastSyncTime, source.syncTime),
    trace: readFirstArray(source.trace, source.logisticsTrace).map((item) => normalizeLogisticsTraceItem(item)),
  }
}

function normalizePagePayload<T>(payload: unknown, page: number, pageSize: number, mapper: (item: unknown) => T): PagePayload<T> {
  const source = isRecord(payload) ? payload : {}

  const rawList = readFirstArray(source.list, source.records, source.rows, source.items, Array.isArray(payload) ? payload : undefined)
  const list = rawList.map((item) => mapper(item))

  return {
    list,
    total: readNonNegativeInt(list.length, source.total, source.totalCount, source.count),
    page: readPositiveInt(page, source.page, source.current, source.pageNum),
    pageSize: readPositiveInt(pageSize, source.pageSize, source.size, source.limit),
  }
}

export function createEmptyBuyerOrderPage(): PagePayload<BuyerOrderSummary> {
  return {
    list: [],
    total: 0,
    page: DEFAULT_PAGE,
    pageSize: DEFAULT_PAGE_SIZE,
  }
}

export function createEmptySellerOrderPage(): PagePayload<SellerOrderSummary> {
  return {
    list: [],
    total: 0,
    page: DEFAULT_PAGE,
    pageSize: DEFAULT_PAGE_SIZE,
  }
}

export function createEmptyBuyerOrderDetail(): BuyerOrderDetail {
  return {
    orderId: null,
    orderNo: '',
    status: 'unknown',
    statusLabel: '状态未知',
    productId: null,
    productTitle: '',
    productThumbnail: '',
    sellerNickname: '',
    dealPrice: 0,
    quantity: 1,
    createTime: '',
    payTime: '',
    shipTime: '',
    completeTime: '',
    shippingCompany: '',
    trackingNo: '',
    totalAmount: 0,
    shippingAddress: '',
    buyerNickname: '',
    sellerId: null,
    buyerId: null,
    productImages: [],
    shippingRemark: '',
  }
}

export function createEmptySellerOrderDetail(): SellerOrderDetail {
  return createEmptyBuyerOrderDetail()
}

export function createEmptySellerOrderLogistics(): SellerOrderLogistics {
  return {
    orderId: null,
    status: 'unknown',
    statusLabel: '状态未知',
    shippingCompany: '',
    trackingNo: '',
    shipTime: '',
    provider: '',
    lastSyncTime: '',
    trace: [],
  }
}

export function getOrderStatusMeta(status: string): OrderStatusMeta {
  const normalized = normalizeOrderStatus(status)

  switch (normalized) {
    case 'pending':
      return { label: '待支付', tone: 'warning' }
    case 'paid':
      return { label: '待发货', tone: 'accent' }
    case 'shipped':
      return { label: '已发货', tone: 'success' }
    case 'completed':
      return { label: '已完成', tone: 'neutral' }
    case 'cancelled':
      return { label: '已取消', tone: 'neutral' }
    default:
      return { label: '状态未知', tone: 'neutral' }
  }
}

export const getBuyerOrderStatusMeta = getOrderStatusMeta
export const getSellerOrderStatusMeta = getOrderStatusMeta

export async function getBuyerOrderList(query?: BuyerOrderListQuery) {
  const normalized = normalizeListQuery(query)
  const payload = await request.get<any, unknown>('/user/orders/buy', { params: normalized.params })
  return normalizePagePayload(payload, normalized.page, normalized.pageSize, normalizeBuyerOrderSummary)
}

export async function getSellerOrderList(query?: SellerOrderListQuery) {
  const normalized = normalizeListQuery(query)
  const payload = await request.get<any, unknown>('/user/orders/sell', { params: normalized.params })
  return normalizePagePayload(payload, normalized.page, normalized.pageSize, normalizeSellerOrderSummary)
}

function normalizeOrderId(orderId: number | string, errorMessage = '订单 ID 无效，无法查看详情。') {
  const normalized = readPositiveInt(-1, orderId)
  if (normalized <= 0) {
    throw new Error(errorMessage)
  }

  return normalized
}

export async function getBuyerOrderDetail(orderId: number | string) {
  const payload = await request.get<any, unknown>(`/user/orders/${normalizeOrderId(orderId)}`)
  return normalizeOrderDetail(payload)
}

export async function getSellerOrderDetail(orderId: number | string) {
  const payload = await request.get<any, unknown>(`/user/orders/${normalizeOrderId(orderId)}`)
  return normalizeOrderDetail(payload)
}

export async function getSellerOrderLogistics(orderId: number | string) {
  const payload = await request.get<any, unknown>(`/user/orders/${normalizeOrderId(orderId, '订单 ID 无效，无法查看物流。')}/logistics`)
  return normalizeSellerOrderLogistics(payload)
}

export async function createBuyerOrder(input: CreateBuyerOrderInput) {
  const payload = await request.post<any, unknown>('/user/orders', normalizeCreateOrderInput(input))
  return normalizeCreateOrderResult(payload)
}

export async function shipSellerOrder(orderId: number | string, input: ShipSellerOrderInput) {
  const payload = await request.post<any, unknown>(
    `/user/orders/${normalizeOrderId(orderId, '订单 ID 无效，无法执行发货。')}/ship`,
    normalizeShipOrderInput(input),
  )
  return normalizeActionMessage(payload, '发货请求已提交。')
}

export async function payBuyerOrder(orderId: number | string) {
  const payload = await request.post<any, unknown>(`/user/orders/${normalizeOrderId(orderId, '订单 ID 无效，无法发起支付。')}/pay`)
  return normalizeActionMessage(payload, '支付请求已提交。')
}

export async function cancelBuyerOrder(orderId: number | string, input?: CancelBuyerOrderInput) {
  const payload = await request.post<any, unknown>(
    `/user/orders/${normalizeOrderId(orderId, '订单 ID 无效，无法取消订单。')}/cancel`,
    normalizeCancelOrderInput(input),
  )
  return normalizeActionMessage(payload, '取消订单请求已提交。')
}

export async function confirmBuyerOrderReceipt(orderId: number | string) {
  const payload = await request.post<any, unknown>(
    `/user/orders/${normalizeOrderId(orderId, '订单 ID 无效，无法确认收货。')}/confirm-receipt`,
  )
  return normalizeActionMessage(payload, '确认收货请求已提交。')
}

export async function simulateBuyerOrderMockPay(orderId: number | string, scenario: BuyerMockPayScenario = 'SUCCESS') {
  const normalizedScenario = normalizeMockPayScenario(scenario)
  const payload = await request.post<any, unknown>(
    `/user/orders/${normalizeOrderId(orderId, '订单 ID 无效，无法触发 mock 支付。')}/pay/mock`,
    undefined,
    {
      params: { scenario: normalizedScenario },
    },
  )
  return normalizeMockPayResult(payload, normalizedScenario)
}
