/** 数字格式化：千分位、固定小数；null 显示「—」。 */
export function nf(v: number | null | undefined, digits = 0): string {
  if (v === null || v === undefined || Number.isNaN(v)) return '—'
  return Number(v).toLocaleString('en-US', { minimumFractionDigits: digits, maximumFractionDigits: digits })
}

/** 带正负号的百分比数值。 */
export function sg(v: number | null | undefined, digits = 0): string {
  if (v === null || v === undefined) return '—'
  return (v > 0 ? '+' : '') + nf(v, digits)
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/** 2025-11 → Nov-25 */
export function ym(v: string | null | undefined): string {
  if (!v) return '—'
  const [y, m] = v.split('-')
  return `${MONTHS[Number(m) - 1]}-${y.slice(2)}`
}

/** 2026-04-16 → 16-Apr-26 */
export function dmy(v: string | null | undefined): string {
  if (!v) return '—'
  const [y, m, d] = v.split('-')
  return `${Number(d)}-${MONTHS[Number(m) - 1]}-${y.slice(2)}`
}

export function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] as string)
}
