/**
 * Mock 开关：开发机在后端未就绪时可直接开启。
 * 约定：VITE_USE_MOCK=true 时，API 层优先走本地 mock 数据。
 */
const mockFlag = String(import.meta.env.VITE_USE_MOCK ?? '').toLowerCase()

/**
 * 用函数而不是裸布尔值，调用方读起来更像“判断当前是否启用 mock”。
 */
export const isMockEnabled = () => mockFlag === 'true'

/**
 * 统一的 mock 延迟，便于模拟真实网络请求手感。
 */
export const mockDelay = (ms = 320) =>
  new Promise<void>((resolve) => {
    window.setTimeout(() => resolve(), ms)
  })

/**
 * 简单深拷贝（仅用于 mock 的纯数据对象）。
 */
export function cloneData<T>(data: T): T {
  return JSON.parse(JSON.stringify(data)) as T
}

/**
 * 本地存储读写工具。
 */
export function readLocalJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return fallback
    return JSON.parse(raw) as T
  } catch {
    // 本地缓存损坏时直接回退，避免 mock 页面因为脏数据整体崩掉。
    return fallback
  }
}

export function writeLocalJson<T>(key: string, value: T) {
  // 统一序列化为 JSON 字符串，和 readLocalJson 的读取方式保持对称。
  localStorage.setItem(key, JSON.stringify(value))
}
