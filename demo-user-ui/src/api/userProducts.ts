import request from '@/utils/request'

export interface UserProductListQuery {
  status?: string
  page?: number
  pageSize?: number
}

export interface UserProductSummary {
  id: number | null
  title: string
  description: string
  price: number
  category: string
  status: string
  viewCount: number
  reason: string
  createTime: string
  updateTime: string
}

export interface UserProductDetail extends UserProductSummary {
  imageUrls: string[]
  reviewRemark: string
  submitTime: string
}

export interface CreateUserProductInput {
  title: string
  description?: string
  price: number | string
  category?: string
  imageUrls?: string[]
}

export interface UpdateUserProductInput {
  title: string
  description?: string
  price: number | string
  imageUrls?: string[]
}

export interface UserProductStatusMeta {
  label: string
  tone: 'neutral' | 'accent' | 'success' | 'warning'
}

export type UserProductStatusAction = 'off_shelf' | 'resubmit' | 'on_shelf' | 'withdraw'

export interface UserProductStatusActionMeta {
  action: UserProductStatusAction
  label: string
  description: string
  tone: 'neutral' | 'accent' | 'warning'
}

export interface PagePayload<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

type UnknownRecord = Record<string, unknown>

const DEFAULT_PAGE = 1
const DEFAULT_PAGE_SIZE = 10

