<script setup lang="ts">
import type { UserAddressItem } from '@/api/address'

const props = defineProps<{
  address: UserAddressItem
  modelValue: number | null
  name: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number]
}>()

function selectAddress() {
  if (!props.disabled && props.address.id !== null) {
    emit('update:modelValue', props.address.id)
  }
}
</script>

<template>
  <label
    class="commerce-address-option"
    :class="address.id === modelValue ? 'commerce-address-option-selected' : ''"
  >
    <input
      v-if="address.id !== null"
      type="radio"
      :name="name"
      :value="address.id"
      :checked="address.id === modelValue"
      :disabled="disabled"
      class="checkbox-standard mt-1"
      @change="selectAddress"
    />
    <span class="min-w-0 flex-1">
      <span class="flex flex-wrap items-center gap-x-2 gap-y-1">
        <span class="text-[14px] font-semibold text-gray-900">{{ address.receiverName || '收件人待补充' }}</span>
        <span class="font-numeric text-[13px] text-gray-600">{{ address.mobile || '手机号待补充' }}</span>
        <span v-if="address.isDefault" class="chip chip-accent">默认</span>
      </span>
      <span class="mt-2 block break-words text-[13px] leading-5 text-gray-600">{{ address.fullAddress || '地址信息待补充' }}</span>
    </span>
  </label>
</template>
