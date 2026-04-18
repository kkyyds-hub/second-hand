<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Loader2, MapPinHouse, Plus, RefreshCw } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import { createEmptyAddressListResult, deleteMyAddress, getMyAddressList, setMyDefaultAddress, type UserAddressItem } from '@/api/address'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const hasLoadedOnce = ref(false)
const errorMessage = ref('')
const addressPage = ref(createEmptyAddressListResult())
const settingDefaultAddressId = ref<number | null>(null)
const deletingAddressId = ref<number | null>(null)
const defaultActionStatus = ref<'idle' | 'success' | 'error'>('idle')
const defaultActionMessage = ref('')
const deleteActionStatus = ref<'idle' | 'success' | 'error'>('idle')
const deleteActionMessage = ref('')

const addressList = computed<UserAddressItem[]>(() => addressPage.value.list)
const hasCreatedNotice = computed(() => route.query.created === '1')
const hasEditedNotice = computed(() => route.query.edited === '1')
const hasActionNotice = computed(() => hasCreatedNotice.value || hasEditedNotice.value)
const actionNoticeText = computed(() => (hasEditedNotice.value ? '地址编辑成功，列表已刷新。' : '地址新增成功，列表已刷新。'))
const hasDefaultActionNotice = computed(() => defaultActionStatus.value !== 'idle')
const hasDeleteActionNotice = computed(() => deleteActionStatus.value !== 'idle')
const hasEmptyState = computed(() => {
  return !loading.value && hasLoadedOnce.value && !errorMessage.value && addressList.value.length === 0
})

function dismissActionNotice() {
  if (!hasActionNotice.value) {
    return
  }

  const nextQuery = { ...route.query }
  delete nextQuery.created
  delete nextQuery.edited
  router.replace({ query: nextQuery })
}

function dismissDefaultActionNotice() {
  if (!hasDefaultActionNotice.value || settingDefaultAddressId.value !== null) {
    return
  }

  defaultActionStatus.value = 'idle'
  defaultActionMessage.value = ''
}

function dismissDeleteActionNotice() {
  if (!hasDeleteActionNotice.value || deletingAddressId.value !== null) {
    return
  }

  deleteActionStatus.value = 'idle'
  deleteActionMessage.value = ''
}

function readErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return '地址列表加载失败，请稍后重试。'
}

function readDefaultActionErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return '默认地址设置失败，请稍后重试。'
}

function readDeleteActionErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return '地址删除失败，请稍后重试。'
}

function isSettingDefaultFor(addressId: number | null) {
  return addressId !== null && settingDefaultAddressId.value === addressId
}

function isDeletingFor(addressId: number | null) {
  return addressId !== null && deletingAddressId.value === addressId
}

function canSetDefaultFor(item: UserAddressItem) {
  return item.id !== null && !item.isDefault && !loading.value && settingDefaultAddressId.value === null && deletingAddressId.value === null
}

function canDeleteFor(item: UserAddressItem) {
  return item.id !== null && !loading.value && deletingAddressId.value === null && settingDefaultAddressId.value === null
}

async function loadAddressList(options?: { throwOnError?: boolean }) {
  if (loading.value) {
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''
    addressPage.value = await getMyAddressList({
      page: 1,
      pageSize: 20,
    })
  } catch (error: unknown) {
    errorMessage.value = readErrorMessage(error)
    if (options?.throwOnError) {
      throw error
    }
  } finally {
    loading.value = false
    hasLoadedOnce.value = true
  }
}

function reloadAddressList() {
  return loadAddressList()
}

async function handleSetDefault(item: UserAddressItem) {
  if (!canSetDefaultFor(item) || item.id === null) {
    return
  }

  try {
    settingDefaultAddressId.value = item.id
    defaultActionStatus.value = 'idle'
    defaultActionMessage.value = ''
    await setMyDefaultAddress(item.id)
    await loadAddressList({ throwOnError: true })
    defaultActionStatus.value = 'success'
    defaultActionMessage.value = '默认地址设置成功，列表已刷新。'
  } catch (error: unknown) {
    defaultActionStatus.value = 'error'
    defaultActionMessage.value = readDefaultActionErrorMessage(error)
  } finally {
    settingDefaultAddressId.value = null
  }
}

async function handleDelete(item: UserAddressItem) {
  if (!canDeleteFor(item) || item.id === null) {
    return
  }

  try {
    deletingAddressId.value = item.id
    deleteActionStatus.value = 'idle'
    deleteActionMessage.value = ''
    await deleteMyAddress(item.id)
    await loadAddressList({ throwOnError: true })
    deleteActionStatus.value = 'success'
    deleteActionMessage.value = '地址删除成功，列表已刷新。'
  } catch (error: unknown) {
    deleteActionStatus.value = 'error'
    deleteActionMessage.value = readDeleteActionErrorMessage(error)
  } finally {
    deletingAddressId.value = null
  }
}

onMounted(() => {
  reloadAddressList()
})
</script>

