import type { LocationQueryValue } from 'vue-router'

export const ADMIN_HOME_PATH = '/'
export const ADMIN_LOGIN_PATH = '/login'

export function sanitizeAdminRedirect(value: LocationQueryValue | string | null | undefined): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) {
    return ADMIN_HOME_PATH
  }

  try {
    const target = new URL(value, window.location.origin)
    if (target.origin !== window.location.origin || target.pathname === ADMIN_LOGIN_PATH) return ADMIN_HOME_PATH
    return `${target.pathname}${target.search}${target.hash}`
  } catch {
    return ADMIN_HOME_PATH
  }
}

export function createAdminLoginPath(redirect?: string): string {
  const safeRedirect = sanitizeAdminRedirect(redirect)
  return safeRedirect === ADMIN_HOME_PATH ? ADMIN_LOGIN_PATH : `${ADMIN_LOGIN_PATH}?redirect=${encodeURIComponent(safeRedirect)}`
}

export function getCurrentAdminPath(): string {
  return sanitizeAdminRedirect(`${window.location.pathname}${window.location.search}${window.location.hash}`)
}
