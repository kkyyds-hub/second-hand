<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Loader2 } from 'lucide-vue-next'
import { createMyAddress } from '@/api/address'
import {
  collectAddressValidationErrors,
  createEmptyAddressFormModel,
  normalizeAddressFormModel,
  type AddressFormField,
  type AddressFormModel,
} from '@/pages/address-form'

const router = useRouter()
const submitting = ref(false)
const submitStatus = ref<'idle' | 'success' | 'error'>('idle')
const submitMessage = ref('')

const addressForm = reactive<AddressFormModel>(createEmptyAddressFormModel())

const normalizedForm = computed(() => normalizeAddressFormModel(addressForm))

const validationErrors = computed<Partial<Record<AddressFormField, string>>>(() => {
  return collectAddressValidationErrors(normalizedForm.value)
})

const hasValidationErrors = computed(() => Object.keys(validationErrors.value).length > 0)
const canSubmit = computed(() => !submitting.value && !hasValidationErrors.value)

function clearSubmitStatus() {
  if (submitting.value || submitStatus.value === 'idle') {
    return
  }

  submitStatus.value = 'idle'
  submitMessage.value = ''
}

function readErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return '地址新增失败，请稍后重试。'
}

async function submitCreateForm() {
  if (submitting.value) {
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

    /**
     * 页面层只负责 submit/loading/error 交互态；
     * AddressDTO 映射与参数归一化统一下沉到 `src/api/address.ts`。
     */
    await createMyAddress(normalizedForm.value)

    submitStatus.value = 'success'
    submitMessage.value = '地址新增成功，正在返回地址列表...'
    await router.replace({
      name: 'AccountAddressList',
      query: { created: '1' },
    })
  } catch (error: unknown) {
    submitStatus.value = 'error'
    submitMessage.value = readErrorMessage(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="space-y-6">
    <section class="card p-6 md:p-8">
      <p class="muted-kicker">Address center</p>
      <div class="mt-3 flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <h1 class="section-title">新增收货地址</h1>
          <p class="section-desc">填写 AddressController 所需字段并提交，成功后会返回地址列表页。</p>
        </div>

        <router-link class="btn-default gap-2" to="/account/addresses">
          <ArrowLeft class="h-4 w-4" />
          <span>返回地址列表</span>
        </router-link>
      </div>
    </section>

    <section class="card p-6">
      <form class="space-y-5" @submit.prevent="submitCreateForm">
        <div class="grid gap-5 md:grid-cols-2">
          <div>
            <label class="form-label" for="address-receiver-name">收件人姓名 *</label>
            <input
              id="address-receiver-name"
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
            <label class="form-label" for="address-mobile">手机号 *</label>
            <input
              id="address-mobile"
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
            <label class="form-label" for="address-province-code">省份编码 *</label>
            <input
              id="address-province-code"
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
            <label class="form-label" for="address-province-name">省份名称 *</label>
            <input
              id="address-province-name"
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
            <label class="form-label" for="address-city-code">城市编码 *</label>
            <input
              id="address-city-code"
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
            <label class="form-label" for="address-city-name">城市名称 *</label>
            <input
              id="address-city-name"
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
            <label class="form-label" for="address-district-code">区县编码 *</label>
            <input
              id="address-district-code"
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
            <label class="form-label" for="address-district-name">区县名称 *</label>
            <input
              id="address-district-name"
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
          <label class="form-label" for="address-detail-address">详细地址 *</label>
          <textarea
            id="address-detail-address"
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
            <span>{{ submitting ? '提交中...' : '提交新增地址' }}</span>
          </button>
          <router-link class="btn-default" to="/account/addresses">取消</router-link>
        </div>
      </form>
    </section>
  </div>
</template>
