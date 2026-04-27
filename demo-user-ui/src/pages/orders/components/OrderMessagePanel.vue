<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CheckCheck, Loader2, MessageSquareText, RefreshCw, Send } from 'lucide-vue-next'
import {
  createEmptyOrderMessagePage,
  getOrderMessageList,
  markOrderMessagesAsRead,
  readSendOrderMessageValidationError,
  sendOrderMessage,
} from '@/api/messages'

const props = withDefaults(defineProps<{
  orderId: number | null
  currentUserId: number | null
  counterpartUserId: number | null
  counterpartLabel?: string
}>(), {
  counterpartLabel: '对方',
})

const loading = ref(false)
const hasLoadedOnce = ref(false)
const errorMessage = ref('')
const actionErrorMessage = ref('')
const actionSuccessMessage = ref('')
const pageData = ref(createEmptyOrderMessagePage())
const draftMessage = ref('')
const sendSubmitting = ref(false)
const markReadSubmitting = ref(false)

const messages = computed(() => pageData.value.list)
const unreadIncomingCount = computed(() => {
  if (props.currentUserId === null) {
    return 0
  }

  return messages.value.filter((item) => item.toUserId === props.currentUserId && !item.read).length
})
const hasEmptyState = computed(() => !loading.value && hasLoadedOnce.value && !errorMessage.value && messages.value.length === 0)
const sendValidationError = computed(() => readSendOrderMessageValidationError({
  toUserId: props.counterpartUserId ?? undefined,
  content: draftMessage.value,
}))
const canSubmitSend = computed(() => {
  return Boolean(
    props.orderId !== null
      && props.currentUserId !== null
      && props.counterpartUserId !== null
      && !loading.value
      && !sendSubmitting.value
      && !markReadSubmitting.value
      && !sendValidationError.value,
  )
})
const canMarkRead = computed(() => {
  return Boolean(
    props.orderId !== null
      && unreadIncomingCount.value > 0
      && !loading.value
      && !sendSubmitting.value
      && !markReadSubmitting.value,
  )
})

function readErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return fallback
}

function isOutgoingMessage(fromUserId: number | null) {
  return props.currentUserId !== null && fromUserId === props.currentUserId
}

function readMessageCardClass(fromUserId: number | null) {
  if (isOutgoingMessage(fromUserId)) {
    return 'rounded-2xl border border-blue-100 bg-blue-50/80 px-4 py-3'
  }

  return 'rounded-2xl border border-gray-100 bg-gray-50/80 px-4 py-3'
}

function readMessageAuthor(fromUserId: number | null) {
  return isOutgoingMessage(fromUserId) ? '我' : props.counterpartLabel
}

function readMessageStatusText(fromUserId: number | null, read: boolean) {
  if (isOutgoingMessage(fromUserId)) {
    return read ? '对方已读' : '对方未读'
  }

  return read ? '已读' : '未读'
}

function clearActionMessages() {
  if (sendSubmitting.value || markReadSubmitting.value) {
    return
  }

  actionErrorMessage.value = ''
  actionSuccessMessage.value = ''
}

async function loadMessages() {
  if (loading.value || props.orderId === null) {
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''

    /**
     * Day06 Package-2 先只做“单订单内最近会话”的最小切片。
     * 列表、发送、已读回执先落地，是否要扩展成完整消息中心留给后续 Day08/Day09。
     */
    pageData.value = await getOrderMessageList(props.orderId, { page: 1, pageSize: 50 })
  } catch (error: unknown) {
    pageData.value = createEmptyOrderMessagePage()
    errorMessage.value = readErrorMessage(error, '订单会话加载失败，请稍后重试。')
  } finally {
    loading.value = false
    hasLoadedOnce.value = true
  }
}

async function submitSend() {
  if (!canSubmitSend.value || props.orderId === null || props.counterpartUserId === null) {
    return
  }

  try {
    sendSubmitting.value = true
    actionErrorMessage.value = ''
    actionSuccessMessage.value = ''

    await sendOrderMessage(props.orderId, {
      toUserId: props.counterpartUserId,
      content: draftMessage.value,
    })

    draftMessage.value = ''
    actionSuccessMessage.value = '订单消息已发送。'
    await loadMessages()
  } catch (error: unknown) {
    actionErrorMessage.value = readErrorMessage(error, '订单消息发送失败，请稍后重试。')
  } finally {
    sendSubmitting.value = false
  }
}

async function submitMarkRead() {
  if (!canMarkRead.value || props.orderId === null) {
    return
  }

  try {
    markReadSubmitting.value = true
    actionErrorMessage.value = ''
    actionSuccessMessage.value = ''

    actionSuccessMessage.value = await markOrderMessagesAsRead(props.orderId)
    await loadMessages()
  } catch (error: unknown) {
    actionErrorMessage.value = readErrorMessage(error, '订单会话已读回执失败，请稍后重试。')
  } finally {
    markReadSubmitting.value = false
  }
}

