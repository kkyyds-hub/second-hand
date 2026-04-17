import request, { normalizeUserProfile, type UserProfile } from '@/utils/request'

export interface UpdateMyProfileParams {
  nickname: string
  bio?: string | null
  avatar?: string | null
}

export interface BindMyEmailParams {
  email: string
  verifyCode: string
}

export interface BindMyPhoneParams {
  mobile: string
  verifyCode: string
}

export interface UnbindMyEmailParams {
  verifyCode: string
  currentPassword?: string | null
}

export interface UnbindMyPhoneParams {
  verifyCode: string
  currentPassword?: string | null
}

interface NormalizedProfilePatchPayload {
  nickname: string
  bio?: string | null
  avatar?: string | null
}

interface AvatarUploadConfig {
  uploadUrl: string
  resourceUrl: string | null
  expiresIn: number | null
  extraHeaders: Record<string, string>
}

interface BindMyEmailPayload {
  value: string
  verifyCode: string
}

interface BindMyPhonePayload {
  value: string
  verifyCode: string
}

interface UnbindMyEmailPayload {
  verifyChannel: 'email'
  verifyCode: string
  currentPassword?: string
}

interface UnbindMyPhonePayload {
  verifyChannel: 'phone'
  verifyCode: string
  currentPassword?: string
}

type UnknownRecord = Record<string, unknown>

