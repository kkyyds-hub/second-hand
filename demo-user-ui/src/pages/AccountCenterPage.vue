<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  getUserDisplayName,
  getUserPrimaryContact,
  hasUserProfileSnapshot,
  isSellerUser,
  readCurrentUser,
  type UserProfile,
} from '@/utils/request'

const currentUser = ref<UserProfile | null>(readCurrentUser())

const hasSessionProfile = computed(() => hasUserProfileSnapshot(currentUser.value))
const displayName = computed(() => getUserDisplayName(currentUser.value))
const primaryContact = computed(() => getUserPrimaryContact(currentUser.value))
const roleTags = computed(() => [isSellerUser(currentUser.value) ? 'Seller' : 'User', currentUser.value?.status || 'Status pending'])
const avatarInitial = computed(() => displayName.value.slice(0, 1).toUpperCase() || 'U')
const sessionRoleText = computed(() => (isSellerUser(currentUser.value) ? 'Seller / User' : 'User'))

const identityRows = computed(() => {
  return [
    { label: 'User ID', value: currentUser.value?.id ?? '-' },
    { label: 'Login name', value: currentUser.value?.loginName || '-' },
    { label: 'Nickname', value: currentUser.value?.nickname || '-' },
    { label: 'Registered at', value: currentUser.value?.registerTime || '-' },
  ]
})

const contactRows = computed(() => {
  return [
    { label: 'Mobile', value: currentUser.value?.mobile || '-' },
    { label: 'Email', value: currentUser.value?.email || '-' },
    { label: 'Region', value: currentUser.value?.region || '-' },
    { label: 'Last login IP', value: currentUser.value?.lastLoginIp || '-' },
  ]
})

const accountRows = computed(() => {
  return [
    { label: 'Credit score', value: currentUser.value?.creditScore ?? '-' },
    { label: 'Product count', value: currentUser.value?.productCount ?? '-' },
    { label: 'Account status', value: currentUser.value?.status || '-' },
    { label: 'Bio', value: currentUser.value?.bio || 'No bio yet' },
  ]
})
</script>

