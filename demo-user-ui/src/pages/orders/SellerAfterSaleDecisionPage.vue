<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Loader2 } from 'lucide-vue-next'
import { submitSellerAfterSaleDecision } from '@/api/afterSales'
import { getUserDisplayName, isSellerUser, readCurrentUser } from '@/utils/request'

const route = useRoute()
const currentUser = readCurrentUser()

const sellerEnabled = computed(() => isSellerUser(currentUser))
const sellerName = computed(() => getUserDisplayName(currentUser))

const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const form = reactive({
  afterSaleId: '',
  decision: '',
  remark: '',
})

function readPositiveInt(value: unknown) {
  if (typeof value === 'number' && Number.isInteger(value) && value > 0) {
    return value
  }

  if (typeof value === 'string') {
    const normalized = value.trim()
    if (/^\d+$/.test(normalized)) {
      const parsed = Number(normalized)
      if (parsed > 0) {
        return parsed
      }
    }
  }

  return null
}

function readQueryAfterSaleId(value: unknown) {
  if (Array.isArray(value)) {
    return readQueryAfterSaleId(value[0])
  }

  if (typeof value === 'number' && Number.isFinite(value)) {
    return String(Math.trunc(value))
  }

  if (typeof value === 'string') {
    return value.trim()
  }

  return ''
}

const normalizedAfterSaleId = computed(() => readPositiveInt(form.afterSaleId))
const approvedValue = computed<boolean | null>(() => {
  if (form.decision === 'approve') {
    return true
  }

  if (form.decision === 'reject') {
    return false
  }

  return null
})
const trimmedRemark = computed(() => form.remark.trim())
const queryAfterSaleId = computed(() => readQueryAfterSaleId(route.query.afterSaleId))

const validationError = computed(() => {
  if (!sellerEnabled.value) {
    return '当前账号的本地 session 快照未显示卖家身份，前端先不开放 seller decision 提交。'
  }

  if (normalizedAfterSaleId.value === null) {
    return '请输入有效的 afterSaleId。'
  }

  if (approvedValue.value === null) {
    return '请选择同意或拒绝。'
  }

  if (trimmedRemark.value.length > 200) {
    return '处理备注长度不能超过 200 个字符。'
  }

  return ''
})

const canSubmit = computed(() => !submitting.value && !validationError.value)
const helperText = computed(() => {
  if (submitting.value) {
    return '正在提交卖家处理，请稍候。'
  }

  if (validationError.value) {
    return validationError.value
  }

  return '当前页面不补售后查询接口，也不从订单详情猜 afterSaleId；请手动输入，或通过 URL query 预填。'
})

function syncAfterSaleIdFromQuery(value: unknown) {
  const prefilledId = readQueryAfterSaleId(value)
  if (prefilledId) {
    form.afterSaleId = prefilledId
  }
}

function clearMessages() {
  if (submitting.value) {
    return
  }

  errorMessage.value = ''
  successMessage.value = ''
}

async function submitDecision() {
  if (submitting.value) {
    return
  }

  if (validationError.value || normalizedAfterSaleId.value === null || approvedValue.value === null) {
    errorMessage.value = validationError.value || '卖家处理参数无效，请检查后重试。'
    successMessage.value = ''
    return
  }

  try {
    submitting.value = true
    errorMessage.value = ''
    successMessage.value = ''

    successMessage.value = await submitSellerAfterSaleDecision(normalizedAfterSaleId.value, {
      approved: approvedValue.value,
      remark: trimmedRemark.value,
    })
  } catch (error: unknown) {
    if (error instanceof Error && error.message.trim()) {
      errorMessage.value = error.message
    } else {
      errorMessage.value = '卖家处理售后失败，请稍后重试。'
    }
    successMessage.value = ''
  } finally {
    submitting.value = false
  }
}

watch(
  () => route.query.afterSaleId,
  (value) => {
    syncAfterSaleIdFromQuery(value)
  },
  { immediate: true },
)
</script>

