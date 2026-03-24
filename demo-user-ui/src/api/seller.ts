import request from '@/utils/request'

export interface SellerSummary {
  totalProducts: number
  underReviewProducts: number
  onSaleProducts: number
  offShelfProducts: number
  soldProducts: number
  totalOrders: number
  pendingOrders: number
  paidOrders: number
  shippedOrders: number
  completedOrders: number
  cancelledOrders: number
}

type UnknownRecord = Record<string, unknown>

function isRecord(value: unknown): value is UnknownRecord {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function normalizeCount(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }

  if (typeof value === 'string') {
    const normalized = value.trim()
    if (!normalized) {
      return 0
    }

    const parsed = Number(normalized)
    if (Number.isFinite(parsed)) {
      return parsed
    }
  }

  return 0
}

function readCount(source: UnknownRecord, ...keys: string[]) {
  for (const key of keys) {
    if (key in source) {
      return normalizeCount(source[key])
    }
  }

  return 0
}

export function createEmptySellerSummary(): SellerSummary {
  return {
    totalProducts: 0,
    underReviewProducts: 0,
    onSaleProducts: 0,
    offShelfProducts: 0,
    soldProducts: 0,
    totalOrders: 0,
    pendingOrders: 0,
    paidOrders: 0,
    shippedOrders: 0,
    completedOrders: 0,
    cancelledOrders: 0,
  }
}

export function normalizeSellerSummary(payload: unknown): SellerSummary {
  const source = isRecord(payload) ? payload : {}

  /**
   * 首页摘要只消费一个稳定的 `SellerSummary` 结构。
   * 无论后端返回数字、数字字符串，还是个别历史别名字段，都在 API 层归一成页面可直接展示的数字。
   */
  return {
    totalProducts: readCount(source, 'totalProducts', 'productTotal'),
    underReviewProducts: readCount(source, 'underReviewProducts', 'reviewingProducts'),
    onSaleProducts: readCount(source, 'onSaleProducts', 'saleProducts'),
    offShelfProducts: readCount(source, 'offShelfProducts', 'offSaleProducts'),
    soldProducts: readCount(source, 'soldProducts', 'soldOutProducts'),
    totalOrders: readCount(source, 'totalOrders', 'orderTotal'),
    pendingOrders: readCount(source, 'pendingOrders', 'pendingOrderCount'),
    paidOrders: readCount(source, 'paidOrders', 'paidOrderCount'),
    shippedOrders: readCount(source, 'shippedOrders', 'shippedOrderCount'),
    completedOrders: readCount(source, 'completedOrders', 'completeOrders', 'completedOrderCount'),
    cancelledOrders: readCount(source, 'cancelledOrders', 'canceledOrders', 'cancelledOrderCount'),
  }
}

export async function getSellerSummary() {
  const payload = await request.get<any, unknown>('/user/seller/summary')
  return normalizeSellerSummary(payload)
}
