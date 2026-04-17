<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { changeMyPassword } from '@/api/security'

const form = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const submitting = ref(false)
const submitStatus = ref<'idle' | 'success' | 'error'>('idle')
const submitMessage = ref('')

const hasRequiredFields = computed(() => {
  return Boolean(form.currentPassword.trim() && form.newPassword.trim() && form.confirmPassword.trim())
})

const passwordsMatch = computed(() => form.newPassword === form.confirmPassword)
const canSubmit = computed(() => !submitting.value && hasRequiredFields.value && passwordsMatch.value)

function clearStatus() {
  if (submitting.value || submitStatus.value === 'idle') {
    return
  }

  submitStatus.value = 'idle'
  submitMessage.value = ''
}

async function submitForm() {
  if (submitting.value) {
    return
  }

  if (!hasRequiredFields.value) {
    submitStatus.value = 'error'
    submitMessage.value = 'Please complete all password fields.'
    return
  }

  if (!passwordsMatch.value) {
    submitStatus.value = 'error'
    submitMessage.value = 'The new password confirmation does not match.'
    return
  }

  try {
    submitting.value = true
    submitStatus.value = 'idle'
    submitMessage.value = ''

    const message = await changeMyPassword({
      currentPassword: form.currentPassword,
      newPassword: form.newPassword,
      confirmPassword: form.confirmPassword,
    })

    form.currentPassword = ''
    form.newPassword = ''
    form.confirmPassword = ''
    submitStatus.value = 'success'
    submitMessage.value = message || 'Password updated.'
  } catch (error: unknown) {
    submitStatus.value = 'error'
    submitMessage.value = error instanceof Error && error.message.trim() ? error.message : 'Password change failed.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="space-y-6">
    <section class="card p-6 md:p-8">
      <p class="muted-kicker">Security</p>
      <div class="mt-3 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 class="section-title">Change Password</h1>
          <p class="section-desc mt-2">
            This page handles password-change form state only. It does not include phone or email binding flows.
          </p>
        </div>
        <router-link class="btn-default" to="/account">Back to account center</router-link>
      </div>
    </section>

    <section class="card p-6">
      <form class="space-y-5" @submit.prevent="submitForm">
        <div>
          <label class="form-label" for="current-password">Current password</label>
          <input
            id="current-password"
            v-model="form.currentPassword"
            class="input-standard"
            type="password"
            autocomplete="current-password"
            placeholder="Enter current password"
            :disabled="submitting"
            @input="clearStatus"
          />
        </div>

        <div>
          <label class="form-label" for="new-password">New password</label>
          <input
            id="new-password"
            v-model="form.newPassword"
            class="input-standard"
            type="password"
            autocomplete="new-password"
            placeholder="Enter new password"
            :disabled="submitting"
            @input="clearStatus"
          />
        </div>

        <div>
          <label class="form-label" for="confirm-password">Confirm new password</label>
          <input
            id="confirm-password"
            v-model="form.confirmPassword"
            class="input-standard"
            type="password"
            autocomplete="new-password"
            placeholder="Confirm new password"
            :disabled="submitting"
            @input="clearStatus"
          />
        </div>

        <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4 text-sm text-slate-600">
          <p class="font-medium text-slate-900">Current boundary</p>
          <p class="mt-2 leading-6">
            Real submit now goes through `src/api/security.ts` and calls `POST /user/me/password`.
            This page still only exposes the current-password path; verify-channel code flow is kept at the API contract layer.
          </p>
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
          <button class="btn-primary" type="submit" :disabled="!canSubmit">
            {{ submitting ? 'Submitting...' : 'Submit password change' }}
          </button>
          <p v-if="!hasRequiredFields" class="text-xs text-slate-500">Complete all fields before submitting.</p>
          <p v-else-if="!passwordsMatch" class="text-xs text-orange-600">New password confirmation must match.</p>
        </div>
      </form>
    </section>
  </div>
</template>
