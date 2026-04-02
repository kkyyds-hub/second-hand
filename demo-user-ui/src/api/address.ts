import request from '@/utils/request'

export interface AddressListQuery {
  page?: number
  pageSize?: number
}

export interface UserAddressItem {
  id: number | null
  receiverName: string
  mobile: string
  provinceCode: string
  provinceName: string
  cityCode: string
  cityName: string
  districtCode: string
  districtName: string
  detailAddress: string
  isDefault: boolean
  fullAddress: string
}

export interface AddressListResult {
  list: UserAddressItem[]
  total: number
  page: number
  pageSize: number
}

export interface CreateAddressInput {
  receiverName: string
  mobile: string
  provinceCode: string
  provinceName: string
  cityCode: string
  cityName: string
  districtCode: string
  districtName: string
  detailAddress: string
  isDefault?: boolean | number | string | null
}

export type UpdateAddressInput = CreateAddressInput

interface CreateAddressPayload {
  receiverName: string
  mobile: string
  provinceCode: string
  provinceName: string
  cityCode: string
  cityName: string
  districtCode: string
  districtName: string
  detailAddress: string
  isDefault: boolean
}

type UnknownRecord = Record<string, unknown>

const DEFAULT_PAGE = 1
const DEFAULT_PAGE_SIZE = 20

function isRecord(value: unknown): value is UnknownRecord {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
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
    if (normalized === null) {
      continue
    }

    if (normalized > 0) {
      return Math.trunc(normalized)
    }
  }

  return fallback
}

function readNonNegativeInt(fallback: number, ...values: unknown[]) {
  for (const value of values) {
    const normalized = normalizeNumber(value)
    if (normalized === null) {
      continue
    }

    if (normalized >= 0) {
      return Math.trunc(normalized)
    }
  }

  return fallback
}

function readBoolean(value: unknown) {
  if (typeof value === 'boolean') {
    return value
  }

  const normalizedNumber = normalizeNumber(value)
  if (normalizedNumber !== null) {
    return normalizedNumber === 1
  }

  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase()
    return ['true', 'yes', 'y'].includes(normalized)
  }

  return false
}

function readFirstArray(...values: unknown[]) {
  for (const value of values) {
    if (Array.isArray(value)) {
      return value
    }
  }

  return []
}

function normalizeAddressItem(payload: unknown): UserAddressItem {
  const source = isRecord(payload) ? payload : {}
  const normalizedId = readNonNegativeInt(-1, source.id, source.addressId)

  const provinceName = readFirstText(source.provinceName, source.province)
  const cityName = readFirstText(source.cityName, source.city)
  const districtName = readFirstText(source.districtName, source.district, source.area)
  const detailAddress = readFirstText(source.detailAddress, source.address, source.addressDetail)
  const fullAddress = [provinceName, cityName, districtName, detailAddress].filter(Boolean).join(' ')

  return {
    id: normalizedId >= 0 ? normalizedId : null,
    receiverName: readFirstText(source.receiverName, source.consignee, source.name),
    mobile: readFirstText(source.mobile, source.phone, source.tel),
    provinceCode: readFirstText(source.provinceCode),
    provinceName,
    cityCode: readFirstText(source.cityCode),
    cityName,
    districtCode: readFirstText(source.districtCode),
    districtName,
    detailAddress,
    isDefault: readBoolean(source.isDefault ?? source.defaultFlag ?? source.defaultAddress),
    fullAddress,
  }
}

function normalizeQuery(query: AddressListQuery | undefined) {
  return {
    page: readPositiveInt(DEFAULT_PAGE, query?.page),
    pageSize: readPositiveInt(DEFAULT_PAGE_SIZE, query?.pageSize),
  }
}

function normalizeCreateAddressPayload(input: CreateAddressInput): CreateAddressPayload {
  /**
   * AddressController 的 create 契约使用 AddressDTO 字段；
   * 这里统一做 trim + boolean 归一化，避免页面层散落字段整理逻辑。
   */
  return {
    receiverName: readFirstText(input.receiverName),
    mobile: readFirstText(input.mobile),
    provinceCode: readFirstText(input.provinceCode),
    provinceName: readFirstText(input.provinceName),
    cityCode: readFirstText(input.cityCode),
    cityName: readFirstText(input.cityName),
    districtCode: readFirstText(input.districtCode),
    districtName: readFirstText(input.districtName),
    detailAddress: readFirstText(input.detailAddress),
    isDefault: readBoolean(input.isDefault),
  }
}

function normalizeAddressId(addressId: number | string) {
  const normalizedId = readPositiveInt(-1, addressId)

  if (normalizedId <= 0) {
    throw new Error('地址 ID 无效，无法继续地址操作。')
  }

  return normalizedId
}

function buildAddressDetailPath(addressId: number | string) {
  return `/user/addresses/${normalizeAddressId(addressId)}`
}

function normalizeAddressListResult(payload: unknown, query: Required<AddressListQuery>): AddressListResult {
  const source = isRecord(payload) ? payload : {}
  const nestedSource = isRecord(source.data) ? source.data : {}

  /**
   * 接口主形状按后端 `PageResult` 读取（`list/total/page/pageSize`），
   * 同时兜底少量历史分页别名，确保页面层始终消费统一结构。
   */
  const rawList = readFirstArray(
    source.list,
    source.records,
    source.rows,
    source.items,
    nestedSource.list,
    nestedSource.records,
    nestedSource.rows,
    Array.isArray(payload) ? payload : undefined,
  )

  const list = rawList.map((item) => normalizeAddressItem(item))
  const total = readNonNegativeInt(
    list.length,
    source.total,
    source.totalCount,
    source.count,
    nestedSource.total,
    nestedSource.totalCount,
    nestedSource.count,
  )

  return {
    list,
    total,
    page: readPositiveInt(query.page, source.page, source.current, source.currentPage, source.pageNum, nestedSource.page),
    pageSize: readPositiveInt(query.pageSize, source.pageSize, source.size, source.limit, nestedSource.pageSize, nestedSource.size),
  }
}

export function createEmptyAddressListResult(): AddressListResult {
  return {
    list: [],
    total: 0,
    page: DEFAULT_PAGE,
    pageSize: DEFAULT_PAGE_SIZE,
  }
}

export async function getMyAddressList(query?: AddressListQuery) {
  const normalizedQuery = normalizeQuery(query)
  const payload = await request.get<any, unknown>('/user/addresses', {
    params: normalizedQuery,
  })

  return normalizeAddressListResult(payload, normalizedQuery)
}

export async function createMyAddress(input: CreateAddressInput) {
  const normalizedPayload = normalizeCreateAddressPayload(input)
  const payload = await request.post<any, unknown>('/user/addresses', normalizedPayload)

  return normalizeAddressItem(payload)
}

export async function getMyAddressDetail(addressId: number | string) {
  const payload = await request.get<any, unknown>(buildAddressDetailPath(addressId))
  return normalizeAddressItem(payload)
}

export async function updateMyAddress(addressId: number | string, input: UpdateAddressInput) {
  const normalizedPayload = normalizeCreateAddressPayload(input)
  const payload = await request.put<any, unknown>(buildAddressDetailPath(addressId), normalizedPayload)

  return normalizeAddressItem(payload)
}
