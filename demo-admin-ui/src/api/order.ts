import request from '@/utils/request'

export type AdminOrderSortField = 'createTime' | 'payTime'
export type AdminOrderSortOrder = 'asc' | 'desc'

export interface AdminOrderItem {
  orderId: number
  orderNo: string
  buyerId: number
  buyerNickname: string
  buyerMobile: string
  sellerId: number
  sellerNickname: string
  sellerMobile: string
  productId: number | null
  productTitle: string | null
  productThumbnail: string | null
  flagCount: number
  totalAmount: number | string
  status: string
  shippingCompany: string | null
  trackingNo: string | null
  createTime: string | null
  payTime: string | null
  shipTime: string | null
  completeTime: string | null
  cancelTime: string | null
  cancelReason: string | null
}

export interface AdminOrderPage {
  list: AdminOrderItem[]
  total: number
  page: number
  pageSize: number
}

export interface AdminOrderQuery {
  page: number
  pageSize: number
  keyword?: string
  status?: string
  startTime?: string
  endTime?: string
  sortField: AdminOrderSortField
  sortOrder: AdminOrderSortOrder
}

export interface AdminOrderLineItem {
  productId: number
  productTitle: string
  productThumbnail: string | null
  productStatus: string
  price: number | string
  quantity: number
}

export interface AdminOrderFlag {
  id: number
  type: string
  remark: string | null
  createdBy: number | null
  createdByNickname: string | null
  createTime: string | null
}

export interface AdminOrderDetail extends Omit<AdminOrderItem, 'productId' | 'productTitle' | 'productThumbnail' | 'flagCount'> {
  shippingAddress: string | null
  shippingRemark: string | null
  afterSaleId: number | null
  afterSaleStatus: string | null
  afterSaleReason: string | null
  afterSalePlatformRemark: string | null
  items: AdminOrderLineItem[]
  flags: AdminOrderFlag[]
}

export type OrderFlagType =
  | 'PAYMENT_RISK'
  | 'PRICE_ANOMALY'
  | 'DELIVERY_RISK'
  | 'AFTERSALE_RISK'
  | 'ACCOUNT_RISK'
  | 'MANUAL_REVIEW'

export interface CreateOrderFlagPayload {
  type: OrderFlagType
  remark?: string
}

const statusLabels: Record<string, string> = {
  pending: '待支付',
  paid: '待发货',
  shipped: '待收货',
  completed: '已完成',
  cancelled: '已取消',
}

const flagLabels: Record<OrderFlagType, string> = {
  PAYMENT_RISK: '支付异常',
  PRICE_ANOMALY: '金额异常',
  DELIVERY_RISK: '发货风险',
  AFTERSALE_RISK: '售后风险',
  ACCOUNT_RISK: '账号风险',
  MANUAL_REVIEW: '人工复核',
}

export function getAdminOrderStatusLabel(status: string) {
  return statusLabels[status] || `未知状态：${status}`
}

export function getOrderFlagLabel(type: string) {
  return flagLabels[type as OrderFlagType] || `未知标记：${type}`
}

export function getAdminOrders(query: AdminOrderQuery) {
  return request.get<any, AdminOrderPage>('/admin/orders', { params: query })
}

export function getAdminOrderDetail(orderId: number) {
  return request.get<any, AdminOrderDetail>(`/admin/orders/${orderId}`)
}

export function getAdminOrderFlags(orderId: number) {
  return request.get<any, AdminOrderFlag[]>(`/admin/orders/${orderId}/flags`)
}

export function createAdminOrderFlag(orderId: number, payload: CreateOrderFlagPayload) {
  return request.post<any, string>(`/admin/orders/${orderId}/flags`, payload)
}