function isRecord(value: unknown): value is UnknownRecord {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function hasOwnKey(source: object, key: string) {
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

function readFirstNumber(...values: unknown[]) {
  for (const value of values) {
    if (typeof value === 'number' && Number.isFinite(value)) {
      return value
    }

    if (typeof value !== 'string') {
      continue
    }

    const normalized = value.trim()
    if (!normalized) {
      continue
    }

    const parsed = Number(normalized)
    if (Number.isFinite(parsed)) {
      return parsed
    }
  }

  return null
}

function normalizeProfilePatchPayload(data: UpdateMyProfileParams): NormalizedProfilePatchPayload {
  const payload: NormalizedProfilePatchPayload = {
    nickname: readFirstText(data.nickname),
  }

  if (hasOwnKey(data, 'bio')) {
    payload.bio = readFirstNullableText(data.bio)
  }

  if (hasOwnKey(data, 'avatar')) {
    payload.avatar = readFirstNullableText(data.avatar)
  }

  return payload
}

function normalizeBindMyEmailPayload(data: BindMyEmailParams): BindMyEmailPayload {
  const value = readFirstText(data.email)
  const verifyCode = readFirstText(data.verifyCode)

  if (!value) {
    throw new Error('Email is required.')
  }

  if (!verifyCode) {
    throw new Error('Verification code is required.')
  }

  return {
    value,
    verifyCode,
  }
}

function normalizeBindMyPhonePayload(data: BindMyPhoneParams): BindMyPhonePayload {
  const value = readFirstText(data.mobile)
  const verifyCode = readFirstText(data.verifyCode)

  if (!value) {
    throw new Error('Mobile number is required.')
  }

  if (!/^1\d{10}$/.test(value)) {
    throw new Error('Mobile number must be an 11-digit mainland China phone number.')
  }

  if (!verifyCode) {
    throw new Error('Verification code is required.')
  }

  return {
    value,
    verifyCode,
  }
}

function normalizeUnbindMyEmailPayload(data: UnbindMyEmailParams): UnbindMyEmailPayload {
  const verifyCode = readFirstText(data.verifyCode)
  if (!verifyCode) {
    throw new Error('Verification code is required.')
  }

  const payload: UnbindMyEmailPayload = {
    verifyChannel: 'email',
    verifyCode,
  }

  const currentPassword = readFirstText(data.currentPassword)
  if (currentPassword) {
    payload.currentPassword = currentPassword
  }

  return payload
}

function normalizeUnbindMyPhonePayload(data: UnbindMyPhoneParams): UnbindMyPhonePayload {
  const verifyCode = readFirstText(data.verifyCode)
  if (!verifyCode) {
    throw new Error('Verification code is required.')
  }

  const payload: UnbindMyPhonePayload = {
    verifyChannel: 'phone',
    verifyCode,
  }

  const currentPassword = readFirstText(data.currentPassword)
  if (currentPassword) {
    payload.currentPassword = currentPassword
  }

  return payload
}

function normalizeExtraHeaders(payload: unknown) {
  if (!isRecord(payload)) {
    return {}
  }

  const normalizedHeaders: Record<string, string> = {}
  for (const [key, value] of Object.entries(payload)) {
    const normalizedKey = key.trim()
    const normalizedValue = readFirstText(value)
    if (!normalizedKey || !normalizedValue) {
      continue
    }

    normalizedHeaders[normalizedKey] = normalizedValue
  }

  return normalizedHeaders
}

function normalizeAvatarUploadConfig(payload: unknown): AvatarUploadConfig {
  const source = isRecord(payload) ? payload : {}

  return {
    uploadUrl: readFirstText(source.uploadUrl, source.url),
    resourceUrl: readFirstNullableText(source.resourceUrl, source.avatarUrl),
    expiresIn: readFirstNumber(source.expiresIn, source.expires),
    extraHeaders: normalizeExtraHeaders(source.extraHeaders),
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
  const hasSubmittedBio = hasOwnKey(submitted, 'bio')
  const hasSubmittedAvatar = hasOwnKey(submitted, 'avatar')
  const submittedBio = readFirstNullableText(submitted.bio)
  const submittedAvatar = readFirstNullableText(submitted.avatar)

  const hasServerNickname = hasOwnKey(source, 'nickname') || hasOwnKey(source, 'nickName')
  const hasServerBio = hasOwnKey(source, 'bio') || hasOwnKey(source, 'profile')
  const hasServerAvatar = hasOwnKey(source, 'avatar') || hasOwnKey(source, 'avatarUrl')

  return {
    id: serverProfile.id ?? currentProfile.id,
    loginName: readFirstText(serverProfile.loginName, currentProfile.loginName),
    mobile: readFirstNullableText(serverProfile.mobile, currentProfile.mobile),
    avatar: hasServerAvatar
      ? readFirstNullableText(source.avatar, source.avatarUrl, serverProfile.avatar)
      : hasSubmittedAvatar
        ? submittedAvatar
        : readFirstNullableText(currentProfile.avatar),
    nickname: hasServerNickname
      ? readFirstNullableText(source.nickname, source.nickName, submittedNickname)
      : readFirstNullableText(submittedNickname, currentProfile.nickname),
    bio: hasServerBio
      ? readFirstNullableText(source.bio, source.profile, submittedBio)
      : hasSubmittedBio
        ? submittedBio
        : readFirstNullableText(currentProfile.bio),
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

function mergeServerUserSnapshot(payload: unknown, currentSnapshot: UserProfile | null | undefined): UserProfile {
  const serverProfile = normalizeUserProfile(payload)
  const currentProfile = normalizeUserProfile(currentSnapshot)

  return {
    id: serverProfile.id ?? currentProfile.id,
    loginName: readFirstText(serverProfile.loginName, currentProfile.loginName),
    mobile: readFirstNullableText(serverProfile.mobile, currentProfile.mobile),
    avatar: readFirstNullableText(serverProfile.avatar, currentProfile.avatar),
    nickname: readFirstNullableText(serverProfile.nickname, currentProfile.nickname),
    bio: readFirstNullableText(serverProfile.bio, currentProfile.bio),
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

export async function bindMyEmail(data: BindMyEmailParams, currentSnapshot: UserProfile | null | undefined) {
  const normalizedPayload = normalizeBindMyEmailPayload(data)
  const payload = await request.post<any, unknown>('/user/me/bindings/email', normalizedPayload)
  const nextSnapshot = mergeServerUserSnapshot(payload, currentSnapshot)

  // The bind endpoint should return UserVO, but we still fall back to submitted email for contract drift.
  return {
    ...nextSnapshot,
    email: readFirstNullableText(nextSnapshot.email, normalizedPayload.value),
  }
}

export async function bindMyPhone(data: BindMyPhoneParams, currentSnapshot: UserProfile | null | undefined) {
  const normalizedPayload = normalizeBindMyPhonePayload(data)
  const payload = await request.post<any, unknown>('/user/me/bindings/phone', normalizedPayload)
  const nextSnapshot = mergeServerUserSnapshot(payload, currentSnapshot)

  // The bind endpoint should return UserVO, but we still fall back to submitted mobile for contract drift.
  return {
    ...nextSnapshot,
    mobile: readFirstNullableText(nextSnapshot.mobile, normalizedPayload.value),
  }
}

export async function unbindMyEmail(data: UnbindMyEmailParams, currentSnapshot: UserProfile | null | undefined) {
  const normalizedPayload = normalizeUnbindMyEmailPayload(data)

  /**
   * UserMeController currently returns a success message string for unbind.
   * We still keep this write-back in API layer so page code never needs to patch session fields directly.
   */
  await request.delete<any, unknown>('/user/me/bindings/email', {
    data: normalizedPayload,
  })

  return {
    ...normalizeUserProfile(currentSnapshot),
    email: null,
  }
}

export async function unbindMyPhone(data: UnbindMyPhoneParams, currentSnapshot: UserProfile | null | undefined) {
  const normalizedPayload = normalizeUnbindMyPhonePayload(data)

  /**
   * UserMeController currently returns a success message string for unbind.
   * We still keep this write-back in API layer so page code never needs to patch session fields directly.
   */
  await request.delete<any, unknown>('/user/me/bindings/phone', {
    data: normalizedPayload,
  })

  return {
    ...normalizeUserProfile(currentSnapshot),
    mobile: null,
  }
}

function resolveProfileNickname(currentSnapshot: UserProfile | null | undefined) {
  const fallbackNickname = readFirstText(currentSnapshot?.nickname, currentSnapshot?.loginName)
  if (!fallbackNickname) {
    throw new Error('Current session is missing nickname or login name, so avatar write-back cannot continue.')
  }

  return fallbackNickname
}

function buildAvatarUploadHeaders(uploadConfig: AvatarUploadConfig, file: File) {
  const headers = {
    ...uploadConfig.extraHeaders,
  }

  const normalizedType = readFirstText(headers['content-type'], headers['Content-Type'], file.type)
  if (normalizedType) {
    headers['content-type'] = normalizedType
  }

  delete headers['Content-Type']
  return headers
}

export async function uploadMyAvatar(file: File, currentSnapshot: UserProfile | null | undefined) {
  const uploadConfigPayload = await request.post<any, unknown>('/user/me/upload-config', {
    fileName: file.name,
    contentType: file.type,
  })
  const uploadConfig = normalizeAvatarUploadConfig(uploadConfigPayload)

  if (!uploadConfig.uploadUrl) {
    throw new Error('Avatar upload config is missing an upload URL.')
  }

  const avatarUrl = await request.put<any, unknown>(uploadConfig.uploadUrl, file, {
    headers: buildAvatarUploadHeaders(uploadConfig, file),
  })
  const normalizedAvatarUrl = readFirstText(avatarUrl, uploadConfig.resourceUrl)

  if (!normalizedAvatarUrl) {
    throw new Error('Avatar upload succeeded, but no usable avatar URL was returned.')
  }

  // Step two only returns the uploaded resource URL. Persisting it stays in the API layer.
  const profilePatch: UpdateMyProfileParams = {
    nickname: resolveProfileNickname(currentSnapshot),
    avatar: normalizedAvatarUrl,
  }

  if (typeof currentSnapshot?.bio !== 'undefined') {
    profilePatch.bio = currentSnapshot.bio
  }

  return updateMyProfile(profilePatch, currentSnapshot)
}
