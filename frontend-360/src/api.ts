/**
 * District 360 V1.0 空间下钻接口（后端 /api/v2/space，只读主题库 theme.sp_*）。
 * 接口地址用相对路径：页面挂在什么二级路由下，接口就跟着走。
 */
const BASE = 'api/v2/space'

export type Level = 'emirate' | 'municipality' | 'district' | 'community' | 'plot' | 'building' | 'floor' | 'unit'
export type Layer = 'municipalities' | 'districts' | 'communities' | 'plots' | 'buildings' | 'floors' | 'units'

export const LEVELS: Level[] = ['emirate', 'municipality', 'district', 'community', 'plot', 'building', 'floor', 'unit']

export interface NodeRef {
  level: Level
  key: string
  name: string
}

export interface BreakdownItem {
  k: string
  n: number
  area: number
}

/** 计数汇总，字段随层级不同（楼层只有单元数等） */
export interface NodeStat {
  municipalities?: number
  districts?: number
  communities?: number
  plots?: number
  plotsConstructed?: number
  plotsUnderConstruction?: number
  plotsNotConstructed?: number
  plotsAreaSqm?: number
  buildings?: number
  buildingsUnderConstruction?: number
  floors?: number
  units?: number
  unitsAreaSqm?: number
  bedrooms?: number
}

export interface NodeDetail {
  level: Level
  key: string
  name: string
  nameAr: string | null
  path: NodeRef[]
  childLevel: Level | null
  childCount: number
  bbox: [number, number, number, number] | null
  center: [number, number] | null
  geometry: GeoJSON.Geometry | null
  info: Record<string, string | number | boolean>
  stat: NodeStat
  breakdowns: Record<string, BreakdownItem[]>
  layers: Layer[]
}

export interface ChildItem {
  key: string
  name: string
  sub?: string
  [k: string]: string | number | boolean | undefined
}

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
  hasMore: boolean
}

export interface SearchHit {
  level: Level
  key: string
  name: string
  sub?: string
}

export interface MetaTable {
  table: string
  theme: string
  title: string
  sources: string
  extractor?: string
  rows?: number
  asOf?: string
  loadedAt?: string
}

export interface EtlRun {
  source: string
  status: string
  startedAt: string
  finishedAt?: string
  rows?: number
  asOf?: string
  message?: string
}

export interface Meta {
  tables: MetaTable[]
  runs: EtlRun[]
}

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

async function get<T>(path: string, signal?: AbortSignal): Promise<T> {
  const res = await fetch(BASE + path, { headers: { Accept: 'application/json' }, signal })
  const body = (await res.json().catch(() => null)) as ApiResponse<T> | null
  if (!res.ok || !body || body.code !== 0) {
    throw new Error(body?.message || `请求失败：${res.status}`)
  }
  return body.data
}

const enc = encodeURIComponent

export const api = {
  node: (level: Level, key: string, signal?: AbortSignal) => get<NodeDetail>(`/nodes/${level}/${enc(key)}`, signal),
  children: (level: Level, key: string, page = 0, size = 50, q?: string) => {
    const p = new URLSearchParams({ page: String(page), size: String(size) })
    if (q) p.set('q', q)
    return get<PageResult<ChildItem>>(`/nodes/${level}/${enc(key)}/children?${p}`)
  },
  layer: async (level: Level, key: string, layer: Layer, signal?: AbortSignal): Promise<GeoJSON.FeatureCollection> => {
    const res = await fetch(`${BASE}/nodes/${level}/${enc(key)}/layers/${layer}.geojson`, { signal })
    if (!res.ok) throw new Error(`请求失败：${res.status}`)
    return res.json()
  },
  search: (q: string) => get<SearchHit[]>(`/search?q=${enc(q)}&limit=6`),
  meta: () => get<Meta>('/meta'),
}
