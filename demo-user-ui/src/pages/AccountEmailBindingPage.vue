<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { bindMyEmail, unbindMyEmail } from '@/api/profile'
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
  email: currentUser.value?.email || '',
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
const currentEmail = computed(() => (currentUser.value?.email || '').trim())
const hasBoundEmail = computed(() => Boolean(currentEmail.value))

const normalizedBindEmail = computed(() => bindForm.email.trim())
const normalizedBindCode = computed(() => bindForm.verifyCode.trim())
const normalizedUnbindCode = computed(() => unbindForm.verifyCode.trim())

const canSubmitBind = computed(() => {
  return !binding.value && Boolean(normalizedBindEmail.value && normalizedBindCode.value)
})

const canSubmitUnbind = computed(() => {
  return hasBoundEmail.value && !unbinding.value && Boolean(normalizedUnbindCode.value)
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

  if (!normalizedBindEmail.value || !normalizedBindCode.value) {
    bindStatus.value = 'error'
    bindMessage.value = 'Email and verification code are required.'
    return
  }

  try {
    binding.value = true
    bindStatus.value = 'idle'
    bindMessage.value = ''

    const nextUser = await bindMyEmail(
      {
        email: bindForm.email,
        verifyCode: bindForm.verifyCode,
      },
      currentUser.value,
    )

    saveCurrentUser(nextUser)
    currentUser.value = nextUser

    bindForm.email = nextUser.email || normalizedBindEmail.value
    bindForm.verifyCode = ''

    bindStatus.value = 'success'
    bindMessage.value = 'Email binding updated.'
  } catch (error: unknown) {
    bindStatus.value = 'error'
    bindMessage.value = readErrorMessage(error, 'Email binding failed.')
  } finally {
    binding.value = false
  }
}

async function submitUnbindForm() {
  if (unbinding.value) {
    return
  }

  if (!hasBoundEmail.value) {
    unbindStatus.value = 'error'
    unbindMessage.value = 'No email is currently bound.'
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

    const nextUser = await unbindMyEmail(
      {
        verifyCode: unbindForm.verifyCode,
        currentPassword: unbindForm.currentPassword,
      },
      currentUser.value,
    )

    saveCurrentUser(nextUser)
    currentUser.value = nextUser

    bindForm.email = ''
    unbindForm.verifyCode = ''
    unbindForm.currentPassword = ''

    unbindStatus.value = 'success'
    unbindMessage.value = 'Email unbound.'
  } catch (error: unknown) {
    unbindStatus.value = 'error'
    unbindMessage.value = readErrorMessage(error, 'Email unbind failed.')
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
          <h1 class="section-title">Email Binding</h1>
          <p class="section-desc mt-2">
            This slice only covers email bind and unbind flows. Phone bind/unbind stays out of this thread.
          </p>
        </div>
        <router-link class="btn-default" to="/account">Back to account center</router-link>
      </div>
    </section>

    <section v-if="!hasSessionProfile" class="rounded-2xl border border-orange-200 bg-orange-50 px-5 py-4 text-sm text-orange-700">
      No local account snapshot is available. Please sign in again before changing email binding.
    </section>

    <section class="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
      <section class="card p-6">
        <p class="muted-kicker">Current snapshot</p>
        <h2 class="section-title mt-2">Email status</h2>

        <div class="mt-6 grid gap-4 text-sm text-slate-600">
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">Account</p>
            <p class="mt-2 text-base font-medium text-slate-900">{{ displayName }}</p>
          </div>
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">Bound email</p>
            <p class="mt-2 text-base font-medium text-slate-900">{{ currentEmail || 'No email bound' }}</p>
          </div>
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">Thread boundary</p>
            <p class="mt-2 text-base font-medium text-slate-900">Email bind/unbind only. Phone binding is not implemented here.</p>
          </div>
        </div>
      </section>

      <section class="space-y-6">
        <section class="card p-6">
          <p class="muted-kicker">Bind</p>
          <h2 class="section-title mt-2">Bind or update email</h2>

          <form class="mt-6 space-y-4" @submit.prevent="submitBindForm">
            <div>
              <label class="form-label" for="bind-email-value">Email</label>
              <input
                id="bind-email-value"
                v-model="bindForm.email"
                class="input-standard"
                type="email"
                autocomplete="email"
                placeholder="Enter email"
                :disabled="binding"
                @input="clearBindStatus"
              />
            </div>

            <div>
              <label class="form-label" for="bind-email-code">Verification code</label>
              <input
                id="bind-email-code"
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
              {{ binding ? 'Submitting...' : 'Submit email bind' }}
            </button>
          </form>
        </section>

        <section class="card p-6">
          <p class="muted-kicker">Unbind</p>
          <h2 class="section-title mt-2">Unbind current email</h2>

          <form class="mt-6 space-y-4" @submit.prevent="submitUnbindForm">
            <div>
              <label class="form-label" for="unbind-email-code">Verification code</label>
              <input
                id="unbind-email-code"
                v-model="unbindForm.verifyCode"
                class="input-standard"
                type="text"
                autocomplete="one-time-code"
                placeholder="Enter verification code"
                :disabled="unbinding || !hasBoundEmail"
                @input="clearUnbindStatus"
              />
            </div>

            <div>
              <label class="form-label" for="unbind-email-password">Current password (optional)</label>
              <input
                id="unbind-email-password"
                v-model="unbindForm.currentPassword"
                class="input-standard"
                type="password"
                autocomplete="current-password"
                placeholder="Enter current password when required"
                :disabled="unbinding || !hasBoundEmail"
                @input="clearUnbindStatus"
              />
            </div>

            <p v-if="!hasBoundEmail" class="text-xs text-slate-500">There is no bound email to unbind.</p>

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
              {{ unbinding ? 'Submitting...' : 'Submit email unbind' }}
            </button>
          </form>
        </section>
      </section>
    </section>
  </div>
</template>