<template>
  <div class="page-body page-body-narrow">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">地址</p>
          <h1 class="page-title">收货地址</h1>
          <p class="page-desc">列表、新增、编辑、删除与默认地址操作统一放在同一种壳层里，减少地址域的样式割裂。</p>
        </div>
        <div class="page-actions">
          <router-link class="btn-default" to="/account">返回账户中心</router-link>
          <button class="btn-default" type="button" :disabled="loading" @click="reloadAddressList">
            <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
            <RefreshCw v-else class="h-4 w-4" />
            <span>{{ loading ? '刷新中' : '刷新列表' }}</span>
          </button>
          <router-link class="btn-primary" to="/account/addresses/new">
            <Plus class="h-4 w-4" />
            <span>新增地址</span>
          </router-link>
        </div>
      </div>
    </section>

    <section v-if="hasActionNotice" class="notice-banner notice-banner-success">
      <span class="notice-dot bg-emerald-500"></span>
      <span class="flex-1">{{ actionNoticeText }}</span>
      <button class="text-[12px] font-medium" type="button" @click="dismissActionNotice">关闭</button>
    </section>

    <section
      v-if="hasDefaultActionNotice"
      class="notice-banner"
      :class="defaultActionStatus === 'success' ? 'notice-banner-success' : 'notice-banner-danger'"
    >
      <span class="notice-dot" :class="defaultActionStatus === 'success' ? 'bg-emerald-500' : 'bg-red-500'"></span>
      <span class="flex-1">{{ defaultActionMessage }}</span>
      <button class="text-[12px] font-medium" type="button" :disabled="settingDefaultAddressId !== null" @click="dismissDefaultActionNotice">关闭</button>
    </section>

    <section
      v-if="hasDeleteActionNotice"
      class="notice-banner"
      :class="deleteActionStatus === 'success' ? 'notice-banner-success' : 'notice-banner-danger'"
    >
      <span class="notice-dot" :class="deleteActionStatus === 'success' ? 'bg-emerald-500' : 'bg-red-500'"></span>
      <span class="flex-1">{{ deleteActionMessage }}</span>
      <button class="text-[12px] font-medium" type="button" :disabled="deletingAddressId !== null" @click="dismissDeleteActionNotice">关闭</button>
    </section>

    <section v-if="errorMessage" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <div class="flex-1">
        <p>{{ errorMessage }}</p>
        <button class="btn-default mt-3" type="button" :disabled="loading" @click="reloadAddressList">重试加载</button>
      </div>
    </section>

    <section v-else-if="loading && !hasLoadedOnce" class="section-panel">
      <div class="section-body flex min-h-[280px] items-center justify-center">
        <div class="flex items-center gap-3 text-gray-500">
          <Loader2 class="h-5 w-5 animate-spin" />
          <p class="text-[13px]">正在加载地址列表...</p>
        </div>
      </div>
    </section>

    <section v-else-if="hasEmptyState" class="section-panel">
      <div class="section-body">
        <div class="empty-state min-h-[280px]">
          <MapPinHouse class="empty-state-icon" />
          <p class="empty-state-title">暂无收货地址</p>
          <p class="empty-state-text">当前账户还没有可展示的地址记录，可以先新增一个默认地址用于后续下单。</p>
          <div class="mt-5 flex flex-wrap items-center justify-center gap-3">
            <button class="btn-default" type="button" :disabled="loading" @click="reloadAddressList">重新加载</button>
            <router-link class="btn-primary" to="/account/addresses/new">
              <Plus class="h-4 w-4" />
              <span>新增地址</span>
            </router-link>
          </div>
        </div>
      </div>
    </section>

    <section v-else class="section-panel">
      <div class="section-header">
        <div>
          <h2 class="section-heading">地址列表</h2>
          <p class="section-subtitle">共 <span class="font-numeric text-gray-800">{{ addressPage.total }}</span> 条记录</p>
        </div>
        <span class="chip chip-muted font-numeric">第 {{ addressPage.page }} 页 · 每页 {{ addressPage.pageSize }} 条</span>
      </div>

      <div class="section-body bg-gray-50/50">
        <div class="grid gap-4">
          <article
            v-for="item in addressList"
            :key="item.id ?? `${item.receiverName}-${item.mobile}-${item.fullAddress}`"
            class="list-card-item"
          >
            <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <span class="text-[16px] font-semibold text-gray-900">{{ item.receiverName || '未命名收件人' }}</span>
                  <span class="text-[14px] font-numeric text-gray-600">{{ item.mobile || '未绑定手机号' }}</span>
                  <span v-if="item.isDefault" class="chip chip-success">默认地址</span>
                </div>
                <p class="mt-3 text-[13px] leading-6 text-gray-700">{{ item.fullAddress || '地址信息缺失' }}</p>
                <p class="mt-2 text-[12px] font-numeric text-gray-400">地址 ID：{{ item.id ?? '-' }}</p>
              </div>

              <div class="flex flex-wrap items-center gap-2 border-t border-gray-100 pt-4 md:border-0 md:pt-0">
                <template v-if="item.id !== null">
                  <button
                    v-if="!item.isDefault"
                    class="btn-default !h-9 px-3"
                    type="button"
                    :disabled="!canSetDefaultFor(item)"
                    @click="handleSetDefault(item)"
                  >
                    <Loader2 v-if="isSettingDefaultFor(item.id)" class="h-3.5 w-3.5 animate-spin" />
                    <span>设为默认</span>
                  </button>

                  <router-link class="btn-default !h-9 px-3" :to="{ name: 'AccountAddressEdit', params: { addressId: item.id } }">
                    编辑
                  </router-link>

                  <button class="btn-danger !h-9 px-3" type="button" :disabled="!canDeleteFor(item)" @click="handleDelete(item)">
                    <Loader2 v-if="isDeletingFor(item.id)" class="h-3.5 w-3.5 animate-spin" />
                    <span>删除</span>
                  </button>
                </template>
                <p v-else class="text-[12px] text-gray-400">ID 缺失，不可操作</p>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>
  </div>
</template>
