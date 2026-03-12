import request from '@/utils/request'
import { isMockEnabled } from '@/mock/config'
import { mockApproveProductReview, mockGetProductReviewList, mockRejectProductReview } from '@/mock/product'

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

function normalizeRiskLevel(category?: string): ProductReviewItem['riskLevel'] {
  const text = (category || '').toLowerCase()
  if (text.includes('奢') || text.includes('包') || text.includes('珠宝')) return 'HIGH'
  if (text.includes('数码') || text.includes('鞋')) return 'MEDIUM'
  return 'LOW'
}

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
