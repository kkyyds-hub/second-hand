<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { AlertTriangle, CheckCircle, Filter, Loader2, Plus, Search } from 'lucide-vue-next'
import { createUser, exportUsers, getUserList, restrictUser, unrestrictUser, type UserItem } from '@/api/user'

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
const handleRestrict = async (userId: string) => {
  try {
    await restrictUser(userId, '运营手动限制')
    await fetchData()
  } catch (e) {
    console.warn('Restrict user failed.', e)
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
  <div class="space-y-4 max-w-[1600px] mx-auto">
    <div class="flex justify-between items-center pb-2">
      <div>
        <h1 class="text-xl font-bold text-gray-900">用户与商家管理</h1>
        <p class="text-sm text-gray-500 mt-1">管理平台内买家档案、商家资质与账号状态</p>
      </div>
      <div class="flex space-x-3">
        <button @click="handleExport" class="btn-default gap-2">导出明细</button>
        <button @click="isModalOpen = true" class="btn-primary gap-2">
          <Plus class="w-4 h-4" />
          人工建档
        </button>
      </div>
    </div>

    <div class="card p-4 flex flex-col lg:flex-row gap-4 items-start lg:items-center justify-between">
      <div class="flex flex-wrap gap-3 items-center w-full lg:w-auto">
        <div class="relative w-full sm:w-72">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索 ID / 昵称 / 手机号"
            class="input-standard !pl-9 w-full"
          />
        </div>

        <select v-model="selectedRole" class="input-standard min-w-[140px]">
          <option value="ALL">全部业务角色</option>
          <option value="BUYER_NORMAL">普通买家</option>
          <option value="BUYER_VERIFIED">认证买家</option>
          <option value="SELLER_PERSONAL">个人卖家</option>
          <option value="SELLER_ENTERPRISE">企业商家</option>
        </select>

        <select v-model="selectedStatus" class="input-standard min-w-[140px]">
          <option value="ALL">全部账号状态</option>
          <option value="active">正常</option>
          <option value="inactive">预警</option>
          <option value="frozen">限流</option>
          <option value="banned">封禁</option>
        </select>
      </div>

      <div class="flex space-x-2 shrink-0">
        <button class="btn-default gap-2 text-red-600 bg-red-50 hover:bg-red-100 border-red-200 border transition-colors hidden sm:flex">
          <AlertTriangle class="w-4 h-4" /> 仅看风控预警账户
        </button>
        <button class="btn-default gap-2 text-gray-600">
          <Filter class="w-4 h-4" /> 更多筛选
        </button>
      </div>
    </div>

    <div class="card overflow-hidden relative">
      <div v-if="loading" class="absolute inset-0 z-10 flex items-center justify-center bg-white/60 backdrop-blur-[1px]">
        <div class="flex items-center gap-2 rounded border border-gray-200 bg-white px-4 py-2 text-sm text-gray-600 shadow-sm">
          <Loader2 class="w-4 h-4 animate-spin" /> 数据加载中...
        </div>
      </div>

      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse whitespace-nowrap">
          <thead>
            <tr class="bg-gray-50 border-b border-gray-200 text-sm font-semibold text-gray-700">
              <th class="py-3 px-4 min-w-[180px]">用户档案</th>
              <th class="py-3 px-4">账号角色</th>
              <th class="py-3 px-4">信用分</th>
              <th class="py-3 px-4">近30天成交</th>
              <th class="py-3 px-4">投诉/纠纷</th>
              <th class="py-3 px-4">账号状态</th>
              <th class="py-3 px-4">注册日期</th>
              <th class="py-3 px-4 text-right">操作</th>
            </tr>
          </thead>
          <tbody class="text-sm text-gray-700 divide-y divide-gray-100">
            <tr v-for="user in users" :key="user.id" class="hover:bg-blue-50/50 transition-colors group">
              <td class="py-3 px-4">
                <div class="flex flex-col">
                  <span class="font-medium text-gray-900">{{ user.name }}</span>
                  <div class="flex items-center text-xs text-gray-500 mt-0.5 space-x-2">
                    <span class="font-numeric">ID: {{ user.id }}</span>
                    <span>{{ user.phone }}</span>
                  </div>
                </div>
              </td>

              <td class="py-3 px-4">
                <span class="px-2 py-1 rounded-sm border text-xs" :class="getRoleBadgeClass(user.role)">
                  {{ user.role }}
                </span>
              </td>

              <td class="py-3 px-4 font-numeric">{{ user.creditScore }}</td>
              <td class="py-3 px-4 font-numeric">{{ user.trade30d }}</td>
              <td class="py-3 px-4 font-numeric">{{ user.complaints }}</td>

              <td class="py-3 px-4">
                <span class="px-2 py-1 rounded-sm border text-xs font-medium" :class="getStatusBadgeClass(user.status)">
                  {{ user.status }}
                </span>
              </td>

              <td class="py-3 px-4 font-numeric">{{ user.registerDate }}</td>

              <td class="py-3 px-4 text-right">
                <div class="flex items-center justify-end space-x-3">
                  <button v-if="user.status !== '封禁'" @click="handleRestrict(user.id)" class="text-red-600 hover:text-red-800 font-medium text-xs">
                    限制账号
                  </button>
                  <button v-else @click="handleUnrestrict(user.id)" class="text-gray-500 hover:text-gray-700 font-medium text-xs">
                    解除限制
                  </button>
                </div>
              </td>
            </tr>

            <tr v-if="users.length === 0">
              <td colspan="8" class="py-12 text-center text-gray-400">没有找到符合条件的用户</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="border-t border-gray-200 px-4 py-3 flex flex-col sm:flex-row items-center justify-between bg-gray-50/80 gap-3">
        <div class="text-sm text-gray-500 flex items-center space-x-4">
          <span>共 <span class="font-semibold font-numeric text-gray-700">{{ totalCount }}</span> 条</span>
          <select v-model.number="pageSize" class="input-standard py-1 text-xs bg-white">
            <option :value="10">10 条/页</option>
            <option :value="20">20 条/页</option>
            <option :value="50">50 条/页</option>
          </select>
        </div>

        <div class="flex space-x-1">
          <button
            class="px-2.5 py-1 min-w-[32px] border border-gray-300 rounded bg-white text-gray-600 text-sm hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
            :disabled="currentPage <= 1"
            @click="goToPage(currentPage - 1)"
          >
            &lt;
          </button>
          <button
            v-for="page in pageNumbers"
            :key="page"
            class="px-2.5 py-1 min-w-[32px] border rounded text-sm"
            :class="page === currentPage ? 'border-blue-600 bg-blue-50 text-blue-600 font-medium' : 'border-gray-300 bg-white text-gray-600 hover:bg-gray-50'"
            @click="goToPage(page)"
          >
            {{ page }}
          </button>
          <button
            class="px-2.5 py-1 min-w-[32px] border border-gray-300 rounded bg-white text-gray-600 text-sm hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
            :disabled="currentPage >= totalPages"
            @click="goToPage(currentPage + 1)"
          >
            &gt;
          </button>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <div v-if="isModalOpen" class="fixed inset-0 bg-gray-900/40 z-50 flex items-center justify-center">
        <div class="bg-white rounded-md shadow-xl w-full max-w-md border border-gray-200 overflow-hidden flex flex-col" @click.stop>
          <div class="flex justify-between items-center px-6 py-4 border-b border-gray-200">
            <h2 class="text-base font-bold text-gray-800">人工建立用户档案</h2>
            <button @click="isModalOpen = false" class="text-gray-400 hover:text-gray-600">
              <span class="text-xl leading-none">&times;</span>
            </button>
          </div>

          <div class="p-6 space-y-5">
            <div>
              <label for="new_user_name" class="block text-sm font-medium text-gray-700 mb-1.5">平台昵称 <span class="text-red-500">*</span></label>
              <input id="new_user_name" v-model="newUser.name" type="text" class="input-standard w-full" placeholder="输入用户展示昵称" />
            </div>

            <div>
              <label for="new_user_phone" class="block text-sm font-medium text-gray-700 mb-1.5">手机号码 <span class="text-red-500">*</span></label>
              <input id="new_user_phone" v-model="newUser.phone" type="tel" class="input-standard w-full font-numeric" placeholder="11位手机号" />
            </div>

            <div>
              <label for="new_user_role" class="block text-sm font-medium text-gray-700 mb-1.5">初始角色 <span class="text-red-500">*</span></label>
              <select id="new_user_role" v-model="newUser.role" class="input-standard w-full bg-gray-50">
                <option value="BUYER_NORMAL">普通买家</option>
                <option value="BUYER_VERIFIED">认证买家</option>
                <option value="SELLER_PERSONAL">个人卖家</option>
                <option value="SELLER_ENTERPRISE">企业商家</option>
              </select>
            </div>

            <div class="bg-blue-50 p-3 rounded-sm border border-blue-100 flex items-start gap-2">
              <CheckCircle class="w-4 h-4 text-blue-500 shrink-0 mt-0.5" />
              <p class="text-xs text-blue-800 leading-relaxed">
                建档成功后会写入后端真实数据，列表将自动刷新。
              </p>
            </div>
          </div>

          <div class="px-6 py-4 border-t border-gray-200 flex justify-end space-x-3 bg-gray-50/50 mt-auto">
            <button @click="isModalOpen = false" class="btn-default">取消</button>
            <button @click="handleAddUser" class="btn-primary" :disabled="!newUser.name || !newUser.phone">
              确认并建档
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
