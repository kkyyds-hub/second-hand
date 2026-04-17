<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { bindMyPhone, unbindMyPhone } from '@/api/profile'
import {
  getUserDisplayName,
  hasUserProfileSnapshot,
  readCurrentUser,
  saveCurrentUser,
  type UserProfile,
} from '@/utils/request'

type ActionStatus = 'idle' | 'success' | 'error'

const currentUser = ref<UserProfile | null>(readCurrentUser())

const bindForm = reactive({
  mobile: currentUser.value?.mobile || '',
  verifyCode: '',
})

const unbindForm = reactive({
  verifyCode: '',
  currentPassword: '',
})

const binding = ref(false)
const bindStatus = ref<ActionStatus>('idle')
const bindMessage = ref('')

const unbinding = ref(false)
const unbindStatus = ref<ActionStatus>('idle')
const unbindMessage = ref('')

const hasSessionProfile = computed(() => hasUserProfileSnapshot(currentUser.value))
const displayName = computed(() => getUserDisplayName(currentUser.value))
const currentMobile = computed(() => (currentUser.value?.mobile || '').trim())
const hasBoundMobile = computed(() => Boolean(currentMobile.value))

const normalizedBindMobile = computed(() => bindForm.mobile.trim())
const normalizedBindCode = computed(() => bindForm.verifyCode.trim())
const normalizedUnbindCode = computed(() => unbindForm.verifyCode.trim())

const canSubmitBind = computed(() => {
  return !binding.value && Boolean(normalizedBindMobile.value && normalizedBindCode.value)
})

const canSubmitUnbind = computed(() => {
  return hasBoundMobile.value && !unbinding.value && Boolean(normalizedUnbindCode.value)
})

function readErrorMessage(error: unknown, fallbackMessage: string) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return fallbackMessage
}

function clearBindStatus() {
  if (binding.value || bindStatus.value === 'idle') {
    return
  }

  bindStatus.value = 'idle'
  bindMessage.value = ''
}

function clearUnbindStatus() {
  if (unbinding.value || unbindStatus.value === 'idle') {
    return
  }

  unbindStatus.value = 'idle'
  unbindMessage.value = ''
}

async function submitBindForm() {
  if (binding.value) {
    return
  }

  if (!normalizedBindMobile.value || !normalizedBindCode.value) {
    bindStatus.value = 'error'
    bindMessage.value = 'Mobile number and verification code are required.'
    return
  }

  try {
    binding.value = true
    bindStatus.value = 'idle'
    bindMessage.value = ''

    const nextUser = await bindMyPhone(
      {
        mobile: bindForm.mobile,
        verifyCode: bindForm.verifyCode,
      },
      currentUser.value,
    )

    saveCurrentUser(nextUser)
    currentUser.value = nextUser

    bindForm.mobile = nextUser.mobile || normalizedBindMobile.value
    bindForm.verifyCode = ''

    bindStatus.value = 'success'
    bindMessage.value = 'Phone binding updated.'
  } catch (error: unknown) {
    bindStatus.value = 'error'
    bindMessage.value = readErrorMessage(error, 'Phone binding failed.')
  } finally {
    binding.value = false
  }
}

async function submitUnbindForm() {
  if (unbinding.value) {
    return
  }

  if (!hasBoundMobile.value) {
    unbindStatus.value = 'error'
    unbindMessage.value = 'No phone number is currently bound.'
    return
  }

  if (!normalizedUnbindCode.value) {
    unbindStatus.value = 'error'
    unbindMessage.value = 'Verification code is required.'
    return
  }

  try {
    unbinding.value = true
    unbindStatus.value = 'idle'
    unbindMessage.value = ''

    const nextUser = await unbindMyPhone(
      {
        verifyCode: unbindForm.verifyCode,
        currentPassword: unbindForm.currentPassword,
      },
      currentUser.value,
    )

    saveCurrentUser(nextUser)
    currentUser.value = nextUser

    bindForm.mobile = ''
    unbindForm.verifyCode = ''
    unbindForm.currentPassword = ''

    unbindStatus.value = 'success'
    unbindMessage.value = 'Phone unbound.'
  } catch (error: unknown) {
    unbindStatus.value = 'error'
    unbindMessage.value = readErrorMessage(error, 'Phone unbind failed.')
  } finally {
    unbinding.value = false
  }
}
</script>

