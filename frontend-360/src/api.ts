/**
 * District 360 V1.0 空间下钻接口（后端 /api/v2/space，只读主题库 theme.sp_*）。
 * 接口地址用相对路径：页面挂在什么二级路由下，接口就跟着走。
 */
const BASE = 'api/v2/space'

export type Level = 'emirate' | 'municipality' | 'district' | 'community' | 'plot' | 'building' | 'floor' | 'unit'
export type Layer = 'municipalities' | 'districts' | 'communities' | 'plots' | 'buildings' | 'floors' | 'units' | 'masterplans' | 'sites'

/** 开发建设页签（GET /nodes/{level}/{key}/dev）。scope 为实际统计范围：建筑及以下按所在地块；没有地块时为 null */
export interface DevSummary {
  masterPlans: number
  mpPlots: number
  mpPlotsLinked: number
  mpPlotsBuilt: number
  plannedGfa: number
  plannedUnits: number
  plannedPopulation: number
  plannedHotelRooms: number
  mpPlotAreaSqm: number
  sites: number
  siteBuildings: number
  siteAreaSqm: number
}

export interface DevMasterPlan {
  projectId: string
  projectNo?: string
  name?: string
  stage?: string
  stageRaw?: string
  approvalYear?: number
  developer?: string
  level?: string
  areaSqm?: number
  cx?: number
  cy?: number
  plots?: number
  plotsBuilt?: number
  plannedGfa?: number
  plannedUnits?: number
}

export interface DevSite {
  siteId: string
  description?: string
  areaSqm?: number
  buildings?: number
  plotKey?: string
  cx?: number
  cy?: number
}

export interface DevPlotControl {
  projectId?: string
  projectName?: string
  stage?: string
  masterplanType?: string
  approvalStatus?: string
  registeredLandUse?: string
  landUseL1?: string
  landUseL2?: string
  plotNumber?: string
  plotAreaSqm?: number
  far?: number
  storeys?: number
  buildings?: number
  units?: number
  population?: number
  totalGfa?: number
  residentialGfa?: number
  officeGfa?: number
  retailGfa?: number
  hotelGfa?: number
}

export interface DevView {
  scope: { level: Level; key: string } | null
  summary?: DevSummary
  stages?: { k: string; n: number }[]
  gfaByUse?: { k: string; gfa: number }[]
  masterPlans?: DevMasterPlan[]
  sites?: DevSite[]
  plotControls?: DevPlotControl[]
}

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

/** 市场页签（GET /nodes/{level}/{key}/mkt）。租金为年租金 AED，分组与汇总用中位数 */
export interface MktView {
  scope: { level: Level; key: string } | null
  tenancy?: {
    records: number; contracts: number; units: number; activeContracts: number; activeAnnualRent: number
    medianAnnualRent?: number; rentPerSqm?: number; expiring90: number; firstStart?: string; lastEnd?: string
  }
  byStatus?: { k: string; n: number; medianRent?: number }[]
  byUnitType?: { k: string; n: number; medianRent?: number }[]
  byPropertyUsage?: { k: string; n: number; medianRent?: number }[]
  byYear?: { y: number; n: number; medianRent?: number }[]
  tenancies?: { contractNum?: string; unitName?: string; unitType?: string; propertyName?: string; status?: string;
    startDate?: string; endDate?: string; annualRent?: number; areaSqm?: number; bedrooms?: number }[]
  adrec?: { sales: number; salesValue: number; salesRatePerSqm?: number; mortgages: number; mortgagesValue: number;
    rents: number; rentsAnnualValue: number; plotTransactions: number }
  adrecByMonth?: { m: string; sales: number; mortgages: number; rents: number }[]
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
  /** 如「ODS 无源表：…」 */
  note?: string
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
  mkt: (level: Level, key: string, signal?: AbortSignal) => get<MktView>(`/nodes/${level}/${enc(key)}/mkt`, signal),
  dev: (level: Level, key: string, signal?: AbortSignal) => get<DevView>(`/nodes/${level}/${enc(key)}/dev`, signal),
  search: (q: string) => get<SearchHit[]>(`/search?q=${enc(q)}&limit=6`),
  meta: () => get<Meta>('/meta'),
}
