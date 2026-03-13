<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { AlertTriangle, CheckCircle, Filter, Loader2, Plus, RefreshCw, Search } from 'lucide-vue-next'
import { createUser, exportUsers, getUserList, restrictUser, unrestrictUser, type UserItem } from '@/api/user'
import {
  fetchUserCredit,
  fetchUserCreditLogs,
  fetchUserViolationRecords,
  recalcUserCredit,
  type CreditLogItem,
  type UserCreditInfo,
  type UserViolationItem,
} from '@/api/adminExtra'

/**
 * 表格数据：始终以接口返回为准。
 */
const users = ref<UserItem[]>([])

/**
 * 查询区状态：
 * - role/status 用“后端可识别值”做选项 value，避免前后端再做二次猜测。
 */
const searchQuery = ref('')
const selectedRole = ref('ALL')
const selectedStatus = ref('ALL')

/**
 * 分页状态：
 * - currentPage/pageSize 会直接参与后端分页请求。
 */
const currentPage = ref(1)
const pageSize = ref(10)
const totalCount = ref(0)

/**
 * 交互状态：
 * - loading 控制遮罩。
 * - fetchTimer 用于搜索防抖，减少请求抖动。
 */
const loading = ref(false)
let fetchTimer: ReturnType<typeof setTimeout> | undefined

/**
 * 人工建档弹窗状态：
 * role 默认普通买家。
 */
const isModalOpen = ref(false)
const newUser = ref({
  name: '',
  phone: '',
  role: 'BUYER_NORMAL',
})

/**
 * 封禁理由弹窗状态：
 */
const isBanModalOpen = ref(false)
const banTargetUser = ref<{ id: string; name: string } | null>(null)
const banReason = ref('')
const isBanning = ref(false)
const banError = ref('')
const isDetailModalOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailUser = ref<UserItem | null>(null)
const userCredit = ref<UserCreditInfo | null>(null)
const userCreditLogs = ref<CreditLogItem[]>([])
const userViolations = ref<UserViolationItem[]>([])
const recalcLoading = ref(false)

/**
 * 总页数与分页按钮。
 */
const totalPages = computed(() => {
  const pages = Math.ceil(totalCount.value / pageSize.value)
  return Math.max(1, pages || 1)
})

const pageNumbers = computed(() => {
  const total = totalPages.value
  const current = currentPage.value
  const start = Math.max(1, current - 2)
  const end = Math.min(total, start + 4)
  const result: number[] = []
  for (let p = start; p <= end; p += 1) {
    result.push(p)
  }
  return result
})

/**
 * 核心查询：
 * 1) 使用后端分页参数；
 * 2) role/status/search 都走后端过滤；
 * 3) 请求完成后回填 total/page。
 */
const fetchData = async () => {
  try {
    loading.value = true
    const res = await getUserList({
      page: currentPage.value,
      pageSize: pageSize.value,
      searchQuery: searchQuery.value || undefined,
      role: selectedRole.value === 'ALL' ? undefined : selectedRole.value,
      status: selectedStatus.value === 'ALL' ? undefined : selectedStatus.value,
    })

    users.value = res.items || []
    totalCount.value = res.total || 0
    currentPage.value = res.page || currentPage.value
  } catch (error) {
    console.warn('User API request failed.', error)
  } finally {
    loading.value = false
  }
}

/**
 * 统一页码跳转：
 * 只在合法范围内切页，避免无效请求。
 */
const goToPage = (page: number) => {
  if (page < 1 || page > totalPages.value || page === currentPage.value) return
  currentPage.value = page
  fetchData()
}

/**
 * 封禁与解封后，保持当前筛选条件重新拉取。
 */
const openBanModal = (user: UserItem) => {
  banTargetUser.value = { id: user.id, name: user.name }
  banReason.value = ''
  banError.value = ''
  isBanModalOpen.value = true
}

const confirmBan = async () => {
  if (!banTargetUser.value) return
  const reason = banReason.value.trim()
  if (!reason) {
    banError.value = '封禁理由不能为空'
    return
  }

  try {
    isBanning.value = true
    banError.value = ''
    await restrictUser(banTargetUser.value.id, reason)
    isBanModalOpen.value = false
    await fetchData()
  } catch (e: any) {
    console.warn('Restrict user failed.', e)
    banError.value = e.message || '封禁失败，请稍后重试'
  } finally {
    isBanning.value = false
  }
}

