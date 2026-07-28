export const adminBrandName = '拾光集运营后台'
export const adminShortBrandName = '拾光集'

export function buildAdminDocumentTitle(pageTitle: string): string {
  return `${pageTitle} · ${adminBrandName}`
}
