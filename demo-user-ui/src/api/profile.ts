import request, { normalizeUserProfile, type UserProfile } from '@/utils/request'

export interface UpdateMyProfileParams {
  nickname: string
  bio: string
}

interface NormalizedProfilePatchPayload {
  nickname: string
  bio: string | null
}

type UnknownRecord = Record<string, unknown>

function isRecord(value: unknown): value is UnknownRecord {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function hasOwnKey(source: UnknownRecord, key: string) {
  return Object.prototype.hasOwnProperty.call(source, key)
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

function normalizeProfilePatchPayload(data: UpdateMyProfileParams): NormalizedProfilePatchPayload {
  return {
    nickname: readFirstText(data.nickname),
    bio: readFirstNullableText(data.bio),
  }
}

function mergeProfileSnapshot(
  payload: unknown,
  submitted: NormalizedProfilePatchPayload,
  currentSnapshot: UserProfile | null | undefined,
): UserProfile {
  const serverProfile = normalizeUserProfile(payload)
  const currentProfile = normalizeUserProfile(currentSnapshot)
  const source = isRecord(payload) ? payload : {}

  const submittedNickname = readFirstText(submitted.nickname)
  const submittedBio = readFirstNullableText(submitted.bio)

  const hasServerNickname = hasOwnKey(source, 'nickname') || hasOwnKey(source, 'nickName')
  const hasServerBio = hasOwnKey(source, 'bio') || hasOwnKey(source, 'profile')

  /**
   * Day02 第一刀允许后端返回“空体/部分字段”。
   * 这里统一在 API 层合并 session 快照，避免页面层分散做字段兜底。
   */
  return {
    id: serverProfile.id ?? currentProfile.id,
    loginName: readFirstText(serverProfile.loginName, currentProfile.loginName),
    mobile: readFirstNullableText(serverProfile.mobile, currentProfile.mobile),
    avatar: readFirstNullableText(serverProfile.avatar, currentProfile.avatar),
    nickname: hasServerNickname
      ? readFirstNullableText(source.nickname, source.nickName, submittedNickname)
      : readFirstNullableText(submittedNickname),
    bio: hasServerBio ? readFirstNullableText(source.bio, source.profile, submittedBio) : submittedBio,
    email: readFirstNullableText(serverProfile.email, currentProfile.email),
    registerTime: readFirstNullableText(serverProfile.registerTime, currentProfile.registerTime),
    lastLoginIp: readFirstNullableText(serverProfile.lastLoginIp, currentProfile.lastLoginIp),
    creditScore: serverProfile.creditScore ?? currentProfile.creditScore,
    productCount: serverProfile.productCount ?? currentProfile.productCount,
    status: readFirstNullableText(serverProfile.status, currentProfile.status),
    region: readFirstNullableText(serverProfile.region, currentProfile.region),
    isSeller: serverProfile.isSeller ?? currentProfile.isSeller,
  }
}

export async function updateMyProfile(data: UpdateMyProfileParams, currentSnapshot: UserProfile | null | undefined) {
  const normalizedPayload = normalizeProfilePatchPayload(data)
  const payload = await request.patch<any, unknown>('/user/me/profile', normalizedPayload)

  return mergeProfileSnapshot(payload, normalizedPayload, currentSnapshot)
}