const handleUnrestrict = async (userId: string) => {
  try {
    await unrestrictUser(userId)
    await fetchData()
  } catch (e) {
    console.warn('Unrestrict user failed.', e)
  }
}

const openDetailModal = async (user: UserItem) => {
  isDetailModalOpen.value = true
  detailUser.value = user
  detailLoading.value = true
  detailError.value = ''
  userCredit.value = null
  userCreditLogs.value = []
  userViolations.value = []

  try {
    const [credit, creditLogs, violations] = await Promise.all([
      fetchUserCredit(user.id),
      fetchUserCreditLogs(user.id, 1, 5),
      fetchUserViolationRecords(user.id, 1, 5),
    ])
    userCredit.value = credit
    userCreditLogs.value = creditLogs.list || []
    userViolations.value = violations.list || []
  } catch (error: any) {
    detailError.value = error?.message || '详情拉取失败'
  } finally {
    detailLoading.value = false
  }
}

const handleRecalcCredit = async () => {
  if (!detailUser.value) return
  try {
    recalcLoading.value = true
    const latest = await recalcUserCredit(detailUser.value.id)
    userCredit.value = latest
    const creditLogs = await fetchUserCreditLogs(detailUser.value.id, 1, 5)
    userCreditLogs.value = creditLogs.list || []
  } catch (error: any) {
    detailError.value = error?.message || '信用重算失败'
  } finally {
    recalcLoading.value = false
  }
}

/**
 * 导出接口目前按关键字导出。
 */
const handleExport = async () => {
  try {
    await exportUsers(searchQuery.value || undefined)
  } catch (error) {
    console.warn('Export user failed.', error)
  }
}

/**
 * 人工建档：
 * - 改为调用后端真实接口；
 * - 成功后关闭弹窗并回到第一页刷新。
 */
const handleAddUser = async () => {
  const name = newUser.value.name.trim()
  const phone = newUser.value.phone.trim()
  if (!name || !phone) return

  try {
    await createUser({
      name,
      phone,
      role: newUser.value.role,
    })
    isModalOpen.value = false
    newUser.value = { name: '', phone: '', role: 'BUYER_NORMAL' }
    currentPage.value = 1
    await fetchData()
  } catch (error) {
    console.warn('Create user failed.', error)
  }
}

/**
 * 搜索/筛选变化时做防抖请求，并且回到第一页。
 */
watch([searchQuery, selectedRole, selectedStatus], () => {
  currentPage.value = 1
  window.clearTimeout(fetchTimer)
  fetchTimer = window.setTimeout(() => {
    fetchData()
  }, 300)
})

/**
 * 每页大小变化时立即重查。
 */
watch(pageSize, () => {
  currentPage.value = 1
  fetchData()
})

onMounted(() => {
  fetchData()
})

/**
 * 状态/角色标签样式。
 */
const getStatusBadgeClass = (status: string) => {
  switch (status) {
    case '正常': return 'bg-green-50 text-green-700 border-green-200'
    case '预警': return 'bg-orange-50 text-orange-700 border-orange-200'
    case '限流': return 'bg-yellow-50 text-yellow-700 border-yellow-200'
    case '封禁': return 'bg-red-50 text-red-700 border-red-200'
    default: return 'bg-gray-50 text-gray-700 border-gray-200'
  }
}

const getRoleBadgeClass = (role: string) => {
  switch (role) {
    case '企业商家': return 'bg-blue-50 text-blue-700 border-blue-200 font-semibold'
    case '个人卖家': return 'bg-cyan-50 text-cyan-700 border-cyan-200'
    case '平台运营': return 'bg-violet-50 text-violet-700 border-violet-200'
    case '普通买家':
    default:
      return 'bg-gray-50 text-gray-700 border-gray-200'
  }
}
</script>

