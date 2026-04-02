import type { CreateAddressInput } from '@/api/address'

export interface AddressFormModel extends Omit<CreateAddressInput, 'isDefault'> {
  isDefault: boolean
}

export type AddressFormField =
  | 'receiverName'
  | 'mobile'
  | 'provinceCode'
  | 'provinceName'
  | 'cityCode'
  | 'cityName'
  | 'districtCode'
  | 'districtName'
  | 'detailAddress'

const MOBILE_PATTERN = /^1[3-9]\d{9}$/

function normalizeText(value: unknown) {
  if (typeof value !== 'string') {
    return ''
  }

  return value.trim()
}

export function createEmptyAddressFormModel(): AddressFormModel {
  return {
    receiverName: '',
    mobile: '',
    provinceCode: '',
    provinceName: '',
    cityCode: '',
    cityName: '',
    districtCode: '',
    districtName: '',
    detailAddress: '',
    isDefault: false,
  }
}

export function toAddressFormModel(source: Partial<CreateAddressInput> | null | undefined): AddressFormModel {
  return {
    receiverName: normalizeText(source?.receiverName),
    mobile: normalizeText(source?.mobile),
    provinceCode: normalizeText(source?.provinceCode),
    provinceName: normalizeText(source?.provinceName),
    cityCode: normalizeText(source?.cityCode),
    cityName: normalizeText(source?.cityName),
    districtCode: normalizeText(source?.districtCode),
    districtName: normalizeText(source?.districtName),
    detailAddress: normalizeText(source?.detailAddress),
    isDefault: Boolean(source?.isDefault),
  }
}

export function normalizeAddressFormModel(model: AddressFormModel): CreateAddressInput {
  return {
    receiverName: normalizeText(model.receiverName),
    mobile: normalizeText(model.mobile),
    provinceCode: normalizeText(model.provinceCode),
    provinceName: normalizeText(model.provinceName),
    cityCode: normalizeText(model.cityCode),
    cityName: normalizeText(model.cityName),
    districtCode: normalizeText(model.districtCode),
    districtName: normalizeText(model.districtName),
    detailAddress: normalizeText(model.detailAddress),
    isDefault: Boolean(model.isDefault),
  }
}

/**
 * create/edit 复用同一套字段校验规则，避免两个页面在 Day02 中出现校验漂移。
 */
export function collectAddressValidationErrors(payload: CreateAddressInput) {
  const errors: Partial<Record<AddressFormField, string>> = {}

  if (!payload.receiverName) {
    errors.receiverName = '收件人姓名不能为空。'
  }

  if (!payload.mobile) {
    errors.mobile = '手机号不能为空。'
  } else if (!MOBILE_PATTERN.test(payload.mobile)) {
    errors.mobile = '手机号格式不正确。'
  }

  if (!payload.provinceCode) {
    errors.provinceCode = '省份编码不能为空。'
  }

  if (!payload.provinceName) {
    errors.provinceName = '省份名称不能为空。'
  }

  if (!payload.cityCode) {
    errors.cityCode = '城市编码不能为空。'
  }

  if (!payload.cityName) {
    errors.cityName = '城市名称不能为空。'
  }

  if (!payload.districtCode) {
    errors.districtCode = '区县编码不能为空。'
  }

  if (!payload.districtName) {
    errors.districtName = '区县名称不能为空。'
  }

  if (!payload.detailAddress) {
    errors.detailAddress = '详细地址不能为空。'
  }

  return errors
}
