import type { CreateUserProductInput, UpdateUserProductInput, UserProductDetail } from '@/api/userProducts'

export interface UserProductFormModel {
  title: string
  description: string
  category: string
  price: string
  imageUrls: string[]
}

export type UserProductFormField = 'title' | 'description' | 'category' | 'price' | 'imageUrls'

const TITLE_MAX_LENGTH = 120
const DESCRIPTION_MAX_LENGTH = 2000
const CATEGORY_MAX_LENGTH = 60
const IMAGE_URL_MAX_LENGTH = 500

function normalizeText(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function normalizeImageUrls(values: unknown) {
  if (!Array.isArray(values)) return []
  return values.map((value) => normalizeText(value)).filter(Boolean)
}

function parsePositivePrice(value: unknown) {
  const raw = typeof value === 'string' ? value : ''
  const normalized = normalizeText(raw)
  if (!normalized || raw !== normalized) return null

  const parsed = Number(normalized)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

export function createEmptyUserProductFormModel(): UserProductFormModel {
  return { title: '', description: '', category: '', price: '', imageUrls: [] }
}

export function toUserProductFormModel(source: Partial<UserProductDetail> | null | undefined): UserProductFormModel {
  return {
    title: normalizeText(source?.title),
    description: normalizeText(source?.description),
    category: normalizeText(source?.category),
    price: typeof source?.price === 'number' && Number.isFinite(source.price) && source.price > 0 ? String(source.price) : '',
    imageUrls: normalizeImageUrls(source?.imageUrls),
  }
}

export function collectUserProductValidationErrors(
  model: UserProductFormModel,
  options?: { includeCategory?: boolean },
): Partial<Record<UserProductFormField, string>> {
  const errors: Partial<Record<UserProductFormField, string>> = {}
  const title = normalizeText(model.title)
  const description = normalizeText(model.description)
  const category = normalizeText(model.category)
  const priceText = normalizeText(model.price)

  if (!title) errors.title = '请填写商品标题。'
  else if (title.length > TITLE_MAX_LENGTH) errors.title = `商品标题不能超过 ${TITLE_MAX_LENGTH} 个字符。`

  if (description.length > DESCRIPTION_MAX_LENGTH) errors.description = `商品描述不能超过 ${DESCRIPTION_MAX_LENGTH} 个字符。`
  if (options?.includeCategory !== false && category.length > CATEGORY_MAX_LENGTH) {
    errors.category = `商品分类不能超过 ${CATEGORY_MAX_LENGTH} 个字符。`
  }

  if (!priceText) errors.price = '请填写商品价格。'
  else if (parsePositivePrice(priceText) === null) errors.price = '请输入大于 0 的有效价格。'

  if (model.imageUrls.some((url) => normalizeText(url).length > IMAGE_URL_MAX_LENGTH)) {
    errors.imageUrls = `单个图片链接不能超过 ${IMAGE_URL_MAX_LENGTH} 个字符。`
  }

  return errors
}

export function normalizeCreateUserProductInput(model: UserProductFormModel): CreateUserProductInput {
  return {
    title: normalizeText(model.title),
    description: normalizeText(model.description),
    category: normalizeText(model.category),
    price: parsePositivePrice(model.price) ?? 0,
    imageUrls: normalizeImageUrls(model.imageUrls),
  }
}

export function normalizeUpdateUserProductInput(model: UserProductFormModel): UpdateUserProductInput {
  return {
    title: normalizeText(model.title),
    description: normalizeText(model.description),
    price: parsePositivePrice(model.price) ?? 0,
    imageUrls: normalizeImageUrls(model.imageUrls),
  }
}

export function userProductUpdateFingerprint(model: UserProductFormModel) {
  const input = normalizeUpdateUserProductInput(model)
  return JSON.stringify([input.title, input.price, input.description, input.imageUrls])
}
