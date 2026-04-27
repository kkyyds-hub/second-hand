import request from '@/utils/request'

export interface CreateAfterSaleInput {
  orderId: number | string
  reason: string
  evidenceImages?: string[]
}

export interface CreateAfterSaleResult {
  afterSaleId: number | null
}

export interface InitiateAfterSaleDisputeInput {
  content: string
}

export interface SellerAfterSaleDecisionInput {
  approved: boolean
  remark?: string
}

type UnknownRecord = Record<string, unknown>

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
    if (normalized !== null && normalized > 0) {
      return Math.trunc(normalized)
    }
  }

  return fallback
}

function readPositiveId(...values: unknown[]) {
  const id = readPositiveInt(-1, ...values)
  return id > 0 ? id : null
}

function normalizeAfterSaleId(afterSaleId: number | string, errorMessage: string) {
  const normalized = readPositiveInt(-1, afterSaleId)
  if (normalized <= 0) {
    throw new Error(errorMessage)
  }

  return normalized
}

function normalizeActionMessage(payload: unknown, fallback: string) {
  if (typeof payload === 'string') {
    const normalized = payload.trim()
    if (normalized) {
      return normalized
    }
  }

  const source = isRecord(payload) ? payload : {}
  const message = readFirstText(source.message, source.msg, source.result)
  return message || fallback
}

function normalizeEvidenceImages(images?: string[]) {
  const normalized = Array.isArray(images) ? images.map((item) => readFirstText(item)).filter(Boolean) : []

  if (normalized.length > 3) {
    throw new Error('凭证图片最多 3 张。')
  }

  return normalized
}

function normalizeCreateAfterSaleInput(input: CreateAfterSaleInput) {
  const orderId = normalizeAfterSaleId(input.orderId, '订单 ID 无效，无法发起售后申请。')
  const reason = readFirstText(input.reason)
  const evidenceImages = normalizeEvidenceImages(input.evidenceImages)

  if (reason.length < 2 || reason.length > 200) {
    throw new Error('售后原因长度需在 2~200 个字符之间。')
  }

  /**
   * 字段映射统一下沉 API 层，页面只关心“用户输入是否完整”。
   * 这样能避免多个页面重复手写 `orderId/reason/evidenceImages` 的契约拼装逻辑。
   */
  return {
    orderId,
    reason,
    evidenceImages,
  }
}

function normalizeCreateAfterSaleResult(payload: unknown): CreateAfterSaleResult {
  if (typeof payload === 'number' && Number.isFinite(payload)) {
    return { afterSaleId: payload > 0 ? Math.trunc(payload) : null }
  }

  const source = isRecord(payload) ? payload : {}
  return {
    afterSaleId: readPositiveId(payload, source.afterSaleId, source.id),
  }
}

function normalizeInitiateDisputeInput(input: InitiateAfterSaleDisputeInput) {
  const content = readFirstText(input.content)

  if (content.length < 2 || content.length > 500) {
    throw new Error('纠纷说明长度需在 2~500 个字符之间。')
  }

  return { content }
}

function normalizeSellerDecisionInput(input: SellerAfterSaleDecisionInput) {
  if (typeof input.approved !== 'boolean') {
    throw new Error('卖家处理结果无效，请重新选择同意或拒绝。')
  }

  const remark = readFirstText(input.remark)
  if (remark.length > 200) {
    throw new Error('卖家处理备注长度不能超过 200 个字符。')
  }

  return {
    approved: input.approved,
    remark,
  }
}

export async function createBuyerAfterSale(input: CreateAfterSaleInput) {
  const payload = await request.post<any, unknown>('/user/after-sales', normalizeCreateAfterSaleInput(input))
  return normalizeCreateAfterSaleResult(payload)
}

export async function submitSellerAfterSaleDecision(afterSaleId: number | string, input: SellerAfterSaleDecisionInput) {
  const payload = await request.put<any, unknown>(
    `/user/after-sales/${normalizeAfterSaleId(afterSaleId, '售后 ID 无效，无法提交卖家处理。')}/seller-decision`,
    normalizeSellerDecisionInput(input),
  )

  return normalizeActionMessage(payload, input.approved ? '已同意退货退款。' : '已拒绝退货退款。')
}

export async function initiateBuyerAfterSaleDispute(afterSaleId: number | string, input: InitiateAfterSaleDisputeInput) {
  const payload = await request.post<any, unknown>(
    `/user/after-sales/${normalizeAfterSaleId(afterSaleId, '售后 ID 无效，无法发起纠纷。')}/dispute`,
    normalizeInitiateDisputeInput(input),
  )
  return normalizeActionMessage(payload, '纠纷提交请求已发送。')
}
