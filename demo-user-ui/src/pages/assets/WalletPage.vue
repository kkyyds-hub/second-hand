<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { AlertCircle, CreditCard, Loader2, RefreshCw, Send, Wallet } from 'lucide-vue-next'
import {
  applyWalletWithdraw,
  createEmptyWalletTransactionPage,
  getWalletBalance,
  getWalletTransactions,
  type WalletBalance,
  type WalletTransaction,
} from '@/api/wallet'

const DEFAULT_BALANCE: WalletBalance = {
  balance: '0.00',
  balanceNumber: null,
}

const balance = ref<WalletBalance>({ ...DEFAULT_BALANCE })
const transactions = ref(createEmptyWalletTransactionPage())
const loading = ref(false)
const withdrawSubmitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const withdrawError = ref('')
const currentPage = ref(1)
const pageSize = 10
const withdrawForm = reactive({
  amount: '',
  bankCardNo: '',
})

const totalPages = computed(() => Math.max(1, Math.ceil(transactions.value.total / transactions.value.pageSize)))
const hasTransactions = computed(() => transactions.value.list.length > 0)
const canGoPrev = computed(() => currentPage.value > 1 && !loading.value)
const canGoNext = computed(() => currentPage.value < totalPages.value && !loading.value)

function formatCurrency(value: string | number | null | undefined) {
  const normalized = Number(value ?? 0)
  if (!Number.isFinite(normalized)) {
    return '¥0.00'
  }

  return `¥${normalized.toFixed(2)}`
}

function formatSignedCurrency(item: WalletTransaction) {
  const amount = item.amountNumber ?? Number(item.amount || 0)
  const prefix = amount > 0 ? '+' : ''
  return `${prefix}${formatCurrency(amount)}`
}

function getAmountTone(item: WalletTransaction) {
  const amount = item.amountNumber ?? Number(item.amount || 0)
  if (amount > 0) {
    return 'text-emerald-700'
  }

  if (amount < 0) {
    return 'text-red-600'
  }

  return 'text-gray-700'
}

function formatBizType(type: string) {
  const normalized = type.trim().toUpperCase()
  const typeMap: Record<string, string> = {
    ORDER_PAY: '订单支付',
    ORDER_REFUND: '订单退款',
    WITHDRAW: '提现',
    ADJUST: '账户调整',
  }

  return typeMap[normalized] || type || '钱包流水'
}

