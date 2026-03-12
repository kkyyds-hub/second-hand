import request from '../utils/request'

/**
 * 列表行：这是页面真正要消费的前端展示模型。
 */
export interface UserItem {
  id: string
  name: string
  avatar?: string
  role: string
  creditScore: number
  trade30d: number
  complaints: number
  status: string
  registerDate: string
  lastActive: string
  phone: string
}

/**
 * 用户列表请求参数。
 * - `role/status` 允许传中文展示值或后端值，API 层统一做映射。
 */
export interface UserListParams {
  page: number
  pageSize: number
  searchQuery?: string
  role?: string
  status?: string
}

export interface UserListResponse {
  total: number
  items: UserItem[]
  page: number
  pageSize: number
}

/**
 * 后端分页对象（PageResult）在前端的接收结构。
 */
interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

/**
 * 后端 UserVO 的核心字段。
 */
interface UserVo {
  id: number
  username?: string
  mobile?: string
  avatar?: string
  nickname?: string
  email?: string
  registerTime?: string
  lastLoginIp?: string
  creditScore?: number
  productCount?: number
  status?: string
  region?: string
}

/**
 * 角色展示映射：
 * 后端目前没有直接返回“业务角色名”，这里按已有字段做兜底推断。
 */
function normalizeRole(user: UserVo) {
  if (user.username?.toLowerCase().startsWith('admin')) {
    return '平台运营'
  }
  if ((user.productCount ?? 0) > 20) {
    return '企业商家'
  }
  if ((user.productCount ?? 0) > 0) {
    return '个人卖家'
  }
  return '普通买家'
}

/**
 * 状态展示映射：后端状态码 -> 页面中文状态。
 */
function normalizeStatus(status?: string) {
  switch ((status || '').toLowerCase()) {
    case 'banned':
    case 'ban':
      return '封禁'
    case 'frozen':
      return '限流'
    case 'inactive':
      return '预警'
    case 'active':
    default:
      return '正常'
  }
}

/**
 * 查询参数状态映射：页面值 -> 后端值。
 */
function toBackendStatus(status?: string) {
  if (!status || status === 'ALL' || status === '全部') return undefined
  switch (status) {
    case '正常':
      return 'active'
    case '预警':
      return 'inactive'
    case '限流':
      return 'frozen'
    case '封禁':
      return 'banned'
    default:
      return status
  }
}

/**
 * 查询参数角色映射：页面值 -> 后端值。
 */
function toBackendRole(role?: string) {
  if (!role || role === 'ALL' || role === '全部') return undefined
  switch (role) {
    case '普通买家':
      return 'BUYER_NORMAL'
    case '认证买家':
      return 'BUYER_VERIFIED'
    case '个人卖家':
      return 'SELLER_PERSONAL'
    case '企业商家':
      return 'SELLER_ENTERPRISE'
    default:
      return role
  }
}

/**
 * 日期格式化：统一取 YYYY-MM-DD。
 */
function formatDate(value?: string) {
  if (!value) return '--'
  return value.replace('T', ' ').slice(0, 10)
}

/**
 * 单条用户转换：后端 UserVO -> 页面 UserItem。
 */
function normalizeUser(user: UserVo): UserItem {
  return {
    id: String(user.id),
    name: user.nickname || user.username || `用户${user.id}`,
    avatar: user.avatar,
    role: normalizeRole(user),
    creditScore: user.creditScore ?? 0,
    trade30d: user.productCount ?? 0,
    complaints: 0,
    status: normalizeStatus(user.status),
    registerDate: formatDate(user.registerTime),
    lastActive: user.lastLoginIp ? '近期活跃' : '--',
    phone: user.mobile || user.email || '--',
  }
}

/**
 * 获取用户分页列表。
 */
export async function getUserList(params: UserListParams): Promise<UserListResponse> {
  const res = (await request({
    url: '/admin/user',
    method: 'get',
    params: {
      page: params.page,
      pageSize: params.pageSize,
      keyword: params.searchQuery,
      role: toBackendRole(params.role),
      status: toBackendStatus(params.status),
    },
  })) as PageResult<UserVo>

  return {
    total: res.total ?? 0,
    items: (res.list || []).map(normalizeUser),
    page: res.page ?? params.page,
    pageSize: res.pageSize ?? params.pageSize,
  }
}

/**
 * 管理员手动建档参数。
 */
export interface CreateUserPayload {
  name: string
  phone: string
  role: string
}

/**
 * 管理员手动建档。
 */
export function createUser(data: CreateUserPayload) {
  return request({
    url: '/admin/user',
    method: 'post',
    data,
  })
}

/**
 * 限制用户（封禁）。
 */
export function restrictUser(userId: string, reason: string) {
  return request({
    url: `/admin/user/${userId}/ban`,
    method: 'put',
    params: { reason },
  })
}

/**
 * 解除限制（解封）。
 */
export function unrestrictUser(userId: string) {
  return request({
    url: `/admin/user/${userId}/unban`,
    method: 'put',
  })
}

/**
 * 导出用户 CSV。
 */
export function exportUsers(keyword?: string, startTime?: string, endTime?: string) {
  return request({
    url: '/admin/user/export',
    method: 'get',
    params: { keyword, startTime, endTime },
    responseType: 'text',
  })
}
