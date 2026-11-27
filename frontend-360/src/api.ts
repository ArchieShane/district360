/**
 * District 360 V1.0 空间下钻接口（后端 /api/v2/space，只读主题库 theme.sp_*）。
 * 接口地址用相对路径：页面挂在什么二级路由下，接口就跟着走。
 */
/**
 * 接口前缀（相对页面地址）。开发时页面在根路径，走 vite 代理；
 * 部署在后端二级目录下（如 /district360/v2/）时打包设 VITE_API_BASE=../api/v2/space。
 */
const BASE = import.meta.env.VITE_API_BASE || 'api/v2/space'

export type Level = 'emirate' | 'municipality' | 'district' | 'community' | 'plot' | 'building' | 'floor' | 'unit'
export type Layer = 'municipalities' | 'districts' | 'communities' | 'plots' | 'buildings' | 'floors' | 'units' | 'masterplans' | 'sites' | 'projects' | 'busstops' | 'streetlights' | 'facilities'

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
  capitalProjects?: CapitalProjects
  permits?: Permits
  sources?: Sources
}

/** 业务视图各主题表的数据状态：rows 为 0 时 note 说明原因（如「ODS 源表无数据：…」），页面显示待接入而不是 0 */
export type Sources = Record<string, { rows: number; note?: string | null } | null>

/** 一组计数（k 为原值） */
export interface KN { k: string; n: number }

/** 资本项目汇总（GSEC）。延期：未完工且预测完工晚于原计划，或实际进度落后修订计划 10 个百分点以上 */
export interface ProjectSummary {
  projects: number; budgetAed: number; spentAed: number; spentPct?: number | null
  completed: number; ongoing: number; delayed: number; delayedBudgetAed: number; mapped: number
}
export interface CapitalProject {
  projectId: string; name?: string; category?: string; phase?: string; status?: string
  budgetAed?: number; spentAed?: number; spentPct?: number | null
  actualProgress?: number | null; plannedProgress?: number | null; originalProgress?: number | null
  plannedEnd?: string | null; forecastEnd?: string | null; delayed?: boolean | null; completed?: boolean | null
  contractor?: string | null; districtKey?: string | null; cx?: number | null; cy?: number | null
}
export interface CapitalProjects {
  summary?: ProjectSummary
  byStatus?: (KN & { budgetAed: number; delayed: number })[]
  byCategory?: (KN & { budgetAed: number; delayed: number })[]
  byPhase?: (KN & { budgetAed: number; delayed: number })[]
  items?: CapitalProject[]
}

/** 建设许可（EMECS）与建筑类申请（MEPS）。近 12 / 6 个月以该表最晚签发日 asOf 为锚 */
export interface PermitSummary {
  asOf?: string | null; permits: number; newLast12m: number; newLast6m: number; newPrev6m: number; inProgress: number; stalled180: number
}
export interface Permits {
  summary?: PermitSummary
  byStatus?: KN[]
  applications?: { applications: number; completionCertificates: number; maintenancePermits: number; demolitionPermits: number; other: number }
  applicationTypes?: (KN & { kind: string })[]
  applicationsByMonth?: { month: string; maintenance: number; demolition: number; completion: number }[]
  /** 仅地块：本地块的许可（type=permit）与申请（completion / maintenance / demolition / other） */
  plotRecords?: { type: string; number?: string; name?: string; status?: string; projectStatus?: string; date?: string; completedOn?: string }[]
}

/** Tawtheeq 单元状态（入住率） */
export interface OccupancySummary { units: number; rented: number; vacant: number; occupancyPct?: number | null; blocked: number; linkedUnits: number }
export interface Occupancy {
  summary?: OccupancySummary
  byUsage?: { k: string; units: number; rented: number; occupancyPct?: number | null }[]
  byClassification?: { k: string; units: number; rented: number; occupancyPct?: number | null }[]
  /** 仅楼层 / 单元：对上 BDMS 的单元状态 */
  units?: { unitKey: string; unitNumber?: string; isRented?: boolean | null; blocked?: boolean | null; status?: 'rented' | 'vacant' | 'blocked' | null
    usageType?: string; classification?: string; areaSqm?: number }[]
}

