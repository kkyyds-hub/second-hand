import type { ProductReviewItem, ProductReviewQuery, ProductReviewResponse } from '@/api/product'
import { cloneData, mockDelay, readLocalJson, writeLocalJson } from './config'

const PRODUCT_STORAGE_KEY = 'demo_admin_mock_product_review_v1'

const productSeed: ProductReviewItem[] = [
  {
    id: 'PRD-8902',
    title: 'Apple iPhone 15 Pro Max 256GB 钛金属',
    category: '数码3C',
    seller: '数码回收_老王',
    price: '￥ 6,950',
    submitTime: '2026-03-12 10:30:00',
    status: 'PENDING',
    riskLevel: 'LOW',
  },
  {
    id: 'PRD-8903',
    title: '全新未拆封 劳力士绿水鬼',
    category: '奢侈品',
    seller: 'WatchMaster',
    price: '￥ 125,000',
    submitTime: '2026-03-12 11:15:22',
    status: 'PENDING',
    riskLevel: 'HIGH',
  },
  {
    id: 'PRD-8904',
    title: 'Nike Air Force 1 联名款 42码',
    category: '潮鞋',
    seller: 'Sneaker搬砖人',
    price: '￥ 8,500',
    submitTime: '2026-03-12 14:20:10',
    status: 'PENDING',
    riskLevel: 'MEDIUM',
  },
  {
    id: 'PRD-8890',
    title: 'Sony A7M4 微单套机 95新',
    category: '数码3C',
    seller: '光影流年',
    price: '￥ 13,200',
    submitTime: '2026-03-11 16:45:00',
    status: 'APPROVED',
    riskLevel: 'LOW',
  },
  {
    id: 'PRD-8885',
    title: '高仿 LV 经典老花包',
    category: '箱包',
    seller: '时尚买手',
    price: '￥ 500',
    submitTime: '2026-03-11 09:10:00',
    status: 'REJECTED',
    riskLevel: 'HIGH',
  },
]

function loadProducts() {
  return readLocalJson<ProductReviewItem[]>(PRODUCT_STORAGE_KEY, cloneData(productSeed))
}

function saveProducts(list: ProductReviewItem[]) {
  writeLocalJson(PRODUCT_STORAGE_KEY, list)
}

export async function mockGetProductReviewList(query: ProductReviewQuery): Promise<ProductReviewResponse> {
  await mockDelay()
  const keyword = (query.keyword || '').trim().toLowerCase()
  const status = query.status

  const items = loadProducts().filter((item) => {
    if (status && item.status !== status) return false
    if (!keyword) return true

    const text = `${item.id} ${item.title} ${item.seller}`.toLowerCase()
    return text.includes(keyword)
  })

  return {
    total: items.length,
    items: cloneData(items),
  }
}

export async function mockApproveProductReview(id: string): Promise<void> {
  await mockDelay(220)
  const list = loadProducts()
  const target = list.find((item) => item.id === id)
  if (!target) {
    throw new Error('商品不存在')
  }
  target.status = 'APPROVED'
  saveProducts(list)
}

export async function mockRejectProductReview(id: string): Promise<void> {
  await mockDelay(220)
  const list = loadProducts()
  const target = list.find((item) => item.id === id)
  if (!target) {
    throw new Error('商品不存在')
  }
  target.status = 'REJECTED'
  saveProducts(list)
}
