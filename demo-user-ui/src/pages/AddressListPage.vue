<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Loader2, MapPinHouse, Plus, RefreshCw } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import { createEmptyAddressListResult, getMyAddressList, type UserAddressItem } from '@/api/address'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const hasLoadedOnce = ref(false)
const errorMessage = ref('')
const addressPage = ref(createEmptyAddressListResult())

const addressList = computed<UserAddressItem[]>(() => addressPage.value.list)
const hasCreatedNotice = computed(() => route.query.created === '1')
const hasEditedNotice = computed(() => route.query.edited === '1')
const hasActionNotice = computed(() => hasCreatedNotice.value || hasEditedNotice.value)
const actionNoticeText = computed(() => (hasEditedNotice.value ? '地址编辑成功，列表已刷新。' : '地址新增成功，列表已刷新。'))
const hasEmptyState = computed(() => {
  return !loading.value && hasLoadedOnce.value && !errorMessage.value && addressList.value.length === 0
})

function dismissActionNotice() {
  if (!hasActionNotice.value) {
    return
  }

  /**
   * 成功提示通过 query 参数透传一次即可（created / edited），
   * 手动关闭后从 URL 清理，避免刷新页面时重复出现历史提示。
   */
  const nextQuery = { ...route.query }
  delete nextQuery.created
  delete nextQuery.edited
  router.replace({ query: nextQuery })
}

function readErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return '地址列表加载失败，请稍后重试。'
}

async function loadAddressList() {
  if (loading.value) {
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''

    /**
     * 页面层只处理 loading / empty / error / retry。
     * 字段兼容和分页结构兼容统一收敛在 `src/api/address.ts`。
     */
    addressPage.value = await getMyAddressList({
      page: 1,
      pageSize: 20,
    })
  } catch (error: unknown) {
    errorMessage.value = readErrorMessage(error)
  } finally {
    loading.value = false
    hasLoadedOnce.value = true
  }
}

onMounted(() => {
  loadAddressList()
})
</script>

<template>
  <div class="space-y-6">
    <section class="card p-6 md:p-8">
      <p class="muted-kicker">Address center</p>
      <div class="mt-3 flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <h1 class="section-title">收货地址</h1>
          <p class="section-desc">
            当前页面支持地址列表读取、新增入口与编辑入口，删除与默认地址设置能力将在后续切片补齐。
          </p>
        </div>

        <div class="flex items-center gap-3">
          <router-link class="btn-default" to="/account">返回账户中心</router-link>
          <router-link class="btn-primary gap-2" to="/account/addresses/new">
            <Plus class="h-4 w-4" />
            <span>新增地址</span>
          </router-link>
          <button class="btn-default gap-2" type="button" :disabled="loading" @click="loadAddressList">
            <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
            <RefreshCw v-else class="h-4 w-4" />
            <span>{{ loading ? '刷新中...' : '重试/刷新' }}</span>
          </button>
        </div>
      </div>
    </section>

    <section v-if="hasActionNotice" class="rounded-2xl border border-emerald-200 bg-emerald-50 px-5 py-4 text-sm text-emerald-700">
      <p>{{ actionNoticeText }}</p>
      <button class="btn-default mt-3" type="button" @click="dismissActionNotice">我知道了</button>
    </section>

    <section v-if="errorMessage" class="rounded-2xl border border-orange-200 bg-orange-50 px-5 py-4 text-sm text-orange-700">
      <p>{{ errorMessage }}</p>
      <button class="btn-default mt-3" type="button" :disabled="loading" @click="loadAddressList">重试加载</button>
    </section>

    <section v-else-if="loading && !hasLoadedOnce" class="card p-8">
      <div class="flex items-center gap-3 text-slate-600">
        <Loader2 class="h-5 w-5 animate-spin" />
        <p class="text-sm">正在加载地址列表...</p>
      </div>
    </section>

    <section v-else-if="hasEmptyState" class="card p-8">
      <div class="flex flex-col items-center justify-center gap-3 text-center text-slate-500">
        <MapPinHouse class="h-10 w-10 text-slate-300" />
        <p class="text-base font-medium text-slate-700">暂无收货地址</p>
        <p class="text-sm">当前账号还没有可展示的地址记录。</p>
        <div class="mt-2 flex flex-wrap items-center justify-center gap-2">
          <router-link class="btn-primary" to="/account/addresses/new">新增地址</router-link>
          <button class="btn-default" type="button" :disabled="loading" @click="loadAddressList">重新加载</button>
        </div>
      </div>
    </section>

    <section v-else class="card p-6">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p class="muted-kicker">Address list</p>
          <h2 class="section-title mt-2">地址列表</h2>
        </div>
        <p class="text-sm text-slate-500">
          共 {{ addressPage.total }} 条，当前第 {{ addressPage.page }} 页（pageSize={{ addressPage.pageSize }}）
        </p>
      </div>

      <div class="mt-6 grid gap-4">
        <article
          v-for="item in addressList"
          :key="item.id ?? `${item.receiverName}-${item.mobile}-${item.fullAddress}`"
          class="rounded-2xl border border-slate-200 bg-slate-50 px-5 py-5"
        >
          <div class="flex flex-wrap items-center gap-2">
            <p class="text-base font-semibold text-slate-900">{{ item.receiverName || '未命名收件人' }}</p>
            <span v-if="item.isDefault" class="rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-1 text-xs text-emerald-700">
              默认地址
            </span>
          </div>

          <p class="mt-2 text-sm text-slate-600">{{ item.mobile || '未绑定手机号' }}</p>
          <p class="mt-2 text-sm leading-6 text-slate-700">{{ item.fullAddress || '地址信息缺失' }}</p>
          <p class="mt-3 text-xs text-slate-400">地址 ID：{{ item.id ?? '-' }}</p>

          <div class="mt-4 flex items-center gap-2">
            <router-link
              v-if="item.id !== null"
              class="btn-default"
              :to="{ name: 'AccountAddressEdit', params: { addressId: item.id } }"
            >
              编辑地址
            </router-link>
            <p v-else class="text-xs text-slate-400">地址 ID 缺失，暂不可编辑。</p>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>