function isRecord(value: unknown): value is UnknownRecord {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function readFirstArray(...values: unknown[]) {
  for (const value of values) {
    if (Array.isArray(value)) {
      return value
    }
  }

  return []
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

function normalizePrice(...values: unknown[]) {
  for (const value of values) {
    const normalized = normalizeNumber(value)
    if (normalized !== null && normalized >= 0) {
      return normalized
    }
  }

  return 0
}

function normalizePositivePrice(...values: unknown[]) {
  for (const value of values) {
    const normalized = normalizeNumber(value)
    if (normalized !== null && normalized > 0) {
      return normalized
    }
  }

  throw new Error('商品价格必须大于 0。')
}

function normalizeStatus(value: unknown) {
  const normalized = readFirstText(value).toLowerCase()

  if (['on_sale', 'sold', 'off_shelf', 'under_review', 'rejected'].includes(normalized)) {
    return normalized
  }

  return normalized || 'unknown'
}

function normalizeListQuery(query?: UserProductListQuery) {
  const page = readPositiveInt(DEFAULT_PAGE, query?.page)
  const pageSize = readPositiveInt(DEFAULT_PAGE_SIZE, query?.pageSize)
  const params: Record<string, string | number> = { page, pageSize }

  const status = normalizeStatus(query?.status)
  if (status !== 'unknown') {
    params.status = status
  }

  return { params, page, pageSize }
}

function normalizeProductId(productId: number | string) {
  const normalized = readPositiveInt(-1, productId)
  if (normalized <= 0) {
    throw new Error('商品 ID 无效，无法继续商品操作。')
  }

  return normalized
}

function splitImageUrls(values: unknown[]) {
  return values
    .flatMap((item) => {
      if (Array.isArray(item)) {
        return item
      }

      if (typeof item === 'string') {
        return item.split(',')
      }

      return []
    })
    .map((item) => readFirstText(item))
    .filter(Boolean)
}

function normalizeUserProductSummary(payload: unknown): UserProductSummary {
  const source = isRecord(payload) ? payload : {}
  const id = readNonNegativeInt(-1, source.id, source.productId)

  return {
    id: id >= 0 ? id : null,
    title: readFirstText(source.title, source.name, source.productTitle) || '未命名商品',
    description: readFirstText(source.description, source.summary),
    price: normalizePrice(source.price, source.salePrice),
    category: readFirstText(source.category, source.categoryName),
    status: normalizeStatus(source.status),
    viewCount: readNonNegativeInt(0, source.viewCount, source.views),
    reason: readFirstText(source.reason, source.reviewRemark),
    createTime: readFirstText(source.createTime, source.createdAt),
    updateTime: readFirstText(source.updateTime, source.updatedAt),
  }
}

function normalizeUserProductDetail(payload: unknown): UserProductDetail {
  const source = isRecord(payload) ? payload : {}
  const summary = normalizeUserProductSummary(source)

  /**
   * Day04 详情页统一消费 `imageUrls` 数组：
   * - 兼容后端已按 List<String> 返回；
   * - 同时兜底历史逗号拼接字符串，避免页面层散落字段兼容逻辑。
   */
  const imageUrls = splitImageUrls([source.imageUrls, source.images, source.imageList, source.gallery])

  return {
    ...summary,
    description: readFirstText(source.description, summary.description),
    imageUrls,
    reviewRemark: readFirstText(source.reviewRemark, source.reason),
    submitTime: readFirstText(source.submitTime),
  }
}

function normalizePagePayload<T>(payload: unknown, page: number, pageSize: number, mapper: (item: unknown) => T): PagePayload<T> {
  const source = isRecord(payload) ? payload : {}

  const rawList = readFirstArray(
    source.list,
    source.records,
    source.rows,
    source.items,
    Array.isArray(payload) ? payload : undefined,
  )

  const list = rawList.map((item) => mapper(item))

  return {
    list,
    total: readNonNegativeInt(list.length, source.total, source.totalCount, source.count),
    page: readPositiveInt(page, source.page, source.current, source.pageNum),
    pageSize: readPositiveInt(pageSize, source.pageSize, source.size, source.limit),
  }
}

function normalizeImagePayload(images: unknown) {
  if (!Array.isArray(images)) {
    return []
  }

  return images
    .map((item) => readFirstText(item))
    .filter(Boolean)
}

function normalizeCreatePayload(input: CreateUserProductInput) {
  /**
   * Day04 第二包把创建表单的清洗逻辑集中在 API 层：
   * - 页面层只维护输入态和校验提示；
   * - contract 需要的 `images`、`category` 归一化统一在这里处理。
   */
  return {
    title: readFirstText(input.title),
    description: readFirstText(input.description),
    price: normalizePositivePrice(input.price),
    category: readFirstText(input.category),
    images: normalizeImagePayload(input.imageUrls),
  }
}

function normalizeUpdatePayload(input: UpdateUserProductInput) {
  return {
    title: readFirstText(input.title),
    description: readFirstText(input.description),
    price: normalizePositivePrice(input.price),
    images: normalizeImagePayload(input.imageUrls),
  }
}

function readActionMessage(payload: unknown, fallback: string) {
  const normalized = readFirstText(payload)
  return normalized || fallback
}

function buildUserProductPath(productId: number | string) {
  return `/user/products/${normalizeProductId(productId)}`
}

export function createEmptyUserProductPage(): PagePayload<UserProductSummary> {
  return {
    list: [],
    total: 0,
    page: DEFAULT_PAGE,
    pageSize: DEFAULT_PAGE_SIZE,
  }
}

export function createEmptyUserProductDetail(): UserProductDetail {
  return {
    id: null,
    title: '',
    description: '',
    price: 0,
    category: '',
    status: 'unknown',
    viewCount: 0,
    reason: '',
    createTime: '',
    updateTime: '',
    imageUrls: [],
    reviewRemark: '',
    submitTime: '',
  }
}

export function getUserProductStatusMeta(status: string): UserProductStatusMeta {
  const normalized = normalizeStatus(status)

  switch (normalized) {
    case 'under_review':
      return { label: '审核中', tone: 'accent' }
    case 'on_sale':
      return { label: '在售', tone: 'success' }
    case 'off_shelf':
      return { label: '已下架', tone: 'warning' }
    case 'sold':
      return { label: '已售出', tone: 'neutral' }
    case 'rejected':
      return { label: '审核驳回', tone: 'warning' }
    default:
      return { label: '状态未知', tone: 'neutral' }
  }
}

export function getUserProductStatusActions(status: string): UserProductStatusActionMeta[] {
  const normalized = normalizeStatus(status)

  switch (normalized) {
    case 'on_sale':
      return [
        {
          action: 'off_shelf',
          label: '下架',
          description: '把在售商品转为已下架。',
          tone: 'warning',
        },
      ]
    case 'under_review':
      return [
        {
          action: 'withdraw',
          label: '撤回审核',
          description: '把审核中商品撤回到已下架。',
          tone: 'warning',
        },
        {
          action: 'off_shelf',
          label: '下架',
          description: '兼容入口：审核中商品也可直接下架。',
          tone: 'warning',
        },
      ]
    case 'off_shelf':
    case 'rejected':
      return [
        {
          action: 'resubmit',
          label: '重新提交审核',
          description: '标准入口：下架商品提审。',
          tone: 'accent',
        },
        {
          action: 'on_shelf',
          label: '兼容提审(on-shelf)',
          description: '兼容入口：语义等价于重新提交审核，不是直接在售。',
          tone: 'neutral',
        },
      ]
    default:
      return []
  }
}

export async function getUserProductList(query?: UserProductListQuery) {
  const normalized = normalizeListQuery(query)
  const payload = await request.get<any, unknown>('/user/products', { params: normalized.params })
  return normalizePagePayload(payload, normalized.page, normalized.pageSize, normalizeUserProductSummary)
}

export async function getUserProductDetail(productId: number | string) {
  const payload = await request.get<any, unknown>(buildUserProductPath(productId))
  return normalizeUserProductDetail(payload)
}

export async function createUserProduct(input: CreateUserProductInput) {
  const payload = await request.post<any, unknown>('/user/products', normalizeCreatePayload(input))
  return normalizeUserProductDetail(payload)
}

export async function updateUserProduct(productId: number | string, input: UpdateUserProductInput) {
  const payload = await request.put<any, unknown>(buildUserProductPath(productId), normalizeUpdatePayload(input))
  return normalizeUserProductDetail(payload)
}

export async function deleteUserProduct(productId: number | string) {
  const payload = await request.delete<any, unknown>(buildUserProductPath(productId))
  return readActionMessage(payload, '删除成功')
}

export async function runUserProductStatusAction(productId: number | string, action: UserProductStatusAction) {
  switch (action) {
    case 'off_shelf': {
      const payload = await request.put<any, unknown>(`${buildUserProductPath(productId)}/off-shelf`)
      return readActionMessage(payload, '下架成功')
    }
    case 'resubmit': {
      await request.put<any, unknown>(`${buildUserProductPath(productId)}/resubmit`)
      return '重新提交审核成功'
    }
    case 'on_shelf': {
      /**
       * Day04 兼容语义冻结：on-shelf 入口实质等价于“重新提交审核”。
       * 前端需要显式保留这个提示，避免用户误解成“直接进入在售”。
       */
      await request.put<any, unknown>(`${buildUserProductPath(productId)}/on-shelf`)
      return '兼容提审已提交（on-shelf）'
    }
    case 'withdraw': {
      await request.put<any, unknown>(`${buildUserProductPath(productId)}/withdraw`)
      return '撤回审核成功'
    }
    default:
      return '状态操作已提交'
  }
}
