function normalizeUserProductStatus(status: unknown) {
  return typeof status === 'string' ? status.trim().toLowerCase() : 'unknown'
}

export function canEditUserProduct(status: unknown) {
  return ['on_sale', 'under_review', 'off_shelf'].includes(normalizeUserProductStatus(status))
}

export function canDeleteUserProduct(status: unknown) {
  return ['under_review', 'off_shelf'].includes(normalizeUserProductStatus(status))
}

export function isValidUserProductId(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}
