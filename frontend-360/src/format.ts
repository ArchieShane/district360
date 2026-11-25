const nf0 = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 })
const nf1 = new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 })
const nf2 = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 })

/** 千分位；空值显示 — */
export function num(v: unknown, digits = 0): string {
  if (v === null || v === undefined || v === '') return '—'
  const n = Number(v)
  if (!Number.isFinite(n)) return String(v)
  return (digits === 0 ? nf0 : digits === 1 ? nf1 : nf2).format(n)
}

export function pct(a: number | undefined, b: number | undefined, digits = 1): string {
  if (!a || !b) return '0'
  return (a / b * 100).toFixed(digits)
}

/** 面积：≥ 1 km² 用 km²，否则用 m² / 公顷 */
export function area(sqm: number | undefined): string {
  if (!sqm) return '—'
  if (sqm >= 1e6) return num(sqm / 1e6, 2) + ' km²'
  if (sqm >= 1e4) return num(sqm / 1e4, 1) + ' ha'
  return num(sqm) + ' m²'
}

export function display(v: unknown): string {
  if (v === null || v === undefined) return '—'
  if (typeof v === 'boolean') return v ? '是' : '否'
  if (typeof v === 'number') return Number.isInteger(v) ? num(v) : num(v, 2)
  const s = String(v)
  if (/^-?\d+(\.\d+)?$/.test(s) && s.length < 16 && s.includes('.')) return num(Number(s), 2)
  return s
}
