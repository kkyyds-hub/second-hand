import type { CreateUserPayload, UserItem, UserListResponse } from '@/api/user'
import { cloneData, mockDelay, readLocalJson, writeLocalJson } from './config'

/**
 * 用户管理 mock：
 * - 首次读取时用 seed 初始化
 * - 后续所有增删改都写回本地存储，便于你反复点击后仍能保留现场
 */
const USER_STORAGE_KEY = 'demo_admin_mock_users_v1'

type RoleCode = 'BUYER_NORMAL' | 'BUYER_VERIFIED' | 'SELLER_PERSONAL' | 'SELLER_ENTERPRISE'
type StatusCode = 'active' | 'inactive' | 'frozen' | 'banned'

interface MockUserRecord {
  id: string
  name: string
  phone: string
  roleCode: RoleCode
  statusCode: StatusCode
  creditScore: number
  trade30d: number
  complaints: number
  registerDate: string
  lastActive: string
  avatar?: string
}

export interface MockUserListParams {
  page: number
  pageSize: number
  keyword?: string
  role?: string
  status?: string
}

// 这批 seed 尽量覆盖普通买家、卖家、企业商家、异常账号等常见 review 场景。
const userSeed: MockUserRecord[] = [
  {
    id: '10001',
    name: '运营小王',
    phone: '13800000001',
    roleCode: 'BUYER_NORMAL',
    statusCode: 'active',
    creditScore: 680,
    trade30d: 1,
    complaints: 0,
    registerDate: '2026-01-08',
    lastActive: '近期活跃',
  },
  {
    id: '10002',
    name: '潮鞋买手店',
    phone: '13800000002',
    roleCode: 'SELLER_PERSONAL',
    statusCode: 'active',
    creditScore: 720,
    trade30d: 15,
    complaints: 1,
    registerDate: '2026-01-12',
    lastActive: '近期活跃',
  },
  {
    id: '10003',
    name: '企业回收中心',
    phone: '13800000003',
    roleCode: 'SELLER_ENTERPRISE',
    statusCode: 'inactive',
    creditScore: 640,
    trade30d: 45,
    complaints: 3,
    registerDate: '2026-01-16',
    lastActive: '--',
  },
  {
    id: '10004',
    name: '鉴定达人',
    phone: '13800000004',
    roleCode: 'BUYER_VERIFIED',
    statusCode: 'frozen',
    creditScore: 601,
    trade30d: 7,
    complaints: 2,
    registerDate: '2026-02-01',
    lastActive: '近期活跃',
  },
  {
    id: '10005',
    name: '高风险测试号',
    phone: '13800000005',
    roleCode: 'SELLER_PERSONAL',
    statusCode: 'banned',
    creditScore: 320,
    trade30d: 6,
    complaints: 12,
    registerDate: '2026-02-07',
    lastActive: '--',
  },
]

function loadUsers() {
  return readLocalJson<MockUserRecord[]>(USER_STORAGE_KEY, cloneData(userSeed))
}

function saveUsers(list: MockUserRecord[]) {
  writeLocalJson(USER_STORAGE_KEY, list)
}

function roleToLabel(code: RoleCode): string {
  switch (code) {
    case 'BUYER_VERIFIED':
      return '认证买家'
    case 'SELLER_PERSONAL':
      return '个人卖家'
    case 'SELLER_ENTERPRISE':
      return '企业商家'
    case 'BUYER_NORMAL':
    default:
      return '普通买家'
  }
}

function statusToLabel(code: StatusCode): string {
  switch (code) {
    case 'inactive':
      return '预警'
    case 'frozen':
      return '限流'
    case 'banned':
      return '封禁'
    case 'active':
    default:
      return '正常'
  }
}

function toUserItem(item: MockUserRecord): UserItem {
  return {
    id: item.id,
    name: item.name,
    phone: item.phone,
    role: roleToLabel(item.roleCode),
    status: statusToLabel(item.statusCode),
    creditScore: item.creditScore,
    trade30d: item.trade30d,
    complaints: item.complaints,
    registerDate: item.registerDate,
    lastActive: item.lastActive,
    avatar: item.avatar,
  }
}

export async function mockGetUserList(params: MockUserListParams): Promise<UserListResponse> {
  await mockDelay()
  const keyword = (params.keyword || '').trim().toLowerCase()
  const role = params.role
  const status = params.status

  // mock 查询尽量贴近真实接口语义：role/status 直接按 API 层映射后的值过滤。
  const filtered = loadUsers().filter((item) => {
    if (role && item.roleCode !== role) return false
    if (status && item.statusCode !== status) return false

    if (keyword) {
      const text = `${item.id} ${item.name} ${item.phone}`.toLowerCase()
      if (!text.includes(keyword)) return false
    }
    return true
  })

  const start = (params.page - 1) * params.pageSize
  const end = start + params.pageSize
  const slice = filtered.slice(start, end).map(toUserItem)

  return {
    total: filtered.length,
    items: slice,
    page: params.page,
    pageSize: params.pageSize,
  }
}

export async function mockCreateUser(payload: CreateUserPayload): Promise<void> {
  await mockDelay(260)
  const list = loadUsers()
  const now = new Date()
  const newItem: MockUserRecord = {
    id: String(Date.now()).slice(-8),
    name: payload.name.trim(),
    phone: payload.phone.trim(),
    roleCode: (payload.role as RoleCode) || 'BUYER_NORMAL',
    statusCode: 'active',
    creditScore: 600,
    trade30d: 0,
    complaints: 0,
    registerDate: now.toISOString().slice(0, 10),
    lastActive: '近期活跃',
  }
  // 新建用户插到头部，模拟“后台刚建档就能立刻看到”的体验。
  list.unshift(newItem)
  saveUsers(list)
}

// mock 限制/解除限制只切换状态码，不额外模拟复杂审核流。
export async function mockRestrictUser(userId: string): Promise<void> {
  await mockDelay(220)
  const list = loadUsers()
  const target = list.find((item) => item.id === userId)
  if (!target) {
    throw new Error('用户不存在')
  }
  target.statusCode = 'banned'
  saveUsers(list)
}

export async function mockUnrestrictUser(userId: string): Promise<void> {
  await mockDelay(220)
  const list = loadUsers()
  const target = list.find((item) => item.id === userId)
  if (!target) {
    throw new Error('用户不存在')
  }
  target.statusCode = 'active'
  saveUsers(list)
}

/**
 * mock 导出返回 CSV 文本内容，方便页面在不连真后端时也能走完整导出链路。
 */
export async function mockExportUsers(keyword?: string): Promise<string> {
  await mockDelay(240)
  const lowKeyword = (keyword || '').trim().toLowerCase()
  const rows = loadUsers().filter((item) => {
    if (!lowKeyword) return true
    const text = `${item.id} ${item.name} ${item.phone}`.toLowerCase()
    return text.includes(lowKeyword)
  })

  const headers = ['id', 'name', 'phone', 'role', 'status', 'creditScore', 'trade30d', 'complaints', 'registerDate']
  const body = rows.map((item) =>
    [
      item.id,
      item.name,
      item.phone,
      roleToLabel(item.roleCode),
      statusToLabel(item.statusCode),
      item.creditScore,
      item.trade30d,
      item.complaints,
      item.registerDate,
    ].join(','),
  )

  return [headers.join(','), ...body].join('\n')
}