async function loadWallet(page = currentPage.value) {
  if (loading.value) {
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''
    currentPage.value = page
    const [nextBalance, nextTransactions] = await Promise.all([
      getWalletBalance(),
      getWalletTransactions({ page, pageSize }),
    ])
    balance.value = nextBalance
    transactions.value = nextTransactions
    currentPage.value = nextTransactions.page
  } catch (error: unknown) {
    errorMessage.value = error instanceof Error && error.message.trim() ? error.message : '钱包数据加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function submitWithdraw() {
  if (withdrawSubmitting.value) {
    return
  }

  try {
    withdrawSubmitting.value = true
    withdrawError.value = ''
    successMessage.value = ''
    const result = await applyWalletWithdraw({ ...withdrawForm })
    successMessage.value = result.requestId ? `${result.message} 申请编号：${result.requestId}` : result.message
    withdrawForm.amount = ''
    withdrawForm.bankCardNo = ''
    await loadWallet(1)
  } catch (error: unknown) {
    withdrawError.value = error instanceof Error && error.message.trim() ? error.message : '提现申请提交失败，请稍后重试。'
  } finally {
    withdrawSubmitting.value = false
  }
}

function goPrev() {
  if (canGoPrev.value) {
    loadWallet(currentPage.value - 1)
  }
}

function goNext() {
  if (canGoNext.value) {
    loadWallet(currentPage.value + 1)
  }
}

onMounted(() => {
  loadWallet(1)
})
</script>

<template>
  <div class="page-body">
    <section class="page-hero">
      <div class="page-hero-content">
        <div class="page-header-main">
          <p class="page-kicker">资产中心</p>
          <h1 class="page-title">钱包余额与流水</h1>
          <p class="page-desc">查看当前钱包余额、钱包交易流水，并提交演示版提现申请。提现只记录申请，不代表真实银行或支付渠道出金已打通。</p>
        </div>
        <div class="page-actions">
          <router-link class="btn-default" to="/assets/points">积分</router-link>
          <router-link class="btn-default" to="/assets/credit">信用</router-link>
          <button class="btn-default" type="button" :disabled="loading" @click="loadWallet(currentPage)">
            <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
            <RefreshCw v-else class="h-4 w-4" />
            <span>{{ loading ? '刷新中' : '刷新钱包' }}</span>
          </button>
        </div>
      </div>
    </section>

    <section v-if="errorMessage" class="notice-banner notice-banner-danger">
      <span class="notice-dot bg-red-500"></span>
      <div>
        <p class="font-semibold">钱包数据加载失败</p>
        <p class="mt-1 text-[12px] leading-5">{{ errorMessage }}</p>
      </div>
    </section>

    <section v-if="successMessage" class="notice-banner notice-banner-success">
      <span class="notice-dot bg-emerald-500"></span>
      <span>{{ successMessage }}</span>
    </section>

    <section class="grid gap-6 lg:grid-cols-[0.9fr_1.1fr]">
      <section class="section-panel">
        <div class="section-header">
          <div>
            <h2 class="section-heading">钱包余额</h2>
            <p class="section-subtitle">余额接口：GET /user/wallet/balance</p>
          </div>
          <Wallet class="h-5 w-5 text-gray-400" />
        </div>
        <div class="section-body">
          <p class="text-[13px] text-gray-500">当前可用余额</p>
          <p class="mt-3 font-numeric text-[34px] font-bold tracking-tight text-gray-900">{{ formatCurrency(balance.balance) }}</p>
          <div class="mt-5 rounded-2xl border border-blue-100 bg-blue-50/70 p-4 text-[12px] leading-6 text-blue-800">
            说明：Day07 仅承接用户端资产中心前端最小闭环；提现按钮提交的是后端申请记录，不写成真实金融出金链路。
          </div>
        </div>
      </section>

      <section class="section-panel">
        <div class="section-header">
          <div>
            <h2 class="section-heading">提现申请</h2>
            <p class="section-subtitle">表单字段对齐 amount / bankCardNo，失败态在页面内展示。</p>
          </div>
          <CreditCard class="h-5 w-5 text-gray-400" />
        </div>
        <form class="section-body space-y-4" @submit.prevent="submitWithdraw">
          <div>
            <label class="form-label" for="withdrawAmount">提现金额</label>
            <input id="withdrawAmount" v-model="withdrawForm.amount" class="input-standard" inputmode="decimal" placeholder="请输入提现金额，最小 0.01" />
          </div>
          <div>
            <label class="form-label" for="bankCardNo">银行卡号</label>
            <input id="bankCardNo" v-model="withdrawForm.bankCardNo" class="input-standard" maxlength="32" placeholder="请输入 4~32 位银行卡号" />
            <p class="form-helper">当前不接真实银行通道，只提交提现申请记录。</p>
          </div>
          <div v-if="withdrawError" class="notice-banner notice-banner-danger !mb-0">
            <AlertCircle class="mt-0.5 h-4 w-4 shrink-0" />
            <span>{{ withdrawError }}</span>
          </div>
          <button class="btn-primary w-full sm:w-auto" type="submit" :disabled="withdrawSubmitting">
            <Loader2 v-if="withdrawSubmitting" class="h-4 w-4 animate-spin" />
            <Send v-else class="h-4 w-4" />
            <span>{{ withdrawSubmitting ? '提交中' : '提交提现申请' }}</span>
          </button>
        </form>
      </section>
    </section>

    <section class="section-panel">
      <div class="section-header">
        <div>
          <h2 class="section-heading">钱包流水</h2>
          <p class="section-subtitle">流水接口：GET /user/wallet/transactions?page={{ currentPage }}&pageSize={{ pageSize }}</p>
        </div>
        <span class="chip chip-neutral">共 {{ transactions.total }} 条</span>
      </div>
      <div class="section-body">
        <div v-if="loading" class="empty-state">
          <Loader2 class="empty-state-icon animate-spin" />
          <p class="empty-state-title">正在加载钱包流水</p>
        </div>
        <div v-else-if="!hasTransactions" class="empty-state">
          <Wallet class="empty-state-icon" />
          <p class="empty-state-title">暂无钱包流水</p>
          <p class="empty-state-text">后续完成订单支付、退款、提现或调整后会在这里展示记录。</p>
        </div>
        <div v-else class="space-y-3">
          <article v-for="item in transactions.list" :key="item.id ?? `${item.bizType}-${item.bizId}-${item.createTime}`" class="list-card-item">
            <div class="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
              <div>
                <p class="text-[14px] font-semibold text-gray-900">{{ formatBizType(item.bizType) }}</p>
                <p class="mt-1 text-[12px] text-gray-500">{{ item.remark || '无备注' }}</p>
                <p class="mt-2 text-[11px] text-gray-400">业务 ID：{{ item.bizId ?? '-' }} · {{ item.createTime || '时间待确认' }}</p>
              </div>
              <div class="text-left md:text-right">
                <p class="font-numeric text-[18px] font-bold" :class="getAmountTone(item)">{{ formatSignedCurrency(item) }}</p>
                <p class="mt-1 text-[12px] text-gray-500">余额 {{ formatCurrency(item.balanceAfter) }}</p>
              </div>
            </div>
          </article>
        </div>
        <div class="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-gray-100 pt-4">
          <p class="text-[12px] text-gray-500">第 {{ currentPage }} / {{ totalPages }} 页</p>
          <div class="flex gap-2">
            <button class="btn-default" type="button" :disabled="!canGoPrev" @click="goPrev">上一页</button>
            <button class="btn-default" type="button" :disabled="!canGoNext" @click="goNext">下一页</button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