watch(
  () => [props.orderId, props.currentUserId, props.counterpartUserId] as const,
  ([orderId, currentUserId, counterpartUserId]) => {
    pageData.value = createEmptyOrderMessagePage()
    errorMessage.value = ''
    actionErrorMessage.value = ''
    actionSuccessMessage.value = ''
    hasLoadedOnce.value = false

    if (orderId !== null && currentUserId !== null && counterpartUserId !== null) {
      loadMessages()
    }
  },
  { immediate: true },
)
</script>

<template>
  <section class="section-panel">
    <div class="section-header">
      <div>
        <div class="flex flex-wrap items-center gap-2">
          <h2 class="section-heading">订单会话</h2>
          <span class="chip chip-neutral font-numeric">消息 {{ pageData.total }}</span>
          <span v-if="unreadIncomingCount > 0" class="chip chip-warning font-numeric">待读 {{ unreadIncomingCount }}</span>
        </div>
        <p class="section-subtitle">只覆盖 order chat 的 list / send / mark-as-read；系统通知中心仍归 Day08。</p>
      </div>
      <div class="section-actions">
        <button class="btn-default !h-9 px-3" type="button" :disabled="loading" @click="loadMessages">
          <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
          <RefreshCw v-else class="h-4 w-4" />
          <span>{{ loading ? '刷新中...' : '刷新会话' }}</span>
        </button>
        <button class="btn-default !h-9 px-3" type="button" :disabled="!canMarkRead" @click="submitMarkRead">
          <Loader2 v-if="markReadSubmitting" class="h-4 w-4 animate-spin" />
          <CheckCheck v-else class="h-4 w-4" />
          <span>{{ markReadSubmitting ? '提交中...' : '标记已读' }}</span>
        </button>
      </div>
    </div>

    <div class="section-body space-y-4">
      <div v-if="errorMessage" class="notice-banner notice-banner-danger">
        <span class="notice-dot bg-red-500"></span>
        <div class="flex-1">
          <p class="font-semibold">订单会话加载失败</p>
          <p class="mt-1 text-[12px] leading-5">{{ errorMessage }}</p>
        </div>
      </div>

      <div v-if="actionErrorMessage" class="notice-banner notice-banner-danger">
        <span class="notice-dot bg-red-500"></span>
        <span>{{ actionErrorMessage }}</span>
      </div>

      <div v-if="actionSuccessMessage" class="notice-banner notice-banner-success">
        <span class="notice-dot bg-emerald-500"></span>
        <span>{{ actionSuccessMessage }}</span>
      </div>

      <section class="rounded-2xl border border-gray-100 bg-gray-50/80 p-4 space-y-3">
        <div>
          <h3 class="text-[14px] font-semibold text-gray-900">发送给 {{ counterpartLabel }}</h3>
          <p class="form-helper">消息内容由 API 层统一生成 clientMsgId，并做 1~500 字符兜底校验。</p>
        </div>
        <div class="space-y-3">
          <textarea
            v-model="draftMessage"
            class="input-standard min-h-[112px]"
            maxlength="500"
            placeholder="输入订单沟通内容（1~500 字）"
            :disabled="sendSubmitting || currentUserId === null || counterpartUserId === null"
            @input="clearActionMessages"
          ></textarea>
          <div class="flex flex-wrap items-center gap-3">
            <button class="btn-primary" type="button" :disabled="!canSubmitSend" @click="submitSend">
              <Loader2 v-if="sendSubmitting" class="h-4 w-4 animate-spin" />
              <Send v-else class="h-4 w-4" />
              <span>{{ sendSubmitting ? '发送中...' : '发送消息' }}</span>
            </button>
            <span class="form-helper">{{ sendValidationError || `当前会话对端：${counterpartLabel}` }}</span>
          </div>
        </div>
      </section>

      <div v-if="loading && !hasLoadedOnce" class="empty-state min-h-[240px]">
        <Loader2 class="empty-state-icon animate-spin text-blue-500" />
        <p class="empty-state-title">正在加载订单会话</p>
      </div>

      <div v-else-if="hasEmptyState" class="empty-state min-h-[240px]">
        <MessageSquareText class="empty-state-icon" />
        <p class="empty-state-title">当前订单还没有会话消息</p>
        <p class="empty-state-text">可以先发送一条消息，后续再由 verify 线程做运行态判定。</p>
      </div>

      <div v-else class="space-y-3">
        <article
          v-for="item in messages"
          :key="item.id || `${item.createTime}-${item.fromUserId}-${item.content}`"
          :class="readMessageCardClass(item.fromUserId)"
        >
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div class="inline-meta font-numeric">
              <span>{{ readMessageAuthor(item.fromUserId) }}</span>
              <span class="inline-meta-dot"></span>
              <span>{{ item.createTime || '-' }}</span>
            </div>
            <span class="chip chip-muted">{{ readMessageStatusText(item.fromUserId, item.read) }}</span>
          </div>
          <p class="mt-2 whitespace-pre-wrap break-words text-[13px] leading-6 text-gray-700">{{ item.content || '-' }}</p>
        </article>
      </div>
    </div>
  </section>
</template>
