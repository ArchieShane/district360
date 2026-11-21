/** 与后端 /api/v1 返回结构一一对应。 */

export type BlockStatus = 'ok' | 'sample' | 'not_assessed' | 'unavailable'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface DistrictListItem {
  code: string
  nameEn: string
  nameAr: string | null
  sortNo: number
  mappingConfirmed: boolean
  mappingNote: string | null
  makaniDistrictId: number | null
  livDistrictId: number | null
  centroid: [number, number] | null
  bbox: [number, number, number, number] | null
  areaKm2: number | null
  liveabilityScore: number | null
  liveabilityStatus: BlockStatus
}

export interface LandUseShare {
  key: string
  nameZh: string
  nameEn: string
  plotCount: number
  pct: number | null
}

export interface DistrictOverview {
  district: {
    code: string
    nameEn: string
    nameAr: string | null
    municipality: string
    areaKm2: number | null
    communities: number | null
    plots: number | null
    mappingConfirmed: boolean
    mappingNote: string | null
  }
  liveability: {
    status: BlockStatus
    asOf: string | null
    existing: number | null
    current: number | null
    target: number | null
    gap: number | null
  }
  land: {
    status: BlockStatus
    asOf: string | null
    plots: number | null
    buildings: number | null
    buildingsUnderConstruction: number | null
    plotBuiltRatePct: number | null
    plotStatus: {
      constructed: number | null
      underConstruction: number | null
      vacantServiced: number | null
      vacantUnserviced: number | null
    }
    landUse: LandUseShare[]
  }
  realEstate: {
    status: BlockStatus
    asOf: string | null
    periodStart: string | null
    periodEnd: string | null
    salesAedBn: number | null
    salesYoyPct: number | null
    transactions: number | null
    mortgages: number | null
    avgPriceSqm: number | null
    activeLeases: number | null
    monthly: { month: string; transactions: number }[]
  }
  facilities: {
    status: BlockStatus
    asOf: string | null
    schools: number | null
    medical: number | null
    mosques: number | null
    parks: {
      status: BlockStatus
      asOf: string | null
      count: number | null
      areaHa: number | null
      gapHa: number | null
    }
  }
}

export interface SourceInfo {
  source: 'makani' | 'liveability' | 'adrec'
  enabled: boolean
  rowCount: number
  sourceAsOf: string | null
  sample: boolean
  lastStatus: string | null
  lastFinishedAt: string | null
  lastMessage: string | null
}

export interface MetaInfo {
  districtCount: number
  blockCount: number
  sources: SourceInfo[]
}

export type SourceKey = 'makani' | 'liveability' | 'adrec'

export interface LineageInfo {
  statDb: string
  sources: Record<SourceKey, { nameZh: string; nameEn: string; database: string; implemented: boolean }>
  metrics: Record<string, {
    nameZh: string
    nameEn: string
    source: SourceKey
    statColumn: string
    sourceTables: string[]
    logicZh: string
    logicEn: string
    sql: string | null
  }>
}