<template>
  <div class="space-y-6 max-w-[1600px] mx-auto pb-8">
    <!-- Page Header (Level 1: 治理工作台入口) -->
    <div class="bg-white border border-gray-200/80 rounded-xl p-6 shadow-sm relative overflow-hidden">
      <div class="absolute top-0 right-0 w-64 h-full bg-gradient-to-l from-blue-50/50 to-transparent pointer-events-none"></div>
      <div class="relative z-10 flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <div class="flex items-center gap-3 mb-2">
            <h1 class="text-2xl font-bold text-gray-900 tracking-tight">用户与商家管理</h1>
            <span class="px-2 py-0.5 bg-gray-100 text-gray-600 text-[12px] font-medium rounded border border-gray-200">治理中心</span>
          </div>
          <p class="text-[14px] text-gray-500">
            统一管理平台内买家档案、商家资质与账号状态，当前共 <span class="font-numeric font-medium text-gray-900">{{ totalCount }}</span> 个档案
          </p>
        </div>
        <div class="flex items-center gap-3">
          <div class="flex items-center gap-2 text-[12px] text-orange-700 bg-orange-50 px-3 py-1.5 rounded-md border border-orange-100 mr-2">
            <AlertTriangle class="w-3.5 h-3.5" />
            <span>预警账户: <span class="font-numeric font-bold">14</span></span>
          </div>
          <button @click="handleExport" class="btn-default gap-2 text-[13px] py-2 px-4">导出明细</button>
          <button @click="isModalOpen = true" class="btn-primary gap-2 text-[13px] py-2 px-4">
            <Plus class="w-4 h-4" />
            人工建档
          </button>
        </div>
      </div>
    </div>

    <!-- Filter Bar (Level 2: 高频管理工具栏) -->
    <div class="bg-white border border-gray-200/80 rounded-xl p-4 shadow-sm flex flex-col lg:flex-row gap-4 items-start lg:items-center justify-between">
      <div class="flex flex-wrap gap-3 items-center w-full lg:w-auto">
        <div class="relative w-full sm:w-72">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索 ID / 昵称 / 手机号"
            class="input-standard !pl-9 w-full py-2 text-[13px] bg-gray-50/50 focus:bg-white"
          />
        </div>

        <div class="h-6 w-px bg-gray-200 hidden sm:block mx-1"></div>

        <select v-model="selectedRole" class="input-standard min-w-[140px] py-2 text-[13px] bg-gray-50/50 focus:bg-white">
          <option value="ALL">全部业务角色</option>
          <option value="BUYER">普通买家</option>
          <option value="SELLER">卖家</option>
          <option value="PLATFORM_OPS">平台运营</option>
        </select>

        <select v-model="selectedStatus" class="input-standard min-w-[140px] py-2 text-[13px] bg-gray-50/50 focus:bg-white">
          <option value="ALL">全部账号状态</option>
          <option value="active">正常</option>
          <option value="inactive">预警</option>
          <option value="frozen">限流</option>
          <option value="banned">封禁</option>
        </select>
      </div>

      <div class="flex space-x-3 shrink-0">
        <button class="btn-default gap-1.5 text-red-600 bg-red-50/50 hover:bg-red-50 border-red-100 border transition-colors hidden sm:flex py-2 px-3 text-[13px]">
          <AlertTriangle class="w-3.5 h-3.5" /> 仅看预警
        </button>
        <button class="btn-default gap-1.5 text-gray-600 py-2 px-3 text-[13px] bg-gray-50/50 hover:bg-gray-100">
          <Filter class="w-3.5 h-3.5" /> 更多筛选
        </button>
      </div>
    </div>

    <!-- Data Table (Level 3: 治理列表) -->
    <div class="bg-white border border-gray-200/80 rounded-xl shadow-sm overflow-hidden relative">
      <div v-if="loading" class="absolute inset-0 z-10 flex items-center justify-center bg-white/60 backdrop-blur-[1px]">
        <div class="flex items-center gap-2 rounded-lg border border-gray-200 bg-white px-5 py-2.5 text-[13px] font-medium text-gray-600 shadow-sm">
          <Loader2 class="w-4 h-4 animate-spin text-blue-600" /> 数据加载中...
        </div>
      </div>

      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse whitespace-nowrap">
          <thead>
            <tr class="bg-gray-50/80 border-b border-gray-200/80 text-[13px] font-medium text-gray-500">
              <th class="py-3.5 px-5 min-w-[220px]">用户档案</th>
              <th class="py-3.5 px-5">账号角色</th>
              <th class="py-3.5 px-5">信用分</th>
              <th class="py-3.5 px-5">近30天成交</th>
              <th class="py-3.5 px-5">投诉/纠纷</th>
              <th class="py-3.5 px-5">账号状态</th>
              <th class="py-3.5 px-5">注册日期</th>
              <th class="py-3.5 px-5 text-right">操作</th>
            </tr>
          </thead>
          <tbody class="text-[13px] text-gray-700 divide-y divide-gray-100/80">
            <tr v-for="user in users" :key="user.id" class="hover:bg-gray-50/80 transition-colors group">
              <td class="py-3 px-5">
                <div class="flex items-center gap-3.5">
                  <div class="w-9 h-9 rounded-lg bg-gray-100 border border-gray-200 flex items-center justify-center text-gray-600 font-bold text-[14px] shrink-0 shadow-sm">
                    {{ user.name.charAt(0).toUpperCase() }}
                  </div>
                  <div class="flex flex-col">
                    <span class="font-medium text-gray-900 text-[14px]">{{ user.name }}</span>
                    <div class="flex items-center text-[11px] text-gray-500 mt-1 space-x-2">
                      <span class="font-numeric bg-gray-100 px-1.5 py-0.5 rounded border border-gray-200 leading-none">ID: {{ user.id }}</span>
                      <span class="font-numeric">{{ user.phone }}</span>
                    </div>
                  </div>
                </div>
              </td>

              <td class="py-3 px-5">
                <span class="badge px-2 py-1" :class="getRoleBadgeClass(user.role)">
                  {{ user.role }}
                </span>
              </td>

              <td class="py-3 px-5">
                <div class="flex items-center gap-2">
                  <span class="font-numeric font-medium text-[14px]" :class="user.creditScore < 600 ? 'text-red-600' : 'text-gray-900'">{{ user.creditScore }}</span>
                  <span v-if="user.creditScore < 600" class="text-[10px] text-red-600 bg-red-50 border border-red-100 px-1.5 py-0.5 rounded leading-none">偏低</span>
                </div>
              </td>
              <td class="py-3 px-5 font-numeric text-[14px]">{{ user.trade30d }}</td>
              <td class="py-3 px-5">
                <span class="font-numeric text-[14px]" :class="user.complaints > 0 ? 'text-orange-600 font-bold' : 'text-gray-500'">{{ user.complaints }}</span>
              </td>

              <td class="py-3 px-5">
                <span class="badge flex items-center w-fit gap-1.5 px-2 py-1" :class="getStatusBadgeClass(user.status)">
                  <span class="w-1.5 h-1.5 rounded-full" :class="{
                    'bg-green-500': user.status === '正常',
                    'bg-orange-500': user.status === '预警',
                    'bg-yellow-500': user.status === '限流',
                    'bg-red-500': user.status === '封禁'
                  }"></span>
                  {{ user.status }}
                </span>
              </td>

              <td class="py-3 px-5 font-numeric text-gray-500 text-[12px]">{{ user.registerDate }}</td>

              <td class="py-3 px-5 text-right">
                <div class="flex items-center justify-end space-x-3 opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity">
                  <button
                    @click="openDetailModal(user)"
                    class="text-gray-600 hover:text-gray-900 font-medium text-[13px] transition-colors bg-white border border-gray-200 px-3 py-1.5 rounded-md shadow-sm hover:border-gray-300"
                  >
                    详情
                  </button>
                  <button v-if="user.status !== '封禁'" @click="openBanModal(user)" class="text-red-600 hover:text-red-700 font-medium text-[13px] transition-colors bg-red-50 border border-red-100 px-3 py-1.5 rounded-md shadow-sm hover:border-red-200">
                    限制
                  </button>
                  <button v-else @click="handleUnrestrict(user.id)" class="text-gray-600 hover:text-gray-900 font-medium text-[13px] transition-colors bg-white border border-gray-200 px-3 py-1.5 rounded-md shadow-sm hover:border-gray-300">
                    解封
                  </button>
                </div>
              </td>
            </tr>

            <tr v-if="users.length === 0 && !loading">
              <td colspan="8" class="py-16 text-center">
                <div class="flex flex-col items-center justify-center text-gray-400">
                  <Search class="w-8 h-8 mb-3 text-gray-300" />
                  <p class="text-[14px] font-medium text-gray-500">没有找到符合条件的用户</p>
                  <p class="text-[12px] mt-1">请尝试调整筛选条件或搜索关键词</p>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div class="border-t border-gray-100 px-5 py-4 flex flex-col sm:flex-row items-center justify-between bg-gray-50/50 gap-4">
        <div class="text-[13px] text-gray-500 flex items-center space-x-4">
          <span>共 <span class="font-medium font-numeric text-gray-900">{{ totalCount }}</span> 条记录</span>
          <select v-model.number="pageSize" class="input-standard py-1.5 text-[12px] bg-white border-gray-200">
            <option :value="10">10 条/页</option>
            <option :value="20">20 条/页</option>
            <option :value="50">50 条/页</option>
          </select>
        </div>

        <div class="flex space-x-1.5">
          <button
            class="px-3 py-1.5 min-w-[36px] border border-gray-200 rounded-md bg-white text-gray-600 text-[13px] hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors shadow-sm"
            :disabled="currentPage <= 1"
            @click="goToPage(currentPage - 1)"
          >
            上一页
          </button>
          <button
            v-for="page in pageNumbers"
            :key="page"
            class="px-3 py-1.5 min-w-[36px] border rounded-md text-[13px] transition-colors shadow-sm font-numeric"
            :class="page === currentPage ? 'border-gray-900 bg-gray-900 text-white font-medium' : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50'"
            @click="goToPage(page)"
          >
            {{ page }}
          </button>
          <button
            class="px-3 py-1.5 min-w-[36px] border border-gray-200 rounded-md bg-white text-gray-600 text-[13px] hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors shadow-sm"
            :disabled="currentPage >= totalPages"
            @click="goToPage(currentPage + 1)"
          >
            下一页
          </button>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <div v-if="isDetailModalOpen" class="fixed inset-0 bg-gray-900/40 backdrop-blur-sm z-50 flex items-center justify-center p-4">
        <div class="bg-white rounded-xl shadow-2xl w-full max-w-3xl border border-gray-200/80 overflow-hidden flex flex-col max-h-[85vh]" @click.stop>
          <div class="flex justify-between items-center px-6 py-4 border-b border-gray-100 bg-gray-50/60">
            <h2 class="text-[16px] font-bold text-gray-900">
              用户详情联调
              <span v-if="detailUser" class="ml-2 text-gray-500 font-normal text-[13px]">ID: {{ detailUser.id }}</span>
            </h2>
            <button @click="isDetailModalOpen = false" class="text-gray-400 hover:text-gray-700 transition-colors p-1.5 rounded-md hover:bg-gray-200">
              <span class="text-xl leading-none">&times;</span>
            </button>
          </div>

          <div class="p-6 space-y-5 overflow-y-auto">
            <div v-if="detailLoading" class="flex items-center gap-2 text-[13px] text-gray-500">
              <Loader2 class="w-4 h-4 animate-spin" /> 正在拉取信用与违规数据...
            </div>

            <div v-else>
              <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
                <div class="rounded-lg border border-gray-200 bg-gray-50/60 p-4">
                  <p class="text-[12px] text-gray-500">信用分</p>
                  <p class="text-[22px] font-semibold text-gray-900 font-numeric mt-1">{{ userCredit?.creditScore ?? '--' }}</p>
                </div>
                <div class="rounded-lg border border-gray-200 bg-gray-50/60 p-4">
                  <p class="text-[12px] text-gray-500">信用等级</p>
                  <p class="text-[18px] font-semibold text-gray-900 mt-1">{{ userCredit?.creditLevel ?? '--' }}</p>
                </div>
                <div class="rounded-lg border border-gray-200 bg-gray-50/60 p-4">
                  <p class="text-[12px] text-gray-500">违规记录(近5条)</p>
                  <p class="text-[18px] font-semibold text-gray-900 font-numeric mt-1">{{ userViolations.length }}</p>
                </div>
              </div>

              <div class="mt-3">
                <button @click="handleRecalcCredit" class="btn-default gap-2 text-[12px] py-1.5 px-3" :disabled="recalcLoading || !detailUser">
                  <RefreshCw v-if="recalcLoading" class="w-3.5 h-3.5 animate-spin" />
                  <RefreshCw v-else class="w-3.5 h-3.5" />
                  触发信用重算
                </button>
              </div>

              <div class="mt-5">
                <h3 class="text-[14px] font-semibold text-gray-900 mb-2">信用流水（/admin/credit/logs）</h3>
                <div class="rounded-lg border border-gray-200 overflow-hidden">
                  <table class="w-full text-left text-[12px]">
                    <thead class="bg-gray-50 text-gray-500">
                      <tr>
                        <th class="px-3 py-2">时间</th>
                        <th class="px-3 py-2">类型</th>
                        <th class="px-3 py-2">变动</th>
                        <th class="px-3 py-2">分数</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="log in userCreditLogs" :key="log.id" class="border-t border-gray-100">
                        <td class="px-3 py-2 text-gray-600">{{ log.createTime || '--' }}</td>
                        <td class="px-3 py-2 text-gray-700">{{ log.reasonType || '--' }}</td>
                        <td class="px-3 py-2 font-numeric" :class="(log.delta || 0) >= 0 ? 'text-green-700' : 'text-red-700'">{{ log.delta }}</td>
                        <td class="px-3 py-2 text-gray-700 font-numeric">{{ log.scoreBefore }} -> {{ log.scoreAfter }}</td>
                      </tr>
                      <tr v-if="userCreditLogs.length === 0">
                        <td colspan="4" class="px-3 py-4 text-center text-gray-400">暂无信用流水</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <div class="mt-5">
                <h3 class="text-[14px] font-semibold text-gray-900 mb-2">违规记录（/admin/users?userId=...）</h3>
                <div class="rounded-lg border border-gray-200 p-3 text-[12px] space-y-2">
                  <div v-for="v in userViolations" :key="v.id" class="border border-gray-100 rounded-md px-3 py-2 bg-gray-50/40">
                    <div class="flex justify-between gap-3">
                      <span class="font-medium text-gray-800">{{ v.violationTypeDesc || v.violationType }}</span>
                      <span class="text-gray-500 font-numeric">{{ v.recordTime || '--' }}</span>
                    </div>
                    <p class="text-gray-500 mt-1">{{ v.description || '无描述' }}</p>
                  </div>
                  <p v-if="userViolations.length === 0" class="text-gray-400 text-center py-2">暂无违规记录</p>
                </div>
              </div>

              <p v-if="detailError" class="text-[12px] text-red-600 mt-4">{{ detailError }}</p>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 人工建档弹窗 -->
    <Teleport to="body">
      <div v-if="isModalOpen" class="fixed inset-0 bg-gray-900/40 backdrop-blur-sm z-50 flex items-center justify-center">
        <div class="bg-white rounded-xl shadow-2xl w-full max-w-md border border-gray-200/80 overflow-hidden flex flex-col" @click.stop>
          <div class="flex justify-between items-center px-6 py-5 border-b border-gray-100 bg-gray-50/50">
            <h2 class="text-[16px] font-bold text-gray-900">人工建立用户档案</h2>
            <button @click="isModalOpen = false" class="text-gray-400 hover:text-gray-700 transition-colors p-1.5 rounded-md hover:bg-gray-200">
              <span class="text-xl leading-none">&times;</span>
            </button>
          </div>

          <div class="p-6 space-y-5">
            <div>
              <label for="new_user_name" class="block text-[13px] font-medium text-gray-700 mb-2">平台昵称 <span class="text-red-500">*</span></label>
              <input id="new_user_name" v-model="newUser.name" type="text" class="input-standard w-full py-2" placeholder="输入用户展示昵称" />
            </div>

            <div>
              <label for="new_user_phone" class="block text-[13px] font-medium text-gray-700 mb-2">手机号码 <span class="text-red-500">*</span></label>
              <input id="new_user_phone" v-model="newUser.phone" type="tel" class="input-standard w-full font-numeric py-2" placeholder="11位手机号" />
            </div>

            <div>
              <label for="new_user_role" class="block text-[13px] font-medium text-gray-700 mb-2">初始角色 <span class="text-red-500">*</span></label>
              <select id="new_user_role" v-model="newUser.role" class="input-standard w-full bg-white py-2">
                <option value="BUYER_NORMAL">普通买家</option>
                <option value="BUYER_VERIFIED">认证买家</option>
                <option value="SELLER_PERSONAL">个人卖家</option>
                <option value="SELLER_ENTERPRISE">企业商家</option>
              </select>
            </div>

            <div class="bg-blue-50/50 p-3.5 rounded-lg border border-blue-100 flex items-start gap-2.5 mt-2">
              <CheckCircle class="w-4.5 h-4.5 text-blue-500 shrink-0 mt-0.5" />
              <p class="text-[13px] text-blue-800/80 leading-relaxed">
                建档成功后会写入后端真实数据，列表将自动刷新。
              </p>
            </div>
          </div>

          <div class="px-6 py-4 border-t border-gray-100 flex justify-end space-x-3 bg-gray-50/80 mt-auto">
            <button @click="isModalOpen = false" class="btn-default px-4 py-2">取消</button>
            <button @click="handleAddUser" class="btn-primary px-4 py-2" :disabled="!newUser.name || !newUser.phone">
              确认并建档
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 封禁理由弹窗 -->
    <Teleport to="body">
      <div v-if="isBanModalOpen" class="fixed inset-0 bg-gray-900/40 backdrop-blur-sm z-50 flex items-center justify-center">
        <div class="bg-white rounded-xl shadow-2xl w-full max-w-md border border-gray-200/80 overflow-hidden flex flex-col" @click.stop>
          <div class="flex justify-between items-center px-6 py-5 border-b border-gray-100 bg-red-50/30">
            <h2 class="text-[16px] font-bold text-gray-900 flex items-center gap-2">
              <AlertTriangle class="w-4.5 h-4.5 text-red-500" />
              限制账号
            </h2>
            <button @click="isBanModalOpen = false" class="text-gray-400 hover:text-gray-700 transition-colors p-1.5 rounded-md hover:bg-gray-200" :disabled="isBanning">
              <span class="text-xl leading-none">&times;</span>
            </button>
          </div>

          <div class="p-6 space-y-5">
            <div class="bg-gray-50 p-4 rounded-lg border border-gray-200 flex flex-col gap-1.5">
              <span class="text-[12px] text-gray-500 font-medium">当前操作用户</span>
              <span class="text-[14px] font-bold text-gray-900">{{ banTargetUser?.name }} <span class="text-gray-500 font-numeric font-normal ml-1">(ID: {{ banTargetUser?.id }})</span></span>
            </div>

            <div>
              <label for="ban_reason" class="block text-[13px] font-medium text-gray-700 mb-2">封禁理由 <span class="text-red-500">*</span></label>
              <textarea 
                id="ban_reason" 
                v-model="banReason" 
                class="input-standard w-full min-h-[120px] resize-none py-2.5" 
                placeholder="请输入限制原因，如涉嫌违规交易、辱骂骚扰、恶意刷单等"
                :disabled="isBanning"
                maxlength="100"
              ></textarea>
              <div class="flex justify-between items-center mt-2">
                <span v-if="banError" class="text-[12px] text-red-500 flex items-center gap-1.5 font-medium"><AlertTriangle class="w-3.5 h-3.5" /> {{ banError }}</span>
                <span v-else class="text-[12px] text-gray-400"></span>
                <span class="text-[12px] text-gray-400 font-numeric">{{ banReason.length }}/100</span>
              </div>
            </div>
          </div>

          <div class="px-6 py-4 border-t border-gray-100 flex justify-end space-x-3 bg-gray-50/80 mt-auto">
            <button @click="isBanModalOpen = false" class="btn-default px-4 py-2" :disabled="isBanning">取消</button>
            <button @click="confirmBan" class="btn-primary bg-red-600 hover:bg-red-700 border-red-700/50 flex items-center gap-2 px-4 py-2" :disabled="isBanning || !banReason.trim()">
              <Loader2 v-if="isBanning" class="w-4 h-4 animate-spin" />
              {{ isBanning ? '提交中...' : '确认限制' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