<template>
  <div class="page-body">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">卖家售后</p>
          <h1 class="page-title">seller decision</h1>
          <p class="page-desc">
            {{ sellerName }}，这里提供 Day06 Package-3 的最小卖家售后处理入口：手动输入 afterSaleId，选择同意或拒绝后提交。
          </p>
        </div>
        <div class="page-actions">
          <router-link class="btn-default" to="/seller">返回卖家工作台</router-link>
          <router-link class="btn-default" to="/orders/seller">查看卖家订单</router-link>
        </div>
      </div>
    </section>

    <section v-if="!sellerEnabled" class="notice-banner notice-banner-warning">
      <span class="notice-dot bg-orange-500"></span>
      <span>当前账号的本地 session 快照未显示卖家身份，因此本页仅保留说明态；运行态权限仍以后端 seller decision 接口校验为准。</span>
    </section>

    <section class="section-panel">
      <div class="section-header">
        <div>
          <h2 class="section-heading">处理边界</h2>
          <p class="section-subtitle">本包只落 seller decision，不补售后查询接口、不猜 afterSaleId 来源，也不在本轮做 runtime verify。</p>
        </div>
      </div>
      <div class="section-body">
        <div class="grid gap-3 md:grid-cols-3">
          <article class="submetric-card">
            <p class="text-[12px] text-gray-500">afterSaleId 来源</p>
            <p class="mt-2 text-[14px] font-medium text-gray-900">手动输入 / URL query 预填</p>
          </article>
          <article class="submetric-card">
            <p class="text-[12px] text-gray-500">请求契约</p>
            <p class="mt-2 text-[14px] font-medium text-gray-900">PUT seller-decision + approved / remark</p>
          </article>
          <article class="submetric-card">
            <p class="text-[12px] text-gray-500">本轮不做</p>
            <p class="mt-2 text-[14px] font-medium text-gray-900">runtime verify / final acceptance / 后端改造</p>
          </article>
        </div>
      </div>
    </section>

    <section class="section-panel">
      <div class="section-header">
        <div>
          <div class="flex flex-wrap items-center gap-2">
            <h2 class="section-heading">卖家处理售后</h2>
            <span v-if="queryAfterSaleId" class="chip chip-neutral font-numeric">query afterSaleId {{ queryAfterSaleId }}</span>
          </div>
          <p class="section-subtitle">支持形如 <code>/orders/seller/after-sales/decision?afterSaleId=123</code> 的 URL 预填。</p>
        </div>
      </div>
      <div class="section-body space-y-4">
        <div v-if="errorMessage" class="notice-banner notice-banner-danger">
          <span class="notice-dot bg-red-500"></span>
          <span>{{ errorMessage }}</span>
        </div>

        <div v-if="successMessage" class="notice-banner notice-banner-success">
          <span class="notice-dot bg-emerald-500"></span>
          <span>{{ successMessage }}</span>
        </div>

        <div class="grid gap-4 lg:grid-cols-[240px_minmax(0,1fr)]">
          <div>
            <label class="form-label" for="seller-decision-after-sale-id">afterSaleId</label>
            <input
              id="seller-decision-after-sale-id"
              v-model="form.afterSaleId"
              class="input-standard"
              type="text"
              inputmode="numeric"
              maxlength="20"
              placeholder="请输入售后单 ID"
              :disabled="submitting"
              @input="clearMessages"
            />
          </div>

          <div>
            <label class="form-label" for="seller-decision-remark">处理备注</label>
            <input
              id="seller-decision-remark"
              v-model="form.remark"
              class="input-standard"
              type="text"
              maxlength="200"
              placeholder="可选，最多 200 个字符"
              :disabled="submitting"
              @input="clearMessages"
            />
          </div>
        </div>

        <div class="space-y-2">
          <p class="form-label">处理结果</p>
          <div class="flex flex-wrap gap-3">
            <label class="inline-flex items-center gap-2 rounded-full border border-gray-200 px-4 py-2 text-[13px] text-gray-700">
              <input v-model="form.decision" type="radio" value="approve" :disabled="submitting" @change="clearMessages" />
              <span>同意退货退款</span>
            </label>
            <label class="inline-flex items-center gap-2 rounded-full border border-gray-200 px-4 py-2 text-[13px] text-gray-700">
              <input v-model="form.decision" type="radio" value="reject" :disabled="submitting" @change="clearMessages" />
              <span>拒绝退货退款</span>
            </label>
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-3">
          <button class="btn-primary" type="button" :disabled="!canSubmit" @click="submitDecision">
            <Loader2 v-if="submitting" class="h-4 w-4 animate-spin" />
            <span>{{ submitting ? '提交中...' : '提交 seller decision' }}</span>
          </button>
          <span class="chip chip-muted">
            {{ approvedValue === null ? '待选择结果' : approvedValue ? '当前选择：同意' : '当前选择：拒绝' }}
          </span>
        </div>

        <p class="form-helper">{{ helperText }}</p>
      </div>
    </section>
  </div>
</template>
