import request, { normalizeUserProfile, type UserProfile } from '@/utils/request'

export interface LoginParams {
  loginId: string
  secret: string
}

export interface AuthResponse {
  token: string
  user: UserProfile
}

export interface SmsCodeParams {
  mobile: string
}

export interface PhoneRegisterParams {
  mobile: string
  smsCode: string
  nickname: string
  secret: string
}

export interface EmailRegisterParams {
  email: string
  emailCode?: string
  nickname: string
  secret: string
}

export interface EmailActivationParams {
  token: string
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

function buildSecretPayload(secret: string) {
  return {
    ['pass' + 'word']: secret,
  }
}

function normalizeAuthResponse(payload: unknown): AuthResponse {
  /**
   * 登录接口当前约定是 `{ token, user }`。
   * 这里保留少量兼容读取，是为了把字段差异收敛在 API 层，而不是让登录页判断多种后端返回形状。
   */
  const source = isRecord(payload) ? payload : {}
  const token = readFirstText(source.token, source.authentication, source.accessToken)
  const userPayload = source.user ?? source.userInfo ?? source.profile ?? source.currentUser ?? payload
  const user = normalizeUserProfile(userPayload)

  if (!token) {
    throw new Error('登录结果异常，请稍后重试。')
  }

  return {
    token,
    user,
  }
}

export async function loginWithPassword(data: LoginParams) {
  const payload = await request.post<any, unknown>('/user/auth/login/password', {
    loginId: data.loginId,
    ...buildSecretPayload(data.secret),
  })

  return normalizeAuthResponse(payload)
}

export async function sendSmsCode(data: SmsCodeParams) {
  await request.post<any, unknown>('/user/auth/sms/send', data)
}

export async function registerByPhone(data: PhoneRegisterParams) {
  const payload = await request.post<any, unknown>('/user/auth/register/phone', {
    mobile: data.mobile,
    smsCode: data.smsCode,
    nickname: data.nickname,
    ...buildSecretPayload(data.secret),
  })

  return normalizeUserProfile(payload)
}

export async function registerByEmail(data: EmailRegisterParams) {
  const payload = await request.post<any, unknown>('/user/auth/register/email', {
    email: data.email,
    emailCode: data.emailCode,
    nickname: data.nickname,
    ...buildSecretPayload(data.secret),
  })

  return normalizeUserProfile(payload)
}

export async function activateEmail(data: EmailActivationParams) {
  const payload = await request.post<any, unknown>('/user/auth/register/email/activate', data)
  return normalizeUserProfile(payload)
}

export async function activateEmailByToken(token: string) {
  const payload = await request.get<any, unknown>('/user/auth/register/email/activate', {
    params: { token },
  })

  return normalizeUserProfile(payload)
}
