import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getCartCount, type CartCount } from '@/api/cart'

/**
 * 购物车数量全局状态。
 *
 * 设计要点：
 * 1) 只维护 total / available / loading，不缓存购物车列表（列表由页面按需加载）；
 * 2) 角标显示 total（失效商品仍在购物车中）；
 * 3) 登录态变化（登录/登出/401/切换账号）时由布局层统一 reset + refresh，
 *    避免把旧用户的数量串给新用户；
 * 4) 任何加入/删除/结算成功后调用 refreshCount() 同步角标。
 */
export const useCartStore = defineStore('cart', () => {
  const total = ref(0)
  const available = ref(0)
  const loading = ref(false)
  /** 记录当前计数归属的用户 ID，用于检测账号切换。 */
  const ownerId = ref<number | null>(null)

  function applyCount(count: CartCount, owner: number | null) {
    total.value = count.total
    available.value = count.available
    ownerId.value = owner
  }

  /**
   * 拉取当前用户购物车数量。失败时保留旧值（避免角标抖动），由调用方决定提示。
   */
  async function refreshCount(owner: number | null): Promise<CartCount> {
    loading.value = true
    try {
      const count = await getCartCount()
      applyCount(count, owner)
      return count
    } finally {
      loading.value = false
    }
  }

  /**
   * 重置为 0（登出 / 401 / 切换账号时调用）。
   */
  function reset() {
    total.value = 0
    available.value = 0
    loading.value = false
    ownerId.value = null
  }

  return {
    total,
    available,
    loading,
    ownerId,
    refreshCount,
    reset,
  }
})