/** 城市运行业务视图汇总（近 12 个月以该表最晚日期 asOf 为锚） */
export interface CaseSummary { asOf?: string | null; cases12m: number; closed12m: number; closedPct?: number | null; slaRated: number; slaPct?: number | null; csat?: number | null }
export interface AccidentSummary { asOf?: string | null; accidents12m: number }
export interface DistortionSummary { asOf?: string | null; reports12m: number; slaRated: number; slaFixedPct?: number | null; duplicatePct?: number | null; convertedToViolation: number; open: number }
export interface ViolationSummary { violations: number; open: number; awareness: number; warning: number; fine: number; fineAed: number; receivedAed: number; collectionPct?: number | null; asOf?: string | null }
export interface OccupancyCaseSummary { cases: number; open: number; urgent: number; fineAed: number }
export interface OpsRecord { type: 'distortion' | 'violation' | 'occupancy'; number?: string; raisedOn?: string; closedOn?: string | null; status?: string; item?: string; fineAed?: number | null; withinSla?: boolean | null }

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
  /** 使用面积：单元面积合计，不含单个 > 10,000 m² 的异常单元 */
  unitsAreaSqm?: number
  /** 被排除的异常单元数 */
  unitsAreaOutliers?: number
  /** 建筑面积 GFA：楼层面积合计 */
  floorsAreaSqm?: number
  /** 可建面积：未注销地块 devcode_max_gfa 合计 */
  plotsMaxGfaSqm?: number
  /** udm_building 有竣工 / 拆除日期的建筑数与最近日期 */
  buildingsCompleted?: number
  buildingsDemolished?: number
  lastCompletionDate?: string
  lastDemolitionDate?: string
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
  occupancy?: Occupancy
  sources?: Sources
}

/** 城市运行页签（GET /nodes/{level}/{key}/ops）。地块及以下按所在社区统计 */
export interface OpsView {
  scope: { level: Level; key: string } | null
  summary?: { lights: number; lamps: number; lightsInService: number; lightsOutOfService: number; busStops: number; busPlatforms: number }
  lightsByStatus?: { k: string; n: number; lamps: number }[]
  lightsByLampType?: { k: string; n: number; lamps: number }[]
  busStops?: { stopId: string; name?: string; nameAr?: string; platforms: number; codes?: string; cx?: number; cy?: number }[]
  cases?: { summary?: CaseSummary; byCategory?: (KN & { closed: number })[]; byType?: (KN & { closed: number })[]; byMonth?: { month: string; opened: number; closed: number }[] }
  accidents?: { summary?: AccidentSummary; bySeverity?: KN[]; byKind?: KN[]; byMonth?: { month: string; n: number }[] }
  distortions?: { summary?: DistortionSummary; byService?: (KN & { slaFixedPct?: number | null })[]; byEntity?: (KN & { slaFixedPct?: number | null })[]
    byMonth?: { month: string; reports: number; convertedToViolation: number }[] }
  violations?: { summary?: ViolationSummary; byService?: (KN & { open: number; fineAed: number })[]; byItem?: (KN & { open: number; fineAed: number })[] }
  occupancyCases?: { summary?: OccupancyCaseSummary; byStage?: KN[] }
  /** 仅地块及以下：本地块的视觉违规、违规、占用案件记录 */
  plotRecords?: OpsRecord[]
  sources?: Sources
}

export type AstCategory = 'school' | 'medical' | 'pharmacy' | 'mosque' | 'park'

export interface AstItem {
  category: AstCategory; id: string; name?: string; nameAr?: string; subtype?: string; subCategory?: string
  capacity?: number; students?: number; staff?: number; isPublic?: boolean; areaSqm?: number; plotKey?: string
  cx?: number; cy?: number
  /** 详情字段的 JSON 文本 */
  attrs?: string
}

