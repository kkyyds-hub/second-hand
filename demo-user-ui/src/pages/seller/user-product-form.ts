import type { CreateUserProductInput, UpdateUserProductInput, UserProductDetail } from '@/api/userProducts'

export interface UserProductFormModel {
  title: string
  description: string
  category: string
  price: string
  imageUrlsText: string
}

export type UserProductFormField = 'title' | 'description' | 'category' | 'price' | 'imageUrlsText'

const TITLE_MAX_LENGTH = 120
const DESCRIPTION_MAX_LENGTH = 2000
const CATEGORY_MAX_LENGTH = 60
const IMAGE_URL_MAX_LENGTH = 500

function normalizeText(value: unknown) {
  if (typeof value !== 'string') {
    return ''
  }

  return value.trim()
}

function normalizePriceText(value: unknown) {
  return normalizeText(value)
}

function normalizeImageUrlsFromText(value: string) {
  /**
   * Day04 商品图输入允许“换行或逗号”两种录入习惯，
   * 这样 create/edit 可以共用同一个文本域而不牺牲可读性。
   */
  return value
    .split(/[,\n]/g)
    .map((item) => normalizeText(item))
    .filter(Boolean)
}

function parsePositivePrice(value: string) {
  if (!value) {
    return null
  }

  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null
  }

  return parsed
}

export function createEmptyUserProductFormModel(): UserProductFormModel {
  return {
    title: '',
    description: '',
    category: '',
    price: '',
    imageUrlsText: '',
  }
}

export function toUserProductFormModel(source: Partial<UserProductDetail> | null | undefined): UserProductFormModel {
  const images = Array.isArray(source?.imageUrls)
    ? source.imageUrls.map((item) => normalizeText(item)).filter(Boolean)
    : []

  return {
    title: normalizeText(source?.title),
    description: normalizeText(source?.description),
    category: normalizeText(source?.category),
    price: typeof source?.price === 'number' && Number.isFinite(source.price) && source.price > 0 ? String(source.price) : '',
    imageUrlsText: images.join('\n'),
  }
}

export function collectUserProductValidationErrors(
  model: UserProductFormModel,
  options?: { requireCategory?: boolean },
): Partial<Record<UserProductFormField, string>> {
  const errors: Partial<Record<UserProductFormField, string>> = {}

  const title = normalizeText(model.title)
  const description = normalizeText(model.description)
  const category = normalizeText(model.category)
  const priceText = normalizePriceText(model.price)
  const imageUrls = normalizeImageUrlsFromText(model.imageUrlsText)

  if (!title) {
    errors.title = '商品标题不能为空。'
  } else if (title.length > TITLE_MAX_LENGTH) {
    errors.title = `商品标题不能超过 ${TITLE_MAX_LENGTH} 个字符。`
  }

  if (description.length > DESCRIPTION_MAX_LENGTH) {
    errors.description = `商品描述不能超过 ${DESCRIPTION_MAX_LENGTH} 个字符。`
  }

  if (options?.requireCategory && !category) {
    errors.category = '商品分类不能为空。'
  } else if (category.length > CATEGORY_MAX_LENGTH) {
    errors.category = `商品分类不能超过 ${CATEGORY_MAX_LENGTH} 个字符。`
  }

  if (!priceText) {
    errors.price = '商品价格不能为空。'
  } else if (parsePositivePrice(priceText) === null) {
    errors.price = '商品价格必须为大于 0 的数字。'
  }

  if (imageUrls.some((url) => url.length > IMAGE_URL_MAX_LENGTH)) {
    errors.imageUrlsText = `单个图片 URL 不能超过 ${IMAGE_URL_MAX_LENGTH} 个字符。`
  }

  return errors
}

export function normalizeCreateUserProductInput(model: UserProductFormModel): CreateUserProductInput {
  return {
    title: normalizeText(model.title),
    description: normalizeText(model.description),
    category: normalizeText(model.category),
    price: parsePositivePrice(normalizePriceText(model.price)) ?? 0,
    imageUrls: normalizeImageUrlsFromText(model.imageUrlsText),
  }
}

export function normalizeUpdateUserProductInput(model: UserProductFormModel): UpdateUserProductInput {
  return {
    title: normalizeText(model.title),
    description: normalizeText(model.description),
    price: parsePositivePrice(normalizePriceText(model.price)) ?? 0,
    imageUrls: normalizeImageUrlsFromText(model.imageUrlsText),
  }
}
