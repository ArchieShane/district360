import type { ApiResponse, DistrictFeatureCollection, DistrictListItem, DistrictOverview, FacilityCategory, FacilityItem, LineageInfo, MetaInfo, PageResult } from './types'

/**
 * 接口地址用相对路径：页面挂在什么路径下（开发时 /，生产时 /district360/ 等二级路由），接口就跟着走，
 * 换二级路由名只改后端配置，前端不用重新打包。
 */
const BASE = 'api/v1'
/** 瓦片地址 MapLibre 要绝对地址；用当前页面所在目录拼，保留 {z}/{x}/{y} 不被转义 */
const ABS_BASE = () => new URL('./', location.href).href + BASE

async function get<T>(path: string): Promise<T> {
  const res = await fetch(BASE + path, { headers: { Accept: 'application/json' } })
  const body = (await res.json().catch(() => null)) as ApiResponse<T> | null
  if (!res.ok || !body || body.code !== 0) {
    throw new Error(body?.message || `请求失败：${res.status}`)
  }
  return body.data
}

export const api = {
  districts: (page: number, size: number, q?: string) => {
    const p = new URLSearchParams({ page: String(page), size: String(size) })
    if (q) p.set('q', q)
    return get<PageResult<DistrictListItem>>(`/districts?${p}`)
  },
  /** v = 数据版本（最近一次 makani 抽取完成时间），数据重新抽取后地址变化，避免浏览器用到旧缓存 */
  boundaries: async (v: string): Promise<DistrictFeatureCollection> => {
    const res = await fetch(`${BASE}/map/districts.geojson?v=${encodeURIComponent(v)}`)
    if (!res.ok) throw new Error(`请求失败：${res.status}`)
    return res.json()
  },
  overview: (code: string) => get<DistrictOverview>(`/districts/${encodeURIComponent(code)}/overview`),
  meta: () => get<MetaInfo>('/meta'),
  facilities: (code: string, category: FacilityCategory) =>
    get<FacilityItem[]>(`/districts/${encodeURIComponent(code)}/facilities/${category}`),
  lineage: () => get<LineageInfo>('/lineage'),
  plotTilesUrl: (v: string) => `${ABS_BASE()}/map/tiles/plots/{z}/{x}/{y}.pbf?v=${encodeURIComponent(v)}`,
  buildingTilesUrl: (v: string) => `${ABS_BASE()}/map/tiles/buildings/{z}/{x}/{y}.pbf?v=${encodeURIComponent(v)}`,
}
