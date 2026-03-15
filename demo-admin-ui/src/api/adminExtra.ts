import request from '@/utils/request'

/**
 * 这批扩展接口主要服务首页统计与运维中心。
 * FrontDay08 之后，页面优先消费这里整理好的 bundle 结果，
 * review 时重点关注 snapshot / availability / failedSources 是否仍保持一致。
 */
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

export interface HomeStatisticsAvailability {
  dau: boolean
  orderGmv: boolean
  productPublish: boolean
}

export type BundleNoticeTone = 'warning' | 'danger'

export interface BundleNotice {
  tone: BundleNoticeTone
  title: string
  message: string
}

export interface HomeStatisticsBundle {
  snapshot: HomeStatisticsSnapshot
  availability: HomeStatisticsAvailability
  hasAnySuccess: boolean
  failedSources: string[]
  failureSummary: string
  notice: BundleNotice | null
}

const HOME_STATISTICS_SOURCE_LABELS: Record<keyof HomeStatisticsAvailability, string> = {
  dau: 'DAU',
  orderGmv: '订单与 GMV',
  productPublish: '发布量',
}

/**
 * 把 availability 布尔位翻译成页面真正会展示的中文来源名。
 *
 * FrontDay08 之后，页面层不再自己维护“某个布尔位对应哪段文案”，
 * 而是直接消费 API 模块给出的失败来源列表，避免同一套映射继续散落在页面里。
 */
const collectUnavailableSources = <Key extends string>(
  availability: Record<Key, boolean>,
  sourceLabels: Record<Key, string>,
) => {
  return (Object.keys(sourceLabels) as Key[])
    .filter((key) => !availability[key])
    .map((key) => sourceLabels[key])
}

const buildFailureSummary = (scopeLabel: string, failedSources: string[]) => {
  if (!failedSources.length) {
    return ''
  }

  return `${scopeLabel}（${failedSources.join('、')}）`
}

const buildBundleNotice = (
  failedSources: string[],
  hasAnySuccess: boolean,
  config: {
    warningTitle: string
    dangerTitle: string
    buildWarningMessage: (failedSources: string[]) => string
    buildDangerMessage: (failedSources: string[]) => string
  },
): BundleNotice | null => {
  if (!failedSources.length) {
    return null
  }

  return hasAnySuccess
    ? {
        tone: 'warning',
        title: config.warningTitle,
        message: config.buildWarningMessage(failedSources),
      }
    : {
        tone: 'danger',
        title: config.dangerTitle,
        message: config.buildDangerMessage(failedSources),
      }
}

/**
 * 兜底对象仍然放在 API 层，保证页面拿到的结构稳定，
 * 页面不用再自己判断 null / undefined / 缺字段。
 */
const createEmptyOrderGmv = (date: string): StatisticsOrderGmv => ({
  date,
  gmv: 0,
  orderCount: 0,
})

const createEmptyProductPublish = (date: string): StatisticsProductPublish => ({
  date,
  total: 0,
  byCategory: {},
})

const normalizeOrderGmv = (date: string, payload?: Partial<StatisticsOrderGmv> | null): StatisticsOrderGmv => ({
  date: payload?.date || date,
  gmv: Number(payload?.gmv ?? 0),
  orderCount: Number(payload?.orderCount ?? 0),
})

const normalizeProductPublish = (
  date: string,
  payload?: Partial<StatisticsProductPublish> | null,
): StatisticsProductPublish => ({
  date: payload?.date || date,
  total: Number(payload?.total ?? 0),
  byCategory: payload?.byCategory ?? {},
})

/**
 * 这是给旧调用方保留的严格模式入口。
 * 如果页面需要“部分成功可展示”的能力，应优先改用 bundle 版本。
 */
export async function fetchHomeStatistics(date: string): Promise<HomeStatisticsSnapshot> {
  const bundle = await fetchHomeStatisticsBundle(date)
  const isAllAvailable =
    bundle.availability.dau &&
    bundle.availability.orderGmv &&
    bundle.availability.productPublish

  if (!isAllAvailable) {
    throw new Error(bundle.notice?.message || '首页统计接口未全部可用')
  }

  return bundle.snapshot
}

/**
 * Bundle 版本使用 allSettled 处理“部分成功”场景，
 * 避免单个统计接口失败时整块首页统计直接中断。
 */
export async function fetchHomeStatisticsBundle(date: string): Promise<HomeStatisticsBundle> {
  const [dau, orderGmv, productPublish] = await Promise.allSettled([
    request.get<any, number>('/admin/statistics/dau', { params: { date } }),
    request.get<any, StatisticsOrderGmv>('/admin/statistics/order-gmv', { params: { date } }),
    request.get<any, StatisticsProductPublish>('/admin/statistics/product-publish', { params: { date } }),
  ])

  const availability: HomeStatisticsAvailability = {
    dau: dau.status === 'fulfilled',
    orderGmv: orderGmv.status === 'fulfilled',
    productPublish: productPublish.status === 'fulfilled',
  }
  const failedSources = collectUnavailableSources(availability, HOME_STATISTICS_SOURCE_LABELS)
  const hasAnySuccess = Object.values(availability).some(Boolean)
  const notice = buildBundleNotice(failedSources, hasAnySuccess, {
    warningTitle: '看板部分数据暂未同步',
    dangerTitle: '看板暂未同步成功',
    buildWarningMessage: (sources) => `部分数据暂未同步：${sources.join('、')}。页面先展示已同步内容。`,
    buildDangerMessage: (sources) => `看板暂未同步成功：${sources.join('、')}。当前先保留既有卡片与兜底视图，请稍后重试。`,
  })

  return {
    snapshot: {
      dau: dau.status === 'fulfilled' ? Number(dau.value ?? 0) : 0,
      orderGmv: orderGmv.status === 'fulfilled' ? normalizeOrderGmv(date, orderGmv.value) : createEmptyOrderGmv(date),
      productPublish: productPublish.status === 'fulfilled'
        ? normalizeProductPublish(date, productPublish.value)
        : createEmptyProductPublish(date),
    },
    availability,
    hasAnySuccess,
    failedSources,
    failureSummary: buildFailureSummary('统计快照', failedSources),
    notice,
  }
}

