import request from '@/utils/request'

export interface ChangeMyPasswordParams {
  currentPassword: string
  newPassword: string
  confirmPassword: string
  verifyChannel?: 'phone' | 'email' | string
  verifyCode?: string | null
}

interface ChangePasswordPayload {
  oldPassword?: string
  currentPassword?: string
  newPassword: string
  verifyChannel?: string
  code?: string
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

function normalizeVerifyChannel(value: unknown) {
  const normalized = readFirstText(value).toLowerCase()
  if (normalized === 'phone' || normalized === 'email') {
    return normalized
  }

  return ''
}

function normalizeChangePasswordPayload(params: ChangeMyPasswordParams): ChangePasswordPayload {
  const currentPassword = readFirstText(params.currentPassword)
  const newPassword = readFirstText(params.newPassword)
  const confirmPassword = readFirstText(params.confirmPassword)
  const verifyChannel = normalizeVerifyChannel(params.verifyChannel)
  const verifyCode = readFirstText(params.verifyCode)

  if (!newPassword) {
    throw new Error('New password is required.')
  }

  if (!confirmPassword) {
    throw new Error('Password confirmation is required.')
  }

  if (newPassword !== confirmPassword) {
    throw new Error('The new password confirmation does not match.')
  }

  if (!currentPassword && !(verifyChannel && verifyCode)) {
    throw new Error('Provide current password or verify channel with code.')
  }

  const payload: ChangePasswordPayload = {
    newPassword,
  }

  /**
   * Day02 password slice keeps backward compatibility during contract alignment:
   * frontend submits both keys, backend resolves oldPassword/currentPassword.
   */
  if (currentPassword) {
    payload.oldPassword = currentPassword
    payload.currentPassword = currentPassword
  }

  if (verifyChannel) {
    payload.verifyChannel = verifyChannel
  }

  if (verifyCode) {
    payload.code = verifyCode
  }

  return payload
}

export async function changeMyPassword(params: ChangeMyPasswordParams) {
  const payload = normalizeChangePasswordPayload(params)
  const response = await request.post<any, unknown>('/user/me/password', payload)

  if (typeof response === 'string' && response.trim()) {
    return response
  }

  return 'Password updated.'
}