<template>
  <div class="space-y-6">
    <section class="card p-6 md:p-8">
      <p class="muted-kicker">Account center</p>
      <h1 class="section-title mt-3">Account Center</h1>
      <p class="section-desc">
        This page now stays focused on account overview, current profile summary, and entry links. Profile edit, avatar upload,
        password change, email binding, and address management each live on their own pages.
      </p>
    </section>

    <section v-if="!hasSessionProfile" class="rounded-2xl border border-orange-200 bg-orange-50 px-5 py-4 text-sm text-orange-700">
      No local account snapshot is available. Please sign in again before opening account center.
    </section>

    <section class="grid gap-6 lg:grid-cols-[1.2fr_1fr]">
      <div class="card p-6">
        <div class="flex flex-col gap-4 sm:flex-row sm:items-center">
          <img
            v-if="currentUser?.avatar"
            :src="currentUser.avatar"
            :alt="`${displayName} avatar`"
            class="h-16 w-16 rounded-3xl border border-slate-200 object-cover"
          />
          <div
            v-else
            class="flex h-16 w-16 items-center justify-center rounded-3xl bg-slate-900 text-2xl font-semibold text-white"
          >
            {{ avatarInitial }}
          </div>

          <div class="flex-1">
            <p class="text-lg font-semibold text-slate-900">{{ displayName }}</p>
            <p class="mt-1 text-sm text-slate-500">{{ primaryContact }}</p>
            <div class="mt-3 flex flex-wrap gap-2">
              <span
                v-for="tag in roleTags"
                :key="tag"
                class="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-medium text-slate-600"
              >
                {{ tag }}
              </span>
            </div>
          </div>
        </div>

        <div class="mt-6 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4 text-sm text-slate-600">
          <p class="font-medium text-slate-900">Account summary</p>
          <p class="mt-2 leading-6">
            The overview still reads from the local session snapshot. Day02 page-level abilities are now split out so the center
            page stays a clean index rather than a pile of forms.
          </p>
        </div>
      </div>

      <div class="card p-6">
        <p class="muted-kicker">Session snapshot</p>
        <h2 class="section-title mt-2">Current session summary</h2>
        <div class="mt-6 space-y-4 text-sm text-slate-600">
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">Role</p>
            <p class="mt-2 font-medium text-slate-900">{{ sessionRoleText }}</p>
          </div>
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">Source</p>
            <p class="mt-2 font-medium text-slate-900">Local session snapshot written after sign-in.</p>
          </div>
          <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs text-slate-400">Page scope</p>
            <p class="mt-2 font-medium text-slate-900">Overview and entry links only.</p>
          </div>
        </div>
      </div>
    </section>

    <section class="grid gap-6 md:grid-cols-2 xl:grid-cols-6">
      <section class="card p-6">
        <p class="muted-kicker">Entry</p>
        <h2 class="section-title mt-2">Profile edit</h2>
        <p class="section-desc mt-3">Keep the existing nickname and bio edit flow on its own page.</p>
        <div class="mt-6">
          <router-link class="btn-default" to="/account/profile">Open profile edit</router-link>
        </div>
      </section>

      <section class="card p-6">
        <p class="muted-kicker">Entry</p>
        <h2 class="section-title mt-2">Avatar upload</h2>
        <p class="section-desc mt-3">This thread's Day02 slice: a dedicated page for the avatar upload chain.</p>
        <div class="mt-6">
          <router-link class="btn-default" to="/account/avatar">Open avatar upload</router-link>
        </div>
      </section>

      <section class="card p-6">
        <p class="muted-kicker">Entry</p>
        <h2 class="section-title mt-2">Password change</h2>
        <p class="section-desc mt-3">Preserve the existing Day02 security slice as an independent page entry.</p>
        <div class="mt-6">
          <router-link class="btn-default" to="/account/security/password">Open password page</router-link>
        </div>
      </section>

      <section class="card p-6">
        <p class="muted-kicker">Entry</p>
        <h2 class="section-title mt-2">Email binding</h2>
        <p class="section-desc mt-3">Day02 email bind/unbind lives on its own page and is isolated from phone binding.</p>
        <div class="mt-6">
          <router-link class="btn-default" to="/account/security/email">Open email binding</router-link>
        </div>
      </section>

      <section class="card p-6">
        <p class="muted-kicker">Entry</p>
        <h2 class="section-title mt-2">Phone binding</h2>
        <p class="section-desc mt-3">Day02 phone bind/unbind is also isolated on a dedicated security page.</p>
        <div class="mt-6">
          <router-link class="btn-default" to="/account/security/phone">Open phone binding</router-link>
        </div>
      </section>

      <section class="card p-6">
        <p class="muted-kicker">Entry</p>
        <h2 class="section-title mt-2">Address book</h2>
        <p class="section-desc mt-3">Keep the existing address list route reachable without mixing it into this slice.</p>
        <div class="mt-6">
          <router-link class="btn-default" to="/account/addresses">Open addresses</router-link>
        </div>
      </section>
    </section>

    <section class="grid gap-6 xl:grid-cols-3">
      <section class="card p-6">
        <p class="muted-kicker">Identity</p>
        <h2 class="section-title mt-2">Identity</h2>
        <div class="mt-6 grid gap-4">
          <div
            v-for="row in identityRows"
            :key="row.label"
            class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4"
          >
            <p class="text-xs text-slate-400">{{ row.label }}</p>
            <p class="mt-2 text-base font-medium text-slate-900">{{ row.value }}</p>
          </div>
        </div>
      </section>

      <section class="card p-6">
        <p class="muted-kicker">Contact</p>
        <h2 class="section-title mt-2">Contact</h2>
        <div class="mt-6 grid gap-4">
          <div
            v-for="row in contactRows"
            :key="row.label"
            class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4"
          >
            <p class="text-xs text-slate-400">{{ row.label }}</p>
            <p class="mt-2 text-base font-medium text-slate-900">{{ row.value }}</p>
          </div>
        </div>
      </section>

      <section class="card p-6">
        <p class="muted-kicker">Status</p>
        <h2 class="section-title mt-2">Status</h2>
        <div class="mt-6 grid gap-4">
          <div
            v-for="row in accountRows"
            :key="row.label"
            class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4"
          >
            <p class="text-xs text-slate-400">{{ row.label }}</p>
            <p class="mt-2 text-base font-medium text-slate-900">{{ row.value }}</p>
          </div>
        </div>
      </section>
    </section>
  </div>
</template>
