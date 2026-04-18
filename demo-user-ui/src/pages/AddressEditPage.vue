<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Loader2 } from 'lucide-vue-next'
import { getMyAddressDetail, updateMyAddress } from '@/api/address'
import {
  collectAddressValidationErrors,
  createEmptyAddressFormModel,
  normalizeAddressFormModel,
  toAddressFormModel,
  type AddressFormField,
  type AddressFormModel,
} from '@/pages/address-form'

const route = useRoute()
const router = useRouter()

const loadingDetail = ref(true)
const loadErrorMessage = ref('')
const submitting = ref(false)
const submitStatus = ref<'idle' | 'success' | 'error'>('idle')
const submitMessage = ref('')
const addressForm = reactive<AddressFormModel>(createEmptyAddressFormModel())

function readRouteAddressId(value: unknown) {
  if (typeof value === 'number' && Number.isInteger(value) && value > 0) {
    return value
  }

  if (typeof value === 'string') {
    const normalized = value.trim()
    if (/^\d+$/.test(normalized)) {
      const parsed = Number(normalized)
      if (Number.isInteger(parsed) && parsed > 0) {
        return parsed
      }
    }
  }

  return null
}

const addressId = computed(() => readRouteAddressId(route.params.addressId))
const normalizedForm = computed(() => normalizeAddressFormModel(addressForm))

const validationErrors = computed<Partial<Record<AddressFormField, string>>>(() => {
  return collectAddressValidationErrors(normalizedForm.value)
})

const hasValidationErrors = computed(() => Object.keys(validationErrors.value).length > 0)
const canSubmit = computed(() => {
  return !loadingDetail.value && !submitting.value && !hasValidationErrors.value && !loadErrorMessage.value
})

function clearSubmitStatus() {
  if (submitting.value || submitStatus.value === 'idle') {
    return
  }

  submitStatus.value = 'idle'
  submitMessage.value = ''
}

function readLoadErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return '地址详情加载失败，请稍后重试。'
}

function readSubmitErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return '地址编辑失败，请稍后重试。'
}

function fillAddressForm() {
  const normalized = toAddressFormModel(createEmptyAddressFormModel())
  Object.assign(addressForm, normalized)
}

function fillAddressFormFromDetail(payload: ReturnType<typeof toAddressFormModel>) {
  Object.assign(addressForm, payload)
}

async function loadAddressDetail() {
  const currentAddressId = addressId.value
  if (!currentAddressId) {
    loadingDetail.value = false
    loadErrorMessage.value = '地址 ID 无效，无法进入编辑页。'
    return
  }

  try {
    loadingDetail.value = true
    loadErrorMessage.value = ''
    fillAddressForm()
    clearSubmitStatus()

    /**
     * 页面层只负责“加载态/错误态/提交态”；
     * 详情接口字段兼容和结构归一化统一收敛在 `src/api/address.ts`。
     */
    const detail = await getMyAddressDetail(currentAddressId)
    fillAddressFormFromDetail(toAddressFormModel(detail))
  } catch (error: unknown) {
    loadErrorMessage.value = readLoadErrorMessage(error)
  } finally {
    loadingDetail.value = false
  }
}

async function submitEditForm() {
  if (submitting.value || loadingDetail.value) {
    return
  }

  const currentAddressId = addressId.value
  if (!currentAddressId) {
    submitStatus.value = 'error'
    submitMessage.value = '地址 ID 无效，无法提交编辑。'
    return
  }

  if (hasValidationErrors.value) {
    submitStatus.value = 'error'
    submitMessage.value = '请先补全所有必填字段后再提交。'
    return
  }

  try {
    submitting.value = true
    submitStatus.value = 'idle'
    submitMessage.value = ''

    await updateMyAddress(currentAddressId, normalizedForm.value)

    submitStatus.value = 'success'
    submitMessage.value = '地址编辑成功，正在返回地址列表...'
    await router.replace({
      name: 'AccountAddressList',
      query: { edited: '1' },
    })
  } catch (error: unknown) {
    submitStatus.value = 'error'
    submitMessage.value = readSubmitErrorMessage(error)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadAddressDetail()
})
</script>

