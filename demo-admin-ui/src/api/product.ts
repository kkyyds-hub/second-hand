import request from '@/utils/request'
import { isMockEnabled } from '@/mock/config'
import { mockApproveProductReview, mockGetProductReviewList, mockRejectProductReview } from '@/mock/product'

/**
 * 商品审核 API 收口文件：
 * - 页面只消费 ProductReviewItem 这种前端展示模型
 * - 后端状态码、价格格式、风险等级推断都在这里统一整理
 */
/**
 * 商品审核列表项的类型定义
 */
export interface ProductReviewItem {
  id: string
  title: string
  category: string
  seller: string
  price: string
  submitTime: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
}

export interface ProductReviewQuery {
  keyword?: string
  status?: 'PENDING' | 'APPROVED' | 'REJECTED'
}

export interface ProductReviewResponse {
  total: number
  items: ProductReviewItem[]
}

/**
 * 审核页会反复消费状态 / 风险等级的中文文案。
 * 把这层翻译放在 product API 模块，后续如果审核语义调整，
 * 页面不需要再自己维护一份 `PENDING / APPROVED / REJECTED` 的 switch。
 */
export function getProductReviewStatusText(status?: ProductReviewItem['status'] | string) {
  switch (status) {
    case 'APPROVED':
      return '已通过'
    case 'REJECTED':
      return '已驳回'
    case 'PENDING':
      return '待审核'
    default:
      return '未知'
  }
}

export function getProductReviewRiskText(risk?: ProductReviewItem['riskLevel'] | string) {
  switch (risk) {
    case 'HIGH':
      return '高风险'
    case 'MEDIUM':
      return '中风险'
    case 'LOW':
      return '低风险'
    default:
      return '未知'
  }
}

interface BackendPageResult<T> {
  list?: T[]
  total?: number
  page?: number
  pageSize?: number
}

interface BackendProductItem {
  productId: number
  productName?: string
  category?: string
  status?: string
  submitTime?: string
  price?: number | string | null
  description?: string
}

/**
 * 页面筛选值 -> 后端状态码。
 * review 联调时如果发现筛选不生效，先从这里核对映射。
 */
function toBackendStatus(status?: ProductReviewQuery['status']) {
  switch (status) {
    case 'APPROVED':
      return 'on_sale'
    case 'REJECTED':
      return 'off_shelf'
    case 'PENDING':
      return 'under_review'
    default:
      return undefined
  }
}

/**
 * 后端商品状态 -> 前端审核视图状态。
 * 注意这里是“审核页语义”，不是商城最终展示语义。
 */
function fromBackendStatus(status?: string): ProductReviewItem['status'] {
  switch ((status || '').toLowerCase()) {
    case 'on_sale':
      return 'APPROVED'
    case 'off_shelf':
      return 'REJECTED'
    case 'under_review':
    default:
      return 'PENDING'
  }
}

function formatPrice(value?: number | string | null) {
  if (value === null || value === undefined || value === '') {
    return '--'
  }

  const numeric = Number(value)
  if (Number.isNaN(numeric)) {
    return String(value)
  }

  return `￥ ${numeric.toLocaleString('zh-CN')}`
}

function formatDateTime(value?: string) {
  if (!value) return '--'
  return value.replace('T', ' ').slice(0, 19)
}

/**
 * 当前风险等级先按类目做轻量推断：
 * 奢品 / 珠宝优先标高风险，数码 / 鞋类标中风险。
 * 后续如果后端补了独立风控等级字段，应优先改这里而不是改页面。
 */
function normalizeRiskLevel(category?: string): ProductReviewItem['riskLevel'] {
  const text = (category || '').toLowerCase()
  if (text.includes('奢') || text.includes('包') || text.includes('珠宝')) return 'HIGH'
  if (text.includes('数码') || text.includes('鞋')) return 'MEDIUM'
  return 'LOW'
}

/**
 * 单条商品归一化：
 * - 把后端字段名转换成页面能直接渲染的字段
 * - 把 seller 这类暂未打通的字段留兜底值，避免页面出现 undefined
 */
function normalizeProduct(item: BackendProductItem): ProductReviewItem {
  return {
    id: String(item.productId),
    title: item.productName || `商品#${item.productId}`,
    category: item.category || '--',
    seller: '--',
    price: formatPrice(item.price),
    submitTime: formatDateTime(item.submitTime),
    status: fromBackendStatus(item.status),
    riskLevel: normalizeRiskLevel(item.category),
  }
}

/**
 * 查询商品审核列表。
 */
export function getProductReviewList(query: ProductReviewQuery): Promise<ProductReviewResponse> {
  if (isMockEnabled()) {
    return mockGetProductReviewList(query)
  }

  // 当前审核页先一次性拉较大的列表，再在前端做展示；后续真分页时优先改这里。
  return request
    .get<any, BackendPageResult<BackendProductItem>>('/admin/products/pending-approval', {
      params: {
        page: 1,
        pageSize: 100,
        productName: query.keyword,
        status: toBackendStatus(query.status),
      },
    })
    .then((res) => ({
      total: res.total || 0,
      items: (res.list || []).map(normalizeProduct),
    }))
}

/**
 * 通过审核。
 */
export function approveProductReview(id: string): Promise<void> {
  if (isMockEnabled()) {
    return mockApproveProductReview(id)
  }

  return request.put<any, void>(`/admin/products/${id}/approve`)
}

/**
 * 驳回审核。
 */
export function rejectProductReview(id: string, reason: string): Promise<void> {
  if (isMockEnabled()) {
    return mockRejectProductReview(id)
  }

  return request.put<any, void>(`/admin/products/${id}/reject`, {
    reason,
  })
}
