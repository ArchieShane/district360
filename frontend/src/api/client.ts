import type { ApiResponse, DistrictFeatureCollection, DistrictListItem, DistrictOverview, LineageInfo, MetaInfo, PageResult } from './types'

const BASE = '/api/v1'

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
  boundaries: async (): Promise<DistrictFeatureCollection> => {
    const res = await fetch(`${BASE}/map/districts.geojson`)
    if (!res.ok) throw new Error(`请求失败：${res.status}`)
    return res.json()
  },
  overview: (code: string) => get<DistrictOverview>(`/districts/${encodeURIComponent(code)}/overview`),
  meta: () => get<MetaInfo>('/meta'),
  lineage: () => get<LineageInfo>('/lineage'),
  buildingTilesUrl: () => `${location.origin}${BASE}/map/tiles/buildings/{z}/{x}/{y}.pbf`,
}