<template>
  <div class="page-body page-body-narrow">
    <section class="page-header"><div class="page-header-main"><p class="page-kicker">地址</p><h1 class="page-title">编辑收货地址</h1><p class="page-desc">编辑页与新增页保持完全一致的表单节奏，只在顶部状态与提交链路上区分。</p></div><div class="page-actions"><router-link class="btn-default" to="/account/addresses"><ArrowLeft class="h-4 w-4" /><span>返回列表</span></router-link></div></section>
    <section v-if="loadErrorMessage" class="notice-banner notice-banner-danger"><span class="notice-dot bg-red-500"></span><div class="flex-1"><p>{{ loadErrorMessage }}</p><div class="mt-3 flex flex-wrap items-center gap-2"><button class="btn-default" type="button" :disabled="loadingDetail" @click="loadAddressDetail">重试加载</button><router-link class="btn-default" to="/account/addresses">返回列表</router-link></div></div></section>
    <section v-else-if="loadingDetail" class="section-panel"><div class="section-body flex min-h-[280px] items-center justify-center"><div class="flex items-center gap-3 text-gray-500"><Loader2 class="h-5 w-5 animate-spin" /><p class="text-[13px]">正在加载地址详情...</p></div></div></section>
    <form v-else class="space-y-6" @submit.prevent="submitEditForm">
      <section class="section-panel"><div class="section-header section-header-plain"><div><h2 class="section-heading">基础信息</h2></div></div><div class="section-body pt-0 grid gap-5 md:grid-cols-2"><div><label class="form-label" for="address-edit-receiver-name">收件人姓名 *</label><input id="address-edit-receiver-name" v-model="addressForm.receiverName" class="input-standard" type="text" maxlength="50" placeholder="请输入收件人姓名" :disabled="submitting" @input="clearSubmitStatus" /><p v-if="validationErrors.receiverName" class="form-helper !text-red-500">{{ validationErrors.receiverName }}</p></div><div><label class="form-label" for="address-edit-mobile">手机号 *</label><input id="address-edit-mobile" v-model="addressForm.mobile" class="input-standard" type="tel" maxlength="11" placeholder="请输入手机号" :disabled="submitting" @input="clearSubmitStatus" /><p v-if="validationErrors.mobile" class="form-helper !text-red-500">{{ validationErrors.mobile }}</p></div></div></section>
      <section class="section-panel"><div class="section-header section-header-plain"><div><h2 class="section-heading">地区信息</h2></div></div><div class="section-body pt-0 space-y-5"><div class="grid gap-5 md:grid-cols-2"><div><label class="form-label" for="address-edit-province-code">省份编码 *</label><input id="address-edit-province-code" v-model="addressForm.provinceCode" class="input-standard" type="text" maxlength="20" placeholder="例如 310000" :disabled="submitting" @input="clearSubmitStatus" /><p v-if="validationErrors.provinceCode" class="form-helper !text-red-500">{{ validationErrors.provinceCode }}</p></div><div><label class="form-label" for="address-edit-province-name">省份名称 *</label><input id="address-edit-province-name" v-model="addressForm.provinceName" class="input-standard" type="text" maxlength="30" placeholder="例如 上海市" :disabled="submitting" @input="clearSubmitStatus" /><p v-if="validationErrors.provinceName" class="form-helper !text-red-500">{{ validationErrors.provinceName }}</p></div></div><div class="grid gap-5 md:grid-cols-2"><div><label class="form-label" for="address-edit-city-code">城市编码 *</label><input id="address-edit-city-code" v-model="addressForm.cityCode" class="input-standard" type="text" maxlength="20" placeholder="例如 310100" :disabled="submitting" @input="clearSubmitStatus" /><p v-if="validationErrors.cityCode" class="form-helper !text-red-500">{{ validationErrors.cityCode }}</p></div><div><label class="form-label" for="address-edit-city-name">城市名称 *</label><input id="address-edit-city-name" v-model="addressForm.cityName" class="input-standard" type="text" maxlength="30" placeholder="例如 上海市" :disabled="submitting" @input="clearSubmitStatus" /><p v-if="validationErrors.cityName" class="form-helper !text-red-500">{{ validationErrors.cityName }}</p></div></div><div class="grid gap-5 md:grid-cols-2"><div><label class="form-label" for="address-edit-district-code">区县编码 *</label><input id="address-edit-district-code" v-model="addressForm.districtCode" class="input-standard" type="text" maxlength="20" placeholder="例如 310115" :disabled="submitting" @input="clearSubmitStatus" /><p v-if="validationErrors.districtCode" class="form-helper !text-red-500">{{ validationErrors.districtCode }}</p></div><div><label class="form-label" for="address-edit-district-name">区县名称 *</label><input id="address-edit-district-name" v-model="addressForm.districtName" class="input-standard" type="text" maxlength="30" placeholder="例如 浦东新区" :disabled="submitting" @input="clearSubmitStatus" /><p v-if="validationErrors.districtName" class="form-helper !text-red-500">{{ validationErrors.districtName }}</p></div></div></div></section>
      <section class="section-panel"><div class="section-header section-header-plain"><div><h2 class="section-heading">详细设置</h2></div></div><div class="section-body pt-0 space-y-5"><div><label class="form-label" for="address-edit-detail-address">详细地址 *</label><textarea id="address-edit-detail-address" v-model="addressForm.detailAddress" class="input-standard min-h-[120px] resize-y" rows="4" maxlength="200" placeholder="请输入详细地址" :disabled="submitting" @input="clearSubmitStatus" /><p v-if="validationErrors.detailAddress" class="form-helper !text-red-500">{{ validationErrors.detailAddress }}</p></div><label class="inline-flex items-center gap-2 text-[13px] font-medium text-gray-700"><input v-model="addressForm.isDefault" class="checkbox-standard" type="checkbox" :disabled="submitting" @change="clearSubmitStatus" /><span>设为默认地址</span></label></div></section>
      <section class="space-y-4"><div v-if="submitStatus === 'success'" class="notice-banner notice-banner-success"><span class="notice-dot bg-emerald-500"></span><span>{{ submitMessage }}</span></div><div v-else-if="submitStatus === 'error'" class="notice-banner notice-banner-danger"><span class="notice-dot bg-red-500"></span><span>{{ submitMessage }}</span></div><div class="flex flex-wrap items-center gap-3"><button class="btn-primary" type="submit" :disabled="!canSubmit"><Loader2 v-if="submitting" class="h-4 w-4 animate-spin" /><span>{{ submitting ? '提交中...' : '提交地址编辑' }}</span></button><router-link class="btn-default" to="/account/addresses">取消返回</router-link></div></section>
    </form>
  </div>
</template>
