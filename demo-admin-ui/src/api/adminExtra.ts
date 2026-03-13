import request from '@/utils/request'

export interface StatisticsOrderGmv {
  date: string
  gmv: number
  orderCount: number
}

export interface StatisticsProductPublish {
  date: string
  total: number
  byCategory: Record<string, number>
}

export interface HomeStatisticsSnapshot {
  dau: number
  orderGmv: StatisticsOrderGmv
  productPublish: StatisticsProductPublish
}

export async function fetchHomeStatistics(date: string): Promise<HomeStatisticsSnapshot> {
  const [dau, orderGmv, productPublish] = await Promise.all([
    request.get<any, number>('/admin/statistics/dau', { params: { date } }),
    request.get<any, StatisticsOrderGmv>('/admin/statistics/order-gmv', { params: { date } }),
    request.get<any, StatisticsProductPublish>('/admin/statistics/product-publish', { params: { date } }),
  ])

  return {
    dau,
    orderGmv,
    productPublish,
  }
}

export interface UserCreditInfo {
  userId: number
  creditScore: number
  creditLevel: string
  creditUpdatedAt: string
}

export interface CreditLogItem {
  id: number
  userId: number
  delta: number
  reasonType: string
  refId: number | null
  scoreBefore: number
  scoreAfter: number
  reasonNote: string
  createTime: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

export function fetchUserCredit(userId: string | number) {
  return request.get<any, UserCreditInfo>('/admin/credit', {
    params: { userId },
  })
}

export function fetchUserCreditLogs(userId: string | number, page = 1, pageSize = 10) {
  return request.get<any, PageResult<CreditLogItem>>('/admin/credit/logs', {
    params: { userId, page, pageSize },
  })
}

export function recalcUserCredit(userId: string | number) {
  return request.post<any, UserCreditInfo>('/admin/credit/recalc', null, {
    params: { userId },
  })
}

export interface UserViolationItem {
  id: number
  userId: number
  username: string | null
  violationType: string
  violationTypeDesc: string | null
  orderId: string | null
  description: string | null
  evidenceUrls: string[] | null
  punishmentResult: string | null
  recordTime: string
  creditScoreChange: number | null
}

export interface UserViolationRecordResponse {
  list: UserViolationItem[]
}

export function fetchUserViolationRecords(userId: string | number, page = 1, size = 10) {
  return request.get<any, UserViolationRecordResponse>('/admin/users', {
    params: { userId, page, size },
  })
}

export interface ProductViolationItem {
  id: number
  productId: number
  violationType: string
  violationDesc: string
  createdBy: number
  createTime: string
}

export function fetchProductViolations(productId: string | number, page = 1, pageSize = 10) {
  return request.get<any, PageResult<ProductViolationItem>>(`/admin/products/${productId}/violations`, {
    params: { page, pageSize },
  })
}

export interface ForceOffShelfPayload {
  reasonCode: string
  reasonText: string
  reportTicketNo?: string
}

export function forceOffShelfProduct(productId: string | number, payload: ForceOffShelfPayload) {
  return request.put<any, string>(`/admin/products/${productId}/force-off-shelf`, payload)
}

export interface OutboxMetrics {
  new: number
  sent: number
  fail: number
  failRetrySum: number
  queriedAt: number
  excludeExchanges: string[]
}

export function fetchOutboxMetrics() {
  return request.get<any, OutboxMetrics>('/admin/ops/outbox/metrics')
}

export function publishOutboxOnce(limit = 50) {
  return request.post<any, { pulled: number; sent: number; failed: number; limit: number }>(
    '/admin/ops/outbox/publish-once',
    null,
    { params: { limit } },
  )
}

export interface ViolationTypeDistribution {
  violationType: string
  violationTypeDesc: string | null
  count: number
  percentage: number
}

export interface ViolationStatistics {
  violationTypeDistribution: ViolationTypeDistribution[]
}

export function fetchViolationStatistics() {
  return request.get<any, ViolationStatistics>('/admin/users/user-violations/statistics')
}

export function fetchShipTimeoutTasks(page = 1, pageSize = 10) {
  return request.get<any, PageResult<any>>('/admin/ops/tasks/ship-timeout', {
    params: { page, pageSize },
  })
}

export function fetchRefundTasks(page = 1, pageSize = 10) {
  return request.get<any, PageResult<any>>('/admin/ops/tasks/refund', {
    params: { page, pageSize },
  })
}

export function fetchShipReminderTasks(page = 1, pageSize = 10) {
  return request.get<any, PageResult<any>>('/admin/ops/tasks/ship-reminder', {
    params: { page, pageSize },
  })
}

export function runShipTimeoutOnce(limit = 50) {
  return request.post<any, { taskType: string; batchSize: number; success: number; processedAt: number }>(
    '/admin/ops/tasks/ship-timeout/run-once',
    null,
    { params: { limit } },
  )
}

export function runRefundOnce(limit = 50) {
  return request.post<any, { taskType: string; batchSize: number; success: number; processedAt: number }>(
    '/admin/ops/tasks/refund/run-once',
    null,
    { params: { limit } },
  )
}

export function runShipReminderOnce(limit = 50) {
  return request.post<any, { taskType: string; batchSize: number; success: number; processedAt: number }>(
    '/admin/ops/tasks/ship-reminder/run-once',
    null,
    { params: { limit } },
  )
}

export function fetchAdminOrders(page = 1, pageSize = 10) {
  return request.get<any, PageResult<any>>('/admin/orders', {
    params: { page, pageSize },
  })
}