<template>
  <div class="space-y-6">
    <section class="card p-6 md:p-8">
      <p class="muted-kicker">Security</p>
      <div class="mt-3 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 class="section-title">Phone Binding</h1>
          <p class="section-desc mt-2">
            This slice only covers phone bind and unbind flows. It does not include email binding.
          </p>
        </div>
        <router-link class="btn-default" to="/account">Back to account center</router-link>
      </div>
    </section>

    <section v-if="!hasSessionProfile" class="rounded-2xl border border-orange-200 bg-orange-50 px-5 py-4 text-sm text-orange-700">
      No local account snapshot is available. Please sign in again before changing phone binding.
    </section>

    <section class="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
      <section class="card p-6">
        <p class="muted-kicker">Current snapshot</p>
        <h2 class="section-title mt-2">Phone status</h2>

        <div class="mt-6 grid gap-4 text-sm text-slate-600">
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">Account</p>
            <p class="mt-2 text-base font-medium text-slate-900">{{ displayName }}</p>
          </div>
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">Bound mobile</p>
            <p class="mt-2 text-base font-medium text-slate-900">{{ currentMobile || 'No mobile bound' }}</p>
          </div>
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">Thread boundary</p>
            <p class="mt-2 text-base font-medium text-slate-900">Phone bind/unbind only. Email binding is not implemented here.</p>
          </div>
        </div>
      </section>

      <section class="space-y-6">
        <section class="card p-6">
          <p class="muted-kicker">Bind</p>
          <h2 class="section-title mt-2">Bind or update phone</h2>

          <form class="mt-6 space-y-4" @submit.prevent="submitBindForm">
            <div>
              <label class="form-label" for="bind-phone-value">Mobile number</label>
              <input
                id="bind-phone-value"
                v-model="bindForm.mobile"
                class="input-standard"
                type="tel"
                autocomplete="tel"
                placeholder="Enter mobile number"
                :disabled="binding"
                @input="clearBindStatus"
              />
            </div>

            <div>
              <label class="form-label" for="bind-phone-code">Verification code</label>
              <input
                id="bind-phone-code"
                v-model="bindForm.verifyCode"
                class="input-standard"
                type="text"
                autocomplete="one-time-code"
                placeholder="Enter verification code"
                :disabled="binding"
                @input="clearBindStatus"
              />
            </div>

            <section
              v-if="bindStatus === 'success'"
              class="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700"
            >
              {{ bindMessage }}
            </section>
            <section
              v-else-if="bindStatus === 'error'"
              class="rounded-2xl border border-orange-200 bg-orange-50 px-4 py-3 text-sm text-orange-700"
            >
              {{ bindMessage }}
            </section>

            <button class="btn-primary" type="submit" :disabled="!canSubmitBind">
              {{ binding ? 'Submitting...' : 'Submit phone bind' }}
            </button>
          </form>
        </section>

        <section class="card p-6">
          <p class="muted-kicker">Unbind</p>
          <h2 class="section-title mt-2">Unbind current phone</h2>

          <form class="mt-6 space-y-4" @submit.prevent="submitUnbindForm">
            <div>
              <label class="form-label" for="unbind-phone-code">Verification code</label>
              <input
                id="unbind-phone-code"
                v-model="unbindForm.verifyCode"
                class="input-standard"
                type="text"
                autocomplete="one-time-code"
                placeholder="Enter verification code"
                :disabled="unbinding || !hasBoundMobile"
                @input="clearUnbindStatus"
              />
            </div>

            <div>
              <label class="form-label" for="unbind-phone-password">Current password (optional)</label>
              <input
                id="unbind-phone-password"
                v-model="unbindForm.currentPassword"
                class="input-standard"
                type="password"
                autocomplete="current-password"
                placeholder="Enter current password when required"
                :disabled="unbinding || !hasBoundMobile"
                @input="clearUnbindStatus"
              />
            </div>

            <p v-if="!hasBoundMobile" class="text-xs text-slate-500">There is no bound phone number to unbind.</p>

            <section
              v-if="unbindStatus === 'success'"
              class="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700"
            >
              {{ unbindMessage }}
            </section>
            <section
              v-else-if="unbindStatus === 'error'"
              class="rounded-2xl border border-orange-200 bg-orange-50 px-4 py-3 text-sm text-orange-700"
            >
              {{ unbindMessage }}
            </section>

            <button class="btn-primary" type="submit" :disabled="!canSubmitUnbind">
              {{ unbinding ? 'Submitting...' : 'Submit phone unbind' }}
            </button>
          </form>
        </section>
      </section>
    </section>
  </div>
</template>
