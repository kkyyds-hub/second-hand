import axios from 'axios'
import type { AxiosError, AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'

export interface ApiResult<T> {
  code: number | string
  msg: string
  data: T
}

export interface UserProfile {
  id: number | null
  loginName: string
  mobile?: string | null
  avatar?: string | null
  nickname?: string | null
  bio?: string | null
  email?: string | null
  registerTime?: string | null
  lastLoginIp?: string | null
  creditScore?: number | null
  productCount?: number | null
  status?: string | null
  region?: string | null
  isSeller?: number | null
}

type UnknownRecord = Record<string, unknown>

const USER_TOKEN_KEY = 'user_token'
const USER_PROFILE_KEY = 'user_profile'
const LEGACY_USER_TOKEN_KEYS = ['authentication', 'userToken']
const PUBLIC_PATH_PREFIXES = ['/login', '/register/phone', '/register/email', '/activate/email']
const DEFAULT_REDIRECT_PATH = '/'
const DEFAULT_ERROR_MESSAGE = '请求失败，请稍后重试'
const SESSION_EXPIRED_MESSAGE = '登录状态已失效，请重新登录'

function isBrowser() {
  return typeof window !== 'undefined'
}

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

function readFirstNullableText(...values: unknown[]) {
  const text = readFirstText(...values)
  return text || null
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

function readFirstNumber(...values: unknown[]) {
  for (const value of values) {
    const normalized = normalizeNumber(value)
    if (normalized !== null) {
      return normalized
    }
  }

  return null
}

function isPublicPath(pathname: string) {
  return PUBLIC_PATH_PREFIXES.some((prefix) => pathname.startsWith(prefix))
}

export function normalizeUserProfile(payload: unknown): UserProfile {
  if (!isRecord(payload)) {
    return {
      id: null,
      loginName: '',
      mobile: null,
      avatar: null,
      nickname: null,
      bio: null,
      email: null,
      registerTime: null,
      lastLoginIp: null,
      creditScore: null,
      productCount: null,
      status: null,
      region: null,
      isSeller: null,
    }
  }

  return {
    id: readFirstNumber(payload.id, payload.userId),
    loginName: readFirstText(payload['user' + 'name'], payload.loginName, payload.loginId, payload.account),
    mobile: readFirstNullableText(payload.mobile, payload.phone),
    avatar: readFirstNullableText(payload.avatar, payload.avatarUrl),
    nickname: readFirstNullableText(payload.nickname, payload.nickName),
    bio: readFirstNullableText(payload.bio, payload.profile),
    email: readFirstNullableText(payload.email),
    registerTime: readFirstNullableText(payload.registerTime, payload.createdAt, payload.createTime),
    lastLoginIp: readFirstNullableText(payload.lastLoginIp, payload.last_login_ip),
    creditScore: readFirstNumber(payload.creditScore, payload.score),
    productCount: readFirstNumber(payload.productCount, payload.productsCount),
    status: readFirstNullableText(payload.status, payload.userStatus),
    region: readFirstNullableText(payload.region, payload.area),
    isSeller: readFirstNumber(payload.isSeller, payload.sellerFlag),
  }
}

export function hasUserProfileSnapshot(user: UserProfile | null | undefined) {
  return Boolean(user && (user.id !== null || user.loginName || user.nickname || user.mobile || user.email))
}

export function getUserDisplayName(user: UserProfile | null | undefined) {
  return readFirstText(user?.nickname, user?.loginName) || '用户'
}

export function getUserPrimaryContact(user: UserProfile | null | undefined) {
  return readFirstText(user?.mobile, user?.email) || '未绑定手机或邮箱'
}

export function isSellerUser(user: UserProfile | null | undefined) {
  return Number(user?.isSeller ?? 0) === 1
}

export function normalizeUserRedirectPath(value: unknown, fallback = DEFAULT_REDIRECT_PATH) {
  const safeFallback = typeof fallback === 'string' && fallback.startsWith('/') ? fallback : DEFAULT_REDIRECT_PATH

  if (typeof value !== 'string') {
    return safeFallback
  }

  const normalized = value.trim()
  if (!normalized.startsWith('/') || normalized.startsWith('//')) {
    return safeFallback
  }

  return normalized
}

export function buildLoginRedirectPath(targetPath: unknown) {
  const redirect = encodeURIComponent(normalizeUserRedirectPath(targetPath))
  return `/login?redirect=${redirect}`
}

export function readUserToken() {
  if (!isBrowser()) {
    return ''
  }

  const direct = readFirstText(window.localStorage.getItem(USER_TOKEN_KEY))
  if (direct) {
    return direct
  }

  /**
   * Day01 继续兼容老 key，避免旧登录态在切到独立用户端主工程后立刻失效。
   * 一旦读到旧 key，就回写主 key，让后续请求和守卫都走统一来源。
   */
  for (const key of LEGACY_USER_TOKEN_KEYS) {
    const legacyValue = readFirstText(window.localStorage.getItem(key))
    if (!legacyValue) {
      continue
    }

    window.localStorage.setItem(USER_TOKEN_KEY, legacyValue)
    return legacyValue
  }

  return ''
}

export function saveUserToken(token: string) {
  if (!isBrowser()) {
    return
  }

  const normalized = token.trim()
  if (!normalized) {
    clearUserToken()
    return
  }

  window.localStorage.setItem(USER_TOKEN_KEY, normalized)
  window.localStorage.setItem('authentication', normalized)
}

export function clearUserToken() {
  if (!isBrowser()) {
    return
  }

  window.localStorage.removeItem(USER_TOKEN_KEY)
  window.localStorage.removeItem('authentication')
  window.localStorage.removeItem('userToken')
}

export function readCurrentUser() {
  if (!isBrowser()) {
    return null
  }

  const raw = window.localStorage.getItem(USER_PROFILE_KEY)
  if (!raw) {
    return null
  }

  try {
    const normalized = normalizeUserProfile(JSON.parse(raw))
    if (!hasUserProfileSnapshot(normalized)) {
      clearCurrentUser()
      return null
    }

    window.localStorage.setItem(USER_PROFILE_KEY, JSON.stringify(normalized))
    return normalized
  } catch {
    clearCurrentUser()
    return null
  }
}

export function saveCurrentUser(user: UserProfile) {
  if (!isBrowser()) {
    return
  }

  const normalized = normalizeUserProfile(user)
  if (!hasUserProfileSnapshot(normalized)) {
    clearCurrentUser()
    return
  }

  /**
   * 账户中心与登录后壳层当前都依赖这份本地 session 快照。
   * 在 Day01 不引入新的 profile 接口前，统一在写入时完成归一化，避免页面各自兜底字段。
   */
  window.localStorage.setItem(USER_PROFILE_KEY, JSON.stringify(normalized))
}

export function clearCurrentUser() {
  if (!isBrowser()) {
    return
  }

  window.localStorage.removeItem(USER_PROFILE_KEY)
}

export function saveUserSession(token: string, user: UserProfile) {
  saveUserToken(token)
  saveCurrentUser(user)
}

export function clearUserSession() {
  clearUserToken()
  clearCurrentUser()
}

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = readUserToken()

    /**
     * 用户端当前协议固定使用 `authentication` 请求头。
     * token 注入必须留在 request 层，避免页面和 API 模块重复拼鉴权头。
     */
    if (token && config.headers) {
      config.headers.authentication = token
    }

    return config
  },
  (error: unknown) => Promise.reject(error),
)

service.interceptors.response.use(
  <T>(response: AxiosResponse<ApiResult<T> | T>) => {
    const res = response.data

    if (isRecord(res) && 'code' in res && 'msg' in res) {
      const apiResult = res as unknown as ApiResult<T>

      if (Number(apiResult.code) !== 1) {
        return Promise.reject(new Error(apiResult.msg || DEFAULT_ERROR_MESSAGE))
      }

      return apiResult.data
    }

    return res
  },
  (error: AxiosError<{ msg?: string; message?: string }>) => {
    const status = error.response?.status
    const message = error.response?.data?.msg || error.response?.data?.message || error.message || DEFAULT_ERROR_MESSAGE

    if (status === 401) {
      clearUserSession()

      /**
       * 401 发生时统一在底层清 session，并仅对受保护页面执行回跳。
       * 这样登录页、注册页、激活页不会因为辅助接口失败而被二次重定向。
       */
      if (isBrowser() && !isPublicPath(window.location.pathname)) {
        const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`
        window.location.assign(buildLoginRedirectPath(currentPath))
      }

      return Promise.reject(new Error(SESSION_EXPIRED_MESSAGE))
    }

    return Promise.reject(new Error(message))
  },
)

export default service