/** 设施页签（GET /nodes/{level}/{key}/ast）。建筑及以下按所在地块统计 */
export interface AstView {
  scope: { level: Level; key: string } | null
  summary?: { schools: number; schoolCapacity: number; students: number; schoolStaff: number; seatUtilization?: number
    medical: number; beds: number; hospitals: number; pharmacies: number; mosques: number; worshippers: number
    parks: number; parkAreaHa: number }
  bySubtype?: Record<AstCategory, { k: string; n: number; capacity: number }[]>
  items?: Record<AstCategory, AstItem[]>
  /** 设施类型目录（宜居 35 类）；宜居数据只有区域粒度，社区及以下按所在区域；目录未抽取时为 null */
  facilityTypes?: FacilityTypes | null
}

/** 设施类型目录（宜居库 dim_facility_types，原型 8 个领域 35 类）与范围内现状数量 */
export interface FacilityTypes {
  scope: { level: Level; key: string }
  total: number
  typesPresent: number
  typesInCatalogue: number
  top: { type: string; label: string; category: string; n: number }[]
  /** 按数量降序；首页总览不带 */
  domains?: { category: string; label: string; n: number; present: number; types: { type: string; label: string; n: number }[] }[]
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

/** 宜居：一个区域的评分（只有区域粒度；inScope=false 时评分为 null，只有公园） */
export interface LivDistrict {
  key: string
  name: string
  nameAr?: string
  municipalityCode: string
  inScope: boolean
  existing: number | null
  /** 在途评分（原 Current），排名按它 */
  pipeline: number | null
  target: number | null
  /** 目标 − 在途 */
  gap: number | null
  rank: number | null
  rankOf: number
  muniRank: number | null
  muniRankOf: number
  parks: number | null
  parkAreaHa: number | null
  parkGapHa: number | null
  sample: boolean
  asOf?: string
  /** 达标所需投资 CAPEX（AED）：优先 fact_budget.total_capex_tier_target，为空时用 total_capex */
  capexAed?: number | null
  /** capexAed 用的是哪个：tier_target 分档目标口径 / total 宜居平台 Budget 总额 */
  capexBasis?: 'tier_target' | 'total' | null
  /** fact_budget.total_capex 原值（AED） */
  capexTotalAed?: number | null
  opexAed?: number | null
  /** 全部按分档目标缺口计的 CAPEX（AED） */
  capexTierTargetAed?: number | null
  /** 优先级分档 high_priority / priority / remaining（只有评估范围内的区有） */
  tier?: string | null
  /** 分档规则号 H1–H3 / P1–P4 / R */
  tierRule?: string | null
  /** 触发项，逗号分隔 */
  tierTriggers?: string | null
  livDistrictId?: number | null
  calcVersionId?: number | null
}

/** 最缺设施：在途供给评分低于分档目标的 FPP 类项目（0–100） */
export interface LivFacilityGap {
  rank: number
  facility: string
  /** critical / important / basic */
  category?: string | null
  existing?: number | null
  pipeline?: number | null
  ap50?: number | null
  target?: number | null
  gap: number
}

/** 居民需求：理想社区调查一个选项 */
export interface LivNeed {
  rank: number
  item: string
  pct: number
  votes?: number
  respondents?: number
}

export interface LivSummary {
  districts: number
  inScope: number
  avgExisting: number | null
  avgPipeline: number | null
  avgTarget: number | null
  parks: number
  parkAreaHa: number
  parkGapHa: number
  /** 全部区域 CAPEX 合计（AED，不按评估范围过滤） */
  capexAed?: number
  /** 各分档区域数 */
  tiers?: Record<string, number>
  asOf?: string
}

/** 地块周边设施（/nodes/{level}/{key}/nearby，地块及以下）：500 / 1000 m 范围圈 */
export interface NearbyItem {
  /** school / medical / pharmacy / mosque / park / bus */
  category: string
  id: string
  name?: string
  subtype?: string
  cx: number
  cy: number
  /** 与地块面内一点的距离（米） */
  dist: number
}
export interface NearbyView {
  scope: { level: Level; key: string } | null
  center?: [number, number] | null
  radii?: number[]
  counts?: Record<string, { r500: number; r1000: number; nearest: number | null }>
  items?: NearbyItem[]
}

/** 需关注区域的一项指标（原型 Districts to watch 的 6 项） */
export interface WatchIndicator {
  key: 'gap' | 'capex' | 'prj' | 'occ' | 'park' | 'vio'
  name: string
  /** 阈值文字，如「≥ 8 分」 */
  threshold: string
  /** false = 源数据未到位，暂不可算 */
  available: boolean
  /** 越过阈值的区数（不可算时为 null） */
  count: number | null
  /** 点卡片后打开的页签 */
  tab: string
  note?: string
}

/** 一个区越过的一项：value 为指标值（gap 分、capex AED、park %），其余为参照 */
export interface WatchItem {
  key: WatchIndicator['key']
  value: number
  pipeline?: number
  target?: number
  sharePct?: number
  shareOf?: string
  areaHa?: number
  needHa?: number
  /** prj：项目数、延期项目预算 */
  projects?: number
  delayedBudgetAed?: number
  /** occ：有状态的单元、已租 */
  units?: number
  rented?: number
  /** vio：有时限的上报、按期整改 */
  reports?: number
  fixed?: number
}

export interface WatchDistrict {
  key: string
  name: string
  municipalityCode: string
  cx: number | null
  cy: number | null
  items: WatchItem[]
}

/** /nodes/{emirate|municipality}/{key}/watch */
export interface WatchView {
  scope: { level: Level; key: string }
  indicators: WatchIndicator[]
  maxCards: number
  districts: WatchDistrict[]
  asOf?: string
}

/** /nodes/{level}/{key}/liv：酋长国 / 市政给 summary + districts；区域及以下给 district（社区及以下按所在区域） */
export interface LivView {
  scope: { level: Level; key: string } | null
  summary?: LivSummary
  districts?: LivDistrict[]
  district?: LivDistrict | null
  facilityGaps?: LivFacilityGap[]
  needs?: LivNeed[]
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
  // 仅开发：?mock=biz 时给业务视图填示例数据（源表还没导入时检查页面用，不进生产包）
  if (import.meta.env.DEV && location.search.includes('mock=biz')) return (await import('./mock')).mockBiz(path, body.data) as T
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
  /** 首页五主题总览（酋长国 / 市政 / 区域 / 社区） */
  overview: (level: Level, key: string, signal?: AbortSignal) => get<Record<string, unknown>>(`/nodes/${level}/${enc(key)}/overview`, signal),
  nearby: (level: Level, key: string, signal?: AbortSignal) => get<NearbyView>(`/nodes/${level}/${enc(key)}/nearby`, signal),
  watch: (level: Level, key: string, signal?: AbortSignal) => get<WatchView>(`/nodes/${level}/${enc(key)}/watch`, signal),
  liv: (level: Level, key: string, signal?: AbortSignal) => get<LivView>(`/nodes/${level}/${enc(key)}/liv`, signal),
  ast: (level: Level, key: string, signal?: AbortSignal) => get<AstView>(`/nodes/${level}/${enc(key)}/ast`, signal),
  ops: (level: Level, key: string, signal?: AbortSignal) => get<OpsView>(`/nodes/${level}/${enc(key)}/ops`, signal),
  mkt: (level: Level, key: string, signal?: AbortSignal) => get<MktView>(`/nodes/${level}/${enc(key)}/mkt`, signal),
  dev: (level: Level, key: string, signal?: AbortSignal) => get<DevView>(`/nodes/${level}/${enc(key)}/dev`, signal),
  search: (q: string) => get<SearchHit[]>(`/search?q=${enc(q)}&limit=6`),
  meta: () => get<Meta>('/meta'),
}
