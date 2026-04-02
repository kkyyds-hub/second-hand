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
  <div class="space-y-6">
    <section class="card p-6 md:p-8">
      <p class="muted-kicker">Address center</p>
      <div class="mt-3 flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <h1 class="section-title">编辑收货地址</h1>
          <p class="section-desc">地址编辑仅覆盖 update 最小链路，成功后返回地址列表并触发刷新展示。</p>
        </div>

        <router-link class="btn-default gap-2" to="/account/addresses">
          <ArrowLeft class="h-4 w-4" />
          <span>返回地址列表</span>
        </router-link>
      </div>
    </section>

    <section v-if="loadErrorMessage" class="rounded-2xl border border-orange-200 bg-orange-50 px-5 py-4 text-sm text-orange-700">
      <p>{{ loadErrorMessage }}</p>
      <div class="mt-3 flex items-center gap-2">
        <button class="btn-default" type="button" :disabled="loadingDetail" @click="loadAddressDetail">重试加载</button>
        <router-link class="btn-default" to="/account/addresses">返回列表</router-link>
      </div>
    </section>

    <section v-else-if="loadingDetail" class="card p-8">
      <div class="flex items-center gap-3 text-slate-600">
        <Loader2 class="h-5 w-5 animate-spin" />
        <p class="text-sm">正在加载地址详情...</p>
      </div>
    </section>

    <section v-else class="card p-6">
      <form class="space-y-5" @submit.prevent="submitEditForm">
        <div class="grid gap-5 md:grid-cols-2">
          <div>
            <label class="form-label" for="address-edit-receiver-name">收件人姓名 *</label>
            <input
              id="address-edit-receiver-name"
              v-model="addressForm.receiverName"
              class="input-standard"
              type="text"
              maxlength="50"
              placeholder="请输入收件人姓名"
              :disabled="submitting"
              @input="clearSubmitStatus"
            />
            <p v-if="validationErrors.receiverName" class="mt-1.5 text-xs text-orange-600">{{ validationErrors.receiverName }}</p>
          </div>

          <div>
            <label class="form-label" for="address-edit-mobile">手机号 *</label>
            <input
              id="address-edit-mobile"
              v-model="addressForm.mobile"
              class="input-standard"
              type="tel"
              maxlength="11"
              placeholder="请输入收件人手机号"
              :disabled="submitting"
              @input="clearSubmitStatus"
            />
            <p v-if="validationErrors.mobile" class="mt-1.5 text-xs text-orange-600">{{ validationErrors.mobile }}</p>
          </div>
        </div>

        <div class="grid gap-5 md:grid-cols-2">
          <div>
            <label class="form-label" for="address-edit-province-code">省份编码 *</label>
            <input
              id="address-edit-province-code"
              v-model="addressForm.provinceCode"
              class="input-standard"
              type="text"
              maxlength="20"
              placeholder="例如：310000"
              :disabled="submitting"
              @input="clearSubmitStatus"
            />
            <p v-if="validationErrors.provinceCode" class="mt-1.5 text-xs text-orange-600">{{ validationErrors.provinceCode }}</p>
          </div>

          <div>
            <label class="form-label" for="address-edit-province-name">省份名称 *</label>
            <input
              id="address-edit-province-name"
              v-model="addressForm.provinceName"
              class="input-standard"
              type="text"
              maxlength="30"
              placeholder="例如：上海市"
              :disabled="submitting"
              @input="clearSubmitStatus"
            />
            <p v-if="validationErrors.provinceName" class="mt-1.5 text-xs text-orange-600">{{ validationErrors.provinceName }}</p>
          </div>
        </div>

        <div class="grid gap-5 md:grid-cols-2">
          <div>
            <label class="form-label" for="address-edit-city-code">城市编码 *</label>
            <input
              id="address-edit-city-code"
              v-model="addressForm.cityCode"
              class="input-standard"
              type="text"
              maxlength="20"
              placeholder="例如：310100"
              :disabled="submitting"
              @input="clearSubmitStatus"
            />
            <p v-if="validationErrors.cityCode" class="mt-1.5 text-xs text-orange-600">{{ validationErrors.cityCode }}</p>
          </div>

          <div>
            <label class="form-label" for="address-edit-city-name">城市名称 *</label>
            <input
              id="address-edit-city-name"
              v-model="addressForm.cityName"
              class="input-standard"
              type="text"
              maxlength="30"
              placeholder="例如：上海市"
              :disabled="submitting"
              @input="clearSubmitStatus"
            />
            <p v-if="validationErrors.cityName" class="mt-1.5 text-xs text-orange-600">{{ validationErrors.cityName }}</p>
          </div>
        </div>

        <div class="grid gap-5 md:grid-cols-2">
          <div>
            <label class="form-label" for="address-edit-district-code">区县编码 *</label>
            <input
              id="address-edit-district-code"
              v-model="addressForm.districtCode"
              class="input-standard"
              type="text"
              maxlength="20"
              placeholder="例如：310115"
              :disabled="submitting"
              @input="clearSubmitStatus"
            />
            <p v-if="validationErrors.districtCode" class="mt-1.5 text-xs text-orange-600">{{ validationErrors.districtCode }}</p>
          </div>

          <div>
            <label class="form-label" for="address-edit-district-name">区县名称 *</label>
            <input
              id="address-edit-district-name"
              v-model="addressForm.districtName"
              class="input-standard"
              type="text"
              maxlength="30"
              placeholder="例如：浦东新区"
              :disabled="submitting"
              @input="clearSubmitStatus"
            />
            <p v-if="validationErrors.districtName" class="mt-1.5 text-xs text-orange-600">{{ validationErrors.districtName }}</p>
          </div>
        </div>

        <div>
          <label class="form-label" for="address-edit-detail-address">详细地址 *</label>
          <textarea
            id="address-edit-detail-address"
            v-model="addressForm.detailAddress"
            class="input-standard min-h-[120px] resize-y"
            rows="4"
            maxlength="200"
            placeholder="请输入详细地址"
            :disabled="submitting"
            @input="clearSubmitStatus"
          />
          <p v-if="validationErrors.detailAddress" class="mt-1.5 text-xs text-orange-600">{{ validationErrors.detailAddress }}</p>
        </div>

        <div>
          <label class="inline-flex items-center gap-2 text-sm text-slate-700">
            <input
              v-model="addressForm.isDefault"
              class="h-4 w-4 rounded border-slate-300 text-slate-900 focus:ring-slate-200"
              type="checkbox"
              :disabled="submitting"
              @change="clearSubmitStatus"
            />
            <span>设为默认地址</span>
          </label>
        </div>

        <section
          v-if="submitStatus === 'success'"
          class="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700"
        >
          {{ submitMessage }}
        </section>
        <section
          v-else-if="submitStatus === 'error'"
          class="rounded-2xl border border-orange-200 bg-orange-50 px-4 py-3 text-sm text-orange-700"
        >
          {{ submitMessage }}
        </section>

        <div class="flex flex-wrap items-center gap-3">
          <button class="btn-primary gap-2" type="submit" :disabled="!canSubmit">
            <Loader2 v-if="submitting" class="h-4 w-4 animate-spin" />
            <span>{{ submitting ? '提交中...' : '提交地址编辑' }}</span>
          </button>
          <router-link class="btn-default" to="/account/addresses">取消</router-link>
        </div>
      </form>
    </section>
  </div>
</template>