// 用户信用信息。
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

// 通用分页结果，供多个扩展接口复用。
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

// 用户违规记录与统计。
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

// 运维中心使用的 Outbox 指标。
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

/**
 * 运行概览 bundle 仍然挂在 adminExtra API 模块下，
 * 目的是把失败来源、摘要与快照一起稳定输出给页面。
 */
export interface OpsRuntimeAvailability {
  adminOrders: boolean
  outboxMetrics: boolean
  shipTimeoutTasks: boolean
  refundTasks: boolean
  shipReminderTasks: boolean
  violationStatistics: boolean
}

export interface OpsRuntimeSnapshot {
  orderTotal: number
  outboxNew: number
  outboxFail: number
  outboxSent: number
  shipTimeoutTotal: number
  refundTotal: number
  shipReminderTotal: number
  topViolationType: string
  topViolationCount: number
}

export interface OpsRuntimeBundle {
  snapshot: OpsRuntimeSnapshot
  availability: OpsRuntimeAvailability
  hasAnySuccess: boolean
  failedSources: string[]
  failureSummary: string
  notice: BundleNotice | null
}

const OPS_RUNTIME_SOURCE_LABELS: Record<keyof OpsRuntimeAvailability, string> = {
  adminOrders: '订单快照',
  outboxMetrics: 'Outbox 指标',
  shipTimeoutTasks: '发货超时任务',
  refundTasks: '退款任务',
  shipReminderTasks: '发货提醒任务',
  violationStatistics: '违规统计',
}

const createEmptyOpsRuntimeSnapshot = (): OpsRuntimeSnapshot => ({
  orderTotal: 0,
  outboxNew: 0,
  outboxFail: 0,
  outboxSent: 0,
  shipTimeoutTotal: 0,
  refundTotal: 0,
  shipReminderTotal: 0,
  topViolationType: '--',
  topViolationCount: 0,
})

const normalizePageTotal = (payload?: Partial<PageResult<any>> | null) => Number(payload?.total ?? 0)

export const getOpsRuntimeFailedSources = (availability: OpsRuntimeAvailability) =>
  collectUnavailableSources(availability, OPS_RUNTIME_SOURCE_LABELS)

/**
 * allSettled 让运行概览允许“部分成功”，
 * 页面只消费这里整理好的 availability / failedSources / notice 结构。
 */
export async function fetchOpsRuntimeBundle(): Promise<OpsRuntimeBundle> {
  const [orders, outbox, shipTimeout, refund, shipReminder, violationStats] = await Promise.allSettled([
    fetchAdminOrders(1, 1),
    fetchOutboxMetrics(),
    fetchShipTimeoutTasks(1, 1),
    fetchRefundTasks(1, 1),
    fetchShipReminderTasks(1, 1),
    fetchViolationStatistics(),
  ])

  const availability: OpsRuntimeAvailability = {
    adminOrders: orders.status === 'fulfilled',
    outboxMetrics: outbox.status === 'fulfilled',
    shipTimeoutTasks: shipTimeout.status === 'fulfilled',
    refundTasks: refund.status === 'fulfilled',
    shipReminderTasks: shipReminder.status === 'fulfilled',
    violationStatistics: violationStats.status === 'fulfilled',
  }
  const failedSources = getOpsRuntimeFailedSources(availability)
  const hasAnySuccess = Object.values(availability).some(Boolean)
  const notice = buildBundleNotice(failedSources, hasAnySuccess, {
    warningTitle: '部分运行概览暂未同步',
    dangerTitle: '运行概览暂未同步成功',
    buildWarningMessage: (sources) => `部分运行概览暂未同步：${sources.join('、')}。页面先展示已同步内容。`,
    buildDangerMessage: (sources) => `运行概览暂未同步成功：${sources.join('、')}。请稍后重试。`,
  })

  const violationDistribution =
    violationStats.status === 'fulfilled' ? violationStats.value?.violationTypeDistribution || [] : []
  const firstViolation = violationDistribution[0]

  return {
    snapshot: {
      ...createEmptyOpsRuntimeSnapshot(),
      orderTotal: orders.status === 'fulfilled' ? normalizePageTotal(orders.value) : 0,
      outboxNew: outbox.status === 'fulfilled' ? Number(outbox.value?.new ?? 0) : 0,
      outboxFail: outbox.status === 'fulfilled' ? Number(outbox.value?.fail ?? 0) : 0,
      outboxSent: outbox.status === 'fulfilled' ? Number(outbox.value?.sent ?? 0) : 0,
      shipTimeoutTotal: shipTimeout.status === 'fulfilled' ? normalizePageTotal(shipTimeout.value) : 0,
      refundTotal: refund.status === 'fulfilled' ? normalizePageTotal(refund.value) : 0,
      shipReminderTotal: shipReminder.status === 'fulfilled' ? normalizePageTotal(shipReminder.value) : 0,
      topViolationType: firstViolation?.violationTypeDesc || firstViolation?.violationType || '--',
      topViolationCount: Number(firstViolation?.count ?? 0),
    },
    availability,
    hasAnySuccess,
    failedSources,
    failureSummary: buildFailureSummary('运行概览', failedSources),
    notice,
  }
}
